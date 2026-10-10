package org.telegram.messenger.plugins;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class PluginStorage {
    public static final int MAX_ENTRIES = 4096;
    public static final int MAX_VALUE_BYTES = 256 * 1024;
    public static final int MAX_TOTAL_VALUE_BYTES = 4 * 1024 * 1024;

    private PluginStorage() {
    }

    public static Session open(String pluginId) {
        return new Session(pluginId);
    }

    public static final class Session {
        private final String pluginId;
        private SQLiteDatabase database;
        private boolean closed;

        private Session(String pluginId) {
            if (pluginId == null
                    || !pluginId.matches("[a-z][a-z0-9_.-]{0,127}")
                    || pluginId.getBytes(StandardCharsets.UTF_8).length > 128) {
                throw failure("INVALID_PLUGIN", "Invalid plugin ID");
            }

            this.pluginId = pluginId;
        }

        public synchronized String globalScope() {
            requireOpen();
            return "global";
        }

        public synchronized String accountScope(int account) {
            requireOpen();
            long owner = currentOwner(account);

            if (owner <= 0) {
                throw failure(
                        "ACCOUNT_UNAVAILABLE",
                        "Telegram account " + account + " is unavailable"
                );
            }

            return "account:" + account + ":" + owner;
        }

        public synchronized String read(String scopeToken, String key) {
            requireOpen();
            validateKey(key);
            Scope scope = parseScope(scopeToken);
            requireOwner(scope);

            try (Cursor cursor = openDatabase().query(
                    "storage_values",
                    new String[] {"value_type", "value", "byte_size"},
                    "scope_kind = ? AND account = ? AND owner = ? AND storage_key = ?",
                    scope.arguments(key),
                    null,
                    null,
                    null,
                    "1"
            )) {
                if (!cursor.moveToFirst()) {
                    return null;
                }

                JSONObject result = new JSONObject();
                String kind = cursor.getString(0);
                byte[] value = cursor.getBlob(1);
                result.put("kind", kind);
                result.put("payload", decodeValue(kind, value));
                result.put("byte_size", cursor.getInt(2));
                return result.toString();
            } catch (RuntimeException error) {
                throw error;
            } catch (Exception error) {
                throw failure("STORAGE_FAILURE", error.toString());
            }
        }

        public synchronized void apply(String payload) {
            requireOpen();
            JSONObject request;

            try {
                request = new JSONObject(payload);
            } catch (Exception error) {
                throw failure("INVALID_REQUEST", "Invalid storage request");
            }

            JSONArray operations = request.optJSONArray("operations");

            if (operations == null) {
                throw failure("INVALID_REQUEST", "Missing storage operations");
            }

            Set<Scope> scopes = new LinkedHashSet<>();

            for (int index = 0; index < operations.length(); index++) {
                JSONObject operation = operations.optJSONObject(index);

                if (operation == null) {
                    throw failure("INVALID_REQUEST", "Invalid storage operation");
                }

                if (!"clear_all".equals(operation.optString("action"))) {
                    scopes.add(parseScope(operation.optString("scope", null)));
                }
            }

            requireOwners(scopes);
            SQLiteDatabase db = openDatabase();
            db.beginTransaction();

            try {
                for (int index = 0; index < operations.length(); index++) {
                    applyOperation(db, operations.getJSONObject(index));
                }

                enforceQuota(db);

                if (!request.isNull("schema_version")) {
                    int schemaVersion = request.getInt("schema_version");

                    if (schemaVersion < 0) {
                        throw failure("INVALID_REQUEST", "Invalid storage schema version");
                    }

                    ContentValues values = new ContentValues();
                    values.put("schema_version", schemaVersion);
                    db.update("storage_metadata", values, "singleton = 1", null);
                }

                requireOwners(scopes);
                db.setTransactionSuccessful();
            } catch (RuntimeException error) {
                throw error;
            } catch (Exception error) {
                throw failure("STORAGE_FAILURE", error.toString());
            } finally {
                db.endTransaction();
            }
        }

        public synchronized String statistics() {
            requireOpen();
            int count;
            int bytes;
            int schemaVersion;

            try (Cursor cursor = openDatabase().rawQuery(
                    "SELECT COUNT(*), COALESCE(SUM(byte_size), 0) FROM storage_values",
                    null
            )) {
                cursor.moveToFirst();
                count = cursor.getInt(0);
                bytes = cursor.getInt(1);
            }

            try (Cursor cursor = openDatabase().query(
                    "storage_metadata",
                    new String[] {"schema_version"},
                    "singleton = 1",
                    null,
                    null,
                    null,
                    null,
                    "1"
            )) {
                if (!cursor.moveToFirst()) {
                    throw failure("STORAGE_FAILURE", "Missing storage metadata");
                }

                schemaVersion = cursor.getInt(0);
            }

            try {
                JSONObject limits = new JSONObject();
                limits.put("max_entries", MAX_ENTRIES);
                limits.put("max_value_bytes", MAX_VALUE_BYTES);
                limits.put("max_total_value_bytes", MAX_TOTAL_VALUE_BYTES);

                JSONObject result = new JSONObject();
                result.put("value_count", count);
                result.put("value_bytes", bytes);
                result.put("schema_version", schemaVersion);
                result.put("limits", limits);
                return result.toString();
            } catch (Exception error) {
                throw failure("STORAGE_FAILURE", error.toString());
            }
        }

        public synchronized void close() {
            closed = true;

            if (database != null) {
                database.close();
                database = null;
            }
        }

        private void applyOperation(SQLiteDatabase db, JSONObject operation) throws Exception {
            String action = operation.getString("action");

            if ("clear_all".equals(action)) {
                db.delete("storage_values", null, null);
                return;
            }

            Scope scope = parseScope(operation.getString("scope"));
            String isolation = "scope_kind = ? AND account = ? AND owner = ?";

            if ("clear".equals(action)) {
                db.delete("storage_values", isolation, scope.arguments());
                return;
            }

            String key = operation.getString("key");
            validateKey(key);

            if ("delete".equals(action)) {
                db.delete(
                        "storage_values",
                        isolation + " AND storage_key = ?",
                        scope.arguments(key)
                );
                return;
            }

            if (!"set".equals(action)) {
                throw failure("INVALID_REQUEST", "Unsupported storage operation");
            }

            JSONObject stored = operation.getJSONObject("value");
            String kind = stored.getString("kind");
            int byteSize = stored.getInt("byte_size");

            if (byteSize < 0 || byteSize > MAX_VALUE_BYTES) {
                throw failure("QUOTA_EXCEEDED", "Storage value exceeds the per-value limit");
            }

            byte[] value = encodeValue(kind, stored.get("payload"));

            if (measuredSize(kind, value) != byteSize) {
                throw failure("INVALID_REQUEST", "Storage value size does not match payload");
            }

            ContentValues values = new ContentValues();
            values.put("scope_kind", scope.kind);
            values.put("account", scope.account);
            values.put("owner", scope.owner);
            values.put("storage_key", key);
            values.put("value_type", kind);
            values.put("value", value);
            values.put("byte_size", byteSize);
            db.insertWithOnConflict(
                    "storage_values",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
            );
        }

        private void enforceQuota(SQLiteDatabase db) {
            try (Cursor cursor = db.rawQuery(
                    "SELECT COUNT(*), COALESCE(SUM(byte_size), 0) FROM storage_values",
                    null
            )) {
                cursor.moveToFirst();

                if (cursor.getInt(0) > MAX_ENTRIES) {
                    throw failure("QUOTA_EXCEEDED", "Storage entry limit exceeded");
                }

                if (cursor.getLong(1) > MAX_TOTAL_VALUE_BYTES) {
                    throw failure("QUOTA_EXCEEDED", "Storage size limit exceeded");
                }
            }
        }

        private SQLiteDatabase openDatabase() {
            if (database != null && database.isOpen()) {
                return database;
            }

            File directory = new File(
                    ApplicationLoader.applicationContext.getNoBackupFilesDir(),
                    "fluxgram_plugins/storage"
            );

            if (!directory.exists() && !directory.mkdirs() && !directory.exists()) {
                throw failure("STORAGE_FAILURE", "Cannot create plugin storage directory");
            }

            database = SQLiteDatabase.openOrCreateDatabase(
                    new File(directory, sha256(pluginId) + ".db"),
                    null
            );
            database.execSQL("CREATE TABLE IF NOT EXISTS storage_metadata (singleton INTEGER NOT NULL PRIMARY KEY CHECK(singleton = 1), plugin_id TEXT NOT NULL, schema_version INTEGER NOT NULL)");
            database.execSQL("CREATE TABLE IF NOT EXISTS storage_values (scope_kind INTEGER NOT NULL, account INTEGER NOT NULL, owner INTEGER NOT NULL, storage_key TEXT NOT NULL, value_type TEXT NOT NULL, value BLOB NOT NULL, byte_size INTEGER NOT NULL, PRIMARY KEY(scope_kind, account, owner, storage_key))");
            database.execSQL("INSERT OR IGNORE INTO storage_metadata(singleton, plugin_id, schema_version) VALUES(1, ?, 0)", new Object[] {pluginId});

            try (Cursor cursor = database.query(
                    "storage_metadata",
                    new String[] {"plugin_id"},
                    "singleton = 1",
                    null,
                    null,
                    null,
                    null,
                    "1"
            )) {
                if (!cursor.moveToFirst() || !pluginId.equals(cursor.getString(0))) {
                    database.close();
                    database = null;
                    throw failure("STORAGE_FAILURE", "Plugin storage identity mismatch");
                }
            }

            database.setVersion(1);
            return database;
        }

        private void requireOpen() {
            if (closed) {
                throw failure("INACTIVE", "Plugin storage is inactive");
            }
        }

        private void requireOwners(Set<Scope> scopes) {
            for (Scope scope : scopes) {
                requireOwner(scope);
            }
        }

        private void requireOwner(Scope scope) {
            if (scope.kind == 1 && currentOwner(scope.account) != scope.owner) {
                throw failure(
                        "OWNER_CHANGED",
                        "Telegram account " + scope.account + " changed owner"
                );
            }
        }
    }

    private static final class Scope {
        final int kind;
        final int account;
        final long owner;

        Scope(int kind, int account, long owner) {
            this.kind = kind;
            this.account = account;
            this.owner = owner;
        }

        String[] arguments(String... extra) {
            String[] result = new String[3 + extra.length];
            result[0] = Integer.toString(kind);
            result[1] = Integer.toString(account);
            result[2] = Long.toString(owner);
            System.arraycopy(extra, 0, result, 3, extra.length);
            return result;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Scope)) {
                return false;
            }

            Scope scope = (Scope) other;
            return kind == scope.kind && account == scope.account && owner == scope.owner;
        }

        @Override
        public int hashCode() {
            int result = kind;
            result = 31 * result + account;
            result = 31 * result + Long.hashCode(owner);
            return result;
        }
    }

    private static Scope parseScope(String token) {
        if ("global".equals(token)) {
            return new Scope(0, -1, 0);
        }

        if (token == null || !token.startsWith("account:")) {
            throw failure("INVALID_REQUEST", "Invalid storage scope");
        }

        String[] fields = token.split(":", -1);

        if (fields.length != 3) {
            throw failure("INVALID_REQUEST", "Invalid storage scope");
        }

        try {
            int account = Integer.parseInt(fields[1]);
            long owner = Long.parseLong(fields[2]);

            if (account < 0 || owner <= 0) {
                throw new NumberFormatException();
            }

            return new Scope(1, account, owner);
        } catch (NumberFormatException error) {
            throw failure("INVALID_REQUEST", "Invalid storage scope");
        }
    }

    private static long currentOwner(int account) {
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) {
            return 0;
        }

        return UserConfig.getInstance(account).getClientUserId();
    }

    private static void validateKey(String key) {
        if (key == null
                || !key.matches("[a-z][a-z0-9_.-]{0,127}")
                || key.getBytes(StandardCharsets.UTF_8).length > 128) {
            throw failure("INVALID_REQUEST", "Invalid storage key");
        }
    }

    private static byte[] encodeValue(String kind, Object payload) {
        String text;

        switch (kind) {
            case "bool":
                if (!(payload instanceof Boolean)) {
                    throw failure("INVALID_REQUEST", "Invalid boolean storage value");
                }

                return new byte[] {(byte) ((Boolean) payload ? 1 : 0)};
            case "int":
                if (!(payload instanceof Number)) {
                    throw failure("INVALID_REQUEST", "Invalid integer storage value");
                }

                text = Long.toString(((Number) payload).longValue());
                return text.getBytes(StandardCharsets.UTF_8);
            case "float":
                if (!(payload instanceof Number)) {
                    throw failure("INVALID_REQUEST", "Invalid float storage value");
                }

                double value = ((Number) payload).doubleValue();

                if (!Double.isFinite(value)) {
                    throw failure("INVALID_REQUEST", "Invalid float storage value");
                }

                return Double.toString(value).getBytes(StandardCharsets.UTF_8);
            case "string":
            case "json":
                if (!(payload instanceof String)) {
                    throw failure("INVALID_REQUEST", "Invalid text storage value");
                }

                return ((String) payload).getBytes(StandardCharsets.UTF_8);
            case "bytes":
                if (!(payload instanceof String)) {
                    throw failure("INVALID_REQUEST", "Invalid byte storage value");
                }

                try {
                    return Base64.decode((String) payload, Base64.NO_WRAP);
                } catch (IllegalArgumentException error) {
                    throw failure("INVALID_REQUEST", "Invalid byte storage value");
                }
            default:
                throw failure("INVALID_REQUEST", "Unsupported storage value type");
        }
    }

    private static Object decodeValue(String kind, byte[] payload) {
        String text = new String(payload, StandardCharsets.UTF_8);

        switch (kind) {
            case "bool":
                return payload.length == 1 && payload[0] != 0;
            case "int":
                return Long.parseLong(text);
            case "float":
                return Double.parseDouble(text);
            case "string":
            case "json":
                return text;
            case "bytes":
                return Base64.encodeToString(payload, Base64.NO_WRAP);
            default:
                throw failure("STORAGE_FAILURE", "Unsupported stored value type");
        }
    }

    private static int measuredSize(String kind, byte[] value) {
        if ("bool".equals(kind)) {
            return 1;
        }

        if ("int".equals(kind) || "float".equals(kind)) {
            return 8;
        }

        return value.length;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder result = new StringBuilder(digest.length * 2);

            for (byte current : digest) {
                result.append(String.format(Locale.ROOT, "%02x", current & 0xff));
            }

            return result.toString();
        } catch (Exception error) {
            throw failure("STORAGE_FAILURE", error.toString());
        }
    }

    private static IllegalStateException failure(String code, String message) {
        return new IllegalStateException(code + ":" + message);
    }
}

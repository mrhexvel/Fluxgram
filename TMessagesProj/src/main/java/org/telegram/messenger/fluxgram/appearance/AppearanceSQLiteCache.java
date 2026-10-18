package org.telegram.messenger.fluxgram.appearance;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.telegram.messenger.ApplicationLoader;

import java.io.File;

public final class AppearanceSQLiteCache implements AppearanceRepository.Store {
    public static final int MAX_ENTRIES = 10000;

    private final int account;
    private final long owner;
    private final AppearanceApiParser parser;
    private SQLiteDatabase database;

    public AppearanceSQLiteCache(int account, long owner, AppearanceApiParser parser) {
        this.account = account;
        this.owner = owner;
        this.parser = parser;
    }

    @Override
    public AppearanceApiDto.Result read(FluxGramPeerKey requested) throws Exception {
        FluxGramPeerKey canonical = readAlias(requested);

        if (canonical == null) {
            canonical = requested;
        }

        try (Cursor cursor = open().query(
                "profiles",
                new String[] {"result_json"},
                "account = ? AND owner = ? AND peer_type = ? AND peer_id = ?",
                arguments(canonical),
                null,
                null,
                null,
                "1"
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }

            return parser.parseResult(cursor.getString(0));
        }
    }

    @Override
    public AppearanceApiDto.Result readCanonical(FluxGramPeerKey canonical) throws Exception {
        try (Cursor cursor = open().query(
                "profiles",
                new String[] {"result_json"},
                "account = ? AND owner = ? AND peer_type = ? AND peer_id = ?",
                arguments(canonical),
                null,
                null,
                null,
                "1"
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }

            return parser.parseResult(cursor.getString(0));
        }
    }

    @Override
    public void write(FluxGramPeerKey requested, AppearanceApiDto.Result result, long storedAt) {
        SQLiteDatabase db = open();
        db.beginTransaction();

        try {
            ContentValues profile = new ContentValues();
            profile.put("account", account);
            profile.put("owner", owner);
            profile.put("peer_type", result.getPeer().getType().getApiValue());
            profile.put("peer_id", result.getPeer().getId());
            profile.put("revision", result.getRevision().toString());
            profile.put("expires_at", result.getExpiresAt());
            profile.put("positive", result.getProfile() == null ? 0 : 1);
            profile.put("result_json", result.getJson());
            profile.put("stored_at", storedAt);
            db.insertWithOnConflict("profiles", null, profile, SQLiteDatabase.CONFLICT_REPLACE);

            if (requested.equals(result.getPeer())) {
                db.delete(
                        "aliases",
                        "account = ? AND owner = ? AND requested_type = ? AND requested_id = ?",
                        arguments(requested)
                );
            } else {
                ContentValues alias = new ContentValues();
                alias.put("account", account);
                alias.put("owner", owner);
                alias.put("requested_type", requested.getType().getApiValue());
                alias.put("requested_id", requested.getId());
                alias.put("canonical_type", result.getPeer().getType().getApiValue());
                alias.put("canonical_id", result.getPeer().getId());
                db.insertWithOnConflict("aliases", null, alias, SQLiteDatabase.CONFLICT_REPLACE);
            }

            prune(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void clear() {
        SQLiteDatabase db = open();
        String[] isolation = new String[] {Integer.toString(account), Long.toString(owner)};
        db.delete("aliases", "account = ? AND owner = ?", isolation);
        db.delete("profiles", "account = ? AND owner = ?", isolation);
    }

    @Override
    public void close() {
        if (database != null) {
            database.close();
            database = null;
        }
    }

    private FluxGramPeerKey readAlias(FluxGramPeerKey requested) {
        try (Cursor cursor = open().query(
                "aliases",
                new String[] {"canonical_type", "canonical_id"},
                "account = ? AND owner = ? AND requested_type = ? AND requested_id = ?",
                arguments(requested),
                null,
                null,
                null,
                "1"
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }

            FluxGramPeerKey.Type type = FluxGramPeerKey.Type.fromApiValue(cursor.getString(0));
            return type == null ? null : new FluxGramPeerKey(type, cursor.getLong(1));
        }
    }

    private String[] arguments(FluxGramPeerKey peer) {
        return new String[] {
                Integer.toString(account),
                Long.toString(owner),
                peer.getType().getApiValue(),
                Long.toString(peer.getId())
        };
    }

    private SQLiteDatabase open() {
        if (database != null && database.isOpen()) {
            return database;
        }

        File directory = new File(
                ApplicationLoader.applicationContext.getNoBackupFilesDir(),
                "fluxgram_appearance"
        );

        if (!directory.exists() && !directory.mkdirs() && !directory.exists()) {
            throw new IllegalStateException("cannot create appearance cache directory");
        }

        database = SQLiteDatabase.openOrCreateDatabase(new File(directory, "cache.db"), null);
        database.execSQL("CREATE TABLE IF NOT EXISTS profiles (account INTEGER NOT NULL, owner INTEGER NOT NULL, peer_type TEXT NOT NULL, peer_id INTEGER NOT NULL, revision TEXT NOT NULL, expires_at INTEGER NOT NULL, positive INTEGER NOT NULL, result_json TEXT NOT NULL, stored_at INTEGER NOT NULL, PRIMARY KEY(account, owner, peer_type, peer_id))");
        database.execSQL("CREATE TABLE IF NOT EXISTS aliases (account INTEGER NOT NULL, owner INTEGER NOT NULL, requested_type TEXT NOT NULL, requested_id INTEGER NOT NULL, canonical_type TEXT NOT NULL, canonical_id INTEGER NOT NULL, PRIMARY KEY(account, owner, requested_type, requested_id))");
        database.execSQL("CREATE INDEX IF NOT EXISTS profiles_isolation_stored ON profiles(account, owner, stored_at)");
        database.setVersion(1);
        return database;
    }

    private void prune(SQLiteDatabase db) {
        db.execSQL(
                "DELETE FROM profiles WHERE account = ? AND owner = ? AND rowid IN (SELECT rowid FROM profiles WHERE account = ? AND owner = ? ORDER BY stored_at DESC LIMIT -1 OFFSET ?)",
                new Object[] {account, owner, account, owner, MAX_ENTRIES}
        );
        db.execSQL(
                "DELETE FROM aliases WHERE account = ? AND owner = ? AND NOT EXISTS (SELECT 1 FROM profiles WHERE profiles.account = aliases.account AND profiles.owner = aliases.owner AND profiles.peer_type = aliases.canonical_type AND profiles.peer_id = aliases.canonical_id)",
                new Object[] {account, owner}
        );
    }
}

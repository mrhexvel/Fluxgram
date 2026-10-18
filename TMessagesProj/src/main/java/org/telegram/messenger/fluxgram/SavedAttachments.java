package org.telegram.messenger.fluxgram;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class SavedAttachments {
    static final class Statistics {
        final long count;
        final long bytes;
        final long failures;

        Statistics(long count, long bytes, long failures) {
            this.count = count;
            this.bytes = bytes;
            this.failures = failures;
        }
    }

    private static final long MB = 1024L * 1024;
    private static final long FREE_RESERVE = 64 * MB;
    private static final Pattern EXTENSION = Pattern.compile("[A-Za-z0-9]{1,12}");
    private static final Pattern FILE_NAME = Pattern.compile("-?[0-9]+_[0-9]+\\.[A-Za-z0-9]{1,12}");
    private static final DispatchQueue queue = new DispatchQueue("fluxgram-attachments", true, android.os.Process.THREAD_PRIORITY_BACKGROUND);
    private static final Semaphore pending = new Semaphore(32);
    private static final HashMap<Integer, SavedAttachments> instances = new HashMap<>();
    private static final long[] TOTAL_LIMITS = {300 * MB, 1024 * MB, 2048 * MB, 5120 * MB, 16384 * MB, Long.MAX_VALUE};
    private static final long[] FILE_LIMITS = {10 * MB, 50 * MB, 100 * MB, 500 * MB};
    private final int account;
    private final long owner;
    private final File directory;
    private final SharedPreferences preferences;
    private SQLiteDatabase database;
    private boolean initialized;
    private final AtomicInteger failures;
    private final AtomicBoolean failureFlushPending = new AtomicBoolean();
    private long used;
    private int count;
    private volatile boolean closed;
    private final HashMap<Long, HashSet<Integer>> changes = new HashMap<>();
    private boolean updatePosted;

    private SavedAttachments(int account, long owner) {
        this.account = account;
        this.owner = owner;
        directory = new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "fluxgram_attachments/" + account + "_" + owner);
        preferences = ApplicationLoader.applicationContext.getSharedPreferences("fluxgram_attachments_" + account, Context.MODE_PRIVATE);
        failures = new AtomicInteger(preferences.getInt("failures", 0));
    }

    public static synchronized SavedAttachments get(int account) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        SavedAttachments current = instances.get(account);

        if (current == null || current.owner != owner || current.closed) {
            current = new SavedAttachments(account, owner);
            instances.put(account, current);
            current.initializeExisting();
        }

        return current;
    }

    private void initializeExisting() {
        queue.postRunnable(() -> {
            if (!active() || !directory.isDirectory()) {
                return;
            }

            try {
                open();
            } catch (Exception error) {
                FileLog.e(error);
            }
        });
    }

    public boolean isEnabled() {
        return preferences.getBoolean("enabled", false);
    }

    public void setEnabled(boolean enabled) {
        preferences.edit().putBoolean("enabled", enabled).apply();
    }

    public int totalLimitIndex() {
        return Math.max(0, Math.min(TOTAL_LIMITS.length - 1, preferences.getInt("total_limit", 0)));
    }

    public int fileLimitIndex() {
        return Math.max(0, Math.min(FILE_LIMITS.length - 1, preferences.getInt("file_limit", 1)));
    }

    public void setTotalLimit(int index, java.util.function.Consumer<Boolean> completed) {
        preferences.edit().putInt("total_limit", index).apply();
        queue.postRunnable(() -> {
            boolean success = true;

            try {
                open();
                trim(0);
            } catch (Exception error) {
                FileLog.e(error);
                success = false;
            }

            boolean trimmed = success;
            AndroidUtilities.runOnUIThread(() -> {
                if (active()) {
                    completed.accept(trimmed);
                }
            });
        });
    }

    public void setFileLimit(int index) {
        preferences.edit().putInt("file_limit", index).apply();
    }

    public boolean allChats() {
        return preferences.getBoolean("all_chats", true);
    }

    public Set<String> selectedChats() {
        return new HashSet<>(preferences.getStringSet("chats", new HashSet<>()));
    }

    public void setChats(Set<String> chats) {
        preferences.edit().putBoolean("all_chats", chats == null)
                .putStringSet("chats", chats == null ? new HashSet<>() : new HashSet<>(chats)).apply();
    }

    private boolean active() {
        return !closed && owner != 0 && UserConfig.getInstance(account).getClientUserId() == owner;
    }

    private boolean allowed(long dialog) {
        return active() && isEnabled() && EditHistory.keepsDeleted(account)
                && (allChats() || preferences.getStringSet("chats", new HashSet<>()).contains(Long.toString(dialog)));
    }

    private static boolean supported(TLRPC.Message message) {
        if (message == null || message.ttl != 0 || message.ttl_period != 0 || message.media == null
                || message.media.ttl_seconds != 0 || DialogObject.isEncryptedDialog(message.dialog_id)) {
            return false;
        }

        if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
            return message.media.photo != null;
        }

        return message.media instanceof TLRPC.TL_messageMediaDocument && message.media.document != null
                && !(message.media.document instanceof TLRPC.TL_documentEncrypted)
                && !MessageObject.isMusicMessage(message)
                && !MessageObject.isGifMessage(message) && !MessageObject.isStickerMessage(message)
                && !MessageObject.isAnimatedStickerMessage(message);
    }

    private File file(TLRPC.Message message) {
        String extension = "jpg";

        if (message.media instanceof TLRPC.TL_messageMediaDocument) {
            String name = FileLoader.getAttachFileName(message.media.document);
            int dot = name.lastIndexOf('.');
            extension = dot >= 0 ? name.substring(dot + 1) : "bin";

            if (!EXTENSION.matcher(extension).matches()) {
                extension = "bin";
            }
        }

        return new File(directory, message.dialog_id + "_" + message.id + "." + extension);
    }

    public boolean apply(TLRPC.Message message) {
        if (active() && message.fluxgramDeleted && supported(message)) {
            message.attachPath = file(message).getAbsolutePath();
            return true;
        }

        return false;
    }

    public void capture(TLRPC.Message message) {
        if (!supported(message) || !allowed(message.dialog_id)) {
            return;
        }

        FileInputStream input = null;
        boolean acquired = false;

        try {
            long limit = Math.min(FILE_LIMITS[fileLimitIndex()], TOTAL_LIMITS[totalLimitIndex()]);
            Source source = findSource(message, limit);
            File destination = file(message);

            if (source == null || destination.exists()) {
                return;
            }

            if (!pending.tryAcquire()) {
                recordFailure();
                return;
            }

            acquired = true;
            input = new FileInputStream(source.file);
            FileInputStream retained = input;
            long dialog = message.dialog_id;
            int mid = message.id;
            if (!queue.postRunnable(() -> save(retained, destination, source.size, dialog, mid))) {
                throw new IOException("Attachment queue is unavailable");
            }
        } catch (Exception error) {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception closeError) {
                    FileLog.e(closeError);
                }
            }

            if (acquired) {
                pending.release();
            }

            recordFailure();
            FileLog.e(error);
        }
    }

    private static final class Source {
        final File file;
        final long size;

        Source(File file, long size) {
            this.file = file;
            this.size = size;
        }
    }

    private Source completeSource(File file, long expected, long limit) {
        if (AttachmentCopy.isComplete(file, expected, limit)
                && !FileLoader.getInstance(account).isLoadingFile(file.getName())) {
            return new Source(file, expected);
        }

        return null;
    }

    private Source findSource(TLRPC.Message message, long limit) {
        FileLoader loader = FileLoader.getInstance(account);
        File original = loader.getPathToMessage(message);
        long expected = message.media.document == null ? original.length() : message.media.document.size;
        Source source = completeSource(original, expected, limit);

        if (source != null) {
            return source;
        }

        if (MessageObject.isVideoMessage(message)) {
            for (TLRPC.Document document : message.media.alt_documents) {
                if (!MessageObject.isVideoDocument(document) || document instanceof TLRPC.TL_documentEncrypted
                        || "application/x-mpegurl".equalsIgnoreCase(document.mime_type)
                        || "application/x-tgstoryboard".equalsIgnoreCase(document.mime_type)
                        || "application/x-tgstoryboardmap".equalsIgnoreCase(document.mime_type)) {
                    continue;
                }

                source = completeSource(loader.getPathToAttach(document), document.size, limit);

                if (source != null) {
                    return source;
                }
            }
        }

        if (message.attachPath != null && !message.attachPath.isEmpty()) {
            return completeSource(new File(message.attachPath), expected, limit);
        }

        return null;
    }

    private void open() throws Exception {
        if (!active()) {
            throw new IllegalStateException("Attachment archive is closed");
        }

        if (initialized) {
            return;
        }

        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create attachment directory");
        }

        if (database == null) {
            database = SQLiteDatabase.openOrCreateDatabase(new File(directory, "index.db"), null);
        }
        database.execSQL("CREATE TABLE IF NOT EXISTS attachments (path TEXT PRIMARY KEY, dialog INTEGER NOT NULL, mid INTEGER NOT NULL, size INTEGER NOT NULL, saved INTEGER NOT NULL)");
        database.execSQL("CREATE INDEX IF NOT EXISTS attachments_age ON attachments(saved, path)");
        database.execSQL("CREATE INDEX IF NOT EXISTS attachments_message ON attachments(dialog, mid)");

        try (Cursor cursor = database.rawQuery("SELECT COALESCE(SUM(size), 0), COUNT(*) FROM attachments", null)) {
            cursor.moveToFirst();
            used = cursor.getLong(0);
            count = cursor.getInt(1);
        }

        reconcileIndex();
        File[] files = directory.listFiles();

        if (files != null) {
            for (File entry : files) {
                String name = entry.getName();

                if (name.endsWith(".partial")) {
                    entry.delete();
                } else if (FILE_NAME.matcher(name).matches()) {
                    try (Cursor cursor = database.rawQuery("SELECT 1 FROM attachments WHERE path = ?", new String[] {name})) {
                        if (!cursor.moveToFirst()) {
                            entry.delete();
                        }
                    }
                }
            }
        }

        trim(0);
        initialized = true;
    }

    private void reconcileIndex() throws Exception {
        try (Cursor cursor = database.rawQuery("SELECT path, dialog, mid, size FROM attachments", null)) {
            while (cursor.moveToNext()) {
                File target = new File(directory, cursor.getString(0));

                if (!target.isFile()) {
                    removeRow(cursor);
                }
            }
        }
    }

    private void save(FileInputStream input, File destination, long size, long dialog, int mid) {
        try (FileInputStream retained = input) {
            if (!allowed(dialog) || size > FILE_LIMITS[fileLimitIndex()]) {
                return;
            }

            open();

            if (destination.exists() || size > TOTAL_LIMITS[totalLimitIndex()]) {
                return;
            }

            if (directory.getUsableSpace() < size + FREE_RESERVE) {
                recordFailure();
                return;
            }

            AttachmentCopy.write(retained, destination, size, FILE_LIMITS[fileLimitIndex()], () -> !allowed(dialog));
            ContentValues row = new ContentValues();
            row.put("path", destination.getName());
            row.put("dialog", dialog);
            row.put("mid", mid);
            row.put("size", size);
            row.put("saved", System.currentTimeMillis());

            try {
                database.insertOrThrow("attachments", null, row);
            } catch (Exception error) {
                destination.delete();
                throw error;
            }

            used += size;
            count++;

            try {
                trim(0);
            } catch (Exception error) {
                try (Cursor cursor = database.rawQuery("SELECT path, dialog, mid, size FROM attachments WHERE path = ?", new String[] {destination.getName()})) {
                    if (cursor.moveToFirst()) {
                        removeRow(cursor);
                    }
                }

                throw error;
            }

            changed(dialog, mid);
        } catch (Exception error) {
            if (allowed(dialog)) {
                recordFailure();
            }

            FileLog.e(error);
        } finally {
            pending.release();
        }
    }

    private void trim(long incoming) throws Exception {
        long limit = TOTAL_LIMITS[totalLimitIndex()];

        while (used > limit - incoming || count >= 20000) {
            try (Cursor cursor = database.rawQuery("SELECT path, dialog, mid, size FROM attachments ORDER BY saved, path LIMIT 1", null)) {
                if (!cursor.moveToFirst()) {
                    break;
                }

                removeRow(cursor);
            }
        }
    }

    private void removeRow(Cursor cursor) throws Exception {
        File target = new File(directory, cursor.getString(0));

        if (target.exists() && !target.delete()) {
            throw new IllegalStateException("Cannot remove archived attachment");
        }

        database.delete("attachments", "path = ?", new String[] {cursor.getString(0)});
        used -= cursor.getLong(3);
        count--;
        changed(cursor.getLong(1), cursor.getInt(2));
    }

    public void remove(long dialog, ArrayList<Integer> ids) {
        HashMap<Long, ArrayList<Integer>> messages = new HashMap<>();
        messages.put(dialog, ids);
        remove(messages, null);
    }

    void remove(Map<Long, ArrayList<Integer>> messages, Consumer<Boolean> completed) {
        HashMap<Long, ArrayList<Integer>> copy = new HashMap<>();

        for (Map.Entry<Long, ArrayList<Integer>> entry : messages.entrySet()) {
            copy.put(entry.getKey(), entry.getValue() == null
                    ? null : new ArrayList<>(entry.getValue()));
        }

        boolean posted = queue.postRunnable(() -> {
            boolean success = false;

            try {
                if (!active()) {
                    throw new IllegalStateException("Attachment archive is closed");
                }

                if (!directory.isDirectory()) {
                    success = true;
                } else {
                    open();

                    for (Map.Entry<Long, ArrayList<Integer>> entry : copy.entrySet()) {
                        long dialog = entry.getKey();
                        ArrayList<Integer> ids = entry.getValue();

                        if (ids == null) {
                            deleteWhere("dialog = ?", new String[] {Long.toString(dialog)});
                        } else {
                            for (int mid : ids) {
                                deleteWhere("dialog = ? AND mid = ?", new String[] {Long.toString(dialog), Integer.toString(mid)});
                            }
                        }
                    }

                    success = true;
                }
            } catch (Exception error) {
                FileLog.e(error);
            }

            if (completed != null) {
                completed.accept(success);
            }
        });

        if (!posted && completed != null) {
            completed.accept(false);
        }
    }

    private void changed(long dialog, int mid) {
        synchronized (changes) {
            changes.computeIfAbsent(dialog, key -> new HashSet<>()).add(mid);

            if (updatePosted) {
                return;
            }

            updatePosted = true;
        }

        AndroidUtilities.runOnUIThread(() -> {
            HashMap<Long, HashSet<Integer>> changed;

            synchronized (changes) {
                changed = new HashMap<>(changes);
                changes.clear();
                updatePosted = false;
            }

            if (active()) {
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.fluxgramAttachmentsChanged, changed);
            }
        }, 100);
    }

    public int failureCount() {
        return failures.get();
    }

    void loadStatistics(java.util.function.Consumer<Statistics> completed) {
        queue.postRunnable(() -> {
            Statistics result = null;

            try {
                if (active()) {
                    File index = new File(directory, "index.db");

                    if (!index.isFile()) {
                        result = new Statistics(0, 0, failures.get());
                    } else {
                        SQLiteDatabase statsDatabase = database;
                        boolean closeDatabase = false;

                        if (statsDatabase == null) {
                            statsDatabase = SQLiteDatabase.openDatabase(
                                    index.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY
                            );
                            closeDatabase = true;
                        }

                        try (Cursor cursor = statsDatabase.rawQuery(
                                "SELECT COUNT(*), COALESCE(SUM(size), 0) FROM attachments", null)) {
                            if (cursor.moveToFirst()) {
                                result = new Statistics(cursor.getLong(0), cursor.getLong(1), failures.get());
                            }
                        } finally {
                            if (closeDatabase) {
                                statsDatabase.close();
                            }
                        }
                    }
                }
            } catch (Exception error) {
                FileLog.e(error);
            }

            Statistics statistics = result;
            AndroidUtilities.runOnUIThread(() -> completed.accept(active() ? statistics : null));
        });
    }

    private void recordFailure() {
        failures.incrementAndGet();
        flushFailures();
    }

    private void flushFailures() {
        if (!failureFlushPending.compareAndSet(false, true)) {
            return;
        }

        queue.postRunnable(() -> {
            int value = failures.get();

            if (active()) {
                preferences.edit().putInt("failures", value).apply();
            }

            failureFlushPending.set(false);

            if (value != failures.get()) {
                flushFailures();
            }
        });
    }

    public void clearFiles(java.util.function.Consumer<Boolean> completed) {
        queue.postRunnable(() -> {
            try {
                if (directory.isDirectory()) {
                    open();
                    deleteWhere("1", new String[0]);
                }

                AndroidUtilities.runOnUIThread(() -> completed.accept(true));
            } catch (Exception error) {
                FileLog.e(error);
                AndroidUtilities.runOnUIThread(() -> completed.accept(false));
            }
        });
    }

    private void deleteWhere(String where, String[] args) throws Exception {
        while (true) {
            int removed = 0;

            try (Cursor cursor = database.rawQuery("SELECT path, dialog, mid, size FROM attachments WHERE " + where + " LIMIT 64", args)) {
                while (cursor.moveToNext()) {
                    removeRow(cursor);
                    removed++;
                }
            }

            if (removed == 0) {
                return;
            }
        }
    }

    public static synchronized void clearAccount(int account) {
        SavedAttachments archive = instances.remove(account);
        ApplicationLoader.applicationContext.getSharedPreferences("fluxgram_attachments_" + account, Context.MODE_PRIVATE)
                .edit().clear().apply();

        if (archive != null) {
            archive.closed = true;
        }

        File parent = new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "fluxgram_attachments");
        File[] directories = parent.listFiles(file -> file.getName().startsWith(account + "_"));
        queue.postRunnable(() -> {
            if (archive != null && archive.database != null) {
                archive.database.close();
                archive.database = null;
            }

            if (directories != null) {
                for (File directory : directories) {
                    File[] files = directory.listFiles();

                    if (files != null) {
                        for (File file : files) {
                            file.delete();
                        }
                    }

                    directory.delete();
                }
            }
        });
    }
}

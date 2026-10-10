package org.telegram.messenger.plugins;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import org.telegram.messenger.ApplicationLoader;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PluginPrivateFiles {
    static final int MAX_FILES = 512;
    static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    static final int MAX_TOTAL_FILE_BYTES = 32 * 1024 * 1024;

    private static final int MAGIC = 0x46474631;
    private static final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    private final String pluginId;
    private final File directory;
    private final Object lock;
    private boolean prepared;

    PluginPrivateFiles(String pluginId) {
        this.pluginId = pluginId;
        this.directory = new File(
                ApplicationLoader.applicationContext.getNoBackupFilesDir(),
                "fluxgram_plugins/files/" + sha256(pluginId)
        );
        this.lock = locks.computeIfAbsent(pluginId, ignored -> new Object());
    }

    static void clearPlugin(String pluginId) {
        PluginPrivateFiles files = new PluginPrivateFiles(pluginId);

        if (!files.directory.exists()) {
            return;
        }

        files.clearAll();
    }

    static void clearOwner(int account, long owner) {
        File root = new File(
                ApplicationLoader.applicationContext.getNoBackupFilesDir(),
                "fluxgram_plugins/files"
        );
        File[] directories = root.listFiles();

        if (directories == null) {
            if (root.exists()) {
                throw failure("STORAGE_FAILURE", "Cannot inspect plugin file storage");
            }

            return;
        }

        String scope = "account:" + account + ":" + owner;

        for (File directory : directories) {
            if (!directory.isDirectory()) {
                throw failure("STORAGE_FAILURE", "Invalid plugin file storage namespace");
            }

            File identity = new File(directory, "identity");
            String pluginId = new String(readRegular(identity, 512), StandardCharsets.UTF_8);
            PluginPrivateFiles files = new PluginPrivateFiles(pluginId);

            if (!files.directory.equals(directory)) {
                throw failure("STORAGE_FAILURE", "Plugin file storage identity mismatch");
            }

            files.clear(scope);
        }
    }

    boolean contains(String scope, String path) {
        synchronized (lock) {
            prepare();
            validate(scope, path);
            File file = target(scope, path);

            if (!file.exists()) {
                return false;
            }

            read(file, scope, path);
            return true;
        }
    }

    byte[] read(String scope, String path) {
        synchronized (lock) {
            prepare();
            validate(scope, path);
            File file = target(scope, path);

            if (!file.exists()) {
                return null;
            }

            return read(file, scope, path).data;
        }
    }

    void write(String scope, String path, byte[] data) {
        synchronized (lock) {
            prepare();
            validate(scope, path);

            if (data == null) {
                throw failure("INVALID_REQUEST", "Storage file data is missing");
            }

            if (data.length > MAX_FILE_BYTES) {
                throw failure("QUOTA_EXCEEDED", "Storage file exceeds the per-file limit");
            }

            File destination = target(scope, path);
            Statistics statistics = statistics();
            Entry previous = destination.exists() ? read(destination, scope, path) : null;
            long projectedBytes = statistics.bytes - (previous == null ? 0 : previous.data.length) + data.length;
            int projectedCount = statistics.count + (previous == null ? 1 : 0);

            if (projectedCount > MAX_FILES) {
                throw failure("QUOTA_EXCEEDED", "Storage file count limit exceeded");
            }

            if (projectedBytes > MAX_TOTAL_FILE_BYTES) {
                throw failure("QUOTA_EXCEEDED", "Storage file size limit exceeded");
            }

            File temporary = new File(directory, ".tmp-" + UUID.randomUUID());

            try {
                writeEntry(temporary, scope, path, data);
                Os.rename(temporary.getAbsolutePath(), destination.getAbsolutePath());

                try {
                    syncDirectory();
                } catch (RuntimeException ignored) {
                }
            } catch (RuntimeException error) {
                throw error;
            } catch (Exception error) {
                throw failure("STORAGE_FAILURE", error.toString());
            } finally {
                if (temporary.exists() && !temporary.delete()) {
                    temporary.deleteOnExit();
                }
            }
        }
    }

    boolean delete(String scope, String path) {
        synchronized (lock) {
            prepare();
            validate(scope, path);
            File file = target(scope, path);

            if (!file.exists()) {
                return false;
            }

            read(file, scope, path);

            if (!file.delete()) {
                throw failure("STORAGE_FAILURE", "Cannot delete storage file");
            }

            syncDirectory();
            return true;
        }
    }

    void clear(String scope) {
        synchronized (lock) {
            prepare();
            List<File> matches = new ArrayList<>();

            for (File file : entries()) {
                Entry entry = read(file, null, null);

                if (scope.equals(entry.scope)) {
                    matches.add(file);
                }
            }

            for (File file : matches) {
                if (!file.delete()) {
                    throw failure("STORAGE_FAILURE", "Cannot clear storage files");
                }
            }

            if (!matches.isEmpty()) {
                syncDirectory();
            }
        }
    }

    Statistics statistics() {
        synchronized (lock) {
            prepare();
            int count = 0;
            long bytes = 0;

            for (File file : entries()) {
                Entry entry = read(file, null, null);
                count++;
                bytes += entry.data.length;

                if (count > MAX_FILES || bytes > MAX_TOTAL_FILE_BYTES) {
                    throw failure("STORAGE_FAILURE", "Plugin file storage exceeds its limits");
                }
            }

            return new Statistics(count, bytes);
        }
    }

    private void clearAll() {
        synchronized (lock) {
            prepare();
            deleteTree(directory);
        }
    }

    private void prepare() {
        if (prepared) {
            return;
        }

        File parent = directory.getParentFile();

        if (parent == null || (!parent.exists() && !parent.mkdirs() && !parent.exists())) {
            throw failure("STORAGE_FAILURE", "Cannot create plugin files directory");
        }

        requireDirectory(parent);

        if (!directory.exists() && !directory.mkdir() && !directory.exists()) {
            throw failure("STORAGE_FAILURE", "Cannot create plugin file storage");
        }

        requireDirectory(directory);
        File identity = new File(directory, "identity");

        if (identity.exists()) {
            byte[] bytes = readRegular(identity, 512);

            if (!pluginId.equals(new String(bytes, StandardCharsets.UTF_8))) {
                throw failure("STORAGE_FAILURE", "Plugin file storage identity mismatch");
            }
        } else {
            File temporary = new File(directory, ".tmp-identity-" + UUID.randomUUID());

            try {
                writeRaw(temporary, pluginId.getBytes(StandardCharsets.UTF_8));
                Os.rename(temporary.getAbsolutePath(), identity.getAbsolutePath());
                syncDirectory();
            } catch (Exception error) {
                throw failure("STORAGE_FAILURE", error.toString());
            } finally {
                temporary.delete();
            }
        }

        cleanupTemporaryFiles();
        prepared = true;
    }

    private List<File> entries() {
        File[] files = directory.listFiles();

        if (files == null) {
            throw failure("STORAGE_FAILURE", "Cannot inspect plugin file storage");
        }

        List<File> result = new ArrayList<>();

        for (File file : files) {
            String name = file.getName();

            if ("identity".equals(name) || name.startsWith(".tmp-")) {
                continue;
            }

            if (!name.matches("[0-9a-f]{64}\\.fgf")) {
                throw failure("STORAGE_FAILURE", "Unexpected plugin file storage entry");
            }

            result.add(file);
        }

        return result;
    }

    private void cleanupTemporaryFiles() {
        File[] files = directory.listFiles();

        if (files == null) {
            throw failure("STORAGE_FAILURE", "Cannot inspect plugin file storage");
        }

        for (File file : files) {
            if (file.getName().startsWith(".tmp-") && !file.delete()) {
                throw failure("STORAGE_FAILURE", "Cannot recover plugin file storage");
            }
        }
    }

    private Entry read(File file, String expectedScope, String expectedPath) {
        byte[] encoded = readRegular(file, MAX_FILE_BYTES + 1024);

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC) {
                throw failure("STORAGE_FAILURE", "Invalid storage file header");
            }

            String scope = input.readUTF();
            String path = input.readUTF();
            int length = input.readInt();

            validate(scope, path);

            if (length < 0 || length > MAX_FILE_BYTES || encoded.length != headerSize(scope, path) + length) {
                throw failure("STORAGE_FAILURE", "Invalid storage file size");
            }

            byte[] data = new byte[length];
            input.readFully(data);

            if ((expectedScope != null && !expectedScope.equals(scope))
                    || (expectedPath != null && !expectedPath.equals(path))
                    || !file.equals(target(scope, path))) {
                throw failure("STORAGE_FAILURE", "Storage file identity mismatch");
            }

            return new Entry(scope, data);
        } catch (EOFException error) {
            throw failure("STORAGE_FAILURE", "Truncated storage file");
        } catch (IOException error) {
            throw failure("STORAGE_FAILURE", error.toString());
        }
    }

    private void writeEntry(File file, String scope, String path, byte[] data) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(file);
             DataOutputStream output = new DataOutputStream(stream)) {
            output.writeInt(MAGIC);
            output.writeUTF(scope);
            output.writeUTF(path);
            output.writeInt(data.length);
            output.write(data);
            output.flush();
            stream.getFD().sync();
        }
    }

    private void writeRaw(File file, byte[] data) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(data);
            output.flush();
            output.getFD().sync();
        }
    }

    private static byte[] readRegular(File file, int maximumBytes) {
        java.io.FileDescriptor descriptor = null;

        try {
            descriptor = Os.open(
                    file.getAbsolutePath(),
                    OsConstants.O_RDONLY | OsConstants.O_NOFOLLOW,
                    0
            );
            StructStat stat = Os.fstat(descriptor);

            if (!OsConstants.S_ISREG(stat.st_mode) || stat.st_size > maximumBytes) {
                throw failure("STORAGE_FAILURE", "Invalid plugin file storage entry");
            }

            try (FileInputStream input = new FileInputStream(descriptor);
                 ByteArrayOutputStream output = new ByteArrayOutputStream((int) stat.st_size)) {
                descriptor = null;
                byte[] buffer = new byte[8192];
                int count;

                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }

                return output.toByteArray();
            }
        } catch (ErrnoException | IOException error) {
            throw failure("STORAGE_FAILURE", error.toString());
        } finally {
            if (descriptor != null) {
                try {
                    Os.close(descriptor);
                } catch (ErrnoException ignored) {
                }
            }
        }
    }

    private void syncDirectory() {
        java.io.FileDescriptor descriptor = null;

        try {
            descriptor = Os.open(
                    directory.getAbsolutePath(),
                    OsConstants.O_RDONLY,
                    0
            );
            Os.fsync(descriptor);
        } catch (ErrnoException error) {
            throw failure("STORAGE_FAILURE", error.toString());
        } finally {
            if (descriptor != null) {
                try {
                    Os.close(descriptor);
                } catch (ErrnoException ignored) {
                }
            }
        }
    }

    private File target(String scope, String path) {
        return new File(directory, sha256(scope + "\u0000" + path) + ".fgf");
    }

    private static int headerSize(String scope, String path) {
        return 4 + 2 + scope.getBytes(StandardCharsets.UTF_8).length
                + 2 + path.getBytes(StandardCharsets.UTF_8).length + 4;
    }

    private static void validate(String scope, String path) {
        if (scope == null || (!"global".equals(scope) && !scope.matches("account:[0-9]+:[1-9][0-9]*"))) {
            throw failure("INVALID_REQUEST", "Invalid storage scope");
        }

        if (path == null || path.isEmpty() || path.getBytes(StandardCharsets.UTF_8).length > 240) {
            throw failure("INVALID_REQUEST", "Invalid storage file path");
        }

        String[] components = path.split("/", -1);

        if (components.length > 16) {
            throw failure("INVALID_REQUEST", "Invalid storage file path");
        }

        for (String component : components) {
            if (!component.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}")) {
                throw failure("INVALID_REQUEST", "Invalid storage file path");
            }
        }
    }

    private static void requireDirectory(File file) {
        try {
            StructStat stat = Os.lstat(file.getAbsolutePath());

            if (!OsConstants.S_ISDIR(stat.st_mode)) {
                throw failure("STORAGE_FAILURE", "Invalid plugin file storage directory");
            }
        } catch (ErrnoException error) {
            throw failure("STORAGE_FAILURE", error.toString());
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            StringBuilder result = new StringBuilder(digest.length * 2);

            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }

            return result.toString();
        } catch (Exception error) {
            throw failure("STORAGE_FAILURE", error.toString());
        }
    }

    private static RuntimeException failure(String code, String message) {
        return new IllegalStateException(code + ":" + message);
    }

    private static void deleteTree(File file) {
        try {
            StructStat stat = Os.lstat(file.getAbsolutePath());

            if (OsConstants.S_ISDIR(stat.st_mode)) {
                File[] children = file.listFiles();

                if (children == null) {
                    throw failure("STORAGE_FAILURE", "Cannot inspect plugin file storage");
                }

                for (File child : children) {
                    deleteTree(child);
                }
            }

            Os.remove(file.getAbsolutePath());
        } catch (ErrnoException error) {
            if (error.errno != OsConstants.ENOENT) {
                throw failure("STORAGE_FAILURE", error.toString());
            }
        }
    }

    static final class Statistics {
        final int count;
        final long bytes;

        Statistics(int count, long bytes) {
            this.count = count;
            this.bytes = bytes;
        }
    }

    private static final class Entry {
        final String scope;
        final byte[] data;

        Entry(String scope, byte[] data) {
            this.scope = scope;
            this.data = data;
        }
    }
}

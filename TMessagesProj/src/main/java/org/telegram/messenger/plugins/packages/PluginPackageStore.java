package org.telegram.messenger.plugins.packages;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PluginPackageStore {
    public static final String SOURCE_ARCHIVE = ".fluxgram-source.fgplugin";

    private final File root, cache;
    private Map<String, String> pendingErrors = Collections.emptyMap();

    public static final class Installed {
        public final PluginManifest manifest;
        public final File directory;

        Installed(PluginManifest manifest, File directory) {
            this.manifest = manifest;
            this.directory = directory;
        }
    }

    public PluginPackageStore(File root, File cache) throws IOException {
        this.root = root.getCanonicalFile();
        this.cache = cache.getCanonicalFile();
        mkdir(root);
        mkdir(cache);
    }

    public static final class Pending {
        public final String id;
        public final boolean delete, enableAfterUpdate, enabledBeforeUpdate, rollback, migrated;

        Pending(
                String id,
                boolean delete,
                boolean enableAfterUpdate,
                boolean enabledBeforeUpdate,
                boolean rollback,
                boolean migrated) {
            this.id = id;
            this.delete = delete;
            this.enableAfterUpdate = enableAfterUpdate;
            this.enabledBeforeUpdate = enabledBeforeUpdate;
            this.rollback = rollback;
            this.migrated = migrated;
        }
    }

    public synchronized Installed install(File reviewedArchive, String expectedHash)
            throws IOException {
        return install(reviewedArchive, expectedHash, null);
    }

    public synchronized Installed install(
            File reviewedArchive, String expectedHash, Runnable beforePublish)
            throws IOException {
        return prepare(reviewedArchive, expectedHash, null, null,
                false, false, false, beforePublish);
    }

    public synchronized void scheduleUpdate(
            File archive, String hash, String expectedId, String expectedVersion, boolean enable)
            throws IOException {
        scheduleUpdate(archive, hash, expectedId, expectedVersion, enable, false);
    }

    public synchronized void scheduleUpdate(
            File archive,
            String hash,
            String expectedId,
            String expectedVersion,
            boolean enable,
            boolean enabledBefore)
            throws IOException {
        prepare(archive, hash, expectedId, expectedVersion,
                enable, enabledBefore, false, null);
    }

    public synchronized void scheduleDebugReload(
            File archive,
            String hash,
            String expectedId,
            String expectedVersion,
            boolean enabledBefore)
            throws IOException {
        prepare(archive, hash, expectedId, expectedVersion,
                enabledBefore, enabledBefore, true, null);
    }

    private Installed prepare(
            File reviewedArchive,
            String expectedHash,
            String expectedId,
            String expectedVersion,
            boolean enable,
            boolean enabledBefore,
            boolean exactVersion,
            Runnable beforePublish)
            throws IOException {
        File frozen = File.createTempFile("fgplugin-install-", ".zip", cache);
        File staging = null;

        try {
            try (InputStream in = new FileInputStream(reviewedArchive);
                    FileOutputStream out = new FileOutputStream(frozen)) {
                copy(in, out, PluginPackageValidator.MAX_ARCHIVE_BYTES);
            }

            PluginPackageValidator.Result checked = PluginPackageValidator.inspect(frozen, cache);

            if (!checked.sha256.equals(expectedHash)) {
                throw new IOException("Archive changed after review");
            }

            File destination = new File(root, checked.manifest.id);

            String id = checked.manifest.id;
            requireNoPending(id);

            boolean update = expectedId != null;

            if (update) {
                if (!id.equals(expectedId)) {
                    throw new IOException("Plugin ID changed after review");
                }

                Installed previous = readInstalled(destination, id);

                if (!previous.manifest.version.equals(expectedVersion)) {
                    throw new IOException("Installed version changed after review");
                }

                int versionComparison = compareVersions(
                        checked.manifest.version, previous.manifest.version);

                if (exactVersion ? versionComparison != 0 : versionComparison <= 0) {
                    throw new IOException(
                            exactVersion
                                    ? "Debug reload must keep version " + previous.manifest.version
                                    : "Update must have a newer version than " + previous.manifest.version);
                }

                destination = new File(root, ".pending-" + id);
            } else if (destination.exists()) {
                throw new IOException("Plugin already installed; review it as an update");
            }

            staging = File.createTempFile(".install-", "", root);

            if (!staging.delete() || !staging.mkdir()) {
                throw new IOException("Cannot create staging directory");
            }

            File payload = update ? new File(staging, "package") : staging;
            mkdir(payload);

            if (update && enable) {
                createMarker(staging, "enable");
            }

            if (update && enabledBefore) {
                createMarker(staging, "enabled-before");
            }

            try (ZipFile zip = new ZipFile(frozen)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();

                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    File target = new File(payload, entry.getName());

                    if (!target.getCanonicalPath()
                            .startsWith(payload.getCanonicalPath() + File.separator)) {
                        throw new IOException("Invalid extraction path");
                    }

                    if (entry.isDirectory()) {
                        mkdir(target);
                    } else {
                        mkdir(target.getParentFile());

                        try (InputStream in = zip.getInputStream(entry);
                                FileOutputStream out = new FileOutputStream(target)) {
                            copy(in, out, PluginPackageValidator.MAX_FILE_BYTES);
                            out.getFD().sync();
                        }
                    }
                }
            }

            try (InputStream in = new FileInputStream(frozen);
                    FileOutputStream out = new FileOutputStream(new File(payload, SOURCE_ARCHIVE))) {
                copy(in, out, PluginPackageValidator.MAX_ARCHIVE_BYTES);
                out.getFD().sync();
            }

            if (beforePublish != null) {
                beforePublish.run();
            }

            if (!staging.renameTo(destination)) {
                throw new IOException("Cannot publish plugin installation");
            }

            staging = null;

            return new Installed(checked.manifest, destination);
        } finally {
            frozen.delete();

            if (staging != null) {
                deleteTree(staging);
            }
        }
    }

    public synchronized File export(String id, File destinationDirectory) throws IOException {
        validateId(id);
        Installed installed = readInstalled(new File(root, id), id);
        File source = new File(installed.directory, SOURCE_ARCHIVE);

        if (!source.isFile() || !source.getCanonicalFile().equals(source.getAbsoluteFile())) {
            throw new IOException("Source archive is unavailable. Update or reinstall this plugin from its original .fgplugin file.");
        }

        destinationDirectory = destinationDirectory.getCanonicalFile();
        mkdir(destinationDirectory);
        File folder = File.createTempFile("fgplugin-share-", "", destinationDirectory);

        if (!folder.delete() || !folder.mkdir()) {
            throw new IOException("Cannot prepare plugin for sharing");
        }

        File result = new File(folder, id + "-" + installed.manifest.version + ".fgplugin");

        try {
            try (InputStream in = new FileInputStream(source);
                    FileOutputStream out = new FileOutputStream(result)) {
                copy(in, out, PluginPackageValidator.MAX_ARCHIVE_BYTES);
            }

            PluginPackageValidator.Result checked =
                    PluginPackageValidator.inspectInstalledArchive(result, cache);

            if (!checked.manifest.id.equals(id)
                    || !checked.manifest.version.equals(installed.manifest.version)) {
                throw new IOException("Source archive does not match the installed plugin");
            }

            return result;
        } catch (IOException error) {
            deleteTree(folder);
            throw error;
        }
    }

    public synchronized ArrayList<Installed> list() throws IOException {
        ArrayList<Installed> result = new ArrayList<>();
        File[] folders = root.listFiles();

        if (folders == null) {
            throw new IOException("Cannot read installed plugins");
        }

        for (File folder : folders) {
            if (folder.getName().startsWith(".install-")) {
                deleteTree(folder);
                continue;
            }

            if (folder.getName().startsWith(".") || !folder.isDirectory()) {
                continue;
            }

            try {
                result.add(readInstalled(folder, folder.getName()));
            } catch (IOException invalidInstallation) {
                System.err.println("FluxGram: skipping invalid installation " + folder.getName());
            }
        }

        return result;
    }

    public synchronized Installed get(String id) throws IOException {
        validateId(id);
        return readInstalled(new File(root, id), id);
    }

    private Installed readInstalled(File folder, String expectedId) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();

        try (InputStream in = new FileInputStream(new File(folder, "manifest.json"))) {
            copy(in, data, PluginPackageValidator.MAX_MANIFEST_BYTES);
        }

        PluginManifest manifest =
                PluginManifest.parse(new String(data.toByteArray(), StandardCharsets.UTF_8));

        boolean valid = manifest.id.equals(expectedId);

        if (manifest.hasPythonModule()) {
            valid &= new File(folder, manifest.entryFile()).isFile();
        }

        if (manifest.hasJavaModule()) {
            valid &= new File(folder, manifest.javaModule.path).isFile();
        }

        if (!valid) {
            throw new IOException("Invalid installed plugin " + expectedId);
        }

        return new Installed(manifest, folder);
    }

    private static void validateId(String id) throws IOException {
        if (id == null
                || id.length() > 100
                || id.startsWith("fluxgram.")
                || !id.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")) {
            throw new IOException("Invalid external plugin ID");
        }
    }

    private void requireNoPending(String id) throws IOException {
        validateId(id);

        if (new File(root, ".pending-" + id).exists() || new File(root, ".backup-" + id).exists()) {
            throw new IOException("Restart FluxGram to finish the pending operation first");
        }
    }

    public static int compareVersions(String left, String right) {
        String[] a = left.split("\\."), b = right.split("\\.");

        for (int i = 0; i < 3; i++) {
            int comparison = new BigInteger(a[i]).compareTo(new BigInteger(b[i]));

            if (comparison != 0) {
                return comparison;
            }
        }

        return 0;
    }

    public synchronized void remove(String id) throws IOException {
        requireNoPending(id);
        File current = new File(root, id);
        if (!current.exists()) {
            throw new IOException("Plugin is not installed");
        }

        File removed = new File(root, ".install-deleted-" + java.util.UUID.randomUUID());

        if (!current.renameTo(removed)) {
            throw new IOException("Cannot remove plugin");
        }

        try {
            deleteTree(removed);
        } catch (IOException error) {
            System.err.println("FluxGram: deferred removed plugin file cleanup: " + id);
        }
    }

    public synchronized void scheduleDelete(String id) throws IOException {
        requireNoPending(id);

        if (!new File(root, id).isDirectory()) {
            throw new IOException("Plugin is not installed");
        }

        File staging = File.createTempFile(".install-", "", root);

        try {
            if (!staging.delete() || !staging.mkdir()) {
                throw new IOException("Cannot create deletion request");
            }

            try (FileOutputStream out = new FileOutputStream(new File(staging, "delete"))) {
                out.getFD().sync();
            }

            if (!staging.renameTo(new File(root, ".pending-" + id))) {
                throw new IOException("Cannot publish deletion request");
            }
        } finally {
            deleteTree(staging);
        }
    }

    public synchronized Map<String, Pending> pending() throws IOException {
        Map<String, Pending> result = new LinkedHashMap<>();
        Map<String, String> errors = new LinkedHashMap<>();
        File[] files = root.listFiles();

        if (files == null) {
            throw new IOException("Cannot read pending plugin operations");
        }

        for (File file : files) {
            if (!file.getName().startsWith(".pending-")) {
                continue;
            }

            String id = file.getName().substring(".pending-".length());

            try {
                validateId(id);

                if (!file.isDirectory()) {
                    throw new IOException("Invalid pending operation");
                }

                result.put(
                        id,
                        new Pending(
                                id,
                                new File(file, "delete").isFile(),
                                new File(file, "enable").isFile(),
                                new File(file, "enabled-before").isFile(),
                                new File(file, "rollback").isFile(),
                                new File(file, "migrated").isFile()));
            } catch (IOException error) {
                errors.put(id, error.getMessage());
            }
        }

        pendingErrors = Collections.unmodifiableMap(errors);

        return result;
    }

    public synchronized Map<String, String> pendingErrors() {
        return pendingErrors;
    }

    public synchronized void applyPending() throws IOException {
        Map<String, Pending> operations = pending();
        Map<String, String> errors = new LinkedHashMap<>(pendingErrors);

        for (Pending operation : operations.values()) {
            try {
                if (operation.delete) {
                    applyPendingDelete(operation.id);
                } else if (operation.rollback) {
                    rollbackPending(operation.id);
                    finishPendingRollback(operation.id);
                } else {
                    activatePendingUpdate(operation.id);
                    markPendingMigrated(operation.id);
                    finishPendingUpdate(operation.id);
                }
            } catch (IOException error) {
                errors.put(operation.id, error.getMessage());
            }
        }

        pendingErrors = Collections.unmodifiableMap(errors);
    }

    public synchronized Installed activatePendingUpdate(String id) throws IOException {
        Pending operation = requirePendingUpdate(id);

        if (operation.rollback) {
            throw new IOException("Plugin update is rolling back");
        }

        File journal = new File(root, ".pending-" + id);
        File current = new File(root, id);
        File backup = new File(root, ".backup-" + id);
        File replacement = new File(journal, "package");

        if (replacement.exists()) {
            readInstalled(replacement, id);

            if (current.exists()) {
                if (backup.exists() || !current.renameTo(backup)) {
                    throw new IOException("Cannot back up plugin");
                }
            } else if (!backup.exists()) {
                throw new IOException("Missing plugin update backup");
            }

            if (!replacement.renameTo(current)) {
                throw new IOException("Cannot activate plugin update");
            }
        }

        if (!backup.exists()) {
            throw new IOException("Missing plugin update backup");
        }

        return readInstalled(current, id);
    }

    public synchronized void rollbackPending(String id) throws IOException {
        Pending operation = requirePendingUpdate(id);
        File journal = new File(root, ".pending-" + id);

        if (!operation.rollback) {
            createMarker(journal, "rollback");
        }

        File current = new File(root, id);
        File backup = new File(root, ".backup-" + id);

        if (!backup.exists()) {
            if (current.exists()) {
                return;
            }

            throw new IOException("Missing plugin update rollback backup");
        }

        File failed = new File(root, ".install-failed-" + java.util.UUID.randomUUID());

        if (current.exists() && !current.renameTo(failed)) {
            throw new IOException("Cannot isolate failed plugin update");
        }

        if (!backup.renameTo(current)) {
            if (failed.exists()) {
                failed.renameTo(current);
            }

            throw new IOException("Cannot restore plugin update backup");
        }

        deleteTree(failed);
    }

    public synchronized void cancelPendingUpdate(String id) throws IOException {
        Pending operation = requirePendingUpdate(id);
        File journal = new File(root, ".pending-" + id);
        File backup = new File(root, ".backup-" + id);

        if (operation.rollback || operation.migrated
                || !new File(journal, "package").isDirectory() || backup.exists()) {
            throw new IOException("Plugin update has already been activated");
        }

        finishJournal(id);
    }

    public synchronized void finishPendingRollback(String id) throws IOException {
        Pending operation = requirePendingUpdate(id);

        if (!operation.rollback) {
            throw new IOException("Plugin update is not rolling back");
        }

        File current = new File(root, id);
        File backup = new File(root, ".backup-" + id);
        readInstalled(current, id);

        if (backup.exists()) {
            throw new IOException("Plugin update rollback is incomplete");
        }

        finishJournal(id);
    }

    public synchronized void markPendingMigrated(String id) throws IOException {
        Pending operation = requirePendingUpdate(id);
        File journal = new File(root, ".pending-" + id);
        File current = new File(root, id);
        File backup = new File(root, ".backup-" + id);

        if (operation.rollback
                || new File(journal, "package").exists()
                || !backup.exists()) {
            throw new IOException("Plugin update is not active");
        }

        readInstalled(current, id);

        if (!new File(journal, "migrated").isFile()) {
            createMarker(journal, "migrated");
        }
    }

    public synchronized void finishPendingUpdate(String id) throws IOException {
        Pending operation = requirePendingUpdate(id);
        File journal = new File(root, ".pending-" + id);
        File current = new File(root, id);
        File backup = new File(root, ".backup-" + id);
        readInstalled(current, id);

        if (operation.rollback || !new File(journal, "migrated").isFile()) {
            throw new IOException("Plugin update migration is incomplete");
        }

        deleteTree(backup);
        finishJournal(id);
    }

    public synchronized void applyPendingDelete(String id) throws IOException {
        Pending operation = pending().get(id);

        if (operation == null || !operation.delete) {
            throw new IOException("Plugin has no pending deletion");
        }

        File current = new File(root, id);
        File backup = new File(root, ".backup-" + id);

        if (current.exists() && !current.renameTo(backup)) {
            throw new IOException("Cannot remove plugin");
        }

        deleteTree(backup);
        finishJournal(id);
    }

    private Pending requirePendingUpdate(String id) throws IOException {
        Pending operation = pending().get(id);

        if (operation == null || operation.delete) {
            throw new IOException("Plugin has no pending update");
        }

        return operation;
    }

    private void finishJournal(String id) throws IOException {
        File journal = new File(root, ".pending-" + id);
        File finished = new File(root, ".install-done-" + java.util.UUID.randomUUID());

        if (!journal.renameTo(finished)) {
            throw new IOException("Cannot finish plugin operation");
        }

        deleteTree(finished);
    }

    private static void createMarker(File directory, String name) throws IOException {
        try (FileOutputStream out = new FileOutputStream(new File(directory, name))) {
            out.getFD().sync();
        }
    }

    public static void copy(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int count;

        while ((count = in.read(buffer)) != -1) {
            if ((total += count) > limit) {
                throw new IOException("File exceeds size limit");
            }

            out.write(buffer, 0, count);
        }
    }

    private static void mkdir(File folder) throws IOException {
        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("Cannot create plugin directory");
        }
    }

    private static void deleteTree(File file) throws IOException {
        if (file.getCanonicalFile().equals(file.getAbsoluteFile()) && file.isDirectory()) {
            File[] children = file.listFiles();

            if (children == null) {
                throw new IOException("Cannot list plugin directory for removal");
            }

            for (File child : children) {
                deleteTree(child);
            }
        }

        if (!file.delete() && file.exists()) {
            throw new IOException("Cannot remove plugin file");
        }
    }
}

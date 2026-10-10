package org.telegram.messenger.plugins.packages;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PluginPackageStore {
    public static final String SOURCE_ARCHIVE = ".fluxgram-source.fgplugin";

    private final File root, cache;

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
        public final boolean delete, enableAfterUpdate;

        Pending(String id, boolean delete, boolean enableAfterUpdate) {
            this.id = id;
            this.delete = delete;
            this.enableAfterUpdate = enableAfterUpdate;
        }
    }

    public synchronized Installed install(File reviewedArchive, String expectedHash)
            throws IOException {
        return prepare(reviewedArchive, expectedHash, null, null, false);
    }

    public synchronized void scheduleUpdate(
            File archive, String hash, String expectedId, String expectedVersion, boolean enable)
            throws IOException {
        prepare(archive, hash, expectedId, expectedVersion, enable);
    }

    private Installed prepare(
            File reviewedArchive,
            String expectedHash,
            String expectedId,
            String expectedVersion,
            boolean enable)
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

                if (compareVersions(checked.manifest.version, previous.manifest.version) <= 0) {
                    throw new IOException(
                            "Update must have a newer version than " + previous.manifest.version);
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
                try (FileOutputStream out = new FileOutputStream(new File(staging, "enable"))) {
                    out.getFD().sync();
                }
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

    private Installed readInstalled(File folder, String expectedId) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();

        try (InputStream in = new FileInputStream(new File(folder, "manifest.json"))) {
            copy(in, data, PluginPackageValidator.MAX_MANIFEST_BYTES);
        }

        PluginManifest manifest =
                PluginManifest.parse(new String(data.toByteArray(), StandardCharsets.UTF_8));

        if (!manifest.id.equals(expectedId) || !new File(folder, manifest.entryFile()).isFile()) {
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
        File[] files = root.listFiles();

        if (files == null) {
            throw new IOException("Cannot read pending plugin operations");
        }

        for (File file : files) {
            if (!file.getName().startsWith(".pending-")) {
                continue;
            }

            String id = file.getName().substring(".pending-".length());
            validateId(id);

            if (!file.isDirectory()) {
                throw new IOException("Invalid pending operation");
            }

            result.put(
                    id,
                    new Pending(
                            id,
                            new File(file, "delete").isFile(),
                            new File(file, "enable").isFile()));
        }

        return result;
    }

    public synchronized void applyPending() throws IOException {
        for (Pending operation : pending().values()) {
            File journal = new File(root, ".pending-" + operation.id);
            File current = new File(root, operation.id);
            File backup = new File(root, ".backup-" + operation.id);
            File replacement = new File(journal, "package");

            if (operation.delete) {
                if (current.exists() && !current.renameTo(backup)) {
                    throw new IOException("Cannot remove plugin");
                }
            } else if (replacement.exists()) {
                readInstalled(replacement, operation.id);

                if (current.exists() && !current.renameTo(backup)) {
                    throw new IOException("Cannot back up plugin");
                }

                if (!replacement.renameTo(current)) {
                    if (backup.exists()) {
                        backup.renameTo(current);
                    }

                    throw new IOException("Cannot activate plugin update");
                }
            } else {
                readInstalled(current, operation.id);
            }

            deleteTree(backup);

            File finished = new File(root, ".install-done-" + java.util.UUID.randomUUID());

            if (!journal.renameTo(finished)) {
                throw new IOException("Cannot finish plugin operation");
            }

            deleteTree(finished);
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

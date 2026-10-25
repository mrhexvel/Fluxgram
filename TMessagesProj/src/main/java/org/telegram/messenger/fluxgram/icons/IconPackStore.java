package org.telegram.messenger.fluxgram.icons;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class IconPackStore {
    public static final String SOURCE_ARCHIVE = ".fluxgram-source.icons";
    public static final String IDENTITY_FILE = ".fluxgram-identity";
    public static final String INDEX_FILE = ".fluxgram-index";
    public static final int MAX_PACKS = 32;
    public static final long MAX_STORAGE_BYTES = 256L * 1024 * 1024;

    private static final int INDEX_MAGIC = 0x46474958;
    private static final int INDEX_VERSION = 1;
    private static final String REJECTED_FILE = ".fluxgram-rejected";
    private static final int REJECTED_MAGIC = 0x4647524a;
    private static final int REJECTED_VERSION = 1;
    private static final int MAX_REJECTED_BYTES = 256;
    private static final int MAX_INDEX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_INDEX_MANIFEST_BYTES = 128 * 1024;
    private static final int MAX_INDEX_PATH_BYTES = 720;

    private static final Object STORAGE_LOCK = new Object();
    private static final HashMap<String, String> ACTIVE_GENERATIONS = new HashMap<>();

    public static final class Installed {
        public final IconPackManifest manifest;
        private final File directory;
        private final String storeRoot;
        private final String archiveSha256;
        private final String generation;
        private final Map<String, String> iconHashes;

        private Installed(
                IconPackManifest manifest,
                File directory,
                String storeRoot,
                String archiveSha256,
                String generation,
                Map<String, String> iconHashes) {
            this.manifest = manifest;
            this.directory = directory;
            this.storeRoot = storeRoot;
            this.archiveSha256 = archiveSha256;
            this.generation = generation;
            this.iconHashes = iconHashes;
        }

        String generation() {
            return generation;
        }

        String iconHash(String resourceName) {
            String path = manifest.icons.get(resourceName);

            return path == null ? null : iconHashes.get(path);
        }
    }

    private final File root;
    private final File cache;
    private final IconPackArchiveValidator.Catalog catalog;

    public IconPackStore(
            File root,
            File cache,
            IconPackArchiveValidator.Catalog catalog) throws IOException {
        this.root = root.getCanonicalFile();
        this.cache = cache.getCanonicalFile();
        this.catalog = catalog;

        synchronized (STORAGE_LOCK) {
            mkdir(this.root);
            mkdir(this.cache);
            recover();
        }
    }

    public Installed install(File archive, String expectedHash) throws IOException {
        synchronized (STORAGE_LOCK) {
            return installLocked(archive, expectedHash);
        }
    }

    private Installed installLocked(File archive, String expectedHash) throws IOException {
        IconPackArchiveValidator.Snapshot reviewed = null;
        File staging = null;
        File backup = null;
        File destination = null;

        try {
            recover();
            reviewed = IconPackArchiveValidator.snapshot(archive, cache, catalog);

            IconPackArchiveValidator.Result checked = reviewed.result;
            File frozen = reviewed.archive();
            String generation = UUID.randomUUID().toString();

            IconPackManifest.require(
                    checked.sha256.equals(expectedHash),
                    "Archive changed after review");

            destination = child(storageName(checked.manifest.id));

            staging = File.createTempFile(".install-", "", root);

            if (!staging.delete() || !staging.mkdir()) {
                throw new IOException("Cannot create icon pack staging directory");
            }

            extract(frozen, staging);

            try (InputStream input = new FileInputStream(frozen);
                    FileOutputStream output = new FileOutputStream(new File(staging, SOURCE_ARCHIVE))) {
                IconPackArchiveValidator.copy(
                        input,
                        output,
                        IconPackArchiveValidator.MAX_ARCHIVE_BYTES);
                output.getFD().sync();
            }

            verifyPayload(staging, checked.iconHashes);
            writeInstallationMetadata(staging, checked, generation);
            checkStorageLimit(destination, staging);

            reviewed.close();
            reviewed = null;

            if (destination.exists()) {
                backup = child(".backup-" + storageName(checked.manifest.id));
                deleteTree(backup);

                if (!destination.renameTo(backup)) {
                    throw new IOException("Cannot prepare icon pack update");
                }

                ACTIVE_GENERATIONS.remove(destination.getCanonicalPath());
            }

            if (!staging.renameTo(destination)) {
                if (backup != null && backup.exists()) {
                    if (!backup.renameTo(destination)) {
                        throw new IOException("Cannot publish or restore icon pack");
                    }

                    readInstalled(destination, checked.manifest.id, true);
                }

                throw new IOException("Cannot publish icon pack");
            }

            staging = null;
            ACTIVE_GENERATIONS.put(destination.getCanonicalPath(), generation);

            if (backup != null) {
                File obsolete = backup;
                backup = null;

                try {
                    deleteTree(obsolete);
                } catch (IOException ignored) {
                }
            }

            return new Installed(
                    checked.manifest,
                    destination,
                    root.getPath(),
                    checked.sha256,
                    generation,
                    checked.iconHashes);
        } finally {
            try {
                if (staging != null) {
                    deleteTree(staging);
                }

                if (backup != null && backup.exists() && destination != null) {
                    if (!destination.exists()) {
                        if (backup.renameTo(destination)) {
                            readInstalled(destination, null, true);
                        }
                    }
                }
            } finally {
                if (reviewed != null) {
                    reviewed.close();
                }
            }
        }
    }

    public ArrayList<Installed> list() throws IOException {
        synchronized (STORAGE_LOCK) {
            return listLocked();
        }
    }

    private ArrayList<Installed> listLocked() throws IOException {
        recover();

        File[] files = root.listFiles();

        if (files == null) {
            throw new IOException("Cannot read installed icon packs");
        }

        ArrayList<Installed> result = new ArrayList<>();

        for (File file : files) {
            if (!file.isDirectory() || !file.getName().startsWith("pack-")) {
                continue;
            }

            try {
                result.add(readInstalled(file, null, true));
            } catch (IOException ignored) {
            }
        }

        Collections.sort(result, Comparator.comparing(pack -> pack.manifest.id));

        return result;
    }

    public Installed get(String id) throws IOException {
        synchronized (STORAGE_LOCK) {
            return getLocked(id);
        }
    }

    private Installed getLocked(String id) throws IOException {
        validateId(id);
        recover();

        return readInstalled(child(storageName(id)), id, true);
    }

    public File export(String id, File destination) throws IOException {
        synchronized (STORAGE_LOCK) {
            return exportLocked(id, destination);
        }
    }

    private File exportLocked(String id, File destination) throws IOException {
        Installed installed = getLocked(id);
        File source = new File(installed.directory, SOURCE_ARCHIVE);
        File canonicalDestination = destination.getCanonicalFile();
        File parent = canonicalDestination.getParentFile();
        File requestedParent = destination.getAbsoluteFile().getParentFile();
        File normalizedDestination = requestedParent == null
                ? null
                : new File(requestedParent.getCanonicalFile(), destination.getName());

        if (parent == null
                || normalizedDestination == null
                || canonicalDestination.isDirectory()
                || isWithin(canonicalDestination, root)
                || isWithin(canonicalDestination, cache)
                || (destination.exists()
                        && !canonicalDestination.equals(normalizedDestination))) {
            throw new IOException("Invalid export destination");
        }

        mkdir(parent);
        File temporary = File.createTempFile(".icon-pack-export-", ".icons", parent);

        try {
            try (InputStream input = new FileInputStream(source);
                    FileOutputStream output = new FileOutputStream(temporary)) {
                IconPackArchiveValidator.copy(
                        input,
                        output,
                        IconPackArchiveValidator.MAX_ARCHIVE_BYTES);
                output.getFD().sync();
            }

            IconPackArchiveValidator.Result checked =
                    IconPackArchiveValidator.inspect(temporary, cache, catalog);

            if (!checked.manifest.id.equals(id)
                    || !checked.sha256.equals(installed.archiveSha256)) {
                throw new IOException("Exported icon pack does not match installation");
            }

            if (!temporary.renameTo(canonicalDestination)) {
                throw new IOException("Cannot publish icon pack export");
            }

            temporary = null;

            return canonicalDestination;
        } finally {
            if (temporary != null) {
                temporary.delete();
            }
        }
    }

    public void delete(String id) throws IOException {
        synchronized (STORAGE_LOCK) {
            deleteLocked(id);
        }
    }

    private void deleteLocked(String id) throws IOException {
        validateId(id);
        recover();

        String storageName = storageName(id);
        File destination = child(storageName);
        File backup = child(".backup-" + storageName);

        if (!destination.exists()) {
            ACTIVE_GENERATIONS.remove(destination.getCanonicalPath());
            deleteTree(backup);
            return;
        }

        File deleting = child(".delete-" + storageName);
        deleteTree(deleting);

        if (!destination.renameTo(deleting)) {
            throw new IOException("Cannot prepare icon pack deletion");
        }

        deleteTree(deleting);
        deleteTree(backup);
        ACTIVE_GENERATIONS.remove(destination.getCanonicalPath());
    }

    public byte[] resolve(Installed installed, String resourceName) throws IOException {
        synchronized (STORAGE_LOCK) {
            return resolveLocked(installed, resourceName);
        }
    }

    private byte[] resolveLocked(Installed installed, String resourceName) throws IOException {
        if (installed == null || !IconPackManifest.isValidResourceName(resourceName)) {
            return null;
        }

        String relative = installed.manifest.icons.get(resourceName);

        if (relative == null) {
            return null;
        }

        File rootDirectory = installed.directory.getCanonicalFile();

        if (!root.getPath().equals(installed.storeRoot)
                || !root.equals(rootDirectory.getParentFile())
                || !installed.generation.equals(ACTIVE_GENERATIONS.get(rootDirectory.getPath()))) {
            return null;
        }

        File icon = iconFile(rootDirectory, relative);

        if (icon == null) {
            return null;
        }

        byte[] bytes = readBytes(icon, IconPackArchiveValidator.MAX_ICON_BYTES);
        String expectedHash = installed.iconHashes.get(relative);

        if (expectedHash == null || !expectedHash.equals(hash(bytes))) {
            return null;
        }

        return bytes;
    }

    private Installed readInstalled(File directory, String expectedId, boolean verifyStorageName)
            throws IOException {
        try {
            if (!directory.isDirectory()
                    || !directory.getCanonicalFile().equals(directory.getAbsoluteFile())) {
                throw new IOException("Invalid icon pack installation");
            }

            Identity identity = readIdentity(directory);

            if (identity.indexSha256 != null) {
                try {
                    return readIndexedInstallation(directory, expectedId, verifyStorageName, identity);
                } catch (IOException ignored) {
                }
            }

            if (isRejectedInstallation(directory, identity)) {
                throw new IconPackArchiveValidator.InvalidArchiveException(
                        "Icon pack failed the current validation contract");
            }

            return readFullyValidatedInstallation(directory, expectedId, verifyStorageName, identity);
        } catch (IOException error) {
            try {
                ACTIVE_GENERATIONS.remove(directory.getCanonicalPath());
            } catch (IOException ignored) {
            }

            throw error;
        }
    }

    private Installed readIndexedInstallation(
            File directory,
            String expectedId,
            boolean verifyStorageName,
            Identity identity) throws IOException {
        File indexFile = internalFile(directory, INDEX_FILE);
        byte[] indexBytes = readBytes(indexFile, MAX_INDEX_BYTES);

        IconPackManifest.require(
                identity.indexSha256.equals(hash(indexBytes)),
                "Icon pack index does not match identity");

        InstallationIndex index = decodeIndex(indexBytes);

        IconPackManifest.require(
                index.validationVersion == catalog.validationVersion(),
                "Icon pack validation version changed");
        IconPackManifest.require(
                index.archiveSha256.equals(identity.sha256)
                        && index.generation.equals(identity.generation),
                "Icon pack index does not match identity");

        File source = internalFile(directory, SOURCE_ARCHIVE);

        IconPackManifest.require(
                source.length() == index.archiveBytes
                        && source.length() <= IconPackArchiveValidator.MAX_ARCHIVE_BYTES
                        && index.archiveSha256.equals(
                                hash(source, IconPackArchiveValidator.MAX_ARCHIVE_BYTES)),
                "Icon pack source archive changed");
        validateInstallationIdentity(directory, expectedId, verifyStorageName, index.manifest);
        validateIndexedPaths(index.manifest, index.iconHashes);
        verifyPayload(directory, index.iconHashes);

        Installed installed = new Installed(
                index.manifest,
                directory,
                root.getPath(),
                index.archiveSha256,
                index.generation,
                index.iconHashes);

        ACTIVE_GENERATIONS.put(directory.getCanonicalPath(), index.generation);

        return installed;
    }

    private Installed readFullyValidatedInstallation(
            File directory,
            String expectedId,
            boolean verifyStorageName,
            Identity identity) throws IOException {
        File source = internalFile(directory, SOURCE_ARCHIVE);
        IconPackArchiveValidator.Result checked;

        try {
            checked = IconPackArchiveValidator.inspect(source, cache, catalog);
        } catch (IconPackArchiveValidator.InvalidArchiveException error) {
            rememberRejectedInstallation(directory, identity, source);
            throw error;
        }

        validateInstallationIdentity(directory, expectedId, verifyStorageName, checked.manifest);

        IconPackManifest.require(
                checked.sha256.equals(identity.sha256),
                "Icon pack identity does not match source archive");

        Installed installed = new Installed(
                checked.manifest,
                directory,
                root.getPath(),
                checked.sha256,
                identity.generation,
                checked.iconHashes);

        verifyPayload(directory, checked.iconHashes);
        ACTIVE_GENERATIONS.put(directory.getCanonicalPath(), identity.generation);
        repairInstallationMetadata(directory, checked, identity.generation);

        return installed;
    }

    private void validateInstallationIdentity(
            File directory,
            String expectedId,
            boolean verifyStorageName,
            IconPackManifest manifest) throws IOException {
        IconPackManifest.require(
                expectedId == null || manifest.id.equals(expectedId),
                "Icon pack directory does not match manifest");
        IconPackManifest.require(
                !verifyStorageName || directory.getName().equals(storageName(manifest.id)),
                "Icon pack storage key does not match manifest");
    }

    private static void validateIndexedPaths(
            IconPackManifest manifest,
            Map<String, String> indexedHashes) throws IOException {
        Set<String> manifestPaths = new HashSet<>(manifest.icons.values());

        IconPackManifest.require(
                !indexedHashes.isEmpty() && manifestPaths.containsAll(indexedHashes.keySet()),
                "Icon pack index does not match manifest");
    }

    private void extract(File archive, File destination) throws IOException {
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String path = IconPackArchiveValidator.validateArchivePath(
                        entry.getName(),
                        entry.isDirectory());
                File target = new File(destination, path).getCanonicalFile();

                IconPackManifest.require(
                        target.getPath().startsWith(destination.getCanonicalPath() + File.separator),
                        "Invalid extraction path");

                if (entry.isDirectory()) {
                    mkdir(target);
                } else {
                    mkdir(target.getParentFile());

                    try (InputStream input = zip.getInputStream(entry);
                            FileOutputStream output = new FileOutputStream(target)) {
                        IconPackArchiveValidator.copy(
                                input,
                                output,
                                IconPackArchiveValidator.MAX_FILE_BYTES);
                        output.getFD().sync();
                    }
                }
            }
        }
    }

    private void recover() throws IOException {
        File[] files = root.listFiles();

        if (files == null) {
            throw new IOException("Cannot inspect icon pack storage");
        }

        for (File file : files) {
            String name = file.getName();

            if (name.startsWith(".install-") || name.startsWith(".delete-")) {
                deleteTree(file);
            } else if (name.startsWith(".backup-")) {
                String storageKey = name.substring(".backup-".length());

                if (!storageKey.matches("pack-[0-9a-f]{64}")) {
                    deleteTree(file);
                    continue;
                }

                File destination = child(storageKey);
                boolean destinationValid = isValidInstallation(destination, null, true);

                if (destinationValid) {
                    ACTIVE_GENERATIONS.remove(file.getCanonicalPath());
                    deleteTree(file);
                } else if (!destination.exists() && isValidInstallation(file, null, false)) {
                    Installed backupPack = readInstalled(file, null, false);

                    IconPackManifest.require(
                            storageKey.equals(storageName(backupPack.manifest.id)),
                            "Icon pack backup key does not match manifest");
                    deleteTree(destination);

                    if (!file.renameTo(destination)) {
                        throw new IOException("Cannot restore interrupted icon pack update");
                    }

                    ACTIVE_GENERATIONS.remove(file.getCanonicalPath());
                    readInstalled(destination, backupPack.manifest.id, true);
                } else {
                    ACTIVE_GENERATIONS.remove(file.getCanonicalPath());
                    ACTIVE_GENERATIONS.remove(destination.getCanonicalPath());
                }
            }
        }
    }

    private boolean isValidInstallation(File directory, String id, boolean verifyStorageName) {
        try {
            readInstalled(directory, id, verifyStorageName);
            return true;
        } catch (IOException ignored) {
            try {
                ACTIVE_GENERATIONS.remove(directory.getCanonicalPath());
            } catch (IOException ignoredPath) {
            }

            return false;
        }
    }

    private static void verifyPayload(File directory, Map<String, String> hashes)
            throws IOException {
        File canonicalDirectory = directory.getCanonicalFile();
        long total = 0;

        for (Map.Entry<String, String> expected : hashes.entrySet()) {
            File icon = iconFile(canonicalDirectory, expected.getKey());

            IconPackManifest.require(icon != null, "Installed icon file is missing");
            total += icon.length();
            IconPackManifest.require(
                    total <= IconPackArchiveValidator.MAX_UNPACKED_BYTES,
                    "Installed icon files exceed size limit");
            IconPackManifest.require(
                    expected.getValue().equals(
                            hash(icon, IconPackArchiveValidator.MAX_ICON_BYTES)),
                    "Installed icon file does not match source archive");
        }
    }

    private static File iconFile(File directory, String relative) throws IOException {
        File requested = new File(directory, relative).getAbsoluteFile();
        File icon = requested.getCanonicalFile();

        if (!icon.equals(requested)
                || !icon.getPath().startsWith(directory.getPath() + File.separator)
                || !icon.isFile()) {
            return null;
        }

        return icon;
    }

    private static File internalFile(File directory, String name) throws IOException {
        File file = directChild(directory, name);

        if (!file.isFile()) {
            throw new IOException("Missing icon pack internal file");
        }

        return file;
    }

    private static File directChild(File directory, String name) throws IOException {
        File canonicalDirectory = directory.getCanonicalFile();
        File file = new File(canonicalDirectory, name).getAbsoluteFile();

        IconPackManifest.require(
                file.getParentFile() != null
                        && file.getParentFile().equals(canonicalDirectory)
                        && file.getCanonicalFile().equals(file),
                "Invalid icon pack internal path");

        return file;
    }

    private static byte[] readBytes(File file, long limit) throws IOException {
        try (InputStream input = new FileInputStream(file);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;

            while ((read = input.read(buffer)) != -1) {
                IconPackManifest.require(output.size() + read <= limit, "Icon exceeds size limit");
                output.write(buffer, 0, read);
            }

            return output.toByteArray();
        }
    }

    private void checkStorageLimit(File replaced, File staging) throws IOException {
        File[] files = root.listFiles();

        if (files == null) {
            throw new IOException("Cannot inspect icon pack storage");
        }

        int packs = 1;
        long bytes = treeSize(staging);

        for (File file : files) {
            if (file.equals(staging)) {
                continue;
            }

            if (file.isDirectory() && file.getName().startsWith(".backup-pack-")) {
                bytes += treeSize(file);
            } else if (file.isDirectory() && file.getName().startsWith("pack-")) {
                if (!file.equals(replaced)) {
                    packs++;
                }

                bytes += treeSize(file);
            }
        }

        IconPackManifest.require(packs <= MAX_PACKS, "Too many installed icon packs");
        IconPackManifest.require(bytes <= MAX_STORAGE_BYTES, "Icon pack storage limit exceeded");
    }

    private long treeSize(File file) throws IOException {
        boolean symbolicLink = !file.getCanonicalFile().equals(file.getAbsoluteFile());

        if (!file.exists()) {
            if (symbolicLink) {
                throw new IOException("Icon pack storage contains a symbolic link");
            }

            return 0;
        }

        if (symbolicLink) {
            throw new IOException("Icon pack storage contains a symbolic link");
        }

        if (!file.isDirectory()) {
            return file.length();
        }

        File[] children = file.listFiles();

        if (children == null) {
            throw new IOException("Cannot inspect icon pack directory");
        }

        long total = 0;

        for (File child : children) {
            total += treeSize(child);
            IconPackManifest.require(total <= MAX_STORAGE_BYTES, "Icon pack storage limit exceeded");
        }

        return total;
    }

    private void writeInstallationMetadata(
            File directory,
            IconPackArchiveValidator.Result checked,
            String generation) throws IOException {
        byte[] index = encodeIndex(checked, generation);
        String indexSha256 = hash(index);

        clearRejectedMetadata(directory);
        writeAtomic(directory, INDEX_FILE, index);
        writeIdentity(directory, checked.sha256, generation, indexSha256);
    }

    private boolean isRejectedInstallation(File directory, Identity identity) throws IOException {
        File rejected = directChild(directory, REJECTED_FILE);

        if (!rejected.exists()) {
            return false;
        }

        if (!rejected.isFile()) {
            throw new IOException("Invalid icon pack rejection marker");
        }

        RejectedInstallation stored;

        try {
            stored = decodeRejectedInstallation(readBytes(rejected, MAX_REJECTED_BYTES));
        } catch (IOException error) {
            clearRejectedMetadata(directory);
            return false;
        }

        if (stored.validationVersion != catalog.validationVersion()
                || !stored.generation.equals(identity.generation)) {
            clearRejectedMetadata(directory);
            return false;
        }

        File source = internalFile(directory, SOURCE_ARCHIVE);

        if (source.length() != stored.archiveBytes
                || !stored.archiveSha256.equals(
                        hash(source, IconPackArchiveValidator.MAX_ARCHIVE_BYTES))) {
            clearRejectedMetadata(directory);
            return false;
        }

        return true;
    }

    private void rememberRejectedInstallation(
            File directory,
            Identity identity,
            File source) {
        try {
            long archiveBytes = source.length();
            String archiveSha256 = hash(source, IconPackArchiveValidator.MAX_ARCHIVE_BYTES);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();

            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(REJECTED_MAGIC);
                output.writeInt(REJECTED_VERSION);
                output.writeInt(catalog.validationVersion());
                output.writeLong(archiveBytes);
                writeString(output, archiveSha256, 64);
                writeString(output, identity.generation, 36);
            }

            writeAtomic(directory, REJECTED_FILE, bytes.toByteArray());
        } catch (IOException ignored) {
        }
    }

    private static RejectedInstallation decodeRejectedInstallation(byte[] bytes)
            throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            IconPackManifest.require(
                    input.readInt() == REJECTED_MAGIC
                            && input.readInt() == REJECTED_VERSION,
                    "Invalid icon pack rejection marker");

            int validationVersion = input.readInt();
            long archiveBytes = input.readLong();
            String archiveSha256 = readString(input, 64);
            String generation = readString(input, 36);

            IconPackManifest.require(
                    validationVersion > 0
                            && archiveBytes > 0
                            && archiveBytes <= IconPackArchiveValidator.MAX_ARCHIVE_BYTES
                            && isSha256(archiveSha256)
                            && isCanonicalGeneration(generation)
                            && input.read() == -1,
                    "Invalid icon pack rejection marker");

            return new RejectedInstallation(
                    validationVersion,
                    archiveBytes,
                    archiveSha256,
                    generation);
        }
    }

    private static void clearRejectedMetadata(File directory) throws IOException {
        File rejected = directChild(directory, REJECTED_FILE);

        if (rejected.exists() && !rejected.delete()) {
            throw new IOException("Cannot clear icon pack rejection marker");
        }
    }

    private void repairInstallationMetadata(
            File directory,
            IconPackArchiveValidator.Result checked,
            String generation) {
        try {
            writeInstallationMetadata(directory, checked, generation);
        } catch (IOException ignored) {
        }
    }

    private byte[] encodeIndex(
            IconPackArchiveValidator.Result checked,
            String generation) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(INDEX_MAGIC);
            output.writeInt(INDEX_VERSION);
            output.writeInt(catalog.validationVersion());
            output.writeLong(checked.archiveBytes);
            writeString(output, checked.sha256, 64);
            writeString(output, generation, 36);
            writeString(
                    output,
                    IconPackManifest.serialize(checked.manifest),
                    MAX_INDEX_MANIFEST_BYTES);

            ArrayList<String> paths = new ArrayList<>(checked.iconHashes.keySet());
            Collections.sort(paths);
            output.writeInt(paths.size());

            for (String path : paths) {
                writeString(output, path, MAX_INDEX_PATH_BYTES);
                writeString(output, checked.iconHashes.get(path), 64);
            }
        }

        IconPackManifest.require(bytes.size() <= MAX_INDEX_BYTES, "Icon pack index is too large");

        return bytes.toByteArray();
    }

    private static InstallationIndex decodeIndex(byte[] bytes) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            IconPackManifest.require(input.readInt() == INDEX_MAGIC, "Invalid icon pack index");
            IconPackManifest.require(
                    input.readInt() == INDEX_VERSION,
                    "Unsupported icon pack index");

            int validationVersion = input.readInt();
            long archiveBytes = input.readLong();
            String archiveSha256 = readString(input, 64);
            String generation = readString(input, 36);
            String manifestJson = readString(input, MAX_INDEX_MANIFEST_BYTES);
            IconPackManifest manifest = IconPackManifest.parse(manifestJson);
            int count = input.readInt();

            IconPackManifest.require(
                    validationVersion > 0
                            && archiveBytes > 0
                            && archiveBytes <= IconPackArchiveValidator.MAX_ARCHIVE_BYTES
                            && isSha256(archiveSha256)
                            && isCanonicalGeneration(generation)
                            && count > 0
                            && count <= IconPackManifest.MAX_ICONS,
                    "Invalid icon pack index");

            LinkedHashMap<String, String> iconHashes = new LinkedHashMap<>();

            for (int i = 0; i < count; i++) {
                String path = IconPackManifest.validateIconPath(
                        readString(input, MAX_INDEX_PATH_BYTES));
                String sha256 = readString(input, 64);

                IconPackManifest.require(
                        isSha256(sha256) && iconHashes.put(path, sha256) == null,
                        "Invalid icon pack index");
            }

            IconPackManifest.require(input.read() == -1, "Trailing icon pack index data");

            return new InstallationIndex(
                    validationVersion,
                    archiveBytes,
                    archiveSha256,
                    generation,
                    manifest,
                    iconHashes);
        }
    }

    private static void writeString(DataOutputStream output, String value, int maxBytes)
            throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

        IconPackManifest.require(bytes.length <= maxBytes, "Icon pack index field is too large");
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input, int maxBytes) throws IOException {
        int length = input.readInt();

        IconPackManifest.require(length >= 0 && length <= maxBytes, "Invalid icon pack index field");

        byte[] bytes = new byte[length];
        input.readFully(bytes);

        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException error) {
            throw new IOException("Invalid icon pack index text", error);
        }
    }

    private static void writeIdentity(
            File directory,
            String sha256,
            String generation,
            String indexSha256) throws IOException {
        String value = "2\n" + sha256 + "\n" + generation + "\n" + indexSha256;

        writeAtomic(directory, IDENTITY_FILE, value.getBytes(StandardCharsets.US_ASCII));
    }

    private static void writeAtomic(File directory, String name, byte[] bytes) throws IOException {
        File target = directChild(directory, name);
        File temporary = directChild(directory, name + ".new");

        if (temporary.exists() && !temporary.delete()) {
            throw new IOException("Cannot replace icon pack metadata temporary file");
        }

        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(bytes);
                output.getFD().sync();
            }

            if (!temporary.renameTo(target)) {
                throw new IOException("Cannot publish icon pack metadata");
            }
        } finally {
            if (temporary.exists()) {
                temporary.delete();
            }
        }
    }

    private static Identity readIdentity(File directory) throws IOException {
        File identity = internalFile(directory, IDENTITY_FILE);

        if (identity.length() != 101 && identity.length() != 168) {
            throw new IOException("Invalid icon pack identity");
        }

        byte[] bytes = new byte[(int) identity.length()];

        try (InputStream input = new FileInputStream(identity)) {
            int offset = 0;

            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);

                if (read < 0) {
                    throw new IOException("Truncated icon pack identity");
                }

                offset += read;
            }

            if (input.read() != -1) {
                throw new IOException("Oversized icon pack identity");
            }
        }

        String value = new String(bytes, StandardCharsets.US_ASCII);
        if (bytes.length == 101) {
            String sha256 = value.substring(0, 64);
            String generation = value.substring(65);

            IconPackManifest.require(value.charAt(64) == '\n', "Invalid icon pack identity");
            IconPackManifest.require(
                    isSha256(sha256) && isCanonicalGeneration(generation),
                    "Invalid icon pack identity");

            return new Identity(sha256, generation, null);
        }

        String sha256 = value.substring(2, 66);
        String generation = value.substring(67, 103);
        String indexSha256 = value.substring(104);

        IconPackManifest.require(
                value.startsWith("2\n")
                        && value.charAt(66) == '\n'
                        && value.charAt(103) == '\n'
                        && isSha256(sha256)
                        && isCanonicalGeneration(generation)
                        && isSha256(indexSha256),
                "Invalid icon pack identity");

        return new Identity(sha256, generation, indexSha256);
    }

    private static final class Identity {
        final String sha256;
        final String generation;
        final String indexSha256;

        Identity(String sha256, String generation, String indexSha256) {
            this.sha256 = sha256;
            this.generation = generation;
            this.indexSha256 = indexSha256;
        }
    }

    private static final class InstallationIndex {
        final int validationVersion;
        final long archiveBytes;
        final String archiveSha256;
        final String generation;
        final IconPackManifest manifest;
        final Map<String, String> iconHashes;

        InstallationIndex(
                int validationVersion,
                long archiveBytes,
                String archiveSha256,
                String generation,
                IconPackManifest manifest,
                Map<String, String> iconHashes) {
            this.validationVersion = validationVersion;
            this.archiveBytes = archiveBytes;
            this.archiveSha256 = archiveSha256;
            this.generation = generation;
            this.manifest = manifest;
            this.iconHashes = Collections.unmodifiableMap(new LinkedHashMap<>(iconHashes));
        }
    }

    private static final class RejectedInstallation {
        final int validationVersion;
        final long archiveBytes;
        final String archiveSha256;
        final String generation;

        RejectedInstallation(
                int validationVersion,
                long archiveBytes,
                String archiveSha256,
                String generation) {
            this.validationVersion = validationVersion;
            this.archiveBytes = archiveBytes;
            this.archiveSha256 = archiveSha256;
            this.generation = generation;
        }
    }

    private static String hash(File file, long limit) throws IOException {
        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }

        byte[] buffer = new byte[8192];

        try (InputStream input = new FileInputStream(file)) {
            long total = 0;
            int read;

            while ((read = input.read(buffer)) != -1) {
                total += read;
                IconPackManifest.require(total <= limit, "File exceeds size limit");
                digest.update(buffer, 0, read);
            }
        }

        StringBuilder result = new StringBuilder(64);

        for (byte value : digest.digest()) {
            result.append(String.format(Locale.ROOT, "%02x", value & 255));
        }

        return result.toString();
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static boolean isCanonicalGeneration(String value) {
        try {
            return value != null && UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private static String hash(byte[] bytes) throws IOException {
        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }

        return hex(digest.digest(bytes));
    }

    private static boolean isWithin(File file, File directory) {
        return file.equals(directory)
                || file.getPath().startsWith(directory.getPath() + File.separator);
    }

    private File child(String name) throws IOException {
        File result = new File(root, name).getCanonicalFile();

        IconPackManifest.require(
                result.getParentFile() != null && result.getParentFile().equals(root),
                "Invalid icon pack storage path");

        return result;
    }

    private static void validateId(String id) throws IOException {
        IconPackManifest.require(IconPackManifest.isValidId(id), "Invalid icon pack ID");
    }

    private static String storageName(String id) throws IOException {
        validateId(id);

        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }

        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(id));

            digest.update(encoded);
        } catch (CharacterCodingException error) {
            throw new IOException("Invalid icon pack ID", error);
        }

        return "pack-" + hex(digest.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);

        for (byte value : bytes) {
            result.append(String.format(Locale.ROOT, "%02x", value & 255));
        }

        return result.toString();
    }

    private static void mkdir(File directory) throws IOException {
        if ((!directory.isDirectory() && !directory.mkdirs()) || !directory.isDirectory()) {
            throw new IOException("Cannot create icon pack directory");
        }
    }

    private static void deleteTree(File file) throws IOException {
        boolean symbolicLink = !file.getCanonicalFile().equals(file.getAbsoluteFile());

        if (!file.exists()) {
            if (symbolicLink && !file.delete()) {
                throw new IOException("Cannot delete icon pack symbolic link");
            }

            return;
        }

        if (file.isDirectory() && !symbolicLink) {
            File[] children = file.listFiles();

            if (children == null) {
                throw new IOException("Cannot read icon pack directory");
            }

            for (File child : children) {
                deleteTree(child);
            }
        }

        if (!file.delete() && file.exists()) {
            throw new IOException("Cannot delete icon pack file");
        }
    }
}

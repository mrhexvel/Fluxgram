package org.telegram.messenger.fluxgram.icons;

import java.io.ByteArrayOutputStream;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class IconPackStore {
    public static final String SOURCE_ARCHIVE = ".fluxgram-source.icons";
    public static final String IDENTITY_FILE = ".fluxgram-identity";
    public static final int MAX_PACKS = 32;
    public static final long MAX_STORAGE_BYTES = 256L * 1024 * 1024;

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
            writeIdentity(staging, checked.sha256, generation);
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
        if (!directory.isDirectory() || !directory.getCanonicalFile().equals(directory.getAbsoluteFile())) {
            throw new IOException("Invalid icon pack installation");
        }

        File source = new File(directory, SOURCE_ARCHIVE);
        IconPackArchiveValidator.Result checked =
                IconPackArchiveValidator.inspect(source, cache, catalog);

        IconPackManifest.require(
                expectedId == null || checked.manifest.id.equals(expectedId),
                "Icon pack directory does not match manifest");
        IconPackManifest.require(
                !verifyStorageName || directory.getName().equals(storageName(checked.manifest.id)),
                "Icon pack storage key does not match manifest");
        Identity identity = readIdentity(directory);

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

        return installed;
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

        for (Map.Entry<String, String> expected : hashes.entrySet()) {
            File icon = iconFile(canonicalDirectory, expected.getKey());

            IconPackManifest.require(icon != null, "Installed icon file is missing");
            IconPackManifest.require(
                    expected.getValue().equals(hash(icon)),
                    "Installed icon file does not match source archive");
        }
    }

    private static File iconFile(File directory, String relative) throws IOException {
        File icon = new File(directory, relative).getCanonicalFile();

        if (!icon.getPath().startsWith(directory.getPath() + File.separator)
                || !icon.isFile()) {
            return null;
        }

        return icon;
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

    private static void writeIdentity(File directory, String sha256, String generation)
            throws IOException {
        File identity = new File(directory, IDENTITY_FILE);
        String value = sha256 + "\n" + generation;

        try (FileOutputStream output = new FileOutputStream(identity)) {
            output.write(value.getBytes(StandardCharsets.US_ASCII));
            output.getFD().sync();
        }
    }

    private static Identity readIdentity(File directory) throws IOException {
        File identity = new File(directory, IDENTITY_FILE);

        if (!identity.isFile() || identity.length() != 101) {
            throw new IOException("Invalid icon pack identity");
        }

        byte[] bytes = new byte[101];

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
        int separator = value.indexOf('\n');

        IconPackManifest.require(separator == 64, "Invalid icon pack identity");

        String sha256 = value.substring(0, separator);
        String generation = value.substring(separator + 1);

        try {
            UUID.fromString(generation);
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid icon pack generation", error);
        }

        return new Identity(sha256, generation);
    }

    private static final class Identity {
        final String sha256;
        final String generation;

        Identity(String sha256, String generation) {
            this.sha256 = sha256;
            this.generation = generation;
        }
    }

    private static String hash(File file) throws IOException {
        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }

        byte[] buffer = new byte[8192];

        try (InputStream input = new FileInputStream(file)) {
            int read;

            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        StringBuilder result = new StringBuilder(64);

        for (byte value : digest.digest()) {
            result.append(String.format(Locale.ROOT, "%02x", value & 255));
        }

        return result.toString();
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

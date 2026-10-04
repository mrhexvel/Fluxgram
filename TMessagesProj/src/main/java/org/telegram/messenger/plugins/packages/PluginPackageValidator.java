package org.telegram.messenger.plugins.packages;

import static org.telegram.messenger.plugins.packages.PluginManifest.require;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PluginPackageValidator {
    public static final long MAX_ARCHIVE_BYTES = 8L * 1024 * 1024;
    public static final long MAX_UNPACKED_BYTES = 32L * 1024 * 1024;
    public static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    public static final int MAX_MANIFEST_BYTES = 64 * 1024;
    public static final int MAX_ENTRIES = 512;
    public static final int MAX_ICON_BYTES = 512 * 1024;

    public static final class Result {
        public final PluginManifest manifest;
        public final String sha256;
        public final byte[] iconBytes;
        public final long archiveBytes, unpackedBytes;
        public final int fileCount;

        private Result(
                PluginManifest manifest,
                String hash,
                long archiveBytes,
                long unpackedBytes,
                int fileCount,
                byte[] iconBytes) {
            this.manifest = manifest;
            this.iconBytes = iconBytes;
            this.sha256 = hash;
            this.archiveBytes = archiveBytes;
            this.unpackedBytes = unpackedBytes;
            this.fileCount = fileCount;
        }
    }

    private PluginPackageValidator() {}

    public static Result inspect(File archive, File privateCacheDirectory) throws IOException {
        File snapshot = File.createTempFile("fgplugin-review-", ".zip", privateCacheDirectory);

        try {
            MessageDigest digest;

            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException impossible) {
                throw new IOException(impossible);
            }

            long archiveBytes = 0;
            byte[] buffer = new byte[8192];

            try (InputStream in = new FileInputStream(archive);
                    FileOutputStream out = new FileOutputStream(snapshot)) {
                int read;

                while ((read = in.read(buffer)) != -1) {
                    archiveBytes += read;

                    require(archiveBytes <= MAX_ARCHIVE_BYTES, "Archive exceeds 8 MiB");

                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }

            checkDirectory(snapshot);

            Map<String, Boolean> paths = new HashMap<>();
            Map<String, String> spellings = new HashMap<>();

            byte[] manifestBytes = null;
            int entries = 0, files = 0;
            long total = 0;

            try (ZipFile zip = new ZipFile(snapshot)) {
                Enumeration<? extends ZipEntry> iterator = zip.entries();

                while (iterator.hasMoreElements()) {
                    ZipEntry entry = iterator.nextElement();

                    require(++entries <= MAX_ENTRIES, "Too many ZIP entries");

                    String path = validatePath(entry.getName(), entry.isDirectory());

                    require(
                            paths.put(path, entry.isDirectory()) == null,
                            "Duplicate ZIP path: " + path);

                    String prefix = "";

                    for (String part : path.split("/")) {
                        prefix = prefix.isEmpty() ? part : prefix + "/" + part;

                        String previous = spellings.put(prefix.toLowerCase(Locale.ROOT), prefix);

                        require(
                                previous == null || previous.equals(prefix),
                                "Case-conflicting ZIP path");
                    }

                    boolean isManifest = path.equals("manifest.json") && !entry.isDirectory();
                    long limit = isManifest ? MAX_MANIFEST_BYTES : MAX_FILE_BYTES;

                    require(
                            entry.getSize() >= 0 && entry.getSize() <= limit,
                            "Oversized or invalid file: " + path);
                    require(
                            !entry.isDirectory() || entry.getSize() == 0,
                            "Directory contains file data");

                    ByteArrayOutputStream metadata =
                            isManifest ? new ByteArrayOutputStream() : null;
                    CRC32 crc = new CRC32();
                    long size = 0;

                    try (InputStream in = zip.getInputStream(entry)) {
                        int read;

                        while ((read = in.read(buffer)) != -1) {
                            size += read;
                            total += read;

                            require(
                                    size <= limit && total <= MAX_UNPACKED_BYTES,
                                    "Unpacked size limit exceeded");

                            crc.update(buffer, 0, read);

                            if (metadata != null) {
                                metadata.write(buffer, 0, read);
                            }
                        }
                    }

                    require(
                            size == entry.getSize() && crc.getValue() == entry.getCrc(),
                            "Corrupt ZIP entry: " + path);

                    if (!entry.isDirectory()) {
                        files++;
                    }

                    if (metadata != null) {
                        manifestBytes = metadata.toByteArray();
                    }
                }
            }

            for (String path : paths.keySet()) {
                int slash = path.indexOf('/');

                while (slash >= 0) {
                    require(
                            !Boolean.FALSE.equals(paths.get(path.substring(0, slash))),
                            "File used as a directory");
                    slash = path.indexOf('/', slash + 1);
                }
            }

            require(manifestBytes != null, "Missing root manifest.json");

            PluginManifest manifest = PluginManifest.parse(decodeUtf8(manifestBytes));

            require(
                    Boolean.FALSE.equals(paths.get(manifest.entryFile())),
                    "Missing Python entrypoint file");

            byte[] iconBytes = null;

            if (manifest.icon != null) {
                require(Boolean.FALSE.equals(paths.get(manifest.icon)), "Missing plugin icon");

                try (ZipFile zip = new ZipFile(snapshot)) {
                    ZipEntry entry = zip.getEntry(manifest.icon);
                    require(entry.getSize() <= MAX_ICON_BYTES, "Plugin icon exceeds 512 KiB");

                    try (InputStream in = zip.getInputStream(entry);
                            ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                        PluginPackageStore.copy(in, out, MAX_ICON_BYTES);
                        iconBytes = out.toByteArray();
                    }
                }

                validateIcon(iconBytes, manifest.icon);
            }

            return new Result(manifest, hex(digest.digest()), archiveBytes, total, files, iconBytes);
        } finally {
            if (!snapshot.delete() && snapshot.exists()) {
                throw new IOException("Could not remove temporary plugin archive");
            }
        }
    }

    public static void validateIconPath(String path) throws IOException {
        validatePath(path, false);
        String lower = path.toLowerCase(Locale.ROOT);
        require(lower.endsWith(".png") || lower.endsWith(".webp"), "Icon must be PNG or WebP");
    }

    public static void validateIcon(byte[] data, String path) throws IOException {
        validateIconPath(path);
        require(data.length <= MAX_ICON_BYTES && data.length >= 20, "Invalid icon size");
        int width = 0;
        int height = 0;

        if (path.toLowerCase(Locale.ROOT).endsWith(".png")) {
            byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

            for (int i = 0; i < signature.length; i++) {
                require(data[i] == signature[i], "Invalid PNG icon");
            }

            boolean end = false;
            boolean pixels = false;
            int offset = 8;

            while (offset + 12 <= data.length) {
                long length = number(data, offset, 4, false);
                require(length <= data.length - offset - 12, "Truncated PNG icon");
                String type = new String(data, offset + 4, 4, StandardCharsets.US_ASCII);
                CRC32 crc = new CRC32();
                crc.update(data, offset + 4, (int) length + 4);
                require(crc.getValue() == number(data, offset + 8 + (int) length, 4, false), "Invalid PNG checksum");
                require(!type.equals("acTL") && !type.equals("fcTL") && !type.equals("fdAT"), "Animated icons are not supported");

                if (offset == 8) {
                    require(type.equals("IHDR") && length == 13, "Invalid PNG header");
                    width = (int) number(data, offset + 8, 4, false);
                    height = (int) number(data, offset + 12, 4, false);
                } else {
                    require(!type.equals("IHDR"), "Duplicate PNG header");
                }

                pixels |= type.equals("IDAT");
                offset += (int) length + 12;

                if (type.equals("IEND")) {
                    require(length == 0 && offset == data.length, "Invalid PNG end");
                    end = true;
                    break;
                }
            }

            require(end && pixels, "Incomplete PNG icon");
        } else {
            require(new String(data, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(data, 8, 4, StandardCharsets.US_ASCII).equals("WEBP"), "Invalid WebP icon");
            require(number(data, 4, 4, true) == data.length - 8, "Invalid WebP length");
            int offset = 12;
            boolean pixels = false;

            while (offset + 8 <= data.length) {
                String type = new String(data, offset, 4, StandardCharsets.US_ASCII);
                long length = number(data, offset + 4, 4, true);
                require(length <= data.length - offset - 8, "Truncated WebP icon");
                int start = offset + 8;
                require(!type.equals("ANIM") && !type.equals("ANMF"), "Animated icons are not supported");

                if (type.equals("VP8X")) {
                    require(length == 10 && (data[start] & 2) == 0, "Animated or invalid WebP icon");
                    checkIconDimensions((int) number(data, start + 4, 3, true) + 1,
                            (int) number(data, start + 7, 3, true) + 1);
                } else if (type.equals("VP8 ")) {
                    require(!pixels && length >= 10 && data[start + 3] == (byte) 0x9d
                            && data[start + 4] == 1 && data[start + 5] == 0x2a, "Invalid WebP frame");
                    width = (int) number(data, start + 6, 2, true) & 0x3fff;
                    height = (int) number(data, start + 8, 2, true) & 0x3fff;
                    pixels = true;
                } else if (type.equals("VP8L")) {
                    require(!pixels && length >= 5 && data[start] == 0x2f, "Invalid WebP frame");
                    long bits = number(data, start + 1, 4, true);
                    width = (int) (bits & 0x3fff) + 1;
                    height = (int) ((bits >> 14) & 0x3fff) + 1;
                    pixels = true;
                }

                offset += 8 + (int) length + ((int) length & 1);
            }

            require(pixels && offset == data.length, "Incomplete WebP icon");
        }

        checkIconDimensions(width, height);
    }

    private static void checkIconDimensions(int width, int height) throws IOException {
        require(width > 0 && height > 0 && width <= 1024 && height <= 1024,
                "Icon dimensions must be between 1 and 1024 pixels");
    }

    private static long number(byte[] data, int offset, int length, boolean littleEndian) {
        long value = 0;

        for (int i = 0; i < length; i++) {
            int index = littleEndian ? offset + length - i - 1 : offset + i;
            value = (value << 8) | (data[index] & 255);
        }

        return value;
    }

    private static String validatePath(String name, boolean directory) throws IOException {
        require(name.length() > 0 && name.length() <= 240, "Invalid ZIP path length");

        String path = directory ? name.substring(0, name.length() - 1) : name;

        require(!path.isEmpty(), "Empty ZIP path");

        String[] parts = path.split("/", -1);

        require(parts.length <= 16, "ZIP path is too deep");

        for (String part : parts) {
            require(
                    part.matches("[A-Za-z0-9_.-]+")
                            && !part.equals(".")
                            && !part.equals("..")
                            && !part.endsWith("."),
                    "Unsafe ZIP path: " + name);
        }

        require(
                !parts[0].equalsIgnoreCase("fluxgram") && !parts[0].equalsIgnoreCase("fluxgram.py"),
                "Do not bundle the FluxGram SDK");

        require(!parts[0].equalsIgnoreCase(".fluxgram-source.fgplugin"),
                "Reserved package path");

        return path;
    }

    private static void checkDirectory(File file) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            long length = in.length();
            long end = -1;

            for (long pos = length - 22; pos >= Math.max(0, length - 65557); pos--) {
                in.seek(pos);

                if (u32(in) == 0x06054b50L) {
                    in.seek(pos + 20);

                    if (pos + 22 + u16(in) == length) {
                        end = pos;
                        break;
                    }
                }
            }

            require(end >= 0, "Missing ZIP end record");
            in.seek(end + 4);

            require(u16(in) == 0 && u16(in) == 0, "Multi-disk ZIP is unsupported");

            int diskEntries = u16(in), count = u16(in);
            long size = u32(in), offset = u32(in);

            require(
                    count > 0 && count <= MAX_ENTRIES && diskEntries == count,
                    "Invalid ZIP entry count");
            require(offset + size == end, "Invalid or unsupported ZIP directory");
            in.seek(offset);

            for (int i = 0; i < count; i++) {
                require(
                        in.getFilePointer() + 46 <= end && u32(in) == 0x02014b50L,
                        "Invalid ZIP directory entry");
                u16(in);

                require(u16(in) <= 20, "Unsupported ZIP version");

                int flags = u16(in), method = u16(in);

                require((flags & (1 | 64)) == 0, "Encrypted ZIP is unsupported");
                require(
                        method == ZipEntry.STORED || method == ZipEntry.DEFLATED,
                        "Unsupported ZIP compression");
                in.skipBytes(16);

                int name = u16(in), extra = u16(in), comment = u16(in);

                require(u16(in) == 0, "Multi-disk ZIP is unsupported");
                u16(in);

                long attributes = u32(in), localOffset = u32(in);
                int type = (int) (attributes >>> 16) & 0xf000;
                require(
                        type == 0 || type == 0x8000 || type == 0x4000,
                        "Links and special files are unsupported");
                require(localOffset < offset, "Invalid ZIP local header offset");

                long next = in.getFilePointer() + name + extra + comment;
                require(next <= end, "Invalid ZIP directory lengths");
                in.seek(next);
            }

            require(in.getFilePointer() == end, "Unexpected ZIP directory data");
        }
    }

    private static int u16(RandomAccessFile in) throws IOException {
        return Short.toUnsignedInt(Short.reverseBytes(in.readShort()));
    }

    private static long u32(RandomAccessFile in) throws IOException {
        return Integer.toUnsignedLong(Integer.reverseBytes(in.readInt()));
    }

    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();

        for (byte value : bytes) {
            out.append(String.format(Locale.ROOT, "%02x", value & 255));
        }

        return out.toString();
    }
}

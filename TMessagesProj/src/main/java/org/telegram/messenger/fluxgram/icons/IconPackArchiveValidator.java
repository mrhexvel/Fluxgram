package org.telegram.messenger.fluxgram.icons;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public final class IconPackArchiveValidator {
    public static final class InvalidArchiveException extends IOException {
        InvalidArchiveException(String message) {
            super(message);
        }

        InvalidArchiveException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final long MAX_ARCHIVE_BYTES = 16L * 1024 * 1024;
    public static final long MAX_UNPACKED_BYTES = 64L * 1024 * 1024;
    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    public static final long MAX_ICON_BYTES = 2L * 1024 * 1024;
    public static final int MAX_METADATA_BYTES = 64 * 1024;
    public static final int MAX_ENTRIES = 4096;
    public static final double MAX_COMPRESSION_RATIO = 250.0;

    private static final String SNAPSHOT_PREFIX = "icon-pack-review-";
    private static final String SNAPSHOT_SUFFIX = ".icons";
    private static final long STALE_SNAPSHOT_MILLIS = 24L * 60 * 60 * 1000;
    private static final Object SNAPSHOT_LOCK = new Object();
    private static final HashSet<String> ACTIVE_SNAPSHOTS = new HashSet<>();

    public interface Catalog {
        boolean contains(String resourceName);

        default boolean decodesRaster(byte[] bytes, String path, int width, int height) {
            return false;
        }

        default boolean decodesSvg(byte[] bytes, String path) {
            return false;
        }
    }

    public static final class Snapshot implements AutoCloseable {
        public final Result result;

        private File archive;

        private Snapshot(File archive, Result result) {
            this.archive = archive;
            this.result = result;
        }

        File archive() throws IOException {
            if (archive == null) {
                throw new IOException("Icon pack snapshot is closed");
            }

            return archive;
        }

        @Override
        public void close() throws IOException {
            File current = archive;

            if (current != null && !current.delete() && current.exists()) {
                throw new IOException("Could not remove temporary icon pack archive");
            }

            if (current != null) {
                unregisterSnapshot(current);
            }
            archive = null;
        }
    }

    public static final class Result {
        public final IconPackManifest manifest;
        public final String sha256;
        public final long archiveBytes;
        public final long unpackedBytes;
        public final int fileCount;
        public final Map<String, String> iconHashes;

        private Result(
                IconPackManifest manifest,
                String sha256,
                long archiveBytes,
                long unpackedBytes,
                int fileCount,
                Map<String, String> iconHashes) {
            this.manifest = manifest;
            this.sha256 = sha256;
            this.archiveBytes = archiveBytes;
            this.unpackedBytes = unpackedBytes;
            this.fileCount = fileCount;
            this.iconHashes = Collections.unmodifiableMap(new LinkedHashMap<>(iconHashes));
        }
    }

    private IconPackArchiveValidator() {}

    public static Result inspect(File archive, File cacheDirectory, Catalog catalog)
            throws IOException {
        try (Snapshot snapshot = snapshot(archive, cacheDirectory, catalog)) {
            return snapshot.result;
        }
    }

    public static Snapshot snapshot(File archive, File cacheDirectory, Catalog catalog)
            throws IOException {
        requireDirectory(cacheDirectory);
        cleanupStaleSnapshots(cacheDirectory);

        File snapshot = File.createTempFile(SNAPSHOT_PREFIX, SNAPSHOT_SUFFIX, cacheDirectory);
        registerSnapshot(snapshot);
        boolean keep = false;

        try {
            MessageDigest digest = digest();
            long archiveBytes = copyArchive(archive, snapshot, digest);
            HashMap<String, Boolean> paths = new HashMap<>();
            HashMap<String, String> spellings = new HashMap<>();
            byte[] metadata = null;
            long unpackedBytes = 0;
            int entries = 0;
            int files = 0;
            byte[] buffer = new byte[8192];

            try (ZipFile zip = new ZipFile(snapshot)) {
                Enumeration<? extends ZipEntry> iterator = zip.entries();

                while (iterator.hasMoreElements()) {
                    ZipEntry entry = iterator.nextElement();

                    IconPackManifest.require(++entries <= MAX_ENTRIES, "Too many ZIP entries");

                    String path = validateArchivePath(entry.getName(), entry.isDirectory());

                    IconPackManifest.require(
                            paths.put(path, entry.isDirectory()) == null,
                            "Duplicate ZIP path: " + path);

                    String prefix = "";

                    for (String part : path.split("/")) {
                        prefix = prefix.isEmpty() ? part : prefix + "/" + part;

                        String previous = spellings.put(prefix.toLowerCase(Locale.ROOT), prefix);

                        IconPackManifest.require(
                                previous == null || previous.equals(prefix),
                                "Case-conflicting ZIP path");
                    }

                    boolean isMetadata = path.equals("metadata.json") && !entry.isDirectory();
                    long limit = isMetadata ? MAX_METADATA_BYTES : MAX_FILE_BYTES;

                    IconPackManifest.require(
                            entry.getSize() >= 0 && entry.getSize() <= limit,
                            "Oversized or invalid file: " + path);
                    IconPackManifest.require(
                            !entry.isDirectory() || entry.getSize() == 0,
                            "Directory contains file data");
                    IconPackManifest.require(
                            entry.getMethod() == ZipEntry.STORED
                                    || entry.getMethod() == ZipEntry.DEFLATED,
                            "Unsupported ZIP compression");

                    if (!entry.isDirectory() && entry.getSize() > 0) {
                        IconPackManifest.require(
                                entry.getCompressedSize() > 0
                                        && entry.getSize() / (double) entry.getCompressedSize()
                                        <= MAX_COMPRESSION_RATIO,
                                "ZIP compression ratio is too high");
                    }

                    ByteArrayOutputStream metadataOut =
                            isMetadata ? new ByteArrayOutputStream() : null;
                    CRC32 crc = new CRC32();
                    long size = 0;

                    try (InputStream input = zip.getInputStream(entry)) {
                        int read;

                        while ((read = input.read(buffer)) != -1) {
                            size += read;
                            unpackedBytes += read;

                            IconPackManifest.require(
                                    size <= limit && unpackedBytes <= MAX_UNPACKED_BYTES,
                                    "Unpacked size limit exceeded");

                            crc.update(buffer, 0, read);

                            if (metadataOut != null) {
                                metadataOut.write(buffer, 0, read);
                            }
                        }
                    }

                    IconPackManifest.require(
                            size == entry.getSize() && crc.getValue() == entry.getCrc(),
                            "Corrupt ZIP entry: " + path);

                    if (!entry.isDirectory()) {
                        files++;
                    }

                    if (metadataOut != null) {
                        metadata = metadataOut.toByteArray();
                    }
                }

                validateTree(paths);
                IconPackManifest.require(metadata != null, "Missing root metadata.json");

                IconPackManifest manifest = IconPackManifest.parse(decodeUtf8(metadata));

                Map<String, String> iconHashes = validateIcons(zip, paths, manifest, catalog);

                Result result = new Result(
                        manifest,
                        hex(digest.digest()),
                        archiveBytes,
                        unpackedBytes,
                        files,
                        iconHashes);

                keep = true;

                return new Snapshot(snapshot, result);
            } catch (ZipException error) {
                throw new InvalidArchiveException("Invalid icon pack archive", error);
            }
        } finally {
            if (!keep && !snapshot.delete() && snapshot.exists()) {
                throw new IOException("Could not remove temporary icon pack archive");
            }
            if (!keep) {
                unregisterSnapshot(snapshot);
            }
        }
    }

    private static void cleanupStaleSnapshots(File cacheDirectory) throws IOException {
        File[] files = cacheDirectory.listFiles();

        if (files == null) {
            throw new IOException("Cannot inspect icon pack cache directory");
        }

        long threshold = System.currentTimeMillis() - STALE_SNAPSHOT_MILLIS;

        synchronized (SNAPSHOT_LOCK) {
            for (File file : files) {
                String name = file.getName();
                String path = file.getCanonicalPath();

                if (name.startsWith(SNAPSHOT_PREFIX)
                        && name.endsWith(SNAPSHOT_SUFFIX)
                        && file.isFile()
                        && file.lastModified() > 0
                        && file.lastModified() <= threshold
                        && !ACTIVE_SNAPSHOTS.contains(path)
                        && !file.delete()
                        && file.exists()) {
                    throw new IOException("Could not remove stale icon pack snapshot");
                }
            }
        }
    }

    private static void registerSnapshot(File snapshot) throws IOException {
        synchronized (SNAPSHOT_LOCK) {
            ACTIVE_SNAPSHOTS.add(snapshot.getCanonicalPath());
        }
    }

    private static void unregisterSnapshot(File snapshot) throws IOException {
        synchronized (SNAPSHOT_LOCK) {
            ACTIVE_SNAPSHOTS.remove(snapshot.getCanonicalPath());
        }
    }

    public static String validateArchivePath(String path, boolean directory) throws IOException {
        IconPackManifest.require(path != null && !path.isEmpty() && path.length() <= 240, "Invalid ZIP path");
        IconPackManifest.require(path.indexOf('\\') < 0 && path.charAt(0) != '/', "Invalid ZIP path");
        IconPackManifest.require(!path.contains("//"), "Invalid ZIP path");

        String normalized = directory && path.endsWith("/")
                ? path.substring(0, path.length() - 1)
                : path;

        IconPackManifest.require(!normalized.isEmpty() && !normalized.endsWith("/"), "Invalid ZIP path");

        for (String part : normalized.split("/")) {
            IconPackManifest.require(
                    IconPackManifest.isSafeText(part)
                            && !part.isEmpty()
                            && !part.equals(".")
                            && !part.equals(".."),
                    "Invalid ZIP path");
        }

        return normalized;
    }

    private static void validateIcon(byte[] bytes, String path, Catalog catalog) throws IOException {
        String lower = path.toLowerCase(Locale.ROOT);

        if (lower.endsWith(".png")) {
            int[] dimensions = validatePng(bytes);

            IconPackManifest.require(
                    catalog != null
                            && catalog.decodesRaster(bytes, path, dimensions[0], dimensions[1]),
                    "PNG icon cannot be decoded");
        } else if (lower.endsWith(".webp")) {
            int[] dimensions = validateWebp(bytes);

            IconPackManifest.require(
                    catalog != null
                            && catalog.decodesRaster(bytes, path, dimensions[0], dimensions[1]),
                    "WebP icon cannot be decoded");
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            int[] dimensions = validateJpeg(bytes);

            IconPackManifest.require(
                    catalog != null
                            && catalog.decodesRaster(bytes, path, dimensions[0], dimensions[1]),
                    "JPEG icon cannot be decoded");
        } else {
            String svg = decodeUtf8(bytes);
            String normalized = svg.trim().toLowerCase(Locale.ROOT);

            IconPackManifest.require(
                    normalized.startsWith("<svg") || normalized.startsWith("<?xml"),
                    "Invalid SVG icon");
            String withoutComments = normalized.replaceAll("(?s)<!--.*?-->", "");

            IconPackManifest.require(
                    !withoutComments.contains("<!"),
                    "Unsafe SVG icon");
            IconPackManifest.require(normalized.contains("<svg"), "Invalid SVG icon");
            validateSvg(bytes);
            IconPackManifest.require(
                    catalog != null && catalog.decodesSvg(bytes, path),
                    "SVG icon cannot be decoded");
        }
    }

    static void copy(InputStream input, FileOutputStream output, long limit) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;

        while ((read = input.read(buffer)) != -1) {
            total += read;
            IconPackManifest.require(total <= limit, "File exceeds size limit");
            output.write(buffer, 0, read);
        }
    }

    private static long copyArchive(File source, File destination, MessageDigest digest)
            throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;

        try (InputStream input = new FileInputStream(source);
                FileOutputStream output = new FileOutputStream(destination)) {
            int read;

            while ((read = input.read(buffer)) != -1) {
                total += read;
                IconPackManifest.require(total <= MAX_ARCHIVE_BYTES, "Archive exceeds size limit");
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }

            output.getFD().sync();
        }

        return total;
    }

    private static void validateTree(Map<String, Boolean> paths) throws IOException {
        for (String path : paths.keySet()) {
            int slash = path.indexOf('/');

            while (slash >= 0) {
                IconPackManifest.require(
                        !Boolean.FALSE.equals(paths.get(path.substring(0, slash))),
                        "File used as a directory");
                slash = path.indexOf('/', slash + 1);
            }
        }
    }

    private static Map<String, String> validateIcons(
            ZipFile zip,
            Map<String, Boolean> paths,
            IconPackManifest manifest,
            Catalog catalog) throws IOException {
        Set<String> supportedPaths = new HashSet<>();
        LinkedHashMap<String, String> hashes = new LinkedHashMap<>();

        for (Map.Entry<String, String> icon : manifest.icons.entrySet()) {
            if (catalog == null || !catalog.contains(icon.getKey())) {
                continue;
            }

            IconPackManifest.require(
                    Boolean.FALSE.equals(paths.get(icon.getValue())),
                    "Missing icon file: " + icon.getValue());
            supportedPaths.add(icon.getValue());
        }

        IconPackManifest.require(!supportedPaths.isEmpty(), "Icon pack has no supported resources");

        for (Map.Entry<String, Boolean> path : paths.entrySet()) {
            if (path.getValue() || path.getKey().equals("metadata.json")) {
                continue;
            }

            IconPackManifest.validateIconPath(path.getKey());

            if (!supportedPaths.contains(path.getKey())) {
                continue;
            }

            ZipEntry entry = zip.getEntry(path.getKey());

            try (InputStream input = zip.getInputStream(entry);
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;

                while ((read = input.read(buffer)) != -1) {
                    IconPackManifest.require(
                            output.size() + read <= MAX_ICON_BYTES,
                            "Icon exceeds size limit");
                    output.write(buffer, 0, read);
                }

                byte[] bytes = output.toByteArray();

                validateIcon(bytes, path.getKey(), catalog);
                hashes.put(path.getKey(), hex(digest().digest(bytes)));
            }
        }

        return hashes;
    }

    private static int[] validatePng(byte[] bytes) throws IOException {
        byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

        IconPackManifest.require(bytes.length >= 20, "Invalid PNG icon");

        for (int i = 0; i < signature.length; i++) {
            IconPackManifest.require(bytes[i] == signature[i], "Invalid PNG icon");
        }

        boolean end = false;
        boolean pixels = false;
        int width = 0;
        int height = 0;
        int offset = 8;

        while (offset + 12 <= bytes.length) {
            long length = number(bytes, offset, 4, false);

            IconPackManifest.require(length <= bytes.length - offset - 12, "Truncated PNG icon");

            String type = ascii(bytes, offset + 4, 4);
            CRC32 crc = new CRC32();
            crc.update(bytes, offset + 4, (int) length + 4);

            IconPackManifest.require(
                    crc.getValue() == number(bytes, offset + 8 + (int) length, 4, false),
                    "Invalid PNG checksum");
            IconPackManifest.require(
                    !type.equals("acTL") && !type.equals("fcTL") && !type.equals("fdAT"),
                    "Animated icons are unsupported");

            if (offset == 8) {
                IconPackManifest.require(type.equals("IHDR") && length == 13, "Invalid PNG header");
                width = (int) number(bytes, offset + 8, 4, false);
                height = (int) number(bytes, offset + 12, 4, false);
            } else {
                IconPackManifest.require(!type.equals("IHDR"), "Duplicate PNG header");
            }

            pixels |= type.equals("IDAT");
            offset += (int) length + 12;

            if (type.equals("IEND")) {
                IconPackManifest.require(length == 0, "Invalid PNG end");
                end = true;
                break;
            }
        }

        IconPackManifest.require(end && pixels, "Incomplete PNG icon");
        checkDimensions(width, height);

        return new int[] {width, height};
    }

    private static int[] validateWebp(byte[] bytes) throws IOException {
        IconPackManifest.require(
                bytes.length >= 20
                        && ascii(bytes, 0, 4).equals("RIFF")
                        && ascii(bytes, 8, 4).equals("WEBP"),
                "Invalid WebP icon");
        IconPackManifest.require(
                number(bytes, 4, 4, true) == bytes.length - 8,
                "Invalid WebP length");

        int width = 0;
        int height = 0;
        int offset = 12;
        boolean pixels = false;

        while (offset + 8 <= bytes.length) {
            String type = ascii(bytes, offset, 4);
            long length = number(bytes, offset + 4, 4, true);

            IconPackManifest.require(length <= bytes.length - offset - 8, "Truncated WebP icon");

            int start = offset + 8;

            IconPackManifest.require(
                    !type.equals("ANIM") && !type.equals("ANMF"),
                    "Animated icons are unsupported");

            if (type.equals("VP8X")) {
                IconPackManifest.require(
                        length == 10 && (bytes[start] & 2) == 0,
                        "Animated or invalid WebP icon");
                checkDimensions(
                        (int) number(bytes, start + 4, 3, true) + 1,
                        (int) number(bytes, start + 7, 3, true) + 1);
            } else if (type.equals("VP8 ")) {
                IconPackManifest.require(
                        !pixels
                                && length >= 10
                                && bytes[start + 3] == (byte) 0x9d
                                && bytes[start + 4] == 1
                                && bytes[start + 5] == 0x2a,
                        "Invalid WebP frame");
                width = (int) number(bytes, start + 6, 2, true) & 0x3fff;
                height = (int) number(bytes, start + 8, 2, true) & 0x3fff;
                pixels = true;
            } else if (type.equals("VP8L")) {
                IconPackManifest.require(
                        !pixels && length >= 5 && bytes[start] == 0x2f,
                        "Invalid WebP frame");

                long bits = number(bytes, start + 1, 4, true);
                width = (int) (bits & 0x3fff) + 1;
                height = (int) ((bits >> 14) & 0x3fff) + 1;
                pixels = true;
            }

            offset += 8 + (int) length + ((int) length & 1);
        }

        IconPackManifest.require(pixels && offset == bytes.length, "Incomplete WebP icon");
        checkDimensions(width, height);

        return new int[] {width, height};
    }

    private static int[] validateJpeg(byte[] bytes) throws IOException {
        IconPackManifest.require(
                bytes.length >= 4
                        && (bytes[0] & 0xff) == 0xff
                        && (bytes[1] & 0xff) == 0xd8,
                "Invalid JPEG icon");

        int width = 0;
        int height = 0;
        int offset = 2;
        int scanOffset = -1;

        while (offset < bytes.length) {
            IconPackManifest.require((bytes[offset] & 0xff) == 0xff, "Invalid JPEG marker");

            while (offset < bytes.length && (bytes[offset] & 0xff) == 0xff) {
                offset++;
            }

            IconPackManifest.require(offset < bytes.length, "Truncated JPEG marker");

            int marker = bytes[offset++] & 0xff;

            IconPackManifest.require(marker != 0x00, "Invalid JPEG marker");

            if (marker == 0xd9) {
                break;
            }
            if (marker == 0x01 || marker >= 0xd0 && marker <= 0xd7) {
                continue;
            }

            IconPackManifest.require(offset + 2 <= bytes.length, "Truncated JPEG segment");

            int length = (int) number(bytes, offset, 2, false);

            IconPackManifest.require(length >= 2 && length <= bytes.length - offset, "Invalid JPEG segment");

            if (isJpegStartOfFrame(marker)) {
                IconPackManifest.require(length >= 8, "Invalid JPEG frame");
                height = (int) number(bytes, offset + 3, 2, false);
                width = (int) number(bytes, offset + 5, 2, false);
                checkDimensions(width, height);
            }

            offset += length;

            if (marker == 0xda) {
                scanOffset = offset;
                break;
            }
        }

        IconPackManifest.require(width > 0 && height > 0 && scanOffset >= 0, "Incomplete JPEG icon");

        boolean end = false;

        for (int i = scanOffset; i + 1 < bytes.length; i++) {
            if ((bytes[i] & 0xff) != 0xff) {
                continue;
            }

            int marker = bytes[++i] & 0xff;

            while (marker == 0xff && i + 1 < bytes.length) {
                marker = bytes[++i] & 0xff;
            }

            if (marker == 0x00 || marker >= 0xd0 && marker <= 0xd7) {
                continue;
            }
            if (marker == 0xd9) {
                end = true;
                break;
            }
        }

        IconPackManifest.require(end, "Incomplete JPEG icon");

        return new int[] {width, height};
    }

    private static boolean isJpegStartOfFrame(int marker) {
        return marker >= 0xc0
                && marker <= 0xcf
                && marker != 0xc4
                && marker != 0xc8
                && marker != 0xcc;
    }

    private static void validateSvg(byte[] bytes) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
            Element root = document.getDocumentElement();
            String rootName = root.getLocalName() == null ? root.getNodeName() : root.getLocalName();

            rejectSvgProcessingInstructions(document);
            IconPackManifest.require(rootName.equalsIgnoreCase("svg"), "Invalid SVG root");
            validateSvgViewport(root);

            int[] count = {0, 0};

            validateSvgElement(root, 0, count);
        } catch (ParserConfigurationException | SAXException error) {
            throw new InvalidArchiveException("Invalid SVG icon", error);
        }
    }

    private static void rejectSvgProcessingInstructions(Node node) throws IOException {
        NodeList children = node.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            IconPackManifest.require(
                    child.getNodeType() != Node.PROCESSING_INSTRUCTION_NODE,
                    "Unsafe SVG icon");
        }
    }

    private static void validateSvgViewport(Element root) throws IOException {
        String viewBox = root.getAttribute("viewBox").trim();

        if (!viewBox.isEmpty()) {
            String[] values = viewBox.split("[\\s,]+");

            IconPackManifest.require(values.length == 4, "Invalid SVG viewBox");

            double x = finiteNumber(values[0], "Invalid SVG viewBox");
            double y = finiteNumber(values[1], "Invalid SVG viewBox");
            double width = finiteNumber(values[2], "Invalid SVG viewBox");
            double height = finiteNumber(values[3], "Invalid SVG viewBox");

            IconPackManifest.require(
                    Math.abs(x) <= 1000000
                            && Math.abs(y) <= 1000000
                            && width > 0
                            && height > 0
                            && width <= 16384
                            && height <= 16384,
                    "SVG viewBox is out of bounds");
            validateOptionalSvgLength(root.getAttribute("width"));
            validateOptionalSvgLength(root.getAttribute("height"));
            return;
        }

        double width = svgLength(root.getAttribute("width"));
        double height = svgLength(root.getAttribute("height"));

        IconPackManifest.require(
                width > 0 && height > 0 && width <= 4096 && height <= 4096,
                "SVG viewport is missing or out of bounds");
    }

    private static double svgLength(String value) throws IOException {
        String normalized = value.trim().toLowerCase(Locale.ROOT);

        if (normalized.endsWith("px")) {
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        }

        return finiteNumber(normalized, "Invalid SVG viewport");
    }

    private static void validateOptionalSvgLength(String value) throws IOException {
        String normalized = value.trim().toLowerCase(Locale.ROOT);

        if (normalized.isEmpty()) {
            return;
        }

        if (normalized.endsWith("%")) {
            double percent = finiteNumber(
                    normalized.substring(0, normalized.length() - 1).trim(),
                    "Invalid SVG viewport");

            IconPackManifest.require(
                    percent > 0 && percent <= 100,
                    "SVG viewport is out of bounds");
            return;
        }

        double length = svgLength(normalized);

        IconPackManifest.require(
                length > 0 && length <= 4096,
                "SVG viewport is out of bounds");
    }

    private static double finiteNumber(String value, String message) throws IOException {
        try {
            double number = Double.parseDouble(value);

            IconPackManifest.require(Double.isFinite(number), message);

            return number;
        } catch (NumberFormatException error) {
            throw new InvalidArchiveException(message, error);
        }
    }

    private static void validateSvgElement(Element element, int depth, int[] count)
            throws IOException {
        String name = element.getLocalName() == null
                ? element.getNodeName()
                : element.getLocalName();
        String normalizedName = name.toLowerCase(Locale.ROOT);

        IconPackManifest.require(depth <= 64 && ++count[0] <= 2048, "SVG icon is too complex");
        IconPackManifest.require(
                !normalizedName.equals("script")
                        && !normalizedName.equals("style")
                        && !normalizedName.equals("image")
                        && !normalizedName.equals("use")
                        && !normalizedName.equals("filter")
                        && !normalizedName.equals("mask")
                        && !normalizedName.equals("pattern")
                        && !normalizedName.equals("foreignobject")
                        && !normalizedName.equals("animate")
                        && !normalizedName.equals("animatemotion")
                        && !normalizedName.equals("animatetransform")
                        && !normalizedName.equals("set"),
                "Unsafe SVG icon");

        NamedNodeMap attributes = element.getAttributes();

        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String attributeName = attribute.getLocalName() == null
                    ? attribute.getNodeName()
                    : attribute.getLocalName();
            String normalizedAttribute = attributeName.toLowerCase(Locale.ROOT);
            String value = attribute.getNodeValue().trim().toLowerCase(Locale.ROOT);

            count[1] += value.length();

            IconPackManifest.require(
                    value.length() <= 65536 && count[1] <= 262144,
                    "SVG attributes are too complex");
            IconPackManifest.require(!normalizedAttribute.startsWith("on"), "Unsafe SVG icon");

            if (normalizedAttribute.equals("xmlns")
                    || attribute.getNodeName().toLowerCase(Locale.ROOT).startsWith("xmlns:")) {
                continue;
            }

            if (normalizedAttribute.equals("href")) {
                IconPackManifest.require(value.startsWith("#"), "External SVG reference");
            }

            IconPackManifest.require(
                    !value.contains("http:")
                            && !value.contains("https:")
                            && !value.contains("file:")
                            && !value.contains("data:")
                            && !value.contains("javascript:")
                            && hasOnlyLocalSvgUrls(value),
                    "External SVG reference");
        }

        NodeList children = element.getChildNodes();

        rejectSvgProcessingInstructions(element);

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            if (child instanceof Element) {
                validateSvgElement((Element) child, depth + 1, count);
            }
        }
    }

    private static boolean hasOnlyLocalSvgUrls(String value) {
        int offset = 0;

        while ((offset = value.indexOf("url(", offset)) >= 0) {
            int end = value.indexOf(')', offset + 4);

            if (end < 0) {
                return false;
            }

            String reference = value.substring(offset + 4, end).trim();

            if (reference.length() >= 2
                    && ((reference.startsWith("\"") && reference.endsWith("\""))
                            || (reference.startsWith("'") && reference.endsWith("'")))) {
                reference = reference.substring(1, reference.length() - 1).trim();
            }

            if (!reference.startsWith("#")) {
                return false;
            }

            offset = end + 1;
        }

        return true;
    }

    private static void checkDimensions(int width, int height) throws IOException {
        IconPackManifest.require(
                width > 0 && height > 0 && width <= 2048 && height <= 2048,
                "Icon dimensions must be between 1 and 2048 pixels");
    }

    private static long number(byte[] bytes, int offset, int length, boolean littleEndian) {
        long value = 0;

        for (int i = 0; i < length; i++) {
            int index = littleEndian ? offset + length - i - 1 : offset + i;
            value = (value << 8) | (bytes[index] & 255);
        }

        return value;
    }

    private static void requireDirectory(File directory) throws IOException {
        if ((!directory.isDirectory() && !directory.mkdirs()) || !directory.isDirectory()) {
            throw new IOException("Cannot create icon pack cache directory");
        }
    }

    private static MessageDigest digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
    }

    private static String decodeUtf8(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException error) {
            throw new InvalidArchiveException("Invalid UTF-8", error);
        }
    }

    private static String ascii(byte[] bytes, int offset, int count) {
        return new String(bytes, offset, count, StandardCharsets.US_ASCII);
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);

        for (byte value : bytes) {
            result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }

        return result.toString();
    }
}

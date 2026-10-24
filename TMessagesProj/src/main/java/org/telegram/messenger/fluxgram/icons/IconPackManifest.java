package org.telegram.messenger.fluxgram.icons;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class IconPackManifest {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ICONS = 3000;

    private static final Pattern RESOURCE_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,128}");

    public final int schemaVersion;
    public final String id;
    public final String name;
    public final String author;
    public final String version;
    public final Map<String, String> icons;

    private IconPackManifest(
            int schemaVersion,
            String id,
            String name,
            String author,
            String version,
            Map<String, String> icons) {
        this.schemaVersion = schemaVersion;
        this.id = id;
        this.name = name;
        this.author = author;
        this.version = version;
        this.icons = Collections.unmodifiableMap(new LinkedHashMap<>(icons));
    }

    public static IconPackManifest parse(String json) throws IOException {
        String id = null;
        String name = null;
        String author = "Unknown";
        String version = "1.0";
        Integer schemaVersion = null;
        LinkedHashMap<String, String> icons = new LinkedHashMap<>();
        LinkedHashMap<String, Boolean> fields = new LinkedHashMap<>();

        JsonReader reader = new JsonReader(new StringReader(json));
        reader.setStrictness(Strictness.STRICT);

        try {
            reader.beginObject();

            while (reader.hasNext()) {
                String field = reader.nextName();

                require(fields.put(field, true) == null, "Duplicate manifest field: " + field);

                switch (field) {
                    case "schemaVersion":
                    case "schema_version":
                        require(schemaVersion == null, "Duplicate schema version");
                        require(reader.peek() == JsonToken.NUMBER, "Expected numeric schema version");
                        schemaVersion = reader.nextInt();
                        break;
                    case "packId":
                        id = readString(reader, field);
                        break;
                    case "packName":
                        name = readString(reader, field);
                        break;
                    case "author":
                        author = readString(reader, field);
                        break;
                    case "version":
                        version = readString(reader, field);
                        break;
                    case "icons":
                        readIcons(reader, icons);
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }

            reader.endObject();
            require(reader.peek() == JsonToken.END_DOCUMENT, "Trailing manifest data");
        } catch (IconPackArchiveValidator.InvalidArchiveException error) {
            throw error;
        } catch (IOException | IllegalStateException | NumberFormatException error) {
            throw new IconPackArchiveValidator.InvalidArchiveException(
                    "Invalid icon pack manifest",
                    error);
        } finally {
            reader.close();
        }

        require(isValidId(id), "Invalid icon pack ID");
        require(isSafeText(name) && !name.trim().isEmpty() && name.length() <= 128, "Invalid icon pack name");
        require(isSafeText(author) && author.length() <= 128, "Invalid icon pack author");
        require(isSafeText(version) && !version.trim().isEmpty() && version.length() <= 64, "Invalid icon pack version");
        require(!icons.isEmpty(), "Icon pack is empty");
        require(
                schemaVersion == null || schemaVersion == SCHEMA_VERSION,
                "Unsupported icon pack schema");

        return new IconPackManifest(
                schemaVersion == null ? SCHEMA_VERSION : schemaVersion,
                id,
                name,
                author,
                version,
                icons);
    }

    public static boolean isValidResourceName(String name) {
        return name != null && RESOURCE_PATTERN.matcher(name).matches();
    }

    public static boolean isValidId(String id) {
        if (id == null
                || id.trim().isEmpty()
                || id.length() > 128
                || id.equals(".")
                || id.equals("..")
                || id.indexOf('/') >= 0
                || id.indexOf('\\') >= 0) {
            return false;
        }

        return isSafeText(id);
    }

    public static String validateIconPath(String path) throws IOException {
        require(path != null && !path.isEmpty() && path.length() <= 240, "Invalid icon path");
        require(path.indexOf('\\') < 0 && path.charAt(0) != '/', "Invalid icon path");
        require(!path.endsWith("/") && !path.contains("//"), "Invalid icon path");

        String[] parts = path.split("/");

        for (String part : parts) {
            require(
                    isSafeText(part)
                            && !part.isEmpty()
                            && !part.equals(".")
                            && !part.equals(".."),
                    "Invalid icon path");
        }

        String lower = path.toLowerCase(Locale.ROOT);

        require(
                lower.endsWith(".svg") || lower.endsWith(".png") || lower.endsWith(".webp"),
                "Unsupported icon format");

        return path;
    }

    static void require(boolean condition, String message) throws IOException {
        if (!condition) {
            throw new IconPackArchiveValidator.InvalidArchiveException(message);
        }
    }

    static boolean isSafeText(String value) {
        if (value == null) {
            return false;
        }

        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);

            if (Character.isISOControl(current) || Character.isLowSurrogate(current)) {
                return false;
            }
            if (Character.isHighSurrogate(current)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    return false;
                }

                i++;
            }
        }

        return true;
    }

    private static String readString(JsonReader reader, String field) throws IOException {
        require(reader.peek() == JsonToken.STRING, "Expected string for " + field);

        return reader.nextString();
    }

    private static void readIcons(JsonReader reader, LinkedHashMap<String, String> icons)
            throws IOException {
        require(reader.peek() == JsonToken.BEGIN_OBJECT, "Expected object for icons");

        reader.beginObject();

        while (reader.hasNext()) {
            String resourceName = reader.nextName();

            require(isValidResourceName(resourceName), "Invalid icon resource name");
            require(icons.size() < MAX_ICONS, "Too many icons");
            require(reader.peek() == JsonToken.STRING, "Expected icon path");

            String path = validateIconPath(reader.nextString());

            require(icons.put(resourceName, path) == null, "Duplicate icon resource name");
        }

        reader.endObject();
    }
}

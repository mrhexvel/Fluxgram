package org.telegram.messenger.plugins.packages;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PluginManifest {
    public static final int SCHEMA_VERSION = 2;
    public static final int SDK_VERSION = 2;
    public static final int SDK_MINOR_VERSION = 4;
    public final int schemaVersion, sdkVersion, minSdkMinor;
    public final String access = "full";
    public final String id, name, version, description, author, entrypoint, icon;
    public final List<String> declaredCapabilities;

    private PluginManifest(
            int schemaVersion,
            int sdkVersion,
            int minSdkMinor,
            String id,
            String name,
            String version,
            String description,
            String author,
            String entrypoint,
            String icon,
            List<String> capabilities) {
        this.schemaVersion = schemaVersion;
        this.sdkVersion = sdkVersion;
        this.minSdkMinor = minSdkMinor;
        this.id = id;
        this.name = name;
        this.version = version;
        this.description = description;
        this.author = author;
        this.entrypoint = entrypoint;
        this.icon = icon;
        this.declaredCapabilities = Collections.unmodifiableList(new ArrayList<>(capabilities));
    }

    public static PluginManifest parse(String json) throws IOException {
        int schemaVersion = -1, sdkVersion = -1, minSdkMinor = -1;
        String id = null,
                name = null,
                version = null,
                description = null,
                author = null,
                entrypoint = null,
                icon = null;

        List<String> capabilities = new ArrayList<>();
        Set<String> fields = new HashSet<>();

        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setStrictness(Strictness.STRICT);
            reader.beginObject();

            while (reader.hasNext()) {
                String field = reader.nextName();
                require(fields.add(field), "Duplicate manifest field: " + field);

                switch (field) {
                    case "schema_version":
                        schemaVersion = version(reader, field);
                        break;
                    case "sdk_version":
                        sdkVersion = version(reader, field);
                        break;
                    case "min_sdk_minor":
                        minSdkMinor = version(reader, field);
                        break;
                    case "id":
                        id = string(reader, 100);
                        break;
                    case "name":
                        name = string(reader, 80);
                        break;
                    case "version":
                        version = string(reader, 64);
                        break;
                    case "description":
                        description = string(reader, 2000);
                        break;
                    case "author":
                        author = string(reader, 100);
                        break;
                    case "entrypoint":
                        entrypoint = string(reader, 128);
                        break;
                    case "icon":
                        icon = string(reader, 240);
                        PluginPackageValidator.validateIconPath(icon);
                        break;
                    case "access":
                        require("full".equals(string(reader, 20)), "Only full access is supported");
                        break;
                    case "declared_capabilities":
                        reader.beginArray();
                        while (reader.hasNext()) {
                            require(capabilities.size() < 32, "Too many declared capabilities");

                            String capability = string(reader, 160);
                            require(
                                    !capabilities.contains(capability),
                                    "Duplicate declared capability");
                            capabilities.add(capability);
                        }
                        reader.endArray();
                        break;
                    default:
                        throw new IOException("Unknown manifest field: " + field);
                }
            }

            reader.endObject();
            require(reader.peek() == JsonToken.END_DOCUMENT, "Unexpected data after manifest");
        } catch (IllegalStateException | IllegalArgumentException error) {
            throw new IOException("Invalid manifest JSON", error);
        }

        for (String field :
                new String[] {
                    "schema_version",
                    "sdk_version",
                    "id",
                    "name",
                    "version",
                    "description",
                    "author",
                    "entrypoint",
                    "access",
                    "declared_capabilities"
                }) {
            require(fields.contains(field), "Missing manifest field: " + field);
        }

        require(
                schemaVersion == 1 || schemaVersion == SCHEMA_VERSION,
                "Unsupported schema_version");

        if (schemaVersion == 1) {
            require(sdkVersion == 1, "Unsupported sdk_version for schema_version 1");
            require(!fields.contains("min_sdk_minor"), "Unknown manifest field: min_sdk_minor");
            minSdkMinor = 0;
        } else {
            require(fields.contains("min_sdk_minor"), "Missing manifest field: min_sdk_minor");
        }

        require(id.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"), "Invalid plugin id");

        require(
                !id.startsWith("fluxgram."),
                "The fluxgram namespace is reserved for built-in plugins");

        require(
                version.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"),
                "Version must have the form major.minor.patch");

        require(
                entrypoint.matches("[a-zA-Z_][a-zA-Z0-9_]*:PluginEntry"),
                "Entrypoint must be a root Python module followed by :PluginEntry");

        require(
                !entrypoint.equals("fluxgram:PluginEntry"),
                "The SDK module cannot be an entrypoint");

        return new PluginManifest(
                schemaVersion,
                sdkVersion,
                minSdkMinor,
                id,
                name,
                version,
                description,
                author,
                entrypoint,
                icon,
                capabilities);
    }

    public String entryFile() {
        return entrypoint.substring(0, entrypoint.indexOf(':')) + ".py";
    }

    public boolean isCompatible() {
        return schemaVersion == SCHEMA_VERSION
                && sdkVersion == SDK_VERSION
                && minSdkMinor <= SDK_MINOR_VERSION;
    }

    public String compatibilityError() {
        if (schemaVersion != SCHEMA_VERSION) {
            return "Plugin manifest schema " + schemaVersion
                    + " is incompatible with schema " + SCHEMA_VERSION;
        }

        return "Plugin requires SDK " + sdkVersion + "." + minSdkMinor
                + ", but FluxGram provides SDK " + SDK_VERSION + "." + SDK_MINOR_VERSION;
    }

    private static String string(JsonReader reader, int limit) throws IOException {
        require(reader.peek() == JsonToken.STRING, "Expected a string");

        String value = reader.nextString();

        require(
                !value.trim().isEmpty() && value.length() <= limit,
                "Empty or oversized manifest string");

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            require(
                    !Character.isISOControl(c) || c == '\n' || c == '\t',
                    "Invalid control character");
            require(
                    c < '\u202a' || c > '\u202e',
                    "Bidirectional formatting controls are not allowed");
            require(
                    c < '\u2066' || c > '\u2069',
                    "Bidirectional formatting controls are not allowed");
        }

        return value;
    }

    private static int version(JsonReader reader, String field) throws IOException {
        require(reader.peek() == JsonToken.NUMBER, "Expected numeric " + field);

        String value = reader.nextString();
        require(value.matches("0|[1-9][0-9]*"), "Invalid " + field);

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw new IOException("Invalid " + field, error);
        }
    }

    static void require(boolean condition, String message) throws IOException {
        if (!condition) {
            throw new IOException(message);
        }
    }
}

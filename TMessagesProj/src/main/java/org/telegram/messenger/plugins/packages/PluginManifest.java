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
    public static final int SCHEMA_VERSION = 5;
    public static final int SDK_VERSION = 2;
    public static final int SDK_MINOR_VERSION = 9;
    public static final int JAVA_API_VERSION = 1;
    public static final int JAVA_API_MINOR_VERSION = 21;
    public static final String HOST_FINGERPRINT =
            "e00a486e8d50a882bf33ad91e62b558eb47e9b0b529ae2b504843fad7f863cb6";
    public final int schemaVersion, sdkVersion, minSdkMinor;
    public final String access = "full";
    public final String id, name, version, description, author, entrypoint, icon;
    public final List<String> declaredCapabilities;
    public final JavaModule javaModule;

    public static final class JavaModule {
        public final String path, entrypoint, sha256, hostFingerprint;
        public final int apiVersion, minApiMinor;

        private JavaModule(String path, String entrypoint, String sha256,
                int apiVersion, int minApiMinor, String hostFingerprint) {
            this.path = path;
            this.entrypoint = entrypoint;
            this.sha256 = sha256;
            this.apiVersion = apiVersion;
            this.minApiMinor = minApiMinor;
            this.hostFingerprint = hostFingerprint;
        }

        public boolean isCompatible() {
            return apiVersion == JAVA_API_VERSION && minApiMinor <= JAVA_API_MINOR_VERSION
                    && (hostFingerprint == null || HOST_FINGERPRINT.equals(hostFingerprint));
        }

        public boolean supportsLowLevelHost() {
            return HOST_FINGERPRINT.equals(hostFingerprint);
        }
    }

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
            List<String> capabilities,
            JavaModule javaModule) {
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
        this.javaModule = javaModule;
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
        JavaModule javaModule = null;

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
                    case "java_module":
                        javaModule = javaModule(reader);
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
                    "id",
                    "name",
                    "version",
                    "description",
                    "author",
                    "access",
                    "declared_capabilities"
                }) {
            require(fields.contains(field), "Missing manifest field: " + field);
        }

        require(
                schemaVersion >= 1 && schemaVersion <= SCHEMA_VERSION,
                "Unsupported schema_version");

        boolean anyPythonField = fields.contains("sdk_version")
                || fields.contains("min_sdk_minor")
                || fields.contains("entrypoint");
        boolean allPythonFields = fields.contains("sdk_version")
                && fields.contains("entrypoint")
                && (schemaVersion == 1 || fields.contains("min_sdk_minor"));

        if (schemaVersion <= 3) {
            require(allPythonFields, "Missing Python module field");
        } else {
            require(!anyPythonField || allPythonFields, "Incomplete Python module");
            require(allPythonFields || javaModule != null, "Manifest must declare a module");
        }

        if (schemaVersion == 1) {
            require(sdkVersion == 1, "Unsupported sdk_version for schema_version 1");
            require(!fields.contains("min_sdk_minor"), "Unknown manifest field: min_sdk_minor");
            minSdkMinor = 0;
        } else if (allPythonFields) {
            require(fields.contains("min_sdk_minor"), "Missing manifest field: min_sdk_minor");
        }

        require(schemaVersion >= 3 || javaModule == null,
                "java_module requires schema_version 3 or newer");
        require(schemaVersion != 3 || javaModule != null,
                "java_module is required in schema_version 3");
        require(schemaVersion != 5 || javaModule != null,
                "java_module is required in schema_version 5");
        require(javaModule == null || javaModule.hostFingerprint == null || schemaVersion == 5,
                "host_fingerprint requires schema_version 5");
        require(schemaVersion != 5 || javaModule.hostFingerprint != null,
                "host_fingerprint is required in schema_version 5");

        require(id.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"), "Invalid plugin id");

        require(
                !id.startsWith("fluxgram."),
                "The fluxgram namespace is reserved for built-in plugins");

        require(
                version.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"),
                "Version must have the form major.minor.patch");

        if (allPythonFields) {
            require(
                    entrypoint.matches("[a-zA-Z_][a-zA-Z0-9_]*:PluginEntry"),
                    "Entrypoint must be a root Python module followed by :PluginEntry");

            require(
                    !entrypoint.equals("fluxgram:PluginEntry"),
                    "The SDK module cannot be an entrypoint");
        }

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
                capabilities,
                javaModule);
    }

    private static JavaModule javaModule(JsonReader reader) throws IOException {
        String path = null, entrypoint = null, sha256 = null, hostFingerprint = null;
        int apiVersion = -1, minApiMinor = -1;
        Set<String> fields = new HashSet<>();
        reader.beginObject();

        while (reader.hasNext()) {
            String field = reader.nextName();
            require(fields.add(field), "Duplicate Java module field: " + field);

            switch (field) {
                case "path":
                    path = string(reader, 240);
                    PluginPackageValidator.validatePath(path, false);
                    require(path.endsWith(".dex"), "Java module must be a DEX file");
                    break;
                case "entrypoint":
                    entrypoint = string(reader, 240);
                    require(entrypoint.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_$]*)+"),
                            "Invalid Java entrypoint");
                    break;
                case "sha256":
                    sha256 = string(reader, 64);
                    require(sha256.matches("[a-f0-9]{64}"), "Invalid DEX SHA-256");
                    break;
                case "api_version":
                    apiVersion = version(reader, field);
                    break;
                case "min_api_minor":
                    minApiMinor = version(reader, field);
                    break;
                case "host_fingerprint":
                    hostFingerprint = string(reader, 64);
                    require(hostFingerprint.matches("[a-f0-9]{64}"),
                            "Invalid host fingerprint");
                    break;
                default:
                    throw new IOException("Unknown Java module field: " + field);
            }
        }

        reader.endObject();
        require(path != null && entrypoint != null && sha256 != null
                && apiVersion >= 0 && minApiMinor >= 0, "Incomplete java_module");

        return new JavaModule(
                path, entrypoint, sha256, apiVersion, minApiMinor, hostFingerprint);
    }

    public String entryFile() {
        if (!hasPythonModule()) {
            return null;
        }

        return entrypoint.substring(0, entrypoint.indexOf(':')) + ".py";
    }

    public boolean hasPythonModule() {
        return entrypoint != null;
    }

    public boolean hasJavaModule() {
        return javaModule != null;
    }

    public boolean isCompatible() {
        return schemaVersion >= 2 && schemaVersion <= SCHEMA_VERSION
                && (!hasPythonModule()
                        || sdkVersion == SDK_VERSION && minSdkMinor <= SDK_MINOR_VERSION)
                && (javaModule == null || javaModule.isCompatible());
    }

    public String compatibilityError() {
        if (schemaVersion < 2 || schemaVersion > SCHEMA_VERSION) {
            return "Plugin manifest schema " + schemaVersion
                    + " is incompatible with schema " + SCHEMA_VERSION;
        }

        if (javaModule != null && javaModule.hostFingerprint != null
                && !HOST_FINGERPRINT.equals(javaModule.hostFingerprint)) {
            return "Plugin requires a different low-level host contract";
        }

        if (javaModule != null && !javaModule.isCompatible()) {
            return "Plugin requires Java API " + javaModule.apiVersion + "." + javaModule.minApiMinor
                    + ", but FluxGram provides Java API " + JAVA_API_VERSION + "." + JAVA_API_MINOR_VERSION;
        }

        if (hasPythonModule()) {
            return "Plugin requires SDK " + sdkVersion + "." + minSdkMinor
                    + ", but FluxGram provides SDK " + SDK_VERSION + "." + SDK_MINOR_VERSION;
        }

        return "Plugin manifest is incompatible";
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

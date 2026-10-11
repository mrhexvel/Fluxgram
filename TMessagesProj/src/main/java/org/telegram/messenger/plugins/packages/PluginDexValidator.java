package org.telegram.messenger.plugins.packages;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.telegram.messenger.plugins.packages.PluginManifest.require;

public final class PluginDexValidator {
    private PluginDexValidator() {
    }

    public static byte[] read(InputStream in, PluginManifest.JavaModule module) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;

        while ((count = in.read(buffer)) != -1) {
            require(out.size() + count <= PluginPackageValidator.MAX_FILE_BYTES, "DEX exceeds size limit");
            out.write(buffer, 0, count);
        }

        byte[] data = out.toByteArray();

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder hash = new StringBuilder();

            for (byte value : digest) {
                hash.append(Character.forDigit((value & 255) >>> 4, 16));
                hash.append(Character.forDigit(value & 15, 16));
            }

            require(hash.toString().equals(module.sha256), "DEX SHA-256 mismatch");
        } catch (NoSuchAlgorithmException error) {
            throw new IOException(error);
        }

        validateClasses(data, module.entrypoint);
        return data;
    }

    private static void validateClasses(byte[] data, String entrypoint) throws IOException {
        require(data.length >= 112, "Truncated DEX");
        String magic = new String(data, 0, 8, StandardCharsets.US_ASCII);
        require(magic.matches("dex\\n0(35|37|38|39)\u0000"), "Unsupported DEX version");
        ByteBuffer dex = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        require(dex.getInt(32) == data.length && dex.getInt(36) == 112
                && dex.getInt(40) == 0x12345678, "Invalid DEX header");
        int strings = dex.getInt(56), stringsOffset = dex.getInt(60);
        int types = dex.getInt(64), typesOffset = dex.getInt(68);
        int classes = dex.getInt(96), classesOffset = dex.getInt(100);
        table(data.length, stringsOffset, strings, 4);
        table(data.length, typesOffset, types, 4);
        table(data.length, classesOffset, classes, 32);
        String expected = "L" + entrypoint.replace('.', '/') + ";";
        boolean found = false;

        for (int i = 0; i < classes; i++) {
            int type = dex.getInt(classesOffset + i * 32);
            require(type >= 0 && type < types, "Invalid DEX class type");
            int string = dex.getInt(typesOffset + type * 4);
            require(string >= 0 && string < strings, "Invalid DEX type descriptor");
            int offset = dex.getInt(stringsOffset + string * 4);
            String descriptor = descriptor(data, offset);
            require(!descriptor.startsWith("Lorg/telegram/")
                    && !descriptor.startsWith("Landroid/")
                    && !descriptor.startsWith("Ljava/")
                    && !descriptor.startsWith("Ljavax/")
                    && !descriptor.startsWith("Ldalvik/")
                    && !descriptor.startsWith("Lcom/chaquo/"), "DEX bundles host/platform classes");
            found |= descriptor.equals(expected);
        }

        require(found, "Java entrypoint is not defined in DEX");
    }

    private static void table(int length, int offset, int count, int width) throws IOException {
        require(offset >= 112 && count > 0 && (long) offset + (long) count * width <= length,
                "Invalid DEX table");
    }

    private static String descriptor(byte[] data, int offset) throws IOException {
        require(offset >= 112 && offset < data.length, "Invalid DEX string offset");
        int start = offset;

        for (int i = 0; ; i++) {
            require(i < 5 && start < data.length, "Invalid DEX string length");

            if ((data[start++] & 128) == 0) {
                break;
            }
        }

        int end = start;

        while (end < data.length && data[end] != 0) {
            require(end - start < 1024, "DEX class descriptor too long");
            end++;
        }

        require(end < data.length, "Unterminated DEX descriptor");
        String value = new String(data, start, end - start, StandardCharsets.UTF_8);
        require(value.startsWith("L") && value.endsWith(";"), "Invalid DEX class descriptor");
        return value;
    }
}

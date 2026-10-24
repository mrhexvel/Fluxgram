package org.telegram.messenger.fluxgram.icons;

import android.content.SharedPreferences;

import org.telegram.messenger.MessagesController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

public final class IconPackSettings {
    private static final String ACTIVE_ORDER = "fluxgram_icon_pack_active_order";
    private static final String PENDING_DELETE_ID = "fluxgram_icon_pack_pending_delete_id";
    private static final String PENDING_DELETE_GENERATION =
            "fluxgram_icon_pack_pending_delete_generation";
    private static final String PENDING_DELETE_ORDER = "fluxgram_icon_pack_pending_delete_order";
    private static final String FORMAT_PREFIX = "1;";
    private static final int MAX_ENCODED_LENGTH = 8192;

    private IconPackSettings() {
    }

    public static synchronized List<String> getActiveIds(IconPackStore store) throws IOException {
        require(store != null, "Icon pack store is required");

        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        List<IconPackStore.Installed> installed = store.list();
        recoverPendingDelete(preferences, installed);
        List<String> stored = read(preferences);
        List<String> normalized = normalize(stored, installed);
        String encoded = encode(normalized);

        if (preferences.contains(ACTIVE_ORDER) && !encoded.equals(readRaw(preferences))) {
            persistOrder(preferences, normalized);
        }

        return normalized;
    }

    public static synchronized void setActiveIds(
            Collection<String> activeIds,
            IconPackStore store) throws IOException {
        require(activeIds != null, "Active icon pack IDs are required");
        require(store != null, "Icon pack store is required");

        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        List<IconPackStore.Installed> installed = store.list();
        recoverPendingDelete(preferences, installed);
        List<String> normalized = normalize(activeIds, installed);

        persistOrder(preferences, normalized);
    }

    public static synchronized void delete(IconPackStore store, String id) throws IOException {
        require(store != null, "Icon pack store is required");
        require(IconPackManifest.isValidId(id), "Valid icon pack ID is required");

        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        List<IconPackStore.Installed> installed = store.list();
        recoverPendingDelete(preferences, installed);
        List<String> previous = normalize(read(preferences), installed);
        ArrayList<String> remaining = new ArrayList<>(previous);
        remaining.remove(id);
        String generation = generation(installed, id);

        if (!preferences.edit()
                .putString(ACTIVE_ORDER, encode(remaining))
                .putString(PENDING_DELETE_ID, id)
                .putString(PENDING_DELETE_GENERATION, generation == null ? "" : generation)
                .putString(PENDING_DELETE_ORDER, encode(previous))
                .commit()) {
            throw new IOException("Cannot save icon pack state");
        }

        try {
            store.delete(id);
        } catch (IOException error) {
            try {
                recoverPendingDelete(preferences, store.list());
            } catch (IOException recoveryError) {
                error.addSuppressed(recoveryError);
            }

            throw error;
        }

        clearPendingDelete(preferences);
    }

    public static synchronized void reset() throws IOException {
        if (!MessagesController.getGlobalMainSettings()
                .edit()
                .remove(ACTIVE_ORDER)
                .remove(PENDING_DELETE_ID)
                .remove(PENDING_DELETE_GENERATION)
                .remove(PENDING_DELETE_ORDER)
                .commit()) {
            throw new IOException("Cannot reset icon pack state");
        }
    }

    static List<String> normalize(
            Collection<String> activeIds,
            List<IconPackStore.Installed> installed) {
        HashSet<String> available = new HashSet<>();

        if (installed != null) {
            for (IconPackStore.Installed pack : installed) {
                if (pack != null && IconPackManifest.isValidId(pack.manifest.id)) {
                    available.add(pack.manifest.id);
                }
            }
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();

        if (activeIds != null) {
            for (String id : activeIds) {
                if (available.contains(id)) {
                    normalized.add(id);

                    if (normalized.size() == IconPackStore.MAX_PACKS) {
                        break;
                    }
                }
            }
        }

        return Collections.unmodifiableList(new ArrayList<>(normalized));
    }

    static String encode(Collection<String> ids) {
        StringBuilder result = new StringBuilder(FORMAT_PREFIX);
        result.append(ids.size()).append(';');

        for (String id : ids) {
            result.append(id.length()).append(':').append(id);
        }

        return result.toString();
    }

    static List<String> decode(String value) {
        if (value == null
                || value.length() > MAX_ENCODED_LENGTH
                || !value.startsWith(FORMAT_PREFIX)) {
            throw new IllegalArgumentException("Invalid icon pack order");
        }

        int countEnd = value.indexOf(';', FORMAT_PREFIX.length());

        if (countEnd < 0) {
            throw new IllegalArgumentException("Invalid icon pack order");
        }

        int count = parseNumber(value, FORMAT_PREFIX.length(), countEnd);

        if (count > IconPackStore.MAX_PACKS) {
            throw new IllegalArgumentException("Invalid icon pack order");
        }

        ArrayList<String> result = new ArrayList<>(count);
        int offset = countEnd + 1;

        for (int index = 0; index < count; index++) {
            int lengthEnd = value.indexOf(':', offset);

            if (lengthEnd < 0) {
                throw new IllegalArgumentException("Invalid icon pack order");
            }

            int length = parseNumber(value, offset, lengthEnd);
            int idStart = lengthEnd + 1;
            int idEnd = idStart + length;

            if (length > 128 || idEnd < idStart || idEnd > value.length()) {
                throw new IllegalArgumentException("Invalid icon pack order");
            }

            String id = value.substring(idStart, idEnd);

            if (!IconPackManifest.isValidId(id)) {
                throw new IllegalArgumentException("Invalid icon pack order");
            }

            result.add(id);
            offset = idEnd;
        }

        if (offset != value.length()) {
            throw new IllegalArgumentException("Invalid icon pack order");
        }

        return result;
    }

    private static List<String> read(SharedPreferences preferences) {
        String raw = readRaw(preferences);

        if (raw == null) {
            return Collections.emptyList();
        }

        try {
            return decode(raw);
        } catch (IllegalArgumentException exception) {
            return Collections.emptyList();
        }
    }

    private static String readRaw(SharedPreferences preferences) {
        try {
            return preferences.getString(ACTIVE_ORDER, null);
        } catch (ClassCastException exception) {
            return null;
        }
    }

    private static void recoverPendingDelete(
            SharedPreferences preferences,
            List<IconPackStore.Installed> installed) throws IOException {
        String pendingId = readString(preferences, PENDING_DELETE_ID);
        String pendingGeneration = readString(preferences, PENDING_DELETE_GENERATION);
        String pendingOrder = readString(preferences, PENDING_DELETE_ORDER);
        boolean hasPendingId = preferences.contains(PENDING_DELETE_ID);
        boolean hasPendingGeneration = preferences.contains(PENDING_DELETE_GENERATION);
        boolean hasPendingOrder = preferences.contains(PENDING_DELETE_ORDER);

        if (!hasPendingId && !hasPendingGeneration && !hasPendingOrder) {
            return;
        }

        if (!hasPendingId
                || !hasPendingGeneration
                || !hasPendingOrder
                || !IconPackManifest.isValidId(pendingId)
                || pendingGeneration == null
                || pendingOrder == null) {
            clearPendingDelete(preferences);

            return;
        }

        List<String> previous;

        try {
            previous = decode(pendingOrder);
        } catch (IllegalArgumentException exception) {
            clearPendingDelete(preferences);

            return;
        }

        boolean stillInstalled = false;

        for (IconPackStore.Installed pack : installed) {
            if (pack != null
                    && pendingId.equals(pack.manifest.id)
                    && pendingGeneration.equals(pack.generation())) {
                stillInstalled = true;
                break;
            }
        }

        List<String> recovered = stillInstalled
                ? normalize(previous, installed)
                : normalize(read(preferences), installed);

        if (!preferences.edit()
                .putString(ACTIVE_ORDER, encode(recovered))
                .remove(PENDING_DELETE_ID)
                .remove(PENDING_DELETE_GENERATION)
                .remove(PENDING_DELETE_ORDER)
                .commit()) {
            throw new IOException("Cannot recover icon pack state");
        }
    }

    private static void persistOrder(
            SharedPreferences preferences,
            Collection<String> activeIds) throws IOException {
        if (!preferences.edit().putString(ACTIVE_ORDER, encode(activeIds)).commit()) {
            throw new IOException("Cannot save icon pack state");
        }
    }

    private static void clearPendingDelete(SharedPreferences preferences) throws IOException {
        if (!preferences.edit()
                .remove(PENDING_DELETE_ID)
                .remove(PENDING_DELETE_GENERATION)
                .remove(PENDING_DELETE_ORDER)
                .commit()) {
            throw new IOException("Cannot finish icon pack deletion");
        }
    }

    private static String readString(SharedPreferences preferences, String key) {
        try {
            return preferences.getString(key, null);
        } catch (ClassCastException exception) {
            return null;
        }
    }

    private static String generation(List<IconPackStore.Installed> installed, String id) {
        for (IconPackStore.Installed pack : installed) {
            if (pack != null && id.equals(pack.manifest.id)) {
                return pack.generation();
            }
        }

        return null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static int parseNumber(String value, int start, int end) {
        if (start >= end || end - start > 4) {
            throw new IllegalArgumentException("Invalid icon pack order");
        }

        int result = 0;

        for (int index = start; index < end; index++) {
            char character = value.charAt(index);

            if (character < '0' || character > '9') {
                throw new IllegalArgumentException("Invalid icon pack order");
            }

            result = result * 10 + character - '0';
        }

        return result;
    }
}

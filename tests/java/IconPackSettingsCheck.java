package org.telegram.messenger.fluxgram.icons;

import android.content.MemorySharedPreferences;

import org.telegram.messenger.MessagesController;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class IconPackSettingsCheck {
    public static void main(String[] args) throws Exception {
        MemorySharedPreferences preferences = new MemorySharedPreferences();
        MessagesController.preferences = preferences;

        IconPackStore store = new IconPackStore(
                new File(args[0]),
                new File(args[1]),
                catalog());
        store.install(new File(args[2]), args[3]);
        store.install(new File(args[4]), args[5]);
        store.install(new File(args[6]), args[7]);

        require(IconPackSettings.getActiveIds(store).isEmpty());

        IconPackSettings.setActiveIds(
                Arrays.asList("comma,:; pack", "missing", "alpha", "alpha", "Солнечный 🌞"),
                store);

        List<String> active = IconPackSettings.getActiveIds(store);
        require(active.equals(Arrays.asList("comma,:; pack", "alpha", "Солнечный 🌞")));
        require(preferences.getString("fluxgram_icon_pack_active_order", null)
                .equals(IconPackSettings.encode(active)));

        try {
            active.add("missing");
            throw new AssertionError();
        } catch (UnsupportedOperationException expected) {
        }

        store.install(new File(args[8]), args[9]);
        require(IconPackSettings.getActiveIds(store).equals(active));

        IconPackSettings.delete(store, "comma,:; pack");
        require(IconPackSettings.getActiveIds(store).equals(Arrays.asList("alpha", "Солнечный 🌞")));

        store.install(new File(args[4]), args[5]);
        require(!IconPackSettings.getActiveIds(store).contains("comma,:; pack"));

        IconPackSettings.setActiveIds(Collections.singletonList("comma,:; pack"), store);
        String punctuationGeneration = store.get("comma,:; pack").generation();
        preferences.edit()
                .putString("fluxgram_icon_pack_active_order", "1;0;")
                .putString("fluxgram_icon_pack_pending_delete_id", "comma,:; pack")
                .putString(
                        "fluxgram_icon_pack_pending_delete_generation",
                        punctuationGeneration)
                .putString(
                        "fluxgram_icon_pack_pending_delete_order",
                        IconPackSettings.encode(Collections.singletonList("comma,:; pack")))
                .commit();
        require(IconPackSettings.getActiveIds(store)
                .equals(Collections.singletonList("comma,:; pack")));

        preferences.edit()
                .putString("fluxgram_icon_pack_active_order", "1;0;")
                .putString("fluxgram_icon_pack_pending_delete_id", "comma,:; pack")
                .putString(
                        "fluxgram_icon_pack_pending_delete_generation",
                        punctuationGeneration)
                .putString(
                        "fluxgram_icon_pack_pending_delete_order",
                        IconPackSettings.encode(Collections.singletonList("comma,:; pack")))
                .commit();
        store.delete("comma,:; pack");
        store.install(new File(args[4]), args[5]);
        require(IconPackSettings.getActiveIds(store).isEmpty());

        IconPackSettings.setActiveIds(Collections.singletonList("comma,:; pack"), store);
        preferences.failNextCommit();
        expectIo(() -> IconPackSettings.delete(store, "comma,:; pack"));
        require(store.get("comma,:; pack") != null);
        require(IconPackSettings.getActiveIds(store)
                .equals(Collections.singletonList("comma,:; pack")));

        preferences.edit()
                .putString("fluxgram_icon_pack_active_order", "1;2;5:alpha4:bad")
                .apply();
        require(IconPackSettings.getActiveIds(store).isEmpty());
        require(preferences.getString("fluxgram_icon_pack_active_order", null).equals("1;0;"));

        preferences.putWrongType("fluxgram_icon_pack_active_order");
        require(IconPackSettings.getActiveIds(store).isEmpty());
        require(preferences.getString("fluxgram_icon_pack_active_order", null).equals("1;0;"));

        expectInvalid("1;33;");
        expectInvalid("1;1;129:" + repeat('a', 129));
        expectInvalid("1;1;2:\ud800x");
        expectInvalid("1;0;tail");
        expectInvalid(repeat('a', 8193));

        expectInvalidArgument(() -> IconPackSettings.getActiveIds(null));
        expectInvalidArgument(() -> IconPackSettings.setActiveIds(null, store));
        expectInvalidArgument(() -> IconPackSettings.setActiveIds(active, null));
        expectInvalidArgument(() -> IconPackSettings.delete(null, "alpha"));
        expectInvalidArgument(() -> IconPackSettings.delete(store, null));

        IconPackSettings.reset();
        require(IconPackSettings.getActiveIds(store).isEmpty());
        require(!preferences.contains("fluxgram_icon_pack_active_order"));
    }

    private static IconPackArchiveValidator.Catalog catalog() {
        return new IconPackArchiveValidator.Catalog() {
            @Override
            public boolean contains(String resourceName) {
                return "msg_add".equals(resourceName);
            }

            @Override
            public boolean decodesSvg(byte[] bytes, String path) {
                String value = new String(bytes, StandardCharsets.UTF_8);

                return value.contains("<svg");
            }
        };
    }

    private static void expectInvalid(String value) {
        try {
            IconPackSettings.decode(value);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
        }
    }

    private static void expectIo(ThrowingRunnable runnable) {
        try {
            runnable.run();
            throw new AssertionError();
        } catch (IOException expected) {
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static void expectInvalidArgument(ThrowingRunnable runnable) {
        try {
            runnable.run();
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static String repeat(char character, int count) {
        char[] result = new char[count];
        Arrays.fill(result, character);

        return new String(result);
    }

    private static void require(boolean value) {
        if (!value) {
            throw new AssertionError();
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}

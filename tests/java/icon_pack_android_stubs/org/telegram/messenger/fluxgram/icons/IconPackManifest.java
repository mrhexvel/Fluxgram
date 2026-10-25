package org.telegram.messenger.fluxgram.icons;

import java.util.Map;

public final class IconPackManifest {
    public final String id;
    public final Map<String, String> icons;

    public IconPackManifest(Map<String, String> icons) {
        this("test.pack", icons);
    }

    public IconPackManifest(String id, Map<String, String> icons) {
        this.id = id;
        this.icons = icons;
    }

    public static boolean isValidResourceName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,128}");
    }
}

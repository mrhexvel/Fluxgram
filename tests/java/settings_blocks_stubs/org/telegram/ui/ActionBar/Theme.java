package org.telegram.ui.ActionBar;

public final class Theme {
    public static final int key_windowBackgroundWhite = 10;
    public static final int key_windowBackgroundWhiteBlackText = 11;
    public static final int key_windowBackgroundWhiteGrayText = 12;
    public static final int key_windowBackgroundWhiteBlueText = 13;
    public static final int key_divider = 14;

    public interface ResourcesProvider {
    }

    public static int getColor(int key, ResourcesProvider provider) {
        return key + 100;
    }
}

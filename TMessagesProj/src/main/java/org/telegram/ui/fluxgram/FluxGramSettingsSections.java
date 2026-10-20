package org.telegram.ui.fluxgram;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.fluxgram.AppearanceSettings;
import org.telegram.ui.Components.UniversalRecyclerView;

public final class FluxGramSettingsSections {
    private FluxGramSettingsSections() {
    }

    public static void apply(UniversalRecyclerView listView, int account) {
        if (listView == null) {
            return;
        }

        int dividerStyle = AppearanceSettings.getSettingsDividerStyle(account);
        listView.adapter.setDividersEnabled(dividerStyle == AppearanceSettings.DIVIDER_LINE);
        applyRadius(listView, AppearanceSettings.getSettingsSectionRadius(account));
        listView.setSegmentedSections(dividerStyle == AppearanceSettings.DIVIDER_SEGMENTS);
    }

    public static boolean showsDividers(int account) {
        return AppearanceSettings.getSettingsDividerStyle(account) == AppearanceSettings.DIVIDER_LINE;
    }

    public static void applyRadius(UniversalRecyclerView listView, int radius) {
        if (listView == null) {
            return;
        }

        radius = Math.max(0, Math.min(AppearanceSettings.MAX_SETTINGS_SECTION_RADIUS, radius));
        listView.setSections(AndroidUtilities.dp(12), AndroidUtilities.dp(radius), false);
    }
}

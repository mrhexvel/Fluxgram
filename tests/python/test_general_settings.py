from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"


class GeneralSettingsTest(unittest.TestCase):
    def test_full_number_format_has_one_central_integration(self):
        settings = (JAVA / "messenger/fluxgram/GeneralSettings.java").read_text()
        locale = (JAVA / "messenger/LocaleController.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        self.assertIn('FULL_NUMBER_FORMAT = "fluxgram_full_number_format"', settings)
        self.assertIn("getBoolean(FULL_NUMBER_FORMAT, false)", settings)
        self.assertIn("NumberFormat.getIntegerInstance(resolvedLocale)", settings)
        self.assertEqual(1, locale.count("GeneralSettings.usesFullNumberFormat()"))
        self.assertIn("rounded[0] = number;", settings)
        self.assertIn("return GeneralSettings.formatFullNumber(number, rounded, locale);", locale)
        self.assertIn("addSectionLabel(items, R.string.FluxGramFormatting);", activity)
        self.assertIn(".setChecked(GeneralSettings.usesFullNumberFormat())", activity)
        self.assertIn("GeneralSettings.setUsesFullNumberFormat(enabled);", activity)

    def test_time_seconds_reuses_time_of_day_formatter(self):
        settings = (JAVA / "messenger/fluxgram/GeneralSettings.java").read_text()
        locale = (JAVA / "messenger/LocaleController.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()
        formatting_section = activity.split(
            "addSectionLabel(items, R.string.FluxGramFormatting);", 1
        )[1].split(
            "addSectionLabel(items, R.string.FluxGramNotifications);", 1
        )[0]

        self.assertIn('SHOW_TIME_SECONDS = "fluxgram_show_time_seconds"', settings)
        self.assertIn("getBoolean(SHOW_TIME_SECONDS, false)", settings)
        self.assertEqual(1, locale.count("GeneralSettings.showsTimeSeconds()"))
        formatter_day = locale.split("public FastDateFormat getFormatterDay()", 1)[1].split(
            "private volatile FastDateFormat formatterDayWithSeconds", 1
        )[0]
        self.assertIn("return getFormatterDayWithSeconds();", formatter_day)
        self.assertIn("UItem.asCheck(\n                    FULL_NUMBER_FORMAT", formatting_section)
        self.assertIn("UItem.asCheck(\n                    SHOW_TIME_SECONDS", formatting_section)
        self.assertEqual(1, formatting_section.count("UItem.asShadow(null)"))
        self.assertIn("GeneralSettings.setShowsTimeSeconds(enabled);", activity)
        self.assertIn("LocaleController.getInstance().recreateFormatters();", activity)

    def test_relative_last_seen_preserves_online_and_privacy_statuses(self):
        settings = (JAVA / "messenger/fluxgram/GeneralSettings.java").read_text()
        locale = (JAVA / "messenger/LocaleController.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()
        user_status = locale.split(
            "public static String formatUserStatus(int currentAccount, TLRPC.User user, boolean[] isOnline, boolean[] madeShorter)",
            1,
        )[1].split("private String escapeString", 1)[0]
        formatting_section = activity.split(
            "addSectionLabel(items, R.string.FluxGramFormatting);", 1
        )[1].split(
            "addSectionLabel(items, R.string.FluxGramNotifications);", 1
        )[0]

        self.assertIn('RELATIVE_LAST_SEEN = "fluxgram_relative_last_seen"', settings)
        self.assertIn("getBoolean(RELATIVE_LAST_SEEN, false)", settings)
        self.assertEqual(1, locale.count("GeneralSettings.usesRelativeLastSeen()"))
        self.assertLess(
            user_status.index("user.status.expires == -102"),
            user_status.index("GeneralSettings.usesRelativeLastSeen()"),
        )
        self.assertLess(
            user_status.index("GeneralSettings.usesRelativeLastSeen()"),
            user_status.index("return formatDateOnline(user.status.expires, madeShorter);"),
        )
        self.assertIn("(long) currentTime - user.status.expires", user_status)
        self.assertIn("formatRelativeDate(elapsed)", user_status)
        self.assertIn("UItem.asCheck(\n                    RELATIVE_LAST_SEEN", formatting_section)
        self.assertEqual(1, formatting_section.count("UItem.asShadow(null)"))
        self.assertIn("GeneralSettings.setUsesRelativeLastSeen(enabled);", activity)

    def test_locale_aware_formatting_and_persistence(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("java and javac are required")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shared_preferences = root / "android/content/SharedPreferences.java"
            messages_controller = root / "org/telegram/messenger/MessagesController.java"
            check = root / "GeneralSettingsCheck.java"

            shared_preferences.parent.mkdir(parents=True)
            messages_controller.parent.mkdir(parents=True)

            shared_preferences.write_text(textwrap.dedent("""
                package android.content;

                public interface SharedPreferences {
                    boolean getBoolean(String key, boolean fallback);
                    Editor edit();

                    interface Editor {
                        Editor putBoolean(String key, boolean value);
                        void apply();
                    }
                }
            """))
            messages_controller.write_text(textwrap.dedent("""
                package org.telegram.messenger;

                import android.content.SharedPreferences;
                import java.util.HashMap;
                import java.util.Map;

                public final class MessagesController {
                    private static final Preferences PREFERENCES = new Preferences();

                    public static SharedPreferences getGlobalMainSettings() {
                        return PREFERENCES;
                    }

                    private static final class Preferences implements SharedPreferences, SharedPreferences.Editor {
                        private final Map<String, Boolean> values = new HashMap<>();

                        @Override
                        public boolean getBoolean(String key, boolean fallback) {
                            return values.containsKey(key) ? values.get(key) : fallback;
                        }

                        @Override
                        public SharedPreferences.Editor edit() {
                            return this;
                        }

                        @Override
                        public SharedPreferences.Editor putBoolean(String key, boolean value) {
                            values.put(key, value);
                            return this;
                        }

                        @Override
                        public void apply() {
                        }
                    }
                }
            """))
            check.write_text(textwrap.dedent("""
                import java.util.Locale;
                import org.telegram.messenger.fluxgram.GeneralSettings;

                public final class GeneralSettingsCheck {
                    public static void main(String[] args) {
                        if (GeneralSettings.usesFullNumberFormat()) {
                            throw new AssertionError("default must be disabled");
                        }

                        GeneralSettings.setUsesFullNumberFormat(true);
                        if (!GeneralSettings.usesFullNumberFormat()) {
                            throw new AssertionError("enabled value was not retained");
                        }
                        if (GeneralSettings.showsTimeSeconds()) {
                            throw new AssertionError("number setting changed time setting");
                        }
                        GeneralSettings.setShowsTimeSeconds(true);
                        if (!GeneralSettings.showsTimeSeconds()) {
                            throw new AssertionError("time setting was not retained");
                        }
                        if (!GeneralSettings.usesFullNumberFormat()) {
                            throw new AssertionError("time setting changed number setting");
                        }
                        if (GeneralSettings.usesRelativeLastSeen()) {
                            throw new AssertionError("time setting changed last seen setting");
                        }
                        GeneralSettings.setUsesRelativeLastSeen(true);
                        if (!GeneralSettings.usesRelativeLastSeen()) {
                            throw new AssertionError("last seen setting was not retained");
                        }
                        if (!GeneralSettings.usesFullNumberFormat() || !GeneralSettings.showsTimeSeconds()) {
                            throw new AssertionError("last seen setting changed another setting");
                        }
                        int[] rounded = new int[1];
                        if (!"1,234,567".equals(GeneralSettings.formatFullNumber(1234567, rounded, Locale.US))) {
                            throw new AssertionError("US grouping failed");
                        }
                        if (rounded[0] != 1234567) {
                            throw new AssertionError("exact rounded value was not retained");
                        }
                        if (!"1.234.567".equals(GeneralSettings.formatFullNumber(1234567, null, Locale.GERMANY))) {
                            throw new AssertionError("German grouping failed");
                        }
                        if (!"-1,234".equals(GeneralSettings.formatFullNumber(-1234, rounded, Locale.US))) {
                            throw new AssertionError("negative grouping failed");
                        }
                        if (rounded[0] != -1234) {
                            throw new AssertionError("negative rounded value was not retained");
                        }
                    }
                }
            """))

            subprocess.run(
                [
                    "javac",
                    "-d",
                    directory,
                    str(shared_preferences),
                    str(messages_controller),
                    str(JAVA / "messenger/fluxgram/GeneralSettings.java"),
                    str(check),
                ],
                check=True,
                capture_output=True,
            )
            subprocess.run(
                ["java", "-cp", directory, "GeneralSettingsCheck"],
                check=True,
                capture_output=True,
            )


if __name__ == "__main__":
    unittest.main()

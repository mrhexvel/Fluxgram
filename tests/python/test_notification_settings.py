from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"


class NotificationSettingsTest(unittest.TestCase):
    def test_settings_use_account_notifications_storage(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("java and javac are required")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shared_preferences = root / "android/content/SharedPreferences.java"
            messages_controller = root / "org/telegram/messenger/MessagesController.java"
            check = root / "NotificationSettingsCheck.java"

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
                    private static final Preferences[] PREFERENCES = {
                        new Preferences(), new Preferences()
                    };

                    public static SharedPreferences getNotificationsSettings(int account) {
                        return PREFERENCES[account];
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
                import org.telegram.messenger.fluxgram.NotificationSettings;

                public final class NotificationSettingsCheck {
                    public static void main(String[] args) {
                        if (!NotificationSettings.isInAppVibrationEnabled(0)) {
                            throw new AssertionError("vibration default must be enabled");
                        }
                        if (NotificationSettings.isNotificationDelayDisabled(0)) {
                            throw new AssertionError("delay default must be enabled");
                        }

                        NotificationSettings.setInAppVibrationEnabled(0, false);
                        NotificationSettings.setNotificationDelayDisabled(0, true);

                        if (NotificationSettings.isInAppVibrationEnabled(0)) {
                            throw new AssertionError("vibration setting was not retained");
                        }
                        if (!NotificationSettings.isNotificationDelayDisabled(0)) {
                            throw new AssertionError("delay setting was not retained");
                        }
                        if (!NotificationSettings.isInAppVibrationEnabled(1)) {
                            throw new AssertionError("vibration leaked between accounts");
                        }
                        if (NotificationSettings.isNotificationDelayDisabled(1)) {
                            throw new AssertionError("delay leaked between accounts");
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
                    str(JAVA / "messenger/fluxgram/NotificationSettings.java"),
                    str(check),
                ],
                check=True,
                capture_output=True,
            )
            subprocess.run(
                ["java", "-cp", directory, "NotificationSettingsCheck"],
                check=True,
                capture_output=True,
            )

    def test_delay_uses_existing_notifications_queue(self):
        controller = (JAVA / "messenger/NotificationsController.java").read_text()
        method = controller.split(
            "private void scheduleNotificationDelay(boolean onlineReason)", 1
        )[1].split("protected void repeatNotificationMaybe()", 1)[0]

        self.assertIn("NotificationSettings.isNotificationDelayDisabled(currentAccount)", method)
        self.assertIn("notificationDelayRunnable.run();", method)
        self.assertIn("notificationsQueue.cancelRunnable(notificationDelayRunnable);", method)
        self.assertEqual(1, method.count("notificationsQueue.postRunnable"))
        self.assertLess(
            method.index("NotificationSettings.isNotificationDelayDisabled(currentAccount)"),
            method.index("notificationDelayWakelock.acquire(10000)"),
        )

    def test_general_settings_use_one_notifications_section(self):
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()
        section = activity.split(
            "addSectionLabel(items, R.string.FluxGramNotifications);", 1
        )[1].split(
            "addSectionLabel(items, R.string.FluxGramProfile);", 1
        )[0]

        self.assertIn("NotificationSettings.isInAppVibrationEnabled(currentAccount)", section)
        self.assertIn("NotificationSettings.isNotificationDelayDisabled(currentAccount)", section)
        self.assertEqual(1, section.count("UItem.asShadow(null)"))

    def test_notification_labels_have_russian_fallbacks(self):
        resources = ROOT / "TMessagesProj/src/main/res"
        default_strings = (resources / "values/strings.xml").read_text()
        russian_strings = (resources / "values-ru/strings.xml").read_text()

        for name in ("FluxGramNotifications", "FluxGramDisableNotificationDelay"):
            self.assertIn(f'name="{name}"', default_strings)
            self.assertIn(f'name="{name}"', russian_strings)


if __name__ == "__main__":
    unittest.main()

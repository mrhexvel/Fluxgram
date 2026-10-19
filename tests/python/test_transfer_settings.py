from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"


class TransferSettingsTest(unittest.TestCase):
    def test_download_policy_has_one_bounded_integration(self):
        operation = (JAVA / "messenger/FileLoadOperation.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        self.assertEqual(1, operation.count("TransferSettings.usesExpandedDownloadWindow("))
        self.assertIn("preloadPrefixSize > 0", operation)
        self.assertIn("getfileExperimentalParams", operation)
        self.assertIn("forceSmallChunk", operation)
        self.assertIn("downloadChunkSizeBig = 1024 * 512;", operation)
        self.assertIn("maxDownloadRequestsBig = 8;", operation)
        self.assertIn("addSectionLabel(items, R.string.FluxGramDataTransfer);", activity)
        self.assertIn("TransferSettings.setIncreasesDownloadSpeed(enabled);", activity)

    def test_policy_preserves_existing_gates_and_persistence(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("java and javac are required")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shared_preferences = root / "android/content/SharedPreferences.java"
            messages_controller = root / "org/telegram/messenger/MessagesController.java"
            check = root / "TransferSettingsCheck.java"

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
                import org.telegram.messenger.fluxgram.TransferSettings;

                public final class TransferSettingsCheck {
                    public static void main(String[] args) {
                        if (TransferSettings.increasesDownloadSpeed()) {
                            throw new AssertionError("default must be disabled");
                        }
                        if (TransferSettings.usesExpandedDownloadWindow(false, false, false)) {
                            throw new AssertionError("default path must stay unchanged");
                        }
                        if (!TransferSettings.usesExpandedDownloadWindow(true, false, false)) {
                            throw new AssertionError("preload gate was lost");
                        }
                        if (!TransferSettings.usesExpandedDownloadWindow(false, true, false)) {
                            throw new AssertionError("server experimental gate was lost");
                        }
                        if (TransferSettings.usesExpandedDownloadWindow(true, true, true)) {
                            throw new AssertionError("small chunk gate must take priority");
                        }

                        TransferSettings.setIncreasesDownloadSpeed(true);
                        if (!TransferSettings.increasesDownloadSpeed()) {
                            throw new AssertionError("enabled value was not retained");
                        }
                        if (!TransferSettings.usesExpandedDownloadWindow(false, false, false)) {
                            throw new AssertionError("enabled policy was not applied");
                        }
                        if (TransferSettings.usesExpandedDownloadWindow(false, false, true)) {
                            throw new AssertionError("setting bypassed the small chunk gate");
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
                    str(JAVA / "messenger/fluxgram/TransferSettings.java"),
                    str(check),
                ],
                check=True,
                capture_output=True,
            )
            subprocess.run(
                ["java", "-cp", directory, "TransferSettingsCheck"],
                check=True,
                capture_output=True,
            )


if __name__ == "__main__":
    unittest.main()

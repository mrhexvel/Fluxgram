from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
SETTINGS = JAVA / "messenger/fluxgram/ChatSettings.java"


class ChatSettingsTest(unittest.TestCase):
    def test_hide_send_as_defaults_off_and_persists_globally(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("java and javac are required")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shared_preferences = root / "android/content/SharedPreferences.java"
            messages_controller = root / "org/telegram/messenger/MessagesController.java"
            check = root / "ChatSettingsCheck.java"
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
                    public static final MemoryPreferences preferences = new MemoryPreferences();

                    public static SharedPreferences getGlobalMainSettings() {
                        return preferences;
                    }

                    public static final class MemoryPreferences implements SharedPreferences {
                        private final Map<String, Object> values = new HashMap<>();

                        public boolean getBoolean(String key, boolean fallback) {
                            return values.containsKey(key) ? (Boolean) values.get(key) : fallback;
                        }

                        public void putRaw(String key, Object value) {
                            values.put(key, value);
                        }

                        public Editor edit() {
                            return new MemoryEditor();
                        }

                        private final class MemoryEditor implements Editor {
                            private String key;
                            private boolean value;

                            public Editor putBoolean(String key, boolean value) {
                                this.key = key;
                                this.value = value;
                                return this;
                            }

                            public void apply() {
                                values.put(key, value);
                            }
                        }
                    }
                }
            """))
            check.write_text(textwrap.dedent("""
                import org.telegram.messenger.fluxgram.ChatSettings;
                import org.telegram.messenger.MessagesController;

                public final class ChatSettingsCheck {
                    public static void main(String[] args) {
                        if (args.length > 0) {
                            MessagesController.preferences.putRaw("fluxgram_hide_send_as", 7);
                        }

                        require(!ChatSettings.hidesSendAs());
                        ChatSettings.setHidesSendAs(true);
                        require(ChatSettings.hidesSendAs());
                        ChatSettings.setHidesSendAs(false);
                        require(!ChatSettings.hidesSendAs());
                    }

                    private static void require(boolean value) {
                        if (!value) {
                            throw new AssertionError();
                        }
                    }
                }
            """))

            subprocess.run(
                [
                    "javac",
                    "-d",
                    str(root),
                    str(shared_preferences),
                    str(messages_controller),
                    str(SETTINGS),
                    str(check),
                ],
                check=True,
            )
            subprocess.run(
                ["java", "-cp", str(root), "ChatSettingsCheck"],
                check=True,
            )
            subprocess.run(
                ["java", "-cp", str(root), "ChatSettingsCheck", "malformed"],
                check=True,
            )

    def test_chat_activity_hides_only_its_sender_button(self):
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        enter_view = (JAVA / "ui/Components/ChatActivityEnterView.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        composer = chat.split(
            "chatActivityEnterView = new ChatActivityEnterView", 1
        )[1].split("int lastContentViewHeight", 1)[0]

        self.assertIn(
            "public void updateSendAsButton(boolean forceHide, boolean animated)",
            composer,
        )
        self.assertIn(
            "super.updateSendAsButton(forceHide || ChatSettings.hidesSendAs(), animated)",
            composer,
        )
        self.assertNotIn("ChatSettings", enter_view)
        self.assertIn(".setChecked(ChatSettings.hidesSendAs())", activity)
        self.assertIn("ChatSettings.setHidesSendAs(hidden)", activity)
        self.assertIn("parentLayout.rebuildFragments(0)", activity)


if __name__ == "__main__":
    unittest.main()

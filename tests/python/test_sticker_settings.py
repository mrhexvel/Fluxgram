from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
SETTINGS = JAVA / "messenger/fluxgram/StickerSettings.java"
POLICY = JAVA / "messenger/fluxgram/GreetingStickerPolicy.java"


class StickerSettingsTest(unittest.TestCase):
    def test_setting_defaults_off_and_persists_globally(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("java and javac are required")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shared_preferences = root / "android/content/SharedPreferences.java"
            messages_controller = root / "org/telegram/messenger/MessagesController.java"
            check = root / "StickerSettingsCheck.java"
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
                import org.telegram.messenger.fluxgram.GreetingStickerPolicy;
                import org.telegram.messenger.fluxgram.StickerSettings;
                import org.telegram.messenger.MessagesController;

                public final class StickerSettingsCheck {
                    public static void main(String[] args) {
                        if (args.length > 0) {
                            MessagesController.preferences.putRaw(
                                    "fluxgram_show_all_recent_stickers",
                                    7
                            );
                            MessagesController.preferences.putRaw(
                                    "fluxgram_show_greeting_sticker",
                                    7
                            );
                        }
                        require(!StickerSettings.showsAllRecent());
                        require(StickerSettings.showsGreeting());
                        StickerSettings.setShowsAllRecent(true);
                        require(StickerSettings.showsAllRecent());
                        StickerSettings.setShowsAllRecent(false);
                        require(!StickerSettings.showsAllRecent());
                        StickerSettings.setShowsGreeting(false);
                        require(!StickerSettings.showsGreeting());
                        StickerSettings.setShowsGreeting(true);
                        require(StickerSettings.showsGreeting());

                        require(!GreetingStickerPolicy.shouldHideRandomSticker(
                                false, true, false, false, false, false, 0
                        ));
                        require(!GreetingStickerPolicy.shouldHideRandomSticker(
                                false, true, true, true, false, false, 0
                        ));
                        require(!GreetingStickerPolicy.shouldHideRandomSticker(
                                false, true, true, false, true, false, 0
                        ));
                        require(GreetingStickerPolicy.shouldHideRandomSticker(
                                false, true, true, false, true, true, 0
                        ));
                        require(!GreetingStickerPolicy.shouldHideRandomSticker(
                                false, true, true, false, false, false, 1
                        ));
                        require(!GreetingStickerPolicy.shouldHideRandomSticker(
                                true, true, true, false, false, false, 0
                        ));
                        require(!GreetingStickerPolicy.shouldHideRandomSticker(
                                false, false, true, false, false, false, 0
                        ));
                        require(GreetingStickerPolicy.shouldHideRandomSticker(
                                false, true, true, false, false, false, 0
                        ));
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
                    str(POLICY),
                    str(check),
                ],
                check=True,
            )
            subprocess.run(
                ["java", "-cp", str(root), "StickerSettingsCheck"],
                check=True,
            )
            subprocess.run(
                ["java", "-cp", str(root), "StickerSettingsCheck", "malformed"],
                check=True,
            )

    def test_policy_changes_only_regular_sticker_accessor_limit(self):
        controller = (JAVA / "messenger/MediaDataController.java").read_text()
        accessor = controller.split(
            "public ArrayList<TLRPC.Document> getRecentStickers(int type, boolean firstEmpty)",
            1,
        )[1].split("public ArrayList<TLRPC.Document> getRecentStickersNoCopy", 1)[0]

        self.assertIn("StickerSettings.showsAllRecent() && type == TYPE_IMAGE", accessor)
        self.assertIn("? arrayList.size()", accessor)
        self.assertIn(": Math.min(arrayList.size(), 20)", accessor)
        self.assertEqual(1, controller.count("StickerSettings.showsAllRecent()"))
        self.assertNotIn("maxRecentStickersCount", accessor)

    def test_chat_setting_refreshes_existing_sticker_panel(self):
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()
        chats = activity.split(
            "if (section == CHATS) {\n            addSectionLabel",
            1,
        )[1].split(
            "if (section == NAVIGATION)", 1
        )[0]
        click = activity.split(
            "if (section == CHATS) {\n            if (item.id",
            1,
        )[1].split(
            "if (section == NAVIGATION)", 1
        )[0]

        self.assertIn("(items, R.string.FluxGramStickers)", chats)
        self.assertIn(".setChecked(StickerSettings.showsAllRecent())", chats)
        self.assertIn("StickerSettings.setShowsAllRecent(enabled)", click)
        self.assertIn("NotificationCenter.getInstance(currentAccount)", click)
        self.assertIn("NotificationCenter.recentDocumentsDidLoad", click)
        self.assertIn("MediaDataController.TYPE_IMAGE", click)

    def test_greeting_setting_preserves_required_and_business_surfaces(self):
        settings = SETTINGS.read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        empty_view = chat.split("private void createEmptyView", 1)[1].split(
            "private void updateGreetingLock", 1
        )[0]

        self.assertIn('"fluxgram_show_greeting_sticker"', settings)
        self.assertIn("preferences.getBoolean(SHOW_GREETING, true)", settings)
        self.assertIn("showGreeting = true", settings)
        self.assertIn(".setChecked(StickerSettings.showsGreeting())", activity)
        self.assertIn("StickerSettings.setShowsGreeting(enabled)", activity)
        self.assertIn("parentLayout.rebuildFragments(0)", activity)
        self.assertIn("boolean contactRequiresPremium", empty_view)
        self.assertIn("long paidMessageStars", empty_view)
        self.assertIn("boolean paidMessageGreetingRequired", empty_view)
        self.assertIn("paidMessageStars > StarsController.getInstance(currentAccount).getBalance().amount", empty_view)
        self.assertIn("boolean hasGreetingAccessRestriction", empty_view)
        self.assertIn("getDialogId() != getUserConfig().getClientUserId()", empty_view)
        self.assertIn("boolean hideRandomGreetingSticker", empty_view)
        self.assertIn("GreetingStickerPolicy.shouldHideRandomSticker", empty_view)
        self.assertIn("StickerSettings.showsGreeting()", empty_view)
        self.assertIn("isRandomGreetingStickerPolicyEligible()", empty_view)
        self.assertIn("userInfo != null && userInfo.business_intro != null", empty_view)
        self.assertIn("paidMessageStars > 0", empty_view)
        self.assertIn(") || hasGreetingAccessRestriction)", empty_view)
        self.assertIn("if (emptyMessage == null && hideRandomGreetingSticker)", empty_view)
        self.assertIn("LocaleController.getString(R.string.NoMessages)", empty_view)

        eligibility = chat.split(
            "private boolean isRandomGreetingStickerPolicyEligible", 1
        )[1].split("private boolean isFluxGramChatSnowEligible", 1)[0]
        self.assertIn("chatMode != MODE_DEFAULT", eligibility)
        self.assertIn("currentEncryptedChat != null", eligibility)
        self.assertIn("isInPreviewMode()", eligibility)
        self.assertIn("inBubbleMode", eligibility)
        self.assertIn("isInsideContainer", eligibility)
        self.assertIn("isThreadChat()", eligibility)
        self.assertIn("quickReplyShortcut != null", eligibility)
        self.assertIn("businessLink != null", eligibility)
        self.assertIn("isReport()", eligibility)
        self.assertIn("UserObject.isBot(currentUser)", eligibility)
        self.assertIn("MessagesController.isSupportUser(currentUser)", eligibility)

        user_info = chat.split(
            "} else if (id == NotificationCenter.userInfoDidLoad)", 1
        )[1].split(
            "NotificationCenter.didSetNewWallpapper", 1
        )[0]
        self.assertIn("userInfo = (TLRPC.UserFull) args[1]", user_info)
        self.assertIn("!StickerSettings.showsGreeting()", user_info)
        self.assertIn("isRandomGreetingStickerPolicyEligible()", user_info)
        self.assertIn("greetingsViewContainer = null", user_info)
        self.assertIn("createEmptyView(true)", user_info)


if __name__ == "__main__":
    unittest.main()

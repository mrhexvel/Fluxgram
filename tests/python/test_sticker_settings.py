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
                    int getInt(String key, int fallback);
                    Editor edit();

                    interface Editor {
                        Editor putBoolean(String key, boolean value);
                        Editor putInt(String key, int value);
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

                        public int getInt(String key, int fallback) {
                            return values.containsKey(key) ? (Integer) values.get(key) : fallback;
                        }

                        public void putRaw(String key, Object value) {
                            values.put(key, value);
                        }

                        public Editor edit() {
                            return new MemoryEditor();
                        }

                        private final class MemoryEditor implements Editor {
                            private String key;
                            private Object value;

                            public Editor putBoolean(String key, boolean value) {
                                this.key = key;
                                this.value = value;
                                return this;
                            }

                            public Editor putInt(String key, int value) {
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
                            MessagesController.preferences.putRaw(
                                    "fluxgram_show_sticker_time",
                                    7
                            );
                            MessagesController.preferences.putRaw(
                                    "fluxgram_sticker_shape",
                                    "invalid"
                            );
                        }
                        require(!StickerSettings.showsAllRecent());
                        require(StickerSettings.showsGreeting());
                        require(StickerSettings.showsStickerTime());
                        require(StickerSettings.getStickerShape() == StickerSettings.SHAPE_DEFAULT);
                        require(StickerSettings.getStickerTimeMode() == StickerSettings.TIME_DEFAULT);
                        require(StickerSettings.getStickerSize() == StickerSettings.DEFAULT_STICKER_SIZE);
                        StickerSettings.setShowsAllRecent(true);
                        require(StickerSettings.showsAllRecent());
                        StickerSettings.setShowsAllRecent(false);
                        require(!StickerSettings.showsAllRecent());
                        StickerSettings.setShowsGreeting(false);
                        require(!StickerSettings.showsGreeting());
                        StickerSettings.setShowsGreeting(true);
                        require(StickerSettings.showsGreeting());
                        StickerSettings.setShowsStickerTime(false);
                        require(!StickerSettings.showsStickerTime());
                        StickerSettings.setShowsStickerTime(true);
                        require(StickerSettings.showsStickerTime());
                        StickerSettings.setStickerTimeMode(StickerSettings.TIME_SIDE);
                        require(StickerSettings.getStickerTimeMode() == StickerSettings.TIME_SIDE);
                        StickerSettings.setStickerTimeMode(StickerSettings.TIME_HIDDEN);
                        require(!StickerSettings.showsStickerTime());
                        StickerSettings.setStickerSize(4);
                        require(StickerSettings.getStickerSize() == 4);
                        StickerSettings.setStickerSize(20);
                        require(StickerSettings.getStickerSize() == 20);
                        StickerSettings.setStickerSize(99);
                        require(StickerSettings.getStickerSize() == 20);
                        StickerSettings.setStickerShape(StickerSettings.SHAPE_ROUNDED);
                        require(StickerSettings.getStickerShape() == StickerSettings.SHAPE_ROUNDED);
                        StickerSettings.setStickerShape(StickerSettings.SHAPE_MESSAGE);
                        require(StickerSettings.getStickerShape() == StickerSettings.SHAPE_MESSAGE);
                        StickerSettings.setStickerShape(7);
                        require(StickerSettings.getStickerShape() == StickerSettings.SHAPE_MESSAGE);

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
        chats = activity
        click = activity

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

    def test_sticker_time_setting_hides_only_chat_sticker_status_block(self):
        settings = SETTINGS.read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        cell = (JAVA / "ui/Cells/ChatMessageCell.java").read_text()
        chats = activity
        click = activity
        draw_time = cell.split(
            "public void drawTime(Canvas canvas, float alpha, boolean fromParent)",
            1,
        )[1].split("private void drawTimeInternal", 1)[0]

        self.assertIn('"fluxgram_show_sticker_time"', settings)
        self.assertIn("preferences.getInt(STICKER_TIME_MODE, -1)", settings)
        self.assertIn("TIME_SIDE = 1", settings)
        self.assertIn("TIME_HIDDEN = 2", settings)
        self.assertIn("StickerSettings.getStickerTimeMode()", chats)
        self.assertIn("StickerSettings.setStickerTimeMode(index)", click)
        self.assertIn("parentLayout.rebuildFragments(0)", activity)
        self.assertNotIn("StickerSettings", cell)
        self.assertIn("private boolean drawStickerTime = true", cell)
        self.assertIn("if (isStickerTimeHidden())", draw_time)
        self.assertIn("MessageObject.TYPE_STICKER", draw_time)
        self.assertIn("MessageObject.TYPE_ANIMATED_STICKER", draw_time)
        self.assertIn("!currentMessageObject.isDice()", draw_time)
        self.assertIn("!currentMessageObject.isAnimatedEmoji()", draw_time)
        self.assertIn("isStickerTimeHidden() || !currentMessageObject.isImportedForward()", cell)
        self.assertIn("isStickerTimeHidden() || currentMessageObject == null", cell)
        self.assertIn("isStickerTimeHidden() && reactionsLayoutInBubble.isSmall", cell)
        self.assertIn("rebuildChatPresentation()", click)
        self.assertIn("AndroidUtilities.isTablet()", activity)
        self.assertIn("rebuildAllFragments(true)", activity)
        self.assertNotIn("setPresentationHidden", cell.split(
            "public void setDrawStickerTime", 1
        )[1].split("private boolean shouldDrawReactions", 1)[0])
        self.assertIn("setStickerTimeMode(StickerSettings.getStickerTimeMode())", chat)
        self.assertEqual(3, chat.count("applyStickerPresentation("))
        self.assertIn("private boolean isStickerTimeSide()", cell)
        self.assertIn("photoImage.getImageX() - dp(8) - blockWidth", cell)

    def test_sticker_shape_clips_only_chat_stickers(self):
        settings = SETTINGS.read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()
        shape_cell = (
            JAVA / "ui/Components/FluxGramStickerShapeCell.java"
        ).read_text()
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        cell = (JAVA / "ui/Cells/ChatMessageCell.java").read_text()
        draw_photo = cell.split(
            "protected boolean drawPhotoImage(Canvas canvas)", 1
        )[1].split("public boolean areTags()", 1)[0]

        self.assertIn('"fluxgram_sticker_shape"', settings)
        self.assertIn("SHAPE_DEFAULT = 0", settings)
        self.assertIn("SHAPE_ROUNDED = 1", settings)
        self.assertIn("SHAPE_MESSAGE = 2", settings)
        self.assertIn("preferences.getInt(STICKER_SHAPE, DEFAULT_STICKER_SHAPE)", settings)
        self.assertIn("new FluxGramStickerShapeCell(", activity)
        self.assertIn("StickerSettings.getStickerShape()", shape_cell)
        self.assertIn("StickerSettings.setStickerShape(shape)", shape_cell)
        self.assertIn("ShapeOption[] options = new ShapeOption[3]", shape_cell)
        self.assertIn("cornerRadius = nearCornerRadius = dp(6)", chat)
        self.assertIn("cornerRadius = dp(SharedConfig.bubbleRadius)", chat)
        self.assertIn("nearCornerRadius = cornerRadius / 3", chat)
        self.assertIn("setStickerCornerRadii(cornerRadius, nearCornerRadius)", chat)
        self.assertNotIn("StickerSettings", cell)
        self.assertIn("documentAttachType == DOCUMENT_ATTACH_TYPE_STICKER", draw_photo)
        self.assertIn("MessageObject.TYPE_STICKER", draw_photo)
        self.assertIn("MessageObject.TYPE_ANIMATED_STICKER", draw_photo)
        self.assertIn("!currentMessageObject.isDice()", draw_photo)
        self.assertIn("!currentMessageObject.isAnimatedEmoji()", draw_photo)
        self.assertIn("isStickerShapePresentation() ? stickerCornerRadius : 0", draw_photo)
        self.assertIn("MessageObject.isPremiumSticker(currentMessageObject.getDocument())", draw_photo)
        self.assertIn("stickerClipPath.addRoundRect", draw_photo)
        self.assertIn("canvas.clipPath(stickerClipPath)", draw_photo)
        self.assertIn("int[] currentRadii = photoImage.getRoundRadius()", draw_photo)
        self.assertIn("photoImage.setRoundRadius(stickerImageRadii)", draw_photo)
        self.assertNotIn("drawBackground = true", draw_photo)


if __name__ == "__main__":
    unittest.main()

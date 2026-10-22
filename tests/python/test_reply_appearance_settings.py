from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
SETTINGS = JAVA / "messenger/fluxgram/ReplyAppearanceSettings.java"


class ReplyAppearanceSettingsTest(unittest.TestCase):
    def test_state_defaults_enabled_and_persists_each_option(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("java and javac are required")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shared_preferences = root / "android/content/SharedPreferences.java"
            messages_controller = root / "org/telegram/messenger/MessagesController.java"
            check = root / "ReplyAppearanceSettingsCheck.java"
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
                        public int applyCount;

                        public boolean getBoolean(String key, boolean fallback) {
                            return values.containsKey(key) ? (Boolean) values.get(key) : fallback;
                        }

                        public void putRaw(String key, Object value) {
                            values.put(key, value);
                        }

                        public Editor edit() {
                            return new Editor() {
                                private final Map<String, Boolean> pending = new HashMap<>();

                                public Editor putBoolean(String key, boolean value) {
                                    pending.put(key, value);
                                    return this;
                                }

                                public void apply() {
                                    values.putAll(pending);
                                    applyCount++;
                                }
                            };
                        }
                    }
                }
            """))
            check.write_text(textwrap.dedent("""
                import java.lang.reflect.Field;

                import org.telegram.messenger.fluxgram.ReplyAppearanceSettings;
                import org.telegram.messenger.MessagesController;

                public final class ReplyAppearanceSettingsCheck {
                    public static void main(String[] args) throws Exception {
                        if (args.length > 0) {
                            MessagesController.preferences.putRaw("fluxgram_reply_use_peer_color", 7);
                            MessagesController.preferences.putRaw("fluxgram_reply_show_background_emoji", 7);
                            MessagesController.preferences.putRaw("fluxgram_reply_show_background", 7);
                        }

                        ReplyAppearanceSettings.State initial = ReplyAppearanceSettings.getState();
                        require(initial.usesPeerColor());
                        require(initial.showsBackgroundEmoji());
                        require(initial.showsBackground());

                        ReplyAppearanceSettings.setUsePeerColor(false);
                        ReplyAppearanceSettings.State color = ReplyAppearanceSettings.getState();
                        require(!color.usesPeerColor());
                        require(color.showsBackgroundEmoji());
                        require(color.showsBackground());

                        ReplyAppearanceSettings.setShowBackgroundEmoji(false);
                        ReplyAppearanceSettings.State emoji = ReplyAppearanceSettings.getState();
                        require(!emoji.usesPeerColor());
                        require(!emoji.showsBackgroundEmoji());
                        require(emoji.showsBackground());

                        ReplyAppearanceSettings.setShowBackground(false);
                        ReplyAppearanceSettings.State background = ReplyAppearanceSettings.getState();
                        require(!background.usesPeerColor());
                        require(!background.showsBackgroundEmoji());
                        require(!background.showsBackground());

                        Field stateField = ReplyAppearanceSettings.class.getDeclaredField("state");
                        stateField.setAccessible(true);
                        stateField.set(null, null);

                        ReplyAppearanceSettings.State restored = ReplyAppearanceSettings.getState();
                        require(!restored.usesPeerColor());
                        require(!restored.showsBackgroundEmoji());
                        require(!restored.showsBackground());

                        ReplyAppearanceSettings.setAll(true);
                        ReplyAppearanceSettings.State all = ReplyAppearanceSettings.getState();
                        require(all.usesPeerColor());
                        require(all.showsBackgroundEmoji());
                        require(all.showsBackground());
                        require(MessagesController.preferences.applyCount == 4);

                        stateField.set(null, null);
                        ReplyAppearanceSettings.State restoredAll = ReplyAppearanceSettings.getState();
                        require(restoredAll.usesPeerColor());
                        require(restoredAll.showsBackgroundEmoji());
                        require(restoredAll.showsBackground());
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
                ["java", "-cp", str(root), "ReplyAppearanceSettingsCheck"],
                check=True,
            )
            subprocess.run(
                ["java", "-cp", str(root), "ReplyAppearanceSettingsCheck", "malformed"],
                check=True,
            )

    def test_chat_activity_is_the_only_fluxgram_policy_consumer(self):
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        cell = (JAVA / "ui/Cells/ChatMessageCell.java").read_text()
        transition = (JAVA / "ui/TextMessageEnterTransition.java").read_text()

        self.assertEqual(3, chat.count("applyReplyPresentation("))
        self.assertIn("ReplyAppearanceSettings.State state", chat)
        self.assertIn("cell.setReplyPresentation(", chat)
        self.assertNotIn("ReplyAppearanceSettings", cell)
        self.assertIn("private boolean useReplyPeerColor = true;", cell)
        self.assertIn("private boolean drawReplyBackgroundEmoji = true;", cell)
        self.assertIn("private boolean drawReplyBackground = true;", cell)
        self.assertNotIn("setReplyEmojiDrawable", cell)
        self.assertEqual(3, cell.count("checkReplyLinePresentation()"))
        self.assertIn("messageView.checkReplyLinePresentation();", transition)
        copy_params = cell.split("public void copyParamsTo", 1)[1].split(
            "public int childPosition", 1
        )[0]
        self.assertNotIn("setReplyPresentation", copy_params)

    def test_background_and_emoji_gates_preserve_reply_content(self):
        line = (JAVA / "ui/Components/ReplyMessageLine.java").read_text()
        cell = (JAVA / "ui/Cells/ChatMessageCell.java").read_text()

        self.assertIn("emojiEnabled && emojiDocumentId != 0", line)
        self.assertIn("emojiEnabled && stickerDocumentId != 0", line)
        self.assertNotIn("emojiFallback", line)
        self.assertEqual(2, line.count("removeOldDrawable();"))
        self.assertIn("setThemedReplyColor", line)
        self.assertIn("return check(messageObject, currentUser, currentChat, resourcesProvider, type, true);", line)
        self.assertIn("return animated ? nameColorAnimated.set(color) : color;", line)
        self.assertIn("ReplyMessageLine.TYPE_REPLY,\n                useReplyPeerColor", cell)
        self.assertIn("replyLine.setBackgroundColor(Color.TRANSPARENT)", cell)
        self.assertIn("hasReply && drawReplyBackground", cell)
        self.assertIn("needDrawReplyBackground && drawReplyBackground", cell)
        self.assertIn("if (drawReplyBackground) {\n                    replyLine.drawLoadingBackground", cell)
        self.assertIn("replyLine.drawLine(canvas, replySelectorRect)", cell)
        self.assertIn("if (replyTextLayout != null)", cell)

    def test_chat_settings_expose_one_expandable_reply_group(self):
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()
        strings = (ROOT / "TMessagesProj/src/main/res/values/strings.xml").read_text()
        strings_ru = (ROOT / "TMessagesProj/src/main/res/values-ru/strings.xml").read_text()

        self.assertEqual(1, activity.count("UItem.asExpandableSwitch("))
        self.assertIn("REPLY_APPEARANCE", activity)
        self.assertIn('enabledReplyOptions + "/3"', activity)
        self.assertIn("replyAppearanceExpanded", activity)
        self.assertIn(".setChecked(replyAppearance.usesPeerColor())", activity)
        self.assertIn(".setChecked(replyAppearance.showsBackgroundEmoji())", activity)
        self.assertIn(".setChecked(replyAppearance.showsBackground())", activity)
        self.assertEqual(4, activity.count("ReplyAppearanceSettings.set"))
        self.assertIn('name="FluxGramReplyPeerColor"', strings)
        self.assertIn('name="FluxGramReplyBackgroundEmoji"', strings)
        self.assertIn('name="FluxGramReplyBackground"', strings)
        self.assertIn('name="FluxGramReplyPeerColor"', strings_ru)
        self.assertIn('name="FluxGramReplyPeerColor">Цвет автора</string>', strings_ru)
        self.assertIn('name="FluxGramReplyBackgroundEmoji"', strings_ru)
        self.assertIn('name="FluxGramReplyBackground"', strings_ru)
        self.assertNotIn("FluxGramMessageAppearanceActivity", activity)

        expandable_cell = (JAVA / "ui/Cells/TextCheckCell2.java").read_text()
        self.assertIn('info.setClassName("android.widget.Button")', expandable_cell)
        self.assertIn('info.setClassName("android.widget.Switch")', expandable_cell)
        self.assertIn("checkBoxClickArea.setContentDescription(textView.getText())", expandable_cell)
        self.assertIn("checkBox.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO)", expandable_cell)

        size_label = activity.index(
            'addSectionLabel(items, "(etg) " + LocaleController.getString(R.string.FluxGramStickerSize))'
        )
        preview = activity.index("UItem.asCustom(STICKER_SIZE_PREVIEW, messageAppearancePreview)")
        time_mode = activity.index("STICKER_TIME_MODE", preview)
        reply_group = activity.index("UItem.asExpandableSwitch(", time_mode)
        shape_header = activity.index("addSectionLabel(items, R.string.FluxGramStickerShape)", reply_group)
        shape_selector = activity.index("UItem.asCustom(STICKER_SHAPE, stickerShapeCell)", shape_header)
        self.assertLess(size_label, preview)
        self.assertLess(preview, time_mode)
        self.assertLess(time_mode, reply_group)
        self.assertLess(reply_group, shape_header)
        self.assertLess(shape_header, shape_selector)

    def test_chat_settings_use_one_slider_and_real_message_preview(self):
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()
        sticker_preview = (
            JAVA / "ui/Components/FluxGramStickerPreviewCell.java"
        ).read_text()
        combined_preview = (
            JAVA / "ui/Components/FluxGramMessageAppearancePreviewCell.java"
        ).read_text()

        shape_cell = (
            JAVA / "ui/Components/FluxGramStickerShapeCell.java"
        ).read_text()

        self.assertEqual(1, activity.count("new FluxGramMessageAppearancePreviewCell("))
        self.assertIn("UItem.asCustomShadow(label, LayoutHelper.WRAP_CONTENT)", activity)
        self.assertIn("UItem.asCustom(STICKER_SIZE_PREVIEW, messageAppearancePreview)", activity)
        self.assertIn("new SeekBarView", combined_preview)
        self.assertNotIn("FluxGramStickerSize));", combined_preview)
        self.assertIn("112,", combined_preview)
        self.assertIn("new FluxGramStickerPreviewCell(", combined_preview)
        drag = combined_preview.split(
            "public void onSeekBarDrag(boolean stop, float progress)", 1
        )[1].split("public CharSequence getContentDescription()", 1)[0]
        self.assertIn("if (stop) {", drag)
        self.assertIn("StickerSettings.setStickerSize(size);", drag.split("if (stop) {", 1)[1])
        self.assertNotIn("StickerSettings.setStickerSize(size);", drag.split("if (stop) {", 1)[0])
        self.assertIn("extends ThemePreviewMessagesCell", sticker_preview)
        self.assertIn("setReply(replyMessage, stickerMessage)", sticker_preview)
        self.assertIn("cell.setReplyPresentation(", sticker_preview)
        self.assertIn("cell.setCustomStickerDrawable(createStickerDrawable(context))", sticker_preview)
        self.assertIn("R.drawable.fluxgram_sticker_preview", sticker_preview)
        self.assertIn("REPLY_EMOJI_DOCUMENT_ID = 5197203181720854098L", sticker_preview)
        self.assertNotIn("setReplyEmojiDrawable", sticker_preview)
        self.assertNotIn("MediaDataController", sticker_preview)
        self.assertNotIn("NotificationCenter", sticker_preview)
        self.assertNotIn("searchLocalSticker", sticker_preview)
        self.assertIn("message.media.document = document", sticker_preview)
        self.assertIn("messageObject.useCustomPhoto = true", sticker_preview)
        self.assertIn("message.messageOwner.flags |= TLRPC.MESSAGE_FLAG_REPLY", sticker_preview)
        self.assertIn("replyHeader.flags |= 16", sticker_preview)
        self.assertIn("stickerCell.setStickerTimeMode", sticker_preview)
        self.assertIn("stickerCell.setStickerCornerRadii(cornerRadius, nearCornerRadius)", sticker_preview)
        self.assertIn("ChatMessageCell stickerCell = cells[0]", sticker_preview)
        self.assertIn("ChatMessageCell replyCell = cells[1]", sticker_preview)
        self.assertIn("TLRPC.Message message = createMessage(account, false, -1)", sticker_preview)
        self.assertIn("messagesPreview.updatePresentation();", combined_preview)
        self.assertNotIn("drawRoundRect", sticker_preview)
        self.assertIn("MeasureSpec.makeMeasureSpec(dp(130)", shape_cell)
        self.assertIn("ValueAnimator.ofFloat(selectionProgress, target)", shape_cell)
        self.assertIn("new float[]{", shape_cell)


if __name__ == "__main__":
    unittest.main()

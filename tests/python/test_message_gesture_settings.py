from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
SETTINGS = JAVA / "messenger/fluxgram/MessageGestureSettings.java"
EXECUTOR = JAVA / "messenger/fluxgram/MessageActionExecutor.java"
CHAT_ACTIVITY = JAVA / "ui/ChatActivity.java"
SETTINGS_ACTIVITY = JAVA / "ui/FluxGramSettingsActivity.java"
SWIPE_ACTIVITY = JAVA / "ui/FluxGramMessageSwipeSettingsActivity.java"
SWIPE_CELL = JAVA / "ui/Components/FluxGramSwipeActionCell.java"


class MessageGestureSettingsTest(unittest.TestCase):
    def test_double_tap_policy_defaults_and_persists_globally(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("java and javac are required")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shared_preferences = root / "android/content/SharedPreferences.java"
            messages_controller = root / "org/telegram/messenger/MessagesController.java"
            check = root / "MessageGestureSettingsCheck.java"
            shared_preferences.parent.mkdir(parents=True)
            messages_controller.parent.mkdir(parents=True)

            shared_preferences.write_text(textwrap.dedent("""
                package android.content;

                public interface SharedPreferences {
                    int getInt(String key, int fallback);
                    boolean getBoolean(String key, boolean fallback);
                    String getString(String key, String fallback);
                    Editor edit();

                    interface Editor {
                        Editor putInt(String key, int value);
                        Editor putBoolean(String key, boolean value);
                        Editor putString(String key, String value);
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

                        public int getInt(String key, int fallback) {
                            return values.containsKey(key) ? (Integer) values.get(key) : fallback;
                        }

                        public boolean getBoolean(String key, boolean fallback) {
                            return values.containsKey(key) ? (Boolean) values.get(key) : fallback;
                        }

                        public String getString(String key, String fallback) {
                            return values.containsKey(key) ? (String) values.get(key) : fallback;
                        }

                        public void putRaw(String key, Object value) {
                            values.put(key, value);
                        }

                        public Editor edit() {
                            return new MemoryEditor();
                        }

                        private final class MemoryEditor implements Editor {
                            public Editor putInt(String key, int value) {
                                values.put(key, value);
                                return this;
                            }

                            public Editor putBoolean(String key, boolean value) {
                                values.put(key, value);
                                return this;
                            }

                            public Editor putString(String key, String value) {
                                values.put(key, value);
                                return this;
                            }

                            public void apply() {
                            }
                        }
                    }
                }
            """))
            check.write_text(textwrap.dedent("""
                import org.telegram.messenger.MessagesController;
                import org.telegram.messenger.fluxgram.MessageActionExecutor;
                import org.telegram.messenger.fluxgram.MessageGestureSettings;

                import java.util.Arrays;

                public final class MessageGestureSettingsCheck {
                    public static void main(String[] args) {
                        if (args.length > 0) {
                            MessagesController.preferences.putRaw(
                                    "fluxgram_message_gestures_version", "bad");
                            MessagesController.preferences.putRaw(
                                    "fluxgram_message_double_tap_enabled", 7);
                            MessagesController.preferences.putRaw(
                                     "fluxgram_message_double_tap_action", 7);
                            MessagesController.preferences.putRaw(
                                    "fluxgram_message_swipe_enabled", 7);
                            MessagesController.preferences.putRaw(
                                    "fluxgram_message_swipe_actions", 7);
                        }

                        MessageGestureSettings.State initial = MessageGestureSettings.getState();
                        require(initial.isDoubleTapEnabled());
                        require(MessageActionExecutor.REACT_DEFAULT.equals(
                                initial.getDoubleTapAction()));
                        require(initial.isSwipeEnabled());
                        require(initial.getSwipeActions().size() == 1);
                        require(MessageActionExecutor.REPLY.equals(
                                initial.getSwipeActions().get(0)));

                        MessageGestureSettings.setDoubleTapAction(MessageActionExecutor.REPLY);
                        require(MessageActionExecutor.REPLY.equals(
                                MessageGestureSettings.getState().getDoubleTapAction()));
                        require(MessageActionExecutor.REPLY.equals(
                                MessagesController.preferences.getString(
                                        "fluxgram_message_double_tap_action", null)));
                        MessageGestureSettings.setDoubleTapEnabled(false);
                        require(!MessageGestureSettings.getState().isDoubleTapEnabled());
                        require(!MessagesController.preferences.getBoolean(
                                "fluxgram_message_double_tap_enabled", true));
                        require(MessagesController.preferences.getInt(
                                "fluxgram_message_gestures_version", 0) == 2);

                        MessageGestureSettings.setDoubleTapAction(MessageActionExecutor.COPY);
                        require(MessageActionExecutor.COPY.equals(
                                MessageGestureSettings.getState().getDoubleTapAction()));

                        MessageGestureSettings.setDoubleTapAction("unknown");
                        require(MessageActionExecutor.REACT_DEFAULT.equals(
                                MessageGestureSettings.getState().getDoubleTapAction()));

                        MessageGestureSettings.setSwipeEnabled(false);
                        require(!MessageGestureSettings.getState().isSwipeEnabled());
                        require(!MessagesController.preferences.getBoolean(
                                "fluxgram_message_swipe_enabled", true));
                        MessageGestureSettings.setSwipeActions(Arrays.asList(
                                "unknown", MessageActionExecutor.REPLY));
                        require(MessageGestureSettings.getState().getSwipeActions().size() == 1);
                        require(MessageActionExecutor.REPLY.equals(
                                MessageGestureSettings.getState().getSwipeActions().get(0)));
                        MessageGestureSettings.setSwipeActions(Arrays.asList("unknown"));
                        require(MessageActionExecutor.REPLY.equals(
                                MessageGestureSettings.getState().getSwipeActions().get(0)));
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
                    "--release",
                    "8",
                    "-d",
                    str(root),
                    str(shared_preferences),
                    str(messages_controller),
                    str(EXECUTOR),
                    str(SETTINGS),
                    str(check),
                ],
                check=True,
            )
            subprocess.run(
                ["java", "-cp", str(root), "MessageGestureSettingsCheck"],
                check=True,
            )
            subprocess.run(
                ["java", "-cp", str(root), "MessageGestureSettingsCheck", "malformed"],
                check=True,
            )

    def test_chat_uses_target_bound_reply_without_menu_state(self):
        chat = CHAT_ACTIVITY.read_text()
        resolution = chat.split("private MessageActionExecutor.Resolution resolveReplyAction(", 1)[1]
        resolution = resolution.split(
            "private MessageActionExecutor.Resolution resolveDefaultReactionAction(", 1
        )[0]

        self.assertIn("MessageGestureSettings.getState()", chat)
        self.assertIn("findCurrentMessageForAction(expected)", resolution)
        self.assertIn("isReplyGestureChatModeAllowed()", resolution)
        self.assertIn("isReplyPermissionGranted(messageObject)", resolution)
        self.assertIn("isReplyActionAvailable(tappedView, messageObject, trigger)", resolution)
        self.assertIn("performReplyAction(messageObject)", resolution)
        self.assertIn("showFieldPanelForReply(messageObject)", resolution)
        self.assertIn("resolveReplyMessageForDestination(messageObject)", resolution)
        self.assertIn(
            "getMessagesController().isPeerNoForwards(messageObject.getDialogId())",
            resolution,
        )
        self.assertIn("if (!shouldSelectReplyDestination(messageObject))", resolution)
        self.assertIn("return !messageObject.messageOwner.noforwards", resolution)
        self.assertIn("MessageObject.TYPE_PAID_MEDIA", resolution)
        self.assertIn("messageObject.messageOwner.fluxgramDeleted", resolution)
        self.assertIn('args.putBoolean("reply_to", true)', resolution)
        self.assertNotIn("selectedObject", resolution)
        self.assertNotIn("processSelectedOption", resolution)

    def test_swipe_reply_uses_captured_target_and_shared_executor(self):
        chat = CHAT_ACTIVITY.read_text()
        touch = chat.split("private void processTouchEvent(MotionEvent e)", 1)[1]
        touch = touch.split("public boolean onTouchEvent(MotionEvent e)", 1)[0]
        resolution = chat.split("private MessageActionExecutor.Resolution resolveReplyAction(", 1)[1]
        resolution = resolution.split("private MessageActionExecutor.Resolution resolveCopyAction(", 1)[0]

        self.assertIn("slidingMessageActionTarget = fluxGramMessageActions.capture(", touch)
        self.assertIn("MessageGestureSettings.getState().isSwipeEnabled()", touch)
        self.assertIn("slidingMessageActionId = MessageGestureSettings.getState()", touch)
        self.assertIn("MessageActionExecutor.Trigger.SWIPE", touch)
        self.assertIn("resolveReplyAction(", touch)
        self.assertIn("slidingMessageActionTarget = null", touch)
        self.assertNotIn("showFieldPanelForReply(", touch)
        self.assertNotIn("replyingMessageObject =", touch)
        self.assertIn("trigger == MessageActionExecutor.Trigger.SWIPE", resolution)
        self.assertIn("showsMessageForReplyAction(tappedView, messageObject, trigger)", chat)
        self.assertIn("((ChatMessageCell) view).getMessageObject()", chat)

    def test_settings_card_is_between_messages_and_composer(self):
        source = SETTINGS_ACTIVITY.read_text()
        chats = source.split(
            "if (section == CHATS) {\n            addSectionLabel", 1
        )[1]
        chats = chats.split("if (section == NAVIGATION)", 1)[0]

        self.assertLess(
            chats.index("R.string.FluxGramMessages"),
            chats.index("R.string.FluxGramMessageGestures"),
        )
        self.assertLess(
            chats.index("R.string.FluxGramMessageGestures"),
            chats.index("R.string.FluxGramMessageInput"),
        )
        self.assertIn("gestureSettings.isDoubleTapEnabled()", chats)
        self.assertIn("DOUBLE_TAP_MESSAGE_ACTION", chats)
        self.assertIn("MessageGestureSettings.setDoubleTapEnabled(enabled)", source)
        self.assertIn("MessageGestureSettings.setDoubleTapAction(actionIds[index])", source)
        self.assertIn("MessageActionExecutor.COPY", source)
        self.assertIn("gestureSettings.isSwipeEnabled()", chats)
        self.assertIn("SWIPE_MESSAGE_ACTIONS", chats)
        self.assertIn("MessageGestureSettings.setSwipeEnabled(enabled)", source)
        self.assertIn("new FluxGramMessageSwipeSettingsActivity()", source)

    def test_swipe_editor_publishes_only_working_actions(self):
        settings = SETTINGS.read_text()
        activity = SWIPE_ACTIVITY.read_text()
        cell = SWIPE_CELL.read_text()

        self.assertIn("Collections.singletonList(", settings)
        self.assertIn("MessageActionExecutor.REPLY", settings)
        self.assertIn("getSwipeActions()", settings)
        self.assertIn("setSwipeActions(Collection<String> actionIds)", settings)
        self.assertIn("adapter.reorderSectionStart()", activity)
        self.assertIn("setReorderLongPressEnabled(false)", activity)
        self.assertIn("listView.startReorder(cell)", cell)
        self.assertIn("if (activeActions.size() == 1)", activity)
        self.assertNotIn("MessageActionExecutor.COPY", activity)
        self.assertNotIn("MessageActionExecutor.REACT_DEFAULT", activity)

    def test_copy_action_uses_fresh_message_and_attached_group(self):
        chat = CHAT_ACTIVITY.read_text()
        resolution = chat.split("private MessageActionExecutor.Resolution resolveCopyAction(", 1)[1]
        resolution = resolution.split("private boolean isReplyGestureChatModeAllowed()", 1)[0]
        copy_path = chat.split("private boolean isCopyActionAvailable(", 1)[1]
        copy_path = copy_path.split("private boolean performReplyAction(", 1)[0]
        menu_copy = chat.split("case OPTION_COPY:", 1)[1]
        menu_copy = menu_copy.split("case OPTION_SAVE_TO_GALLERY:", 1)[0]

        self.assertIn("findCurrentMessageForAction(expected)", resolution)
        self.assertIn("isCopyPermissionGranted(messageObject)", resolution)
        self.assertIn("isCopyActionAvailable(tappedView, messageObject)", resolution)
        self.assertIn("performCopyAction(tappedView, messageObject)", resolution)
        self.assertIn("messageObject.messageOwner.fluxgramDeleted", copy_path)
        self.assertIn("isCopyableUniqueStarGift(messageObject)", copy_path)
        self.assertIn("isMessagePeerNoForwards(messageObject)", chat)
        self.assertIn("messageObject.getDialogId() == dialog_id", chat)
        self.assertIn("int type = getMessageType(messageObject)", copy_path)
        self.assertIn("if (type == 0)", copy_path)
        self.assertIn("if (type == 1)", copy_path)
        self.assertIn("if (type == -1)", copy_path)
        self.assertIn("currentEncryptedChat == null", copy_path)
        self.assertIn("getCurrentMessagesGroup()", copy_path)
        self.assertIn("group.groupId != messageObject.getGroupId()", copy_path)
        self.assertIn("return containsMessage && captions <= 1", copy_path)
        self.assertIn("copyMessageToClipboard(messageObject, group)", copy_path)
        self.assertNotIn("selectedObject", copy_path)
        self.assertIn(
            "copyMessageToClipboard(selectedObject, selectedObjectGroup)",
            menu_copy,
        )


if __name__ == "__main__":
    unittest.main()

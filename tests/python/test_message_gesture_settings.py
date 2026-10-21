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
                                 "unknown",
                                 MessageActionExecutor.FORWARD,
                                 MessageActionExecutor.EDIT,
                                 MessageActionExecutor.REPLY,
                                 MessageActionExecutor.FORWARD));
                        require(MessageGestureSettings.getState().getSwipeActions().size() == 3);
                        require(MessageActionExecutor.FORWARD.equals(
                                 MessageGestureSettings.getState().getSwipeActions().get(0)));
                        require(MessageActionExecutor.EDIT.equals(
                                 MessageGestureSettings.getState().getSwipeActions().get(1)));
                        require(MessageActionExecutor.REPLY.equals(
                                 MessageGestureSettings.getState().getSwipeActions().get(2)));
                        require("forward,edit,reply".equals(MessagesController.preferences.getString(
                                 "fluxgram_message_swipe_actions", null)));
                        MessageGestureSettings.setSwipeActions(Arrays.asList("unknown"));
                        require(MessageGestureSettings.getState().getSwipeActions().size() == 1);
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
        self.assertIn("slidingMessageActionId = findSwipeAction(view, message)", touch)
        self.assertIn("MessageActionExecutor.Trigger.SWIPE", touch)
        self.assertIn("resolveReplyAction(", touch)
        self.assertIn("resolveForwardAction(", touch)
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

        self.assertIn("Arrays.asList(", settings)
        self.assertIn("MessageActionExecutor.REPLY", settings)
        self.assertIn("MessageActionExecutor.FORWARD", settings)
        self.assertIn("MessageActionExecutor.EDIT", settings)
        self.assertIn("getSwipeActions()", settings)
        self.assertIn("setSwipeActions(Collection<String> actionIds)", settings)
        self.assertIn("adapter.reorderSectionStart()", activity)
        self.assertIn("setReorderLongPressEnabled(false)", activity)
        self.assertIn("listView.startReorder(cell)", cell)
        self.assertIn("if (activeActions.size() == 1)", activity)
        self.assertIn("MessageActionExecutor.FORWARD", activity)
        self.assertIn("MessageActionExecutor.EDIT", activity)
        self.assertNotIn("MessageActionExecutor.COPY", activity)
        self.assertNotIn("MessageActionExecutor.REACT_DEFAULT", activity)

    def test_edit_action_uses_fresh_message_and_album_caption_without_menu_state(self):
        chat = CHAT_ACTIVITY.read_text()
        resolution = chat.split("private MessageActionExecutor.Resolution resolveEditAction(", 1)[1]
        resolution = resolution.split("private boolean isReplyGestureChatModeAllowed()", 1)[0]
        edit_path = chat.split("private boolean isEditPermissionGranted(", 1)[1]
        edit_path = edit_path.split("private boolean isReplyActionAvailable(", 1)[0]
        album = chat.split("private MessageObject resolveCurrentEditMessage(", 1)[1]
        album = album.split("private boolean isCopyableMessageType(", 1)[0]
        handler = chat.split("private boolean performEditAction(", 1)[1]
        handler = handler.split("private boolean performForwardAction(", 1)[0]

        self.assertIn("findCurrentMessageForAction(expected)", resolution)
        self.assertIn("resolveCurrentEditMessage(swipedView, messageObject)", resolution)
        self.assertIn("isEditPermissionGranted(messageObject, editMessage)", resolution)
        self.assertIn("isEditActionAvailable(", resolution)
        self.assertIn("performEditAction(swipedView, messageObject)", resolution)
        self.assertIn("messageObject.canEditMessage(currentChat)", edit_path)
        self.assertIn("editMessage.canEditMessage(currentChat)", edit_path)
        self.assertIn("chatMode == MODE_DEFAULT", chat)
        self.assertIn("chatActivityEnterView.hasAudioToSend()", chat)
        self.assertIn("messageObject.getDialogId() == mergeDialogId", chat)
        self.assertIn("messageObject.getId() <= 0", chat)
        self.assertIn("getCurrentMessagesGroup()", album)
        self.assertIn("findCurrentMessageForAction(", album)
        self.assertIn("captionsCount < 2 ? editMessage : null", album)
        self.assertIn("startEditingMessageObject(editMessage)", handler)
        self.assertNotIn("selectedObject", resolution + edit_path + album + handler)
        self.assertNotIn("processSelectedOption", resolution + edit_path + album + handler)

    def test_forward_action_uses_fresh_group_without_menu_state(self):
        chat = CHAT_ACTIVITY.read_text()
        resolution = chat.split("private MessageActionExecutor.Resolution resolveForwardAction(", 1)[1]
        resolution = resolution.split("private boolean isReplyGestureChatModeAllowed()", 1)[0]
        forward_path = chat.split("private boolean isForwardPermissionGranted(", 1)[1]
        forward_path = forward_path.split("private boolean performReplyAction(", 1)[0]
        handler = chat.split("private boolean performForwardAction(", 1)[1]
        handler = handler.split("private boolean shouldSelectReplyDestination(", 1)[0]

        self.assertIn("findCurrentMessageForAction(expected)", resolution)
        self.assertIn("resolveCurrentForwardMessages(swipedView, messageObject)", resolution)
        self.assertIn("isForwardPermissionGranted(messages)", resolution)
        self.assertIn("isForwardActionAvailable(", resolution)
        self.assertIn("performForwardAction(expected, messageObject, messages)", resolution)
        self.assertIn("getCurrentMessagesGroup()", forward_path)
        self.assertIn("findCurrentMessageForAction(", forward_path)
        self.assertIn("containsTarget ? messages : null", forward_path)
        self.assertIn("canForwardMessages(messages)", forward_path)
        self.assertIn("messageObject.messageOwner.noforwards", forward_path)
        self.assertIn("type == -1", forward_path)
        self.assertIn("type == 0", forward_path)
        self.assertIn("type == 1", forward_path)
        self.assertIn("type == 20", forward_path)
        self.assertIn("currentEncryptedChat != null", forward_path)
        self.assertIn("refreshForwardMessages(", handler)
        self.assertIn("forwardingGestureMessages = currentMessages", handler)
        self.assertIn('args.putInt("messagesCount", messages.size())', handler)
        self.assertIn("gestureForwardTarget", chat)
        self.assertIn("fmessages.addAll(currentMessages)", chat)
        self.assertNotIn("selectedObject", resolution + forward_path + handler)
        self.assertNotIn("processSelectedOption", resolution + forward_path + handler)

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

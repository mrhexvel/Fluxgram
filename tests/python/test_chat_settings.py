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
                            MessagesController.preferences.putRaw("fluxgram_hide_keyboard_on_scroll", 7);
                            MessagesController.preferences.putRaw("fluxgram_add_comma_after_mention", 7);
                            MessagesController.preferences.putRaw("fluxgram_quick_channel_topic_transitions", 7);
                            MessagesController.preferences.putRaw("fluxgram_show_message_reactions", 7);
                            MessagesController.preferences.putRaw("fluxgram_show_edited_mark", 7);
                            MessagesController.preferences.putRaw("fluxgram_show_message_tails", 7);
                            MessagesController.preferences.putRaw("fluxgram_show_message_avatar_online", 7);
                            MessagesController.preferences.putRaw("fluxgram_show_message_views", 7);
                            MessagesController.preferences.putRaw("fluxgram_show_message_share_button", 7);
                            MessagesController.preferences.putRaw("fluxgram_show_messages_from_author", 7);
                            MessagesController.preferences.putRaw("fluxgram_group_message_menu_actions", 7);
                            MessagesController.preferences.putRaw("fluxgram_show_recent_actions_shortcut", 7);
                            MessagesController.preferences.putRaw("fluxgram_show_administrators_shortcut", 7);
                            MessagesController.preferences.putRaw("fluxgram_show_participants_shortcut", 7);
                            MessagesController.preferences.putRaw("fluxgram_show_permissions_shortcut", 7);
                            MessagesController.preferences.putRaw("fluxgram_wide_channel_posts", 7);
                            MessagesController.preferences.putRaw("fluxgram_show_attach_camera_tile", 7);
                        }

                        require(!ChatSettings.hidesSendAs());
                        require(!ChatSettings.hidesKeyboardOnScroll());
                        require(!ChatSettings.addsCommaAfterMention());
                        require(!ChatSettings.allowsQuickChannelTopicTransitions());
                        require(ChatSettings.showsMessageReactions());
                        require(ChatSettings.showsEditedMark());
                        require(ChatSettings.showsMessageTails());
                        require(!ChatSettings.showsMessageAvatarOnline());
                        require(ChatSettings.showsMessageViews());
                        require(ChatSettings.showsMessageShareButton());
                        require(!ChatSettings.showsMessagesFromAuthor());
                        require(ChatSettings.groupsMessageMenuActions());
                        require(!ChatSettings.showsRecentActionsShortcut());
                        require(!ChatSettings.showsAdministratorsShortcut());
                        require(!ChatSettings.showsParticipantsShortcut());
                        require(!ChatSettings.showsPermissionsShortcut());
                        require(!ChatSettings.usesWideChannelPosts());
                        require(ChatSettings.showsAttachCameraTile());
                        ChatSettings.setHidesSendAs(true);
                        require(ChatSettings.hidesSendAs());
                        ChatSettings.setHidesSendAs(false);
                        require(!ChatSettings.hidesSendAs());
                        ChatSettings.setHidesKeyboardOnScroll(true);
                        require(ChatSettings.hidesKeyboardOnScroll());
                        ChatSettings.setHidesKeyboardOnScroll(false);
                        require(!ChatSettings.hidesKeyboardOnScroll());
                        ChatSettings.setAddsCommaAfterMention(true);
                        require(ChatSettings.addsCommaAfterMention());
                        ChatSettings.setAddsCommaAfterMention(false);
                        require(!ChatSettings.addsCommaAfterMention());
                        ChatSettings.setAllowsQuickChannelTopicTransitions(true);
                        require(ChatSettings.allowsQuickChannelTopicTransitions());
                        ChatSettings.setAllowsQuickChannelTopicTransitions(false);
                        require(!ChatSettings.allowsQuickChannelTopicTransitions());
                        ChatSettings.setShowsMessageReactions(false);
                        require(!ChatSettings.showsMessageReactions());
                        ChatSettings.setShowsMessageReactions(true);
                        require(ChatSettings.showsMessageReactions());
                        ChatSettings.setShowsEditedMark(false);
                        require(!ChatSettings.showsEditedMark());
                        ChatSettings.setShowsEditedMark(true);
                        require(ChatSettings.showsEditedMark());
                        ChatSettings.setShowsMessageTails(false);
                        require(!ChatSettings.showsMessageTails());
                        ChatSettings.setShowsMessageTails(true);
                        require(ChatSettings.showsMessageTails());
                        ChatSettings.setShowsMessageAvatarOnline(true);
                        require(ChatSettings.showsMessageAvatarOnline());
                        ChatSettings.setShowsMessageAvatarOnline(false);
                        require(!ChatSettings.showsMessageAvatarOnline());
                        ChatSettings.setShowsMessageViews(false);
                        require(!ChatSettings.showsMessageViews());
                        ChatSettings.setShowsMessageViews(true);
                        require(ChatSettings.showsMessageViews());
                        ChatSettings.setShowsMessageShareButton(false);
                        require(!ChatSettings.showsMessageShareButton());
                        ChatSettings.setShowsMessageShareButton(true);
                        require(ChatSettings.showsMessageShareButton());
                        ChatSettings.setShowsMessagesFromAuthor(true);
                        require(ChatSettings.showsMessagesFromAuthor());
                        ChatSettings.setShowsMessagesFromAuthor(false);
                        require(!ChatSettings.showsMessagesFromAuthor());
                        ChatSettings.setGroupsMessageMenuActions(false);
                        require(!ChatSettings.groupsMessageMenuActions());
                        ChatSettings.setGroupsMessageMenuActions(true);
                        require(ChatSettings.groupsMessageMenuActions());
                        ChatSettings.setShowsRecentActionsShortcut(true);
                        require(ChatSettings.showsRecentActionsShortcut());
                        require(MessagesController.preferences.getBoolean("fluxgram_show_recent_actions_shortcut", false));
                        ChatSettings.setShowsRecentActionsShortcut(false);
                        require(!ChatSettings.showsRecentActionsShortcut());
                        require(!MessagesController.preferences.getBoolean("fluxgram_show_recent_actions_shortcut", true));
                        ChatSettings.setShowsAdministratorsShortcut(true);
                        require(ChatSettings.showsAdministratorsShortcut());
                        require(MessagesController.preferences.getBoolean("fluxgram_show_administrators_shortcut", false));
                        ChatSettings.setShowsAdministratorsShortcut(false);
                        require(!ChatSettings.showsAdministratorsShortcut());
                        require(!MessagesController.preferences.getBoolean("fluxgram_show_administrators_shortcut", true));
                        ChatSettings.setShowsParticipantsShortcut(true);
                        require(ChatSettings.showsParticipantsShortcut());
                        require(MessagesController.preferences.getBoolean("fluxgram_show_participants_shortcut", false));
                        ChatSettings.setShowsParticipantsShortcut(false);
                        require(!ChatSettings.showsParticipantsShortcut());
                        require(!MessagesController.preferences.getBoolean("fluxgram_show_participants_shortcut", true));
                        ChatSettings.setShowsPermissionsShortcut(true);
                        require(ChatSettings.showsPermissionsShortcut());
                        require(MessagesController.preferences.getBoolean("fluxgram_show_permissions_shortcut", false));
                        ChatSettings.setShowsPermissionsShortcut(false);
                        require(!ChatSettings.showsPermissionsShortcut());
                        require(!MessagesController.preferences.getBoolean("fluxgram_show_permissions_shortcut", true));
                        ChatSettings.setUsesWideChannelPosts(true);
                        require(ChatSettings.usesWideChannelPosts());
                        require(MessagesController.preferences.getBoolean("fluxgram_wide_channel_posts", false));
                        ChatSettings.setUsesWideChannelPosts(false);
                        require(!ChatSettings.usesWideChannelPosts());
                        require(!MessagesController.preferences.getBoolean("fluxgram_wide_channel_posts", true));
                        ChatSettings.setShowsAttachCameraTile(false);
                        require(!ChatSettings.showsAttachCameraTile());
                        require(!MessagesController.preferences.getBoolean("fluxgram_show_attach_camera_tile", true));
                        ChatSettings.setShowsAttachCameraTile(true);
                        require(ChatSettings.showsAttachCameraTile());
                        require(MessagesController.preferences.getBoolean("fluxgram_show_attach_camera_tile", false));
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

    def test_chat_activity_hides_only_message_reaction_badges(self):
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        message_cell = (JAVA / "ui/Cells/ChatMessageCell.java").read_text()
        action_cell = (JAVA / "ui/Cells/ChatActionCell.java").read_text()
        reactions_layout = (
            JAVA / "ui/Components/Reactions/ReactionsLayoutInBubble.java"
        ).read_text()
        stars_sheet = (JAVA / "ui/Stars/StarsReactionsSheet.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        adapter = chat.split("class ChatActivityAdapter", 1)[1]
        self.assertEqual(
            2,
            adapter.count(
                "setDrawMessageReactions(ChatSettings.showsMessageReactions())"
            ),
        )
        self.assertNotIn("ChatSettings", message_cell)
        self.assertNotIn("ChatSettings", action_cell)
        self.assertIn("drawMessageReactions || isTag", message_cell)
        self.assertIn("drawMessageReactions || isTag", action_cell)
        self.assertIn("setPresentationHidden(!drawMessageReactions)", message_cell)
        self.assertIn("setPresentationHidden(!drawMessageReactions)", action_cell)
        self.assertIn("contentEmpty || isPresentationHidden()", reactions_layout)
        self.assertIn("for (int i = 0; i < reactionButtons.size(); i++)", reactions_layout)
        self.assertIn("reactionsLayoutInBubble.isPresentationHidden()", stars_sheet)
        self.assertIn("pushed.run()", stars_sheet)
        self.assertIn("reactionsLayoutInBubble.hasUnreadReactions", message_cell)
        self.assertIn("reactionsLayoutInBubble.hasUnreadReactions", action_cell)
        self.assertIn(
            "dummyMessageCell.setDrawMessageReactions(ChatSettings.showsMessageReactions())",
            chat,
        )
        self.assertNotIn("currentMessageObject.shouldDrawReactions()", message_cell)
        self.assertNotIn("currentMessageObject.shouldDrawReactions()", action_cell)
        self.assertIn(
            ".setChecked(ChatSettings.showsMessageReactions())",
            activity,
        )
        self.assertIn("ChatSettings.setShowsMessageReactions(shown)", activity)

    def test_chat_activity_can_hide_only_edited_marker(self):
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        message_cell = (JAVA / "ui/Cells/ChatMessageCell.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        self.assertEqual(
            2,
            chat.count("setDrawEditedMark(ChatSettings.showsEditedMark())"),
        )
        self.assertNotIn("ChatSettings", message_cell)
        self.assertIn("private boolean drawEditedMark = true;", message_cell)
        self.assertIn("if (!drawEditedMark) {\n            edited = false;", message_cell)
        self.assertIn(".setChecked(ChatSettings.showsEditedMark())", activity)
        self.assertIn("ChatSettings.setShowsEditedMark(shown)", activity)
        handler = activity.split("item.id == SHOW_EDITED_MARK", 1)[1]
        handler = handler.split("item.id == DOUBLE_TAP_MESSAGES", 1)[0]
        self.assertIn("rebuildChatPresentation()", handler)

    def test_chat_activity_can_replace_message_tails_with_rounded_corners(self):
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        message_cell = (JAVA / "ui/Cells/ChatMessageCell.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        self.assertEqual(
            2,
            chat.count("setDrawMessageTail(ChatSettings.showsMessageTails())"),
        )
        self.assertNotIn("ChatSettings", message_cell)
        self.assertIn("private boolean drawMessageTail = true;", message_cell)
        self.assertIn("private boolean isMessageTailSuppressedByGroup()", message_cell)
        self.assertIn("private boolean hasMessageTail()", message_cell)
        self.assertIn("private boolean drawsMessageTail()", message_cell)
        self.assertIn("private boolean shouldUseMessageTailDrawable", message_cell)
        self.assertEqual(2, message_cell.count("shouldUseMessageTailDrawable("))
        self.assertEqual(5, message_cell.count("drawsMessageTail()"))
        self.assertIn("return !drawsMessageTail();", message_cell)
        self.assertIn(
            "if (drawMessageTail && !mediaBackground && !pinnedBottom)",
            message_cell,
        )
        self.assertIn(
            "if (drawMessageTail && transitionParams.changePinnedBottomProgress != 1f",
            message_cell,
        )
        self.assertEqual(3, message_cell.count("hasMessageTail() ? 7 : 1"))
        self.assertEqual(3, message_cell.count("getBotButtonTouchInset()"))
        self.assertIn(
            "return drawMessageTail ? (mediaBackground ? 1 : 7) : 1;",
            message_cell,
        )
        self.assertEqual(
            3,
            message_cell.count(
                "currentMessageObject.isOutOwner() || !hasMessageTail() ? 11 : 17"
            ),
        )
        self.assertNotIn("backgroundDrawableLeft + dp(mediaBackground ? 1 : 7)", message_cell)
        self.assertIn(".setChecked(ChatSettings.showsMessageTails())", activity)
        self.assertIn("ChatSettings.setShowsMessageTails(shown)", activity)
        handler = activity.split("item.id == SHOW_MESSAGE_TAILS", 1)[1]
        handler = handler.split("item.id == DOUBLE_TAP_MESSAGES", 1)[0]
        self.assertIn("rebuildChatPresentation()", handler)

    def test_chat_activity_can_show_online_status_on_message_avatars(self):
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        message_cell = (JAVA / "ui/Cells/ChatMessageCell.java").read_text()
        indicator = (
            JAVA / "ui/fluxgram/MessageAvatarOnlineIndicator.java"
        ).read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        self.assertNotIn("MessageAvatarOnlineIndicator", message_cell)
        self.assertIn(
            "imageReceiver.draw(canvas) && shouldDrawMessageAvatarOnlineIndicator()",
            chat,
        )
        self.assertIn("messageAvatarOnlineIndicator.draw(", chat)
        self.assertIn("chatMode == MODE_DEFAULT", chat)
        self.assertIn("currentEncryptedChat == null", chat)
        self.assertIn("&& !isTopic", chat)
        self.assertIn("&& !isComments", chat)
        self.assertIn("&& !isThreadChat()", chat)
        self.assertIn("chatListView.invalidate()", chat)
        self.assertIn("MessagesController.UPDATE_MASK_STATUS", chat)
        self.assertIn("messageObject.messageOwner.fwd_from != null", indicator)
        self.assertIn("messageObject.customAvatarDrawable != null", indicator)
        self.assertIn("cell.getCurrentUser()", indicator)
        self.assertIn("boundChat.signature_profiles", indicator)
        self.assertIn("user.status.expires <= 0", indicator)
        self.assertIn("onlinePrivacy.containsKey(senderId)", indicator)
        self.assertIn("Theme.key_chats_onlineCircle", indicator)
        self.assertIn(
            ".setChecked(ChatSettings.showsMessageAvatarOnline())",
            activity,
        )
        self.assertIn("ChatSettings.setShowsMessageAvatarOnline(shown)", activity)
        handler = activity.split("item.id == SHOW_MESSAGE_AVATAR_ONLINE", 1)[1]
        handler = handler.split("item.id == DOUBLE_TAP_MESSAGES", 1)[0]
        self.assertIn("rebuildChatPresentation()", handler)

    def test_chat_activity_can_hide_message_view_count(self):
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        message_cell = (JAVA / "ui/Cells/ChatMessageCell.java").read_text()
        transition = (JAVA / "ui/TextMessageEnterTransition.java").read_text()
        share_alert = (JAVA / "ui/Components/ShareAlert.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        self.assertNotIn("ChatSettings", message_cell)
        self.assertNotIn("ChatSettings", transition)
        self.assertNotIn("ChatSettings.showsMessageViews", share_alert)
        self.assertIn("private boolean drawMessageViews = true;", message_cell)
        self.assertIn(
            "private boolean shouldDrawMessageViews(MessageObject messageObject)",
            message_cell,
        )
        self.assertEqual(3, message_cell.count("shouldDrawMessageViews("))
        self.assertEqual(
            2,
            chat.count("setDrawMessageViews(ChatSettings.showsMessageViews())"),
        )
        self.assertIn(".setChecked(ChatSettings.showsMessageViews())", activity)
        self.assertIn("ChatSettings.setShowsMessageViews(shown)", activity)
        handler = activity.split("item.id == SHOW_MESSAGE_VIEWS", 1)[1]
        handler = handler.split("item.id == DOUBLE_TAP_MESSAGES", 1)[0]
        self.assertIn("rebuildChatPresentation()", handler)

    def test_chat_activity_can_hide_only_message_share_side_button(self):
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        message_cell = (JAVA / "ui/Cells/ChatMessageCell.java").read_text()
        message_object = (JAVA / "messenger/MessageObject.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        self.assertNotIn("ChatSettings", message_cell)
        self.assertIn("private boolean drawMessageShareButton = true;", message_cell)
        self.assertIn(
            "return drawSideButton != 1 || drawMessageShareButton;",
            message_cell,
        )
        self.assertEqual(
            2,
            chat.count(
                "setDrawMessageShareButton(ChatSettings.showsMessageShareButton())"
            ),
        )
        self.assertIn(
            "cell.drawMessageShareButton = drawMessageShareButton;",
            message_cell,
        )
        self.assertGreaterEqual(
            message_cell.count("isSideButtonAllowedByPresentation()"),
            7,
        )
        self.assertIn("return null;", message_cell.split("virtualViewId == SHARE", 2)[2])
        self.assertIn(
            "virtualViewId == SHARE && !isSideButtonAllowedByPresentation()",
            message_cell,
        )
        self.assertIn("drawSideButton == 2", message_cell)
        self.assertIn("drawSideButton == 3", message_cell)
        self.assertIn("SIDE_BUTTON_SPONSORED_CLOSE", message_cell)
        self.assertNotIn("ChatSettings", message_object)
        self.assertIn(
            ".setChecked(ChatSettings.showsMessageShareButton())",
            activity,
        )
        self.assertIn("ChatSettings.setShowsMessageShareButton(shown)", activity)
        handler = activity.split("item.id == SHOW_MESSAGE_SHARE_BUTTON", 1)[1]
        handler = handler.split("item.id == DOUBLE_TAP_MESSAGES", 1)[0]
        self.assertIn("rebuildChatPresentation()", handler)

    def test_chat_activity_can_search_messages_from_fresh_author(self):
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        self.assertIn(
            "private static final int OPTION_FLUXGRAM_MESSAGES_FROM_AUTHOR = 10002;",
            chat,
        )
        self.assertIn("messagesFromAuthorTarget = target;", chat)
        self.assertIn("findCurrentMessageForAction(expected)", chat)
        self.assertIn("!expected.equals(actual)", chat)
        action = chat.split(
            "option == OPTION_FLUXGRAM_MESSAGES_FROM_AUTHOR", 1
        )[1].split("boolean preserveDim", 1)[0]
        self.assertIn("selectedObject = null;", action)
        self.assertIn("selectedObjectGroup = null;", action)
        self.assertIn("closeMenu(true);", action)
        self.assertIn("message.messageOwner.from_id", chat)
        self.assertIn("message.messageOwner.action != null", chat)
        self.assertIn(
            "(message.getDialogId() != dialog_id && message.getDialogId() != mergeDialogId)",
            chat,
        )
        self.assertIn("message.getId() <= 0", chat)
        self.assertIn("(ChatObject.isChannel(currentChat) && !currentChat.megagroup)", chat)
        self.assertIn("ChatObject.isMonoForum(currentChat)", chat)
        self.assertIn("(threadMessageId != 0 && !isTopic)", chat)
        self.assertIn("currentEncryptedChat != null", chat)
        self.assertIn("chatMode != MODE_DEFAULT", chat)
        self.assertIn("openSearchWithUser(user)", chat)
        self.assertIn("openSearchWithChat(chat)", chat)
        self.assertIn("R.drawable.msg_search", chat)
        self.assertIn(
            ".setChecked(ChatSettings.showsMessagesFromAuthor())",
            activity,
        )
        self.assertIn("ChatSettings.setShowsMessagesFromAuthor(shown)", activity)

    def test_chat_activity_can_group_primary_message_menu_actions(self):
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()
        action_row = (
            JAVA / "ui/Components/FluxGramMessageMenuActionRow.java"
        ).read_text()

        grouping = chat.split(
            "&& ChatSettings.groupsMessageMenuActions()", 1
        )[1].split("if (options.isEmpty()", 1)[0]
        self.assertIn("knownVisibleActionCount > 5", grouping)
        self.assertIn("options.contains(OPTION_TRANSLATE)", chat)
        self.assertIn("welcomeMessageRevert && options.contains(OPTION_DELETE)", chat)
        self.assertIn("welcomeMessageRevert && priority[0] == OPTION_DELETE", grouping)
        self.assertIn("selectedObject.messageOwner.originalLanguage", chat)
        self.assertIn("knownVisibleActionCount == 5", chat)
        self.assertIn("&& !menuLanguageResolved", chat)
        self.assertIn("createMenu(v, singleMessageMenu, listView, x, y, searchGroup, longpress, suggestEdit, true, language)", chat)
        self.assertIn("detectionSequence != messageMenuLanguageDetectionSequence", chat)
        self.assertIn("!isMessageMenuSourceCurrent(v, message)", chat)
        self.assertIn("actualMessage.getDialogId() == expectedMessage.getDialogId()", chat)
        self.assertIn("actualMessage.getId() == expectedMessage.getId()", chat)
        self.assertGreaterEqual(
            chat.count("messageMenuLanguageDetectionSequence++"),
            3,
        )
        self.assertIn("{OPTION_REPLY}", grouping)
        self.assertIn("{OPTION_DELETE}", grouping)
        self.assertIn("{OPTION_COPY, OPTION_COPY_PHONE_NUMBER}", grouping)
        self.assertIn("{OPTION_EDIT}", grouping)
        self.assertIn("{OPTION_COPY_LINK}", grouping)
        self.assertIn("{OPTION_FORWARD}", grouping)
        self.assertIn("groupedActionIcons.add(icons.remove(index))", grouping)
        self.assertIn("groupedActionItems.add(items.remove(index))", grouping)
        self.assertIn("groupedActionOptions.add(options.remove(index))", grouping)
        self.assertIn("groupedActionOptions.size() == 4", grouping)
        self.assertIn("processSelectedOption(actionOption)", chat)
        self.assertIn("scrimPopupContainerLayout.applyViewBottom(popupBottomLayout)", chat)
        self.assertIn("button.setContentDescription(label)", action_row)
        self.assertIn("button.setEnabled(enabled)", action_row)
        self.assertIn("Theme.key_actionBarDefaultSubmenuBackground", action_row)
        self.assertIn("public void updateColors()", action_row)
        self.assertIn("setMinimumWidth(AndroidUtilities.dp(224))", action_row)
        scrim = (
            JAVA / "ui/Components/ChatScrimPopupContainerLayout.java"
        ).read_text()
        self.assertIn("popupParams.weight = 1f", scrim)
        self.assertIn("if (LocaleController.isRTL)", scrim)
        self.assertIn("leftMargin = widthDiff + dp(36)", scrim)
        self.assertIn(
            ".setChecked(ChatSettings.groupsMessageMenuActions())",
            activity,
        )
        self.assertIn(
            "ChatSettings.setGroupsMessageMenuActions(grouped)",
            activity,
        )

    def test_chat_activity_hides_keyboard_only_during_message_list_drag(self):
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        enter_view = (JAVA / "ui/Components/ChatActivityEnterView.java").read_text()
        recycler = (JAVA / "ui/Components/RecyclerListView.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        scroll_listener = chat.split(
            "chatListView.setOnScrollListener(new RecyclerView.OnScrollListener()", 1
        )[1].split("chatListView.setOnItemClickListener", 1)[0]
        gate = scroll_listener.split("ChatSettings.hidesKeyboardOnScroll()", 1)[1]
        gate = gate.split("chatListView.invalidate()", 1)[0]

        self.assertIn("dy != 0", scroll_listener)
        self.assertIn(
            "recyclerView.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING",
            scroll_listener,
        )
        self.assertNotIn("SCROLL_STATE_SETTLING", gate)
        self.assertIn("chatMode != MODE_PINNED", gate)
        self.assertIn("chatMode != MODE_SEARCH", gate)
        self.assertIn("chatActivityEnterView.getVisibility() == View.VISIBLE", gate)
        self.assertIn("!inPreviewMode", gate)
        self.assertIn("!isInsideContainer", gate)
        self.assertIn("!inBubbleMode", gate)
        self.assertIn("!isReport()", gate)
        self.assertIn("!isInPollAddOptionMode()", gate)
        self.assertIn(
            "!(chatActivityEnterView.hasBotWebView() && "
            "chatActivityEnterView.botCommandsMenuIsShowing())",
            gate,
        )
        self.assertIn("chatActivityEnterView.hidePopup(true)", gate)
        self.assertIn("chatActivityEnterView.closeKeyboard()", gate)
        self.assertLess(
            gate.index("chatActivityEnterView.hidePopup(true)"),
            gate.index("chatActivityEnterView.closeKeyboard()"),
        )
        hide_popup = enter_view.split(
            "public boolean hidePopup(boolean byBackButton, boolean forceAnimate, "
            "boolean waitKeyboardOpen)",
            1,
        )[1].split("private void setSearchingTypeInternal", 1)[0]
        self.assertIn("botReplyMarkup.is_persistent", hide_popup)
        self.assertIn("return false", hide_popup)
        self.assertIn('putInt("closed_botkeyboard_"', hide_popup)
        self.assertNotIn("ChatSettings", enter_view)
        self.assertNotIn("ChatSettings", recycler)
        self.assertIn(".setChecked(ChatSettings.hidesKeyboardOnScroll())", activity)
        self.assertIn("ChatSettings.setHidesKeyboardOnScroll(hidden)", activity)
        handler = activity.split("item.id == HIDE_KEYBOARD_ON_SCROLL", 1)[1]
        handler = handler.split("item.id == ADD_COMMA_AFTER_MENTION", 1)[0]
        self.assertNotIn("rebuildFragments", handler)

    def test_chat_activity_adds_comma_only_to_human_mentions(self):
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        mentions_container = (
            JAVA / "ui/Components/MentionsContainerView.java"
        ).read_text()
        mentions_adapter = (JAVA / "ui/Adapters/MentionsAdapter.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        helper = chat.split(
            "private CharSequence createUserMention(TLRPC.User user)", 1
        )[1].split("private boolean approved", 1)[0]
        self.assertIn(
            "ChatSettings.addsCommaAfterMention() && !UserObject.isBot(user)",
            helper,
        )
        self.assertIn('String separator = addComma ? ", " : " ";', helper)
        self.assertIn('return "@" + username + separator;', helper)
        self.assertIn("new SpannableString(name + separator)", helper)
        self.assertIn(
            "int spanEnd = addComma ? name.length() : spannable.length();",
            helper,
        )
        self.assertIn(
            "new URLSpanUserMention(\"\" + user.id, 3),\n"
            "                0,\n"
            "                spanEnd,",
            helper,
        )
        self.assertEqual(2, chat.count("createUserMention(user)"))
        self.assertIn('"@" + username + " "', chat)
        self.assertNotIn("ChatSettings", mentions_container)
        self.assertIn("int index = text.indexOf(' ');", mentions_adapter)
        self.assertIn(".setChecked(ChatSettings.addsCommaAfterMention())", activity)
        self.assertIn("ChatSettings.setAddsCommaAfterMention(enabled)", activity)
        handler = activity.split("item.id == ADD_COMMA_AFTER_MENTION", 1)[1]
        handler = handler.split("item.id == QUICK_CHANNEL_TOPIC_TRANSITIONS", 1)[0]
        self.assertNotIn("rebuildFragments", handler)

    def test_chat_activity_gates_only_existing_quick_transitions(self):
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        pulling_drawable = (JAVA / "ui/ChatPullingDownDrawable.java").read_text()
        topics_tabs = (JAVA / "ui/Components/TopicsTabsView.java").read_text()
        topics = (JAVA / "ui/TopicsFragment.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        policy = "ChatSettings.allowsQuickChannelTopicTransitions()"
        self.assertEqual(3, chat.count(policy))
        eligibility = chat.split("final boolean allowPullingDownScroll =", 1)[1]
        eligibility = eligibility.split("if (allowPullingDownScroll", 1)[0]
        self.assertIn(policy, eligibility)
        release = chat.split("boolean openNextChat =", 1)[1]
        release = release.split("if (openNextChat)", 1)[0]
        self.assertIn(policy, release)
        callback = chat.split("pullingDownDrawable.runOnAnimationFinish(() ->", 1)[1]
        callback = callback.split("});", 1)[0]
        self.assertIn(policy, callback)
        self.assertIn("pullingDownBackAnimator.cancel()", callback)
        self.assertIn("pullingDownOffset = 0", callback)
        self.assertLess(
            callback.index("pullingDownBackAnimator.cancel()"),
            callback.index("pullingDownOffset = 0"),
        )
        self.assertIn("animateToNextChat()", chat)
        self.assertNotIn("ChatSettings", pulling_drawable)
        self.assertNotIn("ChatSettings", topics_tabs)
        self.assertNotIn("ChatSettings", topics)
        self.assertIn(
            ".setChecked(ChatSettings.allowsQuickChannelTopicTransitions())",
            activity,
        )
        self.assertIn(
            "ChatSettings.setAllowsQuickChannelTopicTransitions(enabled)",
            activity,
        )
        handler = activity.split("item.id == QUICK_CHANNEL_TOPIC_TRANSITIONS", 1)[1]
        handler = handler.split("item.id == SHOW_RECENT_ACTIONS_SHORTCUT", 1)[0]
        self.assertNotIn("rebuildFragments", handler)

    def test_chat_activity_adds_guarded_recent_actions_shortcut(self):
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        helper = chat.split(
            "private boolean canOpenFluxGramRecentActions()", 1
        )[1].split("\n    }", 1)[0]
        host = chat.split(
            "private boolean canHostFluxGramAdminShortcuts()", 1
        )[1].split("\n    }", 1)[0]
        self.assertIn("ChatSettings.showsRecentActionsShortcut()", helper)
        self.assertIn("!ChatObject.isNotInChat(currentChat)", helper)
        self.assertIn("ChatObject.hasAdminRights(currentChat)", helper)
        self.assertIn("chatMode == MODE_DEFAULT", host)
        self.assertIn("!inPreviewMode", host)
        self.assertIn("!isInsideContainer", host)
        self.assertIn("!inBubbleMode", host)
        self.assertIn("!isReport()", host)
        self.assertIn("currentEncryptedChat == null", host)
        self.assertIn("dialog_id == -currentChat.id", host)
        self.assertIn("!ChatObject.isCommunity(currentChat)", host)
        self.assertIn("!ChatObject.isMonoForum(currentChat)", host)
        self.assertIn("ChatObject.isChannel(currentChat)", host)
        self.assertNotIn("!isTopic", host)

        menu = chat.split("if (searchItem != null)", 1)[1]
        menu = menu.split("if (ChatObject.isBoostSupported", 1)[0]
        self.assertIn("if (canHostFluxGramAdminShortcuts())", menu)
        self.assertIn(
            "updateFluxGramAdminShortcutsVisibility()",
            menu,
        )
        self.assertIn("R.drawable.msg_log", menu)
        self.assertIn("R.string.EventLog", menu)

        handler = chat.split("id == recent_actions", 1)[1]
        handler = handler.split("id == report", 1)[0]
        self.assertLess(
            handler.index("headerItem.closeSubMenu()"),
            handler.index("canOpenFluxGramRecentActions()"),
        )
        self.assertIn("new ChannelAdminLogActivity(currentChat)", handler)
        updates = chat.split(
            "if ((updateMask & MessagesController.UPDATE_MASK_CHAT) != 0", 1
        )[1]
        updates = updates.split("NotificationCenter.didReceiveNewMessages", 1)[0]
        self.assertIn(
            "updateFluxGramAdminShortcutsVisibility()",
            updates,
        )
        self.assertIn(
            ".setChecked(ChatSettings.showsRecentActionsShortcut())",
            activity,
        )
        self.assertIn(
            "ChatSettings.setShowsRecentActionsShortcut(shown)",
            activity,
        )
        settings_handler = activity.split(
            "item.id == SHOW_RECENT_ACTIONS_SHORTCUT", 1
        )[1].split("item.id == SHOW_MESSAGE_REACTIONS", 1)[0]
        self.assertIn("parentLayout.rebuildFragments(0)", settings_handler)

    def test_chat_activity_adds_guarded_permissions_shortcut(self):
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        helper = chat.split(
            "private boolean canOpenFluxGramPermissions()", 1
        )[1].split("\n    }", 1)[0]
        self.assertIn("canHostFluxGramAdminShortcuts()", helper)
        self.assertIn("ChatSettings.showsPermissionsShortcut()", helper)
        self.assertIn("!ChatObject.isNotInChat(currentChat)", helper)
        self.assertIn("ChatObject.hasAdminRights(currentChat)", helper)
        self.assertNotIn("ChatObject.canChangeChatInfo(currentChat)", helper)

        route = chat.split(
            "private int getFluxGramPermissionsType()", 1
        )[1].split("\n    }", 1)[0]
        self.assertIn("!ChatObject.isChannelAndNotMegaGroup(currentChat)", route)
        self.assertIn("!currentChat.gigagroup", route)
        self.assertIn("ChatUsersActivity.TYPE_KICKED", route)
        self.assertIn("ChatUsersActivity.TYPE_BANNED", route)

        menu = chat.split("if (searchItem != null)", 1)[1]
        menu = menu.split("if (ChatObject.isBoostSupported", 1)[0]
        self.assertIn("R.drawable.msg_permissions", menu)
        self.assertIn("R.string.ChannelPermissions", menu)
        self.assertIn("R.string.ChannelBlacklist", menu)

        handler = chat.split("id == permissions", 1)[1]
        handler = handler.split("id == report", 1)[0]
        self.assertLess(
            handler.index("headerItem.closeSubMenu()"),
            handler.index("canOpenFluxGramPermissions()"),
        )
        self.assertIn('args.putLong("chat_id", currentChat.id)', handler)
        self.assertIn('args.putInt("type", getFluxGramPermissionsType())', handler)
        self.assertNotIn("ARG_FLUXGRAM_GROUP_SETTINGS", handler)
        self.assertIn("fragment.setInfo(chatInfo)", handler)
        self.assertIn("presentFragment(fragment)", handler)

        visibility = chat.split(
            "private void updateFluxGramAdminShortcutsVisibility()", 1
        )[1].split("\n    }", 1)[0]
        self.assertIn(
            "setSubItemShown(permissions, canOpenFluxGramPermissions())",
            visibility,
        )
        self.assertIn("fluxGramPermissionsItem.setText", visibility)
        self.assertIn("getFluxGramPermissionsType()", visibility)
        self.assertIn(
            ".setChecked(ChatSettings.showsPermissionsShortcut())",
            activity,
        )
        self.assertIn(
            "ChatSettings.setShowsPermissionsShortcut(shown)",
            activity,
        )
        settings_handler = activity.split(
            "item.id == SHOW_PERMISSIONS_SHORTCUT", 1
        )[1].split("item.id == SHOW_MESSAGE_REACTIONS", 1)[0]
        self.assertIn("parentLayout.rebuildFragments(0)", settings_handler)

    def test_chat_activity_limits_wide_posts_to_supported_channel_content(self):
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        cell = (JAVA / "ui/Cells/ChatMessageCell.java").read_text()
        message = (JAVA / "messenger/MessageObject.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        surface_policy = chat.split(
            "private boolean shouldUseWideChannelPostSurface(", 1
        )[1].split("\n    }", 1)[0]
        for gate in (
            "ChatSettings.usesWideChannelPosts()",
            "chatMode != MODE_DEFAULT",
            "inPreviewMode",
            "isInsideContainer",
            "inBubbleMode",
            "isReport()",
            "currentEncryptedChat != null",
            "dialog_id != -currentChat.id",
            "!ChatObject.isChannelAndNotMegaGroup(currentChat)",
            "ChatObject.isCommunity(currentChat)",
            "ChatObject.isMonoForum(currentChat)",
            "threadMessageId != 0",
            "isTopic",
            "filtered",
            "isMessagesSearchListVisible()",
            "message.preview",
            "message.isSponsored()",
            "message.getDialogId() != dialog_id",
            "message.messageOwner.peer_id.channel_id != currentChat.id",
        ):
            self.assertIn(gate, surface_policy)

        text_policy = chat.split(
            "private boolean shouldUseWideChannelTextPost(", 1
        )[1].split("\n    }", 1)[0]
        self.assertIn("shouldUseWideChannelPostSurface", text_policy)
        self.assertIn("groupedMessages != null", text_policy)
        self.assertIn("message.type != MessageObject.TYPE_TEXT", text_policy)
        self.assertIn("TLRPC.TL_messageMediaEmpty", text_policy)
        self.assertIn("TLRPC.TL_messageMediaWebPage", text_policy)

        media_policy = chat.split(
            "private boolean shouldUseWideChannelMediaPost(", 1
        )[1].split("\n    }", 1)[0]
        for gate in (
            "shouldUseWideChannelPostSurface",
            "groupedMessages != null",
            "message.getGroupId() != 0",
            "isOrdinaryWideChannelMedia(message)",
        ):
            self.assertIn(gate, media_policy)

        ordinary_media = chat.split(
            "private boolean isOrdinaryWideChannelMedia(", 1
        )[1].split("\n    }", 1)[0]
        for gate in (
            "message.isLivePhoto()",
            "message.messageOwner.media",
            "message.isPaid()",
            "message.isPaidSuggestedPost()",
            "!media.extended_media.isEmpty()",
            "message.type == MessageObject.TYPE_PHOTO",
            "TLRPC.TL_messageMediaPhoto",
            "message.isPhoto()",
            "message.type == MessageObject.TYPE_VIDEO",
            "TLRPC.TL_messageMediaDocument",
            "message.isVideo()",
            "!message.isRoundVideo()",
            "!message.isGif()",
            "!message.isAnyKindOfSticker()",
        ):
            self.assertIn(gate, ordinary_media)
        self.assertNotIn("MessageObject.getMedia", ordinary_media)

        album_policy = chat.split(
            "private boolean shouldUseWideChannelMediaAlbum(", 1
        )[1].split("\n    }", 1)[0]
        for gate in (
            "groupedMessages.groupId == 0",
            "groupedMessages.messages.size() < 2",
            "groupedMessages.messages.get(i)",
            "shouldUseWideChannelPostSurface(message, filtered)",
            "message.getGroupId() != groupedMessages.groupId",
            "!isOrdinaryWideChannelMedia(message)",
            "!TextUtils.isEmpty(message.caption)",
            "++captions > 1",
        ):
            self.assertIn(gate, album_policy)

        bind = chat.split(
            "} else if (position >= messagesStartRow && position < messagesEndRow) {",
            1,
        )[1].split(
            "MessageObject.GroupedMessages groupedMessages = getValidGroupedMessage(message);",
            1,
        )[1].split("messageCell.setMessageObject", 1)[0]
        self.assertIn("ensureGroupedMessagesLayout(groupedMessages, isFiltered)", bind)
        self.assertIn("shouldUseWideChannelTextPost(message, groupedMessages, isFiltered)", bind)
        self.assertIn("shouldUseWideChannelMediaPost(message, groupedMessages, isFiltered)", bind)
        self.assertIn("shouldUseWideChannelMediaAlbum(groupedMessages, isFiltered)", bind)
        self.assertIn("messageCell.setWideTextPost(wideTextPost)", bind)
        self.assertIn("messageCell.setWideMediaPost(wideMediaPost)", bind)
        self.assertIn("ChatSettings.showsMessageTails() && !wideTextPost", bind)
        self.assertIn(
            "ChatSettings.showsMessageShareButton() && !wideTextPost && !wideMediaPost && !wideMediaAlbum",
            bind,
        )

        self.assertNotIn("messageTextWidthOverride", message)
        self.assertNotIn("setMessageTextWidthOverride", cell)
        self.assertIn("AndroidUtilities.getMinTabletSide()", cell)
        self.assertIn("linkPreviewMaxWidth = maxWidth", cell)
        self.assertIn("drawSideButton != 0 && !wideTextPost", cell)
        self.assertIn("backgroundWidth = maxWidth + dp(31)", cell)
        self.assertIn("cell.setWideTextPost(wideTextPost)", cell)
        self.assertIn("cell.wideMediaPost = wideMediaPost", cell)
        self.assertIn("else if (wideMediaPost)", cell)
        self.assertIn("photoWidth = getWidePostContentWidth()", cell)
        self.assertIn("useFullWidth = true", cell)
        self.assertIn("wideMediaPost && imageH > imageW", cell)
        self.assertIn(
            "photoHeight = Math.min(defaultPhotoWidth + dp(100), AndroidUtilities.getPhotoSize())",
            cell,
        )
        self.assertIn("int fittedPhotoWidth = w", cell)
        self.assertIn("wideMediaPost && photoWidth > fittedPhotoWidth", cell)
        self.assertIn("photoImage.setAspectFit(true)", cell)

        dummy = chat.split(
            "private int getHeightForMessage(MessageObject object, boolean withGroupCaption)",
            1,
        )[1].split("return dummyMessageCell.computeHeight", 1)[0]
        self.assertIn("dummyMessageCell.hasDiscussion =", dummy)
        self.assertIn("dummyMessageCell.linkedChatId =", dummy)
        self.assertIn("ensureGroupedMessagesLayout(groupedMessages, filtered)", dummy)
        self.assertIn("shouldUseWideChannelTextPost(", dummy)
        self.assertIn("shouldUseWideChannelMediaPost(", dummy)
        self.assertIn("shouldUseWideChannelMediaAlbum(groupedMessages, filtered)", dummy)
        self.assertIn("dummyMessageCell.setWideTextPost(wideTextPost)", dummy)
        self.assertIn("dummyMessageCell.setWideMediaPost(wideMediaPost)", dummy)
        self.assertIn(
            "ChatSettings.showsMessageShareButton() && !wideTextPost && !wideMediaPost && !wideMediaAlbum",
            dummy,
        )
        self.assertIn(
            "MessageObject.GroupedMessages groupedMessages = getValidGroupedMessage(object)",
            chat,
        )

        grouped_layout = message.split("public static class GroupedMessages", 1)[1]
        grouped_layout = grouped_layout.split("public int getMaxEditDate()", 1)[0]
        self.assertIn("public boolean setLayoutWidth(int width)", grouped_layout)
        self.assertIn("Math.max(800, Math.min(900, width))", grouped_layout)
        self.assertIn("maxSizeWidth = layoutWidth", grouped_layout)
        self.assertIn("firstSpanAdditionalSize = 1000 - maxSizeWidth", grouped_layout)
        self.assertIn("needShare && layoutWidth == 800", grouped_layout)
        self.assertIn("cachedWidthForCaption = -1", grouped_layout)
        self.assertIn("isDocuments = false", grouped_layout)

        calculate_group = chat.split(
            "private void calculateGroupedMessages(", 1
        )[1].split("\n    }", 1)[0]
        self.assertIn("shouldUseWideChannelMediaAlbum", calculate_group)
        self.assertIn("? 900 : 800", calculate_group)
        self.assertIn("cachedWidthForCaption = -1", calculate_group)
        self.assertIn("groupedMessages.calculate()", calculate_group)

        notify_group = chat.split(
            "private void recalculateAndNotifyGroupedMessages(", 1
        )[1].split("\n    }", 1)[0]
        self.assertIn("calculateGroupedMessages(groupedMessages, filtered)", notify_group)
        self.assertIn("chatAdapter.notifyItemRangeChanged", notify_group)
        self.assertIn(
            "recalculateAndNotifyGroupedMessages(getValidGroupedMessage(obj)",
            chat,
        )
        self.assertIn(
            "recalculateAndNotifyGroupedMessages(getValidGroupedMessage(existMessageObject)",
            chat,
        )

        server_ack = chat.split(
            "id == NotificationCenter.messageReceivedByServer", 1
        )[1].split("id == NotificationCenter.messageReceivedByAck", 1)[0]
        duplicate_ack = server_ack.split(
            "messagesDict[0].indexOfKey(newMsgId) >= 0", 1
        )[1].split("TLRPC.Message newMsgObj", 1)[0]
        self.assertIn("removedGroup.messages.remove(removed)", duplicate_ack)
        self.assertIn("calculateGroupedMessages(removedGroup", duplicate_ack)
        self.assertIn(
            "recalculateAndNotifyGroupedMessages(getValidGroupedMessage(obj)",
            server_ack,
        )

        media_update = chat.split(
            "id == NotificationCenter.updateMessageMedia", 1
        )[1].split("id == NotificationCenter.voiceTranscriptionUpdate", 1)[0]
        self.assertIn(
            "recalculateAndNotifyGroupedMessages(getValidGroupedMessage(existMessageObject)",
            media_update,
        )

        ensure_group = chat.split(
            "private void ensureGroupedMessagesLayout(", 1
        )[1].split("\n    }", 1)[0]
        self.assertIn("shouldUseWideChannelMediaAlbum", ensure_group)
        self.assertIn("groupedMessages.setLayoutWidth(layoutWidth)", ensure_group)
        self.assertIn("groupedMessages.calculate()", ensure_group)

        span_lookup = chat.split(
            "chatLayoutManager.setSpanSizeLookup", 1
        )[1].split("chatListView.setLayoutManager", 1)[0]
        self.assertLess(
            span_lookup.index("ensureGroupedMessagesLayout"),
            span_lookup.index("groupedPosition.spanSize"),
        )
        self.assertEqual(chat.count(".calculate();"), 2)

        self.assertIn(
            ".setChecked(ChatSettings.usesWideChannelPosts())",
            activity,
        )
        settings_handler = activity.split(
            "item.id == WIDE_CHANNEL_POSTS", 1
        )[1].split("item.id == SHOW_EDITED_MARK", 1)[0]
        self.assertIn("ChatSettings.setUsesWideChannelPosts(enabled)", settings_handler)
        self.assertIn("rebuildChatPresentation()", settings_handler)

    def test_wide_text_post_uses_cell_local_canonical_layout(self):
        message = (JAVA / "messenger/MessageObject.java").read_text()
        cell = (JAVA / "ui/Cells/ChatMessageCell.java").read_text()
        selection = (JAVA / "ui/Cells/TextSelectionHelper.java").read_text()
        transition = (JAVA / "ui/TextMessageEnterTransition.java").read_text()
        scrim = (JAVA / "ui/Components/ScrimOptions.java").read_text()
        chat = (JAVA / "ui/ChatActivity.java").read_text()

        canonical = message.split(
            "public static TextLayoutBlocks createCanonical", 1
        )[1].split("private TextLayoutBlocks", 1)[0]
        self.assertIn("ZalgoDisplayFilter.filter(messageObject.messageText)", canonical)
        self.assertIn("Theme.chat_msgGameTextPaint", canonical)
        self.assertIn("new TextLayoutBlocks(messageObject, text, paint, width, true)", canonical)
        self.assertIn("layout.valid && !layout.textLayoutBlocks.isEmpty() ? layout : null", canonical)

        canonical_mode = message.split(
            "private TextLayoutBlocks(MessageObject messageObject", 1
        )[1].split("\n        }\n\n    }", 1)[0]
        for contract in (
            "messageObject.totalAnimatedEmojiCount >= 4",
            "messageObject.totalAnimatedEmojiCount >= 50",
            "messageObject.emojiOnlyCount > 0",
            "cutIntoRanges(text, textRanges, !canonical)",
            "block.layoutCode(range.language, range.end - range.start, noforwards)",
            "block.messageObject = messageObject",
            "SpoilerEffect.addSpoilers",
            "blockMaxWidth -= dp(canonical ? 24 : 32)",
        ):
            self.assertIn(contract, canonical_mode)

        local_layout = cell.split("private void updateWideTextLayout()", 1)[1]
        local_layout = local_layout.split("public ArrayList<MessageObject.TextLayoutBlock>", 1)[0]
        self.assertIn("MessageObject.TextLayoutBlocks.createCanonical", local_layout)
        self.assertIn("layout.bounceFrom(previous)", local_layout)
        self.assertIn("if (layout == null)", local_layout)
        self.assertIn("wideTextLayoutMessage == currentMessageObject", local_layout)
        self.assertIn("wideTextLayoutWidth == width", local_layout)
        self.assertIn("updateWideTextLayout();", cell)
        self.assertIn("widthChanged && (messageObject.isPoll() || wideTextPost)", cell)
        self.assertIn("wideTextLayoutWidth != getWidePostContentWidth()", cell)
        self.assertIn("transitionParams.animateOutTextXOffset", cell)

        for accessor in (
            "getActiveTextLayoutBlocks()",
            "getActiveTextWidth()",
            "getActiveTextHeight()",
            "getActiveTextHeight(TransitionParams transitionParams)",
            "getActiveTextXOffset()",
            "getActiveLastLineWidth()",
            "hasActiveCodeAtTop()",
            "hasActiveCodeAtBottom()",
            "hasActiveQuoteAtBottom()",
        ):
            self.assertIn(accessor, cell)

        self.assertNotIn("currentMessageObject.textLayoutBlocks =", cell)
        self.assertNotIn("currentMessageObject.textWidth =", cell)
        self.assertNotIn("currentMessageObject.textXOffset =", cell)
        self.assertNotIn("currentMessageObject.lastLineWidth =", cell)
        self.assertNotIn("currentMessageObject.messageText = ", cell)
        self.assertNotIn("new MessageObject(", local_layout)

        cell_consumers = cell.split(
            "private boolean isSideButtonAllowedByPresentation", 1
        )[1]
        for shared_geometry in (
            "currentMessageObject.textLayoutBlocks",
            "currentMessageObject.textWidth",
            "currentMessageObject.textXOffset",
            "currentMessageObject.textHeight(",
            "currentMessageObject.getLastLineWidth(",
            "messageObject.textLayoutBlocks",
            "messageObject.textWidth",
            "messageObject.textXOffset",
            "messageObject.textHeight(",
            "messageObject.getLastLineWidth(",
        ):
            self.assertNotIn(shared_geometry, cell_consumers)

        self.assertIn("getActiveTextLayoutBlocks()", selection)
        self.assertIn("getActiveTextXOffset()", selection)
        self.assertNotIn("messageObject.textLayoutBlocks", selection)
        self.assertNotIn("messageObject.textXOffset", selection)
        self.assertIn("messageView.getActiveTextLayoutBlocks()", transition)
        self.assertNotIn("messageView.getMessageObject().textLayoutBlocks", transition)
        self.assertIn("textblocks = cell.getActiveTextLayoutBlocks()", scrim)
        self.assertIn("rtloffset = cell.getActiveTextXOffset()", scrim)
        self.assertIn(
            "dummyMessageCell.getActiveTextLayoutBlocks()",
            chat.split("private int scrollOffsetForQuote", 1)[1].split(
                "private int getScrollOffsetForMessage", 1
            )[0],
        )
        self.assertIn("dummyMessageCell.getMessageObject() != object", chat)
        self.assertIn("textLayoutBlocks = messageView.getActiveTextLayoutBlocks()", transition)

    def test_photo_hd_default_reuses_telegram_quality_state(self):
        shared = (JAVA / "messenger/SharedConfig.java").read_text()
        media = (JAVA / "messenger/MediaController.java").read_text()
        viewer = (JAVA / "ui/PhotoViewer.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()
        attachment = (JAVA / "ui/Components/ChatAttachAlertPhotoLayout.java").read_text()
        sender = (JAVA / "messenger/SendMessagesHelper.java").read_text()
        strings = (ROOT / "TMessagesProj/src/main/res/values/strings.xml").read_text()
        strings_ru = (ROOT / "TMessagesProj/src/main/res/values-ru/strings.xml").read_text()

        setter = shared.split(
            "public static void setPhotoHighQualityDefault", 1
        )[1].split("\n    }", 1)[0]
        self.assertIn("photoHighQualityDefault = enabled", setter)
        self.assertIn('putBoolean("photoHighQualityDefault", photoHighQualityDefault)', setter)
        self.assertIn("return SharedConfig.photoHighQualityDefault", media)
        self.assertIn(
            "SharedConfig.setPhotoHighQualityDefault(photoEntry.isHighQuality())",
            viewer,
        )
        photo_quality = viewer.split(
            "if (!photoEntry.isVideo || photoEntry.isLivePhoto())", 1
        )[1].split("return;", 1)[0]
        self.assertIn("photoEntry.highQuality = !photoEntry.isHighQuality()", photo_quality)
        self.assertIn("SharedConfig.setPhotoHighQualityDefault", photo_quality)

        selection = attachment.split(
            "private int addToSelectedPhotos", 1
        )[1].split("private void updateCheckedPhotos", 1)[0]
        self.assertIn("object.highQuality = null", selection)
        self.assertIn("object.highQuality = object.isHighQuality()", selection)
        self.assertIn("if (!forceDocument && groupMediaFinal)", sender)
        self.assertIn("!info.isVideo && info.videoEditedInfo == null", sender)

        self.assertIn("R.string.FluxGramAttachments", activity)
        self.assertIn("R.string.FluxGramPhotoHdDefault", activity)
        self.assertIn(".setChecked(SharedConfig.photoHighQualityDefault)", activity)
        handler = activity.split("item.id == PHOTO_HD_DEFAULT", 1)[1].split(
            "item.id == SHOW_ATTACH_CAMERA_TILE", 1
        )[0]
        self.assertIn("SharedConfig.setPhotoHighQualityDefault(enabled)", handler)
        self.assertNotIn("rebuildFragments", handler)
        self.assertNotIn("rebuildChatPresentation", handler)

        self.assertNotIn("photoHighQualityDefault", SETTINGS.read_text())
        self.assertIn('name="FluxGramPhotoHdDefault"', strings)
        self.assertIn('name="FluxGramPhotoHdDefault"', strings_ru)

    def test_chat_attach_camera_tile_is_scoped_and_keeps_fallback_routes(self):
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        alert = (JAVA / "ui/Components/ChatAttachAlert.java").read_text()
        photo = (JAVA / "ui/Components/ChatAttachAlertPhotoLayout.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()
        strings = (ROOT / "TMessagesProj/src/main/res/values/strings.xml").read_text()
        strings_ru = (ROOT / "TMessagesProj/src/main/res/values-ru/strings.xml").read_text()

        self.assertIn(
            "true, ChatSettings.showsAttachCameraTile(), themeDelegate",
            chat,
        )
        self.assertEqual(1, chat.count("ChatSettings.showsAttachCameraTile()"))
        self.assertNotIn("ChatSettings", alert)
        self.assertNotIn("ChatSettings", photo)

        old_constructor = alert.split(
            "boolean needCamera, Theme.ResourcesProvider resourcesProvider", 1
        )[1].split("\n    }", 1)[0]
        self.assertIn("needCamera, true, resourcesProvider", old_constructor)
        self.assertIn(
            "needCamera, showCameraTile, resourcesProvider",
            alert,
        )
        self.assertIn(
            "this(alert, context, forceDarkTheme, needCamera, true, resourcesProvider)",
            photo,
        )

        predicate = photo.split("private boolean hasCameraEntry()", 1)[1].split(
            "\n    }", 1
        )[0]
        self.assertIn("needCamera", predicate)
        self.assertIn("selectedAlbumEntry == galleryAlbumEntry", predicate)
        self.assertIn(
            "showCameraTile || noCameraPermissions || !SharedConfig.inappCamera",
            predicate,
        )
        camera_surface_predicate = photo.split(
            "private boolean shouldShowInAppCamera()", 1
        )[1].split("\n    }", 1)[0]
        self.assertIn("SharedConfig.inappCamera", camera_surface_predicate)
        self.assertIn("showCameraTile || noGalleryPermissions", camera_surface_predicate)
        self.assertIn("else if (!shouldShowInAppCamera())", photo)
        self.assertIn("!mediaEnabled || !shouldShowInAppCamera()", photo)
        self.assertIn("cameraView != null && shouldShowInAppCamera()", photo)
        self.assertIn("!noCameraPermissions && shouldShowInAppCamera()", photo)
        self.assertNotIn(
            "if (needCamera && selectedAlbumEntry == galleryAlbumEntry)",
            photo,
        )
        self.assertNotIn(
            "if (selectedAlbumEntry == galleryAlbumEntry && needCamera)",
            photo,
        )
        self.assertIn("if (position == 0 && adapter.hasCameraEntry())", photo)
        self.assertIn("if (hasCameraEntry() && position == 0)", photo)
        self.assertIn("if (hasCameraEntry()) {\n                hasCamera = true", photo)
        self.assertIn("if (adapter.hasCameraEntry()) {\n                        updateIndex++", photo)
        self.assertIn("if (hasCameraEntry() && !noCameraPermissions)", photo)
        self.assertIn("!adapter.hasCamera || noCameraPermissions || noGalleryPermissions", photo)

        self.assertIn("cameraPhotos.size()", photo)
        self.assertIn("arrayList.addAll(cameraPhotos)", photo)
        self.assertIn("GalleryEmptyView", photo)
        self.assertIn(
            "emptyView.doOnCameraAccess(ChatAttachAlertPhotoLayout.this::openCameraWithPermissionCheck)",
            photo,
        )
        self.assertIn("if (SharedConfig.inappCamera)", photo)
        self.assertIn("parentAlert.delegate.didPressedButton(0, false", photo)

        self.assertIn("R.string.FluxGramShowAttachCameraTile", activity)
        self.assertIn(".setChecked(ChatSettings.showsAttachCameraTile())", activity)
        handler = activity.split("item.id == SHOW_ATTACH_CAMERA_TILE", 1)[1].split(
            "item.id == SHOW_MESSAGE_REACTIONS", 1
        )[0]
        self.assertIn("ChatSettings.setShowsAttachCameraTile(shown)", handler)
        self.assertIn("parentLayout.rebuildFragments(0)", handler)
        self.assertIn('name="FluxGramShowAttachCameraTile"', strings)
        self.assertIn('name="FluxGramShowAttachCameraTileInfo"', strings)
        self.assertIn('name="FluxGramShowAttachCameraTile"', strings_ru)
        self.assertIn('name="FluxGramShowAttachCameraTileInfo"', strings_ru)

    def test_chat_activity_adds_guarded_administrators_shortcut(self):
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        helper = chat.split(
            "private boolean canOpenFluxGramAdministrators()", 1
        )[1].split("\n    }", 1)[0]
        self.assertIn("canHostFluxGramAdminShortcuts()", helper)
        self.assertIn("ChatSettings.showsAdministratorsShortcut()", helper)
        self.assertIn("!ChatObject.isNotInChat(currentChat)", helper)
        self.assertIn("ChatObject.hasAdminRights(currentChat)", helper)

        menu = chat.split("if (searchItem != null)", 1)[1]
        menu = menu.split("if (ChatObject.isBoostSupported", 1)[0]
        self.assertIn("R.drawable.msg_admins", menu)
        self.assertIn("R.string.ChannelAdministrators", menu)
        self.assertIn(
            "updateFluxGramAdminShortcutsVisibility()",
            menu,
        )

        handler = chat.split("id == administrators", 1)[1]
        handler = handler.split("id == report", 1)[0]
        self.assertLess(
            handler.index("headerItem.closeSubMenu()"),
            handler.index("canOpenFluxGramAdministrators()"),
        )
        self.assertIn('args.putLong("chat_id", currentChat.id)', handler)
        self.assertIn('args.putInt("type", ChatUsersActivity.TYPE_ADMIN)', handler)
        self.assertIn("fragment.setInfo(chatInfo)", handler)
        self.assertIn("presentFragment(fragment)", handler)

        updates = chat.split(
            "if ((updateMask & MessagesController.UPDATE_MASK_CHAT) != 0", 1
        )[1].split("NotificationCenter.didReceiveNewMessages", 1)[0]
        self.assertIn(
            "updateFluxGramAdminShortcutsVisibility()",
            updates,
        )
        visibility = chat.split(
            "private void updateFluxGramAdminShortcutsVisibility()", 1
        )[1].split("\n    }", 1)[0]
        self.assertIn(
            "setSubItemShown(recent_actions, canOpenFluxGramRecentActions())",
            visibility,
        )
        self.assertIn(
            "setSubItemShown(administrators, canOpenFluxGramAdministrators())",
            visibility,
        )
        rights_update = chat.split(
            "id == NotificationCenter.channelRightsUpdated", 1
        )[1].split("id == NotificationCenter.updateMentionsCount", 1)[0]
        self.assertNotIn(
            "chat.id == currentChat.id && chatActivityEnterView != null",
            rights_update,
        )
        self.assertLess(
            rights_update.index("currentChat = chat"),
            rights_update.index("updateFluxGramAdminShortcutsVisibility()"),
        )
        self.assertIn(
            ".setChecked(ChatSettings.showsAdministratorsShortcut())",
            activity,
        )
        self.assertIn(
            "ChatSettings.setShowsAdministratorsShortcut(shown)",
            activity,
        )
        settings_handler = activity.split(
            "item.id == SHOW_ADMINISTRATORS_SHORTCUT", 1
        )[1].split("item.id == SHOW_MESSAGE_REACTIONS", 1)[0]
        self.assertIn("parentLayout.rebuildFragments(0)", settings_handler)

    def test_chat_activity_adds_guarded_participants_shortcut(self):
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        helper = chat.split(
            "private boolean canOpenFluxGramParticipants()", 1
        )[1].split("\n    }", 1)[0]
        self.assertIn("canHostFluxGramAdminShortcuts()", helper)
        self.assertIn("ChatSettings.showsParticipantsShortcut()", helper)
        self.assertIn("!ChatObject.isNotInChat(currentChat)", helper)
        self.assertIn("ChatObject.hasAdminRights(currentChat)", helper)

        menu = chat.split("if (searchItem != null)", 1)[1]
        menu = menu.split("if (ChatObject.isBoostSupported", 1)[0]
        self.assertIn("R.drawable.msg_groups", menu)
        self.assertIn("ChatObject.isChannelAndNotMegaGroup(currentChat)", menu)
        self.assertIn("R.string.ChannelSubscribers", menu)
        self.assertIn("R.string.ChannelMembers", menu)

        handler = chat.split("id == participants", 1)[1]
        handler = handler.split("id == report", 1)[0]
        self.assertLess(
            handler.index("headerItem.closeSubMenu()"),
            handler.index("canOpenFluxGramParticipants()"),
        )
        self.assertIn('args.putLong("chat_id", currentChat.id)', handler)
        self.assertIn('args.putInt("type", ChatUsersActivity.TYPE_USERS)', handler)
        self.assertNotIn("ARG_FLUXGRAM_GROUP_SETTINGS", handler)
        self.assertIn("fragment.setInfo(chatInfo)", handler)
        self.assertIn("presentFragment(fragment)", handler)

        visibility = chat.split(
            "private void updateFluxGramAdminShortcutsVisibility()", 1
        )[1].split("\n    }", 1)[0]
        self.assertIn(
            "setSubItemShown(participants, canOpenFluxGramParticipants())",
            visibility,
        )
        self.assertIn(
            ".setChecked(ChatSettings.showsParticipantsShortcut())",
            activity,
        )
        self.assertIn(
            "ChatSettings.setShowsParticipantsShortcut(shown)",
            activity,
        )
        settings_handler = activity.split(
            "item.id == SHOW_PARTICIPANTS_SHORTCUT", 1
        )[1].split("item.id == SHOW_MESSAGE_REACTIONS", 1)[0]
        self.assertIn("parentLayout.rebuildFragments(0)", settings_handler)


if __name__ == "__main__":
    unittest.main()

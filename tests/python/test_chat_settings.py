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
                            MessagesController.preferences.putRaw("fluxgram_show_message_reactions", 7);
                        }

                        require(!ChatSettings.hidesSendAs());
                        require(!ChatSettings.hidesKeyboardOnScroll());
                        require(!ChatSettings.addsCommaAfterMention());
                        require(ChatSettings.showsMessageReactions());
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
                        ChatSettings.setShowsMessageReactions(false);
                        require(!ChatSettings.showsMessageReactions());
                        ChatSettings.setShowsMessageReactions(true);
                        require(ChatSettings.showsMessageReactions());
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
        handler = handler.split("item.id == SHOW_MESSAGE_REACTIONS", 1)[0]
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
        handler = handler.split("item.id == SHOW_MESSAGE_REACTIONS", 1)[0]
        self.assertNotIn("rebuildFragments", handler)


if __name__ == "__main__":
    unittest.main()

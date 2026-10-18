from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"


def method(source, signature, next_signature):
    start = source.index(signature)
    end = source.index(next_signature, start)
    return source[start:end]


class DeletedMessageServerGuardsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.message_object = (JAVA / "messenger/MessageObject.java").read_text()
        cls.send_helper = (JAVA / "messenger/SendMessagesHelper.java").read_text()
        cls.archived_copy = (JAVA / "messenger/fluxgram/ArchivedMessageCopy.java").read_text()
        cls.metadata = (JAVA / "messenger/ChatMessagesMetadataController.java").read_text()
        cls.chat = (JAVA / "ui/ChatActivity.java").read_text()
        cls.viewer = (JAVA / "ui/PhotoViewer.java").read_text()

    def test_archived_message_uses_copy_capability(self):
        can_forward = method(
            self.message_object,
            "    public boolean canForwardMessage()",
            "    public boolean canEditMedia()",
        )

        self.assertIn("messageOwner.fluxgramDeleted", can_forward)
        self.assertIn("return canSendAsCopy();", can_forward)
        self.assertIn("public boolean canSendAsCopy()", can_forward)
        self.assertIn("ArchivedMessageCopy.hasLocalAttachment(this)", can_forward)

    def test_archived_forward_is_sent_as_new_message(self):
        entry = method(
            self.send_helper,
            "    public int sendMessage(\n        ArrayList<MessageObject> messages,",
            "    private int sendArchivedMessageCopy(",
        )
        copy_batch = method(
            self.send_helper,
            "    private int sendArchivedMessageCopy(",
            "    public static int canSendMessageToChat(",
        )

        self.assertIn("ArchivedMessageCopy.contains(messages)", entry)
        self.assertIn("sendArchivedMessageCopy", entry)
        self.assertIn("ArchivedMessageCopy.canSendSelection(messages)", copy_batch)
        self.assertIn("ArchivedMessageCopy.send", copy_batch)
        self.assertNotIn("TL_messages_forwardMessages", self.archived_copy)
        self.assertIn("generatePhotoSizes", self.archived_copy)
        self.assertIn("document.access_hash = 0", self.archived_copy)
        self.assertIn("document.thumbs.clear()", self.archived_copy)
        self.assertIn("hideCaption && hasCaption", self.archived_copy)
        self.assertIn("message.messageOwner.fluxgramDeleted != archived", self.archived_copy)
        self.assertIn("AndroidUtilities.runOnUIThread", self.archived_copy)

    def test_metadata_collection_skips_archived_messages_before_timestamps(self):
        check_messages = method(
            self.metadata,
            "    public void checkMessages(",
            "    private void loadStoriesForMessages(",
        )

        self.assertIn("if (messageObject.messageOwner.fluxgramDeleted)", check_messages)
        self.assertLess(
            check_messages.index("if (messageObject.messageOwner.fluxgramDeleted)"),
            check_messages.index("messageObject.reactionsLastCheckTime = currentTime"),
        )

    def test_metadata_request_boundaries_filter_archived_messages(self):
        stories = method(
            self.metadata,
            "    private void loadStoriesForMessages(",
            "    public void loadReactionsForMessages(",
        )
        reactions = method(
            self.metadata,
            "    public void loadReactionsForMessages(",
            "    public void loadExtendedMediaForMessages(",
        )
        extended = method(
            self.metadata,
            "    public void loadExtendedMediaForMessages(",
            "    public void onFragmentDestroy()",
        )

        self.assertIn("if (messageObject.messageOwner.fluxgramDeleted)", stories)
        for request in (reactions, extended):
            self.assertIn("if (messageObject.messageOwner.fluxgramDeleted)", request)
            self.assertIn("if (req.id.isEmpty())", request)
            self.assertLess(
                request.index("if (req.id.isEmpty())"),
                request.index("sendRequest(req"),
            )

    def test_chat_forwarding_rechecks_selection_and_dispatch(self):
        open_forward = method(
            self.chat,
            "    private void openForward(",
            "    public void showBottomOverlayProgress(",
        )
        process_option = method(
            self.chat,
            "    private void processSelectedOption(",
            "    public void showSuggestionOfferForEditMessage(",
        )
        dispatch = method(
            self.chat,
            "    public boolean didSelectDialogs(",
            "    public boolean checkRecordLocked(",
        )

        self.assertIn("if (cantForwardMessagesCount != 0)", open_forward)
        self.assertIn("!canForwardMessages(selectedMessages)", open_forward)
        self.assertIn("!selectedObject.canForwardMessage()", process_option)
        self.assertIn("!canForwardMessages(selectedObjectGroup.messages)", process_option)
        self.assertIn("!canForwardMessages(fmessages)", dispatch)

    def test_photo_viewer_blocks_archived_server_actions(self):
        self.assertIn(
            "currentMessageObject == null || !currentMessageObject.canForwardMessage()",
            self.viewer,
        )
        self.assertIn("if (!canForwardMessages(msgs))", self.viewer)
        self.assertIn("if (!canForwardMessages(fmessages))", self.viewer)
        self.assertIn("if (!canForwardMessages(messages))", self.viewer)
        self.assertIn(
            "id == gallery_menu_reply && currentMessageObject.messageOwner.fluxgramDeleted",
            self.viewer,
        )
        self.assertIn(
            "currentMessageObject != null && currentMessageObject.messageOwner.fluxgramDeleted",
            self.viewer,
        )
        self.assertIn("if (messageObject.messageOwner.fluxgramDeleted)", self.viewer)
        self.assertIn("!newMessageObject.messageOwner.fluxgramDeleted", self.viewer)
        self.assertIn("!messageObject.messageOwner.fluxgramDeleted", self.viewer)

    def test_local_chat_actions_remain_available(self):
        archived_menu = method(
            self.chat,
            "        if (message.messageOwner.fluxgramDeleted)",
            "        if (chatMode == MODE_DEFAULT",
        )
        local_delete = method(
            self.chat,
            "    private void processSelectedOption(",
            "        boolean preserveDim = false;",
        )

        self.assertIn("OPTION_COPY", archived_menu)
        self.assertIn("OPTION_FORWARD", archived_menu)
        self.assertIn("OPTION_FLUXGRAM_EDIT_HISTORY", archived_menu)
        self.assertIn("OPTION_DELETE", archived_menu)
        self.assertIn("getDeletedArchive().remove", local_delete)


if __name__ == "__main__":
    unittest.main()

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
PLUGIN_ROOT = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/plugins"
COMPOSER = (
    ROOT
    / "TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java"
)


class OutgoingTextTransformTest(unittest.TestCase):
    def test_python_adapter_uses_dedicated_bounded_slots_and_owner_checks(self):
        source = (PLUGIN_ROOT / "PythonPluginAdapter.java").read_text()
        method = source.split("public synchronized void transformOutgoingText", 1)[1]
        method = method.split("public synchronized void messageReceived", 1)[0]

        self.assertIn("TRANSFORM_CAPACITY = 2", source)
        self.assertIn("transformSlots.tryAcquire()", method)
        self.assertIn("transformSlots.release()", method)
        self.assertIn("lifecycle.isCurrent(generation)", method)
        self.assertIn("getClientUserId()", method)
        self.assertIn("OutgoingTextTransformRequest.MAX_TEXT_LENGTH", method)
        self.assertIn("callback.complete(null)", method)

    def test_composer_validates_draft_identity_before_continuation(self):
        source = COMPOSER.read_text()
        method = source.split("private void sendTextMessage", 1)[1]
        method = method.split("private void sendPreparedTextMessage", 1)[0]

        self.assertIn("outgoingTextTransformPending", method)
        self.assertIn("sequence != outgoingTextTransformSequence", method)
        self.assertIn("dialog_id != capturedDialogId", method)
        self.assertIn("currentTopicId != topicId", method)
        self.assertIn("getClientUserId() != ownerId", method)
        self.assertIn("!originalText.contentEquals(currentMessage)", method)
        self.assertIn("sendPreparedTextMessage", method)

    def test_transform_runs_before_telegram_text_processing(self):
        source = COMPOSER.read_text()
        send = source.split("protected boolean sendMessageInternal", 1)[1]
        send = send.split("private void sendTextMessage", 1)[0]
        prepared = source.split("private void sendPreparedTextMessage", 1)[1]
        prepared = prepared.split("protected boolean showConfirmAlert", 1)[0]

        self.assertIn("sendTextMessage(message", send)
        self.assertNotIn("processSendingText(message", send)
        self.assertIn("processSendingText(message", prepared)
        self.assertLess(
            prepared.index("checkPremiumAnimatedEmoji"),
            prepared.index("processSendingText(message"),
        )

    def test_destroy_invalidates_pending_continuation(self):
        source = COMPOSER.read_text()
        destroy = source.split("public void onDestroy()", 1)[1]
        destroy = destroy.split("public void checkChannelRights", 1)[0]

        self.assertIn("outgoingTextTransformPending = false", destroy)
        self.assertIn("outgoingTextTransformSequence++", destroy)


if __name__ == "__main__":
    unittest.main()

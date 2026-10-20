from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
MESSAGE_OBJECT = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/MessageObject.java"


class ForumTopicResolutionTest(unittest.TestCase):
    def test_service_message_without_explicit_topic_uses_general_topic(self):
        source = MESSAGE_OBJECT.read_text()
        service_branch = source.split(
            "if (message instanceof TLRPC.TL_messageService", 1
        )[1].split("} else {", 1)[0]

        self.assertIn("message.reply_to.reply_to_msg_id", service_branch)
        self.assertIn("message.reply_to.reply_to_top_id", service_branch)
        self.assertIn("topicId == 0 && sureIsForum ? 1 : topicId", service_branch)


if __name__ == "__main__":
    unittest.main()

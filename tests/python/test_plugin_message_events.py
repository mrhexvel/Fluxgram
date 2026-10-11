from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
PLUGIN_SOURCE = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/plugins"


class PluginMessageEventsTest(unittest.TestCase):
    def test_observer_filters_before_snapshot_and_fans_out_through_manager(self):
        source = (PLUGIN_SOURCE / "PluginMessageEvents.java").read_text()

        self.assertIn("NotificationCenter.didReceiveNewMessages", source)
        self.assertIn("Boolean.TRUE.equals(args[2])", source)
        self.assertIn("ChatActivity.MODE_DEFAULT", source)
        self.assertIn("message.isOut()", source)
        self.assertIn("PluginMessageReceivedEvent.capture(account, message)", source)
        self.assertIn("manager.hasMessageReceivedPlugins()", source)
        self.assertIn("manager.messageReceived", source)

        manager = (PLUGIN_SOURCE / "PluginManager.java").read_text()
        self.assertGreaterEqual(
            manager.count("entry.plugin.hasMessageReceivedSubscribers()"),
            2,
        )

    def test_snapshot_copies_models_and_notification_state_before_enqueue(self):
        source = (PLUGIN_SOURCE / "PluginMessageReceivedEvent.java").read_text()

        for value in (
            "message.getId()",
            "message.getDialogId()",
            "message.getTopicId()",
            "message.getFromChatId()",
            "message.messageText.toString()",
            "UserConfig.getInstance(account).getClientUserId()",
            "controller.getDialog(dialogId)",
            "controller.isDialogMuted(dialogId, topicId)",
            "NotificationsController.getSharedPrefKey",
        ):
            self.assertIn(value, source)

        adapter = (PLUGIN_SOURCE / "PythonPluginAdapter.java").read_text()
        self.assertNotIn("MessageObject", adapter)
        self.assertIn("final long ownerId = event.ownerId", adapter)
        self.assertIn('+ ":" + event.ownerId', adapter)
        self.assertIn("!messageReceivedSubscribers", adapter)
        self.assertIn("event.toJson()", adapter)

    def test_server_confirmed_edits_use_a_dedicated_notification(self):
        observer = (PLUGIN_SOURCE / "PluginMessageEvents.java").read_text()
        controller = (
            ROOT
            / "TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java"
        ).read_text()
        notification_center = (
            ROOT
            / "TMessagesProj/src/main/java/org/telegram/messenger/NotificationCenter.java"
        ).read_text()

        self.assertIn("fluxgramMessageEdited = totalEvents++", notification_center)
        self.assertEqual(controller.count(
            "postNotificationName(NotificationCenter.fluxgramMessageEdited"
        ), 1)
        self.assertIn("NotificationCenter.fluxgramMessageEdited", observer)
        self.assertIn("manager.hasMessageEditedPlugins()", observer)
        self.assertIn("message.messageOwner instanceof TLRPC.TL_message", observer)
        self.assertIn("message.messageOwner.edit_date <= 0", observer)
        self.assertIn("PluginMessageEditedEvent.capture(account, message)", observer)

        edited_handler = observer.split("private void messageEdited", 1)[1]
        self.assertNotIn("message.isOut()", edited_handler)

    def test_edited_snapshot_uses_shared_models_and_no_identity_dedupe(self):
        event = (PLUGIN_SOURCE / "PluginMessageEditedEvent.java").read_text()
        adapter = (PLUGIN_SOURCE / "PythonPluginAdapter.java").read_text()
        edited_handler = adapter.split(
            "void messageEdited(PluginMessageEditedEvent event)",
            1,
        )[1]

        self.assertIn("PluginMessageReceivedEvent.capture(account, message)", event)
        self.assertIn('result.put("edit_date", editDate)', event)
        self.assertIn("!messageEditedSubscribers", edited_handler)
        self.assertIn("eventSlots.tryAcquire()", edited_handler)
        self.assertIn('"dispatch_message_edited"', edited_handler)
        self.assertNotIn("observedMessages", edited_handler)


if __name__ == "__main__":
    unittest.main()

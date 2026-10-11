from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
PLUGIN_SOURCE = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/plugins"
CHAT_ACTIVITY = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java"


class PluginChatEventsTest(unittest.TestCase):
    def test_lifecycle_tracker_is_idempotent_and_preserves_identity(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        sources = (
            PLUGIN_SOURCE / "PluginChatEvent.java",
            PLUGIN_SOURCE / "PluginChatLifecycle.java",
            ROOT / "tests/java/PluginChatLifecycleCheck.java",
        )

        with tempfile.TemporaryDirectory() as directory:
            subprocess.run(
                ["javac", "-d", directory, *(str(source) for source in sources)],
                check=True,
                capture_output=True,
            )
            subprocess.run(
                [
                    "java",
                    "-cp",
                    directory,
                    "org.telegram.messenger.plugins.PluginChatLifecycleCheck",
                ],
                check=True,
                capture_output=True,
            )

    def test_chat_activity_uses_navigation_lifecycle_not_resume_pause(self):
        source = CHAT_ACTIVITY.read_text()
        fully_visible = source.split("void onBecomeFullyVisible()", 1)[1].split(
            "private boolean shownConversionDateTimeToast",
            1,
        )[0]
        fully_hidden = source.split("void onBecomeFullyHidden()", 1)[1].split(
            "public void saveKeyboardPositionBeforeTransition",
            1,
        )[0]
        on_resume = source.split("void onResume()", 1)[1].split(
            "void onPause()",
            1,
        )[0]
        on_pause = source.split("void onPause()", 1)[1].split(
            "void onConfigurationChanged",
            1,
        )[0]

        self.assertIn("updatePluginChatLifecycle()", fully_visible)
        self.assertIn("pluginChatLifecycle.onHidden()", fully_hidden)
        self.assertIn("pluginChatLifecycle.onDestroyed()", source)
        self.assertIn("void onPreviewOpenAnimationEnd()", source)
        self.assertNotIn("updatePluginChatLifecycle()", on_resume)
        self.assertNotIn("pluginChatLifecycle", on_pause)

        topic_switch = source.split("lastSwitchTopicTime", 1)[1].split(
            "if (chatAdapter != null)",
            1,
        )[0]
        self.assertIn("updatePluginChatLifecycle()", topic_switch)

    def test_scope_excludes_unsupported_chat_surfaces(self):
        source = CHAT_ACTIVITY.read_text()
        policy = source.split("boolean isPluginChatLifecycleEligible()", 1)[1].split(
            "private static class ChatActivityTextSelectionHelper",
            1,
        )[0]

        for value in (
                "chatMode != MODE_DEFAULT",
                "currentEncryptedChat != null",
                "isInPreviewMode()",
                "inBubbleMode",
                "isInsideContainer",
                "isComments",
                "isThreadChat() && !isTopic",
                "isReport()",
        ):
            self.assertIn(value, policy)

    def test_manager_and_adapter_use_independent_bounded_channels(self):
        manager = (PLUGIN_SOURCE / "PluginManager.java").read_text()
        adapter = (PLUGIN_SOURCE / "PythonPluginAdapter.java").read_text()
        handler = adapter.split("void chatOpened(PluginChatEvent event)", 1)[1]

        self.assertGreaterEqual(
            manager.count("entry.plugin.hasChatOpenedSubscribers()"),
            2,
        )
        self.assertGreaterEqual(
            manager.count("entry.plugin.hasChatClosedSubscribers()"),
            2,
        )
        self.assertIn("eventSlots.tryAcquire()", handler)
        self.assertIn('"dispatch_chat_opened" : "dispatch_chat_closed"', handler)
        self.assertIn("getClientUserId() != ownerId", handler)
        self.assertIn("setChatOpenedSubscriptionActive", adapter)
        self.assertIn("setChatClosedSubscriptionActive", adapter)
        self.assertIn("chatOpenedSubscribers = false", adapter)
        self.assertIn("chatClosedSubscribers = false", adapter)


if __name__ == "__main__":
    unittest.main()

from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
PLUGIN_SOURCE = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/plugins"


class PluginRuntimeTest(unittest.TestCase):
    def test_lifecycle_and_bounded_priority_queue(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        sources = (
            PLUGIN_SOURCE / "PythonPluginLifecycle.java",
            PLUGIN_SOURCE / "PythonPluginTaskQueue.java",
            ROOT / "tests/java/PythonPluginRuntimeCheck.java",
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
                    "org.telegram.messenger.plugins.PythonPluginRuntimeCheck",
                ],
                check=True,
                capture_output=True,
            )

    def test_adapter_uses_lifecycle_at_load_boundaries(self):
        source = (PLUGIN_SOURCE / "PythonPluginAdapter.java").read_text()
        self.assertIn("new PythonPluginTaskQueue(MAILBOX_CAPACITY)", source)
        self.assertIn("lifecycle.requestEnabled(enabled)", source)
        self.assertIn("new PendingSetting(request, account, ownerId, value)", source)
        self.assertIn("getClientUserId() != pending.ownerId", source)
        self.assertIn('"dispatch_setting_changed"', source)
        self.assertGreaterEqual(source.count("lifecycle.isCurrent(request)"), 5)


if __name__ == "__main__":
    unittest.main()

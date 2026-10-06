from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class PluginUiLifecycleTest(unittest.TestCase):
    def test_listener_isolation_removal_and_chat_identity(self):
        if not shutil.which('javac') or not shutil.which('java'):
            self.skipTest('Requires JDK')

        source = (ROOT / 'TMessagesProj/src/main/java/org/telegram/messenger/plugins/PluginUiLifecycle.java').read_text()
        source = re.sub(r'^(?:package org\.telegram\..*|import org\.telegram\..*);\n',
                        '', source, flags=re.M)
        fixture = (ROOT / 'tests/java/PluginUiLifecycleCheck.java').read_text()

        with tempfile.TemporaryDirectory() as directory:
            lifecycle = Path(directory) / 'PluginUiLifecycle.java'
            check = Path(directory) / 'PluginUiLifecycleCheck.java'
            lifecycle.write_text(source)
            check.write_text(fixture)

            subprocess.run(['javac', '-d', directory, str(lifecycle), str(check)],
                           check=True, capture_output=True)
            subprocess.run(['java', '-cp', directory, 'PluginUiLifecycleCheck'],
                           check=True, capture_output=True)

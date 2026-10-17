from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class MessageMenuMetadataTest(unittest.TestCase):
    def test_async_results_and_menu_lifecycle(self):
        if not shutil.which('javac') or not shutil.which('java'):
            self.skipTest('Requires JDK')

        source = (ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/fluxgram/MessageMenuMetadataController.java').read_text()
        source = re.sub(r'^(?:package org\.telegram\..*|import (?:org\.telegram|android)\..*);\n',
                        '', source, flags=re.M)
        fixture = (ROOT / 'tests/java/MessageMenuMetadataCheck.java').read_text()

        with tempfile.TemporaryDirectory() as directory:
            controller = Path(directory) / 'MessageMenuMetadataController.java'
            check = Path(directory) / 'MessageMenuMetadataCheck.java'
            controller.write_text(source)
            check.write_text(fixture)
            subprocess.run(['javac', '-d', directory, str(controller), str(check)],
                           check=True, capture_output=True)
            subprocess.run(['java', '-cp', directory, 'MessageMenuMetadataCheck'],
                            check=True, capture_output=True)

    def test_menu_uses_local_record_only_as_server_fallback(self):
        chat = (ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java').read_text()
        poll = (ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/PollItemMenu.java').read_text()
        todo = (ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/TodoItemMenu.java').read_text()
        cell = (ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/Components/FluxGramReadTimeCell.java').read_text()

        self.assertIn('final boolean showLocalReadTime = !showPrivateMessageSeen && localReadTime != null;', chat)
        self.assertIn('userInfo != null && userInfo.read_dates_private', chat)
        self.assertIn('>= getMessagesController().pmReadDateExpirePeriod', chat)
        self.assertIn('new FluxGramReadTimeCell(getContext(), localReadTime, themeDelegate)', chat)
        self.assertIn('new FluxGramReadTimeCell(getContext(), localReadTime, resourcesProvider)', poll)
        self.assertIn('new FluxGramReadTimeCell(getContext(), localReadTime, resourcesProvider)', todo)
        self.assertNotIn('MessagePrivateSeenView', cell)
        self.assertNotIn('ConnectionsManager', cell)

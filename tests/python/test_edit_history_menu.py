from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class EditHistoryMenuTest(unittest.TestCase):
    def test_async_results_and_menu_lifecycle(self):
        if not shutil.which('javac') or not shutil.which('java'):
            self.skipTest('Requires JDK')

        source = (ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/fluxgram/EditHistoryMenuController.java').read_text()
        source = re.sub(r'^(?:package org\.telegram\..*|import (?:org\.telegram|android)\..*);\n',
                        '', source, flags=re.M)
        fixture = (ROOT / 'tests/java/EditHistoryMenuCheck.java').read_text()

        with tempfile.TemporaryDirectory() as directory:
            controller = Path(directory) / 'EditHistoryMenuController.java'
            check = Path(directory) / 'EditHistoryMenuCheck.java'
            controller.write_text(source)
            check.write_text(fixture)
            subprocess.run(['javac', '-d', directory, str(controller), str(check)],
                           check=True, capture_output=True)
            subprocess.run(['java', '-cp', directory, 'EditHistoryMenuCheck'],
                           check=True, capture_output=True)

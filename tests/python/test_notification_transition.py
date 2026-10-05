from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class NotificationTransitionTest(unittest.TestCase):
    def test_interrupted_entrance_exit_and_detach(self):
        if not shutil.which('javac') or not shutil.which('java'):
            self.skipTest('Requires JDK')

        source = (ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/Components/NotificationCardLayout.java').read_text()
        source = re.sub(r'^(?:package|import) .*;\n', '', source, flags=re.M)
        fixture = (ROOT / 'tests/java/NotificationTransitionCheck.java.in').read_text()

        with tempfile.TemporaryDirectory() as directory:
            layout = Path(directory) / 'NotificationCardLayout.java'
            check = Path(directory) / 'NotificationTransitionCheck.java'
            layout.write_text('import java.util.function.Consumer;\n' + source)
            check.write_text(fixture)

            subprocess.run(['javac', '-d', directory, str(layout), str(check)],
                           check=True, capture_output=True)
            subprocess.run(['java', '-cp', directory, 'NotificationTransitionCheck'],
                           check=True, capture_output=True)

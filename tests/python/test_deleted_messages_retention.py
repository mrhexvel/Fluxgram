from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class DeletedMessagesRetentionTest(unittest.TestCase):
    def test_dialog_routing_batch_union_and_immutable_delivery(self):
        if not shutil.which('javac') or not shutil.which('java'):
            self.skipTest('Requires JDK')

        source = (ROOT / 'TMessagesProj/src/main/java/org/telegram/messenger/fluxgram/DeletedMessagesRetention.java').read_text()
        source = re.sub(r'^(?:package org\.telegram\..*|import org\.telegram\..*);\n',
                        '', source, flags=re.M)
        fixture = (ROOT / 'tests/java/DeletedMessagesRetentionCheck.java.in').read_text()

        with tempfile.TemporaryDirectory() as directory:
            retention = Path(directory) / 'DeletedMessagesRetention.java'
            check = Path(directory) / 'DeletedMessagesRetentionCheck.java'
            retention.write_text(source)
            check.write_text(fixture)
            subprocess.run(['javac', '-d', directory, str(retention), str(check)],
                           check=True, capture_output=True)
            subprocess.run(['java', '-cp', directory, 'DeletedMessagesRetentionCheck'],
                           check=True, capture_output=True)

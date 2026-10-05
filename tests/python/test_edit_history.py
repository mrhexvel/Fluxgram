from pathlib import Path
import re
import shutil
import sqlite3
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/fluxgram"


class EditHistoryTest(unittest.TestCase):
    def test_change_detection(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        with tempfile.TemporaryDirectory() as directory:
            subprocess.run([
                "javac", "-d", directory,
                str(SOURCE / "EditHistoryPolicy.java"),
                str(ROOT / "tests/java/EditHistoryPolicyCheck.java"),
            ], check=True, capture_output=True)
            subprocess.run([
                "java", "-cp", directory, "EditHistoryPolicyCheck",
            ], check=True, capture_output=True)

    def test_capture_filters_and_releases_database_resources(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        source = (SOURCE / "EditHistoryCapture.java").read_text()
        source = re.sub(r"^(?:package org\.telegram\..*|import org\.telegram\..*);\n", "", source, flags=re.M)
        fixture = (ROOT / "tests/java/EditHistoryCaptureCheck.java.in").read_text()

        with tempfile.TemporaryDirectory() as directory:
            capture = Path(directory) / "EditHistoryCapture.java"
            check = Path(directory) / "EditHistoryCaptureCheck.java"
            capture.write_text(source)
            check.write_text(fixture)
            subprocess.run(["javac", "-d", directory, str(capture), str(check)],
                           check=True, capture_output=True)
            subprocess.run(["java", "-cp", directory, "EditHistoryCaptureCheck"],
                           check=True, capture_output=True)

    def test_media_replacement_detection(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        source = (SOURCE / "EditHistory.java").read_text()
        start = source.index("    private static void writeMediaIdentity(")
        end = source.index("    private static void restoreBeforePendingEdit", start)
        method = source[start:end]
        fixture = (ROOT / "tests/java/EditHistoryMediaCheck.java.in").read_text()

        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "EditHistoryMediaCheck.java"
            path.write_text(fixture.replace("MEDIA_IDENTITY_METHOD", method))
            subprocess.run(["javac", "-d", directory, str(path)], check=True, capture_output=True)
            subprocess.run(["java", "-cp", directory, "EditHistoryMediaCheck"],
                           check=True, capture_output=True)

    def test_history_is_persistent_deduplicated_and_scoped(self):
        source = (SOURCE / "EditHistory.java").read_text()
        schema = re.search(r'db.execSQL\("(CREATE TABLE.*?)"\);', source)[1]
        query = re.search(r'"(SELECT data FROM edit_versions.*?)"', source)[1]

        with tempfile.TemporaryDirectory() as directory:
            path = str(Path(directory) / "history.db")
            with sqlite3.connect(path) as db:
                db.execute(schema)
                records = [
                    (1, 100, 7, 10, "first", b"original"),
                    (1, 100, 7, 10, "first", b"original"),
                    (1, 100, 7, 11, "second", b"edited"),
                    (1, 100, 7, 11, "third", b"same second"),
                    (2, 100, 7, 10, "first", b"other account"),
                    (1, 200, 7, 10, "first", b"other chat"),
                    (1, 100, 8, 10, "first", b"other message"),
                ]
                db.executemany("INSERT OR IGNORE INTO edit_versions VALUES (?, ?, ?, ?, ?, ?)", records)

            with sqlite3.connect(path) as db:
                result = db.execute(query, (1, 100, 7)).fetchall()
                self.assertEqual(result, [(b"same second",), (b"edited",), (b"original",)])
                self.assertEqual(db.execute(query, (2, 100, 7)).fetchall(), [(b"other account",)])
                self.assertEqual(db.execute(query, (1, 100, 9)).fetchall(), [])

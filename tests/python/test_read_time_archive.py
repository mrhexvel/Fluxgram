from pathlib import Path
import re
import shutil
import sqlite3
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/fluxgram"
MESSAGES_STORAGE = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/MessagesStorage.java"
MESSAGES_CONTROLLER = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java"
STEALTH_ACTIVITY = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/FluxGramStealthActivity.java"


class ReadTimeArchiveTest(unittest.TestCase):
    def test_private_dialog_policy_and_timestamp_normalization(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        with tempfile.TemporaryDirectory() as directory:
            subprocess.run([
                "javac", "-d", directory,
                str(SOURCE / "ReadTimePolicy.java"),
                str(ROOT / "tests/java/ReadTimePolicyCheck.java"),
            ], check=True, capture_output=True)
            subprocess.run([
                "java", "-cp", directory, "ReadTimePolicyCheck",
            ], check=True, capture_output=True)

    def test_archive_schema_is_owner_scoped_and_first_write_wins(self):
        source = (SOURCE / "ReadTimeArchive.java").read_text()
        statements = re.findall(r'database\.execSQL\("(CREATE.*?)"\);', source)
        insert = re.search(r'"(INSERT OR IGNORE INTO read_times.*?)"', source)[1]

        with sqlite3.connect(":memory:") as database:
            for statement in statements:
                database.execute(statement)

            database.execute(insert, (1, 10, 7, 100))
            database.execute(insert, (1, 10, 7, 200))
            database.execute(insert, (2, 10, 7, 300))
            database.execute(insert, (1, 20, 7, 400))
            database.execute(insert, (1, 10, 8, 500))

            self.assertEqual(
                database.execute(
                    "SELECT read_at, exact FROM read_times WHERE owner = ? AND dialog = ? AND message = ?",
                    (1, 10, 7),
                ).fetchone(),
                (100, 0),
            )
            self.assertEqual(database.execute("SELECT COUNT(*) FROM read_times").fetchone(), (4,))

    def test_capture_runs_before_telegram_read_state_update(self):
        storage = MESSAGES_STORAGE.read_text()
        start = storage.index("    private void markMessagesAsReadInternal(")
        end = storage.index("    private void markMessagesContentAsReadInternal(", start)
        method = storage[start:end]

        self.assertLess(
            method.index("ReadTimeArchive.capture(this, currentAccount, outbox, outboxReadDate);"),
            method.index("UPDATE messages_v2 SET read_state = read_state | 1", method.index("if (!isEmpty(outbox))")),
        )
        self.assertIn("mid > 0 AND mid <= ?", (SOURCE / "ReadTimeArchive.java").read_text())
        self.assertIn("out = 1 AND read_state IN(0, 2)", (SOURCE / "ReadTimeArchive.java").read_text())

    def test_setting_timestamp_and_logout_are_wired(self):
        archive = (SOURCE / "ReadTimeArchive.java").read_text()
        activity = STEALTH_ACTIVITY.read_text()
        controller = MESSAGES_CONTROLLER.read_text()
        storage = MESSAGES_STORAGE.read_text()

        self.assertIn("checked[3] = ReadTimeArchive.isEnabled(currentAccount);", activity)
        self.assertIn("ReadTimeArchive.setEnabled(currentAccount, checked[index]);", activity)
        self.assertIn("date > 0 ? date : getConnectionsManager().getCurrentTime()", controller)
        self.assertIn("ReadTimeArchive.clearAccount(currentAccount);", storage)
        self.assertIn("getNoBackupFilesDir()", archive)
        self.assertIn('preferences.edit().clear().putLong(OWNER, owner).apply();', archive)
        self.assertIn("MAX_RECORDS = 100000", archive)


if __name__ == "__main__":
    unittest.main()

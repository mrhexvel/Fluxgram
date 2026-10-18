from pathlib import Path
import re
import shutil
import sqlite3
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"


class DeletedMessagesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not shutil.which("javac") or not shutil.which("java"):
            raise unittest.SkipTest("Requires JDK")

        source = (JAVA / "messenger/fluxgram/DeletedMessagesArchive.java").read_text()
        cls.archive_count = re.search(r'"(SELECT COUNT\(\*\) FROM fg_deleted_messages)"', source)[1]
        storage = (JAVA / "messenger/MessagesStorage.java").read_text()
        cls.schema = re.search(r'"(CREATE TABLE messages_v2\(.*?)"', storage)[1]
        cls.schema = cls.schema.replace("IF NOT EXISTS ", "")
        expressions = re.findall(r'db.executeFast\(("CREATE (?:UNIQUE INDEX|INDEX|TEMP VIEW).*?)\)\.stepThis\(\)\.dispose\(\);', source, re.S)
        expressions = [expr for expr in expressions if '" + archive' in expr or '" + view' in expr]
        cls.directory = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.directory.cleanup)
        path = Path(cls.directory.name)
        columns = [row[1] for row in cls.schema_columns()]
        names = ",".join(columns)
        code = '''public class ArchiveSqlCheck {
    public static void main(String[] args) {
        String original = "messages_v2";
        String archive = "fg_deleted_messages";
        String view = "fg_timeline_messages";
        String topic = "";
        boolean topics = false;
        String names = "NAMES";
        String live = "m." + names.replace(",", ",m.");
        String saved = "d." + names.replace(",", ",d.");
        PRINT
    }
}'''.replace("NAMES", names).replace("PRINT", "\n".join(f"System.out.println({expr});" for expr in expressions))
        (path / "ArchiveSqlCheck.java").write_text(code)
        subprocess.run(["javac", str(path / "ArchiveSqlCheck.java")], check=True, capture_output=True)
        result = subprocess.run(["java", "-cp", str(path), "ArchiveSqlCheck"], check=True, capture_output=True, text=True)
        cls.statements = result.stdout.splitlines()
        cls.topic_schema = re.search(r'"(CREATE TABLE messages_topics\(.*?)"', storage)[1]
        with sqlite3.connect(":memory:") as db:
            db.execute(cls.topic_schema)
            topic_names = ",".join(row[1] for row in db.execute("PRAGMA table_info(messages_topics)"))
        topic_code = code.replace("ArchiveSqlCheck", "ArchiveTopicSqlCheck")
        topic_code = topic_code.replace('"messages_v2"', '"messages_topics"').replace('"fg_deleted_messages"', '"fg_deleted_topics"')
        topic_code = topic_code.replace('"fg_timeline_messages"', '"fg_timeline_topics"').replace('String topic = "";', 'String topic = ", topic_id";')
        topic_code = topic_code.replace("boolean topics = false;", "boolean topics = true;").replace(names, topic_names)
        (path / "ArchiveTopicSqlCheck.java").write_text(topic_code)
        subprocess.run(["javac", str(path / "ArchiveTopicSqlCheck.java")], check=True, capture_output=True)
        result = subprocess.run(["java", "-cp", str(path), "ArchiveTopicSqlCheck"], check=True, capture_output=True, text=True)
        cls.topic_statements = result.stdout.splitlines()
        boundary_method = source[source.index("    public static String boundaryQuery"):source.index("    public boolean prepare")]
        boundary_code = "public class BoundarySqlCheck {" + boundary_method + 'public static void main(String[] args) {System.out.println(boundaryQuery("fg_timeline_messages", true, "uid = -1 AND mid > 0"));}}'
        (path / "BoundarySqlCheck.java").write_text(boundary_code)
        subprocess.run(["javac", str(path / "BoundarySqlCheck.java")], check=True, capture_output=True)
        result = subprocess.run(["java", "-cp", str(path), "BoundarySqlCheck"], check=True, capture_output=True, text=True)
        cls.boundary = result.stdout.strip()
        cls.select = re.search(r'messageSelect = ("SELECT m.read_state.*?timelineMessages.*?);', storage)[1]
        cls.select = cls.select.replace('" + archiveColumn + "', "m.fluxgram_deleted").replace('" + timelineMessages + "', "fg_timeline_messages")[1:-1]

    @classmethod
    def schema_columns(cls):
        with sqlite3.connect(":memory:") as db:
            db.execute(cls.schema)
            return db.execute("PRAGMA table_info(messages_v2)").fetchall()

    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        self.db.execute(self.schema)
        self.db.execute("CREATE INDEX live_date ON messages_v2(uid, date, mid)")
        self.db.execute("CREATE TABLE fg_deleted_messages AS SELECT * FROM messages_v2 WHERE 0")
        self.db.execute("CREATE TABLE fg_deleted_attachment_cleanup(uid INTEGER NOT NULL, mid INTEGER NOT NULL, PRIMARY KEY(uid, mid))")
        self.db.execute("CREATE TABLE randoms_v2(mid INTEGER, uid INTEGER, random_id INTEGER, PRIMARY KEY(mid, uid))")
        for statement in self.statements:
            self.db.execute(statement)

    def insert(self, table, records):
        self.db.executemany(f"INSERT INTO {table}(uid, mid, date, read_state, mention, data, is_channel) VALUES (?, ?, ?, 3, 0, X'01', ?)", records)

    def page(self, dialog, before=1000000):
        return self.db.execute(self.select + " WHERE m.uid = ? AND m.mid < ? ORDER BY m.date DESC, m.mid DESC LIMIT 50", (dialog, before)).fetchall()

    def test_pagination_deduplication_and_dialog_isolation(self):
        self.insert("messages_v2", [(1, i, i, 0) for i in range(1, 101, 2)])
        self.insert("fg_deleted_messages", [(1, i, i, 0) for i in range(2, 101)])
        self.insert("fg_deleted_messages", [(-2, i, i, 1) for i in range(1, 101)])
        first = self.page(1)
        second = self.page(1, first[-1][3])
        self.assertEqual([row[3] for row in first + second], list(range(100, 0, -1)))
        self.assertEqual([row[15] for row in first], [int(row[3] % 2 == 0) for row in first])
        self.assertEqual(len(self.page(-2)), 50)
        self.assertEqual(self.db.execute("SELECT COUNT(*) FROM fg_timeline_messages WHERE read_state IN(0,2) OR mention = 1").fetchone()[0], 0)

    def test_large_archive_uses_indexed_page_reads(self):
        self.insert("fg_deleted_messages", [(dialog, mid, mid, 1) for dialog in range(-100, 0) for mid in range(1, 1001)])
        steps = 0

        def progress():
            nonlocal steps
            steps += 100
            return 0

        self.db.set_progress_handler(progress, 100)
        rows = self.page(-20, 800)
        self.db.set_progress_handler(None, 0)
        self.assertEqual([row[3] for row in rows], list(range(799, 749, -1)))
        self.assertLess(steps, 20000)

    def test_deep_page_in_large_chat_does_not_sort_entire_archive(self):
        self.insert("fg_deleted_messages", [(-1, mid, mid, 1) for mid in range(1, 100001)])
        steps = 0

        def progress():
            nonlocal steps
            steps += 100
            return 0

        self.db.set_progress_handler(progress, 100)
        rows = self.db.execute(self.select + " WHERE m.uid = -1 AND m.date <= 50000 AND m.mid < 50000 ORDER BY m.date DESC, m.mid DESC LIMIT 50").fetchall()
        self.db.set_progress_handler(None, 0)
        self.assertEqual([row[3] for row in rows], list(range(49999, 49949, -1)))
        self.assertLess(steps, 10000)

    def test_chat_boundary_does_not_scan_message_bodies(self):
        self.insert("fg_deleted_messages", [(-1, mid, mid, 1) for mid in range(1, 100001)])
        steps = 0

        def progress():
            nonlocal steps
            steps += 100
            return 0

        self.db.set_progress_handler(progress, 100)
        result = self.db.execute(self.boundary).fetchone()
        self.db.set_progress_handler(None, 0)
        self.assertEqual(result[0], 100000)
        self.assertLess(steps, 1000)

    def test_topics_are_isolated_and_duplicates_are_suppressed(self):
        self.db.execute(self.topic_schema)
        self.db.execute("CREATE TABLE fg_deleted_topics AS SELECT * FROM messages_topics WHERE 0")
        for statement in self.topic_statements:
            self.db.execute(statement)
        self.db.executemany("INSERT INTO fg_deleted_topics(uid, mid, topic_id, date) VALUES (-1, ?, ?, ?)", [(7, 10, 1), (7, 20, 1), (8, 10, 2)])
        self.db.execute("INSERT INTO messages_topics(uid, mid, topic_id, date) VALUES (-1, 7, 10, 1)")
        rows = self.db.execute("SELECT mid, fluxgram_deleted FROM fg_timeline_topics WHERE uid = -1 AND topic_id = 10 ORDER BY date DESC, mid DESC LIMIT 50").fetchall()
        self.assertEqual(rows, [(8, 1), (7, 0)])
        self.assertEqual(self.db.execute("SELECT fluxgram_deleted FROM fg_timeline_topics WHERE topic_id = 20").fetchall(), [(1,)])

    def test_archive_survives_reopening_database(self):
        self.insert("fg_deleted_messages", [(1, 7, 1, 0)])
        self.db.commit()
        with tempfile.TemporaryDirectory() as directory:
            filename = str(Path(directory) / "cache.db")
            with sqlite3.connect(filename) as stored:
                self.db.backup(stored)
            with sqlite3.connect(filename) as reopened:
                for statement in self.statements:
                    reopened.execute(statement)
                self.assertEqual(reopened.execute("SELECT mid, fluxgram_deleted FROM fg_timeline_messages").fetchall(), [(7, 1)])

    def test_statistics_count_only_canonical_archive_rows(self):
        self.insert("fg_deleted_messages", [(1, 7, 1, 0), (1, 8, 2, 0), (-2, 9, 3, 1)])
        self.db.execute("CREATE TABLE fg_deleted_topics(uid INTEGER, mid INTEGER, topic_id INTEGER)")
        self.db.executemany(
            "INSERT INTO fg_deleted_topics VALUES (?, ?, ?)",
            [(1, 7, 10), (1, 7, 20), (1, 8, 10)],
        )

        self.assertEqual(self.db.execute(self.archive_count).fetchone(), (3,))

    def test_channel_ids_are_not_removed_by_direct_chat_delete(self):
        self.insert("fg_deleted_messages", [(1, 7, 1, 0), (-2, 7, 1, 1)])
        self.db.execute("DELETE FROM fg_deleted_messages WHERE mid IN(7) AND is_channel = 0")
        self.assertEqual(self.db.execute("SELECT uid FROM fg_deleted_messages").fetchall(), [(-2,)])

    def test_attachment_cleanup_survives_restart_after_archive_delete(self):
        self.insert(
            "fg_deleted_messages",
            [(1, 7, 1, 0), (1, 8, 2, 0), (2, 7, 3, 0)],
        )
        attachments = {(1, 7), (1, 8), (2, 7)}

        self.db.execute("SAVEPOINT fg_archive_remove")
        self.db.execute(
            "INSERT OR IGNORE INTO fg_deleted_attachment_cleanup(uid, mid) "
            "SELECT uid, mid FROM fg_deleted_messages WHERE uid = 1 AND mid IN(7)"
        )
        self.db.execute("DELETE FROM fg_deleted_messages WHERE uid = 1 AND mid IN(7)")
        self.db.execute("RELEASE fg_archive_remove")

        pending = self.db.execute(
            "SELECT uid, mid FROM fg_deleted_attachment_cleanup ORDER BY uid, mid"
        ).fetchall()
        self.assertEqual(pending, [(1, 7)])
        self.assertEqual(
            self.db.execute(
                "SELECT uid, mid FROM fg_deleted_messages ORDER BY uid, mid"
            ).fetchall(),
            [(1, 8), (2, 7)],
        )

        for key in pending:
            attachments.discard(key)
        self.db.executemany(
            "DELETE FROM fg_deleted_attachment_cleanup WHERE uid = ? AND mid = ?",
            pending,
        )

        self.assertEqual(attachments, {(1, 8), (2, 7)})
        self.assertEqual(
            self.db.execute("SELECT COUNT(*) FROM fg_deleted_attachment_cleanup").fetchone(),
            (0,),
        )

    def test_attachment_cleanup_is_durable_and_acknowledged_after_files(self):
        archive = (JAVA / "messenger/fluxgram/DeletedMessagesArchive.java").read_text()
        attachments = (JAVA / "messenger/fluxgram/SavedAttachments.java").read_text()
        remove = archive[
            archive.index("    public boolean remove("):
            archive.index("    public void removeDialog(")
        ]
        finish = archive[
            archive.index("    private void finishAttachmentCleanup("):
            archive.rindex("\n}")
        ]

        journal = remove.index(
            "INSERT OR IGNORE INTO fg_deleted_attachment_cleanup(uid, mid) SELECT uid, mid"
        )
        delete = remove.index('db.executeFast("DELETE FROM " + table + where)')

        self.assertLess(journal, delete)
        self.assertIn("SAVEPOINT fg_archive_remove", remove)
        self.assertIn("resumeAttachmentCleanup();", remove)
        self.assertIn("SavedAttachments.get(account).remove(messages, success ->", archive)
        self.assertIn("if (!success || initializedDatabase != storage.getDatabase())", finish)
        self.assertIn("DELETE FROM fg_deleted_attachment_cleanup", finish)
        self.assertIn("completed.accept(success);", attachments)
        self.assertIn("if (!active())", attachments)

    def test_failed_batch_rolls_back(self):
        self.insert("messages_v2", [(1, 1, 1, 0)])
        self.db.execute("SAVEPOINT fg_archive_batch")
        self.db.execute("INSERT INTO fg_deleted_messages SELECT * FROM messages_v2")
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO fg_deleted_messages SELECT * FROM messages_v2")
        self.db.execute("ROLLBACK TO fg_archive_batch")
        self.db.execute("RELEASE fg_archive_batch")
        self.assertEqual(self.db.execute("SELECT COUNT(*) FROM fg_deleted_messages").fetchone()[0], 0)
        self.assertEqual(len(self.page(1)), 1)

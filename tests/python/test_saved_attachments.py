from pathlib import Path
import re
import shutil
import sqlite3
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/fluxgram"
STEALTH_ACTIVITY = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/FluxGramStealthActivity.java"
RESOURCES = ROOT / "TMessagesProj/src/main/res"


class SavedAttachmentsTest(unittest.TestCase):
    def test_streaming_atomic_copy_and_deleted_source(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        with tempfile.TemporaryDirectory() as directory:
            subprocess.run([
                "javac", "-d", directory,
                str(SOURCE / "AttachmentCopy.java"),
                str(ROOT / "tests/java/AttachmentCopyCheck.java"),
            ], check=True, capture_output=True)
            subprocess.run([
                "java", "-Xmx24m", "-cp", directory, "AttachmentCopyCheck", directory,
            ], check=True, capture_output=True)

    def test_index_oldest_first_and_chat_isolation(self):
        source = (SOURCE / "SavedAttachments.java").read_text()
        statements = re.findall(r'database.execSQL\("(CREATE.*?)"\);', source)
        oldest = re.search(r'database.rawQuery\("(SELECT path, dialog, mid, size FROM attachments ORDER BY.*?)"', source)[1]

        with sqlite3.connect(":memory:") as db:
            for statement in statements:
                db.execute(statement)
            db.executemany("INSERT INTO attachments VALUES (?, ?, ?, ?, ?)", [
                ("1_7.jpg", 1, 7, 100, 30),
                ("2_7.pdf", 2, 7, 200, 10),
                ("1_8.jpg", 1, 8, 300, 20),
            ])
            self.assertEqual(db.execute(oldest).fetchone(), ("2_7.pdf", 2, 7, 200))
            db.execute("DELETE FROM attachments WHERE dialog = ? AND mid = ?", (1, 7))
            self.assertEqual(db.execute("SELECT SUM(size) FROM attachments").fetchone()[0], 500)
            plan = db.execute("EXPLAIN QUERY PLAN " + oldest).fetchall()
            self.assertTrue(any("attachments_age" in row[3] for row in plan))
            self.assertFalse(any("TEMP B-TREE" in row[3] for row in plan))

    def test_statistics_use_index_without_scanning_files(self):
        source = (SOURCE / "SavedAttachments.java").read_text()
        statements = re.findall(r'database.execSQL\("(CREATE.*?)"\);', source)
        query = re.search(
            r'"(SELECT COUNT\(\*\), COALESCE\(SUM\(size\), 0\) FROM attachments)"',
            source,
        )[1]
        start = source.index("    void loadStatistics(")
        end = source.index("    private void recordFailure(", start)
        method = source[start:end]

        self.assertNotIn("listFiles()", method)
        self.assertNotIn("open();", method)
        self.assertIn("failures.get()", method)

        with sqlite3.connect(":memory:") as db:
            for statement in statements:
                db.execute(statement)
            db.executemany("INSERT INTO attachments VALUES (?, ?, ?, ?, ?)", [
                ("1_7.jpg", 1, 7, 100, 10),
                ("1_8.pdf", 1, 8, 2 ** 31, 20),
                ("2_9.jpg", 2, 9, 300, 30),
            ])

            self.assertEqual(db.execute(query).fetchone(), (3, 2 ** 31 + 400))

    def test_provider_exposes_only_attachment_directory(self):
        root = ET.parse(ROOT / "TMessagesProj/src/main/res/xml/provider_paths.xml").getroot()
        paths = [item for item in root if item.attrib.get("name") == "fluxgram_attachments"]
        self.assertEqual(len(paths), 1)
        self.assertEqual(paths[0].tag, "files-path")
        self.assertEqual((Path("/data/user/0/app/files") / paths[0].attrib["path"]).resolve(),
                         Path("/data/user/0/app/no_backup/fluxgram_attachments"))

    def test_attachment_switch_requires_deleted_message_retention(self):
        source = STEALTH_ACTIVITY.read_text()
        switch = source[source.index("    private void addSwitch("):source.index("    private void addStatistic(")]
        deleted_branch = re.search(r"if \(index == 0\) \{(.*?)\} else if", switch, re.DOTALL)[1]

        self.assertIn("checked[0] ? R.string.FluxGramStealthAttachmentOptions", source)
        self.assertIn(": R.string.FluxGramStealthAttachmentRequiresDeleted", source)
        self.assertIn("boolean enabled = index != 4 || checked[0];", switch)
        self.assertIn("cell.setEnabled(enabled, null);", switch)
        self.assertIn("if (enabled) {", switch)
        self.assertIn("list.adapter.update(false);", deleted_branch)
        self.assertNotIn("SavedAttachments", deleted_branch)

    def test_removed_stealth_explanations_are_not_rendered(self):
        source = STEALTH_ACTIVITY.read_text()

        self.assertNotIn("FluxGramStealthPreview", source)
        self.assertNotIn("FluxGramAttachmentInfo", source)

        for directory in ("values", "values-ru"):
            names = {
                item.attrib["name"]
                for item in ET.parse(RESOURCES / directory / "strings.xml").getroot()
                if item.tag == "string"
            }
            self.assertNotIn("FluxGramStealthPreview", names)
            self.assertNotIn("FluxGramAttachmentInfo", names)
            self.assertIn("FluxGramStealthAttachmentRequiresDeleted", names)

    def test_downloaded_video_variant_and_voice_selection(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        source = (SOURCE / "SavedAttachments.java").read_text()
        supported = source[source.index("    private static boolean supported("):source.index("    private File file(")]
        selection = source[source.index("    private static final class Source"):source.index("    private void open()")]
        template = (ROOT / "tests/java/SavedAttachmentMediaCheck.java.in").read_text()
        code = "import org.telegram.messenger.fluxgram.AttachmentCopy;\n" + template.replace("SUPPORTED_METHOD", supported).replace("SOURCE_METHODS", selection)

        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "SavedAttachmentMediaCheck.java"
            target.write_text(code)
            subprocess.run([
                "javac", "-d", directory, str(SOURCE / "AttachmentCopy.java"), str(target),
            ], check=True, capture_output=True)
            subprocess.run([
                "java", "-cp", directory, "SavedAttachmentMediaCheck", directory,
            ], check=True, capture_output=True)

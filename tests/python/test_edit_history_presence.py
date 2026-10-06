from pathlib import Path
import re
import sqlite3
import unittest


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'TMessagesProj/src/main/java/org/telegram/messenger/fluxgram/EditHistory.java'


class EditHistoryPresenceTest(unittest.TestCase):
    def test_presence_is_scoped_and_tracks_removal(self):
        source = SOURCE.read_text()
        schema = re.search(r'"(CREATE TABLE IF NOT EXISTS edit_versions .*?)"', source)[1]
        query = re.search(r'"(SELECT 1 FROM edit_versions .*?)"', source)[1]

        with sqlite3.connect(':memory:') as db:
            db.execute(schema)
            self.assertIsNone(db.execute(query, (1, 20, 30)).fetchone())

            db.execute('INSERT INTO edit_versions VALUES (?, ?, ?, ?, ?, ?)',
                       (1, 20, 30, 100, 'first', b'version'))
            self.assertEqual(db.execute(query, (1, 20, 30)).fetchone(), (1,))

            for key in ((2, 20, 30), (1, 21, 30), (1, 20, 31)):
                self.assertIsNone(db.execute(query, key).fetchone())

            plan = db.execute('EXPLAIN QUERY PLAN ' + query, (1, 20, 30)).fetchall()
            self.assertTrue(any('COVERING INDEX' in row[3] for row in plan), plan)

            db.execute('DELETE FROM edit_versions WHERE owner = ? AND dialog = ? AND message = ?',
                       (1, 20, 30))
            self.assertIsNone(db.execute(query, (1, 20, 30)).fetchone())

    def test_clear_versions_is_owner_scoped_and_async(self):
        source = SOURCE.read_text()
        schema = re.search(r'"(CREATE TABLE IF NOT EXISTS edit_versions .*?)"', source)[1]
        query = re.search(r'"(SELECT 1 FROM edit_versions .*?)"', source)[1]
        clear_start = source.index('    public static void clearVersions(')
        clear_end = source.index('    public static void load(', clear_start)
        method = source[clear_start:clear_end]
        where = re.search(
            r'db\.delete\("edit_versions", "(owner = \?)", '
            r'new String\[\] \{Long\.toString\(owner\)\}\);',
            method,
        )[1]

        self.assertIn('MessagesStorage.getInstance(account).getStorageQueue().postRunnable', method)
        self.assertIn('AndroidUtilities.runOnUIThread', method)
        self.assertIn('owner == UserConfig.getInstance(account).getClientUserId()', method)
        self.assertNotIn('SavedAttachments.clearAccount', method)
        self.assertNotIn('preferences(account).edit().clear()', method)
        self.assertNotIn('SQLiteDatabase.deleteDatabase', method)

        with sqlite3.connect(':memory:') as db:
            db.execute(schema)
            records = [
                (1, 20, 30, 100, 'first', b'first version'),
                (1, 21, 31, 101, 'second', b'second version'),
                (2, 20, 30, 100, 'other', b'other owner'),
            ]
            db.executemany('INSERT INTO edit_versions VALUES (?, ?, ?, ?, ?, ?)', records)

            db.execute('DELETE FROM edit_versions WHERE ' + where, (1,))

            self.assertIsNone(db.execute(query, (1, 20, 30)).fetchone())
            self.assertIsNone(db.execute(query, (1, 21, 31)).fetchone())
            self.assertEqual(db.execute(query, (2, 20, 30)).fetchone(), (1,))

    def test_statistics_count_saved_versions_for_current_owner(self):
        source = SOURCE.read_text()
        schema = re.search(r'"(CREATE TABLE IF NOT EXISTS edit_versions .*?)"', source)[1]
        query = re.search(r'"(SELECT COUNT\(\*\) FROM edit_versions WHERE owner = \?)"', source)[1]

        with sqlite3.connect(':memory:') as db:
            db.execute(schema)
            records = [
                (1, 20, 30, 100, 'first', b'first version'),
                (1, 20, 30, 101, 'second', b'second version'),
                (1, 21, 31, 100, 'third', b'third version'),
                (2, 20, 30, 100, 'other', b'other owner'),
            ]
            db.executemany('INSERT INTO edit_versions VALUES (?, ?, ?, ?, ?, ?)', records)

            self.assertEqual(db.execute(query, (1,)).fetchone(), (3,))
            self.assertEqual(db.execute(query, (2,)).fetchone(), (1,))

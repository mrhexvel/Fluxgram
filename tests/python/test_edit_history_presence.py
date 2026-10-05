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

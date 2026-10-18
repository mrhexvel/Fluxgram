from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "TMessagesProj/src/main/java/org/telegram"


class LocalStorageStatisticsTest(unittest.TestCase):
    def test_sources_are_aggregated_off_ui_and_owner_scoped(self):
        source = (SOURCE / "messenger/fluxgram/LocalStorageStatistics.java").read_text()

        self.assertIn("storage.getStorageQueue().postRunnable", source)
        self.assertIn("SavedAttachments.get(account).loadStatistics", source)
        self.assertIn("responses != 2", source)
        self.assertIn("owner == UserConfig.getInstance(account).getClientUserId()", source)
        self.assertIn("? new Result(this) : null", source)
        self.assertIn("editVersionsAvailable", source)
        self.assertIn("deletedMessagesAvailable", source)
        self.assertIn("attachmentsAvailable", source)

    def test_activity_invalidates_stale_statistics(self):
        source = (SOURCE / "ui/FluxGramStealthActivity.java").read_text()

        self.assertIn("int generation = ++statisticsGeneration", source)
        self.assertIn("generation != statisticsGeneration", source)
        self.assertIn("currentAccount != account", source)
        self.assertIn("statisticsGeneration++;", source)

    def test_clear_saved_data_uses_owner_scoped_background_services(self):
        cleanup = (SOURCE / "messenger/fluxgram/LocalStorageCleanup.java").read_text()
        archive = (SOURCE / "messenger/fluxgram/DeletedMessagesArchive.java").read_text()
        activity = (SOURCE / "ui/FluxGramStealthActivity.java").read_text()

        self.assertIn("EditHistory.clearVersions(account, collector::complete);", cleanup)
        self.assertIn("getDeletedArchive().clear(collector::complete);", cleanup)
        self.assertIn("SavedAttachments.get(account).clearFiles(collector::complete);", cleanup)
        self.assertIn("responses == 3", cleanup)
        self.assertIn("owner == UserConfig.getInstance(account).getClientUserId()", cleanup)
        self.assertIn("SAVEPOINT fg_archive_clear", archive)
        self.assertLess(
            archive.index("INSERT OR IGNORE INTO fg_deleted_attachment_cleanup", archive.index("public void clear(")),
            archive.index("DELETE FROM fg_deleted_messages", archive.index("public void clear(")),
        )
        self.assertIn("LocalStorageCleanup.clear(account, success ->", activity)
        self.assertIn("FluxGramStealthClearInfo", activity)
        self.assertIn("FluxGramStealthRetentionInfo", activity)
        self.assertIn("cell.setEnabled(!storageClearRunning, null);", activity)

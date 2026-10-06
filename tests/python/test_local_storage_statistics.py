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

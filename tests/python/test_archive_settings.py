from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"


class ArchiveSettingsTest(unittest.TestCase):
    def test_visibility_setting_reuses_telegram_archive_state(self):
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()
        shared_config = (JAVA / "messenger/SharedConfig.java").read_text()
        chats_section = activity.split("if (section == CHATS) {", 1)[1].split(
            "PluginManager manager", 1
        )[0]

        self.assertIn("addSectionLabel(items, R.string.ArchivedChats);", chats_section)
        self.assertIn("LocaleController.getString(R.string.HideAboveTheList)", chats_section)
        self.assertIn(".setChecked(SharedConfig.archiveHidden)", chats_section)
        self.assertEqual(1, activity.count("SharedConfig.toggleArchiveHidden();"))
        self.assertIn("parentLayout.rebuildFragments(0);", activity)
        self.assertIn('getBoolean("archiveHidden", false)', shared_config)
        self.assertIn('editor.putBoolean("archiveHidden", archiveHidden);', shared_config)

    def test_visibility_slice_does_not_change_pull_or_swipe_state_machines(self):
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()
        dialogs = (JAVA / "ui/DialogsActivity.java").read_text()

        self.assertNotIn("archivePullViewState", activity)
        self.assertNotIn("ItemTouchHelper", activity)
        self.assertIn("ARCHIVE_ITEM_STATE_HIDDEN", dialogs)
        self.assertIn("public int getMovementFlags", dialogs)
        self.assertIn("public void onSwiped", dialogs)


if __name__ == "__main__":
    unittest.main()

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

    def test_pull_setting_defaults_to_telegram_behavior(self):
        settings = (JAVA / "messenger/fluxgram/ArchiveSettings.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        self.assertIn('"fluxgram_archive_reveal_on_pull"', settings)
        self.assertIn("preferences.getBoolean(REVEAL_ON_PULL, true)", settings)
        self.assertIn("ArchiveSettings.setRevealsOnPull(enabled);", activity)
        self.assertIn(".setChecked(ArchiveSettings.revealsOnPull())", activity)

    def test_pull_setting_gates_only_archive_reveal_transitions(self):
        dialogs = (JAVA / "ui/DialogsActivity.java").read_text()

        self.assertIn(
            "if (!ArchiveSettings.revealsOnPull() || diff < height",
            dialogs,
        )
        self.assertIn(
            "boolean canShowInternal = ArchiveSettings.revealsOnPull()",
            dialogs,
        )
        self.assertIn(
            "viewPage.archivePullViewState == ARCHIVE_ITEM_STATE_HIDDEN\n"
            "                                && ArchiveSettings.revealsOnPull()",
            dialogs,
        )
        self.assertEqual(5, dialogs.count("ArchiveSettings.revealsOnPull()"))
        self.assertIn("setStoriesOvercroll(viewPage, newOverScroll)", dialogs)


if __name__ == "__main__":
    unittest.main()

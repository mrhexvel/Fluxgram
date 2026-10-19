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

    def test_unarchive_swipe_setting_defaults_to_telegram_behavior(self):
        settings = (JAVA / "messenger/fluxgram/ArchiveSettings.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        self.assertIn('"fluxgram_disable_unarchive_swipe"', settings)
        self.assertIn(
            "preferences.getBoolean(DISABLE_UNARCHIVE_SWIPE, false)",
            settings,
        )
        self.assertIn("ArchiveSettings.setDisablesUnarchiveSwipe(disabled);", activity)
        self.assertIn(
            ".setChecked(ArchiveSettings.disablesUnarchiveSwipe())",
            activity,
        )

    def test_unarchive_swipe_is_checked_before_gesture_and_action(self):
        dialogs = (JAVA / "ui/DialogsActivity.java").read_text()
        swipe_controller = dialogs.split("private class SwipeController", 1)[1]
        movement = swipe_controller.split("public int getMovementFlags", 1)[1].split(
            "public boolean onMove", 1
        )[0]
        action = swipe_controller.split("public void onSwiped", 1)[1].split(
            "TLRPC.Dialog dialog", 1
        )[0]

        self.assertIn("if (actionBar.isActionModeShowed(null))", movement)
        self.assertIn(
            "folderId == 1 && ArchiveSettings.disablesUnarchiveSwipe()",
            movement,
        )
        self.assertGreater(
            movement.index("if (actionBar.isActionModeShowed(null))"),
            movement.index("long dialogId = dialogCell.getDialogId();"),
        )
        self.assertGreater(
            movement.index("folderId == 1 && ArchiveSettings.disablesUnarchiveSwipe()"),
            movement.index("} else {"),
        )
        self.assertIn(
            "folderId == 1 && ArchiveSettings.disablesUnarchiveSwipe()",
            action,
        )
        self.assertIn("parentPage.dialogsAdapter.notifyItemChanged(position);", action)
        self.assertEqual(2, dialogs.count("ArchiveSettings.disablesUnarchiveSwipe()"))


if __name__ == "__main__":
    unittest.main()

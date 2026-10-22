from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"


class FluxGramSingleChoiceDialogsTest(unittest.TestCase):
    @staticmethod
    def method(source, name):
        start = source.index(f"    private void {name}(")
        end = source.find("\n    private ", start + 1)
        return source[start:] if end < 0 else source[start:end]

    def test_fluxgram_selection_dialogs_use_telegram_radio_cells(self):
        alerts = (JAVA / "ui/Components/AlertsCreator.java").read_text()
        settings = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()
        ghost = (JAVA / "ui/FluxGramGhostModeActivity.java").read_text()
        fonts = (JAVA / "ui/FluxGramFontSettingsActivity.java").read_text()
        stealth = (JAVA / "ui/FluxGramStealthActivity.java").read_text()
        plugins = (JAVA / "ui/PluginSettingsActivity.java").read_text()

        helper = alerts.split(
            "public static Dialog createSingleChoiceDialog(Activity parentActivity, final CharSequence[] options", 1
        )[1].split("public static AlertDialog.Builder createTTLAlert", 1)[0]

        self.assertIn("new AlertDialog.Builder(parentActivity, resourcesProvider)", helper)
        self.assertIn("new RadioColorCell(parentActivity, resourcesProvider)", helper)
        self.assertIn("Theme.key_radioBackground, resourcesProvider", helper)
        self.assertIn("Theme.key_dialogRadioBackgroundChecked, resourcesProvider", helper)
        self.assertIn("builder.getDismissRunnable().run()", helper)
        self.assertLess(
            helper.index("builder.getDismissRunnable().run()"),
            helper.index("listener.onClick(null, sel)"),
        )

        settings_dialogs = (
            "showDoubleTapActionDialog",
            "showFolderPresentationDialog",
            "showFolderCounterDialog",
            "showAppearanceTitleDialog",
            "showStickerShapeDialog",
            "showTransitionStyleDialog",
            "showBottomNavigationDialog",
            "showTabletModeDialog",
            "showDividerStyleDialog",
            "showGlassOutlineStyleDialog",
        )

        self.assertEqual(10, settings.count("AlertsCreator.createSingleChoiceDialog"))
        for name in settings_dialogs:
            dialog = self.method(settings, name)
            self.assertEqual(1, dialog.count("AlertsCreator.createSingleChoiceDialog"), name)
            self.assertIn("selected,", dialog, name)
            self.assertNotIn(".setItems(", dialog, name)

        ghost_dialog = self.method(ghost, "showSilentSendModeDialog")
        font_dialog = self.method(fonts, "showModeDialog")
        self.assertEqual(1, ghost_dialog.count("AlertsCreator.createSingleChoiceDialog"))
        self.assertEqual(1, font_dialog.count("AlertsCreator.createSingleChoiceDialog"))
        self.assertIn("selected,", ghost_dialog)
        self.assertIn("selected,", font_dialog)

        self.assertEqual(1, stealth.count("AlertsCreator.createSingleChoiceDialog"))
        self.assertIn("archive.fileLimitIndex(),", stealth)
        self.assertEqual(2, stealth.count(".setItems(new CharSequence[]"))
        self.assertNotIn("createSingleChoiceDialog", self.method(stealth, "showChatSelection"))
        self.assertNotIn("createSingleChoiceDialog", self.method(stealth, "showDeletedChatSelection"))

        self.assertEqual(1, plugins.count("AlertsCreator.createSingleChoiceDialog"))
        self.assertIn("selectedIndex,", plugins)

        for source in (settings, ghost, fonts, plugins):
            self.assertNotIn('"✓  "', source)


if __name__ == "__main__":
    unittest.main()

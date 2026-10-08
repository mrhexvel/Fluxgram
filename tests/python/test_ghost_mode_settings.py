from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
RES = ROOT / "TMessagesProj/src/main/res"


class GhostModeSettingsTest(unittest.TestCase):
    def test_preferences_are_account_and_owner_scoped_with_safe_defaults(self):
        source = (JAVA / "messenger/fluxgram/GhostModeSettings.java").read_text()

        self.assertIn('"fluxgram_ghost_mode_" + account', source)
        self.assertIn("getClientUserId()", source)
        self.assertIn("preferences.getLong(OWNER, 0) != owner", source)
        self.assertIn("preferences.edit().clear().putLong(OWNER, owner).apply()", source)
        self.assertIn("preferences.getBoolean(key, false)", source)

        for key in ("enabled", "hide_online", "hide_typing", "hide_read_receipts", "hide_story_views"):
            self.assertIn(f'"{key}"', source)

    def test_logout_clears_ghost_mode_preferences(self):
        storage = (JAVA / "messenger/MessagesStorage.java").read_text()
        cleanup = storage[storage.index("    public void cleanup(boolean isLogin)"):
                          storage.index("    public void saveSecretParams", storage.index("    public void cleanup(boolean isLogin)"))]

        self.assertIn("GhostModeSettings.clearAccount(currentAccount);", cleanup)

    def test_screen_only_persists_settings_in_first_stage(self):
        source = (JAVA / "ui/FluxGramGhostModeActivity.java").read_text()

        self.assertIn("UItem.asExpandableSwitch(", source)
        self.assertIn('selected + "/4"', source)
        self.assertIn(".setCollapsed(!expanded)", source)
        self.assertIn("expanded = !expanded;", source)
        self.assertIn("GhostModeSettings.setEnabled(currentAccount, !GhostModeSettings.isEnabled(currentAccount));", source)
        self.assertIn(".setEnabled(enabled)", source)
        self.assertIn("if (expanded)", source)
        self.assertNotIn("MessagesController", source)
        self.assertNotIn("ConnectionsManager", source)

    def test_settings_home_has_separate_ghost_and_storage_routes(self):
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()
        home = (JAVA / "ui/Components/FluxGramSettingsHomeView.java").read_text()

        self.assertIn("new FluxGramGhostModeActivity()", activity)
        self.assertIn("new FluxGramStealthActivity()", activity)
        self.assertIn("R.string.FluxGramGhostMode", home)
        self.assertIn("R.string.FluxGramStealth", home)

    def test_english_and_russian_strings_are_complete(self):
        required = {
            "FluxGramGhostMode",
            "FluxGramGhostModeDescription",
            "FluxGramGhostModeDisabledInfo",
            "FluxGramGhostModeHideOnline",
            "FluxGramGhostModeHideTyping",
            "FluxGramGhostModeHideReadReceipts",
            "FluxGramGhostModeHideStoryViews",
            "FluxGramGhostModePreviewInfo",
        }

        for path in (RES / "values/strings.xml", RES / "values-ru/strings.xml"):
            names = {item.attrib["name"] for item in ET.parse(path).getroot().findall("string")}

            self.assertTrue(required.issubset(names), path)


if __name__ == "__main__":
    unittest.main()

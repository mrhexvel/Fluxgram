from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
ACTIVITY = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/FluxGramSettingsActivity.java"


class FluxGramSettingsNavigationTest(unittest.TestCase):
    def test_ghost_mode_and_storage_are_home_categories(self):
        source = ACTIVITY.read_text()
        home_items = source.split("if (section == HOME) {", 1)[1].split(
            "if (section == GENERAL) {", 1
        )[0]
        home_clicks = source.split("if (section == HOME) {", 2)[2].split(
            "if (section == GENERAL) {", 1
        )[0]

        self.assertIn("GHOST_MODE,", home_items)
        self.assertIn("STEALTH,", home_items)
        self.assertLess(
            home_items.index("addSectionLabel(items, R.string.FluxGramPrivacy)"),
            home_items.index("addSectionLabel(items, R.string.FluxGramCategories)"),
        )
        privacy = home_items.split(
            "addSectionLabel(items, R.string.FluxGramPrivacy)", 1
        )[1].split("addSectionLabel(items, R.string.FluxGramCategories)", 1)[0]
        self.assertIn("GHOST_MODE,", privacy)
        self.assertIn("STEALTH,", privacy)
        self.assertIn("items.add(UItem.asShadow(null));", privacy)
        self.assertIn("new FluxGramGhostModeActivity()", home_clicks)
        self.assertIn("new FluxGramStealthActivity()", home_clicks)

    def test_nested_sections_do_not_duplicate_navigation_rows(self):
        source = ACTIVITY.read_text()
        fill_items = source.split("private void fillItems", 1)[1].split(
            "PluginManager manager", 1
        )[0]
        general_items = fill_items.split("if (section == GENERAL) {", 1)[1].split(
            "if (section == APPEARANCE) {", 1
        )[0]
        chats_items = fill_items.split("if (section == CHATS) {", 1)[1]

        self.assertNotIn("GHOST_MODE,", general_items)
        self.assertNotIn("STEALTH,", chats_items)


if __name__ == "__main__":
    unittest.main()

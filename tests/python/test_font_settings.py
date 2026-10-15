from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"


class FontSettingsTest(unittest.TestCase):
    def test_configuration_is_global_and_has_bounded_roles(self):
        source = (JAVA / "messenger/fluxgram/FontSettings.java").read_text()

        self.assertIn('getSharedPreferences(\n                "mainconfig"', source)
        self.assertNotIn("UserConfig", source)
        self.assertIn("MODE_TELEGRAM = 0", source)
        self.assertIn("MODE_SYSTEM = 1", source)
        self.assertIn("MODE_CUSTOM = 2", source)

        for role in (
            "REGULAR",
            "MEDIUM",
            "BOLD",
            "EXTRA_BOLD",
            "ITALIC",
            "BOLD_ITALIC",
            "MONOSPACE",
        ):
            self.assertIn(role, source)

    def test_import_is_private_bounded_validated_and_atomic(self):
        source = (JAVA / "messenger/fluxgram/FontSettings.java").read_text()

        self.assertIn("MAX_FONT_BYTES = 16 * 1024 * 1024", source)
        self.assertIn("getNoBackupFilesDir()", source)
        self.assertIn('"fluxgram/fonts"', source)
        self.assertIn("resolver.openInputStream(uri)", source)
        self.assertIn("output.getFD().sync()", source)
        self.assertIn("new Typeface.Builder(file).build()", source)
        self.assertIn("Typeface.createFromFile(file)", source)
        self.assertIn("Os.rename(temporary.getAbsolutePath(), fontFile(role).getAbsolutePath())", source)

    def test_custom_fallbacks_preserve_monospace(self):
        source = (JAVA / "messenger/fluxgram/FontSettings.java").read_text()

        self.assertIn("if (role == Role.MONOSPACE)", source)
        self.assertIn("return telegramTypeface(Role.MONOSPACE);", source)
        self.assertIn("loadCustomTypeface(Role.REGULAR)", source)
        self.assertIn("loadCustomTypeface(Role.MEDIUM)", source)
        self.assertIn("loadCustomTypeface(Role.BOLD)", source)
        self.assertIn("loadCustomTypeface(Role.ITALIC)", source)

    def test_screen_uses_telegram_components_and_background_import(self):
        activity = (JAVA / "ui/FluxGramFontSettingsActivity.java").read_text()
        preview = (JAVA / "ui/Components/FluxGramFontPreviewCell.java").read_text()

        self.assertIn("new UniversalRecyclerView(", activity)
        self.assertIn("UItem.asCustomShadow(label, LayoutHelper.WRAP_CONTENT)", activity)
        self.assertIn("Intent.ACTION_OPEN_DOCUMENT", activity)
        self.assertIn("Intent.CATEGORY_OPENABLE", activity)
        self.assertIn("Utilities.globalQueue.postRunnable", activity)
        self.assertIn("ItemOptions.makeOptions(this, anchor)", activity)
        self.assertIn("FontSettings.getPreviewTypeface", preview)

    def test_fonts_section_precedes_chat_list(self):
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        fonts = activity.index("addSectionLabel(items, R.string.FluxGramFonts);")
        chat_list = activity.index("addSectionLabel(items, R.string.FluxGramChatList);")
        self.assertLess(fonts, chat_list)
        self.assertIn("presentFragment(new FluxGramFontSettingsActivity())", activity)

    def test_first_stage_does_not_change_production_typeface_routes(self):
        android_utilities = (JAVA / "messenger/AndroidUtilities.java").read_text()
        theme = (JAVA / "ui/ActionBar/Theme.java").read_text()

        self.assertNotIn("FontSettings", android_utilities)
        self.assertNotIn("FontSettings", theme)


if __name__ == "__main__":
    unittest.main()

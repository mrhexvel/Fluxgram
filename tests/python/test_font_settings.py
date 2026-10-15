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
        self.assertIn("customTypefaces.get(Role.REGULAR)", source)
        self.assertIn("customTypefaces.get(Role.MEDIUM)", source)
        self.assertIn("customTypefaces.get(Role.BOLD)", source)
        self.assertIn("customTypefaces.get(Role.ITALIC)", source)

    def test_screen_uses_telegram_components_and_background_import(self):
        activity = (JAVA / "ui/FluxGramFontSettingsActivity.java").read_text()
        preview = (JAVA / "ui/Components/FluxGramFontPreviewCell.java").read_text()

        self.assertIn("new UniversalRecyclerView(", activity)
        self.assertIn("UItem.asCustomShadow(label, LayoutHelper.WRAP_CONTENT)", activity)
        self.assertIn("Intent.ACTION_OPEN_DOCUMENT", activity)
        self.assertIn("Intent.CATEGORY_OPENABLE", activity)
        self.assertIn("Utilities.globalQueue.postRunnable", activity)
        self.assertIn("FontSettings.preparePreview()", activity)
        self.assertIn("ItemOptions.makeOptions(this, anchor)", activity)
        self.assertIn("FontSettings.getPreviewTypeface", preview)

    def test_fonts_section_precedes_chat_list(self):
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        fonts = activity.index("addSectionLabel(items, R.string.FluxGramFonts);")
        chat_list = activity.index("addSectionLabel(items, R.string.FluxGramChatList);")
        self.assertLess(fonts, chat_list)
        self.assertIn("presentFragment(new FluxGramFontSettingsActivity())", activity)

    def test_active_configuration_is_captured_before_android_utilities(self):
        settings = (JAVA / "messenger/fluxgram/FontSettings.java").read_text()
        loader = (JAVA / "messenger/ApplicationLoader.java").read_text()

        self.assertLess(
            loader.index("FontSettings.initialize();"),
            loader.index("AndroidUtilities.getHelloWorld()"),
        )
        self.assertIn("if (active != null)", settings)
        self.assertIn("active = new Active(mode, typefaces);", settings)
        self.assertIn("return resolveTypeface(active.mode, role, active.typefaces);", settings)

    def test_android_utilities_routes_supported_font_roles(self):
        android_utilities = (JAVA / "messenger/AndroidUtilities.java").read_text()
        settings = (JAVA / "messenger/fluxgram/FontSettings.java").read_text()

        self.assertIn("FontSettings.getActiveTypeface(FontSettings.Role.REGULAR)", android_utilities)
        self.assertIn("FontSettings.getActiveTypeface(FontSettings.Role.BOLD)", android_utilities)
        self.assertIn("FontSettings.getActiveTypefaceForAsset(assetPath)", android_utilities)
        self.assertIn("return getAssetTypeface(assetPath);", android_utilities)
        self.assertIn("TYPEFACE_ROBOTO_MEDIUM.equals(assetPath)", settings)
        self.assertIn("TYPEFACE_ROBOTO_EXTRA_BOLD.equals(assetPath)", settings)
        self.assertIn("TYPEFACE_ROBOTO_MEDIUM_ITALIC.equals(assetPath)", settings)
        self.assertIn("TYPEFACE_ROBOTO_MONO.equals(assetPath)", settings)
        self.assertIn('"fonts/ritalic.ttf".equals(assetPath)', settings)

        asset_mapping = settings.split(
            "private static Role roleForAssetPath", 1
        )[1].split("private static String assetPathForRole", 1)[0]
        self.assertNotIn("num.otf", asset_mapping)
        self.assertNotIn("mw_bold", asset_mapping)
        self.assertNotIn("rcondensed", asset_mapping)

        paint_typeface = (
            JAVA / "ui/Components/Paint/PaintTypeface.java"
        ).read_text()
        self.assertIn("AndroidUtilities.getAssetTypeface", paint_typeface)
        self.assertNotIn(
            "AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_",
            paint_typeface,
        )

    def test_main_text_primitives_and_theme_use_resolver(self):
        theme = (JAVA / "ui/ActionBar/Theme.java").read_text()
        simple = (JAVA / "ui/ActionBar/SimpleTextView.java").read_text()
        animated = (JAVA / "ui/Components/AnimatedTextView.java").read_text()
        edit = (JAVA / "ui/Components/EditTextEffects.java").read_text()

        self.assertIn("textPaint.setTypeface(AndroidUtilities.regular())", simple)
        self.assertIn("textPaint.setTypeface(AndroidUtilities.regular())", animated)
        self.assertIn("setTypeface(AndroidUtilities.regular())", edit)
        self.assertIn("chat_msgTextPaint.setTypeface(AndroidUtilities.regular())", theme)
        self.assertIn("dialogs_messagePaint[a].setTypeface(AndroidUtilities.regular())", theme)
        self.assertIn("chat_msgTextCodePaint.setTypeface(AndroidUtilities.monospace())", theme)
        self.assertNotIn("chat_msgTextCodePaint.setTypeface(Typeface.MONOSPACE)", theme)

    def test_settings_and_profile_information_use_resolver(self):
        settings = (JAVA / "ui/SettingsActivity.java").read_text()
        detail = (JAVA / "ui/Cells/TextDetailCell.java").read_text()
        about = (JAVA / "ui/Cells/AboutLinkCell.java").read_text()
        notifications = (JAVA / "ui/Cells/NotificationsCheckCell.java").read_text()
        theme = (JAVA / "ui/ActionBar/Theme.java").read_text()

        self.assertGreaterEqual(
            settings.count("setTypeface(AndroidUtilities.regular())"), 5
        )
        self.assertIn("textView.setTypeface(AndroidUtilities.regular())", detail)
        self.assertIn("valueTextView.setTypeface(AndroidUtilities.regular())", detail)
        self.assertIn("rightValueTextView.setTypeface(AndroidUtilities.regular())", detail)
        self.assertIn("valueTextView.setTypeface(AndroidUtilities.regular())", about)
        self.assertIn("showMoreTextView.setTypeface(AndroidUtilities.regular())", about)
        self.assertIn("textView.setTypeface(AndroidUtilities.regular())", notifications)
        self.assertIn("multilineValueTextView.setTypeface(AndroidUtilities.regular())", notifications)
        self.assertIn("profile_aboutTextPaint.setTypeface(AndroidUtilities.regular())", theme)

    def test_chat_composer_and_mentions_use_regular_role(self):
        composer = (
            JAVA / "ui/Components/ChatActivityEnterView.java"
        ).read_text()
        mentions = (JAVA / "ui/Cells/MentionCell.java").read_text()

        self.assertIn(
            "messageEditText.setTypeface(AndroidUtilities.regular())",
            composer,
        )
        self.assertIn(
            "nameTextView.setTypeface(AndroidUtilities.regular())",
            mentions,
        )
        self.assertIn(
            "usernameTextView.setTypeface(AndroidUtilities.regular())",
            mentions,
        )


if __name__ == "__main__":
    unittest.main()

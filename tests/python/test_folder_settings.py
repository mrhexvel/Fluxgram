from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"


class FolderSettingsTest(unittest.TestCase):
    def test_policy_is_owner_scoped_and_defaults_to_name_and_icon(self):
        source = (JAVA / "messenger/fluxgram/FolderSettings.java").read_text()

        self.assertIn("public static final class Policy", source)
        self.assertIn("private final int presentationMode;", source)
        self.assertIn("private final int counterMode;", source)
        self.assertIn('"fluxgram_folders_" + account', source)
        self.assertIn("preferences.getLong(OWNER, 0) != owner", source)
        self.assertIn("? MODE_TITLE_AND_ICON", source)
        self.assertIn("? COUNTER_ALL", source)
        self.assertIn("validatePresentationMode(mode)", source)
        self.assertIn("validateCounterMode(counterMode)", source)

    def test_semantic_icons_use_existing_telegram_assets(self):
        source = (JAVA / "ui/fluxgram/FolderPresentation.java").read_text()

        for icon in (
            "msg_folders",
            "msg_folders_private",
            "msg_folders_groups",
            "msg_folders_channels",
            "msg_folders_bots",
            "msg_folders_muted",
            "msg_folders_read",
            "msg_folders_archive",
        ):
            self.assertIn(f"R.drawable.{icon}", source)
        self.assertIn("DIALOG_FILTER_FLAG_ALL_CHATS", source)
        self.assertIn("ColoredImageSpan.ALIGN_CENTER", source)

    def test_root_tabs_use_neutral_default_title_hook(self):
        tabs = (JAVA / "ui/Components/FilterTabsView.java").read_text()
        dialogs = (JAVA / "ui/DialogsActivity.java").read_text()

        self.assertNotIn("FolderSettings", tabs)
        self.assertNotIn("FolderPresentation", tabs)
        self.assertEqual(5, tabs.count("getDefaultTabTitle("))
        self.assertIn("protected CharSequence getDefaultTabTitle", tabs)
        self.assertIn("if (!usesFluxGramRootDialogsAppearance())", dialogs)
        self.assertIn("FolderPresentation.createTitle", dialogs)
        self.assertIn(
            "filterTabsView.addTab(a, filter.localId, filter.name, filter.entities",
            dialogs,
        )

    def test_main_tabs_selector_uses_same_policy_and_resolver(self):
        source = (JAVA / "ui/MainTabsActivity.java").read_text()

        self.assertIn("FolderSettings.getPolicy(currentAccount)", source)
        self.assertIn("FolderPresentation.resolveIcon(folder)", source)
        self.assertIn("folderItem.setContentDescription", source)
        self.assertIn("folderPolicy.getPresentationMode() == FolderSettings.MODE_ICON", source)
        self.assertIn("folderPolicy.getPresentationMode() == FolderSettings.MODE_TITLE", source)
        self.assertIn("FolderUnreadCounter.resolve(currentAccount, folder, folderPolicy)", source)

    def test_counter_modes_use_storage_backed_aggregates(self):
        settings = (JAVA / "messenger/fluxgram/FolderSettings.java").read_text()
        storage = (JAVA / "messenger/MessagesStorage.java").read_text()
        resolver = (JAVA / "ui/fluxgram/FolderUnreadCounter.java").read_text()
        dialogs = (JAVA / "ui/DialogsActivity.java").read_text()

        for mode in ("COUNTER_ALL", "COUNTER_UNMUTED", "COUNTER_HIDDEN"):
            self.assertIn(mode, settings)
        self.assertIn("counterStride * 3", storage)
        self.assertIn("pendingMainAllUnreadCount", storage)
        self.assertIn("pendingMainUnmutedUnreadCount", storage)
        self.assertIn("pendingUnmutedUnreadCount", storage)
        self.assertIn("getMainAllUnreadCount()", resolver)
        self.assertIn("getMainUnmutedUnreadCount()", resolver)
        self.assertIn("filter.unmutedUnreadCount", resolver)
        self.assertIn("FolderUnreadCounter.resolve", dialogs)

    def test_counter_setting_rebuilds_existing_surfaces(self):
        source = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        self.assertIn("FOLDER_COUNTER", source)
        self.assertIn("showFolderCounterDialog()", source)
        self.assertIn("FolderSettings.setCounterMode(currentAccount, index)", source)
        self.assertIn("folderPreview.updateFolders(true);", source)
        self.assertIn("AndroidUtilities.runOnUIThread(rebuildFolderFragments, 250);", source)

    def test_preview_uses_the_real_folder_tabs_component(self):
        preview = (JAVA / "ui/Components/FluxGramFolderPreviewCell.java").read_text()
        settings = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        self.assertIn("new FilterTabsView(context, resourcesProvider)", preview)
        self.assertIn("FolderPresentation.createTitle", preview)
        self.assertIn("FolderUnreadCounter.resolve", preview)
        self.assertIn("MessagesController.getInstance(account).getDialogFilters()", preview)
        self.assertIn("NotificationCenter.dialogFiltersUpdated", preview)
        self.assertIn("BlurredBackgroundProviderImpl.topPanel(resourcesProvider)", preview)
        self.assertIn("background.setRadius(dp(18));", preview)
        self.assertIn("background.setPadding(dp(6.666f));", preview)
        self.assertIn("tabsView.refreshTabs(animated);", preview)
        self.assertIn("items.add(UItem.asCustomShadow(folderPreview, 58));", settings)


if __name__ == "__main__":
    unittest.main()

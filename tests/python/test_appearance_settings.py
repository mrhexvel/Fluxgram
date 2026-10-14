from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"


class AppearanceSettingsTest(unittest.TestCase):
    def test_preferences_are_account_and_owner_scoped(self):
        source = (JAVA / "messenger/fluxgram/AppearanceSettings.java").read_text()

        self.assertIn('"fluxgram_appearance_" + account', source)
        self.assertIn("getClientUserId()", source)
        self.assertIn("preferences.getLong(OWNER, 0) != owner", source)
        self.assertIn("preferences.edit().clear().putLong(OWNER, owner).apply()", source)
        self.assertIn('"title_mode"', source)
        self.assertIn('"hide_action_bar_status"', source)
        self.assertIn('"center_action_bar_title"', source)

    def test_title_modes_have_safe_fallbacks(self):
        source = (JAVA / "messenger/fluxgram/AppearanceSettings.java").read_text()

        self.assertIn("mode < TITLE_APP_NAME || mode > TITLE_CHATS", source)
        self.assertIn("UserObject.getPublicUsername(user)", source)
        self.assertIn("if (!TextUtils.isEmpty(username))", source)
        self.assertIn("return UserObject.getFirstName(user);", source)
        self.assertIn("R.string.FluxGramAppName", source)
        self.assertIn("R.string.FilterChats", source)

    def test_screen_uses_telegram_preview_and_controls(self):
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()
        preview = (JAVA / "ui/Components/FluxGramChatListPreviewCell.java").read_text()

        self.assertIn("new FluxGramChatListPreviewCell(", activity)
        self.assertLess(
            activity.index("appearancePreview = new FluxGramChatListPreviewCell("),
            activity.index("listView = new UniversalRecyclerView("),
        )
        self.assertIn("UItem.asButton(\n                    ACTION_BAR_TITLE", activity)
        self.assertIn("UItem.asCheck(\n                        HIDE_ACTION_BAR_STATUS", activity)
        self.assertIn("UItem.asCheck(\n                    CENTER_ACTION_BAR_TITLE", activity)
        self.assertIn("addSectionLabel(items, R.string.FluxGramChatList);", activity)
        self.assertIn("UItem.asCustomShadow(label, LayoutHelper.WRAP_CONTENT)", activity)
        self.assertNotIn("UItem.asHeader", activity)
        self.assertIn("new ActionBar(context, resourcesProvider)", preview)
        self.assertIn("new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable", preview)
        self.assertIn("statusDrawable.attach();", preview)
        self.assertIn("statusDrawable.detach();", preview)

    def test_main_dialogs_title_hook_is_fail_closed(self):
        source = (JAVA / "ui/DialogsActivity.java").read_text()
        eligibility = source.split("private boolean usesFluxGramDialogsTitle()", 1)[1]

        self.assertIn("initialDialogsType == DIALOGS_TYPE_DEFAULT", eligibility)
        self.assertIn("!onlySelect", eligibility)
        self.assertIn("folderId == 0", eligibility)
        self.assertIn("communityId == 0", eligibility)
        self.assertIn("TextUtils.isEmpty(searchString)", eligibility)
        self.assertIn("AppearanceSettings.getActionBarTitle(currentAccount)", source)
        self.assertIn("AppearanceSettings.hidesActionBarStatus(currentAccount)", source)
        self.assertIn("AppearanceSettings.centersActionBarTitle(currentAccount)", source)

    def test_action_bar_centering_uses_symmetric_bounds(self):
        source = (JAVA / "ui/ActionBar/ActionBar.java").read_text()

        self.assertIn("public void setTitleCentered(boolean centered)", source)
        self.assertIn("width - Math.max(leftInset, rightInset) * 2", source)
        self.assertIn("(getMeasuredWidth() - titleTextView[i].getMeasuredWidth()) / 2", source)


if __name__ == "__main__":
    unittest.main()

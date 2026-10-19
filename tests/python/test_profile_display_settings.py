from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"


class ProfileDisplaySettingsTest(unittest.TestCase):
    def test_settings_are_global_and_disabled_by_default(self):
        settings = (JAVA / "messenger/fluxgram/ProfileDisplaySettings.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        self.assertIn('HIDE_OWN_PHONE = "fluxgram_hide_own_phone"', settings)
        self.assertIn('SHOW_ID_AND_DC = "fluxgram_show_profile_id_dc"', settings)
        self.assertIn("getBoolean(HIDE_OWN_PHONE, false)", settings)
        self.assertIn("getBoolean(SHOW_ID_AND_DC, false)", settings)
        self.assertIn("addSectionLabel(items, R.string.FluxGramProfile);", activity)
        self.assertIn(".setChecked(ProfileDisplaySettings.hidesOwnPhone())", activity)
        self.assertIn(".setChecked(ProfileDisplaySettings.showsIdAndDc())", activity)

    def test_own_phone_is_hidden_only_on_self_facing_surfaces(self):
        settings_activity = (JAVA / "ui/SettingsActivity.java").read_text()
        profile = (JAVA / "ui/ProfileActivity.java").read_text()
        user_info = (JAVA / "ui/UserInfoActivity.java").read_text()
        main_tabs = (JAVA / "ui/MainTabsActivity.java").read_text()

        self.assertIn("if (!hidePhone && user != null && !TextUtils.isEmpty(user.phone))", settings_activity)
        self.assertIn("ProfileDisplaySettings.getNameWithoutPhone(user)", settings_activity)
        self.assertIn("ProfileDisplaySettings.getNameWithoutPhone(user)", main_tabs)
        self.assertIn("user.id == getUserConfig().getClientUserId()", profile)
        self.assertIn("&& ProfileDisplaySettings.hidesOwnPhone();", profile)
        self.assertIn("if (!isBot && !hideOwnPhone && (hasPhone || !hasInfo))", profile)
        self.assertIn("R.string.FluxGramPhoneHiddenValue", user_info)
        self.assertIn("new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANGE_PHONE_NUMBER)", user_info)

    def test_profile_identity_uses_photo_dc_without_account_fallback(self):
        settings = (JAVA / "messenger/fluxgram/ProfileDisplaySettings.java").read_text()
        profile = (JAVA / "ui/ProfileActivity.java").read_text()

        self.assertIn("user.photo.dc_id", settings)
        self.assertIn("chat.photo.dc_id", settings)
        self.assertIn("big.dc_id > 0", settings)
        self.assertNotIn("getCurrentDatacenterId", settings)
        self.assertNotIn("getCurrentDatacenterId", profile)
        self.assertIn("profileIdRow = rowCount++;", profile)
        self.assertIn("profileDcRow = rowCount++;", profile)
        self.assertIn("R.string.FluxGramUserId", profile)
        self.assertIn("R.string.FluxGramChatId", profile)
        self.assertIn("R.string.FluxGramProfilePhotoDc", profile)
        self.assertIn("R.string.Unavailable", profile)
        self.assertIn("if (position == profileIdRow)", profile)
        self.assertIn("AndroidUtilities.addToClipboard(String.valueOf(id));", profile)
        self.assertIn("R.string.FluxGramProfileIdCopied", profile)
        self.assertIn("if (position == profileDcRow)", profile)


if __name__ == "__main__":
    unittest.main()

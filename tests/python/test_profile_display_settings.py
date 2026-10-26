from pathlib import Path
import json
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

    def test_user_id_row_prefers_official_registration_month_with_local_fallback(self):
        profile = (JAVA / "ui/ProfileActivity.java").read_text()
        controller = (JAVA / "messenger/MessagesController.java").read_text()
        lookup = (JAVA / "messenger/fluxgram/RegistrationDateLookup.java").read_text()
        asset = ROOT / "TMessagesProj/src/main/assets/fluxgram/registration/id_date.json"

        self.assertIn("position == profileIdRow && userId != 0", profile)
        self.assertIn("R.drawable.msg_calendar2", profile)
        self.assertIn("getMessagesController().getPeerRegistrationMonth(userId)", profile)
        self.assertIn("UserInfoCell.displayDate(registrationMonth)", profile)
        self.assertIn("RegistrationDateLookup.estimate(userId)", profile)
        self.assertIn("RegistrationDateLookup.Kind.APPROXIMATE", profile)
        self.assertIn("RegistrationDateLookup.Kind.AFTER", profile)
        self.assertIn("getMessagesController().loadPeerSettings(user, null, true)", profile)
        self.assertIn("NotificationCenter.peerSettingsDidLoad", profile)
        self.assertIn("registrationDateRequestAnchor != null", profile)
        self.assertIn("registrationDateLoaded = true", profile)
        self.assertIn("R.string.FluxGramRegistrationDateUnavailable", profile)
        self.assertIn("hideRegistrationDateHint(true);", profile)
        self.assertIn("hideRegistrationDateHint(false);", profile)
        self.assertNotIn("FluxGram registration", controller)
        self.assertIn("public String getPeerRegistrationMonth(long dialogId)", controller)
        self.assertIn('notificationsPreferences.getString("fluxgram_registration_month_" + dialogId, null)', controller)
        self.assertIn('editor.putString("fluxgram_registration_month_" + dialogId, settings.registration_month)', controller)
        self.assertIn("settings.registration_month = cachedSettings.registration_month", controller)
        self.assertIn("userFull.settings.registration_month = settings.registration_month", controller)
        self.assertIn("userPeerSettings.clear();", controller)
        self.assertNotIn("exteraAuthBot", profile)
        self.assertNotIn("id_date.json", profile)
        self.assertIn('ASSET_PATH = "fluxgram/registration/id_date.json"', lookup)
        self.assertIn("userId <= values[0][0]", lookup)
        self.assertIn("userId <= next[0]", lookup)
        self.assertIn("Kind.APPROXIMATE", lookup)
        self.assertIn("Kind.AFTER", lookup)
        self.assertTrue(asset.is_file())

        anchors = json.loads(asset.read_text())["data"]
        self.assertEqual(155, len(anchors))
        self.assertEqual({"id": 1000000, "date": 1380326400}, anchors[0])
        self.assertEqual({"id": 8461412540, "date": 1755628800}, anchors[-1])
        self.assertEqual(sorted(anchor["id"] for anchor in anchors), [anchor["id"] for anchor in anchors])


if __name__ == "__main__":
    unittest.main()

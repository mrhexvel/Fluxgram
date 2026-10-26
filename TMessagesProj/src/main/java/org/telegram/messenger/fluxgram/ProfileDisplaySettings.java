package org.telegram.messenger.fluxgram;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

public final class ProfileDisplaySettings {
    private static final String HIDE_OWN_PHONE = "fluxgram_hide_own_phone";
    private static final String HIDE_PROFILE_PHONE_NUMBERS = "fluxgram_hide_profile_phone_numbers";
    private static final String SHOW_ID_AND_DC = "fluxgram_show_profile_id_dc";

    private static volatile boolean loaded;
    private static volatile boolean hideOwnPhone;
    private static volatile boolean hideProfilePhoneNumbers;
    private static volatile boolean showIdAndDc;

    public static boolean hidesOwnPhone() {
        ensureLoaded();

        return hideOwnPhone;
    }

    public static synchronized void setHidesOwnPhone(boolean hidden) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(HIDE_OWN_PHONE, hidden)
                .apply();

        hideOwnPhone = hidden;
    }

    public static boolean hidesProfilePhoneNumbers() {
        ensureLoaded();

        return hideProfilePhoneNumbers;
    }

    public static synchronized void setHidesProfilePhoneNumbers(boolean hidden) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(HIDE_PROFILE_PHONE_NUMBERS, hidden)
                .apply();

        hideProfilePhoneNumbers = hidden;
    }

    public static boolean showsIdAndDc() {
        ensureLoaded();

        return showIdAndDc;
    }

    public static synchronized void setShowsIdAndDc(boolean shown) {
        ensureLoaded();

        MessagesController.getGlobalMainSettings()
                .edit()
                .putBoolean(SHOW_ID_AND_DC, shown)
                .apply();

        showIdAndDc = shown;
    }

    public static String getNameWithoutPhone(TLRPC.User user) {
        if (user == null || UserObject.isDeleted(user)) {
            return LocaleController.getString(R.string.HiddenName);
        }

        String name = ContactsController.formatName(user.first_name, user.last_name);
        name = AndroidUtilities.removeRTL(AndroidUtilities.removeDiacritics(name));

        return !TextUtils.isEmpty(name) ? name : LocaleController.getString(R.string.HiddenName);
    }

    public static int getProfilePhotoDc(TLRPC.User user) {
        if (user == null || user.photo == null || user.photo instanceof TLRPC.TL_userProfilePhotoEmpty) {
            return 0;
        }

        return getProfilePhotoDc(user.photo.dc_id, user.photo.photo_big, user.photo.photo_small);
    }

    public static int getProfilePhotoDc(TLRPC.Chat chat) {
        if (chat == null || chat.photo == null || chat.photo instanceof TLRPC.TL_chatPhotoEmpty) {
            return 0;
        }

        return getProfilePhotoDc(chat.photo.dc_id, chat.photo.photo_big, chat.photo.photo_small);
    }

    public static String getProfilePhotoDcDescription(int dc) {
        switch (dc) {
            case 1:
            case 3:
                return "DC" + dc + ", Miami, US";
            case 2:
            case 4:
                return "DC" + dc + ", Amsterdam, NL";
            case 5:
                return "DC5, Singapore, SG";
            default:
                return dc > 0 ? "DC" + dc : LocaleController.getString(R.string.Unavailable);
        }
    }

    public static String maskProfilePhone(String phone) {
        if (phone == null) {
            return null;
        }

        char[] pattern = {'\u280c', '\u2862', '\u2891', '\u2828', '\u2825', '\u282e', '\u2851'};
        StringBuilder masked = new StringBuilder(phone.length());
        for (int index = 0; index < phone.length(); index++) {
            masked.append(pattern[index % pattern.length]);
        }
        return masked.toString();
    }

    private static int getProfilePhotoDc(int dc, TLRPC.FileLocation big, TLRPC.FileLocation small) {
        if (dc > 0) {
            return dc;
        }
        if (big != null && big.dc_id > 0) {
            return big.dc_id;
        }
        if (small != null && small.dc_id > 0) {
            return small.dc_id;
        }

        return 0;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }

        synchronized (ProfileDisplaySettings.class) {
            if (loaded) {
                return;
            }

            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            hideOwnPhone = preferences.getBoolean(HIDE_OWN_PHONE, false);
            hideProfilePhoneNumbers = preferences.getBoolean(HIDE_PROFILE_PHONE_NUMBERS, true);
            showIdAndDc = preferences.getBoolean(SHOW_ID_AND_DC, false);
            loaded = true;
        }
    }

    private ProfileDisplaySettings() {
    }
}

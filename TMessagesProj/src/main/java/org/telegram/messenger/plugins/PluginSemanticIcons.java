package org.telegram.messenger.plugins;

import org.telegram.messenger.R;

public final class PluginSemanticIcons {
    private PluginSemanticIcons() {
    }

    public static int resolve(String icon) {
        switch (icon) {
            case "":
                return 0;
            case "adjustments":
                return R.drawable.msg_photo_settings;
            case "auto_delete":
                return R.drawable.msg_autodelete;
            case "blur":
                return R.drawable.msg_photo_blur;
            case "clear":
                return R.drawable.msg_clear;
            case "colors":
                return R.drawable.msg_colors;
            case "copy":
                return R.drawable.msg_copy;
            case "curve":
                return R.drawable.msg_photo_curve;
            case "customize":
            case "particles":
                return R.drawable.msg_customize;
            case "delete":
                return R.drawable.msg_delete;
            case "edit":
                return R.drawable.msg_edit;
            case "emoji":
                return R.drawable.msg_emoji_smiles;
            case "favorite":
                return R.drawable.msg_fave;
            case "file":
                return R.drawable.msg_view_file;
            case "flip":
                return R.drawable.msg_photo_flip;
            case "link":
                return R.drawable.msg_instant_link;
            case "list":
                return R.drawable.msg_list;
            case "message":
                return R.drawable.msg_message;
            case "notifications":
                return R.drawable.msg_notifications;
            case "open":
                return R.drawable.msg_openin;
            case "reset":
                return R.drawable.msg_reset;
            case "retry":
                return R.drawable.msg_retry;
            case "select":
                return R.drawable.msg_select;
            case "send":
                return R.drawable.msg_send;
            case "settings":
                return R.drawable.msg_settings;
            case "speed":
                return R.drawable.msg_speed;
            case "translate":
                return R.drawable.msg_translate;
            case "zoom_in":
                return R.drawable.msg_zoomin;
            default:
                throw new IllegalArgumentException("Invalid semantic icon");
        }
    }
}

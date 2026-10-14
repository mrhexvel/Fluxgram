package org.telegram.messenger.plugins;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public final class PluginSettings {
    public static final class Setting {
        public final String type;
        public final String key;
        public final String title;
        public final String description;
        public final String label;
        public final String icon;
        public final int iconResId;
        public final Object defaultValue;
        public final List<String> optionValues = new ArrayList<>();
        public final List<String> optionTitles = new ArrayList<>();
        public final int minimum;
        public final int maximum;
        public final int step;
        public final String unit;
        public final int maxLength;
        public final String placeholder;

        private Setting(JSONObject object) throws Exception {
            type = object.has("type") ? text(object, "type", 10) : "switch";
            key = text(object, "key", 64);
            title = text(object, "title", 120);
            description = text(object, "description", 1000);
            label = object.has("label") ? text(object, "label", 120).trim() : "";
            icon = object.has("icon") ? text(object, "icon", 32) : "";
            iconResId = iconResource(icon);
            defaultValue = object.get("default");

            if (!key.matches("[a-z][a-z0-9_]{0,63}") || title.trim().isEmpty()) {
                throw new IllegalArgumentException("Invalid plugin setting");
            }

            if (type.equals("select")) {
                JSONArray options = object.getJSONArray("options");

                if (options.length() < 1 || options.length() > 32) {
                    throw new IllegalArgumentException("Invalid option count");
                }

                for (int i = 0; i < options.length(); i++) {
                    JSONArray option = options.getJSONArray(i);

                    if (option.length() != 2 || !(option.get(0) instanceof String)
                            || !(option.get(1) instanceof String)) {
                        throw new IllegalArgumentException("Invalid select option");
                    }

                    String value = option.getString(0);
                    String name = option.getString(1);

                    if (value.trim().isEmpty() || name.trim().isEmpty() || value.length() > 64
                            || name.length() > 120 || optionValues.contains(value)) {
                        throw new IllegalArgumentException("Invalid select option");
                    }

                    optionValues.add(value);
                    optionTitles.add(name);
                }
            }

            if (type.equals("slider")) {
                minimum = integer(object, "minimum");
                maximum = integer(object, "maximum");
                step = integer(object, "step");
                unit = text(object, "unit", 20);

                if (minimum < -1000000 || maximum > 1000000 || minimum >= maximum || step <= 0
                        || (maximum - minimum) % step != 0 || (maximum - minimum) / step > 1000) {
                    throw new IllegalArgumentException("Invalid slider range");
                }
            } else {
                minimum = maximum = 0;
                step = 1;
                unit = "";
            }

            if (type.equals("text")) {
                maxLength = integer(object, "max_length");
                placeholder = text(object, "placeholder", 120);

                if (maxLength < 1 || maxLength > 4096) {
                    throw new IllegalArgumentException("Invalid text length limit");
                }
            } else {
                maxLength = 0;
                placeholder = "";
            }

            if (!accepts(defaultValue)) {
                throw new IllegalArgumentException("Invalid setting type or default");
            }
        }

        private static int iconResource(String icon) {
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
                    throw new IllegalArgumentException("Invalid setting icon");
            }
        }

        public boolean accepts(Object value) {
            if (type.equals("switch")) {
                return value instanceof Boolean;
            }

            if (type.equals("select")) {
                return value instanceof String && optionValues.contains(value);
            }

            if (type.equals("slider") && value instanceof Integer) {
                int number = (Integer) value;
                return number >= minimum && number <= maximum && (number - minimum) % step == 0;
            }

            if (type.equals("text") && value instanceof String) {
                String text = (String) value;
                return text.codePointCount(0, text.length()) <= maxLength
                        && !text.contains("\n") && !text.contains("\r");
            }

            return false;
        }

        public String displayValue(Object value) {
            if (type.equals("select")) {
                return optionTitles.get(optionValues.indexOf(value));
            }

            if (type.equals("text") && ((String) value).isEmpty()) {
                return placeholder;
            }

            return value + (unit.isEmpty() ? "" : " " + unit);
        }
    }

    private static String text(JSONObject object, String key, int limit) throws Exception {
        Object value = object.get(key);

        if (!(value instanceof String) || ((String) value).length() > limit) {
            throw new IllegalArgumentException("Invalid setting " + key);
        }

        return (String) value;
    }

    private static int integer(JSONObject object, String key) throws Exception {
        Object value = object.get(key);

        if (!(value instanceof Integer)) {
            throw new IllegalArgumentException("Invalid integer setting " + key);
        }

        return (Integer) value;
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                "fluxgram_plugin_settings", Context.MODE_PRIVATE);
    }

    public static boolean getBoolean(String id, String key, boolean fallback) {
        try {
            return preferences().getBoolean("value:" + id + ":" + key, fallback);
        } catch (ClassCastException error) {
            return fallback;
        }
    }

    public static String getString(String id, String key, String fallback) {
        try {
            return preferences().getString("value:" + id + ":" + key, fallback);
        } catch (ClassCastException error) {
            return fallback;
        }
    }

    public static int getInt(String id, String key, int fallback) {
        try {
            return preferences().getInt("value:" + id + ":" + key, fallback);
        } catch (ClassCastException error) {
            return fallback;
        }
    }

    public static Object getValue(String id, Setting setting) {
        Object value;

        if (setting.type.equals("switch")) {
            value = getBoolean(id, setting.key, (Boolean) setting.defaultValue);
        } else if (setting.type.equals("select") || setting.type.equals("text")) {
            value = getString(id, setting.key, (String) setting.defaultValue);
        } else {
            value = getInt(id, setting.key, (Integer) setting.defaultValue);
        }

        return setting.accepts(value) ? value : setting.defaultValue;
    }

    public static void setValue(String id, Setting setting, Object value) {
        if (!setting.accepts(value)) {
            throw new IllegalArgumentException("Invalid setting value");
        }

        String key = "value:" + id + ":" + setting.key;
        SharedPreferences.Editor edit = preferences().edit();

        if (value instanceof Boolean) {
            edit.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            edit.putInt(key, (Integer) value);
        } else {
            edit.putString(key, (String) value);
        }

        edit.apply();
    }

    public static List<Setting> parse(String json) throws Exception {
        JSONArray array = new JSONArray(json);

        if (array.length() > 64) {
            throw new IllegalArgumentException("Too many settings");
        }

        ArrayList<Setting> result = new ArrayList<>();
        HashSet<String> keys = new HashSet<>();

        for (int i = 0; i < array.length(); i++) {
            Setting setting = new Setting(array.getJSONObject(i));

            if (!keys.add(setting.key)) {
                throw new IllegalArgumentException("Duplicate setting key");
            }

            result.add(setting);
        }

        return Collections.unmodifiableList(result);
    }

    public static void saveSchema(String id, String version, String json) {
        preferences().edit().putString("schema:" + id + ":" + version, json).apply();
    }

    public static List<Setting> readSchema(String id, String version) {
        try {
            return parse(preferences().getString("schema:" + id + ":" + version, "[]"));
        } catch (Exception error) {
            return Collections.emptyList();
        }
    }

    public static void clearForPlugin(String id) {
        clearForPlugin(id, false);
    }

    public static boolean clearForPluginNow(String id) {
        return clearForPlugin(id, true);
    }

    private static boolean clearForPlugin(String id, boolean synchronous) {
        SharedPreferences stored = preferences();
        SharedPreferences.Editor edit = stored.edit();
        String valuePrefix = "value:" + id + ":";
        String schemaPrefix = "schema:" + id + ":";

        for (String key : stored.getAll().keySet()) {
            if (key.startsWith(valuePrefix) || key.startsWith(schemaPrefix)) {
                edit.remove(key);
            }
        }

        if (synchronous) {
            return edit.commit();
        }

        edit.apply();
        return true;
    }
}

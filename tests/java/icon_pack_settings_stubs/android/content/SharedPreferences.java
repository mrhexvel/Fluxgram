package android.content;

public interface SharedPreferences {
    boolean contains(String key);

    String getString(String key, String defaultValue);

    Editor edit();

    interface Editor {
        Editor putString(String key, String value);

        Editor remove(String key);

        void apply();

        boolean commit();
    }
}

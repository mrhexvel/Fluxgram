package android.content;

import java.util.HashMap;
import java.util.Map;

public final class MemorySharedPreferences implements SharedPreferences {
    private final HashMap<String, Object> values = new HashMap<>();
    private boolean failNextCommit;

    @Override
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public String getString(String key, String defaultValue) {
        Object value = values.get(key);

        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof String)) {
            throw new ClassCastException();
        }

        return (String) value;
    }

    @Override
    public Editor edit() {
        return new MemoryEditor();
    }

    public void putWrongType(String key) {
        values.put(key, true);
    }

    public void failNextCommit() {
        failNextCommit = true;
    }

    private final class MemoryEditor implements Editor {
        private final HashMap<String, String> updates = new HashMap<>();
        private final HashMap<String, Boolean> removals = new HashMap<>();

        @Override
        public Editor putString(String key, String value) {
            updates.put(key, value);
            removals.remove(key);

            return this;
        }

        @Override
        public Editor remove(String key) {
            updates.remove(key);
            removals.put(key, true);

            return this;
        }

        @Override
        public void apply() {
            commit();
        }

        @Override
        public boolean commit() {
            if (failNextCommit) {
                failNextCommit = false;

                return false;
            }

            for (String key : removals.keySet()) {
                values.remove(key);
            }
            for (Map.Entry<String, String> update : updates.entrySet()) {
                values.put(update.getKey(), update.getValue());
            }

            return true;
        }
    }
}

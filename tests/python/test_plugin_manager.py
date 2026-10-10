from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class PluginManagerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not shutil.which("javac") or not shutil.which("java"):
            raise unittest.SkipTest("Requires JDK")
        cls.temp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.temp.cleanup)
        root = Path(cls.temp.name)
        sources = {
            "android/content/Context.java": """package android.content;

public abstract class Context {

    public static final int MODE_PRIVATE = 0;

    public abstract SharedPreferences getSharedPreferences(String name, int mode);
}
""",
            "android/content/SharedPreferences.java": """package android.content;

public interface SharedPreferences {

    boolean getBoolean(String key, boolean fallback);

    Editor edit();

    interface Editor {

        Editor putBoolean(String key, boolean value);

        Editor remove(String key);

        boolean commit();

        void apply();
    }
}
""",
            "org/telegram/messenger/FileLog.java": """package org.telegram.messenger;

public class FileLog {

    public static void e(String message, Exception error) {
        throw new AssertionError(error);
    }
}
""",
            "RegistryCheck.java": """import android.content.*;

import org.telegram.messenger.plugins.*;

import java.util.*;

public class RegistryCheck {

    static class Prefs implements SharedPreferences, SharedPreferences.Editor {

        Map<String, Boolean> values = new HashMap<>();

        public boolean getBoolean(String key, boolean fallback) {
            return values.getOrDefault(key, fallback);
        }

        public Editor edit() {
            return this;
        }

        public Editor putBoolean(String key, boolean value) {
            values.put(key, value);

            return this;
        }

        public Editor remove(String key) {
            values.remove(key);

            return this;
        }

        public void apply() {}

        public boolean commit() {
            return true;
        }
    }

    static class P implements Plugin {

        String id;

        boolean active;

        int calls;

        P(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return id;
        }

        public String getDescription() {
            return id;
        }

        public String getVersion() {
            return "1.0.0";
        }

        public void onEnabledChanged(boolean value) {
            active = value;
        }

        public void beforeSend(BeforeSendEvent event) {
            calls++;
        }
    }

    static void check(boolean value) {
        if (!value) {
            throw new AssertionError();
        }
    }

    public static void main(String[] args) {
        Prefs prefs = new Prefs();
        PluginManager manager = PluginManager.getInstance();
        manager.initialize(
                new Context() {

                    public SharedPreferences getSharedPreferences(String name, int mode) {
                        return prefs;
                    }
                });

        P external = new P("example.test");
        manager.register(external, false);

        if (args[0].equals("defaults")) {
            check(!external.active && !manager.isEnabled(external.id));

            P builtin = new P("fluxgram.test");
            manager.register(builtin);
            check(!builtin.active && !manager.isEnabled(builtin.id));
            check(!manager.isSystemEnabled());
        } else if (args[0].equals("blocked")) {
            manager.setSystemEnabled(true);
            check(manager.setEnabled(external.id, true));
            check(external.active);
            manager.blockUntilRestart(external.id);
            check(!external.active && !manager.isEnabled(external.id));
            check(!prefs.getBoolean("enabled." + external.id, true));
            check(!manager.setEnabled(external.id, true));
            manager.beforeSend(null);
            check(external.calls == 0);
            check(manager.getPlugin(external.id) == external);
        } else if (args[0].equals("global_switch")) {
            manager.setEnabled(external.id, true);
            check(!external.active);
            manager.beforeSend(null);
            check(external.calls == 0);

            manager.setSystemEnabled(true);
            check(external.active);
            check(prefs.getBoolean("system.enabled", false));
            manager.beforeSend(null);
            check(external.calls == 1);

            manager.setSystemEnabled(false);
            check(!external.active);
            check(manager.isEnabled(external.id));
            check(!prefs.getBoolean("system.enabled", true));
            manager.beforeSend(null);
            check(external.calls == 1);

            P later = new P("example.later");
            manager.register(later, true);
            check(!later.active);
            manager.setSystemEnabled(true);
            check(external.active && later.active);
        } else if (args[0].equals("already_disabled")) {
            manager.blockUntilRestart(external.id);
            check(!manager.setEnabled(external.id, true));
            check(!external.active);
        } else if (args[0].equals("clear_state")) {
            P other = new P("example.other");
            manager.register(other, false);
            manager.setSystemEnabled(true);
            manager.setEnabled(external.id, true);
            manager.setEnabled(other.id, true);
            manager.clearPersistedEnabledState(external.id);
            check(!prefs.values.containsKey("enabled." + external.id));
            check(prefs.getBoolean("enabled." + other.id, false));
            check(prefs.getBoolean("system.enabled", false));
        }
    }
}
""",
        }
        files = []
        for name, content in sources.items():
            file = root / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(content)
            files.append(str(file))
        folder = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/plugins"
        files += [
            str(folder / name)
            for name in ("Plugin.java", "PluginManager.java", "BeforeSendEvent.java")
        ]
        subprocess.run(
            ["javac", "-d", str(root)] + files, check=True, capture_output=True
        )
        cls.classpath = str(root)

    def run_case(self, name):
        result = subprocess.run(
            ["java", "-cp", self.classpath, "RegistryCheck", name],
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_external_plugins_default_disabled(self):
        self.run_case("defaults")

    def test_pending_operation_disables_callbacks_and_blocks_reenable(self):
        self.run_case("blocked")

    def test_pending_operation_blocks_an_already_disabled_plugin(self):
        self.run_case("already_disabled")

    def test_global_switch_stops_callbacks_and_preserves_selection(self):
        self.run_case("global_switch")

    def test_clear_persisted_state_preserves_other_plugins_and_global_switch(self):
        self.run_case("clear_state")

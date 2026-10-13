from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class PluginCrashRecoveryTest(unittest.TestCase):
    def test_recovery_ui_uses_confirmed_plugin_id_and_one_shot_notice(self):
        crash = (
            ROOT
            / "TMessagesProj/src/main/java/org/telegram/messenger/plugins/PluginCrashRecovery.java"
        ).read_text()
        adapter = (
            ROOT
            / "TMessagesProj/src/main/java/org/telegram/messenger/plugins/PythonPluginAdapter.java"
        ).read_text()
        sheet = (
            ROOT
            / "TMessagesProj/src/main/java/org/telegram/ui/FluxGramPluginRecoverySheet.java"
        ).read_text()
        settings = (
            ROOT
            / "TMessagesProj/src/main/java/org/telegram/ui/FluxGramSettingsActivity.java"
        ).read_text()

        self.assertNotIn("getStackTrace", crash)
        self.assertIn("runExternalWorker(id, runnable)", adapter)
        self.assertIn("manager.hasRecoveryNotice()", sheet)
        self.assertIn("manager.claimRecoveryNotice()", sheet)
        self.assertIn("manager.setSystemEnabled(true)", sheet)
        self.assertIn("manager.isRecoveryPlugin(plugin.getId())", settings)

    def test_recovery_startup_returns_before_external_import(self):
        source = (
            ROOT
            / "TMessagesProj/src/main/java/org/telegram/messenger/plugins/ExternalPlugins.java"
        ).read_text()
        recovery = source.index("if (manager.isRecoveryBlockingImports())")
        metadata = source.index("loadMetadataOnly(manager);", recovery)
        recovery_return = source.index("return;", metadata)
        migration = source.index("PythonPluginAdapter.migrateStorage(update)")
        helper = source[source.index("private static void loadMetadataOnly"):]

        self.assertLess(recovery, metadata)
        self.assertLess(metadata, recovery_return)
        self.assertLess(recovery_return, migration)
        self.assertNotIn("migrateStorage", helper.split("public static boolean", 1)[0])

    def test_uncaught_guarded_error_creates_recovery_evidence(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
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
    String getString(String key, String fallback);
    Editor edit();

    interface Editor {
        Editor putBoolean(String key, boolean value);
        Editor putString(String key, String value);
        Editor remove(String key);
        boolean commit();
    }
}
""",
                "org/telegram/messenger/plugins/CrashRecoveryCheck.java": """package org.telegram.messenger.plugins;

import android.content.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class CrashRecoveryCheck {
    private static final class Prefs implements SharedPreferences {
        final Map<String, Object> values = new HashMap<>();

        public boolean getBoolean(String key, boolean fallback) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : fallback;
        }

        public String getString(String key, String fallback) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : fallback;
        }

        public Editor edit() {
            return new Editor() {
                final Map<String, Object> updates = new HashMap<>();
                final java.util.HashSet<String> removals = new java.util.HashSet<>();

                public Editor putBoolean(String key, boolean value) {
                    updates.put(key, value);
                    removals.remove(key);
                    return this;
                }

                public Editor putString(String key, String value) {
                    updates.put(key, value);
                    removals.remove(key);
                    return this;
                }

                public Editor remove(String key) {
                    removals.add(key);
                    updates.remove(key);
                    return this;
                }

                public boolean commit() {
                    for (String key : removals) {
                        values.remove(key);
                    }
                    values.putAll(updates);
                    return true;
                }
            };
        }
    }

    private static void check(boolean value) {
        if (!value) {
            throw new AssertionError();
        }
    }

    public static void main(String[] args) throws Exception {
        Prefs prefs = new Prefs();
        AtomicReference<Throwable> delegated = new AtomicReference<>();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> delegated.set(error));
        PluginCrashRecovery.install(new Context() {
            public SharedPreferences getSharedPreferences(String name, int mode) {
                return prefs;
            }
        });

        RuntimeException guarded = new RuntimeException("guarded");
        Thread guardedThread = new Thread(() -> PluginCrashRecovery.runExternalWorker("qa.crash", () -> {
            throw guarded;
        }));
        guardedThread.start();
        guardedThread.join();

        check(prefs.getBoolean(PluginCrashRecovery.PENDING_KEY, false));
        check("qa.crash".equals(prefs.getString(
                PluginCrashRecovery.PENDING_PLUGIN_KEY, null)));
        check(delegated.get() == guarded);
        check(PluginCrashRecovery.consumePending(prefs));
        check(prefs.getBoolean(PluginCrashRecovery.LATCHED_KEY, false));
        check(prefs.getBoolean(PluginCrashRecovery.NOTICE_KEY, false));
        check("qa.crash".equals(prefs.getString(PluginCrashRecovery.PLUGIN_KEY, null)));

        prefs.values.clear();
        delegated.set(null);

        RuntimeException handled = new RuntimeException("handled");
        try {
            PluginCrashRecovery.runExternal("qa.handled", () -> {
                throw handled;
            });
        } catch (RuntimeException error) {
            PluginCrashRecovery.handled(error);
        }

        check(!prefs.getBoolean(PluginCrashRecovery.PENDING_KEY, false));

        RuntimeException ordinary = new RuntimeException("ordinary");
        Thread ordinaryThread = new Thread(() -> {
            throw ordinary;
        });
        ordinaryThread.start();
        ordinaryThread.join();

        check(!prefs.getBoolean(PluginCrashRecovery.PENDING_KEY, false));
        check(delegated.get() == ordinary);
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

            files.append(str(
                ROOT
                / "TMessagesProj/src/main/java/org/telegram/messenger/plugins/PluginCrashRecovery.java"
            ))
            compile_result = subprocess.run(
                ["javac", "-d", str(root)] + files,
                capture_output=True,
                text=True,
            )
            self.assertEqual(compile_result.returncode, 0, compile_result.stderr)

            result = subprocess.run(
                ["java", "-cp", str(root), "org.telegram.messenger.plugins.CrashRecoveryCheck"],
                capture_output=True,
                text=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()

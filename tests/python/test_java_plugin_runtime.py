from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/plugins"


class JavaPluginRuntimeTest(unittest.TestCase):
    def test_ui_resource_lifecycle_and_nonblocking_stop(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        sources = [
            *sorted((SOURCE / "api").glob("*.java")),
            SOURCE / "JavaPluginUiResources.java",
            SOURCE / "JavaPluginUiStop.java",
            ROOT / "tests/java/JavaPluginUiResourcesCheck.java",
        ]

        with tempfile.TemporaryDirectory() as directory:
            subprocess.run(
                ["javac", "--release", "8", "-d", directory, *map(str, sources)],
                check=True,
                capture_output=True,
            )
            subprocess.run(
                ["java", "-cp", directory, "org.telegram.messenger.plugins.JavaPluginUiResourcesCheck"],
                check=True,
                capture_output=True,
            )

    def test_lifecycle_resources_failures_and_generation(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        sources = [
            *sorted((SOURCE / "api").glob("*.java")),
            SOURCE / "JavaPluginSession.java",
            ROOT / "tests/java/JavaPluginSessionCheck.java",
        ]

        with tempfile.TemporaryDirectory() as directory:
            subprocess.run(
                ["javac", "--release", "8", "-d", directory, *map(str, sources)],
                check=True,
                capture_output=True,
            )
            subprocess.run(
                ["java", "-cp", directory, "org.telegram.messenger.plugins.JavaPluginSessionCheck"],
                check=True,
                capture_output=True,
            )

    def test_loader_does_not_run_during_migration(self):
        source = (SOURCE / "PythonPluginAdapter.java").read_text()
        migration = source.split("private static void migrateExternalStorage", 1)[1].split(
            "private final ThreadPoolExecutor", 1
        )[0]
        self.assertNotIn("JavaPluginLoader", migration)
        load = source.split("private void load(long request)", 1)[1].split(
            "private void handleFailure", 1
        )[0]
        self.assertLess(load.index('"migrate_external_plugin"'), load.index("JavaPluginLoader.prepare"))
        self.assertLess(load.index("JavaPluginLoader.prepare"), load.index('"create_external_plugin"'))
        self.assertLess(load.index("javaLoader.start()"), load.index('instance.callAttr("on_load")'))
        self.assertIn("instance != null || javaLoader != null", source)
        loader = (SOURCE / "JavaPluginLoader.java").read_text()
        self.assertIn("Class.forName(module.entrypoint, false, loader)", loader)
        self.assertIn("PluginDexValidator.read(in, module)", loader)
        self.assertLess(loader.index("dex.setReadOnly()"), loader.index("out.write(data)"))

    def test_release_keeps_java_api(self):
        rules = (ROOT / "TMessagesProj/proguard-rules.pro").read_text()
        self.assertIn("-keep class org.telegram.messenger.plugins.** { *; }", rules)

    def test_adapter_waits_for_ui_acknowledgement(self):
        source = (SOURCE / "PythonPluginAdapter.java").read_text()
        reconcile = source.split("private void reconcile()", 1)[1].split("private void load", 1)[0]
        self.assertLess(reconcile.index("javaUiStop.prepare"), reconcile.index("if (!unload("))
        teardown = source.split("private void unloadStrict()", 1)[1].split("public synchronized void beforeSend", 1)[0]
        self.assertLess(teardown.index("javaUiStop.requireSuccess()"), teardown.index("javaLoader.close()"))
        self.assertIn("javaLoader != null && javaUiStop == null", teardown)
        self.assertIn('bridge.callAttr("unload_plugin", instance)', teardown)
        self.assertIn("scheduleRemoval(removal)", source)
        self.assertIn("javaUiStop.prepare(task, () -> scheduleRemoval(task))", source)
        self.assertNotIn("lifecycle.getState() != PythonPluginLifecycle.State.FAILED", source)


if __name__ == "__main__":
    unittest.main()

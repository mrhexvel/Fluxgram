from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/plugins"
PYTHON = ROOT / "TMessagesProj/src/main/python/fluxgram_runtime"


class PluginStorageTest(unittest.TestCase):
    def test_sqlite_backend_is_private_bounded_and_owner_aware(self):
        source = (JAVA / "PluginStorage.java").read_text()

        self.assertIn("getNoBackupFilesDir()", source)
        self.assertIn("sha256(pluginId) + \".db\"", source)
        self.assertIn("MAX_ENTRIES = 4096", source)
        self.assertIn("MAX_VALUE_BYTES = 256 * 1024", source)
        self.assertIn("MAX_TOTAL_VALUE_BYTES = 4 * 1024 * 1024", source)
        self.assertGreaterEqual(source.count("requireOwners(scopes);"), 2)
        self.assertIn("db.beginTransaction();", source)
        self.assertIn("db.setTransactionSuccessful();", source)

    def test_runtime_uses_generation_checked_bounded_submit(self):
        adapter = (JAVA / "PythonPluginAdapter.java").read_text()
        runtime = (PYTHON / "_storage.py").read_text()
        bindings = (PYTHON / "_bindings.py").read_text()

        self.assertIn("new Semaphore(2)", adapter)
        self.assertIn("int submitStorage(long generation, PyObject callback)", adapter)
        self.assertIn("lifecycle.isCurrent(generation)", adapter)
        self.assertIn("PluginStorage.open(plugin_id)", runtime)
        self.assertIn("Plugin storage cannot perform direct I/O on the UI thread", runtime)
        self.assertIn("self._host.submitStorage(self._generation, callback)", runtime)
        self.assertIn("storage=AndroidStoragePort(plugin_id)", bindings)


if __name__ == "__main__":
    unittest.main()

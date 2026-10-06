from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
PLUGINS = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/plugins"


class PluginRemovalCleanupTest(unittest.TestCase):
    def test_settings_cleanup_uses_exact_plugin_prefixes(self):
        source = (PLUGINS / "PluginSettings.java").read_text()
        start = source.index("    public static void clearForPlugin(")
        method = source[start:source.index("\n    }", start) + 6]

        self.assertIn('"value:" + id + ":"', method)
        self.assertIn('"schema:" + id + ":"', method)
        self.assertIn("stored.getAll().keySet()", method)
        self.assertIn("edit.remove(key)", method)
        self.assertIn("edit.apply()", method)
        self.assertNotIn("clear()", method)

    def test_successful_package_removal_cleans_before_unregister(self):
        source = (PLUGINS / "ExternalPlugins.java").read_text()
        start = source.index("    public static void delete(")
        method = source[start:]

        removed = method.index("store.remove(id);")
        settings = method.index("PluginSettings.clearForPlugin(id);")
        enabled = method.index("manager.clearPersistedEnabledState(id);")
        unregister = method.index("manager.unregister(id);")
        pending = method.index("store.pending()")

        self.assertLess(removed, settings)
        self.assertLess(settings, enabled)
        self.assertLess(enabled, unregister)
        self.assertLess(unregister, pending)

    def test_update_path_does_not_clear_host_data(self):
        source = (PLUGINS / "ExternalPlugins.java").read_text()
        start = source.index("    public static void install(")
        end = source.index("    public static void export(", start)
        method = source[start:end]

        self.assertNotIn("clearForPlugin", method)
        self.assertNotIn("clearPersistedEnabledState", method)


if __name__ == "__main__":
    unittest.main()

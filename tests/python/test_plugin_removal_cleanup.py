from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
PLUGINS = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/plugins"


class PluginRemovalCleanupTest(unittest.TestCase):
    def test_settings_cleanup_uses_exact_plugin_prefixes(self):
        source = (PLUGINS / "PluginSettings.java").read_text()
        start = source.index("    private static boolean clearForPlugin(")
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

        journal = method.index("store.scheduleDelete(id);")
        storage = method.index("PluginStorage.clearPlugin(id);")
        settings = method.index("PluginSettings.clearForPluginNow(id)")
        enabled = method.index("manager.clearPersistedEnabledStateNow(id)")
        removed = method.index("store.applyPendingDelete(id);")
        unregister = method.index("manager.unregister(id);")
        pending = method.index("store.pending()", unregister)

        self.assertLess(journal, storage)
        self.assertLess(storage, settings)
        self.assertLess(settings, enabled)
        self.assertLess(enabled, removed)
        self.assertLess(removed, unregister)
        self.assertLess(enabled, unregister)
        self.assertLess(unregister, pending)

    def test_update_path_does_not_clear_host_data(self):
        source = (PLUGINS / "ExternalPlugins.java").read_text()
        start = source.index("    public static void install(")
        end = source.index("    public static void export(", start)
        method = source[start:end]

        self.assertNotIn("clearForPlugin", method)
        self.assertNotIn("clearPersistedEnabledState", method)

    def test_logout_cleanup_is_durable_and_owner_scoped(self):
        maintenance = (PLUGINS / "PluginStorageMaintenance.java").read_text()
        storage = (PLUGINS / "PluginStorage.java").read_text()
        messages = (
            ROOT
            / "TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java"
        ).read_text()
        connections = (
            ROOT
            / "TMessagesProj/src/main/java/org/telegram/tgnet/ConnectionsManager.java"
        ).read_text()
        push = (
            ROOT
            / "TMessagesProj/src/main/java/org/telegram/messenger/PushListenerController.java"
        ).read_text()
        user_config = (
            ROOT
            / "TMessagesProj/src/main/java/org/telegram/messenger/UserConfig.java"
        ).read_text()
        start = messages.index(
            "    public void performLogout(int type, long fluxGramPluginStorageOwner)"
        )
        method = messages[start:messages.index("\n    }", start) + 6]

        self.assertIn("scheduleOwnerCleanup(", method)
        self.assertIn("runPendingOwnerCleanup();", method)
        self.assertLess(
            method.index("scheduleOwnerCleanup("),
            method.index("getUserConfig().clearConfig();"),
        )
        self.assertLess(
            method.index("getUserConfig().clearConfig();"),
            method.index("runPendingOwnerCleanup();"),
        )
        schedule_failure = method.index(
            'FileLog.e("Cannot schedule FluxGram plugin owner storage cleanup");'
        )
        self.assertLess(schedule_failure, method.index("return;", schedule_failure))
        self.assertLess(method.index("return;", schedule_failure), method.index("if (type == 1)"))
        self.assertIn("putBoolean(key(account, owner), true).commit()", maintenance)
        self.assertIn("!PluginStorage.clearOwner(owner.account, owner.owner)", maintenance)
        self.assertIn("scope_kind = 1 AND account = ? AND owner = ?", storage)
        self.assertIn("performLogout(0, owner);", connections)
        self.assertIn("performLogout(0, owner);", push)
        self.assertIn("PluginStorage.runWithAccountOwnerLock(", user_config)


if __name__ == "__main__":
    unittest.main()

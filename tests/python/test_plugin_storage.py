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

    def test_private_files_use_safe_atomic_host_storage(self):
        source = (JAVA / "PluginPrivateFiles.java").read_text()
        storage = (JAVA / "PluginStorage.java").read_text()
        runtime = (PYTHON / "_storage.py").read_text()

        self.assertIn("getNoBackupFilesDir()", source)
        self.assertIn('sha256(scope + "\\u0000" + path)', source)
        self.assertIn("OsConstants.O_NOFOLLOW", source)
        self.assertIn("stream.getFD().sync();", source)
        self.assertIn("Os.rename(", source)
        self.assertIn("MAX_FILES = 512", source)
        self.assertIn("MAX_FILE_BYTES = 8 * 1024 * 1024", source)
        self.assertIn("MAX_TOTAL_FILE_BYTES = 32 * 1024 * 1024", source)
        self.assertIn("locks.computeIfAbsent(pluginId", source)
        self.assertIn("synchronized (lock)", source)
        self.assertIn("fileWrite(String scopeToken, String path, byte[] data)", storage)
        self.assertIn("runFileOperation(scope", storage)
        self.assertIn("runWithAccountOwnerLock(scope.account", storage)
        self.assertIn("def file_write(self, scope, path, data):", runtime)

    def test_android_runs_storage_migrations_before_plugin_instances(self):
        bridge = (PYTHON / "_bridge.py").read_text()
        adapter = (JAVA / "PythonPluginAdapter.java").read_text()
        external = (JAVA / "ExternalPlugins.java").read_text()
        application = (
            ROOT
            / "TMessagesProj/src/main/java/org/telegram/messenger/ApplicationLoader.java"
        ).read_text()

        self.assertIn("def migrate_external_plugin(", bridge)
        self.assertIn("_migrate_plugin_storage(_plugin_class(module), storage)", bridge)
        self.assertIn("finally:\n        _forget_namespace(namespace)", bridge)
        self.assertLess(
            adapter.index('callAttr("migrate_external_plugin"'),
            adapter.index('"create_external_plugin"'),
        )
        migrate = adapter.split("public static void migrateStorage", 1)[1].split(
            "private static void migrateExternalStorage", 1
        )[0]
        self.assertLess(migrate.index("!installed.manifest.hasPythonModule()"), migrate.index("return;"))
        self.assertLess(
            external.index("PythonPluginAdapter.migrateStorage(installed)"),
            external.index("manager.register(adapter, false);"),
        )
        self.assertLess(
            application.index("UserConfig.getInstance(a).loadConfig();"),
            application.index("ExternalPlugins.initialize(applicationContext);"),
        )

    def test_update_keeps_package_backup_until_migration_commits(self):
        external = (JAVA / "ExternalPlugins.java").read_text()
        store = (
            JAVA / "packages" / "PluginPackageStore.java"
        ).read_text()

        activate = external.index("store.activatePendingUpdate(operation.id)")
        migrate = external.index("PythonPluginAdapter.migrateStorage(update)")
        finish = external.index("store.finishPendingUpdate(operation.id)", migrate)
        rollback = external.index("store.rollbackPending(operation.id)", migrate)
        migrated = external.index("store.markPendingMigrated(operation.id)", migrate)

        self.assertLess(activate, migrate)
        self.assertLess(migrate, migrated)
        self.assertLess(migrated, finish)
        self.assertLess(migrate, finish)
        self.assertLess(migrate, rollback)
        self.assertIn('createMarker(journal, "rollback")', store)
        self.assertIn('createMarker(journal, "migrated")', store)
        self.assertLess(
            store.index('createMarker(journal, "migrated")'),
            store.index("deleteTree(backup);", store.index("finishPendingUpdate")),
        )

    def test_startup_isolates_pending_recovery_failures(self):
        external = (JAVA / "ExternalPlugins.java").read_text()
        manager = (JAVA / "PluginManager.java").read_text()
        store = (JAVA / "packages" / "PluginPackageStore.java").read_text()

        recovery_loop = external[
            external.index("for (PluginPackageStore.Pending operation : operations.values())"):
            external.index("LinkedHashMap<String, IncompatiblePlugin> unavailable")
        ]
        blocked = manager[
            manager.index("synchronized void registerBlockedUntilRestart"):
            manager.index("public synchronized boolean setEnabled")
        ]

        self.assertIn("catch (Exception | LinkageError error)", recovery_loop)
        self.assertIn("recoveryFailed.add(operation.id);", recovery_loop)
        self.assertIn("recordPendingErrors(store.pendingErrors(), recoveryFailed);", external)
        self.assertIn("manager.registerBlockedUntilRestart(adapter);", external)
        self.assertIn("pendingErrors = Collections.unmodifiableMap(errors);", store)
        self.assertIn("entry.blockedUntilRestart = blockedUntilRestart;", blocked)
        self.assertNotIn("preferences.edit()", blocked)

    def test_cleanup_covers_kv_files_sidecars_and_exact_owner(self):
        storage = (JAVA / "PluginStorage.java").read_text()
        files = (JAVA / "PluginPrivateFiles.java").read_text()

        self.assertIn("public static synchronized void clearPlugin(String pluginId)", storage)
        self.assertIn('database.getPath() + "-wal"', storage)
        self.assertIn('database.getPath() + "-shm"', storage)
        self.assertIn("public static synchronized boolean clearOwner(int account, long owner)", storage)
        self.assertIn("scope_kind = 1 AND account = ? AND owner = ?", storage)
        self.assertIn("static void clearPlugin(String pluginId)", files)
        self.assertIn("static void clearOwner(int account, long owner)", files)
        self.assertIn('String scope = "account:" + account + ":" + owner', files)

    def test_fresh_install_cleans_orphans_before_package_publication(self):
        external = (JAVA / "ExternalPlugins.java").read_text()
        store = (JAVA / "packages" / "PluginPackageStore.java").read_text()
        start = external.index("    public static void install(")
        method = external[start:external.index("    public static void export(", start)]
        prepare = store.index("            if (beforePublish != null)")
        publish = store.index("            if (!staging.renameTo(destination))")

        self.assertIn("() -> PluginStorage.clearPlugin(installedId)", method)
        self.assertLess(prepare, publish)


if __name__ == "__main__":
    unittest.main()

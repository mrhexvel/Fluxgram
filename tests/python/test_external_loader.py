import importlib.util
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

SOURCE = (
    Path(__file__).resolve().parents[2]
    / "TMessagesProj/src/main/python/fluxgram_runtime/_bridge.py"
)


class Plugin:
    def __init__(self, plugin_id):
        self.id = plugin_id

    def _bind_runtime(self, host, generation):
        self.host, self.generation = host, generation

    def _unload(self):
        self.closed = True


class ExternalLoaderTest(unittest.TestCase):
    def setUp(self):
        package = types.ModuleType("fluxgram")
        package.__path__ = []
        plugin = types.ModuleType("fluxgram.plugin")
        plugin.Plugin = Plugin
        events = types.ModuleType("fluxgram.events")
        events.BeforeSendEvent = object
        self.patch = patch.dict(
            sys.modules,
            {"fluxgram": package, "fluxgram.plugin": plugin, "fluxgram.events": events},
        )
        self.patch.start()
        self.addCleanup(self.patch.stop)
        spec = importlib.util.spec_from_file_location("fluxgram_runtime._bridge", SOURCE)
        self.bridge = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.bridge)
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def create(self, name, value):
        root = self.root / name
        root.mkdir()
        (root / "helper.py").write_text(f"value = {value!r}\n")
        (root / "main.py").write_text(
            "from fluxgram.plugin import Plugin\nfrom .helper import value\n"
            "class PluginEntry(Plugin):\n    result = value\n"
        )
        return self.bridge.create_external_plugin(
            str(root), "main", "test." + name, self, 1
        )

    def test_same_module_names_are_independent(self):
        old_path = sys.path[:]
        a, b = self.create("one", 1), self.create("two", 2)
        self.assertEqual((a.result, b.result), (1, 2))
        self.assertEqual(sys.path, old_path)
        self.bridge.unload_plugin(a)
        self.assertTrue(a.closed)
        self.assertFalse(
            any(key.startswith(a._external_namespace) for key in sys.modules)
        )
        self.assertIn(b._external_namespace + ".helper", sys.modules)
        self.bridge.unload_plugin(b)

    def test_failed_import_cleans_partial_namespace(self):
        (self.root / "main.py").write_text('raise RuntimeError("broken")')
        before = set(sys.modules)
        with self.assertRaises(RuntimeError):
            self.bridge.create_external_plugin(
                str(self.root), "main", "test.bad", self, 1
            )
        self.assertFalse(
            any(key.startswith("_fgplugin_") for key in set(sys.modules) - before)
        )

    def test_invalid_entry_class_is_rejected(self):
        (self.root / "main.py").write_text("class PluginEntry: pass")
        with self.assertRaises(TypeError):
            self.bridge.create_external_plugin(
                str(self.root), "main", "test.bad", self, 1
            )

    def test_new_enable_has_fresh_module_globals(self):
        a = self.create("one", 1)
        namespace = a._external_namespace
        sys.modules[namespace + ".helper"].value = 100
        self.bridge.unload_plugin(a)
        b = self.bridge.create_external_plugin(
            str(self.root / "one"), "main", "test.one", self, 2
        )
        self.assertEqual(b.result, 1)
        self.assertNotEqual(b._external_namespace, namespace)
        self.bridge.unload_plugin(b)

    def test_unload_exception_preserves_namespace_for_retry(self):
        a = self.create("one", 1)

        def fail():
            raise RuntimeError("unload error")

        a._unload = fail
        with self.assertRaises(RuntimeError):
            self.bridge.unload_plugin(a)
        self.assertIn(a._external_namespace, sys.modules)
        a._unload = lambda: None
        self.bridge.unload_plugin(a)
        self.assertNotIn(a._external_namespace, sys.modules)

    def test_setting_changes_dispatch_through_settings(self):
        calls = []
        plugin = Plugin("test.settings")
        plugin.settings = types.SimpleNamespace(_dispatch_changed=lambda *args: calls.append(args))
        self.bridge.dispatch_setting_changed(plugin, "prefix", "Updated")
        self.assertEqual(calls, [(plugin, "prefix", "Updated")])

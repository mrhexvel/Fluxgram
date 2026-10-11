from contextlib import contextmanager
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


class EventContext:
    def __init__(self, **values):
        self.__dict__.update(values)


class PluginSettingChangedEvent:
    def __init__(self, **values):
        self.__dict__.update(values)


class Record:
    def __init__(self, **values):
        self.__dict__.update(values)


class ExternalLoaderTest(unittest.TestCase):
    def setUp(self):
        @contextmanager
        def runtime_scope(factory):
            yield

        package = types.ModuleType("fluxgram")
        package.__path__ = []
        plugin = types.ModuleType("fluxgram.plugin")
        plugin.Plugin = Plugin
        events = types.ModuleType("fluxgram.events")
        events.BeforeSendEvent = object
        events.EventContext = EventContext
        events.MessageReceivedEvent = Record
        events.PluginSettingChangedEvent = PluginSettingChangedEvent
        models = types.ModuleType("fluxgram.models")
        models.ChatInfo = Record
        models.ChatNotifications = Record
        models.MessageInfo = Record
        models.NotificationOverride = Record
        runtime = types.ModuleType("fluxgram._runtime")
        runtime.runtime_scope = runtime_scope
        runtime_package = types.ModuleType("fluxgram_runtime")
        runtime_package.__path__ = []
        bindings = types.ModuleType("fluxgram_runtime._bindings")
        bindings.create_runtime_bindings = object()
        self.patch = patch.dict(
            sys.modules,
            {
                "fluxgram": package,
                "fluxgram.plugin": plugin,
                "fluxgram.events": events,
                "fluxgram.models": models,
                "fluxgram._runtime": runtime,
                "fluxgram_runtime": runtime_package,
                "fluxgram_runtime._bindings": bindings,
            },
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

    def test_typed_setting_event_precedes_legacy_dispatch(self):
        calls = []
        plugin = Plugin("test.settings")
        plugin.events = types.SimpleNamespace(
            _dispatch=lambda event: calls.append(("typed", event))
        )
        plugin.settings = types.SimpleNamespace(
            _dispatch_changed=lambda *args: calls.append(("legacy", args))
        )

        self.bridge.dispatch_setting_changed(
            plugin,
            "prefix",
            "Updated",
            2,
            99,
            7,
        )

        event = calls[0][1]
        self.assertEqual(
            (
                calls[0][0],
                event.context.account,
                event.context.owner_id,
                event.context.generation,
                event.key,
                event.value,
            ),
            ("typed", 2, 99, 7, "prefix", "Updated"),
        )
        self.assertEqual(calls[1], ("legacy", (plugin, "prefix", "Updated")))

    def test_typed_message_snapshot_is_rebuilt_from_json(self):
        calls = []
        plugin = Plugin("test.messages")
        plugin.events = types.SimpleNamespace(
            _dispatch=lambda event: calls.append(event)
        )
        payload = """{
            "chat": {
                "id": -10,
                "title": "Group",
                "kind": "group",
                "is_forum": true,
                "archived": false,
                "notifications": {
                    "chat_override": {"mode": "muted", "mute_until": null},
                    "topic_override": {"mode": "until", "mute_until": 123},
                    "effective_muted": true
                }
            },
            "message": {
                "id": 7,
                "dialog_id": -10,
                "topic_id": 3,
                "sender_id": 99,
                "text": "Hello",
                "outgoing": false,
                "mentioned": true,
                "is_reply": true,
                "silent": false
            }
        }"""

        self.bridge.dispatch_message_received(plugin, payload, 2, 88, 9)

        event = calls[0]
        self.assertEqual(
            (
                event.account,
                event.context.account,
                event.context.owner_id,
                event.context.generation,
                event.chat.id,
                event.chat.kind,
                event.chat.notifications.topic_override.mute_until,
                event.message.id,
                event.message.sender_id,
                event.message.text,
            ),
            (2, 2, 88, 9, -10, "group", 123, 7, 99, "Hello"),
        )

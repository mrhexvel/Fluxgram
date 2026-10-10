import importlib.util
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2] / "sdk/python/src/fluxgram"


class Resource:
    def __init__(self, *args):
        self.calls = 0
        self.failure = None

    def _close(self):
        self.calls += 1
        if self.failure is not None:
            raise self.failure

    close = _close


class PluginCleanupTest(unittest.TestCase):
    def setUp(self):
        def module(name, **values):
            result = types.ModuleType(name)
            result.__dict__.update(values)
            return result

        modules = {
            "fluxgram": module("fluxgram", __path__=[]),
            "fluxgram_runtime": module("fluxgram_runtime", __path__=[]),
            "android": module("android", __path__=[]),
            "android.util": module("android.util", Log=object),
            "fluxgram.events": module("fluxgram.events", BeforeSendEvent=object),
            "fluxgram_runtime._event_bus": module("fluxgram_runtime._event_bus", EventBus=Resource),
            "fluxgram_runtime.client": module("fluxgram_runtime.client", Client=Resource),
            "fluxgram.ui": module("fluxgram.ui", UI=Resource),
            "fluxgram_runtime._ui_thread": module(
                "fluxgram_runtime._ui_thread",
                call_ui=lambda callback: callback(),
            ),
        }
        with patch.dict(sys.modules, modules):
            settings_spec = importlib.util.spec_from_file_location("fluxgram.settings", ROOT / "settings.py")
            settings = importlib.util.module_from_spec(settings_spec)
            sys.modules["fluxgram.settings"] = settings
            settings_spec.loader.exec_module(settings)
            spec = importlib.util.spec_from_file_location("fluxgram.plugin", ROOT / "plugin.py")
            loaded = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(loaded)
            self.plugin = loaded.Plugin("test.cleanup")

        self.hook = Resource()
        self.plugin.on_unload = self.hook._close

    def test_failure_does_not_skip_other_cleanup(self):
        self.plugin.ui.failure = RuntimeError("UI failed")
        with self.assertRaisesRegex(RuntimeError, "ui"):
            self.plugin._unload()
        self.assertEqual([resource.calls for resource in (
            self.plugin.ui, self.plugin.client, self.plugin.events, self.hook
        )], [1, 1, 1, 1])

    def test_retry_only_repeats_failed_steps(self):
        self.hook.failure = RuntimeError("hook failed")
        with self.assertRaises(RuntimeError):
            self.plugin._unload()
        self.hook.failure = None
        self.plugin._unload()
        self.plugin._unload()
        self.assertEqual(self.hook.calls, 2)
        self.assertEqual(self.plugin.ui.calls, 1)
        self.assertEqual(self.plugin.events.calls, 1)

    def test_multiple_failures_are_reported(self):
        self.plugin.ui.failure = RuntimeError("UI failed")
        self.plugin.events.failure = RuntimeError("events failed")
        with self.assertRaisesRegex(RuntimeError, "ui, events"):
            self.plugin._unload()
        self.assertEqual(self.hook.calls, 1)

    def test_system_exit_does_not_skip_cleanup(self):
        self.plugin.ui.failure = SystemExit(1)
        with self.assertRaises(RuntimeError):
            self.plugin._unload()
        self.assertEqual(self.plugin.events.calls, 1)
        self.assertEqual(self.hook.calls, 1)

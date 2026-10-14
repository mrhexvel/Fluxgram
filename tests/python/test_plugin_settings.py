import importlib.util
from pathlib import Path
import re
import sys
import types
import unittest
from unittest.mock import patch


PROJECT = Path(__file__).resolve().parents[2]
SDK = PROJECT / "sdk/python/src"
RUNTIME = PROJECT / "TMessagesProj/src/main/python"


class PluginSettingsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location(
            "settings_test_module", SDK / "fluxgram/settings.py"
        )
        cls.sdk = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = cls.sdk
        spec.loader.exec_module(cls.sdk)
        cls.addClassCleanup(sys.modules.pop, spec.name, None)

    def host(self, values, active=True):
        return types.SimpleNamespace(
            isGenerationActive=lambda generation: active and generation == 7,
            readBooleanSetting=lambda key, fallback: values.get(key, fallback),
            readStringSetting=lambda key, fallback: values.get(key, fallback),
            readIntSetting=lambda key, fallback: values.get(key, fallback),
        )

    def test_default_and_live_changes(self):
        values = {}
        settings = self.sdk.Settings([self.sdk.Switch("show_muted", "Muted", True)])
        settings._bind_runtime(self.host(values), 7)
        self.assertTrue(settings.get("show_muted"))
        values["show_muted"] = False
        self.assertFalse(settings.get("show_muted"))
        replacement = self.sdk.Settings([self.sdk.Switch("show_muted", "Muted", True)])
        replacement._bind_runtime(self.host(values), 7)
        self.assertFalse(replacement.get("show_muted"))

    def test_inactive_runtime_cannot_read(self):
        settings = self.sdk.Settings([self.sdk.Switch("enabled", "Enabled")])
        with self.assertRaises(RuntimeError):
            settings.get("enabled")
        settings._bind_runtime(self.host({}, active=False), 7)
        with self.assertRaises(RuntimeError):
            settings.get("enabled")

    def test_invalid_schema(self):
        for key in ("", "../key", "a:b", "x" * 65):
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.sdk.Switch(key, "Title")
        with self.assertRaises(TypeError):
            self.sdk.Switch("key", "Title", 1)
        item = self.sdk.Switch("key", "Title")
        with self.assertRaises(ValueError):
            self.sdk.Settings([item, item])
        with self.assertRaises(ValueError):
            self.sdk.Settings([object()])

    def test_schema_export(self):
        settings = self.sdk.Settings([self.sdk.Switch("key", "Title", True, "Details")])
        self.assertEqual(settings._describe(), [
            {"key": "key", "title": "Title", "default": True, "description": "Details", "label": "", "type": "switch"}
        ])

    def test_label_export_and_whitespace(self):
        item = self.sdk.Switch("key", "Title", label="  Notifications  ")
        self.assertEqual(self.sdk.Settings([item])._describe()[0]["label"], "Notifications")
        blank = self.sdk.Switch("key", "Title", label="   ")
        self.assertEqual(self.sdk.Settings([blank])._describe()[0]["label"], "")

    def test_invalid_label(self):
        for label in (None, 123, "x" * 121):
            with self.subTest(label=label), self.assertRaises(ValueError):
                self.sdk.Switch("key", "Title", label=label)

    def test_icon_export_and_validation(self):
        item = self.sdk.Switch("key", "Title", icon="notifications")
        self.assertEqual(self.sdk.Settings([item])._describe()[0]["icon"], "notifications")

        for icon in (None, 123, "msg_notifications", "unknown"):
            with self.subTest(icon=icon), self.assertRaises(ValueError):
                self.sdk.Switch("key", "Title", icon=icon)

    def test_legacy_setting_names_are_aliases(self):
        self.assertIs(self.sdk.SwitchSetting, self.sdk.Switch)
        self.assertIs(self.sdk.SelectSetting, self.sdk.Select)
        self.assertIs(self.sdk.SliderSetting, self.sdk.Slider)
        self.assertIs(self.sdk.TextSetting, self.sdk.Text)

    def test_java_icon_allowlist_matches_sdk(self):
        source = (
            PROJECT
            / "TMessagesProj/src/main/java/org/telegram/messenger/plugins/PluginSettings.java"
        ).read_text()
        method = source.split("private static int iconResource", 1)[1].split(
            "public boolean accepts", 1
        )[0]
        icons = set(re.findall(r'case "([a-z_]+)"', method))
        self.assertEqual(icons, set(self.sdk._SETTING_ICONS))

    def test_builtin_notifications_apply_switch_without_reload(self):
        module = types.ModuleType("fluxgram")
        module.Plugin = object
        module.MessageReceivedEvent = object
        module.Switch = self.sdk.Switch
        module.Select = self.sdk.Select
        module.Slider = self.sdk.Slider
        received = []
        ui_module = types.ModuleType("fluxgram.ui")
        ui_module.Notifications = lambda ui: types.SimpleNamespace(
            show_message=lambda event, **kwargs: received.append(kwargs)
        )

        with patch.dict(sys.modules, {"fluxgram": module, "fluxgram.ui": ui_module}):
            spec = importlib.util.spec_from_file_location(
                "test_notifications", RUNTIME / "fluxgram_notifications.py"
            )
            builtin = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(builtin)
        plugin = builtin.PluginEntry()
        values = {"show_muted_chats": True}
        plugin.settings = self.sdk.Settings(plugin.settings_schema)
        plugin.settings._bind_runtime(self.host(values), 7)
        plugin.ui = object()
        plugin.events = types.SimpleNamespace(subscribe=lambda *args, **kwargs: None)
        plugin.on_load()
        plugin.on_message(object())
        values["show_muted_chats"] = False
        plugin.on_message(object())
        self.assertEqual([item["respect_mute"] for item in received], [False, True])
        self.assertEqual(received[-1]["duration"], 5000)
        values["duration_mode"] = "3"
        plugin.on_message(object())
        self.assertEqual(received[-1]["duration"], 3000)
        values.update(duration_mode="custom", custom_duration=7)
        plugin.on_message(object())
        self.assertEqual(received[-1]["duration"], 7000)

    def test_select_values_and_removed_option_fallback(self):
        item = self.sdk.Select("mode", "Mode", (("a", "First"), ("b", "Second")), "a")
        values = {"mode": "b"}
        settings = self.sdk.Settings([item])
        settings._bind_runtime(self.host(values), 7)
        self.assertEqual(settings.get("mode"), "b")
        values["mode"] = "removed"
        self.assertEqual(settings.get("mode"), "a")
        self.assertEqual(settings._describe()[0]["type"], "select")

    def test_select_rejects_bad_options(self):
        for options in ((), (("a", "First"), ("a", "Duplicate")), ("ab",), (("a", ""),)):
            with self.subTest(options=options), self.assertRaises(ValueError):
                self.sdk.Select("mode", "Mode", options, "a")

    def test_slider_range_step_and_old_value_fallback(self):
        item = self.sdk.Slider("duration", "Duration", 2, 10, 4, step=2, unit="s")
        values = {"duration": 8}
        settings = self.sdk.Settings([item])
        settings._bind_runtime(self.host(values), 7)
        self.assertEqual(settings.get("duration"), 8)
        for invalid in (1, 3, 11):
            values["duration"] = invalid
            self.assertEqual(settings.get("duration"), 4)
        data = settings._describe()[0]
        self.assertEqual((data["type"], data["step"], data["unit"]), ("slider", 2, "s"))

    def test_slider_rejects_invalid_ranges(self):
        for low, high, default, step in ((1, 1, 1, 1), (0, 10, 5, 0), (0, 10, 3, 2),
                                          (0, 9, 2, 2), (0, 2000, 2, 1), (0, 10, 12, 1)):
            with self.subTest(low=low, high=high, default=default, step=step), self.assertRaises(ValueError):
                self.sdk.Slider("key", "Title", low, high, default, step)
        with self.assertRaises(TypeError):
            self.sdk.Slider("key", "Title", 0, 10, True)

    def test_text_default_empty_and_live_changes(self):
        item = self.sdk.Text("prefix", "Prefix", default="Hello", placeholder="Enter prefix", max_length=12)
        values = {}
        settings = self.sdk.Settings([item])
        settings._bind_runtime(self.host(values), 7)
        self.assertEqual(settings.get("prefix"), "Hello")
        values["prefix"] = "  Привет  "
        self.assertEqual(settings.get("prefix"), "  Привет  ")
        values["prefix"] = ""
        self.assertEqual(settings.get("prefix"), "")
        self.assertEqual(settings._describe()[0]["type"], "text")
        self.assertEqual(settings._describe()[0]["max_length"], 12)
        self.assertEqual(settings._describe()[0]["placeholder"], "Enter prefix")

    def test_text_old_value_outside_new_limit_falls_back(self):
        item = self.sdk.Text("text", "Text", default="ok", max_length=3)
        values = {"text": "too long"}
        settings = self.sdk.Settings([item])
        settings._bind_runtime(self.host(values), 7)
        self.assertEqual(settings.get("text"), "ok")
        values["text"] = "a\nb"
        self.assertEqual(settings.get("text"), "ok")

    def test_text_limit_counts_unicode_codepoints(self):
        item = self.sdk.Text("emoji", "Emoji", default="😀", max_length=1)
        settings = self.sdk.Settings([item])
        settings._bind_runtime(self.host({"emoji": "🚀"}), 7)
        self.assertEqual(settings.get("emoji"), "🚀")
        with self.assertRaises(ValueError):
            self.sdk.Text("emoji", "Emoji", default="😀🚀", max_length=1)

    def test_invalid_text_schema(self):
        for options in ({"max_length": 0}, {"max_length": 4097}, {"max_length": True},
                        {"default": None}, {"default": "a\nb"}, {"default": "a\rb"},
                        {"placeholder": 123}, {"placeholder": "x" * 121}):
            with self.subTest(options=options), self.assertRaises(ValueError):
                self.sdk.Text("text", "Text", **options)

    def callback_items(self, callback):
        return (
            self.sdk.Switch("switch", "Switch", on_changed=callback),
            self.sdk.Select("select", "Select", (("a", "A"),), "a", on_changed=callback),
            self.sdk.Slider("slider", "Slider", 0, 10, 5, on_changed=callback),
            self.sdk.Text("text", "Text", on_changed=callback),
        )

    def test_callbacks_receive_current_instance_for_shared_schema(self):
        calls = []

        def changed(plugin, value):
            calls.append((plugin, value))

        schema = self.callback_items(changed)
        owners = [types.SimpleNamespace(), types.SimpleNamespace()]

        for owner in owners:
            owner.settings = self.sdk.Settings(schema)
            owner.settings._bind_runtime(self.host({}), 7)

            for item in schema:
                owner.settings._dispatch_changed(owner, item.key, item.default)

        self.assertEqual(calls, [(owner, item.default) for owner in owners for item in schema])
        self.assertTrue(all(item.on_changed is changed for item in schema))

    def test_callback_precedence_and_legacy_fallback(self):
        calls = []
        owner = types.SimpleNamespace(on_setting_changed=lambda key, value: calls.append((key, value)))
        settings = self.sdk.Settings([
            self.sdk.Text("own", "Own", on_changed=lambda plugin, value: calls.append((plugin, value))),
            self.sdk.Text("legacy", "Legacy"),
        ])
        settings._bind_runtime(self.host({}), 7)
        settings._dispatch_changed(owner, "own", "first")
        settings._dispatch_changed(owner, "legacy", "second")

        self.assertEqual(calls, [(owner, "first"), ("legacy", "second")])

    def test_failed_callback_does_not_invoke_fallback(self):
        calls = []

        def changed(plugin, value):
            raise ValueError("callback failed")

        owner = types.SimpleNamespace(on_setting_changed=lambda *args: calls.append(args))
        settings = self.sdk.Settings([self.sdk.Text("text", "Text", on_changed=changed)])
        settings._bind_runtime(self.host({}), 7)

        with self.assertRaisesRegex(ValueError, "callback failed"):
            settings._dispatch_changed(owner, "text", "new")

        self.assertEqual(calls, [])

    def test_inactive_runtime_does_not_dispatch_callbacks(self):
        calls = []
        settings = self.sdk.Settings(self.callback_items(lambda *args: calls.append(args)))
        settings._dispatch_changed(object(), "text", "new")
        settings._bind_runtime(self.host({}, active=False), 7)
        settings._dispatch_changed(object(), "text", "new")
        self.assertEqual(calls, [])

    def test_callback_excluded_from_serialized_schema(self):
        import json

        settings = self.sdk.Settings(self.callback_items(lambda plugin, value: None))
        data = json.loads(json.dumps(settings._describe()))
        self.assertEqual(len(data), 4)
        self.assertTrue(all("on_changed" not in item for item in data))

    def test_invalid_callback(self):
        for item in self.callback_items(None):
            with self.subTest(setting=type(item).__name__), self.assertRaises(TypeError):
                from dataclasses import replace

                replace(item, on_changed="method_name")

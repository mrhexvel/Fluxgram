import importlib
import json
import sys
import types
import uuid
from pathlib import Path

from .plugin import Plugin
from .events import BeforeSendEvent


def create_plugin(module_name: str, plugin_id: str, host, generation):
    module = importlib.import_module(module_name)

    return _instantiate(module, plugin_id, host, generation)


def _instantiate(module, plugin_id, host, generation):
    module_name = module.__name__
    plugin_class = getattr(module, "PluginEntry", None)

    if not isinstance(plugin_class, type):
        raise TypeError(
            f"{module_name} must define a PluginEntry class"
        )

    if not issubclass(plugin_class, Plugin):
        raise TypeError(
            f"{module_name}.PluginEntry must inherit from fluxgram.Plugin"
        )

    plugin = plugin_class(plugin_id)
    plugin._bind_runtime(host, generation)

    return plugin


def dispatch_before_send(plugin, event_json: str):
    data = json.loads(event_json)

    event = BeforeSendEvent(
        account=data["account"],
        text_length=data["text_length"],
        schedule_date=data["schedule_date"],
    )

    plugin.before_send(event)


def _forget_namespace(namespace):
    for name in tuple(sys.modules):
        if name == namespace or name.startswith(namespace + "."):
            sys.modules.pop(name, None)


def create_external_plugin(directory, module_name, plugin_id, host, generation):

    namespace = "_fgplugin_" + uuid.uuid4().hex
    package = types.ModuleType(namespace)
    package.__path__ = [str(Path(directory).resolve())]
    package.__package__ = namespace
    sys.modules[namespace] = package

    try:
        module = importlib.import_module(namespace + "." + module_name)
        plugin = _instantiate(module, plugin_id, host, generation)
        plugin._external_namespace = namespace

        return plugin
    except BaseException:
        _forget_namespace(namespace)
        raise


def unload_plugin(plugin):
    plugin._unload()
    namespace = getattr(plugin, "_external_namespace", None)

    if namespace is not None:
        _forget_namespace(namespace)


def describe_settings(plugin):
    return json.dumps(plugin.settings._describe(), ensure_ascii=False)


def dispatch_setting_changed(plugin, key, value):
    plugin.settings._dispatch_changed(plugin, key, value)

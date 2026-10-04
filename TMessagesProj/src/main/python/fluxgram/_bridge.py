import importlib
import json

from .plugin import Plugin
from .events import BeforeSendEvent


def create_plugin(module_name: str, plugin_id: str, host, generation):
    module = importlib.import_module(module_name)
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
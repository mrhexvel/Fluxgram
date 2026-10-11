import importlib
import json
import sys
import types
import uuid
from pathlib import Path

from fluxgram.events import (
    BeforeSendEvent,
    EventContext,
    PluginSettingChangedEvent,
)
from fluxgram.plugin import Plugin
from fluxgram._runtime import runtime_scope

from ._bindings import create_runtime_bindings


def create_plugin(module_name: str, plugin_id: str, host, generation):
    module = importlib.import_module(module_name)

    return _instantiate(module, plugin_id, host, generation)


def _instantiate(module, plugin_id, host, generation):
    plugin_class = _plugin_class(module)

    with runtime_scope(create_runtime_bindings):
        plugin = plugin_class(plugin_id)

    plugin._bind_runtime(host, generation)

    return plugin


def _plugin_class(module):
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

    return plugin_class


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


def _load_external_module(directory, module_name):
    namespace = "_fgplugin_" + uuid.uuid4().hex
    package = types.ModuleType(namespace)
    package.__path__ = [str(Path(directory).resolve())]
    package.__package__ = namespace
    sys.modules[namespace] = package

    try:
        return namespace, importlib.import_module(namespace + "." + module_name)
    except BaseException:
        _forget_namespace(namespace)
        raise


def create_external_plugin(directory, module_name, plugin_id, host, generation):
    namespace, module = _load_external_module(directory, module_name)

    try:
        plugin = _instantiate(module, plugin_id, host, generation)
        plugin._external_namespace = namespace

        return plugin
    except BaseException:
        _forget_namespace(namespace)
        raise


class _MigrationHost:
    def isGenerationActive(self, generation):
        return generation == 1


def _migrate(module, plugin_id):
    from fluxgram.storage import Storage, _migrate_plugin_storage

    from ._storage import AndroidStoragePort

    port = AndroidStoragePort(plugin_id)
    storage = Storage(port)
    storage._bind_runtime(_MigrationHost(), 1)

    try:
        _migrate_plugin_storage(_plugin_class(module), storage)
    finally:
        storage._close()


def migrate_plugin(module_name, plugin_id):
    _migrate(importlib.import_module(module_name), plugin_id)


def migrate_external_plugin(directory, module_name, plugin_id):
    namespace, module = _load_external_module(directory, module_name)

    try:
        _migrate(module, plugin_id)
    finally:
        _forget_namespace(namespace)


def unload_plugin(plugin):
    plugin._unload()
    namespace = getattr(plugin, "_external_namespace", None)

    if namespace is not None:
        _forget_namespace(namespace)


def describe_settings(plugin):
    return json.dumps(plugin.settings._describe(), ensure_ascii=False)


def dispatch_setting_changed(
        plugin,
        key,
        value,
        account=None,
        owner_id=None,
        generation=None,
):
    if account is not None and owner_id is not None and generation is not None:
        plugin.events._dispatch(PluginSettingChangedEvent(
            context=EventContext(
                account=int(account),
                owner_id=int(owner_id),
                generation=int(generation),
            ),
            key=str(key),
            value=value,
        ))

    plugin.settings._dispatch_changed(plugin, key, value)

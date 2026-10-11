import importlib
import json
import sys
import types
import uuid
from pathlib import Path

from fluxgram.events import (
    ApplicationBackgroundedEvent,
    ApplicationForegroundedEvent,
    BeforeSendEvent,
    ChatClosedEvent,
    ChatOpenedEvent,
    EventContext,
    MessageDeletedEvent,
    MessageEditedEvent,
    MessageReceivedEvent,
    PluginSettingChangedEvent,
)
from fluxgram.models import ChatInfo, ChatNotifications, MessageInfo, NotificationOverride
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


def dispatch_message_received(
        plugin,
        payload,
        account,
        owner_id,
        generation,
):
    data, chat, message = _decode_message_snapshot(payload)
    context = EventContext(
        account=int(account),
        owner_id=int(owner_id),
        generation=int(generation),
    )
    plugin.events._dispatch(MessageReceivedEvent(
        account=context.account,
        chat=chat,
        message=message,
        context=context,
    ))


def dispatch_message_edited(
        plugin,
        payload,
        account,
        owner_id,
        generation,
):
    data, chat, message = _decode_message_snapshot(payload)
    context = EventContext(
        account=int(account),
        owner_id=int(owner_id),
        generation=int(generation),
    )
    plugin.events._dispatch(MessageEditedEvent(
        account=context.account,
        chat=chat,
        message=message,
        edit_date=int(data["edit_date"]),
        context=context,
    ))


def dispatch_messages_deleted(
        plugin,
        payload,
        account,
        owner_id,
        generation,
):
    data = json.loads(str(payload))
    context = EventContext(
        account=int(account),
        owner_id=int(owner_id),
        generation=int(generation),
    )
    channel_id = (
        int(data["channel_id"])
        if data.get("channel_id") is not None
        else None
    )

    for message_id in data["message_ids"]:
        plugin.events._dispatch(MessageDeletedEvent(
            context=context,
            message_id=int(message_id),
            channel_id=channel_id,
        ))


def dispatch_chat_opened(
        plugin,
        account,
        owner_id,
        generation,
        presentation_id,
        dialog_id,
        topic_id,
):
    plugin.events._dispatch(ChatOpenedEvent(
        context=EventContext(
            account=int(account),
            owner_id=int(owner_id),
            generation=int(generation),
        ),
        presentation_id=int(presentation_id),
        dialog_id=int(dialog_id),
        topic_id=int(topic_id),
    ))


def dispatch_chat_closed(
        plugin,
        account,
        owner_id,
        generation,
        presentation_id,
        dialog_id,
        topic_id,
):
    plugin.events._dispatch(ChatClosedEvent(
        context=EventContext(
            account=int(account),
            owner_id=int(owner_id),
            generation=int(generation),
        ),
        presentation_id=int(presentation_id),
        dialog_id=int(dialog_id),
        topic_id=int(topic_id),
    ))


def dispatch_application_foregrounded(
        plugin,
        account,
        owner_id,
        generation,
):
    plugin.events._dispatch(ApplicationForegroundedEvent(
        context=EventContext(
            account=int(account),
            owner_id=int(owner_id),
            generation=int(generation),
        ),
    ))


def dispatch_application_backgrounded(
        plugin,
        account,
        owner_id,
        generation,
):
    plugin.events._dispatch(ApplicationBackgroundedEvent(
        context=EventContext(
            account=int(account),
            owner_id=int(owner_id),
            generation=int(generation),
        ),
    ))


def _decode_message_snapshot(payload):
    data = json.loads(str(payload))
    chat_data = data["chat"]
    message_data = data["message"]
    notifications_data = chat_data["notifications"]
    chat_override_data = notifications_data["chat_override"]
    topic_override_data = notifications_data.get("topic_override")
    chat_override = NotificationOverride(
        mode=str(chat_override_data["mode"]),
        mute_until=(
            int(chat_override_data["mute_until"])
            if chat_override_data.get("mute_until") is not None
            else None
        ),
    )
    topic_override = (
        NotificationOverride(
            mode=str(topic_override_data["mode"]),
            mute_until=(
                int(topic_override_data["mute_until"])
                if topic_override_data.get("mute_until") is not None
                else None
            ),
        )
        if topic_override_data is not None
        else None
    )
    notifications = ChatNotifications(
        chat_override=chat_override,
        topic_override=topic_override,
        effective_muted=(
            bool(notifications_data["effective_muted"])
            if notifications_data.get("effective_muted") is not None
            else None
        ),
    )
    chat = ChatInfo(
        id=int(chat_data["id"]),
        title=(
            str(chat_data["title"])
            if chat_data.get("title") is not None
            else None
        ),
        kind=str(chat_data["kind"]),
        is_forum=(
            bool(chat_data["is_forum"])
            if chat_data.get("is_forum") is not None
            else None
        ),
        archived=(
            bool(chat_data["archived"])
            if chat_data.get("archived") is not None
            else None
        ),
        notifications=notifications,
    )
    message = MessageInfo(
        id=int(message_data["id"]),
        dialog_id=int(message_data["dialog_id"]),
        topic_id=int(message_data["topic_id"]),
        sender_id=(
            int(message_data["sender_id"])
            if message_data.get("sender_id") is not None
            else None
        ),
        text=str(message_data["text"]),
        outgoing=bool(message_data["outgoing"]),
        mentioned=bool(message_data["mentioned"]),
        is_reply=bool(message_data["is_reply"]),
        silent=bool(message_data["silent"]),
    )

    return data, chat, message

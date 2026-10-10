from pkgutil import extend_path

from .plugin import Plugin
from .settings import SwitchSetting, SelectSetting, SliderSetting, TextSetting
from .events import BeforeSendEvent, MessageReceivedEvent
from .models import (
    ChatInfo,
    ChatNotifications,
    MessageInfo,
    NotificationOverride,
)

API_VERSION = 2
API_MINOR_VERSION = 0

__path__ = extend_path(__path__, __name__)

__all__ = [
    "Plugin",
    "SwitchSetting",
    "SelectSetting",
    "SliderSetting",
    "TextSetting",
    "BeforeSendEvent",
    "MessageReceivedEvent",
    "API_VERSION",
    "API_MINOR_VERSION",
    "ChatInfo",
    "ChatNotifications",
    "MessageInfo",
    "NotificationOverride",
]

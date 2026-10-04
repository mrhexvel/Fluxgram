from android.os import Looper
from java import jlong
from org.telegram.messenger import DialogObject, UserConfig

from ._telegram.chats import read_chat
from .models import ChatInfo


def _require_integer(name, value, minimum, maximum):
    if isinstance(value, bool) or not isinstance(value, int):
        raise TypeError(f"{name} must be an integer")

    if not minimum <= value <= maximum:
        raise ValueError(f"{name} is out of range")


class Chats:
    def __init__(self, require_active):
        self._require_active = require_active

    def get(self, *, account: int, dialog_id: int, topic_id: int = 0) -> ChatInfo:


        self._require_active()

        if Looper.myLooper() != Looper.getMainLooper():
            raise RuntimeError(
                "client.chats.get must run on the UI thread; "
                "use it inside a message.received callback with thread='ui'"
            )

        _require_integer("account", account, 0, int(UserConfig.MAX_ACCOUNT_COUNT) - 1)
        _require_integer("dialog_id", dialog_id, -(2**63), 2**63 - 1)
        _require_integer("topic_id", topic_id, 0, 2**63 - 1)

        if dialog_id == 0 or DialogObject.isFolderDialogId(jlong(dialog_id)):
            raise ValueError("dialog_id must identify a chat, not zero or a folder")

        result = read_chat(account, dialog_id, topic_id)
        self._require_active()

        return result


class Client:
    def __init__(self):
        self._closed = False
        self._is_active = lambda: False
        self.chats = Chats(self._require_active)

    def _bind_runtime(self, host, generation):
        if self._closed:
            raise RuntimeError("Plugin client is closed")

        self._is_active = lambda: host.isGenerationActive(generation)

    def _require_active(self):
        if self._closed or not self._is_active():
            raise RuntimeError("Plugin client is inactive")

    def _close(self):
        self._closed = True
        self._is_active = lambda: False

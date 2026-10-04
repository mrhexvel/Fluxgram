from android.util import Log

from .events import BeforeSendEvent
from ._event_bus import EventBus
from .client import Client
from .ui import UI


class Plugin:
    def __init__(self, plugin_id: str):
        self._plugin_id = plugin_id
        self.events = EventBus(plugin_id)
        self.client = Client()
        self.ui = UI()

    def _bind_runtime(self, host, generation):
        self.client._bind_runtime(host, generation)
        self.ui._bind_runtime(host, generation)
        self.events.bind_runtime(host, generation)

    def _unload(self):
        self.ui._close()
        self.client._close()
        self.events.close()
        self.on_unload()

    @property
    def id(self) -> str:
        return self._plugin_id

    def log(self, message: str):
        Log.i(
            "FluxGramPython",
            f"[{self.id}] {message}",
        )

    def on_load(self):
        pass

    def on_unload(self):
        pass

    def before_send(self, event: BeforeSendEvent):
        pass

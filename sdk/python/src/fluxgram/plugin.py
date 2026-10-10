from .events import BeforeSendEvent
from .settings import Settings


class Plugin:
    settings_schema = ()

    def __init__(self, plugin_id: str):
        from fluxgram_runtime._event_bus import EventBus
        from fluxgram_runtime._ui_thread import call_ui
        from fluxgram_runtime.client import Client

        from .ui import UI

        self._plugin_id = plugin_id
        self._call_ui = call_ui
        self.settings = Settings(self.settings_schema)
        self.events = EventBus(plugin_id)
        self.client = Client()
        self.ui = UI()
        self._cleanup_done = set()

    def _bind_runtime(self, host, generation):
        self.settings._bind_runtime(host, generation)
        self.client._bind_runtime(host, generation)
        self.ui._bind_runtime(host, generation)
        self.events.bind_runtime(host, generation)

    def _unload(self):
        steps = (
            ("ui", lambda: self._call_ui(self.ui._close)),
            ("client", self.client._close),
            ("events", lambda: self._call_ui(self.events.close)),
            ("on_unload", self.on_unload),
        )
        errors = []

        for name, close in steps:
            if name in self._cleanup_done:
                continue

            try:
                close()
                self._cleanup_done.add(name)
            except BaseException as error:
                errors.append((name, error))

        if errors:
            names = ", ".join(name for name, _ in errors)
            raise RuntimeError(f"Plugin cleanup failed: {names}") from errors[0][1]

    @property
    def id(self) -> str:
        return self._plugin_id

    def log(self, message: str):
        from android.util import Log

        Log.i(
            "FluxGramPython",
            f"[{self.id}] {message}",
        )

    def on_setting_changed(self, key: str, value: bool | str | int):
        pass

    def on_load(self):
        pass

    def on_unload(self):
        pass

    def before_send(self, event: BeforeSendEvent):
        pass

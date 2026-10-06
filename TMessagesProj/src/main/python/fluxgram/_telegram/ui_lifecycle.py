from java import dynamic_proxy, jclass
from org.telegram.messenger.plugins import PluginUiLifecycle


Listener = jclass("org.telegram.messenger.plugins.PluginUiLifecycle$Listener")


class _Listener(dynamic_proxy(Listener)):
    def __init__(self, owner):
        super().__init__()
        self.owner = owner

    def onBackground(self):
        owner = self.owner

        if owner is not None and owner.ui._active():
            owner.on_background()

    def onChatOpened(self, account, dialog_id, topic_id):
        owner = self.owner

        if owner is not None and owner.ui._active():
            owner.on_chat_opened((int(account), int(dialog_id), int(topic_id)))


class UiLifecycle:
    def __init__(self, ui, on_background, on_chat_opened):
        self.ui = ui
        self.on_background = on_background
        self.on_chat_opened = on_chat_opened
        self._listener = None

    def start(self):
        self.ui._require_ui()

        if self._listener is None:
            listener = _Listener(self)
            PluginUiLifecycle.addListener(listener)
            self._listener = listener

    def close(self):
        if self._listener is not None:
            PluginUiLifecycle.removeListener(self._listener)
            self._listener.owner = None
            self._listener = None

from fluxgram import Plugin, MessageReceivedEvent


class PluginEntry(Plugin):
    # Plugin policy, independent of the SDK and the system notification settings.
    RESPECT_MUTE = False
    SHOW_IN_CURRENT_CHAT = False

    def on_load(self):
        self.events.subscribe("notification.received", self.on_message, thread="ui")

    def on_message(self, event: MessageReceivedEvent):
        return self.ui.notify_message(
            event,
            respect_mute=self.RESPECT_MUTE,
            show_in_current_chat=self.SHOW_IN_CURRENT_CHAT,
        )

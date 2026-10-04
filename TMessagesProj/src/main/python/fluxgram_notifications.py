from fluxgram import Plugin, MessageReceivedEvent, SwitchSetting, SelectSetting, SliderSetting


class PluginEntry(Plugin):

    settings_schema = (
        SwitchSetting(
            "show_muted_chats",
            "Заглушённые чаты",
            default=False,
            description="Показывать уведомления внутри FluxGram, даже если уведомления чата выключены.",
            label="Уведомления",
        ),
        SelectSetting(
            "duration_mode",
            "Длительность показа",
            options=(("3", "3 секунды"), ("5", "5 секунд"), ("8", "8 секунд"), ("custom", "Своя")),
            default="5",
            label="Отображение",
        ),
        SliderSetting(
            "custom_duration",
            "Своя длительность",
            minimum=1,
            maximum=10,
            default=5,
            unit="с",
            description="Используется, когда выше выбрана длительность «Своя».",
        ),
    )
    SHOW_IN_CURRENT_CHAT = False

    def on_load(self):
        self.events.subscribe("notification.received", self.on_message, thread="ui")

    def on_message(self, event: MessageReceivedEvent):
        mode = self.settings.get("duration_mode")
        seconds = self.settings.get("custom_duration") if mode == "custom" else int(mode)

        return self.ui.notify_message(
            event,
            duration=seconds * 1000,
            respect_mute=not self.settings.get("show_muted_chats"),
            show_in_current_chat=self.SHOW_IN_CURRENT_CHAT,
        )

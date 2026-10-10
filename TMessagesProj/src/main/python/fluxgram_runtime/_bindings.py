from android.os import Looper
from android.util import Log

from fluxgram._runtime import RuntimeBindings

from ._event_bus import EventBus
from ._ui_thread import call_ui, post_ui
from .client import Client


class UIThread:
    def is_ui_thread(self):
        return Looper.myLooper() == Looper.getMainLooper()

    def post(self, callback):
        post_ui(callback)


class UIServices:
    def create_bulletin_presenter(self, ui):
        from ._telegram.bulletins import BulletinPresenter

        return BulletinPresenter(ui)

    def create_notifications(self, ui):
        from ._telegram.notifications_service import NotificationsService

        return NotificationsService(ui)

    def open_chat(self, account, dialog_id, topic_id, message_id):
        from ._telegram.navigation import open_chat

        return open_chat(account, dialog_id, topic_id, message_id)


def _log(plugin_id, message):
    Log.i(
        "FluxGramPython",
        f"[{plugin_id}] {message}",
    )


def create_runtime_bindings(plugin_id):
    return RuntimeBindings(
        events=EventBus(plugin_id),
        client=Client(),
        ui_thread=UIThread(),
        ui_services=UIServices(),
        call_ui=call_ui,
        log=_log,
    )

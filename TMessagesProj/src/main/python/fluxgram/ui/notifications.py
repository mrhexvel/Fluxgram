from org.telegram.ui import ChatActivity
from org.telegram.ui.Components import BulletinFactory

from .._telegram.bulletins import BulletinPresenter
from .._telegram.navigation import foreground_fragment
from .._telegram.notification_card import create_message_bulletin, update_message_bulletin
from ..client import _require_integer
from .navigation import Navigation


class Notifications:
    def __init__(self, ui):
        self._ui = ui
        self._navigation = Navigation(ui)
        self._presenter = BulletinPresenter(ui)
        ui._register_cleanup(self._presenter.dismiss)
        self._notification_key = None
        self._notification_handle = None
        ui._register_cleanup(self._clear_notification)

    def _clear_notification(self):
        self._notification_key = None
        self._notification_handle = None

    def show_message(self, event, *, respect_mute=True, show_in_current_chat=False, duration=5000):
        self._ui._require_ui()

        if not isinstance(respect_mute, bool) or not isinstance(show_in_current_chat, bool):
            raise TypeError("Notification options must be booleans")

        _require_integer("duration", duration, 1000, 10000)

        if event.message.outgoing:
            return False

        if respect_mute and event.chat.notifications.effective_muted is not False:
            return False

        fragment = foreground_fragment()

        if fragment is None:
            return False

        if (
            not show_in_current_chat
            and isinstance(fragment, ChatActivity)
            and int(fragment.getCurrentAccount()) == event.account
            and int(fragment.getDialogId()) == event.dialog_id
            and int(fragment.getTopicId()) == event.topic_id
        ):
            return False

        if not BulletinFactory.canShowBulletin(fragment):
            return False

        account, dialog_id = event.account, event.dialog_id
        topic_id, message_id = event.topic_id, event.message_id
        key = (account, dialog_id, topic_id)

        def on_click():
            self._navigation.open_chat(
                account=account,
                dialog_id=dialog_id,
                topic_id=topic_id,
                message_id=message_id,
            )

        if self._notification_key == key and self._notification_handle is not None:
            updated = self._notification_handle.update(
                lambda bulletin: update_message_bulletin(bulletin, event),
                on_click=on_click,
                duration=duration,
            )

            if updated:
                return True

        self._clear_notification()
        bulletin = create_message_bulletin(fragment, event)
        bulletin.setDuration(duration)
        handle = self._presenter.show(bulletin, on_click=on_click)

        if handle is None:
            return False

        self._notification_key = key
        self._notification_handle = handle

        return True

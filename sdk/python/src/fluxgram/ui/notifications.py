import traceback

from ._notification_queue import NotificationQueue, NotificationRequest
from .navigation import Navigation


class Notifications:
    def __init__(self, ui):
        from android.util import Log
        from org.telegram.ui import ChatActivity
        from org.telegram.ui.Components import BulletinFactory

        from fluxgram_runtime._telegram.bulletins import BulletinPresenter, has_visible_bulletin
        from fluxgram_runtime._telegram.navigation import foreground_fragment
        from fluxgram_runtime._telegram.notification_card import create_message_bulletin, update_message_bulletin
        from fluxgram_runtime._telegram.ui_lifecycle import UiLifecycle
        from fluxgram_runtime._ui_thread import post_ui
        from fluxgram_runtime.client import _require_integer

        self._ui = ui
        self._Log = Log
        self._ChatActivity = ChatActivity
        self._BulletinFactory = BulletinFactory
        self._has_visible_bulletin = has_visible_bulletin
        self._foreground_fragment = foreground_fragment
        self._create_message_bulletin = create_message_bulletin
        self._update_message_bulletin = update_message_bulletin
        self._post_ui = post_ui
        self._require_integer = _require_integer
        self._navigation = Navigation(ui)
        self._presenter = BulletinPresenter(ui)
        ui._register_cleanup(self._presenter.dismiss)

        self._queue = NotificationQueue(capacity=3)
        self._notification_key = None
        self._notification_handle = None
        self._current_request = None
        self._revision = 0
        self._advance_pending = False
        self._lifecycle = UiLifecycle(ui, self._on_background, self._on_chat_opened)
        ui._register_cleanup(self._close)

    def _clear_notification(self):
        self._notification_key = None
        self._notification_handle = None
        self._current_request = None

    def _reset(self):
        self._revision += 1
        self._advance_pending = False
        self._queue.clear()
        self._clear_notification()

    def _close(self):
        self._reset()
        self._lifecycle.close()

    def _on_background(self):
        self._reset()
        self._presenter.dismiss()

    def _on_chat_opened(self, key):
        self._queue.discard_open_chat(key)
        request = self._current_request

        if request is not None and request.key == key and not request.show_in_current_chat:
            self._presenter.dismiss()

    def _eligible(self, request, fragment):
        event = request.event

        if event.message.outgoing:
            return False

        if request.respect_mute and event.chat.notifications.effective_muted is not False:
            return False

        if (
            not request.show_in_current_chat
            and isinstance(fragment, self._ChatActivity)
            and (int(fragment.getCurrentAccount()), int(fragment.getDialogId()), int(fragment.getTopicId()))
            == request.key
        ):
            return False

        return True

    def _click(self, request):
        account, dialog_id, topic_id = request.key
        message_id = request.event.message_id

        def on_click():
            self._queue.discard_open_chat(request.key)
            self._navigation.open_chat(
                account=account,
                dialog_id=dialog_id,
                topic_id=topic_id,
                message_id=message_id,
            )

        return on_click

    def _show(self, request, fragment):
        bulletin = self._create_message_bulletin(fragment, request.event)
        bulletin.setDuration(request.duration)
        handle = self._presenter.show(
            bulletin,
            on_click=self._click(request),
            on_closed=self._on_closed,
        )

        if handle is None:
            return False

        self._notification_key = request.key
        self._notification_handle = handle
        self._current_request = request

        return True

    def _on_closed(self, handle):
        if self._notification_handle is not handle:
            return

        self._clear_notification()
        self._schedule_advance()

    def _schedule_advance(self):
        if self._advance_pending or not self._queue or not self._ui._active():
            return

        revision = self._revision
        self._advance_pending = True

        try:
            self._post_ui(lambda: self._advance(revision))
        except Exception:
            self._advance_pending = False
            raise

    def _advance(self, revision):
        if revision != self._revision:
            return

        self._advance_pending = False

        if not self._ui._active():
            self._reset()

            return

        if self._notification_handle is not None:
            return

        fragment = self._foreground_fragment()

        if fragment is None:
            self._on_background()

            return

        if not self._BulletinFactory.canShowBulletin(fragment) or self._has_visible_bulletin():
            self._queue.clear()

            return

        while self._queue:
            request = self._queue.pop()

            if not self._eligible(request, fragment):
                continue

            try:
                if self._show(request, fragment):
                    return
            except Exception:
                self._Log.e("FluxGramPython", traceback.format_exc())

    def show_message(self, event, *, respect_mute=True, show_in_current_chat=False, duration=5000):
        self._ui._require_ui()

        if not isinstance(respect_mute, bool) or not isinstance(show_in_current_chat, bool):
            raise TypeError("Notification options must be booleans")

        self._require_integer("duration", duration, 1000, 10000)
        request = NotificationRequest(event, duration, respect_mute, show_in_current_chat)
        fragment = self._foreground_fragment()

        if fragment is None:
            self._on_background()

            return False

        if not self._eligible(request, fragment) or not self._BulletinFactory.canShowBulletin(fragment):
            return False

        self._lifecycle.start()
        handle = self._notification_handle

        if handle is not None:
            if self._notification_key == request.key:
                updated = handle.update(
                    lambda bulletin: self._update_message_bulletin(bulletin, event),
                    on_click=self._click(request),
                    duration=duration,
                )

                if updated:
                    self._current_request = request

                    return True

            self._queue.offer(request)

            return True

        if self._advance_pending or self._queue:
            self._queue.offer(request)
            self._schedule_advance()

            return True

        return self._show(request, fragment)

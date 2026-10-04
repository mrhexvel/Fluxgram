import traceback

from android.os import Looper
from android.util import Log
from java import dynamic_proxy, jclass, jlong
from org.telegram.messenger import DialogObject, UserConfig
from org.telegram.ui import ChatActivity
from org.telegram.ui.Components import BulletinFactory

from ._telegram.navigation import foreground_fragment, open_chat
from ._telegram.notification_card import create_message_bulletin
from ._ui_thread import post_ui
from .client import _require_integer


OnClickListener = jclass("android.view.View$OnClickListener")
OnAttachStateChangeListener = jclass("android.view.View$OnAttachStateChangeListener")


class _Click(dynamic_proxy(OnClickListener)):
    def __init__(self, owner, callback):
        super().__init__()
        self.owner = owner
        self.callback = callback

    def onClick(self, view):
        owner, callback = self.owner, self.callback
        if owner is None or callback is None or not owner._active():
            return
        try:
            owner._require_ui()
            if foreground_fragment() is None:
                return
            owner._dismiss()
            callback()
        except Exception:
            Log.e("FluxGramPython", traceback.format_exc())


class _Detach(dynamic_proxy(OnAttachStateChangeListener)):
    def __init__(self, owner, bulletin, click):
        super().__init__()
        self.owner = owner
        self.bulletin = bulletin
        self.click = click

    def onViewAttachedToWindow(self, view):
        pass

    def onViewDetachedFromWindow(self, view):
        if self.owner is not None and self.owner._bulletin == self.bulletin:
            self.owner._bulletin = None
        if self.click is not None:
            self.click.owner = None
            self.click.callback = None
        view.setOnClickListener(None)
        view.removeOnAttachStateChangeListener(self)
        self.owner = self.bulletin = self.click = None


class UI:
    def __init__(self):
        self._closed = False
        self._is_active = lambda: False
        self._bulletin = None

    def _bind_runtime(self, host, generation):
        self._is_active = lambda: host.isGenerationActive(generation)

    def _active(self):
        return not self._closed and self._is_active()

    def _require_ui(self):
        if not self._active():
            raise RuntimeError("Plugin UI is inactive")
        if Looper.myLooper() != Looper.getMainLooper():
            raise RuntimeError("Plugin UI must be called on the UI thread")

    def _dismiss(self):
        bulletin = self._bulletin
        self._bulletin = None
        if bulletin is not None:
            bulletin.hide()

    def _close(self):
        self._closed = True
        self._is_active = lambda: False
        if Looper.myLooper() == Looper.getMainLooper():
            self._dismiss()
        else:
            post_ui(self._dismiss)

    def bulletin(self, title, text, *, on_click=None, duration=4000, top=True):
        """Show a native bulletin; return False when no foreground UI is available.

        A newer bulletin replaces the previous one. Callbacks run on the UI
        thread and must not perform blocking work.
        """
        self._require_ui()
        if not isinstance(title, str) or not isinstance(text, str):
            raise TypeError("title and text must be strings")
        if on_click is not None and not callable(on_click):
            raise TypeError("on_click must be callable or None")
        _require_integer("duration", duration, 1000, 10000)
        if not isinstance(top, bool):
            raise TypeError("top must be a boolean")

        fragment = foreground_fragment()
        if fragment is None or not BulletinFactory.canShowBulletin(fragment):
            return False

        self._dismiss()
        bulletin = BulletinFactory.of(fragment).createSimpleBulletin(title, text)
        bulletin.setDuration(duration)
        return self._show_bulletin(bulletin, on_click, top)

    def _show_bulletin(self, bulletin, on_click, top=True):
        layout = bulletin.getLayout()
        click = _Click(self, on_click) if on_click is not None else None
        detach = _Detach(self, bulletin, click)
        if click is not None:
            layout.setOnClickListener(click)
        layout.addOnAttachStateChangeListener(detach)
        self._bulletin = bulletin
        try:
            bulletin.show(top)
            if not bulletin.isShowing():
                detach.onViewDetachedFromWindow(layout)
                return False
        except Exception:
            self._dismiss()
            detach.onViewDetachedFromWindow(layout)
            raise
        return True

    def open_chat(self, *, account, dialog_id, topic_id=0, message_id=0):
        """Open a chat using the app's navigation, potentially loading its topic."""
        self._require_ui()
        _require_integer("account", account, 0, int(UserConfig.MAX_ACCOUNT_COUNT) - 1)
        _require_integer("dialog_id", dialog_id, -(2**63), 2**63 - 1)
        _require_integer("topic_id", topic_id, 0, 2**63 - 1)
        _require_integer("message_id", message_id, -(2**31), 2**31 - 1)
        if dialog_id == 0 or DialogObject.isFolderDialogId(jlong(dialog_id)):
            raise ValueError("dialog_id must identify a chat")
        if not UserConfig.getInstance(account).isClientActivated():
            return False
        return open_chat(account, dialog_id, topic_id, message_id)

    def notify_message(self, event, *, respect_mute=True, show_in_current_chat=False):
        """Show an incoming-message preview with tap-to-open navigation.

        With respect_mute=True, unknown mute state also suppresses the preview.
        To replace the native notification, return this result from a
        notification.received callback. Other callers only display a card.
        """
        self._require_ui()
        if not isinstance(respect_mute, bool) or not isinstance(show_in_current_chat, bool):
            raise TypeError("Notification options must be booleans")
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

        account, dialog_id = event.account, event.dialog_id
        topic_id, message_id = event.topic_id, event.message_id
        if not BulletinFactory.canShowBulletin(fragment):
            return False
        self._dismiss()
        return self._show_bulletin(
            create_message_bulletin(fragment, event),
            on_click=lambda: self.open_chat(
                account=account,
                dialog_id=dialog_id,
                topic_id=topic_id,
                message_id=message_id,
            ),
        )

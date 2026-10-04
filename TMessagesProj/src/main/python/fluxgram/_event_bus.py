import traceback
import time
from collections import OrderedDict

from java import dynamic_proxy, jclass
from android.util import Log
from org.telegram.messenger import NotificationCenter, UserConfig
from org.telegram.ui import ChatActivity
from org.telegram.messenger.plugins import PluginNotifications

from ._context import build_message_event
from ._ui_thread import post_ui


Delegate = jclass(
    "org.telegram.messenger.NotificationCenter$NotificationCenterDelegate"
)


class _MessageObserver(dynamic_proxy(Delegate)):
    def __init__(self, subscription):
        super().__init__()
        self.subscription = subscription

    def didReceivedNotification(self, event_id, account, args):
        subscription = self.subscription

        if subscription is None:
            return

        try:
            subscription.deliver(event_id, account, args)
        except Exception:
            Log.e(
                "FluxGramPython",
                f"[{subscription.plugin_id}] "
                f"Message callback failed\n{traceback.format_exc()}",
            )


NotificationListener = jclass(
    "org.telegram.messenger.plugins.PluginNotifications$Listener"
)


class _NotificationObserver(dynamic_proxy(NotificationListener)):
    def __init__(self, subscription):
        super().__init__()
        self.subscription = subscription

    def isActive(self):
        sub = self.subscription

        return sub is not None and not sub.closed and sub.is_active()

    def onMessage(self, account, message):
        sub = self.subscription

        if sub is None:
            return False

        return sub.deliver_message(account, message)


class _MessageSubscription:
    def __init__(self, plugin_id, callback, is_active, notification_mode=False):
        self.plugin_id = plugin_id
        self.callback = callback
        self.is_active = is_active

        self.closed = False
        self.notification_mode = notification_mode
        self.notification_observer = None
        self.observer = None
        self.centers = []
        self.seen = OrderedDict()

    def attach(self):
        if self.closed or not self.is_active():
            return

        self.observer = _MessageObserver(self)

        try:
            for account in range(UserConfig.MAX_ACCOUNT_COUNT):
                center = NotificationCenter.getInstance(account)
                center.addObserver(
                    self.observer,
                    NotificationCenter.didReceiveNewMessages,
                )
                self.centers.append(center)

            if self.notification_mode:
                self.notification_observer = _NotificationObserver(self)
                PluginNotifications.addListener(self.notification_observer)
        except Exception:
            self.closed = True
            self.detach()
            raise

    def deliver(self, event_id, account, args):
        if self.closed or not self.is_active():
            return

        if event_id != NotificationCenter.didReceiveNewMessages:
            return


        if len(args) < 4:
            return

        if bool(args[2]):
            return

        if int(args[3]) != ChatActivity.MODE_DEFAULT:
            return

        messages = args[1]

        if messages is None:
            return

        for index in range(messages.size()):
            if self.closed or not self.is_active():
                return

            self.deliver_message(account, messages.get(index))

    def deliver_message(self, account, message):
        if self.closed or not self.is_active():
            return False

        if message is None or message.isOut():
            return False

        if self.notification_mode and not PluginNotifications.isEligible(message):
            return False

        key = (int(account), int(message.getDialogId()), int(message.getId()))

        if key in self.seen:
            timestamp, shown = self.seen[key]

            return shown and time.monotonic() - timestamp < 120

        self.seen[key] = (time.monotonic(), False)

        if len(self.seen) > 512:
            self.seen.popitem(last=False)

        try:
            event = build_message_event(account, message)
            shown = self.callback(event) is True
            self.seen[key] = (time.monotonic(), shown)

            return shown
        except Exception:
            Log.e(
                "FluxGramPython",
                f"[{self.plugin_id}] message callback failed\n{traceback.format_exc()}",
            )

            return False

    def unsubscribe(self):
        if self.closed:
            return

        self.closed = True
        post_ui(self.detach)

    def detach(self):
        errors = []

        if self.notification_observer is not None:
            self.notification_observer.subscription = None

            try:
                PluginNotifications.removeListener(self.notification_observer)
                self.notification_observer = None
            except BaseException as error:
                errors.append(error)

        observer = self.observer

        if observer is not None:
            observer.subscription = None

        for center in tuple(self.centers):
            try:
                center.removeObserver(
                    observer,
                    NotificationCenter.didReceiveNewMessages,
                )
                self.centers.remove(center)
            except BaseException as error:
                errors.append(error)

        if errors:
            raise RuntimeError("Could not detach plugin observers") from errors[0]

        self.seen.clear()
        self.observer = None
        self.callback = None
        self.is_active = lambda: False


class EventBus:
    def __init__(self, plugin_id):
        self.plugin_id = plugin_id
        self._subscriptions = []
        self._closed = False
        self._is_active = lambda: False

    def bind_runtime(self, host, generation):
        self._is_active = lambda: host.isGenerationActive(generation)

    def subscribe(self, name, callback, *, thread="ui"):


        if self._closed:
            raise RuntimeError("Plugin event bus is closed")

        if name not in ("message.received", "notification.received"):
            raise ValueError(f"Unsupported event: {name}")

        if thread != "ui":
            raise ValueError("This event currently supports only thread='ui'")

        if not callable(callback):
            raise TypeError("callback must be callable")

        subscription = _MessageSubscription(
            self.plugin_id,
            callback,
            self._is_active,
            notification_mode=name == "notification.received",
        )

        self._subscriptions.append(subscription)
        post_ui(subscription.attach)

        return subscription

    def close(self):
        self._closed = True
        self._is_active = lambda: False
        errors = []

        for subscription in tuple(self._subscriptions):
            subscription.closed = True

            try:
                subscription.detach()
                self._subscriptions.remove(subscription)
            except BaseException as error:
                errors.append(error)

        if errors:
            raise RuntimeError("Could not close plugin subscriptions") from errors[0]

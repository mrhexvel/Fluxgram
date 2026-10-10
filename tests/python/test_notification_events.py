import importlib.util
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import patch


SOURCE = (
    Path(__file__).resolve().parents[2]
    / "TMessagesProj/src/main/python/fluxgram_runtime/_event_bus.py"
)


class Message:
    def __init__(self, dialog=10, mid=20, outgoing=False, eligible=True):
        self.dialog, self.mid = dialog, mid
        self.outgoing, self.eligible = outgoing, eligible

    def getDialogId(self):
        return self.dialog

    def getId(self):
        return self.mid

    def isOut(self):
        return self.outgoing


class JavaList(list):
    def size(self):
        return len(self)

    def get(self, index):
        return self[index]


class NotificationEventsTest(unittest.TestCase):
    def setUp(self):
        self.pending = []
        self.listeners = []
        self.errors = []
        self.active = True
        self.calls = []
        self.result = True
        modules = {}

        def module(name, **values):
            m = types.ModuleType(name)
            m.__path__ = []
            m.__dict__.update(values)
            modules[name] = m
            return m

        for name in ("android", "org", "org.telegram", "fluxgram_runtime"):
            module(name)
        module(
            "java", dynamic_proxy=lambda interface: object, jclass=lambda name: object
        )
        module(
            "android.util",
            Log=types.SimpleNamespace(e=lambda *args: self.errors.append(args)),
        )
        center = types.SimpleNamespace(
            addObserver=lambda *args: None, removeObserver=lambda *args: None
        )
        module(
            "org.telegram.messenger",
            UserConfig=types.SimpleNamespace(MAX_ACCOUNT_COUNT=2),
            NotificationCenter=types.SimpleNamespace(
                didReceiveNewMessages=1, getInstance=lambda account: center
            ),
        )
        module("org.telegram.ui", ChatActivity=types.SimpleNamespace(MODE_DEFAULT=0))
        module(
            "org.telegram.messenger.plugins",
            PluginNotifications=types.SimpleNamespace(
                addListener=self.listeners.append,
                removeListener=self.listeners.remove,
                isEligible=lambda message: message.eligible,
            ),
        )
        module(
            "fluxgram_runtime._context",
            build_message_event=lambda account, message: (account, message),
        )
        module("fluxgram_runtime._ui_thread", post_ui=self.pending.append)
        self.module_patch = patch.dict(sys.modules, modules)
        self.module_patch.start()
        self.addCleanup(self.module_patch.stop)
        spec = importlib.util.spec_from_file_location("fluxgram_runtime._event_bus", SOURCE)
        self.sdk = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.sdk)
        self.sub = self.sdk._MessageSubscription(
            "test", self.callback, lambda: self.active, True
        )
        self.sub.attach()
        self.native = self.listeners[0]

    def callback(self, event):
        self.calls.append(event)
        if isinstance(self.result, Exception):
            raise self.result
        return self.result

    def receive(self, message, account=0, scheduled=False, mode=0):
        self.sub.deliver(
            1, account, [message.dialog, JavaList([message]), scheduled, mode]
        )

    def test_message_event_first_confirms_without_second_card(self):
        message = Message()
        self.receive(message)
        self.assertTrue(self.native.onMessage(0, message))
        self.assertEqual(len(self.calls), 1)

    def test_native_first_does_not_duplicate_later_message_event(self):
        message = Message()
        self.assertTrue(self.native.onMessage(0, message))
        self.receive(message)
        self.assertEqual(len(self.calls), 1)

    def test_no_confirmation_means_native_fallback(self):
        for result in (False, None, 1, "yes"):
            with self.subTest(result=result):
                self.sub.seen.clear()
                self.result = result
                self.assertFalse(self.native.onMessage(0, Message()))

    def test_callback_failure_and_repeat_keep_native_path(self):
        self.result = RuntimeError("failed to display")
        self.assertFalse(self.native.onMessage(0, Message()))
        self.assertFalse(self.native.onMessage(0, Message()))
        self.assertEqual(len(self.errors), 1)

    def test_accounts_and_chats_do_not_share_receipts(self):
        for account, dialog in ((0, 10), (1, 10), (0, 11)):
            self.assertTrue(self.native.onMessage(account, Message(dialog=dialog)))
        self.assertEqual(len(self.calls), 3)

    def test_disable_invalidates_a_successful_receipt_immediately(self):
        self.assertTrue(self.native.onMessage(0, Message()))
        self.active = False
        self.assertFalse(self.native.isActive())
        self.assertFalse(self.native.onMessage(0, Message()))

    def test_unsubscribe_stops_before_queued_detach(self):
        self.sub.unsubscribe()
        self.assertFalse(self.native.isActive())
        self.assertFalse(self.native.onMessage(0, Message()))
        self.pending.pop()()
        self.assertEqual(self.listeners, [])
        self.assertIsNone(self.native.subscription)

    def test_old_receipts_do_not_suppress_or_redisplay(self):
        self.assertTrue(self.native.onMessage(0, Message()))
        self.sub.seen[(0, 10, 20)] = (self.sdk.time.monotonic() - 121, True)
        self.assertFalse(self.native.onMessage(0, Message()))
        self.assertEqual(len(self.calls), 1)

    def test_service_outgoing_and_scheduled_events_are_not_shown(self):
        self.assertFalse(self.native.onMessage(0, Message(eligible=False)))
        self.assertFalse(self.native.onMessage(0, Message(outgoing=True)))
        self.receive(Message(), scheduled=True)
        self.receive(Message(), mode=1)
        self.assertEqual(self.calls, [])

    def test_receipt_cache_is_bounded(self):
        for mid in range(600):
            self.native.onMessage(0, Message(mid=mid))
        self.assertEqual(len(self.sub.seen), 512)

    def test_regular_event_subscription_does_not_register_native_listener(self):
        normal = self.sdk._MessageSubscription("normal", self.callback, lambda: True)
        normal.attach()
        self.assertEqual(len(self.listeners), 1)
        normal.detach()

    def test_close_retries_only_failed_subscriptions(self):
        calls = []
        failed = [True]

        def detach_bad():
            calls.append("bad")
            if failed[0]:
                raise RuntimeError("detach failed")

        bad = types.SimpleNamespace(closed=False, detach=detach_bad)
        good = types.SimpleNamespace(closed=False, detach=lambda: calls.append("good"))
        bus = self.sdk.EventBus("test")
        bus._subscriptions = [bad, good]

        with self.assertRaises(RuntimeError):
            bus.close()
        self.assertEqual(calls, ["bad", "good"])
        self.assertEqual(bus._subscriptions, [bad])
        self.assertTrue(bad.closed)
        self.assertTrue(good.closed)

        failed[0] = False
        bus.close()
        self.assertEqual(calls, ["bad", "good", "bad"])
        self.assertEqual(bus._subscriptions, [])

    def test_detach_retains_failed_centers_for_retry(self):
        calls = []

        def fail(*args):
            calls.append("bad")
            raise RuntimeError("center failed")

        bad = types.SimpleNamespace(removeObserver=fail)
        good = types.SimpleNamespace(removeObserver=lambda *args: calls.append("good"))
        self.sub.centers = [bad, good]
        with self.assertRaises(RuntimeError):
            self.sub.detach()
        self.assertEqual(calls, ["bad", "good"])
        self.assertEqual(self.sub.centers, [bad])
        bad.removeObserver = lambda *args: None
        self.sub.detach()
        self.assertEqual(self.sub.centers, [])


if __name__ == "__main__":
    unittest.main()

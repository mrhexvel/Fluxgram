import importlib.util
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import Mock, patch


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'sdk/python/src/fluxgram/ui/__init__.py'
RUNTIME = ROOT / 'TMessagesProj/src/main/python/fluxgram_runtime'


class Bulletin:
    visible = None

    def __init__(self):
        self.layout = Mock()
        self.showing = False
        self.show_count = 0
        self.hide_count = 0
        self.refreshes = []

    def getLayout(self):
        return self.layout

    def setDuration(self, duration):
        self.duration = duration

    def restartTimeout(self, duration):
        self.duration = duration
        self.refreshes.append(duration)

    def show(self, top):
        if Bulletin.visible is not None and Bulletin.visible.isShowing():
            Bulletin.visible.hide()

        Bulletin.visible = self
        self.showing = True
        self.show_count += 1

    def hide(self):
        self.showing = False
        self.hide_count += 1

    def isShowing(self):
        return self.showing

    @staticmethod
    def getVisibleBulletin():
        if Bulletin.visible is not None and Bulletin.visible.isShowing():
            return Bulletin.visible

        return None

    def finish_hide(self):
        self.showing = False
        detach = self.layout.addOnAttachStateChangeListener.call_args.args[0]
        detach.onViewDetachedFromWindow(self.layout)


class ChatActivity:
    def getCurrentAccount(self):
        return 0

    def getDialogId(self):
        return 10

    def getTopicId(self):
        return 0


class NotificationRefreshTest(unittest.TestCase):
    def setUp(self):
        Bulletin.visible = None
        modules = {}

        def module(name, **values):
            result = types.ModuleType(name)
            result.__path__ = []
            result.__dict__.update(values)
            modules[name] = result

        for name in (
            'android',
            'org',
            'org.telegram',
            'fluxgram',
            'fluxgram_runtime',
            'fluxgram_runtime._telegram',
        ):
            module(name)

        self.fragment = object()
        self.pending = []
        self.lifecycle_listeners = []
        self.created = []
        self.render = Mock()
        self.fail_dialogs = set()

        def create(fragment, event):
            if event.dialog_id in self.fail_dialogs:
                raise RuntimeError('card creation failed')

            bulletin = Bulletin()
            bulletin.event = event
            self.created.append(bulletin)
            return bulletin

        module('java', dynamic_proxy=lambda target: object, jclass=lambda name: object, jlong=int)
        module('fluxgram.events', MessageReceivedEvent=object)
        module('android.os', Looper=types.SimpleNamespace(myLooper=lambda: 1, getMainLooper=lambda: 1))
        module('android.util', Log=Mock())
        module('org.telegram.messenger', DialogObject=Mock(), UserConfig=Mock())
        module('org.telegram.ui', ChatActivity=ChatActivity)
        module('org.telegram.ui.Components', Bulletin=Bulletin, BulletinFactory=types.SimpleNamespace(
            canShowBulletin=lambda fragment: True,
            of=lambda fragment: types.SimpleNamespace(createSimpleBulletin=lambda *args: Bulletin()),
        ))
        module('fluxgram_runtime._telegram.navigation', foreground_fragment=lambda: self.fragment, open_chat=Mock())
        module('fluxgram_runtime._telegram.notification_card', create_message_bulletin=create, update_message_bulletin=self.render)
        module('fluxgram_runtime._ui_thread', post_ui=self.pending.append)
        module('fluxgram_runtime.client', _require_integer=lambda *args: None)
        module('org.telegram.messenger.plugins', PluginUiLifecycle=types.SimpleNamespace(
            addListener=self.lifecycle_listeners.append,
            removeListener=self.lifecycle_listeners.remove,
        ))

        modules_patch = patch.dict(sys.modules, modules)
        modules_patch.start()
        self.addCleanup(modules_patch.stop)

        for name, path in (
            ('fluxgram_runtime._telegram.bulletins', RUNTIME / '_telegram/bulletins.py'),
            ('fluxgram_runtime._telegram.ui_lifecycle', RUNTIME / '_telegram/ui_lifecycle.py'),
            ('fluxgram.ui', SOURCE),
            (
                'fluxgram_runtime._telegram.notifications_service',
                RUNTIME / '_telegram/notifications_service.py',
            ),
        ):
            spec = importlib.util.spec_from_file_location(name, path)
            loaded = importlib.util.module_from_spec(spec)
            sys.modules[name] = loaded
            spec.loader.exec_module(loaded)

        notifications_service = loaded
        self.sdk = sys.modules['fluxgram.ui']

        class Services:
            def create_bulletin_presenter(inner, ui):
                presenter = sys.modules['fluxgram_runtime._telegram.bulletins']

                return presenter.BulletinPresenter(ui)

            def create_notifications(inner, ui):
                return notifications_service.NotificationsService(ui)

            def open_chat(inner, account, dialog_id, topic_id, message_id):
                navigation = sys.modules['fluxgram_runtime._telegram.navigation']

                return navigation.open_chat(account, dialog_id, topic_id, message_id)

        self.ui_services = Services()

        self.ui_runtime = types.SimpleNamespace(
            is_ui_thread=lambda: True,
            post=self.pending.append,
        )
        self.ui = self.sdk.UI(self.ui_runtime, self.ui_services)
        self.ui._is_active = lambda: True
        self.notifications = self.sdk.Notifications(self.ui)._delegate
        self.notifications._navigation.open_chat = Mock()

    def event(self, account=0, dialog=10, topic=0, mid=1, muted=False, outgoing=False):
        return types.SimpleNamespace(
            account=account, dialog_id=dialog, topic_id=topic, message_id=mid,
            message=types.SimpleNamespace(outgoing=outgoing),
            chat=types.SimpleNamespace(notifications=types.SimpleNamespace(effective_muted=muted)),
        )

    def test_same_chat_refreshes_without_recreating_and_opens_latest_message(self):
        self.assertTrue(self.notifications.show_message(self.event()))
        bulletin = self.created[0]
        click = self.notifications._presenter.current.click
        latest = self.event(mid=2)

        self.assertTrue(self.notifications.show_message(latest, duration=8000))
        self.assertEqual(len(self.created), 1)
        self.assertEqual(bulletin.show_count, 1)
        self.assertEqual(bulletin.hide_count, 0)
        self.assertEqual(bulletin.refreshes, [8000])
        self.render.assert_called_once_with(bulletin, latest)

        click.onClick(bulletin.layout)
        click.onClick(bulletin.layout)
        self.notifications._navigation.open_chat.assert_called_once_with(account=0, dialog_id=10, topic_id=0, message_id=2)

    def drain(self):
        while self.pending:
            self.pending.pop(0)()

    def finish(self, bulletin):
        bulletin.finish_hide()
        self.drain()

    def test_account_chat_and_topic_are_separate(self):
        self.notifications.show_message(self.event())
        old = self.created[0]

        for options in ({'account': 1}, {'dialog': 11}, {'topic': 7}):
            self.notifications.show_message(self.event(**options))

        self.assertEqual(len(self.created), 1)
        self.assertEqual(old.hide_count, 0)

        for expected in ((1, 10, 0), (0, 11, 0), (0, 10, 7)):
            self.finish(self.created[-1])
            self.assertEqual(self.notifications._notification_key, expected)

    def test_old_detach_does_not_clear_replacement(self):
        self.notifications.show_message(self.event())
        old = self.created[0]
        detach = old.layout.addOnAttachStateChangeListener.call_args.args[0]
        self.notifications.show_message(self.event(dialog=11))
        self.finish(old)
        current = self.notifications._presenter.current.bulletin
        detach.onViewDetachedFromWindow(old.layout)

        self.notifications.show_message(self.event(dialog=11, mid=2))
        self.assertIs(self.notifications._presenter.current.bulletin, current)
        self.assertEqual(current.refreshes, [5000])

    def test_hiding_card_is_not_revived(self):
        self.notifications.show_message(self.event())
        old = self.created[0]
        old.hide()
        self.notifications.show_message(self.event(mid=2))
        self.assertEqual(len(self.created), 1)
        self.assertEqual(old.refreshes, [])
        self.finish(old)
        self.assertEqual(len(self.created), 2)
        self.assertEqual(self.created[-1].event.message_id, 2)

    def test_filtered_events_do_not_refresh(self):
        self.notifications.show_message(self.event())
        bulletin = self.notifications._presenter.current.bulletin

        for options in ({'muted': True}, {'outgoing': True}):
            self.assertFalse(self.notifications.show_message(self.event(**options)))

        self.fragment = ChatActivity()
        self.assertFalse(self.notifications.show_message(self.event()))
        self.assertEqual(bulletin.refreshes, [])
        self.render.assert_not_called()

    def test_closing_clears_state_and_prevents_stale_click(self):
        self.notifications.show_message(self.event())
        click = self.notifications._presenter.current.click
        self.ui._close()
        click.onClick(None)

        self.assertIsNone(self.notifications._presenter.current)
        self.assertIsNone(self.notifications._notification_handle)
        self.assertIsNone(self.notifications._notification_key)
        self.notifications._navigation.open_chat.assert_not_called()

    def test_generic_bulletin_is_not_reused_as_notification(self):
        self.notifications._presenter.show(Bulletin(), on_click=lambda: None)
        old = self.notifications._presenter.current.bulletin
        self.notifications.show_message(self.event())

        self.assertIsNot(self.notifications._presenter.current.bulletin, old)
        self.assertEqual(old.hide_count, 1)
        self.render.assert_not_called()

    def test_context_has_no_feature_methods_or_services(self):
        self.notifications.show_message(self.event())
        bulletin = self.created[0]
        self.notifications.show_message(self.event(mid=2))

        self.assertEqual(len(self.created), 1)
        self.assertEqual(bulletin.refreshes, [5000])
        self.assertIsInstance(self.ui, self.sdk.UI)
        self.assertFalse(hasattr(self.ui, 'notifications'))
        self.assertFalse(hasattr(self.ui, 'bulletin'))
        self.assertFalse(hasattr(self.ui, 'notify_message'))
        self.assertFalse(hasattr(self.ui, 'open_chat'))

    def test_detach_releases_java_view_and_callback(self):
        self.notifications.show_message(self.event())
        handle = self.notifications._presenter.current
        bulletin = handle.bulletin
        click = handle.click
        detach = bulletin.layout.addOnAttachStateChangeListener.call_args.args[0]
        detach.onViewDetachedFromWindow(bulletin.layout)

        self.assertIsNone(handle.bulletin)
        self.assertIsNone(handle.on_click)
        self.assertIsNone(handle.presenter)
        self.assertIsNone(click.handle)
        self.assertIsNone(self.notifications._presenter.current)

        self.notifications.show_message(self.event(mid=2))
        self.assertEqual(len(self.created), 2)

    def test_show_failure_cleans_up_and_allows_next_notification(self):
        self.notifications.show_message(self.event())
        failing = Bulletin()
        failing.show = Mock(side_effect=RuntimeError('show failed'))

        with self.assertRaisesRegex(RuntimeError, 'show failed'):
            self.notifications._presenter.show(failing, on_click=lambda: None)

        self.assertIsNone(self.notifications._presenter.current)
        failing.layout.setOnClickListener.assert_called_with(None)
        self.finish(self.created[0])
        self.notifications.show_message(self.event(mid=2))
        self.assertEqual(len(self.created), 2)

    def test_failed_dismiss_can_be_retried_during_cleanup(self):
        self.notifications.show_message(self.event())
        bulletin = self.notifications._presenter.current.bulletin
        original_hide = bulletin.hide
        bulletin.hide = Mock(side_effect=RuntimeError('hide failed'))

        with self.assertRaisesRegex(RuntimeError, 'cleanup failed'):
            self.ui._close()

        self.assertIs(self.notifications._presenter.current.bulletin, bulletin)
        bulletin.hide = original_hide
        self.ui._close()
        self.assertIsNone(self.notifications._presenter.current)

    def test_wrappers_share_lifecycle_without_inheriting_context(self):
        bulletins = self.sdk.Bulletins(self.ui)
        navigation = self.sdk.Navigation(self.ui)
        self.assertFalse(isinstance(bulletins, self.sdk.UI))
        self.assertFalse(isinstance(navigation, self.sdk.UI))
        self.assertFalse(isinstance(self.notifications, self.sdk.UI))
        self.notifications.show_message(self.event())
        old = self.created[-1]
        self.assertTrue(bulletins.show('Title', 'Text'))
        self.assertFalse(old.isShowing())
        current = bulletins._presenter.current.bulletin

        self.ui._close()
        self.assertFalse(current.isShowing())
        self.assertEqual(self.ui._cleanup_callbacks, [])

        with self.assertRaisesRegex(RuntimeError, 'inactive'):
            self.notifications.show_message(self.event())

        with self.assertRaisesRegex(RuntimeError, 'inactive'):
            bulletins.show('Title', 'Text')

        with self.assertRaisesRegex(RuntimeError, 'inactive'):
            navigation.open_chat(account=0, dialog_id=10)

    def test_unused_context_allocates_no_feature_resources(self):
        context = self.sdk.UI(self.ui_runtime, self.ui_services)
        self.assertEqual(context._cleanup_callbacks, [])
        self.assertFalse(hasattr(context, '_presenter'))
        self.assertFalse(hasattr(context, '_notification_handle'))

    def test_separate_context_shutdown_does_not_close_another_plugin(self):
        other_ui = self.sdk.UI(self.ui_runtime, self.ui_services)
        other_ui._is_active = lambda: True
        other = self.sdk.Notifications(other_ui)._delegate
        self.notifications.show_message(self.event())
        other.show_message(self.event(dialog=11))
        current = other._presenter.current.bulletin
        self.ui._close()

        self.assertTrue(current.isShowing())
        self.assertTrue(other_ui._active())
        other_ui._close()
        self.assertFalse(current.isShowing())

    def test_pending_update_keeps_position_and_latest_contents(self):
        self.notifications.show_message(self.event())
        self.notifications.show_message(self.event(dialog=11))
        self.notifications.show_message(self.event(dialog=12))
        self.notifications.show_message(self.event(dialog=11, mid=99), duration=8000)
        self.finish(self.created[0])

        current = self.created[-1]
        self.assertEqual(current.event.dialog_id, 11)
        self.assertEqual(current.event.message_id, 99)
        self.assertEqual(current.duration, 8000)
        self.finish(current)
        self.assertEqual(self.created[-1].event.dialog_id, 12)

    def test_overflow_discards_oldest_pending_without_creating_views(self):
        self.notifications.show_message(self.event())

        for dialog in (11, 12, 13, 14):
            self.notifications.show_message(self.event(dialog=dialog))

        self.assertEqual(len(self.created), 1)

        for dialog in (12, 13, 14):
            self.finish(self.created[-1])
            self.assertEqual(self.created[-1].event.dialog_id, dialog)

        self.finish(self.created[-1])
        self.assertEqual(len(self.created), 4)
        self.assertIsNone(self.notifications._notification_handle)

    def test_background_invalidates_already_posted_advance(self):
        self.notifications.show_message(self.event())
        self.notifications.show_message(self.event(dialog=11))
        self.created[0].finish_hide()
        self.assertEqual(len(self.pending), 1)
        self.lifecycle_listeners[0].onBackground()
        self.drain()
        self.assertEqual(len(self.created), 1)
        self.assertFalse(self.notifications._queue)

    def test_opened_chat_is_removed_from_pending(self):
        self.notifications.show_message(self.event())
        self.notifications.show_message(self.event(dialog=11))
        self.notifications.show_message(self.event(dialog=12))
        self.lifecycle_listeners[0].onChatOpened(0, 11, 0)
        self.finish(self.created[0])
        self.assertEqual(self.created[-1].event.dialog_id, 12)

    def test_opening_current_chat_dismisses_then_advances(self):
        self.notifications.show_message(self.event())
        current = self.created[0]
        self.notifications.show_message(self.event(dialog=11))
        self.lifecycle_listeners[0].onChatOpened(0, 10, 0)
        self.assertEqual(current.hide_count, 1)
        self.assertEqual(len(self.created), 1)
        self.finish(current)
        self.assertEqual(self.created[-1].event.dialog_id, 11)

    def test_current_chat_is_rechecked_before_presenting(self):
        self.notifications.show_message(self.event(dialog=11))
        self.notifications.show_message(self.event())
        self.fragment = ChatActivity()
        self.finish(self.created[0])
        self.assertEqual(len(self.created), 1)

    def test_explicit_current_chat_option_is_preserved(self):
        self.notifications.show_message(self.event(dialog=11))
        self.notifications.show_message(self.event(), show_in_current_chat=True)
        self.fragment = ChatActivity()
        self.lifecycle_listeners[0].onChatOpened(0, 10, 0)
        self.finish(self.created[0])
        self.assertEqual(self.created[-1].event.dialog_id, 10)

    def test_disable_cancels_queue_and_unregisters_lifecycle(self):
        self.notifications.show_message(self.event())
        self.notifications.show_message(self.event(dialog=11))
        listener = self.lifecycle_listeners[0]
        self.ui._close()
        self.finish(self.created[0])
        listener.onChatOpened(0, 12, 0)
        self.assertEqual(len(self.created), 1)
        self.assertEqual(self.lifecycle_listeners, [])
        self.assertIsNone(listener.owner)
        self.assertFalse(self.notifications._queue)

    def test_another_bulletin_is_not_replaced_by_queued_notification(self):
        self.notifications.show_message(self.event())
        self.notifications.show_message(self.event(dialog=11))
        bulletins = self.sdk.Bulletins(self.ui)
        bulletins.show('Title', 'Text')
        current = bulletins._presenter.current.bulletin
        self.finish(self.created[0])
        self.assertTrue(current.isShowing())
        self.assertEqual(len(self.created), 1)
        self.assertFalse(self.notifications._queue)

    def test_missing_foreground_drops_queue(self):
        self.notifications.show_message(self.event())
        self.notifications.show_message(self.event(dialog=11))
        self.fragment = None
        self.finish(self.created[0])
        self.assertEqual(len(self.created), 1)
        self.assertFalse(self.notifications._queue)

    def test_failed_queued_card_does_not_block_following_card(self):
        self.notifications.show_message(self.event())
        self.notifications.show_message(self.event(dialog=11))
        self.notifications.show_message(self.event(dialog=12))
        self.fail_dialogs.add(11)
        self.finish(self.created[0])
        self.assertEqual(self.created[-1].event.dialog_id, 12)
        self.assertEqual(len(self.created), 2)

    def test_stale_advance_cannot_modify_new_foreground_session(self):
        self.notifications.show_message(self.event())
        self.notifications.show_message(self.event(dialog=11))
        self.created[0].finish_hide()
        self.lifecycle_listeners[0].onBackground()
        self.notifications.show_message(self.event(dialog=12))
        self.notifications.show_message(self.event(dialog=13))
        self.drain()

        self.assertEqual(self.created[-1].event.dialog_id, 12)
        self.assertEqual(len(self.lifecycle_listeners), 1)
        self.finish(self.created[-1])
        self.assertEqual(self.created[-1].event.dialog_id, 13)

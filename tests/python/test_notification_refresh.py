import importlib.util
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import Mock, patch


SOURCE = Path(__file__).resolve().parents[2] / 'TMessagesProj/src/main/python/fluxgram/ui/__init__.py'


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

        for name in ('android', 'org', 'org.telegram', 'fluxgram', 'fluxgram._telegram'):
            module(name)

        self.fragment = object()
        self.created = []
        self.render = Mock()

        def create(fragment, event):
            bulletin = Bulletin()
            self.created.append(bulletin)
            return bulletin

        module('java', dynamic_proxy=lambda target: object, jclass=lambda name: object, jlong=int)
        module('android.os', Looper=types.SimpleNamespace(myLooper=lambda: 1, getMainLooper=lambda: 1))
        module('android.util', Log=Mock())
        module('org.telegram.messenger', DialogObject=Mock(), UserConfig=Mock())
        module('org.telegram.ui', ChatActivity=ChatActivity)
        module('org.telegram.ui.Components', BulletinFactory=types.SimpleNamespace(
            canShowBulletin=lambda fragment: True,
            of=lambda fragment: types.SimpleNamespace(createSimpleBulletin=lambda *args: Bulletin()),
        ))
        module('fluxgram._telegram.navigation', foreground_fragment=lambda: self.fragment, open_chat=Mock())
        module('fluxgram._telegram.notification_card', create_message_bulletin=create, update_message_bulletin=self.render)
        module('fluxgram._ui_thread', post_ui=lambda action: action())
        module('fluxgram.client', _require_integer=lambda *args: None)

        with patch.dict(sys.modules, modules):
            for name, path in (
                ('fluxgram._telegram.bulletins', SOURCE.parent.parent / '_telegram/bulletins.py'),
                ('fluxgram.ui', SOURCE),
            ):
                spec = importlib.util.spec_from_file_location(name, path)
                loaded = importlib.util.module_from_spec(spec)
                sys.modules[name] = loaded
                spec.loader.exec_module(loaded)

            self.sdk = loaded

        self.ui = self.sdk.UI()
        self.ui._is_active = lambda: True
        self.notifications = self.sdk.Notifications(self.ui)
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

    def test_account_chat_and_topic_are_separate(self):
        for options in ({'account': 1}, {'dialog': 11}, {'topic': 7}):
            with self.subTest(options=options):
                self.notifications._presenter.dismiss()
                self.notifications.show_message(self.event())
                old = self.notifications._presenter.current.bulletin
                self.notifications.show_message(self.event(**options))
                self.assertIsNot(self.notifications._presenter.current.bulletin, old)
                self.assertEqual(old.hide_count, 1)

    def test_old_detach_does_not_clear_replacement(self):
        self.notifications.show_message(self.event())
        old = self.notifications._presenter.current.bulletin
        detach = old.layout.addOnAttachStateChangeListener.call_args.args[0]
        self.notifications.show_message(self.event(dialog=11))
        current = self.notifications._presenter.current.bulletin
        detach.onViewDetachedFromWindow(old.layout)

        self.notifications.show_message(self.event(dialog=11, mid=2))
        self.assertIs(self.notifications._presenter.current.bulletin, current)
        self.assertEqual(current.refreshes, [5000])

    def test_hiding_card_is_not_revived(self):
        self.notifications.show_message(self.event())
        old = self.notifications._presenter.current.bulletin
        old.hide()
        self.notifications.show_message(self.event(mid=2))

        self.assertIsNot(self.notifications._presenter.current.bulletin, old)
        self.assertEqual(old.refreshes, [])

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
        context = self.sdk.UI()
        self.assertEqual(context._cleanup_callbacks, [])
        self.assertFalse(hasattr(context, '_presenter'))
        self.assertFalse(hasattr(context, '_notification_handle'))

    def test_separate_context_shutdown_does_not_close_another_plugin(self):
        other_ui = self.sdk.UI()
        other_ui._is_active = lambda: True
        other = self.sdk.Notifications(other_ui)
        self.notifications.show_message(self.event())
        other.show_message(self.event(dialog=11))
        current = other._presenter.current.bulletin
        self.ui._close()

        self.assertTrue(current.isShowing())
        self.assertTrue(other_ui._active())
        other_ui._close()
        self.assertFalse(current.isShowing())

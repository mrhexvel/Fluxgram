import importlib.util
from pathlib import Path
import sys
import types
import unittest
from unittest.mock import Mock, patch


SOURCE = Path(__file__).resolve().parents[2] / 'sdk/python/src/fluxgram/ui/context.py'


class UiContextTest(unittest.TestCase):
    def setUp(self):
        self.thread = 'main'
        self.pending = []
        modules = {}

        for name in ('android', 'android.os', 'fluxgram', 'fluxgram.ui', 'fluxgram._ui_thread'):
            module = types.ModuleType(name)
            module.__path__ = []
            modules[name] = module

        modules['android.os'].Looper = types.SimpleNamespace(
            myLooper=lambda: self.thread,
            getMainLooper=lambda: 'main',
        )
        modules['fluxgram._ui_thread'].post_ui = self.pending.append

        modules_patch = patch.dict(sys.modules, modules)
        modules_patch.start()
        self.addCleanup(modules_patch.stop)
        spec = importlib.util.spec_from_file_location('fluxgram.ui.context', SOURCE)
        sdk = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(sdk)

        self.ui = sdk.UI()
        self.host = types.SimpleNamespace(isGenerationActive=lambda generation: generation == 7)
        self.ui._bind_runtime(self.host, 7)

    def test_access_requires_active_generation_and_ui_thread(self):
        self.ui._require_ui()
        self.thread = 'worker'

        with self.assertRaisesRegex(RuntimeError, 'UI thread'):
            self.ui._require_ui()

        self.thread = 'main'
        self.ui._bind_runtime(self.host, 8)

        with self.assertRaisesRegex(RuntimeError, 'inactive'):
            self.ui._require_ui()

    def test_cleanup_runs_in_reverse_order_only_once(self):
        calls = []
        self.ui._register_cleanup(lambda: calls.append('first'))
        self.ui._register_cleanup(lambda: calls.append('second'))
        self.ui._close()
        self.ui._close()

        self.assertEqual(calls, ['second', 'first'])
        self.assertFalse(self.ui._active())
        self.assertEqual(self.ui._cleanup_callbacks, [])

    def test_cleanup_failure_does_not_skip_other_resources_and_can_be_retried(self):
        completed = Mock()
        failing = Mock(side_effect=[RuntimeError('failure'), None])
        self.ui._register_cleanup(completed)
        self.ui._register_cleanup(failing)

        with self.assertRaisesRegex(RuntimeError, 'cleanup failed'):
            self.ui._close()

        completed.assert_called_once_with()
        self.assertFalse(self.ui._active())
        self.ui._close()
        self.assertEqual(failing.call_count, 2)
        completed.assert_called_once_with()
        self.assertEqual(self.ui._cleanup_callbacks, [])

    def test_worker_close_invalidates_immediately_and_cleans_on_ui_thread(self):
        cleanup = Mock()
        self.ui._register_cleanup(cleanup)
        self.thread = 'worker'
        self.ui._close()

        self.assertFalse(self.ui._active())
        cleanup.assert_not_called()
        self.assertEqual(len(self.pending), 1)
        self.thread = 'main'
        self.pending.pop()()
        cleanup.assert_called_once_with()

    def test_closed_ui_cannot_acquire_new_resources(self):
        self.ui._close()

        with self.assertRaisesRegex(RuntimeError, 'inactive'):
            self.ui._register_cleanup(lambda: None)

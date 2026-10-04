import importlib.util
from pathlib import Path
import queue
import sys
import threading
import types
import unittest
from unittest.mock import patch


class UiCleanupTest(unittest.TestCase):
    def setUp(self):
        self.tasks = queue.Queue()
        main = threading.current_thread()

        class Handler:
            def __init__(inner, looper):
                pass

            def post(inner, task):
                self.tasks.put(task)
                return True

        def module(name, **values):
            result = types.ModuleType(name)
            result.__dict__.update(values)
            return result

        modules = {
            "java": module("java", dynamic_proxy=lambda target: object),
            "java.lang": module("java.lang", Runnable=object),
            "android": module("android"),
            "android.os": module("android.os", Handler=Handler, Looper=types.SimpleNamespace(
                myLooper=threading.current_thread, getMainLooper=lambda: main)),
            "android.util": module("android.util", Log=types.SimpleNamespace(e=lambda *args: None)),
        }
        source = Path(__file__).resolve().parents[2] / "TMessagesProj/src/main/python/fluxgram/_ui_thread.py"
        with patch.dict(sys.modules, modules):
            spec = importlib.util.spec_from_file_location("cleanup_ui", source)
            self.sdk = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(self.sdk)

    def test_worker_waits_and_receives_ui_error(self):
        result = []
        failure = RuntimeError("UI cleanup failed")

        def cleanup():
            raise failure

        def worker():
            try:
                self.sdk.call_ui(cleanup)
            except BaseException as error:
                result.append(error)

        thread = threading.Thread(target=worker, daemon=True)
        thread.start()
        task = self.tasks.get(timeout=2)
        self.assertEqual(result, [])
        task.run()
        thread.join(timeout=2)
        self.assertFalse(thread.is_alive())
        self.assertEqual(result, [failure])

    def test_ui_thread_does_not_wait_on_itself(self):
        self.assertEqual(self.sdk.call_ui(lambda: 42), 42)
        self.assertTrue(self.tasks.empty())

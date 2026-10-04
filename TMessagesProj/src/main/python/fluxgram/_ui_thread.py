import traceback

from java import dynamic_proxy
from java.lang import Runnable
from android.os import Handler, Looper
from android.util import Log


_handler = Handler(Looper.getMainLooper())


class _UiTask(dynamic_proxy(Runnable)):
    def __init__(self, callback):
        super().__init__()
        self._callback = callback

    def run(self):
        callback = self._callback
        self._callback = None

        try:
            callback()
        except Exception:
            Log.e("FluxGramPython", traceback.format_exc())


def post_ui(callback):
    if not _handler.post(_UiTask(callback)):
        raise RuntimeError("Could not schedule a UI task")
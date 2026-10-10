class UI:
    def __init__(self):
        self._closed = False
        self._is_active = lambda: False
        self._cleanup_callbacks = []

    def _bind_runtime(self, host, generation):
        self._is_active = lambda: host.isGenerationActive(generation)

    def _active(self):
        return not self._closed and self._is_active()

    def _require_ui(self):
        from android.os import Looper

        if not self._active():
            raise RuntimeError("Plugin UI is inactive")

        if Looper.myLooper() != Looper.getMainLooper():
            raise RuntimeError("Plugin UI must be called on the UI thread")

    def _register_cleanup(self, callback):
        if self._closed:
            raise RuntimeError("Plugin UI is inactive")

        self._cleanup_callbacks.append(callback)

    def _dispose(self):
        errors = []

        for callback in reversed(tuple(self._cleanup_callbacks)):
            try:
                callback()
            except Exception as error:
                errors.append(error)
            else:
                self._cleanup_callbacks.remove(callback)

        if errors:
            raise RuntimeError("Plugin UI cleanup failed") from errors[0]

    def _close(self):
        from android.os import Looper

        self._closed = True
        self._is_active = lambda: False

        if Looper.myLooper() == Looper.getMainLooper():
            self._dispose()
        else:
            from .._ui_thread import post_ui

            post_ui(self._dispose)

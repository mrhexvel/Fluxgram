import traceback

from android.util import Log
from java import dynamic_proxy, jclass
from org.telegram.ui.Components import BulletinFactory

from .navigation import foreground_fragment
from ..client import _require_integer


OnClickListener = jclass("android.view.View$OnClickListener")
OnAttachStateChangeListener = jclass("android.view.View$OnAttachStateChangeListener")


class _Click(dynamic_proxy(OnClickListener)):
    def __init__(self, handle):
        super().__init__()
        self.handle = handle

    def onClick(self, view):
        handle = self.handle

        if handle is None or not handle.is_showing():
            return

        presenter = handle.presenter
        callback = handle.on_click

        if callback is None or not presenter.ui._active():
            return

        try:
            presenter.ui._require_ui()

            if foreground_fragment() is None:
                return

            presenter.dismiss()
            callback()
        except Exception:
            Log.e("FluxGramPython", traceback.format_exc())


class _Detach(dynamic_proxy(OnAttachStateChangeListener)):
    def __init__(self, handle):
        super().__init__()
        self.handle = handle

    def onViewAttachedToWindow(self, view):
        pass

    def onViewDetachedFromWindow(self, view):
        handle = self.handle
        self.handle = None

        if handle is not None:
            handle.release(view, self)


class BulletinHandle:
    def __init__(self, presenter, bulletin, on_click):
        self.presenter = presenter
        self.bulletin = bulletin
        self.on_click = on_click
        self.click = _Click(self) if on_click is not None else None

    def is_showing(self):
        return (
            self.presenter is not None
            and self.presenter.current is self
            and self.bulletin is not None
            and self.bulletin.isShowing()
        )

    def update(self, render, *, on_click, duration):
        if not self.is_showing():
            return False

        self.presenter.ui._require_ui()
        render(self.bulletin)
        self.on_click = on_click
        self.bulletin.restartTimeout(duration)

        return True

    def release(self, view, detach):
        if self.presenter is not None and self.presenter.current is self:
            self.presenter.current = None

        if self.click is not None:
            self.click.handle = None

        view.setOnClickListener(None)
        view.removeOnAttachStateChangeListener(detach)
        self.presenter = None
        self.bulletin = None
        self.on_click = None
        self.click = None


class BulletinPresenter:
    def __init__(self, ui):
        self.ui = ui
        self.current = None

    def dismiss(self):
        handle = self.current
        self.current = None

        if handle is not None and handle.bulletin is not None:
            try:
                handle.bulletin.hide()
            except Exception:
                if self.current is None and handle.presenter is self:
                    self.current = handle

                raise

    def show(self, bulletin, on_click=None, top=True):
        self.ui._require_ui()
        self.dismiss()

        layout = bulletin.getLayout()
        handle = BulletinHandle(self, bulletin, on_click)
        detach = _Detach(handle)

        if handle.click is not None:
            layout.setOnClickListener(handle.click)

        layout.addOnAttachStateChangeListener(detach)
        self.current = handle

        try:
            bulletin.show(top)

            if not bulletin.isShowing():
                detach.onViewDetachedFromWindow(layout)

                return None
        except Exception:
            try:
                self.dismiss()
            finally:
                detach.onViewDetachedFromWindow(layout)

            raise

        return handle

    def simple(self, title, text, *, on_click=None, duration=4000, top=True):
        self.ui._require_ui()

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

        bulletin = BulletinFactory.of(fragment).createSimpleBulletin(title, text)
        bulletin.setDuration(duration)

        return self.show(bulletin, on_click, top) is not None

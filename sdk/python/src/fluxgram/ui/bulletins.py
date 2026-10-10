class Bulletins:
    def __init__(self, ui):
        from .._telegram.bulletins import BulletinPresenter

        self._presenter = BulletinPresenter(ui)
        ui._register_cleanup(self._presenter.dismiss)

    def show(self, title, text, *, on_click=None, duration=4000, top=True):
        return self._presenter.simple(
            title, text, on_click=on_click, duration=duration, top=top,
        )

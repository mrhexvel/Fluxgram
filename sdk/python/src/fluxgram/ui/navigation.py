class Navigation:
    def __init__(self, ui):
        self._ui = ui

    def open_chat(self, *, account, dialog_id, topic_id=0, message_id=0):
        from fluxgram_runtime._telegram.navigation import open_chat

        self._ui._require_ui()

        return open_chat(account, dialog_id, topic_id, message_id)

from collections import OrderedDict
from dataclasses import dataclass


@dataclass(frozen=True)
class NotificationRequest:
    event: object
    duration: int
    respect_mute: bool
    show_in_current_chat: bool

    @property
    def key(self):
        return self.event.account, self.event.dialog_id, self.event.topic_id


class NotificationQueue:
    def __init__(self, capacity=3):
        self._capacity = capacity
        self._items = OrderedDict()

    def offer(self, request):
        self._items[request.key] = request

        while len(self._items) > self._capacity:
            self._items.popitem(last=False)

    def pop(self):
        if not self._items:
            return None

        return self._items.popitem(last=False)[1]

    def discard_open_chat(self, key):
        request = self._items.get(key)

        if request is not None and not request.show_in_current_chat:
            del self._items[key]

    def clear(self):
        self._items.clear()

    def __bool__(self):
        return bool(self._items)

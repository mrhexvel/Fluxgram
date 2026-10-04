from dataclasses import dataclass

from .notifications import ChatNotifications


@dataclass(frozen=True)
class ChatInfo:
    id: int
    title: str | None

    # private / group / channel / secret / unknown
    kind: str

    is_forum: bool | None
    archived: bool | None
    notifications: ChatNotifications

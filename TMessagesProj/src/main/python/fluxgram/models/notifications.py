from dataclasses import dataclass


@dataclass(frozen=True)
class NotificationOverride:

    mode: str
    mute_until: int | None = None


@dataclass(frozen=True)
class ChatNotifications:
    chat_override: NotificationOverride
    topic_override: NotificationOverride | None

    effective_muted: bool | None

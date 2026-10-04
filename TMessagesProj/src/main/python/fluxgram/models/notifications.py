from dataclasses import dataclass


@dataclass(frozen=True)
class NotificationOverride:
    # inherit / enabled / muted / until / unknown
    mode: str
    mute_until: int | None = None


@dataclass(frozen=True)
class ChatNotifications:
    chat_override: NotificationOverride
    topic_override: NotificationOverride | None

    effective_muted: bool | None

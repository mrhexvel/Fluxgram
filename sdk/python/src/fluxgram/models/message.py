from dataclasses import dataclass


@dataclass(frozen=True)
class MessageInfo:
    id: int
    dialog_id: int
    topic_id: int

    sender_id: int | None

    text: str
    outgoing: bool
    mentioned: bool
    is_reply: bool
    silent: bool

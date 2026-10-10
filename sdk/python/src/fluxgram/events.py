from dataclasses import dataclass

from .models import ChatInfo, MessageInfo


@dataclass(frozen=True)
class BeforeSendEvent:
    account: int
    text_length: int
    schedule_date: int

    @property
    def scheduled(self) -> bool:
        return self.schedule_date != 0


@dataclass(frozen=True)
class MessageReceivedEvent:
    account: int
    chat: ChatInfo
    message: MessageInfo

    @property
    def dialog_id(self) -> int:
        return self.message.dialog_id

    @property
    def message_id(self) -> int:
        return self.message.id

    @property
    def topic_id(self) -> int:
        return self.message.topic_id

    @property
    def text(self) -> str:
        return self.message.text

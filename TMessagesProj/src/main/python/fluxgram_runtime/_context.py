from fluxgram.events import MessageReceivedEvent

from ._telegram.chats import read_chat
from ._telegram.messages import read_message


def build_message_event(account, message):
    account = int(account)
    message_info = read_message(message)
    chat_info = read_chat(
        account=account,
        dialog_id=message_info.dialog_id,
        topic_id=message_info.topic_id,
    )

    return MessageReceivedEvent(
        account=account,
        chat=chat_info,
        message=message_info,
    )

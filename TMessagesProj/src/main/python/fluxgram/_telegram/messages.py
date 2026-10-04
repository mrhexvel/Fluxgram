from ..models import MessageInfo


def read_message(message):

    sender_id = int(message.getFromChatId())

    return MessageInfo(
        id=int(message.getId()),
        dialog_id=int(message.getDialogId()),
        topic_id=int(message.getTopicId()),
        sender_id=sender_id if sender_id != 0 else None,
        text=(
            str(message.messageText)
            if message.messageText is not None
            else ""
        ),
        outgoing=bool(message.isOut()),
        mentioned=bool(message.messageOwner.mentioned),
        is_reply=bool(message.isReply()),
        silent=bool(message.messageOwner.silent),
    )

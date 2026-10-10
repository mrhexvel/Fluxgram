from java import jclass, jlong

from org.telegram.messenger import (
    ChatObject,
    DialogObject,
    MessagesController,
)

from fluxgram.models import ChatInfo

from .notifications import read_notifications


JavaLong = jclass("java.lang.Long")


def _read_chat_identity(controller, dialog_id):
    title = None
    kind = "unknown"
    is_forum = None

    if DialogObject.isEncryptedDialog(jlong(dialog_id)):
        kind = "secret"
        is_forum = False

    elif DialogObject.isUserDialog(jlong(dialog_id)):
        kind = "private"
        is_forum = False

        user = controller.getUser(JavaLong(jlong(dialog_id)))

        if user is not None:
            first_name = (
                str(user.first_name) if user.first_name else ""
            )
            last_name = (
                str(user.last_name) if user.last_name else ""
            )

            title = f"{first_name} {last_name}".strip() or None

    elif DialogObject.isChatDialog(jlong(dialog_id)):
        chat = controller.getChat(JavaLong(jlong(-dialog_id)))

        if chat is not None:
            title = str(chat.title) if chat.title else None

            is_broadcast = (
                ChatObject.isChannel(chat)
                and not bool(chat.megagroup)
            )

            kind = "channel" if is_broadcast else "group"
            is_forum = bool(ChatObject.isForum(chat))

    return title, kind, is_forum


def read_chat(account, dialog_id, topic_id=0):

    account = int(account)
    dialog_id = int(dialog_id)
    topic_id = int(topic_id)

    controller = MessagesController.getInstance(account)
    title, kind, is_forum = _read_chat_identity(controller, dialog_id)
    dialog = controller.getDialog(jlong(dialog_id))

    archived = (
        int(dialog.folder_id) == 1
        if dialog is not None
        else None
    )

    notifications = read_notifications(
        controller,
        account,
        dialog_id,
        topic_id,
        kind,
    )

    return ChatInfo(
        id=dialog_id,
        title=title,
        kind=kind,
        is_forum=is_forum,
        archived=archived,
        notifications=notifications,
    )

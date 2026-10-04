from java import jlong

from org.telegram.messenger import (
    MessagesController,
    NotificationsController,
)

from ..models import ChatNotifications, NotificationOverride


def _read_override(preferences, dialog_id, topic_id):
    key = str(
        NotificationsController.getSharedPrefKey(
            jlong(dialog_id),
            jlong(topic_id),
            True,
        )
    )

    value = int(preferences.getInt("notify2_" + key, -1))

    if value == -1:
        return NotificationOverride("inherit")

    if value in (0, 1):
        return NotificationOverride("enabled")

    if value == 2:
        return NotificationOverride("muted")

    if value == 3:
        mute_until = int(
            preferences.getInt("notifyuntil_" + key, 0)
        )

        return NotificationOverride("until", mute_until)

    return NotificationOverride("unknown")


def read_notifications(
    controller,
    account,
    dialog_id,
    topic_id,
    kind,
):
    preferences = MessagesController.getNotificationsSettings(
        account
    )

    chat_override = _read_override(
        preferences, dialog_id, 0
    )

    topic_override = (
        _read_override(preferences, dialog_id, topic_id)
        if topic_id != 0
        else None
    )

    selected = chat_override

    if (
        topic_override is not None
        and topic_override.mode != "inherit"
    ):
        selected = topic_override

    if selected.mode == "unknown":
        effective_muted = None
    elif selected.mode == "inherit" and kind == "unknown":
        effective_muted = None
    else:
        effective_muted = bool(
            controller.isDialogMuted(
                jlong(dialog_id),
                jlong(topic_id),
            )
        )

    return ChatNotifications(
        chat_override=chat_override,
        topic_override=topic_override,
        effective_muted=effective_muted,
    )

from android.content import Intent
from java import jint, jlong
from org.telegram.messenger import ApplicationLoader, DialogObject, SharedConfig
from org.telegram.ui import LaunchActivity


def foreground_fragment():
    """Return a usable foreground fragment, never one behind the app lock."""
    if (
        ApplicationLoader.mainInterfacePaused
        or SharedConfig.appLocked
        or SharedConfig.isWaitingForPasscodeEnter
    ):
        return None
    fragment = LaunchActivity.getLastFragmentIncludeMainTabs()
    if fragment is None:
        return None
    activity = fragment.getParentActivity()
    if activity is None or activity.isFinishing() or activity.isDestroyed():
        return None
    return fragment


def open_chat(account, dialog_id, topic_id=0, message_id=0):
    """Use Telegram's notification route, including account and topic loading."""
    fragment = foreground_fragment()
    if fragment is None:
        return False

    activity = fragment.getParentActivity()
    intent = Intent()
    intent.setClassName(activity, "org.telegram.ui.LaunchActivity")
    intent.setAction("com.tmessages.openchat.fluxgram")
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
    intent.putExtra("currentAccount", jint(account))
    intent.putExtra("message_id", jint(message_id))

    if DialogObject.isEncryptedDialog(jlong(dialog_id)):
        intent.putExtra("encId", jint(DialogObject.getEncryptedChatId(jlong(dialog_id))))
    elif DialogObject.isChatDialog(jlong(dialog_id)):
        intent.putExtra("chatId", jlong(-dialog_id))
        intent.putExtra("topicId", jlong(topic_id))
    elif DialogObject.isUserDialog(jlong(dialog_id)):
        intent.putExtra("userId", jlong(dialog_id))
    else:
        raise ValueError("Unsupported dialog_id")

    activity.startActivity(intent)
    return True

from android.content import Intent
from java import jint, jlong
from org.telegram.messenger import ApplicationLoader, DialogObject, SharedConfig, UserConfig
from org.telegram.ui import LaunchActivity

from ..client import _require_integer


def foreground_fragment():

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
    _require_integer("account", account, 0, int(UserConfig.MAX_ACCOUNT_COUNT) - 1)
    _require_integer("dialog_id", dialog_id, -(2**63), 2**63 - 1)
    _require_integer("topic_id", topic_id, 0, 2**63 - 1)
    _require_integer("message_id", message_id, -(2**31), 2**31 - 1)

    if dialog_id == 0 or DialogObject.isFolderDialogId(jlong(dialog_id)):
        raise ValueError("dialog_id must identify a chat")

    if not UserConfig.getInstance(account).isClientActivated():
        return False

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

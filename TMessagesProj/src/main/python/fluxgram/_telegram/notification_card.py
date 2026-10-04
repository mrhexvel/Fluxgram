from android.text import TextUtils
from android.view import Gravity
from java import jarray, jclass, jint, jlong
from org.telegram.messenger import (
    AndroidUtilities, DialogObject, Emoji, LocaleController,
    MessagesController, UserObject,
)
from org.telegram.ui import LaunchActivity
from org.telegram.ui.ActionBar import Theme
from org.telegram.ui.Components import (
    AvatarDrawable, Bulletin, LayoutHelper, NotificationBlurBackground,
)


CardLayout = jclass("org.telegram.ui.Components.Bulletin$TwoLineBackupLayout")
JavaLong = jclass("java.lang.Long")
JavaInteger = jclass("java.lang.Integer")
Strings = jclass("org.telegram.messenger.R$string")


def _theme_color(key, provider):
    # An untyped None also matches getColor(int, boolean[]) in Chaquopy.
    if provider is None:
        return Theme.getColor(key)
    return Theme.getColor(key, provider)


def _peer(controller, dialog_id):
    if DialogObject.isEncryptedDialog(jlong(dialog_id)):
        encrypted = controller.getEncryptedChat(JavaInteger(
            jint(DialogObject.getEncryptedChatId(jlong(dialog_id)))
        ))
        if encrypted is None:
            return None
        return controller.getUser(JavaLong(jlong(encrypted.user_id)))
    if DialogObject.isUserDialog(jlong(dialog_id)):
        return controller.getUser(JavaLong(jlong(dialog_id)))
    if DialogObject.isChatDialog(jlong(dialog_id)):
        return controller.getChat(JavaLong(jlong(-dialog_id)))
    return None


def _name(peer):
    if peer is None:
        return ""
    if hasattr(peer, "first_name"):
        return str(UserObject.getUserName(peer))
    return str(peer.title) if peer.title else ""


def _one_line(text):
    return " ".join(text.split())


def message_labels(event, controller, peer):
    title = event.chat.title or _name(peer) or "FluxGram"
    if event.chat.is_forum and event.topic_id:
        topic = controller.getTopicsController().findTopic(
            jlong(-event.dialog_id), jlong(event.topic_id)
        )
        if topic is not None and topic.title:
            title += " · " + str(topic.title)

    text = _one_line(event.text[:512])
    if not text:
        text = str(LocaleController.getString(Strings.Message))
    if event.chat.kind == "group" and event.message.sender_id:
        sender = _name(_peer(controller, event.message.sender_id))
        if sender:
            text = _one_line(sender)[:80] + ": " + text
    return _one_line(title)[:160], text


def create_message_bulletin(fragment, event):
    """Build a theme-aware avatar card. Avatar loading uses ImageReceiver."""
    activity = fragment.getParentActivity()
    provider = fragment.getResourceProvider()
    controller = MessagesController.getInstance(event.account)
    peer = _peer(controller, event.dialog_id)
    title, text = message_labels(event, controller, peer)

    # The activity container has no DialogsActivity search/header offset.
    # Bubble activities retain their own fragment container.
    container = (
        activity.getMainContainerFrameLayout()
        if isinstance(activity, LaunchActivity)
        else fragment.getBulletinLayoutContainer()
    )
    location = jarray(jint)([0, 0])
    container.getLocationOnScreen(location)
    insets = container.getRootWindowInsets()
    status_bar = (
        insets.getSystemWindowInsetTop()
        if insets is not None
        else AndroidUtilities.statusBarHeight
    )
    safe_top = max(0, int(status_bar) - int(location[1]))
    dp = AndroidUtilities.dp

    layout = CardLayout(activity, provider)
    layout.setPadding(dp(12), safe_top + dp(8), dp(12), dp(8))
    layout.setMinimumHeight(safe_top + dp(96))
    background = NotificationBlurBackground(
        container, layout,
        _theme_color(Theme.key_dialogBackground, provider),
        _theme_color(Theme.key_windowBackgroundWhiteBlueText, provider),
    )
    layout.setCustomBackground(background)

    image = layout.imageView
    image.setLayoutParams(LayoutHelper.createFrameRelatively(
        48, 48, Gravity.START | Gravity.CENTER_VERTICAL, 12, 12, 12, 12
    ))
    image.setRoundRadius(dp(24))
    image.getImageReceiver().setCurrentAccount(event.account)
    avatar = AvatarDrawable()
    if peer is not None:
        avatar.setInfo(event.account, peer)
        image.setForUserOrChat(peer, avatar)
    else:
        avatar.setInfo(jlong(event.dialog_id), title, None)
        image.setImageDrawable(avatar)
    image.setImportantForAccessibility(2)  # Decorative; title identifies the chat.

    column = layout.titleTextView.getParent()
    column.setLayoutParams(LayoutHelper.createFrameRelatively(
        -1, -2, Gravity.START | Gravity.CENTER_VERTICAL, 72, 12, 16, 12
    ))
    title_view = layout.titleTextView
    title_view.setPadding(0, 0, 0, dp(3))
    title_view.setTextSize(2, 16.0)
    title_view.setSingleLine(True)
    title_view.setEllipsize(TextUtils.TruncateAt.END)
    title_view.setTextColor(_theme_color(Theme.key_dialogTextBlack, provider))
    title_view.setText(Emoji.replaceEmoji(title, title_view.getPaint().getFontMetricsInt(), False))

    body_view = layout.subtitleTextView
    body_view.setPadding(0, 0, 0, 0)
    body_view.setTextSize(2, 14.0)
    body_view.setSingleLine(False)
    body_view.setMaxLines(2)
    body_view.setEllipsize(TextUtils.TruncateAt.END)
    body_view.setTextColor(_theme_color(Theme.key_dialogTextGray, provider))
    body_view.setText(Emoji.replaceEmoji(text, body_view.getPaint().getFontMetricsInt(), False))
    return Bulletin.make(container, layout, 5000)

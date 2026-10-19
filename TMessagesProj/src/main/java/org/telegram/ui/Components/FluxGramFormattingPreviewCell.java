package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.fluxgram.GeneralSettings;
import org.telegram.messenger.fluxgram.ZalgoDisplayFilter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;

public final class FluxGramFormattingPreviewCell extends ThemePreviewMessagesCell {
    private final int account;
    private final ChatAvatarContainer avatarContainer;
    private ValueAnimator contentAnimator;
    private ViewTreeObserver.OnPreDrawListener pendingTransition;
    private Drawable transitionDrawable;
    private String status;

    public FluxGramFormattingPreviewCell(
            Context context,
            int account,
            INavigationLayout navigationLayout,
            Theme.ResourcesProvider resourcesProvider
    ) {
        super(context, navigationLayout, 0, 0, account, resourcesProvider);
        this.account = account;

        setPadding(0, 0, 0, AndroidUtilities.dp(11));

        avatarContainer = new ChatAvatarContainer(context, null, false, resourcesProvider);
        avatarContainer.setOccupyStatusBar(false);
        avatarContainer.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault, resourcesProvider));
        avatarContainer.setTitle(LocaleController.getString(R.string.AppName));

        TLRPC.User previewUser = new TLRPC.TL_user();
        previewUser.id = 1L;
        previewUser.first_name = LocaleController.getString(R.string.AppName);
        avatarContainer.setUserAvatar(previewUser);

        addView(avatarContainer, 0, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));
        addView(new View(context), 1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        addView(new View(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        for (ChatMessageCell cell : getCells()) {
            cell.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        bindContent(true, true);
    }

    public void updateContent(boolean animated, boolean updateHeader, boolean updateMessages) {
        boolean shouldAnimate = animated
                && isAttachedToWindow()
                && SharedConfig.animationsEnabled()
                && !LiteMode.isPowerSaverApplied();
        Drawable snapshot = shouldAnimate ? captureContent(updateHeader, updateMessages) : null;

        cancelContentAnimation();
        bindContent(updateHeader, updateMessages);

        if (snapshot == null) {
            return;
        }

        transitionDrawable = snapshot;
        getOverlay().add(transitionDrawable);
        pendingTransition = () -> {
            getViewTreeObserver().removeOnPreDrawListener(pendingTransition);
            pendingTransition = null;

            contentAnimator = ValueAnimator.ofFloat(0f, 1f);
            contentAnimator.setDuration(220L);
            contentAnimator.addUpdateListener(valueAnimator -> {
                float progress = (float) valueAnimator.getAnimatedValue();
                if (transitionDrawable != null) {
                    transitionDrawable.setAlpha((int) (255f * (1f - progress)));
                    invalidate();
                }
            });
            contentAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    contentAnimator = null;
                    removeTransitionDrawable();
                }
            });
            contentAnimator.start();

            return true;
        };
        getViewTreeObserver().addOnPreDrawListener(pendingTransition);
    }

    private Drawable captureContent(boolean includeHeader, boolean includeMessages) {
        ChatMessageCell[] cells = getCells();
        int top = includeHeader ? 0 : Math.min(cells[0].getTop(), cells[1].getTop());
        int bottom = includeMessages
                ? Math.max(cells[0].getBottom(), cells[1].getBottom())
                : avatarContainer.getBottom();
        if (getWidth() <= 0 || bottom <= top) {
            return null;
        }

        Bitmap bitmap = Bitmap.createBitmap(getWidth(), bottom - top, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(0f, -top);
        draw(canvas);

        BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
        drawable.setBounds(0, top, getWidth(), bottom);
        return drawable;
    }

    private void cancelContentAnimation() {
        if (pendingTransition != null) {
            getViewTreeObserver().removeOnPreDrawListener(pendingTransition);
            pendingTransition = null;
        }
        if (contentAnimator != null) {
            contentAnimator.cancel();
            contentAnimator = null;
        }
        removeTransitionDrawable();
    }

    private void removeTransitionDrawable() {
        if (transitionDrawable != null) {
            getOverlay().remove(transitionDrawable);
            transitionDrawable = null;
        }
    }

    private void bindContent(boolean updateHeader, boolean updateMessages) {
        if (updateHeader) {
            long nowSeconds = System.currentTimeMillis() / 1000L;
            if (GeneralSettings.usesRelativeLastSeen()) {
                status = LocaleController.formatString(
                        R.string.LastSeenFormatted,
                        LocaleController.formatRelativeDate(300L)
                );
            } else {
                status = LocaleController.formatDateOnline(nowSeconds - 300L, null);
            }
            avatarContainer.setSubtitle(status);
        }

        String sample = LocaleController.getString(R.string.FluxGramFormattingPreviewText)
                + " A\u0300\u0301\u0302\u0303\u0304\u0305";
        String incomingText = ZalgoDisplayFilter.filter(sample).toString();
        String outgoingText = LocaleController.getString(R.string.FluxGramFormattingPreviewOutgoingText);
        String count = LocaleController.formatShortNumber(12345, null);

        if (updateMessages) {
            ChatMessageCell[] cells = getCells();
            cells[0].setMessageObject(createMessage(incomingText, false, false, 1), null, false, false, false);
            cells[1].setMessageObject(createMessage(outgoingText, true, true, 2), null, false, false, false);
            cells[0].requestLayout();
            cells[1].requestLayout();
        }

        setContentDescription(status + ". "
                + LocaleController.getString(R.string.FluxGramFormattingPreviewText) + ". "
                + outgoingText + ". " + count);
        requestLayout();
        invalidate();
    }

    private MessageObject createMessage(String text, boolean outgoing, boolean showViews, int id) {
        TLRPC.Message message = new TLRPC.TL_message();
        message.id = id;
        message.message = text;
        message.date = (int) (System.currentTimeMillis() / 1000L);
        message.dialog_id = 1L;
        message.flags = 259;
        message.media = new TLRPC.TL_messageMediaEmpty();
        message.out = outgoing;
        message.peer_id = new TLRPC.TL_peerUser();
        message.peer_id.user_id = UserConfig.getInstance(account).getClientUserId();
        if (showViews) {
            message.flags |= TLRPC.MESSAGE_FLAG_HAS_VIEWS;
            message.views = 12345;
        }
        if (outgoing) {
            message.from_id = new TLRPC.TL_peerUser();
            message.from_id.user_id = UserConfig.getInstance(account).getClientUserId();
        }

        MessageObject messageObject = new MessageObject(account, message, true, false);
        messageObject.eventId = 1L;
        messageObject.resetLayout();

        return messageObject;
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelContentAnimation();
        super.onDetachedFromWindow();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(View.class.getName());
        info.setClickable(false);
    }
}

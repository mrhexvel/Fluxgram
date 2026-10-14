package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.fluxgram.AppearanceSettings;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;

public final class FluxGramChatListPreviewCell extends FrameLayout {
    private final int account;
    private final Theme.ResourcesProvider resourcesProvider;
    private final ActionBar actionBar;
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable statusDrawable;
    private Drawable premiumStar;

    public FluxGramChatListPreviewCell(
            Context context,
            int account,
            Theme.ResourcesProvider resourcesProvider
    ) {
        super(context);
        this.account = account;
        this.resourcesProvider = resourcesProvider;

        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        statusDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(null, dp(26));
        statusDrawable.center = true;

        actionBar = new ActionBar(context, resourcesProvider);
        actionBar.setOccupyStatusBar(false);
        actionBar.createMenu().addItem(0, R.drawable.ic_ab_other);
        addView(actionBar, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.TOP,
                16,
                16,
                16,
                16
        ));

        update(false);
    }

    public void update(boolean animated) {
        updateColors();
        actionBar.setTitleCentered(AppearanceSettings.centersActionBarTitle(account));

        TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        Drawable status = null;

        if (user != null && !AppearanceSettings.hidesActionBarStatus(account)) {
            Long documentId = UserObject.getEmojiStatusDocumentId(user);

            if (documentId != null) {
                statusDrawable.set(documentId, animated);
                statusDrawable.setParticles(
                        user.emoji_status instanceof TLRPC.TL_emojiStatusCollectible,
                        animated
                );
                statusDrawable.setColor(color(Theme.key_profile_verifiedBackground));
                status = statusDrawable;
            } else if (MessagesController.getInstance(account).isPremiumUser(user)) {
                status = getPremiumStar();
            }
        }

        if (status != statusDrawable) {
            statusDrawable.set((Drawable) null, animated);
            statusDrawable.setParticles(false, animated);
        }

        actionBar.setTitle(AppearanceSettings.getActionBarTitle(account), status);
    }

    private void updateColors() {
        actionBar.setItemsColor(color(Theme.key_actionBarDefaultIcon), false);
        actionBar.setItemsBackgroundColor(color(Theme.key_actionBarDefaultSelector), false);
        actionBar.setTitleColor(color(Theme.key_actionBarDefaultTitle));
        actionBar.setBackground(Theme.createRoundRectDrawable(
                dp(12), color(Theme.key_actionBarDefault)
        ));
    }

    private Drawable getPremiumStar() {
        if (premiumStar == null) {
            Drawable star = getContext().getResources()
                    .getDrawable(R.drawable.msg_premium_liststar)
                    .mutate();
            premiumStar = new AnimatedEmojiDrawable.WrapSizeDrawable(star, dp(18), dp(18)) {
                @Override
                public void draw(@NonNull Canvas canvas) {
                    canvas.save();
                    canvas.translate(dp(-2), dp(1));
                    super.draw(canvas);
                    canvas.restore();
                }
            };
        }

        premiumStar.setColorFilter(new PorterDuffColorFilter(
                color(Theme.key_profile_verifiedBackground),
                PorterDuff.Mode.MULTIPLY
        ));

        return premiumStar;
    }

    private int color(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1, Theme.dividerPaint);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        statusDrawable.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        statusDrawable.detach();
        super.onDetachedFromWindow();
    }
}

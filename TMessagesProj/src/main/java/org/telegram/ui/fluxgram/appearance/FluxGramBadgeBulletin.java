package org.telegram.ui.fluxgram.appearance;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.fluxgram.appearance.ProfileBadge;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;

public final class FluxGramBadgeBulletin {
    private FluxGramBadgeBulletin() {
    }

    public static void show(BaseFragment fragment, int account, ProfileBadge badge) {
        Context context = fragment.getContext();
        if (context == null || badge == null) {
            return;
        }

        BadgeLayout layout = new BadgeLayout(
                context,
                fragment.getResourceProvider()
        );
        AnimatedEmojiDrawable drawable = new AnimatedEmojiDrawable(
                AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES_LARGE,
                account,
                badge.getDocumentId()
        );
        drawable.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(
                        Theme.key_windowBackgroundWhiteBlueText,
                        fragment.getResourceProvider()
                ),
                PorterDuff.Mode.SRC_IN
        ));
        layout.imageView.setAnimatedEmojiDrawable(drawable);
        layout.setText(badge.getTitle(), badge.getDescription());

        BulletinFactory.of(fragment)
                .create(layout, Bulletin.DURATION_LONG)
                .wrapContent()
                .show();
    }

    private static final class BadgeLayout extends Bulletin.TwoLineBackupLayout {
        private boolean hasDescription;

        private BadgeLayout(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);
            setWrapWidth();
            setMinimumHeight(AndroidUtilities.dp(64));
            titleTextView.setSingleLine(true);
            titleTextView.setEllipsize(TextUtils.TruncateAt.END);
        }

        private void setText(String title, String description) {
            hasDescription = !TextUtils.isEmpty(description);
            titleTextView.setText(title);
            subtitleTextView.setText(description);
            subtitleTextView.setVisibility(hasDescription ? View.VISIBLE : View.GONE);
        }

        @Override
        public CharSequence getAccessibilityText() {
            return hasDescription
                    ? super.getAccessibilityText()
                    : titleTextView.getText();
        }
    }
}

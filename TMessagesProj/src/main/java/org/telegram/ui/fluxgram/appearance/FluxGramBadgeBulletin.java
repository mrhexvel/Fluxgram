package org.telegram.ui.fluxgram.appearance;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

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

        Bulletin.TwoLineBackupLayout layout = new Bulletin.TwoLineBackupLayout(
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
        layout.titleTextView.setText(badge.getTitle());
        layout.subtitleTextView.setText(badge.getDescription());

        BulletinFactory.of(fragment)
                .create(layout, Bulletin.DURATION_LONG)
                .show();
    }
}

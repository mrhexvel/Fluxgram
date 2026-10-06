package org.telegram.ui.fluxgram.appearance;

import android.content.Context;

import org.telegram.messenger.fluxgram.appearance.ProfileBadge;
import org.telegram.ui.ActionBar.BaseFragment;
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
        layout.imageView.setAnimatedEmojiDrawable(new AnimatedEmojiDrawable(
                AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES_LARGE,
                account,
                badge.getDocumentId()
        ));
        layout.titleTextView.setText(badge.getTitle());
        layout.subtitleTextView.setText(badge.getDescription());

        BulletinFactory.of(fragment)
                .create(layout, Bulletin.DURATION_LONG)
                .show();
    }
}

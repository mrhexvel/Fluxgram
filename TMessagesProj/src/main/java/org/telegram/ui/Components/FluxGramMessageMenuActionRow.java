package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public class FluxGramMessageMenuActionRow extends FrameLayout {
    private final LinearLayout buttonsLayout;
    private final Theme.ResourcesProvider resourcesProvider;

    public FluxGramMessageMenuActionRow(
            Context context,
            Theme.ResourcesProvider resourcesProvider
    ) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setMinimumWidth(AndroidUtilities.dp(224));

        buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        addView(buttonsLayout, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                48,
                Gravity.CENTER,
                8,
                8,
                8,
                8
        ));

        updateColors();
    }

    public void updateColors() {
        Drawable background = ContextCompat.getDrawable(getContext(), R.drawable.popup_fixed_alert4);
        if (background != null) {
            background = background.mutate();
            background.setColorFilter(new PorterDuffColorFilter(
                    Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider),
                    PorterDuff.Mode.MULTIPLY
            ));
            setBackground(background);
        }

        for (int i = 0; i < buttonsLayout.getChildCount(); i++) {
            ImageView button = (ImageView) buttonsLayout.getChildAt(i);
            button.setColorFilter(new PorterDuffColorFilter(
                    Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider),
                    PorterDuff.Mode.SRC_IN
            ));
            button.setBackground(Theme.createRadSelectorDrawable(
                    Theme.getColor(Theme.key_dialogButtonSelector, resourcesProvider),
                    8,
                    8
            ));
        }
    }

    public void addAction(
            int icon,
            CharSequence label,
            boolean enabled,
            OnClickListener listener
    ) {
        ImageView button = new ImageView(getContext());
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setImageResource(icon);
        button.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider),
                PorterDuff.Mode.SRC_IN
        ));
        button.setBackground(Theme.createRadSelectorDrawable(
                Theme.getColor(Theme.key_dialogButtonSelector, resourcesProvider),
                8,
                8
        ));
        button.setContentDescription(label);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.5f);
        button.setOnClickListener(listener);
        ScaleStateListAnimator.apply(button, 0.08f, 1.5f);

        LinearLayout.LayoutParams params = LayoutHelper.createLinear(0, 48, 1f);
        buttonsLayout.addView(button, params);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64), MeasureSpec.EXACTLY)
        );
    }
}

package org.telegram.ui.fluxgram;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.LayoutHelper;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class PluginComposerPreviewView extends FrameLayout {
    private final BooleanSupplier eligible;
    private final EditTextCaption field;
    private final FluxGramComposerController controller;
    private boolean active;
    private boolean destroyed;

    public PluginComposerPreviewView(Context context, int account, String pluginId,
            BooleanSupplier eligible, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.eligible = eligible;
        setClipChildren(true);
        setClipToPadding(true);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));

        field = new EditTextCaption(context, resourcesProvider) {
            private final Consumer<Canvas> nativeDraw = canvas -> super.onDraw(canvas);

            @Override
            protected void onDraw(Canvas canvas) {
                if (controller == null) {
                    nativeDraw.accept(canvas);
                } else {
                    controller.drawText(this, canvas, nativeDraw);
                }
            }

            @Override
            protected void drawCursor(Canvas canvas, Rect bounds, int line, float baseline,
                    boolean rtl, Consumer<Canvas> nativeDraw) {
                if (controller == null) {
                    nativeDraw.accept(canvas);
                } else {
                    controller.drawCursor(this, canvas, bounds, line, baseline, rtl, nativeDraw);
                }
            }

            @Override
            protected void onSelectionChanged(int start, int end) {
                super.onSelectionChanged(start, end);

                if (controller != null) {
                    controller.selectionChanged(this, start, end);
                }
            }
        };
        field.setTextSize(17);
        field.setTextColor(Theme.getColor(Theme.key_chat_messagePanelText, resourcesProvider));
        field.setHintTextColor(Theme.getColor(Theme.key_chat_messagePanelHint, resourcesProvider));
        field.setCursorColor(Theme.getColor(Theme.key_chat_messagePanelCursor, resourcesProvider));
        field.setHint(LocaleController.getString(R.string.FluxGramPluginPreviewHint));
        field.setGravity(Gravity.TOP | Gravity.START);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        field.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.setMaxLines(3);
        field.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(11),
                AndroidUtilities.dp(14), AndroidUtilities.dp(11));
        field.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_chat_messagePanelBackground, resourcesProvider)));

        FrameLayout inputContainer = new FrameLayout(context);
        addView(inputContainer, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        inputContainer.addView(field, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 88,
                Gravity.TOP, 16, 12, 16, 12));

        controller = new FluxGramComposerController(
                field, this, account, 0, pluginId,
                () -> active && !destroyed && eligible.getAsBoolean(), resourcesProvider);
    }

    public void setActive(boolean value) {
        if (destroyed) {
            return;
        }

        active = value;
        refresh();

        if (!active) {
            field.clearFocus();
        }
    }

    public void refresh() {
        if (destroyed) {
            return;
        }

        boolean enabled = active && eligible.getAsBoolean();
        field.setEnabled(enabled);

        if (!enabled) {
            field.clearFocus();
        }

        controller.refresh();
    }

    public void destroy() {
        if (destroyed) {
            return;
        }

        destroyed = true;
        active = false;
        field.clearFocus();
        controller.destroy();
    }
}

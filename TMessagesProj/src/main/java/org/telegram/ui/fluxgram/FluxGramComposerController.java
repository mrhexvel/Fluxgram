package org.telegram.ui.fluxgram;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.BaseInputConnection;
import android.widget.EditText;

import org.telegram.messenger.LiteMode;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.plugins.PluginComposerRegistry;
import org.telegram.messenger.plugins.api.ComposerCursorPresentation;
import org.telegram.messenger.plugins.api.ComposerCursorSnapshot;
import org.telegram.messenger.plugins.api.ComposerGeometry;
import org.telegram.messenger.plugins.api.ComposerInput;
import org.telegram.messenger.plugins.api.ComposerSelectionSnapshot;
import org.telegram.messenger.plugins.api.ComposerTextCandidate;
import org.telegram.messenger.plugins.api.ComposerTextChange;
import org.telegram.messenger.plugins.api.ComposerTextLayoutChange;
import org.telegram.messenger.plugins.api.ComposerTextLayoutSnapshot;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ForegroundDetector;
import org.telegram.ui.Components.EditTextCaption;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class FluxGramComposerController implements TextWatcher,
        View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener, ForegroundDetector.Listener {
    private final EditText field;
    private final int account;
    private final long ownerId;
    private final long dialogId;
    private final BooleanSupplier eligible;
    private final Theme.ResourcesProvider colors;
    private final ForegroundDetector foreground;
    private final Drawable overlay;
    private PluginComposerRegistry.Surface surface;
    private ComposerGeometry geometry;
    private ViewTreeObserver observer;
    private boolean destroyed;
    private final ComposerInputChanges inputChanges = new ComposerInputChanges();
    private String beforeText;
    private int changeStart;
    private int changeRemoved;
    private int changeInserted;
    private long textRevision;
    private ComposerTextLayoutSnapshot pendingLayoutBefore;
    private String pendingLayoutAfterText;
    private PluginComposerRegistry.OverlayTextCapture pendingRemovedCapture;
    private boolean pendingLayoutChange;
    private boolean pendingLayoutCoalesced;
    private final ComposerNativeCursorCapture cursorCapture = new ComposerNativeCursorCapture();
    private final Rect cursorBounds = new Rect();
    private long cursorRevision;
    private int cursorOffset = -1;
    private int cursorLine = -1;
    private float cursorBaseline = Float.NaN;
    private boolean cursorRtl;
    private ComposerSelectionCapture selectionCapture;
    private long selectionRevision;

    public FluxGramComposerController(EditText field, int account, long dialogId,
            BooleanSupplier eligible, Theme.ResourcesProvider colors) {
        this.field = field;
        this.account = account;
        this.dialogId = dialogId;
        this.ownerId = UserConfig.getInstance(account).getClientUserId();
        this.eligible = eligible;
        this.colors = colors;
        foreground = ForegroundDetector.getInstance();
        overlay = new Drawable() {
            @Override
            public void draw(Canvas canvas) {
                if (surface != null && available()) {
                    surface.draw(canvas);
                }
            }

            @Override
            public void setAlpha(int alpha) {
            }

            @Override
            public void setColorFilter(ColorFilter filter) {
            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };
        field.addOnAttachStateChangeListener(this);
        field.addTextChangedListener(this);

        if (foreground != null) {
            foreground.addListener(this);
        }

        if (field.isAttachedToWindow()) {
            onViewAttachedToWindow(field);
        }
    }

    private boolean available() {
        return !destroyed && eligible.getAsBoolean()
                && ownerId > 0 && UserConfig.selectedAccount == account
                && UserConfig.getInstance(account).getClientUserId() == ownerId
                && field.isAttachedToWindow() && field.isShown() && field.isEnabled()
                && field.hasFocus() && field.hasWindowFocus() && field.getAlpha() > 0
                && field.getWindowVisibility() == View.VISIBLE
                && field.getWidth() > 0 && field.getHeight() > 0
                && foreground != null && foreground.isForeground()
                && !LiteMode.isPowerSaverApplied() && LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND);
    }

    public void refresh() {
        if (surface != null && surface.isClosed()) {
            field.getOverlay().remove(overlay);
            surface = null;
        }

        if (!available()) {
            hide();
            return;
        }

        int width = field.getWidth();
        int height = field.getHeight();
        float density = field.getResources().getDisplayMetrics().density;
        int accent = Theme.getColor(Theme.key_chat_messagePanelCursor, colors);
        boolean rtl = field.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

        if (geometry == null || geometry.width != width || geometry.height != height
                || geometry.density != density || geometry.accentColor != accent || geometry.rtl != rtl) {
            geometry = new ComposerGeometry(width, height, density, accent, rtl);
            overlay.setBounds(0, 0, width, height);
            cursorRevision++;
            cursorCapture.release();
            clearSelectionCapture();
        }

        if (surface == null) {
            surface = PluginComposerRegistry.attach(new PluginComposerRegistry.Host() {
                @Override
                public int account() {
                    return account;
                }

                @Override
                public long ownerId() {
                    return ownerId;
                }

                @Override
                public long dialogId() {
                    return dialogId;
                }

                @Override
                public boolean isAvailable() {
                    return available();
                }

                @Override
                public ComposerGeometry geometry() {
                    return geometry;
                }

                @Override
                public void invalidate() {
                    overlay.invalidateSelf();
                }

                @Override
                public void invalidateCursor() {
                    field.invalidate();
                }

                @Override
                public void cursorOwnerChanged() {
                    cursorRevision++;
                    cursorCapture.release();
                    field.invalidate();
                }

                @Override
                public void selectionOwnerChanged() {
                    clearSelectionCapture();
                    overlay.invalidateSelf();
                }

                @Override
                public PluginComposerRegistry.TextCapture captureText(
                        ComposerTextCandidate candidate, String expectedText) {
                    if (!(field instanceof EditTextCaption)) {
                        return null;
                    }

                    return ComposerNativeTextCapture.capture((EditTextCaption) field, candidate, expectedText);
                }

                @Override
                public void drawCursor(Canvas canvas, Consumer<Canvas> nativeDraw,
                        ComposerCursorSnapshot snapshot, ComposerCursorPresentation presentation) {
                    cursorCapture.draw(canvas, nativeDraw, snapshot, presentation);
                }
            });

            if (surface != null) {
                field.getOverlay().add(overlay);
            }
        }
    }

    private void hide() {
        if (surface != null) {
            PluginComposerRegistry.Surface previous = surface;
            surface = null;
            field.getOverlay().remove(overlay);
            previous.close();
        }

        inputChanges.reset();
        beforeText = null;
        clearPendingLayout();
        cursorCapture.release();
        clearSelectionCapture();
    }

    public void drawText(EditTextCaption source, Canvas canvas, Consumer<Canvas> nativeDraw) {
        if (source == field && !destroyed && surface != null) {
            surface.drawText(canvas, nativeDraw);
        } else {
            nativeDraw.accept(canvas);
        }
    }

    public void drawCursor(EditTextCaption source, Canvas canvas, Rect bounds,
            int line, float baseline, boolean rtl, Consumer<Canvas> nativeDraw) {
        if (source != field || destroyed || surface == null || !surface.hasCursorEffect()
                || bounds == null || bounds.isEmpty() || source.getSelectionStart() < 0
                || source.getSelectionStart() != source.getSelectionEnd()) {
            nativeDraw.accept(canvas);
            return;
        }

        int offset = source.getSelectionStart();
        Layout layout = source.getLayout();

        if (layout == null || line < 0 || line >= layout.getLineCount()) {
            nativeDraw.accept(canvas);
            return;
        }

        if (cursorOffset != offset || cursorLine != line || cursorBaseline != baseline
                || cursorRtl != rtl || !cursorBounds.equals(bounds)) {
            cursorOffset = offset;
            cursorLine = line;
            cursorBaseline = baseline;
            cursorRtl = rtl;
            cursorBounds.set(bounds);
            cursorRevision++;
            cursorCapture.release();
        }

        surface.drawCursor(canvas, new ComposerCursorSnapshot(
                cursorRevision, offset, line,
                bounds.left, bounds.top, bounds.right, bounds.bottom, baseline, rtl), nativeDraw);
    }

    public void selectionChanged(EditTextCaption source, int start, int end) {
        if (source != field || destroyed || surface == null || !surface.hasSelectionEffect()) {
            return;
        }

        clearSelectionCapture();
        surface.selectionChanged(null, null);
        field.invalidate();
    }

    public void destroy() {
        destroyed = true;
        hide();
        removeObserver();
        field.removeTextChangedListener(this);
        field.removeOnAttachStateChangeListener(this);

        if (foreground != null) {
            foreground.removeListener(this);
        }
    }

    private void removeObserver() {
        if (observer != null && observer.isAlive()) {
            observer.removeOnPreDrawListener(this);
        }

        observer = null;
    }

    @Override
    public void onViewAttachedToWindow(View view) {
        if (destroyed) {
            return;
        }

        removeObserver();
        observer = field.getViewTreeObserver();
        observer.addOnPreDrawListener(this);
        refresh();
    }

    @Override
    public void onViewDetachedFromWindow(View view) {
        hide();
        removeObserver();
    }

    @Override
    public boolean onPreDraw() {
        refresh();

        if (surface != null) {
            deliverTextLayout();
            surface.prepareText();
            prepareSelection();
        }

        return true;
    }

    @Override
    public void onBecameForeground() {
        refresh();
    }

    @Override
    public void onBecameBackground() {
        hide();
    }

    @Override
    public void beforeTextChanged(CharSequence text, int start, int count, int after) {
        if (surface != null && surface.hasEffects()) {
            inputChanges.beforeChange(text, start, count, after);
        } else {
            inputChanges.reset();
        }

        beforeText = null;

        if (surface != null && surface.hasTextLayoutEffect() && field instanceof EditTextCaption
                && text != null && text.length() <= ComposerInputChanges.MAX_TEXT_LENGTH) {
            if (!pendingLayoutChange) {
                pendingLayoutChange = true;
                pendingLayoutBefore = ComposerTextLayoutSnapshots.capture(
                        (EditTextCaption) field, textRevision, text.toString());

                if (surface.hasTextTransitionEffect() && count > 0 && start >= 0
                        && start <= text.length() && count <= text.length() - start) {
                    pendingRemovedCapture = ComposerRemovedTextCapture.capture(
                            (EditTextCaption) field, textRevision, text.toString(), start, start + count);
                }
            } else {
                pendingLayoutCoalesced = true;
                pendingRemovedCapture = null;
            }
        } else {
            clearPendingLayout();
        }

        if (surface != null && surface.hasTextEffect() && text != null
                && text.length() <= ComposerInputChanges.MAX_TEXT_LENGTH
                && text.length() - count + after <= ComposerInputChanges.MAX_TEXT_LENGTH
                && start >= 0 && count >= 0 && after >= 0 && start <= text.length()
                && count <= text.length() - start) {
            beforeText = text.toString();
            changeStart = start;
            changeRemoved = count;
            changeInserted = after;
        }
    }

    @Override
    public void onTextChanged(CharSequence text, int start, int before, int count) {
        inputChanges.changed(text, start, before, count);
    }

    @Override
    public void afterTextChanged(Editable text) {
        if (surface != null && surface.hasEffects() && available()) {
            boolean composing = text.length() <= ComposerInputChanges.MAX_TEXT_LENGTH
                    && BaseInputConnection.getComposingSpanStart(text) >= 0;
            ComposerInput input = inputChanges.take(text.length(), composing);

            if (input != null) {
                surface.input(input);
            }

            if (surface.hasTextEffect()) {
                textRevision++;
                ComposerTextChange change = null;

                if (beforeText != null && text.length() <= ComposerInputChanges.MAX_TEXT_LENGTH) {
                    change = new ComposerTextChange(textRevision, beforeText, text.toString(), changeStart,
                            changeRemoved, changeInserted, field.getSelectionStart(), field.getSelectionEnd(),
                            BaseInputConnection.getComposingSpanStart(text),
                            BaseInputConnection.getComposingSpanEnd(text));
                }

                surface.textChanged(change);

                if (pendingLayoutBefore != null && text.length() <= ComposerInputChanges.MAX_TEXT_LENGTH) {
                    pendingLayoutAfterText = text.toString();
                } else {
                    clearPendingLayout();
                }
            }
        } else {
            inputChanges.reset();
            clearPendingLayout();
        }

        beforeText = null;
    }

    private void deliverTextLayout() {
        if (pendingLayoutBefore == null || pendingLayoutAfterText == null
                || !(field instanceof EditTextCaption) || surface == null
                || !surface.hasTextLayoutEffect()) {
            return;
        }

        ComposerTextLayoutSnapshot before = pendingLayoutBefore;
        String afterText = pendingLayoutAfterText;
        PluginComposerRegistry.OverlayTextCapture removedCapture = pendingRemovedCapture;
        boolean coalesced = pendingLayoutCoalesced;
        ComposerTextLayoutSnapshot after = ComposerTextLayoutSnapshots.capture(
                (EditTextCaption) field, textRevision, afterText);
        clearPendingLayout();

        if (after == null) {
            return;
        }

        int prefix = 0;
        int beforeLength = before.text.length();
        int afterLength = after.text.length();
        int sharedLength = Math.min(beforeLength, afterLength);

        while (prefix < sharedLength && before.text.charAt(prefix) == after.text.charAt(prefix)) {
            prefix++;
        }

        int suffix = 0;

        while (suffix < beforeLength - prefix && suffix < afterLength - prefix
                && before.text.charAt(beforeLength - suffix - 1) == after.text.charAt(afterLength - suffix - 1)) {
            suffix++;
        }

        int removedCount = beforeLength - prefix - suffix;

        if (coalesced || removedCapture == null || removedCapture.getStart() != prefix
                || removedCapture.getEnd() != prefix + removedCount) {
            removedCapture = null;
        }

        surface.textLayoutChanged(new ComposerTextLayoutChange(
                after.revision,
                before,
                after,
                prefix,
                removedCount,
                afterLength - prefix - suffix,
                coalesced,
                removedCapture));
    }

    private void prepareSelection() {
        if (!(field instanceof EditTextCaption) || surface == null || !surface.hasSelectionEffect()) {
            clearSelectionCapture();
            return;
        }

        if (selectionCapture != null && selectionCapture.isValid()) {
            return;
        }

        clearSelectionCapture();
        ComposerSelectionCapture capture;

        try {
            capture = ComposerSelectionCapture.capture((EditTextCaption) field, ++selectionRevision);
        } catch (Exception | LinkageError | AssertionError | OutOfMemoryError error) {
            FileLog.e(error);
            capture = null;
        }

        if (capture != null) {
            selectionCapture = capture;
            ComposerSelectionSnapshot snapshot = capture.snapshot();
            surface.selectionChanged(snapshot, capture);
        } else {
            surface.selectionChanged(null, null);
        }
    }

    private void clearSelectionCapture() {
        selectionCapture = null;
    }

    private void clearPendingLayout() {
        pendingLayoutBefore = null;
        pendingLayoutAfterText = null;
        pendingRemovedCapture = null;
        pendingLayoutChange = false;
        pendingLayoutCoalesced = false;
    }
}

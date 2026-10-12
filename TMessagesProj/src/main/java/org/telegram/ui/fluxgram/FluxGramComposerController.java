package org.telegram.ui.fluxgram;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.inputmethod.BaseInputConnection;
import android.widget.EditText;

import org.telegram.messenger.LiteMode;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.plugins.PluginComposerRegistry;
import org.telegram.messenger.plugins.api.ComposerCursorPresentation;
import org.telegram.messenger.plugins.api.ComposerCursorSnapshot;
import org.telegram.messenger.plugins.api.ComposerExpandedBounds;
import org.telegram.messenger.plugins.api.ComposerGeometry;
import org.telegram.messenger.plugins.api.ComposerInput;
import org.telegram.messenger.plugins.api.ComposerSelectionSnapshot;
import org.telegram.messenger.plugins.api.ComposerTextCandidate;
import org.telegram.messenger.plugins.api.ComposerTextChange;
import org.telegram.messenger.plugins.api.ComposerTextInsertion;
import org.telegram.messenger.plugins.api.ComposerTextLayoutChange;
import org.telegram.messenger.plugins.api.ComposerTextLayoutSnapshot;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ForegroundDetector;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.chat.ChatInputViewsContainer;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class FluxGramComposerController implements TextWatcher,
        View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener, ForegroundDetector.Listener {
    private final EditText field;
    private final int account;
    private final long ownerId;
    private final long dialogId;
    private final ChatInputViewsContainer expandedHost;
    private final BooleanSupplier eligible;
    private final Theme.ResourcesProvider colors;
    private final ForegroundDetector foreground;
    private final Drawable overlay;
    private View overlayHost;
    private View expandedLayer;
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
    private String pendingLayoutBeforeText;
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
    private final RectF sendBounds = new RectF();
    private final int[] fieldLocation = new int[2];
    private final int[] sendLocation = new int[2];
    private final int[] expandedLocation = new int[2];
    private long sendTransitionDeadline;

    public FluxGramComposerController(EditText field, ChatInputViewsContainer expandedHost,
            int account, long dialogId,
            BooleanSupplier eligible, Theme.ResourcesProvider colors) {
        this.field = field;
        this.expandedHost = expandedHost;
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
                    Rect bounds = getBounds();
                    int save = canvas.save();

                    try {
                        canvas.translate(bounds.left, bounds.top);
                        surface.draw(canvas);
                    } finally {
                        canvas.restoreToCount(save);
                    }
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
                && field.hasFocus() && field.hasWindowFocus()
                && (field.getAlpha() > 0 || SystemClock.uptimeMillis() <= sendTransitionDeadline)
                && field.getWindowVisibility() == View.VISIBLE
                && field.getWidth() > 0 && field.getHeight() > 0
                && foreground != null && foreground.isForeground()
                && !LiteMode.isPowerSaverApplied() && LiteMode.isEnabled(LiteMode.FLAG_CHAT_BACKGROUND);
    }

    public void refresh() {
        if (surface != null && surface.isClosed()) {
            removeOverlay();
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
        ViewParent parent = field.getParent();
        View currentOverlayHost = parent instanceof View ? (View) parent : null;

        if (currentOverlayHost == null) {
            hide();
            return;
        }

        if (overlayHost != currentOverlayHost) {
            removeOverlay();
            overlayHost = currentOverlayHost;

            if (surface != null) {
                overlayHost.getOverlay().add(overlay);
            }
        }

        int overlayLeft = field.getLeft() + Math.round(field.getTranslationX());
        int overlayTop = field.getTop() + Math.round(field.getTranslationY());
        overlay.setBounds(overlayLeft, overlayTop, overlayLeft + width, overlayTop + height);

        if (geometry == null || geometry.width != width || geometry.height != height
                || geometry.density != density || geometry.accentColor != accent || geometry.rtl != rtl) {
            geometry = new ComposerGeometry(width, height, density, accent, rtl);
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

                    if (expandedLayer != null) {
                        expandedLayer.invalidate();
                    }
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
                public void effectsChanged() {
                    refreshExpandedLayer();
                    invalidate();
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
                overlayHost.getOverlay().add(overlay);
            }
        }

        refreshExpandedLayer();
    }

    private void refreshExpandedLayer() {
        if (surface == null || !surface.hasExpandedEffect()) {
            removeExpandedLayer();
            return;
        }

        if (expandedLayer != null) {
            return;
        }

        expandedLayer = new View(field.getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                FluxGramComposerController.this.drawExpanded(canvas, this);
            }
        };
        expandedLayer.setWillNotDraw(false);
        expandedLayer.setClickable(false);
        expandedLayer.setFocusable(false);
        expandedLayer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int keyboardIndex = expandedHost.indexOfChild(expandedHost.getInAppKeyboardBubbleContainer());
        expandedHost.addView(expandedLayer, Math.max(0, keyboardIndex),
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    private void drawExpanded(Canvas canvas, View layer) {
        if (surface == null || !surface.hasExpandedEffect() || !available()
                || !layer.isAttachedToWindow() || layer.getWindowToken() != field.getWindowToken()
                || layer.getWidth() <= 0 || layer.getHeight() <= 0) {
            return;
        }

        field.getLocationInWindow(fieldLocation);
        layer.getLocationInWindow(expandedLocation);
        int fieldLeft = fieldLocation[0] - expandedLocation[0];
        int fieldTop = fieldLocation[1] - expandedLocation[1];
        int outset = Math.round(ComposerExpandedBounds.MAX_OUTSET_DP
                * field.getResources().getDisplayMetrics().density);
        int left = Math.max(-outset, -fieldLeft);
        int top = Math.max(-outset, -fieldTop);
        int right = Math.min(field.getWidth() + outset, layer.getWidth() - fieldLeft);
        int bottom = Math.min(field.getHeight() + outset, layer.getHeight() - fieldTop);

        if (left >= right || top >= bottom) {
            return;
        }

        ComposerExpandedBounds bounds = new ComposerExpandedBounds(left, top, right, bottom);
        int save = canvas.save();

        try {
            canvas.clipRect(fieldLeft + left, fieldTop + top, fieldLeft + right, fieldTop + bottom);
            canvas.translate(fieldLeft, fieldTop);
            surface.drawExpanded(canvas, bounds);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private void removeExpandedLayer() {
        if (expandedLayer != null) {
            ViewParent parent = expandedLayer.getParent();

            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(expandedLayer);
            }

            expandedLayer = null;
        }
    }

    private void removeOverlay() {
        if (overlayHost != null) {
            overlayHost.getOverlay().remove(overlay);
            overlayHost = null;
        }
    }

    private void hide() {
        if (surface != null) {
            PluginComposerRegistry.Surface previous = surface;
            surface = null;
            removeOverlay();
            previous.close();
        }

        sendTransitionDeadline = 0;
        removeExpandedLayer();

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

    public void sent(EditTextCaption source, View button) {
        if (source != field || destroyed) {
            return;
        }

        sendTransitionDeadline = SystemClock.uptimeMillis() + 1_000;
        refresh();

        if (surface == null || !surface.hasSendEffect()) {
            sendTransitionDeadline = 0;
            return;
        }

        boolean hasTarget = button instanceof ChatActivityEnterView.SendButton
                && button.isAttachedToWindow() && button.isShown()
                && button.getWindowToken() == field.getWindowToken()
                && button.getWidth() > 0 && button.getHeight() > 0;
        float targetX = 0;
        float targetY = 0;

        if (hasTarget) {
            ((ChatActivityEnterView.SendButton) button).getBounds(sendBounds);
            field.getLocationInWindow(fieldLocation);
            button.getLocationInWindow(sendLocation);
            targetX = sendLocation[0] - fieldLocation[0] + sendBounds.centerX();
            targetY = sendLocation[1] - fieldLocation[1] + sendBounds.centerY();
            hasTarget = !sendBounds.isEmpty() && Float.isFinite(targetX) && Float.isFinite(targetY);
        }

        surface.sent(hasTarget, targetX, targetY);
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

        if (surface != null && (surface.hasTextLayoutEffect() || surface.hasTextInsertionEffect())
                && field instanceof EditTextCaption
                && text != null && text.length() <= ComposerInputChanges.MAX_TEXT_LENGTH) {
            if (!pendingLayoutChange) {
                pendingLayoutChange = true;
                pendingLayoutBeforeText = text.toString();

                if (surface.hasTextLayoutEffect()) {
                    pendingLayoutBefore = ComposerTextLayoutSnapshots.capture(
                            (EditTextCaption) field, textRevision, pendingLayoutBeforeText);
                }

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

            boolean textEffect = surface.hasTextEffect();
            boolean insertionEffect = surface.hasTextInsertionEffect();

            if (textEffect || insertionEffect) {
                textRevision++;
            }

            if (textEffect) {
                ComposerTextChange change = null;

                if (beforeText != null && text.length() <= ComposerInputChanges.MAX_TEXT_LENGTH) {
                    change = new ComposerTextChange(textRevision, beforeText, text.toString(), changeStart,
                            changeRemoved, changeInserted, field.getSelectionStart(), field.getSelectionEnd(),
                            BaseInputConnection.getComposingSpanStart(text),
                            BaseInputConnection.getComposingSpanEnd(text));
                }

                surface.textChanged(change);
            }

            if (pendingLayoutBeforeText != null && text.length() <= ComposerInputChanges.MAX_TEXT_LENGTH) {
                pendingLayoutAfterText = text.toString();
            } else {
                clearPendingLayout();
            }
        } else {
            inputChanges.reset();
            clearPendingLayout();
        }

        beforeText = null;
    }

    private void deliverTextLayout() {
        if (pendingLayoutBeforeText == null || pendingLayoutAfterText == null
                || !(field instanceof EditTextCaption)) {
            return;
        }

        if (surface == null
                || !surface.hasTextLayoutEffect() && !surface.hasTextInsertionEffect()) {
            clearPendingLayout();
            return;
        }

        ComposerTextLayoutSnapshot before = pendingLayoutBefore;
        String beforeText = pendingLayoutBeforeText;
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
        int beforeLength = beforeText.length();
        int afterLength = after.text.length();
        int sharedLength = Math.min(beforeLength, afterLength);

        while (prefix < sharedLength && beforeText.charAt(prefix) == after.text.charAt(prefix)) {
            prefix++;
        }

        int suffix = 0;

        while (suffix < beforeLength - prefix && suffix < afterLength - prefix
                && beforeText.charAt(beforeLength - suffix - 1) == afterText.charAt(afterLength - suffix - 1)) {
            suffix++;
        }

        int removedCount = beforeLength - prefix - suffix;

        if (coalesced || removedCapture == null || removedCapture.getStart() != prefix
                || removedCapture.getEnd() != prefix + removedCount) {
            removedCapture = null;
        }

        int insertedCount = afterLength - prefix - suffix;

        if (surface.hasTextLayoutEffect() && before != null) {
            surface.textLayoutChanged(new ComposerTextLayoutChange(
                    after.revision,
                    before,
                    after,
                    prefix,
                    removedCount,
                    insertedCount,
                    coalesced,
                    removedCapture));
        }

        if (surface.hasTextInsertionEffect()) {
            ComposerTextInsertion insertion = ComposerTextInsertions.create(
                    after, prefix, removedCount, insertedCount, coalesced);

            if (insertion != null) {
                surface.textInserted(insertion);
            }
        }
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
        pendingLayoutBeforeText = null;
        pendingLayoutAfterText = null;
        pendingRemovedCapture = null;
        pendingLayoutChange = false;
        pendingLayoutCoalesced = false;
    }
}

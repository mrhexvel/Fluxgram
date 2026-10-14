package org.telegram.ui.fluxgram;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.inputmethod.BaseInputConnection;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.plugins.ComposerInputRendererExtensions;
import org.telegram.messenger.plugins.ComposerInputRendererHostSurface;
import org.telegram.messenger.plugins.PluginComposerRegistry;
import org.telegram.messenger.plugins.PluginManagedUi;
import org.telegram.messenger.plugins.api.ComposerCursorPresentation;
import org.telegram.messenger.plugins.api.ComposerCursorSnapshot;
import org.telegram.messenger.plugins.api.ComposerExpandedBounds;
import org.telegram.messenger.plugins.api.ComposerGeometry;
import org.telegram.messenger.plugins.api.ComposerHapticFeedback;
import org.telegram.messenger.plugins.api.ComposerInput;
import org.telegram.messenger.plugins.api.ComposerSelectionSnapshot;
import org.telegram.messenger.plugins.api.ComposerTextCandidate;
import org.telegram.messenger.plugins.api.ComposerTextClusterSelection;
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
    private final ViewGroup expandedHost;
    private final String previewPluginId;
    private final BooleanSupplier eligible;
    private final Theme.ResourcesProvider colors;
    private final ForegroundDetector foreground;
    private final Drawable overlay;
    private final PluginManagedUi.Scope activityScope;
    private final PluginManagedUi.Scope fragmentScope;
    private PluginManagedUi.Scope viewScope;
    private PluginManagedUi.Scope surfaceScope;
    private ComposerInputRendererHostSurface rendererSurface;
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
    private long pendingLayoutDeadline;
    private final ComposerNativeCursorCapture cursorCapture = new ComposerNativeCursorCapture();
    private final Rect cursorBounds = new Rect();
    private long cursorRevision;
    private int cursorOffset = -1;
    private int cursorLine = -1;
    private float cursorBaseline = Float.NaN;
    private float cursorContentOffsetY = Float.NaN;
    private boolean cursorRtl;
    private ComposerSelectionCapture selectionCapture;
    private long selectionRevision;
    private final RectF sendBounds = new RectF();
    private final int[] fieldLocation = new int[2];
    private final int[] sendLocation = new int[2];
    private final int[] expandedLocation = new int[2];
    private long sendTransitionDeadline;
    private boolean destroyCleanupScheduled;
    private boolean destroyCleanupComplete;
    private long destroyCleanupDelay = 16;
    private boolean surfaceCleanupScheduled;
    private long surfaceCleanupDelay = 16;

    public FluxGramComposerController(EditText field, ChatInputViewsContainer expandedHost,
            int account, long dialogId,
            BooleanSupplier eligible, Theme.ResourcesProvider colors) {
        this(field, expandedHost, account, dialogId, null, eligible, colors);
    }

    public FluxGramComposerController(EditText field, ViewGroup expandedHost,
            int account, long dialogId, String previewPluginId,
            BooleanSupplier eligible, Theme.ResourcesProvider colors) {
        this.field = field;
        this.expandedHost = expandedHost;
        this.account = account;
        this.dialogId = dialogId;
        this.previewPluginId = previewPluginId;
        this.ownerId = UserConfig.getInstance(account).getClientUserId();
        this.eligible = eligible;
        this.colors = colors;
        foreground = ForegroundDetector.getInstance();
        activityScope = PluginManagedUi.openActivity(
                account, ownerId, PluginManagedUi.nextIdentity(), this::identityAvailable);
        fragmentScope = activityScope.openFragment(
                PluginManagedUi.nextIdentity(), () -> !destroyed);
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
        boolean initialized = false;

        try {
            fragmentScope.register(() -> field.removeOnAttachStateChangeListener(this));
            field.addOnAttachStateChangeListener(this);
            fragmentScope.register(() -> field.removeTextChangedListener(this));
            field.addTextChangedListener(this);

            if (foreground != null) {
                activityScope.register(() -> foreground.removeListener(this));
                foreground.addListener(this);
            }

            if (field.isAttachedToWindow()) {
                onViewAttachedToWindow(field);
            }

            initialized = true;
        } finally {
            if (!initialized) {
                try {
                    activityScope.close();
                } catch (Exception | LinkageError | AssertionError error) {
                    FileLog.e(error);
                    new ScopeCleanupRetry(activityScope).schedule();
                }
            }
        }
    }

    private boolean identityAvailable() {
        return !destroyed && ownerId > 0
                && UserConfig.getInstance(account).getClientUserId() == ownerId;
    }

    private boolean available() {
        return identityAvailable() && eligible.getAsBoolean()
                && UserConfig.selectedAccount == account
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
            surface = null;
            removeOverlay();
            removeExpandedLayer();
        }

        if (surfaceScope != null && !surfaceScope.isActive()) {
            closeSurfaceScope();

            if (surfaceScope != null) {
                return;
            }
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

        if (surfaceScope == null || (surface == null && rendererSurface == null)) {
            if (viewScope == null || !viewScope.isActive()) {
                hide();
                return;
            }

            PluginComposerRegistry.Host host = new PluginComposerRegistry.Host() {
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
                public void invalidateText() {
                    field.invalidate();
                    invalidate();
                }

                @Override
                public void invalidateCursor() {
                    field.invalidate();
                    invalidate();
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
                public boolean performHapticFeedback(int feedback) {
                    int platformFeedback;

                    if (feedback == ComposerHapticFeedback.TICK) {
                        platformFeedback = HapticFeedbackConstants.CLOCK_TICK;
                    } else if (feedback == ComposerHapticFeedback.IMPACT) {
                        platformFeedback = HapticFeedbackConstants.KEYBOARD_TAP;
                    } else if (feedback == ComposerHapticFeedback.CONFIRM) {
                        platformFeedback = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                                ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY;
                    } else if (feedback == ComposerHapticFeedback.REJECT) {
                        platformFeedback = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                                ? HapticFeedbackConstants.REJECT : HapticFeedbackConstants.LONG_PRESS;
                    } else {
                        return false;
                    }

                    return field.performHapticFeedback(platformFeedback);
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
                public PluginComposerRegistry.TextClusterCapture captureTextClusters(
                        ComposerTextClusterSelection selection, String expectedText) {
                    if (!(field instanceof EditTextCaption)) {
                        return null;
                    }

                    return ComposerNativeTextClusterCapture.capture(
                            (EditTextCaption) field, selection, expectedText);
                }

                @Override
                public void drawCursor(Canvas canvas, Consumer<Canvas> nativeDraw,
                        ComposerCursorSnapshot snapshot, ComposerCursorPresentation presentation) {
                    cursorCapture.draw(canvas, nativeDraw, snapshot, presentation, cursorContentOffsetY);
                }
            };
            boolean creatingScope = surfaceScope == null;
            PluginManagedUi.Scope nextScope = surfaceScope;
            PluginComposerRegistry.Surface nextSurface = null;
            ComposerInputRendererHostSurface nextRenderer = null;
            boolean surfaceManaged = false;
            boolean rendererManaged = false;

            try {
                if (creatingScope) {
                    nextScope = viewScope.openSurface(
                            PluginManagedUi.nextIdentity(), this::available);
                    nextScope.register(this::removeOverlay);
                    nextScope.register(this::removeExpandedLayer);

                    if (previewPluginId == null) {
                        nextRenderer = ComposerInputRendererHostSurface.attach(
                                field, account, ownerId, dialogId, () -> geometry,
                                this::available, () -> {
                                    field.invalidate();

                                    if (expandedLayer != null) {
                                        expandedLayer.invalidate();
                                    }
                                });
                    }
                }

                nextSurface = previewPluginId == null
                        ? PluginComposerRegistry.attach(host)
                        : PluginComposerRegistry.attachPreview(host, previewPluginId);

                if (nextSurface == null && nextRenderer == null && creatingScope) {
                    try {
                        nextScope.close();
                    } catch (Exception | LinkageError | AssertionError cleanupError) {
                        surfaceScope = nextScope;
                        scheduleSurfaceCleanup();
                        FileLog.e(cleanupError);
                    }

                    return;
                }

                if (nextSurface != null) {
                    nextScope.register(nextSurface);
                    surfaceManaged = true;
                    surface = nextSurface;
                    overlayHost.getOverlay().add(overlay);
                }

                if (nextRenderer != null) {
                    nextScope.register(nextRenderer);
                    rendererManaged = true;
                    rendererSurface = nextRenderer;
                }

                if (creatingScope) {
                    surfaceScope = nextScope;
                }
            } catch (RuntimeException | LinkageError | AssertionError error) {
                surface = null;
                rendererSurface = null;
                surfaceScope = null;

                if (nextScope != null) {
                    try {
                        nextScope.close();
                    } catch (Exception | LinkageError | AssertionError cleanupError) {
                        error.addSuppressed(cleanupError);
                        surfaceScope = nextScope;
                        scheduleSurfaceCleanup();
                    }
                }

                if (nextSurface != null && !surfaceManaged && !nextSurface.isClosed()) {
                    nextSurface.close();
                }

                if (nextRenderer != null && !rendererManaged) {
                    try {
                        nextRenderer.close();
                    } catch (Exception | LinkageError | AssertionError cleanupError) {
                        error.addSuppressed(cleanupError);
                    }
                }

                removeOverlay();
                removeExpandedLayer();
                FileLog.e(error);
                return;
            }
        }

        try {
            refreshExpandedLayer();
        } catch (RuntimeException | LinkageError | AssertionError error) {
            hide();
            FileLog.e(error);
        }
    }

    private void refreshExpandedLayer() {
        boolean legacyExpanded = surface != null && surface.hasExpandedEffect();
        boolean rendererExpanded = rendererSurface != null && rendererSurface.hasCapability(
                ComposerInputRendererExtensions.CAPABILITY_EXPANDED_DRAW);

        if (!legacyExpanded && !rendererExpanded || expandedHost == null) {
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
        int layerIndex = expandedHost.getChildCount();

        if (expandedHost instanceof ChatInputViewsContainer) {
            ChatInputViewsContainer chatHost = (ChatInputViewsContainer) expandedHost;
            layerIndex = Math.max(0, chatHost.indexOfChild(chatHost.getInAppKeyboardBubbleContainer()));
        }

        expandedHost.addView(expandedLayer, layerIndex,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    private void drawExpanded(Canvas canvas, View layer) {
        boolean legacyExpanded = surface != null && surface.hasExpandedEffect();
        boolean rendererExpanded = rendererSurface != null && rendererSurface.hasCapability(
                ComposerInputRendererExtensions.CAPABILITY_EXPANDED_DRAW);

        if (!legacyExpanded && !rendererExpanded || !available()
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
            if (legacyExpanded) {
                surface.drawExpanded(canvas, bounds);
            }

            if (rendererExpanded) {
                rendererSurface.drawExpanded(canvas, bounds);
            }
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private void removeExpandedLayer() {
        View layer = expandedLayer;
        expandedLayer = null;

        if (layer == null) {
            return;
        }

        AndroidUtilities.runOnUIThread(() -> {
            ViewParent parent = layer.getParent();

            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(layer);
            }
        });
    }

    private void removeOverlay() {
        if (overlayHost != null) {
            overlayHost.getOverlay().remove(overlay);
            overlayHost = null;
        }
    }

    private void hide() {
        PluginComposerRegistry.Surface previous = surface;
        surface = null;
        rendererSurface = null;
        closeSurfaceScope();

        if (previous != null && surfaceScope == null && !previous.isClosed()) {
            previous.close();
        }

        sendTransitionDeadline = 0;
        removeOverlay();
        removeExpandedLayer();

        inputChanges.reset();
        beforeText = null;
        clearPendingLayout();
        cursorCapture.release();
        clearSelectionCapture();
    }

    public void drawText(EditTextCaption source, Canvas canvas, Consumer<Canvas> nativeDraw) {
        Consumer<Canvas> legacyDraw = target -> {
            if (source == field && !destroyed && surface != null) {
                surface.drawContent(target,
                        contentCanvas -> surface.drawText(contentCanvas, nativeDraw));
            } else {
                nativeDraw.accept(target);
            }
        };

        if (source == field && !destroyed && rendererSurface != null && available()) {
            rendererSurface.draw(canvas, legacyDraw);
        } else {
            legacyDraw.accept(canvas);
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

        float contentOffsetY = source.getOffsetY() - source.getScrollY();

        if (cursorOffset != offset || cursorLine != line || cursorBaseline != baseline
                || cursorContentOffsetY != contentOffsetY
                || cursorRtl != rtl || !cursorBounds.equals(bounds)) {
            cursorOffset = offset;
            cursorLine = line;
            cursorBaseline = baseline;
            cursorContentOffsetY = contentOffsetY;
            cursorRtl = rtl;
            cursorBounds.set(bounds);
            cursorRevision++;
            cursorCapture.release();
        }

        surface.drawCursor(canvas, new ComposerCursorSnapshot(
                cursorRevision, offset, line,
                bounds.left, bounds.top + contentOffsetY,
                bounds.right, bounds.bottom + contentOffsetY,
                baseline + contentOffsetY, rtl), nativeDraw);
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

        boolean legacySend = surface != null && surface.hasSendEffect();
        boolean rendererSend = rendererSurface != null && rendererSurface.hasCapability(
                ComposerInputRendererExtensions.CAPABILITY_SEND);

        if (!legacySend && !rendererSend) {
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

        if (legacySend) {
            surface.sent(hasTarget, targetX, targetY);
        }

        if (rendererSend) {
            rendererSurface.sent(hasTarget, targetX, targetY);
        }
    }

    public void destroy() {
        destroyed = true;
        surface = null;
        finishDestroyCleanup();
    }

    private void finishDestroyCleanup() {
        if (destroyCleanupComplete) {
            return;
        }

        try {
            activityScope.close();
        } catch (Exception | LinkageError | AssertionError error) {
            FileLog.e(error);

            if (!destroyCleanupScheduled) {
                destroyCleanupScheduled = true;
                long delay = destroyCleanupDelay;
                destroyCleanupDelay = Math.min(30_000, destroyCleanupDelay * 2);
                AndroidUtilities.runOnUIThread(() -> {
                    destroyCleanupScheduled = false;
                    finishDestroyCleanup();
                }, delay);
            }

            return;
        }

        destroyCleanupComplete = true;
        viewScope = null;
        surfaceScope = null;

        removeOverlay();
        removeExpandedLayer();
        inputChanges.reset();
        beforeText = null;
        clearPendingLayout();
        cursorCapture.release();
        clearSelectionCapture();
    }

    private void removeObserver() {
        if (observer != null && observer.isAlive()) {
            observer.removeOnPreDrawListener(this);
        }

        observer = null;
    }

    private void closeSurfaceScope() {
        if (surfaceCleanupScheduled) {
            return;
        }

        PluginManagedUi.Scope previous = surfaceScope;
        surfaceScope = null;

        if (previous == null) {
            return;
        }

        try {
            previous.close();
            surfaceCleanupDelay = 16;
        } catch (Exception | LinkageError | AssertionError error) {
            surfaceScope = previous;
            FileLog.e(error);
            scheduleSurfaceCleanup();
        }
    }

    private void scheduleSurfaceCleanup() {
        if (surfaceCleanupScheduled) {
            return;
        }

        surfaceCleanupScheduled = true;
        long delay = surfaceCleanupDelay;
        surfaceCleanupDelay = Math.min(30_000, surfaceCleanupDelay * 2);
        AndroidUtilities.runOnUIThread(() -> {
            surfaceCleanupScheduled = false;
            closeSurfaceScope();
        }, delay);
    }

    private void closeViewScope() {
        PluginManagedUi.Scope previous = viewScope;
        viewScope = null;

        if (previous == null) {
            removeObserver();
            return;
        }

        try {
            previous.close();
        } catch (Exception | LinkageError | AssertionError error) {
            viewScope = previous;
            FileLog.e(error);
        }
    }

    @Override
    public void onViewAttachedToWindow(View view) {
        if (destroyed || !activityScope.isActive() || !fragmentScope.isActive()) {
            return;
        }

        hide();
        closeViewScope();

        if (viewScope != null) {
            return;
        }

        viewScope = fragmentScope.openView(
                PluginManagedUi.nextIdentity(), () -> identityAvailable()
                        && field.isAttachedToWindow()
                        && foreground != null && foreground.isForeground());
        observer = field.getViewTreeObserver();
        ViewTreeObserver attachedObserver = observer;
        viewScope.register(() -> {
            if (attachedObserver.isAlive()) {
                attachedObserver.removeOnPreDrawListener(this);
            }

            if (observer == attachedObserver) {
                observer = null;
            }
        });
        observer.addOnPreDrawListener(this);
        refresh();
    }

    @Override
    public void onViewDetachedFromWindow(View view) {
        hide();
        closeViewScope();
    }

    @Override
    public boolean onPreDraw() {
        refresh();

        if (surface != null || rendererSurface != null) {
            deliverTextLayout();

            if (surface != null) {
                surface.prepareText();
                prepareSelection();
            }
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

    private static final class ScopeCleanupRetry implements Runnable {
        private final PluginManagedUi.Scope scope;
        private long delay = 16;

        ScopeCleanupRetry(PluginManagedUi.Scope scope) {
            this.scope = scope;
        }

        void schedule() {
            AndroidUtilities.runOnUIThread(this, delay);
            delay = Math.min(30_000, delay * 2);
        }

        @Override
        public void run() {
            try {
                scope.close();
            } catch (Exception | LinkageError | AssertionError error) {
                FileLog.e(error);
                schedule();
            }
        }
    }

    @Override
    public void beforeTextChanged(CharSequence text, int start, int count, int after) {
        boolean legacyEffects = surface != null && surface.hasEffects();
        boolean rendererInput = rendererSurface != null && rendererSurface.hasCapability(
                ComposerInputRendererExtensions.CAPABILITY_INPUT);
        boolean rendererLayout = rendererSurface != null && rendererSurface.hasCapability(
                ComposerInputRendererExtensions.CAPABILITY_TEXT_LAYOUT);

        if (legacyEffects || rendererInput) {
            inputChanges.beforeChange(text, start, count, after);
        } else {
            inputChanges.reset();
        }

        beforeText = null;

        if ((surface != null && (surface.hasTextLayoutEffect() || surface.hasTextInsertionEffect())
                || rendererLayout)
                && field instanceof EditTextCaption
                && text != null && text.length() <= ComposerInputChanges.MAX_TEXT_LENGTH) {
            pendingLayoutDeadline = SystemClock.uptimeMillis() + 500;

            if (!pendingLayoutChange) {
                pendingLayoutChange = true;
                pendingLayoutBeforeText = text.toString();

                if (rendererLayout || surface != null && surface.hasTextLayoutEffect()) {
                    pendingLayoutBefore = ComposerTextLayoutSnapshots.capture(
                            (EditTextCaption) field, textRevision, pendingLayoutBeforeText);
                }

                if (surface != null && surface.hasTextTransitionEffect()
                        && count > 0 && start >= 0
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
        boolean legacyEffects = surface != null && surface.hasEffects();
        boolean rendererInput = rendererSurface != null && rendererSurface.hasCapability(
                ComposerInputRendererExtensions.CAPABILITY_INPUT);
        boolean rendererLayout = rendererSurface != null && rendererSurface.hasCapability(
                ComposerInputRendererExtensions.CAPABILITY_TEXT_LAYOUT);

        if ((legacyEffects || rendererInput || rendererLayout) && available()) {
            boolean composing = text.length() <= ComposerInputChanges.MAX_TEXT_LENGTH
                    && BaseInputConnection.getComposingSpanStart(text) >= 0;
            ComposerInput input = inputChanges.take(text.length(), composing);

            if (input != null) {
                if (legacyEffects) {
                    surface.input(input);
                }

                if (rendererInput) {
                    rendererSurface.input(input);
                }
            }

            boolean textEffect = surface != null && surface.hasTextEffect();
            boolean insertionEffect = surface != null && surface.hasTextInsertionEffect();

            if (textEffect || insertionEffect || rendererLayout) {
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

        boolean legacyLayout = surface != null && surface.hasTextLayoutEffect();
        boolean legacyInsertion = surface != null && surface.hasTextInsertionEffect();
        boolean rendererLayout = rendererSurface != null && rendererSurface.hasCapability(
                ComposerInputRendererExtensions.CAPABILITY_TEXT_LAYOUT);

        if (!legacyLayout && !legacyInsertion && !rendererLayout) {
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

        if (after == null) {
            if (SystemClock.uptimeMillis() < pendingLayoutDeadline) {
                field.postInvalidateOnAnimation();
            } else {
                clearPendingLayout();
            }

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

        if (insertedCount > 0 && after.truncated
                && !ComposerTextLayoutSnapshots.containsVisibleRange(after, prefix, insertedCount)
                && SystemClock.uptimeMillis() < pendingLayoutDeadline) {
            field.postInvalidateOnAnimation();
            return;
        }

        clearPendingLayout();

        if ((legacyLayout || rendererLayout) && before != null) {
            ComposerTextLayoutChange change = new ComposerTextLayoutChange(
                    after.revision,
                    before,
                    after,
                    prefix,
                    removedCount,
                    insertedCount,
                    coalesced,
                    removedCapture);

            if (legacyLayout) {
                surface.textLayoutChanged(change);
            }

            if (rendererLayout) {
                rendererSurface.textLayoutChanged(new ComposerTextLayoutChange(
                        change.revision,
                        change.before,
                        change.after,
                        change.start,
                        change.removedCount,
                        change.insertedCount,
                        change.coalesced,
                        null));
            }
        }

        if (legacyInsertion) {
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
        pendingLayoutDeadline = 0;
    }
}

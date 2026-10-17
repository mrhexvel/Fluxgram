package org.telegram.messenger.plugins;

import android.graphics.Canvas;
import android.hardware.SensorManager;
import android.view.Choreographer;

import org.fluxgram.ComposerEffect;
import org.fluxgram.ComposerContentEffect;
import org.fluxgram.ComposerContentPresentation;
import org.fluxgram.ComposerCursorEffect;
import org.fluxgram.ComposerCursorPresentation;
import org.fluxgram.ComposerCursorSnapshot;
import org.fluxgram.ComposerExpandedBounds;
import org.fluxgram.ComposerExpandedEffect;
import org.fluxgram.ComposerGeometry;
import org.fluxgram.ComposerHapticFeedback;
import org.fluxgram.ComposerInput;
import org.fluxgram.ComposerMotionEffect;
import org.fluxgram.ComposerMotionSnapshot;
import org.fluxgram.ComposerSelectionEffect;
import org.fluxgram.ComposerSelectionPresentation;
import org.fluxgram.ComposerSelectionSnapshot;
import org.fluxgram.ComposerSendEffect;
import org.fluxgram.ComposerSendEvent;
import org.fluxgram.ComposerSurface;
import org.fluxgram.ComposerTextCandidate;
import org.fluxgram.ComposerTextCapture;
import org.fluxgram.ComposerTextChange;
import org.fluxgram.ComposerTextClusterCapture;
import org.fluxgram.ComposerTextClusterEffect;
import org.fluxgram.ComposerTextClusterPresentation;
import org.fluxgram.ComposerTextClusterSelection;
import org.fluxgram.ComposerTextClusterSnapshot;
import org.fluxgram.ComposerTextEffect;
import org.fluxgram.ComposerTextFontSnapshot;
import org.fluxgram.ComposerTextInsertion;
import org.fluxgram.ComposerTextInsertionEffect;
import org.fluxgram.ComposerTextLayers;
import org.fluxgram.ComposerTextLayerTransitionEffect;
import org.fluxgram.ComposerTextLayoutChange;
import org.fluxgram.ComposerTextLayoutEffect;
import org.fluxgram.ComposerTextLayoutSnapshot;
import org.fluxgram.ComposerTextLineSnapshot;
import org.fluxgram.ComposerTextPresentation;
import org.fluxgram.ComposerTextRunSnapshot;
import org.fluxgram.ComposerTextTransitionEffect;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

public final class ComposerRegistryCheck {
    private static final class Host implements PluginComposerRegistry.Host {
        boolean available = true;
        int invalidations;
        int textInvalidations;
        int captures;
        ComposerTextCandidate capturedCandidate;
        ComposerTextClusterSelection capturedClusterSelection;
        ClusterCapture clusterCapture;
        int clusterCaptures;
        boolean failCapture;
        int cursorDraws;
        int cursorInvalidations;
        int cursorOwnerChanges;
        int selectionOwnerChanges;
        int effectsChanges;
        int haptics;
        int lastHaptic;
        boolean hapticResult = true;
        boolean failHaptic;
        ComposerCursorSnapshot cursorSnapshot;
        ComposerCursorPresentation cursorPresentation;
        final ComposerGeometry geometry = new ComposerGeometry(320, 50, 1, 0xFF123456, false);

        @Override
        public int account() {
            return 1;
        }

        @Override
        public long ownerId() {
            return 20;
        }

        @Override
        public long dialogId() {
            return 30;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public ComposerGeometry geometry() {
            return geometry;
        }

        @Override
        public void invalidate() {
            invalidations++;
        }

        @Override
        public void invalidateText() {
            textInvalidations++;
        }

        @Override
        public void invalidateCursor() {
            cursorInvalidations++;
        }

        @Override
        public void cursorOwnerChanged() {
            cursorOwnerChanges++;
        }

        @Override
        public void selectionOwnerChanged() {
            selectionOwnerChanges++;
        }

        @Override
        public void effectsChanged() {
            effectsChanges++;
        }

        @Override
        public boolean performHapticFeedback(int feedback) {
            if (failHaptic) {
                failHaptic = false;
                throw new IllegalStateException("intentional haptic failure");
            }

            haptics++;
            lastHaptic = feedback;
            return hapticResult;
        }

        @Override
        public PluginComposerRegistry.TextCapture captureText(ComposerTextCandidate candidate, String expectedText) {
            if (failCapture) {
                failCapture = false;
                throw new IllegalStateException("intentional host capture failure");
            }

            captures++;
            capturedCandidate = candidate;
            return new Capture(candidate);
        }

        @Override
        public PluginComposerRegistry.TextClusterCapture captureTextClusters(
                ComposerTextClusterSelection selection, String expectedText) {
            clusterCaptures++;
            capturedClusterSelection = selection;
            clusterCapture = new ClusterCapture(selection);
            return clusterCapture;
        }

        @Override
        public void drawCursor(Canvas canvas, java.util.function.Consumer<Canvas> nativeDraw,
                ComposerCursorSnapshot snapshot, ComposerCursorPresentation presentation) {
            cursorDraws++;
            cursorSnapshot = snapshot;
            cursorPresentation = presentation;
            nativeDraw.accept(canvas);
        }
    }

    private static final class Capture implements PluginComposerRegistry.TextCapture {
        final ComposerTextCandidate candidate;
        int draws;
        int releases;
        boolean valid = true;
        ComposerTextPresentation presentation;

        Capture(ComposerTextCandidate candidate) {
            this.candidate = candidate;
        }

        @Override
        public long getRevision() {
            return candidate.revision;
        }

        @Override
        public int getStart() {
            return candidate.start;
        }

        @Override
        public int getEnd() {
            return candidate.end;
        }

        @Override
        public float getLeft() {
            return 10;
        }

        @Override
        public float getTop() {
            return 5;
        }

        @Override
        public float getRight() {
            return 20;
        }

        @Override
        public float getBottom() {
            return 15;
        }

        @Override
        public float getBaseline() {
            return 14;
        }

        @Override
        public boolean isValid() {
            return valid;
        }

        @Override
        public void draw(Canvas canvas, java.util.function.Consumer<Canvas> nativeDraw,
                ComposerTextPresentation presentation) {
            draws++;
            this.presentation = presentation;
            nativeDraw.accept(canvas);
        }

        @Override
        public void release() {
            releases++;
        }
    }

    private static class Effect implements ComposerEffect {
        ComposerSurface context;
        ComposerInput input;
        int inputs;
        int frames;
        int draws;
        int closes;
        boolean failFrame;
        boolean failClose;
        boolean slow;
        boolean forever;

        @Override
        public void onInput(ComposerInput event) {
            input = event;
            inputs++;
        }

        @Override
        public boolean onFrame(long timestamp) {
            frames++;

            if (failFrame) {
                throw new IllegalStateException("intentional frame failure");
            }

            return forever;
        }

        @Override
        public void draw(Canvas canvas, ComposerGeometry geometry) throws Exception {
            draws++;

            if (slow) {
                Thread.sleep(6);
            }
        }

        @Override
        public void close() throws Exception {
            closes++;

            if (failClose) {
                failClose = false;
                throw new Exception("retry cleanup");
            }
        }
    }

    private static final class ClusterCapture implements PluginComposerRegistry.TextClusterCapture {
        final long revision;
        final Capture[] captures;
        int draws;
        int releases;
        ComposerTextClusterPresentation presentation;
        boolean valid = true;

        ClusterCapture(ComposerTextClusterSelection selection) {
            revision = selection.revision;
            captures = new Capture[selection.getCount()];

            for (int index = 0; index < captures.length; index++) {
                captures[index] = new Capture(selection.getCandidate(index));
            }
        }

        @Override
        public long getRevision() {
            return revision;
        }

        @Override
        public int getCount() {
            return captures.length;
        }

        @Override
        public ComposerTextCapture getCapture(int index) {
            return captures[index];
        }

        @Override
        public boolean isValid() {
            return valid;
        }

        @Override
        public void draw(Canvas canvas, java.util.function.Consumer<Canvas> nativeDraw,
                ComposerTextClusterPresentation value) {
            draws++;
            presentation = value;
            nativeDraw.accept(canvas);
        }

        @Override
        public void release() {
            releases++;

            for (Capture capture : captures) {
                capture.release();
            }
        }
    }

    private static class TextEffect extends Effect implements ComposerTextEffect {
        ComposerSurface surface;
        ComposerTextChange change;
        ComposerTextCapture capture;
        ComposerTextPresentation presentation = new ComposerTextPresentation(0, 0, 1);
        boolean stale;
        boolean reenter;
        Runnable reentrantAction;

        @Override
        public ComposerTextCandidate selectCandidate(ComposerTextChange value) {
            change = value;
            surface.requestFrame();

            if (reenter) {
                reenter = false;
                reentrantAction.run();
            }

            return new ComposerTextCandidate(value.revision + (stale ? 1 : 0), value.start,
                    value.start + value.insertedCount);
        }

        @Override
        public ComposerTextPresentation present(ComposerTextCapture value) {
            capture = value;
            return presentation;
        }
    }

    private static final class CursorEffect extends Effect implements ComposerCursorEffect {
        ComposerCursorSnapshot snapshot;
        ComposerCursorPresentation presentation = new ComposerCursorPresentation(0, 0, 1);

        @Override
        public ComposerCursorPresentation presentCursor(ComposerCursorSnapshot value) {
            snapshot = value;
            return presentation;
        }
    }

    private static final class ContentEffect extends Effect implements ComposerContentEffect {
        ComposerContentPresentation presentation = new ComposerContentPresentation(4, -3);
        int presentations;

        @Override
        public ComposerContentPresentation presentContent() {
            presentations++;
            return presentation;
        }
    }

    private static final class MotionEffect extends Effect implements ComposerMotionEffect {
        ComposerMotionSnapshot motion;
        int motions;

        @Override
        public void onMotion(ComposerMotionSnapshot value) {
            motion = value;
            motions++;
        }
    }

    private static final class SelectionEffect extends Effect implements ComposerSelectionEffect {
        ComposerSelectionSnapshot snapshot;
        ComposerSelectionPresentation presentation = new ComposerSelectionPresentation(0, 0, 1);
        Runnable onPresent;

        @Override
        public ComposerSelectionPresentation presentSelection(ComposerSelectionSnapshot value) {
            snapshot = value;

            if (onPresent != null) {
                Runnable action = onPresent;
                onPresent = null;
                action.run();
            }

            return presentation;
        }
    }

    private static final class SendEffect extends Effect implements ComposerSendEffect {
        ComposerSendEvent event;
        int sends;
        boolean failSend;

        @Override
        public void onSend(ComposerSendEvent value) {
            event = value;
            sends++;

            if (failSend) {
                throw new IllegalStateException("intentional send failure");
            }
        }
    }

    private static final class ExpandedEffect extends Effect implements ComposerExpandedEffect {
        ComposerExpandedBounds bounds;
        ComposerGeometry expandedGeometry;
        int expandedDraws;
        boolean failExpandedDraw;

        @Override
        public void drawExpanded(Canvas canvas, ComposerGeometry geometry, ComposerExpandedBounds value) {
            bounds = value;
            expandedGeometry = geometry;
            expandedDraws++;

            if (failExpandedDraw) {
                throw new IllegalStateException("intentional expanded draw failure");
            }
        }
    }

    private static final class InsertionEffect extends Effect implements ComposerTextInsertionEffect {
        ComposerTextInsertion insertion;
        int insertions;
        boolean failInsertion;

        @Override
        public void onTextInserted(ComposerTextInsertion value) {
            insertion = value;
            insertions++;

            if (failInsertion) {
                throw new IllegalStateException("intentional insertion failure");
            }
        }
    }

    private static final class SelectionCapture implements PluginComposerRegistry.SelectionCapture {
        int draws;
        int releases;
        boolean valid = true;
        ComposerSelectionPresentation presentation;

        @Override
        public long getRevision() {
            return 40;
        }

        @Override
        public int getStart() {
            return 1;
        }

        @Override
        public int getEnd() {
            return 3;
        }

        @Override
        public float getLeft() {
            return 10;
        }

        @Override
        public float getTop() {
            return 5;
        }

        @Override
        public float getRight() {
            return 300;
        }

        @Override
        public float getBottom() {
            return 15;
        }

        @Override
        public float getBaseline() {
            return 15;
        }

        @Override
        public boolean isValid() {
            return valid;
        }

        @Override
        public void draw(Canvas canvas, ComposerSelectionPresentation value) {
            draws++;
            presentation = value;
        }

        @Override
        public void release() {
            releases++;
        }
    }

    private static class LayoutTextEffect extends TextEffect implements ComposerTextLayoutEffect {
        ComposerTextLayoutChange layoutChange;

        @Override
        public void onTextLayoutChanged(ComposerTextLayoutChange value) {
            layoutChange = value;
        }
    }

    private static final class ClusterTextEffect extends Effect implements ComposerTextClusterEffect {
        ComposerTextClusterSelection selection;
        ComposerTextClusterCapture capture;
        ComposerTextClusterPresentation presentation;

        @Override
        public ComposerTextClusterSelection selectClusters(ComposerTextLayoutChange change) {
            return selection;
        }

        @Override
        public ComposerTextClusterPresentation presentClusters(ComposerTextClusterCapture value) {
            capture = value;
            return presentation;
        }
    }

    private static final class TransitionTextEffect extends LayoutTextEffect implements ComposerTextTransitionEffect {
        ComposerTextCapture removedCapture;
        ComposerTextPresentation removedPresentation = new ComposerTextPresentation(0, 0, 1);

        @Override
        public ComposerTextPresentation presentRemovedText(
                ComposerTextLayoutChange change, ComposerTextCapture capture) {
            removedCapture = capture;
            return removedPresentation;
        }
    }

    private static final class LayerTransitionTextEffect extends LayoutTextEffect
            implements ComposerTextLayerTransitionEffect {
        ComposerTextCapture removedCapture;
        ComposerTextLayers removedLayers;

        @Override
        public ComposerTextLayers presentRemovedTextLayers(
                ComposerTextLayoutChange change, ComposerTextCapture capture) {
            removedCapture = capture;
            return removedLayers;
        }
    }

    private static final class OverlayCapture implements PluginComposerRegistry.OverlayTextCapture {
        int draws;
        int releases;
        ComposerTextPresentation presentation;

        @Override
        public long getRevision() {
            return 20;
        }

        @Override
        public int getStart() {
            return 1;
        }

        @Override
        public int getEnd() {
            return 2;
        }

        @Override
        public float getLeft() {
            return 10;
        }

        @Override
        public float getTop() {
            return 5;
        }

        @Override
        public float getRight() {
            return 20;
        }

        @Override
        public float getBottom() {
            return 15;
        }

        @Override
        public float getBaseline() {
            return 14;
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public void draw(Canvas canvas, ComposerTextPresentation presentation) {
            draws++;
            this.presentation = presentation;
        }

        @Override
        public void release() {
            releases++;
        }
    }

    public static void main(String[] args) throws Exception {
        AtomicBoolean active = new AtomicBoolean(true);
        Effect effect = new Effect();
        AutoCloseable registration = register("test.effect", active::get, context -> {
            effect.context = context;
            return effect;
        });
        Host host = new Host();
        PluginComposerRegistry.Surface surface = PluginComposerRegistry.attach(host);
        require(effect.context.getAccount() == 1 && effect.context.getOwnerId() == 20
                && effect.context.getDialogId() == 30, "surface identity");
        require(!effect.context.isPreview(), "ordinary surface marked as preview");
        surface.input(new ComposerInput(1, 0, 1, false, false));
        surface.input(new ComposerInput(2, 0, 3, false, true));
        require(effect.inputs == 0, "input callback was synchronous");
        tick();
        require(effect.inputs == 1 && effect.input.insertedCount == 3 && effect.input.composing, "input coalescing");
        surface.draw(new Canvas());
        require(effect.draws == 1, "effect not drawn");
        surface.input(new ComposerInput(65, 65, 10000, true, false));
        tick();
        require(effect.input.bulk, "bulk flag lost");
        active.set(false);
        surface.input(new ComposerInput(1, 0, 4, false, false));
        surface.draw(new Canvas());
        require(effect.draws == 1 && effect.inputs == 2, "stale callbacks delivered");
        surface.close();
        registration.close();
        require(effect.closes == 1 && !effect.context.isActive(), "cleanup was not idempotent");
        require(Choreographer.getInstance().pending.isEmpty(), "detached frame demand");

        Effect hidden = new Effect();
        AutoCloseable hiddenRegistration = register("test.hidden", () -> true, context -> hidden);
        Host hiddenHost = new Host();
        PluginComposerRegistry.Surface hiddenSurface = PluginComposerRegistry.attach(hiddenHost);
        hiddenSurface.input(new ComposerInput(1, 0, 1, false, false));
        hiddenHost.available = false;
        tick();
        require(hiddenSurface.isClosed() && hidden.closes == 1, "hidden host left a reusable stale surface");
        hiddenRegistration.close();

        Effect bad = new Effect();
        bad.failFrame = true;
        Effect good = new Effect();
        AutoCloseable badRegistration = register("test.bad", () -> true, context -> bad);
        AutoCloseable goodRegistration = register("test.good", () -> true, context -> good);
        surface = PluginComposerRegistry.attach(new Host());
        surface.input(new ComposerInput(1, 0, 1, false, false));
        tick();
        require(bad.closes == 1 && good.frames == 1, "frame failure broke another effect");
        surface.close();
        badRegistration.close();
        goodRegistration.close();

        Effect retry = new Effect();
        retry.failClose = true;
        AutoCloseable retryRegistration = register("test.retry", () -> true, context -> retry);
        surface = PluginComposerRegistry.attach(new Host());
        surface.close();
        require(retry.closes == 1, "failed cleanup silently retried during detach");
        retryRegistration.close();
        require(retry.closes == 2, "failed cleanup was not retained for retry");

        Effect expensive = new Effect();
        expensive.slow = true;
        Effect deferred = new Effect();
        AutoCloseable expensiveRegistration = register("test.expensive", () -> true, context -> expensive);
        AutoCloseable deferredRegistration = register("test.deferred", () -> true, context -> deferred);
        surface = PluginComposerRegistry.attach(new Host());
        Choreographer.getInstance().tick(3_000_000_000L);
        surface.draw(new Canvas());
        require(expensive.draws == 1 && deferred.draws == 0, "aggregate drawing budget ignored");
        Choreographer.getInstance().tick(3_016_000_000L);
        surface.draw(new Canvas());
        require(deferred.draws == 1 && expensive.draws == 1, "deferred draw starved");
        surface.close();
        expensiveRegistration.close();
        deferredRegistration.close();

        Effect slow = new Effect();
        slow.slow = true;
        AutoCloseable slowRegistration = register("test.slow", () -> true, context -> slow);
        surface = PluginComposerRegistry.attach(new Host());
        Choreographer.getInstance().tick(4_000_000_000L);
        surface.draw(new Canvas());
        Choreographer.getInstance().tick(4_016_000_000L);
        surface.draw(new Canvas());
        Choreographer.getInstance().tick(4_032_000_000L);
        surface.draw(new Canvas());
        require(!surface.hasEffects() && slow.closes == 1, "slow effect was not suspended");
        surface.close();
        slowRegistration.close();

        Effect endless = new Effect();
        endless.forever = true;
        AutoCloseable endlessRegistration = register("test.endless", () -> true, context -> endless);
        surface = PluginComposerRegistry.attach(new Host());
        surface.input(new ComposerInput(1, 0, 1, false, false));
        Choreographer.getInstance().tick(5_000_000_000L);
        Choreographer.getInstance().tick(7_100_000_000L);
        require(endless.closes == 1 && Choreographer.getInstance().pending.isEmpty(), "endless effect was not stopped");
        surface.close();
        endlessRegistration.close();

        TextEffect text = new TextEffect();
        AutoCloseable textRegistration = registerText("test.text", () -> true, context -> {
            text.surface = context;
            return text;
        });
        rejects(() -> registerText("test.text.conflict", () -> true, context -> new TextEffect()));
        Host textHost = new Host();
        surface = PluginComposerRegistry.attach(textHost);
        require(surface.hasTextEffect(), "text owner not attached");
        require(!surface.hasTextLayoutEffect(), "legacy text effect became layout-aware");
        text.surface.requestFrame();
        tick();
        require(textHost.textInvalidations == 1 && textHost.invalidations == 0,
                "text frame did not invalidate the input field");
        ComposerTextChange change = new ComposerTextChange(7, "ab", "abc", 2, 0, 1, 3, 3, -1, -1);
        surface.textChanged(change);
        surface.prepareText();
        require(text.change == change && textHost.captures == 1, "text candidate was not captured");
        int[] nativeDraws = {0};
        surface.drawText(new Canvas(), canvas -> nativeDraws[0]++);
        require(text.capture.getRevision() == 7 && nativeDraws[0] == 1, "text presentation was not applied");
        PluginComposerRegistry.Surface textSurface = surface;
        text.reentrantAction = () -> textSurface.textChanged(new ComposerTextChange(
                11, "abcdef", "abcdefx", 6, 0, 1, 7, 7, -1, -1));
        text.reenter = true;
        surface.textChanged(new ComposerTextChange(10, "abcde", "abcdef", 5, 0, 1, 6, 6, -1, -1));
        surface.prepareText();
        require(textHost.capturedCandidate.revision == 11, "reentrant text change restored a stale candidate");
        textHost.failCapture = true;
        surface.textChanged(new ComposerTextChange(12, "abcdefx", "abcdefxy", 7, 0, 1, 8, 8, -1, -1));
        surface.prepareText();
        require(surface.hasTextEffect(), "host capture failure quarantined plugin");
        int capturesBeforeStaleCandidate = textHost.captures;
        text.stale = true;
        surface.textChanged(new ComposerTextChange(8, "abc", "abcd", 3, 0, 1, 4, 4, -1, -1));
        surface.prepareText();
        require(textHost.captures == capturesBeforeStaleCandidate, "stale candidate reached host capture");
        surface.drawText(new Canvas(), canvas -> nativeDraws[0]++);
        require(nativeDraws[0] == 2, "native fallback missing for stale candidate");
        text.stale = false;
        text.presentation = new ComposerTextPresentation(
                3, -2, 0.75f, 1.5f, 0.5f, 30, 5, 5, 0xFFFF8800, 0.4f, 6);
        surface.textChanged(new ComposerTextChange(9, "abcd", "abcde", 4, 0, 1, 5, 5, -1, -1));
        surface.prepareText();
        surface.drawText(new Canvas(), canvas -> nativeDraws[0]++);
        require(nativeDraws[0] == 3 && text.capture instanceof Capture
                && ((Capture) text.capture).presentation == text.presentation
                && text.presentation.tintColor == 0xFFFF8800 && text.presentation.tintAmount == 0.4f
                && text.presentation.blurRadius == 6,
                "bounded transform presentation was not applied");
        Capture transformedCapture = (Capture) text.capture;
        text.presentation = new ComposerTextPresentation(0, 0, 1, 5, 1, 0, 0, 0);
        surface.textChanged(new ComposerTextChange(10, "abcde", "abcdef", 5, 0, 1, 6, 6, -1, -1));
        surface.prepareText();
        surface.drawText(new Canvas(), canvas -> nativeDraws[0]++);
        require(nativeDraws[0] == 4 && text.closes == 1 && transformedCapture.releases == 1,
                "invalid transform did not fail closed or release capture");
        surface.close();
        textRegistration.close();

        TextEffect concurrentText = new TextEffect();
        CursorEffect concurrentCursor = new CursorEffect();
        AutoCloseable concurrentTextRegistration = registerText(
                "test.text.concurrent_cursor", () -> true, context -> {
                    concurrentText.surface = context;
                    return concurrentText;
                });
        AutoCloseable concurrentCursorRegistration = registerCursor(
                "test.cursor.concurrent_text", () -> true, context -> concurrentCursor);
        Host concurrentHost = new Host();
        surface = PluginComposerRegistry.attach(concurrentHost);
        surface.textChanged(new ComposerTextChange(
                13, "a", "ab", 1, 0, 1, 2, 2, -1, -1));
        surface.prepareText();
        ComposerCursorSnapshot concurrentSnapshot = new ComposerCursorSnapshot(
                31, 2, 0, 24, 5, 26, 29, 25, false);
        int[] concurrentNativeDraws = {0};
        PluginComposerRegistry.Surface concurrentSurface = surface;
        surface.drawText(new Canvas(), canvas -> concurrentSurface.drawCursor(
                canvas, concurrentSnapshot, cursorCanvas -> concurrentNativeDraws[0]++));
        require(concurrentCursor.snapshot == concurrentSnapshot
                        && concurrentHost.cursorDraws == 1 && concurrentNativeDraws[0] == 1,
                "text presentation bypassed concurrent cursor effect");
        surface.close();
        concurrentCursorRegistration.close();
        concurrentTextRegistration.close();

        TextEffect invalidTint = new TextEffect();
        invalidTint.presentation = new ComposerTextPresentation(
                0, 0, 1, 1, 1, 0, 0, 0, 0xFFFF0000, 2);
        AutoCloseable invalidTintRegistration = registerText(
                "test.text.invalid_tint", () -> true, context -> {
                    invalidTint.surface = context;
                    return invalidTint;
                });
        surface = PluginComposerRegistry.attach(new Host());
        surface.textChanged(new ComposerTextChange(13, "a", "ab", 1, 0, 1, 2, 2, -1, -1));
        surface.prepareText();
        surface.drawText(new Canvas(), canvas -> nativeDraws[0]++);
        require(invalidTint.closes == 1, "invalid tint did not fail closed");
        surface.close();
        invalidTintRegistration.close();

        TextEffect invalidBlur = new TextEffect();
        invalidBlur.presentation = new ComposerTextPresentation(
                0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 13);
        AutoCloseable invalidBlurRegistration = registerText(
                "test.text.invalid_blur", () -> true, context -> {
                    invalidBlur.surface = context;
                    return invalidBlur;
                });
        surface = PluginComposerRegistry.attach(new Host());
        surface.textChanged(new ComposerTextChange(14, "a", "ab", 1, 0, 1, 2, 2, -1, -1));
        surface.prepareText();
        surface.drawText(new Canvas(), canvas -> nativeDraws[0]++);
        require(invalidBlur.closes == 1, "invalid blur did not fail closed");
        surface.close();
        invalidBlurRegistration.close();

        LayoutTextEffect layoutText = new LayoutTextEffect();
        AutoCloseable layoutTextRegistration = registerText(
                "test.text.layout", () -> true, context -> {
                    layoutText.surface = context;
                    return layoutText;
                });
        surface = PluginComposerRegistry.attach(new Host());
        require(surface.hasTextLayoutEffect(), "layout text effect not attached");
        ComposerTextLineSnapshot[] sourceLines = {
                new ComposerTextLineSnapshot(0, 2, 2, 4, 20, 3, 18, 15, false)
        };
        ComposerTextLayoutSnapshot beforeLayout = new ComposerTextLayoutSnapshot(
                20, "ab", 2, 2, -1, -1, 100, 40, 4, 3, 0, 0,
                false, false, sourceLines);
        sourceLines[0] = null;
        require(beforeLayout.getLineCount() == 1 && beforeLayout.getLine(0) != null,
                "layout snapshot retained mutable line array");
        ComposerTextClusterSnapshot[] sourceClusters = {
                new ComposerTextClusterSnapshot(2, 3, 0, false, 20, 28, 20, 28, 8)
        };
        ComposerTextRunSnapshot[] sourceRuns = {
                new ComposerTextRunSnapshot(0, 3, 0, false, 0, 1)
        };
        ComposerTextFontSnapshot font = new ComposerTextFontSnapshot(
                16, 1, 0, 0, 1, 0xFF112233, 0, 400, "en", null, null);
        ComposerTextLayoutSnapshot afterLayout = new ComposerTextLayoutSnapshot(
                21, "abc", 3, 3, -1, -1, 100, 40, 4, 3, 0, 0,
                false, false, false, ComposerTextLayoutSnapshot.SPAN_UNDERLINE, font,
                new ComposerTextLineSnapshot[] {
                        new ComposerTextLineSnapshot(0, 3, 3, 4, 28, 3, 18, 15, false)
                }, sourceClusters, sourceRuns);
        sourceClusters[0] = null;
        sourceRuns[0] = null;
        require(afterLayout.getClusterCount() == 1 && afterLayout.getCluster(0) != null,
                "layout snapshot retained mutable cluster array");
        require(afterLayout.getRunCount() == 1 && afterLayout.getRun(0) != null
                && afterLayout.font == font && afterLayout.spanFlags == ComposerTextLayoutSnapshot.SPAN_UNDERLINE,
                "layout snapshot retained mutable run array or lost font metadata");
        ComposerTextLayoutChange layoutChange = new ComposerTextLayoutChange(
                21, beforeLayout, afterLayout, 2, 0, 1, false);
        surface.textLayoutChanged(layoutChange);
        require(layoutText.layoutChange == layoutChange && layoutText.layoutChange.after.revision == 21,
                "layout change was not delivered");
        surface.close();
        layoutTextRegistration.close();

        ClusterTextEffect clusterText = new ClusterTextEffect();
        AutoCloseable clusterTextRegistration = registerText(
                "test.text.clusters", () -> true, context -> {
                    clusterText.context = context;
                    return clusterText;
                });
        Host clusterHost = new Host();
        surface = PluginComposerRegistry.attach(clusterHost);
        ComposerTextCandidate[] selectedCandidates = {
                new ComposerTextCandidate(22, 1, 2),
                new ComposerTextCandidate(22, 2, 3)
        };
        clusterText.selection = new ComposerTextClusterSelection(22, selectedCandidates);
        selectedCandidates[0] = null;
        ComposerTextPresentation firstCluster = new ComposerTextPresentation(1, -2, 0.5f);
        ComposerTextPresentation secondLayer = new ComposerTextPresentation(0, 0, 0.25f);
        ComposerTextPresentation[] firstClusterLayers = {firstCluster, secondLayer};
        ComposerTextLayers[] clusterLayers = {
                new ComposerTextLayers(firstClusterLayers), null
        };
        clusterText.presentation = ComposerTextClusterPresentation.layered(22, clusterLayers);
        firstClusterLayers[0] = null;
        clusterLayers[0] = null;
        ComposerTextLayoutSnapshot clusterLayout = new ComposerTextLayoutSnapshot(
                22, "abc", 3, 3, -1, -1, 100, 40, 4, 3, 0, 0,
                false, false, false,
                new ComposerTextLineSnapshot[] {
                        new ComposerTextLineSnapshot(0, 3, 3, 4, 28, 3, 18, 15, false)
                },
                new ComposerTextClusterSnapshot[] {
                        new ComposerTextClusterSnapshot(0, 1, 0, false, 0, 8, 0, 8, 8),
                        new ComposerTextClusterSnapshot(1, 2, 0, false, 8, 16, 8, 16, 8),
                        new ComposerTextClusterSnapshot(2, 3, 0, false, 16, 24, 16, 24, 8)
                });
        ComposerTextLayoutChange clusterChange = new ComposerTextLayoutChange(
                22, afterLayout, clusterLayout, 2, 0, 1, false);
        surface.textChanged(new ComposerTextChange(22, "ab", "abc", 2, 0, 1, 3, 3, -1, -1));
        surface.textLayoutChanged(clusterChange);
        surface.prepareText();
        int[] clusterNativeDraws = {0};
        surface.drawText(new Canvas(), canvas -> clusterNativeDraws[0]++);
        require(clusterHost.clusterCaptures == 1 && clusterHost.capturedClusterSelection.getCount() == 2
                && clusterHost.capturedClusterSelection.getCandidate(0) != null
                && clusterText.capture == clusterHost.clusterCapture
                && clusterHost.clusterCapture.draws == 1 && clusterNativeDraws[0] == 1
                && clusterHost.clusterCapture.presentation.getPresentation(0) == firstCluster
                && clusterHost.clusterCapture.presentation.getLayers(0).getCount() == 2
                && clusterHost.clusterCapture.presentation.getLayers(0).getPresentation(1) == secondLayer
                && clusterHost.clusterCapture.presentation.getPresentation(1) == null,
                "cluster captures or layered presentations were not immutable");
        ClusterCapture previousClusterCapture = clusterHost.clusterCapture;
        surface.textChanged(new ComposerTextChange(23, "abc", "abcd", 3, 0, 1, 4, 4, -1, -1));
        require(previousClusterCapture.releases == 1 && previousClusterCapture.captures[0].releases == 1,
                "cluster captures were not released on new input");
        clusterText.selection = new ComposerTextClusterSelection(23, new ComposerTextCandidate[] {
                new ComposerTextCandidate(23, 1, 3)
        });
        surface.textLayoutChanged(new ComposerTextLayoutChange(
                23, clusterLayout, clusterLayout, 3, 0, 1, false));
        require(clusterText.closes == 1 && !surface.hasTextEffect(),
                "non-cluster-aligned range did not fail closed");
        surface.close();
        clusterTextRegistration.close();

        TransitionTextEffect transitionText = new TransitionTextEffect();
        AutoCloseable transitionRegistration = registerText(
                "test.text.transition", () -> true, context -> {
                    transitionText.surface = context;
                    return transitionText;
                });
        surface = PluginComposerRegistry.attach(new Host());
        require(surface.hasTextTransitionEffect(), "transition text effect not attached");
        OverlayCapture overlayCapture = new OverlayCapture();
        transitionText.removedPresentation = new ComposerTextPresentation(
                2, 1, 0.5f, 0.8f, 1.2f, -20, 5, 5, 0xFF00AAFF, 0.6f, 4);
        ComposerTextLayoutChange transitionChange = new ComposerTextLayoutChange(
                21, beforeLayout, afterLayout, 1, 1, 0, false, overlayCapture);
        surface.textLayoutChanged(transitionChange);
        int[] transitionNativeDraws = {0};
        surface.drawText(new Canvas(), canvas -> transitionNativeDraws[0]++);
        require(transitionNativeDraws[0] == 1 && overlayCapture.draws == 1
                && transitionText.removedCapture == overlayCapture
                && overlayCapture.presentation == transitionText.removedPresentation,
                "removed text overlay was not drawn after native text");
        transitionText.removedPresentation = null;
        surface.drawText(new Canvas(), canvas -> transitionNativeDraws[0]++);
        require(transitionNativeDraws[0] == 2 && overlayCapture.draws == 1 && overlayCapture.releases == 1,
                "finished removed text overlay was retained");
        surface.close();
        transitionRegistration.close();

        LayerTransitionTextEffect layerTransitionText = new LayerTransitionTextEffect();
        AutoCloseable layerTransitionRegistration = registerText(
                "test.text.layer_transition", () -> true, context -> {
                    layerTransitionText.surface = context;
                    return layerTransitionText;
                });
        surface = PluginComposerRegistry.attach(new Host());
        OverlayCapture layeredOverlayCapture = new OverlayCapture();
        ComposerTextPresentation removedBlur = new ComposerTextPresentation(
                0, 0, 0.4f, 1, 1, 0, 5, 5, 0, 0, 6);
        ComposerTextPresentation removedSharp = new ComposerTextPresentation(0, 0, 0.6f);
        layerTransitionText.removedLayers = new ComposerTextLayers(
                new ComposerTextPresentation[] {removedBlur, removedSharp});
        surface.textLayoutChanged(new ComposerTextLayoutChange(
                21, beforeLayout, afterLayout, 1, 1, 0, false, layeredOverlayCapture));
        surface.drawText(new Canvas(), canvas -> transitionNativeDraws[0]++);
        require(layerTransitionText.removedCapture == layeredOverlayCapture
                        && layeredOverlayCapture.draws == 2
                        && layeredOverlayCapture.presentation == removedSharp,
                "layered removed text presentation was not applied");
        layerTransitionText.removedLayers = new ComposerTextLayers(
                new ComposerTextPresentation[] {removedBlur, removedSharp, removedBlur});
        surface.drawText(new Canvas(), canvas -> transitionNativeDraws[0]++);
        require(layerTransitionText.closes == 1 && layeredOverlayCapture.releases == 1,
                "unbounded removed text layers did not fail closed");
        surface.close();
        layerTransitionRegistration.close();

        ContentEffect content = new ContentEffect();
        AutoCloseable contentRegistration = registerContent(
                "test.content", () -> true, context -> {
                    content.context = context;
                    return content;
                });
        rejects(() -> registerContent(
                "test.content.conflict", () -> true, context -> new ContentEffect()));
        Host contentHost = new Host();
        surface = PluginComposerRegistry.attach(contentHost);
        require(surface.hasContentEffect(), "content owner not attached");
        Canvas contentCanvas = new Canvas();
        int[] contentNativeDraws = {0};
        surface.drawContent(contentCanvas, canvas -> {
            contentNativeDraws[0]++;
            require(canvas.getTranslationX() == 4 && canvas.getTranslationY() == -3,
                    "content translation was not applied around native draw");
        });
        require(content.presentations == 1 && contentNativeDraws[0] == 1
                && contentCanvas.getTranslationX() == 0 && contentCanvas.getTranslationY() == 0,
                "content transform was not restored");
        content.presentation = null;
        surface.drawContent(contentCanvas, canvas -> contentNativeDraws[0]++);
        require(contentNativeDraws[0] == 2 && content.closes == 0,
                "null content presentation did not use native fallback");
        content.presentation = new ComposerContentPresentation(25, 0);
        surface.drawContent(contentCanvas, canvas -> contentNativeDraws[0]++);
        require(contentNativeDraws[0] == 3 && content.closes == 1 && !surface.hasContentEffect(),
                "invalid content presentation did not fail closed");
        surface.close();
        contentRegistration.close();

        Effect nonMotion = new Effect();
        AutoCloseable nonMotionRegistration = register(
                "test.motion.invalid", () -> true, context -> {
                    nonMotion.context = context;
                    return nonMotion;
                });
        surface = PluginComposerRegistry.attach(new Host());
        require(!nonMotion.context.setMotionUpdatesEnabled(true),
                "non-motion effect enabled sensor updates");
        surface.close();
        nonMotionRegistration.close();

        MotionEffect motion = new MotionEffect();
        MotionEffect secondMotion = new MotionEffect();
        AutoCloseable motionRegistration = register(
                "test.motion.first", () -> true, context -> {
                    motion.context = context;
                    return motion;
                });
        AutoCloseable secondMotionRegistration = register(
                "test.motion.second", () -> true, context -> {
                    secondMotion.context = context;
                    return secondMotion;
                });
        Host motionHost = new Host();
        surface = PluginComposerRegistry.attach(motionHost);
        SensorManager sensors = SensorManager.getInstance();
        int registerCalls = sensors.registerCalls;
        int unregisterCalls = sensors.unregisterCalls;
        require(motion.context.setMotionUpdatesEnabled(true)
                && secondMotion.context.setMotionUpdatesEnabled(true)
                && sensors.registerCalls == registerCalls + 1,
                "motion sensor was not registered");
        sensors.dispatch(40, 2, 1, 2, 3);
        sensors.dispatch(41, 3, 4, 5, 6);
        require(motion.motions == 0, "motion callback bypassed frame coordinator");
        tick();
        require(motion.motions == 1 && secondMotion.motions == 1
                && motion.motion == secondMotion.motion
                && motion.motion.source == ComposerMotionSnapshot.SOURCE_GRAVITY
                && motion.motion.timestampNanos == 41 && motion.motion.accuracy == 3
                && motion.motion.x == 4 && motion.motion.y == 5 && motion.motion.z == 6,
                "motion snapshots were not copied or coalesced");
        require(motion.context.setMotionUpdatesEnabled(false)
                && sensors.unregisterCalls == unregisterCalls,
                "disabling one motion client stopped shared sensor");
        surface.close();
        require(sensors.unregisterCalls == unregisterCalls + 1
                && motion.closes == 1 && secondMotion.closes == 1,
                "motion sensor was not released with surface");
        motionRegistration.close();
        secondMotionRegistration.close();

        sensors.gravityRegisterSuccess = false;
        MotionEffect fallbackMotion = new MotionEffect();
        AutoCloseable fallbackRegistration = register(
                "test.motion.fallback", () -> true, context -> {
                    fallbackMotion.context = context;
                    return fallbackMotion;
                });
        surface = PluginComposerRegistry.attach(new Host());
        registerCalls = sensors.registerCalls;
        require(fallbackMotion.context.setMotionUpdatesEnabled(true)
                && sensors.registerCalls == registerCalls + 2,
                "accelerometer fallback was not registered");
        sensors.dispatch(42, 1, 7, 8, 9);
        tick();
        require(fallbackMotion.motion.source == ComposerMotionSnapshot.SOURCE_ACCELEROMETER,
                "accelerometer fallback source was not reported");
        surface.close();
        fallbackRegistration.close();
        sensors.gravityRegisterSuccess = true;

        MotionEffect cleanupMotion = new MotionEffect();
        AutoCloseable cleanupRegistration = register(
                "test.motion.cleanup", () -> true, context -> {
                    cleanupMotion.context = context;
                    return cleanupMotion;
                });
        surface = PluginComposerRegistry.attach(new Host());
        require(cleanupMotion.context.setMotionUpdatesEnabled(true),
                "cleanup sensor was not registered");
        sensors.failUnregister = true;
        surface.close();
        cleanupRegistration.close();
        MotionEffect afterCleanupMotion = new MotionEffect();
        AutoCloseable afterCleanupRegistration = register(
                "test.motion.after_cleanup", () -> true, context -> {
                    afterCleanupMotion.context = context;
                    return afterCleanupMotion;
                });
        surface = PluginComposerRegistry.attach(new Host());
        require(afterCleanupMotion.context.setMotionUpdatesEnabled(true),
                "sensor state remained stuck after cleanup failure");
        surface.close();
        afterCleanupRegistration.close();

        MotionEffect endlessMotion = new MotionEffect();
        endlessMotion.forever = true;
        AutoCloseable motionEndlessRegistration = register(
                "test.motion.endless", () -> true, context -> {
                    endlessMotion.context = context;
                    return endlessMotion;
                });
        surface = PluginComposerRegistry.attach(new Host());
        require(endlessMotion.context.setMotionUpdatesEnabled(true),
                "endless motion sensor was not registered");
        long motionStart = Choreographer.getInstance().getFrameTimeNanos() + 16_666_667L;
        sensors.dispatch(43, 1, 1, 1, 1);
        Choreographer.getInstance().tick(motionStart);
        sensors.dispatch(44, 1, 2, 2, 2);
        Choreographer.getInstance().tick(motionStart + 2_100_000_000L);
        require(endlessMotion.closes == 1,
                "motion samples bypassed continuous animation timeout");
        surface.close();
        motionEndlessRegistration.close();

        Effect firstHaptic = new Effect();
        Effect secondHaptic = new Effect();
        AutoCloseable firstHapticRegistration = register(
                "test.haptic.first", () -> true, context -> {
                    firstHaptic.context = context;
                    return firstHaptic;
                });
        AutoCloseable secondHapticRegistration = register(
                "test.haptic.second", () -> true, context -> {
                    secondHaptic.context = context;
                    return secondHaptic;
                });
        Host hapticHost = new Host();
        surface = PluginComposerRegistry.attach(hapticHost);
        require(!firstHaptic.context.performHapticFeedback(99),
                "invalid haptic feedback was accepted");
        require(firstHaptic.context.performHapticFeedback(ComposerHapticFeedback.TICK)
                && hapticHost.haptics == 1
                && hapticHost.lastHaptic == ComposerHapticFeedback.TICK,
                "valid haptic feedback was not delivered");
        require(!secondHaptic.context.performHapticFeedback(ComposerHapticFeedback.IMPACT),
                "surface haptic interval was not enforced");

        long hapticTime = Choreographer.getInstance().getFrameTimeNanos();

        for (int i = 1; i < 8; i++) {
            hapticTime += 40_000_000L;
            Choreographer.getInstance().tick(hapticTime);
            require((i % 2 == 0 ? firstHaptic.context : secondHaptic.context)
                            .performHapticFeedback(ComposerHapticFeedback.IMPACT),
                    "bounded haptic feedback was rejected early");
        }

        hapticTime += 40_000_000L;
        Choreographer.getInstance().tick(hapticTime);
        require(!firstHaptic.context.performHapticFeedback(ComposerHapticFeedback.CONFIRM)
                        && hapticHost.haptics == 8,
                "surface haptic rate limit was not enforced");
        hapticTime += 1_000_000_000L;
        Choreographer.getInstance().tick(hapticTime);
        hapticHost.failHaptic = true;
        require(!firstHaptic.context.performHapticFeedback(ComposerHapticFeedback.REJECT),
                "host haptic failure was reported as success");
        hapticTime += 40_000_000L;
        Choreographer.getInstance().tick(hapticTime);
        require(secondHaptic.context.performHapticFeedback(ComposerHapticFeedback.CONFIRM),
                "host haptic failure suspended the effect");
        surface.close();
        require(!firstHaptic.context.performHapticFeedback(ComposerHapticFeedback.TICK),
                "detached surface performed haptic feedback");
        firstHapticRegistration.close();
        secondHapticRegistration.close();

        Effect boundaryHaptic = new Effect();
        AutoCloseable boundaryHapticRegistration = register(
                "test.haptic.boundary", () -> true, context -> {
                    boundaryHaptic.context = context;
                    return boundaryHaptic;
                });
        surface = PluginComposerRegistry.attach(new Host());
        long boundaryStart = hapticTime + 1_000_000_000L;
        Choreographer.getInstance().tick(boundaryStart);
        require(boundaryHaptic.context.performHapticFeedback(ComposerHapticFeedback.TICK),
                "boundary haptic did not start");
        hapticTime = boundaryStart + 720_000_000L;

        for (int i = 0; i < 7; i++) {
            Choreographer.getInstance().tick(hapticTime);
            require(boundaryHaptic.context.performHapticFeedback(ComposerHapticFeedback.TICK),
                    "boundary haptic was rejected early");
            hapticTime += 40_000_000L;
        }

        Choreographer.getInstance().tick(boundaryStart + 1_000_000_000L);
        require(boundaryHaptic.context.performHapticFeedback(ComposerHapticFeedback.TICK),
                "expired haptic slot was not released");
        Choreographer.getInstance().tick(boundaryStart + 1_040_000_000L);
        require(!boundaryHaptic.context.performHapticFeedback(ComposerHapticFeedback.TICK),
                "haptic fixed-window boundary admitted a burst");
        surface.close();
        boundaryHapticRegistration.close();

        CursorEffect cursor = new CursorEffect();
        AutoCloseable cursorRegistration = registerCursor(
                "test.cursor", () -> true, context -> {
                    cursor.context = context;
                    return cursor;
                });
        rejects(() -> registerCursor(
                "test.cursor.conflict", () -> true, context -> new CursorEffect()));
        Host cursorHost = new Host();
        surface = PluginComposerRegistry.attach(cursorHost);
        require(surface.hasCursorEffect(), "cursor owner not attached");
        ComposerCursorSnapshot cursorSnapshot = new ComposerCursorSnapshot(
                30, 3, 0, 20, 5, 22, 29, 25, false);
        int[] cursorNativeDraws = {0};
        surface.drawCursor(new Canvas(), cursorSnapshot, canvas -> cursorNativeDraws[0]++);
        require(cursor.snapshot == cursorSnapshot && cursorHost.cursorDraws == 1
                && cursorHost.cursorSnapshot == cursorSnapshot
                && cursorHost.cursorPresentation == cursor.presentation
                && cursorNativeDraws[0] == 1, "cursor presentation was not applied");
        cursor.context.requestFrame();
        tick();
        require(cursorHost.cursorInvalidations == 1,
                "cursor frame did not invalidate the input field");
        cursor.presentation = null;
        surface.drawCursor(new Canvas(), cursorSnapshot, canvas -> cursorNativeDraws[0]++);
        require(cursorNativeDraws[0] == 2 && cursorHost.cursorDraws == 1,
                "null cursor presentation did not use native fallback");
        cursor.presentation = new ComposerCursorPresentation(0, 0, 1, 5, 1, 0, 0, 0);
        surface.drawCursor(new Canvas(), cursorSnapshot, canvas -> cursorNativeDraws[0]++);
        require(cursorNativeDraws[0] == 3 && cursor.closes == 1,
                "invalid cursor presentation did not fail closed");
        surface.close();
        require(cursorHost.cursorOwnerChanges == 2,
                "cursor owner lifecycle was not reported");
        cursorRegistration.close();

        Effect selectionBlocker = new Effect();
        selectionBlocker.slow = true;
        AutoCloseable selectionBlockerRegistration = register(
                "test.selection.blocker", () -> true, context -> selectionBlocker);
        SelectionEffect selection = new SelectionEffect();
        AutoCloseable selectionRegistration = registerSelection(
                "test.selection", () -> true, context -> {
                    selection.context = context;
                    return selection;
                });
        rejects(() -> registerSelection(
                "test.selection.conflict", () -> true, context -> new SelectionEffect()));
        Host selectionHost = new Host();
        surface = PluginComposerRegistry.attach(selectionHost);
        require(surface.hasSelectionEffect(), "selection owner not attached");
        ComposerSelectionSnapshot selectionSnapshot = new ComposerSelectionSnapshot(
                40, 1, 3, 1, 10, 5, 300, 15);
        SelectionCapture selectionCapture = new SelectionCapture();
        surface.selectionChanged(selectionSnapshot, selectionCapture);
        Choreographer.getInstance().tick(30_000_000_000L);
        surface.draw(new Canvas());
        require(selectionCapture.draws == 0,
                "selection replay bypassed aggregate draw budget");
        Choreographer.getInstance().tick(30_016_666_667L);
        surface.draw(new Canvas());
        require(selection.snapshot == selectionSnapshot && selectionCapture.draws == 1
                && selectionCapture.presentation == selection.presentation,
                "deferred selection presentation was not resumed");
        selectionBlockerRegistration.close();
        selection.context.requestFrame();
        Choreographer.getInstance().tick(31_000_000_000L);
        selection.context.requestFrame();
        Choreographer.getInstance().tick(34_000_000_000L);
        require(selection.closes == 0,
                "separate selection animation burst reused stale deadline");
        selection.presentation = null;
        surface.draw(new Canvas());
        require(selectionCapture.draws == 1 && selectionCapture.releases == 0,
                "null selection presentation discarded capture");
        selection.presentation = new ComposerSelectionPresentation(0, 0, 1);
        SelectionCapture staleSelectionCapture = selectionCapture;
        selection.onPresent = () -> staleSelectionCapture.valid = false;
        surface.draw(new Canvas());
        require(selectionCapture.draws == 1 && selectionCapture.releases == 1,
                "reentrant selection mutation replayed stale capture");
        selectionCapture = new SelectionCapture();
        surface.selectionChanged(selectionSnapshot, selectionCapture);
        selection.presentation = new ComposerSelectionPresentation(0, 0, 1, 5, 1, 0, 0, 0);
        surface.draw(new Canvas());
        require(selectionCapture.releases == 1 && selection.closes == 1,
                "invalid selection presentation did not fail closed");
        surface.close();
        require(selectionHost.selectionOwnerChanges == 2,
                "selection owner lifecycle was not reported");
        selectionRegistration.close();

        SendEffect firstSend = new SendEffect();
        SendEffect secondSend = new SendEffect();
        AutoCloseable firstSendRegistration = register(
                "test.send.first", () -> true, context -> {
                    firstSend.context = context;
                    return firstSend;
                });
        AutoCloseable secondSendRegistration = register(
                "test.send.second", () -> true, context -> secondSend);
        Host sendHost = new Host();
        surface = PluginComposerRegistry.attach(sendHost);
        require(surface.hasSendEffect(), "send effect not attached");
        surface.sent(true, 350, 25);
        require(firstSend.sends == 1 && secondSend.sends == 1
                && firstSend.event == secondSend.event && firstSend.event.sequence == 1
                && firstSend.event.hasTarget && firstSend.event.targetX == 350
                && firstSend.event.targetY == 25, "send event was not delivered consistently");
        surface.sent(true, Float.NaN, 25);
        require(firstSend.sends == 2 && firstSend.event.sequence == 2
                && !firstSend.event.hasTarget && firstSend.event.targetX == 0
                && firstSend.event.targetY == 0, "invalid send target did not fail closed");
        firstSend.failSend = true;
        surface.sent(false, 0, 0);
        require(firstSend.closes == 1 && secondSend.sends == 3,
                "send failure broke another additive effect");
        sendHost.available = false;
        surface.sent(false, 0, 0);
        require(secondSend.sends == 3, "inactive surface received send event");
        surface.close();
        firstSendRegistration.close();
        secondSendRegistration.close();

        ExpandedEffect expanded = new ExpandedEffect();
        AutoCloseable expandedRegistration = register(
                "test.expanded", () -> true, context -> expanded);
        Host expandedHost = new Host();
        surface = PluginComposerRegistry.attach(expandedHost);
        require(surface.hasExpandedEffect(), "expanded effect not attached");
        ComposerExpandedBounds expandedBounds = new ComposerExpandedBounds(-40, -60, 400, 100);
        surface.drawExpanded(new Canvas(), expandedBounds);
        require(expanded.expandedDraws == 1 && expanded.bounds == expandedBounds
                && expanded.expandedGeometry == expandedHost.geometry,
                "expanded draw did not preserve field-local geometry");
        surface.drawExpanded(new Canvas(), new ComposerExpandedBounds(10, 10, 10, 20));
        require(expanded.expandedDraws == 1, "empty expanded bounds were drawn");
        expanded.failExpandedDraw = true;
        surface.drawExpanded(new Canvas(), expandedBounds);
        require(expanded.closes == 1 && !surface.hasExpandedEffect(),
                "expanded draw failure did not fail closed");
        require(expandedHost.effectsChanges > 0, "expanded lifecycle was not reported to host");
        surface.close();
        expandedRegistration.close();

        InsertionEffect firstInsertion = new InsertionEffect();
        InsertionEffect secondInsertion = new InsertionEffect();
        AutoCloseable firstInsertionRegistration = register(
                "test.insertion.first", () -> true, context -> firstInsertion);
        AutoCloseable secondInsertionRegistration = register(
                "test.insertion.second", () -> true, context -> secondInsertion);
        TextEffect insertionTextOwner = new TextEffect();
        AutoCloseable insertionTextRegistration = registerText(
                "test.insertion.text", () -> true, context -> {
                    insertionTextOwner.surface = context;
                    return insertionTextOwner;
                });
        Host insertionHost = new Host();
        surface = PluginComposerRegistry.attach(insertionHost);
        require(surface.hasTextInsertionEffect() && surface.hasTextEffect(),
                "additive insertion effect did not coexist with text owner");
        ComposerTextInsertion insertion = new ComposerTextInsertion(
                afterLayout.revision, afterLayout, 0, 0, 1, false, 0, 1);
        surface.textInserted(insertion);
        require(firstInsertion.insertions == 1 && secondInsertion.insertions == 1
                && firstInsertion.insertion == insertion && secondInsertion.insertion == insertion,
                "insertion event was not delivered consistently");
        firstInsertion.failInsertion = true;
        surface.textInserted(insertion);
        require(firstInsertion.closes == 1 && secondInsertion.insertions == 2
                && insertionTextOwner.closes == 0,
                "insertion failure broke another effect or text owner");
        insertionHost.available = false;
        surface.textInserted(insertion);
        require(secondInsertion.insertions == 2, "inactive surface received insertion event");
        surface.close();
        firstInsertionRegistration.close();
        secondInsertionRegistration.close();
        insertionTextRegistration.close();

        ArrayList<AutoCloseable> registrations = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            registrations.add(register("test.limit" + i, () -> true, context -> new Effect()));
        }

        try {
            register("test.excess", () -> true, context -> new Effect());
            throw new AssertionError("unbounded registration");
        } catch (IllegalStateException expected) {
        }

        for (AutoCloseable item : registrations) {
            item.close();
        }

        ArrayList<PluginComposerRegistry.Surface> surfaces = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            surfaces.add(PluginComposerRegistry.attach(new Host()));
            require(surfaces.get(i) != null, "surface limit too low");
        }

        require(PluginComposerRegistry.attach(new Host()) == null, "unbounded surface registry");

        for (PluginComposerRegistry.Surface item : surfaces) {
            item.close();
        }

        require(PluginComposerRegistry.diagnostics().contains("registrations=0 surfaces=0 pending=0"), "registry leak");
        checkExclusiveCleanupRetry();
        checkAttributedExecution();
    }

    private static void checkExclusiveCleanupRetry() throws Exception {
        TextEffect effect = new TextEffect();
        effect.failClose = true;
        AutoCloseable registration = registerText(
                "test.text.cleanup_retry", () -> true, context -> effect);
        PluginComposerRegistry.Surface surface = PluginComposerRegistry.attach(new Host());
        int handledBeforeCleanup = HostExtensionAttribution.handled;

        rejectsCleanup(registration::close);
        require(HostExtensionAttribution.handled == handledBeforeCleanup + 2,
                "nested composer cleanup failure lost attribution");
        rejects(() -> registerText(
                "test.text.cleanup_conflict", () -> true, context -> new TextEffect()));
        registration.close();
        AutoCloseable replacement = registerText(
                "test.text.cleanup_replacement", () -> true, context -> new TextEffect());
        replacement.close();
        surface.close();
        require(PluginComposerRegistry.diagnostics().contains("registrations=0 surfaces=0 pending=0"),
                "exclusive cleanup retry left registry state");
    }

    private static void checkAttributedExecution() throws Exception {
        String pluginId = "test.attributed";
        Effect effect = new Effect();
        int beforeRegistration = HostExtensionAttribution.pluginIds.size();
        AutoCloseable registration = register(pluginId, () -> true, context -> effect);
        int afterRegistration = HostExtensionAttribution.pluginIds.size();
        PluginComposerRegistry.Surface surface = PluginComposerRegistry.attach(new Host());
        int afterFactory = HostExtensionAttribution.pluginIds.size();
        surface.input(new ComposerInput(1, 0, 1, false, false));
        tick();
        int afterFrame = HostExtensionAttribution.pluginIds.size();
        surface.draw(new Canvas());
        int afterDraw = HostExtensionAttribution.pluginIds.size();
        surface.close();
        int afterClose = HostExtensionAttribution.pluginIds.size();
        registration.close();

        require(afterRegistration == beforeRegistration + 1,
                "compat registration factory lost attribution");
        require(afterFactory == afterRegistration + 1, "composer factory lost attribution");
        require(afterFrame == afterFactory + 2, "composer frame callbacks lost attribution");
        require(afterDraw == afterFrame + 1, "composer draw lost attribution");
        require(afterClose == afterDraw + 1, "composer cleanup lost attribution");

        for (int index = beforeRegistration;
                index < HostExtensionAttribution.pluginIds.size(); index++) {
            require(pluginId.equals(HostExtensionAttribution.pluginIds.get(index)),
                    "composer callback attributed to another plugin");
        }

        String failingPluginId = "test.attributed_factory_failure";
        int handledBeforeFactory = HostExtensionAttribution.handled;
        AutoCloseable failingRegistration = register(
                failingPluginId, () -> true, context -> {
                    throw new IllegalStateException("intentional factory failure");
                });
        PluginComposerRegistry.Surface failingSurface = PluginComposerRegistry.attach(new Host());
        require(HostExtensionAttribution.handled == handledBeforeFactory + 1,
                "composer factory failure lost attribution");
        require(failingPluginId.equals(HostExtensionAttribution.pluginIds.get(
                        HostExtensionAttribution.pluginIds.size() - 1)),
                "composer factory failure attributed to another plugin");
        failingSurface.close();
        failingRegistration.close();
    }

    private static AutoCloseable register(String pluginId, BooleanSupplier active,
            ComposerEffect.Factory factory) {
        return LegacyComposerExtensions.register(pluginId, 1, active, factory);
    }

    private static AutoCloseable registerText(String pluginId, BooleanSupplier active,
            ComposerTextEffect.Factory factory) {
        return LegacyComposerExtensions.registerText(pluginId, 1, active, factory);
    }

    private static AutoCloseable registerCursor(String pluginId, BooleanSupplier active,
            ComposerCursorEffect.Factory factory) {
        return LegacyComposerExtensions.registerCursor(pluginId, 1, active, factory);
    }

    private static AutoCloseable registerSelection(String pluginId, BooleanSupplier active,
            ComposerSelectionEffect.Factory factory) {
        return LegacyComposerExtensions.registerSelection(pluginId, 1, active, factory);
    }

    private static AutoCloseable registerContent(String pluginId, BooleanSupplier active,
            ComposerContentEffect.Factory factory) {
        return LegacyComposerExtensions.registerContent(pluginId, 1, active, factory);
    }

    private static void tick() {
        for (int i = 0; i < 10 && !Choreographer.getInstance().pending.isEmpty(); i++) {
            Choreographer.getInstance().tick(Choreographer.getInstance().getFrameTimeNanos() + 16_666_667L);
        }
    }

    private static void rejects(AutoCloseable action) throws Exception {
        try {
            action.close();
        } catch (IllegalStateException expected) {
            return;
        }

        throw new AssertionError("Expected rejection");
    }

    private static void rejectsCleanup(AutoCloseable action) throws Exception {
        try {
            action.close();
        } catch (Exception expected) {
            return;
        }

        throw new AssertionError("Expected cleanup failure");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

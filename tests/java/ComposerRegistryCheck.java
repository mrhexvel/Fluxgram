package org.telegram.messenger.plugins;

import android.graphics.Canvas;
import android.view.Choreographer;

import org.telegram.messenger.plugins.api.ComposerEffect;
import org.telegram.messenger.plugins.api.ComposerGeometry;
import org.telegram.messenger.plugins.api.ComposerInput;
import org.telegram.messenger.plugins.api.ComposerSurface;
import org.telegram.messenger.plugins.api.ComposerTextCandidate;
import org.telegram.messenger.plugins.api.ComposerTextCapture;
import org.telegram.messenger.plugins.api.ComposerTextChange;
import org.telegram.messenger.plugins.api.ComposerTextClusterSnapshot;
import org.telegram.messenger.plugins.api.ComposerTextEffect;
import org.telegram.messenger.plugins.api.ComposerTextFontSnapshot;
import org.telegram.messenger.plugins.api.ComposerTextLayoutChange;
import org.telegram.messenger.plugins.api.ComposerTextLayoutEffect;
import org.telegram.messenger.plugins.api.ComposerTextLayoutSnapshot;
import org.telegram.messenger.plugins.api.ComposerTextLineSnapshot;
import org.telegram.messenger.plugins.api.ComposerTextPresentation;
import org.telegram.messenger.plugins.api.ComposerTextRunSnapshot;
import org.telegram.messenger.plugins.api.ComposerTextTransitionEffect;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ComposerRegistryCheck {
    private static final class Host implements PluginComposerRegistry.Host {
        boolean available = true;
        int invalidations;
        int captures;
        ComposerTextCandidate capturedCandidate;
        boolean failCapture;
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
        public PluginComposerRegistry.TextCapture captureText(ComposerTextCandidate candidate, String expectedText) {
            if (failCapture) {
                failCapture = false;
                throw new IllegalStateException("intentional host capture failure");
            }

            captures++;
            capturedCandidate = candidate;
            return new Capture(candidate);
        }
    }

    private static final class Capture implements PluginComposerRegistry.TextCapture {
        final ComposerTextCandidate candidate;
        int draws;
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

    private static class LayoutTextEffect extends TextEffect implements ComposerTextLayoutEffect {
        ComposerTextLayoutChange layoutChange;

        @Override
        public void onTextLayoutChanged(ComposerTextLayoutChange value) {
            layoutChange = value;
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

    private static final class OverlayCapture implements PluginComposerRegistry.OverlayTextCapture {
        int draws;
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
    }

    public static void main(String[] args) throws Exception {
        AtomicBoolean active = new AtomicBoolean(true);
        Effect effect = new Effect();
        AutoCloseable registration = PluginComposerRegistry.register("test.effect", active::get, context -> {
            effect.context = context;
            return effect;
        });
        Host host = new Host();
        PluginComposerRegistry.Surface surface = PluginComposerRegistry.attach(host);
        require(effect.context.getAccount() == 1 && effect.context.getOwnerId() == 20
                && effect.context.getDialogId() == 30, "surface identity");
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
        AutoCloseable hiddenRegistration = PluginComposerRegistry.register("test.hidden", () -> true, context -> hidden);
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
        AutoCloseable badRegistration = PluginComposerRegistry.register("test.bad", () -> true, context -> bad);
        AutoCloseable goodRegistration = PluginComposerRegistry.register("test.good", () -> true, context -> good);
        surface = PluginComposerRegistry.attach(new Host());
        surface.input(new ComposerInput(1, 0, 1, false, false));
        tick();
        require(bad.closes == 1 && good.frames == 1, "frame failure broke another effect");
        surface.close();
        badRegistration.close();
        goodRegistration.close();

        Effect retry = new Effect();
        retry.failClose = true;
        AutoCloseable retryRegistration = PluginComposerRegistry.register("test.retry", () -> true, context -> retry);
        surface = PluginComposerRegistry.attach(new Host());
        surface.close();
        require(retry.closes == 1, "failed cleanup silently retried during detach");
        retryRegistration.close();
        require(retry.closes == 2, "failed cleanup was not retained for retry");

        Effect expensive = new Effect();
        expensive.slow = true;
        Effect deferred = new Effect();
        AutoCloseable expensiveRegistration = PluginComposerRegistry.register("test.expensive", () -> true, context -> expensive);
        AutoCloseable deferredRegistration = PluginComposerRegistry.register("test.deferred", () -> true, context -> deferred);
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
        AutoCloseable slowRegistration = PluginComposerRegistry.register("test.slow", () -> true, context -> slow);
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
        AutoCloseable endlessRegistration = PluginComposerRegistry.register("test.endless", () -> true, context -> endless);
        surface = PluginComposerRegistry.attach(new Host());
        surface.input(new ComposerInput(1, 0, 1, false, false));
        Choreographer.getInstance().tick(5_000_000_000L);
        Choreographer.getInstance().tick(7_100_000_000L);
        require(endless.closes == 1 && Choreographer.getInstance().pending.isEmpty(), "endless effect was not stopped");
        surface.close();
        endlessRegistration.close();

        TextEffect text = new TextEffect();
        AutoCloseable textRegistration = PluginComposerRegistry.registerText("test.text", () -> true, context -> {
            text.surface = context;
            return text;
        });
        rejects(() -> PluginComposerRegistry.registerText("test.text.conflict", () -> true, context -> new TextEffect()));
        Host textHost = new Host();
        surface = PluginComposerRegistry.attach(textHost);
        require(surface.hasTextEffect(), "text owner not attached");
        require(!surface.hasTextLayoutEffect(), "legacy text effect became layout-aware");
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
        text.presentation = new ComposerTextPresentation(3, -2, 0.75f, 1.5f, 0.5f, 30, 5, 5);
        surface.textChanged(new ComposerTextChange(9, "abcd", "abcde", 4, 0, 1, 5, 5, -1, -1));
        surface.prepareText();
        surface.drawText(new Canvas(), canvas -> nativeDraws[0]++);
        require(nativeDraws[0] == 3 && text.capture instanceof Capture
                && ((Capture) text.capture).presentation == text.presentation,
                "bounded transform presentation was not applied");
        text.presentation = new ComposerTextPresentation(0, 0, 1, 5, 1, 0, 0, 0);
        surface.textChanged(new ComposerTextChange(10, "abcde", "abcdef", 5, 0, 1, 6, 6, -1, -1));
        surface.prepareText();
        surface.drawText(new Canvas(), canvas -> nativeDraws[0]++);
        require(nativeDraws[0] == 4 && text.closes == 1, "invalid transform did not fail closed");
        surface.close();
        textRegistration.close();

        LayoutTextEffect layoutText = new LayoutTextEffect();
        AutoCloseable layoutTextRegistration = PluginComposerRegistry.registerText(
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

        TransitionTextEffect transitionText = new TransitionTextEffect();
        AutoCloseable transitionRegistration = PluginComposerRegistry.registerText(
                "test.text.transition", () -> true, context -> {
                    transitionText.surface = context;
                    return transitionText;
                });
        surface = PluginComposerRegistry.attach(new Host());
        require(surface.hasTextTransitionEffect(), "transition text effect not attached");
        OverlayCapture overlayCapture = new OverlayCapture();
        transitionText.removedPresentation = new ComposerTextPresentation(
                2, 1, 0.5f, 0.8f, 1.2f, -20, 5, 5);
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
        require(transitionNativeDraws[0] == 2 && overlayCapture.draws == 1,
                "finished removed text overlay was retained");
        surface.close();
        transitionRegistration.close();

        ArrayList<AutoCloseable> registrations = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            registrations.add(PluginComposerRegistry.register("test.limit" + i, () -> true, context -> new Effect()));
        }

        try {
            PluginComposerRegistry.register("test.excess", () -> true, context -> new Effect());
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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

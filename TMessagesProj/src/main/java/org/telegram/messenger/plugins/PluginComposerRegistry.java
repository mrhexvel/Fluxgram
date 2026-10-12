package org.telegram.messenger.plugins;

import android.graphics.Canvas;
import android.os.Looper;
import android.os.Trace;
import android.util.Log;
import android.view.Choreographer;
import android.view.animation.AnimationUtils;

import org.telegram.messenger.plugins.api.ComposerEffect;
import org.telegram.messenger.plugins.api.ComposerCursorEffect;
import org.telegram.messenger.plugins.api.ComposerCursorPresentation;
import org.telegram.messenger.plugins.api.ComposerCursorSnapshot;
import org.telegram.messenger.plugins.api.ComposerExpandedBounds;
import org.telegram.messenger.plugins.api.ComposerExpandedEffect;
import org.telegram.messenger.plugins.api.ComposerGeometry;
import org.telegram.messenger.plugins.api.ComposerInput;
import org.telegram.messenger.plugins.api.ComposerSelectionEffect;
import org.telegram.messenger.plugins.api.ComposerSelectionPresentation;
import org.telegram.messenger.plugins.api.ComposerSelectionSnapshot;
import org.telegram.messenger.plugins.api.ComposerSendEffect;
import org.telegram.messenger.plugins.api.ComposerSendEvent;
import org.telegram.messenger.plugins.api.ComposerSurface;
import org.telegram.messenger.plugins.api.ComposerTextCandidate;
import org.telegram.messenger.plugins.api.ComposerTextCapture;
import org.telegram.messenger.plugins.api.ComposerTextChange;
import org.telegram.messenger.plugins.api.ComposerTextEffect;
import org.telegram.messenger.plugins.api.ComposerTextLayoutChange;
import org.telegram.messenger.plugins.api.ComposerTextLayoutEffect;
import org.telegram.messenger.plugins.api.ComposerTextPresentation;
import org.telegram.messenger.plugins.api.ComposerTextTransitionEffect;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class PluginComposerRegistry {
    public interface Host {
        int account();

        long ownerId();

        long dialogId();

        boolean isAvailable();

        ComposerGeometry geometry();

        void invalidate();

        default void invalidateCursor() {
            invalidate();
        }

        default void cursorOwnerChanged() {
            invalidateCursor();
        }

        default void selectionOwnerChanged() {
            invalidate();
        }

        default void effectsChanged() {
            invalidate();
        }

        TextCapture captureText(ComposerTextCandidate candidate, String expectedText);

        default void drawCursor(Canvas canvas, Consumer<Canvas> nativeDraw,
                ComposerCursorSnapshot snapshot, ComposerCursorPresentation presentation) {
            nativeDraw.accept(canvas);
        }
    }

    public interface TextCapture extends ComposerTextCapture {
        boolean isValid();

        void draw(Canvas canvas, Consumer<Canvas> nativeDraw, ComposerTextPresentation presentation);

        default void release() {
        }
    }

    public interface OverlayTextCapture extends ComposerTextCapture {
        boolean isValid();

        void draw(Canvas canvas, ComposerTextPresentation presentation);

        default void release() {
        }
    }

    public interface SelectionCapture extends ComposerTextCapture {
        boolean isValid();

        void draw(Canvas canvas, ComposerSelectionPresentation presentation);

        default void release() {
        }
    }

    private static final LinkedHashSet<Registration> registrations = new LinkedHashSet<>();
    private static final LinkedHashSet<Surface> surfaces = new LinkedHashSet<>();
    private static ComposerFrameCoordinator coordinator;
    private static long drawTimestamp = Long.MIN_VALUE;
    private static long drawNanos;
    private static long skippedDraws;

    private PluginComposerRegistry() {
    }

    private static ComposerFrameCoordinator frames() {
        requireUi();

        if (coordinator == null) {
            Choreographer choreographer = Choreographer.getInstance();
            Choreographer.FrameCallback callback = timestamp -> coordinator.frame(timestamp);
            coordinator = new ComposerFrameCoordinator(new ComposerFrameCoordinator.Driver() {
                @Override
                public void post(long delayMillis) {
                    choreographer.postFrameCallbackDelayed(callback, delayMillis);
                }

                @Override
                public void cancel() {
                    choreographer.removeFrameCallback(callback);
                }
            }, System::nanoTime);
        }

        return coordinator;
    }

    static AutoCloseable register(String pluginId, BooleanSupplier active, ComposerEffect.Factory factory) {
        requireUi();

        if (factory == null || registrations.size() >= 8) {
            throw new IllegalStateException("Composer effect limit reached or factory missing");
        }

        Registration registration = new Registration(pluginId, active, factory, false, false, false);
        registrations.add(registration);

        for (Surface surface : new ArrayList<>(surfaces)) {
            registration.attach(surface);
        }

        return registration;
    }

    static AutoCloseable registerText(String pluginId, BooleanSupplier active, ComposerTextEffect.Factory factory) {
        requireUi();

        if (factory == null || registrations.size() >= 8) {
            throw new IllegalStateException("Composer effect limit reached or factory missing");
        }

        for (Registration registration : registrations) {
            if (registration.text && !registration.closed) {
                throw new IllegalStateException("Composer native text presentation is already owned");
            }
        }

        Registration registration = new Registration(pluginId, active, factory::create, true, false, false);
        registrations.add(registration);

        for (Surface surface : new ArrayList<>(surfaces)) {
            registration.attach(surface);
        }

        return registration;
    }

    static AutoCloseable registerCursor(
            String pluginId, BooleanSupplier active, ComposerCursorEffect.Factory factory) {
        requireUi();

        if (factory == null || registrations.size() >= 8) {
            throw new IllegalStateException("Composer effect limit reached or factory missing");
        }

        for (Registration registration : registrations) {
            if (registration.cursor && !registration.closed) {
                throw new IllegalStateException("Composer cursor presentation is already owned");
            }
        }

        Registration registration = new Registration(pluginId, active, factory::create, false, true, false);
        registrations.add(registration);

        for (Surface surface : new ArrayList<>(surfaces)) {
            registration.attach(surface);
        }

        return registration;
    }

    static AutoCloseable registerSelection(
            String pluginId, BooleanSupplier active, ComposerSelectionEffect.Factory factory) {
        requireUi();

        if (factory == null || registrations.size() >= 8) {
            throw new IllegalStateException("Composer effect limit reached or factory missing");
        }

        for (Registration registration : registrations) {
            if (registration.selection && !registration.closed) {
                throw new IllegalStateException("Composer selection presentation is already owned");
            }
        }

        Registration registration = new Registration(pluginId, active, factory::create, false, false, true);
        registrations.add(registration);

        for (Surface surface : new ArrayList<>(surfaces)) {
            registration.attach(surface);
        }

        return registration;
    }

    public static Surface attach(Host host) {
        requireUi();

        if (surfaces.size() >= 4 || !host.isAvailable()) {
            return null;
        }

        Surface surface = new Surface(host);
        surfaces.add(surface);

        for (Registration registration : new ArrayList<>(registrations)) {
            registration.attach(surface);
        }

        return surface;
    }

    public static String diagnostics() {
        requireUi();
        return "registrations=" + registrations.size() + " surfaces=" + surfaces.size()
                + " pending=" + (coordinator == null ? 0 : coordinator.pendingCount())
                + " frames=" + (coordinator == null ? 0 : coordinator.frames)
                + " paced=" + (coordinator == null ? 0 : coordinator.paced)
                + " deferred=" + (coordinator == null ? 0 : coordinator.deferred)
                + " skippedDraws=" + skippedDraws;
    }

    private static void requireUi() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("Composer API requires main thread");
        }
    }

    public static final class Surface implements AutoCloseable {
        private Host host;
        private final ArrayList<Binding> bindings = new ArrayList<>();
        private Binding[] snapshot = new Binding[0];
        private Binding cursorOwner;
        private Binding selectionOwner;
        private ComposerSelectionSnapshot selectionSnapshot;
        private SelectionCapture selectionCapture;
        private long sendSequence;
        private int nextDraw;
        private int nextExpandedDraw;
        private boolean drawingPresentedText;

        private Surface(Host host) {
            this.host = host;
        }

        public void input(ComposerInput input) {
            requireUi();

            for (Binding binding : snapshot) {
                binding.input(input);
            }
        }

        public void textChanged(ComposerTextChange change) {
            requireUi();

            for (Binding binding : snapshot) {
                binding.textChanged(change);
            }
        }

        public void prepareText() {
            requireUi();

            for (Binding binding : snapshot) {
                binding.prepareText();
            }
        }

        public void textLayoutChanged(ComposerTextLayoutChange change) {
            requireUi();

            for (Binding binding : snapshot) {
                binding.textLayoutChanged(change);
            }
        }

        public void sent(boolean hasTarget, float targetX, float targetY) {
            requireUi();
            ComposerGeometry geometry = host == null ? null : host.geometry();
            float limit = geometry == null ? 0
                    : Math.max(96 * geometry.density, geometry.height * 2f);
            boolean validTarget = hasTarget && geometry != null
                    && Float.isFinite(targetX) && Float.isFinite(targetY)
                    && targetX >= -limit && targetX <= geometry.width + limit
                    && targetY >= -limit && targetY <= geometry.height + limit;
            ComposerSendEvent event = new ComposerSendEvent(
                    ++sendSequence, validTarget, validTarget ? targetX : 0, validTarget ? targetY : 0);

            for (Binding binding : snapshot) {
                binding.sent(event);
            }
        }

        public void drawText(Canvas canvas, Consumer<Canvas> nativeDraw) {
            requireUi();
            boolean drawn = false;

            for (Binding binding : snapshot) {
                if (binding.registration.text) {
                    drawingPresentedText = true;

                    try {
                        if (binding.drawText(canvas, nativeDraw)) {
                            drawn = true;
                            break;
                        }
                    } finally {
                        drawingPresentedText = false;
                    }
                }
            }

            if (!drawn) {
                nativeDraw.accept(canvas);
            }

            for (Binding binding : snapshot) {
                binding.drawRemovedText(canvas);
            }
        }

        public void drawCursor(Canvas canvas, ComposerCursorSnapshot cursor, Consumer<Canvas> nativeDraw) {
            requireUi();

            if (drawingPresentedText) {
                nativeDraw.accept(canvas);
                return;
            }

            for (Binding binding : snapshot) {
                if (binding.registration.cursor && binding.drawCursor(canvas, cursor, nativeDraw)) {
                    return;
                }
            }

            nativeDraw.accept(canvas);
        }

        public void selectionChanged(ComposerSelectionSnapshot selection, SelectionCapture capture) {
            requireUi();
            boolean changed = selectionSnapshot != null || selectionCapture != null
                    || selection != null || capture != null;
            clearSelectionCapture();

            if (selectionOwner != null && selection != null && capture != null) {
                selectionSnapshot = selection;
                selectionCapture = capture;
            } else if (capture != null) {
                capture.release();
            }

            if (changed && host != null) {
                host.invalidate();
            }
        }

        public void draw(Canvas canvas) {
            requireUi();
            prepareDrawBudget();

            Binding[] current = snapshot;
            int count = current.length;
            boolean drawSelection = selectionOwner != null
                    && selectionSnapshot != null && selectionCapture != null;
            int drawCount = count + (drawSelection ? 1 : 0);
            int from = drawCount == 0 ? 0 : nextDraw % drawCount;
            skippedDraws += from;

            for (int index = from; index < drawCount; index++) {
                if (drawNanos >= 2_000_000L) {
                    skippedDraws += drawCount - index;
                    nextDraw = index;
                    return;
                }

                long start = System.nanoTime();

                if (index < count) {
                    current[index].draw(canvas);
                } else if (selectionOwner != null && selectionSnapshot != null && selectionCapture != null) {
                    selectionOwner.drawSelection(canvas, selectionSnapshot, selectionCapture);
                }

                drawNanos += System.nanoTime() - start;
            }

            nextDraw = 0;
        }

        public void drawExpanded(Canvas canvas, ComposerExpandedBounds bounds) {
            requireUi();

            if (bounds == null || bounds.left >= bounds.right || bounds.top >= bounds.bottom) {
                return;
            }

            prepareDrawBudget();
            Binding[] current = snapshot;
            int count = current.length;
            int from = count == 0 ? 0 : nextExpandedDraw % count;

            for (int index = from; index < count; index++) {
                if (drawNanos >= 2_000_000L) {
                    skippedDraws += count - index;
                    nextExpandedDraw = index;
                    return;
                }

                Binding binding = current[index];

                if (!(binding.effect instanceof ComposerExpandedEffect)) {
                    continue;
                }

                long start = System.nanoTime();
                binding.drawExpanded(canvas, bounds);
                drawNanos += System.nanoTime() - start;
            }

            nextExpandedDraw = 0;
        }

        private void prepareDrawBudget() {
            long timestamp = AnimationUtils.currentAnimationTimeMillis();

            if (timestamp != drawTimestamp) {
                drawTimestamp = timestamp;
                drawNanos = 0;
            }
        }

        public boolean hasEffects() {
            requireUi();
            return snapshot.length != 0;
        }

        public boolean hasTextEffect() {
            requireUi();

            for (Binding binding : snapshot) {
                if (binding.registration.text) {
                    return true;
                }
            }

            return false;
        }

        public boolean hasTextLayoutEffect() {
            requireUi();

            for (Binding binding : snapshot) {
                if (binding.registration.text && binding.effect instanceof ComposerTextLayoutEffect) {
                    return true;
                }
            }

            return false;
        }

        public boolean hasTextTransitionEffect() {
            requireUi();

            for (Binding binding : snapshot) {
                if (binding.registration.text && binding.effect instanceof ComposerTextTransitionEffect) {
                    return true;
                }
            }

            return false;
        }

        public boolean hasCursorEffect() {
            requireUi();

            for (Binding binding : snapshot) {
                if (binding.registration.cursor) {
                    return true;
                }
            }

            return false;
        }

        public boolean hasSelectionEffect() {
            requireUi();
            return selectionOwner != null;
        }

        public boolean hasSendEffect() {
            requireUi();

            for (Binding binding : snapshot) {
                if (binding.effect instanceof ComposerSendEffect) {
                    return true;
                }
            }

            return false;
        }

        public boolean hasExpandedEffect() {
            requireUi();

            for (Binding binding : snapshot) {
                if (binding.effect instanceof ComposerExpandedEffect) {
                    return true;
                }
            }

            return false;
        }

        public boolean isClosed() {
            requireUi();
            return host == null;
        }

        private void changed() {
            snapshot = bindings.toArray(new Binding[0]);
            Binding nextCursorOwner = null;
            Binding nextSelectionOwner = null;

            for (Binding binding : snapshot) {
                if (binding.registration.cursor) {
                    nextCursorOwner = binding;
                } else if (binding.registration.selection) {
                    nextSelectionOwner = binding;
                }
            }

            if (cursorOwner != nextCursorOwner) {
                cursorOwner = nextCursorOwner;

                if (host != null) {
                    host.cursorOwnerChanged();
                }
            }

            if (selectionOwner != nextSelectionOwner) {
                selectionOwner = nextSelectionOwner;
                clearSelectionCapture();

                if (host != null) {
                    host.selectionOwnerChanged();
                }
            }

            if (host != null) {
                host.effectsChanged();
            }
        }

        private void clearSelectionCapture() {
            selectionSnapshot = null;

            if (selectionCapture != null) {
                try {
                    selectionCapture.release();
                } catch (Exception | LinkageError | AssertionError error) {
                    Log.e("FluxGramComposer", "Selection capture release failed", error);
                }

                selectionCapture = null;
            }
        }

        @Override
        public void close() {
            requireUi();
            surfaces.remove(this);

            for (Binding binding : new ArrayList<>(bindings)) {
                try {
                    binding.close();
                } catch (Exception error) {
                    binding.registration.suspend(error);
                }
            }

            bindings.clear();
            changed();
            clearSelectionCapture();
            host = null;
        }
    }

    private static final class Registration implements AutoCloseable {
        final String pluginId;
        final BooleanSupplier active;
        final ComposerEffect.Factory factory;
        final boolean text;
        final boolean cursor;
        final boolean selection;
        final ArrayList<Binding> owned = new ArrayList<>();
        boolean closed;
        boolean failed;

        Registration(String pluginId, BooleanSupplier active, ComposerEffect.Factory factory,
                boolean text, boolean cursor, boolean selection) {
            this.pluginId = pluginId;
            this.active = active;
            this.factory = factory;
            this.text = text;
            this.cursor = cursor;
            this.selection = selection;
        }

        void attach(Surface surface) {
            if (closed || failed || !active.getAsBoolean()) {
                return;
            }

            Binding binding = new Binding(this, surface);
            owned.add(binding);
            surface.bindings.add(binding);
            surface.changed();
            long start = System.nanoTime();

            try {
                binding.effect = factory.create(binding);

                if (binding.effect == null) {
                    throw new IllegalStateException("Composer factory returned null");
                }

                if (!binding.isActive()) {
                    binding.close();
                } else {
                    surface.changed();
                }
            } catch (Exception | LinkageError | AssertionError error) {
                quarantine(error);
            } finally {
                binding.record(start);
            }
        }

        void quarantine(Throwable error) {
            suspend(error);

            for (Binding binding : new ArrayList<>(owned)) {
                try {
                    binding.close();
                } catch (Exception cleanupError) {
                    Log.e("FluxGramComposer", "[" + pluginId + "] cleanup requires retry", cleanupError);
                }
            }
        }

        void suspend(Throwable error) {
            failed = true;
            Log.e("FluxGramComposer", "[" + pluginId + "] effect suspended", error);

            for (Binding binding : new ArrayList<>(owned)) {
                binding.detach();
            }
        }

        @Override
        public void close() throws Exception {
            requireUi();
            closed = true;
            registrations.remove(this);
            Exception failure = null;

            for (Binding binding : new ArrayList<>(owned)) {
                try {
                    binding.close();
                } catch (Exception error) {
                    if (failure == null) {
                        failure = error;
                    } else {
                        failure.addSuppressed(error);
                    }
                }
            }

            Log.i("FluxGramComposer", "[" + pluginId + "] " + diagnostics());

            if (failure != null) {
                throw failure;
            }
        }
    }

    private static final class Binding implements ComposerSurface, ComposerFrameCoordinator.Client {
        final Registration registration;
        final int account;
        final long ownerId;
        final long dialogId;
        Surface surface;
        ComposerEffect effect;
        ComposerInput pendingInput;
        ComposerTextCandidate textCandidate;
        String textCandidateContent;
        TextCapture textCapture;
        ComposerTextLayoutChange removedChange;
        OverlayTextCapture removedCapture;
        long textSequence;
        ComposerGeometry lastGeometry;
        long burstStart;
        int overruns;
        boolean detached;
        boolean framing;

        Binding(Registration registration, Surface surface) {
            this.registration = registration;
            this.surface = surface;
            account = surface.host.account();
            ownerId = surface.host.ownerId();
            dialogId = surface.host.dialogId();
            lastGeometry = surface.host.geometry();
        }

        @Override
        public int getAccount() {
            return account;
        }

        @Override
        public long getOwnerId() {
            return ownerId;
        }

        @Override
        public long getDialogId() {
            return dialogId;
        }

        @Override
        public ComposerGeometry getGeometry() {
            requireUi();

            if (isActive()) {
                lastGeometry = surface.host.geometry();
            }

            return lastGeometry;
        }

        @Override
        public boolean isActive() {
            requireUi();
            return !detached && !registration.closed && !registration.failed
                    && registration.active.getAsBoolean() && surface != null
                    && surface.host != null && surface.host.isAvailable();
        }

        @Override
        public void requestFrame() {
            requireUi();

            if (!framing) {
                burstStart = 0;
            }

            frames().request(this);
        }

        void input(ComposerInput input) {
            if (!isActive()) {
                return;
            }

            int inserted = input.insertedCount + (pendingInput == null ? 0 : pendingInput.insertedCount);
            int removed = input.removedCount + (pendingInput == null ? 0 : pendingInput.removedCount);
            boolean bulk = input.bulk || pendingInput != null && pendingInput.bulk || inserted + removed > 64;
            pendingInput = new ComposerInput(Math.min(65, inserted), Math.min(65, removed),
                    input.textLength, bulk, input.composing);
            burstStart = 0;
            requestFrame();
        }

        void textChanged(ComposerTextChange change) {
            long sequence = ++textSequence;
            textCandidate = null;
            textCandidateContent = null;
            clearTextCapture();
            removedChange = null;
            clearRemovedCapture();

            if (!registration.text || !isActive() || effect == null || change == null) {
                return;
            }

            long start = System.nanoTime();

            try {
                ComposerTextCandidate candidate = ((ComposerTextEffect) effect).selectCandidate(change);

                if (candidate != null && candidate.revision == change.revision
                        && candidate.start >= 0 && candidate.start < candidate.end
                        && candidate.end <= change.text.length() && sequence == textSequence) {
                    textCandidate = candidate;
                    textCandidateContent = change.text;
                }
            } catch (Exception | LinkageError | AssertionError error) {
                registration.quarantine(error);
            } finally {
                record(start);
            }
        }

        void prepareText() {
            if (!registration.text || !isActive() || textCandidate == null || textCapture != null) {
                return;
            }

            ComposerTextCandidate candidate = textCandidate;
            String expectedText = textCandidateContent;
            textCandidate = null;
            textCandidateContent = null;

            try {
                textCapture = surface.host.captureText(candidate, expectedText);
            } catch (Exception | LinkageError | AssertionError error) {
                Log.e("FluxGramComposer", "Native text capture failed", error);
            }
        }

        void textLayoutChanged(ComposerTextLayoutChange change) {
            removedChange = null;
            clearRemovedCapture();

            if (!registration.text || !isActive() || !(effect instanceof ComposerTextLayoutEffect)
                    || change == null) {
                return;
            }

            long start = System.nanoTime();

            try {
                ((ComposerTextLayoutEffect) effect).onTextLayoutChanged(change);

                if (effect instanceof ComposerTextTransitionEffect
                        && change.removedCapture instanceof OverlayTextCapture) {
                    removedChange = change;
                    removedCapture = (OverlayTextCapture) change.removedCapture;
                }
            } catch (Exception | LinkageError | AssertionError error) {
                registration.quarantine(error);
            } finally {
                record(start);
            }
        }

        void sent(ComposerSendEvent event) {
            if (!isActive() || !(effect instanceof ComposerSendEffect) || event == null) {
                return;
            }

            long start = System.nanoTime();

            try {
                ((ComposerSendEffect) effect).onSend(event);
            } catch (Exception | LinkageError | AssertionError error) {
                registration.quarantine(error);
            } finally {
                record(start);
            }
        }

        boolean drawText(Canvas canvas, Consumer<Canvas> nativeDraw) {
            if (!registration.text || !isActive() || effect == null || textCapture == null) {
                return false;
            }

            if (!textCapture.isValid()) {
                clearTextCapture();
                return false;
            }

            long start = System.nanoTime();
            ComposerTextPresentation presentation;

            try {
                presentation = ((ComposerTextEffect) effect).present(textCapture);

                if (presentation == null) {
                    clearTextCapture();
                    return false;
                }

                validatePresentation(presentation, textCapture);

            } catch (Exception | LinkageError | AssertionError error) {
                clearTextCapture();
                registration.quarantine(error);
                return false;
            } finally {
                record(start);
            }

            textCapture.draw(canvas, nativeDraw, presentation);
            return true;
        }

        void drawRemovedText(Canvas canvas) {
            if (!registration.text || !isActive() || !(effect instanceof ComposerTextTransitionEffect)
                    || removedChange == null || removedCapture == null) {
                return;
            }

            if (!removedCapture.isValid()) {
                removedChange = null;
                clearRemovedCapture();
                return;
            }

            long start = System.nanoTime();
            ComposerTextPresentation presentation;

            try {
                presentation = ((ComposerTextTransitionEffect) effect)
                        .presentRemovedText(removedChange, removedCapture);

                if (presentation == null) {
                    removedChange = null;
                    clearRemovedCapture();
                    return;
                }

                validatePresentation(presentation, removedCapture);
            } catch (Exception | LinkageError | AssertionError error) {
                removedChange = null;
                clearRemovedCapture();
                registration.quarantine(error);
                return;
            } finally {
                record(start);
            }

            try {
                removedCapture.draw(canvas, presentation);
            } catch (Exception | LinkageError | AssertionError error) {
                removedChange = null;
                clearRemovedCapture();
                Log.e("FluxGramComposer", "Removed text replay failed", error);
            }
        }

        boolean drawCursor(Canvas canvas, ComposerCursorSnapshot cursor, Consumer<Canvas> nativeDraw) {
            if (!registration.cursor || !isActive() || !(effect instanceof ComposerCursorEffect)
                    || cursor == null) {
                return false;
            }

            long start = System.nanoTime();
            ComposerCursorPresentation presentation;

            try {
                presentation = ((ComposerCursorEffect) effect).presentCursor(cursor);

                if (presentation == null) {
                    return false;
                }

                validateCursorPresentation(presentation, cursor);
            } catch (Exception | LinkageError | AssertionError error) {
                registration.quarantine(error);
                return false;
            } finally {
                record(start);
            }

            try {
                surface.host.drawCursor(canvas, nativeDraw, cursor, presentation);
                return true;
            } catch (Exception | LinkageError | AssertionError error) {
                Log.e("FluxGramComposer", "Native cursor replay failed", error);
                return false;
            }
        }

        void drawSelection(Canvas canvas, ComposerSelectionSnapshot selection, SelectionCapture capture) {
            if (!registration.selection || !isActive() || !(effect instanceof ComposerSelectionEffect)
                    || selection == null || capture == null) {
                return;
            }

            if (!capture.isValid()) {
                surface.selectionChanged(null, null);
                return;
            }

            long start = System.nanoTime();
            ComposerSelectionPresentation presentation;

            try {
                presentation = ((ComposerSelectionEffect) effect).presentSelection(selection);

                if (presentation == null) {
                    return;
                }

                validateSelectionPresentation(presentation, selection, capture);

                if (!capture.isValid()) {
                    surface.selectionChanged(null, null);
                    return;
                }
            } catch (Exception | LinkageError | AssertionError error) {
                surface.selectionChanged(null, null);
                registration.quarantine(error);
                return;
            } finally {
                record(start);
            }

            try {
                capture.draw(canvas, presentation);
            } catch (OutOfMemoryError error) {
                surface.selectionChanged(null, null);
                Log.e("FluxGramComposer", "Selection replay failed", error);
            } catch (Exception | LinkageError | AssertionError error) {
                surface.selectionChanged(null, null);
                Log.e("FluxGramComposer", "Selection replay failed", error);
            }
        }

        private void validatePresentation(ComposerTextPresentation presentation, ComposerTextCapture capture) {
            float limit = Math.max(lastGeometry.width, lastGeometry.height) * 2f;
            float width = capture.getRight() - capture.getLeft();
            float height = capture.getBottom() - capture.getTop();

            validatePresentation(presentation, capture.getLeft(), capture.getTop(), width, height, limit);
        }

        private void validateCursorPresentation(
                ComposerCursorPresentation presentation, ComposerCursorSnapshot cursor) {
            float limit = Math.max(lastGeometry.width, lastGeometry.height) * 2f;
            float width = cursor.right - cursor.left;
            float height = cursor.bottom - cursor.top;

            if (cursor.selectionOffset < 0 || cursor.line < 0 || !Float.isFinite(cursor.baseline)) {
                throw new IllegalArgumentException("Invalid composer cursor snapshot");
            }

            validatePresentation(presentation, cursor.left, cursor.top, width, height, limit);
        }

        private void validateSelectionPresentation(ComposerSelectionPresentation presentation,
                ComposerSelectionSnapshot selection, SelectionCapture capture) {
            float limit = Math.max(lastGeometry.width, lastGeometry.height) * 2f;
            float width = capture.getRight() - capture.getLeft();
            float height = capture.getBottom() - capture.getTop();

            if (selection.start < 0 || selection.start >= selection.end || selection.lineCount < 1
                    || selection.lineCount > 16 || selection.left != capture.getLeft()
                    || selection.top != capture.getTop() || selection.right != capture.getRight()
                    || selection.bottom != capture.getBottom()
                    || selection.revision != capture.getRevision()) {
                throw new IllegalArgumentException("Invalid composer selection snapshot");
            }

            validatePresentation(presentation, capture.getLeft(), capture.getTop(), width, height, limit, 512, 256);
        }

        private void validatePresentation(ComposerTextPresentation presentation,
                float left, float top, float width, float height, float limit) {
            validatePresentation(presentation, left, top, width, height, limit, 256, 256);
        }

        private void validatePresentation(ComposerTextPresentation presentation,
                float left, float top, float width, float height, float limit,
                float maxWidth, float maxHeight) {

            if (!Float.isFinite(presentation.translationX) || !Float.isFinite(presentation.translationY)
                    || !Float.isFinite(width) || width <= 0 || width > maxWidth
                    || !Float.isFinite(height) || height <= 0 || height > maxHeight
                    || !Float.isFinite(presentation.alpha) || presentation.alpha < 0 || presentation.alpha > 1
                    || !Float.isFinite(presentation.scaleX) || presentation.scaleX < 0 || presentation.scaleX > 4
                    || !Float.isFinite(presentation.scaleY) || presentation.scaleY < 0 || presentation.scaleY > 4
                    || !Float.isFinite(presentation.rotationDegrees)
                    || Math.abs(presentation.rotationDegrees) > 360
                    || !Float.isFinite(presentation.pivotX) || presentation.pivotX < 0
                    || presentation.pivotX > width
                    || !Float.isFinite(presentation.pivotY) || presentation.pivotY < 0
                    || presentation.pivotY > height
                    || !Float.isFinite(presentation.tintAmount)
                    || presentation.tintAmount < 0 || presentation.tintAmount > 1
                    || (presentation.tintAmount > 0 && (presentation.tintColor >>> 24) != 0xFF)
                    || !Float.isFinite(presentation.blurRadius) || presentation.blurRadius < 0
                    || presentation.blurRadius > ComposerTextPresentation.MAX_BLUR_RADIUS
                    || Math.abs(presentation.translationX) > limit
                    || Math.abs(presentation.translationY) > limit
                    || !validTransformedBounds(presentation, left, top, width, height, limit)) {
                throw new IllegalArgumentException("Invalid composer text presentation");
            }
        }

        private boolean validTransformedBounds(
                ComposerTextPresentation presentation, float left, float top,
                float width, float height, float limit) {
            double radians = Math.toRadians(presentation.rotationDegrees);
            double cosine = Math.cos(radians);
            double sine = Math.sin(radians);
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;

            for (int corner = 0; corner < 4; corner++) {
                float outset = presentation.blurRadius > 0
                        ? (float) Math.ceil(presentation.blurRadius) + 1 : 0;
                float x = (corner & 1) == 0 ? -outset : width + outset;
                float y = (corner & 2) == 0 ? -outset : height + outset;
                double scaledX = (x - presentation.pivotX) * presentation.scaleX;
                double scaledY = (y - presentation.pivotY) * presentation.scaleY;
                float transformedX = (float) (left + presentation.translationX
                        + presentation.pivotX + scaledX * cosine - scaledY * sine);
                float transformedY = (float) (top + presentation.translationY
                        + presentation.pivotY + scaledX * sine + scaledY * cosine);

                if (!Float.isFinite(transformedX) || !Float.isFinite(transformedY)) {
                    return false;
                }

                minX = Math.min(minX, transformedX);
                minY = Math.min(minY, transformedY);
                maxX = Math.max(maxX, transformedX);
                maxY = Math.max(maxY, transformedY);
            }

            return maxX - minX <= limit && maxY - minY <= limit
                    && minX >= -limit && maxX <= lastGeometry.width + limit
                    && minY >= -limit && maxY <= lastGeometry.height + limit;
        }

        private void clearTextCapture() {
            if (textCapture != null) {
                try {
                    textCapture.release();
                } catch (Exception | LinkageError | AssertionError error) {
                    Log.e("FluxGramComposer", "Native text capture release failed", error);
                }

                textCapture = null;
            }
        }

        private void clearRemovedCapture() {
            if (removedCapture != null) {
                try {
                    removedCapture.release();
                } catch (Exception | LinkageError | AssertionError error) {
                    Log.e("FluxGramComposer", "Removed text capture release failed", error);
                }

                removedCapture = null;
            }
        }

        @Override
        public void onInactive() {
            if (surface != null && surface.host != null && !surface.host.isAvailable()) {
                surface.close();
                return;
            }

            try {
                close();
            } catch (Exception error) {
                registration.suspend(error);
            }
        }

        @Override
        public boolean onFrame(long frameTimeNanos) {
            if (!isActive() || effect == null) {
                return false;
            }

            if (burstStart == 0) {
                burstStart = frameTimeNanos;
            } else if (frameTimeNanos - burstStart > 2_000_000_000L) {
                registration.quarantine(new IllegalStateException("Composer animation exceeded two seconds without input"));
                return false;
            }

            long start = System.nanoTime();
            Trace.beginSection("FGComposer.frame");
            framing = true;

            try {
                ComposerInput input = pendingInput;
                pendingInput = null;

                if (input != null) {
                    effect.onInput(input);
                }

                boolean again = effect.onFrame(frameTimeNanos);

                if (isActive()) {
                    if (registration.cursor) {
                        surface.host.invalidateCursor();
                    } else {
                        surface.host.invalidate();
                    }
                }

                return again;
            } catch (Exception | LinkageError | AssertionError error) {
                registration.quarantine(error);
                return false;
            } finally {
                framing = false;
                Trace.endSection();
                record(start);
            }
        }

        void draw(Canvas canvas) {
            if (!isActive() || effect == null) {
                return;
            }

            ComposerGeometry geometry = getGeometry();
            int save = canvas.save();
            long start = System.nanoTime();
            Trace.beginSection("FGComposer.draw");

            try {
                canvas.clipRect(0, 0, geometry.width, geometry.height);
                effect.draw(canvas, geometry);
            } catch (Exception | LinkageError | AssertionError error) {
                registration.quarantine(error);
            } finally {
                try {
                    canvas.restoreToCount(save);
                } catch (Exception | LinkageError | AssertionError error) {
                    registration.quarantine(error);
                } finally {
                    Trace.endSection();
                    record(start);
                }
            }
        }

        void drawExpanded(Canvas canvas, ComposerExpandedBounds bounds) {
            if (!isActive() || !(effect instanceof ComposerExpandedEffect)) {
                return;
            }

            ComposerGeometry geometry = getGeometry();
            int save = canvas.save();
            long start = System.nanoTime();
            Trace.beginSection("FGComposer.drawExpanded");

            try {
                canvas.clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom);
                ((ComposerExpandedEffect) effect).drawExpanded(canvas, geometry, bounds);
            } catch (Exception | LinkageError | AssertionError error) {
                registration.quarantine(error);
            } finally {
                try {
                    canvas.restoreToCount(save);
                } catch (Exception | LinkageError | AssertionError error) {
                    registration.quarantine(error);
                } finally {
                    Trace.endSection();
                    record(start);
                }
            }
        }

        void record(long start) {
            if (System.nanoTime() - start > 4_000_000L && ++overruns >= 3 && !registration.failed) {
                registration.quarantine(new IllegalStateException("Composer callback budget exceeded"));
            }
        }

        void detach() {
            detached = true;
            frames().cancel(this);
            pendingInput = null;
            textCandidate = null;
            textCandidateContent = null;
            clearTextCapture();
            removedChange = null;
            clearRemovedCapture();

            if (surface != null) {
                surface.bindings.remove(this);
                surface.changed();

                if (surface.host != null) {
                    surface.host.invalidate();
                }

                surface = null;
            }
        }

        void close() throws Exception {
            detach();

            try {
                if (effect != null) {
                    effect.close();
                    effect = null;
                }

                registration.owned.remove(this);
            } catch (LinkageError | AssertionError error) {
                throw new Exception("Composer cleanup failed", error);
            }
        }
    }
}

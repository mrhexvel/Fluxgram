package org.telegram.messenger.plugins;

import android.graphics.Canvas;
import android.os.Looper;
import android.os.Trace;
import android.util.Log;
import android.view.Choreographer;
import android.view.animation.AnimationUtils;

import org.telegram.messenger.plugins.api.ComposerEffect;
import org.telegram.messenger.plugins.api.ComposerGeometry;
import org.telegram.messenger.plugins.api.ComposerInput;
import org.telegram.messenger.plugins.api.ComposerSurface;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.function.BooleanSupplier;

public final class PluginComposerRegistry {
    public interface Host {
        int account();

        long ownerId();

        long dialogId();

        boolean isAvailable();

        ComposerGeometry geometry();

        void invalidate();
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

        Registration registration = new Registration(pluginId, active, factory);
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
        private int nextDraw;

        private Surface(Host host) {
            this.host = host;
        }

        public void input(ComposerInput input) {
            requireUi();

            for (Binding binding : snapshot) {
                binding.input(input);
            }
        }

        public void draw(Canvas canvas) {
            requireUi();
            long timestamp = AnimationUtils.currentAnimationTimeMillis();

            if (timestamp != drawTimestamp) {
                drawTimestamp = timestamp;
                drawNanos = 0;
            }

            Binding[] current = snapshot;
            int count = current.length;
            int from = count == 0 ? 0 : nextDraw % count;
            skippedDraws += from;

            for (int index = from; index < count; index++) {
                if (drawNanos >= 2_000_000L) {
                    skippedDraws += count - index;
                    nextDraw = index;
                    return;
                }

                long start = System.nanoTime();
                current[index].draw(canvas);
                drawNanos += System.nanoTime() - start;
            }

            nextDraw = 0;
        }

        public boolean hasEffects() {
            requireUi();
            return snapshot.length != 0;
        }

        public boolean isClosed() {
            requireUi();
            return host == null;
        }

        private void changed() {
            snapshot = bindings.toArray(new Binding[0]);
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
            host = null;
        }
    }

    private static final class Registration implements AutoCloseable {
        final String pluginId;
        final BooleanSupplier active;
        final ComposerEffect.Factory factory;
        final ArrayList<Binding> owned = new ArrayList<>();
        boolean closed;
        boolean failed;

        Registration(String pluginId, BooleanSupplier active, ComposerEffect.Factory factory) {
            this.pluginId = pluginId;
            this.active = active;
            this.factory = factory;
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
        ComposerGeometry lastGeometry;
        long burstStart;
        int overruns;
        boolean detached;

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

            try {
                ComposerInput input = pendingInput;
                pendingInput = null;

                if (input != null) {
                    effect.onInput(input);
                }

                boolean again = effect.onFrame(frameTimeNanos);

                if (isActive()) {
                    surface.host.invalidate();
                }

                return again;
            } catch (Exception | LinkageError | AssertionError error) {
                registration.quarantine(error);
                return false;
            } finally {
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

        void record(long start) {
            if (System.nanoTime() - start > 4_000_000L && ++overruns >= 3 && !registration.failed) {
                registration.quarantine(new IllegalStateException("Composer callback budget exceeded"));
            }
        }

        void detach() {
            detached = true;
            frames().cancel(this);
            pendingInput = null;

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

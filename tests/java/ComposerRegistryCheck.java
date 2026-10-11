package org.telegram.messenger.plugins;

import android.graphics.Canvas;
import android.view.Choreographer;

import org.telegram.messenger.plugins.api.ComposerEffect;
import org.telegram.messenger.plugins.api.ComposerGeometry;
import org.telegram.messenger.plugins.api.ComposerInput;
import org.telegram.messenger.plugins.api.ComposerSurface;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ComposerRegistryCheck {
    private static final class Host implements PluginComposerRegistry.Host {
        boolean available = true;
        int invalidations;
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
    }

    private static final class Effect implements ComposerEffect {
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
        Choreographer.getInstance().tick(1_000_000_000L);
        Choreographer.getInstance().tick(3_100_000_000L);
        require(endless.closes == 1 && Choreographer.getInstance().pending.isEmpty(), "endless effect was not stopped");
        surface.close();
        endlessRegistration.close();

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
            Choreographer.getInstance().tick(1_000_000_000L + i * 16_000_000L);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

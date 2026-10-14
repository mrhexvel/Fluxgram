package org.telegram.messenger.plugins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class HostExtensionRegistryCheck {
    private interface CheckedAction {
        void run() throws Exception;
    }

    private static final class Boundary implements HostExtensionRegistry.CallbackBoundary {
        final List<String> pluginIds = new ArrayList<>();
        final List<Throwable> handled = new ArrayList<>();

        @Override
        public void run(String pluginId, HostExtensionRegistry.Operation operation) throws Exception {
            pluginIds.add(pluginId);
            operation.run();
        }

        @Override
        public void handled(Throwable error) {
            handled.add(error);
        }
    }

    private static final class Owner {
        final AtomicBoolean current = new AtomicBoolean(true);
        final PluginSession session;
        final PluginScope scope;

        Owner(String pluginId) {
            session = new PluginSession(pluginId, 1, current::get, () -> {});
            scope = session.openScope("extensions", () -> {}, 64);
        }

        void close() throws Exception {
            scope.close();
            session.close();
        }
    }

    public static void main(String[] args) throws Exception {
        checkOrderingConflictsAndContributionBounds();
        checkReplacementExclusivityBudgetAndFailureBoundary();
        checkReplacementBudgetAndCleanupRetry();
        checkFactoriesRunOutsideRegistryLock();
        checkExceptionQuarantinesOnlyBinding();
        checkBudgetQuarantinesRegistration();
        checkDisableDuringCallback();
        checkConcurrentCloseDuringCallback();
        checkCleanupRetryDuringFactory();
        checkConcurrentCleanupDuringFactory();
        checkEndpointCleanupRetry();
        checkScopeCleanupRetry();
        checkDetachedObserverCleanupRetry();
    }

    private static void checkOrderingConflictsAndContributionBounds() throws Exception {
        Boundary boundary = new Boundary();
        List<Throwable> reports = new ArrayList<>();
        HostExtensionRegistry registry = new HostExtensionRegistry(() -> 0, boundary, reports::add);
        HostExtensionDescriptor<String, String> descriptor = contributor("test.order", 10, 3, 2);
        HostExtensionRegistry.Endpoint<String, String> endpoint = registry.openEndpoint(descriptor);
        Owner pluginB = new Owner("plugin.b");
        Owner pluginA = new Owner("plugin.a");
        Owner pluginC = new Owner("plugin.c");
        AtomicReference<HostExtensionContributionSink<String>> lateSink = new AtomicReference<>();
        AtomicBoolean overflowRejected = new AtomicBoolean();

        registry.registerContributor(pluginB.scope, descriptor,
                new HostExtensionRegistrationSpec("b", 5),
                contribution("b", null, null));
        registry.registerContributor(pluginA.scope, descriptor,
                new HostExtensionRegistrationSpec("a1", 5),
                contribution("a1", lateSink, overflowRejected));
        registry.registerContributor(pluginA.scope, descriptor,
                new HostExtensionRegistrationSpec("a2", 5),
                contribution("a2", null, null));
        registry.registerContributor(pluginC.scope, descriptor,
                new HostExtensionRegistrationSpec("c", 10),
                contribution("c", null, null));

        List<String> values = endpoint.collectContributions("input");
        require(values.equals(Arrays.asList("c", "c-extra", "a1", "a1-extra",
                "a2", "a2-extra", "b", "b-extra")), "unexpected contribution order: " + values);
        require(overflowRejected.get(), "per-callback contribution limit was not enforced");
        require(!lateSink.get().add("late"), "late contribution was accepted");
        rejectsRuntime(() -> values.add("mutable"));
        rejectsRuntime(() -> registry.registerContributor(pluginA.scope, descriptor,
                new HostExtensionRegistrationSpec("a1", 5), contribution("duplicate", null, null)));
        rejectsRuntime(() -> registry.openEndpoint(contributor("test.order", 11, 3, 2)));
        rejectsRuntime(() -> registry.openEndpoint(contributor("test.order", 10, 3, 2)));
        require(registry.registrationCount() == 4, "duplicate registration changed registry state");
        require(registry.bindingCount() == 4, "unexpected binding count");
        require(reports.isEmpty() && boundary.handled.isEmpty(), "unexpected callback failure");

        endpoint.close();
        pluginC.close();
        pluginA.close();
        pluginB.close();
        require(registry.registrationCount() == 0 && registry.endpointCount() == 0,
                "ordered registry did not clean up");
    }

    private static HostExtensionContributorFactory<String, String> contribution(
            String value, AtomicReference<HostExtensionContributionSink<String>> lateSink,
            AtomicBoolean overflowRejected) {
        return () -> (input, sink) -> {
            sink.add(value);
            sink.add(value + "-extra");

            if (overflowRejected != null) {
                overflowRejected.set(!sink.add(value + "-overflow"));
            }

            if (lateSink != null) {
                lateSink.set(sink);
            }
        };
    }

    private static void checkReplacementExclusivityBudgetAndFailureBoundary() throws Exception {
        AtomicLong clock = new AtomicLong();
        Boundary boundary = new Boundary();
        List<Throwable> reports = new ArrayList<>();
        HostExtensionRegistry registry = new HostExtensionRegistry(clock::get, boundary, reports::add);
        HostExtensionDescriptor<ReplacementInput, Void> descriptor = replacement(
                "test.replacement", 10, 2);
        AtomicBoolean firstActive = new AtomicBoolean(true);
        AtomicBoolean secondActive = new AtomicBoolean(true);
        AtomicInteger closes = new AtomicInteger();

        AutoCloseable registration = registry.registerReplacement(
                "plugin.first", 1, firstActive::get, descriptor,
                new HostExtensionRegistrationSpec("renderer", 0),
                () -> new HostExtensionReplacement<ReplacementInput>() {
                    @Override
                    public boolean replace(ReplacementInput input) {
                        if (input.pluginFailure) {
                            throw new IllegalStateException("intentional plugin failure");
                        }

                        input.callNative();
                        clock.addAndGet(5);
                        return true;
                    }

                    @Override
                    public void close() {
                        closes.incrementAndGet();
                    }
                });
        rejectsRuntime(() -> registry.registerReplacement(
                "plugin.second", 1, secondActive::get, descriptor,
                new HostExtensionRegistrationSpec("renderer", 0), inputFactory(false)));

        HostExtensionRegistry.Endpoint<ReplacementInput, Void> endpoint =
                registry.openEndpoint(descriptor);
        ReplacementInput successful = new ReplacementInput(clock, false, false);
        require(endpoint.dispatchReplacement(successful),
                "replacement did not handle input");
        require(successful.nativeCalls == 1, "replacement did not invoke native continuation");
        require(registry.registrationCount() == 1 && registry.bindingCount() == 1,
                "native continuation time consumed callback budget");

        ReplacementInput hostFailure = new ReplacementInput(clock, true, false);
        require(!endpoint.dispatchReplacement(hostFailure),
                "host failure committed replacement");
        require(boundary.handled.size() == 1 && reports.size() == 1
                        && reports.get(0) == hostFailure.hostFailure,
                "host failure was attributed to plugin");
        require(registry.registrationCount() == 1 && registry.bindingCount() == 1,
                "host failure quarantined replacement");

        require(!endpoint.dispatchReplacement(new ReplacementInput(clock, false, true)),
                "plugin failure committed replacement");
        require(boundary.handled.size() == 2 && reports.size() == 2,
                "plugin failure was not attributed");
        require(registry.registrationCount() == 1 && registry.bindingCount() == 0,
                "plugin failure did not quarantine binding");

        endpoint.close();
        registration.close();
        require(closes.get() == 1 && registry.registrationCount() == 0,
                "replacement cleanup did not release exclusivity");

        registry.registerReplacement("plugin.second", 1, secondActive::get, descriptor,
                new HostExtensionRegistrationSpec("renderer", 0), inputFactory(true)).close();
    }

    private static HostExtensionReplacementFactory<ReplacementInput> inputFactory(boolean result) {
        return () -> input -> result;
    }

    private static void checkReplacementBudgetAndCleanupRetry() throws Exception {
        AtomicLong clock = new AtomicLong();
        Boundary boundary = new Boundary();
        List<Throwable> reports = new ArrayList<>();
        HostExtensionRegistry registry = new HostExtensionRegistry(clock::get, boundary, reports::add);
        HostExtensionDescriptor<ReplacementInput, Void> budgetDescriptor = replacement(
                "test.replacement_budget", 10, 2);
        AutoCloseable budgetRegistration = registry.registerReplacement(
                "plugin.budget", 1, () -> true, budgetDescriptor,
                new HostExtensionRegistrationSpec("renderer", 0),
                () -> input -> {
                    clock.addAndGet(11);
                    return true;
                });
        HostExtensionRegistry.Endpoint<ReplacementInput, Void> budgetEndpoint =
                registry.openEndpoint(budgetDescriptor);

        require(!budgetEndpoint.dispatchReplacement(
                        new ReplacementInput(clock, false, false)),
                "replacement budget overrun committed frame");
        require(registry.registrationCount() == 1 && registry.bindingCount() == 1,
                "first replacement budget overrun quarantined registration");
        require(!budgetEndpoint.dispatchReplacement(
                        new ReplacementInput(clock, false, false)),
                "quarantined replacement committed frame");
        require(registry.registrationCount() == 0 && registry.bindingCount() == 0
                        && reports.size() == 1,
                "replacement budget quarantine mismatch");
        budgetEndpoint.close();
        budgetRegistration.close();

        HostExtensionDescriptor<ReplacementInput, Void> cleanupDescriptor = replacement(
                "test.replacement_cleanup", 10, 2);
        AtomicInteger closes = new AtomicInteger();
        AutoCloseable cleanupRegistration = registry.registerReplacement(
                "plugin.cleanup", 1, () -> true, cleanupDescriptor,
                new HostExtensionRegistrationSpec("renderer", 0),
                () -> new HostExtensionReplacement<ReplacementInput>() {
                    @Override
                    public boolean replace(ReplacementInput input) {
                        return true;
                    }

                    @Override
                    public void close() throws Exception {
                        if (closes.incrementAndGet() == 1) {
                            throw new Exception("intentional replacement cleanup failure");
                        }
                    }
                });
        HostExtensionRegistry.Endpoint<ReplacementInput, Void> cleanupEndpoint =
                registry.openEndpoint(cleanupDescriptor);

        rejects(cleanupRegistration::close);
        require(registry.registrationCount() == 1,
                "failed replacement cleanup released exclusivity");
        rejectsRuntime(() -> registry.registerReplacement(
                "plugin.conflict", 1, () -> true, cleanupDescriptor,
                new HostExtensionRegistrationSpec("renderer", 0), inputFactory(true)));
        cleanupRegistration.close();
        require(closes.get() == 2 && registry.registrationCount() == 0,
                "replacement cleanup retry mismatch");
        cleanupEndpoint.close();
    }

    private static final class ReplacementInput
            implements HostExtensionBudgetInput, HostExtensionFailureInput {
        final AtomicLong clock;
        final RuntimeException hostFailure = new RuntimeException("intentional host failure");
        final boolean failHost;
        final boolean pluginFailure;
        long excludedNanos;
        int nativeCalls;

        ReplacementInput(AtomicLong clock, boolean failHost, boolean pluginFailure) {
            this.clock = clock;
            this.failHost = failHost;
            this.pluginFailure = pluginFailure;
        }

        void callNative() {
            long started = clock.get();

            try {
                nativeCalls++;
                clock.addAndGet(100);

                if (failHost) {
                    throw hostFailure;
                }

            } finally {
                excludedNanos += Math.max(0, clock.get() - started);
            }
        }

        @Override
        public long excludedCallbackNanos() {
            return excludedNanos;
        }

        @Override
        public boolean isHostFailure(Throwable error) {
            return error == hostFailure;
        }
    }

    private static void checkFactoriesRunOutsideRegistryLock() throws Exception {
        Boundary boundary = new Boundary();
        List<Throwable> reports = new ArrayList<>();
        HostExtensionRegistry registry = new HostExtensionRegistry(() -> 0, boundary, reports::add);
        HostExtensionDescriptor<String, Void> descriptor = observer("test.lock", 10, 2);
        Owner owner = new Owner("plugin.lock");

        registry.registerObserver(owner.scope, descriptor,
                new HostExtensionRegistrationSpec("lock", 0), () -> {
                    Thread probe = new Thread(registry::endpointCount);
                    probe.start();
                    probe.join(1000);

                    if (probe.isAlive()) {
                        throw new AssertionError("Factory ran under registry lock");
                    }

                    return input -> {};
                });

        HostExtensionRegistry.Endpoint<String, Void> endpoint = registry.openEndpoint(descriptor);
        require(registry.bindingCount() == 1, "factory did not create binding");
        require(reports.isEmpty() && boundary.handled.isEmpty(), "factory lock probe failed");

        endpoint.close();
        owner.close();
    }

    private static void checkExceptionQuarantinesOnlyBinding() throws Exception {
        Boundary boundary = new Boundary();
        List<Throwable> reports = new ArrayList<>();
        HostExtensionRegistry registry = new HostExtensionRegistry(() -> 0, boundary, reports::add);
        HostExtensionDescriptor<String, String> descriptor = contributor("test.failure", 10, 2, 1);
        HostExtensionRegistry.Endpoint<String, String> first = registry.openEndpoint(descriptor);
        HostExtensionRegistry.Endpoint<String, String> second = registry.openEndpoint(descriptor);
        Owner bad = new Owner("plugin.bad");
        Owner good = new Owner("plugin.good");
        AtomicInteger badCloses = new AtomicInteger();

        registry.registerContributor(bad.scope, descriptor,
                new HostExtensionRegistrationSpec("bad", 10), () -> new HostExtensionContributor<String, String>() {
                    @Override
                    public void contribute(String input, HostExtensionContributionSink<String> sink) {
                        throw new IllegalStateException("intentional callback failure");
                    }

                    @Override
                    public void close() {
                        badCloses.incrementAndGet();
                    }
                });
        registry.registerContributor(good.scope, descriptor,
                new HostExtensionRegistrationSpec("good", 0), contribution("good", null, null));

        require(first.collectContributions("first").equals(Arrays.asList("good")),
                "exception changed contributor fallback");
        require(first.collectContributions("again").equals(Arrays.asList("good")),
                "quarantined binding ran again");
        require(second.collectContributions("second").equals(Arrays.asList("good")),
                "failure quarantined another surface binding");
        require(badCloses.get() == 2, "failed bindings were not closed independently");
        require(boundary.handled.size() == 2 && reports.size() == 2,
                "callback failure attribution mismatch");
        require(registry.registrationCount() == 2 && registry.bindingCount() == 2,
                "binding quarantine removed registration or healthy bindings");

        second.close();
        first.close();
        good.close();
        bad.close();
    }

    private static void checkBudgetQuarantinesRegistration() throws Exception {
        AtomicLong clock = new AtomicLong();
        Boundary boundary = new Boundary();
        List<Throwable> reports = new ArrayList<>();
        HostExtensionRegistry registry = new HostExtensionRegistry(clock::get, boundary, reports::add);
        HostExtensionDescriptor<String, String> descriptor = contributor("test.budget", 10, 2, 1);
        HostExtensionRegistry.Endpoint<String, String> endpoint = registry.openEndpoint(descriptor);
        Owner owner = new Owner("plugin.slow");
        AtomicInteger closes = new AtomicInteger();

        registry.registerContributor(owner.scope, descriptor,
                new HostExtensionRegistrationSpec("slow", 0), () -> new HostExtensionContributor<String, String>() {
                    @Override
                    public void contribute(String input, HostExtensionContributionSink<String> sink) {
                        sink.add(input);
                        clock.addAndGet(11);
                    }

                    @Override
                    public void close() {
                        closes.incrementAndGet();
                    }
                });

        require(endpoint.collectContributions("first").equals(Arrays.asList("first")),
                "first budget overrun did not preserve result");
        require(endpoint.collectContributions("second").isEmpty(),
                "quarantining budget overrun preserved result");
        require(endpoint.collectContributions("third").isEmpty(),
                "budget-quarantined registration ran again");
        require(registry.registrationCount() == 0 && registry.bindingCount() == 0,
                "budget quarantine left active registry state");
        require(closes.get() == 1 && reports.size() == 1,
                "budget quarantine cleanup or report mismatch");

        endpoint.close();
        owner.close();
    }

    private static void checkDisableDuringCallback() throws Exception {
        Boundary boundary = new Boundary();
        HostExtensionRegistry registry = new HostExtensionRegistry(() -> 0, boundary, error -> {});
        HostExtensionDescriptor<String, Void> descriptor = observer("test.disable", 10, 2);
        HostExtensionRegistry.Endpoint<String, Void> endpoint = registry.openEndpoint(descriptor);
        Owner disabled = new Owner("plugin.disable");
        Owner other = new Owner("plugin.other");
        AtomicInteger invalidatorCalls = new AtomicInteger();
        AtomicInteger staleCalls = new AtomicInteger();
        AtomicInteger otherCalls = new AtomicInteger();

        registry.registerObserver(disabled.scope, descriptor,
                new HostExtensionRegistrationSpec("disable", 10), () -> input -> {
                    invalidatorCalls.incrementAndGet();
                    disabled.current.set(false);
                });
        registry.registerObserver(disabled.scope, descriptor,
                new HostExtensionRegistrationSpec("stale", 5),
                () -> input -> staleCalls.incrementAndGet());
        registry.registerObserver(other.scope, descriptor,
                new HostExtensionRegistrationSpec("other", 0),
                () -> input -> otherCalls.incrementAndGet());

        endpoint.dispatchObserved("first");
        endpoint.dispatchObserved("second");
        require(invalidatorCalls.get() == 1, "disabled callback ran again");
        require(staleCalls.get() == 0, "later callback ran after generation invalidation");
        require(otherCalls.get() == 2, "disabling one plugin suppressed another plugin");

        endpoint.close();
        disabled.close();
        other.close();
    }

    private static void checkEndpointCleanupRetry() throws Exception {
        Boundary boundary = new Boundary();
        HostExtensionRegistry registry = new HostExtensionRegistry(() -> 0, boundary, error -> {});
        HostExtensionDescriptor<String, Void> descriptor = observer("test.endpoint_cleanup", 10, 2);
        HostExtensionRegistry.Endpoint<String, Void> endpoint = registry.openEndpoint(descriptor);
        Owner owner = new Owner("plugin.endpoint_cleanup");
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();

        registry.registerObserver(owner.scope, descriptor,
                new HostExtensionRegistrationSpec("retry", 0),
                () -> failingObserver(calls, closes));
        endpoint.dispatchObserved("before");
        rejects(endpoint::close);
        endpoint.dispatchObserved("after");
        endpoint.close();
        require(calls.get() == 1, "closed endpoint dispatched after failed cleanup");
        require(closes.get() == 2, "endpoint cleanup did not retry only failed binding");

        owner.close();
        require(closes.get() == 2, "successful endpoint cleanup repeated through scope");
    }

    private static void checkCleanupRetryDuringFactory() throws Exception {
        Boundary boundary = new Boundary();
        List<Throwable> reports = new ArrayList<>();
        HostExtensionRegistry registry = new HostExtensionRegistry(() -> 0, boundary, reports::add);
        HostExtensionDescriptor<String, Void> descriptor = observer("test.factory_cleanup", 10, 2);
        Owner owner = new Owner("plugin.factory_cleanup");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger closes = new AtomicInteger();
        AtomicReference<HostExtensionRegistry.Endpoint<String, Void>> opened = new AtomicReference<>();

        registry.registerObserver(owner.scope, descriptor,
                new HostExtensionRegistrationSpec("factory", 0), () -> {
                    entered.countDown();

                    if (!release.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("factory release timed out");
                    }

                    return new HostExtensionObserver<String>() {
                        @Override
                        public void onObserved(String input) {
                        }

                        @Override
                        public void close() throws Exception {
                            if (closes.incrementAndGet() == 1) {
                                throw new Exception("intentional unpublished cleanup failure");
                            }
                        }
                    };
                });

        Thread opening = new Thread(() -> opened.set(registry.openEndpoint(descriptor)));
        opening.start();
        require(entered.await(1, TimeUnit.SECONDS), "factory did not start");
        rejects(owner.scope::close);
        release.countDown();
        opening.join(2000);
        require(!opening.isAlive() && opened.get() != null, "endpoint opening did not finish");
        require(closes.get() == 1 && reports.size() == 1,
                "unpublished callback cleanup failure was not retained");
        owner.scope.close();
        owner.session.close();
        require(closes.get() == 2, "scope did not retry unpublished callback cleanup");

        opened.get().close();
        require(closes.get() == 2, "successful unpublished cleanup repeated");
    }

    private static void checkConcurrentCloseDuringCallback() throws Exception {
        Boundary boundary = new Boundary();
        HostExtensionRegistry registry = new HostExtensionRegistry(() -> 0, boundary, error -> {});
        HostExtensionDescriptor<String, Void> descriptor = observer("test.concurrent_close", 10, 2);
        HostExtensionRegistry.Endpoint<String, Void> endpoint = registry.openEndpoint(descriptor);
        Owner owner = new Owner("plugin.concurrent_close");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();

        registry.registerObserver(owner.scope, descriptor,
                new HostExtensionRegistrationSpec("running", 0),
                () -> new HostExtensionObserver<String>() {
                    @Override
                    public void onObserved(String input) throws Exception {
                        calls.incrementAndGet();
                        entered.countDown();

                        if (!release.await(2, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("callback release timed out");
                        }
                    }

                    @Override
                    public void close() {
                        closes.incrementAndGet();
                    }
                });

        Thread dispatch = new Thread(() -> endpoint.dispatchObserved("running"));
        dispatch.start();
        require(entered.await(1, TimeUnit.SECONDS), "callback did not start");
        rejects(endpoint::close);
        release.countDown();
        dispatch.join(2000);
        require(!dispatch.isAlive(), "callback did not finish");
        endpoint.close();
        require(calls.get() == 1 && closes.get() == 1,
                "concurrent close raced callback or lost cleanup retry");

        owner.close();
    }

    private static void checkConcurrentCleanupDuringFactory() throws Exception {
        Boundary boundary = new Boundary();
        HostExtensionRegistry registry = new HostExtensionRegistry(() -> 0, boundary, error -> {});
        HostExtensionDescriptor<String, Void> descriptor = observer("test.factory_concurrent", 10, 2);
        Owner owner = new Owner("plugin.factory_concurrent");
        CountDownLatch factoryEntered = new CountDownLatch(1);
        CountDownLatch factoryRelease = new CountDownLatch(1);
        CountDownLatch cleanupEntered = new CountDownLatch(1);
        CountDownLatch cleanupRelease = new CountDownLatch(1);
        AtomicInteger closes = new AtomicInteger();
        AtomicReference<HostExtensionRegistry.Endpoint<String, Void>> opened = new AtomicReference<>();

        registry.registerObserver(owner.scope, descriptor,
                new HostExtensionRegistrationSpec("factory", 0), () -> {
                    factoryEntered.countDown();

                    if (!factoryRelease.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("factory release timed out");
                    }

                    return new HostExtensionObserver<String>() {
                        @Override
                        public void onObserved(String input) {
                        }

                        @Override
                        public void close() throws Exception {
                            closes.incrementAndGet();
                            cleanupEntered.countDown();

                            if (!cleanupRelease.await(2, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("cleanup release timed out");
                            }
                        }
                    };
                });

        Thread opening = new Thread(() -> opened.set(registry.openEndpoint(descriptor)));
        opening.start();
        require(factoryEntered.await(1, TimeUnit.SECONDS), "factory did not start");
        rejects(owner.scope::close);
        factoryRelease.countDown();
        require(cleanupEntered.await(1, TimeUnit.SECONDS), "rejected callback cleanup did not start");
        rejects(owner.scope::close);
        cleanupRelease.countDown();
        opening.join(2000);
        require(!opening.isAlive() && opened.get() != null, "endpoint opening did not finish");
        owner.scope.close();
        owner.session.close();
        require(closes.get() == 1, "concurrent rejected callback cleanup repeated");

        opened.get().close();
        require(closes.get() == 1, "endpoint repeated rejected callback cleanup");
    }

    private static void checkScopeCleanupRetry() throws Exception {
        Boundary boundary = new Boundary();
        HostExtensionRegistry registry = new HostExtensionRegistry(() -> 0, boundary, error -> {});
        HostExtensionDescriptor<String, Void> descriptor = observer("test.scope_cleanup", 10, 2);
        HostExtensionRegistry.Endpoint<String, Void> endpoint = registry.openEndpoint(descriptor);
        Owner owner = new Owner("plugin.scope_cleanup");
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();

        registry.registerObserver(owner.scope, descriptor,
                new HostExtensionRegistrationSpec("retry", 0),
                () -> failingObserver(calls, closes));
        endpoint.dispatchObserved("before");
        rejects(owner.scope::close);
        endpoint.dispatchObserved("after");
        owner.scope.close();
        owner.session.close();
        require(calls.get() == 1, "closing scope dispatched after failed cleanup");
        require(closes.get() == 2, "scope cleanup did not retry failed binding");
        require(registry.registrationCount() == 0 && registry.bindingCount() == 0,
                "scope retry left registry state");

        endpoint.close();
        require(closes.get() == 2, "successful scope cleanup repeated through endpoint");
    }

    private static void checkDetachedObserverCleanupRetry() throws Exception {
        Boundary boundary = new Boundary();
        HostExtensionRegistry registry = new HostExtensionRegistry(() -> 0, boundary, error -> {});
        HostExtensionDescriptor<String, Void> descriptor = observer("test.detached", 10, 2);
        HostExtensionRegistry.Endpoint<String, Void> endpoint = registry.openEndpoint(descriptor);
        AtomicBoolean active = new AtomicBoolean(true);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        AutoCloseable registration = registry.registerObserver(
                "plugin.detached", 7, active::get, descriptor,
                new HostExtensionRegistrationSpec("detached", 0),
                () -> failingObserver(calls, closes));

        endpoint.dispatchObserved("before");
        rejects(registration::close);
        endpoint.dispatchObserved("after");
        require(registry.registrationCount() == 1 && registry.bindingCount() == 1,
                "failed detached cleanup released registry state");
        registration.close();
        require(calls.get() == 1, "closing detached registration remained active");
        require(closes.get() == 2, "detached cleanup did not retry");
        require(registry.registrationCount() == 0 && registry.bindingCount() == 0,
                "detached cleanup left registry state");
        require(boundary.pluginIds.equals(Arrays.asList(
                "plugin.detached", "plugin.detached", "plugin.detached", "plugin.detached")),
                "detached callbacks lost attribution");

        rejectsRuntime(() -> registry.registerObserver(
                "plugin.factory_failure", 8, () -> true, descriptor,
                new HostExtensionRegistrationSpec("factory_failure", 0),
                () -> {
                    throw new IllegalStateException("intentional factory failure");
                }));
        require(registry.registrationCount() == 0 && registry.bindingCount() == 0,
                "strict detached factory rollback left registry state");

        rejectsRuntime(() -> registry.registerObserver(
                "plugin.inactive", 9, () -> false, descriptor,
                new HostExtensionRegistrationSpec("inactive", 0),
                () -> failingObserver(new AtomicInteger(), new AtomicInteger())));
        endpoint.close();
    }

    private static HostExtensionObserver<String> failingObserver(
            AtomicInteger calls, AtomicInteger closes) {
        return new HostExtensionObserver<String>() {
            @Override
            public void onObserved(String input) {
                calls.incrementAndGet();
            }

            @Override
            public void close() throws Exception {
                if (closes.incrementAndGet() == 1) {
                    throw new Exception("intentional cleanup failure");
                }
            }
        };
    }

    private static HostExtensionDescriptor<String, Void> observer(
            String id, long budget, int overruns) {
        return new HostExtensionDescriptor<>(
                id, 1, HostExtensionDescriptor.Mode.OBSERVER, budget, overruns, 0);
    }

    private static HostExtensionDescriptor<String, String> contributor(
            String id, long budget, int overruns, int contributions) {
        return new HostExtensionDescriptor<>(
                id, 1, HostExtensionDescriptor.Mode.CONTRIBUTOR,
                budget, overruns, contributions);
    }

    private static <I> HostExtensionDescriptor<I, Void> replacement(
            String id, long budget, int overruns) {
        return new HostExtensionDescriptor<>(
                id, 1, HostExtensionDescriptor.Mode.REPLACEMENT, budget, overruns, 0);
    }

    private static void rejects(CheckedAction action) throws Exception {
        try {
            action.run();
        } catch (Exception expected) {
            return;
        }

        throw new AssertionError("Expected rejection");
    }

    private static void rejectsRuntime(CheckedAction action) throws Exception {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }

        throw new AssertionError("Expected runtime rejection");
    }

    private static void require(boolean result, String message) {
        if (!result) {
            throw new AssertionError(message);
        }
    }
}

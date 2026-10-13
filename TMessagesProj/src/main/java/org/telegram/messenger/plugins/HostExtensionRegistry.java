package org.telegram.messenger.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

final class HostExtensionRegistry {
    interface Operation {
        void run() throws Exception;
    }

    interface CallbackBoundary {
        void run(String pluginId, Operation operation) throws Exception;

        void handled(Throwable error);
    }

    private interface Factory<I, C> {
        Callback<I, C> create() throws Exception;
    }

    private interface Callback<I, C> extends AutoCloseable {
        void invoke(I input, HostExtensionContributionSink<C> sink) throws Exception;
    }

    private static final int MAX_REGISTRATIONS = 128;
    private static final int MAX_PLUGIN_REGISTRATIONS = 32;
    private static final int MAX_ENDPOINTS = 64;
    private static final int MAX_DESCRIPTORS = 128;

    private final LongSupplier clock;
    private final CallbackBoundary boundary;
    private final Consumer<Throwable> report;
    private final ArrayList<Registration<?, ?>> registrations = new ArrayList<>();
    private final ArrayList<Endpoint<?, ?>> endpoints = new ArrayList<>();
    private final Map<String, HostExtensionDescriptor<?, ?>> descriptors = new HashMap<>();
    private long nextSequence;

    HostExtensionRegistry(LongSupplier clock, CallbackBoundary boundary, Consumer<Throwable> report) {
        if (clock == null || boundary == null || report == null) {
            throw new IllegalArgumentException("Host extension registry dependencies are required");
        }

        this.clock = clock;
        this.boundary = boundary;
        this.report = report;
    }

    <I> AutoCloseable registerObserver(PluginScope owner,
            HostExtensionDescriptor<I, Void> descriptor,
            HostExtensionRegistrationSpec spec,
            HostExtensionObserverFactory<I> factory) {
        requireMode(descriptor, HostExtensionDescriptor.Mode.OBSERVER);

        return register(owner, descriptor, spec, () -> {
            HostExtensionObserver<I> observer = factory.create();

            if (observer == null) {
                throw new IllegalStateException("Observer factory returned null");
            }

            return new Callback<I, Void>() {
                @Override
                public void invoke(I input, HostExtensionContributionSink<Void> sink) throws Exception {
                    observer.onObserved(input);
                }

                @Override
                public void close() throws Exception {
                    observer.close();
                }
            };
        });
    }

    <I, C> AutoCloseable registerContributor(PluginScope owner,
            HostExtensionDescriptor<I, C> descriptor,
            HostExtensionRegistrationSpec spec,
            HostExtensionContributorFactory<I, C> factory) {
        requireMode(descriptor, HostExtensionDescriptor.Mode.CONTRIBUTOR);

        return register(owner, descriptor, spec, () -> {
            HostExtensionContributor<I, C> contributor = factory.create();

            if (contributor == null) {
                throw new IllegalStateException("Contributor factory returned null");
            }

            return new Callback<I, C>() {
                @Override
                public void invoke(I input, HostExtensionContributionSink<C> sink) throws Exception {
                    contributor.contribute(input, sink);
                }

                @Override
                public void close() throws Exception {
                    contributor.close();
                }
            };
        });
    }

    <I, C> Endpoint<I, C> openEndpoint(HostExtensionDescriptor<I, C> descriptor) {
        Endpoint<I, C> endpoint;
        ArrayList<Registration<?, ?>> snapshot;

        synchronized (this) {
            requireDescriptor(descriptor);

            if (endpoints.size() >= MAX_ENDPOINTS) {
                throw new IllegalStateException("Host extension endpoint limit reached");
            }

            endpoint = new Endpoint<>(descriptor);
            endpoints.add(endpoint);
            snapshot = new ArrayList<>(registrations);
        }

        for (Registration<?, ?> registration : snapshot) {
            if (registration.descriptor.id.equals(descriptor.id)) {
                attachUnchecked(registration, endpoint);
            }
        }

        return endpoint;
    }

    synchronized int registrationCount() {
        return registrations.size();
    }

    synchronized int endpointCount() {
        return endpoints.size();
    }

    synchronized int bindingCount() {
        int count = 0;

        for (Registration<?, ?> registration : registrations) {
            count += registration.bindings.size();
        }

        return count;
    }

    private <I, C> AutoCloseable register(PluginScope owner,
            HostExtensionDescriptor<I, C> descriptor,
            HostExtensionRegistrationSpec spec,
            Factory<I, C> factory) {
        if (owner == null || spec == null || factory == null) {
            throw new IllegalArgumentException("Extension registration is incomplete");
        }

        owner.requireActive();
        Registration<I, C> registration;

        synchronized (this) {
            requireDescriptor(descriptor);
            requireRegistration(owner, descriptor, spec);
            registration = new Registration<>(owner, descriptor, spec, factory, nextSequence++);
        }

        AutoCloseable resource = owner.registerResource(registration);
        ArrayList<Endpoint<?, ?>> snapshot;

        try {
            synchronized (this) {
                owner.requireActive();
                requireRegistration(owner, descriptor, spec);
                registrations.add(registration);
                snapshot = new ArrayList<>(endpoints);
            }

            for (Endpoint<?, ?> endpoint : snapshot) {
                if (endpoint.descriptor.id.equals(descriptor.id)) {
                    attachUnchecked(registration, endpoint);
                }
            }

            return resource;
        } catch (RuntimeException | Error error) {
            try {
                resource.close();
            } catch (Exception cleanupError) {
                error.addSuppressed(cleanupError);
            }

            throw error;
        }
    }

    private synchronized void requireDescriptor(HostExtensionDescriptor<?, ?> descriptor) {
        if (descriptor == null) {
            throw new IllegalArgumentException("Extension descriptor is required");
        }

        HostExtensionDescriptor<?, ?> existing = descriptors.get(descriptor.id);

        if (existing != null && existing != descriptor) {
            throw new IllegalStateException("Non-canonical extension descriptor: " + descriptor.id);
        }

        if (existing == null && descriptors.size() >= MAX_DESCRIPTORS) {
            throw new IllegalStateException("Host extension descriptor limit reached");
        }

        descriptors.put(descriptor.id, descriptor);
    }

    private synchronized void requireRegistration(PluginScope owner,
            HostExtensionDescriptor<?, ?> descriptor,
            HostExtensionRegistrationSpec spec) {
        if (registrations.size() >= MAX_REGISTRATIONS) {
            throw new IllegalStateException("Host extension registration limit reached");
        }

        int pluginCount = 0;

        for (Registration<?, ?> registration : registrations) {
            if (registration.pluginId.equals(owner.getPluginId())
                    && registration.generation == owner.getGeneration()) {
                pluginCount++;

                if (registration.descriptor.id.equals(descriptor.id)
                        && registration.spec.id.equals(spec.id)) {
                    throw new IllegalStateException("Duplicate extension registration");
                }
            }
        }

        if (pluginCount >= MAX_PLUGIN_REGISTRATIONS) {
            throw new IllegalStateException("Plugin extension registration limit reached");
        }
    }

    private static void requireMode(HostExtensionDescriptor<?, ?> descriptor,
            HostExtensionDescriptor.Mode mode) {
        if (descriptor == null || descriptor.mode != mode) {
            throw new IllegalArgumentException("Extension mode mismatch");
        }
    }

    @SuppressWarnings("unchecked")
    private <I, C> void attachUnchecked(Registration<?, ?> registration, Endpoint<?, ?> endpoint) {
        attach((Registration<I, C>) registration, (Endpoint<I, C>) endpoint);
    }

    private <I, C> void attach(Registration<I, C> registration, Endpoint<I, C> endpoint) {
        synchronized (this) {
            if (!registration.isActive() || !endpoint.isActive()) {
                return;
            }

            registration.attaching++;
            endpoint.attaching++;
        }

        Callback<I, C> callback;

        try {
            callback = callFactory(registration);
        } catch (Exception | LinkageError | AssertionError error) {
            finishFailedAttachment(registration, endpoint);
            report.accept(error);
            return;
        } catch (Error error) {
            finishFailedAttachment(registration, endpoint);
            throw error;
        }

        Binding<I, C> binding = new Binding<>(registration, endpoint, callback);
        boolean accepted;

        synchronized (this) {
            registration.attaching--;
            endpoint.attaching--;
            accepted = registration.isActive() && endpoint.isActive();
            binding.active = accepted;
            registration.bindings.add(binding);
            endpoint.bindings.add(binding);
            endpoint.rebuild();
        }

        if (!accepted) {
            try {
                binding.close();
            } catch (Exception | LinkageError | AssertionError error) {
                report.accept(error);
            }
        }
    }

    private synchronized void finishFailedAttachment(
            Registration<?, ?> registration, Endpoint<?, ?> endpoint) {
        registration.attaching--;
        endpoint.attaching--;
    }

    private <I, C> Callback<I, C> callFactory(Registration<I, C> registration) throws Exception {
        Object[] value = new Object[1];

        runPlugin(registration.pluginId, () -> value[0] = registration.factory.create());

        @SuppressWarnings("unchecked")
        Callback<I, C> callback = (Callback<I, C>) value[0];

        if (callback == null) {
            throw new IllegalStateException("Extension factory returned null");
        }

        return callback;
    }

    private void runPlugin(String pluginId, Operation operation) throws Exception {
        try {
            boundary.run(pluginId, operation);
        } catch (Exception | LinkageError | AssertionError error) {
            boundary.handled(error);
            throw error;
        }
    }

    private void closeCallback(String pluginId, AutoCloseable callback) throws Exception {
        runPlugin(pluginId, callback::close);
    }

    private synchronized void detach(Binding<?, ?> binding) {
        binding.active = false;
        binding.endpoint.rebuild();
    }

    private void quarantine(Binding<?, ?> binding, Throwable error) {
        synchronized (this) {
            detach(binding);
        }

        try {
            binding.close();
        } catch (Exception | LinkageError | AssertionError cleanupError) {
            error.addSuppressed(cleanupError);
        }

        report.accept(error);
    }

    private void quarantine(Registration<?, ?> registration, Throwable error) {
        registration.quarantined = true;

        try {
            registration.close();
        } catch (Exception | LinkageError | AssertionError cleanupError) {
            error.addSuppressed(cleanupError);
        }

        report.accept(error);
    }

    private static Exception collect(Exception failure, Throwable error) {
        if (failure == null) {
            return new Exception("Host extension cleanup failed", error);
        }

        failure.addSuppressed(error);
        return failure;
    }

    final class Endpoint<I, C> implements AutoCloseable {
        private final HostExtensionDescriptor<I, C> descriptor;
        private final ArrayList<Binding<I, C>> bindings = new ArrayList<>();
        private volatile Binding<I, C>[] snapshot = emptyBindings();
        private volatile boolean closed;
        private int attaching;

        Endpoint(HostExtensionDescriptor<I, C> descriptor) {
            this.descriptor = descriptor;
        }

        void dispatchObserved(I input) {
            requireMode(descriptor, HostExtensionDescriptor.Mode.OBSERVER);

            for (Binding<I, C> binding : snapshot) {
                binding.observe(input);
            }
        }

        List<C> collectContributions(I input) {
            requireMode(descriptor, HostExtensionDescriptor.Mode.CONTRIBUTOR);
            ArrayList<C> result = new ArrayList<>();

            for (Binding<I, C> binding : snapshot) {
                List<C> contributions = binding.contribute(input);

                if (contributions != null) {
                    result.addAll(contributions);
                }
            }

            return Collections.unmodifiableList(result);
        }

        boolean isActive() {
            return !closed;
        }

        @Override
        public void close() throws Exception {
            ArrayList<Binding<I, C>> closing;
            boolean attachmentPending;

            synchronized (HostExtensionRegistry.this) {
                closed = true;
                endpoints.remove(this);
                attachmentPending = attaching > 0;
                closing = new ArrayList<>(bindings);
                Collections.reverse(closing);

                for (Binding<I, C> binding : closing) {
                    detach(binding);
                }
            }

            Exception failure = attachmentPending
                    ? new Exception("Host extension attachment is still running")
                    : null;

            for (Binding<I, C> binding : closing) {
                try {
                    binding.close();
                } catch (Exception | LinkageError | AssertionError error) {
                    failure = collect(failure, error);
                }
            }

            if (failure != null) {
                throw failure;
            }
        }

        @SuppressWarnings("unchecked")
        private void rebuild() {
            bindings.sort(BINDING_ORDER);
            ArrayList<Binding<I, C>> activeBindings = new ArrayList<>(bindings.size());

            for (Binding<I, C> binding : bindings) {
                if (binding.active) {
                    activeBindings.add(binding);
                }
            }

            snapshot = activeBindings.toArray(new Binding[0]);
        }

        @SuppressWarnings("unchecked")
        private Binding<I, C>[] emptyBindings() {
            return new Binding[0];
        }
    }

    private final class Registration<I, C> implements AutoCloseable {
        final PluginScope owner;
        final String pluginId;
        final long generation;
        final HostExtensionDescriptor<I, C> descriptor;
        final HostExtensionRegistrationSpec spec;
        final Factory<I, C> factory;
        final long sequence;
        final ArrayList<Binding<I, C>> bindings = new ArrayList<>();
        volatile boolean closing;
        volatile boolean quarantined;
        int budgetOverruns;
        int attaching;

        Registration(PluginScope owner, HostExtensionDescriptor<I, C> descriptor,
                HostExtensionRegistrationSpec spec, Factory<I, C> factory, long sequence) {
            this.owner = owner;
            this.pluginId = owner.getPluginId();
            this.generation = owner.getGeneration();
            this.descriptor = descriptor;
            this.spec = spec;
            this.factory = factory;
            this.sequence = sequence;
        }

        boolean isActive() {
            return !closing && !quarantined && owner.isActive();
        }

        boolean recordBudget(long elapsed) {
            if (elapsed <= descriptor.callbackBudgetNanos) {
                return true;
            }

            synchronized (HostExtensionRegistry.this) {
                if (!isActive()) {
                    return false;
                }

                budgetOverruns++;

                if (budgetOverruns < descriptor.maxBudgetOverruns) {
                    return true;
                }

                quarantined = true;
            }

            quarantine(this, new IllegalStateException("Host extension callback budget exceeded"));
            return false;
        }

        @Override
        public void close() throws Exception {
            ArrayList<Binding<I, C>> closingBindings;
            boolean attachmentPending;

            synchronized (HostExtensionRegistry.this) {
                closing = true;
                registrations.remove(this);
                attachmentPending = attaching > 0;
                closingBindings = new ArrayList<>(bindings);
                Collections.reverse(closingBindings);

                for (Binding<I, C> binding : closingBindings) {
                    detach(binding);
                }
            }

            Exception failure = attachmentPending
                    ? new Exception("Host extension attachment is still running")
                    : null;

            for (Binding<I, C> binding : closingBindings) {
                try {
                    binding.close();
                } catch (Exception | LinkageError | AssertionError error) {
                    failure = collect(failure, error);
                }
            }

            if (failure != null) {
                throw failure;
            }
        }
    }

    private final class Binding<I, C> implements AutoCloseable {
        final Registration<I, C> registration;
        final Endpoint<I, C> endpoint;
        Callback<I, C> callback;
        volatile boolean active = true;
        boolean closing;
        boolean invoking;

        Binding(Registration<I, C> registration, Endpoint<I, C> endpoint,
                Callback<I, C> callback) {
            this.registration = registration;
            this.endpoint = endpoint;
            this.callback = callback;
        }

        void observe(I input) {
            Callback<I, C> invocation = beginInvocation();

            if (invocation == null) {
                return;
            }

            long start = clock.getAsLong();
            Throwable failure = null;

            try {
                runPlugin(registration.pluginId, () -> invocation.invoke(input, null));
            } catch (Exception | LinkageError | AssertionError error) {
                failure = error;
            } finally {
                finishInvocation();
            }

            if (failure != null) {
                quarantine(this, failure);
                return;
            }

            if (!isActive()) {
                return;
            }

            registration.recordBudget(clock.getAsLong() - start);
        }

        List<C> contribute(I input) {
            Callback<I, C> invocation = beginInvocation();

            if (invocation == null) {
                return null;
            }

            ContributionSink sink = new ContributionSink(
                    registration.descriptor.maxContributionsPerCallback);
            long start = clock.getAsLong();
            Throwable failure = null;

            try {
                runPlugin(registration.pluginId, () -> invocation.invoke(input, sink));
            } catch (Exception | LinkageError | AssertionError error) {
                failure = error;
            } finally {
                sink.close();
                finishInvocation();
            }

            if (failure != null) {
                quarantine(this, failure);
                return null;
            }

            if (!isActive()
                    || !registration.recordBudget(clock.getAsLong() - start)
                    || !isActive()) {
                return null;
            }

            return sink.values();
        }

        boolean isActive() {
            return active && registration.isActive() && endpoint.isActive();
        }

        private Callback<I, C> beginInvocation() {
            synchronized (HostExtensionRegistry.this) {
                if (!isActive() || invoking || callback == null) {
                    return null;
                }

                invoking = true;
                return callback;
            }
        }

        private void finishInvocation() {
            synchronized (HostExtensionRegistry.this) {
                invoking = false;
            }
        }

        @Override
        public void close() throws Exception {
            synchronized (HostExtensionRegistry.this) {
                active = false;

                if (callback == null) {
                    return;
                }

                if (closing) {
                    throw new IllegalStateException("Host extension cleanup is already running");
                }

                if (invoking) {
                    throw new IllegalStateException("Host extension callback is still running");
                }

                closing = true;
            }

            try {
                closeCallback(registration.pluginId, callback);

                synchronized (HostExtensionRegistry.this) {
                    callback = null;
                    registration.bindings.remove(this);
                    endpoint.bindings.remove(this);
                    endpoint.rebuild();
                }
            } finally {
                synchronized (HostExtensionRegistry.this) {
                    closing = false;
                }
            }
        }

        private final class ContributionSink implements HostExtensionContributionSink<C> {
            private final int limit;
            private final ArrayList<C> values = new ArrayList<>();
            private boolean open = true;

            ContributionSink(int limit) {
                this.limit = limit;
            }

            @Override
            public synchronized boolean add(C contribution) {
                if (!open || contribution == null || !Binding.this.isActive()
                        || values.size() >= limit) {
                    return false;
                }

                values.add(contribution);
                return true;
            }

            synchronized void close() {
                open = false;
            }

            synchronized List<C> values() {
                return Collections.unmodifiableList(new ArrayList<>(values));
            }
        }
    }

    private static final Comparator<Binding<?, ?>> BINDING_ORDER = (left, right) -> {
        int priority = Integer.compare(right.registration.spec.priority, left.registration.spec.priority);

        if (priority != 0) {
            return priority;
        }

        int plugin = left.registration.pluginId.compareTo(right.registration.pluginId);

        if (plugin != 0) {
            return plugin;
        }

        return Long.compare(left.registration.sequence, right.registration.sequence);
    };
}

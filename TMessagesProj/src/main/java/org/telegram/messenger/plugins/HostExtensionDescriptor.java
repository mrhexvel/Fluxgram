package org.telegram.messenger.plugins;

final class HostExtensionDescriptor<I, C> {
    enum Mode {
        OBSERVER,
        CONTRIBUTOR,
        REPLACEMENT
    }

    final String id;
    final int contractVersion;
    final Mode mode;
    final long callbackBudgetNanos;
    final int maxBudgetOverruns;
    final int maxContributionsPerCallback;

    HostExtensionDescriptor(String id, int contractVersion, Mode mode,
            long callbackBudgetNanos, int maxBudgetOverruns,
            int maxContributionsPerCallback) {
        if (id == null || id.length() > 128 || !id.matches("[a-z][a-z0-9_.-]*")) {
            throw new IllegalArgumentException("Invalid extension ID");
        }

        if (mode == null || contractVersion <= 0 || callbackBudgetNanos <= 0
                || maxBudgetOverruns <= 0) {
            throw new IllegalArgumentException("Invalid extension contract limits");
        }

        if ((mode == Mode.OBSERVER || mode == Mode.REPLACEMENT)
                && maxContributionsPerCallback != 0
                || mode == Mode.CONTRIBUTOR && maxContributionsPerCallback <= 0) {
            throw new IllegalArgumentException("Invalid contribution limit");
        }

        this.id = id;
        this.contractVersion = contractVersion;
        this.mode = mode;
        this.callbackBudgetNanos = callbackBudgetNanos;
        this.maxBudgetOverruns = maxBudgetOverruns;
        this.maxContributionsPerCallback = maxContributionsPerCallback;
    }

}

final class HostExtensionRegistrationSpec {
    final String id;
    final int priority;

    HostExtensionRegistrationSpec(String id, int priority) {
        if (id == null || id.length() > 128 || !id.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid extension registration ID");
        }

        this.id = id;
        this.priority = priority;
    }
}

interface HostExtensionObserver<I> extends AutoCloseable {
    void onObserved(I input) throws Exception;

    @Override
    default void close() throws Exception {
    }
}

interface HostExtensionObserverFactory<I> {
    HostExtensionObserver<I> create() throws Exception;
}

interface HostExtensionContributor<I, C> extends AutoCloseable {
    void contribute(I input, HostExtensionContributionSink<C> sink) throws Exception;

    @Override
    default void close() throws Exception {
    }
}

interface HostExtensionContributorFactory<I, C> {
    HostExtensionContributor<I, C> create() throws Exception;
}

interface HostExtensionContributionSink<C> {
    boolean add(C contribution);
}

interface HostExtensionReplacement<I> extends AutoCloseable {
    boolean replace(I input) throws Exception;

    @Override
    default void close() throws Exception {
    }
}

interface HostExtensionReplacementFactory<I> {
    HostExtensionReplacement<I> create() throws Exception;
}

interface HostExtensionBudgetInput {
    long excludedCallbackNanos();
}

interface HostExtensionFailureInput {
    boolean isHostFailure(Throwable error);
}

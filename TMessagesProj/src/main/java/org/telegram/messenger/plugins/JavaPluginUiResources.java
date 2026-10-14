package org.telegram.messenger.plugins;

import org.telegram.messenger.plugins.api.JavaPluginUiResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;

final class JavaPluginUiResources {
    private static final int RESOURCES_PER_DISPATCH = 8;
    interface Dispatcher {
        void post(Runnable action);

        boolean isUiThread();
    }

    private final Dispatcher dispatcher;
    private final PluginScope scope;
    private final Consumer<Throwable> report;
    private final ArrayList<Resource> resources = new ArrayList<>();
    private boolean drainPosted;
    private boolean closing;
    private boolean published;
    private Consumer<Exception> completion;

    JavaPluginUiResources(Dispatcher dispatcher, PluginScope scope, Consumer<Throwable> report) {
        this.dispatcher = dispatcher;
        this.scope = scope;
        this.report = report;
    }

    synchronized JavaPluginUiResource register(JavaPluginUiResource.Factory factory) {
        if (closing || !scope.isActive()) {
            throw new IllegalStateException("Java UI scope is inactive");
        }

        if (factory == null) {
            throw new IllegalArgumentException("UI resource factory is required");
        }

        if (resources.size() >= 128) {
            throw new IllegalStateException("Java UI resource limit reached");
        }

        Resource resource = new Resource(factory);
        resources.add(resource);
        if (published) {
            schedule();
        }
        return resource;
    }

    synchronized void activate() {
        if (closing || !scope.isActive()) {
            return;
        }

        published = true;

        if (!resources.isEmpty()) {
            schedule();
        }
    }

    synchronized void closeAsync(Consumer<Exception> onComplete) {
        if (completion != null) {
            throw new IllegalStateException("UI cleanup already pending");
        }

        closing = true;
        scope.beginClose();
        completion = onComplete;

        for (Resource resource : resources) {
            resource.closeRequested = true;

            if (!resource.closingNow) {
                resource.closeAttempted = false;
            }
        }

        schedule();
    }

    synchronized int activeCount() {
        return resources.size();
    }

    private void schedule() {
        if (!drainPosted) {
            drainPosted = true;
            dispatcher.post(this::drain);
        }
    }

    private void drain() {
        if (!dispatcher.isUiThread()) {
            throw new IllegalStateException("UI resource dispatch requires main thread");
        }

        ArrayList<Resource> snapshot;

        synchronized (this) {
            drainPosted = false;
            snapshot = new ArrayList<>();

            for (Resource resource : resources) {
                boolean cancel = closing || resource.closeRequested || !scope.isActive();

                if (!resource.closed && (cancel ? !resource.closeAttempted : published && resource.factory != null)) {
                    snapshot.add(resource);
                }
            }

            if (closing) {
                Collections.reverse(snapshot);
            }
        }

        for (int i = 0; i < Math.min(snapshot.size(), RESOURCES_PER_DISPATCH); i++) {
            process(snapshot.get(i));
        }

        Consumer<Exception> callback = null;
        Exception failure = null;

        synchronized (this) {
            if (snapshot.size() > RESOURCES_PER_DISPATCH) {
                schedule();
            }

            if (completion != null) {
                boolean pending = false;

                for (Resource resource : resources) {
                    pending |= !resource.closeAttempted;

                    if (resource.failure != null) {
                        if (failure == null) {
                            failure = new Exception("Java UI cleanup failed", resource.failure);
                        } else {
                            failure.addSuppressed(resource.failure);
                        }
                    }
                }

                if (pending) {
                    schedule();
                } else {
                    callback = completion;
                    completion = null;

                    if (failure == null) {
                        try {
                            scope.finishExternalClose();
                        } catch (Exception | LinkageError | AssertionError error) {
                            failure = new Exception("Java UI scope cleanup failed", error);
                        }
                    }
                }
            }
        }

        if (callback != null) {
            callback.accept(failure);
        }
    }

    private void process(Resource resource) {
        JavaPluginUiResource.Factory factory;

        synchronized (this) {
            if (resource.closed) {
                return;
            }

            boolean cancel = closing || resource.closeRequested || !scope.isActive();

            if (!published && !cancel) {
                return;
            }

            factory = resource.factory;
            resource.factory = null;

            if (cancel) {
                factory = null;
                resource.closeRequested = true;
            }
        }

        if (factory != null) {
            try {
                AutoCloseable created = factory.create();

                if (created == null) {
                    throw new IllegalStateException("UI factory must return a cleanup handle");
                }

                synchronized (this) {
                    resource.delegate = created;
                }
            } catch (Exception | LinkageError | AssertionError error) {
                synchronized (this) {
                    resource.closed = true;
                    resources.remove(resource);
                }

                report.accept(error);
                return;
            }
        }

        AutoCloseable delegate;

        synchronized (this) {
            if (!(closing || resource.closeRequested || !scope.isActive()) || resource.closeAttempted) {
                return;
            }

            resource.closeRequested = true;
            resource.closeAttempted = true;
            resource.closingNow = true;
            delegate = resource.delegate;
        }

        try {
            if (delegate != null) {
                delegate.close();
            }

            synchronized (this) {
                resource.closed = true;
                resource.delegate = null;
                resource.failure = null;
                resources.remove(resource);
            }
        } catch (Exception | LinkageError | AssertionError error) {
            synchronized (this) {
                resource.failure = error;
            }

            report.accept(error);
        } finally {
            synchronized (this) {
                resource.closingNow = false;
            }
        }
    }

    private final class Resource implements JavaPluginUiResource {
        private Factory factory;
        private AutoCloseable delegate;
        private Throwable failure;
        private boolean closeRequested;
        private boolean closeAttempted;
        private boolean closingNow;
        private boolean closed;

        Resource(Factory factory) {
            this.factory = factory;
        }

        @Override
        public boolean isActive() {
            synchronized (JavaPluginUiResources.this) {
                return delegate != null && !closing && !closeRequested && !closed && scope.isActive();
            }
        }

        @Override
        public boolean isClosed() {
            synchronized (JavaPluginUiResources.this) {
                return closed;
            }
        }

        @Override
        public void close() {
            synchronized (JavaPluginUiResources.this) {
                if (closed || closingNow) {
                    return;
                }

                closeRequested = true;
                closeAttempted = false;
                schedule();
            }
        }
    }
}

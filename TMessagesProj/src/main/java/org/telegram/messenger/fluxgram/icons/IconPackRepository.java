package org.telegram.messenger.fluxgram.icons;

import android.content.Context;
import android.net.Uri;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class IconPackRepository {
    public enum Failure {
        READ,
        INVALID,
        STORAGE
    }

    public static final class ImportException extends Exception {
        public final Failure failure;

        private ImportException(Failure failure, Exception cause) {
            super(cause);
            this.failure = failure;
        }
    }

    public interface Callback<T> {
        void complete(T value, Exception error);
    }

    public static final class Pack {
        public final String id;
        public final String name;
        public final String author;
        public final String version;
        public final int iconCount;

        private Pack(IconPackStore.Installed installed) {
            IconPackManifest manifest = installed.manifest;
            id = manifest.id;
            name = manifest.name;
            author = manifest.author;
            version = manifest.version;
            iconCount = manifest.icons.size();
        }
    }

    public static final class State {
        public final List<Pack> packs;
        public final List<String> activeIds;

        private State(List<Pack> packs, List<String> activeIds) {
            this.packs = Collections.unmodifiableList(new ArrayList<>(packs));
            this.activeIds = Collections.unmodifiableList(new ArrayList<>(activeIds));
        }

        public boolean isActive(String id) {
            return activeIds.contains(id);
        }

        public State withActive(String id, boolean active) {
            boolean installed = false;

            for (Pack pack : packs) {
                if (pack.id.equals(id)) {
                    installed = true;
                    break;
                }
            }

            if (!installed) {
                throw new IllegalArgumentException("Installed icon pack is required");
            }

            ArrayList<String> updated = new ArrayList<>(activeIds);

            if (active) {
                if (!updated.contains(id)) {
                    updated.add(id);
                }
            } else {
                updated.remove(id);
            }

            return new State(packs, updated);
        }
    }

    public static final class Review {
        public final IconPackArchiveValidator.Result result;
        public final String previousVersion;

        private IconPackArchiveValidator.Snapshot snapshot;
        private boolean installing;
        private int cleanupAttempts;

        private Review(
                IconPackArchiveValidator.Snapshot snapshot,
                String previousVersion) {
            this.snapshot = snapshot;
            this.result = snapshot.result;
            this.previousVersion = previousVersion;
        }

        public boolean isUpdate() {
            return previousVersion != null;
        }

        private synchronized File claim() throws IOException {
            if (snapshot == null || installing) {
                throw new IOException("Icon pack review is unavailable");
            }

            installing = true;

            return snapshot.archive();
        }

        private synchronized void close() throws IOException {
            IconPackArchiveValidator.Snapshot current = snapshot;

            if (current != null) {
                try {
                    current.close();
                    snapshot = null;
                } catch (IOException error) {
                    cleanupAttempts++;
                    throw error;
                } finally {
                    installing = false;
                }
            }
        }

        private synchronized boolean canDiscard() {
            return snapshot != null && !installing;
        }

        private synchronized boolean shouldRetryCleanup() {
            return snapshot != null && !installing && cleanupAttempts < 3;
        }
    }

    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(
            runnable -> new Thread(runnable, "FluxGramIconPacks"));
    private static final ScheduledExecutorService RUNTIME_WORKER =
            Executors.newSingleThreadScheduledExecutor(
                    runnable -> new Thread(runnable, "FluxGramIconPackRuntime"));
    private static final AtomicLong RUNTIME_REQUEST = new AtomicLong();
    private static final AtomicBoolean RUNTIME_DRAIN_SCHEDULED = new AtomicBoolean();
    private static final AtomicBoolean RUNTIME_UI_REFRESH_SCHEDULED = new AtomicBoolean();
    private static final Object RUNTIME_REQUEST_LOCK = new Object();
    private static final long RUNTIME_DEMAND_DELAY_MS = 16;
    private static volatile long appliedRuntimeRevision = -1;

    private static IconPackStore store;
    private static List<IconPackStore.Installed> installedSnapshot;
    private static List<String> activeSnapshot = Collections.emptyList();
    private static volatile State stateSnapshot;

    static {
        IconPackRuntime.setDemandListener(IconPackRepository::requestRuntimeDemandDrain);
    }

    private IconPackRepository() {
    }

    public static void review(Uri uri, Callback<Review> callback) {
        if (uri == null || callback == null) {
            throw new IllegalArgumentException("Icon pack review arguments are required");
        }

        WORKER.execute(() -> {
            File selected = null;
            IconPackArchiveValidator.Snapshot snapshot = null;
            Review review = null;
            Exception failure = null;

            try {
                Context context = ApplicationLoader.applicationContext;
                IconPackStore currentStore;
                File cache;

                try {
                    currentStore = store(context);
                    cache = cacheDirectory(context);
                    selected = File.createTempFile("icon-pack-selected-", ".icons", cache);
                } catch (Exception error) {
                    throw new ImportException(Failure.STORAGE, error);
                }

                copySelected(context, uri, selected);

                try {
                    snapshot = IconPackArchiveValidator.snapshot(
                            selected,
                            cache,
                            new IconPackAndroidCatalog(context.getResources()));
                } catch (IconPackArchiveValidator.InvalidArchiveException error) {
                    throw new ImportException(Failure.INVALID, error);
                } catch (Exception error) {
                    throw new ImportException(Failure.STORAGE, error);
                }

                String previousVersion;

                try {
                    ensureInventory(currentStore);
                    previousVersion = installedVersion(
                            installedSnapshot,
                            snapshot.result.manifest.id);
                } catch (Exception error) {
                    throw new ImportException(Failure.STORAGE, error);
                }

                review = new Review(snapshot, previousVersion);
                snapshot = null;
            } catch (Exception error) {
                failure = error;
            } finally {
                if (snapshot != null) {
                    Review cleanup = new Review(snapshot, null);
                    snapshot = null;
                    discardNow(cleanup);
                }
                if (selected != null && !selected.delete() && selected.exists()) {
                    ImportException cleanupError = new ImportException(
                            Failure.STORAGE,
                            new IOException("Cannot remove selected icon pack copy"));

                    if (failure == null) {
                        failure = cleanupError;
                    } else {
                        failure.addSuppressed(cleanupError);
                    }

                    retrySelectedCleanup(selected, 1);
                }
            }

            Review value = review;
            Exception error = failure;

            if (error != null && value != null) {
                discardNow(value);
                value = null;
            }

            Review result = value;
            AndroidUtilities.runOnUIThread(() -> callback.complete(result, error));
        });
    }

    public static void install(Review review, Callback<IconPackStore.Installed> callback) {
        if (review == null || callback == null) {
            throw new IllegalArgumentException("Icon pack install arguments are required");
        }

        WORKER.execute(() -> {
            IconPackStore.Installed installed = null;
            Exception failure = null;

            try {
                File archive = review.claim();
                IconPackStore currentStore = store(ApplicationLoader.applicationContext);
                installed = currentStore.install(
                        archive,
                        review.result.sha256);
                ensureInventory(currentStore);
                replaceInstalled(installed);
                publishInventory(
                        installedSnapshot,
                        IconPackSettings.getActiveIdsForInstalled(installedSnapshot));
                requestRuntime(currentStore, installedSnapshot, activeSnapshot);
            } catch (Exception error) {
                failure = error;
            } finally {
                try {
                    review.close();
                } catch (IOException error) {
                    if (failure != null) {
                        failure.addSuppressed(error);
                    }

                    retryCleanup(review);
                }
            }

            IconPackStore.Installed value = installed;
            Exception error = failure;
            AndroidUtilities.runOnUIThread(() -> callback.complete(value, error));
        });
    }

    public static void initializeRuntime() {
        WORKER.execute(() -> {
            IconPackStore currentStore;

            try {
                currentStore = store(ApplicationLoader.applicationContext);
                ensureInventory(currentStore);
            } catch (IOException error) {
                long request;

                synchronized (RUNTIME_REQUEST_LOCK) {
                    request = RUNTIME_REQUEST.incrementAndGet();
                }

                boolean changed = IconPackRuntime.clearIfCurrent(
                        () -> RUNTIME_REQUEST.get() == request,
                        RUNTIME_REQUEST_LOCK);

                if (changed) {
                    publishRuntimeChange();
                }

                return;
            }

            requestRuntime(currentStore, installedSnapshot, activeSnapshot);
        });
    }

    public static void load(Callback<State> callback) {
        requireCallback(callback);
        State current = stateSnapshot;

        if (current != null) {
            AndroidUtilities.runOnUIThread(() -> callback.complete(current, null));

            return;
        }

        WORKER.execute(() -> {
            State value = null;
            Exception failure = null;

            try {
                IconPackStore currentStore = store(ApplicationLoader.applicationContext);
                ensureInventory(currentStore);
                value = stateSnapshot;
            } catch (Exception error) {
                failure = error;
            }

            State result = value;
            Exception error = failure;
            AndroidUtilities.runOnUIThread(() -> callback.complete(result, error));
        });
    }

    public static void setActive(String id, boolean active, Callback<State> callback) {
        if (!IconPackManifest.isValidId(id)) {
            throw new IllegalArgumentException("Valid icon pack ID is required");
        }

        requireCallback(callback);
        WORKER.execute(() -> {
            State value = null;
            Exception failure = null;

            try {
                IconPackStore currentStore = store(ApplicationLoader.applicationContext);
                ensureInventory(currentStore);
                ArrayList<String> updated = new ArrayList<>(activeSnapshot);

                if (active) {
                    if (!contains(installedSnapshot, id)) {
                        throw new IOException("Icon pack is not installed");
                    }
                    if (!updated.contains(id)) {
                        updated.add(id);
                    }
                } else {
                    updated.remove(id);
                }

                IconPackSettings.setActiveIdsForInstalled(updated, installedSnapshot);
                publishInventory(installedSnapshot, updated);
                requestRuntime(currentStore, installedSnapshot, activeSnapshot);
                value = stateSnapshot;
            } catch (Exception error) {
                failure = error;
            }

            complete(callback, value, failure);
        });
    }

    public static void move(String id, int direction, Callback<State> callback) {
        if (!IconPackManifest.isValidId(id) || (direction != -1 && direction != 1)) {
            throw new IllegalArgumentException("Valid icon pack move is required");
        }

        requireCallback(callback);
        WORKER.execute(() -> {
            State value = null;
            Exception failure = null;

            try {
                IconPackStore currentStore = store(ApplicationLoader.applicationContext);
                ensureInventory(currentStore);
                ArrayList<String> updated = new ArrayList<>(activeSnapshot);
                int index = updated.indexOf(id);
                int target = index + direction;

                if (index < 0 || target < 0 || target >= updated.size()) {
                    throw new IOException("Icon pack priority cannot be changed");
                }

                updated.remove(index);
                updated.add(target, id);
                IconPackSettings.setActiveIdsForInstalled(updated, installedSnapshot);
                publishInventory(installedSnapshot, updated);
                requestRuntime(currentStore, installedSnapshot, activeSnapshot);
                value = stateSnapshot;
            } catch (Exception error) {
                failure = error;
            }

            complete(callback, value, failure);
        });
    }

    public static void delete(String id, Callback<State> callback) {
        if (!IconPackManifest.isValidId(id)) {
            throw new IllegalArgumentException("Valid icon pack ID is required");
        }

        requireCallback(callback);
        WORKER.execute(() -> {
            State value = null;
            Exception failure = null;
            IconPackStore currentStore = null;

            try {
                currentStore = store(ApplicationLoader.applicationContext);
                ensureInventory(currentStore);
                IconPackSettings.deleteWithInstalled(currentStore, id, installedSnapshot);
                ArrayList<IconPackStore.Installed> remaining =
                        new ArrayList<>(installedSnapshot);
                remaining.removeIf(pack -> pack.manifest.id.equals(id));
                ArrayList<String> active = new ArrayList<>(activeSnapshot);
                active.remove(id);
                publishInventory(remaining, active);
                requestRuntime(currentStore, installedSnapshot, activeSnapshot);
                value = stateSnapshot;
            } catch (Exception error) {
                failure = error;

                if (currentStore != null) {
                    reconcile(currentStore);
                }
            }

            complete(callback, value, failure);
        });
    }

    public static void discard(Review review) {
        if (review == null || !review.canDiscard()) {
            return;
        }

        WORKER.execute(() -> discardNow(review));
    }

    private static void discardNow(Review review) {
        try {
            review.close();
        } catch (IOException error) {
            retryCleanup(review);
        }
    }

    private static void retryCleanup(Review review) {
        if (review.shouldRetryCleanup()) {
            WORKER.schedule(() -> discardNow(review), 1, TimeUnit.SECONDS);
        }
    }

    private static void retrySelectedCleanup(File selected, int attempt) {
        if (attempt > 3) {
            return;
        }

        WORKER.schedule(() -> {
            if (!selected.delete() && selected.exists()) {
                retrySelectedCleanup(selected, attempt + 1);
            }
        }, 1, TimeUnit.SECONDS);
    }

    private static void ensureInventory(IconPackStore currentStore) throws IOException {
        if (installedSnapshot != null) {
            return;
        }

        List<IconPackStore.Installed> installed = currentStore.list();
        publishInventory(installed, IconPackSettings.getActiveIdsForInstalled(installed));
    }

    private static void publishInventory(
            List<IconPackStore.Installed> installed,
            List<String> activeIds) {
        installedSnapshot = Collections.unmodifiableList(new ArrayList<>(installed));
        activeSnapshot = Collections.unmodifiableList(new ArrayList<>(activeIds));
        stateSnapshot = state(installedSnapshot, activeSnapshot);
    }

    private static void replaceInstalled(IconPackStore.Installed installed) {
        ArrayList<IconPackStore.Installed> updated = new ArrayList<>(installedSnapshot);

        for (int index = 0; index < updated.size(); index++) {
            if (updated.get(index).manifest.id.equals(installed.manifest.id)) {
                updated.set(index, installed);
                installedSnapshot = Collections.unmodifiableList(updated);

                return;
            }
        }

        updated.add(installed);
        installedSnapshot = Collections.unmodifiableList(updated);
    }

    private static void reconcile(IconPackStore currentStore) {
        try {
            List<IconPackStore.Installed> installed = currentStore.list();
            publishInventory(installed, IconPackSettings.getActiveIdsForInstalled(installed));
            requestRuntime(currentStore, installedSnapshot, activeSnapshot);
        } catch (Exception ignored) {
        }
    }

    private static void requestRuntime(
            IconPackStore currentStore,
            List<IconPackStore.Installed> installed,
            List<String> activeIds) {
        long request;

        synchronized (RUNTIME_REQUEST_LOCK) {
            request = RUNTIME_REQUEST.incrementAndGet();
        }
        List<IconPackStore.Installed> installedCopy = new ArrayList<>(installed);
        List<String> activeCopy = new ArrayList<>(activeIds);

        RUNTIME_WORKER.execute(() -> {
            boolean changed;

            try {
                changed = IconPackRuntime.rebuild(
                        ApplicationLoader.applicationContext.getResources(),
                        currentStore,
                        installedCopy,
                        activeCopy,
                        request,
                        () -> RUNTIME_REQUEST.get() == request,
                        RUNTIME_REQUEST_LOCK);
            } catch (Exception error) {
                changed = IconPackRuntime.clearIfCurrent(
                        () -> RUNTIME_REQUEST.get() == request,
                        RUNTIME_REQUEST_LOCK);
            }

            if (changed) {
                publishRuntimeChange();
            }
            if (IconPackRuntime.hasPendingDemands()) {
                requestRuntimeDemandDrain();
            }
        });
    }

    private static void requestRuntimeDemandDrain() {
        if (!RUNTIME_DRAIN_SCHEDULED.compareAndSet(false, true)) {
            return;
        }

        RUNTIME_WORKER.schedule(() -> {
            try {
                long request = RUNTIME_REQUEST.get();
                boolean changed = IconPackRuntime.decodePending(
                        request,
                        () -> RUNTIME_REQUEST.get() == request,
                        RUNTIME_REQUEST_LOCK);

                if (changed) {
                    publishRuntimeChange();
                }
            } finally {
                RUNTIME_DRAIN_SCHEDULED.set(false);

                if (IconPackRuntime.hasPendingDemands()) {
                    requestRuntimeDemandDrain();
                }
            }
        }, RUNTIME_DEMAND_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private static void publishRuntimeChange() {
        if (!RUNTIME_UI_REFRESH_SCHEDULED.compareAndSet(false, true)) {
            return;
        }

        AndroidUtilities.runOnUIThread(() -> {
            long revision = IconPackRuntime.revision();

            try {
                if (appliedRuntimeRevision != revision) {
                    Theme.reloadAllResources(ApplicationLoader.applicationContext);
                    IconPackRuntime.refreshUi();
                    NotificationCenter.getGlobalInstance().postNotificationName(
                            NotificationCenter.fluxgramIconPackChanged);
                    appliedRuntimeRevision = revision;
                }
            } finally {
                RUNTIME_UI_REFRESH_SCHEDULED.set(false);

                if (IconPackRuntime.revision() != revision) {
                    publishRuntimeChange();
                }
            }
        });
    }

    private static <T> void complete(Callback<T> callback, T value, Exception error) {
        AndroidUtilities.runOnUIThread(() -> callback.complete(value, error));
    }

    private static State state(
            List<IconPackStore.Installed> installed,
            List<String> activeIds) {
        ArrayList<Pack> packs = new ArrayList<>();
        HashSet<String> added = new HashSet<>();

        for (String id : activeIds) {
            for (IconPackStore.Installed pack : installed) {
                if (pack.manifest.id.equals(id)) {
                    packs.add(new Pack(pack));
                    added.add(id);
                    break;
                }
            }
        }

        for (IconPackStore.Installed pack : installed) {
            if (added.add(pack.manifest.id)) {
                packs.add(new Pack(pack));
            }
        }

        return new State(packs, activeIds);
    }

    private static boolean contains(List<IconPackStore.Installed> installed, String id) {
        for (IconPackStore.Installed pack : installed) {
            if (pack.manifest.id.equals(id)) {
                return true;
            }
        }

        return false;
    }

    private static void requireCallback(Callback<?> callback) {
        if (callback == null) {
            throw new IllegalArgumentException("Icon pack callback is required");
        }
    }

    private static IconPackStore store(Context context) throws IOException {
        if (store == null) {
            store = new IconPackStore(
                    new File(context.getNoBackupFilesDir(), "fluxgram/icon_packs"),
                    cacheDirectory(context),
                    new IconPackAndroidCatalog(context.getResources()));
        }

        return store;
    }

    private static File cacheDirectory(Context context) throws IOException {
        File directory = new File(context.getCacheDir(), "fluxgram/icon_packs");

        if ((!directory.isDirectory() && !directory.mkdirs()) || !directory.isDirectory()) {
            throw new IOException("Cannot create icon pack cache");
        }

        return directory;
    }

    private static String installedVersion(
            List<IconPackStore.Installed> installed,
            String id) {
        for (IconPackStore.Installed pack : installed) {
            if (pack.manifest.id.equals(id)) {
                return pack.manifest.version;
            }
        }

        return null;
    }

    private static void copySelected(Context context, Uri uri, File selected)
            throws ImportException {
        InputStream input;

        try {
            input = context.getContentResolver().openInputStream(uri);

            if (input == null) {
                throw new IOException("Cannot open selected icon pack");
            }
        } catch (Exception error) {
            throw new ImportException(Failure.READ, error);
        }

        FileOutputStream output;

        try {
            output = new FileOutputStream(selected);
        } catch (Exception error) {
            closeInput(input, null);
            throw new ImportException(Failure.STORAGE, error);
        }

        ImportException failure = null;
        byte[] buffer = new byte[8192];
        long total = 0;

        try {
            while (true) {
                int read;

                try {
                    read = input.read(buffer);
                } catch (Exception error) {
                    throw new ImportException(Failure.READ, error);
                }

                if (read == -1) {
                    break;
                }

                total += read;

                if (total > IconPackArchiveValidator.MAX_ARCHIVE_BYTES) {
                    throw new ImportException(
                            Failure.INVALID,
                            new IOException("Icon pack archive is too large"));
                }

                try {
                    output.write(buffer, 0, read);
                } catch (Exception error) {
                    throw new ImportException(Failure.STORAGE, error);
                }
            }

            try {
                output.getFD().sync();
            } catch (Exception error) {
                throw new ImportException(Failure.STORAGE, error);
            }
        } catch (ImportException error) {
            failure = error;
        } finally {
            failure = closeInput(input, failure);
            failure = closeOutput(output, failure);
        }

        if (failure != null) {
            throw failure;
        }
    }

    private static ImportException closeInput(
            InputStream input,
            ImportException failure) {
        try {
            input.close();
        } catch (Exception error) {
            if (failure == null) {
                return new ImportException(Failure.READ, error);
            }

            failure.addSuppressed(error);
        }

        return failure;
    }

    private static ImportException closeOutput(
            FileOutputStream output,
            ImportException failure) {
        try {
            output.close();
        } catch (Exception error) {
            if (failure == null) {
                return new ImportException(Failure.STORAGE, error);
            }

            failure.addSuppressed(error);
        }

        return failure;
    }
}

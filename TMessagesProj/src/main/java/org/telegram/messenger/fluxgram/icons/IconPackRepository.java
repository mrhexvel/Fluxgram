package org.telegram.messenger.fluxgram.icons;

import android.content.Context;
import android.net.Uri;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    private static IconPackStore store;

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
                            new IconPackAndroidCatalog());
                } catch (IconPackArchiveValidator.InvalidArchiveException error) {
                    throw new ImportException(Failure.INVALID, error);
                } catch (Exception error) {
                    throw new ImportException(Failure.STORAGE, error);
                }

                String previousVersion;

                try {
                    previousVersion = installedVersion(
                            currentStore.list(),
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
                installed = store(ApplicationLoader.applicationContext).install(
                        archive,
                        review.result.sha256);
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

    private static IconPackStore store(Context context) throws IOException {
        if (store == null) {
            store = new IconPackStore(
                    new File(context.getNoBackupFilesDir(), "fluxgram/icon_packs"),
                    cacheDirectory(context),
                    new IconPackAndroidCatalog());
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

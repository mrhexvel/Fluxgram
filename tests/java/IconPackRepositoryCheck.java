package org.telegram.messenger.fluxgram.icons;

import android.content.Context;
import android.content.MemorySharedPreferences;
import android.net.Uri;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class IconPackRepositoryCheck {
    public static void main(String[] args) throws Exception {
        try {
            run(args);
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        File root = new File(args[0]);
        File noBackup = new File(root, "no_backup");
        File cache = new File(root, "cache");
        require(noBackup.mkdirs());
        require(cache.mkdirs());
        ApplicationLoader.applicationContext = new Context(noBackup, cache);
        MessagesController.preferences = new MemorySharedPreferences();

        Outcome<IconPackRepository.Review> first = review(new File(args[1]));
        require(first.error == null);
        require(first.value != null);
        require(!first.value.isUpdate());
        require(first.value.result.manifest.id.equals("example.pack"));

        Outcome<IconPackStore.Installed> installed = install(first.value);
        require(installed.error == null);
        require(installed.value != null);

        Outcome<IconPackRepository.State> initialState = load();
        if (initialState.error != null) {
            throw initialState.error;
        }
        require(initialState.value.packs.size() == 1);
        require(initialState.value.activeIds.isEmpty());

        IconPackRepository.State optimistic = initialState.value.withActive("example.pack", true);
        require(initialState.value.activeIds.isEmpty());
        require(optimistic.activeIds.equals(Collections.singletonList("example.pack")));
        require(optimistic.withActive("example.pack", false).activeIds.isEmpty());

        try {
            initialState.value.withActive("missing.pack", true);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
        }

        require(setActive("missing.pack", true).error != null);
        require(move("example.pack", -1).error != null);

        NotificationCenter.resetIconPackChanges();
        Outcome<IconPackRepository.State> active = setActive("example.pack", true);
        require(active.error == null);
        require(active.value.activeIds.equals(Collections.singletonList("example.pack")));
        awaitIconPackChanges(1);
        require(NotificationCenter.iconPackChangedCount == 1);

        require(setActive("example.pack", true).error == null);
        Thread.sleep(100);
        require(NotificationCenter.iconPackChangedCount == 1);

        Outcome<IconPackRepository.Review> update = review(new File(args[2]));
        require(update.error == null);
        require(update.value.isUpdate());
        require(update.value.previousVersion.equals("1.0"));

        Outcome<IconPackStore.Installed> updated = install(update.value);
        require(updated.error == null);
        require(updated.value != null);
        require(updated.value.manifest.version.equals("2.0"));
        require(load().value.activeIds.equals(Collections.singletonList("example.pack")));

        Outcome<IconPackRepository.Review> otherReview = review(new File(args[3]));
        require(otherReview.error == null);
        require(install(otherReview.value).error == null);
        require(setActive("other.pack", true).value.activeIds.size() == 2);
        require(move("other.pack", -1).value.activeIds.get(0).equals("other.pack"));
        require(move("other.pack", 1).value.activeIds.get(1).equals("other.pack"));

        Outcome<IconPackRepository.Review> current = review(new File(args[2]));
        require(current.error == null);
        require(current.value.previousVersion.equals("2.0"));

        IconPackRepository.discard(current.value);
        Outcome<IconPackStore.Installed> discarded = install(current.value);
        require(discarded.value == null);
        require(discarded.error != null);

        Outcome<IconPackRepository.Review> invalid = review(new File(args[4]));
        require(invalid.value == null);
        require(invalid.error instanceof IconPackRepository.ImportException);
        require(((IconPackRepository.ImportException) invalid.error).failure
                == IconPackRepository.Failure.INVALID);

        Outcome<IconPackRepository.Review> missing = review(new File(root, "missing.icons"));
        require(missing.value == null);
        require(missing.error instanceof IconPackRepository.ImportException);
        require(((IconPackRepository.ImportException) missing.error).failure
                == IconPackRepository.Failure.READ);

        Outcome<IconPackRepository.Review> oversized = review(new File(args[5]));
        require(oversized.value == null);
        require(oversized.error instanceof IconPackRepository.ImportException);
        require(((IconPackRepository.ImportException) oversized.error).failure
                == IconPackRepository.Failure.INVALID);

        Outcome<IconPackRepository.Review> cleanup = review(new File(args[2]));
        require(cleanup.error == null);
        File snapshot = snapshot(cleanup.value);
        require(snapshot.delete());
        require(snapshot.mkdir());
        File blocker = new File(snapshot, "blocker");
        require(blocker.createNewFile());
        IconPackRepository.discard(cleanup.value);
        Thread.sleep(200);
        require(blocker.delete());
        Thread.sleep(1200);
        require(!snapshot.exists());

        File repositoryCache = new File(cache, "fluxgram/icon_packs");
        File[] leftovers = repositoryCache.listFiles();
        require(leftovers != null && leftovers.length == 0);

        Outcome<IconPackRepository.State> inactive = setActive("example.pack", false);
        require(inactive.error == null);
        require(inactive.value.activeIds.equals(Collections.singletonList("other.pack")));

        require(setActive("other.pack", false).value.activeIds.isEmpty());

        Outcome<IconPackRepository.State> deleted = delete("example.pack");
        require(deleted.error == null);
        require(deleted.value.packs.size() == 1);
        require(delete("other.pack").value.packs.isEmpty());

        System.exit(0);
    }

    private static File snapshot(IconPackRepository.Review review) throws Exception {
        Field field = IconPackRepository.Review.class.getDeclaredField("snapshot");
        field.setAccessible(true);
        IconPackArchiveValidator.Snapshot snapshot =
                (IconPackArchiveValidator.Snapshot) field.get(review);

        return snapshot.archive();
    }

    private static void awaitIconPackChanges(int count) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;

        while (NotificationCenter.iconPackChangedCount < count
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
    }

    private static Outcome<IconPackRepository.Review> review(File archive) throws Exception {
        Outcome<IconPackRepository.Review> result = new Outcome<>();
        CountDownLatch latch = new CountDownLatch(1);

        IconPackRepository.review(Uri.fromFile(archive), (value, error) -> {
            result.value = value;
            result.error = error;
            latch.countDown();
        });

        require(latch.await(10, TimeUnit.SECONDS));

        return result;
    }

    private static Outcome<IconPackStore.Installed> install(
            IconPackRepository.Review review) throws Exception {
        Outcome<IconPackStore.Installed> result = new Outcome<>();
        CountDownLatch latch = new CountDownLatch(1);

        IconPackRepository.install(review, (value, error) -> {
            result.value = value;
            result.error = error;
            latch.countDown();
        });

        require(latch.await(10, TimeUnit.SECONDS));

        return result;
    }

    private static Outcome<IconPackRepository.State> load() throws Exception {
        Outcome<IconPackRepository.State> result = new Outcome<>();
        CountDownLatch latch = new CountDownLatch(1);

        IconPackRepository.load((value, error) -> {
            result.value = value;
            result.error = error;
            latch.countDown();
        });

        require(latch.await(10, TimeUnit.SECONDS));

        return result;
    }

    private static Outcome<IconPackRepository.State> setActive(
            String id,
            boolean active) throws Exception {
        Outcome<IconPackRepository.State> result = new Outcome<>();
        CountDownLatch latch = new CountDownLatch(1);

        IconPackRepository.setActive(id, active, (value, error) -> {
            result.value = value;
            result.error = error;
            latch.countDown();
        });

        require(latch.await(10, TimeUnit.SECONDS));

        return result;
    }

    private static Outcome<IconPackRepository.State> delete(String id) throws Exception {
        Outcome<IconPackRepository.State> result = new Outcome<>();
        CountDownLatch latch = new CountDownLatch(1);

        IconPackRepository.delete(id, (value, error) -> {
            result.value = value;
            result.error = error;
            latch.countDown();
        });

        require(latch.await(10, TimeUnit.SECONDS));

        return result;
    }

    private static Outcome<IconPackRepository.State> move(
            String id,
            int direction) throws Exception {
        Outcome<IconPackRepository.State> result = new Outcome<>();
        CountDownLatch latch = new CountDownLatch(1);

        IconPackRepository.move(id, direction, (value, error) -> {
            result.value = value;
            result.error = error;
            latch.countDown();
        });

        require(latch.await(10, TimeUnit.SECONDS));

        return result;
    }

    private static void require(boolean value) {
        if (!value) {
            throw new AssertionError();
        }
    }

    private static final class Outcome<T> {
        private T value;
        private Exception error;
    }
}

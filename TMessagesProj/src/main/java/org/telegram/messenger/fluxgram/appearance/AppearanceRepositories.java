package org.telegram.messenger.fluxgram.appearance;

import android.os.Process;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.UserConfig;

import java.util.HashMap;
import java.util.Map;

public final class AppearanceRepositories {
    private static final DispatchQueue workerQueue = new DispatchQueue(
            "fluxgram-appearance-cache",
            true,
            Process.THREAD_PRIORITY_BACKGROUND
    );
    private static final DispatchQueue networkQueue = new DispatchQueue(
            "fluxgram-appearance-network",
            true,
            Process.THREAD_PRIORITY_BACKGROUND
    );
    private static final Map<String, AppearanceRepository> repositories = new HashMap<>();

    public static synchronized AppearanceRepository get(int account) {
        long owner = UserConfig.getInstance(account).getClientUserId();

        if (owner <= 0) {
            throw new IllegalStateException("account is not active");
        }

        String key = account + ":" + owner;
        AppearanceRepository repository = repositories.get(key);

        if (repository == null) {
            AppearanceApiParser parser = new AppearanceApiParser();
            repository = new AppearanceRepository(
                    account,
                    owner,
                    new AppearanceSQLiteCache(account, owner, parser),
                    new AppearanceHttpClient(BuildConfig.FLUXGRAM_APPEARANCE_API_BASE_URL, parser),
                    value -> UserConfig.getInstance(value).getClientUserId(),
                    System::currentTimeMillis,
                    workerQueue::postRunnable,
                    networkQueue::postRunnable,
                    AndroidUtilities::runOnUIThread
            );
            repositories.put(key, repository);
        }

        return repository;
    }

    private AppearanceRepositories() {
    }
}

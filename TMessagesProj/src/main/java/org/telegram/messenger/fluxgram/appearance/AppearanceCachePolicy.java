package org.telegram.messenger.fluxgram.appearance;

public final class AppearanceCachePolicy {
    public enum State {
        FRESH,
        STALE,
        EXPIRED
    }

    public static final long POSITIVE_STALE_WINDOW_MS = 15 * 60 * 1000L;

    public State state(AppearanceApiDto.Result result, long now) {
        if (now <= result.getExpiresAt()) {
            return State.FRESH;
        }
        if (result.getProfile() != null
                && now - result.getExpiresAt() <= POSITIVE_STALE_WINDOW_MS) {
            return State.STALE;
        }

        return State.EXPIRED;
    }
}

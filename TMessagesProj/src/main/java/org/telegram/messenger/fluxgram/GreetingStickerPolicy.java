package org.telegram.messenger.fluxgram;

public final class GreetingStickerPolicy {
    private GreetingStickerPolicy() {
    }

    public static boolean shouldHideRandomSticker(
            boolean showGreeting,
            boolean eligibleChat,
            boolean userInfoLoaded,
            boolean hasBusinessIntro,
            boolean contactRequiresPremium,
            boolean isPremium,
            long paidMessageStars
    ) {
        return !showGreeting
                && eligibleChat
                && userInfoLoaded
                && !hasBusinessIntro
                && (!contactRequiresPremium || isPremium)
                && paidMessageStars <= 0;
    }
}

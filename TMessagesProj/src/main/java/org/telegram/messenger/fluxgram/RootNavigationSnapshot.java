package org.telegram.messenger.fluxgram;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class RootNavigationSnapshot {
    public static final String CHATS = "chats";
    public static final String CONTACTS = "contacts";
    public static final String CALLS = "calls";
    public static final String SETTINGS = "settings";
    public static final String PROFILE = "profile";

    private static final Set<String> DESTINATIONS = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    CHATS,
                    CONTACTS,
                    CALLS,
                    SETTINGS,
                    PROFILE
            ))
    );

    private final int account;
    private final int rootIdentity;
    private final boolean bottomNavigationVisible;
    private final boolean callsTabVisible;
    private final String activeDestination;

    private RootNavigationSnapshot(
            int account,
            int rootIdentity,
            boolean bottomNavigationVisible,
            boolean callsTabVisible,
            String activeDestination
    ) {
        this.account = account;
        this.rootIdentity = rootIdentity;
        this.bottomNavigationVisible = bottomNavigationVisible;
        this.callsTabVisible = callsTabVisible;
        this.activeDestination = DESTINATIONS.contains(activeDestination)
                ? activeDestination
                : CHATS;
    }

    public static RootNavigationSnapshot create(
            int account,
            int rootIdentity,
            int bottomNavigationMode,
            boolean callsTabVisible,
            String activeDestination
    ) {
        return new RootNavigationSnapshot(
                account,
                rootIdentity,
                BottomNavigationSettings.isVisible(bottomNavigationMode),
                callsTabVisible,
                activeDestination
        );
    }

    public int getAccount() {
        return account;
    }

    public int getRootIdentity() {
        return rootIdentity;
    }

    public boolean isBottomNavigationVisible() {
        return bottomNavigationVisible;
    }

    public boolean isCallsTabVisible() {
        return callsTabVisible;
    }

    public String getActiveDestination() {
        return activeDestination;
    }

    public boolean hasDestination(String destination) {
        return DESTINATIONS.contains(destination);
    }

    public boolean canSelectInRoot(String destination) {
        if (CHATS.equals(destination)) {
            return true;
        }
        if (!bottomNavigationVisible) {
            return false;
        }
        if (CONTACTS.equals(destination) || PROFILE.equals(destination)) {
            return true;
        }
        if (CALLS.equals(destination)) {
            return callsTabVisible;
        }
        if (SETTINGS.equals(destination)) {
            return !callsTabVisible;
        }

        return false;
    }

    public boolean matches(RootNavigationSnapshot other) {
        return equals(other);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof RootNavigationSnapshot)) {
            return false;
        }

        RootNavigationSnapshot other = (RootNavigationSnapshot) object;

        return account == other.account
                && rootIdentity == other.rootIdentity
                && bottomNavigationVisible == other.bottomNavigationVisible
                && callsTabVisible == other.callsTabVisible
                && activeDestination.equals(other.activeDestination);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                account,
                rootIdentity,
                bottomNavigationVisible,
                callsTabVisible,
                activeDestination
        );
    }
}

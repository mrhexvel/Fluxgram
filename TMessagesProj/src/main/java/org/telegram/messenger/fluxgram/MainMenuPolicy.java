package org.telegram.messenger.fluxgram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MainMenuPolicy {
    public static final String THEME = "theme";
    public static final String NEW_GROUP = "new_group";
    public static final String NEW_CHANNEL = "new_channel";
    public static final String SAVED_MESSAGES = "saved_messages";
    public static final String CONTACTS = RootNavigationSnapshot.CONTACTS;
    public static final String CALLS = RootNavigationSnapshot.CALLS;
    public static final String WALLET = "wallet";
    public static final String ACCOUNTS = "accounts";
    public static final String SETTINGS = RootNavigationSnapshot.SETTINGS;
    public static final String PROXY = "proxy";

    public static final String DIVIDER_PRIMARY = "divider:primary";
    public static final String DIVIDER_SYSTEM = "divider:system";

    private static final List<String> DEFAULT_ORDER = Collections.unmodifiableList(Arrays.asList(
            THEME,
            DIVIDER_PRIMARY,
            NEW_GROUP,
            NEW_CHANNEL,
            SAVED_MESSAGES,
            CONTACTS,
            CALLS,
            WALLET,
            ACCOUNTS,
            SETTINGS,
            DIVIDER_SYSTEM,
            PROXY
    ));
    private static final Set<String> KNOWN_IDS = Collections.unmodifiableSet(
            new LinkedHashSet<>(DEFAULT_ORDER)
    );
    private static final Set<String> DIVIDER_IDS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(DIVIDER_PRIMARY, DIVIDER_SYSTEM))
    );
    private static final Set<String> DEFAULT_HIDDEN = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    NEW_CHANNEL,
                    CONTACTS,
                    CALLS,
                    ACCOUNTS,
                    SETTINGS
            ))
    );

    private MainMenuPolicy() {
    }

    public static State defaults() {
        return new State(DEFAULT_ORDER, DEFAULT_HIDDEN);
    }

    public static State normalize(
            Collection<String> orderedIds,
            Collection<String> hiddenIds
    ) {
        LinkedHashSet<String> normalizedOrder = new LinkedHashSet<>();
        if (orderedIds != null) {
            for (String id : orderedIds) {
                if (KNOWN_IDS.contains(id)) {
                    normalizedOrder.add(id);
                }
            }
        }
        normalizedOrder.addAll(DEFAULT_ORDER);

        LinkedHashSet<String> normalizedHidden = new LinkedHashSet<>();
        if (hiddenIds != null) {
            for (String id : hiddenIds) {
                if (KNOWN_IDS.contains(id) && !isDivider(id)) {
                    normalizedHidden.add(id);
                }
            }
        }

        return new State(normalizedOrder, normalizedHidden);
    }

    public static List<String> resolve(
            State state,
            Collection<String> availableIds,
            boolean hasSettingsAlternative,
            boolean hasAccountsAlternative
    ) {
        State normalizedState = state == null
                ? defaults()
                : normalize(state.getOrder(), state.getHidden());
        Set<String> available = availableIds == null
                ? Collections.emptySet()
                : new HashSet<>(availableIds);
        requireReachable(available, SETTINGS, hasSettingsAlternative);
        requireReachable(available, ACCOUNTS, hasAccountsAlternative);
        List<String> result = new ArrayList<>();
        String pendingDivider = null;

        for (String id : normalizedState.getOrder()) {
            if (isDivider(id)) {
                if (!result.isEmpty() && pendingDivider == null) {
                    pendingDivider = id;
                }

                continue;
            }

            boolean forced = SETTINGS.equals(id) && !hasSettingsAlternative
                    || ACCOUNTS.equals(id) && !hasAccountsAlternative;
            boolean visible = available.contains(id)
                    && (forced || !normalizedState.getHidden().contains(id));
            if (!visible) {
                continue;
            }

            if (pendingDivider != null) {
                result.add(pendingDivider);
                pendingDivider = null;
            }
            result.add(id);
        }

        return Collections.unmodifiableList(result);
    }

    public static List<String> knownIds() {
        return DEFAULT_ORDER;
    }

    public static boolean isDivider(String id) {
        return DIVIDER_IDS.contains(id);
    }

    private static void requireReachable(
            Set<String> available,
            String id,
            boolean hasAlternative
    ) {
        if (!hasAlternative && !available.contains(id)) {
            throw new IllegalArgumentException(id);
        }
    }

    public static final class State {
        private final List<String> order;
        private final Set<String> hidden;

        private State(Collection<String> order, Collection<String> hidden) {
            this.order = Collections.unmodifiableList(new ArrayList<>(order));
            this.hidden = Collections.unmodifiableSet(new LinkedHashSet<>(hidden));
        }

        public List<String> getOrder() {
            return order;
        }

        public Set<String> getHidden() {
            return hidden;
        }
    }
}

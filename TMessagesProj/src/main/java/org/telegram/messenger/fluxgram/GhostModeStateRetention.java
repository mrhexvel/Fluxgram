package org.telegram.messenger.fluxgram;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class GhostModeStateRetention {
    static final String ORDER_SUFFIX = "_fg_order";
    static final String NEXT_ORDER = "fg_next_order";

    private GhostModeStateRetention() {
    }

    static String evictionCandidate(
            Map<String, ?> values,
            int limit,
            String excludedSuffix,
            String... prefixes
    ) {
        int count = 0;
        String candidate = null;
        long candidateOrder = Long.MAX_VALUE;

        for (String key : values.keySet()) {
            if (!matches(key, excludedSuffix, prefixes)) {
                continue;
            }

            count++;
            Object storedOrder = values.get(orderKey(key));
            long order = storedOrder instanceof Long ? (Long) storedOrder : 0;

            if (candidate == null || order < candidateOrder
                    || (order == candidateOrder && key.compareTo(candidate) < 0)) {
                candidate = key;
                candidateOrder = order;
            }
        }

        return count >= limit ? candidate : null;
    }

    static List<String> excessEntries(
            Map<String, ?> values,
            int limit,
            String excludedSuffix,
            String... prefixes
    ) {
        ArrayList<Entry> entries = new ArrayList<>();

        for (String key : values.keySet()) {
            if (!matches(key, excludedSuffix, prefixes)) {
                continue;
            }

            Object storedOrder = values.get(orderKey(key));
            long order = storedOrder instanceof Long ? (Long) storedOrder : 0;
            entries.add(new Entry(key, order));
        }

        if (entries.size() <= limit) {
            return new ArrayList<>();
        }

        entries.sort(Comparator.comparingLong((Entry entry) -> entry.order)
                .thenComparing(entry -> entry.key));
        ArrayList<String> excess = new ArrayList<>();

        for (int index = 0; index < entries.size() - limit; index++) {
            excess.add(entries.get(index).key);
        }

        return excess;
    }

    static String orderKey(String key) {
        return key + ORDER_SUFFIX;
    }

    static long nextOrder(long current) {
        return current == Long.MAX_VALUE ? Long.MAX_VALUE : current + 1;
    }

    private static boolean matches(String key, String excludedSuffix, String... prefixes) {
        if (key.endsWith(ORDER_SUFFIX)
                || (excludedSuffix != null && key.endsWith(excludedSuffix))) {
            return false;
        }

        for (String prefix : prefixes) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    private static final class Entry {
        private final String key;
        private final long order;

        private Entry(String key, long order) {
            this.key = key;
            this.order = order;
        }
    }
}

package org.telegram.messenger.fluxgram.appearance;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AppearanceMemoryCache {
    public static final int MAX_ENTRIES = 512;
    private final LinkedHashMap<FluxGramPeerKey, AppearanceApiDto.Result> profiles =
            new LinkedHashMap<FluxGramPeerKey, AppearanceApiDto.Result>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<FluxGramPeerKey, AppearanceApiDto.Result> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };
    private final LinkedHashMap<FluxGramPeerKey, FluxGramPeerKey> aliases =
            new LinkedHashMap<FluxGramPeerKey, FluxGramPeerKey>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<FluxGramPeerKey, FluxGramPeerKey> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };

    public AppearanceApiDto.Result get(FluxGramPeerKey requested) {
        FluxGramPeerKey canonical = aliases.get(requested);
        return profiles.get(canonical == null ? requested : canonical);
    }

    public AppearanceApiDto.Result getCanonical(FluxGramPeerKey canonical) {
        return profiles.get(canonical);
    }

    public void put(FluxGramPeerKey requested, AppearanceApiDto.Result result) {
        profiles.put(result.getPeer(), result);

        if (requested.equals(result.getPeer())) {
            aliases.remove(requested);
        } else {
            aliases.put(requested, result.getPeer());
        }
    }

    public void clear() {
        profiles.clear();
        aliases.clear();
    }

    int size() {
        return profiles.size();
    }
}

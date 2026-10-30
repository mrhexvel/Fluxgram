package org.telegram.messenger.fluxgram.icons;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public final class IconPackResolver {
    private final IconPackStore store;
    private final List<IconPackStore.Installed> activePacks;

    public IconPackResolver(
            IconPackStore store,
            List<IconPackStore.Installed> installed,
            List<String> activeIds) {
        this.store = store;

        HashMap<String, IconPackStore.Installed> byId = new HashMap<>();

        for (IconPackStore.Installed pack : installed) {
            byId.put(pack.manifest.id, pack);
        }

        ArrayList<IconPackStore.Installed> normalized = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();

        for (String id : activeIds) {
            IconPackStore.Installed pack = byId.get(id);

            if (pack != null && seen.add(id)) {
                normalized.add(pack);
            }
        }

        activePacks = Collections.unmodifiableList(normalized);
    }

    public List<String> activeIds() {
        ArrayList<String> ids = new ArrayList<>(activePacks.size());

        for (IconPackStore.Installed pack : activePacks) {
            ids.add(pack.manifest.id);
        }

        return Collections.unmodifiableList(ids);
    }

    public byte[] resolve(String resourceName) {
        for (IconPackStore.Installed pack : activePacks) {
            try {
                byte[] icon = store.resolve(pack, resourceName);

                if (icon != null) {
                    return icon;
                }
            } catch (IOException ignored) {
            }
        }

        return null;
    }
}

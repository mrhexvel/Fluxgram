package org.telegram.messenger.fluxgram.icons;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;

public class IconPackStore {
    public static final class Installed {
        public final IconPackManifest manifest;
        private final String generation;

        public Installed(IconPackManifest manifest) {
            this(manifest, "generation");
        }

        public Installed(IconPackManifest manifest, String generation) {
            this.manifest = manifest;
            this.generation = generation;
        }

        String generation() {
            return generation;
        }
    }

    private byte[] payload;
    private final Map<Installed, byte[]> payloads = new IdentityHashMap<>();
    public Installed lastResolved;

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public void setPayload(Installed installed, byte[] payload) {
        payloads.put(installed, payload);
    }

    public byte[] resolve(Installed installed, String resourceName) throws IOException {
        lastResolved = installed;

        return payloads.containsKey(installed) ? payloads.get(installed) : payload;
    }
}

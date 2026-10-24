package org.telegram.messenger.fluxgram.icons;

import java.io.IOException;

public class IconPackStore {
    public static final class Installed {
        public final IconPackManifest manifest;

        public Installed(IconPackManifest manifest) {
            this.manifest = manifest;
        }
    }

    private byte[] payload;

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public byte[] resolve(Installed installed, String resourceName) throws IOException {
        return payload;
    }
}

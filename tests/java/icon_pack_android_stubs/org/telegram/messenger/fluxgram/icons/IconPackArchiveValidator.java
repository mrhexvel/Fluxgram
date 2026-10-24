package org.telegram.messenger.fluxgram.icons;

public final class IconPackArchiveValidator {
    public static final long MAX_ICON_BYTES = 2L * 1024 * 1024;

    public interface Catalog {
        boolean contains(String resourceName);

        default boolean decodesRaster(byte[] bytes, String path, int width, int height) {
            return false;
        }

        default boolean decodesSvg(byte[] bytes, String path) {
            return false;
        }
    }
}

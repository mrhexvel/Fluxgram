package org.telegram.messenger.fluxgram.icons;

public final class IconPackArchiveValidator {
    public static final int VALIDATION_VERSION = 1;
    public static final long MAX_ICON_BYTES = 2L * 1024 * 1024;
    public static final int MAX_RASTER_DIMENSION = 4096;
    public static final long MAX_RASTER_PIXELS = 2048L * 2048;

    public interface Catalog {
        boolean contains(String resourceName);

        default int validationVersion() {
            return VALIDATION_VERSION;
        }

        default boolean decodesRaster(byte[] bytes, String path, int width, int height) {
            return false;
        }

        default boolean decodesSvg(byte[] bytes, String path) {
            return false;
        }
    }
}

package org.telegram.messenger.plugins;

import android.graphics.Canvas;
import android.view.View;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.plugins.api.JavaPluginContext;
import org.telegram.messenger.plugins.api.JavaPluginUiResource;

import java.util.ArrayList;
import java.util.function.BooleanSupplier;

public final class ComposerInputRendererExtensions {
    public static final String EXTENSION_ID = "ui.chat.composer.input.native_draw";

    private static final HostExtensionDescriptor<Frame, Void> DESCRIPTOR =
            new HostExtensionDescriptor<>(
                    EXTENSION_ID, 1, HostExtensionDescriptor.Mode.REPLACEMENT,
                    2_000_000L, 3, 0);
    private static final HostExtensionRegistry REGISTRY =
            HostExtensionAttribution.create(FileLog::e);
    private static final ArrayList<ComposerInputRendererHostSurface> surfaces = new ArrayList<>();

    private ComposerInputRendererExtensions() {
    }

    public static JavaPluginUiResource register(JavaPluginContext context, Factory factory) {
        if (!(context instanceof LowLevelJavaPluginContext)) {
            throw new IllegalArgumentException("Unsupported Java plugin context");
        }

        if (factory == null) {
            throw new IllegalArgumentException("Composer input renderer factory is required");
        }

        return ((LowLevelJavaPluginContext) context).registerLowLevelUiResource(
                EXTENSION_ID, factory);
    }

    static AutoCloseable register(String pluginId, long generation,
            BooleanSupplier active, Factory factory) {
        AutoCloseable registration = REGISTRY.registerReplacement(
                pluginId, generation, active, DESCRIPTOR,
                new HostExtensionRegistrationSpec(pluginId + ".composer_input_renderer", 0),
                () -> {
                    Renderer renderer = factory.create();

                    if (renderer == null) {
                        throw new IllegalStateException("Composer input renderer factory returned null");
                    }

                    return new HostExtensionReplacement<Frame>() {
                        @Override
                        public boolean replace(Frame input) throws Exception {
                            return renderer.draw(input) == Result.COMMIT;
                        }

                        @Override
                        public void close() throws Exception {
                            renderer.close();
                        }
                    };
                });
        invalidateSurfaces();

        return () -> {
            try {
                registration.close();
            } finally {
                invalidateSurfaces();
            }
        };
    }

    static HostExtensionRegistry.Endpoint<Frame, Void> openEndpoint() {
        return REGISTRY.openEndpoint(DESCRIPTOR);
    }

    static synchronized void addSurface(ComposerInputRendererHostSurface surface) {
        surfaces.add(surface);
    }

    static synchronized void removeSurface(ComposerInputRendererHostSurface surface) {
        surfaces.remove(surface);
    }

    static void report(Throwable error) {
        FileLog.e(error);
    }

    private static void invalidateSurfaces() {
        ArrayList<ComposerInputRendererHostSurface> snapshot;

        synchronized (ComposerInputRendererExtensions.class) {
            snapshot = new ArrayList<>(surfaces);
        }

        for (ComposerInputRendererHostSurface surface : snapshot) {
            surface.invalidate();
        }
    }

    public enum Result {
        COMMIT,
        FALLBACK
    }

    public interface Factory {
        Renderer create() throws Exception;
    }

    public interface Renderer extends AutoCloseable {
        Result draw(Frame frame) throws Exception;

        @Override
        default void close() throws Exception {
        }
    }

    public interface Frame {
        View getView();

        Canvas getCanvas();

        int getWidth();

        int getHeight();

        void drawNative();
    }
}

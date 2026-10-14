package org.telegram.messenger.plugins;

import android.graphics.Canvas;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.plugins.api.ComposerExpandedBounds;
import org.telegram.messenger.plugins.api.ComposerGeometry;
import org.telegram.messenger.plugins.api.ComposerInput;
import org.telegram.messenger.plugins.api.ComposerSendEvent;
import org.telegram.messenger.plugins.api.ComposerTextLayoutChange;
import org.telegram.messenger.plugins.api.JavaPluginContext;
import org.telegram.messenger.plugins.api.JavaPluginUiResource;

import java.util.ArrayList;
import java.util.function.BooleanSupplier;

public final class ComposerInputRendererExtensions {
    public static final String EXTENSION_ID = "ui.chat.composer.input.native_draw";
    public static final int CAPABILITY_INPUT = 1;
    public static final int CAPABILITY_TEXT_LAYOUT = 1 << 1;
    public static final int CAPABILITY_SEND = 1 << 2;
    public static final int CAPABILITY_EXPANDED_DRAW = 1 << 3;

    static final int INVOCATION_INITIALIZE = 0;
    static final int INVOCATION_INPUT = 1;
    static final int INVOCATION_TEXT_LAYOUT = 2;
    static final int INVOCATION_SEND = 3;
    static final int INVOCATION_FRAME = 4;
    static final int INVOCATION_DRAW = 5;
    static final int INVOCATION_EXPANDED_DRAW = 6;

    private static final int ALL_CAPABILITIES = CAPABILITY_INPUT | CAPABILITY_TEXT_LAYOUT
            | CAPABILITY_SEND | CAPABILITY_EXPANDED_DRAW;
    private static final HostExtensionDescriptor<Object, Void> DESCRIPTOR =
            new HostExtensionDescriptor<>(
                    EXTENSION_ID, 2, HostExtensionDescriptor.Mode.REPLACEMENT,
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
                () -> new HostExtensionReplacement<Object>() {
                    private Renderer renderer;

                    @Override
                    public boolean replace(Object input) throws Exception {
                        ComposerInputRendererHostSurface.Invocation invocation =
                                (ComposerInputRendererHostSurface.Invocation) input;

                        if (renderer == null) {
                            renderer = factory.create(invocation.surface);

                            if (renderer == null) {
                                throw new IllegalStateException(
                                        "Composer input renderer factory returned null");
                            }

                            int capabilities = renderer.getCapabilities();

                            if ((capabilities & ~ALL_CAPABILITIES) != 0) {
                                throw new IllegalArgumentException(
                                        "Unknown composer input renderer capability");
                            }

                            invocation.surface.setCapabilities(capabilities);
                        }

                        switch (invocation.type) {
                            case INVOCATION_INITIALIZE:
                                return false;
                            case INVOCATION_INPUT:
                                renderer.onInput((ComposerInput) invocation.value);
                                return false;
                            case INVOCATION_TEXT_LAYOUT:
                                renderer.onTextLayoutChanged(
                                        (ComposerTextLayoutChange) invocation.value);
                                return false;
                            case INVOCATION_SEND:
                                renderer.onSend((ComposerSendEvent) invocation.value);
                                return false;
                            case INVOCATION_FRAME:
                                return renderer.onFrame(invocation.frameTimeNanos);
                            case INVOCATION_DRAW:
                                return renderer.draw((Frame) invocation) == Result.COMMIT;
                            case INVOCATION_EXPANDED_DRAW:
                                renderer.drawExpanded((ExpandedFrame) invocation);
                                return false;
                            default:
                                throw new IllegalArgumentException(
                                        "Unknown composer input renderer invocation");
                        }
                    }

                    @Override
                    public void close() throws Exception {
                        if (renderer != null) {
                            renderer.close();
                            renderer = null;
                        }
                    }
                });
        refreshSurfaces();

        return () -> {
            try {
                registration.close();
            } finally {
                refreshSurfaces();
            }
        };
    }

    static HostExtensionRegistry.Endpoint<Object, Void> openEndpoint() {
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

    private static void refreshSurfaces() {
        ArrayList<ComposerInputRendererHostSurface> snapshot;

        synchronized (ComposerInputRendererExtensions.class) {
            snapshot = new ArrayList<>(surfaces);
        }

        for (ComposerInputRendererHostSurface surface : snapshot) {
            surface.refreshRegistration();
        }
    }

    public enum Result {
        COMMIT,
        FALLBACK
    }

    public interface Factory {
        Renderer create(Surface surface) throws Exception;
    }

    public interface Surface {
        int getAccount();

        long getOwnerId();

        long getDialogId();

        ComposerGeometry getGeometry();

        boolean isActive();

        void requestFrame();
    }

    public interface Renderer extends AutoCloseable {
        default int getCapabilities() {
            return 0;
        }

        default void onInput(ComposerInput input) throws Exception {
        }

        default void onTextLayoutChanged(ComposerTextLayoutChange change) throws Exception {
        }

        default void onSend(ComposerSendEvent event) throws Exception {
        }

        default boolean onFrame(long frameTimeNanos) throws Exception {
            return false;
        }

        Result draw(Frame frame) throws Exception;

        default void drawExpanded(ExpandedFrame frame) throws Exception {
        }

        @Override
        default void close() throws Exception {
        }
    }

    public interface Frame {
        Canvas getCanvas();

        ComposerGeometry getGeometry();

        int getWidth();

        int getHeight();

        void drawNative();
    }

    public interface ExpandedFrame {
        Canvas getCanvas();

        ComposerGeometry getGeometry();

        ComposerExpandedBounds getBounds();
    }
}

package org.fluxgram;

import android.view.View;

/**
 * One screen-scoped custom block shown on a plugin settings page.
 *
 * <p>The factory and all methods of the returned block run on the Android main thread. A new
 * instance is created for each settings screen. The root view must be non-null and unparented.
 * Closing the screen, disabling or reloading the plugin, or closing the registration removes the
 * view and invokes {@link #close()}.</p>
 */
public interface JavaSettingsBlock extends AutoCloseable {
    /** Creates one block instance for an active settings screen. */
    interface Factory {
        /**
         * Creates a block on the Android main thread.
         *
         * @param context screen-scoped host services and theme colors
         * @return non-null block instance
         * @throws Exception when the block cannot be created
         */
        JavaSettingsBlock create(JavaSettingsBlockContext context) throws Exception;
    }

    /**
     * Returns the unparented root view to place inside the Telegram settings section.
     *
     * @return non-null root view
     */
    View getView();

    /**
     * Releases listeners, animations, and other screen-scoped resources on the main thread.
     *
     * @throws Exception when cleanup failed and must be retried by the host
     */
    @Override
    void close() throws Exception;
}

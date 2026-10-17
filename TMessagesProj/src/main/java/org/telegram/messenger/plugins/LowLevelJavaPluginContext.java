package org.telegram.messenger.plugins;

import org.fluxgram.JavaPluginUiResource;

interface LowLevelJavaPluginContext {
    JavaPluginUiResource registerLowLevelUiResource(String extensionId, Object factory);
}

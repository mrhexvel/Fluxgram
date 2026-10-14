package org.telegram.messenger.plugins;

import org.telegram.messenger.plugins.api.JavaPluginUiResource;

interface LowLevelJavaPluginContext {
    JavaPluginUiResource registerLowLevelUiResource(String extensionId, Object factory);
}

package org.telegram.messenger.plugins.api;

public interface ChatSelectionAction {
    default boolean isAvailable(ChatSelectionMessageSnapshot message) throws Exception {
        return true;
    }

    void onSelected(ChatSelectionMessageSnapshot message) throws Exception;
}

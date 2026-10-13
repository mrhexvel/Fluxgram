package org.telegram.messenger.plugins.api;

public interface ComposerTextClusterCapture {
    long getRevision();

    int getCount();

    ComposerTextCapture getCapture(int index);
}

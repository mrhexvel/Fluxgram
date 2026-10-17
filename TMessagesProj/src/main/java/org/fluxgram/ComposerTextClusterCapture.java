package org.fluxgram;

public interface ComposerTextClusterCapture {
    long getRevision();

    int getCount();

    ComposerTextCapture getCapture(int index);
}

package org.fluxgram;

/** Callback-scoped, host-owned batch of text captures for one exact layout revision. */
public interface ComposerTextClusterCapture {
    /** Returns the revision shared by all captures in this batch. */
    long getRevision();

    /** Returns the bounded number of captures, matching the accepted cluster selection. */
    int getCount();

    /** Returns callback-scoped metadata for one host capture. */
    ComposerTextCapture getCapture(int index);
}

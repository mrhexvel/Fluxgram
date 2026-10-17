package org.fluxgram;

/** A composer effect that opts into raw device-motion samples through its surface. */
public interface ComposerMotionEffect extends ComposerEffect {
    /** Receives the latest coalesced raw sensor sample on a requested main-thread frame. */
    void onMotion(ComposerMotionSnapshot snapshot) throws Exception;
}

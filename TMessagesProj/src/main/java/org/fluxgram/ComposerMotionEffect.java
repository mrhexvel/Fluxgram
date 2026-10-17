package org.fluxgram;

public interface ComposerMotionEffect extends ComposerEffect {
    void onMotion(ComposerMotionSnapshot snapshot) throws Exception;
}

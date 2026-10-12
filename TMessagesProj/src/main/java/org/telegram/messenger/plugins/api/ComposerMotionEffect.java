package org.telegram.messenger.plugins.api;

public interface ComposerMotionEffect extends ComposerEffect {
    void onMotion(ComposerMotionSnapshot snapshot) throws Exception;
}

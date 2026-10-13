from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"


class ComposerEffectsTest(unittest.TestCase):
    def test_composing_word_replacements_preserve_small_changes(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        with tempfile.TemporaryDirectory() as directory:
            sources = [
                JAVA / "messenger/plugins/api/ComposerInput.java",
                JAVA / "ui/fluxgram/ComposerInputChanges.java",
                ROOT / "tests/java/ComposerInputChangesCheck.java",
            ]
            subprocess.run(["javac", "--release", "8", "-d", directory, *map(str, sources)],
                           check=True, capture_output=True)
            subprocess.run(["java", "-cp", directory, "org.telegram.ui.fluxgram.ComposerInputChangesCheck"],
                           check=True, capture_output=True)

    def test_registry_identity_cleanup_failure_and_limits(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        with tempfile.TemporaryDirectory() as directory:
            sources = [
                *sorted((ROOT / "tests/java/composer_stubs").rglob("*.java")),
                *sorted((JAVA / "messenger/plugins/api").glob("*.java")),
                JAVA / "messenger/plugins/ComposerFrameCoordinator.java",
                JAVA / "messenger/plugins/ComposerMotionSensors.java",
                JAVA / "messenger/plugins/PluginComposerRegistry.java",
                ROOT / "tests/java/ComposerRegistryCheck.java",
            ]
            subprocess.run(["javac", "--release", "8", "-d", directory, *map(str, sources)],
                           check=True, capture_output=True)
            subprocess.run(["java", "-cp", directory, "org.telegram.messenger.plugins.ComposerRegistryCheck"],
                           check=True, capture_output=True)

    def test_frame_coalescing_budget_fairness_and_cancellation(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        with tempfile.TemporaryDirectory() as directory:
            sources = [
                JAVA / "messenger/plugins/ComposerFrameCoordinator.java",
                ROOT / "tests/java/ComposerFrameCoordinatorCheck.java",
            ]
            subprocess.run(["javac", "--release", "8", "-d", directory, *map(str, sources)],
                           check=True, capture_output=True)
            subprocess.run(["java", "-cp", directory,
                            "org.telegram.messenger.plugins.ComposerFrameCoordinatorCheck"],
                           check=True, capture_output=True)

    def test_text_insertion_geometry_is_bounded_and_cluster_aligned(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        with tempfile.TemporaryDirectory() as directory:
            sources = [
                *sorted((JAVA / "messenger/plugins/api").glob("*.java")),
                ROOT / "tests/java/composer_stubs/android/graphics/Canvas.java",
                JAVA / "ui/fluxgram/ComposerTextInsertions.java",
                ROOT / "tests/java/ComposerTextInsertionsCheck.java",
            ]
            subprocess.run(["javac", "--release", "8", "-d", directory, *map(str, sources)],
                           check=True, capture_output=True)
            subprocess.run(["java", "-cp", directory,
                            "org.telegram.ui.fluxgram.ComposerTextInsertionsCheck"],
                           check=True, capture_output=True)

    def test_composer_is_opt_in_and_does_not_modify_text(self):
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        policy = chat.split("private boolean isPluginComposerEligible()", 1)[1].split(
            "private static class ChatActivityTextSelectionHelper", 1
        )[0]
        for gate in ("paused", "isFullyVisible", "isPluginChatLifecycleEligible", "isTopic",
                     "isEditingMessage", "isInScheduleMode", "isRichDraftActive", "isStories",
                     "isRecordingAudioVideo", "hasAudioToSend", "isEphemeralMessageVisible",
                     "isForum", "isMonoForum", "isCommunity", "isChannelAndNotMegaGroup"):
            self.assertIn(gate, policy)

        controller = (JAVA / "ui/fluxgram/FluxGramComposerController.java").read_text()
        for forbidden in (".setText(", ".setSpan(", ".setSelection(", "getRootView()", "postDelayed("):
            self.assertNotIn(forbidden, controller)

        for required in ("getOverlay().remove", "removeTextChangedListener", "removeListener(this)",
                          "removeOnPreDrawListener", "onBecameBackground", "onViewDetachedFromWindow",
                           "getClientUserId() == ownerId", "selectedAccount == account",
                           "field.getParent()", "sendTransitionDeadline", "ComposerExpandedBounds.MAX_OUTSET_DP",
                           "chatHost.indexOfChild", "removeExpandedLayer", "View layer = expandedLayer",
                           "AndroidUtilities.runOnUIThread", "((ViewGroup) parent).removeView(layer)"):
            self.assertIn(required, controller)

        remove_expanded = controller.split("private void removeExpandedLayer()", 1)[1].split(
            "private void removeOverlay()", 1
        )[0]
        self.assertLess(remove_expanded.index("expandedLayer = null"),
                        remove_expanded.index("AndroidUtilities.runOnUIThread"))

        self.assertIn("chatInputViewsContainer, currentAccount", chat)

    def test_settings_do_not_add_default_composer_preview(self):
        registry = (JAVA / "messenger/plugins/PluginComposerRegistry.java").read_text()
        controller = (JAVA / "ui/fluxgram/FluxGramComposerController.java").read_text()
        activity = (JAVA / "ui/PluginSettingsActivity.java").read_text()
        adapter = (JAVA / "messenger/plugins/PythonPluginAdapter.java").read_text()

        self.assertIn("registration.pluginId.equals(pluginId)", registry)
        self.assertIn("PluginComposerRegistry.attachPreview(host, previewPluginId)", controller)
        self.assertFalse((JAVA / "ui/fluxgram/PluginComposerPreviewView.java").exists())
        self.assertNotIn("PluginComposerPreviewView", activity)
        self.assertNotIn("FluxGramPluginPreview", activity)

        drain = adapter.split("private void drainSettings()", 1)[1].split(
            "public synchronized boolean isStoppingForRemoval()", 1
        )[0]
        self.assertLess(drain.index("javaLoader.settingChanged"), drain.index("dispatch_setting_changed"))
        self.assertLess(drain.index("dispatch_setting_changed"), drain.index("refreshPreview"))

    def test_composer_registration_uses_ui_cleanup_scope(self):
        adapter = (JAVA / "messenger/plugins/PythonPluginAdapter.java").read_text()
        self.assertIn("uiResources.register(() -> PluginComposerRegistry.register", adapter)
        api = (JAVA / "messenger/plugins/api/ComposerEffect.java").read_text()
        self.assertIn("android.graphics.Canvas", api)
        self.assertNotIn("org.telegram.ui", api)


if __name__ == "__main__":
    unittest.main()

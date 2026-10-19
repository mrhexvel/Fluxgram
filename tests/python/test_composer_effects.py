from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
API = ROOT / "TMessagesProj/src/main/java/org/fluxgram"


class ComposerEffectsTest(unittest.TestCase):
    def test_composing_word_replacements_preserve_small_changes(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        with tempfile.TemporaryDirectory() as directory:
            sources = [
                API / "ComposerInput.java",
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
                ROOT / "tests/java/api_stubs/android/view/View.java",
                *sorted(API.glob("*.java")),
                JAVA / "messenger/plugins/ComposerFrameCoordinator.java",
                JAVA / "messenger/plugins/ComposerMotionSensors.java",
                ROOT / "tests/java/e5_stubs/org/telegram/messenger/FileLog.java",
                ROOT / "tests/java/e5_stubs/org/telegram/messenger/plugins/HostExtensionAttribution.java",
                JAVA / "messenger/plugins/PluginResourceRegistry.java",
                JAVA / "messenger/plugins/PluginScope.java",
                JAVA / "messenger/plugins/PluginSession.java",
                JAVA / "messenger/plugins/HostExtensionDescriptor.java",
                JAVA / "messenger/plugins/HostExtensionRegistry.java",
                JAVA / "messenger/plugins/PluginComposerRegistry.java",
                JAVA / "messenger/plugins/LegacyComposerExtensions.java",
                ROOT / "tests/java/ComposerRegistryCheck.java",
                ROOT / "tests/java/LegacyComposerExtensionsCheck.java",
            ]
            subprocess.run(["javac", "--release", "8", "-d", directory, *map(str, sources)],
                           check=True, capture_output=True)
            subprocess.run(["java", "-cp", directory, "org.telegram.messenger.plugins.ComposerRegistryCheck"],
                           check=True, capture_output=True)
            subprocess.run(["java", "-cp", directory,
                            "org.telegram.messenger.plugins.LegacyComposerExtensionsCheck"],
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
                *sorted(API.glob("*.java")),
                ROOT / "tests/java/api_stubs/android/content/Context.java",
                ROOT / "tests/java/api_stubs/android/view/View.java",
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
                           "AndroidUtilities.runOnUIThread", "((ViewGroup) parent).removeView(layer)",
                           "PluginManagedUi.openActivity", "activityScope.openFragment",
                           "fragmentScope.openView", "viewScope.openSurface",
                           "nextScope.register", "scheduleSurfaceCleanup"):
            self.assertIn(required, controller)

        remove_expanded = controller.split("private void removeExpandedLayer()", 1)[1].split(
            "private void removeOverlay()", 1
        )[0]
        self.assertLess(remove_expanded.index("expandedLayer = null"),
                        remove_expanded.index("AndroidUtilities.runOnUIThread"))

        invalidate_cursor = controller.split("public void invalidateCursor()", 1)[1].split(
            "public void cursorOwnerChanged()", 1
        )[0]
        self.assertIn("field.invalidate()", invalidate_cursor)
        self.assertIn("invalidate()", invalidate_cursor)

        draw_cursor = controller.split("public void drawCursor(EditTextCaption source", 1)[1].split(
            "public void selectionChanged", 1
        )[0]
        self.assertIn("source.getOffsetY() - source.getScrollY()", draw_cursor)
        self.assertIn("bounds.top + contentOffsetY", draw_cursor)

        self.assertIn("chatInputViewsContainer, currentAccount", chat)

    def test_settings_do_not_add_default_composer_preview(self):
        registry = (JAVA / "messenger/plugins/PluginComposerRegistry.java").read_text()
        controller = (JAVA / "ui/fluxgram/FluxGramComposerController.java").read_text()
        activity = (JAVA / "ui/PluginSettingsActivity.java").read_text()
        adapter = (JAVA / "messenger/plugins/PythonPluginAdapter.java").read_text()
        surface = (API / "ComposerSurface.java").read_text()

        self.assertNotIn("attachPreview", registry)
        self.assertNotIn("refreshPreview", registry)
        self.assertNotIn("previewPluginId", controller)
        self.assertFalse((JAVA / "ui/fluxgram/PluginComposerPreviewView.java").exists())
        self.assertNotIn("PluginComposerPreviewView", activity)
        self.assertNotIn("FluxGramPluginPreview", activity)
        self.assertIn("default boolean isPreview()", surface)
        self.assertIn("return false;", surface)

        drain = adapter.split("private void drainSettings()", 1)[1].split(
            "public synchronized boolean isStoppingForRemoval()", 1
        )[0]
        self.assertLess(drain.index("javaLoader.settingChanged"), drain.index("dispatch_setting_changed"))
        self.assertNotIn("refreshPreview", drain)

    def test_composer_cluster_capture_reuses_host_adapter(self):
        controller = (JAVA / "ui/fluxgram/FluxGramComposerController.java").read_text()
        capture = (JAVA / "ui/fluxgram/ComposerNativeTextClusterCapture.java").read_text()

        self.assertIn("PluginComposerRegistry.TextClusterCapture,", capture)
        self.assertIn("ComposerInputRendererHostSurface.TextClusterCapture", capture)
        self.assertIn("this::captureTextClusters", controller)
        self.assertNotIn("captureRendererTextClusters", controller)
        self.assertNotIn("new ComposerInputRendererHostSurface.TextClusterCapture", controller)
        self.assertNotIn("pendingLayoutChange", controller)
        self.assertIn("rendererSurface.draw(canvas, legacyDraw)", controller)
        self.assertIn("PluginComposerRegistry.attach(host)", controller)

    def test_composer_registration_uses_ui_cleanup_scope(self):
        adapter = (JAVA / "messenger/plugins/PythonPluginAdapter.java").read_text()
        for method in ("register(", "registerText(", "registerCursor(",
                       "registerSelection(", "registerContent("):
            self.assertIn("uiResources.register(() -> LegacyComposerExtensions." + method, adapter)
        api = (API / "ComposerEffect.java").read_text()
        self.assertIn("android.graphics.Canvas", api)
        self.assertNotIn("org.telegram.ui", api)


if __name__ == "__main__":
    unittest.main()

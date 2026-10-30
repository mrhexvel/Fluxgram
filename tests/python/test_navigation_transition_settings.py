from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
POLICY = JAVA / "messenger/fluxgram/NavigationSettings.java"


class NavigationTransitionSettingsTest(unittest.TestCase):
    def test_policy_normalizes_styles(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("java and javac are required")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            check = root / "NavigationSettingsCheck.java"
            check.write_text(textwrap.dedent("""
                import org.telegram.messenger.fluxgram.NavigationSettings;

                public final class NavigationSettingsCheck {
                    public static void main(String[] args) {
                        require(NavigationSettings.normalizeTransitionStyle(-1) == NavigationSettings.TRANSITION_DEFAULT);
                        require(NavigationSettings.normalizeTransitionStyle(1) == NavigationSettings.TRANSITION_DEFAULT);
                        require(NavigationSettings.normalizeTransitionStyle(2) == NavigationSettings.TRANSITION_SPRING);
                        require(NavigationSettings.normalizeTransitionStyle(3) == NavigationSettings.TRANSITION_DEFAULT);
                    }

                    private static void require(boolean value) {
                        if (!value) {
                            throw new AssertionError();
                        }
                    }
                }
            """))

            subprocess.run(
                ["javac", "-d", str(root), str(POLICY), str(check)],
                check=True,
            )
            subprocess.run(
                ["java", "-cp", str(root), "NavigationSettingsCheck"],
                check=True,
            )

    def test_shared_config_uses_process_global_storage(self):
        shared = (JAVA / "messenger/SharedConfig.java").read_text()

        self.assertIn('"fluxgramNavigationTransitionStyle"', shared)
        self.assertIn('"fluxgramPredictiveBackEnabled"', shared)
        self.assertNotIn('"fluxgramPredictiveBackIntensity"', shared)
        enabled_setter = shared.split("public static boolean setPredictiveBackEnabled", 1)[1].split(
            "public static void toggleInappCamera", 1
        )[0]
        self.assertIn("if (!editor.commit())", enabled_setter)

    def test_policy_is_scoped_to_the_main_phone_layout(self):
        launch = (JAVA / "ui/LaunchActivity.java").read_text()
        setup = launch.split("actionBarLayout = new ActionBarLayout(this, true);", 1)[1].split(
            "frameLayout = new ActivityContentLayout", 1
        )[0]

        self.assertIn("if (!AndroidUtilities.isTablet())", setup)
        self.assertIn("NavigationTransitionPolicy.apply", setup)
        self.assertNotIn("rightActionBarLayout", setup)
        self.assertNotIn("layersActionBarLayout", setup)

    def test_aosp_transition_is_removed(self):
        policy = (JAVA / "ui/fluxgram/NavigationTransitionPolicy.java").read_text()
        layout = (JAVA / "ui/ActionBar/ActionBarLayout.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        self.assertFalse((JAVA / "ui/ActionBar/AospWindowTransition.java").exists())
        self.assertIn("ActionBarLayout.FALLBACK_TRANSITION_SPRING", policy)
        self.assertNotIn("AOSP", policy)
        self.assertNotIn("Aosp", layout)
        self.assertNotIn("TRANSITION_AOSP", activity)

    def test_spring_transition_uses_physics_and_full_window_geometry(self):
        layout = (JAVA / "ui/ActionBar/ActionBarLayout.java").read_text()
        renderer = (JAVA / "ui/ActionBar/SpringWindowTransition.java").read_text()

        self.assertIn("new SpringAnimation(valueHolder)", layout)
        self.assertIn("new SpringForce(SpringWindowTransition.FINAL_VALUE)", layout)
        self.assertIn("prepareSpringTransition(true)", layout)
        self.assertIn("prepareSpringTransition(false)", layout)
        self.assertIn("springTransitionBackground", layout)
        self.assertIn("springTransitionOpen,\n                            springTransitionProgress", layout)
        self.assertIn("springTransitionProgress = animationProgress", layout)
        self.assertIn("clearSpringTransition()", layout)
        self.assertIn("if (!springTransitionActive && (translationX != 0", layout)
        self.assertGreaterEqual(layout.count("if (springTransitionActive) {"), 3)
        self.assertIn("STIFFNESS = 900.0f", renderer)
        self.assertIn("DAMPING_RATIO = 1.0f", renderer)
        self.assertIn("AndroidUtilities.lerp(0.85f, 1.0f", renderer)
        self.assertIn("clampedProgress * 0.35f * width", renderer)
        self.assertIn("open ? clampedProgress : 1.0f - clampedProgress", renderer)
        self.assertNotIn("setAlpha(clampedProgress)", renderer)

    def test_fallback_transition_preserves_preview_and_custom_slides(self):
        layout = (JAVA / "ui/ActionBar/ActionBarLayout.java").read_text()
        programmatic = layout.split("private void startLayoutAnimation", 1)[1].split(
            "public void resumeDelayedFragmentAnimation", 1
        )[0]
        back = layout.split("private void animateBackEndAnimation", 1)[1].split(
            "public void onBackPressed()", 1
        )[0]

        self.assertIn("if (preview)", programmatic)
        self.assertIn("fallbackTransitionDuration", programmatic)
        self.assertIn("fallbackTransitionInterpolator", programmatic)
        self.assertIn("hasCustomTransition", back)
        self.assertIn("!overrideTransition && !hasCustomTransition", back)

    def test_predictive_callback_mode_is_startup_fixed_and_uses_platform_progress(self):
        launch = (JAVA / "ui/LaunchActivity.java").read_text()
        registration = launch.split(
            "if (Build.VERSION.SDK_INT >= 34 && SharedConfig.predictiveBackEnabled)", 1
        )[1].split(
            "checkFrameMetrics();", 1
        )[0]
        teardown = launch.split("protected void onDestroy()", 1)[1].split(
            "Bulletin.removeDelegate", 1
        )[0]

        self.assertIn(
            "Build.VERSION.SDK_INT >= 34 && SharedConfig.predictiveBackEnabled",
            launch,
        )
        self.assertIn("actionBarLayout.onBackProgress(fixedProgress)", registration)
        self.assertNotIn("predictiveBackIntensity", registration)
        self.assertIn("onBackAnimationCallback instanceof OnBackAnimationCallback", teardown)
        self.assertIn("onBackInvokedCallback instanceof OnBackInvokedCallback", teardown)

    def test_navigation_screen_applies_style_live_and_restarts_callback_mode(self):
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        self.assertIn("NavigationTransitionPolicy.apply(parentLayout, style)", activity)
        live_style = activity.split("SharedConfig.setNavigationTransitionStyle(style)", 1)[1].split(
            "listView.adapter.update(true)", 1
        )[0]
        self.assertIn("if (!AndroidUtilities.isTablet())", live_style)
        self.assertIn("confirmPredictiveBackChange(!SharedConfig.predictiveBackEnabled)", activity)
        self.assertIn("SharedConfig.setPredictiveBackEnabled(enabled)", activity)
        self.assertNotIn("predictiveBackIntensity", activity)
        self.assertFalse((JAVA / "ui/Components/FluxGramPredictiveBackIntensityCell.java").exists())


if __name__ == "__main__":
    unittest.main()

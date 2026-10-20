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
    def test_policy_normalizes_styles_and_predictive_progress(self):
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
                        require(NavigationSettings.normalizeTransitionStyle(3) == NavigationSettings.TRANSITION_DEFAULT);
                        require(NavigationSettings.normalizeTransitionStyle(1) == NavigationSettings.TRANSITION_AOSP);
                        require(NavigationSettings.normalizePredictiveBackIntensity(0) == 25);
                        require(NavigationSettings.normalizePredictiveBackIntensity(120) == 100);
                        require(close(NavigationSettings.scalePredictiveBackProgress(0.8f, 50), 0.4f));
                        require(close(NavigationSettings.scalePredictiveBackProgress(2.0f, 100), 1.0f));
                    }

                    private static boolean close(float first, float second) {
                        return Math.abs(first - second) < 0.0001f;
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
        self.assertIn('"fluxgramPredictiveBackIntensity"', shared)
        enabled_setter = shared.split("public static boolean setPredictiveBackEnabled", 1)[1].split(
            "public static void setPredictiveBackIntensity", 1
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

    def test_predictive_callback_mode_is_startup_fixed_and_intensity_is_live(self):
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
        self.assertIn("NavigationSettings.scalePredictiveBackProgress", registration)
        self.assertIn("SharedConfig.predictiveBackIntensity", registration)
        self.assertIn("onBackAnimationCallback instanceof OnBackAnimationCallback", teardown)
        self.assertIn("onBackInvokedCallback instanceof OnBackInvokedCallback", teardown)

    def test_navigation_screen_applies_style_live_and_restarts_callback_mode(self):
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        self.assertIn("NavigationTransitionPolicy.apply(parentLayout, index)", activity)
        live_style = activity.split("SharedConfig.setNavigationTransitionStyle(index)", 1)[1].split(
            "listView.adapter.update(true)", 1
        )[0]
        self.assertIn("if (!AndroidUtilities.isTablet())", live_style)
        self.assertIn("confirmPredictiveBackChange(!SharedConfig.predictiveBackEnabled)", activity)
        self.assertIn("SharedConfig.setPredictiveBackEnabled(enabled)", activity)
        self.assertIn("SharedConfig.setPredictiveBackIntensity(intensity)", (
            JAVA / "ui/Components/FluxGramPredictiveBackIntensityCell.java"
        ).read_text())


if __name__ == "__main__":
    unittest.main()

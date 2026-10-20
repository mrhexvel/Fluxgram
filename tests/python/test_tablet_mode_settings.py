from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
POLICY = JAVA / "messenger/fluxgram/TabletModeSettings.java"


class TabletModeSettingsTest(unittest.TestCase):
    def test_policy_resolves_auto_on_and_off(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("java and javac are required")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            check = root / "TabletModeSettingsCheck.java"
            check.write_text(textwrap.dedent("""
                import org.telegram.messenger.fluxgram.TabletModeSettings;

                public final class TabletModeSettingsCheck {
                    public static void main(String[] args) {
                        require(!TabletModeSettings.resolve(TabletModeSettings.AUTO, false));
                        require(TabletModeSettings.resolve(TabletModeSettings.AUTO, true));
                        require(TabletModeSettings.resolve(TabletModeSettings.ON, false));
                        require(!TabletModeSettings.resolve(TabletModeSettings.OFF, true));
                        require(TabletModeSettings.normalize(-1) == TabletModeSettings.AUTO);
                        require(TabletModeSettings.normalize(3) == TabletModeSettings.AUTO);
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
                ["java", "-cp", str(root), "TabletModeSettingsCheck"],
                check=True,
            )

    def test_shared_config_loads_policy_before_tablet_dependent_defaults(self):
        shared = (JAVA / "messenger/SharedConfig.java").read_text()
        load = shared.split(
            'preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig"',
            1,
        )[1]

        self.assertLess(
            load.index('preferences.getInt("fluxgramTabletMode", legacyTabletMode)'),
            load.index('preferences.getInt("fons_size", AndroidUtilities.isTablet()'),
        )
        self.assertIn('preferences.getBoolean("forceDisableTabletMode", false)', load)
        self.assertIn('editor.putInt("fluxgramTabletMode", normalizedMode)', shared)
        self.assertIn("if (!editor.commit())", shared)

    def test_effective_tablet_state_and_configuration_history_use_policy(self):
        utilities = (JAVA / "messenger/AndroidUtilities.java").read_text()

        self.assertIn(
            "TabletModeSettings.resolve(SharedConfig.tabletMode, isTabletInternal())",
            utilities,
        )
        reset = utilities.split("public static void resetTabletFlag()", 1)[1].split(
            "public static void resetWasTabletFlag()", 1
        )[0]
        self.assertIn("wasTablet = isTablet();", reset)
        self.assertNotIn("wasTablet = isTabletInternal();", reset)

    def test_navigation_screen_requires_confirmed_restart(self):
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        self.assertIn("new FluxGramSettingsActivity(NAVIGATION)", activity)
        self.assertIn("tabletModeOption(SharedConfig.tabletMode)", activity)
        self.assertIn("confirmTabletModeChange(index);", activity)
        self.assertIn("SharedConfig.setTabletMode(mode)", activity)
        self.assertIn("Intent.FLAG_ACTIVITY_CLEAR_TASK", activity)
        self.assertIn("System.exit(0);", activity)


if __name__ == "__main__":
    unittest.main()

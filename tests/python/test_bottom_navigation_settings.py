from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
POLICY = JAVA / "messenger/fluxgram/BottomNavigationSettings.java"


class BottomNavigationSettingsTest(unittest.TestCase):
    def test_policy_normalizes_and_distinguishes_all_modes(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("java and javac are required")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            check = root / "BottomNavigationSettingsCheck.java"
            check.write_text(textwrap.dedent("""
                import org.telegram.messenger.fluxgram.BottomNavigationSettings;

                public final class BottomNavigationSettingsCheck {
                    public static void main(String[] args) {
                        require(BottomNavigationSettings.normalize(-1) == BottomNavigationSettings.FLOATING);
                        require(BottomNavigationSettings.normalize(3) == BottomNavigationSettings.FLOATING);
                        require(BottomNavigationSettings.isVisible(BottomNavigationSettings.FLOATING));
                        require(BottomNavigationSettings.isVisible(BottomNavigationSettings.SHOW));
                        require(!BottomNavigationSettings.isVisible(BottomNavigationSettings.HIDE));
                        require(BottomNavigationSettings.followsContentVisibility(BottomNavigationSettings.FLOATING));
                        require(!BottomNavigationSettings.followsContentVisibility(BottomNavigationSettings.SHOW));
                        require(!BottomNavigationSettings.followsContentVisibility(BottomNavigationSettings.HIDE));
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
                ["java", "-cp", str(root), "BottomNavigationSettingsCheck"],
                check=True,
            )

    def test_shared_config_uses_process_global_synchronous_storage(self):
        shared = (JAVA / "messenger/SharedConfig.java").read_text()

        self.assertIn(
            'preferences.getInt("fluxgramBottomNavigationMode", BottomNavigationSettings.FLOATING)',
            shared,
        )
        setter = shared.split("public static boolean setBottomNavigationMode", 1)[1].split(
            "public static void toggleInappCamera", 1
        )[0]
        self.assertIn('editor.putInt("fluxgramBottomNavigationMode", normalizedMode)', setter)
        self.assertIn("if (!editor.commit())", setter)
        self.assertIn("bottomNavigationMode = normalizedMode;", setter)

    def test_hidden_mode_removes_tabs_and_neighbor_pages(self):
        activity = (JAVA / "ui/MainTabsActivity.java").read_text()

        create_view = activity.split("public View createView(Context context)", 1)[1].split(
            "private void setupUpdateLayout", 1
        )[0]
        self.assertLess(
            create_view.index("if (!hasBottomNavigation())"),
            create_view.index("tabsView = new MainTabsLayout"),
        )
        self.assertIn("return hasBottomNavigation() ? TABS_COUNT : 1;", activity)
        self.assertIn('bundle.putBoolean("hasMainTabs", hasBottomNavigation());', activity)
        self.assertIn("if (!hasBottomNavigation()) {\n            return false;", activity)
        self.assertIn("if (tabsView != null) {", activity)

    def test_show_and_floating_modes_have_distinct_visibility_contracts(self):
        activity = (JAVA / "ui/MainTabsActivity.java").read_text()
        controller = activity.split(
            "private class MainTabsActivityControllerImpl", 1
        )[1].split("/* Slide */", 1)[0]

        self.assertIn(
            "BottomNavigationSettings.followsContentVisibility(bottomNavigationMode)",
            controller,
        )
        self.assertIn("visible = true;", controller)

        dialogs = (JAVA / "ui/DialogsActivity.java").read_text()
        search_padding = dialogs.split(
            "private void checkUi_searchPagesPaddings", 1
        )[1].split("private float getFilterTabsVisibilityFactor", 1)[0]
        self.assertIn("persistentMainTabs", search_padding)
        self.assertIn("additionNavigationBarHeight", search_padding)

    def test_floating_mode_uses_the_existing_fab_scroll_state(self):
        dialogs = (JAVA / "ui/DialogsActivity.java").read_text()
        hide_fab = dialogs.split("private void hideFloatingButton(boolean hide)", 1)[1].split(
            "public float getContactsAlpha", 1
        )[0]
        main_tabs = dialogs.split("private void checkUi_mainTabsVisible()", 1)[1].split(
            "private void checkUi_searchFieldVisibility", 1
        )[0]

        self.assertIn("floatingButtonHidden = hide;", hide_fab)
        self.assertIn("checkUi_mainTabsVisible();", hide_fab)
        self.assertIn("!floatingButtonHidden", main_tabs)

    def test_hidden_mode_keeps_accounts_and_settings_reachable(self):
        dialogs = (JAVA / "ui/DialogsActivity.java").read_text()
        options = dialogs.split("private void showItemOptions()", 1)[1].split(
            "if (proxyMenuSubItem != null)", 1
        )[0]

        self.assertIn("hiddenBottomNavigation && mainTabsActivityController != null", options)
        self.assertIn("R.string.SettingsAccounts", options)
        self.assertIn("mainTabsActivityController.openAccountSelector(optionsItem)", options)
        self.assertIn("getUserConfig().showCallsTab || hiddenBottomNavigation", options)
        self.assertIn("presentFragment(new SettingsActivity())", options)

    def test_navigation_screen_requires_confirmed_restart(self):
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        self.assertIn("bottomNavigationOption(SharedConfig.bottomNavigationMode)", activity)
        self.assertIn("confirmBottomNavigationChange(index);", activity)
        self.assertIn("SharedConfig.setBottomNavigationMode(mode)", activity)
        self.assertIn("restartWithBottomNavigation(mode)", activity)


if __name__ == "__main__":
    unittest.main()

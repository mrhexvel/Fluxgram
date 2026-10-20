from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
BOTTOM_NAVIGATION = JAVA / "messenger/fluxgram/BottomNavigationSettings.java"
SNAPSHOT = JAVA / "messenger/fluxgram/RootNavigationSnapshot.java"


class RootNavigationContractTest(unittest.TestCase):
    def test_snapshot_captures_topology_and_destination_rules(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("java and javac are required")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            check = root / "RootNavigationSnapshotCheck.java"
            check.write_text(textwrap.dedent("""
                import org.telegram.messenger.fluxgram.BottomNavigationSettings;
                import org.telegram.messenger.fluxgram.RootNavigationSnapshot;

                public final class RootNavigationSnapshotCheck {
                    public static void main(String[] args) {
                        RootNavigationSnapshot settings = RootNavigationSnapshot.create(
                                1,
                                20,
                                BottomNavigationSettings.FLOATING,
                                false,
                                RootNavigationSnapshot.SETTINGS
                        );
                        require(settings.getAccount() == 1);
                        require(settings.getRootIdentity() == 20);
                        require(settings.isBottomNavigationVisible());
                        require(!settings.isCallsTabVisible());
                        require(settings.getActiveDestination().equals(RootNavigationSnapshot.SETTINGS));
                        require(settings.canSelectInRoot(RootNavigationSnapshot.CONTACTS));
                        require(settings.canSelectInRoot(RootNavigationSnapshot.SETTINGS));
                        require(!settings.canSelectInRoot(RootNavigationSnapshot.CALLS));
                        require(settings.hasDestination(RootNavigationSnapshot.CALLS));

                        RootNavigationSnapshot calls = RootNavigationSnapshot.create(
                                1,
                                20,
                                BottomNavigationSettings.SHOW,
                                true,
                                RootNavigationSnapshot.CALLS
                        );
                        require(calls.canSelectInRoot(RootNavigationSnapshot.CALLS));
                        require(!calls.canSelectInRoot(RootNavigationSnapshot.SETTINGS));
                        require(!settings.matches(calls));

                        RootNavigationSnapshot hidden = RootNavigationSnapshot.create(
                                1,
                                20,
                                BottomNavigationSettings.HIDE,
                                true,
                                RootNavigationSnapshot.CONTACTS
                        );
                        require(!hidden.isBottomNavigationVisible());
                        require(hidden.getActiveDestination().equals(RootNavigationSnapshot.CONTACTS));
                        require(hidden.canSelectInRoot(RootNavigationSnapshot.CHATS));
                        require(!hidden.canSelectInRoot(RootNavigationSnapshot.CONTACTS));

                        RootNavigationSnapshot copy = RootNavigationSnapshot.create(
                                1,
                                20,
                                BottomNavigationSettings.HIDE,
                                true,
                                RootNavigationSnapshot.CONTACTS
                        );
                        require(hidden.matches(copy));
                        require(hidden.hashCode() == copy.hashCode());
                        require(!hidden.matches(RootNavigationSnapshot.create(
                                1,
                                21,
                                BottomNavigationSettings.HIDE,
                                true,
                                RootNavigationSnapshot.CHATS
                        )));
                    }

                    private static void require(boolean value) {
                        if (!value) {
                            throw new AssertionError();
                        }
                    }
                }
            """))

            subprocess.run(
                [
                    "javac",
                    "-d",
                    str(root),
                    str(BOTTOM_NAVIGATION),
                    str(SNAPSHOT),
                    str(check),
                ],
                check=True,
            )
            subprocess.run(
                ["java", "-cp", str(root), "RootNavigationSnapshotCheck"],
                check=True,
            )

    def test_controller_exposes_snapshot_bound_semantic_navigation(self):
        controller = (JAVA / "ui/MainTabsActivityController.java").read_text()

        self.assertIn("RootNavigationSnapshot getRootNavigationSnapshot()", controller)
        self.assertIn("navigateToRootDestination", controller)
        self.assertIn("RootNavigationSnapshot snapshot", controller)
        self.assertNotIn("int position", controller)

    def test_main_tabs_rejects_stale_snapshots_and_owns_raw_positions(self):
        activity = (JAVA / "ui/MainTabsActivity.java").read_text()
        navigation = activity.split("private boolean navigateToRootDestination", 1)[1].split(
            "private int positionForDestination", 1
        )[0]

        self.assertIn("!currentSnapshot.matches(snapshot)", navigation)
        self.assertIn("currentSnapshot == null", navigation)
        self.assertIn("!currentSnapshot.hasDestination(destination)", navigation)
        self.assertIn("currentSnapshot.canSelectInRoot(destination)", navigation)
        self.assertIn("viewPager.scrollToPosition(positionForDestination(destination))", navigation)

    def test_standalone_fallback_reuses_fragment_routing(self):
        activity = (JAVA / "ui/MainTabsActivity.java").read_text()
        navigation = activity.split("private boolean navigateToRootDestination", 1)[1].split(
            "private int positionForDestination", 1
        )[0]
        standalone = activity.split("private BaseFragment createStandaloneDestination", 1)[1].split(
            "public GlassTabView[] tabs", 1
        )[0]

        self.assertIn("getParentLayout().checkTransitionAnimation()", navigation)
        self.assertIn("boolean presented = presentFragment(fragment);", navigation)
        self.assertIn("presented || fragment.getParentLayout() != null", navigation)
        self.assertIn('args.putBoolean("hasMainTabs", false)', standalone)
        self.assertIn("new ContactsActivity(args)", standalone)
        self.assertIn("new CallLogActivity(args)", standalone)
        self.assertIn("new SettingsActivity(args)", standalone)
        self.assertIn("new ProfileActivity(args)", standalone)

    def test_snapshot_is_fail_closed_during_detach_and_calls_tab_replacement(self):
        activity = (JAVA / "ui/MainTabsActivity.java").read_text()
        snapshot = activity.split("private RootNavigationSnapshot getRootNavigationSnapshot", 1)[1].split(
            "private String destinationForPosition", 1
        )[0]
        stability = activity.split("private boolean hasStableCallsOrSettingsPage", 1)[1].split(
            "private boolean navigateToRootDestination", 1
        )[0]

        self.assertIn("fragmentView == null", snapshot)
        self.assertIn("!fragmentView.isAttachedToWindow()", snapshot)
        self.assertIn("viewPager == null", snapshot)
        self.assertIn("getParentLayout() == null", snapshot)
        self.assertIn("!hasStableCallsOrSettingsPage()", snapshot)
        self.assertIn("dropCallsFragmentAfterPageScroll", stability)
        self.assertIn("state.fragment instanceof CallLogActivity", stability)
        self.assertIn("state.fragment instanceof SettingsActivity", stability)

    def test_existing_popup_is_not_migrated_in_n4_2(self):
        dialogs = (JAVA / "ui/DialogsActivity.java").read_text()
        popup = dialogs.split("private void showItemOptions()", 1)[1].split(
            "if (proxyMenuSubItem != null)", 1
        )[0]

        self.assertNotIn("navigateToRootDestination", popup)
        self.assertNotIn("MainMenuPolicy", popup)


if __name__ == "__main__":
    unittest.main()

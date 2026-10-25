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

    def test_root_pager_shared_axis_transform_is_opt_in_and_bounded(self):
        pager = (JAVA / "ui/ViewPagerActivity.java").read_text()
        activity = (JAVA / "ui/MainTabsActivity.java").read_text()
        transform = activity.split("protected void onViewPagerPageTransform", 1)[1].split(
            "protected int getFragmentsCount", 1
        )[0]

        self.assertIn("protected void onViewPagerPageTransform", pager)
        self.assertIn("boolean hasWidth = getAvailableTranslationX() > 0f", pager)
        self.assertIn("hasWidth ? getCurrentPositionAlpha() : 1f", pager)
        self.assertIn("hasWidth ? getNextPositionAlpha() : 0f", pager)
        self.assertIn("getCurrentPositionAlpha()", pager)
        self.assertIn("getNextPositionAlpha()", pager)
        self.assertIn("SharedConfig.animationsEnabled()", transform)
        self.assertIn("!LiteMode.isPowerSaverApplied()", transform)
        self.assertIn("MathUtils.clamp(visibility, 0f, 1f)", transform)
        self.assertIn("lerp(0.72f, 1f, progress)", transform)
        self.assertIn("lerp(0.96f, 1f, progress)", transform)
        self.assertIn("page.setAlpha(alpha)", transform)
        self.assertIn("page.setScaleX(scale)", transform)
        self.assertIn("page.setScaleY(scale)", transform)
        self.assertNotIn("tabsView.setAlpha", transform)
        self.assertNotIn("tabsView.setScale", transform)
        self.assertIn("return 240L;", pager)

    def test_settings_blur_sources_are_recorded_only_when_invalidated(self):
        settings = (JAVA / "ui/SettingsActivity.java").read_text()
        draw = settings.split("protected void dispatchDraw(Canvas canvas)", 1)[1].split(
            "super.dispatchDraw(canvas)", 1
        )[0]
        colors = settings.split("public void updateColors()", 1)[1].split(
            "private boolean actionBarVisible", 1
        )[0]

        self.assertIn("iBlur3SourceGlassFrosted.needUpdateDisplayList(width, height) || iBlur3Invalidated", draw)
        self.assertIn("iBlur3SourceGlass.needUpdateDisplayList(width, height) || iBlur3Invalidated", draw)
        self.assertIn("private boolean iBlur3Invalidated = true;", settings)
        self.assertIn("iBlur3Invalidated = true;", colors)

    def test_root_glass_capture_uses_the_same_page_transform(self):
        activity = (JAVA / "ui/MainTabsActivity.java").read_text()
        glass = activity.split("iBlur3SourceTabGlass.setupRenderer", 1)[1].split(
            "iBlur3SourceColor = new BlurredBackgroundSourceColor", 1
        )[0]
        draw = activity.split("private void drawRootPageGlassSource", 1)[1].split(
            "protected FrameLayout createContentView", 1
        )[0]

        self.assertIn("View rootPage = getRootPage(fragment)", glass)
        self.assertIn("rootPage.getAlpha()", glass)
        self.assertIn("rootPage.getScaleX()", glass)
        self.assertIn("rootPage.getScaleY()", glass)
        self.assertIn("drawRootPageGlassSource(canvas, fragment, source, width, height)", glass)
        self.assertIn("canvas.scale(rootPage.getScaleX(), rootPage.getScaleY())", draw)
        self.assertIn("canvas.saveLayerAlpha(0, 0, width, height, alpha)", draw)

    def test_root_popup_uses_policy_and_snapshot_navigation(self):
        dialogs = (JAVA / "ui/DialogsActivity.java").read_text()
        popup = dialogs.split("private void showItemOptions()", 1)[1].split(
            "final boolean isCurrentThemeDark", 1
        )[0]
        policy = dialogs.split("private void addRootMainMenuOptions", 1)[1].split(
            "private boolean isRootMainMenuContributorAnchor", 1
        )[0]
        command = dialogs.split("private void addRootMainMenuCommand", 1)[1].split(
            "private void addRootMainMenuTheme", 1
        )[0]

        self.assertIn("usesFluxGramRootDialogsAppearance()", popup)
        self.assertIn("getRootNavigationSnapshot()", popup)
        self.assertIn("snapshot.getAccount() != currentAccount", popup)
        self.assertIn("RootNavigationSnapshot.CHATS.equals(snapshot.getActiveDestination())", popup)
        self.assertIn("addRootMainMenuOptions", popup)
        self.assertIn("MainMenuPolicy.resolve", policy)
        self.assertIn("MainMenuSettings.getState()", policy)
        self.assertIn("snapshot.canSelectInRoot(RootNavigationSnapshot.SETTINGS)", policy)
        self.assertIn("snapshot.canSelectInRoot(RootNavigationSnapshot.PROFILE)", policy)
        self.assertIn("controller.navigateToRootDestination(snapshot, RootNavigationSnapshot.CONTACTS)", command)
        self.assertIn("controller.navigateToRootDestination(snapshot, RootNavigationSnapshot.CALLS)", command)
        self.assertIn("controller.navigateToRootDestination(snapshot, RootNavigationSnapshot.SETTINGS)", command)
        self.assertNotIn("presentFragment(new SettingsActivity", command)

    def test_root_popup_preserves_dynamic_contributor_slot(self):
        dialogs = (JAVA / "ui/DialogsActivity.java").read_text()
        policy = dialogs.split("private void addRootMainMenuOptions", 1)[1].split(
            "private boolean isRootMainMenuContributorAnchor", 1
        )[0]
        anchor = dialogs.split("private boolean isRootMainMenuContributorAnchor", 1)[1].split(
            "private void addRootMainMenuCommand", 1
        )[0]
        contributors = dialogs.split("private void addRootMainMenuContributors", 1)[1].split(
            "private boolean isRootMainMenuProxyAvailable", 1
        )[0]

        self.assertIn("isRootMainMenuContributorAnchor(id)", policy)
        self.assertIn("addRootMainMenuContributors", policy)
        self.assertIn("MainMenuPolicy.WALLET.equals(id)", anchor)
        self.assertIn("applicationLoaderInstance.addItemOptions(io)", contributors)
        self.assertIn("if (walletAvailable)", contributors)
        self.assertIn("io.addBot", contributors)
        self.assertIn("BotWebViewSheet.deleteBot", contributors)


if __name__ == "__main__":
    unittest.main()

from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
POLICY = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/fluxgram/MainMenuPolicy.java"
ROOT_NAVIGATION = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/fluxgram/RootNavigationSnapshot.java"
BOTTOM_NAVIGATION = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/fluxgram/BottomNavigationSettings.java"


class MainMenuPolicyTest(unittest.TestCase):
    def test_policy_normalizes_and_resolves_root_menu(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("java and javac are required")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            check = root / "MainMenuPolicyCheck.java"
            check.write_text(textwrap.dedent("""
                import java.util.Arrays;
                import java.util.HashSet;
                import java.util.List;
                import java.util.Set;
                import org.telegram.messenger.fluxgram.MainMenuPolicy;

                public final class MainMenuPolicyCheck {
                    public static void main(String[] args) {
                        checkStableIds();
                        checkNormalization();
                        checkDefaultTopology();
                        checkReachability();
                        checkDividers();
                    }

                    private static void checkStableIds() {
                        List<String> ids = MainMenuPolicy.knownIds();
                        require(ids.size() == new HashSet<>(ids).size());
                        require(ids.contains(MainMenuPolicy.SETTINGS));
                        require(ids.contains(MainMenuPolicy.DIVIDER_PRIMARY));
                        require(MainMenuPolicy.isDivider(MainMenuPolicy.DIVIDER_SYSTEM));
                        require(!MainMenuPolicy.isDivider(MainMenuPolicy.PROXY));
                    }

                    private static void checkNormalization() {
                        MainMenuPolicy.State state = MainMenuPolicy.normalize(
                                Arrays.asList("unknown", MainMenuPolicy.PROXY, MainMenuPolicy.PROXY),
                                Arrays.asList("unknown", MainMenuPolicy.DIVIDER_PRIMARY, MainMenuPolicy.WALLET)
                        );
                        require(state.getOrder().get(0).equals(MainMenuPolicy.PROXY));
                        require(state.getOrder().size() == MainMenuPolicy.knownIds().size());
                        require(state.getHidden().size() == 1);
                        require(state.getHidden().contains(MainMenuPolicy.WALLET));
                    }

                    private static void checkDefaultTopology() {
                        List<String> menu = MainMenuPolicy.resolve(
                                MainMenuPolicy.defaults(),
                                allCommands(),
                                true,
                                true
                        );
                        require(menu.equals(Arrays.asList(
                                MainMenuPolicy.THEME,
                                MainMenuPolicy.DIVIDER_PRIMARY,
                                MainMenuPolicy.NEW_GROUP,
                                MainMenuPolicy.SAVED_MESSAGES,
                                MainMenuPolicy.WALLET,
                                MainMenuPolicy.DIVIDER_SYSTEM,
                                MainMenuPolicy.PROXY
                        )));
                    }

                    private static void checkReachability() {
                        MainMenuPolicy.State hidden = MainMenuPolicy.normalize(
                                MainMenuPolicy.knownIds(),
                                Arrays.asList(MainMenuPolicy.SETTINGS, MainMenuPolicy.ACCOUNTS)
                        );
                        List<String> menu = MainMenuPolicy.resolve(hidden, allCommands(), false, false);
                        require(menu.contains(MainMenuPolicy.SETTINGS));
                        require(menu.contains(MainMenuPolicy.ACCOUNTS));

                        menu = MainMenuPolicy.resolve(hidden, allCommands(), true, true);
                        require(!menu.contains(MainMenuPolicy.SETTINGS));
                        require(!menu.contains(MainMenuPolicy.ACCOUNTS));

                        MainMenuPolicy.State visible = MainMenuPolicy.normalize(
                                MainMenuPolicy.knownIds(),
                                Arrays.asList()
                        );
                        menu = MainMenuPolicy.resolve(visible, allCommands(), true, true);
                        require(menu.contains(MainMenuPolicy.SETTINGS));
                        require(menu.contains(MainMenuPolicy.ACCOUNTS));

                        requireThrows(() -> MainMenuPolicy.resolve(
                                hidden,
                                new HashSet<>(),
                                false,
                                false
                        ));
                        require(MainMenuPolicy.resolve(
                                hidden,
                                null,
                                true,
                                true
                        ).isEmpty());
                    }

                    private static void checkDividers() {
                        MainMenuPolicy.State state = MainMenuPolicy.normalize(
                                Arrays.asList(
                                        MainMenuPolicy.DIVIDER_PRIMARY,
                                        MainMenuPolicy.NEW_GROUP,
                                        MainMenuPolicy.DIVIDER_SYSTEM,
                                        MainMenuPolicy.PROXY
                                ),
                                Arrays.asList(MainMenuPolicy.NEW_GROUP, MainMenuPolicy.PROXY)
                        );
                        List<String> menu = MainMenuPolicy.resolve(state, allCommands(), true, true);
                        require(!menu.isEmpty());
                        require(!MainMenuPolicy.isDivider(menu.get(0)));
                        require(!MainMenuPolicy.isDivider(menu.get(menu.size() - 1)));

                        for (int index = 1; index < menu.size(); index++) {
                            require(!(MainMenuPolicy.isDivider(menu.get(index - 1))
                                    && MainMenuPolicy.isDivider(menu.get(index))));
                        }

                        MainMenuPolicy.State collapsed = MainMenuPolicy.normalize(
                                Arrays.asList(
                                        MainMenuPolicy.THEME,
                                        MainMenuPolicy.DIVIDER_PRIMARY,
                                        MainMenuPolicy.DIVIDER_SYSTEM,
                                        MainMenuPolicy.PROXY
                                ),
                                Arrays.asList(
                                        MainMenuPolicy.NEW_GROUP,
                                        MainMenuPolicy.NEW_CHANNEL,
                                        MainMenuPolicy.SAVED_MESSAGES,
                                        MainMenuPolicy.CONTACTS,
                                        MainMenuPolicy.CALLS,
                                        MainMenuPolicy.WALLET,
                                        MainMenuPolicy.ACCOUNTS,
                                        MainMenuPolicy.SETTINGS
                                )
                        );
                        menu = MainMenuPolicy.resolve(collapsed, allCommands(), true, true);
                        require(menu.equals(Arrays.asList(
                                MainMenuPolicy.THEME,
                                MainMenuPolicy.DIVIDER_PRIMARY,
                                MainMenuPolicy.PROXY
                        )));
                    }

                    private static Set<String> allCommands() {
                        Set<String> commands = new HashSet<>(MainMenuPolicy.knownIds());
                        commands.remove(MainMenuPolicy.DIVIDER_PRIMARY);
                        commands.remove(MainMenuPolicy.DIVIDER_SYSTEM);
                        return commands;
                    }

                    private static void requireThrows(Runnable runnable) {
                        try {
                            runnable.run();
                        } catch (IllegalArgumentException expected) {
                            return;
                        }

                        throw new AssertionError();
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
                    str(ROOT_NAVIGATION),
                    str(POLICY),
                    str(check),
                ],
                check=True,
            )
            subprocess.run(
                ["java", "-cp", str(root), "MainMenuPolicyCheck"],
                check=True,
            )

    def test_policy_has_no_android_or_legacy_drawer_dependency(self):
        source = POLICY.read_text()

        self.assertNotIn("android.", source)
        self.assertNotIn("DrawerLayoutContainer", source)
        self.assertNotIn("ItemOptions", source)


if __name__ == "__main__":
    unittest.main()

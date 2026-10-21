from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
BOTTOM_NAVIGATION = JAVA / "messenger/fluxgram/BottomNavigationSettings.java"
ROOT_NAVIGATION = JAVA / "messenger/fluxgram/RootNavigationSnapshot.java"
POLICY = JAVA / "messenger/fluxgram/MainMenuPolicy.java"
SETTINGS = JAVA / "messenger/fluxgram/MainMenuSettings.java"


class MainMenuSettingsTest(unittest.TestCase):
    def test_storage_round_trip_normalization_and_reset(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("java and javac are required")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shared_preferences = root / "android/content/SharedPreferences.java"
            messages_controller = root / "org/telegram/messenger/MessagesController.java"
            check = root / "MainMenuSettingsCheck.java"
            shared_preferences.parent.mkdir(parents=True)
            messages_controller.parent.mkdir(parents=True)

            shared_preferences.write_text(textwrap.dedent("""
                package android.content;

                public interface SharedPreferences {
                    boolean contains(String key);
                    String getString(String key, String fallback);
                    Editor edit();

                    interface Editor {
                        Editor putString(String key, String value);
                        Editor remove(String key);
                        void apply();
                    }
                }
            """))
            messages_controller.write_text(textwrap.dedent("""
                package org.telegram.messenger;

                import android.content.SharedPreferences;
                import java.util.HashMap;
                import java.util.Map;

                public final class MessagesController {
                    public static final MemoryPreferences preferences = new MemoryPreferences();

                    public static SharedPreferences getGlobalMainSettings() {
                        return preferences;
                    }

                    public static final class MemoryPreferences implements SharedPreferences {
                        private final Map<String, Object> values = new HashMap<>();

                        public boolean contains(String key) {
                            return values.containsKey(key);
                        }

                        public String getString(String key, String fallback) {
                            return values.containsKey(key) ? (String) values.get(key) : fallback;
                        }

                        public void putRaw(String key, Object value) {
                            values.put(key, value);
                        }

                        public Editor edit() {
                            return new MemoryEditor();
                        }

                        private final class MemoryEditor implements Editor {
                            private final Map<String, String> writes = new HashMap<>();
                            private final java.util.Set<String> removals = new java.util.HashSet<>();

                            public Editor putString(String key, String value) {
                                writes.put(key, value);
                                removals.remove(key);
                                return this;
                            }

                            public Editor remove(String key) {
                                removals.add(key);
                                writes.remove(key);
                                return this;
                            }

                            public void apply() {
                                for (String key : removals) {
                                    values.remove(key);
                                }
                                values.putAll(writes);
                            }
                        }
                    }
                }
            """))
            check.write_text(textwrap.dedent("""
                import java.util.ArrayList;
                import java.util.Collections;
                import org.telegram.messenger.MessagesController;
                import org.telegram.messenger.fluxgram.MainMenuPolicy;
                import org.telegram.messenger.fluxgram.MainMenuSettings;

                public final class MainMenuSettingsCheck {
                    public static void main(String[] args) {
                        require(MainMenuSettings.getState().getOrder().equals(MainMenuPolicy.defaults().getOrder()));
                        require(MainMenuSettings.getState().getHidden().equals(MainMenuPolicy.defaults().getHidden()));

                        MainMenuSettings.setCommandVisible(MainMenuPolicy.NEW_CHANNEL, true);
                        require(!MainMenuSettings.getState().getHidden().contains(MainMenuPolicy.NEW_CHANNEL));

                        ArrayList<String> reverse = new ArrayList<>(MainMenuPolicy.knownIds());
                        Collections.reverse(reverse);
                        MainMenuSettings.setOrder(reverse);
                        require(MainMenuSettings.getState().getOrder().equals(reverse));

                        MainMenuSettings.setCommandVisible(MainMenuPolicy.THEME, false);
                        require(MainMenuSettings.getState().getOrder().equals(reverse));
                        require(MainMenuSettings.getState().getHidden().contains(MainMenuPolicy.THEME));

                        MessagesController.preferences.edit()
                                .putString("fluxgram_main_menu_order", "unknown,proxy,proxy")
                                .putString("fluxgram_main_menu_hidden", "")
                                .apply();
                        MainMenuPolicy.State normalized = MainMenuSettings.getState();
                        require(normalized.getOrder().get(0).equals(MainMenuPolicy.PROXY));
                        require(normalized.getOrder().size() == MainMenuPolicy.knownIds().size());
                        require(normalized.getHidden().isEmpty());

                        MessagesController.preferences.putRaw("fluxgram_main_menu_order", 7);
                        MainMenuPolicy.State malformed = MainMenuSettings.getState();
                        require(malformed.getOrder().equals(MainMenuPolicy.defaults().getOrder()));
                        require(malformed.getHidden().isEmpty());

                        MainMenuSettings.reset();
                        require(MainMenuSettings.getState().getOrder().equals(MainMenuPolicy.defaults().getOrder()));
                        require(MainMenuSettings.getState().getHidden().equals(MainMenuPolicy.defaults().getHidden()));
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
                    str(shared_preferences),
                    str(messages_controller),
                    str(BOTTOM_NAVIGATION),
                    str(ROOT_NAVIGATION),
                    str(POLICY),
                    str(SETTINGS),
                    str(check),
                ],
                check=True,
            )
            subprocess.run(["java", "-cp", str(root), "MainMenuSettingsCheck"], check=True)

    def test_editor_uses_bounded_reorder_and_outside_card_label(self):
        activity = (JAVA / "ui/FluxGramMainMenuSettingsActivity.java").read_text()
        cell = (JAVA / "ui/Components/FluxGramMainMenuCell.java").read_text()
        recycler = (JAVA / "ui/Components/UniversalRecyclerView.java").read_text()

        self.assertIn("UItem.asCustomShadow(label", activity)
        self.assertIn("adapter.reorderSectionStart()", activity)
        self.assertIn("adapter.reorderSectionEnd()", activity)
        self.assertIn("listView.setReorderLongPressEnabled(false)", activity)
        self.assertIn("listView.allowReorder(true)", activity)
        self.assertIn("MainMenuSettings.setOrder(order)", activity)
        self.assertIn("MainMenuSettings.setCommandVisible", activity)
        self.assertIn("MainMenuSettings.reset()", activity)
        self.assertIn("item.object = semanticId", cell)
        bind_text = cell.index("setTextAndCheck(title, visible, needDivider)")
        hide_switch = cell.index("getCheckBox().setVisibility(dividerItem ? INVISIBLE : VISIBLE)")
        self.assertLess(bind_text, hide_switch)
        self.assertIn("ACTION_MOVE_UP", cell)
        self.assertIn("ACTION_MOVE_DOWN", cell)
        self.assertIn("itemTouchHelper.startDrag(holder)", recycler)


if __name__ == "__main__":
    unittest.main()

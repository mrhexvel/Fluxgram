from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
PLUGIN_SOURCE = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/plugins"


class PluginApplicationEventsTest(unittest.TestCase):
    def test_lifecycle_stabilizes_background_and_deduplicates_edges(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        sources = {
            "org/telegram/messenger/AndroidUtilities.java": """package org.telegram.messenger;
public final class AndroidUtilities {
    public static void runOnUIThread(Runnable value, long delay) {}
    public static void cancelRunOnUIThread(Runnable value) {}
}
""",
            "org/telegram/messenger/UserConfig.java": """package org.telegram.messenger;
public final class UserConfig {
    public static int selectedAccount;
    public static boolean isValidAccount(int account) { return true; }
    public static UserConfig getInstance(int account) { return new UserConfig(); }
    public long getClientUserId() { return 1; }
}
""",
            "org/telegram/ui/Components/ForegroundDetector.java": """package org.telegram.ui.Components;
public class ForegroundDetector {
    public interface Listener {
        void onBecameForeground();
        void onBecameBackground();
    }
    public static ForegroundDetector getInstance() { return null; }
    public boolean isForeground() { return false; }
    public boolean isBackground() { return true; }
    public void addListener(Listener listener) {}
}
""",
            "org/telegram/messenger/plugins/PluginManager.java": """package org.telegram.messenger.plugins;
final class PluginManager {
    static PluginManager getInstance() { return new PluginManager(); }
    void applicationForegrounded(PluginApplicationEvent event) {}
    void applicationBackgrounded(PluginApplicationEvent event) {}
}
""",
            "org/telegram/messenger/plugins/PluginApplicationLifecycleCheck.java": """package org.telegram.messenger.plugins;
public final class PluginApplicationLifecycleCheck {
    static final class State implements PluginApplicationLifecycle.State {
        boolean background;
        public boolean isBackground() { return background; }
    }
    static final class Scheduler implements PluginApplicationLifecycle.Scheduler {
        Runnable pending;
        long delay;
        public void post(Runnable value, long delayMs) {
            pending = value;
            delay = delayMs;
        }
        public void cancel(Runnable value) {
            if (pending == value) pending = null;
        }
        void run() {
            Runnable value = pending;
            pending = null;
            if (value != null) value.run();
        }
    }
    static final class Dispatcher implements PluginApplicationLifecycle.Dispatcher {
        int foregrounded;
        int backgrounded;
        public void foregrounded() { foregrounded++; }
        public void backgrounded() { backgrounded++; }
    }
    static void check(boolean value) {
        if (!value) throw new AssertionError();
    }
    public static void main(String[] args) {
        State state = new State();
        Scheduler scheduler = new Scheduler();
        Dispatcher dispatcher = new Dispatcher();
        PluginApplicationLifecycle lifecycle = new PluginApplicationLifecycle(
                state, scheduler, dispatcher, true);

        state.background = true;
        lifecycle.onBecameBackground();
        check(scheduler.pending != null && scheduler.delay == 200L);
        check(dispatcher.backgrounded == 0);
        lifecycle.onBecameForeground();
        check(scheduler.pending == null && dispatcher.foregrounded == 0);

        lifecycle.onBecameBackground();
        scheduler.run();
        check(dispatcher.backgrounded == 1);
        lifecycle.onBecameBackground();
        check(scheduler.pending == null && dispatcher.backgrounded == 1);
        lifecycle.onBecameForeground();
        lifecycle.onBecameForeground();
        check(dispatcher.foregrounded == 1);

        lifecycle.onBecameBackground();
        state.background = false;
        scheduler.run();
        check(dispatcher.backgrounded == 1);
    }
}
""",
        }

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            files = []

            for name, content in sources.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
                files.append(str(path))

            files.extend((
                str(PLUGIN_SOURCE / "PluginApplicationEvent.java"),
                str(PLUGIN_SOURCE / "PluginApplicationLifecycle.java"),
            ))
            subprocess.run(
                ["javac", "-d", directory, *files],
                check=True,
                capture_output=True,
            )
            subprocess.run(
                [
                    "java",
                    "-cp",
                    directory,
                    "org.telegram.messenger.plugins.PluginApplicationLifecycleCheck",
                ],
                check=True,
                capture_output=True,
            )

    def test_source_uses_process_detector_and_selected_account_capture(self):
        source = (PLUGIN_SOURCE / "PluginApplicationLifecycle.java").read_text()
        manager = (PLUGIN_SOURCE / "PluginManager.java").read_text()

        self.assertIn("implements ForegroundDetector.Listener", source)
        self.assertIn("BACKGROUND_GRACE_MS = 200L", source)
        self.assertIn("detector.isForeground()", source)
        self.assertIn("state.isBackground()", source)
        self.assertIn("UserConfig.selectedAccount", source)
        self.assertIn("UserConfig.isValidAccount(account)", source)
        self.assertNotIn("PluginUiLifecycle", source)
        self.assertIn("PluginApplicationLifecycle.initialize()", manager)

    def test_manager_and_adapter_use_independent_bounded_channels(self):
        manager = (PLUGIN_SOURCE / "PluginManager.java").read_text()
        adapter = (PLUGIN_SOURCE / "PythonPluginAdapter.java").read_text()
        handler = adapter.split(
            "void applicationForegrounded(PluginApplicationEvent event)",
            1,
        )[1]

        self.assertGreaterEqual(
            manager.count("entry.plugin.hasApplicationForegroundedSubscribers()"),
            2,
        )
        self.assertGreaterEqual(
            manager.count("entry.plugin.hasApplicationBackgroundedSubscribers()"),
            2,
        )
        self.assertIn("eventSlots.tryAcquire()", handler)
        self.assertIn('"dispatch_application_foregrounded"', handler)
        self.assertIn('"dispatch_application_backgrounded"', handler)
        self.assertIn("getClientUserId() != ownerId", handler)
        self.assertIn("setApplicationForegroundedSubscriptionActive", adapter)
        self.assertIn("setApplicationBackgroundedSubscriptionActive", adapter)


if __name__ == "__main__":
    unittest.main()

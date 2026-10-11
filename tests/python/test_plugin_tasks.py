from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
PLUGINS = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/plugins"


class PluginTasksTest(unittest.TestCase):
    def test_java_scheduler_is_bounded_lifecycle_aware_and_foreground_gated(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        with tempfile.TemporaryDirectory(prefix="fluxgram-task-scheduler-") as folder:
            root = Path(folder)
            sources = {
                "com/chaquo/python/PyObject.java": """package com.chaquo.python;
import java.util.function.Supplier;
public class PyObject {
    private final Supplier<Boolean> callback;
    private final Boolean value;
    public PyObject(Supplier<Boolean> callback) { this(callback, null); }
    private PyObject(Supplier<Boolean> callback, Boolean value) {
        this.callback = callback;
        this.value = value;
    }
    public PyObject call(Object... args) { return new PyObject(null, callback.get()); }
    public <T> T toJava(Class<T> type) { return type.cast(value); }
}
""",
                "org/telegram/messenger/FileLog.java": """package org.telegram.messenger;
public class FileLog {
    public static void e(String message, Throwable error) {
        throw new AssertionError(message, error);
    }
}
""",
                "org/telegram/messenger/AndroidUtilities.java": """package org.telegram.messenger;
import java.util.*;
public class AndroidUtilities {
    private static long now;
    private static final Map<Runnable, Long> timers = new LinkedHashMap<>();
    public static void runOnUIThread(Runnable task, long delay) {
        timers.put(task, now + delay);
    }
    public static void cancelRunOnUIThread(Runnable task) { timers.remove(task); }
    public static void advance(long value) {
        now += value;
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<Runnable, Long> entry : new ArrayList<>(timers.entrySet())) {
                if (entry.getValue() <= now && timers.remove(entry.getKey()) != null) {
                    entry.getKey().run();
                    changed = true;
                }
            }
        } while (changed);
    }
}
""",
                "org/telegram/ui/Components/ForegroundDetector.java": """package org.telegram.ui.Components;
import java.util.*;
public class ForegroundDetector {
    public interface Listener {
        void onBecameForeground();
        void onBecameBackground();
    }
    private static ForegroundDetector instance;
    private final List<Listener> listeners = new ArrayList<>();
    private boolean foreground = true;
    public ForegroundDetector() { instance = this; }
    public static ForegroundDetector getInstance() { return instance; }
    public boolean isForeground() { return foreground; }
    public void addListener(Listener listener) { listeners.add(listener); }
    public void removeListener(Listener listener) { listeners.remove(listener); }
    public void foreground() {
        foreground = true;
        for (Listener listener : new ArrayList<>(listeners)) listener.onBecameForeground();
    }
    public void background() {
        foreground = false;
        for (Listener listener : new ArrayList<>(listeners)) listener.onBecameBackground();
    }
}
""",
            }

            for name, source in sources.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source)

            subprocess.run(
                [
                    "javac",
                    "-d",
                    str(root),
                    *(str(root / name) for name in sources),
                    str(PLUGINS / "PythonPluginTaskScheduler.java"),
                    str(ROOT / "tests/java/PluginTaskSchedulerCheck.java"),
                ],
                check=True,
                capture_output=True,
            )
            subprocess.run(
                [
                    "java",
                    "-cp",
                    str(root),
                    "org.telegram.messenger.plugins.PluginTaskSchedulerCheck",
                ],
                check=True,
                capture_output=True,
            )

    def test_runtime_port_and_adapter_keep_generation_boundaries(self):
        runtime = (
            ROOT / "TMessagesProj/src/main/python/fluxgram_runtime/_tasks.py"
        ).read_text()
        adapter = (PLUGINS / "PythonPluginAdapter.java").read_text()

        self.assertIn("scheduleManagedTask(", runtime)
        self.assertIn("cancelManagedTasks(self._generation)", runtime)
        self.assertIn("taskScheduler.cancelAll();", adapter)
        self.assertIn("taskScheduler.cancelGeneration(request);", adapter)
        self.assertIn("MAILBOX_CAPACITY = 69", adapter)


if __name__ == "__main__":
    unittest.main()

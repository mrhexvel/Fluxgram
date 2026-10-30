from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
DIALOGS_ACTIVITY = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java"
POLICY = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/fluxgram/FolderSwipeGesturePolicy.java"


class FolderSwipeGestureTest(unittest.TestCase):
    def test_folder_swipe_policy_uses_relaxed_symmetric_thresholds(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("java and javac are required")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            check = root / "FolderSwipeGesturePolicyCheck.java"
            check.write_text(textwrap.dedent("""
                import org.telegram.messenger.fluxgram.FolderSwipeGesturePolicy;

                public final class FolderSwipeGesturePolicyCheck {
                    public static void main(String[] args) {
                        require(!FolderSwipeGesturePolicy.shouldStart(2499, 0));
                        require(FolderSwipeGesturePolicy.shouldStart(2500, 0));
                        require(!FolderSwipeGesturePolicy.shouldStart(2500, 2500));

                        require(FolderSwipeGesturePolicy.shouldCommit(-1, 1000, 0, 0, true));
                        require(!FolderSwipeGesturePolicy.shouldCommit(1, 1000, 0, 0, true));
                        require(FolderSwipeGesturePolicy.shouldCommit(1, 1000, 0, 0, false));
                        require(!FolderSwipeGesturePolicy.shouldCommit(-1, 1000, 0, 0, false));
                        require(!FolderSwipeGesturePolicy.shouldCommit(-1, 1000, 2500, 0, true));
                        require(FolderSwipeGesturePolicy.shouldCommit(-1, 1000, 2499, 0, true));
                        require(!FolderSwipeGesturePolicy.shouldCommit(1, 1000, -2500, 0, true));
                        require(!FolderSwipeGesturePolicy.shouldCommit(1, 1000, -2500, 0, false));
                        require(!FolderSwipeGesturePolicy.shouldCommit(-1, 1000, 2500, 0, false));
                        require(FolderSwipeGesturePolicy.shouldCommit(1, 1000, -2499, 0, false));
                        require(FolderSwipeGesturePolicy.shouldCommit(0, 1000, -2500, 0, true));
                        require(!FolderSwipeGesturePolicy.shouldCommit(0, 1000, 2500, 0, true));
                        require(FolderSwipeGesturePolicy.shouldCommit(0, 1000, 2500, 0, false));
                        require(!FolderSwipeGesturePolicy.shouldCommit(0, 1000, -2500, 0, false));
                        require(!FolderSwipeGesturePolicy.shouldCommit(0, 1000, -2500, 3000, true));
                        require(!FolderSwipeGesturePolicy.shouldCommit(100, 0, -2500, 0, true));
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
                ["java", "-cp", str(root), "FolderSwipeGesturePolicyCheck"],
                check=True,
            )

    def test_dialogs_activity_keeps_stock_capture_and_interrupted_transition(self):
        source = DIALOGS_ACTIVITY.read_text()
        gesture = source.split("public boolean onTouchEvent(MotionEvent ev)", 1)[1].split(
            "private void updateFilterTabsVisibility", 1
        )[0]

        self.assertIn("AndroidUtilities.getPixelsInCM(0.3f, true)", gesture)
        self.assertIn("FolderSwipeGesturePolicy.shouldStart(velX, velY)", gesture)
        self.assertIn("!FolderSwipeGesturePolicy.shouldCommit(", gesture)
        self.assertIn(
            "if (ev == null || ev.getAction() == MotionEvent.ACTION_CANCEL || viewPages[1].isLocked)",
            gesture,
        )
        self.assertIn("Math.abs(velX) > 1500", gesture)
        self.assertIn("getMeasuredWidth() >> 1", gesture)
        self.assertNotIn("getMeasuredWidth() / 3.0f", gesture)
        self.assertNotIn("Math.abs(velX) < 3500", gesture)


if __name__ == "__main__":
    unittest.main()

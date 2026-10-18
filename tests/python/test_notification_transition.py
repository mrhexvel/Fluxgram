from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class NotificationTransitionTest(unittest.TestCase):
    def test_blur_reuses_buffers_without_recycling_drawn_bitmaps(self):
        path = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/Components/NotificationBlurBackground.java'
        source = path.read_text()

        self.assertEqual(source.count('Bitmap.createBitmap('), 1)
        self.assertNotIn('.recycle()', source)
        self.assertIn('availableBuffer = retiredBuffer;', source)
        self.assertIn('retiredBuffer = displayedBuffer;', source)
        self.assertIn('displayedBuffer = result;', source)
        self.assertIn('private void clearBuffers()', source)
        self.assertIn('source.getWindowVisibility() != View.VISIBLE', source)

    def test_interrupted_entrance_exit_and_detach(self):
        if not shutil.which('javac') or not shutil.which('java'):
            self.skipTest('Requires JDK')

        source = (ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/Components/NotificationCardLayout.java').read_text()
        source = re.sub(r'^(?:package|import) .*;\n', '', source, flags=re.M)
        fixture = (ROOT / 'tests/java/NotificationTransitionCheck.java').read_text()
        gestures = (ROOT / 'tests/java/NotificationSwipeCheck.java').read_text()
        handler = (ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/Components/NotificationSwipeHandler.java').read_text()
        handler = re.sub(r'^(?:package|import) .*;\n', '', handler, flags=re.M)

        with tempfile.TemporaryDirectory() as directory:
            layout = Path(directory) / 'NotificationCardLayout.java'
            check = Path(directory) / 'NotificationTransitionCheck.java'
            layout.write_text('import java.util.function.Consumer;\n' + source)
            check.write_text(fixture)
            swipe = Path(directory) / 'NotificationSwipeHandler.java'
            swipe_check = Path(directory) / 'NotificationSwipeCheck.java'
            swipe.write_text(handler)
            swipe_check.write_text(gestures)

            subprocess.run(['javac', '-d', directory, str(layout), str(check), str(swipe), str(swipe_check)],
                           check=True, capture_output=True)
            subprocess.run(['java', '-cp', directory, 'NotificationTransitionCheck'],
                           check=True, capture_output=True)
            subprocess.run(['java', '-cp', directory, 'NotificationSwipeCheck'],
                           check=True, capture_output=True)

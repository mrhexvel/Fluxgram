from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"


class ZalgoDisplayFilterTest(unittest.TestCase):
    def test_unicode_policy(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("java and javac are required")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            check = root / "org/telegram/messenger/fluxgram/ZalgoDisplayFilterPolicyCheck.java"
            check.parent.mkdir(parents=True)
            check.write_text(textwrap.dedent(r"""
                package org.telegram.messenger.fluxgram;

                public final class ZalgoDisplayFilterPolicyCheck {
                    private static void assertEquals(String expected, String actual, String message) {
                        if (!expected.equals(actual)) {
                            throw new AssertionError(message + ": " + actual);
                        }
                    }

                    public static void main(String[] args) {
                        String allowed = "a\u0301\u0302\u0303";
                        assertEquals(allowed, ZalgoDisplayFilterPolicy.filter(allowed, null), "three marks changed");

                        String excessive = allowed + "\u0304";
                        String filtered = ZalgoDisplayFilterPolicy.filter(excessive, null);
                        assertEquals("a\u0301\u0302\u0303\u2060", filtered, "excess mark remained visible");
                        if (filtered.length() != excessive.length()) {
                            throw new AssertionError("UTF-16 length changed");
                        }

                        String supplementary = allowed + "\uD834\uDD67";
                        String supplementaryFiltered = ZalgoDisplayFilterPolicy.filter(supplementary, null);
                        assertEquals("a\u0301\u0302\u0303\u2060\u2060", supplementaryFiltered, "supplementary mark failed");
                        if (supplementaryFiltered.length() != supplementary.length()) {
                            throw new AssertionError("supplementary UTF-16 length changed");
                        }

                        boolean[] protectedUnits = new boolean[excessive.length()];
                        protectedUnits[protectedUnits.length - 1] = true;
                        assertEquals(excessive, ZalgoDisplayFilterPolicy.filter(excessive, protectedUnits), "protected mark changed");

                        String semantics = "1\uFE0F\u20E3 \u05D0\u200F \uD83D\uDC69\u200D\uD83D\uDCBB";
                        assertEquals(semantics, ZalgoDisplayFilterPolicy.filter(semantics, null), "emoji or bidi semantics changed");

                        String separated = "a\u0301\u0302\u0303b\u0304\u0305\u0306";
                        assertEquals(separated, ZalgoDisplayFilterPolicy.filter(separated, null), "mark count did not reset");
                    }
                }
            """))

            subprocess.run(
                [
                    "javac",
                    "-d",
                    directory,
                    str(JAVA / "messenger/fluxgram/ZalgoDisplayFilterPolicy.java"),
                    str(check),
                ],
                check=True,
                capture_output=True,
            )
            subprocess.run(
                ["java", "-cp", directory, "org.telegram.messenger.fluxgram.ZalgoDisplayFilterPolicyCheck"],
                check=True,
                capture_output=True,
            )

    def test_filter_is_limited_to_display_layout_inputs(self):
        message_object = (JAVA / "messenger/MessageObject.java").read_text()
        dialog_cell = (JAVA / "ui/Cells/DialogCell.java").read_text()
        transition = (JAVA / "ui/TextMessageEnterTransition.java").read_text()
        display_filter = (JAVA / "messenger/fluxgram/ZalgoDisplayFilter.java").read_text()

        self.assertEqual(1, message_object.count("ZalgoDisplayFilter.filter(messageText)"))
        self.assertEqual(1, dialog_cell.count("messageString = ZalgoDisplayFilter.filter(messageString);"))
        self.assertEqual(1, transition.count("text = ZalgoDisplayFilter.filter(text);"))
        self.assertIn("TextUtils.copySpansFrom", display_filter)
        self.assertIn("ReplacementSpan.class", display_filter)
        self.assertIn("CodeHighlighting.Span.class", display_filter)
        self.assertIn("URLSpanMono.class", display_filter)
        self.assertNotIn("messageText = ZalgoDisplayFilter", message_object)
        self.assertNotIn("messageOwner.message = ZalgoDisplayFilter", message_object)


if __name__ == "__main__":
    unittest.main()

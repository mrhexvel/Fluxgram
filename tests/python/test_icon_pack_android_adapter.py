from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
ICONS = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/fluxgram/icons"
STUBS = ROOT / "tests/java/icon_pack_android_stubs"


class IconPackAndroidAdapterTest(unittest.TestCase):
    def test_catalog_and_decoder_contract(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("java and javac are required")

        with tempfile.TemporaryDirectory(prefix="icon-pack-android-") as directory:
            sources = [str(path) for path in STUBS.rglob("*.java")]
            sources.extend(
                [
                    str(ICONS / "IconPackAndroidCatalog.java"),
                    str(ICONS / "IconPackAndroidDrawableDecoder.java"),
                    str(ROOT / "tests/java/IconPackAndroidCheck.java"),
                ]
            )

            subprocess.run(
                ["javac", "--release", "8", "-d", directory, *sources],
                check=True,
                capture_output=True,
            )
            subprocess.run(
                [
                    "java",
                    "-cp",
                    directory,
                    "org.telegram.messenger.fluxgram.icons.IconPackAndroidCheck",
                ],
                check=True,
                capture_output=True,
            )

        validator = (ICONS / "IconPackArchiveValidator.java").read_text()

        self.assertIn("catalog.decodesSvg(bytes, path)", validator)
        self.assertNotIn("getIdentifier", (ICONS / "IconPackAndroidCatalog.java").read_text())


if __name__ == "__main__":
    unittest.main()

from pathlib import Path
import hashlib
import io
import json
import os
import shutil
import subprocess
import tempfile
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[2]
ICONS = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/fluxgram/icons"
STUBS = ROOT / "tests/java/icon_pack_settings_stubs"


class IconPackSettingsTest(unittest.TestCase):
    def test_persisted_active_order_contract(self):
        candidates = list(
            (
                Path.home()
                / ".gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.11.0"
            ).glob("*/gson-2.11.0.jar")
        )
        gson = os.environ.get("GSON_JAR") or (
            str(candidates[0]) if candidates else None
        )
        if not gson or not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK and Gson 2.11.0 (or GSON_JAR)")

        with tempfile.TemporaryDirectory(prefix="icon-pack-settings-") as directory:
            root = Path(directory)
            classes = root / "classes"
            store = root / "store"
            cache = root / "cache"
            store.mkdir()
            cache.mkdir()

            alpha = self.write_archive(root, "alpha.icons", "alpha", "1.0")
            punctuation = self.write_archive(
                root, "punctuation.icons", "comma,:; pack", "1.0"
            )
            unicode_pack = self.write_archive(
                root, "unicode.icons", "Солнечный 🌞", "1.0"
            )
            alpha_update = self.write_archive(
                root, "alpha-update.icons", "alpha", "2.0"
            )

            sources = [str(path) for path in STUBS.rglob("*.java")]
            sources.extend(
                [
                    str(ICONS / "IconPackManifest.java"),
                    str(ICONS / "IconPackArchiveValidator.java"),
                    str(ICONS / "IconPackStore.java"),
                    str(ICONS / "IconPackSettings.java"),
                    str(ROOT / "tests/java/IconPackSettingsCheck.java"),
                ]
            )

            subprocess.run(
                [
                    "javac",
                    "--release",
                    "8",
                    "-cp",
                    gson,
                    "-d",
                    classes,
                    *sources,
                ],
                check=True,
                capture_output=True,
            )
            subprocess.run(
                [
                    "java",
                    "-cp",
                    str(classes) + os.pathsep + gson,
                    "org.telegram.messenger.fluxgram.icons.IconPackSettingsCheck",
                    store,
                    cache,
                    alpha,
                    self.digest(alpha),
                    punctuation,
                    self.digest(punctuation),
                    unicode_pack,
                    self.digest(unicode_pack),
                    alpha_update,
                    self.digest(alpha_update),
                ],
                check=True,
                capture_output=True,
            )

    @staticmethod
    def write_archive(root, name, pack_id, version):
        path = root / name
        metadata = {
            "packName": pack_id,
            "packId": pack_id,
            "author": "FluxGram",
            "version": version,
            "icons": {"msg_add": "icons/add.svg"},
        }
        output = io.BytesIO()

        with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("metadata.json", json.dumps(metadata, ensure_ascii=False))
            archive.writestr(
                "icons/add.svg",
                b'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">'
                b'<path d="M1 1h2v2z"/></svg>',
            )

        path.write_bytes(output.getvalue())

        return path

    @staticmethod
    def digest(path):
        return hashlib.sha256(path.read_bytes()).hexdigest()


if __name__ == "__main__":
    unittest.main()

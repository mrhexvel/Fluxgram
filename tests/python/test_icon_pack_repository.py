from pathlib import Path
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
REPOSITORY_STUBS = ROOT / "tests/java/icon_pack_repository_stubs"
ANDROID_STUBS = ROOT / "tests/java/icon_pack_android_stubs"
SETTINGS_STUBS = ROOT / "tests/java/icon_pack_settings_stubs"


class IconPackRepositoryTest(unittest.TestCase):
    def test_review_install_update_and_cleanup(self):
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

        with tempfile.TemporaryDirectory(prefix="icon-pack-repository-") as directory:
            root = Path(directory)
            classes = root / "classes"
            first = self.write_archive(root, "first.icons", "1.0")
            update = self.write_archive(root, "update.icons", "2.0")
            other = self.write_archive(root, "other.icons", "1.0", "other.pack")
            invalid = root / "invalid.icons"
            invalid.write_text("invalid")
            oversized = root / "oversized.icons"
            oversized.write_bytes(b"x" * (16 * 1024 * 1024 + 1))

            sources = [str(path) for path in REPOSITORY_STUBS.rglob("*.java")]
            sources.extend(str(path) for path in (ANDROID_STUBS / "android").rglob("*.java"))
            sources.extend(str(path) for path in (ANDROID_STUBS / "com").rglob("*.java"))
            sources.extend(str(path) for path in SETTINGS_STUBS.rglob("*.java"))
            sources.extend(
                [
                    str(ANDROID_STUBS / "org/telegram/messenger/R.java"),
                    str(ANDROID_STUBS / "org/telegram/messenger/SvgHelper.java"),
                    str(ICONS / "IconPackManifest.java"),
                    str(ICONS / "IconPackArchiveValidator.java"),
                    str(ICONS / "IconPackStore.java"),
                    str(ICONS / "IconPackAndroidCatalog.java"),
                    str(ICONS / "IconPackSettings.java"),
                    str(ICONS / "IconPackRepository.java"),
                    str(ROOT / "tests/java/IconPackRepositoryCheck.java"),
                ]
            )

            compilation = subprocess.run(
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
                capture_output=True,
                text=True,
            )
            self.assertEqual(0, compilation.returncode, compilation.stderr)

            result = subprocess.run(
                [
                    "java",
                    "-cp",
                    str(classes) + os.pathsep + gson,
                    "org.telegram.messenger.fluxgram.icons.IconPackRepositoryCheck",
                    root / "app",
                    first,
                    update,
                    other,
                    invalid,
                    oversized,
                ],
                capture_output=True,
                text=True,
                timeout=30,
            )
            self.assertEqual(0, result.returncode, result.stderr)

        repository = (ICONS / "IconPackRepository.java").read_text()
        notification_center = (
            ROOT
            / "TMessagesProj/src/main/java/org/telegram/messenger/NotificationCenter.java"
        ).read_text()
        launch = (
            ROOT / "TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java"
        ).read_text()
        settings = (
            ROOT
            / "TMessagesProj/src/main/java/org/telegram/ui/FluxGramIconPackSettingsActivity.java"
        ).read_text()

        self.assertIn("IconPackSettings.setActiveIds", repository)
        self.assertIn("IconPackSettings.delete", repository)
        self.assertIn("showDialog(dialog, ignored ->", settings)
        self.assertNotIn("dialog.setOnDismissListener", settings)
        self.assertIn("loadFailed = true", settings)
        self.assertIn("item.id == RETRY", settings)
        self.assertIn("state.withActive(pack.id, active)", settings)
        self.assertIn("state = optimisticState", settings)
        self.assertIn("state = previousState", settings)
        self.assertIn("loadState(false)", settings)
        self.assertIn("private static volatile State stateSnapshot", repository)
        self.assertIn("FluxGramIconPackRuntime", repository)
        self.assertIn("RUNTIME_DEMAND_DELAY_MS = 16", repository)
        self.assertIn("RUNTIME_DRAIN_SCHEDULED.compareAndSet(false, true)", repository)
        self.assertIn("RUNTIME_UI_REFRESH_SCHEDULED.compareAndSet(false, true)", repository)
        self.assertIn("RUNTIME_FRAGMENT_REBUILD_REQUESTED.getAndSet(false)", repository)
        startup = repository.split("public static void initializeRuntime()", 1)[1].split(
            "public static void load", 1
        )[0]
        self.assertIn(
            "requestRuntime(currentStore, installedSnapshot, activeSnapshot, false)",
            startup,
        )
        self.assertIn("publishRuntimeChange(false)", startup)
        self.assertIn("IconPackRuntime.decodePending", repository)
        self.assertIn(
            "Theme.reloadAllResources(ApplicationLoader.applicationContext)", repository
        )
        self.assertIn("IconPackRuntime.refreshUi()", repository)
        self.assertIn("NotificationCenter.fluxgramIconPackChanged", repository)
        self.assertNotIn("NotificationCenter.reloadInterface", repository)
        self.assertIn("fluxgramIconPackChanged = totalEvents++", notification_center)
        self.assertIn(".add(NotificationCenter.fluxgramIconPackChanged)", launch)
        refresh = launch.split(
            "id == NotificationCenter.fluxgramIconPackChanged", 1
        )[1].split("NotificationCenter.suggestedLangpack", 1)[0]
        self.assertIn("rebuildAllFragments(true)", refresh)

    @staticmethod
    def write_archive(root, name, version, pack_id="example.pack"):
        path = root / name
        metadata = {
            "packName": "Example",
            "packId": pack_id,
            "author": "FluxGram",
            "version": version,
            "icons": {"msg_add": "icons/add.svg"},
        }
        output = io.BytesIO()

        with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("metadata.json", json.dumps(metadata))
            archive.writestr(
                "icons/add.svg",
                b'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">'
                b'<path d="M1 1h2v2z"/></svg>',
            )

        path.write_bytes(output.getvalue())

        return path


if __name__ == "__main__":
    unittest.main()

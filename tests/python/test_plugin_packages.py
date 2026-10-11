import hashlib
import ast
import io
import json
import os
import re
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import unittest
import warnings
import zipfile
import zlib


ROOT = Path(__file__).resolve().parents[2]
MANIFEST = {
    "schema_version": 2,
    "id": "test.package.fixture",
    "name": "Тестовый плагин",
    "version": "1.0.0",
    "description": "Пакет для проверки установщика",
    "author": "FluxGram",
    "sdk_version": 2,
    "min_sdk_minor": 6,
    "entrypoint": "main:PluginEntry",
    "access": "full",
    "declared_capabilities": [],
}


class PluginPackagesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
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
            raise unittest.SkipTest("Requires JDK and Gson 2.11.0 (or GSON_JAR)")
        cls.compiled = tempfile.TemporaryDirectory(prefix="fgplugin-classes-")
        cls.addClassCleanup(cls.compiled.cleanup)
        folder = (
            ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/plugins/packages"
        )
        subprocess.run(
            [
                "javac",
                "-cp",
                gson,
                "-d",
                cls.compiled.name,
                str(folder / "PluginManifest.java"),
                str(folder / "PluginPackageValidator.java"),
                str(folder / "PluginPackageStore.java"),
                str(ROOT / "tests/java/PluginStoreCheck.java"),
                str(ROOT / "tests/java/PluginPackageCheck.java"),
            ],
            check=True,
            capture_output=True,
        )
        cls.classpath = cls.compiled.name + os.pathsep + gson

    def setUp(self):
        temp = tempfile.TemporaryDirectory(prefix="fgplugin-fixture-")
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.cache = self.root / "cache"
        self.cache.mkdir()

    def archive(self, fields=None, entries=None, raw_manifest=None):
        data = io.BytesIO()
        manifest = MANIFEST.copy()
        if fields:
            manifest.update(fields)
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(data, "w", compression=zipfile.ZIP_DEFLATED) as z:
                z.writestr(
                    "manifest.json",
                    raw_manifest if raw_manifest is not None else json.dumps(manifest),
                )
                z.writestr(
                    "main.py",
                    'raise RuntimeError("Validation must not execute this")\n',
                )
                for name, content in entries or []:
                    z.writestr(name, content)
        return data.getvalue()

    def validate(self, data, accepted):
        archive = self.root / "example.fgplugin"
        archive.write_bytes(data)
        result = subprocess.run(
            [
                "java",
                "-cp",
                self.classpath,
                "PluginPackageCheck",
                str(archive),
                str(self.cache),
            ],
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0 if accepted else 2, result.stderr)
        self.assertEqual(list(self.cache.iterdir()), [], "Temporary archive leaked")
        return result.stdout.splitlines()

    def test_source_archive_path_is_reserved(self):
        self.validate(self.archive(entries=[(".fluxgram-source.fgplugin", "fake")]), False)
        self.validate(self.archive(entries=[(".FLUXGRAM-SOURCE.fgplugin/payload", "fake")]), False)

    def png(self, width=2, height=2, animated=False):
        def chunk(kind, payload):
            return (
                struct.pack(">I", len(payload)) + kind + payload
                + struct.pack(">I", zlib.crc32(kind + payload))
            )

        header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
        pixels = zlib.compress((b"\x00" + b"\x40\x80\xff\xff" * width) * height)
        animation = chunk(b"acTL", struct.pack(">II", 1, 0)) if animated else b""
        return (
            b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + animation
            + chunk(b"IDAT", pixels) + chunk(b"IEND", b"")
        )

    def test_optional_png_icon(self):
        self.validate(self.archive(fields={"icon": "assets/icon.png"},
                                   entries=[("assets/icon.png", self.png())]), True)
        self.validate(self.archive(), True)

    def test_invalid_icon_paths_and_missing_file(self):
        for path in ("../icon.png", "/icon.png", "https://host/icon.png", "icon.jpg", "assets/", "icon.png"):
            with self.subTest(path=path):
                self.validate(self.archive(fields={"icon": path}), False)

    def test_invalid_or_animated_png(self):
        for data in (b"not an image", self.png(1025, 1), self.png(animated=True),
                     self.png()[:-4], self.png() + b"extra", b"x" * (512 * 1024 + 1)):
            with self.subTest(size=len(data)):
                self.validate(self.archive(fields={"icon": "icon.png"},
                                           entries=[("icon.png", data)]), False)

    def test_png_checksum_is_checked(self):
        data = bytearray(self.png())
        data[20] ^= 1
        self.validate(self.archive(fields={"icon": "icon.png"}, entries=[("icon.png", data)]), False)

    def test_webp_icon_and_animation_rejection(self):
        import base64

        data = base64.b64decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==")
        self.validate(self.archive(fields={"icon": "icon.webp"}, entries=[("icon.webp", data)]), True)
        animated = data + b"ANIM" + struct.pack("<I", 6) + bytes(6)
        animated = animated[:4] + struct.pack("<I", len(animated) - 8) + animated[8:]
        self.validate(self.archive(fields={"icon": "icon.webp"}, entries=[("icon.webp", animated)]), False)
        self.validate(self.archive(fields={"icon": "icon.png"}, entries=[("icon.png", data)]), False)

    def test_valid_archive_and_digest(self):
        data = self.archive(entries=[("assets/icon.txt", b"asset")])
        result = self.validate(data, True)
        self.assertEqual(
            result[:3], [MANIFEST["id"], hashlib.sha256(data).hexdigest(), "3"]
        )

    def test_sdk_version_matches_python(self):
        tree = ast.parse(
            (ROOT / "sdk/python/src/fluxgram/__init__.py").read_text()
        )
        values = {
            target.id: node.value.value
            for node in tree.body
            if isinstance(node, ast.Assign)
            and isinstance(node.value, ast.Constant)
            for target in node.targets
            if isinstance(target, ast.Name)
        }
        java = (
            ROOT
            / "TMessagesProj/src/main/java/org/telegram/messenger/plugins/packages/PluginManifest.java"
        ).read_text()
        self.assertEqual(
            values["API_VERSION"],
            int(re.search(r"SDK_VERSION = (\d+)", java).group(1)),
        )
        self.assertEqual(
            values["API_MINOR_VERSION"],
            int(re.search(r"SDK_MINOR_VERSION = (\d+)", java).group(1)),
        )
        self.assertEqual(values["API_VERSION"], MANIFEST["sdk_version"])
        self.assertEqual(values["API_MINOR_VERSION"], MANIFEST["min_sdk_minor"])

    def test_utf8_metadata(self):
        self.validate(
            self.archive(
                raw_manifest=json.dumps(MANIFEST, ensure_ascii=False).encode()
            ),
            True,
        )

    def test_unsafe_paths(self):
        for path in (
            "../escape.py",
            "/absolute.py",
            "a/../../b",
            "C:/file",
            "a\\b",
            "a//b",
            "./a",
            "a/./b",
            "name.",
            "fluxgram/__init__.py",
            "fluxgram.py",
        ):
            with self.subTest(path=path):
                self.validate(self.archive(entries=[(path, b"x")]), False)

    def test_duplicates_and_file_directory_conflicts(self):
        for entries in (
            [("main.py", b"x")],
            [("MAIN.py", b"x")],
            [("a", b"x"), ("a/b", b"x")],
            [("A/x", b"x"), ("a/y", b"x")],
        ):
            with self.subTest(entries=entries):
                self.validate(self.archive(entries=entries), False)

    def test_symbolic_link(self):
        link = zipfile.ZipInfo("link")
        link.create_system = 3
        link.external_attr = 0o120777 << 16
        self.validate(self.archive(entries=[(link, b"../target")]), False)

    def test_manifest_contract(self):
        for fields in (
            {"id": "fluxgram.impersonation"},
            {"id": "../escape"},
            {"sdk_version": 3},
            {"min_sdk_minor": 7},
            {"schema_version": "2"},
            {"access": "restricted"},
            {"entrypoint": "missing:PluginEntry"},
            {"entrypoint": "../main:PluginEntry"},
            {"version": "1.0"},
            {"version": "01.0.0"},
            {"permissions": ["network"]},
            {"name": " "},
            {"declared_capabilities": ["a", "a"]},
            {"declared_capabilities": [1]},
        ):
            with self.subTest(fields=fields):
                self.validate(self.archive(fields=fields), False)

        without_minor = MANIFEST.copy()
        del without_minor["min_sdk_minor"]
        self.validate(self.archive(raw_manifest=json.dumps(without_minor)), False)

        previous_minor = MANIFEST.copy()
        previous_minor["min_sdk_minor"] = 0
        self.validate(self.archive(raw_manifest=json.dumps(previous_minor)), True)

        legacy = MANIFEST.copy()
        legacy["schema_version"] = 1
        legacy["sdk_version"] = 1
        del legacy["min_sdk_minor"]
        self.validate(self.archive(raw_manifest=json.dumps(legacy)), False)

    def test_missing_manifest_field(self):
        manifest = MANIFEST.copy()
        del manifest["author"]
        self.validate(self.archive(raw_manifest=json.dumps(manifest)), False)

    def test_duplicate_json_key_and_invalid_json(self):
        valid = json.dumps(MANIFEST)
        for value in (
            valid[:-1] + ', "id": "example.other"}',
            valid + "{}",
            valid.replace('"schema_version"', "'schema_version'"),
            b"\xff",
            valid[:-1] + ",}",
        ):
            with self.subTest(value=value):
                self.validate(self.archive(raw_manifest=value), False)

    def test_oversized_manifest(self):
        self.validate(self.archive(raw_manifest=b" " * (64 * 1024 + 1)), False)

    def test_oversized_entry(self):
        self.validate(
            self.archive(entries=[("large", b"x" * (8 * 1024 * 1024 + 1))]), False
        )

    def test_total_expansion_limit(self):
        self.validate(
            self.archive(
                entries=[(f"file{i}", b"x" * (7 * 1024 * 1024)) for i in range(5)]
            ),
            False,
        )

    def test_archive_size_limit(self):
        self.validate(b"x" * (8 * 1024 * 1024 + 1), False)

    def test_entry_count_limit(self):
        self.validate(
            self.archive(entries=[(f"file{i}", b"") for i in range(511)]), False
        )

    def test_encryption_flag(self):
        data = bytearray(self.archive())
        local = data.index(b"PK\x03\x04")
        central = data.index(b"PK\x01\x02")
        struct.pack_into("<H", data, local + 6, 1)
        struct.pack_into("<H", data, central + 8, 1)
        self.validate(data, False)

    def test_bad_crc_and_truncation(self):
        original = self.archive()
        data = bytearray(original)
        central = data.index(b"PK\x01\x02")
        struct.pack_into("<I", data, central + 16, 0)
        self.validate(data, False)
        self.validate(original[:-10], False)

    def test_missing_root_manifest(self):
        data = io.BytesIO()
        with zipfile.ZipFile(data, "w") as z:
            z.writestr("plugin/manifest.json", json.dumps(MANIFEST))
            z.writestr("main.py", "")
        self.validate(data.getvalue(), False)


if __name__ == "__main__":
    unittest.main()


class PluginStoreTest(unittest.TestCase):
    setUpClass = classmethod(PluginPackagesTest.setUpClass.__func__)
    setUp = PluginPackagesTest.setUp
    archive = PluginPackagesTest.archive

    def store(self, action, data=None, expected_hash=None, accepted=True, extra=()):
        args = [
            "java",
            "-cp",
            self.classpath,
            "PluginStoreCheck",
            action,
            str(self.root / "installed"),
            str(self.cache),
        ]
        if data is not None:
            archive = self.root / "example.fgplugin"
            archive.write_bytes(data)
            args += [str(archive), expected_hash or hashlib.sha256(data).hexdigest()]
        args += list(extra)
        result = subprocess.run(args, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0 if accepted else 2, result.stderr)
        self.assertEqual(list(self.cache.iterdir()), [], "Installer cache leak")
        return result.stdout.splitlines()

    def test_install_survives_reopen_and_does_not_execute_python(self):
        data = self.archive(entries=[("helpers/__init__.py", "raise RuntimeError()")])
        self.assertEqual(self.store("install", data), [MANIFEST["id"]])
        self.assertEqual(self.store("list"), [MANIFEST["id"]])
        self.assertTrue(
            (self.root / "installed" / MANIFEST["id"] / "helpers/__init__.py").is_file()
        )

    def test_legacy_installation_remains_visible_and_exportable(self):
        legacy = MANIFEST.copy()
        legacy["schema_version"] = 1
        legacy["sdk_version"] = 1
        del legacy["min_sdk_minor"]
        data = self.archive(raw_manifest=json.dumps(legacy))
        installed = self.root / "installed" / legacy["id"]
        installed.mkdir(parents=True)
        (installed / "manifest.json").write_text(json.dumps(legacy))
        (installed / "main.py").write_text("class PluginEntry: pass\n")
        (installed / ".fluxgram-source.fgplugin").write_bytes(data)

        self.assertEqual(
            self.store("listcompat"),
            [f'{legacy["id"]}:false:1:0'],
        )
        output = self.store(
            "export", extra=(legacy["id"], str(self.root / "shares"))
        )
        self.assertEqual(Path(output[0]).read_bytes(), data)

        self.store(
            "update",
            self.archive(fields={"version": "2.0.0"}),
            extra=(legacy["id"], legacy["version"], "false"),
        )
        self.store("apply")
        self.assertEqual(
            self.store("listcompat"),
            [f'{legacy["id"]}:true:2:6'],
        )

    def test_install_sheet_recognizes_incompatible_update(self):
        source = (
            ROOT
            / "TMessagesProj/src/main/java/org/telegram/ui/PluginInstallSheet.java"
        ).read_text()
        self.assertIn(
            "installed = ExternalPlugins.getIncompatiblePlugin(manifest.id);",
            source,
        )

    def test_remove_without_restart_and_reinstall(self):
        data = self.archive()
        self.store("install", data)
        self.assertEqual(self.store("remove", extra=(MANIFEST["id"],)), [])
        self.assertEqual(self.store("pending"), [])
        self.assertFalse((self.root / "installed" / MANIFEST["id"]).exists())
        self.assertEqual(self.store("install", data), [MANIFEST["id"]])

    def test_remove_plugin_with_damaged_manifest(self):
        self.store("install", self.archive())
        (self.root / "installed" / MANIFEST["id"] / "manifest.json").write_text("broken")
        self.assertEqual(self.store("remove", extra=(MANIFEST["id"],)), [])

    def test_remove_does_not_apply_another_pending_update(self):
        self.store("install", self.archive())
        self.store("install", self.archive(fields={"id": "example.other"}))
        self.store("update", self.archive(fields={"id": "example.other", "version": "2.0.0"}),
                   extra=("example.other", MANIFEST["version"], "false"))
        self.store("remove", extra=(MANIFEST["id"],))
        self.assertEqual(self.store("pending"), ["example.other:false:false"])
        manifest = json.loads((self.root / "installed" / "example.other" / "manifest.json").read_text())
        self.assertEqual(manifest["version"], MANIFEST["version"])

    def test_remove_rejects_pending_update(self):
        self.store("install", self.archive())
        self.store("update", self.archive(fields={"version": "2.0.0"}),
                   extra=(MANIFEST["id"], MANIFEST["version"], "false"))
        self.store("remove", accepted=False, extra=(MANIFEST["id"],))
        self.assertEqual(self.store("list"), [MANIFEST["id"]])

    def test_share_preserves_archive_without_runtime_files(self):
        data = self.archive(entries=[("assets/icon.txt", "original asset")])
        self.store("install", data)
        installed = self.root / "installed" / MANIFEST["id"]
        (installed / "private.json").write_text("private plugin settings")
        (installed / "main.py").write_text("modified by running plugin")
        output = self.store(
            "export", extra=(MANIFEST["id"], str(self.root / "shares"))
        )
        self.assertEqual(Path(output[0]).read_bytes(), data)
        self.assertTrue(output[0].endswith(".fgplugin"))

    def test_share_uses_updated_archive_after_restart(self):
        self.store("install", self.archive())
        data = self.archive(fields={"version": "2.0.0"})
        self.store("update", data, extra=(MANIFEST["id"], MANIFEST["version"], "false"))
        self.store("apply")
        output = self.store(
            "export", extra=(MANIFEST["id"], str(self.root / "shares"))
        )
        self.assertEqual(Path(output[0]).read_bytes(), data)

    def test_share_refuses_missing_or_mismatched_source(self):
        self.store("install", self.archive())
        source = self.root / "installed" / MANIFEST["id"] / ".fluxgram-source.fgplugin"
        source.unlink()
        self.store("export", accepted=False, extra=(MANIFEST["id"], str(self.root / "shares")))
        source.write_bytes(self.archive(fields={"version": "8.0.0"}))
        self.store("export", accepted=False, extra=(MANIFEST["id"], str(self.root / "shares")))
        self.assertEqual(list((self.root / "shares").iterdir()), [])

    def test_changed_archive_rejected_without_publication(self):
        self.store("install", self.archive(), expected_hash="0" * 64, accepted=False)
        self.assertEqual(self.store("list"), [])
        self.assertEqual(list((self.root / "installed").iterdir()), [])

    def test_duplicate_does_not_replace_existing_files(self):
        self.store("install", self.archive())
        before = (
            self.root / "installed" / MANIFEST["id"] / "manifest.json"
        ).read_bytes()
        self.store("install", self.archive(fields={"version": "2.0.0"}), accepted=False)
        self.assertEqual(
            (self.root / "installed" / MANIFEST["id"] / "manifest.json").read_bytes(),
            before,
        )

    def test_invalid_archive_does_not_create_installation(self):
        self.store(
            "install", self.archive(entries=[("../outside.py", "x")]), accepted=False
        )
        self.assertEqual(self.store("list"), [])

    def test_interrupted_staging_is_never_loaded(self):
        staging = self.root / "installed/.install-interrupted"
        staging.mkdir(parents=True)
        (staging / "main.py").write_text("raise RuntimeError()")
        self.assertEqual(self.store("list"), [])
        self.assertFalse(staging.exists())

    def test_corrupt_installation_does_not_block_healthy_plugin(self):
        self.store("install", self.archive())
        corrupt = self.root / "installed/broken.plugin"
        corrupt.mkdir()
        (corrupt / "manifest.json").write_text("{}")
        self.assertEqual(self.store("list"), [MANIFEST["id"]])

    def update(self, version="1.1.0", enable=True, accepted=True, **kwargs):
        return self.store(
            "update",
            self.archive(fields={"version": version}),
            extra=(MANIFEST["id"], MANIFEST["version"], str(enable).lower()),
            accepted=accepted,
            **kwargs,
        )

    def installed_version(self):
        return json.loads(
            (self.root / "installed" / MANIFEST["id"] / "manifest.json").read_text()
        )["version"]

    def test_update_only_applies_on_fresh_start(self):
        self.store("install", self.archive())
        self.update()
        self.assertEqual(self.installed_version(), MANIFEST["version"])
        self.assertEqual(self.store("pending"), [MANIFEST["id"] + ":false:true"])
        self.store("apply")
        self.assertEqual(self.installed_version(), "1.1.0")
        self.assertEqual(self.store("pending"), [])
        self.store("apply")
        self.assertEqual(self.installed_version(), "1.1.0")

    def test_update_keeps_backup_until_migration_finishes(self):
        self.store("install", self.archive())
        self.store(
            "update",
            self.archive(fields={"version": "2.0.0"}),
            extra=(MANIFEST["id"], MANIFEST["version"], "true", "true"),
        )
        root = self.root / "installed"

        self.store("activate", extra=(MANIFEST["id"],))

        self.assertEqual(self.installed_version(), "2.0.0")
        self.assertTrue((root / (".backup-" + MANIFEST["id"])).is_dir())
        self.assertTrue((root / (".pending-" + MANIFEST["id"])).is_dir())
        self.assertEqual(
            self.store("pendingfull"),
            [MANIFEST["id"] + ":true:true:false"],
        )

        self.store("markmigrated", extra=(MANIFEST["id"],))
        self.store("finish", extra=(MANIFEST["id"],))
        self.assertEqual(self.installed_version(), "2.0.0")
        self.assertEqual(self.store("pending"), [])

    def test_update_cannot_finish_before_migration_marker(self):
        self.store("install", self.archive())
        self.update(version="2.0.0")
        self.store("markmigrated", accepted=False, extra=(MANIFEST["id"],))
        self.store("activate", extra=(MANIFEST["id"],))

        self.store("finish", accepted=False, extra=(MANIFEST["id"],))

        self.assertEqual(self.installed_version(), "2.0.0")
        self.assertEqual(self.store("pending"), [MANIFEST["id"] + ":false:true"])

    def test_failed_update_rolls_back_package_and_marks_direction(self):
        self.store("install", self.archive())
        self.update(version="2.0.0")
        root = self.root / "installed"
        self.store("activate", extra=(MANIFEST["id"],))

        (root / (".pending-" + MANIFEST["id"]) / "rollback").write_bytes(b"")
        self.assertEqual(
            self.store("pendingfull"),
            [MANIFEST["id"] + ":true:false:true"],
        )

        self.store("rollback", extra=(MANIFEST["id"],))
        self.assertEqual(self.installed_version(), MANIFEST["version"])
        self.assertEqual(self.store("pending"), [])

    def test_interrupted_rollback_finishes_with_old_package(self):
        self.store("install", self.archive())
        self.update(version="2.0.0")
        root = self.root / "installed"
        self.store("activate", extra=(MANIFEST["id"],))
        pending = root / (".pending-" + MANIFEST["id"])
        (pending / "rollback").write_bytes(b"")
        failed = root / ".install-failed-test"
        (root / MANIFEST["id"]).rename(failed)
        (root / (".backup-" + MANIFEST["id"])).rename(root / MANIFEST["id"])

        self.store("rollback", extra=(MANIFEST["id"],))

        self.assertEqual(self.installed_version(), MANIFEST["version"])
        self.assertEqual(self.store("pending"), [])
        self.store("list")
        self.assertFalse(failed.exists())

    def test_rollback_journal_survives_until_enabled_state_is_restored(self):
        self.store("install", self.archive())
        self.update(version="2.0.0")
        self.store("activate", extra=(MANIFEST["id"],))

        self.store("rollbackprepare", extra=(MANIFEST["id"],))

        self.assertEqual(self.installed_version(), MANIFEST["version"])
        self.assertEqual(
            self.store("pendingfull"),
            [MANIFEST["id"] + ":true:false:true"],
        )
        self.store("rollbackprepare", extra=(MANIFEST["id"],))
        self.store("rollbackfinish", extra=(MANIFEST["id"],))
        self.assertEqual(self.store("pending"), [])

    def test_migrated_marker_finishes_after_backup_cleanup_crash(self):
        self.store("install", self.archive())
        self.update(version="2.0.0")
        root = self.root / "installed"
        self.store("activate", extra=(MANIFEST["id"],))
        pending = root / (".pending-" + MANIFEST["id"])
        (pending / "migrated").write_bytes(b"")
        shutil.rmtree(root / (".backup-" + MANIFEST["id"]))

        self.store("finish", extra=(MANIFEST["id"],))

        self.assertEqual(self.installed_version(), "2.0.0")
        self.assertEqual(self.store("pending"), [])

    def test_update_cannot_reuse_or_downgrade_version(self):
        self.store("install", self.archive())
        self.update(version=MANIFEST["version"], accepted=False)
        self.update(version="0.9.0", accepted=False)
        self.assertEqual(self.store("pending"), [])
        self.assertEqual(self.installed_version(), MANIFEST["version"])

    def test_update_versions_compared_numerically(self):
        self.store("install", self.archive(fields={"version": "1.9.0"}))
        self.store(
            "update",
            self.archive(fields={"version": "1.10.0"}),
            extra=(MANIFEST["id"], "1.9.0", "false"),
        )
        self.assertEqual(self.store("pending"), [MANIFEST["id"] + ":false:false"])
        self.store("apply")
        self.assertEqual(self.installed_version(), "1.10.0")

    def test_changed_update_archive_is_rejected(self):
        self.store("install", self.archive())
        self.update(expected_hash="0" * 64, accepted=False)
        self.assertEqual(self.store("pending"), [])
        self.assertEqual(self.installed_version(), MANIFEST["version"])

    def test_stale_update_review_is_rejected(self):
        self.store("install", self.archive())
        self.store(
            "update",
            self.archive(fields={"version": "2.0.0"}),
            extra=(MANIFEST["id"], "0.9.0", "false"),
            accepted=False,
        )
        self.assertEqual(self.store("pending"), [])

    def test_update_different_id_is_rejected(self):
        self.store("install", self.archive())
        self.store(
            "update",
            self.archive(fields={"id": "another.plugin", "version": "2.0.0"}),
            extra=(MANIFEST["id"], MANIFEST["version"], "false"),
            accepted=False,
        )
        self.assertEqual(self.store("pending"), [])

    def test_conflicting_pending_operations_are_rejected(self):
        self.store("install", self.archive())
        self.update()
        self.update(version="2.0.0", accepted=False)
        self.store("delete", extra=(MANIFEST["id"],), accepted=False)
        self.store("apply")
        self.assertEqual(self.installed_version(), "1.1.0")

    def test_delete_waits_for_restart_and_can_be_reinstalled(self):
        self.store("install", self.archive())
        self.store("delete", extra=(MANIFEST["id"],))
        self.assertEqual(self.store("list"), [MANIFEST["id"]])
        self.assertEqual(self.store("pending"), [MANIFEST["id"] + ":true:false"])
        self.assertEqual(self.store("apply"), [])
        self.assertEqual(self.store("pending"), [])
        self.assertEqual(self.store("install", self.archive()), [MANIFEST["id"]])

    def test_delete_rejects_invalid_id_and_builtin(self):
        for name in (
            "../escape",
            "fluxgram.in_app_notifications",
            "not_installed.plugin",
        ):
            self.store("delete", extra=(name,), accepted=False)
        self.assertEqual(self.store("pending"), [])

    def test_interrupted_update_after_backup_resumes(self):
        self.store("install", self.archive())
        self.update()
        root = self.root / "installed"
        (root / MANIFEST["id"]).rename(root / (".backup-" + MANIFEST["id"]))
        self.store("apply")
        self.assertEqual(self.installed_version(), "1.1.0")
        self.assertEqual([p.name for p in root.iterdir()], [MANIFEST["id"]])

    def test_interrupted_update_after_publish_resumes(self):
        self.store("install", self.archive())
        self.update()
        root = self.root / "installed"
        (root / MANIFEST["id"]).rename(root / (".backup-" + MANIFEST["id"]))
        (root / (".pending-" + MANIFEST["id"]) / "package").rename(
            root / MANIFEST["id"]
        )
        self.store("apply")
        self.assertEqual(self.installed_version(), "1.1.0")
        self.assertEqual([p.name for p in root.iterdir()], [MANIFEST["id"]])

    def test_interrupted_delete_after_backup_resumes(self):
        self.store("install", self.archive())
        self.store("delete", extra=(MANIFEST["id"],))
        root = self.root / "installed"
        (root / MANIFEST["id"]).rename(root / (".backup-" + MANIFEST["id"]))
        self.assertEqual(self.store("apply"), [])
        self.assertEqual(list(root.iterdir()), [])

    def test_delete_never_follows_plugin_symlinks(self):
        self.store("install", self.archive())
        unrelated = self.root / "unrelated"
        unrelated.mkdir()
        (unrelated / "important.txt").write_text("keep")
        (self.root / "installed" / MANIFEST["id"] / "link").symlink_to(
            unrelated, target_is_directory=True
        )
        self.store("delete", extra=(MANIFEST["id"],))
        self.assertEqual(self.store("apply"), [])
        self.assertEqual((unrelated / "important.txt").read_text(), "keep")

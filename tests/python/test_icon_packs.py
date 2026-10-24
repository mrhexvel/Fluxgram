import binascii
import hashlib
import io
import json
import os
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
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/fluxgram/icons"


class IconPacksTest(unittest.TestCase):
    MAX_PACKS = 32

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

        cls.compiled = tempfile.TemporaryDirectory(prefix="icon-pack-classes-")
        cls.addClassCleanup(cls.compiled.cleanup)
        subprocess.run(
            [
                "javac",
                "--release",
                "8",
                "-cp",
                gson,
                "-d",
                cls.compiled.name,
                str(JAVA / "IconPackManifest.java"),
                str(JAVA / "IconPackArchiveValidator.java"),
                str(JAVA / "IconPackStore.java"),
                str(JAVA / "IconPackResolver.java"),
                str(ROOT / "tests/java/IconPackCheck.java"),
            ],
            check=True,
            capture_output=True,
        )
        cls.classpath = cls.compiled.name + os.pathsep + gson

    def setUp(self):
        temp = tempfile.TemporaryDirectory(prefix="icon-pack-fixture-")
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.cache = self.root / "cache"
        self.store = self.root / "store"
        self.cache.mkdir()

    def archive(
        self,
        pack_id="example.pack",
        version="1.0",
        icons=None,
        files=None,
        raw_metadata=None,
        extra_entries=None,
    ):
        icons = icons or {"msg_add": "icons/add.svg"}
        files = files or {
            "icons/add.svg": b'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M1 1h2v2z"/></svg>'
        }
        metadata = {
            "packName": "Example",
            "packId": pack_id,
            "author": "FluxGram",
            "version": version,
            "icons": icons,
        }
        output = io.BytesIO()

        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
                archive.writestr(
                    "metadata.json",
                    raw_metadata if raw_metadata is not None else json.dumps(metadata),
                )
                for name, content in files.items():
                    archive.writestr(name, content)
                for name, content in extra_entries or []:
                    archive.writestr(name, content)

        return output.getvalue()

    def write_archive(self, data, name="pack.icons"):
        path = self.root / name
        path.write_bytes(data)
        return path

    def png(self, pixels):
        def chunk(name, content):
            return (
                struct.pack(">I", len(content))
                + name
                + content
                + struct.pack(">I", binascii.crc32(name + content) & 0xFFFFFFFF)
            )

        return (
            b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", pixels)
            + chunk(b"IEND", b"")
        )

    def run_check(self, *args, accepted=True):
        result = subprocess.run(
            ["java", "-cp", self.classpath, "IconPackCheck", *map(str, args)],
            capture_output=True,
            text=True,
        )
        if accepted and result.returncode != 0:
            self.fail(result.stderr)
        if not accepted and result.returncode == 0:
            self.fail("Invalid icon pack was accepted")
        return result

    def installed_dir(self):
        installed = list(self.store.glob("pack-*"))

        self.assertEqual(1, len(installed))

        return installed[0]

    def inspect(self, data, allowed="msg_add", accepted=True):
        archive = self.write_archive(data)
        return self.run_check(
            "inspect", archive, self.cache, allowed, accepted=accepted
        )

    def test_accepts_exteragram_manifest_and_reports_stable_digest(self):
        data = self.archive()
        result = self.inspect(data)
        fields = result.stdout.strip().split("\t")

        self.assertEqual(["example.pack", "Example"], fields[:2])
        self.assertEqual(hashlib.sha256(data).hexdigest(), fields[2])

    def test_ignores_unknown_resources_but_requires_supported_payload(self):
        self.inspect(
            self.archive(
                icons={
                    "msg_add": "icons/add.svg",
                    "upstream_only": "icons/upstream.svg",
                },
                files={
                    "icons/add.svg": b'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M1 1h2v2z"/></svg>',
                    "icons/upstream.svg": b'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M2 2h4v4z"/></svg>',
                },
                extra_entries=[
                    (
                        "unused.svg",
                        b'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M3 3h5v5z"/></svg>',
                    )
                ],
            )
        )
        self.inspect(self.archive(), allowed="msg_delete", accepted=False)

    def test_rejects_missing_payload_and_unsafe_svg(self):
        self.inspect(
            self.archive(icons={"msg_add": "icons/missing.svg"}),
            accepted=False,
        )
        self.inspect(
            self.archive(
                files={
                    "icons/add.svg": b'<!DOCTYPE svg [<!ENTITY x SYSTEM "file:///etc/passwd">]><svg>&x;</svg>'
                }
            ),
            accepted=False,
        )
        self.inspect(
            self.archive(
                files={
                    "icons/add.svg": b'<svg xmlns="http://www.w3.org/2000/svg"><image href="remote.svg"/></svg>'
                }
            ),
            accepted=False,
        )
        self.inspect(self.archive(extra_entries=[("notes.txt", b"not an icon")]), accepted=False)
        self.inspect(
            self.archive(
                files={
                    "icons/add.svg": b'<svg xmlns="http://www.w3.org/2000/svg"><path d="M1 1h2v2z"/></svg>'
                }
            ),
            accepted=False,
        )
        self.inspect(
            self.archive(
                files={
                    "icons/add.svg": b'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100000 24"><path d="M1 1h2v2z"/></svg>'
                }
            ),
            accepted=False,
        )
        self.inspect(
            self.archive(
                files={
                    "icons/add.svg": b'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="1000000000" height="24"><path d="M1 1h2v2z"/></svg>'
                }
            ),
            accepted=False,
        )

    def test_rejects_invalid_metadata_and_archive_paths(self):
        duplicate = (
            '{"packId":"one","packId":"two","packName":"Pack",'
            '"icons":{"msg_add":"icons/add.svg"}}'
        )
        self.inspect(self.archive(raw_metadata=duplicate), accepted=False)
        self.inspect(self.archive(pack_id="../unsafe"), accepted=False)
        self.inspect(self.archive(pack_id="\ud800"), accepted=False)
        self.inspect(
            self.archive(extra_entries=[("icons/\x01.svg", b"<svg/>")]),
            accepted=False,
        )
        self.inspect(
            self.archive(extra_entries=[("../outside.svg", b"<svg/>")]),
            accepted=False,
        )

        for malformed in ("[]", '{"schemaVersion":999999999999999999999}'):
            result = self.run_check("invalid-manifest", malformed)
            self.assertEqual("IO", result.stdout.strip())

    def test_rejects_svg_processing_instruction(self):
        data = self.archive(
            files={
                "icons/add.svg": b'<?xml-stylesheet href="https://example.com/a.css"?><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M1 1h2v2z"/></svg>'
            }
        )
        archive = self.write_archive(data)

        self.inspect(data, accepted=False)
        result = self.run_check(
            "inspect-rejected-svg-callback",
            archive,
            self.cache,
            "msg_add",
        )

        self.assertEqual("0", result.stdout.strip())

    def test_raster_icon_must_decode_with_declared_dimensions(self):
        valid = self.png(zlib.compress(b"\x00\xff\x00\x00\xff"))
        invalid = self.png(b"not-a-deflate-stream")

        self.inspect(
            self.archive(
                icons={"msg_add": "icons/add.png"},
                files={"icons/add.png": valid},
            )
        )
        self.inspect(
            self.archive(
                icons={"msg_add": "icons/add.png"},
                files={"icons/add.png": valid + b'\x00[{"effectName":"TransformEffect"}]'},
            )
        )
        self.inspect(
            self.archive(
                icons={"msg_add": "icons/add.png"},
                files={"icons/add.png": invalid},
            ),
            accepted=False,
        )

    def test_store_updates_exports_recovers_and_deletes(self):
        first = self.archive(version="1.0")
        first_path = self.write_archive(first, "first.icons")
        first_hash = hashlib.sha256(first).hexdigest()

        self.run_check(
            "install", self.store, self.cache, "msg_add", first_path, first_hash
        )
        self.assertEqual(
            "example.pack:1.0",
            self.run_check("list", self.store, self.cache, "msg_add").stdout.strip(),
        )

        exported = self.root / "exported.icons"
        self.run_check(
            "export", self.store, self.cache, "msg_add", "example.pack", exported
        )
        self.assertEqual(first, exported.read_bytes())
        internal = self.installed_dir() / ".fluxgram-identity"
        self.run_check(
            "export",
            self.store,
            self.cache,
            "msg_add",
            "example.pack",
            internal,
            accepted=False,
        )
        self.assertEqual(
            "example.pack:1.0",
            self.run_check("list", self.store, self.cache, "msg_add").stdout.strip(),
        )
        exported.write_bytes(b"old export")
        self.run_check(
            "export", self.store, self.cache, "msg_add", "example.pack", exported
        )
        self.assertEqual(first, exported.read_bytes())

        second = self.archive(
            version="2.0",
            files={
                "icons/add.svg": b'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M2 2h4v4z"/></svg>'
            },
        )
        second_path = self.write_archive(second, "second.icons")
        second_hash = hashlib.sha256(second).hexdigest()
        self.run_check(
            "install", self.store, self.cache, "msg_add", second_path, second_hash
        )
        self.assertEqual(
            "example.pack:2.0",
            self.run_check("list", self.store, self.cache, "msg_add").stdout.strip(),
        )

        destination = self.installed_dir()
        backup = self.store / f".backup-{destination.name}"
        destination.rename(backup)
        self.assertEqual(
            "example.pack:2.0",
            self.run_check("list", self.store, self.cache, "msg_add").stdout.strip(),
        )
        self.assertTrue(destination.is_dir())
        self.assertFalse(backup.exists())

        self.assertEqual(
            "0",
            self.run_check(
                "delete", self.store, self.cache, "msg_add", "example.pack"
            ).stdout.strip(),
        )

    def test_resolver_normalizes_order_and_uses_first_available_pack(self):
        one = self.archive(pack_id="pack.one", version="1")
        two = self.archive(
            pack_id="pack.two",
            version="1",
            files={
                "icons/add.svg": b'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M8 8h2v2z"/></svg>'
            },
        )

        for name, data in (("one", one), ("two", two)):
            path = self.write_archive(data, f"{name}.icons")
            self.run_check(
                "install",
                self.store,
                self.cache,
                "msg_add,msg_delete",
                path,
                hashlib.sha256(data).hexdigest(),
            )

        result = self.run_check(
            "resolve",
            self.store,
            self.cache,
            "msg_add,msg_delete",
            "missing,pack.two,pack.two,pack.one",
            "msg_add",
        )
        lines = result.stdout.splitlines()

        self.assertEqual("pack.two,pack.one", lines[0])
        self.assertIn("M8 8h2v2z", lines[1])

        fallback = self.run_check(
            "resolve",
            self.store,
            self.cache,
            "msg_add,msg_delete",
            "pack.two,pack.one",
            "msg_delete",
        )
        self.assertEqual("NONE", fallback.stdout.splitlines()[1])

    def test_store_preserves_tampered_destination_and_valid_backup(self):
        data = self.archive(version="1.0")
        archive = self.write_archive(data)

        self.run_check(
            "install",
            self.store,
            self.cache,
            "msg_add",
            archive,
            hashlib.sha256(data).hexdigest(),
        )

        destination = self.installed_dir()
        backup = self.store / f".backup-{destination.name}"
        shutil.copytree(destination, backup)
        (destination / "icons/add.svg").write_bytes(b"<svg/>")

        self.assertEqual(
            "",
            self.run_check("list", self.store, self.cache, "msg_add").stdout.strip(),
        )
        self.assertTrue(destination.exists())
        self.assertTrue(backup.exists())

        shutil.rmtree(destination)

        self.assertEqual(
            "example.pack:1.0",
            self.run_check("list", self.store, self.cache, "msg_add").stdout.strip(),
        )
        self.assertTrue(destination.exists())
        self.assertFalse(backup.exists())
        self.assertIn("M1 1h2v2z", (destination / "icons/add.svg").read_text())

    def test_old_resolver_does_not_read_updated_pack(self):
        first = self.archive(version="1.0")
        first_path = self.write_archive(first, "first.icons")

        self.run_check(
            "install",
            self.store,
            self.cache,
            "msg_add",
            first_path,
            hashlib.sha256(first).hexdigest(),
        )

        second = self.archive(
            version="2.0",
            files={
                "icons/add.svg": b'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M9 9h3v3z"/></svg>'
            },
        )
        second_path = self.write_archive(second, "second.icons")
        result = self.run_check(
            "stale-update",
            self.store,
            self.cache,
            "msg_add",
            "example.pack",
            "msg_add",
            second_path,
            hashlib.sha256(second).hexdigest(),
        )

        self.assertEqual("NONE", result.stdout.strip())

        fresh = self.run_check(
            "resolve",
            self.store,
            self.cache,
            "msg_add",
            "example.pack",
            "msg_add",
        )
        self.assertIn("M9 9h3v3z", fresh.stdout.splitlines()[1])

    def test_old_resolver_does_not_revive_after_identical_reinstall(self):
        data = self.archive(version="1.0")
        archive = self.write_archive(data)
        digest = hashlib.sha256(data).hexdigest()

        self.run_check(
            "install",
            self.store,
            self.cache,
            "msg_add",
            archive,
            digest,
        )
        result = self.run_check(
            "stale-reinstall",
            self.store,
            self.cache,
            "msg_add",
            "example.pack",
            "msg_add",
            archive,
            digest,
        )

        self.assertEqual("NONE", result.stdout.strip())

    def test_installed_pack_cannot_be_resolved_by_another_store(self):
        data = self.archive()
        archive = self.write_archive(data)

        self.run_check(
            "install",
            self.store,
            self.cache,
            "msg_add",
            archive,
            hashlib.sha256(data).hexdigest(),
        )

        other_store = self.root / "other-store"
        other_cache = self.root / "other-cache"
        other_cache.mkdir()
        result = self.run_check(
            "cross-store",
            self.store,
            self.cache,
            "msg_add",
            "example.pack",
            "msg_add",
            other_store,
            other_cache,
        )

        self.assertEqual("NONE", result.stdout.strip())

    def test_recovery_removes_invalid_internal_backups(self):
        malformed = self.store / ".backup-invalid"
        malformed.mkdir(parents=True)
        (malformed / "payload").write_text("invalid")

        self.assertEqual(
            "",
            self.run_check("list", self.store, self.cache, "msg_add").stdout.strip(),
        )
        self.assertFalse(malformed.exists())

    def test_validation_removes_only_stale_review_snapshots(self):
        stale = self.cache / "icon-pack-review-orphan.icons"
        fresh = self.cache / "icon-pack-review-active.icons"
        stale.write_bytes(b"orphan")
        fresh.write_bytes(b"active")
        os.utime(stale, (1, 1))

        self.inspect(self.archive())

        self.assertFalse(stale.exists())
        self.assertTrue(fresh.exists())

    def test_accepts_exteragram_style_display_id(self):
        data = self.archive(pack_id="Theme Pack №1")
        archive = self.write_archive(data)

        self.run_check(
            "install",
            self.store,
            self.cache,
            "msg_add",
            archive,
            hashlib.sha256(data).hexdigest(),
        )
        self.assertEqual(
            "Theme Pack №1:1.0",
            self.run_check("list", self.store, self.cache, "msg_add").stdout.strip(),
        )

    def test_limits_number_of_installed_packs(self):
        for index in range(self.MAX_PACKS):
            data = self.archive(pack_id=f"pack.{index}")
            archive = self.write_archive(data, f"pack-{index}.icons")

            self.run_check(
                "install",
                self.store,
                self.cache,
                "msg_add",
                archive,
                hashlib.sha256(data).hexdigest(),
            )

        extra = self.archive(pack_id="pack.extra")
        archive = self.write_archive(extra, "pack-extra.icons")

        self.run_check(
            "install",
            self.store,
            self.cache,
            "msg_add",
            archive,
            hashlib.sha256(extra).hexdigest(),
            accepted=False,
        )
if __name__ == "__main__":
    unittest.main()

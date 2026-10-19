import hashlib
import json
from pathlib import Path
import re
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[2]
API_SOURCE = ROOT / "TMessagesProj/src/main/java/org/fluxgram"
MANIFEST_SOURCE = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/plugins/packages/PluginManifest.java"
PACKAGE_SOURCE = MANIFEST_SOURCE.parent
ARTIFACTS = ROOT / "sdk/python/devkit/src/fluxgram_devkit/artifacts"
DEVKIT_VERSION_SOURCE = ROOT / "sdk/python/devkit/src/fluxgram_devkit/__init__.py"


class PluginDevkitContractTest(unittest.TestCase):
    def test_java_api_artifact_matches_host_contract(self):
        metadata = json.loads((ARTIFACTS / "api.json").read_text())
        manifest = MANIFEST_SOURCE.read_text()
        major = int(re.search(r"JAVA_API_VERSION = (\d+)", manifest).group(1))
        minor = int(re.search(r"JAVA_API_MINOR_VERSION = (\d+)", manifest).group(1))
        self.assertEqual((metadata["api_version"], metadata["api_minor"]), (major, minor))
        devkit_versions = DEVKIT_VERSION_SOURCE.read_text()
        self.assertEqual(int(re.search(r"JAVA_API_VERSION = (\d+)", devkit_versions).group(1)), major)
        self.assertEqual(int(re.search(r"JAVA_API_MINOR = (\d+)", devkit_versions).group(1)), minor)
        self.assertEqual(int(re.search(r"SDK_VERSION = (\d+)", devkit_versions).group(1)),
                         int(re.search(r"SDK_VERSION = (\d+)", manifest).group(1)))
        self.assertEqual(int(re.search(r"SDK_MINOR = (\d+)", devkit_versions).group(1)),
                         int(re.search(r"SDK_MINOR_VERSION = (\d+)", manifest).group(1)))

        digest = hashlib.sha256()

        for path in sorted(API_SOURCE.glob("*.java")):
            digest.update(path.name.encode())
            digest.update(b"\0")
            digest.update(path.read_bytes())

        self.assertEqual(metadata["source_sha256"], digest.hexdigest())

        validator_digest = hashlib.sha256()

        for name in ("PluginManifest.java", "PluginPackageValidator.java", "PluginDexValidator.java"):
            validator_digest.update(name.encode())
            validator_digest.update(b"\0")
            validator_digest.update((PACKAGE_SOURCE / name).read_bytes())

        self.assertEqual(metadata["host_validator_sha256"], validator_digest.hexdigest())
        artifact = ARTIFACTS / f"fluxgram-java-api-{major}.{minor}.jar"
        self.assertEqual(metadata["sha256"], hashlib.sha256(artifact.read_bytes()).hexdigest())
        sources_artifact = ARTIFACTS / f"fluxgram-java-api-{major}.{minor}-sources.jar"
        self.assertEqual(metadata["sources_jar_sha256"],
                         hashlib.sha256(sources_artifact.read_bytes()).hexdigest())

        with zipfile.ZipFile(artifact) as archive:
            classes = [name for name in archive.namelist() if name.endswith(".class")]

        self.assertTrue(classes)
        self.assertTrue(all(name.startswith("org/fluxgram/") for name in classes))
        self.assertIn("org/fluxgram/JavaPluginModule.class", classes)
        self.assertIn("org/fluxgram/JavaSettingsBlock.class", classes)
        self.assertIn("org/fluxgram/JavaSettingsBlockContext.class", classes)
        self.assertIn("org/fluxgram/ComposerSurface.class", classes)

        with zipfile.ZipFile(sources_artifact) as archive:
            sources = {name: archive.read(name) for name in archive.namelist()}

        expected_sources = {
            f"org/fluxgram/{path.name}": path.read_bytes()
            for path in API_SOURCE.glob("*.java")
        }
        self.assertEqual(sources, expected_sources)
        self.assertTrue(all(b"/**" in source for source in sources.values()))

    def test_template_does_not_bundle_host_sdks(self):
        template = ROOT / "sdk/python/devkit/src/fluxgram_devkit/templates/hybrid"
        project = (template / "fluxgram-plugin.toml").read_text()
        self.assertIn('files = ["main.py"]', project)
        self.assertNotIn("fluxgram-java-api", project)
        self.assertFalse((template / "plugin/fluxgram").exists())
        self.assertFalse((template / "plugin/fluxgram.py").exists())

    def test_host_stubs_artifact_matches_manifest_fingerprint(self):
        metadata = json.loads((ARTIFACTS / "host-stubs.json").read_text())
        api_metadata = json.loads((ARTIFACTS / "api.json").read_text())
        manifest = MANIFEST_SOURCE.read_text()
        fingerprint = re.search(r'HOST_FINGERPRINT\s*=\s*\n\s*"([a-f0-9]{64})"', manifest)
        self.assertIsNotNone(fingerprint)
        self.assertEqual(metadata["host_fingerprint"], fingerprint.group(1))
        self.assertEqual(
            metadata["stable_api"],
            f'{api_metadata["api_version"]}.{api_metadata["api_minor"]}',
        )

        artifact = ARTIFACTS / "fluxgram-host-stubs.jar"
        self.assertEqual(metadata["jar_sha256"], hashlib.sha256(artifact.read_bytes()).hexdigest())

        with zipfile.ZipFile(artifact) as archive:
            classes = [name for name in archive.namelist() if name.endswith(".class")]

        self.assertTrue(classes)
        self.assertNotIn("org/telegram/messenger/plugins/PluginManager.class", classes)

    def test_device_bridge_is_shell_only_and_runtime_gated(self):
        main_manifest = ROOT / "TMessagesProj/src/main/AndroidManifest.xml"
        provider = (
            ROOT
            / "TMessagesProj/src/main/java/org/telegram/messenger/plugins/PluginDebugProvider.java"
        )
        bridge = (
            ROOT
            / "TMessagesProj/src/main/java/org/telegram/messenger/plugins/PluginDeveloperBridge.java"
        )
        external = (
            ROOT
            / "TMessagesProj/src/main/java/org/telegram/messenger/plugins/ExternalPlugins.java"
        )
        manifest = main_manifest.read_text()
        provider_source = provider.read_text()
        bridge_source = bridge.read_text()
        external_source = external.read_text()

        self.assertTrue(provider.is_file())
        self.assertIn("PluginDebugProvider", manifest)
        self.assertIn('android:permission="android.permission.DUMP"', manifest)
        self.assertIn('android:exported="true"', manifest)
        self.assertIn('android:grantUriPermissions="false"', manifest)
        self.assertIn("Process.SHELL_UID", provider_source)
        self.assertIn("Process.ROOT_UID", provider_source)
        self.assertIn("PluginDeveloperBridge.isAvailable()", provider_source)
        self.assertIn("SESSION_DURATION_MS = 15 * 60 * 1000L", bridge_source)
        self.assertIn("PluginManager.getInstance().isSystemEnabled()", bridge_source)
        self.assertIn("BuildConfig.DEBUG", bridge_source)
        self.assertIn("PluginDeveloperBridge.isAvailable()", external_source)
        self.assertNotIn("if (!BuildConfig.DEBUG)", external_source)

        for manifest in (
                ROOT / "TMessagesProj/config/debug/AndroidManifest.xml",
                ROOT / "TMessagesProj/config/debug/AndroidManifest_SDK23.xml",
                ROOT / "TMessagesProj/config/release/AndroidManifest.xml",
                ROOT / "TMessagesProj/config/release/AndroidManifest_SDK23.xml"):
            self.assertNotIn("PluginDebugProvider", manifest.read_text())


if __name__ == "__main__":
    unittest.main()

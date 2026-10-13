import hashlib
import json
from pathlib import Path
import re
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[2]
API_SOURCE = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/plugins/api"
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

        with zipfile.ZipFile(artifact) as archive:
            classes = [name for name in archive.namelist() if name.endswith(".class")]

        self.assertTrue(classes)
        self.assertTrue(all(name.startswith("org/telegram/messenger/plugins/api/") for name in classes))
        self.assertIn("org/telegram/messenger/plugins/api/JavaPluginModule.class", classes)
        self.assertIn("org/telegram/messenger/plugins/api/ComposerSurface.class", classes)

    def test_template_does_not_bundle_host_sdks(self):
        template = ROOT / "sdk/python/devkit/src/fluxgram_devkit/templates/python-java"
        project = (template / "fluxgram-plugin.toml").read_text()
        self.assertIn('files = ["main.py"]', project)
        self.assertNotIn("fluxgram-java-api", project)
        self.assertFalse((template / "plugin/fluxgram").exists())
        self.assertFalse((template / "plugin/fluxgram.py").exists())


if __name__ == "__main__":
    unittest.main()

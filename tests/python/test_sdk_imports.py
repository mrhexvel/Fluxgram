import os
from pathlib import Path
import subprocess
import sys
import tomllib
import unittest


ROOT = Path(__file__).resolve().parents[2]
SDK = ROOT / "sdk/python"
SDK_SOURCE = SDK / "src"
RUNTIME_SOURCE = ROOT / "TMessagesProj/src/main/python"


class SdkImportsTest(unittest.TestCase):
    def test_public_api_imports_without_android_runtime(self):
        script = """
import sys

import fluxgram
import fluxgram.models
import fluxgram.testing
import fluxgram.ui
from fluxgram import (
    API_MINOR_VERSION,
    API_VERSION,
    BeforeSendEvent,
    ChatInfo,
    ChatNotifications,
    MessageInfo,
    MessageReceivedEvent,
    NotificationOverride,
    Plugin,
    SelectSetting,
    SliderSetting,
    StorageResult,
    StorageTask,
    SwitchSetting,
    TextSetting,
)
from fluxgram.storage import Storage, StorageMigration
from fluxgram.ui import Bulletins, Navigation, Notifications, UI
from fluxgram.testing import PluginHarness

assert API_VERSION == 2
assert API_MINOR_VERSION == 1
assert fluxgram.ChatInfo is fluxgram.models.ChatInfo
assert set(fluxgram.ui.__all__) == {"UI", "Bulletins", "Navigation", "Notifications"}
assert SwitchSetting("enabled", "Enabled").default is False
assert UI()._active() is False
assert PluginHarness.__module__ == "fluxgram.testing.harness"
assert Storage.__module__ == "fluxgram.storage"
assert StorageMigration.__module__ == "fluxgram.storage"
assert StorageResult.__module__ == "fluxgram.storage"
assert StorageTask.__module__ == "fluxgram.storage"
try:
    Plugin("standalone.test")
except RuntimeError as error:
    assert "only be created by FluxGram" in str(error)
else:
    raise AssertionError("Plugin construction unexpectedly succeeded")
assert not any(name == "fluxgram_runtime" or name.startswith("fluxgram_runtime.") for name in sys.modules)
assert not any(
    name == "android" or name.startswith("android.")
    or name == "java" or name.startswith("java.")
    or name == "org.telegram" or name.startswith("org.telegram.")
    for name in sys.modules
)
"""
        environment = os.environ.copy()
        environment["PYTHONPATH"] = str(SDK_SOURCE)
        result = subprocess.run(
            [sys.executable, "-B", "-c", script],
            cwd=SDK,
            env=environment,
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_public_and_runtime_packages_are_separate(self):
        self.assertTrue((SDK_SOURCE / "fluxgram/__init__.py").is_file())
        self.assertTrue((RUNTIME_SOURCE / "fluxgram_runtime/__init__.py").is_file())
        self.assertFalse((RUNTIME_SOURCE / "fluxgram").exists())

    def test_public_sdk_does_not_import_client_runtime(self):
        forbidden = (
            "from android",
            "from java",
            "from org.telegram",
            "from fluxgram_runtime",
            "import android",
            "import java",
            "import org.telegram",
            "import fluxgram_runtime",
        )

        for source in (SDK_SOURCE / "fluxgram").rglob("*.py"):
            text = source.read_text()

            with self.subTest(source=source.relative_to(SDK_SOURCE)):
                self.assertFalse(any(value in text for value in forbidden))

    def test_public_package_discovers_separate_runtime_source(self):
        script = """
import importlib.util

import fluxgram

runtime_spec = importlib.util.find_spec("fluxgram_runtime._bridge")
assert runtime_spec is not None
assert "TMessagesProj/src/main/python/fluxgram_runtime/_bridge.py" in runtime_spec.origin
assert importlib.util.find_spec("fluxgram._bridge") is None
"""
        environment = os.environ.copy()
        environment["PYTHONPATH"] = os.pathsep.join((
            str(SDK_SOURCE),
            str(RUNTIME_SOURCE),
        ))
        result = subprocess.run(
            [sys.executable, "-B", "-c", script],
            cwd=ROOT,
            env=environment,
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_package_metadata_and_chaquopy_source_set(self):
        metadata = tomllib.loads((SDK / "pyproject.toml").read_text())
        self.assertEqual(metadata["project"]["name"], "fluxgram-sdk")
        self.assertEqual(metadata["project"]["version"], "2.2.0")
        self.assertEqual(metadata["project"]["requires-python"], ">=3.11")
        self.assertTrue((SDK_SOURCE / "fluxgram/py.typed").is_file())

        gradle = (ROOT / "TMessagesProj/build.gradle").read_text()
        self.assertIn('rootProject.file("sdk/python/src")', gradle)
        self.assertIn("git submodule update --init --recursive", gradle)

    def test_sdk_is_pinned_as_a_submodule(self):
        modules = (ROOT / ".gitmodules").read_text()
        self.assertIn('[submodule "sdk/python"]', modules)
        self.assertIn("path = sdk/python", modules)
        self.assertIn(
            "url = https://github.com/mrhexvel/fluxgram-sdk.git",
            modules,
        )

        result = subprocess.run(
            ["git", "ls-files", "--stage", "sdk/python"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertRegex(result.stdout, r"^160000 [0-9a-f]{40} 0\tsdk/python\n$")


if __name__ == "__main__":
    unittest.main()

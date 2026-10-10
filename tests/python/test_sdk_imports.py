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
    SwitchSetting,
    TextSetting,
)
from fluxgram.ui import Bulletins, Navigation, Notifications, UI

assert API_VERSION == 2
assert API_MINOR_VERSION == 0
assert fluxgram.ChatInfo is fluxgram.models.ChatInfo
assert set(fluxgram.ui.__all__) == {"UI", "Bulletins", "Navigation", "Notifications"}
assert SwitchSetting("enabled", "Enabled").default is False
assert UI()._active() is False
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

    def test_sdk_and_runtime_have_no_duplicate_python_files(self):
        sdk_files = {
            path.relative_to(SDK_SOURCE / "fluxgram")
            for path in (SDK_SOURCE / "fluxgram").rglob("*.py")
        }
        runtime_files = {
            path.relative_to(RUNTIME_SOURCE / "fluxgram")
            for path in (RUNTIME_SOURCE / "fluxgram").rglob("*.py")
        }
        self.assertEqual(sdk_files & runtime_files, set())

    def test_public_package_discovers_separate_runtime_source(self):
        script = """
import importlib.util

import fluxgram

spec = importlib.util.find_spec("fluxgram._bridge")
assert spec is not None
assert "TMessagesProj/src/main/python/fluxgram/_bridge.py" in spec.origin
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
        self.assertEqual(metadata["project"]["version"], "2.0.0")
        self.assertEqual(metadata["project"]["requires-python"], ">=3.11")
        self.assertTrue((SDK_SOURCE / "fluxgram/py.typed").is_file())

        gradle = (ROOT / "TMessagesProj/build.gradle").read_text()
        self.assertIn('srcDir(rootProject.file("sdk/python/src"))', gradle)


if __name__ == "__main__":
    unittest.main()

import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
APPEARANCE = JAVA / "messenger/fluxgram/appearance"
UI = JAVA / "ui/fluxgram/appearance"
FIXTURE = ROOT / "TMessagesProj/src/main/assets/fluxgram/appearance/v1/profile-found.json"


class ProfileAppearanceTest(unittest.TestCase):
    def test_peer_key_and_allowlist(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        with tempfile.TemporaryDirectory() as directory:
            subprocess.run([
                "javac", "-d", directory,
                str(APPEARANCE / "FluxGramPeerKey.java"),
                str(APPEARANCE / "ProfileBadge.java"),
                str(APPEARANCE / "ProfileBadgeCatalog.java"),
                str(UI / "ProfileBadgePlacement.java"),
                str(ROOT / "tests/java/ProfileAppearanceCheck.java"),
            ], check=True, capture_output=True)
            subprocess.run([
                "java", "-cp", directory, "ProfileAppearanceCheck",
            ], check=True, capture_output=True)

    def test_local_fixture_matches_stage_two_target(self):
        fixture = json.loads(FIXTURE.read_text())
        result = fixture["result"]
        badge = result["profile"]["badges"][0]

        self.assertEqual(fixture["schema_version"], 1)
        self.assertEqual(fixture["minimum_renderer_version"], 1)
        self.assertEqual(result["peer"], {
            "peer_type": "user",
            "peer_id": "123456789",
        })
        self.assertEqual(badge["badge_id"], "developer")
        self.assertEqual(badge["custom_emoji_document_id"], "5373141891321699086")
        self.assertTrue(badge["particles"])

    def test_profile_activity_has_only_local_renderer_hooks(self):
        source = (JAVA / "ui/ProfileActivity.java").read_text()

        self.assertIn("FluxGramProfileBadgeRenderer[] fluxGramBadgeRenderers", source)
        self.assertIn("updateFluxGramProfileBadge();", source)
        self.assertIn("renderer.bind(badge, false);", source)
        self.assertIn("renderer.onTouchEvent(event)", source)
        self.assertIn("fluxGramBadgeRenderers[badgeIndex].draw(canvas);", source)
        self.assertIn("fluxGramBadgeRenderers[i].attach();", source)
        self.assertIn("fluxGramBadgeRenderers[i].detach();", source)
        self.assertIn("destroyFluxGramBadgeRenderers();", source)

    def test_renderer_uses_particles_and_account_aware_bulletin(self):
        renderer = (UI / "FluxGramProfileBadgeRenderer.java").read_text()
        bulletin = (UI / "FluxGramBadgeBulletin.java").read_text()

        self.assertIn("drawable.setCurrentAccount(account);", renderer)
        self.assertIn("LiteMode.isEnabled(LiteMode.FLAG_PARTICLES)", renderer)
        self.assertIn("ProfileBadgePlacement.findLeft", renderer)
        self.assertNotIn("setMinusWidth", renderer)
        self.assertIn("BulletinFactory.of(fragment)", bulletin)
        self.assertIn("account,", bulletin)
        self.assertNotIn("UserConfig.selectedAccount", renderer)
        self.assertNotIn("UserConfig.selectedAccount", bulletin)

    def test_unsupported_profile_types_are_rejected(self):
        adapter = (APPEARANCE / "TelegramPeerKeyAdapter.java").read_text()
        loader = (APPEARANCE / "LocalAppearanceFixture.java").read_text()

        self.assertIn("ChatObject.isCommunity(chat)", adapter)
        self.assertIn("topicId == 0", (JAVA / "ui/ProfileActivity.java").read_text())
        self.assertIn("minimum_renderer_version", loader)
        self.assertIn("ProfileBadgeCatalog.resolve", loader)
        self.assertNotIn("http://", loader)
        self.assertNotIn("https://", loader)


if __name__ == "__main__":
    unittest.main()

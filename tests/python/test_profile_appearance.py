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

    def test_repository_cache_policy_and_lifecycle(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        with tempfile.TemporaryDirectory() as directory:
            subprocess.run([
                "javac", "-d", directory,
                str(APPEARANCE / "FluxGramPeerKey.java"),
                str(APPEARANCE / "ProfileBadge.java"),
                str(APPEARANCE / "ProfileBadgeCatalog.java"),
                str(APPEARANCE / "AppearanceApiDto.java"),
                str(APPEARANCE / "AppearanceProfile.java"),
                str(APPEARANCE / "AppearanceCachePolicy.java"),
                str(APPEARANCE / "AppearanceMemoryCache.java"),
                str(APPEARANCE / "AppearanceRepository.java"),
                str(ROOT / "tests/java/AppearanceRepositoryCheck.java"),
            ], check=True, capture_output=True)
            subprocess.run([
                "java", "-cp", directory, "AppearanceRepositoryCheck",
            ], check=True, capture_output=True)

    def test_profile_controller_lifecycle_and_delivery(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        with tempfile.TemporaryDirectory() as directory:
            subprocess.run([
                "javac", "-d", directory,
                str(APPEARANCE / "FluxGramPeerKey.java"),
                str(APPEARANCE / "ProfileBadge.java"),
                str(APPEARANCE / "ProfileBadgeCatalog.java"),
                str(APPEARANCE / "AppearanceApiDto.java"),
                str(APPEARANCE / "AppearanceProfile.java"),
                str(APPEARANCE / "AppearanceCachePolicy.java"),
                str(APPEARANCE / "AppearanceMemoryCache.java"),
                str(APPEARANCE / "AppearanceRepository.java"),
                str(UI / "FluxGramProfileAppearanceController.java"),
                str(ROOT / "tests/java/ProfileAppearanceControllerCheck.java"),
            ], check=True, capture_output=True)
            subprocess.run([
                "java", "-cp", directory, "ProfileAppearanceControllerCheck",
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

    def test_android_contract_fixtures_use_real_seed_document(self):
        fixture_directory = FIXTURE.parent
        batch = json.loads((fixture_directory / "batch-mixed.json").read_text())
        request = json.loads((fixture_directory / "batch-request.json").read_text())
        missing = json.loads((fixture_directory / "profile-missing.json").read_text())

        self.assertEqual(
            batch["results"][0]["profile"]["badges"][0]["custom_emoji_document_id"],
            "5373141891321699086",
        )
        self.assertEqual(batch["results"][0]["peer"], request["peers"][0])
        self.assertIsNone(batch["results"][1]["profile"])
        self.assertIsNone(missing["result"]["profile"])

    def test_api_configuration_is_optional_https_only(self):
        config = (ROOT / "gradle/fluxgram-config.gradle").read_text()

        self.assertIn("FLUXGRAM_APPEARANCE_API_BASE_URL", config)
        self.assertIn("fluxgram.appearance_api_base_url", config)
        self.assertIn("uri.scheme == 'https'", config)
        self.assertIn("appearanceApiBaseUrl.isEmpty()", config)

    def test_profile_activity_uses_repository_controller_for_users_and_groups(self):
        source = (JAVA / "ui/ProfileActivity.java").read_text()

        self.assertIn("FluxGramProfileBadgeRenderer[] fluxGramBadgeRenderers", source)
        self.assertIn("FluxGramProfileAppearanceController fluxGramAppearanceController", source)
        self.assertIn("AppearanceRepositories.get(currentAccount)", source)
        self.assertIn("updateFluxGramProfileBadge();", source)
        self.assertIn("renderer.bind(badge, animated);", source)
        self.assertIn("renderer.onTouchEvent(event)", source)
        self.assertIn("fluxGramBadgeRenderers[badgeIndex].draw(canvas);", source)
        self.assertIn("fluxGramBadgeRenderers[i].attach();", source)
        self.assertIn("fluxGramBadgeRenderers[i].detach();", source)
        self.assertIn("destroyFluxGramBadgeRenderers();", source)
        self.assertIn("peer.getType() != FluxGramPeerKey.Type.USER", source)
        self.assertIn("peer.getType() != FluxGramPeerKey.Type.GROUP", source)
        self.assertIn("topicId == 0", source)
        self.assertNotIn("LocalAppearanceFixture", source)

    def test_profile_badge_group_centering_preserves_upstream_edge_states(self):
        source = (JAVA / "ui/ProfileActivity.java").read_text()
        placement = (UI / "ProfileBadgePlacement.java").read_text()

        self.assertEqual(source.count("float nameX = getExpandedNameTranslationX("), 2)
        self.assertIn("Math.min(nameTextView[a].getExactWidth(), a == 1 ? params1.width : viewportWidth)", source)
        self.assertIn("nameTextView[a].getExactWidth(),\n                    nameScale", source)
        self.assertIn("placement.getScaledCenterX()", source)
        self.assertIn("nameX = AndroidUtilities.lerp(minimizedX, nameX, diff);", source)
        self.assertIn("nameTextView[1].setTranslationX(AndroidUtilities.dpf2(18f) - nameTextView[1].getLeft());", source)
        self.assertIn("if (placement == null || !placement.isVisible()) {\n            return upstream;", source)
        self.assertNotIn("isRTL", placement)
        self.assertNotIn("LocaleController", placement)

    def test_async_badge_presence_change_updates_layout_once(self):
        source = (JAVA / "ui/ProfileActivity.java").read_text()
        renderer = (UI / "FluxGramProfileBadgeRenderer.java").read_text()

        self.assertIn("layoutChanged |= renderer.bind(badge, animated);", source)
        self.assertIn("if (layoutChanged && fragmentView != null) {\n            needLayout(false);", source)
        self.assertIn("if (badge == null) {\n                return false;", renderer)
        self.assertIn("boolean visibilityChanged = badge == null;", renderer)

    def test_renderer_uses_particles_and_account_aware_bulletin(self):
        renderer = (UI / "FluxGramProfileBadgeRenderer.java").read_text()
        bulletin = (UI / "FluxGramBadgeBulletin.java").read_text()

        self.assertIn("drawable.setCurrentAccount(account);", renderer)
        self.assertIn("LiteMode.isEnabled(LiteMode.FLAG_PARTICLES)", renderer)
        self.assertIn("badge.hasParticles()", renderer)
        self.assertIn("hitBounds.setEmpty();\n            drawable.setParticles(false, false);", renderer)
        self.assertIn("ProfileBadgePlacement.calculate", renderer)
        self.assertIn("Math.max(placement.getContentRight(), centerX - hitRadius)", renderer)
        self.assertIn("Math.min(placement.getAvailableRight(), centerX + hitRadius)", renderer)
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

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

    def test_chat_header_controller_lifecycle_and_delivery(self):
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
                str(UI / "FluxGramChatHeaderAppearanceController.java"),
                str(ROOT / "tests/java/ChatHeaderAppearanceControllerCheck.java"),
            ], check=True, capture_output=True)
            subprocess.run([
                "java", "-cp", directory, "ChatHeaderAppearanceControllerCheck",
            ], check=True, capture_output=True)

    def test_user_list_controller_batch_recycle_and_lifecycle(self):
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
                str(UI / "FluxGramUserListAppearanceController.java"),
                str(UI / "UserListBadgePlacement.java"),
                str(ROOT / "tests/java/UserListAppearanceControllerCheck.java"),
            ], check=True, capture_output=True)
            subprocess.run([
                "java", "-cp", directory, "UserListAppearanceControllerCheck",
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

    def test_chat_header_uses_user_only_fail_closed_controller(self):
        chat = (JAVA / "ui/ChatActivity.java").read_text()
        controller = (UI / "FluxGramChatHeaderAppearanceController.java").read_text()

        self.assertIn("FluxGramChatHeaderAppearanceController fluxGramChatHeaderAppearanceController", chat)
        self.assertIn("AppearanceRepositories.get(currentAccount)", chat)
        self.assertIn("avatarContainer.applyFluxGramBadge(currentAccount, badge, animated);", chat)
        self.assertIn("updateTitleIcons();\n        updateFluxGramChatHeaderBadge();", chat)
        self.assertIn("chatMode != MODE_DEFAULT", chat)
        self.assertIn("currentUser == null", chat)
        self.assertIn("currentChat != null", chat)
        self.assertIn("isTopic", chat)
        self.assertIn("isComments", chat)
        self.assertIn("isThreadChat()", chat)
        self.assertIn("isReplyChatComment()", chat)
        self.assertIn("threadMessageId != 0", chat)
        self.assertIn("quickReplyShortcut != null", chat)
        self.assertIn("businessLink != null", chat)
        self.assertIn("UserObject.isUserSelf(currentUser)", chat)
        self.assertIn("UserObject.isReplyUser(currentUser)", chat)
        self.assertIn("UserObject.isService(userId)", chat)
        self.assertIn("DialogObject.isUserDialog(dialog_id)", chat)
        self.assertIn("DialogObject.isEncryptedDialog(dialog_id)", chat)
        self.assertIn("currentEncryptedChat.user_id == userId", chat)
        self.assertIn("peer.getType() == FluxGramPeerKey.Type.USER", controller)
        self.assertNotIn("FluxGramPeerKey.Type.GROUP", controller)

    def test_chat_header_renderer_has_local_lifecycle_and_accessibility_hooks(self):
        container = (JAVA / "ui/Components/ChatAvatarContainer.java").read_text()

        self.assertIn("FluxGramProfileBadgeRenderer fluxGramBadgeRenderer", container)
        self.assertIn("public void applyFluxGramBadge(int account, ProfileBadge badge, boolean animated)", container)
        self.assertIn("fluxGramBadgeRenderer.draw(canvas);", container)
        self.assertIn("fluxGramBadgeRenderer.onTouchEvent(event)", container)
        self.assertIn("fluxGramBadgeRenderer.attach();", container)
        self.assertIn("fluxGramBadgeRenderer.detach();", container)
        self.assertIn("fluxGramBadgeRenderer.destroy();", container)
        self.assertIn("fluxGramBadgeRenderer.getAccessibilityLabel()", container)
        self.assertIn("AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS", container)
        self.assertNotIn("setMinusWidth", container)

    def test_chat_header_does_not_extend_forbidden_surfaces(self):
        forbidden = [
            JAVA / "ui/ActionBar/SimpleTextView.java",
            JAVA / "ui/Cells/DialogCell.java",
            JAVA / "ui/Cells/ChatMessageCell.java",
        ]

        for source in forbidden:
            self.assertNotIn("FluxGramChatHeader", source.read_text())

    def test_contacts_lists_are_opt_in_and_fail_closed(self):
        contacts = (JAVA / "ui/ContactsActivity.java").read_text()
        contacts_adapter = (JAVA / "ui/Adapters/ContactsAdapter.java").read_text()
        search_adapter = (JAVA / "ui/Adapters/SearchAdapter.java").read_text()
        controller = (UI / "FluxGramUserListAppearanceController.java").read_text()

        self.assertIn("FluxGramUserListAppearanceController fluxGramAppearanceController", contacts)
        self.assertIn("AppearanceRepositories.get(currentAccount)", contacts)
        self.assertIn("!returnAsResult", contacts)
        self.assertIn("!createSecretChat", contacts)
        self.assertIn("chatId == 0", contacts)
        self.assertIn("channelId == 0", contacts)
        self.assertIn("!onlyUsers", contacts)
        self.assertIn("currentAccount, ignoreUsers", contacts)
        self.assertIn("new SearchAdapterHelper(true, currentAccount)", search_adapter)
        self.assertIn("FluxGramContactsUserCell", contacts_adapter)
        self.assertIn("FluxGramContactsSearchCell", search_adapter)
        self.assertIn("repository.loadBatch", controller)
        self.assertNotIn("repository.load(", controller)
        self.assertIn("MAX_BATCH_SIZE = 100", controller)
        self.assertIn("lookup.getAccount() != account", controller)
        self.assertIn("peer.equals(target.getRequestedPeer())", controller)
        self.assertIn("subscription.cancel();", controller)

    def test_contacts_badges_exclude_phonebook_and_forbidden_surfaces(self):
        search_adapter = (JAVA / "ui/Adapters/SearchAdapter.java").read_text()
        search_cell = (JAVA / "ui/Cells/FluxGramContactsSearchCell.java").read_text()
        user_cell = (JAVA / "ui/Cells/FluxGramContactsUserCell.java").read_text()
        renderer = (UI / "FluxGramUserListBadgeRenderer.java").read_text()

        self.assertIn("case 3:", search_adapter)
        self.assertIn("new ProfileSearchCell(mContext)", search_adapter)
        self.assertIn("UserObject.isUserSelf(user)", search_cell)
        self.assertIn("UserObject.isReplyUser(user)", search_cell)
        self.assertIn("UserObject.isService(user.id)", search_cell)
        self.assertIn("UserObject.isUserSelf(user)", user_cell)
        self.assertIn("setCurrentAccount(account);", search_cell)
        self.assertIn("setCurrentAccount(account);", user_cell)
        self.assertIn("BADGE_SIZE_DP = 20", renderer)
        self.assertIn("LiteMode.FLAG_PARTICLES", renderer)
        self.assertNotIn("MotionEvent", renderer)

        forbidden = [
            JAVA / "ui/Cells/DialogCell.java",
            JAVA / "ui/Cells/ChatMessageCell.java",
            JAVA / "ui/GroupCreateActivity.java",
            JAVA / "ui/Adapters/DialogsSearchAdapter.java",
            JAVA / "ui/Components/ShareAlert.java",
            JAVA / "ui/PrivacyUsersActivity.java",
        ]
        for source in forbidden:
            self.assertNotIn("FluxGramContacts", source.read_text())

    def test_full_chat_users_screen_has_no_fluxgram_integration(self):
        activity = (JAVA / "ui/ChatUsersActivity.java").read_text()
        manage_cell = (JAVA / "ui/Cells/ManageChatUserCell.java").read_text()

        self.assertNotIn("FluxGram", activity)
        self.assertNotIn("fluxGram", activity)
        self.assertNotIn("FluxGram", manage_cell)
        self.assertFalse((JAVA / "ui/Cells/FluxGramChatUsersUserCell.java").exists())

    def test_profile_inline_members_have_separate_fail_closed_controller(self):
        activity = (JAVA / "ui/ProfileActivity.java").read_text()

        self.assertIn("isFluxGramInlineMemberAppearanceEligible()", activity)
        self.assertIn("currentChat != null", activity)
        self.assertIn("!isTopic", activity)
        self.assertIn("!ChatObject.isCommunity(currentChat)", activity)
        self.assertIn("!ChatObject.isChannelAndNotMegaGroup(currentChat)", activity)
        self.assertIn("!currentChat.gigagroup", activity)
        self.assertIn("!currentChat.broadcast", activity)
        self.assertIn("listView.postOnAnimation(runnable);", activity)
        self.assertIn("new FluxGramProfileMemberUserCell(", activity)
        self.assertIn("fluxGramMemberAppearanceController.destroy();", activity)
        self.assertLess(
            activity.index("listView = new ClippedListView"),
            activity.index("fluxGramMemberAppearanceController = new FluxGramUserListAppearanceController"),
        )
        self.assertLess(
            activity.index("fluxGramMemberAppearanceController = new FluxGramUserListAppearanceController"),
            activity.index("listAdapter = new ListAdapter(context)"),
        )

    def test_profile_member_cell_recycles_and_respects_admin_padding(self):
        cell = (JAVA / "ui/Cells/FluxGramProfileMemberUserCell.java").read_text()
        generic = (JAVA / "ui/Cells/UserCell.java").read_text()

        self.assertIn("extends UserCell", cell)
        self.assertIn("super(context, padding, 0, true, resourcesProvider);", cell)
        self.assertIn("setCurrentAccount(account);", cell)
        self.assertIn(
            "setData(Object object, CharSequence name, CharSequence status, int resId, boolean divider)",
            cell,
        )
        self.assertIn("controller.bind(this, requestedPeer);", cell)
        self.assertIn("public void recycle()", cell)
        self.assertIn("controller.unbind(this);", cell)
        self.assertIn("renderer.bind(null, false);", cell)
        self.assertIn("nameTextView.getLeftDrawable()", cell)
        self.assertIn("nameTextView.getRightDrawable()", cell)
        self.assertIn("nameTextView.getRightDrawable2()", cell)
        self.assertIn("nameTextView.getLeft() + nameTextView.getPaddingLeft()", cell)
        self.assertIn("nameTextView.getRight() - nameTextView.getPaddingRight()", cell)
        self.assertNotIn("UserObject.isUserSelf(user)", cell)
        self.assertIn(
            "if (holder.itemView instanceof FluxGramProfileMemberUserCell)",
            (JAVA / "ui/ProfileActivity.java").read_text(),
        )
        self.assertEqual(generic.count("protected void setCurrentAccount(int account)"), 1)
        self.assertNotIn("FluxGramProfileMember", generic)

    def test_profile_inline_members_use_one_visible_participant_mapping(self):
        activity = (JAVA / "ui/ProfileActivity.java").read_text()

        self.assertEqual(activity.count("getVisibleChatParticipant(position)"), 3)
        self.assertIn("visibleSortedUsers.get(index)", activity)
        self.assertIn("visibleChatParticipants.get(index)", activity)
        self.assertNotIn(
            "chatInfo.participants.participants.get(sortedUsers.get(position - membersStartRow))",
            activity,
        )
        self.assertNotIn(
            "visibleChatParticipants.get(sortedUsers.get(position - membersStartRow))",
            activity,
        )

    def test_profile_inline_self_row_is_enabled_and_opens_my_profile(self):
        activity = (JAVA / "ui/ProfileActivity.java").read_text()

        self.assertIn('args.putBoolean("preload_messages", true);', activity)
        self.assertIn('args.putBoolean("my_profile", true);', activity)
        self.assertIn(
            "presentFragment(self ? new ProfileActivity(args, null) : new ProfileActivity(args));",
            activity,
        )
        self.assertIn(
            "return position >= membersStartRow && position < membersEndRow;",
            activity,
        )
        self.assertIn("if (self) {\n                canEditAdmin = false;", activity)
        self.assertIn("allowKick = false;", activity)
        self.assertIn("canRestrict = false;", activity)
        self.assertIn("canEditTag = true;", activity)

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

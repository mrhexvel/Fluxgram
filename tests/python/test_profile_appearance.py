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
                str(UI / "FluxGramDialogListAppearanceController.java"),
                str(UI / "FluxGramUserListAppearanceController.java"),
                str(UI / "UserListBadgePlacement.java"),
                str(ROOT / "tests/java/UserListAppearanceControllerCheck.java"),
            ], check=True, capture_output=True)
            subprocess.run([
                "java", "-cp", directory, "UserListAppearanceControllerCheck",
            ], check=True, capture_output=True)

    def test_main_dialog_badges_are_opt_in_and_fail_closed(self):
        activity = (JAVA / "ui/DialogsActivity.java").read_text()
        adapter = (JAVA / "ui/Adapters/DialogsAdapter.java").read_text()
        cell = (JAVA / "ui/Cells/FluxGramDialogCell.java").read_text()
        generic_cell = (JAVA / "ui/Cells/DialogCell.java").read_text()
        controller = (UI / "FluxGramDialogListAppearanceController.java").read_text()

        self.assertIn("FluxGramDialogListAppearanceController fluxGramAppearanceController", activity)
        self.assertIn("AppearanceRepositories.get(currentAccount)", activity)
        self.assertIn("initialDialogsType == DIALOGS_TYPE_DEFAULT", activity)
        self.assertIn("!onlySelect", activity)
        self.assertIn("searchString == null", activity)
        self.assertIn("(folderId == 0 || folderId == 1)", activity)
        self.assertIn("communityId == 0", activity)
        self.assertIn("viewPage.dialogsAdapter.setFluxGramAppearanceController(fluxGramAppearanceController);", activity)
        self.assertIn("transitionPage.animationSupportDialogsAdapter.setFluxGramAppearanceController(fluxGramAppearanceController);", activity)
        self.assertEqual(activity.count("fluxGramAppearanceController.destroy();"), 2)

        self.assertIn("viewType == VIEW_TYPE_DIALOG && fluxGramAppearanceController != null", adapter)
        self.assertIn("new FluxGramDialogCell(", adapter)
        self.assertIn("recycleAppearance();", adapter)
        self.assertIn("extends DialogCell", cell)
        self.assertIn("DialogObject.isUserDialog(dialogId)", cell)
        self.assertIn("DialogObject.isChatDialog(dialogId)", cell)
        self.assertIn("UserObject.isUserSelf(user)", cell)
        self.assertIn("ChatObject.isChannelAndNotMegaGroup(chat)", cell)
        self.assertIn("ChatObject.isCommunity(chat)", cell)
        self.assertIn("peer.getType() == FluxGramPeerKey.Type.GROUP", cell)
        self.assertIn("protected void drawAdditionalNameStatus(", generic_cell)
        self.assertNotIn("FluxGram", generic_cell)
        self.assertIn("MAX_BATCH_SIZE = 100", controller)
        self.assertIn("MAX_ENTRIES = 512", controller)
        self.assertIn("queuedPeers.remove(peer)", controller)
        self.assertIn("lookup.getAccount() != account", controller)
        self.assertIn("peer.equals(target.getRequestedPeer())", controller)

    def test_dialog_badge_render_path_has_no_lookup_or_layout_mutation(self):
        cell = (JAVA / "ui/Cells/FluxGramDialogCell.java").read_text()
        renderer = (UI / "FluxGramUserListBadgeRenderer.java").read_text()

        draw_method = cell.split("protected void drawAdditionalNameStatus(", 1)[1].split("@Override", 1)[0]
        self.assertNotIn("MessagesController", draw_method)
        self.assertNotIn("controller.bind", draw_method)
        self.assertNotIn("requestLayout", draw_method)
        self.assertNotIn("notifyDataSetChanged", draw_method)
        self.assertIn("renderer.draw(", draw_method)
        self.assertIn("renderer.attach();", cell)
        self.assertIn("controller.unbind(this);", cell)
        self.assertIn("renderer.detach();", cell)
        self.assertIn("renderer.destroy();", cell)
        self.assertIn("LiteMode.FLAG_PARTICLES", renderer)

    def test_archive_dialog_badges_use_separate_fragment_and_skip_synthetic_rows(self):
        activity = (JAVA / "ui/DialogsActivity.java").read_text()
        adapter = (JAVA / "ui/Adapters/DialogsAdapter.java").read_text()
        cell = (JAVA / "ui/Cells/FluxGramDialogCell.java").read_text()

        self.assertIn('args.putInt("folderId", dialogFolder.folder.id);', activity)
        self.assertIn("presentFragment(new DialogsActivity(args));", activity)
        self.assertIn("return folderId == 1;", activity)
        self.assertIn("dialogsCount == 0 && parentFragment.isArchive()", adapter)
        self.assertIn("new ItemInternal(VIEW_TYPE_ARCHIVE_FULLSCREEN)", adapter)
        self.assertIn("viewType == VIEW_TYPE_DIALOG && fluxGramAppearanceController != null", adapter)
        self.assertIn("if (!DialogObject.isChatDialog(dialogId))", cell)
        self.assertNotIn("FluxGramDialogCell", (JAVA / "ui/Cells/ArchiveHintCell.java").read_text())

    def test_main_search_badges_are_opt_in_and_use_a_separate_controller(self):
        activity = (JAVA / "ui/DialogsActivity.java").read_text()
        adapter = (JAVA / "ui/Adapters/DialogsSearchAdapter.java").read_text()
        profile_cell = (JAVA / "ui/Cells/FluxGramDialogsSearchProfileCell.java").read_text()
        message_cell = (JAVA / "ui/Cells/FluxGramDialogsSearchMessageCell.java").read_text()
        hint_cell = (JAVA / "ui/Cells/FluxGramDialogsSearchHintCell.java").read_text()
        generic_hint = (JAVA / "ui/Cells/HintDialogCell.java").read_text()
        share_alert = (JAVA / "ui/Components/ShareAlert.java").read_text()

        self.assertIn("FluxGramDialogListAppearanceController fluxGramSearchAppearanceController", activity)
        self.assertIn("isFluxGramSearchAppearanceEligible()", activity)
        search_eligibility = activity.split("private boolean isFluxGramSearchAppearanceEligible()", 1)[1].split("private FluxGramDialogListAppearanceController", 1)[0]
        self.assertIn("initialDialogsType == DIALOGS_TYPE_DEFAULT", search_eligibility)
        self.assertIn("!onlySelect", search_eligibility)
        self.assertIn("searchString == null", search_eligibility)
        self.assertIn("folderId == 0", search_eligibility)
        self.assertNotIn("folderId == 1", search_eligibility)
        self.assertIn("communityId == 0", search_eligibility)
        self.assertIn("dialogsSearchAdapter.setFluxGramAppearanceController(fluxGramSearchAppearanceController)", activity)
        self.assertEqual(activity.count("fluxGramSearchAppearanceController.destroy();"), 2)

        self.assertIn("case VIEW_TYPE_PROFILE_CELL:", adapter)
        self.assertIn("new FluxGramDialogsSearchProfileCell(", adapter)
        self.assertIn("case VIEW_TYPE_DIALOG_CELL:", adapter)
        self.assertIn("new FluxGramDialogsSearchMessageCell(", adapter)
        self.assertIn("case VIEW_TYPE_PUBLIC_POST:", adapter)
        self.assertIn("new FluxGramDialogsSearchHintCell(", adapter)
        self.assertIn("case VIEW_TYPE_INVITE_CONTACT_CELL:", adapter)
        self.assertIn("recycleAppearance();", adapter)

        self.assertIn("sponsored ? null : peerFor", profile_cell)
        self.assertIn("encryptedChat != null || saved", profile_cell)
        self.assertIn("ChatObject.isChannelAndNotMegaGroup(chat)", profile_cell)
        self.assertIn("messageObject.getDialogId()", adapter)
        self.assertIn("requestedPeer = useFromUserAsAvatar", message_cell)
        self.assertIn("FluxGramDialogCell.peerFor(account, dialogId)", message_cell)
        self.assertIn("FluxGramDialogCell.peerFor(account, dialogId)", hint_cell)
        self.assertIn("protected void drawAfterName(", generic_hint)
        self.assertNotIn("FluxGram", share_alert)

    def test_main_search_badge_render_paths_have_no_lookup_or_layout_mutation(self):
        for file_name, method_name in (
                ("FluxGramDialogsSearchProfileCell.java", "protected void drawAfterNameStatus("),
                ("FluxGramDialogsSearchMessageCell.java", "protected void drawAdditionalNameStatus("),
                ("FluxGramDialogsSearchHintCell.java", "protected void drawAfterName("),
        ):
            cell = (JAVA / "ui/Cells" / file_name).read_text()
            draw_method = cell.split(method_name, 1)[1].split("@Override", 1)[0]

            self.assertNotIn("MessagesController", draw_method)
            self.assertNotIn("controller.bind", draw_method)
            self.assertNotIn("requestLayout", draw_method)
            self.assertNotIn("notifyDataSetChanged", draw_method)
            self.assertIn("renderer.draw(", draw_method)
            self.assertIn("controller.unbind(this);", cell)
            self.assertIn("renderer.detach();", cell)
            self.assertIn("renderer.destroy();", cell)

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

    def test_group_settings_chat_users_origin_is_explicit_and_unique(self):
        edit_activity = (JAVA / "ui/ChatEditActivity.java").read_text()
        activity = (JAVA / "ui/ChatUsersActivity.java").read_text()

        marker = "args.putBoolean(ChatUsersActivity.ARG_FLUXGRAM_GROUP_SETTINGS, true);"
        self.assertEqual(edit_activity.count(marker), 2)
        self.assertIn('public static final String ARG_FLUXGRAM_GROUP_SETTINGS = "fluxgram_group_settings";', activity)
        self.assertIn("arguments.getBoolean(ARG_FLUXGRAM_GROUP_SETTINGS)", activity)

        for creation_site in (JAVA / "ui").rglob("*.java"):
            if creation_site.name != "ChatEditActivity.java":
                self.assertNotIn(marker, creation_site.read_text(), str(creation_site))

    def test_group_settings_chat_users_badges_are_fail_closed_and_screen_scoped(self):
        activity = (JAVA / "ui/ChatUsersActivity.java").read_text()

        self.assertIn("private boolean isFluxGramAppearanceEligible()", activity)
        self.assertIn("currentChat != null", activity)
        self.assertIn("currentChat.id == chatId", activity)
        self.assertIn("chatId > 0", activity)
        self.assertIn("selectType == SELECT_TYPE_MEMBERS", activity)
        self.assertIn("(type == TYPE_USERS || type == TYPE_ADMIN)", activity)
        self.assertIn("!transfer", activity)
        self.assertIn("!needOpenSearch", activity)
        self.assertIn("!ChatObject.isCommunity(currentChat)", activity)
        self.assertIn("!ChatObject.isChannelAndNotMegaGroup(currentChat)", activity)
        self.assertIn("!currentChat.gigagroup", activity)
        self.assertIn("!currentChat.broadcast", activity)
        self.assertIn("AppearanceRepositories.get(currentAccount)", activity)
        self.assertIn("listView.postOnAnimation(runnable);", activity)
        self.assertIn("listView.removeCallbacks(runnable);", activity)
        self.assertIn("catch (IllegalStateException exception)", activity)
        self.assertIn("FileLog.e(exception);", activity)
        self.assertEqual(activity.count("fluxGramAppearanceController.destroy();"), 2)

    def test_group_settings_chat_users_main_and_search_use_opt_in_cell(self):
        activity = (JAVA / "ui/ChatUsersActivity.java").read_text()

        self.assertEqual(activity.count("new FluxGramChatUsersUserCell("), 2)
        self.assertEqual(activity.count("fluxGramAppearanceController == null"), 2)
        self.assertIn("? new ManageChatUserCell(mContext, 2, 2, selectType == SELECT_TYPE_MEMBERS)", activity)
        self.assertIn("? new ManageChatUserCell(mContext, type == TYPE_BANNED || type == TYPE_KICKED ? 7 : 6", activity)
        self.assertEqual(activity.count("if (holder.itemView instanceof ManageChatUserCell)"), 2)

    def test_group_settings_chat_users_cell_lifecycle_filters_and_geometry(self):
        cell = (JAVA / "ui/Cells/FluxGramChatUsersUserCell.java").read_text()
        manage_cell = (JAVA / "ui/Cells/ManageChatUserCell.java").read_text()

        self.assertIn("extends ManageChatUserCell", cell)
        self.assertIn("int account,", cell)
        self.assertIn("int avatarPadding,", cell)
        self.assertIn("int namePadding,", cell)
        self.assertIn("boolean needOption,", cell)
        self.assertIn("Theme.ResourcesProvider resourcesProvider,", cell)
        self.assertIn("controller.bind(this, requestedPeer);", cell)
        self.assertIn("controller.unbind(this);", cell)
        self.assertIn("renderer.bind(null, false);", cell)
        self.assertIn("super.recycle();", cell)
        self.assertIn("renderer.attach();", cell)
        self.assertIn("renderer.detach();", cell)
        self.assertIn("renderer.destroy();", cell)
        self.assertIn("UserObject.isDeleted(user)", cell)
        self.assertIn("UserObject.isReplyUser(user)", cell)
        self.assertIn("UserObject.isAnonymous(user)", cell)
        self.assertIn("UserObject.isService(user.id)", cell)
        self.assertIn("user.id == UserObject.VERIFY", cell)
        self.assertIn("user.id == UserObject.OAUTH", cell)
        self.assertNotIn("UserObject.isUserSelf(user)", cell)
        self.assertIn("getNameTextView()", cell)
        self.assertIn("nameTextView.getLeftDrawable()", cell)
        self.assertIn("nameTextView.getRightDrawable()", cell)
        self.assertIn("nameTextView.getRightDrawable2()", cell)
        self.assertIn("nameTextView.getLeft() + nameTextView.getPaddingLeft()", cell)
        self.assertIn("nameTextView.getRight() - nameTextView.getPaddingRight()", cell)
        self.assertEqual(manage_cell.count("protected SimpleTextView getNameTextView()"), 1)
        self.assertNotIn("FluxGram", manage_cell)
        self.assertNotIn("dispatchDraw", manage_cell)

    def test_group_settings_chat_users_preserve_upstream_self_search_and_loading(self):
        activity = (JAVA / "ui/ChatUsersActivity.java").read_text()

        self.assertIn("searchAdapterHelper = new SearchAdapterHelper(true);", activity)
        self.assertEqual(
            activity.count("if (user.id == getUserConfig().getClientUserId()) {"),
            2,
        )
        self.assertIn("if (user.self) {\n                        return false;", activity)
        self.assertIn("if (peerId == getUserConfig().getClientUserId()) {\n                            return;", activity)
        self.assertIn('args.putLong("user_id", peerId);', activity)
        self.assertIn("presentFragment(new ProfileActivity(args));", activity)
        self.assertIn("if (loadingUsers && !firstLoaded)", activity)
        self.assertIn("loadingUserCellRow = rowCount++;", activity)
        self.assertIn("view = new LoadingCell(mContext, dp(40), dp(120));", activity)
        self.assertNotIn("my_profile", activity)
        self.assertNotIn("new ProfileActivity(args, null)", activity)

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

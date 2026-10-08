from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"
RES = ROOT / "TMessagesProj/src/main/res"


class GhostModeSettingsTest(unittest.TestCase):
    def test_preferences_are_account_and_owner_scoped_with_safe_defaults(self):
        source = (JAVA / "messenger/fluxgram/GhostModeSettings.java").read_text()

        self.assertIn('"fluxgram_ghost_mode_" + account', source)
        self.assertIn("getClientUserId()", source)
        self.assertIn("preferences.getLong(OWNER, 0) != owner", source)
        self.assertIn("preferences.edit().clear().putLong(OWNER, owner).apply()", source)
        self.assertIn("preferences.getBoolean(key, defaultValue)", source)

        for key in (
            "enabled",
            "hide_online",
            "hide_typing",
            "hide_read_receipts",
            "hide_story_views",
            "automatic_offline",
            "read_on_actions",
            "use_send_delay",
            "silent_send_mode",
            "suggest_for_stories",
        ):
            self.assertIn(f'"{key}"', source)

        self.assertIn("return get(account, READ_ON_ACTIONS, true);", source)
        self.assertIn("return get(account, SUGGEST_FOR_STORIES, true);", source)
        self.assertIn("return getInt(account, SILENT_SEND_MODE, SILENT_SEND_NEVER);", source)

    def test_logout_clears_ghost_mode_preferences(self):
        storage = (JAVA / "messenger/MessagesStorage.java").read_text()
        cleanup = storage[storage.index("    public void cleanup(boolean isLogin)"):
                          storage.index("    public void saveSecretParams", storage.index("    public void cleanup(boolean isLogin)"))]
        settings = (JAVA / "messenger/fluxgram/GhostModeSettings.java").read_text()

        self.assertIn("GhostModeSettings.clearAccount(currentAccount);", cleanup)
        self.assertIn("GhostModeReadState.clearAccount(account);", settings)

    def test_screen_uses_five_item_expandable_menu_and_applies_online_changes(self):
        source = (JAVA / "ui/FluxGramGhostModeActivity.java").read_text()

        self.assertIn("UItem.asExpandableSwitch(", source)
        self.assertIn('selected + "/5"', source)
        self.assertIn(".setCollapsed(!expanded)", source)
        self.assertIn("expanded = !expanded;", source)
        self.assertIn("GhostModeSettings.setEnabled(currentAccount, !GhostModeSettings.isEnabled(currentAccount));", source)
        self.assertIn("MessagesController.getInstance(currentAccount).updateGhostModeOnlineStatus();", source)
        self.assertIn("if (expanded)", source)
        self.assertIn("UItem.asCheck(AUTOMATIC_OFFLINE", source)
        self.assertNotIn("if (!GhostModeSettings.isEnabled(currentAccount))", source)
        self.assertNotIn("ConnectionsManager", source)

    def test_extended_screen_has_compact_secondary_settings(self):
        source = (JAVA / "ui/FluxGramGhostModeActivity.java").read_text()

        self.assertIn("UItem.asCheck(READ_ON_ACTIONS", source)
        self.assertIn("UItem.asCheck(USE_SEND_DELAY", source)
        self.assertIn("UItem.asButton(\n                SILENT_SEND_MODE", source)
        self.assertIn("UItem.asCheck(SUGGEST_FOR_STORIES", source)
        self.assertIn("GhostModeSettings.setReadsOnActions", source)
        self.assertIn("GhostModeSettings.setUsesSendDelay", source)
        self.assertIn("GhostModeSettings.setSilentSendMode", source)
        self.assertIn("GhostModeSettings.setSuggestsForStories", source)
        self.assertIn("new AlertDialog.Builder(getParentActivity(), getResourceProvider())", source)

    def test_online_policy_requires_master_and_child_switches(self):
        source = (JAVA / "messenger/fluxgram/GhostModeSettings.java").read_text()
        start = source.index("    public static boolean shouldHideOnline(")
        end = source.index("    public static boolean hidesTyping(", start)

        self.assertIn("return isEnabled(account) && hidesOnline(account);", source[start:end])

    def test_read_policy_requires_master_and_child_switches(self):
        source = (JAVA / "messenger/fluxgram/GhostModeSettings.java").read_text()
        start = source.index("    public static boolean shouldHideReadReceipts(")
        end = source.index("    public static boolean hidesStoryViews(", start)

        self.assertIn("return isEnabled(account) && hidesReadReceipts(account);", source[start:end])

    def test_typing_policy_requires_master_and_child_switches(self):
        source = (JAVA / "messenger/fluxgram/GhostModeSettings.java").read_text()
        start = source.index("    public static boolean shouldHideTypingAction(")
        end = source.index("    public static boolean hidesReadReceipts(", start)
        method = source[start:end]

        self.assertIn("!isEnabled(account) || !hidesTyping(account)", method)
        for action in (0, 1, 3, 4, 5, 7, 8, 9, 10):
            self.assertIn(f"action == {action}", method)
        for action in (2, 6, 11):
            self.assertNotIn(f"action == {action}", method)

    def test_hidden_typing_returns_before_telegram_retry_state_changes(self):
        controller = (JAVA / "messenger/MessagesController.java").read_text()
        start = controller.index("    public boolean sendTyping(long dialogId, long threadMsgId, int action, String emojicon, int classGuid)")
        end = controller.index("    protected void removeDeletedMessagesFromArray", start)
        method = controller[start:end]

        policy = method.index("GhostModeSettings.shouldHideTypingAction(currentAccount, action)")
        retry_state = method.index("LongSparseArray<LongSparseArray<Boolean>> dialogs = sendingTypings[action]")
        request = method.index("TLRPC.TL_messages_setTyping req")

        self.assertLess(policy, retry_state)
        self.assertLess(policy, request)

    def test_interactive_emoji_and_group_call_speaking_keep_direct_paths(self):
        emoji = (JAVA / "ui/EmojiAnimationsOverlay.java").read_text()
        voip = (JAVA / "messenger/voip/VoIPService.java").read_text()

        self.assertIn("TLRPC.TL_sendMessageEmojiInteraction interaction", emoji)
        self.assertIn("TLRPC.TL_messages_setTyping req", emoji)
        self.assertNotIn("GhostModeSettings", emoji)
        self.assertIn("TLRPC.TL_speakingInGroupCallAction", voip)
        self.assertNotIn("GhostModeSettings", voip)

    def test_online_status_is_forced_offline_without_pausing_network(self):
        source = (JAVA / "messenger/MessagesController.java").read_text()
        start = source.index("    public void updateGhostModeOnlineStatus(")
        end = source.index("    private void sendOnlineStatus(", start)
        transition = source[start:end]

        self.assertIn("Utilities.stageQueue.postRunnable", transition)
        self.assertIn("updateOnlineStatus(true);", transition)
        self.assertIn("GhostModeSettings.shouldHideOnline(currentAccount)", transition)
        self.assertIn("boolean active = !hideOnline", transition)
        self.assertIn("boolean pauseExpired = hideOnline", transition)
        self.assertNotIn("setAppPaused", transition)

    def test_status_callbacks_cannot_overwrite_newer_transition(self):
        source = (JAVA / "messenger/MessagesController.java").read_text()
        start = source.index("    private void sendOnlineStatus(")
        end = source.index("    protected void removeDeletedMessagesFromArray", start)
        method = source[start:end]

        self.assertIn("int generation = ++statusRequestGeneration;", method)
        self.assertIn("generation != statusRequestGeneration", method)
        self.assertIn("statusRetryTime = System.currentTimeMillis() + 5000;", method)

    def test_online_status_is_reasserted_after_presence_activity(self):
        controller = (JAVA / "messenger/MessagesController.java").read_text()
        connections = (JAVA / "tgnet/ConnectionsManager.java").read_text()

        self.assertIn("public void reassertGhostModeOfflineStatus()", controller)
        self.assertIn("Utilities.stageQueue.postRunnable(ghostModeOfflineReassertRunnable, 500);", controller)
        self.assertIn("(force || !offlineSent)", controller)
        self.assertIn("offline || GhostModeSettings.shouldHideOnline(currentAccount)", controller)
        self.assertIn("isPresenceActivityRequest(object)", connections)
        self.assertNotIn("object instanceof TLRPC.TL_messages_getHistory", connections)
        self.assertIn("TLRPC.TL_messages_readHistory", connections)
        self.assertIn("TLRPC.TL_channels_readHistory", connections)
        self.assertIn("TLRPC.TL_messages_readMessageContents", connections)
        self.assertIn("TLRPC.TL_channels_readMessageContents", connections)
        self.assertIn("TLRPC.TL_messages_readMentions", connections)
        self.assertIn("TLRPC.TL_messages_sendMessage", connections)
        self.assertIn("TLRPC.TL_messages_sendMedia", connections)
        self.assertIn("TLRPC.TL_messages_sendMultiMedia", connections)
        self.assertIn("getMessagesController().reassertGhostModeOfflineStatus();", connections)

    def test_online_packets_are_forced_offline_at_network_boundary(self):
        source = (JAVA / "tgnet/ConnectionsManager.java").read_text()
        start = source.index("    private void sendRequestInternal(")
        end = source.index("        boolean reassertGhostModeOffline", start)
        boundary = source[start:end]

        self.assertIn("object instanceof TL_account.updateStatus", boundary)
        self.assertIn("GhostModeSettings.shouldHideOnline(currentAccount)", boundary)
        self.assertIn("((TL_account.updateStatus) object).offline = true;", boundary)

    def test_cloud_history_receipts_are_suppressed_without_changing_secret_reads(self):
        source = (JAVA / "messenger/MessagesController.java").read_text()
        start = source.index("    private void completeReadTask(")
        end = source.index("    private void checkReadTasks(", start)
        method = source[start:end]

        policy = method.index("GhostModeSettings.shouldHideReadReceipts(currentAccount)")
        cloud_history = method.index("TLRPC.TL_messages_readHistory")
        secret_history = method.index("TLRPC.TL_messages_readEncryptedHistory")

        self.assertLess(policy, cloud_history)
        self.assertIn("!DialogObject.isEncryptedDialog(task.dialogId)", method[:cloud_history])
        self.assertGreater(secret_history, policy)

    def test_delayed_hidden_read_stays_suppressed_after_setting_is_disabled(self):
        source = (JAVA / "messenger/MessagesController.java").read_text()
        task_start = source.index("    private static class ReadTask {")
        task_end = source.index("    public static class PrintingUser", task_start)
        task = source[task_start:task_end]
        complete_start = source.index("    private void completeReadTask(")
        complete_end = source.index("    private void checkReadTasks(", complete_start)
        complete = source[complete_start:complete_end]
        mark_start = source.index("    public void markDialogAsRead(long dialogId")
        mark_end = source.index("    public void fetchCommunityPendingJoinRequests", mark_start)
        mark = source[mark_start:mark_end]

        self.assertIn("public boolean suppressReceipt;", task)
        self.assertIn("task.suppressReceipt || GhostModeSettings.shouldHideReadReceipts(currentAccount)", complete)
        self.assertIn("currentReadTask.suppressReceipt = suppressReceipt;", mark)
        self.assertLess(
            mark.index("currentReadTask.suppressReceipt = suppressReceipt;"),
            mark.index("completeReadTask(currentReadTask);"),
        )

    def test_local_content_and_mention_updates_run_before_receipt_suppression(self):
        source = (JAVA / "messenger/MessagesController.java").read_text()

        content_start = source.index("    public void markMessageContentAsRead(")
        content_end = source.index("    public void markMentionMessageAsRead(", content_start)
        content = source[content_start:content_end]

        self.assertLess(content.index("GhostModeReadState.recordContent"), content.index("markMessagesContentAsRead"))
        self.assertLess(content.index("markMessagesContentAsRead"), content.index("if (preserveContentRead)"))
        self.assertLess(content.index("NotificationCenter.messagesReadContent"), content.index("if (preserveContentRead)"))

        mention_start = content_end
        mention_end = source.index("    public long createDeleteShowOnceTask(", mention_start)
        mention = source[mention_start:mention_end]

        self.assertLess(
            mention.index("GhostModeReadState.recordContent"),
            mention.index("getMessagesStorage().markMentionMessageAsRead"),
        )
        self.assertLess(
            mention.index("getMessagesStorage().markMentionMessageAsRead"),
            mention.index("if (preserveContentRead)"),
        )

    def test_hidden_content_reads_are_persisted_merged_and_server_acknowledged(self):
        state = (JAVA / "messenger/fluxgram/GhostModeReadState.java").read_text()
        controller = (JAVA / "messenger/MessagesController.java").read_text()
        message_object = (JAVA / "messenger/MessageObject.java").read_text()
        storage = (JAVA / "messenger/MessagesStorage.java").read_text()

        self.assertIn('CONTENT_PREFIX = "content_"', state)
        self.assertIn("GhostModeSettings.shouldHideReadReceipts(account)", state)
        self.assertIn("message instanceof TLRPC.TL_message_secret", state)
        self.assertIn("message.saved_peer_id != null", state)
        self.assertIn("MessageObject.isSecretMedia(message)", state)
        self.assertIn("message.media_unread = false;", state)
        self.assertIn("message.peer_id.channel_id != 0 ? -message.peer_id.channel_id : 0", state)

        self.assertIn("GhostModeReadState.applyContent(currentAccount, messageOwner);", message_object)
        self.assertGreaterEqual(storage.count("GhostModeReadState.applyContent(currentAccount, message);"), 3)
        self.assertIn("GhostModeReadState.acknowledgeContent(currentAccount, 0, update.messages);", controller)
        self.assertIn("GhostModeReadState.acknowledgeContent(currentAccount, -update.channel_id, update.messages);", controller)

    def test_hidden_active_story_state_is_account_owner_scoped_and_monotonic(self):
        source = (JAVA / "messenger/fluxgram/GhostModeStoryState.java").read_text()
        settings = (JAVA / "messenger/fluxgram/GhostModeSettings.java").read_text()

        self.assertIn('"fluxgram_ghost_story_state_" + account', source)
        self.assertIn("getClientUserId()", source)
        self.assertIn("preferences.getLong(OWNER, 0) != owner", source)
        self.assertIn("preferences.edit().clear().putLong(OWNER, owner).apply()", source)
        self.assertIn("if (storyId <= currentStoryId)", source)
        self.assertIn("Math.max(serverMaxId, preferences.getInt(activeKey(dialogId), 0))", source)
        self.assertIn("serverMaxId >= localMaxId", source)
        self.assertIn("SEEN_PREFIX + dialogId + \"_\" + storyId", source)
        self.assertIn("public static synchronized boolean recordSeen", source)
        self.assertIn("public static synchronized boolean isSeen", source)
        self.assertIn("GhostModeStoryState.clearAccount(account);", settings)

    def test_active_story_view_is_hidden_before_telegram_state_and_request(self):
        source = (JAVA / "ui/Stories/StoriesController.java").read_text()
        start = source.index("    public boolean markStoryAsRead(TL_stories.PeerStories")
        end = source.index("    public int getMaxStoriesReadId(", start)
        method = source[start:end]

        hidden = method.index("if (shouldHideStoryView(currentAccount, dialogId, storyItem))")
        telegram_state = method.index("dialogIdToMaxReadId.put(dialogId, newReadId)")
        request = method.index("TL_stories.TL_stories_readStories req")

        self.assertLess(hidden, telegram_state)
        self.assertLess(hidden, request)
        hidden_branch = method[hidden:method.index("        int currentReadId", hidden)]
        self.assertIn("GhostModeStoryState.recordActive", hidden_branch)
        self.assertIn("processReadStories", hidden_branch)
        self.assertIn("NotificationCenter.storiesReadUpdated", hidden_branch)
        self.assertNotIn("dialogIdToMaxReadId.put", hidden_branch)
        self.assertNotIn("storiesStorage.updateMaxReadId", hidden_branch)
        self.assertNotIn("TL_stories_readStories", hidden_branch)

    def test_active_story_effective_cursor_drives_rings_and_navigation(self):
        controller = (JAVA / "ui/Stories/StoriesController.java").read_text()
        peer_view = (JAVA / "ui/Stories/PeerStoriesView.java").read_text()
        profile = (JAVA / "ui/Stories/ProfileStoriesView.java").read_text()
        utilities = (JAVA / "ui/Stories/StoriesUtilities.java").read_text()

        self.assertIn("GhostModeStoryState.mergeActiveMax(currentAccount, dialogId, telegramMaxReadId)", controller)
        self.assertIn("GhostModeStoryState.acknowledgeActive(currentAccount, dialogId, maxStoryId)", controller)
        self.assertIn("storiesController.getMaxStoriesReadId(dialogId)", peer_view)
        self.assertIn("storiesController.getMaxStoriesReadId(dialogId)", profile)
        self.assertIn("storiesController.getMaxStoriesReadId(params.dialogId)", utilities)

    def test_live_and_unresolved_stories_keep_standard_view_path(self):
        controller = (JAVA / "ui/Stories/StoriesController.java").read_text()
        peer_view = (JAVA / "ui/Stories/PeerStoriesView.java").read_text()
        start = controller.index("    private static boolean shouldHideStoryView(")
        end = controller.index("    public int getTotalStoriesCount(", start)
        policy = controller[start:end]

        self.assertIn("storyItem instanceof TL_stories.TL_storyItem", policy)
        self.assertIn("storyItem.media != null", policy)
        self.assertIn("!(storyItem.media instanceof TLRPC.TL_messageMediaVideoStream)", policy)
        self.assertIn("storiesController.shouldMarkStoryAsRead(dialogId, this.storyItem)", peer_view)

    def test_story_lists_hide_increment_views_without_using_telegram_seen_state(self):
        source = (JAVA / "ui/Stories/StoriesController.java").read_text()
        start = source.index("        protected boolean markAsRead(int storyId)", source.index("    public static class StoriesList"))
        end = source.index("        protected boolean canLoad()", start)
        method = source[start:end]

        hidden = method.index("if (shouldHideStoryView(currentAccount, dialogId, storyItem))")
        telegram_seen = method.index("seenStories.add(storyId)")
        request = method.index("TL_stories.TL_stories_incrementStoryViews req")
        hidden_branch = method[hidden:method.index("            if (seenStories.contains", hidden)]

        self.assertLess(hidden, telegram_seen)
        self.assertLess(hidden, request)
        self.assertIn("GhostModeStoryState.recordSeen", hidden_branch)
        self.assertIn("processReadStories", hidden_branch)
        self.assertNotIn("seenStories.add", hidden_branch)
        self.assertNotIn("TL_stories_incrementStoryViews", hidden_branch)
        self.assertLess(method.index("GhostModeSettings.shouldHideStoryViews(currentAccount) && GhostModeStoryState.isSeen"), hidden)

    def test_reopening_story_after_disabling_hiding_uses_telegram_state(self):
        controller = (JAVA / "ui/Stories/StoriesController.java").read_text()
        start = controller.index("    public boolean shouldMarkStoryAsRead(")
        end = controller.index("    private int getTelegramMaxStoriesReadId(", start)
        method = controller[start:end]

        self.assertIn("GhostModeSettings.shouldHideStoryViews(currentAccount)", method)
        self.assertIn("? getMaxStoriesReadId(dialogId)", method)
        self.assertIn(": getTelegramMaxStoriesReadId(dialogId)", method)
        self.assertIn("return storyItem.id > maxReadId;", method)

    def test_mentions_and_live_locations_keep_local_behavior_without_network_receipts(self):
        controller = (JAVA / "messenger/MessagesController.java").read_text()
        location = (JAVA / "messenger/LocationController.java").read_text()

        mentions_start = controller.index("    public void markMentionsAsRead(")
        mentions_end = controller.index("    public void markDialogAsRead(", mentions_start)
        mentions = controller[mentions_start:mentions_end]

        self.assertLess(
            mentions.index("getMessagesStorage().resetMentionsCount"),
            mentions.index("GhostModeSettings.shouldHideReadReceipts(currentAccount)"),
        )

        live_start = location.index("    public void markLiveLoactionsAsRead(")
        live_end = location.index("    public static int getLocationsCount(", live_start)
        live = location[live_start:live_end]

        self.assertLess(
            live.index("GhostModeSettings.shouldHideReadReceipts(currentAccount)"),
            live.index("lastReadLocationTime.put"),
        )

    def test_disappearing_media_receipt_path_is_not_suppressed(self):
        source = (JAVA / "messenger/MessagesController.java").read_text()
        start = source.index("    public void markMessageAsRead2(")
        end = source.index("    public void markMessageAsRead(long dialogId", start)
        method = source[start:end]

        self.assertNotIn("GhostModeSettings", method)
        self.assertIn("TLRPC.TL_channels_readMessageContents", method)
        self.assertIn("TLRPC.TL_messages_readMessageContents", method)

    def test_hidden_read_cursors_are_owner_scoped_and_monotonic(self):
        source = (JAVA / "messenger/fluxgram/GhostModeReadState.java").read_text()

        self.assertIn('"fluxgram_ghost_read_state_" + account', source)
        self.assertIn("getClientUserId()", source)
        self.assertIn("preferences.getLong(OWNER, 0) != owner", source)
        self.assertIn("preferences.edit().clear().putLong(OWNER, owner).apply()", source)
        self.assertIn("if (maxId > currentMaxId)", source)
        self.assertIn("DIALOG_PREFIX + dialogId", source)
        self.assertIn("TOPIC_PREFIX + dialogId + \"_\" + topicId", source)

    def test_server_state_is_clamped_until_it_catches_local_cursor(self):
        source = (JAVA / "messenger/fluxgram/GhostModeReadState.java").read_text()

        dialog_start = source.index("    public static synchronized void applyDialog(")
        dialog_end = source.index("    public static synchronized void applyTopic(", dialog_start)
        dialog = source[dialog_start:dialog_end]

        self.assertIn("serverData && dialog.read_inbox_max_id >= localMaxId", dialog)
        self.assertIn("remove(preferences, key);", dialog)
        self.assertIn("dialog.read_inbox_max_id = localMaxId;", dialog)
        self.assertIn("dialog.unread_count = mergeUnreadCount", dialog)
        self.assertIn("dialog.unread_mark = false;", dialog)

        topic_start = dialog_end
        topic_end = source.index("    public static synchronized int mergeDialogMax(", topic_start)
        topic = source[topic_start:topic_end]

        self.assertIn("serverData && topic.read_inbox_max_id >= localMaxId", topic)
        self.assertIn("topic.read_inbox_max_id = localMaxId;", topic)
        self.assertIn("topic.unread_count = mergeUnreadCount", topic)

        self.assertIn("unreadCount - hiddenUnreadCount", source)
        self.assertNotIn("topMessageId - localMaxId", source)
        self.assertIn("allowParentCursor", source)

    def test_hidden_history_records_cursor_before_queued_local_processing(self):
        source = (JAVA / "messenger/MessagesController.java").read_text()
        start = source.index("    public void markDialogAsRead(")
        end = source.index("    public void fetchCommunityPendingJoinRequests(", start)
        method = source[start:end]

        record = method.index("GhostModeReadState.recordDialog")

        self.assertLess(record, method.index("getMessagesStorage().processPendingRead"))
        self.assertIn("GhostModeSettings.shouldHideReadReceipts(currentAccount)", method[:record])
        self.assertIn("!DialogObject.isEncryptedDialog(dialogId)", method[:record])
        self.assertIn("GhostModeReadState.recordTopic", method)

    def test_dialog_and_topic_loads_merge_persisted_read_cursor(self):
        controller = (JAVA / "messenger/MessagesController.java").read_text()
        storage = (JAVA / "messenger/MessagesStorage.java").read_text()
        topics = (JAVA / "messenger/TopicsController.java").read_text()

        self.assertIn("GhostModeReadState.applyDialog(currentAccount, d, loadType != DIALOGS_LOAD_TYPE_CACHE);", controller)
        self.assertIn("GhostModeReadState.applyDialog(currentAccount, d, !fromCache);", controller)
        self.assertIn("GhostModeReadState.mergeDialogMax(currentAccount, dialogId, update.max_id)", controller)
        self.assertNotIn("GhostModeReadState.applyDialog(currentAccount, dialog, false);", storage)
        self.assertNotIn("GhostModeReadState.applyTopic(currentAccount, dialogId, topicId, topic, false);", storage)
        self.assertIn("GhostModeReadState.applyTopic(currentAccount, -chatId, topicId, newTopic, !fromCache);", topics)
        self.assertIn("GhostModeReadState.applyTopic(currentAccount, -chatId, topicId, topic, serverData);", topics)
        self.assertIn("GhostModeReadState.preserveTopicRead", storage)
        self.assertIn("boolean preserveLocalRead", storage)
        self.assertIn("boolean preserveAllTopics", storage)
        self.assertIn("boolean useQueue, boolean serverData", storage)

        self.assertIn("int serverUnreadCount = dialog.unread_count;", controller)
        self.assertIn("update.still_unread_count = serverUnreadCount;", controller)

    def test_settings_home_has_separate_ghost_and_storage_routes(self):
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()
        home = (JAVA / "ui/Components/FluxGramSettingsHomeView.java").read_text()

        self.assertIn("new FluxGramGhostModeActivity()", activity)
        self.assertIn("new FluxGramStealthActivity()", activity)
        self.assertIn("R.string.FluxGramGhostMode", home)
        self.assertIn("R.string.FluxGramStealth", home)

    def test_english_and_russian_strings_are_complete(self):
        required = {
            "FluxGramGhostMode",
            "FluxGramGhostModeDescription",
            "FluxGramGhostModeDisabledInfo",
            "FluxGramGhostModeHideOnline",
            "FluxGramGhostModeHideTyping",
            "FluxGramGhostModeHideReadReceipts",
            "FluxGramGhostModeHideStoryViews",
            "FluxGramGhostModeAutomaticOffline",
            "FluxGramGhostModeReadOnActions",
            "FluxGramGhostModeReadOnActionsInfo",
            "FluxGramGhostModeUseSendDelay",
            "FluxGramGhostModeUseSendDelayInfo",
            "FluxGramGhostModeSendSilently",
            "FluxGramGhostModeSendSilentlyInfo",
            "FluxGramGhostModeSendSilentlyNever",
            "FluxGramGhostModeSendSilentlyInGhostMode",
            "FluxGramGhostModeSendSilentlyAlways",
            "FluxGramGhostModeSuggestForStories",
            "FluxGramGhostModeSuggestForStoriesInfo",
            "FluxGramGhostModePreviewInfo",
        }

        for path in (RES / "values/strings.xml", RES / "values-ru/strings.xml"):
            names = {item.attrib["name"] for item in ET.parse(path).getroot().findall("string")}

            self.assertTrue(required.issubset(names), path)


if __name__ == "__main__":
    unittest.main()

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
        self.assertIn("preferences.getBoolean(key, false)", source)

        for key in ("enabled", "hide_online", "hide_typing", "hide_read_receipts", "hide_story_views"):
            self.assertIn(f'"{key}"', source)

    def test_logout_clears_ghost_mode_preferences(self):
        storage = (JAVA / "messenger/MessagesStorage.java").read_text()
        cleanup = storage[storage.index("    public void cleanup(boolean isLogin)"):
                          storage.index("    public void saveSecretParams", storage.index("    public void cleanup(boolean isLogin)"))]
        settings = (JAVA / "messenger/fluxgram/GhostModeSettings.java").read_text()

        self.assertIn("GhostModeSettings.clearAccount(currentAccount);", cleanup)
        self.assertIn("GhostModeReadState.clearAccount(account);", settings)

    def test_screen_uses_expandable_menu_and_applies_online_changes(self):
        source = (JAVA / "ui/FluxGramGhostModeActivity.java").read_text()

        self.assertIn("UItem.asExpandableSwitch(", source)
        self.assertIn('selected + "/4"', source)
        self.assertIn(".setCollapsed(!expanded)", source)
        self.assertIn("expanded = !expanded;", source)
        self.assertIn("GhostModeSettings.setEnabled(currentAccount, !GhostModeSettings.isEnabled(currentAccount));", source)
        self.assertIn("MessagesController.getInstance(currentAccount).updateGhostModeOnlineStatus();", source)
        self.assertIn(".setEnabled(enabled)", source)
        self.assertIn("if (expanded)", source)
        self.assertNotIn("ConnectionsManager", source)

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
            "FluxGramGhostModePreviewInfo",
        }

        for path in (RES / "values/strings.xml", RES / "values-ru/strings.xml"):
            names = {item.attrib["name"] for item in ET.parse(path).getroot().findall("string")}

            self.assertTrue(required.issubset(names), path)


if __name__ == "__main__":
    unittest.main()

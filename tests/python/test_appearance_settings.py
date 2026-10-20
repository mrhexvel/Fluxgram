from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"


class AppearanceSettingsTest(unittest.TestCase):
    def test_preferences_are_account_and_owner_scoped(self):
        source = (JAVA / "messenger/fluxgram/AppearanceSettings.java").read_text()

        self.assertIn('"fluxgram_appearance_" + account', source)
        self.assertIn("getClientUserId()", source)
        self.assertIn("preferences.getLong(OWNER, 0) != owner", source)
        self.assertIn("preferences.edit().clear().putLong(OWNER, owner).apply()", source)
        self.assertIn('"title_mode"', source)
        self.assertIn('"hide_action_bar_status"', source)
        self.assertIn('"center_action_bar_title"', source)
        self.assertIn('"hide_stories"', source)
        self.assertIn('"hide_floating_button"', source)
        self.assertIn('"hide_dialogs_search_bar"', source)
        self.assertIn('"sender_mini_avatars"', source)
        self.assertIn('"force_snow"', source)
        self.assertIn('"avatar_corner_radius"', source)
        self.assertIn('"unified_avatar_radius"', source)

    def test_title_modes_have_safe_fallbacks(self):
        source = (JAVA / "messenger/fluxgram/AppearanceSettings.java").read_text()

        self.assertIn("mode < TITLE_APP_NAME || mode > TITLE_CHATS", source)
        self.assertIn("UserObject.getPublicUsername(user)", source)
        self.assertIn("if (!TextUtils.isEmpty(username))", source)
        self.assertIn("return UserObject.getFirstName(user);", source)
        self.assertIn("R.string.FluxGramAppName", source)
        self.assertIn("R.string.FilterChats", source)

    def test_screen_uses_telegram_preview_and_controls(self):
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()
        preview = (JAVA / "ui/Components/FluxGramChatListPreviewCell.java").read_text()

        self.assertIn("new FluxGramChatListPreviewCell(", activity)
        self.assertLess(
            activity.index("appearancePreview = new FluxGramChatListPreviewCell("),
            activity.index("listView = new UniversalRecyclerView("),
        )
        self.assertIn("UItem.asButton(\n                    ACTION_BAR_TITLE", activity)
        self.assertIn("UItem.asCheck(\n                        HIDE_ACTION_BAR_STATUS", activity)
        self.assertIn("UItem.asCheck(\n                    CENTER_ACTION_BAR_TITLE", activity)
        self.assertIn("UItem.asCheck(\n                    HIDE_STORIES", activity)
        self.assertIn("UItem.asCheck(\n                    HIDE_FLOATING_BUTTON", activity)
        self.assertIn("UItem.asCheck(\n                    HIDE_DIALOGS_SEARCH_BAR", activity)
        self.assertIn("UItem.asCheck(\n                    SENDER_MINI_AVATARS", activity)
        self.assertIn("UItem.asCheck(\n                    FORCE_SNOW", activity)
        self.assertIn("addSectionLabel(items, R.string.FluxGramFontsAndEmoji);", activity)
        fonts_and_emoji = activity.split(
            "addSectionLabel(items, R.string.FluxGramFontsAndEmoji);", 1
        )[1].split("addSectionLabel(items, R.string.FluxGramChatList);", 1)[0]
        self.assertIn("UItem.asButton(\n                    FONTS", fonts_and_emoji)
        self.assertIn("UItem.asCheck(\n                    USE_SYSTEM_EMOJI", fonts_and_emoji)
        self.assertEqual(1, fonts_and_emoji.count("UItem.asShadow(null)"))
        self.assertIn("addSectionLabel(items, R.string.FluxGramChatList);", activity)
        self.assertIn("UItem.asCustomShadow(label, LayoutHelper.WRAP_CONTENT)", activity)
        self.assertNotIn("UItem.asHeader", activity)
        self.assertIn("new ActionBar(context, resourcesProvider)", preview)
        self.assertIn("new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable", preview)
        self.assertIn("statusDrawable.attach();", preview)
        self.assertIn("statusDrawable.detach();", preview)

    def test_system_emoji_reuses_shared_config(self):
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()
        appearance = (JAVA / "messenger/fluxgram/AppearanceSettings.java").read_text()
        shared = (JAVA / "messenger/SharedConfig.java").read_text()
        emoji = (JAVA / "messenger/Emoji.java").read_text()

        self.assertIn(".setChecked(SharedConfig.useSystemEmoji)", activity)
        self.assertIn("SharedConfig.toggleUseSystemEmoji();", activity)
        self.assertIn('editor.putBoolean("useSystemEmoji", useSystemEmoji);', shared)
        self.assertIn("if (SharedConfig.useSystemEmoji", emoji)
        self.assertNotIn("useSystemEmoji", appearance)

    def test_main_dialogs_title_hook_is_fail_closed(self):
        source = (JAVA / "ui/DialogsActivity.java").read_text()
        stories = (JAVA / "ui/fluxgram/appearance/FluxGramDialogStoriesCell.java").read_text()
        eligibility = source.split(
            "private boolean usesFluxGramRootDialogsAppearance()", 1
        )[1].split("private int getSearchFieldHeight()", 1)[0]

        self.assertIn("initialDialogsType == DIALOGS_TYPE_DEFAULT", eligibility)
        self.assertIn("!onlySelect", eligibility)
        self.assertIn("folderId == 0", eligibility)
        self.assertIn("communityId == 0", eligibility)
        self.assertIn("searchString == null", eligibility)
        self.assertIn("!inPreviewMode", eligibility)
        self.assertIn("AppearanceSettings.getActionBarTitle(currentAccount)", source)
        self.assertIn("AppearanceSettings.hidesActionBarStatus(currentAccount)", source)
        self.assertIn("AppearanceSettings.centersActionBarTitle(currentAccount)", source)
        self.assertIn("new FluxGramDialogStoriesCell(", source)
        self.assertIn("AppearanceSettings.getActionBarTitle(account)", stories)
        self.assertIn("AppearanceSettings.hidesActionBarStatus(account)", stories)
        self.assertIn("AppearanceSettings.centersActionBarTitle(account)", stories)

    def test_collapsed_header_override_preserves_telegram_default(self):
        source = (JAVA / "ui/Stories/DialogStoriesCell.java").read_text()

        self.assertIn("protected CharSequence getCollapsedHeaderTitleOverride()", source)
        self.assertIn("return null;", source)
        self.assertIn("collapsedHeaderOverrideActive", source)
        self.assertIn("isCollapsedHeaderStatusHidden() ? 0 : titleAlpha", source)

    def test_action_bar_centering_uses_symmetric_bounds(self):
        source = (JAVA / "ui/ActionBar/ActionBar.java").read_text()

        self.assertIn("public void setTitleCentered(boolean centered)", source)
        self.assertIn("width - Math.max(leftInset, rightInset) * 2", source)
        self.assertIn("(getMeasuredWidth() - titleTextView[i].getMeasuredWidth()) / 2", source)

    def test_root_dialog_appearance_hooks_are_bounded(self):
        activity = (JAVA / "ui/DialogsActivity.java").read_text()
        adapter = (JAVA / "ui/Adapters/DialogsAdapter.java").read_text()
        empty_cell = (JAVA / "ui/Cells/DialogsEmptyCell.java").read_text()

        self.assertIn("AppearanceSettings.hidesStories(currentAccount)", activity)
        self.assertIn("AppearanceSettings.hidesFloatingButton(currentAccount)", activity)
        self.assertIn("|| AppearanceSettings.hidesFloatingButton(currentAccount))))", activity)
        self.assertIn("adapter.setFluxGramAppearanceOptions(", activity)
        self.assertIn("&& !fluxGramHideComposeHints", adapter)
        self.assertIn("cell.setHideNewChatHelp(fluxGramHideComposeHints)", adapter)
        self.assertIn("hideNewChatHelp && welcome ? GONE : VISIBLE", empty_cell)

    def test_hidden_search_uses_dynamic_layout_height(self):
        source = (JAVA / "ui/DialogsActivity.java").read_text()
        height = source.split("private int getSearchFieldHeight()", 1)[1].split(
            "private void configureFluxGramAppearance", 1
        )[0]

        self.assertIn("AppearanceSettings.hidesDialogsSearchBar(currentAccount)", height)
        self.assertIn("animatorSearchVisible.getFloatValue()", height)
        self.assertIn("h += getSearchFieldHeight()", source)
        self.assertIn("return dp(DialogStoriesCell.HEIGHT_IN_DP) + getSearchFieldHeight()", source)
        self.assertIn("fragmentView.requestLayout();", source)

    def test_sender_avatars_are_owned_by_opt_in_cell(self):
        base = (JAVA / "ui/Cells/DialogCell.java").read_text()
        cell = (JAVA / "ui/Cells/FluxGramDialogCell.java").read_text()

        self.assertIn("protected CharSequence decorateMessageSender(", base)
        self.assertNotIn("new AvatarSpan(this, currentAccount, 18)", base)
        self.assertIn("new AvatarSpan(this, account, 18)", cell)
        self.assertIn("getMessageSenderAvatarDialogId(messageObject)", cell)
        self.assertIn("currentDialogId == UserObject.VERIFY", base)
        self.assertIn("forwarded.from_name != null", base)
        self.assertIn("isMessageSenderAvatarPeerAvailable(fromId)", base)
        self.assertIn("getClientUserId()", base)

    def test_forced_snow_is_action_bar_scoped(self):
        action_bar = (JAVA / "ui/ActionBar/ActionBar.java").read_text()
        activity = (JAVA / "ui/DialogsActivity.java").read_text()
        theme = (JAVA / "ui/ActionBar/Theme.java").read_text()

        self.assertIn("public void setForceHolidayImage(boolean value)", action_bar)
        self.assertIn("forceHolidayImage ? forcedHolidayDrawable", action_bar)
        self.assertIn("titlesContainer == null ? titleView.getScaleY()", action_bar)
        self.assertIn("titlesContainer == null ? 1f : titlesContainer.getAlpha()", action_bar)
        self.assertIn("AppearanceSettings.forcesSnow(currentAccount)", activity)
        self.assertNotIn("AppearanceSettings", theme)

    def test_avatar_geometry_resolver_preserves_stock_defaults(self):
        geometry = (JAVA / "messenger/fluxgram/AvatarGeometry.java").read_text()
        settings = (JAVA / "messenger/fluxgram/AppearanceSettings.java").read_text()

        self.assertIn("public static final int MAX_CORNER_RADIUS = 28;", geometry)
        self.assertIn("if (setting == MAX_CORNER_RADIUS && !unified)", geometry)
        self.assertIn("return stockRadius;", geometry)
        self.assertIn("radius *= 4f / 7f;", geometry)
        self.assertIn("radius *= 3f / 7f;", geometry)
        self.assertIn("radius /= MAX_CORNER_RADIUS;", geometry)
        self.assertIn("Math.max(0, Math.min(avatarSize / 2, Math.round(radius)))", geometry)
        self.assertIn(
            "getInt(account, AVATAR_CORNER_RADIUS, AvatarGeometry.MAX_CORNER_RADIUS)",
            settings,
        )
        self.assertIn("radius < 0 || radius > AvatarGeometry.MAX_CORNER_RADIUS", settings)

    def test_avatar_geometry_is_limited_to_root_archive_and_forward_selector(self):
        activity = (JAVA / "ui/DialogsActivity.java").read_text()
        adapter = (JAVA / "ui/Adapters/DialogsAdapter.java").read_text()
        base = (JAVA / "ui/Cells/DialogCell.java").read_text()
        cell = (JAVA / "ui/Cells/FluxGramDialogCell.java").read_text()

        eligibility = activity.split(
            "private boolean usesFluxGramDialogAvatarGeometry()", 1
        )[1].split("private int getSearchFieldHeight()", 1)[0]
        self.assertIn("usesFluxGramRootDialogsAppearance()", eligibility)
        self.assertIn("usesFluxGramSelectorAvatarGeometry()", eligibility)
        self.assertIn("initialDialogsType == DIALOGS_TYPE_DEFAULT", eligibility)
        self.assertIn("!onlySelect", eligibility)
        self.assertIn("folderId == 1", eligibility)
        self.assertIn("communityId == 0", eligibility)
        self.assertIn("searchString == null", eligibility)
        self.assertIn("!inPreviewMode", eligibility)
        configure = activity.split(
            "private void configureFluxGramAppearance(DialogsAdapter adapter)", 1
        )[1].split("private void applyFluxGramDialogsAppearance()", 1)[0]
        self.assertIn("boolean enabled = usesFluxGramRootDialogsAppearance();", configure)
        self.assertIn("usesFluxGramDialogAvatarGeometry()", configure)
        self.assertIn("fluxGramAvatarGeometry = avatarGeometry;", adapter)
        self.assertIn("setAvatarGeometryEnabled(fluxGramAvatarGeometry);", adapter)
        self.assertIn("protected int resolveAvatarRoundRadius(", base)
        self.assertIn("return stockRadius;", base)
        self.assertNotIn("AvatarGeometry", base)
        selector = activity.split(
            "private boolean usesFluxGramSelectorAvatarGeometry()", 1
        )[1].split("private int getSearchFieldHeight()", 1)[0]
        self.assertIn("initialDialogsType == DIALOGS_TYPE_FORWARD", selector)
        self.assertIn("&& onlySelect", selector)
        self.assertIn("&& folderId == 0", selector)
        self.assertIn("&& communityId == 0", selector)
        self.assertIn("&& searchString == null", selector)
        self.assertIn("&& !inPreviewMode", selector)
        self.assertIn("fluxGramAppearanceController != null || fluxGramAvatarGeometry", adapter)
        self.assertIn("!isAvatarGeometryDialog()", cell)
        self.assertIn("!UserObject.isUserSelf(user)", cell)
        self.assertIn("AvatarGeometry.resolveRadius(account", cell)

    def test_avatar_geometry_profile_surface_is_fail_closed(self):
        profile = (JAVA / "ui/ProfileActivity.java").read_text()
        stories = (JAVA / "ui/Stories/ProfileStoriesView.java").read_text()
        radial_progress = (JAVA / "ui/Components/RadialProgress.java").read_text()

        eligibility = profile.split(
            "private boolean isFluxGramAvatarGeometryEligible()", 1
        )[1].split("private void updateTtlIcon()", 1)[0]
        self.assertIn("if (isTopic)", eligibility)
        self.assertIn("if (userId != 0)", eligibility)
        self.assertIn("!ChatObject.isCommunity(currentChat)", eligibility)
        self.assertIn("!ChatObject.isChannelAndNotMegaGroup(currentChat)", eligibility)
        self.assertIn("!ChatObject.isForum(currentChat)", eligibility)
        self.assertIn("!ChatObject.isMonoForum(currentChat)", eligibility)
        self.assertIn("!currentChat.gigagroup", eligibility)
        self.assertIn("!currentChat.broadcast", eligibility)
        self.assertIn("AvatarGeometry.ROLE_PROFILE", profile)
        self.assertIn("AvatarGeometry.usesStockGeometry(currentAccount)", profile)
        self.assertIn("protected float getMainAvatarRoundRectRadius()", stories)
        self.assertIn("setRoundRectProgressRadius(resolveMainAvatarRoundRectRadius(rect2))", stories)
        self.assertIn("private float roundRectProgressRadius = -1;", radial_progress)

    def test_avatar_geometry_chat_header_is_opt_in_and_fail_closed(self):
        activity = (JAVA / "ui/ChatActivity.java").read_text()
        container = (JAVA / "ui/Components/ChatAvatarContainer.java").read_text()
        action_bar = (JAVA / "ui/ActionBar/ActionBar.java").read_text()

        eligibility = activity.split(
            "private boolean isFluxGramChatHeaderAvatarGeometryEligible()", 1
        )[1].split("private void updatePluginChatLifecycle()", 1)[0]
        self.assertIn("chatMode != MODE_DEFAULT", eligibility)
        self.assertIn("currentEncryptedChat != null", eligibility)
        self.assertIn("isInPreviewMode()", eligibility)
        self.assertIn("inBubbleMode", eligibility)
        self.assertIn("isInsideContainer", eligibility)
        self.assertIn("isTopic", eligibility)
        self.assertIn("isComments", eligibility)
        self.assertIn("isThreadChat()", eligibility)
        self.assertIn("isReplyChatComment()", eligibility)
        self.assertIn("threadMessageId != 0", eligibility)
        self.assertIn("quickReplyShortcut != null", eligibility)
        self.assertIn("businessLink != null", eligibility)
        self.assertIn("chatInvite != null", eligibility)
        self.assertIn("startLoadFromDate != 0", eligibility)
        self.assertIn("isInBotForumMode()", eligibility)
        self.assertIn("!ChatObject.isCommunity(currentChat)", eligibility)
        self.assertIn("!ChatObject.isChannelAndNotMegaGroup(currentChat)", eligibility)
        self.assertIn("!ChatObject.isForum(currentChat)", eligibility)
        self.assertIn("!ChatObject.isMonoForum(currentChat)", eligibility)
        self.assertIn("!currentChat.gigagroup", eligibility)
        self.assertIn("!currentChat.broadcast", eligibility)
        self.assertIn("AvatarGeometry.ROLE_HEADER", activity)
        self.assertIn("protected int resolveAvatarRoundRadius(", container)
        self.assertIn("return stockRadius;", container)
        self.assertIn("protected float resolveStoryRoundRectRadius(", container)
        self.assertIn("protected float resolveHeaderBackgroundRadius(", container)
        self.assertIn("return avatarRadius * 23f / 21f;", activity)
        self.assertIn("params.roundRectRadius = storyRoundRectRadius;", container)
        self.assertIn("avatarContainer.setActionBar(actionBar);", activity)
        self.assertIn("actionBar.setGlassTitleLeftRadius(headerBackgroundRadius);", container)
        self.assertIn("glassTitleLeftRadiusOverride", action_bar)
        self.assertIn("glassDrawable.setRadius(leftRadius, dp(23), dp(23), leftRadius);", action_bar)
        self.assertNotIn("AvatarGeometry", container)

    def test_avatar_geometry_main_search_uses_opt_in_cells(self):
        activity = (JAVA / "ui/DialogsActivity.java").read_text()
        adapter = (JAVA / "ui/Adapters/DialogsSearchAdapter.java").read_text()
        profile_base = (JAVA / "ui/Cells/ProfileSearchCell.java").read_text()
        hint_base = (JAVA / "ui/Cells/HintDialogCell.java").read_text()
        profile = (JAVA / "ui/Cells/FluxGramSearchProfileCell.java").read_text()
        message = (JAVA / "ui/Cells/FluxGramSearchMessageCell.java").read_text()
        hint = (JAVA / "ui/Cells/FluxGramDialogsSearchHintCell.java").read_text()
        pager = (JAVA / "ui/Components/SearchViewPager.java").read_text()
        universal = (JAVA / "ui/Components/UniversalAdapter.java").read_text()
        channels = (JAVA / "ui/Components/DialogsChannelsAdapter.java").read_text()
        bots = (JAVA / "ui/Components/DialogsBotsAdapter.java").read_text()

        self.assertIn(
            "usesFluxGramRootDialogsAppearance() || usesFluxGramSelectorAvatarGeometry()",
            activity,
        )
        self.assertIn("private boolean fluxGramAvatarGeometry;", adapter)
        self.assertIn("new FluxGramDialogsSearchProfileCell(", adapter)
        self.assertIn("new FluxGramDialogsSearchMessageCell(", adapter)
        self.assertIn("new FluxGramDialogsSearchHintCell(", adapter)
        self.assertIn("channelsSearchAdapter.setFluxGramAvatarGeometry(enabled);", pager)
        self.assertIn("botsSearchAdapter.setFluxGramAvatarGeometry(enabled);", pager)
        self.assertIn("createProfileSearchCell(context)", universal)
        self.assertIn("createSearchMessageCell(context)", universal)
        self.assertIn("new FluxGramSearchProfileCell(context, currentAccount, true)", channels)
        self.assertIn("new FluxGramSearchMessageCell(context, currentAccount, resourcesProvider, true)", channels)
        self.assertIn("new FluxGramSearchProfileCell(context, currentAccount, true)", bots)
        self.assertIn("new FluxGramSearchMessageCell(context, currentAccount, resourcesProvider, true)", bots)
        self.assertIn("protected int resolveAvatarRoundRadius(", profile_base)
        self.assertIn("return stockRadius;", profile_base)
        self.assertNotIn("AvatarGeometry", profile_base)
        self.assertIn("protected int resolveAvatarRoundRadius(", hint_base)
        self.assertIn("return stockRadius;", hint_base)
        self.assertNotIn("AvatarGeometry", hint_base)
        self.assertIn("isAvatarGeometryPeer(object, encryptedChat, saved)", profile)
        self.assertIn("object instanceof TLRPC.Chat", profile)
        self.assertIn("encryptedChat", profile)
        self.assertIn("AvatarGeometry.resolveRadius(account", profile)
        self.assertIn("useFromUserAsAvatar ? AvatarGeometry.ROLE_USER", message)
        self.assertIn("dialogId < 0 && MessagesController.getInstance(account).getChat(-dialogId) != null", message)
        self.assertIn("isAvatarGeometryPeer(user, chat)", hint)
        self.assertIn("if (chat != null)", hint)
        self.assertIn("requestedPeer = FluxGramDialogCell.peerFor(account, dialogId);", hint)

    def test_avatar_geometry_share_sheet_uses_opt_in_cells(self):
        alert = (JAVA / "ui/Components/ShareAlert.java").read_text()
        base = (JAVA / "ui/Cells/ShareDialogCell.java").read_text()
        dialog = (JAVA / "ui/Cells/FluxGramShareDialogCell.java").read_text()
        profile = (JAVA / "ui/Cells/FluxGramShareProfileCell.java").read_text()
        hint = (JAVA / "ui/Cells/FluxGramShareHintCell.java").read_text()

        self.assertIn("return !darkTheme && !includeStory && storyItem == null;", alert)
        self.assertIn("fluxGramAppearanceController != null || avatarGeometry", alert)
        self.assertIn("fluxGramAppearanceController == null && !avatarGeometry", alert)
        self.assertIn("new FluxGramShareDialogCell(", alert)
        self.assertIn("new FluxGramShareProfileCell(", alert)
        self.assertIn("new FluxGramShareHintCell(", alert)
        self.assertIn("protected int resolveAvatarRoundRadius(", base)
        self.assertIn("return stockRadius;", base)
        self.assertNotIn("AvatarGeometry", base)
        self.assertIn("topicWasVisible", base)
        self.assertIn("extends FluxGramSearchProfileCell", profile)
        self.assertIn("AvatarGeometry.resolveRadius(account", dialog)
        self.assertIn("topicVisible || !isAvatarGeometryPeer", dialog)
        self.assertIn("dialogId == Long.MAX_VALUE", dialog)
        self.assertIn("!UserObject.isUserSelf(user)", dialog)
        self.assertIn("AvatarGeometry.resolveRadius(account", hint)
        self.assertIn("!UserObject.isUserSelf(user)", hint)

    def test_avatar_geometry_preview_uses_shared_resolver(self):
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()
        preview = (
            JAVA / "ui/Components/FluxGramAvatarGeometryPreviewCell.java"
        ).read_text()

        self.assertIn("new FluxGramAvatarGeometryPreviewCell(", activity)
        self.assertIn("addSectionLabel(items, R.string.FluxGramAvatarGeometry);", activity)
        self.assertIn("UItem.asCheck(\n                    UNIFIED_AVATAR_RADIUS", activity)
        self.assertIn("new SeekBarView(context, true, resourcesProvider)", preview)
        self.assertIn("setSeparatorsCount(AvatarGeometry.MAX_CORNER_RADIUS + 1)", preview)
        self.assertIn("R.string.FluxGramAvatarSquare", preview)
        self.assertIn("R.string.FluxGramAvatarCircle", preview)
        self.assertIn("class PreviewView extends FrameLayout", preview)
        self.assertIn("canvas.drawRoundRect(rect, dp(12), dp(12), backgroundPaint)", preview)
        self.assertIn("Theme.key_chats_onlineCircle", preview)
        self.assertIn("AvatarGeometry.resolveRadius(", preview)
        self.assertIn("AvatarGeometry.ROLE_USER", preview)
        drag = preview.split("public void onSeekBarDrag(boolean stop, float progress)", 1)[1].split(
            "public CharSequence getContentDescription()", 1
        )[0]
        self.assertIn("if (stop)", drag)
        self.assertIn("AppearanceSettings.setAvatarCornerRadius(account, radius);", drag)
        self.assertLess(
            drag.index("if (stop)"),
            drag.index("AppearanceSettings.setAvatarCornerRadius(account, radius);"),
        )
        set_radius = preview.split("private void setRadius(int radius, boolean fromUser)", 1)[1].split(
            "private CharSequence valueText", 1
        )[0]
        self.assertIn("if (!fromUser)", set_radius)
        self.assertIn("seekBar.setProgress(radius / (float) AvatarGeometry.MAX_CORNER_RADIUS);", set_radius)


if __name__ == "__main__":
    unittest.main()

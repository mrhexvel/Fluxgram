package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.fluxgram.ArchiveSettings;
import org.telegram.messenger.fluxgram.AppearanceSettings;
import org.telegram.messenger.fluxgram.AvatarGeometryWidgets;
import org.telegram.messenger.fluxgram.BottomNavigationSettings;
import org.telegram.messenger.fluxgram.ChatSettings;
import org.telegram.messenger.fluxgram.ExportDestinationSettings;
import org.telegram.messenger.fluxgram.FontSettings;
import org.telegram.messenger.fluxgram.FolderSettings;
import org.telegram.messenger.fluxgram.GeneralSettings;
import org.telegram.messenger.fluxgram.NavigationSettings;
import org.telegram.messenger.fluxgram.NotificationSettings;
import org.telegram.messenger.fluxgram.ProfileDisplaySettings;
import org.telegram.messenger.fluxgram.StickerSettings;
import org.telegram.messenger.fluxgram.TabletModeSettings;
import org.telegram.messenger.fluxgram.TransferSettings;
import org.telegram.messenger.plugins.ExternalPlugins;
import org.telegram.messenger.plugins.Plugin;
import org.telegram.messenger.plugins.PluginDeveloperBridge;
import org.telegram.messenger.plugins.PluginManager;
import org.telegram.messenger.plugins.PythonPluginAdapter;
import org.telegram.messenger.plugins.packages.PluginManifest;
import org.telegram.messenger.plugins.packages.PluginPackageStore;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.FluxGramAvatarGeometryPreviewCell;
import org.telegram.ui.Components.FluxGramChatListPreviewCell;
import org.telegram.ui.Components.FluxGramFabShapePreviewCell;
import org.telegram.ui.Components.FluxGramFolderPreviewCell;
import org.telegram.ui.Components.FluxGramFormattingPreviewCell;
import org.telegram.ui.Components.FluxGramPluginCell;
import org.telegram.ui.Components.FluxGramPredictiveBackIntensityCell;
import org.telegram.ui.Components.FluxGramSectionRadiusCell;
import org.telegram.ui.Components.FluxGramSettingsHeaderCell;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.fluxgram.FluxGramSettingsSections;
import org.telegram.ui.fluxgram.NavigationTransitionPolicy;

import java.util.ArrayList;

public class FluxGramSettingsActivity extends BaseFragment {
    private static final int HOME = 0;
    private static final int GENERAL = 1;
    private static final int PLUGINS = 2;
    private static final int GHOST_MODE = 3;
    private static final int STEALTH = 4;
    private static final int PLUGIN_SYSTEM = 5;
    private static final int APPEARANCE = 6;
    private static final int CHATS = 7;
    private static final int CHANNEL_LINK = 8;
    private static final int CHATS_LINK = 9;
    private static final int BUILT_IN_PLUGINS = 10;
    private static final int ACTION_BAR_TITLE = 11;
    private static final int HIDE_ACTION_BAR_STATUS = 12;
    private static final int CENTER_ACTION_BAR_TITLE = 13;
    private static final int HIDE_STORIES = 14;
    private static final int HIDE_FLOATING_BUTTON = 15;
    private static final int HIDE_DIALOGS_SEARCH_BAR = 16;
    private static final int SENDER_MINI_AVATARS = 17;
    private static final int FORCE_SNOW = 18;
    private static final int FONTS = 19;
    private static final int PLUGIN_DEVELOPER_ACCESS = 20;
    private static final int USE_SYSTEM_EMOJI = 21;
    private static final int FULL_NUMBER_FORMAT = 22;
    private static final int SHOW_TIME_SECONDS = 23;
    private static final int RELATIVE_LAST_SEEN = 24;
    private static final int HIDE_OWN_PHONE = 25;
    private static final int SHOW_PROFILE_ID_DC = 26;
    private static final int IN_APP_VIBRATION = 27;
    private static final int DISABLE_NOTIFICATION_DELAY = 28;
    private static final int FILTER_ZALGO_TEXT = 29;
    private static final int INCREASE_DOWNLOAD_SPEED = 30;
    private static final int INCREASE_UPLOAD_SPEED = 31;
    private static final int SAVE_FILES_DIRECTORY = 32;
    private static final int HIDE_ARCHIVE_ABOVE_LIST = 33;
    private static final int REVEAL_ARCHIVE_ON_PULL = 34;
    private static final int DISABLE_UNARCHIVE_SWIPE = 35;
    private static final int FOLDER_PRESENTATION = 36;
    private static final int FOLDER_COUNTER = 37;
    private static final int SHOW_ALL_CHATS_FOLDER = 38;
    private static final int UNIFIED_AVATAR_RADIUS = 39;
    private static final int SETTINGS_DIVIDER_STYLE = 40;
    private static final int GLASS_OUTLINE_STYLE = 41;
    private static final int APP_ICON = 42;
    private static final int GOOEY_AVATAR = 43;
    private static final int CUSTOM_CHAT_THEMES = 44;
    private static final int NAVIGATION = 45;
    private static final int TABLET_MODE = 46;
    private static final int BOTTOM_NAVIGATION = 47;
    private static final int TRANSITION_STYLE = 48;
    private static final int PREDICTIVE_BACK = 49;
    private static final int MAIN_MENU = 50;
    private static final int SHOW_ALL_RECENT_STICKERS = 51;
    private static final int SHOW_GREETING_STICKER = 52;
    private static final int HIDE_SEND_AS = 53;
    private static final int DOWNLOADS_DIRECTORY_REQUEST = 1001;

    private final int section;
    private UniversalRecyclerView listView;
    private FluxGramFormattingPreviewCell formattingPreview;
    private FluxGramChatListPreviewCell appearancePreview;
    private FluxGramAvatarGeometryPreviewCell avatarGeometryPreview;
    private FluxGramFabShapePreviewCell fabShapePreview;
    private FluxGramSectionRadiusCell sectionRadiusCell;
    private FluxGramFolderPreviewCell folderPreview;
    private FluxGramPredictiveBackIntensityCell predictiveBackIntensityCell;
    private boolean destroyed;

    private final Runnable refresh = () -> {
        if (listView != null && !destroyed) {
            listView.adapter.update(false);
        }
    };
    private final Runnable rebuildFormattingFragments = () -> {
        if (!destroyed && parentLayout != null) {
            parentLayout.rebuildFragments(0);
        }
    };
    private final Runnable rebuildFolderFragments = () -> {
        if (!destroyed && parentLayout != null) {
            parentLayout.rebuildFragments(0);
        }
    };
    private final Runnable rebuildAppearanceFragments = () -> {
        if (!destroyed && parentLayout != null) {
            parentLayout.rebuildFragments(0);
        }
    };

    public FluxGramSettingsActivity() {
        this(HOME);
    }

    public static FluxGramSettingsActivity plugins() {
        return new FluxGramSettingsActivity(PLUGINS);
    }

    private FluxGramSettingsActivity(int section) {
        this.section = section;
    }

    @Override
    public boolean onFragmentCreate() {
        ExternalPlugins.addListener(refresh);
        PluginDeveloperBridge.addListener(refresh);

        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        destroyed = true;
        AndroidUtilities.cancelRunOnUIThread(rebuildFormattingFragments);
        AndroidUtilities.cancelRunOnUIThread(rebuildFolderFragments);
        AndroidUtilities.cancelRunOnUIThread(rebuildAppearanceFragments);
        ExternalPlugins.removeListener(refresh);
        PluginDeveloperBridge.removeListener(refresh);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        int title = R.string.FluxGramSettings;

        if (section == GENERAL) {
            title = R.string.FluxGramGeneral;
        } else if (section == PLUGINS) {
            title = R.string.FluxGramPlugins;
        } else if (section == BUILT_IN_PLUGINS) {
            title = R.string.FluxGramBuiltInPlugins;
        } else if (section == APPEARANCE) {
            title = R.string.FluxGramAppearance;
        } else if (section == CHATS) {
            title = R.string.FluxGramChats;
        } else if (section == NAVIGATION) {
            title = R.string.FluxGramNavigation;
        }

        actionBar.setTitle(section == HOME ? "" : LocaleController.getString(title));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout content = new FrameLayout(context);
        content.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        fragmentView = content;

        if (section == GENERAL) {
            formattingPreview = new FluxGramFormattingPreviewCell(
                    context, currentAccount, parentLayout, getResourceProvider()
            );
        } else if (section == APPEARANCE) {
            appearancePreview = new FluxGramChatListPreviewCell(
                    context, currentAccount, getResourceProvider()
            );
            avatarGeometryPreview = new FluxGramAvatarGeometryPreviewCell(
                    context,
                    currentAccount,
                    getResourceProvider(),
                    this::onAvatarGeometryChanged
            );
            fabShapePreview = new FluxGramFabShapePreviewCell(
                    context,
                    currentAccount,
                    getResourceProvider(),
                    this::scheduleAppearanceRebuild
            );
            sectionRadiusCell = new FluxGramSectionRadiusCell(
                    context,
                    currentAccount,
                    getResourceProvider(),
                    radius -> FluxGramSettingsSections.applyRadius(listView, radius)
            );
            folderPreview = new FluxGramFolderPreviewCell(
                    context, currentAccount, getResourceProvider()
            );
        } else if (section == NAVIGATION) {
            predictiveBackIntensityCell = new FluxGramPredictiveBackIntensityCell(
                    context,
                    getResourceProvider()
            );
        }

        listView = new UniversalRecyclerView(
                this,
                (items, adapter) -> fillItems(items),
                this::onItemClick,
                null
        );

        listView.adapter.setApplyBackground(false);
        if (section == PLUGINS || section == BUILT_IN_PLUGINS) {
            listView.setItemAnimator(null);
        }

        FluxGramSettingsSections.apply(listView, currentAccount);

        content.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT
        ));

        actionBar.setAdaptiveBackground(listView);

        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items) {
        if (section == HOME) {
            items.add(UItem.asCustomShadow(new FluxGramSettingsHeaderCell(
                    getParentActivity(), getResourceProvider()), 220));
            addSectionLabel(items, R.string.FluxGramPrivacy);
            items.add(UItem.asButton(
                    GHOST_MODE,
                    R.drawable.settings_privacy,
                    LocaleController.getString(R.string.FluxGramGhostMode)
            ));
            items.add(UItem.asButton(
                    STEALTH,
                    R.drawable.msg_archive,
                    LocaleController.getString(R.string.FluxGramStealth)
            ));
            items.add(UItem.asShadow(null));
            addSectionLabel(items, R.string.FluxGramCategories);
            items.add(UItem.asButton(
                    GENERAL,
                    R.drawable.msg_settings,
                    LocaleController.getString(R.string.FluxGramGeneral)
            ));
            items.add(UItem.asButton(
                    APPEARANCE,
                    R.drawable.msg_palette,
                    LocaleController.getString(R.string.FluxGramAppearance)
            ));
            items.add(UItem.asButton(
                    CHATS,
                    R.drawable.msg_discussion,
                    LocaleController.getString(R.string.FluxGramChats)
            ));
            items.add(UItem.asButton(
                    NAVIGATION,
                    R.drawable.settings_devices,
                    LocaleController.getString(R.string.FluxGramNavigation)
            ));
            items.add(UItem.asButton(
                    PLUGINS,
                    R.drawable.msg_bots,
                    LocaleController.getString(R.string.FluxGramPlugins)
            ));
            items.add(UItem.asShadow(null));
            addSectionLabel(items, R.string.FluxGramLinks);
            items.add(UItem.asButton(
                    CHANNEL_LINK,
                    R.drawable.msg_channel,
                    LocaleController.getString(R.string.FluxGramChannel),
                    "@FluxGramTG"
            ));
            items.add(UItem.asButton(
                    CHATS_LINK,
                    R.drawable.msg_groups,
                    LocaleController.getString(R.string.FluxGramChats),
                    "@FluxGramForum"
            ));
            items.add(UItem.asShadow(null));

            return;
        }

        if (section == GENERAL) {
            addSectionLabel(items, R.string.FluxGramFormatting);
            items.add(UItem.asCustom(formattingPreview, 247));
            items.add(UItem.asCheck(
                    FULL_NUMBER_FORMAT,
                    LocaleController.getString(R.string.FluxGramFullNumberFormat)
            ).setChecked(GeneralSettings.usesFullNumberFormat()));
            items.add(UItem.asCheck(
                    SHOW_TIME_SECONDS,
                    LocaleController.getString(R.string.FluxGramShowTimeSeconds)
            ).setChecked(GeneralSettings.showsTimeSeconds()));
            items.add(UItem.asCheck(
                    RELATIVE_LAST_SEEN,
                    LocaleController.getString(R.string.FluxGramRelativeLastSeen)
            ).setChecked(GeneralSettings.usesRelativeLastSeen()));
            items.add(UItem.asCheck(
                    FILTER_ZALGO_TEXT,
                    LocaleController.getString(R.string.FluxGramFilterZalgoText)
            ).setChecked(GeneralSettings.filtersZalgoText()));
            items.add(UItem.asShadow(null));
            addSectionLabel(items, R.string.FluxGramNotifications);
            items.add(UItem.asCheck(
                    IN_APP_VIBRATION,
                    LocaleController.getString(R.string.InAppVibrate)
            ).setChecked(NotificationSettings.isInAppVibrationEnabled(currentAccount)));
            items.add(UItem.asCheck(
                    DISABLE_NOTIFICATION_DELAY,
                    LocaleController.getString(R.string.FluxGramDisableNotificationDelay)
            ).setChecked(NotificationSettings.isNotificationDelayDisabled(currentAccount)));
            items.add(UItem.asShadow(null));
            addSectionLabel(items, R.string.FluxGramDataTransfer);
            items.add(UItem.asCheck(
                    INCREASE_DOWNLOAD_SPEED,
                    LocaleController.getString(R.string.FluxGramIncreaseDownloadSpeed)
            ).setChecked(TransferSettings.increasesDownloadSpeed()));
            items.add(UItem.asCheck(
                    INCREASE_UPLOAD_SPEED,
                    LocaleController.getString(R.string.FluxGramIncreaseUploadSpeed)
            ).setChecked(TransferSettings.increasesUploadSpeed()));
            items.add(UItem.asButton(
                    SAVE_FILES_DIRECTORY,
                    R.drawable.msg_filled_sdcard,
                    LocaleController.getString(R.string.FluxGramSaveFilesDirectory),
                    LocaleController.getString(ExportDestinationSettings.hasCustomDownloadsDirectory()
                            ? R.string.FluxGramSelectedFolder
                            : R.string.FluxGramDefaultDownloads)
            ));
            items.add(UItem.asShadow(null));
            addSectionLabel(items, R.string.FluxGramProfile);
            items.add(UItem.asCheck(
                    HIDE_OWN_PHONE,
                    LocaleController.getString(R.string.FluxGramHideOwnPhone)
            ).setChecked(ProfileDisplaySettings.hidesOwnPhone()));
            items.add(UItem.asCheck(
                    SHOW_PROFILE_ID_DC,
                    LocaleController.getString(R.string.FluxGramShowProfileIdDc)
            ).setChecked(ProfileDisplaySettings.showsIdAndDc()));
            items.add(UItem.asShadow(null));

            return;
        }

        if (section == APPEARANCE) {
            appearancePreview.update(false);
            addSectionLabel(items, R.string.FluxGramFontsAndEmoji);
            items.add(UItem.asButton(
                    FONTS,
                    R.drawable.msg_text_outlined,
                    LocaleController.getString(R.string.FluxGramFonts),
                    fontModeOption(FontSettings.getMode())
            ));
            items.add(UItem.asCheck(
                    USE_SYSTEM_EMOJI,
                    LocaleController.getString(R.string.FluxGramUseSystemEmoji)
            ).setChecked(SharedConfig.useSystemEmoji));
            items.add(UItem.asShadow(null));
            addSectionLabel(items, R.string.FluxGramChatList);
            items.add(UItem.asCustom(appearancePreview, 88));
            items.add(UItem.asButton(
                    ACTION_BAR_TITLE,
                    LocaleController.getString(R.string.FluxGramActionBarTitle),
                    appearanceTitleOption(AppearanceSettings.getTitleMode(currentAccount))
            ));

            if (UserConfig.getInstance(currentAccount).isPremium()) {
                items.add(UItem.asCheck(
                        HIDE_ACTION_BAR_STATUS,
                        LocaleController.getString(R.string.FluxGramHideActionBarStatus)
                ).setChecked(AppearanceSettings.hidesActionBarStatus(currentAccount)));
            }

            items.add(UItem.asCheck(
                    CENTER_ACTION_BAR_TITLE,
                    LocaleController.getString(R.string.FluxGramCenterActionBarTitle)
            ).setChecked(AppearanceSettings.centersActionBarTitle(currentAccount)));
            items.add(UItem.asCheck(
                    HIDE_STORIES,
                    LocaleController.getString(R.string.FluxGramHideStories)
            ).setChecked(AppearanceSettings.hidesStories(currentAccount)));
            items.add(UItem.asCheck(
                    HIDE_FLOATING_BUTTON,
                    LocaleController.getString(R.string.FluxGramHideFloatingButton)
            ).setChecked(AppearanceSettings.hidesFloatingButton(currentAccount)));
            items.add(UItem.asCheck(
                    HIDE_DIALOGS_SEARCH_BAR,
                    LocaleController.getString(R.string.FluxGramHideDialogsSearchBar)
            ).setChecked(AppearanceSettings.hidesDialogsSearchBar(currentAccount)));
            items.add(UItem.asCheck(
                    SENDER_MINI_AVATARS,
                    LocaleController.getString(R.string.FluxGramSenderMiniAvatars)
            ).setChecked(AppearanceSettings.showsSenderMiniAvatars(currentAccount)));
            items.add(UItem.asCheck(
                    FORCE_SNOW,
                    LocaleController.getString(R.string.FluxGramForceSnow)
            ).setChecked(AppearanceSettings.forcesSnow(currentAccount)));
            items.add(UItem.asShadow(null));
            addSectionLabel(items, R.string.FluxGramRounding);
            avatarGeometryPreview.update();
            items.add(UItem.asCustom(avatarGeometryPreview, 222));
            items.add(UItem.asCheck(
                    UNIFIED_AVATAR_RADIUS,
                    LocaleController.getString(R.string.FluxGramUnifiedAvatarRadius)
            ).setChecked(AppearanceSettings.usesUnifiedAvatarRadius(currentAccount)));
            fabShapePreview.update();
            items.add(UItem.asCustom(fabShapePreview, 104));
            sectionRadiusCell.update();
            items.add(UItem.asCustom(sectionRadiusCell, 104));
            items.add(UItem.asButton(
                    SETTINGS_DIVIDER_STYLE,
                    LocaleController.getString(R.string.FluxGramDividerStyle),
                    dividerStyleOption(AppearanceSettings.getSettingsDividerStyle(currentAccount))
            ));
            items.add(UItem.asShadow(null));
            addSectionLabel(items, R.string.FluxGramGlass);
            items.add(UItem.asButton(
                    GLASS_OUTLINE_STYLE,
                    LocaleController.getString(R.string.FluxGramGlassOutlineStyle),
                    glassOutlineStyleOption(AppearanceSettings.getGlassOutlineStyle(currentAccount))
            ));
            items.add(UItem.asShadow(null));
            addSectionLabel(items, R.string.OtherSettings);
            items.add(UItem.asButton(
                    APP_ICON,
                    R.drawable.msg_palette,
                    LocaleController.getString(R.string.AppIcon)
            ));
            items.add(UItem.asCheck(
                    GOOEY_AVATAR,
                    LocaleController.getString(R.string.FluxGramGooeyAvatar)
            ).setChecked(AppearanceSettings.usesGooeyAvatar(currentAccount)));
            items.add(UItem.asCheck(
                    CUSTOM_CHAT_THEMES,
                    LocaleController.getString(R.string.FluxGramCustomChatThemes)
            ).setChecked(AppearanceSettings.usesCustomChatThemes(currentAccount)));
            items.add(UItem.asShadow(null));
            addSectionLabel(items, R.string.Filters);
            items.add(UItem.asCustomShadow(folderPreview, 58));
            items.add(UItem.asButton(
                    FOLDER_PRESENTATION,
                    LocaleController.getString(R.string.FluxGramFolderPresentation),
                    folderPresentationOption(FolderSettings.getPolicy(currentAccount).getPresentationMode())
            ));
            items.add(UItem.asButton(
                    FOLDER_COUNTER,
                    LocaleController.getString(R.string.FluxGramFolderCounter),
                    folderCounterOption(FolderSettings.getPolicy(currentAccount).getCounterMode())
            ));
            items.add(UItem.asCheck(
                    SHOW_ALL_CHATS_FOLDER,
                    LocaleController.getString(R.string.FluxGramShowAllChatsFolder)
            ).setChecked(FolderSettings.getPolicy(currentAccount).showsAllChats()));
            items.add(UItem.asShadow(null));

            return;
        }

        if (section == CHATS) {
            addSectionLabel(items, R.string.ArchivedChats);
            items.add(UItem.asCheck(
                    HIDE_ARCHIVE_ABOVE_LIST,
                    LocaleController.getString(R.string.HideAboveTheList)
            ).setChecked(SharedConfig.archiveHidden));
            items.add(UItem.asCheck(
                    REVEAL_ARCHIVE_ON_PULL,
                    LocaleController.getString(R.string.FluxGramRevealArchiveOnPull)
            ).setChecked(ArchiveSettings.revealsOnPull()));
            items.add(UItem.asCheck(
                    DISABLE_UNARCHIVE_SWIPE,
                    LocaleController.getString(R.string.FluxGramDisableUnarchiveSwipe)
            ).setChecked(ArchiveSettings.disablesUnarchiveSwipe()));
            items.add(UItem.asShadow(null));
            addSectionLabel(items, R.string.FluxGramStickers);
            items.add(UItem.asCheck(
                    SHOW_GREETING_STICKER,
                    LocaleController.getString(R.string.FluxGramShowGreetingSticker)
            ).setChecked(StickerSettings.showsGreeting()));
            items.add(UItem.asCheck(
                    SHOW_ALL_RECENT_STICKERS,
                    LocaleController.getString(R.string.FluxGramShowAllRecentStickers)
            ).setChecked(StickerSettings.showsAllRecent()));
            items.add(UItem.asShadow(LocaleController.getString(
                    R.string.FluxGramShowAllRecentStickersInfo)));
            addSectionLabel(items, R.string.FluxGramMessageInput);
            items.add(UItem.asCheck(
                    HIDE_SEND_AS,
                    LocaleController.getString(R.string.FluxGramHideSendAs)
            ).setChecked(ChatSettings.hidesSendAs()));
            items.add(UItem.asShadow(LocaleController.getString(
                    R.string.FluxGramHideSendAsInfo)));

            return;
        }

        if (section == NAVIGATION) {
            addSectionLabel(items, R.string.FluxGramNavigation);
            items.add(UItem.asButton(
                    TABLET_MODE,
                    LocaleController.getString(R.string.FluxGramTabletMode),
                    tabletModeOption(SharedConfig.tabletMode)
            ));
            items.add(UItem.asButton(
                    BOTTOM_NAVIGATION,
                    LocaleController.getString(R.string.FluxGramBottomNavigation),
                    bottomNavigationOption(SharedConfig.bottomNavigationMode)
            ));
            items.add(UItem.asButton(
                    MAIN_MENU,
                    LocaleController.getString(R.string.FluxGramMainMenu)
            ));
            items.add(UItem.asButton(
                    TRANSITION_STYLE,
                    LocaleController.getString(R.string.FluxGramTransitionStyle),
                    transitionStyleOption(SharedConfig.navigationTransitionStyle)
            ));
            items.add(UItem.asCheck(
                    PREDICTIVE_BACK,
                    LocaleController.getString(R.string.FluxGramPredictiveBack)
            ).setChecked(SharedConfig.predictiveBackEnabled));
            if (SharedConfig.predictiveBackEnabled) {
                predictiveBackIntensityCell.update();
                items.add(UItem.asCustom(predictiveBackIntensityCell, 104));
            }
            items.add(UItem.asShadow(LocaleController.getString(
                    R.string.FluxGramNavigationInfo)));

            return;
        }

        PluginManager manager = PluginManager.getInstance();
        boolean systemEnabled = manager.isSystemEnabled();

        if (section == BUILT_IN_PLUGINS) {
            if (systemEnabled) {
                addPluginItems(items, manager, false, false);
            } else {
                items.add(UItem.asShadow(LocaleController.getString(
                        R.string.FluxGramPluginsDisabled)));
            }

            return;
        }

        CharSequence systemDescription = null;

        if (manager.isRecoveryLatched()) {
            systemDescription = LocaleController.getString(
                    R.string.FluxGramPluginsRecoveryDisabled);
        } else if (manager.isRecoveryRestartRequired()) {
            systemDescription = LocaleController.getString(
                    R.string.FluxGramPluginsRecoveryRestart);
        } else if (!systemEnabled) {
            systemDescription = LocaleController.getString(R.string.FluxGramPluginsDisabled);
        }

        items.add(UItem.asRippleCheck(
                PLUGIN_SYSTEM,
                LocaleController.getString(R.string.FluxGramPluginSystem)
        ).setChecked(systemEnabled));

        if (systemEnabled) {
            items.add(UItem.asButton(
                    BUILT_IN_PLUGINS,
                    R.drawable.msg_bot,
                    LocaleController.getString(R.string.FluxGramBuiltInPlugins)
            ));
        }

        items.add(UItem.asShadow(systemDescription));

        if (!systemEnabled) {
            return;
        }

        if (!BuildConfig.DEBUG) {
            boolean developerAccess = PluginDeveloperBridge.isSessionActive();
            addSectionLabel(items, R.string.FluxGramPluginDevelopment);
            items.add(UItem.asRippleCheck(
                    PLUGIN_DEVELOPER_ACCESS,
                    LocaleController.getString(R.string.FluxGramPluginDeveloperAccess)
            ).setChecked(developerAccess));
            items.add(UItem.asShadow(LocaleController.getString(developerAccess
                    ? R.string.FluxGramPluginDeveloperAccessActive
                    : R.string.FluxGramPluginDeveloperAccessInfo)));
        }

        if (ExternalPlugins.getStartupError() != null) {
            items.add(UItem.asShadow(
                    LocaleController.getString(R.string.FluxGramPluginError)
                            + "\n" + ExternalPlugins.getStartupError()));
        }

        addPluginItems(items, manager, true, true);
    }

    private void addPluginItems(
            ArrayList<UItem> items,
            PluginManager manager,
            boolean external,
            boolean showHeader) {
        boolean headerAdded = false;
        ArrayList<Plugin> plugins = manager.getPlugins();

        if (external) {
            plugins.addAll(ExternalPlugins.getIncompatiblePlugins());
        }

        for (Plugin plugin : plugins) {
            boolean isExternal = ExternalPlugins.isExternal(plugin);

            if (isExternal != external) {
                continue;
            }

            if (showHeader && !headerAdded) {
                addSectionLabel(items, R.string.FluxGramInstalledPlugins);
                headerAdded = true;
            }

            PluginPackageStore.Pending pending = ExternalPlugins.getPending(plugin.getId());
            String description = plugin.getDescription();
            boolean incompatible = plugin instanceof ExternalPlugins.IncompatiblePlugin;

            if (pending != null) {
                description += "\n\n" + LocaleController.getString(pending.delete
                        ? R.string.FluxGramPluginDeletionPending : R.string.FluxGramPluginUpdatePending);
            }

            if (plugin instanceof PythonPluginAdapter
                    && ((PythonPluginAdapter) plugin).getLoadError() != null) {
                description += "\n\n" + LocaleController.getString(R.string.FluxGramPluginLoadFailed);
            }

            if (manager.isRecoveryPlugin(plugin.getId())) {
                description += "\n\n" + LocaleController.getString(
                        R.string.FluxGramPluginCausedCrash);
            }

            if (incompatible) {
                PluginManifest manifest =
                        ((ExternalPlugins.IncompatiblePlugin) plugin).getManifest();
                if (manifest.javaModule != null && !manifest.javaModule.isCompatible()) {
                    description += "\n\n" + LocaleController.formatString(
                            "FluxGramPluginJavaIncompatible",
                            R.string.FluxGramPluginJavaIncompatible,
                            manifest.javaModule.apiVersion,
                            manifest.javaModule.minApiMinor,
                            PluginManifest.JAVA_API_VERSION,
                            PluginManifest.JAVA_API_MINOR_VERSION);
                } else {
                    description += "\n\n" + LocaleController.formatString(
                        "FluxGramPluginIncompatible",
                        R.string.FluxGramPluginIncompatible,
                        manifest.sdkVersion,
                        manifest.minSdkMinor,
                        PluginManifest.SDK_VERSION,
                        PluginManifest.SDK_MINOR_VERSION);
                }
            }

            FluxGramPluginCell cell = new FluxGramPluginCell(
                    getParentActivity(), getResourceProvider(), plugin, plugin.getName(),
                    LocaleController.getString(R.string.FluxGramPluginVersion) + " " + plugin.getVersion(),
                    description, !incompatible && manager.isEnabled(plugin.getId()),
                    !incompatible && pending == null && !ExternalPlugins.isRemoving(plugin.getId())
                            && !(plugin instanceof PythonPluginAdapter
                            && ((PythonPluginAdapter) plugin).isStoppingForRemoval()),
                    incompatible ? null
                            : () -> manager.setEnabled(
                                    plugin.getId(), !manager.isEnabled(plugin.getId())),
                    view -> showPluginActions(
                            view,
                            plugin,
                            incompatible,
                            external && pending == null
                                    && !ExternalPlugins.isRemoving(plugin.getId())));
            items.add(UItem.asCustom(cell, LayoutHelper.WRAP_CONTENT));
            items.add(UItem.asShadow(null));
        }
    }

    private void showPluginActions(
            View anchor,
            Plugin plugin,
            boolean incompatible,
            boolean canDelete) {
        ItemOptions options = ItemOptions.makeOptions(this, anchor);

        if (!incompatible) {
            options.add(
                    R.drawable.msg_settings,
                    LocaleController.getString(R.string.Settings),
                    () -> presentFragment(new PluginSettingsActivity(plugin.getId())));
        }

        options.add(
                R.drawable.msg_share,
                LocaleController.getString(R.string.FluxGramSharePlugin),
                () -> sharePlugin(plugin));

        if (canDelete) {
            options.add(
                    R.drawable.msg_delete,
                    LocaleController.getString(R.string.Delete),
                    true,
                    () -> confirmDelete(plugin));
        }

        options.show();
    }

    private void addSectionLabel(ArrayList<UItem> items, int textResource) {
        HeaderCell label = new HeaderCell(getParentActivity(), 33, getResourceProvider());
        label.setText(LocaleController.getString(textResource));
        items.add(UItem.asCustomShadow(label, LayoutHelper.WRAP_CONTENT));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (section == HOME) {
            if (item.id == GENERAL) {
                presentFragment(new FluxGramSettingsActivity(GENERAL));
            } else if (item.id == PLUGINS) {
                presentFragment(new FluxGramSettingsActivity(PLUGINS));
            } else if (item.id == APPEARANCE) {
                presentFragment(new FluxGramSettingsActivity(APPEARANCE));
            } else if (item.id == CHATS) {
                presentFragment(new FluxGramSettingsActivity(CHATS));
            } else if (item.id == NAVIGATION) {
                presentFragment(new FluxGramSettingsActivity(NAVIGATION));
            } else if (item.id == GHOST_MODE) {
                presentFragment(new FluxGramGhostModeActivity());
            } else if (item.id == STEALTH) {
                presentFragment(new FluxGramStealthActivity());
            } else if (item.id == CHANNEL_LINK) {
                Browser.openUrl(getParentActivity(), "https://t.me/FluxGramTG");
            } else if (item.id == CHATS_LINK) {
                Browser.openUrl(getParentActivity(), "https://t.me/FluxGramForum");
            }

            return;
        }

        if (section == GENERAL) {
            if (item.id == FULL_NUMBER_FORMAT) {
                boolean enabled = !GeneralSettings.usesFullNumberFormat();
                GeneralSettings.setUsesFullNumberFormat(enabled);
                ((TextCheckCell) view).setChecked(enabled);
                updateFormattingPreview(false, true);
            } else if (item.id == SHOW_TIME_SECONDS) {
                boolean enabled = !GeneralSettings.showsTimeSeconds();
                GeneralSettings.setShowsTimeSeconds(enabled);
                LocaleController.getInstance().recreateFormatters();
                ((TextCheckCell) view).setChecked(enabled);
                updateFormattingPreview(false, true);
            } else if (item.id == RELATIVE_LAST_SEEN) {
                boolean enabled = !GeneralSettings.usesRelativeLastSeen();
                GeneralSettings.setUsesRelativeLastSeen(enabled);
                ((TextCheckCell) view).setChecked(enabled);
                updateFormattingPreview(true, false);
            } else if (item.id == FILTER_ZALGO_TEXT) {
                boolean enabled = !GeneralSettings.filtersZalgoText();
                GeneralSettings.setFiltersZalgoText(enabled);
                ((TextCheckCell) view).setChecked(enabled);
                updateFormattingPreview(false, true);
            } else if (item.id == IN_APP_VIBRATION) {
                boolean enabled = !NotificationSettings.isInAppVibrationEnabled(currentAccount);
                NotificationSettings.setInAppVibrationEnabled(currentAccount, enabled);
                ((TextCheckCell) view).setChecked(enabled);
                if (enabled) {
                    int feedback = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                            ? HapticFeedbackConstants.CONFIRM
                            : HapticFeedbackConstants.VIRTUAL_KEY;
                    view.performHapticFeedback(feedback);
                }
            } else if (item.id == DISABLE_NOTIFICATION_DELAY) {
                boolean disabled = !NotificationSettings.isNotificationDelayDisabled(currentAccount);
                NotificationSettings.setNotificationDelayDisabled(currentAccount, disabled);
                ((TextCheckCell) view).setChecked(disabled);
            } else if (item.id == INCREASE_DOWNLOAD_SPEED) {
                boolean enabled = !TransferSettings.increasesDownloadSpeed();
                TransferSettings.setIncreasesDownloadSpeed(enabled);
                ((TextCheckCell) view).setChecked(enabled);
            } else if (item.id == INCREASE_UPLOAD_SPEED) {
                boolean enabled = !TransferSettings.increasesUploadSpeed();
                TransferSettings.setIncreasesUploadSpeed(enabled);
                ((TextCheckCell) view).setChecked(enabled);
            } else if (item.id == SAVE_FILES_DIRECTORY) {
                showDownloadsDirectoryOptions(view);
            } else if (item.id == HIDE_OWN_PHONE) {
                boolean hidden = !ProfileDisplaySettings.hidesOwnPhone();
                ProfileDisplaySettings.setHidesOwnPhone(hidden);
                ((TextCheckCell) view).setChecked(hidden);
                parentLayout.rebuildFragments(0);
            } else if (item.id == SHOW_PROFILE_ID_DC) {
                boolean shown = !ProfileDisplaySettings.showsIdAndDc();
                ProfileDisplaySettings.setShowsIdAndDc(shown);
                ((TextCheckCell) view).setChecked(shown);
                parentLayout.rebuildFragments(0);
            }

            return;
        }

        if (section == CHATS) {
            if (item.id == HIDE_ARCHIVE_ABOVE_LIST) {
                SharedConfig.toggleArchiveHidden();
                ((TextCheckCell) view).setChecked(SharedConfig.archiveHidden);
                parentLayout.rebuildFragments(0);
            } else if (item.id == REVEAL_ARCHIVE_ON_PULL) {
                boolean enabled = !ArchiveSettings.revealsOnPull();
                ArchiveSettings.setRevealsOnPull(enabled);
                ((TextCheckCell) view).setChecked(enabled);
                parentLayout.rebuildFragments(0);
            } else if (item.id == DISABLE_UNARCHIVE_SWIPE) {
                boolean disabled = !ArchiveSettings.disablesUnarchiveSwipe();
                ArchiveSettings.setDisablesUnarchiveSwipe(disabled);
                ((TextCheckCell) view).setChecked(disabled);
            } else if (item.id == SHOW_ALL_RECENT_STICKERS) {
                boolean enabled = !StickerSettings.showsAllRecent();
                StickerSettings.setShowsAllRecent(enabled);
                ((TextCheckCell) view).setChecked(enabled);
                NotificationCenter.getInstance(currentAccount).postNotificationName(
                        NotificationCenter.recentDocumentsDidLoad,
                        false,
                        MediaDataController.TYPE_IMAGE
                );
            } else if (item.id == SHOW_GREETING_STICKER) {
                boolean enabled = !StickerSettings.showsGreeting();
                StickerSettings.setShowsGreeting(enabled);
                ((TextCheckCell) view).setChecked(enabled);
                parentLayout.rebuildFragments(0);
            } else if (item.id == HIDE_SEND_AS) {
                boolean hidden = !ChatSettings.hidesSendAs();
                ChatSettings.setHidesSendAs(hidden);
                ((TextCheckCell) view).setChecked(hidden);
                parentLayout.rebuildFragments(0);
            }

            return;
        }

        if (section == NAVIGATION) {
            if (item.id == TABLET_MODE) {
                showTabletModeDialog();
            } else if (item.id == BOTTOM_NAVIGATION) {
                showBottomNavigationDialog();
            } else if (item.id == MAIN_MENU) {
                presentFragment(new FluxGramMainMenuSettingsActivity());
            } else if (item.id == TRANSITION_STYLE) {
                showTransitionStyleDialog();
            } else if (item.id == PREDICTIVE_BACK) {
                confirmPredictiveBackChange(!SharedConfig.predictiveBackEnabled);
            }

            return;
        }

        if (section == APPEARANCE) {
            if (item.id == FONTS) {
                presentFragment(new FluxGramFontSettingsActivity());
            } else if (item.id == USE_SYSTEM_EMOJI) {
                SharedConfig.toggleUseSystemEmoji();
                ((TextCheckCell) view).setChecked(SharedConfig.useSystemEmoji);
                parentLayout.rebuildFragments(0);
            } else if (item.id == ACTION_BAR_TITLE) {
                showAppearanceTitleDialog();
            } else if (item.id == FOLDER_PRESENTATION) {
                showFolderPresentationDialog();
            } else if (item.id == FOLDER_COUNTER) {
                showFolderCounterDialog();
            } else if (item.id == SHOW_ALL_CHATS_FOLDER) {
                boolean shown = !FolderSettings.getPolicy(currentAccount).showsAllChats();
                FolderSettings.setShowsAllChats(currentAccount, shown);
                ((TextCheckCell) view).setChecked(shown);
                updateFolderPreview();
            } else if (item.id == HIDE_ACTION_BAR_STATUS) {
                boolean hidden = !AppearanceSettings.hidesActionBarStatus(currentAccount);
                AppearanceSettings.setHidesActionBarStatus(currentAccount, hidden);
                ((TextCheckCell) view).setChecked(hidden);
                appearancePreview.update(true);
            } else if (item.id == CENTER_ACTION_BAR_TITLE) {
                boolean centered = !AppearanceSettings.centersActionBarTitle(currentAccount);
                AppearanceSettings.setCentersActionBarTitle(currentAccount, centered);
                ((TextCheckCell) view).setChecked(centered);
                appearancePreview.update(true);
            } else if (item.id == HIDE_STORIES) {
                boolean hidden = !AppearanceSettings.hidesStories(currentAccount);
                AppearanceSettings.setHidesStories(currentAccount, hidden);
                ((TextCheckCell) view).setChecked(hidden);
            } else if (item.id == HIDE_FLOATING_BUTTON) {
                boolean hidden = !AppearanceSettings.hidesFloatingButton(currentAccount);
                AppearanceSettings.setHidesFloatingButton(currentAccount, hidden);
                ((TextCheckCell) view).setChecked(hidden);
            } else if (item.id == HIDE_DIALOGS_SEARCH_BAR) {
                boolean hidden = !AppearanceSettings.hidesDialogsSearchBar(currentAccount);
                AppearanceSettings.setHidesDialogsSearchBar(currentAccount, hidden);
                ((TextCheckCell) view).setChecked(hidden);
            } else if (item.id == SENDER_MINI_AVATARS) {
                boolean shown = !AppearanceSettings.showsSenderMiniAvatars(currentAccount);
                AppearanceSettings.setShowsSenderMiniAvatars(currentAccount, shown);
                ((TextCheckCell) view).setChecked(shown);
            } else if (item.id == FORCE_SNOW) {
                boolean forced = !AppearanceSettings.forcesSnow(currentAccount);
                AppearanceSettings.setForcesSnow(currentAccount, forced);
                ((TextCheckCell) view).setChecked(forced);
                appearancePreview.update(true);
            } else if (item.id == UNIFIED_AVATAR_RADIUS) {
                boolean unified = !AppearanceSettings.usesUnifiedAvatarRadius(currentAccount);
                AppearanceSettings.setUsesUnifiedAvatarRadius(currentAccount, unified);
                ((TextCheckCell) view).setChecked(unified);
                avatarGeometryPreview.update();
                onAvatarGeometryChanged();
            } else if (item.id == SETTINGS_DIVIDER_STYLE) {
                showDividerStyleDialog();
            } else if (item.id == GLASS_OUTLINE_STYLE) {
                showGlassOutlineStyleDialog();
            } else if (item.id == APP_ICON) {
                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                AndroidUtilities.scrollToFragmentRow(parentLayout, "appIconSelectorRow");
            } else if (item.id == GOOEY_AVATAR) {
                boolean enabled = !AppearanceSettings.usesGooeyAvatar(currentAccount);
                AppearanceSettings.setUsesGooeyAvatar(currentAccount, enabled);
                ((TextCheckCell) view).setChecked(enabled);
                scheduleAppearanceRebuild();
            } else if (item.id == CUSTOM_CHAT_THEMES) {
                boolean enabled = !AppearanceSettings.usesCustomChatThemes(currentAccount);
                AppearanceSettings.setUsesCustomChatThemes(currentAccount, enabled);
                ((TextCheckCell) view).setChecked(enabled);
                scheduleAppearanceRebuild();
            }

            return;
        }

        if (section == PLUGINS) {
            if (item.id == PLUGIN_SYSTEM) {
                PluginManager manager = PluginManager.getInstance();
                manager.setSystemEnabled(!manager.isSystemEnabled());
                ExternalPlugins.changed();
            } else if (item.id == BUILT_IN_PLUGINS) {
                presentFragment(new FluxGramSettingsActivity(BUILT_IN_PLUGINS));
            } else if (item.id == PLUGIN_DEVELOPER_ACCESS) {
                if (PluginDeveloperBridge.isSessionActive()) {
                    PluginDeveloperBridge.stopSession();
                } else {
                    showDialog(new AlertDialog.Builder(
                            getParentActivity(), getResourceProvider())
                            .setTitle(LocaleController.getString(
                                    R.string.FluxGramPluginDeveloperAccessConfirmTitle))
                            .setMessage(LocaleController.getString(
                                    R.string.FluxGramPluginDeveloperAccessConfirmMessage))
                            .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                            .setPositiveButton(LocaleController.getString(R.string.Enable),
                                    (dialog, which) -> PluginDeveloperBridge.startSession())
                            .create());
                }
            }
        }
    }

    private void showDownloadsDirectoryOptions(View anchor) {
        if (!ExportDestinationSettings.hasCustomDownloadsDirectory()) {
            selectDownloadsDirectory();

            return;
        }

        ItemOptions.makeOptions(this, anchor)
                .add(
                        R.drawable.msg_edit,
                        LocaleController.getString(R.string.FluxGramChooseFolder),
                        this::selectDownloadsDirectory
                )
                .add(
                        R.drawable.msg_removefolder,
                        LocaleController.getString(R.string.FluxGramUseDefaultFolder),
                        () -> {
                            ExportDestinationSettings.clearDownloadsTreeUri();
                            listView.adapter.update(true);
                        }
                )
                .show();
    }

    private void selectDownloadsDirectory() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            );
            startActivityForResult(intent, DOWNLOADS_DIRECTORY_REQUEST);
        } catch (RuntimeException error) {
            showDownloadsDirectoryError();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != DOWNLOADS_DIRECTORY_REQUEST) {
            return;
        }

        if (resultCode != Activity.RESULT_OK || data == null) {
            return;
        }

        Uri uri = data.getData();
        if (!ExportDestinationSettings.setDownloadsTreeUri(uri, data.getFlags())) {
            showDownloadsDirectoryError();

            return;
        }

        if (listView != null) {
            listView.adapter.update(true);
        }
    }

    private void showDownloadsDirectoryError() {
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(LocaleController.getString(R.string.FluxGramAppName))
                .setMessage(LocaleController.getString(R.string.FluxGramFolderAccessError))
                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                .create());
    }

    private void updateFormattingPreview(boolean updateHeader, boolean updateMessages) {
        formattingPreview.updateContent(true, updateHeader, updateMessages);
        AndroidUtilities.cancelRunOnUIThread(rebuildFormattingFragments);
        AndroidUtilities.runOnUIThread(rebuildFormattingFragments, 280L);
    }

    private void showInfo(String title, String message) {
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                .create());
    }

    private CharSequence appearanceTitleOption(int mode) {
        if (mode == AppearanceSettings.TITLE_USERNAME) {
            return LocaleController.getString(R.string.FluxGramActionBarTitleUsername);
        }
        if (mode == AppearanceSettings.TITLE_NAME) {
            return LocaleController.getString(R.string.FluxGramActionBarTitleName);
        }
        if (mode == AppearanceSettings.TITLE_CHATS) {
            return LocaleController.getString(R.string.FluxGramActionBarTitleChats);
        }

        return LocaleController.getString(R.string.FluxGramActionBarTitleAppName);
    }

    private CharSequence fontModeOption(int mode) {
        if (mode == FontSettings.MODE_SYSTEM) {
            return LocaleController.getString(R.string.FluxGramFontSystem);
        }
        if (mode == FontSettings.MODE_CUSTOM) {
            return LocaleController.getString(R.string.FluxGramFontCustom);
        }

        return LocaleController.getString(R.string.FluxGramFontTelegram);
    }

    private CharSequence folderPresentationOption(int mode) {
        if (mode == FolderSettings.MODE_TITLE) {
            return LocaleController.getString(R.string.FluxGramFolderPresentationTitle);
        }
        if (mode == FolderSettings.MODE_ICON) {
            return LocaleController.getString(R.string.FluxGramFolderPresentationIcon);
        }

        return LocaleController.getString(R.string.FluxGramFolderPresentationTitleAndIcon);
    }

    private void showFolderPresentationDialog() {
        CharSequence[] options = {
                folderPresentationOption(FolderSettings.MODE_TITLE_AND_ICON),
                folderPresentationOption(FolderSettings.MODE_TITLE),
                folderPresentationOption(FolderSettings.MODE_ICON)
        };
        int selected = FolderSettings.getPolicy(currentAccount).getPresentationMode();

        for (int i = 0; i < options.length; i++) {
            if (i == selected) {
                options[i] = "✓  " + options[i];
            }
        }

        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(LocaleController.getString(R.string.FluxGramFolderPresentation))
                .setItems(options, (dialog, index) -> {
                    FolderSettings.setPresentationMode(currentAccount, index);
                    updateFolderPreview();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create());
    }

    private CharSequence folderCounterOption(int mode) {
        if (mode == FolderSettings.COUNTER_UNMUTED) {
            return LocaleController.getString(R.string.FluxGramFolderCounterUnmuted);
        }
        if (mode == FolderSettings.COUNTER_HIDDEN) {
            return LocaleController.getString(R.string.FluxGramFolderCounterHidden);
        }

        return LocaleController.getString(R.string.FluxGramFolderCounterAll);
    }

    private void showFolderCounterDialog() {
        CharSequence[] options = {
                folderCounterOption(FolderSettings.COUNTER_ALL),
                folderCounterOption(FolderSettings.COUNTER_UNMUTED),
                folderCounterOption(FolderSettings.COUNTER_HIDDEN)
        };
        int selected = FolderSettings.getPolicy(currentAccount).getCounterMode();

        for (int i = 0; i < options.length; i++) {
            if (i == selected) {
                options[i] = "✓  " + options[i];
            }
        }

        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(LocaleController.getString(R.string.FluxGramFolderCounter))
                .setItems(options, (dialog, index) -> {
                    FolderSettings.setCounterMode(currentAccount, index);
                    updateFolderPreview();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create());
    }

    private void updateFolderPreview() {
        folderPreview.updateFolders(true);
        AndroidUtilities.cancelRunOnUIThread(rebuildFolderFragments);
        AndroidUtilities.runOnUIThread(rebuildFolderFragments, 250);
    }

    private void scheduleAppearanceRebuild() {
        AndroidUtilities.cancelRunOnUIThread(rebuildAppearanceFragments);
        AndroidUtilities.runOnUIThread(rebuildAppearanceFragments, 250);
    }

    private void onAvatarGeometryChanged() {
        AvatarGeometryWidgets.refresh(currentAccount);
        scheduleAppearanceRebuild();
    }

    private void showAppearanceTitleDialog() {
        CharSequence[] options = {
                appearanceTitleOption(AppearanceSettings.TITLE_APP_NAME),
                appearanceTitleOption(AppearanceSettings.TITLE_USERNAME),
                appearanceTitleOption(AppearanceSettings.TITLE_NAME),
                appearanceTitleOption(AppearanceSettings.TITLE_CHATS)
        };
        int selected = AppearanceSettings.getTitleMode(currentAccount);

        for (int i = 0; i < options.length; i++) {
            if (i == selected) {
                options[i] = "✓  " + options[i];
            }
        }

        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(LocaleController.getString(R.string.FluxGramActionBarTitle))
                .setItems(options, (dialog, index) -> {
                    AppearanceSettings.setTitleMode(currentAccount, index);
                    appearancePreview.update(true);
                    listView.adapter.update(true);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create());
    }

    private String dividerStyleOption(int style) {
        if (style == AppearanceSettings.DIVIDER_HIDDEN) {
            return LocaleController.getString(R.string.FluxGramDividerHidden);
        }
        if (style == AppearanceSettings.DIVIDER_SEGMENTS) {
            return LocaleController.getString(R.string.FluxGramDividerSegments);
        }

        return LocaleController.getString(R.string.FluxGramDividerLine);
    }

    private String tabletModeOption(int mode) {
        if (mode == TabletModeSettings.ON) {
            return LocaleController.getString(R.string.FluxGramTabletModeOn);
        }
        if (mode == TabletModeSettings.OFF) {
            return LocaleController.getString(R.string.FluxGramTabletModeOff);
        }

        return LocaleController.getString(R.string.FluxGramTabletModeAuto);
    }

    private String bottomNavigationOption(int mode) {
        if (mode == BottomNavigationSettings.SHOW) {
            return LocaleController.getString(R.string.FluxGramBottomNavigationShow);
        }
        if (mode == BottomNavigationSettings.HIDE) {
            return LocaleController.getString(R.string.FluxGramBottomNavigationHide);
        }

        return LocaleController.getString(R.string.FluxGramBottomNavigationFloating);
    }

    private String transitionStyleOption(int style) {
        if (style == NavigationSettings.TRANSITION_AOSP) {
            return LocaleController.getString(R.string.FluxGramTransitionStyleAosp);
        }
        if (style == NavigationSettings.TRANSITION_SPRING) {
            return LocaleController.getString(R.string.FluxGramTransitionStyleSpring);
        }

        return LocaleController.getString(R.string.FluxGramTransitionStyleDefault);
    }

    private void showTransitionStyleDialog() {
        CharSequence[] options = {
                transitionStyleOption(NavigationSettings.TRANSITION_DEFAULT),
                transitionStyleOption(NavigationSettings.TRANSITION_AOSP),
                transitionStyleOption(NavigationSettings.TRANSITION_SPRING)
        };
        int selected = SharedConfig.navigationTransitionStyle;

        for (int i = 0; i < options.length; i++) {
            if (i == selected) {
                options[i] = "✓  " + options[i];
            }
        }

        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(LocaleController.getString(R.string.FluxGramTransitionStyle))
                .setItems(options, (dialog, index) -> {
                    SharedConfig.setNavigationTransitionStyle(index);
                    if (!AndroidUtilities.isTablet()) {
                        NavigationTransitionPolicy.apply(parentLayout, index);
                    }
                    listView.adapter.update(true);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create());
    }

    private void confirmPredictiveBackChange(boolean enabled) {
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(LocaleController.getString(R.string.FluxGramPredictiveBack))
                .setMessage(LocaleController.getString(R.string.FluxGramPredictiveBackRestartMessage))
                .setPositiveButton(LocaleController.getString(R.string.FluxGramRestartNow),
                        (dialog, which) -> restartWithPredictiveBack(enabled))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create());
    }

    private void restartWithPredictiveBack(boolean enabled) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }

        Intent intent = activity.getPackageManager().getLaunchIntentForPackage(
                activity.getPackageName());
        if (intent == null || !SharedConfig.setPredictiveBackEnabled(enabled)) {
            showInfo(
                    LocaleController.getString(R.string.FluxGramPredictiveBack),
                    LocaleController.getString(R.string.FluxGramPredictiveBackRestartError)
            );

            return;
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.finishAffinity();
        activity.startActivity(intent);
        System.exit(0);
    }

    private void showBottomNavigationDialog() {
        CharSequence[] options = {
                bottomNavigationOption(BottomNavigationSettings.FLOATING),
                bottomNavigationOption(BottomNavigationSettings.SHOW),
                bottomNavigationOption(BottomNavigationSettings.HIDE)
        };
        int selected = SharedConfig.bottomNavigationMode;

        for (int i = 0; i < options.length; i++) {
            if (i == selected) {
                options[i] = "✓  " + options[i];
            }
        }

        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(LocaleController.getString(R.string.FluxGramBottomNavigation))
                .setItems(options, (dialog, index) -> {
                    if (index != SharedConfig.bottomNavigationMode) {
                        confirmBottomNavigationChange(index);
                    }
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create());
    }

    private void confirmBottomNavigationChange(int mode) {
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(LocaleController.getString(R.string.FluxGramTabletModeRestartTitle))
                .setMessage(LocaleController.getString(R.string.FluxGramBottomNavigationRestartMessage))
                .setPositiveButton(LocaleController.getString(R.string.FluxGramRestartNow),
                        (dialog, which) -> restartWithBottomNavigation(mode))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create());
    }

    private void restartWithBottomNavigation(int mode) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }

        Intent intent = activity.getPackageManager().getLaunchIntentForPackage(
                activity.getPackageName());
        if (intent == null || !SharedConfig.setBottomNavigationMode(mode)) {
            showInfo(
                    LocaleController.getString(R.string.FluxGramBottomNavigation),
                    LocaleController.getString(R.string.FluxGramBottomNavigationRestartError)
            );

            return;
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.finishAffinity();
        activity.startActivity(intent);
        System.exit(0);
    }

    private void showTabletModeDialog() {
        CharSequence[] options = {
                tabletModeOption(TabletModeSettings.AUTO),
                tabletModeOption(TabletModeSettings.ON),
                tabletModeOption(TabletModeSettings.OFF)
        };
        int selected = SharedConfig.tabletMode;

        for (int i = 0; i < options.length; i++) {
            if (i == selected) {
                options[i] = "✓  " + options[i];
            }
        }

        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(LocaleController.getString(R.string.FluxGramTabletMode))
                .setItems(options, (dialog, index) -> {
                    if (index != SharedConfig.tabletMode) {
                        confirmTabletModeChange(index);
                    }
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create());
    }

    private void confirmTabletModeChange(int mode) {
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(LocaleController.getString(R.string.FluxGramTabletModeRestartTitle))
                .setMessage(LocaleController.getString(R.string.FluxGramTabletModeRestartMessage))
                .setPositiveButton(LocaleController.getString(R.string.FluxGramRestartNow),
                        (dialog, which) -> restartWithTabletMode(mode))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create());
    }

    private void restartWithTabletMode(int mode) {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }

        Intent intent = activity.getPackageManager().getLaunchIntentForPackage(
                activity.getPackageName());
        if (intent == null || !SharedConfig.setTabletMode(mode)) {
            showInfo(
                    LocaleController.getString(R.string.FluxGramTabletMode),
                    LocaleController.getString(R.string.FluxGramTabletModeRestartError)
            );

            return;
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.finishAffinity();
        activity.startActivity(intent);
        System.exit(0);
    }

    private void showDividerStyleDialog() {
        CharSequence[] options = {
                dividerStyleOption(AppearanceSettings.DIVIDER_HIDDEN),
                dividerStyleOption(AppearanceSettings.DIVIDER_LINE),
                dividerStyleOption(AppearanceSettings.DIVIDER_SEGMENTS)
        };
        int selected = AppearanceSettings.getSettingsDividerStyle(currentAccount);

        for (int i = 0; i < options.length; i++) {
            if (i == selected) {
                options[i] = "✓  " + options[i];
            }
        }

        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(LocaleController.getString(R.string.FluxGramDividerStyle))
                .setItems(options, (dialog, index) -> {
                    AppearanceSettings.setSettingsDividerStyle(currentAccount, index);
                    FluxGramSettingsSections.apply(listView, currentAccount);
                    listView.adapter.update(false);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create());
    }

    private String glassOutlineStyleOption(int style) {
        if (style == AppearanceSettings.GLASS_OUTLINE_SOLID) {
            return LocaleController.getString(R.string.FluxGramGlassOutlineSolid);
        }
        if (style == AppearanceSettings.GLASS_OUTLINE_HIDDEN) {
            return LocaleController.getString(R.string.FluxGramGlassOutlineHidden);
        }

        return LocaleController.getString(R.string.FluxGramGlassOutlineGlare);
    }

    private void showGlassOutlineStyleDialog() {
        CharSequence[] options = {
                glassOutlineStyleOption(AppearanceSettings.GLASS_OUTLINE_GLARE),
                glassOutlineStyleOption(AppearanceSettings.GLASS_OUTLINE_SOLID),
                glassOutlineStyleOption(AppearanceSettings.GLASS_OUTLINE_HIDDEN)
        };
        int selected = AppearanceSettings.getGlassOutlineStyle(currentAccount);

        for (int i = 0; i < options.length; i++) {
            if (i == selected) {
                options[i] = "✓  " + options[i];
            }
        }

        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(LocaleController.getString(R.string.FluxGramGlassOutlineStyle))
                .setItems(options, (dialog, index) -> {
                    AppearanceSettings.setGlassOutlineStyle(currentAccount, index);
                    listView.adapter.update(true);
                    scheduleAppearanceRebuild();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create());
    }

    private boolean preparingShare;

    private void sharePlugin(Plugin plugin) {
        if (!ExternalPlugins.isExternal(plugin)) {
            showInfo(plugin.getName(), LocaleController.getString(R.string.FluxGramBuiltInShareInfo));
            return;
        }

        if (preparingShare) {
            return;
        }

        preparingShare = true;
        ExternalPlugins.export(getParentActivity(), plugin.getId(), (file, error) -> {
            preparingShare = false;

            if (destroyed || getParentActivity() == null) {
                return;
            }

            if (error != null) {
                showError(error);
                return;
            }

            try {
                Intent intent = new Intent(getParentActivity(), LaunchActivity.class);
                intent.setAction(Intent.ACTION_SEND);
                intent.setType("application/octet-stream");
                intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                        getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", file));
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getParentActivity().startActivity(intent);
            } catch (Exception failure) {
                showError(failure);
            }
        });
    }

    private void showError(Exception error) {
        if (destroyed || getParentActivity() == null) {
            return;
        }

        showDialog(
                new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                        .setTitle(LocaleController.getString(R.string.FluxGramPluginError))
                        .setMessage(error.getMessage())
                        .setPositiveButton(LocaleController.getString(R.string.OK), null)
                        .create());
    }

    private void confirmDelete(Plugin plugin) {
        showDialog(
                new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                        .setTitle(LocaleController.formatString(
                                "FluxGramDeletePluginConfirm", R.string.FluxGramDeletePluginConfirm,
                                plugin.getName()))
                        .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                        .setPositiveButton(
                                LocaleController.getString(R.string.Delete),
                                (dialog, which) -> {
                                    ExternalPlugins.delete(
                                            plugin.getId(),
                                            (id, error) -> {
                                                if (destroyed || getParentActivity() == null) {
                                                    return;
                                                }

                                                if (error != null) {
                                                    showError(error);
                                                } else {
                                                    refresh.run();
                                                }
                                            });
                                })
                        .create());
    }

    private void showRestartInfo() {
        showDialog(
                new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                        .setTitle(
                                LocaleController.getString(R.string.FluxGramPluginRestartRequired))
                        .setMessage(LocaleController.getString(R.string.FluxGramPluginRestartInfo))
                        .setNegativeButton(LocaleController.getString(R.string.OK), null)
                        .setPositiveButton(
                                LocaleController.getString(R.string.FluxGramOpenAndroidSettings),
                                (dialog, which) ->
                                        getParentActivity()
                                                .startActivity(
                                                        new Intent(
                                                                android.provider.Settings
                                                                        .ACTION_APPLICATION_DETAILS_SETTINGS,
                                                                android.net.Uri.parse(
                                                                        "package:"
                                                                                + getParentActivity()
                                                                                        .getPackageName()))))
                        .create());
    }

    @Override
    public void onResume() {
        super.onResume();

        if (listView != null) {
            FluxGramSettingsSections.apply(listView, currentAccount);
            listView.adapter.update(false);
        }
    }
}

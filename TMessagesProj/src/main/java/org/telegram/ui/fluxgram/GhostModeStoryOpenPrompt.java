package org.telegram.ui.fluxgram;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.fluxgram.GhostModeSettings;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Stories.StoriesController;

import java.util.ArrayList;

public final class GhostModeStoryOpenPrompt {
    private final BaseFragment fragment;

    private boolean promptShowing;
    private boolean continueOnDismiss;
    private boolean enableBeforeOpen;
    private int pendingAccount;
    private int pendingFragmentAccount;
    private long pendingOwner;
    private OpenContinuation continuation;
    private int temporaryAccount = -1;
    private GhostModeSettings.PrimarySettingsSnapshot temporarySettings;

    public GhostModeStoryOpenPrompt(BaseFragment fragment) {
        this.fragment = fragment;
    }

    public boolean maybeShow(
            int account,
            TL_stories.StoryItem storyItem,
            ArrayList<Long> peerIds,
            int position,
            StoriesController.StoriesList storiesList,
            TL_stories.PeerStories userStories,
            int startStoryId,
            OpenContinuation continuation
    ) {
        if (promptShowing) {
            return true;
        }
        if (!GhostModeSettings.suggestsForStories(account) || GhostModeSettings.shouldHideStoryViews(account)) {
            return false;
        }
        if (fragment == null || fragment.isFinished || fragment.getParentActivity() == null || fragment.getCurrentAccount() != account) {
            return false;
        }

        StoryTarget target = resolveTarget(account, storyItem, peerIds, position, storiesList, userStories, startStoryId);

        if (target == null || !StoriesController.isGhostModeStoryViewEligible(account, target.dialogId, target.storyItem)) {
            return false;
        }

        promptShowing = true;
        continueOnDismiss = false;
        enableBeforeOpen = false;
        pendingAccount = account;
        pendingFragmentAccount = fragment.getCurrentAccount();
        pendingOwner = UserConfig.getInstance(account).getClientUserId();
        this.continuation = continuation;

        AlertDialog dialog = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
                .setTitle(LocaleController.getString(R.string.FluxGramGhostMode))
                .setMessage(LocaleController.getString(R.string.FluxGramGhostModeStoryPrompt))
                .setPositiveButton(LocaleController.getString(R.string.Enable), (alertDialog, which) -> {
                    continueOnDismiss = true;
                    enableBeforeOpen = true;
                })
                .setNegativeButton(LocaleController.getString(R.string.Continue), (alertDialog, which) -> continueOnDismiss = true)
                .setOnPreDismissListener(alertDialog -> finishPrompt())
                .create();

        if (fragment.showDialog(dialog) == null) {
            clearPending();

            return false;
        }

        return true;
    }

    private void finishPrompt() {
        OpenContinuation pendingContinuation = continuation;
        boolean shouldContinue = continueOnDismiss && isRequestCurrent(pendingAccount);
        boolean shouldEnable = enableBeforeOpen;
        int requestAccount = pendingAccount;
        int requestFragmentAccount = pendingFragmentAccount;
        long requestOwner = pendingOwner;
        clearPending();

        if (shouldContinue && pendingContinuation != null) {
            AndroidUtilities.runOnUIThread(() -> {
                if (isContextCurrent(requestAccount, requestFragmentAccount, requestOwner)) {
                    GhostModeSettings.PrimarySettingsSnapshot snapshot = null;

                    if (shouldEnable) {
                        snapshot = enableTemporaryGhostMode(requestAccount);
                    }

                    boolean opened = pendingContinuation.open();

                    if (snapshot != null) {
                        if (opened) {
                            temporaryAccount = requestAccount;
                            temporarySettings = snapshot;
                        } else {
                            restoreTemporaryGhostMode(requestAccount, snapshot);
                        }
                    }
                }
            });
        }
    }

    public void onViewerClosed() {
        if (temporarySettings == null) {
            return;
        }

        int account = temporaryAccount;
        GhostModeSettings.PrimarySettingsSnapshot snapshot = temporarySettings;
        temporaryAccount = -1;
        temporarySettings = null;
        restoreTemporaryGhostMode(account, snapshot);
    }

    private GhostModeSettings.PrimarySettingsSnapshot enableTemporaryGhostMode(int account) {
        boolean hiddenBefore = GhostModeSettings.shouldHideOnline(account);
        boolean automaticOfflineBefore = GhostModeSettings.shouldUseAutomaticOffline(account);
        GhostModeSettings.PrimarySettingsSnapshot snapshot = GhostModeSettings.enableAllPrimarySettings(account);

        if (snapshot != null && (hiddenBefore != GhostModeSettings.shouldHideOnline(account)
                || automaticOfflineBefore != GhostModeSettings.shouldUseAutomaticOffline(account))) {
            MessagesController.getInstance(account).updateGhostModeOnlineStatus();
        }

        return snapshot;
    }

    private void restoreTemporaryGhostMode(int account, GhostModeSettings.PrimarySettingsSnapshot snapshot) {
        boolean hiddenBefore = GhostModeSettings.shouldHideOnline(account);
        boolean automaticOfflineBefore = GhostModeSettings.shouldUseAutomaticOffline(account);

        if (GhostModeSettings.restorePrimarySettings(account, snapshot)
                && (hiddenBefore != GhostModeSettings.shouldHideOnline(account)
                || automaticOfflineBefore != GhostModeSettings.shouldUseAutomaticOffline(account))) {
            MessagesController.getInstance(account).updateGhostModeOnlineStatus();
        }
    }

    private boolean isRequestCurrent(int account) {
        return promptShowing
                && account == pendingAccount
                && isContextCurrent(account, pendingFragmentAccount, pendingOwner);
    }

    private boolean isContextCurrent(int account, int fragmentAccount, long owner) {
        return account == fragmentAccount
                && fragment != null
                && !fragment.isFinished
                && fragment.getParentActivity() != null
                && fragment.getCurrentAccount() == fragmentAccount
                && UserConfig.getInstance(account).getClientUserId() == owner;
    }

    private void clearPending() {
        continuation = null;
        promptShowing = false;
        continueOnDismiss = false;
        enableBeforeOpen = false;
        pendingAccount = 0;
        pendingFragmentAccount = 0;
        pendingOwner = 0;
    }

    private static StoryTarget resolveTarget(
            int account,
            TL_stories.StoryItem storyItem,
            ArrayList<Long> peerIds,
            int position,
            StoriesController.StoriesList storiesList,
            TL_stories.PeerStories userStories,
            int startStoryId
    ) {
        if (storiesList != null) {
            MessageObject listMessage = findListMessage(storiesList, startStoryId);
            TL_stories.StoryItem listStory = listMessage == null ? null : listMessage.storyItem;

            if (listStory == null) {
                listStory = storyItem;
            }
            if (listStory == null && !storiesList.messageObjects.isEmpty()) {
                listStory = storiesList.messageObjects.get(0).storyItem;
            }

            long dialogId = storiesList.dialogId;

            if (dialogId == 0 && listMessage != null) {
                dialogId = listMessage.getDialogId();
            }
            if (dialogId == 0 && listStory != null) {
                dialogId = listStory.dialogId;
            }

            return new StoryTarget(dialogId, listStory);
        }

        long dialogId = resolveDialogId(storyItem, peerIds, position, userStories);

        if (userStories != null || storyItem == null) {
            StoriesController storiesController = MessagesController.getInstance(account).getStoriesController();
            TL_stories.PeerStories peerStories = userStories;

            if (peerStories == null) {
                peerStories = storiesController.getStories(dialogId);
            }
            if (peerStories == null) {
                peerStories = storiesController.getStoriesFromFullPeer(dialogId);
            }

            return new StoryTarget(dialogId, selectPeerStory(storiesController, dialogId, peerStories));
        }

        return new StoryTarget(dialogId, storyItem);
    }

    private static long resolveDialogId(
            TL_stories.StoryItem storyItem,
            ArrayList<Long> peerIds,
            int position,
            TL_stories.PeerStories userStories
    ) {
        if (userStories != null && userStories.peer != null) {
            return DialogObject.getPeerDialogId(userStories.peer);
        }
        if (peerIds != null && position >= 0 && position < peerIds.size()) {
            return peerIds.get(position);
        }

        return storyItem == null ? 0 : storyItem.dialogId;
    }

    private static MessageObject findListMessage(StoriesController.StoriesList storiesList, int startStoryId) {
        if (startStoryId != 0) {
            MessageObject messageObject = storiesList.findMessageObject(startStoryId);

            if (messageObject != null && messageObject.storyItem != null) {
                return messageObject;
            }
        }
        return null;
    }

    private static TL_stories.StoryItem selectPeerStory(
            StoriesController storiesController,
            long dialogId,
            TL_stories.PeerStories peerStories
    ) {
        if (peerStories == null || peerStories.stories == null || peerStories.stories.isEmpty()) {
            return null;
        }

        int maxReadId = storiesController.getMaxStoriesReadId(dialogId);

        if (maxReadId > 0) {
            for (int i = 0; i < peerStories.stories.size(); i++) {
                TL_stories.StoryItem storyItem = peerStories.stories.get(i);

                if (storyItem.id > maxReadId) {
                    return storyItem;
                }
            }
        }

        return peerStories.stories.get(0);
    }

    private static final class StoryTarget {
        private final long dialogId;
        private final TL_stories.StoryItem storyItem;

        private StoryTarget(long dialogId, TL_stories.StoryItem storyItem) {
            this.dialogId = dialogId;
            this.storyItem = storyItem;
        }
    }

    public interface OpenContinuation {
        boolean open();
    }
}

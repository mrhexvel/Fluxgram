package org.telegram.messenger.fluxgram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class MessageActionExecutor {
    public static final String REPLY = "reply";
    public static final String REACT_DEFAULT = "react_default";
    public static final String COPY = "copy";
    public static final String FORWARD = "forward";
    public static final String EDIT = "edit";
    public static final String SELECT = "select";
    public static final String SAVE_TO_GALLERY = "save_to_gallery";
    public static final String SAVE_TO_DOWNLOADS = "save_to_downloads";

    private static final int MAX_SWIPE_ACTIONS = 8;
    private static final List<String> KNOWN_ACTION_IDS = Collections.unmodifiableList(Arrays.asList(
            REPLY,
            REACT_DEFAULT,
            COPY,
            FORWARD,
            EDIT,
            SELECT,
            SAVE_TO_GALLERY,
            SAVE_TO_DOWNLOADS
    ));
    private static final Set<String> KNOWN_ACTION_ID_SET = Collections.unmodifiableSet(
            new HashSet<>(KNOWN_ACTION_IDS)
    );
    private static final List<String> DEFAULT_SWIPE_ORDER = Collections.singletonList(REPLY);

    private final Resolver resolver;

    public MessageActionExecutor(Resolver resolver) {
        this.resolver = resolver;
    }

    public Result execute(String actionId, Trigger trigger, Target expected) {
        if (!KNOWN_ACTION_ID_SET.contains(actionId)) {
            return Result.UNKNOWN_ACTION;
        }
        if (resolver == null || trigger == null || expected == null) {
            return Result.FAILED;
        }

        Resolution resolution;
        try {
            resolution = resolver.resolve(actionId, trigger, expected);
        } catch (RuntimeException exception) {
            return Result.FAILED;
        }

        if (resolution == null) {
            return Result.FAILED;
        }
        if (resolution.getActual() == null) {
            return Result.FAILED;
        }
        if (!expected.equals(resolution.getActual())) {
            return Result.STALE_IDENTITY;
        }
        if (!resolution.isScreenActive()) {
            return Result.SCREEN_INACTIVE;
        }
        if (resolution.getSelection() == null) {
            return Result.FAILED;
        }
        if (resolution.getSelection() != SelectionState.NONE) {
            return Result.SELECTION_ACTIVE;
        }
        if (!resolution.isChatModeAllowed()) {
            return Result.CHAT_MODE_BLOCKED;
        }
        if (!resolution.isPermissionGranted()) {
            return Result.PERMISSION_DENIED;
        }
        if (!resolution.isActionAvailable()) {
            return Result.ACTION_UNAVAILABLE;
        }

        ActionHandler action = resolution.getAction();
        if (action == null) {
            return Result.FAILED;
        }

        try {
            return action.execute() ? Result.EXECUTED : Result.HANDLER_REJECTED;
        } catch (RuntimeException exception) {
            return Result.FAILED;
        }
    }

    public static List<String> normalizeSwipeOrder(Collection<String> actionIds) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (actionIds != null) {
            for (String actionId : actionIds) {
                if (KNOWN_ACTION_ID_SET.contains(actionId)) {
                    normalized.add(actionId);
                }
                if (normalized.size() == MAX_SWIPE_ACTIONS) {
                    break;
                }
            }
        }

        if (normalized.isEmpty()) {
            return DEFAULT_SWIPE_ORDER;
        }

        return Collections.unmodifiableList(new ArrayList<>(normalized));
    }

    public static List<String> knownActionIds() {
        return KNOWN_ACTION_IDS;
    }

    public enum Trigger {
        DOUBLE_TAP,
        SWIPE
    }

    public enum SelectionState {
        NONE,
        MESSAGE,
        TEXT
    }

    public enum Result {
        EXECUTED,
        UNKNOWN_ACTION,
        STALE_IDENTITY,
        SCREEN_INACTIVE,
        SELECTION_ACTIVE,
        CHAT_MODE_BLOCKED,
        PERMISSION_DENIED,
        ACTION_UNAVAILABLE,
        HANDLER_REJECTED,
        FAILED
    }

    public interface Resolver {
        Resolution resolve(String actionId, Trigger trigger, Target expected);
    }

    public interface ActionHandler {
        boolean execute();
    }

    public static final class Target {
        private final int account;
        private final long ownerId;
        private final long presentationId;
        private final long chatDialogId;
        private final long messageDialogId;
        private final int messageId;
        private final long topicId;

        public Target(int account, long ownerId, long presentationId, long chatDialogId,
                long messageDialogId, int messageId, long topicId) {
            if (account < 0
                    || ownerId <= 0
                    || presentationId <= 0
                    || chatDialogId == 0
                    || messageDialogId == 0
                    || messageId == 0
                    || topicId < 0) {
                throw new IllegalArgumentException("Invalid message action identity");
            }

            this.account = account;
            this.ownerId = ownerId;
            this.presentationId = presentationId;
            this.chatDialogId = chatDialogId;
            this.messageDialogId = messageDialogId;
            this.messageId = messageId;
            this.topicId = topicId;
        }

        public int getAccount() {
            return account;
        }

        public long getOwnerId() {
            return ownerId;
        }

        public long getPresentationId() {
            return presentationId;
        }

        public long getChatDialogId() {
            return chatDialogId;
        }

        public long getMessageDialogId() {
            return messageDialogId;
        }

        public int getMessageId() {
            return messageId;
        }

        public long getTopicId() {
            return topicId;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Target)) {
                return false;
            }

            Target target = (Target) object;
            return account == target.account
                    && ownerId == target.ownerId
                    && presentationId == target.presentationId
                    && chatDialogId == target.chatDialogId
                    && messageDialogId == target.messageDialogId
                    && messageId == target.messageId
                    && topicId == target.topicId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(account, ownerId, presentationId, chatDialogId,
                    messageDialogId, messageId, topicId);
        }
    }

    public static final class Resolution {
        private final Target actual;
        private final boolean screenActive;
        private final SelectionState selection;
        private final boolean chatModeAllowed;
        private final boolean permissionGranted;
        private final boolean actionAvailable;
        private final ActionHandler action;

        public Resolution(Target actual, boolean screenActive, SelectionState selection,
                boolean chatModeAllowed, boolean permissionGranted, boolean actionAvailable,
                ActionHandler action) {
            this.actual = actual;
            this.screenActive = screenActive;
            this.selection = selection;
            this.chatModeAllowed = chatModeAllowed;
            this.permissionGranted = permissionGranted;
            this.actionAvailable = actionAvailable;
            this.action = action;
        }

        public Target getActual() {
            return actual;
        }

        public boolean isScreenActive() {
            return screenActive;
        }

        public SelectionState getSelection() {
            return selection;
        }

        public boolean isChatModeAllowed() {
            return chatModeAllowed;
        }

        public boolean isPermissionGranted() {
            return permissionGranted;
        }

        public boolean isActionAvailable() {
            return actionAvailable;
        }

        public ActionHandler getAction() {
            return action;
        }
    }
}

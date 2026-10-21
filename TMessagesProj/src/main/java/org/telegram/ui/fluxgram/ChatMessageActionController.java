package org.telegram.ui.fluxgram;

import org.telegram.messenger.fluxgram.MessageActionExecutor;

import java.util.concurrent.atomic.AtomicLong;

public final class ChatMessageActionController {
    private static final AtomicLong nextPresentationId = new AtomicLong(1);

    private final long presentationId = nextPresentationId.getAndIncrement();
    private boolean destroyed;

    public MessageActionExecutor.Target capture(
            int account,
            long ownerId,
            long chatDialogId,
            long messageDialogId,
            int messageId,
            long topicId
    ) {
        if (destroyed
                || account < 0
                || ownerId <= 0
                || chatDialogId == 0
                || messageDialogId == 0
                || messageId == 0
                || topicId < 0) {
            return null;
        }

        return new MessageActionExecutor.Target(
                account,
                ownerId,
                presentationId,
                chatDialogId,
                messageDialogId,
                messageId,
                topicId
        );
    }

    public MessageActionExecutor.Result execute(
            String actionId,
            MessageActionExecutor.Trigger trigger,
            MessageActionExecutor.Target expected,
            MessageActionExecutor.Resolver resolver
    ) {
        if (destroyed) {
            return MessageActionExecutor.Result.SCREEN_INACTIVE;
        }
        if (expected == null || expected.getPresentationId() != presentationId) {
            return MessageActionExecutor.Result.STALE_IDENTITY;
        }

        MessageActionExecutor executor = new MessageActionExecutor((resolvedActionId,
                resolvedTrigger, resolvedTarget) -> {
            if (destroyed) {
                return null;
            }

            MessageActionExecutor.Resolution resolution = resolver.resolve(
                    resolvedActionId,
                    resolvedTrigger,
                    resolvedTarget
            );

            return destroyed ? null : resolution;
        });

        return executor.execute(actionId, trigger, expected);
    }

    public void destroy() {
        destroyed = true;
    }
}

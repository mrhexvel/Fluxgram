from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
EXECUTOR = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/fluxgram/MessageActionExecutor.java"


class MessageActionExecutorTest(unittest.TestCase):
    def test_executor_revalidates_identity_state_and_availability(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("java and javac are required")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            check = root / "MessageActionExecutorCheck.java"
            check.write_text(textwrap.dedent("""
                import java.util.Arrays;
                import java.util.Collections;
                import java.util.List;
                import org.telegram.messenger.fluxgram.MessageActionExecutor;

                public final class MessageActionExecutorCheck {
                    private static final MessageActionExecutor.Target TARGET = target(7);

                    public static void main(String[] args) {
                        checkKnownActionsAndSwipeOrder();
                        checkUnknownAndInvalidRequests();
                        checkInvalidIdentity();
                        checkIdentity();
                        checkValidationOrder();
                        checkHandlerResults();
                    }

                    private static void checkKnownActionsAndSwipeOrder() {
                        require(MessageActionExecutor.knownActionIds().equals(Arrays.asList(
                                 MessageActionExecutor.REPLY,
                                 MessageActionExecutor.REACT_DEFAULT,
                                 MessageActionExecutor.COPY,
                                 MessageActionExecutor.FORWARD
                        )));
                        require(MessageActionExecutor.normalizeSwipeOrder(null).equals(
                                Collections.singletonList(MessageActionExecutor.REPLY)
                        ));
                        require(MessageActionExecutor.normalizeSwipeOrder(Arrays.asList(
                                "unknown",
                                MessageActionExecutor.REACT_DEFAULT,
                                 MessageActionExecutor.REACT_DEFAULT,
                                 MessageActionExecutor.COPY,
                                 MessageActionExecutor.FORWARD,
                                 MessageActionExecutor.REPLY
                         )).equals(Arrays.asList(
                                 MessageActionExecutor.REACT_DEFAULT,
                                 MessageActionExecutor.COPY,
                                 MessageActionExecutor.FORWARD,
                                 MessageActionExecutor.REPLY
                        )));
                        require(MessageActionExecutor.normalizeSwipeOrder(Arrays.asList(
                                "unknown",
                                null
                        )).equals(Collections.singletonList(MessageActionExecutor.REPLY)));
                        requireImmutable(MessageActionExecutor.knownActionIds());
                        requireImmutable(MessageActionExecutor.normalizeSwipeOrder(Arrays.asList(
                                MessageActionExecutor.REPLY,
                                MessageActionExecutor.REACT_DEFAULT
                        )));
                    }

                    private static void checkUnknownAndInvalidRequests() {
                        int[] resolverCalls = {0};
                        MessageActionExecutor executor = new MessageActionExecutor((action, trigger, expected) -> {
                            resolverCalls[0]++;
                            return available(TARGET, () -> true);
                        });
                        require(executor.execute("unknown", MessageActionExecutor.Trigger.SWIPE, TARGET)
                                == MessageActionExecutor.Result.UNKNOWN_ACTION);
                        require(resolverCalls[0] == 0);
                        require(executor.execute(MessageActionExecutor.REPLY, null, TARGET)
                                == MessageActionExecutor.Result.FAILED);
                        require(executor.execute(MessageActionExecutor.REPLY,
                                MessageActionExecutor.Trigger.SWIPE, null)
                                == MessageActionExecutor.Result.FAILED);
                        require(new MessageActionExecutor(null).execute(
                                MessageActionExecutor.REPLY,
                                MessageActionExecutor.Trigger.SWIPE,
                                TARGET
                        ) == MessageActionExecutor.Result.FAILED);
                        require(new MessageActionExecutor((action, trigger, expected) -> null).execute(
                                MessageActionExecutor.REPLY,
                                MessageActionExecutor.Trigger.SWIPE,
                                TARGET
                        ) == MessageActionExecutor.Result.FAILED);
                        require(execute(new MessageActionExecutor.Resolution(
                                null,
                                true,
                                MessageActionExecutor.SelectionState.NONE,
                                true,
                                true,
                                true,
                                () -> true
                        )) == MessageActionExecutor.Result.FAILED);
                        require(new MessageActionExecutor((action, trigger, expected) -> {
                            throw new IllegalStateException();
                        }).execute(
                                MessageActionExecutor.REPLY,
                                MessageActionExecutor.Trigger.SWIPE,
                                TARGET
                        ) == MessageActionExecutor.Result.FAILED);
                    }

                    private static void checkInvalidIdentity() {
                        requireInvalidTarget(-1, 2, 3, 4, 5, 6, 7);
                        requireInvalidTarget(0, 0, 3, 4, 5, 6, 7);
                        requireInvalidTarget(0, 2, 0, 4, 5, 6, 7);
                        requireInvalidTarget(0, 2, 3, 0, 5, 6, 7);
                        requireInvalidTarget(0, 2, 3, 4, 0, 6, 7);
                        requireInvalidTarget(0, 2, 3, 4, 5, 0, 7);
                        requireInvalidTarget(0, 2, 3, 4, 5, 6, -1);
                        require(new MessageActionExecutor.Target(0, 2, 3, -4, -5, -6, 0)
                                .getMessageId() == -6);
                    }

                    private static void checkIdentity() {
                        MessageActionExecutor.Target[] staleTargets = {
                                new MessageActionExecutor.Target(1, 2, 3, 4, 5, 6, 7),
                                new MessageActionExecutor.Target(0, 8, 3, 4, 5, 6, 7),
                                new MessageActionExecutor.Target(0, 2, 8, 4, 5, 6, 7),
                                new MessageActionExecutor.Target(0, 2, 3, 8, 5, 6, 7),
                                new MessageActionExecutor.Target(0, 2, 3, 4, 8, 6, 7),
                                new MessageActionExecutor.Target(0, 2, 3, 4, 5, 8, 7),
                                new MessageActionExecutor.Target(0, 2, 3, 4, 5, 6, 8)
                        };

                        for (MessageActionExecutor.Target actual : staleTargets) {
                            require(execute(available(actual, () -> true))
                                    == MessageActionExecutor.Result.STALE_IDENTITY);
                        }
                        require(TARGET.equals(target(7)));
                        require(TARGET.hashCode() == target(7).hashCode());
                        require(!TARGET.equals(target(8)));
                    }

                    private static void checkValidationOrder() {
                        require(execute(resolution(TARGET, false,
                                MessageActionExecutor.SelectionState.MESSAGE,
                                false, false, false, null))
                                == MessageActionExecutor.Result.SCREEN_INACTIVE);
                        require(execute(resolution(TARGET, true,
                                MessageActionExecutor.SelectionState.MESSAGE,
                                false, false, false, null))
                                == MessageActionExecutor.Result.SELECTION_ACTIVE);
                        require(execute(resolution(TARGET, true,
                                MessageActionExecutor.SelectionState.TEXT,
                                true, true, true, () -> true))
                                == MessageActionExecutor.Result.SELECTION_ACTIVE);
                        require(execute(resolution(TARGET, true, null,
                                true, true, true, () -> true))
                                == MessageActionExecutor.Result.FAILED);
                        require(execute(resolution(TARGET, true,
                                MessageActionExecutor.SelectionState.NONE,
                                false, false, false, null))
                                == MessageActionExecutor.Result.CHAT_MODE_BLOCKED);
                        require(execute(resolution(TARGET, true,
                                MessageActionExecutor.SelectionState.NONE,
                                true, false, false, null))
                                == MessageActionExecutor.Result.PERMISSION_DENIED);
                        require(execute(resolution(TARGET, true,
                                MessageActionExecutor.SelectionState.NONE,
                                true, true, false, null))
                                == MessageActionExecutor.Result.ACTION_UNAVAILABLE);
                        require(execute(resolution(TARGET, true,
                                MessageActionExecutor.SelectionState.NONE,
                                true, true, true, null))
                                == MessageActionExecutor.Result.FAILED);
                    }

                    private static void checkHandlerResults() {
                        int[] calls = {0};
                        MessageActionExecutor executor = new MessageActionExecutor((action, trigger, expected) -> {
                            require(action.equals(MessageActionExecutor.REACT_DEFAULT));
                            require(trigger == MessageActionExecutor.Trigger.DOUBLE_TAP);
                            require(expected == TARGET);
                            return available(TARGET, () -> {
                                calls[0]++;
                                return true;
                            });
                        });
                        require(executor.execute(
                                MessageActionExecutor.REACT_DEFAULT,
                                MessageActionExecutor.Trigger.DOUBLE_TAP,
                                TARGET
                        ) == MessageActionExecutor.Result.EXECUTED);
                        require(calls[0] == 1);
                        require(execute(available(TARGET, () -> false))
                                == MessageActionExecutor.Result.HANDLER_REJECTED);
                        require(execute(available(TARGET, () -> {
                            throw new IllegalStateException();
                        })) == MessageActionExecutor.Result.FAILED);
                    }

                    private static MessageActionExecutor.Result execute(
                            MessageActionExecutor.Resolution resolution) {
                        MessageActionExecutor executor = new MessageActionExecutor(
                                (action, trigger, expected) -> resolution
                        );
                        return executor.execute(
                                MessageActionExecutor.REPLY,
                                MessageActionExecutor.Trigger.SWIPE,
                                TARGET
                        );
                    }

                    private static MessageActionExecutor.Resolution available(
                            MessageActionExecutor.Target actual,
                            MessageActionExecutor.ActionHandler action) {
                        return resolution(actual, true, MessageActionExecutor.SelectionState.NONE,
                                true, true, true, action);
                    }

                    private static MessageActionExecutor.Resolution resolution(
                            MessageActionExecutor.Target actual,
                            boolean screenActive,
                            MessageActionExecutor.SelectionState selection,
                            boolean chatModeAllowed,
                            boolean permissionGranted,
                            boolean actionAvailable,
                            MessageActionExecutor.ActionHandler action) {
                        return new MessageActionExecutor.Resolution(
                                actual,
                                screenActive,
                                selection,
                                chatModeAllowed,
                                permissionGranted,
                                actionAvailable,
                                action
                        );
                    }

                    private static MessageActionExecutor.Target target(long topicId) {
                        return new MessageActionExecutor.Target(0, 2, 3, 4, 5, 6, topicId);
                    }

                    private static void requireInvalidTarget(
                            int account,
                            long ownerId,
                            long presentationId,
                            long chatDialogId,
                            long messageDialogId,
                            int messageId,
                            long topicId) {
                        try {
                            new MessageActionExecutor.Target(
                                    account,
                                    ownerId,
                                    presentationId,
                                    chatDialogId,
                                    messageDialogId,
                                    messageId,
                                    topicId
                            );
                        } catch (IllegalArgumentException expected) {
                            return;
                        }

                        throw new AssertionError();
                    }

                    private static void requireImmutable(List<String> values) {
                        try {
                            values.add("invalid");
                        } catch (UnsupportedOperationException expected) {
                            return;
                        }

                        throw new AssertionError();
                    }

                    private static void require(boolean value) {
                        if (!value) {
                            throw new AssertionError();
                        }
                    }
                }
            """))

            subprocess.run(
                ["javac", "--release", "8", "-d", str(root), str(EXECUTOR), str(check)],
                check=True,
            )
            subprocess.run(
                ["java", "-cp", str(root), "MessageActionExecutorCheck"],
                check=True,
            )

    def test_executor_is_independent_from_android_and_telegram_ui(self):
        source = EXECUTOR.read_text()

        self.assertNotIn("android.", source)
        self.assertNotIn("org.telegram.ui", source)
        self.assertNotIn("MessageObject", source)
        self.assertNotIn("View", source)


if __name__ == "__main__":
    unittest.main()

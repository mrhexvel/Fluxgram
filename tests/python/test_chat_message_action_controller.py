from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
EXECUTOR = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/fluxgram/MessageActionExecutor.java"
CONTROLLER = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/fluxgram/ChatMessageActionController.java"
CHAT_ACTIVITY = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java"


class ChatMessageActionControllerTest(unittest.TestCase):
    def test_controller_owns_presentation_and_lifecycle(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("java and javac are required")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            check = root / "ChatMessageActionControllerCheck.java"
            check.write_text(textwrap.dedent("""
                import org.telegram.messenger.fluxgram.MessageActionExecutor;
                import org.telegram.ui.fluxgram.ChatMessageActionController;

                public final class ChatMessageActionControllerCheck {
                    public static void main(String[] args) {
                        checkCaptureAndExecution();
                        checkPresentationIsolation();
                        checkDestroy();
                    }

                    private static void checkCaptureAndExecution() {
                        ChatMessageActionController controller = new ChatMessageActionController();
                        MessageActionExecutor.Target target = controller.capture(1, 2, -3, -3, 4, 5);
                        require(target != null);
                        require(target.getAccount() == 1);
                        require(target.getOwnerId() == 2);
                        require(target.getChatDialogId() == -3);
                        require(target.getMessageDialogId() == -3);
                        require(target.getMessageId() == 4);
                        require(target.getTopicId() == 5);

                        int[] resolverCalls = {0};
                        int[] handlerCalls = {0};
                        MessageActionExecutor.Result result = controller.execute(
                                MessageActionExecutor.REACT_DEFAULT,
                                MessageActionExecutor.Trigger.DOUBLE_TAP,
                                target,
                                (action, trigger, expected) -> {
                                    resolverCalls[0]++;
                                    require(action.equals(MessageActionExecutor.REACT_DEFAULT));
                                    require(trigger == MessageActionExecutor.Trigger.DOUBLE_TAP);
                                    require(expected == target);
                                    return available(target, () -> {
                                        handlerCalls[0]++;
                                        return true;
                                    });
                                }
                        );
                        require(result == MessageActionExecutor.Result.EXECUTED);
                        require(resolverCalls[0] == 1);
                        require(handlerCalls[0] == 1);
                        require(controller.capture(-1, 2, -3, -3, 4, 5) == null);
                        require(controller.capture(1, 0, -3, -3, 4, 5) == null);
                        require(controller.capture(1, 2, 0, -3, 4, 5) == null);
                        require(controller.capture(1, 2, -3, 0, 4, 5) == null);
                        require(controller.capture(1, 2, -3, -3, 0, 5) == null);
                        require(controller.capture(1, 2, -3, -3, 4, -1) == null);
                    }

                    private static void checkPresentationIsolation() {
                        ChatMessageActionController first = new ChatMessageActionController();
                        ChatMessageActionController second = new ChatMessageActionController();
                        MessageActionExecutor.Target firstTarget = first.capture(0, 2, 3, 3, 4, 0);
                        MessageActionExecutor.Target secondTarget = second.capture(0, 2, 3, 3, 4, 0);
                        require(firstTarget != null);
                        require(secondTarget != null);
                        require(firstTarget.getPresentationId() != secondTarget.getPresentationId());
                        require(!firstTarget.equals(secondTarget));
                        require(first.execute(
                                MessageActionExecutor.REACT_DEFAULT,
                                MessageActionExecutor.Trigger.DOUBLE_TAP,
                                secondTarget,
                                (action, trigger, expected) -> available(secondTarget, () -> true)
                        ) == MessageActionExecutor.Result.STALE_IDENTITY);
                    }

                    private static void checkDestroy() {
                        ChatMessageActionController controller = new ChatMessageActionController();
                        MessageActionExecutor.Target target = controller.capture(0, 2, 3, 3, 4, 0);
                        require(target != null);

                        int[] handlerCalls = {0};
                        MessageActionExecutor.Result result = controller.execute(
                                MessageActionExecutor.REACT_DEFAULT,
                                MessageActionExecutor.Trigger.DOUBLE_TAP,
                                target,
                                (action, trigger, expected) -> {
                                    controller.destroy();
                                    return available(target, () -> {
                                        handlerCalls[0]++;
                                        return true;
                                    });
                                }
                        );
                        require(result == MessageActionExecutor.Result.FAILED);
                        require(handlerCalls[0] == 0);
                        require(controller.capture(0, 2, 3, 3, 4, 0) == null);
                        require(controller.execute(
                                MessageActionExecutor.REACT_DEFAULT,
                                MessageActionExecutor.Trigger.DOUBLE_TAP,
                                target,
                                (action, trigger, expected) -> available(target, () -> true)
                        ) == MessageActionExecutor.Result.SCREEN_INACTIVE);
                        controller.destroy();
                    }

                    private static MessageActionExecutor.Resolution available(
                            MessageActionExecutor.Target target,
                            MessageActionExecutor.ActionHandler action) {
                        return new MessageActionExecutor.Resolution(
                                target,
                                true,
                                MessageActionExecutor.SelectionState.NONE,
                                true,
                                true,
                                true,
                                action
                        );
                    }

                    private static void require(boolean value) {
                        if (!value) {
                            throw new AssertionError();
                        }
                    }
                }
            """))

            subprocess.run(
                [
                    "javac",
                    "--release",
                    "8",
                    "-d",
                    str(root),
                    str(EXECUTOR),
                    str(CONTROLLER),
                    str(check),
                ],
                check=True,
            )
            subprocess.run(
                ["java", "-cp", str(root), "ChatMessageActionControllerCheck"],
                check=True,
            )

    def test_chat_activity_uses_fresh_resolution_for_default_reaction(self):
        source = CHAT_ACTIVITY.read_text()
        double_tap = source[source.index("public void onDoubleTap("):source.index("private class ChatActivityEnterViewDelegate")]

        self.assertIn("fluxGramMessageActions.execute(", double_tap)
        self.assertIn("MessageActionExecutor.REACT_DEFAULT", double_tap)
        self.assertIn("resolveDefaultReactionAction(", double_tap)
        self.assertIn("findCurrentMessageForAction(expected)", double_tap)
        self.assertIn("chatAdapter.filteredMessages.get(index)", double_tap)
        self.assertIn("messageObject.getDialogId() == expected.getMessageDialogId()", double_tap)
        self.assertIn("messagesDict[loadIndex].get(expected.getMessageId())", double_tap)
        self.assertIn("tappedView.isAttachedToWindow()", double_tap)
        self.assertIn("showsMessageForAction(tappedView, messageObject)", double_tap)
        self.assertIn("fluxGramMessageActions.destroy();", source)


if __name__ == "__main__":
    unittest.main()

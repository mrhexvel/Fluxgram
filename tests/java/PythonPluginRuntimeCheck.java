package org.telegram.messenger.plugins;

import java.util.ArrayList;
import java.util.Arrays;

public final class PythonPluginRuntimeCheck {
    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        PythonPluginLifecycle lifecycle = new PythonPluginLifecycle();
        require(lifecycle.getState() == PythonPluginLifecycle.State.DISABLED, "initial state");
        require(lifecycle.requestEnabled(true), "first reconcile");

        PythonPluginLifecycle.Request first = lifecycle.currentRequest();
        require(first.enabled && lifecycle.isCurrent(first.generation), "enabled request");
        require(!lifecycle.requestEnabled(false), "toggle must coalesce");
        require(lifecycle.finishReconcile(first.generation), "new generation must continue");

        PythonPluginLifecycle.Request disabled = lifecycle.currentRequest();
        require(!disabled.enabled, "latest desired state");
        require(
                lifecycle.setState(disabled.generation, PythonPluginLifecycle.State.DISABLED),
                "current state transition");
        require(!lifecycle.finishReconcile(disabled.generation), "reconcile must finish");

        require(lifecycle.requestEnabled(true), "restart reconcile");
        PythonPluginLifecycle.Request failing = lifecycle.currentRequest();
        require(lifecycle.fail(failing.generation), "current failure");
        require(lifecycle.getState() == PythonPluginLifecycle.State.FAILED, "failed state");
        require(!lifecycle.isEnabled(), "failure disables runtime");
        require(!lifecycle.finishReconcile(failing.generation), "failed reconcile finishes");

        lifecycle.beginRemoval();
        require(lifecycle.isRemovalPending(), "removal state");
        require(
                lifecycle.getState() == PythonPluginLifecycle.State.REMOVAL_PENDING,
                "removal phase");
        require(!lifecycle.requestEnabled(true), "removal blocks enable");
        lifecycle.terminate();
        require(lifecycle.getState() == PythonPluginLifecycle.State.TERMINATED, "terminated");

        PythonPluginTaskQueue queue = new PythonPluginTaskQueue(3);
        ArrayList<String> calls = new ArrayList<>();
        require(queue.offer(() -> calls.add("first")), "first task");
        require(queue.offer(() -> calls.add("second")), "second task");
        require(
                queue.offer(PythonPluginTaskQueue.control(() -> calls.add("control"))),
                "control task");
        require(!queue.offer(() -> calls.add("overflow")), "bounded queue");

        while (!queue.isEmpty()) {
            queue.remove().run();
        }

        require(
                calls.equals(Arrays.asList("control", "first", "second")),
                "control priority");
    }
}

package org.telegram.ui.Components;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;

public final class FluxGramSwipeActionCell extends TextCheckCell {
    private static final int ACTION_MOVE_UP = 0x01020001;
    private static final int ACTION_MOVE_DOWN = 0x01020002;

    private final View reorderHandle;
    private boolean canMoveUp;
    private boolean canMoveDown;
    private Utilities.Callback<Integer> moveCallback;
    private Runnable reorderCallback;

    public FluxGramSwipeActionCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);

        setIcon(R.drawable.list_reorder);
        reorderHandle = new View(context);
        reorderHandle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        reorderHandle.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && reorderCallback != null) {
                reorderCallback.run();
            }

            return true;
        });
        addView(reorderHandle, LayoutHelper.createFrame(
                56,
                LayoutHelper.MATCH_PARENT,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP
        ));
    }

    public void setItem(
            CharSequence title,
            boolean active,
            boolean needDivider,
            boolean canMoveUp,
            boolean canMoveDown,
            Runnable reorderCallback,
            Utilities.Callback<Integer> moveCallback
    ) {
        this.canMoveUp = canMoveUp;
        this.canMoveDown = canMoveDown;
        this.reorderCallback = reorderCallback;
        this.moveCallback = moveCallback;

        setTextAndCheck(title, active, needDivider);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);

        if (canMoveUp) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                    ACTION_MOVE_UP,
                    LocaleController.getString(R.string.FluxGramMoveUp)
            ));
        }
        if (canMoveDown) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                    ACTION_MOVE_DOWN,
                    LocaleController.getString(R.string.FluxGramMoveDown)
            ));
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == ACTION_MOVE_UP && canMoveUp && moveCallback != null) {
            moveCallback.run(-1);
            return true;
        }
        if (action == ACTION_MOVE_DOWN && canMoveDown && moveCallback != null) {
            moveCallback.run(1);
            return true;
        }

        return super.performAccessibilityAction(action, arguments);
    }

    public static final class Factory extends UItem.UItemFactory<FluxGramSwipeActionCell> {
        private static final int FLAG_MOVE_UP = 1;
        private static final int FLAG_MOVE_DOWN = 1 << 1;

        static {
            setup(new Factory());
        }

        @Override
        public FluxGramSwipeActionCell createView(
                Context context,
                RecyclerListView listView,
                int currentAccount,
                int classGuid,
                Theme.ResourcesProvider resourcesProvider
        ) {
            return new FluxGramSwipeActionCell(context, resourcesProvider);
        }

        @Override
        public void bindView(
                View view,
                UItem item,
                boolean divider,
                UniversalAdapter adapter,
                UniversalRecyclerView listView
        ) {
            FluxGramSwipeActionCell cell = (FluxGramSwipeActionCell) view;
            cell.setItem(
                    item.text,
                    item.checked,
                    divider,
                    (item.flags & FLAG_MOVE_UP) != 0,
                    (item.flags & FLAG_MOVE_DOWN) != 0,
                    () -> listView.startReorder(cell),
                    item.intCallback
            );
        }

        @Override
        public boolean equals(UItem first, UItem second) {
            return first.object != null && first.object.equals(second.object);
        }

        @Override
        public boolean contentsEquals(UItem first, UItem second) {
            return equals(first, second)
                    && first.checked == second.checked
                    && first.flags == second.flags
                    && first.text != null
                    && first.text.equals(second.text);
        }

        public static UItem of(
                String actionId,
                CharSequence title,
                boolean active,
                boolean canMoveUp,
                boolean canMoveDown,
                Utilities.Callback<Integer> moveCallback
        ) {
            UItem item = UItem.ofFactory(Factory.class);
            item.object = actionId;
            item.text = title;
            item.checked = active;
            item.flags = (canMoveUp ? FLAG_MOVE_UP : 0)
                    | (canMoveDown ? FLAG_MOVE_DOWN : 0);
            item.intCallback = moveCallback;

            return item;
        }
    }
}

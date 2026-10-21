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

public final class FluxGramMainMenuCell extends TextCheckCell {
    private static final int ACTION_MOVE_UP = 0x01020001;
    private static final int ACTION_MOVE_DOWN = 0x01020002;

    private final View reorderHandle;
    private boolean dividerItem;
    private boolean canMoveUp;
    private boolean canMoveDown;
    private Utilities.Callback<Integer> moveCallback;
    private Runnable reorderCallback;

    public FluxGramMainMenuCell(Context context, Theme.ResourcesProvider resourcesProvider) {
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
            boolean visible,
            boolean dividerItem,
            boolean needDivider,
            boolean canMoveUp,
            boolean canMoveDown,
            Runnable reorderCallback,
            Utilities.Callback<Integer> moveCallback
    ) {
        this.dividerItem = dividerItem;
        this.canMoveUp = canMoveUp;
        this.canMoveDown = canMoveDown;
        this.reorderCallback = reorderCallback;
        this.moveCallback = moveCallback;

        setTextAndCheck(title, visible, needDivider);
        getCheckBox().setVisibility(dividerItem ? INVISIBLE : VISIBLE);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);

        if (dividerItem) {
            info.setClassName(View.class.getName());
            info.setCheckable(false);
            info.setChecked(false);
        }
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

    public static final class Factory extends UItem.UItemFactory<FluxGramMainMenuCell> {
        private static final int FLAG_DIVIDER = 1;
        private static final int FLAG_MOVE_UP = 1 << 1;
        private static final int FLAG_MOVE_DOWN = 1 << 2;

        static {
            setup(new Factory());
        }

        @Override
        public FluxGramMainMenuCell createView(
                Context context,
                RecyclerListView listView,
                int currentAccount,
                int classGuid,
                Theme.ResourcesProvider resourcesProvider
        ) {
            return new FluxGramMainMenuCell(context, resourcesProvider);
        }

        @Override
        public void bindView(
                View view,
                UItem item,
                boolean divider,
                UniversalAdapter adapter,
                UniversalRecyclerView listView
        ) {
            FluxGramMainMenuCell cell = (FluxGramMainMenuCell) view;
            cell.setItem(
                    item.text,
                    item.checked,
                    (item.flags & FLAG_DIVIDER) != 0,
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
                String semanticId,
                CharSequence title,
                boolean visible,
                boolean divider,
                boolean canMoveUp,
                boolean canMoveDown,
                Utilities.Callback<Integer> moveCallback
        ) {
            UItem item = UItem.ofFactory(Factory.class);
            item.object = semanticId;
            item.text = title;
            item.checked = visible;
            item.flags = (divider ? FLAG_DIVIDER : 0)
                    | (canMoveUp ? FLAG_MOVE_UP : 0)
                    | (canMoveDown ? FLAG_MOVE_DOWN : 0);
            item.intCallback = moveCallback;

            return item;
        }
    }
}

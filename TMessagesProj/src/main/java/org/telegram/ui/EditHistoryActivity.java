package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.fluxgram.EditHistory;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.ArrayList;

public final class EditHistoryActivity extends BaseFragment {
    private final MessageObject source;
    private final ArrayList<MessageObject> versions = new ArrayList<>();
    private final ArrayList<Integer> dates = new ArrayList<>();
    private boolean destroyed;
    private RecyclerListView list;
    private HistoryAdapter adapter;
    private EmptyTextProgressView empty;

    public EditHistoryActivity(int account, MessageObject source) {
        setCurrentAccount(account);
        this.source = source;
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString(R.string.FluxGramEditHistory));
        actionBar.setSubtitle(LocaleController.getString(R.string.FluxGramEditHistoryChatInfo));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        SizeNotifierFrameLayout content = new SizeNotifierFrameLayout(context) {
            @Override
            protected boolean isActionBarVisible() {
                return false;
            }

            @Override
            protected boolean isStatusBarVisible() {
                return false;
            }

            @Override
            protected boolean useRootView() {
                return false;
            }
        };
        content.setOccupyStatusBar(false);
        content.setBackgroundImage(Theme.getCachedWallpaper(), false);
        content.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        fragmentView = content;

        list = new RecyclerListView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setItemAnimator(null);
        list.setClipToPadding(false);
        list.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(16));
        adapter = new HistoryAdapter();
        list.setAdapter(adapter);
        content.addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        empty = new EmptyTextProgressView(context, null, getResourceProvider());
        empty.setText(LocaleController.getString(R.string.Loading));
        empty.showTextView();
        content.addView(empty, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        EditHistory.load(currentAccount, source.getDialogId(), source.getId(), (saved, failed) -> {
            if (destroyed || isFinished) {
                return;
            }

            versions.clear();
            dates.clear();

            if (failed || saved.isEmpty()) {
                empty.setText(LocaleController.getString(failed
                        ? R.string.FluxGramEditHistoryError : R.string.FluxGramEditHistoryEmpty));
                return;
            }

            for (int i = saved.size() - 1; i >= 0; i--) {
                addVersion(saved.get(i));
            }

            addVersion(source.messageOwner);
            empty.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();
            list.scrollToPosition(versions.size() - 1);
        });

        return fragmentView;
    }

    private void addVersion(TLRPC.Message version) {
        TLRPC.TL_message display = new TLRPC.TL_message();
        display.id = versions.size() + 1;
        display.peer_id = source.messageOwner.peer_id;
        display.from_id = source.messageOwner.from_id;
        display.dialog_id = source.getDialogId();
        display.out = source.isOutOwner();
        display.date = version.edit_date == 0 ? version.date : version.edit_date;
        display.message = version.message == null || version.message.isEmpty()
                ? LocaleController.getString(R.string.FluxGramEditHistoryNoText) : version.message;
        display.entities = new ArrayList<>(version.entities);
        display.media = new TLRPC.TL_messageMediaEmpty();
        display.flags = 256;

        if (!display.entities.isEmpty()) {
            display.flags |= 128;
        }

        MessageObject message = new MessageObject(currentAccount, display, true, false);
        message.isOutOwnerCached = source.isOutOwner();
        versions.add(message);
        dates.add(display.date);
    }

    @Override
    public void onFragmentDestroy() {
        destroyed = true;
        super.onFragmentDestroy();
    }

    private final class HistoryAdapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public int getItemCount() {
            return versions.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            ChatActionCell label = new ChatActionCell(context, false, getResourceProvider());
            row.addView(label, LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 4
            ));

            ChatMessageCell cell = new ChatMessageCell(context, currentAccount, false, null, getResourceProvider());
            cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
                @Override
                public boolean canPerformActions() {
                    return false;
                }
            });
            cell.setFullyDraw(true);
            row.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            return new RecyclerListView.Holder(row);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            LinearLayout row = (LinearLayout) holder.itemView;
            ChatActionCell label = (ChatActionCell) row.getChildAt(0);
            String title = position == versions.size() - 1
                    ? LocaleController.getString(R.string.FluxGramEditHistoryCurrent)
                    : LocaleController.formatString(R.string.FluxGramEditHistoryVersion, position + 1);
            String date = LocaleController.formatDateChat(dates.get(position));
            label.setCustomText(title + " · " + date);

            ChatMessageCell cell = (ChatMessageCell) row.getChildAt(1);
            cell.isChat = source.getDialogId() < 0;
            cell.setMessageObject(versions.get(position), null, false, false, false);
        }
    }
}

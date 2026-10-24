package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.fluxgram.icons.IconPackManifest;
import org.telegram.messenger.fluxgram.icons.IconPackRepository;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.fluxgram.FluxGramSettingsSections;

import java.util.ArrayList;

public final class FluxGramIconPackSettingsActivity extends BaseFragment {
    private static final int IMPORT = 1;
    private static final int PACK = 2;
    private static final int RETRY = 3;
    private static final int FILE_REQUEST = 1001;

    private UniversalRecyclerView listView;
    private IconPackRepository.State state;
    private IconPackRepository.Review review;
    private int importState;
    private int generation;
    private boolean operationRunning;
    private boolean loadFailed;
    private boolean destroyed;

    @Override
    public void onFragmentDestroy() {
        destroyed = true;
        generation++;
        IconPackRepository.discard(review);
        review = null;
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString(R.string.FluxGramInterfaceIcons));
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

        listView = new UniversalRecyclerView(
                this,
                (items, adapter) -> fillItems(items),
                this::onItemClick,
                this::onItemLongClick
        );
        listView.adapter.setApplyBackground(false);
        FluxGramSettingsSections.apply(listView, currentAccount);
        content.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT
        ));
        actionBar.setAdaptiveBackground(listView);

        loadState();

        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items) {
        boolean busy = operationRunning || importState != 0;

        addSectionLabel(items, R.string.FluxGramInterfaceIcons);
        items.add(UItem.asButton(
                IMPORT,
                R.drawable.msg_download,
                LocaleController.getString(R.string.FluxGramImportIconPack),
                importState == 1
                        ? LocaleController.getString(R.string.FluxGramIconPackChecking)
                        : importState == 2
                                ? LocaleController.getString(R.string.FluxGramIconPackInstalling)
                                : null
        ).setEnabled(!busy));
        items.add(UItem.asShadow(LocaleController.getString(R.string.FluxGramImportIconPackInfo)));

        if (loadFailed && state != null) {
            items.add(UItem.asButton(
                    RETRY,
                    R.drawable.msg_reset,
                    LocaleController.getString(R.string.FluxGramReloadIconPacks)
            ).setEnabled(!busy));
            items.add(UItem.asShadow(LocaleController.getString(
                    R.string.FluxGramReloadIconPacksInfo)));
        }

        if (state == null) {
            addSectionLabel(items, R.string.FluxGramInstalledIconPacks);
            if (loadFailed) {
                items.add(UItem.asButton(
                        RETRY,
                        R.drawable.msg_reset,
                        LocaleController.getString(R.string.FluxGramReloadIconPacks)
                ).setEnabled(!busy));
            } else {
                items.add(UItem.asButton(
                        PACK,
                        LocaleController.getString(R.string.Loading),
                        null
                ).setEnabled(false));
            }
            items.add(UItem.asShadow(null));

            return;
        }

        if (state.packs.isEmpty()) {
            addSectionLabel(items, R.string.FluxGramInstalledIconPacks);
            items.add(UItem.asButton(
                    PACK,
                    LocaleController.getString(R.string.FluxGramNoInstalledIconPacks),
                    null
            ).setEnabled(false));
            items.add(UItem.asShadow(LocaleController.getString(
                    R.string.FluxGramIconPackManagementInfo)));

            return;
        }

        boolean hasActive = !state.activeIds.isEmpty();

        if (hasActive) {
            addSectionLabel(items, R.string.FluxGramActiveIconPacks);

            for (IconPackRepository.Pack pack : state.packs) {
                if (state.isActive(pack.id)) {
                    items.add(packItem(pack, true, busy));
                }
            }

            items.add(UItem.asShadow(LocaleController.getString(
                    R.string.FluxGramActiveIconPacksInfo)));
        }

        if (state.activeIds.size() < state.packs.size()) {
            addSectionLabel(items, hasActive
                    ? R.string.FluxGramInactiveIconPacks
                    : R.string.FluxGramInstalledIconPacks);

            for (IconPackRepository.Pack pack : state.packs) {
                if (!state.isActive(pack.id)) {
                    items.add(packItem(pack, false, busy));
                }
            }

            items.add(UItem.asShadow(LocaleController.getString(
                    R.string.FluxGramIconPackManagementInfo)));
        }
    }

    private UItem packItem(IconPackRepository.Pack pack, boolean active, boolean busy) {
        UItem item = UItem.asButtonCheck(
                PACK,
                pack.name,
                LocaleController.formatString(
                        R.string.FluxGramIconPackListDetails,
                        pack.version,
                        pack.author,
                        pack.iconCount)
        ).setChecked(active).setEnabled(!busy);
        item.object = pack;

        return item;
    }

    private void addSectionLabel(ArrayList<UItem> items, int textResource) {
        HeaderCell label = new HeaderCell(getParentActivity(), 33, getResourceProvider());
        label.setText(LocaleController.getString(textResource));
        items.add(UItem.asCustomShadow(label, LayoutHelper.WRAP_CONTENT));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (operationRunning || importState != 0) {
            return;
        }
        if (item.id == IMPORT) {
            selectIconPack();
            return;
        }
        if (item.id == RETRY) {
            loadState();
            return;
        }
        if (!(item.object instanceof IconPackRepository.Pack) || state == null) {
            return;
        }

        IconPackRepository.Pack pack = (IconPackRepository.Pack) item.object;
        boolean active = !state.isActive(pack.id);
        runOperation(callback -> IconPackRepository.setActive(
                pack.id,
                active,
                callback), state.withActive(pack.id, active));
    }

    private boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        if (operationRunning
                || importState != 0
                || state == null
                || !(item.object instanceof IconPackRepository.Pack)) {
            return false;
        }

        IconPackRepository.Pack pack = (IconPackRepository.Pack) item.object;
        int activeIndex = state.activeIds.indexOf(pack.id);
        ItemOptions options = ItemOptions.makeOptions(this, view);

        if (activeIndex > 0) {
            options.add(
                    R.drawable.msg_go_up,
                    LocaleController.getString(R.string.FluxGramIconPackPriorityUp),
                    () -> move(pack.id, -1));
        }
        if (activeIndex >= 0 && activeIndex + 1 < state.activeIds.size()) {
            options.add(
                    R.drawable.msg_go_down,
                    LocaleController.getString(R.string.FluxGramIconPackPriorityDown),
                    () -> move(pack.id, 1));
        }

        options.add(
                R.drawable.msg_delete,
                LocaleController.getString(R.string.Delete),
                true,
                () -> confirmDelete(pack));
        options.show();

        return true;
    }

    private void move(String id, int direction) {
        runOperation(callback -> IconPackRepository.move(id, direction, callback));
    }

    private void confirmDelete(IconPackRepository.Pack pack) {
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(LocaleController.getString(R.string.FluxGramDeleteIconPack))
                .setMessage(LocaleController.formatString(
                        R.string.FluxGramDeleteIconPackConfirm,
                        pack.name))
                .setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) ->
                        runOperation(callback -> IconPackRepository.delete(pack.id, callback)))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create());
    }

    private interface Operation {
        void run(IconPackRepository.Callback<IconPackRepository.State> callback);
    }

    private void runOperation(Operation operation) {
        runOperation(operation, null);
    }

    private void runOperation(
            Operation operation,
            IconPackRepository.State optimisticState) {
        int currentGeneration = generation;
        IconPackRepository.State previousState = state;
        operationRunning = true;

        if (optimisticState != null) {
            state = optimisticState;
        }

        listView.adapter.update(true);
        operation.run((value, error) -> {
            if (destroyed || currentGeneration != generation) {
                return;
            }

            operationRunning = false;

            if (error == null && value != null) {
                loadFailed = false;
                state = value;
                listView.adapter.update(true);
            } else {
                state = previousState;
                loadFailed = true;
                listView.adapter.update(true);
                showError(R.string.FluxGramIconPackStorageFailed);
            }
        });
    }

    private void loadState() {
        int currentGeneration = generation;
        operationRunning = true;
        loadFailed = false;
        listView.adapter.update(true);
        IconPackRepository.load((value, error) -> {
            if (destroyed || currentGeneration != generation) {
                return;
            }

            operationRunning = false;

            if (error == null && value != null) {
                state = value;
                listView.adapter.update(false);
            } else {
                loadFailed = true;
                listView.adapter.update(true);
                showError(R.string.FluxGramIconPackStorageFailed);
            }
        });
    }

    private void selectIconPack() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/zip",
                    "application/octet-stream"
            });
            startActivityForResult(intent, FILE_REQUEST);
        } catch (RuntimeException error) {
            showError(R.string.FluxGramIconPackReadFailed);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != FILE_REQUEST
                || resultCode != Activity.RESULT_OK
                || data == null
                || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        int currentGeneration = ++generation;
        importState = 1;
        listView.adapter.update(true);

        IconPackRepository.review(uri, (value, error) -> {
            if (destroyed || currentGeneration != generation) {
                IconPackRepository.discard(value);

                return;
            }

            importState = 0;
            listView.adapter.update(true);

            if (!isLastFragment() || isPaused()) {
                IconPackRepository.discard(value);

                return;
            }

            if (error != null || value == null) {
                showError(reviewError(error));

                return;
            }

            showReview(value, currentGeneration);
        });
    }

    private void showReview(IconPackRepository.Review value, int currentGeneration) {
        review = value;
        IconPackManifest manifest = value.result.manifest;
        String details = value.isUpdate()
                ? LocaleController.formatString(
                        R.string.FluxGramIconPackUpdateDetails,
                        value.previousVersion,
                        manifest.version,
                        manifest.author,
                        manifest.icons.size())
                : LocaleController.formatString(
                        R.string.FluxGramIconPackDetails,
                        manifest.version,
                        manifest.author,
                        manifest.icons.size());
        AlertDialog dialog = new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(manifest.name)
                .setMessage(details)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .setPositiveButton(LocaleController.getString(value.isUpdate()
                                ? R.string.FluxGramUpdateIconPack
                                : R.string.FluxGramInstallIconPack),
                        (ignored, which) -> install(value, currentGeneration))
                .create();

        if (showDialog(dialog, ignored -> {
            if (review == value) {
                review = null;
                IconPackRepository.discard(value);
            }
        }) == null) {
            review = null;
            IconPackRepository.discard(value);
        }
    }

    private void install(IconPackRepository.Review value, int currentGeneration) {
        if (destroyed || currentGeneration != generation || review != value) {
            IconPackRepository.discard(value);

            return;
        }

        review = null;
        importState = 2;
        listView.adapter.update(true);

        IconPackRepository.install(value, (installed, error) -> {
            if (destroyed || currentGeneration != generation) {
                return;
            }

            importState = 0;

            if (error != null || installed == null) {
                listView.adapter.update(true);
                showError(R.string.FluxGramIconPackInstallFailed);

                return;
            }

            loadState();

            if (isLastFragment() && !isPaused()) {
                showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                        .setTitle(LocaleController.getString(R.string.FluxGramInterfaceIcons))
                        .setMessage(LocaleController.getString(R.string.FluxGramIconPackInstalled))
                        .setPositiveButton(LocaleController.getString(R.string.OK), null)
                        .create());
            }
        });
    }

    private void showError(int message) {
        if (destroyed || getParentActivity() == null || !isLastFragment() || isPaused()) {
            return;
        }

        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(LocaleController.getString(R.string.FluxGramIconPackError))
                .setMessage(LocaleController.getString(message))
                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                .create());
    }

    private int reviewError(Exception error) {
        if (error instanceof IconPackRepository.ImportException) {
            IconPackRepository.Failure failure =
                    ((IconPackRepository.ImportException) error).failure;

            if (failure == IconPackRepository.Failure.READ) {
                return R.string.FluxGramIconPackReadFailed;
            }
            if (failure == IconPackRepository.Failure.STORAGE) {
                return R.string.FluxGramIconPackStorageFailed;
            }
        }

        return R.string.FluxGramIconPackInvalid;
    }
}

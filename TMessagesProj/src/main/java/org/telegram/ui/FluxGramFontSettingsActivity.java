package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.fluxgram.FontSettings;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Components.FluxGramFontPreviewCell;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.fluxgram.FluxGramSettingsSections;

import java.util.ArrayList;

public final class FluxGramFontSettingsActivity extends BaseFragment {
    private static final int MODE = 1;
    private static final int ROLE_START = 100;
    private static final int FONT_REQUEST = 31;

    private UniversalRecyclerView listView;
    private FluxGramFontPreviewCell preview;
    private FontSettings.Role requestedRole;
    private boolean importing;
    private boolean destroyed;

    @Override
    public void onFragmentDestroy() {
        destroyed = true;
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(LocaleController.getString(R.string.FluxGramFonts));
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

        preview = new FluxGramFontPreviewCell(context, getResourceProvider());
        listView = new UniversalRecyclerView(
                this,
                (items, adapter) -> fillItems(items),
                this::onItemClick,
                null
        );
        listView.adapter.setApplyBackground(false);
        FluxGramSettingsSections.apply(listView, currentAccount);
        content.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT
        ));

        actionBar.setAdaptiveBackground(listView);

        Utilities.globalQueue.postRunnable(() -> {
            FontSettings.preparePreview();
            AndroidUtilities.runOnUIThread(() -> {
                if (!destroyed && preview != null && listView != null) {
                    preview.update();
                    listView.adapter.update(false);
                }
            });
        });

        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items) {
        preview.update();
        items.add(UItem.asCustom(preview, 146));
        items.add(UItem.asShadow(null));

        addSectionLabel(items, R.string.FluxGramFontMode);
        items.add(UItem.asButton(
                MODE,
                LocaleController.getString(R.string.FluxGramFontMode),
                modeName(FontSettings.getMode())
        ).setEnabled(!importing));
        items.add(UItem.asShadow(LocaleController.getString(R.string.FluxGramFontModeInfo)));

        if (FontSettings.getMode() != FontSettings.MODE_CUSTOM) {
            return;
        }

        addSectionLabel(items, R.string.FluxGramCustomFonts);

        FontSettings.Role[] roles = FontSettings.Role.values();

        for (int index = 0; index < roles.length; index++) {
            FontSettings.Role role = roles[index];
            String name = FontSettings.getCustomFontName(role);
            CharSequence value = importing && requestedRole == role
                    ? LocaleController.getString(R.string.FluxGramFontImporting)
                    : name == null
                    ? LocaleController.getString(R.string.FluxGramFontNotSelected)
                    : name;
            items.add(UItem.asButton(
                    ROLE_START + role.ordinal(),
                    roleName(role),
                    value
            ).setEnabled(!importing));
        }

        items.add(UItem.asShadow(LocaleController.getString(R.string.FluxGramCustomFontsInfo)));
    }

    private void addSectionLabel(ArrayList<UItem> items, int textResource) {
        HeaderCell label = new HeaderCell(getParentActivity(), 33, getResourceProvider());
        label.setText(LocaleController.getString(textResource));
        items.add(UItem.asCustomShadow(label, LayoutHelper.WRAP_CONTENT));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (importing) {
            return;
        }

        if (item.id == MODE) {
            showModeDialog();
            return;
        }

        FontSettings.Role role = roleForId(item.id);

        if (role == null) {
            return;
        }

        if (FontSettings.hasCustomFont(role)) {
            showRoleActions(view, role);
        } else {
            selectFont(role);
        }
    }

    private void showModeDialog() {
        CharSequence[] options = {
                modeName(FontSettings.MODE_TELEGRAM),
                modeName(FontSettings.MODE_SYSTEM),
                modeName(FontSettings.MODE_CUSTOM)
        };
        int selected = FontSettings.getMode();

        for (int index = 0; index < options.length; index++) {
            if (index == selected) {
                options[index] = "✓  " + options[index];
            }
        }

        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(LocaleController.getString(R.string.FluxGramFontMode))
                .setItems(options, (dialog, index) -> {
                    FontSettings.setMode(index);
                    preview.update();
                    listView.adapter.update(true);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create());
    }

    private void showRoleActions(View anchor, FontSettings.Role role) {
        ItemOptions.makeOptions(this, anchor)
                .add(
                        R.drawable.msg_edit,
                        LocaleController.getString(R.string.Replace),
                        () -> selectFont(role))
                .add(
                        R.drawable.msg_delete,
                        LocaleController.getString(R.string.Remove),
                        true,
                        () -> removeFont(role))
                .show();
    }

    private void selectFont(FontSettings.Role role) {
        requestedRole = role;

        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "font/ttf",
                    "font/otf",
                    "application/x-font-ttf",
                    "application/x-font-opentype",
                    "application/vnd.ms-opentype"
            });
            startActivityForResult(intent, FONT_REQUEST);
        } catch (RuntimeException error) {
            requestedRole = null;
            showImportError(FontSettings.ImportFailure.READ);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != FONT_REQUEST) {
            return;
        }

        if (resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null || requestedRole == null) {
            requestedRole = null;
            return;
        }

        FontSettings.Role role = requestedRole;
        Uri uri = data.getData();
        importing = true;
        listView.adapter.update(true);

        Utilities.globalQueue.postRunnable(() -> {
            FontSettings.ImportFailure failure = null;

            try {
                FontSettings.importFont(role, uri);
            } catch (FontSettings.FontImportException error) {
                failure = error.getFailure();
            }

            FontSettings.ImportFailure result = failure;
            AndroidUtilities.runOnUIThread(() -> finishImport(result));
        });
    }

    private void finishImport(FontSettings.ImportFailure failure) {
        importing = false;
        requestedRole = null;

        if (destroyed || listView == null) {
            return;
        }

        preview.update();
        listView.adapter.update(true);

        if (failure != null) {
            showImportError(failure);
        }
    }

    private void removeFont(FontSettings.Role role) {
        importing = true;
        requestedRole = role;
        listView.adapter.update(true);

        Utilities.globalQueue.postRunnable(() -> {
            FontSettings.ImportFailure failure = null;

            try {
                FontSettings.removeCustomFont(role);
            } catch (FontSettings.FontImportException error) {
                failure = error.getFailure();
            }

            FontSettings.ImportFailure result = failure;
            AndroidUtilities.runOnUIThread(() -> finishImport(result));
        });
    }

    private void showImportError(FontSettings.ImportFailure failure) {
        int message;

        if (failure == FontSettings.ImportFailure.EMPTY) {
            message = R.string.FluxGramFontEmpty;
        } else if (failure == FontSettings.ImportFailure.TOO_LARGE) {
            message = R.string.FluxGramFontTooLarge;
        } else if (failure == FontSettings.ImportFailure.INVALID) {
            message = R.string.FluxGramFontInvalid;
        } else {
            message = R.string.FluxGramFontImportFailed;
        }

        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(LocaleController.getString(R.string.FluxGramFontImportError))
                .setMessage(LocaleController.getString(message))
                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                .create());
    }

    private CharSequence modeName(int mode) {
        if (mode == FontSettings.MODE_SYSTEM) {
            return LocaleController.getString(R.string.FluxGramFontSystem);
        }
        if (mode == FontSettings.MODE_CUSTOM) {
            return LocaleController.getString(R.string.FluxGramFontCustom);
        }

        return LocaleController.getString(R.string.FluxGramFontTelegram);
    }

    private CharSequence roleName(FontSettings.Role role) {
        if (role == FontSettings.Role.MEDIUM) {
            return LocaleController.getString(R.string.FluxGramFontMedium);
        }
        if (role == FontSettings.Role.BOLD) {
            return LocaleController.getString(R.string.FluxGramFontBold);
        }
        if (role == FontSettings.Role.EXTRA_BOLD) {
            return LocaleController.getString(R.string.FluxGramFontExtraBold);
        }
        if (role == FontSettings.Role.ITALIC) {
            return LocaleController.getString(R.string.FluxGramFontItalic);
        }
        if (role == FontSettings.Role.BOLD_ITALIC) {
            return LocaleController.getString(R.string.FluxGramFontBoldItalic);
        }
        if (role == FontSettings.Role.MONOSPACE) {
            return LocaleController.getString(R.string.FluxGramFontMonospace);
        }

        return LocaleController.getString(R.string.FluxGramFontRegular);
    }

    private FontSettings.Role roleForId(int id) {
        int index = id - ROLE_START;
        FontSettings.Role[] roles = FontSettings.Role.values();

        if (index < 0 || index >= roles.length) {
            return null;
        }

        return roles[index];
    }
}

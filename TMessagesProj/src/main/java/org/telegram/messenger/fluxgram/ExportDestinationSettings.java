package org.telegram.messenger.fluxgram;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

public final class ExportDestinationSettings {
    private static final String DOWNLOADS_TREE_URI = "fluxgram_downloads_tree_uri";
    private static final String DOWNLOADS_TREE_FLAGS = "fluxgram_downloads_tree_flags";

    public static boolean hasCustomDownloadsDirectory() {
        Uri uri = getDownloadsTreeUri();

        return uri != null && hasWritePermission(
                ApplicationLoader.applicationContext.getContentResolver(),
                uri
        );
    }

    public static boolean setDownloadsTreeUri(Uri uri, int resultFlags) {
        if (uri == null || !DocumentsContract.isTreeUri(uri)) {
            return false;
        }

        int flags = resultFlags & (
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        );
        if ((flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0) {
            return false;
        }

        ContentResolver resolver = ApplicationLoader.applicationContext.getContentResolver();
        try {
            resolver.takePersistableUriPermission(uri, flags);
        } catch (RuntimeException error) {
            FileLog.e(error);
            return false;
        }

        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        Uri previousUri = parseUri(preferences.getString(DOWNLOADS_TREE_URI, null));
        int previousFlags = preferences.getInt(DOWNLOADS_TREE_FLAGS, 0);
        preferences.edit()
                .putString(DOWNLOADS_TREE_URI, uri.toString())
                .putInt(DOWNLOADS_TREE_FLAGS, flags)
                .apply();

        if (previousUri != null && !previousUri.equals(uri)) {
            releasePermission(resolver, previousUri, previousFlags);
        }

        return true;
    }

    public static void clearDownloadsTreeUri() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        Uri uri = parseUri(preferences.getString(DOWNLOADS_TREE_URI, null));
        int flags = preferences.getInt(DOWNLOADS_TREE_FLAGS, 0);
        preferences.edit()
                .remove(DOWNLOADS_TREE_URI)
                .remove(DOWNLOADS_TREE_FLAGS)
                .apply();

        if (uri != null) {
            releasePermission(ApplicationLoader.applicationContext.getContentResolver(), uri, flags);
        }
    }

    public static Uri saveDownload(File sourceFile, String filename, String mimeType) {
        Uri treeUri = getDownloadsTreeUri();
        if (treeUri == null || sourceFile == null || !sourceFile.isFile()) {
            return null;
        }

        ContentResolver resolver = ApplicationLoader.applicationContext.getContentResolver();
        if (!hasWritePermission(resolver, treeUri)) {
            return null;
        }

        String displayName = FileLoader.fixFileName(
                TextUtils.isEmpty(filename) ? sourceFile.getName() : filename
        );
        if (TextUtils.isEmpty(displayName)) {
            return null;
        }

        String resolvedMimeType = mimeType;
        if (TextUtils.isEmpty(resolvedMimeType)) {
            String extension = FileLoader.getFileExtension(sourceFile);
            resolvedMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        if (TextUtils.isEmpty(resolvedMimeType)) {
            resolvedMimeType = "application/octet-stream";
        }

        Uri destinationUri = null;
        try {
            Uri parentUri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri)
            );
            destinationUri = DocumentsContract.createDocument(
                    resolver,
                    parentUri,
                    resolvedMimeType,
                    displayName
            );
            if (destinationUri == null) {
                return null;
            }

            try (FileInputStream inputStream = new FileInputStream(sourceFile);
                 OutputStream outputStream = resolver.openOutputStream(destinationUri, "w")) {
                if (outputStream == null) {
                    throw new IllegalStateException("Unable to open export destination");
                }
                AndroidUtilities.copyFile(inputStream, outputStream);
            }

            return destinationUri;
        } catch (Exception error) {
            FileLog.e(error);
            if (destinationUri != null) {
                try {
                    DocumentsContract.deleteDocument(resolver, destinationUri);
                } catch (Exception deleteError) {
                    FileLog.e(deleteError);
                }
            }
            return null;
        }
    }

    private static Uri getDownloadsTreeUri() {
        String value = MessagesController.getGlobalMainSettings().getString(DOWNLOADS_TREE_URI, null);

        return parseUri(value);
    }

    private static Uri parseUri(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }

        try {
            return Uri.parse(value);
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static boolean hasWritePermission(ContentResolver resolver, Uri uri) {
        for (UriPermission permission : resolver.getPersistedUriPermissions()) {
            if (uri.equals(permission.getUri()) && permission.isWritePermission()) {
                return true;
            }
        }

        return false;
    }

    private static void releasePermission(ContentResolver resolver, Uri uri, int flags) {
        int persistedFlags = flags & (
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        );
        if (persistedFlags == 0) {
            return;
        }

        try {
            resolver.releasePersistableUriPermission(uri, persistedFlags);
        } catch (RuntimeException error) {
            FileLog.e(error);
        }
    }

    private ExportDestinationSettings() {
    }
}

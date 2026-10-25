package android.content;

import android.content.res.Resources;

import java.io.File;

public class Context {
    private final File noBackupFilesDir;
    private final File cacheDir;
    private final ContentResolver contentResolver = new ContentResolver();
    private final Resources resources = new Resources();

    public Context(File noBackupFilesDir, File cacheDir) {
        this.noBackupFilesDir = noBackupFilesDir;
        this.cacheDir = cacheDir;
    }

    public File getNoBackupFilesDir() {
        return noBackupFilesDir;
    }

    public File getCacheDir() {
        return cacheDir;
    }

    public ContentResolver getContentResolver() {
        return contentResolver;
    }

    public Resources getResources() {
        return resources;
    }
}

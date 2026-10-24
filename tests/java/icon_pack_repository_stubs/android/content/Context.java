package android.content;

import java.io.File;

public class Context {
    private final File noBackupFilesDir;
    private final File cacheDir;
    private final ContentResolver contentResolver = new ContentResolver();

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
}

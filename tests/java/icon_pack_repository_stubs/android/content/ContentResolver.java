package android.content;

import android.net.Uri;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class ContentResolver {
    public InputStream openInputStream(Uri uri) throws FileNotFoundException {
        return new FileInputStream(uri.file);
    }
}

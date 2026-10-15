package org.telegram.messenger.fluxgram;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.UUID;

public final class FontSettings {
    public static final int MODE_TELEGRAM = 0;
    public static final int MODE_SYSTEM = 1;
    public static final int MODE_CUSTOM = 2;

    public static final int MAX_FONT_BYTES = 16 * 1024 * 1024;

    private static final String MODE = "fluxgram_font_mode";
    private static final String NAME_PREFIX = "fluxgram_font_name_";
    private static final Object lock = new Object();
    private static final EnumMap<Role, Typeface> customTypefaces = new EnumMap<>(Role.class);

    public enum Role {
        REGULAR("regular"),
        MEDIUM("medium"),
        BOLD("bold"),
        EXTRA_BOLD("extra_bold"),
        ITALIC("italic"),
        BOLD_ITALIC("bold_italic"),
        MONOSPACE("monospace");

        private final String key;

        Role(String key) {
            this.key = key;
        }
    }

    public enum ImportFailure {
        EMPTY,
        TOO_LARGE,
        INVALID,
        READ,
        STORE
    }

    public static final class FontImportException extends Exception {
        private final ImportFailure failure;

        private FontImportException(ImportFailure failure, Throwable cause) {
            super(failure.name(), cause);
            this.failure = failure;
        }

        public ImportFailure getFailure() {
            return failure;
        }
    }

    private FontSettings() {
    }

    public static int getMode() {
        int mode = preferences().getInt(MODE, MODE_TELEGRAM);

        if (mode < MODE_TELEGRAM || mode > MODE_CUSTOM) {
            return MODE_TELEGRAM;
        }

        return mode;
    }

    public static void setMode(int mode) {
        if (mode < MODE_TELEGRAM || mode > MODE_CUSTOM) {
            mode = MODE_TELEGRAM;
        }

        preferences().edit().putInt(MODE, mode).apply();
    }

    public static boolean hasCustomFont(Role role) {
        return role != null && fontFile(role).isFile();
    }

    public static String getCustomFontName(Role role) {
        if (!hasCustomFont(role)) {
            return null;
        }

        return preferences().getString(NAME_PREFIX + role.key, null);
    }

    public static String importFont(Role role, Uri uri) throws FontImportException {
        if (role == null || uri == null) {
            throw new FontImportException(ImportFailure.READ, null);
        }

        synchronized (lock) {
            File directory = fontDirectory();

            if ((!directory.isDirectory() && !directory.mkdirs()) || !directory.isDirectory()) {
                throw new FontImportException(ImportFailure.STORE, null);
            }

            File temporary = new File(directory, ".tmp-" + UUID.randomUUID());

            try {
                copyBounded(uri, temporary);
                validateFont(temporary);
                Os.rename(temporary.getAbsolutePath(), fontFile(role).getAbsolutePath());
                syncDirectory(directory);

                String name = readDisplayName(uri);
                preferences().edit().putString(NAME_PREFIX + role.key, name).commit();
                customTypefaces.remove(role);

                return name;
            } catch (FontImportException error) {
                throw error;
            } catch (ErrnoException error) {
                throw new FontImportException(ImportFailure.STORE, error);
            } finally {
                if (temporary.exists()) {
                    temporary.delete();
                }
            }
        }
    }

    public static void removeCustomFont(Role role) throws FontImportException {
        if (role == null) {
            return;
        }

        synchronized (lock) {
            File file = fontFile(role);

            if (file.exists() && !file.delete()) {
                throw new FontImportException(ImportFailure.STORE, null);
            }

            preferences().edit().remove(NAME_PREFIX + role.key).commit();
            customTypefaces.remove(role);
        }
    }

    public static Typeface getPreviewTypeface(Role role) {
        if (role == null) {
            return Typeface.DEFAULT;
        }

        int mode = getMode();

        if (mode == MODE_SYSTEM) {
            return systemTypeface(role);
        }
        if (mode == MODE_CUSTOM) {
            return customTypeface(role);
        }

        return telegramTypeface(role);
    }

    private static Typeface customTypeface(Role role) {
        Typeface direct = loadCustomTypeface(role);

        if (direct != null) {
            return direct;
        }

        if (role == Role.MONOSPACE) {
            return telegramTypeface(Role.MONOSPACE);
        }

        Typeface regular = loadCustomTypeface(Role.REGULAR);

        if (regular == null) {
            return telegramTypeface(role);
        }

        if (role == Role.REGULAR) {
            return regular;
        }
        if (role == Role.MEDIUM) {
            Typeface bold = loadCustomTypeface(Role.BOLD);

            if (bold != null) {
                return bold;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return Typeface.create(regular, 500, false);
            }

            return Typeface.create(regular, Typeface.BOLD);
        }
        if (role == Role.BOLD) {
            Typeface medium = loadCustomTypeface(Role.MEDIUM);
            return medium != null ? medium : Typeface.create(regular, Typeface.BOLD);
        }
        if (role == Role.EXTRA_BOLD) {
            Typeface bold = loadCustomTypeface(Role.BOLD);

            if (bold != null) {
                return bold;
            }

            Typeface medium = loadCustomTypeface(Role.MEDIUM);

            if (medium != null) {
                return medium;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return Typeface.create(regular, 800, false);
            }

            return Typeface.create(regular, Typeface.BOLD);
        }
        if (role == Role.ITALIC) {
            return Typeface.create(regular, Typeface.ITALIC);
        }

        Typeface italic = loadCustomTypeface(Role.ITALIC);
        return Typeface.create(italic == null ? regular : italic, Typeface.BOLD_ITALIC);
    }

    private static Typeface loadCustomTypeface(Role role) {
        synchronized (lock) {
            if (customTypefaces.containsKey(role)) {
                return customTypefaces.get(role);
            }

            File file = fontFile(role);

            if (!file.isFile()) {
                return null;
            }

            try {
                Typeface typeface = createTypeface(file);
                customTypefaces.put(role, typeface);
                return typeface;
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    private static Typeface telegramTypeface(Role role) {
        Typeface typeface;

        if (role == Role.REGULAR) {
            return Typeface.DEFAULT;
        }
        if (role == Role.MEDIUM || role == Role.BOLD) {
            typeface = AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM);
        } else if (role == Role.EXTRA_BOLD) {
            typeface = AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_EXTRA_BOLD);
        } else if (role == Role.ITALIC) {
            typeface = AndroidUtilities.getTypeface("fonts/ritalic.ttf");
        } else if (role == Role.BOLD_ITALIC) {
            typeface = AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM_ITALIC);
        } else {
            typeface = AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO);
        }

        return typeface == null ? Typeface.DEFAULT : typeface;
    }

    private static Typeface systemTypeface(Role role) {
        if (role == Role.REGULAR) {
            return Typeface.create("sans-serif", Typeface.NORMAL);
        }
        if (role == Role.MEDIUM) {
            return Typeface.create("sans-serif-medium", Typeface.NORMAL);
        }
        if (role == Role.BOLD || role == Role.EXTRA_BOLD) {
            return Typeface.create("sans-serif", Typeface.BOLD);
        }
        if (role == Role.ITALIC) {
            return Typeface.create("sans-serif", Typeface.ITALIC);
        }
        if (role == Role.BOLD_ITALIC) {
            return Typeface.create("sans-serif", Typeface.BOLD_ITALIC);
        }

        return Typeface.create("monospace", Typeface.NORMAL);
    }

    private static void copyBounded(Uri uri, File destination) throws FontImportException {
        ContentResolver resolver = ApplicationLoader.applicationContext.getContentResolver();

        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) {
                throw new FontImportException(ImportFailure.READ, null);
            }

            try (FileOutputStream output = new FileOutputStream(destination)) {
                byte[] buffer = new byte[32768];
                int total = 0;
                int count;

                while ((count = input.read(buffer)) != -1) {
                    total += count;

                    if (total > MAX_FONT_BYTES) {
                        throw new FontImportException(ImportFailure.TOO_LARGE, null);
                    }

                    output.write(buffer, 0, count);
                }

                if (total == 0) {
                    throw new FontImportException(ImportFailure.EMPTY, null);
                }

                output.getFD().sync();
            }
        } catch (FontImportException error) {
            throw error;
        } catch (IOException | SecurityException error) {
            throw new FontImportException(ImportFailure.READ, error);
        }
    }

    private static void validateFont(File file) throws FontImportException {
        try {
            createTypeface(file);
        } catch (RuntimeException error) {
            throw new FontImportException(ImportFailure.INVALID, error);
        }
    }

    private static Typeface createTypeface(File file) {
        Typeface typeface;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            typeface = new Typeface.Builder(file).build();
        } else {
            typeface = Typeface.createFromFile(file);
        }

        if (typeface == null) {
            throw new IllegalArgumentException("Invalid font");
        }

        return typeface;
    }

    private static String readDisplayName(Uri uri) {
        String name = null;

        try (Cursor cursor = ApplicationLoader.applicationContext.getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);

                if (index >= 0) {
                    name = cursor.getString(index);
                }
            }
        } catch (RuntimeException ignored) {
        }

        if (name == null || name.trim().isEmpty()) {
            name = uri.getLastPathSegment();
        }
        if (name == null || name.trim().isEmpty()) {
            name = "font";
        }

        name = name.replace('\n', ' ').replace('\r', ' ').trim();

        if (name.length() > 128) {
            name = name.substring(0, 128);
        }

        return name;
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences(
                "mainconfig", Activity.MODE_PRIVATE);
    }

    private static File fontDirectory() {
        return new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "fluxgram/fonts");
    }

    private static File fontFile(Role role) {
        return new File(fontDirectory(), role.key + ".font");
    }

    private static void syncDirectory(File directory) {
        java.io.FileDescriptor descriptor = null;

        try {
            descriptor = Os.open(directory.getAbsolutePath(), OsConstants.O_RDONLY, 0);
            Os.fsync(descriptor);
        } catch (ErrnoException ignored) {
        } finally {
            if (descriptor != null) {
                try {
                    Os.close(descriptor);
                } catch (ErrnoException ignored) {
                }
            }
        }
    }
}

package org.telegram.messenger.fluxgram;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.BooleanSupplier;

public final class AttachmentCopy {
    public static boolean isComplete(File file, long expected, long limit) {
        return expected > 0 && expected <= limit && file.isFile() && file.length() == expected;
    }

    public static void write(InputStream input, File destination, long expected, long limit,
                             BooleanSupplier cancelled) throws IOException {
        if (expected <= 0 || expected > limit || destination.exists()) {
            throw new IOException("Invalid attachment size or destination");
        }

        File temporary = new File(destination.getPath() + ".partial");
        boolean published = false;

        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                long copied = 0;
                int read;

                while ((read = input.read(buffer)) != -1) {
                    if (cancelled.getAsBoolean() || read > expected - copied) {
                        throw new IOException("Attachment copy cancelled or source changed");
                    }

                    output.write(buffer, 0, read);
                    copied += read;
                }

                if (copied != expected || cancelled.getAsBoolean()) {
                    throw new IOException("Incomplete attachment");
                }

                output.getFD().sync();
            }

            if (!temporary.renameTo(destination)) {
                throw new IOException("Cannot publish attachment");
            }

            published = true;
        } finally {
            if (!published) {
                temporary.delete();
            }
        }
    }
}

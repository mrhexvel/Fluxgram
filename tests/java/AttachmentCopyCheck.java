import org.telegram.messenger.fluxgram.AttachmentCopy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public final class AttachmentCopyCheck {
    private static void require(boolean value) {
        if (!value) {
            throw new AssertionError();
        }
    }

    private static void failed(InputStream input, File target, long expected, long limit) throws Exception {
        try {
            AttachmentCopy.write(input, target, expected, limit, () -> false);
            throw new AssertionError("Expected copy failure");
        } catch (IOException expectedError) {
            require(!target.exists());
            require(!new File(target.getPath() + ".partial").exists());
        }
    }

    public static void main(String[] args) throws Exception {
        File directory = new File(args[0]);
        File target = new File(directory, "saved.bin");
        byte[] bytes = new byte[512 * 1024 + 13];
        Arrays.fill(bytes, (byte) 37);
        AttachmentCopy.write(new ByteArrayInputStream(bytes), target, bytes.length, bytes.length, () -> false);
        require(Arrays.equals(bytes, Files.readAllBytes(target.toPath())));

        try {
            AttachmentCopy.write(new ByteArrayInputStream(new byte[] {9}), target, 1, 1, () -> false);
            throw new AssertionError("Existing file was overwritten");
        } catch (IOException expectedError) {
            require(Arrays.equals(bytes, Files.readAllBytes(target.toPath())));
        }

        require(target.delete());
        failed(new ByteArrayInputStream(new byte[3]), target, 4, 10);
        failed(new ByteArrayInputStream(new byte[5]), target, 4, 10);
        failed(new ByteArrayInputStream(new byte[5]), target, 5, 4);

        AtomicInteger checks = new AtomicInteger();
        try {
            AttachmentCopy.write(new ByteArrayInputStream(bytes), target, bytes.length, bytes.length,
                    () -> checks.incrementAndGet() > 2);
            throw new AssertionError("Cancellation ignored");
        } catch (IOException expectedError) {
            require(!target.exists());
            require(!new File(target.getPath() + ".partial").exists());
        }

        long size = 32L * 1024 * 1024;
        InputStream generated = new InputStream() {
            long remaining = size;

            @Override
            public int read() {
                throw new AssertionError("Expected buffered copying");
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                require(buffer.length <= 64 * 1024);

                if (remaining == 0) {
                    return -1;
                }

                int copied = (int) Math.min(length, remaining);
                Arrays.fill(buffer, offset, offset + copied, (byte) 1);
                remaining -= copied;
                return copied;
            }
        };
        AttachmentCopy.write(generated, target, size, size, () -> false);
        require(target.length() == size);
        require(target.delete());

        File source = new File(directory, "cache.bin");
        Files.write(source.toPath(), bytes);

        try (FileInputStream pinned = new FileInputStream(source)) {
            require(source.delete());
            AttachmentCopy.write(pinned, target, bytes.length, bytes.length, () -> false);
        }

        require(Arrays.equals(bytes, Files.readAllBytes(target.toPath())));
    }
}

package org.telegram.messenger.camera;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class CameraCapabilityCache {

    private static final int MAGIC = 0x46474343;
    private static final int VERSION = 2;
    private static final int MAX_DEVICES = 32;
    private static final int MAX_SIZES = 128;
    private static final int MAX_FRAME_RATE_RANGES = 32;
    private static final int MAX_FOCAL_LENGTHS = 16;
    private static final int MAX_LENSES = 16;
    private static final int MAX_PAYLOAD_BYTES = 512 * 1024;

    private CameraCapabilityCache() {
    }

    static byte[] encode(CameraCapabilitySnapshot snapshot, String fingerprint) throws IOException {
        if (fingerprint != null && fingerprint.length() > 1024) {
            throw new IOException("Camera capability fingerprint exceeds limit");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(MAGIC);
        output.writeInt(VERSION);
        output.writeUTF(fingerprint == null ? "" : fingerprint);
        output.writeBoolean(snapshot.isInventoryComplete(CameraCapabilitySnapshot.Backend.CAMERA1));
        output.writeBoolean(snapshot.isInventoryComplete(CameraCapabilitySnapshot.Backend.CAMERA2));
        requireCount(snapshot.getDevices().size(), MAX_DEVICES);
        output.writeInt(snapshot.getDevices().size());

        for (CameraCapabilitySnapshot.Device device : snapshot.getDevices()) {
            output.writeInt(device.getBackend().ordinal());
            output.writeUTF(device.getId());
            output.writeInt(device.getFacing().ordinal());
            output.writeInt(device.getSensorOrientation());
            output.writeBoolean(device.areDetailsComplete());
            writeSizes(output, device.getPreviewSizes());
            writeSizes(output, device.getPhotoSizes());
            writeFrameRateRanges(output, device.getFrameRateRanges());
            output.writeInt(device.getVideoStabilization().ordinal());
            output.writeInt(device.getOpticalStabilization().ordinal());
            writeLenses(output, device.getLenses());
            output.writeBoolean(device.hasZoomRange());
            output.writeFloat(device.getMinimumZoomRatio());
            output.writeFloat(device.getMaximumZoomRatio());
        }

        output.flush();
        byte[] result = bytes.toByteArray();
        if (result.length > MAX_PAYLOAD_BYTES) {
            throw new IOException("Camera capability cache exceeds limit");
        }
        return result;
    }

    static CameraCapabilitySnapshot decode(byte[] bytes, String expectedFingerprint) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_PAYLOAD_BYTES) {
            return null;
        }

        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                return null;
            }
            if (!input.readUTF().equals(expectedFingerprint == null ? "" : expectedFingerprint)) {
                return null;
            }

            boolean camera1Complete = input.readBoolean();
            boolean camera2Complete = input.readBoolean();
            int deviceCount = readCount(input, MAX_DEVICES);
            ArrayList<CameraCapabilitySnapshot.Device> devices = new ArrayList<>(deviceCount);

            for (int index = 0; index < deviceCount; index++) {
                CameraCapabilitySnapshot.Backend backend = readEnum(
                        input,
                        CameraCapabilitySnapshot.Backend.values()
                );
                String id = input.readUTF();
                CameraCapabilitySnapshot.Facing facing = readEnum(
                        input,
                        CameraCapabilitySnapshot.Facing.values()
                );
                int sensorOrientation = input.readInt();
                boolean detailsComplete = input.readBoolean();
                List<Size> previewSizes = readSizes(input);
                List<Size> photoSizes = readSizes(input);
                List<CameraCapabilitySnapshot.FrameRateRange> frameRateRanges = readFrameRateRanges(input);
                CameraCapabilitySnapshot.Support videoStabilization = readEnum(
                        input,
                        CameraCapabilitySnapshot.Support.values()
                );
                CameraCapabilitySnapshot.Support opticalStabilization = readEnum(
                        input,
                        CameraCapabilitySnapshot.Support.values()
                );
                List<CameraCapabilitySnapshot.Lens> lenses = readLenses(input);
                boolean hasZoomRange = input.readBoolean();
                float minimumZoomRatio = input.readFloat();
                float maximumZoomRatio = input.readFloat();

                devices.add(new CameraCapabilitySnapshot.Device(
                        backend,
                        id,
                        facing,
                        sensorOrientation,
                        detailsComplete,
                        previewSizes,
                        photoSizes,
                        frameRateRanges,
                        videoStabilization,
                        opticalStabilization,
                        lenses,
                        hasZoomRange,
                        minimumZoomRatio,
                        maximumZoomRatio
                ));
            }

            if (input.available() != 0) {
                return null;
            }
            return new CameraCapabilitySnapshot(devices, camera1Complete, camera2Complete);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static void writeSizes(DataOutputStream output, List<Size> sizes) throws IOException {
        requireCount(sizes.size(), MAX_SIZES);
        output.writeInt(sizes.size());
        for (Size size : sizes) {
            output.writeInt(size.getWidth());
            output.writeInt(size.getHeight());
        }
    }

    private static List<Size> readSizes(DataInputStream input) throws IOException {
        int count = readCount(input, MAX_SIZES);
        ArrayList<Size> sizes = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            sizes.add(new Size(input.readInt(), input.readInt()));
        }
        return sizes;
    }

    private static void writeFrameRateRanges(
            DataOutputStream output,
            List<CameraCapabilitySnapshot.FrameRateRange> ranges
    ) throws IOException {
        requireCount(ranges.size(), MAX_FRAME_RATE_RANGES);
        output.writeInt(ranges.size());
        for (CameraCapabilitySnapshot.FrameRateRange range : ranges) {
            output.writeInt(range.getMinimumMilliFps());
            output.writeInt(range.getMaximumMilliFps());
        }
    }

    private static List<CameraCapabilitySnapshot.FrameRateRange> readFrameRateRanges(
            DataInputStream input
    ) throws IOException {
        int count = readCount(input, MAX_FRAME_RATE_RANGES);
        ArrayList<CameraCapabilitySnapshot.FrameRateRange> ranges = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            ranges.add(new CameraCapabilitySnapshot.FrameRateRange(input.readInt(), input.readInt()));
        }
        return ranges;
    }

    private static void writeLenses(
            DataOutputStream output,
            List<CameraCapabilitySnapshot.Lens> lenses
    ) throws IOException {
        requireCount(lenses.size(), MAX_LENSES);
        output.writeInt(lenses.size());
        for (CameraCapabilitySnapshot.Lens lens : lenses) {
            output.writeUTF(lens.getId());
            output.writeBoolean(lens.isPhysicalCamera());
            writeFocalLengths(output, lens.getFocalLengths());
        }
    }

    private static List<CameraCapabilitySnapshot.Lens> readLenses(DataInputStream input)
            throws IOException {
        int count = readCount(input, MAX_LENSES);
        ArrayList<CameraCapabilitySnapshot.Lens> lenses = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            lenses.add(new CameraCapabilitySnapshot.Lens(
                    input.readUTF(),
                    input.readBoolean(),
                    readFocalLengths(input)
            ));
        }
        return lenses;
    }

    private static void writeFocalLengths(DataOutputStream output, List<Float> focalLengths)
            throws IOException {
        requireCount(focalLengths.size(), MAX_FOCAL_LENGTHS);
        output.writeInt(focalLengths.size());
        for (Float focalLength : focalLengths) {
            output.writeFloat(focalLength);
        }
    }

    private static List<Float> readFocalLengths(DataInputStream input) throws IOException {
        int count = readCount(input, MAX_FOCAL_LENGTHS);
        ArrayList<Float> focalLengths = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            float focalLength = input.readFloat();
            if (!Float.isFinite(focalLength) || focalLength <= 0) {
                throw new IOException("Invalid focal length");
            }
            focalLengths.add(focalLength);
        }
        return focalLengths;
    }

    private static int readCount(DataInputStream input, int maximum) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > maximum) {
            throw new IOException("Invalid capability count");
        }
        return count;
    }

    private static void requireCount(int count, int maximum) throws IOException {
        if (count < 0 || count > maximum) {
            throw new IOException("Capability count exceeds limit");
        }
    }

    private static <T> T readEnum(DataInputStream input, T[] values) throws IOException {
        int ordinal = input.readInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IOException("Invalid capability enum");
        }
        return values[ordinal];
    }
}

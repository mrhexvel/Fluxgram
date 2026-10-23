package org.telegram.messenger.camera;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CameraCapabilitySnapshot {

    static final int MAX_IDENTIFIER_LENGTH = 128;

    public enum Backend {
        CAMERA1,
        CAMERA2
    }

    public enum Facing {
        BACK,
        FRONT,
        EXTERNAL,
        UNKNOWN
    }

    public enum Support {
        UNKNOWN,
        UNSUPPORTED,
        SUPPORTED
    }

    public static final class FrameRateRange {
        private final int minimumMilliFps;
        private final int maximumMilliFps;

        public FrameRateRange(int minimumMilliFps, int maximumMilliFps) {
            if (minimumMilliFps < 0 || maximumMilliFps < minimumMilliFps) {
                throw new IllegalArgumentException("Invalid frame rate range");
            }

            this.minimumMilliFps = minimumMilliFps;
            this.maximumMilliFps = maximumMilliFps;
        }

        public int getMinimumMilliFps() {
            return minimumMilliFps;
        }

        public int getMaximumMilliFps() {
            return maximumMilliFps;
        }

        @Override
        public String toString() {
            return minimumMilliFps + "-" + maximumMilliFps;
        }
    }

    public static final class Lens {
        private final String id;
        private final boolean physicalCamera;
        private final List<Float> focalLengths;

        Lens(String id, boolean physicalCamera, List<Float> focalLengths) {
            if (!isValidIdentifier(id)) {
                throw new IllegalArgumentException("Missing lens identity");
            }

            ArrayList<Float> copy = new ArrayList<>();
            if (focalLengths != null) {
                for (Float focalLength : focalLengths) {
                    if (focalLength == null || !Float.isFinite(focalLength) || focalLength <= 0) {
                        throw new IllegalArgumentException("Invalid focal length");
                    }
                    copy.add(focalLength);
                }
            }
            this.id = id;
            this.physicalCamera = physicalCamera;
            this.focalLengths = copy.isEmpty()
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(copy);
        }

        public String getId() {
            return id;
        }

        public List<Float> getFocalLengths() {
            return focalLengths;
        }

        public boolean isPhysicalCamera() {
            return physicalCamera;
        }

        @Override
        public String toString() {
            return id + " physical=" + physicalCamera + " focalLengths=" + focalLengths;
        }
    }

    public static final class Device {
        private final Backend backend;
        private final String id;
        private final Facing facing;
        private final int sensorOrientation;
        private final boolean detailsComplete;
        private final List<Size> previewSizes;
        private final List<Size> photoSizes;
        private final List<FrameRateRange> frameRateRanges;
        private final Support videoStabilization;
        private final Support opticalStabilization;
        private final List<Lens> lenses;
        private final boolean hasZoomRange;
        private final float minimumZoomRatio;
        private final float maximumZoomRatio;

        Device(
                Backend backend,
                String id,
                Facing facing,
                int sensorOrientation,
                boolean detailsComplete,
                List<Size> previewSizes,
                List<Size> photoSizes,
                List<FrameRateRange> frameRateRanges,
                Support videoStabilization,
                Support opticalStabilization,
                List<Lens> lenses,
                boolean hasZoomRange,
                float minimumZoomRatio,
                float maximumZoomRatio
        ) {
            if (backend == null || !isValidIdentifier(id) || facing == null
                    || videoStabilization == null || opticalStabilization == null) {
                throw new IllegalArgumentException("Missing camera capability identity");
            }
            if (sensorOrientation < -1 || sensorOrientation >= 360) {
                throw new IllegalArgumentException("Invalid sensor orientation");
            }
            if (hasZoomRange && (!Float.isFinite(minimumZoomRatio)
                    || !Float.isFinite(maximumZoomRatio)
                    || minimumZoomRatio <= 0
                    || maximumZoomRatio < minimumZoomRatio)) {
                throw new IllegalArgumentException("Invalid zoom range");
            }

            this.backend = backend;
            this.id = id;
            this.facing = facing;
            this.sensorOrientation = sensorOrientation;
            this.detailsComplete = detailsComplete;
            this.previewSizes = copySizes(previewSizes);
            this.photoSizes = copySizes(photoSizes);
            this.frameRateRanges = checkedImmutableCopy(frameRateRanges);
            this.videoStabilization = videoStabilization;
            this.opticalStabilization = opticalStabilization;
            this.lenses = copyLenses(lenses);
            this.hasZoomRange = hasZoomRange;
            this.minimumZoomRatio = hasZoomRange ? minimumZoomRatio : 1f;
            this.maximumZoomRatio = hasZoomRange ? maximumZoomRatio : 1f;
        }

        public Backend getBackend() {
            return backend;
        }

        public String getId() {
            return id;
        }

        public Facing getFacing() {
            return facing;
        }

        public int getSensorOrientation() {
            return sensorOrientation;
        }

        public boolean areDetailsComplete() {
            return detailsComplete;
        }

        public List<Size> getPreviewSizes() {
            return previewSizes;
        }

        public List<Size> getPhotoSizes() {
            return photoSizes;
        }

        public List<FrameRateRange> getFrameRateRanges() {
            return frameRateRanges;
        }

        public Support getVideoStabilization() {
            return videoStabilization;
        }

        public Support getOpticalStabilization() {
            return opticalStabilization;
        }

        public List<Lens> getLenses() {
            return lenses;
        }

        public boolean hasZoomRange() {
            return hasZoomRange;
        }

        public float getMinimumZoomRatio() {
            return minimumZoomRatio;
        }

        public float getMaximumZoomRatio() {
            return maximumZoomRatio;
        }

        @Override
        public String toString() {
            return backend + ":" + id
                    + " facing=" + facing
                    + " complete=" + detailsComplete
                    + " fps=" + frameRateRanges
                    + " videoStabilization=" + videoStabilization
                    + " opticalStabilization=" + opticalStabilization
                    + " lenses=" + lenses
                    + " zoom=" + (hasZoomRange
                    ? minimumZoomRatio + "-" + maximumZoomRatio
                    : "unknown");
        }

        private static List<Size> copySizes(List<Size> sizes) {
            if (sizes == null || sizes.isEmpty()) {
                return Collections.emptyList();
            }

            ArrayList<Size> copy = new ArrayList<>(sizes.size());
            for (Size size : sizes) {
                if (size == null || size.getWidth() <= 0 || size.getHeight() <= 0) {
                    throw new IllegalArgumentException("Invalid camera size");
                }
                copy.add(new Size(size.getWidth(), size.getHeight()));
            }
            return Collections.unmodifiableList(copy);
        }

        private static List<Lens> copyLenses(List<Lens> lenses) {
            if (lenses == null || lenses.isEmpty()) {
                return Collections.emptyList();
            }

            ArrayList<Lens> copy = new ArrayList<>(lenses.size());
            Set<String> ids = new HashSet<>();
            for (Lens lens : lenses) {
                if (lens == null || !ids.add(lens.id)) {
                    throw new IllegalArgumentException("Duplicate lens identity");
                }
                copy.add(lens);
            }
            return Collections.unmodifiableList(copy);
        }

        private static <T> List<T> checkedImmutableCopy(List<T> values) {
            if (values == null || values.isEmpty()) {
                return Collections.emptyList();
            }
            for (T value : values) {
                if (value == null) {
                    throw new IllegalArgumentException("Null camera capability");
                }
            }
            return Collections.unmodifiableList(new ArrayList<>(values));
        }
    }

    private static final CameraCapabilitySnapshot EMPTY = new CameraCapabilitySnapshot(
            Collections.emptyList(),
            false,
            false
    );

    private final List<Device> devices;
    private final boolean camera1InventoryComplete;
    private final boolean camera2InventoryComplete;

    CameraCapabilitySnapshot(
            List<Device> devices,
            boolean camera1InventoryComplete,
            boolean camera2InventoryComplete
    ) {
        if (devices == null || devices.isEmpty()) {
            this.devices = Collections.emptyList();
        } else {
            ArrayList<Device> copy = new ArrayList<>(devices.size());
            Set<String> identities = new HashSet<>();
            for (Device device : devices) {
                if (device == null
                        || !identities.add(device.backend.name() + '\u0000' + device.id)) {
                    throw new IllegalArgumentException("Duplicate camera identity");
                }
                copy.add(device);
            }
            this.devices = Collections.unmodifiableList(copy);
        }
        this.camera1InventoryComplete = camera1InventoryComplete;
        this.camera2InventoryComplete = camera2InventoryComplete;
    }

    public static CameraCapabilitySnapshot empty() {
        return EMPTY;
    }

    public List<Device> getDevices() {
        return devices;
    }

    public List<Device> getDevices(Backend backend) {
        if (backend == null || devices.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<Device> result = new ArrayList<>();
        for (Device device : devices) {
            if (device.backend == backend) {
                result.add(device);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public Device findDevice(Backend backend, String id) {
        if (backend == null || id == null) {
            return null;
        }

        for (Device device : devices) {
            if (device.backend == backend && id.equals(device.id)) {
                return device;
            }
        }
        return null;
    }

    public boolean isInventoryComplete(Backend backend) {
        if (backend == Backend.CAMERA1) {
            return camera1InventoryComplete;
        }
        if (backend == Backend.CAMERA2) {
            return camera2InventoryComplete;
        }
        return false;
    }

    public boolean areDetailsComplete(Backend backend) {
        if (!isInventoryComplete(backend)) {
            return false;
        }

        boolean found = false;
        for (Device device : devices) {
            if (device.backend == backend) {
                found = true;
                if (!device.detailsComplete) {
                    return false;
                }
            }
        }
        return found;
    }

    CameraCapabilitySnapshot replaceInventory(
            Backend backend,
            List<Device> replacement,
            boolean complete
    ) {
        ArrayList<Device> updated = new ArrayList<>();
        for (Device device : devices) {
            if (device.backend != backend) {
                updated.add(device);
            }
        }
        if (replacement != null) {
            updated.addAll(replacement);
        }

        return new CameraCapabilitySnapshot(
                updated,
                backend == Backend.CAMERA1 ? complete : camera1InventoryComplete,
                backend == Backend.CAMERA2 ? complete : camera2InventoryComplete
        );
    }

    CameraCapabilitySnapshot replaceDevice(Device replacement) {
        ArrayList<Device> updated = new ArrayList<>(devices.size() + 1);
        boolean replaced = false;
        for (Device device : devices) {
            if (device.backend == replacement.backend && device.id.equals(replacement.id)) {
                updated.add(replacement);
                replaced = true;
            } else {
                updated.add(device);
            }
        }
        if (!replaced) {
            updated.add(replacement);
        }

        return new CameraCapabilitySnapshot(
                updated,
                camera1InventoryComplete,
                camera2InventoryComplete
        );
    }

    @Override
    public String toString() {
        return "camera1Complete=" + camera1InventoryComplete
                + " camera2Complete=" + camera2InventoryComplete
                + " devices=" + devices;
    }

    private static boolean isValidIdentifier(String value) {
        return value != null && !value.isEmpty() && value.length() <= MAX_IDENTIFIER_LENGTH;
    }
}

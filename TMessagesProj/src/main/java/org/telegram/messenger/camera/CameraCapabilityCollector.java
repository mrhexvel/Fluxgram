package org.telegram.messenger.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.util.Base64;
import android.util.Range;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CameraCapabilityCollector {

    private static final String CACHE_KEY = "cameraCapabilities_v2";
    private static final int MAX_ENCODED_CACHE_LENGTH = 700 * 1024;

    private CameraCapabilityCollector() {
    }

    static CameraCapabilitySnapshot load(SharedPreferences preferences) {
        try {
            String encoded = preferences.getString(CACHE_KEY, null);
            if (encoded == null || encoded.length() > MAX_ENCODED_CACHE_LENGTH) {
                return CameraCapabilitySnapshot.empty();
            }
            CameraCapabilitySnapshot snapshot = CameraCapabilityCache.decode(
                    Base64.decode(encoded, Base64.DEFAULT),
                    Build.FINGERPRINT
            );
            return snapshot == null ? CameraCapabilitySnapshot.empty() : snapshot;
        } catch (RuntimeException exception) {
            FileLog.e(exception);
            return CameraCapabilitySnapshot.empty();
        }
    }

    static void save(SharedPreferences preferences, CameraCapabilitySnapshot snapshot) {
        try {
            byte[] encoded = CameraCapabilityCache.encode(snapshot, Build.FINGERPRINT);
            preferences.edit()
                    .putString(CACHE_KEY, Base64.encodeToString(encoded, Base64.NO_WRAP))
                    .apply();
        } catch (Exception exception) {
            FileLog.e(exception);
        }
    }

    static CameraCapabilitySnapshot reconcileCamera1Inventory(
            CameraCapabilitySnapshot snapshot,
            List<CameraInfo> cameraInfos,
            List<CameraCapabilitySnapshot.Device> freshDevices
    ) {
        Map<String, CameraCapabilitySnapshot.Device> detailedDevices = new HashMap<>();
        for (CameraCapabilitySnapshot.Device device : snapshot.getDevices(
                CameraCapabilitySnapshot.Backend.CAMERA1
        )) {
            if (device.areDetailsComplete()) {
                detailedDevices.put(device.getId(), device);
            }
        }
        if (freshDevices != null) {
            for (CameraCapabilitySnapshot.Device device : freshDevices) {
                detailedDevices.put(device.getId(), device);
            }
        }

        ArrayList<CameraCapabilitySnapshot.Device> devices = new ArrayList<>();
        if (cameraInfos != null) {
            for (CameraInfo cameraInfo : cameraInfos) {
                String id = Integer.toString(cameraInfo.getCameraId());
                CameraCapabilitySnapshot.Device detailed = detailedDevices.get(id);
                devices.add(detailed == null ? basicCamera1Device(cameraInfo) : detailed);
            }
        }
        return snapshot.replaceInventory(CameraCapabilitySnapshot.Backend.CAMERA1, devices, true);
    }

    static CameraCapabilitySnapshot collectCamera2(CameraCapabilitySnapshot snapshot) {
        ArrayList<CameraCapabilitySnapshot.Device> devices = new ArrayList<>();
        boolean complete = true;
        try {
            CameraManager manager = (CameraManager) ApplicationLoader.applicationContext
                    .getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) {
                return snapshot.replaceInventory(
                        CameraCapabilitySnapshot.Backend.CAMERA2,
                        Collections.emptyList(),
                        false
                );
            }

            for (String cameraId : manager.getCameraIdList()) {
                try {
                    devices.add(camera2Device(
                            cameraId,
                            manager,
                            manager.getCameraCharacteristics(cameraId)
                    ));
                } catch (Exception exception) {
                    complete = false;
                    FileLog.e(exception);
                }
            }
        } catch (Exception exception) {
            complete = false;
            FileLog.e(exception);
        }

        return snapshot.replaceInventory(
                CameraCapabilitySnapshot.Backend.CAMERA2,
                devices,
                complete
        );
    }

    static CameraCapabilitySnapshot.Device camera1Device(
            CameraInfo cameraInfo,
            Camera.CameraInfo systemInfo,
            Camera.Parameters parameters
    ) {
        List<Size> previewSizes = camera1Sizes(parameters.getSupportedPreviewSizes());
        List<Size> photoSizes = camera1Sizes(parameters.getSupportedPictureSizes());
        ArrayList<CameraCapabilitySnapshot.FrameRateRange> frameRateRanges = new ArrayList<>();
        List<int[]> rawFrameRateRanges = parameters.getSupportedPreviewFpsRange();
        if (rawFrameRateRanges != null) {
            for (int[] range : rawFrameRateRanges) {
                if (range != null && range.length >= 2 && range[0] >= 0 && range[1] >= range[0]) {
                    frameRateRanges.add(new CameraCapabilitySnapshot.FrameRateRange(range[0], range[1]));
                }
            }
        }

        CameraCapabilitySnapshot.Support videoStabilization;
        try {
            videoStabilization = parameters.isVideoStabilizationSupported()
                    ? CameraCapabilitySnapshot.Support.SUPPORTED
                    : CameraCapabilitySnapshot.Support.UNSUPPORTED;
        } catch (RuntimeException exception) {
            videoStabilization = CameraCapabilitySnapshot.Support.UNKNOWN;
        }

        ArrayList<Float> focalLengths = new ArrayList<>(1);
        try {
            float focalLength = parameters.getFocalLength();
            if (Float.isFinite(focalLength) && focalLength > 0) {
                focalLengths.add(focalLength);
            }
        } catch (RuntimeException ignore) {
        }

        boolean hasZoomRange = false;
        float maximumZoomRatio = 1f;
        try {
            if (parameters.isZoomSupported()) {
                List<Integer> zoomRatios = parameters.getZoomRatios();
                if (zoomRatios != null && !zoomRatios.isEmpty()) {
                    int maximumRatio = zoomRatios.get(zoomRatios.size() - 1);
                    if (maximumRatio >= 100) {
                        hasZoomRange = true;
                        maximumZoomRatio = maximumRatio / 100f;
                    }
                }
            }
        } catch (RuntimeException ignore) {
        }

        List<CameraCapabilitySnapshot.Lens> lenses = Collections.singletonList(
                new CameraCapabilitySnapshot.Lens(
                        Integer.toString(cameraInfo.getCameraId()),
                        false,
                        focalLengths
                )
        );
        return new CameraCapabilitySnapshot.Device(
                CameraCapabilitySnapshot.Backend.CAMERA1,
                Integer.toString(cameraInfo.getCameraId()),
                camera1Facing(systemInfo.facing),
                normalizeOrientation(systemInfo.orientation),
                true,
                previewSizes,
                photoSizes,
                frameRateRanges,
                videoStabilization,
                CameraCapabilitySnapshot.Support.UNKNOWN,
                lenses,
                hasZoomRange,
                1f,
                maximumZoomRatio
        );
    }

    private static CameraCapabilitySnapshot.Device basicCamera1Device(CameraInfo cameraInfo) {
        Camera.CameraInfo systemInfo = new Camera.CameraInfo();
        CameraCapabilitySnapshot.Facing facing = cameraInfo.frontCamera == Camera.CameraInfo.CAMERA_FACING_FRONT
                ? CameraCapabilitySnapshot.Facing.FRONT
                : CameraCapabilitySnapshot.Facing.BACK;
        int orientation = -1;
        try {
            Camera.getCameraInfo(cameraInfo.getCameraId(), systemInfo);
            facing = camera1Facing(systemInfo.facing);
            orientation = normalizeOrientation(systemInfo.orientation);
        } catch (RuntimeException exception) {
            FileLog.e(exception);
        }

        return new CameraCapabilitySnapshot.Device(
                CameraCapabilitySnapshot.Backend.CAMERA1,
                Integer.toString(cameraInfo.getCameraId()),
                facing,
                orientation,
                false,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                CameraCapabilitySnapshot.Support.UNKNOWN,
                CameraCapabilitySnapshot.Support.UNKNOWN,
                Collections.emptyList(),
                false,
                1f,
                1f
        );
    }

    private static CameraCapabilitySnapshot.Device camera2Device(
            String cameraId,
            CameraManager manager,
            CameraCharacteristics characteristics
    ) {
        StreamConfigurationMap streamMap = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        );
        List<Size> previewSizes = streamMap == null
                ? Collections.emptyList()
                : camera2Sizes(streamMap.getOutputSizes(SurfaceTexture.class));
        List<Size> photoSizes = streamMap == null
                ? Collections.emptyList()
                : camera2Sizes(streamMap.getOutputSizes(ImageFormat.JPEG));

        ArrayList<CameraCapabilitySnapshot.FrameRateRange> frameRateRanges = new ArrayList<>();
        Range<Integer>[] rawFrameRateRanges = characteristics.get(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
        );
        if (rawFrameRateRanges != null) {
            for (Range<Integer> range : rawFrameRateRanges) {
                if (range != null && range.getLower() >= 0 && range.getUpper() >= range.getLower()) {
                    frameRateRanges.add(new CameraCapabilitySnapshot.FrameRateRange(
                            range.getLower() * 1000,
                            range.getUpper() * 1000
                    ));
                }
            }
        }

        int[] videoStabilizationModes = characteristics.get(
                CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES
        );
        int[] opticalStabilizationModes = characteristics.get(
                CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION
        );
        CameraCapabilitySnapshot.Support videoStabilization = supportForMode(
                videoStabilizationModes,
                CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
        );
        if (videoStabilization != CameraCapabilitySnapshot.Support.SUPPORTED
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && containsMode(
                        videoStabilizationModes,
                        CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
                )) {
            videoStabilization = CameraCapabilitySnapshot.Support.SUPPORTED;
        }
        CameraCapabilitySnapshot.Support opticalStabilization = supportForMode(
                opticalStabilizationModes,
                CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
        );

        ArrayList<String> physicalCameraIds = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Set<String> ids = characteristics.getPhysicalCameraIds();
            if (ids != null) {
                physicalCameraIds.addAll(ids);
                Collections.sort(physicalCameraIds);
            }
        }

        ArrayList<CameraCapabilitySnapshot.Lens> lenses = new ArrayList<>();
        boolean lensDetailsComplete = true;
        if (physicalCameraIds.isEmpty()) {
            lenses.add(new CameraCapabilitySnapshot.Lens(
                    cameraId,
                    false,
                    focalLengths(characteristics)
            ));
        } else {
            for (String physicalCameraId : physicalCameraIds) {
                try {
                    lenses.add(new CameraCapabilitySnapshot.Lens(
                            physicalCameraId,
                            true,
                            focalLengths(manager.getCameraCharacteristics(physicalCameraId))
                    ));
                } catch (Exception exception) {
                    lensDetailsComplete = false;
                    lenses.add(new CameraCapabilitySnapshot.Lens(
                            physicalCameraId,
                            true,
                            Collections.emptyList()
                    ));
                    FileLog.e(exception);
                }
            }
        }

        boolean hasZoomRange = false;
        float minimumZoomRatio = 1f;
        float maximumZoomRatio = 1f;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Range<Float> zoomRatioRange = characteristics.get(
                    CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE
            );
            if (zoomRatioRange != null
                    && Float.isFinite(zoomRatioRange.getLower())
                    && Float.isFinite(zoomRatioRange.getUpper())
                    && zoomRatioRange.getLower() > 0
                    && zoomRatioRange.getUpper() >= zoomRatioRange.getLower()
                    && (zoomRatioRange.getLower() < 1f || zoomRatioRange.getUpper() > 1f)) {
                hasZoomRange = true;
                minimumZoomRatio = zoomRatioRange.getLower();
                maximumZoomRatio = zoomRatioRange.getUpper();
            }
        }
        if (!hasZoomRange) {
            Float maximumDigitalZoom = characteristics.get(
                    CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM
            );
            if (maximumDigitalZoom != null
                    && Float.isFinite(maximumDigitalZoom)
                    && maximumDigitalZoom > 1f) {
                hasZoomRange = true;
                maximumZoomRatio = maximumDigitalZoom;
            }
        }

        Integer rawFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
        Integer rawOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        return new CameraCapabilitySnapshot.Device(
                CameraCapabilitySnapshot.Backend.CAMERA2,
                cameraId,
                camera2Facing(rawFacing),
                normalizeOrientation(rawOrientation == null ? -1 : rawOrientation),
                lensDetailsComplete,
                previewSizes,
                photoSizes,
                frameRateRanges,
                videoStabilization,
                opticalStabilization,
                lenses,
                hasZoomRange,
                minimumZoomRatio,
                maximumZoomRatio
        );
    }

    private static List<Size> camera1Sizes(List<Camera.Size> rawSizes) {
        if (rawSizes == null || rawSizes.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<Size> sizes = new ArrayList<>(rawSizes.size());
        for (Camera.Size size : rawSizes) {
            if (size != null && size.width > 0 && size.height > 0) {
                sizes.add(new Size(size.width, size.height));
            }
        }
        return sizes;
    }

    private static List<Size> camera2Sizes(android.util.Size[] rawSizes) {
        if (rawSizes == null || rawSizes.length == 0) {
            return Collections.emptyList();
        }

        ArrayList<Size> sizes = new ArrayList<>(rawSizes.length);
        for (android.util.Size size : rawSizes) {
            if (size != null && size.getWidth() > 0 && size.getHeight() > 0) {
                sizes.add(new Size(size.getWidth(), size.getHeight()));
            }
        }
        return sizes;
    }

    private static CameraCapabilitySnapshot.Support supportForMode(int[] modes, int requiredMode) {
        if (modes == null) {
            return CameraCapabilitySnapshot.Support.UNKNOWN;
        }
        for (int mode : modes) {
            if (mode == requiredMode) {
                return CameraCapabilitySnapshot.Support.SUPPORTED;
            }
        }
        return CameraCapabilitySnapshot.Support.UNSUPPORTED;
    }

    private static boolean containsMode(int[] modes, int requiredMode) {
        if (modes == null) {
            return false;
        }
        for (int mode : modes) {
            if (mode == requiredMode) {
                return true;
            }
        }
        return false;
    }

    private static List<Float> focalLengths(CameraCharacteristics characteristics) {
        float[] rawFocalLengths = characteristics.get(
                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
        );
        if (rawFocalLengths == null || rawFocalLengths.length == 0) {
            return Collections.emptyList();
        }

        ArrayList<Float> focalLengths = new ArrayList<>(rawFocalLengths.length);
        for (float focalLength : rawFocalLengths) {
            if (Float.isFinite(focalLength) && focalLength > 0) {
                focalLengths.add(focalLength);
            }
        }
        return focalLengths;
    }

    private static CameraCapabilitySnapshot.Facing camera1Facing(int facing) {
        if (facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
            return CameraCapabilitySnapshot.Facing.BACK;
        }
        if (facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return CameraCapabilitySnapshot.Facing.FRONT;
        }
        return CameraCapabilitySnapshot.Facing.UNKNOWN;
    }

    private static CameraCapabilitySnapshot.Facing camera2Facing(Integer facing) {
        if (facing == null) {
            return CameraCapabilitySnapshot.Facing.UNKNOWN;
        }
        if (facing == CameraCharacteristics.LENS_FACING_BACK) {
            return CameraCapabilitySnapshot.Facing.BACK;
        }
        if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            return CameraCapabilitySnapshot.Facing.FRONT;
        }
        if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
            return CameraCapabilitySnapshot.Facing.EXTERNAL;
        }
        return CameraCapabilitySnapshot.Facing.UNKNOWN;
    }

    private static int normalizeOrientation(int orientation) {
        return orientation >= 0 && orientation < 360 ? orientation : -1;
    }
}

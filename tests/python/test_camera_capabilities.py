from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
CAMERA = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/camera"


class CameraCapabilitiesTest(unittest.TestCase):
    def test_snapshot_is_immutable_and_cache_is_bounded(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("java and javac are required")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            check = root / "org/telegram/messenger/camera/CameraCapabilitySnapshotCheck.java"
            check.parent.mkdir(parents=True)
            check.write_text(textwrap.dedent("""
                package org.telegram.messenger.camera;

                import java.io.IOException;
                import java.util.ArrayList;
                import java.util.Arrays;
                import java.util.Collections;
                import java.util.List;

                public final class CameraCapabilitySnapshotCheck {
                    public static void main(String[] args) throws Exception {
                        checkSnapshot();
                        checkCache();
                        checkBounds();
                    }

                    private static void checkSnapshot() {
                        ArrayList<Size> previewSizes = new ArrayList<>();
                        previewSizes.add(new Size(1920, 1080));
                        ArrayList<Float> focalLengths = new ArrayList<>();
                        focalLengths.add(4.3f);
                        CameraCapabilitySnapshot.Device camera1 = device(
                                CameraCapabilitySnapshot.Backend.CAMERA1,
                                "0",
                                true,
                                previewSizes,
                                focalLengths
                        );
                        previewSizes.clear();
                        focalLengths.clear();

                        CameraCapabilitySnapshot snapshot = CameraCapabilitySnapshot.empty()
                                .replaceInventory(
                                        CameraCapabilitySnapshot.Backend.CAMERA1,
                                        Collections.singletonList(camera1),
                                        true
                                );
                        require(snapshot.isInventoryComplete(CameraCapabilitySnapshot.Backend.CAMERA1));
                        require(snapshot.areDetailsComplete(CameraCapabilitySnapshot.Backend.CAMERA1));
                        require(!snapshot.isInventoryComplete(CameraCapabilitySnapshot.Backend.CAMERA2));
                        require(snapshot.getDevices().size() == 1);
                        require(snapshot.getDevices(CameraCapabilitySnapshot.Backend.CAMERA1).size() == 1);
                        require(snapshot.findDevice(CameraCapabilitySnapshot.Backend.CAMERA1, "0") == camera1);
                        require(camera1.getPreviewSizes().size() == 1);
                        require(camera1.getLenses().size() == 1);
                        require(camera1.getLenses().get(0).getFocalLengths().size() == 1);
                        requireImmutable(snapshot.getDevices());
                        requireImmutable(camera1.getPreviewSizes());
                        requireImmutable(camera1.getLenses());
                        requireImmutable(camera1.getLenses().get(0).getFocalLengths());

                        CameraCapabilitySnapshot.Device camera2 = device(
                                CameraCapabilitySnapshot.Backend.CAMERA2,
                                "rear-wide",
                                true,
                                Collections.singletonList(new Size(1280, 720)),
                                Collections.singletonList(2.4f)
                        );
                        snapshot = snapshot.replaceInventory(
                                CameraCapabilitySnapshot.Backend.CAMERA2,
                                Collections.singletonList(camera2),
                                true
                        );
                        require(snapshot.getDevices().size() == 2);
                        require(snapshot.areDetailsComplete(CameraCapabilitySnapshot.Backend.CAMERA2));

                        CameraCapabilitySnapshot.Device incomplete = device(
                                CameraCapabilitySnapshot.Backend.CAMERA1,
                                "0",
                                false,
                                Collections.emptyList(),
                                Collections.emptyList()
                        );
                        snapshot = snapshot.replaceDevice(incomplete);
                        require(!snapshot.areDetailsComplete(CameraCapabilitySnapshot.Backend.CAMERA1));
                        require(snapshot.getDevices().size() == 2);
                    }

                    private static void checkCache() throws Exception {
                        CameraCapabilitySnapshot.Device camera1 = device(
                                CameraCapabilitySnapshot.Backend.CAMERA1,
                                "0",
                                true,
                                Collections.singletonList(new Size(1920, 1080)),
                                Collections.singletonList(4.3f)
                        );
                        CameraCapabilitySnapshot.Device camera2 = device(
                                CameraCapabilitySnapshot.Backend.CAMERA2,
                                "rear-wide",
                                true,
                                Arrays.asList(new Size(1920, 1080), new Size(1280, 720)),
                                Arrays.asList(2.4f, 7.0f)
                        );
                        CameraCapabilitySnapshot snapshot = new CameraCapabilitySnapshot(
                                Arrays.asList(camera1, camera2),
                                true,
                                true
                        );
                        byte[] encoded = CameraCapabilityCache.encode(snapshot, "device-build");
                        CameraCapabilitySnapshot decoded = CameraCapabilityCache.decode(
                                encoded,
                                "device-build"
                        );
                        require(decoded != null);
                        require(decoded.getDevices().size() == 2);
                        require(decoded.areDetailsComplete(CameraCapabilitySnapshot.Backend.CAMERA1));
                        require(decoded.areDetailsComplete(CameraCapabilitySnapshot.Backend.CAMERA2));
                        CameraCapabilitySnapshot.Device decodedCamera2 = decoded.findDevice(
                                CameraCapabilitySnapshot.Backend.CAMERA2,
                                "rear-wide"
                        );
                        require(decodedCamera2 != null);
                        require(decodedCamera2.getFacing() == CameraCapabilitySnapshot.Facing.BACK);
                        require(decodedCamera2.getPreviewSizes().size() == 2);
                        require(decodedCamera2.getFrameRateRanges().get(0).getMaximumMilliFps() == 60000);
                        require(decodedCamera2.getVideoStabilization()
                                == CameraCapabilitySnapshot.Support.SUPPORTED);
                        require(decodedCamera2.getOpticalStabilization()
                                == CameraCapabilitySnapshot.Support.UNSUPPORTED);
                        require(decodedCamera2.getLenses().size() == 1);
                        require(decodedCamera2.getLenses().get(0).getId().equals("rear-wide"));
                        require(decodedCamera2.getLenses().get(0).isPhysicalCamera());
                        require(decodedCamera2.getLenses().get(0).getFocalLengths().equals(
                                Arrays.asList(2.4f, 7.0f)
                        ));
                        require(decodedCamera2.hasZoomRange());
                        require(decodedCamera2.getMinimumZoomRatio() == 0.6f);
                        require(decodedCamera2.getMaximumZoomRatio() == 10f);
                        require(CameraCapabilityCache.decode(encoded, "other-build") == null);
                        require(CameraCapabilityCache.decode(Arrays.copyOf(encoded, 12), "device-build") == null);
                        require(CameraCapabilityCache.decode(new byte[512 * 1024 + 1], "device-build") == null);
                    }

                    private static void checkBounds() throws Exception {
                        ArrayList<CameraCapabilitySnapshot.Device> devices = new ArrayList<>();
                        for (int index = 0; index < 33; index++) {
                            devices.add(device(
                                    CameraCapabilitySnapshot.Backend.CAMERA2,
                                    Integer.toString(index),
                                    true,
                                    Collections.emptyList(),
                                    Collections.emptyList()
                            ));
                        }
                        try {
                            CameraCapabilityCache.encode(
                                    new CameraCapabilitySnapshot(devices, true, true),
                                    "device-build"
                            );
                            throw new AssertionError();
                        } catch (IOException expected) {
                        }

                        try {
                            device(
                                    CameraCapabilitySnapshot.Backend.CAMERA2,
                                    "",
                                    true,
                                    Collections.emptyList(),
                                    Collections.emptyList()
                            );
                            throw new AssertionError();
                        } catch (IllegalArgumentException expected) {
                        }

                        CameraCapabilitySnapshot.Device duplicate = device(
                                CameraCapabilitySnapshot.Backend.CAMERA2,
                                "duplicate",
                                true,
                                Collections.emptyList(),
                                Collections.emptyList()
                        );
                        try {
                            new CameraCapabilitySnapshot(
                                    Arrays.asList(duplicate, duplicate),
                                    true,
                                    true
                            );
                            throw new AssertionError();
                        } catch (IllegalArgumentException expected) {
                        }

                        try {
                            new CameraCapabilitySnapshot.FrameRateRange(60000, 30000);
                            throw new AssertionError();
                        } catch (IllegalArgumentException expected) {
                        }
                    }

                    private static CameraCapabilitySnapshot.Device device(
                            CameraCapabilitySnapshot.Backend backend,
                            String id,
                            boolean complete,
                            List<Size> previewSizes,
                            List<Float> focalLengths
                    ) {
                        List<CameraCapabilitySnapshot.Lens> lenses = focalLengths.isEmpty()
                                ? Collections.emptyList()
                                : Collections.singletonList(
                                        new CameraCapabilitySnapshot.Lens(
                                                id,
                                                backend == CameraCapabilitySnapshot.Backend.CAMERA2,
                                                focalLengths
                                        )
                                );
                        return new CameraCapabilitySnapshot.Device(
                                backend,
                                id,
                                CameraCapabilitySnapshot.Facing.BACK,
                                90,
                                complete,
                                previewSizes,
                                Collections.singletonList(new Size(4000, 3000)),
                                Collections.singletonList(
                                        new CameraCapabilitySnapshot.FrameRateRange(30000, 60000)
                                ),
                                CameraCapabilitySnapshot.Support.SUPPORTED,
                                CameraCapabilitySnapshot.Support.UNSUPPORTED,
                                lenses,
                                true,
                                backend == CameraCapabilitySnapshot.Backend.CAMERA2 ? 0.6f : 1f,
                                10f
                        );
                    }

                    private static void requireImmutable(List<?> values) {
                        try {
                            ((List<Object>) values).add(new Object());
                            throw new AssertionError();
                        } catch (UnsupportedOperationException expected) {
                        }
                    }

                    private static void require(boolean value) {
                        if (!value) {
                            throw new AssertionError();
                        }
                    }
                }
            """))

            subprocess.run(
                [
                    "javac",
                    "-d",
                    str(root),
                    str(CAMERA / "Size.java"),
                    str(CAMERA / "CameraCapabilitySnapshot.java"),
                    str(CAMERA / "CameraCapabilityCache.java"),
                    str(check),
                ],
                check=True,
                cwd=ROOT,
            )
            subprocess.run(
                ["java", "-cp", str(root), "org.telegram.messenger.camera.CameraCapabilitySnapshotCheck"],
                check=True,
                cwd=ROOT,
            )

    def test_collectors_do_not_change_attachment_camera_backend(self):
        collector = (CAMERA / "CameraCapabilityCollector.java").read_text()
        controller = (CAMERA / "CameraController.java").read_text()
        camera_view = (CAMERA / "CameraView.java").read_text()

        self.assertIn("false && SharedConfig.isUsingCamera2", camera_view)
        self.assertNotIn("Camera.open(", collector)
        self.assertIn("getSupportedPreviewFpsRange()", collector)
        self.assertIn("isVideoStabilizationSupported()", collector)
        self.assertIn("CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES", collector)
        self.assertIn("CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES", collector)
        self.assertIn("CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION", collector)
        self.assertIn("LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION", collector)
        self.assertIn("LENS_INFO_AVAILABLE_FOCAL_LENGTHS", collector)
        self.assertIn("getPhysicalCameraIds()", collector)
        self.assertIn("getCameraCharacteristics(physicalCameraId)", collector)
        self.assertIn("CONTROL_ZOOM_RATIO_RANGE", collector)
        self.assertIn('CACHE_KEY = "cameraCapabilities_v2"', collector)
        self.assertIn("MAX_ENCODED_CACHE_LENGTH", collector)
        self.assertIn("zoomRatioRange.getLower() < 1f", collector)
        self.assertIn("zoomRatioRange.getUpper() > 1f", collector)
        self.assertIn("maximumDigitalZoom > 1f", collector)

        self.assertIn("CameraCapabilityCollector.collectCamera2", controller)
        self.assertIn("CameraCapabilityCollector.reconcileCamera1Inventory", controller)
        self.assertEqual(2, controller.count("updateCameraCapabilities(session.cameraInfo, params)"))
        self.assertIn("public CameraCapabilitySnapshot getCameraCapabilities()", controller)
        self.assertIn("public void refreshCameraCapabilities(Runnable callback)", controller)
        self.assertNotIn("CameraCapability", (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/FluxGramSettingsActivity.java").read_text())


if __name__ == "__main__":
    unittest.main()

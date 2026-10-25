from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
ICONS = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/fluxgram/icons"
STUBS = ROOT / "tests/java/icon_pack_android_stubs"


class IconPackAndroidAdapterTest(unittest.TestCase):
    def test_catalog_and_decoder_contract(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("java and javac are required")

        with tempfile.TemporaryDirectory(prefix="icon-pack-android-") as directory:
            sources = [str(path) for path in STUBS.rglob("*.java")]
            sources.extend(
                [
                    str(ICONS / "IconPackAndroidCatalog.java"),
                    str(ICONS / "IconPackAndroidDrawableDecoder.java"),
                    str(ICONS / "IconPackResources.java"),
                    str(ICONS / "IconPackRuntime.java"),
                    str(ROOT / "tests/java/IconPackAndroidCheck.java"),
                ]
            )

            subprocess.run(
                ["javac", "--release", "8", "-d", directory, *sources],
                check=True,
                capture_output=True,
            )
            subprocess.run(
                [
                    "java",
                    "-cp",
                    directory,
                    "org.telegram.messenger.fluxgram.icons.IconPackAndroidCheck",
                ],
                check=True,
                capture_output=True,
            )

        validator = (ICONS / "IconPackArchiveValidator.java").read_text()
        runtime = (ICONS / "IconPackRuntime.java").read_text()

        self.assertIn("catalog.decodesSvg(bytes, path)", validator)
        catalog = (ICONS / "IconPackAndroidCatalog.java").read_text()
        self.assertIn('getIdentifier(resourceName, "drawable", packageName)', catalog)
        self.assertNotIn('drawables.put("msg_add"', catalog)
        self.assertIn("SVG.getFromString", catalog)
        self.assertIn("hasVisiblePixels", catalog)
        self.assertNotIn("SvgHelper", catalog)
        self.assertIn("private static volatile State state", runtime)
        self.assertIn("private static final LinkedHashSet<Demand> demanded", runtime)
        self.assertIn("private static final LinkedHashSet<Demand> pendingDemands", runtime)
        self.assertIn("static boolean decodePending", runtime)
        self.assertIn("plan.requestId != requestId", runtime)
        self.assertIn("Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)", runtime)
        self.assertIn("getOriginalDrawableForDensity", runtime)
        self.assertIn("public static Drawable resolve(Resources resources, String resourceName)", runtime)
        self.assertIn("MAX_CACHE_BYTES", runtime)
        self.assertIn("WeakHashMap<ImageView, ImageBinding>", runtime)
        self.assertIn("binding.drawable.get() != imageView.getDrawable()", runtime)
        self.assertIn("BooleanSupplier currentRequest", runtime)
        self.assertIn("source.recycle()", runtime)
        self.assertIn("IconPackAndroidCatalog.hasVisiblePixels(result)", runtime)
        resolve_paths = runtime.split("public static Drawable resolve", 1)[1].split("static boolean rebuild", 1)[0]
        self.assertNotIn("store.resolve", resolve_paths)
        self.assertNotIn("IconPackAndroidDrawableDecoder", resolve_paths)
        self.assertNotIn("Bitmap.createBitmap", resolve_paths)
        self.assertNotIn("pack.manifest.icons.entrySet()", runtime)
        resolution = runtime.split("private static final class Resolution", 1)[1].split(
            "private static final class Decoded", 1
        )[0]
        self.assertNotIn("Bitmap bitmap", resolution)

        build = (ROOT / "TMessagesProj/build.gradle").read_text()
        self.assertIn("com.caverock:androidsvg-aar:1.4", build)

        application = (ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/ApplicationLoader.java").read_text()
        activity = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/BasePermissionsActivity.java").read_text()

        self.assertIn("new IconPackResources(resources)", application)
        self.assertIn("new IconPackResources(resources)", activity)

        hook_files = [
            "ui/ActionBar/ActionBarMenuSubItem.java",
            "ui/ActionBar/ActionBarMenu.java",
            "ui/ActionBar/ActionBarMenuItem.java",
            "ui/ActionBar/BottomSheet.java",
            "ui/ActionBar/AlertDialog.java",
            "ui/Cells/TextCell.java",
            "ui/Cells/TextSettingsCell.java",
            "ui/Cells/TextCheckCell.java",
            "ui/Cells/SessionCell.java",
            "ui/Components/FluxGramMessageMenuActionRow.java",
            "ui/Components/ProfileActionsView.java",
            "ui/Components/TopicsTabsView.java",
            "ui/Components/chat/buttons/ChatActivityBlurredRoundButton.java",
            "ui/iv/RichEditor.java",
        ]

        for relative in hook_files:
            source = (ROOT / "TMessagesProj/src/main/java/org/telegram" / relative).read_text()
            self.assertIn("IconPackRuntime", source, relative)

        composer = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterViewAnimatedIconView.java").read_text()
        chat = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java").read_text()
        dialogs = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java").read_text()
        topics = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/TopicsFragment.java").read_text()
        tabs = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/Components/glass/GlassTabView.java").read_text()

        self.assertIn('IconPackRuntime.resolve(headerItem.getResources(), "boosts")', chat)
        self.assertIn("new RLottieDrawable(R.raw.boosts", chat)
        self.assertIn(
            'IconPackRuntime.resolve(floatingButton3.getResources(), "fab_compose_small")',
            dialogs,
        )
        self.assertIn("usesFluxGramRootDialogsAppearance()", dialogs)
        self.assertIn("floatingButton3.setImageResource(R.drawable.filled_fab_compose_32)", dialogs)
        self.assertIn('IconPackRuntime.resolve(other.getResources(), "boosts")', topics)
        self.assertIn("new RLottieDrawable(", topics)
        self.assertIn("R.raw.boosts", topics)
        self.assertIn('BOOSTS(R.raw.boosts, 25, 49, "boosts")', tabs)
        for name in ("msg_sticker", "input_keyboard", "input_smile", "msg_gif"):
            self.assertIn(f"R.drawable.{name}", composer)
        self.assertIn(
            'IconPackRuntime.resolve(getResources(), "instant_camera")',
            composer,
        )
        self.assertIn("state == State.VIDEO", composer)
        self.assertIn("R.raw.voice_and_video", composer)
        for name in (
            "tabs_contact_active_24",
            "tabs_contacts_24",
            "tabs_chats_24",
            "tabs_chats_active_24",
            "tabs_calls_active_24",
            "tabs_calls_24",
        ):
            self.assertIn(f'"{name}"', tabs)

        names = "|".join(
            (
                "msg_add",
                "msg_copy",
                "msg_delete",
                "msg_download",
                "msg_edit",
                "msg_forward",
                "msg_gallery",
                "msg_search",
                "msg_select",
                "msg_send",
                "msg_settings",
                "msg_share",
            )
        )
        direct_load = re.compile(
            rf"setImageResource\(\s*R\.drawable\.(?:{names})\s*\)"
        )
        sources_root = ROOT / "TMessagesProj/src/main/java/org/telegram"
        bypasses = []

        for path in sources_root.rglob("*.java"):
            if path.name == "IconPackRuntime.java":
                continue
            if direct_load.search(path.read_text()):
                bypasses.append(str(path.relative_to(ROOT)))

        self.assertEqual([], bypasses)


if __name__ == "__main__":
    unittest.main()

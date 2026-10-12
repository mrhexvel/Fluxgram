from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"


class ComposerTextTest(unittest.TestCase):
    def test_concrete_text_effect_is_not_implemented_in_host(self):
        production = ROOT / "TMessagesProj/src/main/java/org/telegram"
        sources = "\n".join(path.read_text() for path in production.rglob("*.java"))

        for forbidden in (
            "ComposerGlyphPrototype",
            "ComposerGlyphCandidate",
            "ComposerGlyphSnapshot",
            "registerExperimentalGlyphPrototype",
            "createGlyphPrototype",
            "implements ComposerTextEffect",
        ):
            self.assertNotIn(forbidden, sources)

        capture = (JAVA / "ui/fluxgram/ComposerNativeTextCapture.java").read_text()

        for forbidden in ("180_000", "eased", "Cyrillic", "letter(", "requestFrame("):
            self.assertNotIn(forbidden, capture)

        adapter = (JAVA / "messenger/plugins/PythonPluginAdapter.java").read_text()
        self.assertNotIn("fluxgram.in_app_notifications", adapter)
        manifest = (JAVA / "messenger/plugins/packages/PluginManifest.java").read_text()
        self.assertIn("JAVA_API_MINOR_VERSION = 5", manifest)

    def test_external_module_compiles_against_api_1_3(self):
        if not shutil.which("javac"):
            self.skipTest("Requires JDK")

        source = textwrap.dedent(
            """
            package external;

            import android.graphics.Canvas;
            import org.telegram.messenger.plugins.api.*;

            public final class TextModule implements JavaPluginModule {
                public void onLoad(JavaPluginContext context) {
                    context.registerComposerTextEffect(Effect::new);
                }

                public void onUnload() {}

                private static final class Effect implements ComposerTextEffect {
                    private final ComposerSurface surface;

                    Effect(ComposerSurface surface) {
                        this.surface = surface;
                    }

                    public ComposerTextCandidate selectCandidate(ComposerTextChange change) {
                        surface.requestFrame();
                        return new ComposerTextCandidate(change.revision, change.start,
                                change.start + change.insertedCount);
                    }

                    public ComposerTextPresentation present(ComposerTextCapture capture) {
                        return new ComposerTextPresentation(0, 0, 1);
                    }

                    public void onInput(ComposerInput input) {}
                    public boolean onFrame(long timestamp) { return false; }
                    public void draw(Canvas canvas, ComposerGeometry geometry) {}
                    public void close() {}
                }
            }
            """
        )

        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            module = directory / "external/TextModule.java"
            module.parent.mkdir()
            module.write_text(source)
            sources = [
                *sorted((JAVA / "messenger/plugins/api").glob("*.java")),
                ROOT / "tests/java/composer_stubs/android/graphics/Canvas.java",
                module,
            ]
            subprocess.run(
                ["javac", "--release", "8", "-d", str(directory / "classes"), *map(str, sources)],
                check=True,
                capture_output=True,
            )

    def test_external_layout_effect_compiles_against_api_1_4(self):
        if not shutil.which("javac"):
            self.skipTest("Requires JDK")

        source = textwrap.dedent(
            """
            package external;

            import android.graphics.Canvas;
            import org.telegram.messenger.plugins.api.*;

            public final class LayoutEffect implements ComposerTextLayoutEffect {
                public ComposerTextCandidate selectCandidate(ComposerTextChange change) { return null; }
                public ComposerTextPresentation present(ComposerTextCapture capture) { return null; }
                public void onTextLayoutChanged(ComposerTextLayoutChange change) {
                    for (int i = 0; i < change.after.getLineCount(); i++) {
                        ComposerTextLineSnapshot line = change.after.getLine(i);
                    }
                    for (int i = 0; i < change.after.getClusterCount(); i++) {
                        ComposerTextClusterSnapshot cluster = change.after.getCluster(i);
                    }
                }
                public void onInput(ComposerInput input) {}
                public boolean onFrame(long timestamp) { return false; }
                public void draw(Canvas canvas, ComposerGeometry geometry) {}
                public void close() {}
            }
            """
        )

        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            module = directory / "external/LayoutEffect.java"
            module.parent.mkdir()
            module.write_text(source)
            sources = [
                *sorted((JAVA / "messenger/plugins/api").glob("*.java")),
                ROOT / "tests/java/composer_stubs/android/graphics/Canvas.java",
                module,
            ]
            subprocess.run(
                ["javac", "--release", "8", "-d", str(directory / "classes"), *map(str, sources)],
                check=True,
                capture_output=True,
            )


if __name__ == "__main__":
    unittest.main()

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"


class ExportDestinationSettingsTest(unittest.TestCase):
    def test_settings_use_a_persisted_saf_tree(self):
        settings = (JAVA / "messenger/fluxgram/ExportDestinationSettings.java").read_text()
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        self.assertIn("Intent.ACTION_OPEN_DOCUMENT_TREE", activity)
        self.assertIn("Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION", activity)
        self.assertIn("takePersistableUriPermission(uri, flags)", settings)
        self.assertIn("getPersistedUriPermissions()", settings)
        self.assertIn("permission.isWritePermission()", settings)
        self.assertIn("releasePersistableUriPermission(uri, persistedFlags)", settings)
        self.assertIn("DocumentsContract.createDocument(", settings)
        self.assertIn("DocumentsContract.deleteDocument(resolver, destinationUri)", settings)
        self.assertIn("FileLoader.fixFileName(", settings)
        self.assertNotIn("getAbsolutePath()", settings)

    def test_only_explicit_download_exports_use_the_custom_tree(self):
        media = (JAVA / "messenger/MediaController.java").read_text()
        file_loader = (JAVA / "messenger/FileLoader.java").read_text()

        self.assertEqual(3, media.count("ExportDestinationSettings.saveDownload("))
        self.assertIn("Uri customUri = type == 2", media)
        self.assertEqual(2, media.count("isMusic ? null : ExportDestinationSettings.saveDownload("))
        self.assertIn("savedUri = saveFileInternal(isMusic ? 3 : 2, sourceFile, name);", media)
        self.assertIn("else if (Build.VERSION.SDK_INT >= 29)", media)
        self.assertNotIn("ExportDestinationSettings", file_loader)

    def test_ui_supports_replacing_and_clearing_the_tree(self):
        activity = (JAVA / "ui/FluxGramSettingsActivity.java").read_text()

        self.assertIn("R.string.FluxGramSaveFilesDirectory", activity)
        self.assertIn("this::selectDownloadsDirectory", activity)
        self.assertIn("ExportDestinationSettings.clearDownloadsTreeUri();", activity)
        self.assertIn("ExportDestinationSettings.setDownloadsTreeUri(uri, data.getFlags())", activity)


if __name__ == "__main__":
    unittest.main()

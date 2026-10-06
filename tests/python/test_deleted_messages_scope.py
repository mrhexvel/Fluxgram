from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "TMessagesProj/src/main/java/org/telegram"


class DeletedMessagesScopeTest(unittest.TestCase):
    def test_scope_policy(self):
        if not shutil.which("javac") or not shutil.which("java"):
            self.skipTest("Requires JDK")

        with tempfile.TemporaryDirectory() as directory:
            subprocess.run([
                "javac", "-d", directory,
                str(JAVA / "messenger/fluxgram/DeletedMessagesScope.java"),
                str(ROOT / "tests/java/DeletedMessagesScopeCheck.java"),
            ], check=True, capture_output=True)
            subprocess.run([
                "java", "-cp", directory, "DeletedMessagesScopeCheck",
            ], check=True, capture_output=True)

    def test_capture_filters_new_rows_by_actual_dialog(self):
        source = (JAVA / "messenger/fluxgram/DeletedMessagesArchive.java").read_text()
        capture = source[source.index("    public HashMap<Long, HashSet<Integer>> capture("):
                         source.index("    private void copy(")]

        archived = capture.index("SELECT uid, mid FROM fg_deleted_messages")
        live = capture.index("SELECT uid, mid, data, ttl FROM messages_v2")
        allowed = capture.index("deletedMessagesScope.allows(")
        body = capture.index("cursor.byteBufferValue(2)")
        accepted = capture.index("accepted.computeIfAbsent(did")

        self.assertLess(archived, allowed)
        self.assertLess(live, allowed)
        self.assertLess(allowed, body)
        self.assertLess(body, accepted)
        self.assertIn("UserConfig.getInstance(account).getClientUserId(), did", capture)

    def test_preferences_default_to_all_chats_without_clearing_archive(self):
        source = (JAVA / "messenger/fluxgram/EditHistory.java").read_text()
        start = source.index("    public static boolean keepsDeletedInAllChats(")
        end = source.index("    static DeletedMessagesScope deletedMessagesScope(", start)
        methods = source[start:end]

        self.assertIn('getBoolean("deleted_all_chats", true)', methods)
        self.assertIn('putStringSet("deleted_chats", values)', methods)
        self.assertIn("DeletedMessagesScope.validDialog(dialog)", methods)
        self.assertNotIn("deleteDatabase", methods)
        self.assertNotIn("clearVersions", methods)
        self.assertNotIn("clearAccount", methods)

    def test_ui_uses_account_guarded_telegram_picker(self):
        source = (JAVA / "ui/FluxGramStealthActivity.java").read_text()
        start = source.index("    private void showDeletedChatSelection(")
        end = source.index("    private void addAction(", start)
        methods = source[start:end]

        self.assertIn("InviteMembersBottomSheet", methods)
        self.assertIn("picker.setSelectedContacts(selected);", methods)
        self.assertIn("activeAccount(account, owner)", methods)
        self.assertIn("new HashSet<>(dialogs)", methods)
        self.assertIn("DialogObject.isUserDialog(dialog)", methods)
        self.assertIn("DialogObject.isChatDialog(dialog)", methods)
        self.assertNotIn("SavedAttachments", methods)


if __name__ == "__main__":
    unittest.main()

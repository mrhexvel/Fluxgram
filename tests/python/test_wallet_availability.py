from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
APP_GLOBAL_CONFIG = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/AppGlobalConfig.java"


class WalletAvailabilityTest(unittest.TestCase):
    def test_wallet_ignores_disabled_server_flag(self):
        source = APP_GLOBAL_CONFIG.read_text()

        self.assertIn(
            'walletAvailable = ofBoolean("wallet_available", BuildVars.DEBUG_VERSION, true);',
            source,
        )
        self.assertIn("return forceEnabled || handler.value;", source)
        self.assertEqual(1, source.count('ofBoolean("wallet_available"'))


if __name__ == "__main__":
    unittest.main()

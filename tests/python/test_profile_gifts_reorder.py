from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
PROFILE_GIFTS = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/Gifts/ProfileGiftsContainer.java"


class ProfileGiftsReorderTest(unittest.TestCase):
    def test_reorder_uses_gift_indices_instead_of_adapter_positions(self):
        source = PROFILE_GIFTS.read_text()

        self.assertIn("final int fromIndex = list.gifts.indexOf(getSavedGift(viewHolder));", source)
        self.assertIn("final int toIndex = list.gifts.indexOf(getSavedGift(target));", source)
        self.assertIn("if (fromIndex < 0 || toIndex < 0)", source)
        self.assertIn("list.reorder(fromIndex, toIndex);", source)
        self.assertIn("list.reorderPinned(fromIndex, toIndex);", source)
        self.assertIn("listView.adapter.notifyItemMoved(fromPosition, toPosition);", source)
        self.assertNotIn("list.reorder(fromPosition, toPosition);", source)
        self.assertNotIn("list.reorderPinned(fromPosition, toPosition);", source)


if __name__ == "__main__":
    unittest.main()

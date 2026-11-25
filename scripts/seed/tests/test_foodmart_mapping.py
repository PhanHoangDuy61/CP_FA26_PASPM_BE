"""Unit tests for the FoodMart code -> server SKU mapping and the import-ready CSV copies (no backend).

Run from the repository root:  python3 -I -m unittest discover -s scripts/seed/tests -v
"""
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))  # python -I does not add the script directory

import foodmart_mapping as fm  # noqa: E402

HEADER = "source_order_id,occurred_at,sku,quantity,unit_price,unit_cost,status"
LINES = [
    "FMTEST-000001,2026-10-01T09:00:00+07:00,FOODMART-0214,6,1983.33,1388.33,COMPLETED",
    "FMTEST-000001,2026-10-01T09:00:00+07:00,FOODMART-0260,6,2906.67,2034.67,COMPLETED",
    "FMTEST-000002,2026-10-01T09:05:00+07:00,FOODMART-0214,1,1983.33,1388.33,CANCELLED",
]
MAPPING = {
    "FOODMART-0214": fm.Mapped("FOODMART-0214", "SKU-00000000007", 7, "FoodMart Item 0214"),
    "FOODMART-0260": fm.Mapped("FOODMART-0260", "SKU-00000000042", 42, "FoodMart Item 0260"),
}


def write(path, text):
    path.write_bytes(text.encode("utf-8"))


class NameTest(unittest.TestCase):
    def test_product_name_is_derived_from_the_code(self):
        self.assertEqual(fm.product_name("FOODMART-0007"), "FoodMart Item 0007")
        self.assertEqual(fm.code_from_name("FoodMart Item 0007"), "FOODMART-0007")
        self.assertIsNone(fm.code_from_name("Fresh milk"))
        self.assertIsNone(fm.code_from_name("FoodMart Item 7"))


class RewriteTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        self.src = self.dir / "in.csv"
        self.dst = self.dir / "out" / "in.csv"

    def test_only_the_sku_column_changes_and_crlf_bytes_are_kept(self):
        write(self.src, "\r\n".join([HEADER] + LINES) + "\r\n")
        rows = fm.rewrite_csv(self.src, self.dst, MAPPING)
        self.assertEqual(rows, 3)
        out = self.dst.read_bytes().decode("utf-8")
        expected = "\r\n".join([HEADER] + [
            LINES[0].replace("FOODMART-0214", "SKU-00000000007"),
            LINES[1].replace("FOODMART-0260", "SKU-00000000042"),
            LINES[2].replace("FOODMART-0214", "SKU-00000000007"),
        ]) + "\r\n"
        self.assertEqual(out, expected)

    def test_the_original_file_is_not_modified(self):
        original = "\r\n".join([HEADER] + LINES) + "\r\n"
        write(self.src, original)
        fm.rewrite_csv(self.src, self.dst, MAPPING)
        self.assertEqual(self.src.read_bytes().decode("utf-8"), original)

    def test_an_identity_mapping_for_an_already_seeded_database_gives_an_identical_copy(self):
        original = "\r\n".join([HEADER] + LINES) + "\r\n"
        write(self.src, original)
        identity = {c: fm.Mapped(c, c, i, fm.product_name(c)) for i, c in enumerate(MAPPING)}
        fm.rewrite_csv(self.src, self.dst, identity)
        self.assertEqual(self.dst.read_bytes(), self.src.read_bytes())

    def test_an_unmapped_code_fails_and_writes_nothing(self):
        write(self.src, "\r\n".join([HEADER, LINES[0].replace("0214", "9999")]) + "\r\n")
        with self.assertRaisesRegex(fm.MappingError, "FOODMART-9999"):
            fm.rewrite_csv(self.src, self.dst, MAPPING)
        self.assertFalse(self.dst.exists())

    def test_an_unexpected_header_or_a_quoted_field_is_rejected(self):
        write(self.src, "order,sku\r\nA,FOODMART-0214\r\n")
        with self.assertRaisesRegex(fm.MappingError, "header"):
            fm.rewrite_csv(self.src, self.dst, MAPPING)
        write(self.src, "\r\n".join([HEADER, '"FMTEST-1",2026-10-01T09:00:00+07:00,FOODMART-0214,1,1,1,COMPLETED']) + "\r\n")
        with self.assertRaisesRegex(fm.MappingError, "quoted"):
            fm.rewrite_csv(self.src, self.dst, MAPPING)


class MappingFileTest(unittest.TestCase):
    def test_mapping_file_is_sorted_by_code_and_round_trips(self):
        path = Path(tempfile.mkdtemp()) / "mapping.csv"
        fm.write_mapping(path, MAPPING)
        lines = path.read_text(encoding="utf-8").splitlines()
        self.assertEqual(lines[0], "foodmart_code,sku,product_id,name")
        self.assertEqual(lines[1], "FOODMART-0214,SKU-00000000007,7,FoodMart Item 0214")
        self.assertEqual(fm.read_mapping(path), MAPPING)

    def test_codes_in_season_order_follow_the_foodmart_code_not_the_server_sku(self):
        shuffled = {
            "FOODMART-0002": fm.Mapped("FOODMART-0002", "SKU-00000000009", 9, "FoodMart Item 0002"),
            "FOODMART-0001": fm.Mapped("FOODMART-0001", "SKU-00000000010", 10, "FoodMart Item 0001"),
            "FOODMART-0003": fm.Mapped("FOODMART-0003", "SKU-00000000001", 1, "FoodMart Item 0003"),
        }
        self.assertEqual(fm.ordered_codes(shuffled), ["FOODMART-0001", "FOODMART-0002", "FOODMART-0003"])


if __name__ == "__main__":
    unittest.main()

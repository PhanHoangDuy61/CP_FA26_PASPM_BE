"""Unit tests for the demo co-purchase baskets (no backend, no network).

They prove, from the data files alone, the facts the demo relies on: the committed CSV is exactly what the generator
produces, it is well formed, the base files are untouched, the groups are disjoint, sellable and sized for BUNDLE (2..4),
and the F06 policy arithmetic (FBT: support >= 2 and >= 5% of the baskets; CROSS_SELL: confidence >= 0.50) holds on the
full range and on every single day. Whether EFIM then outputs the patterns is verified on a real stack (ROC-20261127-002).

Run from the repository root:  python3 -I -m unittest discover -s scripts/seed/tests -v
"""
import csv
import hashlib
import re
import sys
import unittest
from collections import Counter, defaultdict
from datetime import date, datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(HERE))  # python -I does not add the script directory

import generate_copurchase_baskets as gen  # noqa: E402
import weight_v2_plan as plan  # noqa: E402

OUT = HERE / "data" / gen.OUT_NAME
MATRIX_ROW_LIMIT = 20_000      # F05: at most 20,000 matrix rows per snapshot
MIN_SUPPORT_COUNT, MIN_SUPPORT_RATIO, MIN_CONFIDENCE = 2, 0.05, 0.50   # default F06 policy F06_RULES_V1_1
BASE_SHA256 = {
    "Transactions_F04_Import_Test_Part1.csv": "5e45ffbdccd4947a1e4c2ee2528e6ae58e8b7364b999288c268d3dcc39b2a8ff",
    "Transactions_F04_Import_Test_Part2.csv": "7357315a4d485901a5f2344d8d39eb3b2d16514e0e33880df39deecaca07c46f",
}


def read_new():
    with OUT.open(encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f))


def baskets_by_day():
    """day -> list of sets of FoodMart codes, base orders and co-purchase orders together."""
    days = defaultdict(list)
    for occurred_at, skus in gen.read_base_orders().values():
        days[occurred_at[:10]].append(set(skus))
    orders = defaultdict(lambda: [None, set()])
    for row in read_new():
        orders[row["source_order_id"]][0] = row["occurred_at"][:10]
        orders[row["source_order_id"]][1].add(row["sku"])
    for day, skus in orders.values():
        days[day].append(skus)
    return days


def support(baskets, products):
    return sum(1 for basket in baskets if set(products) <= basket)


class GeneratorTest(unittest.TestCase):
    def test_committed_file_is_what_the_generator_produces(self):
        self.assertEqual(OUT.read_bytes().decode("utf-8"), gen.render(gen.generate()))

    def test_generation_is_deterministic(self):
        self.assertEqual(gen.generate(), gen.generate())

    def test_base_files_are_not_modified(self):
        for name, digest in BASE_SHA256.items():
            self.assertEqual(hashlib.sha256((HERE / "data" / name).read_bytes()).hexdigest(), digest, name)


class FileFormatTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows = read_new()
        cls.catalog = gen.read_catalog()
        cls.base = gen.read_base_orders()
        cls.windows = gen.day_windows(cls.base)

    def test_header_line_endings_and_status(self):
        raw = OUT.read_bytes()
        self.assertTrue(raw.startswith((gen.HEADER + "\r\n").encode()))
        self.assertTrue(raw.endswith(b"\r\n"))
        self.assertNotIn(b'"', raw)  # quoted fields are not supported by the seed's CSV copy
        self.assertEqual({row["status"] for row in self.rows}, {"COMPLETED"})

    def test_order_ids_are_new_and_sequential(self):
        ids = list(dict.fromkeys(row["source_order_id"] for row in self.rows))
        self.assertEqual(ids, [f"{gen.ID_PREFIX}{n:06d}" for n in range(1, len(ids) + 1)])
        self.assertFalse(set(ids) & set(self.base))

    def test_every_line_uses_the_catalogue_price_and_a_valid_quantity(self):
        for row in self.rows:
            price, cost, status = self.catalog[row["sku"]]
            self.assertEqual((row["unit_price"], row["unit_cost"], status), (price, cost, "ACTIVE"), row)
            self.assertTrue(1 <= int(row["quantity"]) <= 4, row)

    def test_one_timestamp_per_order_inside_the_base_data_window_of_its_day(self):
        stamps = defaultdict(set)
        for row in self.rows:
            stamps[row["source_order_id"]].add(row["occurred_at"])
        for order, values in stamps.items():
            self.assertEqual(len(values), 1, order)
            text = next(iter(values))
            self.assertTrue(text.endswith("+07:00"), text)
            moment = datetime.strptime(text[:19], "%Y-%m-%dT%H:%M:%S")
            low, high = self.windows[moment.date()]
            self.assertTrue(low <= moment <= high, text)

    def test_a_product_appears_once_per_order(self):
        seen = Counter((row["source_order_id"], row["sku"]) for row in self.rows)
        self.assertEqual([key for key, count in seen.items() if count > 1], [])

    def test_seed_imports_the_file_after_the_two_base_files(self):
        source = (HERE / "seed_demo.py").read_text(encoding="utf-8")
        names = re.search(r"CSV_FILES = \[(.*?)\]", source, re.S).group(1)
        self.assertEqual(re.findall(r'"(Transactions_[^"]+\.csv)"', names),
                         [*BASE_SHA256, gen.OUT_NAME])


class GroupTest(unittest.TestCase):
    def test_groups_are_disjoint_and_bundle_sized(self):
        members = [code for spec in gen.GROUPS.values() for code in spec["codes"]]
        self.assertEqual(len(members), len(set(members)), "a product must not be in two groups")
        for label, spec in gen.GROUPS.items():
            self.assertTrue(2 <= len(spec["codes"]) <= 4, label)  # BUNDLE needs 2..maxBundleSize (4)
        self.assertEqual(sorted(len(spec["codes"]) for spec in gen.GROUPS.values()), [2, 2, 3, 4])

    def test_group_products_exist_are_active_and_not_perishable(self):
        catalog = gen.read_catalog()
        config = plan.config_from_env({}, date(2026, 11, 27))
        for spec in gen.GROUPS.values():
            for code in spec["codes"]:
                self.assertEqual(catalog[code][2], "ACTIVE", code)
                self.assertFalse(plan.plan_product(config, code).requires_expiry, code + " would be perishable")


class F06PolicyArithmeticTest(unittest.TestCase):
    """The F06 rules of RecommendationRules, applied to the baskets of the files (exact counts, like the backend)."""

    @classmethod
    def setUpClass(cls):
        cls.days = baskets_by_day()
        cls.full = [basket for baskets in cls.days.values() for basket in baskets]

    def fbt_passes(self, baskets, products):
        n, s = len(baskets), support(baskets, products)
        return s >= MIN_SUPPORT_COUNT and s >= MIN_SUPPORT_RATIO * n

    def test_frequent_groups_pass_fbt_on_the_full_range_and_on_every_day(self):
        for label, spec in gen.GROUPS.items():
            if not label.startswith("F"):
                continue
            self.assertTrue(self.fbt_passes(self.full, spec["codes"]), label + " full range")
            for day, baskets in self.days.items():
                self.assertTrue(self.fbt_passes(baskets, spec["codes"]), f"{label} {day}")

    def test_occasional_groups_stay_below_the_fbt_ratio(self):
        for label, spec in gen.GROUPS.items():
            if label.startswith("O"):
                self.assertFalse(self.fbt_passes(self.full, spec["codes"]), label)

    def test_every_group_passes_cross_sell_for_every_anchor(self):
        for label, spec in gen.GROUPS.items():
            together = support(self.full, spec["codes"])
            self.assertGreater(together, 0, label)
            for anchor in spec["codes"]:
                self.assertGreaterEqual(together / support(self.full, [anchor]), MIN_CONFIDENCE, f"{label} {anchor}")

    def test_total_matrix_rows_stay_under_the_f05_limit(self):
        base_rows = sum(len(skus) for _, skus in gen.read_base_orders().values())
        total = base_rows + len(read_new())
        self.assertLessEqual(total, MATRIX_ROW_LIMIT)
        self.assertGreaterEqual(MATRIX_ROW_LIMIT - total, 50, "keep a little headroom below 20,000 rows")


if __name__ == "__main__":
    unittest.main()

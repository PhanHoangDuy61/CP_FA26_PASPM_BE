"""Unit tests for the deterministic Weight v2 demo plan (no backend, no network).

Run from the repository root:  python3 -I -m unittest discover -s scripts/seed/tests -v
"""
import sys
import unittest
from datetime import date, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))  # python -I does not add the script directory

import weight_v2_plan as plan  # noqa: E402

AS_OF = date(2026, 11, 17)
SKUS = [f"FOODMART-{n:04d}" for n in range(1, 1560)]


def cfg(**env):
    return plan.config_from_env(env, today=AS_OF)


def all_plans(config):
    return [plan.plan_product(config, sku) for sku in SKUS]


class DeterminismTest(unittest.TestCase):
    def test_same_seed_and_as_of_give_identical_plans(self):
        self.assertEqual(all_plans(cfg()), all_plans(cfg()))

    def test_a_different_random_seed_changes_the_plans(self):
        self.assertNotEqual(all_plans(cfg()), all_plans(cfg(SEED_RANDOM_SEED="another")))

    def test_a_product_plan_does_not_depend_on_the_other_products(self):
        config = cfg()
        full = all_plans(config)
        self.assertEqual(full[700], plan.plan_product(config, SKUS[700]))

    def test_dates_move_with_as_of_and_keep_their_offsets(self):
        base, later = cfg(), cfg(SEED_AS_OF="2026-12-01")
        shift = timedelta(days=14)
        for a, b in zip(all_plans(base), all_plans(later)):
            self.assertEqual(a.requires_expiry, b.requires_expiry)
            if a.expiry_date:
                self.assertEqual(a.expiry_date + shift, b.expiry_date)
            if a.push_day:
                self.assertEqual(a.push_day + shift, b.push_day)


class ExpiryDistributionTest(unittest.TestCase):
    def setUp(self):
        self.config = cfg()
        self.plans = all_plans(self.config)
        self.perishable = [p for p in self.plans if p.requires_expiry]

    def test_default_share_of_products_requiring_expiry(self):
        share = len(self.perishable) / len(self.plans)
        self.assertAlmostEqual(share, 0.35, delta=0.03)

    def test_products_without_expiry_have_no_dates(self):
        for p in self.plans:
            if not p.requires_expiry:
                self.assertIsNone(p.expiry_date)
                self.assertIsNone(p.push_day)

    def test_every_perishable_batch_has_an_expiry_date_on_or_after_as_of(self):
        for p in self.perishable:
            self.assertIsNotNone(p.expiry_date)
            self.assertGreaterEqual(p.expiry_date, AS_OF)
            self.assertLessEqual(p.expiry_date, AS_OF + timedelta(days=180))

    def test_expiry_buckets_follow_the_configured_percentages(self):
        days = [(p.expiry_date - AS_OF).days for p in self.perishable]
        n = len(days)
        soon = sum(d <= 7 for d in days) / n
        mid = sum(8 <= d <= 30 for d in days) / n
        late = sum(d >= 31 for d in days) / n
        self.assertAlmostEqual(soon, 0.15, delta=0.04)
        self.assertAlmostEqual(mid, 0.25, delta=0.05)
        self.assertAlmostEqual(late, 0.60, delta=0.06)

    def test_bucket_percentages_are_configurable(self):
        config = cfg(SEED_EXPIRY_SOON_PCT="50", SEED_EXPIRY_MID_PCT="0")
        days = [(p.expiry_date - AS_OF).days for p in all_plans(config) if p.requires_expiry]
        self.assertAlmostEqual(sum(d <= 7 for d in days) / len(days), 0.5, delta=0.06)
        self.assertEqual(sum(8 <= d <= 30 for d in days), 0)

    def test_batch_quantity_stays_between_the_minimum_and_the_configured_maximum(self):
        for p in self.perishable:
            self.assertGreaterEqual(p.quantity, 20)
            self.assertLessEqual(p.quantity, 1000)
        self.assertGreater(len({p.quantity for p in self.perishable}), 50, "quantities should vary")

    def test_non_perishable_products_keep_the_full_configured_quantity(self):
        for p in self.plans:
            if not p.requires_expiry:
                self.assertEqual(p.quantity, 1000)


class ForcedExpiryTest(unittest.TestCase):
    def test_a_product_already_flagged_requires_expiry_always_gets_dates(self):
        config = cfg(SEED_EXPIRY_PRODUCT_PCT="0")
        forced = plan.plan_product(config, SKUS[0], force_expiry=True)
        self.assertTrue(forced.requires_expiry)
        self.assertIsNotNone(forced.expiry_date)
        self.assertFalse(plan.plan_product(config, SKUS[0]).requires_expiry)

    def test_forcing_does_not_change_a_product_that_is_already_perishable(self):
        config = cfg()
        perishable = next(p for p in all_plans(config) if p.requires_expiry)
        self.assertEqual(perishable, plan.plan_product(config, perishable.sku, force_expiry=True))


class PushDayTest(unittest.TestCase):
    def setUp(self):
        self.plans = [p for p in all_plans(cfg()) if p.requires_expiry]

    def test_push_day_never_after_expiry(self):
        for p in self.plans:
            if p.push_day:
                self.assertLessEqual(p.push_day, p.expiry_date)

    def test_about_five_percent_have_no_push_day(self):
        missing = sum(p.push_day is None for p in self.plans) / len(self.plans)
        self.assertAlmostEqual(missing, 0.05, delta=0.03)

    def test_most_batches_push_seven_to_fourteen_days_before_expiry(self):
        standard = [p for p in self.plans if p.push_kind == "STANDARD"]
        self.assertGreater(len(standard) / len(self.plans), 0.85)
        for p in standard:
            self.assertIn((p.expiry_date - p.push_day).days, range(7, 15))

    def test_a_few_batches_have_a_push_day_that_is_already_reached(self):
        reached = [p for p in self.plans if p.push_kind == "REACHED"]
        self.assertGreater(len(reached), 0)
        for p in reached:
            self.assertLessEqual(p.push_day, AS_OF)

    def test_the_missing_push_day_switch_can_be_set_to_zero(self):
        config = cfg(SEED_PUSH_MISSING_PCT="0")
        self.assertTrue(all(p.push_day for p in all_plans(config) if p.requires_expiry))


class SeasonTest(unittest.TestCase):
    def setUp(self):
        self.config = cfg()
        self.seasons = {s.state: s for s in plan.season_plans(self.config)}

    def test_one_season_per_state_plus_an_overlapping_active_one(self):
        states = [s.state for s in plan.season_plans(self.config)]
        self.assertEqual(sorted(states), ["ACTIVE", "ACTIVE_OVERLAP", "ENDED", "FUTURE"])

    def test_active_future_and_ended_dates_are_relative_to_as_of(self):
        for key in ("ACTIVE", "ACTIVE_OVERLAP"):
            self.assertLessEqual(self.seasons[key].start, AS_OF)
            self.assertGreaterEqual(self.seasons[key].end, AS_OF)
        self.assertGreater(self.seasons["FUTURE"].start, AS_OF)
        self.assertLess(self.seasons["ENDED"].end, AS_OF)

    def test_the_future_season_is_tet_2027_and_precedes_the_lunar_new_year_for_the_default_date(self):
        future = self.seasons["FUTURE"]
        self.assertEqual(future.name, "Demo: T\u1ebft 2027")
        self.assertEqual((future.start, future.end), (date(2027, 1, 11), date(2027, 2, 10)))

    def test_default_weights(self):
        self.assertEqual(str(self.seasons["ACTIVE"].weight), "0.7")
        self.assertEqual(str(self.seasons["FUTURE"].weight), "0.9")
        self.assertEqual(str(self.seasons["ENDED"].weight), "0.6")
        self.assertGreater(self.seasons["ACTIVE_OVERLAP"].weight, self.seasons["ACTIVE"].weight)

    def test_season_names_are_unique_ignoring_case(self):
        names = [s.name.lower() for s in plan.season_plans(self.config)]
        self.assertEqual(len(names), len(set(names)))

    def test_some_products_belong_to_two_active_seasons(self):
        both = [i for i in range(len(SKUS))
                if {"ACTIVE", "ACTIVE_OVERLAP"} <= set(plan.season_states_for(self.config, i, len(SKUS)))]
        self.assertGreaterEqual(len(both), 20)
        self.assertLess(len(both), 120)

    def test_every_season_has_members_and_membership_is_by_position(self):
        for state in ("ACTIVE", "ACTIVE_OVERLAP", "FUTURE", "ENDED"):
            members = [i for i in range(len(SKUS)) if state in plan.season_states_for(self.config, i, len(SKUS))]
            self.assertGreater(len(members), 100, state)
        self.assertEqual(plan.season_states_for(self.config, 0, len(SKUS)), ["ACTIVE"])

    def test_some_products_have_no_season(self):
        none = [i for i in range(len(SKUS)) if not plan.season_states_for(self.config, i, len(SKUS))]
        self.assertGreater(len(none), 100)


class ConfigTest(unittest.TestCase):
    def test_defaults(self):
        c = cfg()
        self.assertEqual(c.as_of, AS_OF)
        self.assertTrue(c.enabled)
        self.assertEqual((str(c.alpha), str(c.beta), str(c.lam), c.sales_window_days), ("0.6", "0.4", "0.3", 30))

    def test_as_of_and_switch_come_from_the_environment(self):
        c = cfg(SEED_AS_OF="2026-10-05", SEED_WEIGHT_V2="0")
        self.assertEqual(c.as_of, date(2026, 10, 5))
        self.assertFalse(c.enabled)

    def test_invalid_values_are_rejected(self):
        for bad in ({"SEED_AS_OF": "17/11/2026"}, {"SEED_WEIGHT_ALPHA": "0.7"}, {"SEED_EXPIRY_SOON_PCT": "80", "SEED_EXPIRY_MID_PCT": "30"},
                    {"SEED_PUSH_REACHED_PCT": "60", "SEED_PUSH_MISSING_PCT": "50"}, {"SEED_EXPIRY_PRODUCT_PCT": "101"},
                    {"SEED_WEIGHT_SALES_WINDOW_DAYS": "400"}, {"SEED_WEIGHT_LAMBDA": "-0.1"}):
            with self.assertRaises(plan.PlanConfigError, msg=str(bad)):
                cfg(**bad)


if __name__ == "__main__":
    unittest.main()

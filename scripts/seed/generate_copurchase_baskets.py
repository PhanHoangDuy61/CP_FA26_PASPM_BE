#!/usr/bin/env python3
"""Generate data/Transactions_F04_Demo_Copurchase.csv: the co-purchase baskets of the demo seed (no backend needed).

Why this file exists. The base transaction files (Part1, Part2) are random baskets: no pair of products is bought
together more often than chance, so EFIM at the documented demo policy (`PERCENTAGE 0.12`, full range 2026-10-01..04)
returns only single-product patterns and F06 can build nothing but BANNER candidates. F06 needs real co-purchase to
produce BUNDLE, CROSS_SELL and FBT candidates (RecommendationRules, default policy F06_RULES_V1_1):
  * BUNDLE:     a pattern of 2..4 products (no transaction evidence needed);
  * FBT:        the whole pattern is in at least 2 baskets AND in at least 5% of all baskets of the snapshot;
  * CROSS_SELL: per anchor product, (baskets with the whole pattern) / (baskets with the anchor) >= 0.50.

What it creates (four disjoint product groups, so a product is never in two groups):
  * two FREQUENT groups, a pair and a triple, each in about 5.5% of all orders: they pass the 5% FBT rule, and give
    BUNDLE, FBT and CROSS_SELL candidates. About 5% of their orders miss one product, so CROSS_SELL confidence is about
    0.95 and not 1;
  * two OCCASIONAL groups, a quad (the largest allowed bundle) and a pair, each in under 1% of the orders: realistic bulk
    purchases that give BUNDLE and CROSS_SELL candidates but, correctly, no FBT (below 5%).
The matrix of a snapshot may hold at most 20,000 rows (a line per product and order) and the base data already has
18,319 in the full range, so the baskets contain only the group products (no filler) and the test checks the total.
Orders are spread over the four days in proportion to the base data, so the FBT ratio also holds on every single day.
They use their own ids (FMCOP-NNNNNN), the catalogue prices and the same time window per day as the base data. Group
products are non-perishable (so they stay sellable) and rarely appear in the base orders (so confidence stays high).
Everything is deterministic: same input files, same output.

Usage (repository root):
  python3 -I scripts/seed/generate_copurchase_baskets.py            # write the file
  python3 -I scripts/seed/generate_copurchase_baskets.py --check    # fail when the committed file differs
Standard library only.
"""
import csv
import math
import random
import re
import sys
from collections import Counter
from datetime import datetime, timedelta
from pathlib import Path

HERE = Path(__file__).resolve().parent

DATA = HERE / "data"
BASE_FILES = ["Transactions_F04_Import_Test_Part1.csv", "Transactions_F04_Import_Test_Part2.csv"]
PRODUCT_SQL = "FoodMart_Product_Direct_Insert.sql"
OUT_NAME = "Transactions_F04_Demo_Copurchase.csv"
HEADER = "source_order_id,occurred_at,sku,quantity,unit_price,unit_cost,status"
ID_PREFIX = "FMCOP-"
RANDOM_SEED = "paspm-demo-copurchase-v2"

# Disjoint product groups. Chosen once, deterministically: non-perishable under the default Weight v2 plan, ACTIVE,
# unit price 1,900-3,000 and at most 10 lines in the base files (the first 11 by a SHA-256 order of the code).
# orders_pct: share of the base order count of each day (rounded up); full_share: share of the group's orders that
# contain every product (the others miss one); quantity: units per product and order.
GROUPS = {
    "F1-frequent-pair": dict(codes=["FOODMART-0212", "FOODMART-1219"], orders_pct=6.7, full_share=0.95, quantity=(1, 3)),
    "F2-frequent-triple": dict(codes=["FOODMART-1557", "FOODMART-1513", "FOODMART-0750"], orders_pct=6.7, full_share=0.95, quantity=(1, 3)),
    "O1-occasional-quad": dict(codes=["FOODMART-0041", "FOODMART-0092", "FOODMART-0551", "FOODMART-0151"], orders_pct=1.0, full_share=1.0, quantity=(2, 4)),
    "O2-occasional-pair": dict(codes=["FOODMART-1342", "FOODMART-1525"], orders_pct=0.7, full_share=1.0, quantity=(2, 4)),
}
ROW = re.compile(r"^\((\d+),\s*(\d+),\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*([01]),\s*([\d.]+),\s*([\d.]+),\s*'(ACTIVE|INACTIVE)'\)[,;]?\s*$")


def read_catalog(data_dir=DATA):
    """code -> (unit_price string, unit_cost string, status), exactly as in the SQL file."""
    catalog = {}
    for line in (Path(data_dir) / PRODUCT_SQL).read_text(encoding="utf-8").splitlines():
        m = ROW.match(line.strip())
        if m:
            catalog[m.group(3)] = (m.group(7), m.group(8), m.group(9))
    return catalog


def read_base_orders(data_dir=DATA):
    """Base orders as {source_order_id: (occurred_at, [sku, ...])} in file order."""
    orders = {}
    for name in BASE_FILES:
        with (Path(data_dir) / name).open(encoding="utf-8", newline="") as f:
            for row in csv.DictReader(f):
                orders.setdefault(row["source_order_id"], (row["occurred_at"], []))[1].append(row["sku"])
    return orders


def day_windows(base_orders):
    """day -> (first minute, last minute) of the base orders, as datetimes with the +07:00 offset dropped."""
    windows = {}
    for occurred_at, _ in base_orders.values():
        moment = datetime.strptime(occurred_at[:19], "%Y-%m-%dT%H:%M:%S")
        low, high = windows.get(moment.date(), (moment, moment))
        windows[moment.date()] = (min(low, moment), max(high, moment))
    return windows


def per_day_counts(base_orders):
    return Counter(datetime.strptime(o[:19], "%Y-%m-%dT%H:%M:%S").date() for o, _ in base_orders.values())


def generate(data_dir=DATA):
    """The lines of the output file (header first), without line endings."""
    catalog = read_catalog(data_dir)
    base = read_base_orders(data_dir)
    windows, counts = day_windows(base), per_day_counts(base)
    rng = random.Random(RANDOM_SEED)
    orders = []  # (moment, [(sku, quantity)])
    for day in sorted(counts):
        low, high = windows[day]
        minutes = int((high - low).total_seconds() // 60)
        for spec in GROUPS.values():
            members = spec["codes"]
            for _ in range(math.ceil(counts[day] * spec["orders_pct"] / 100)):
                moment = low + timedelta(minutes=rng.randint(0, minutes), seconds=rng.randint(1, 59))
                if moment > high:  # the last minute of a day keeps the base data's end (10-04 ends at 06:00:00)
                    moment = high
                chosen = list(members)
                if rng.random() >= spec["full_share"]:
                    chosen.remove(rng.choice(members))  # one product is missing from this order
                orders.append((moment, [(sku, rng.randint(*spec["quantity"])) for sku in chosen]))
    orders.sort(key=lambda order: order[0])
    lines = [HEADER]
    for number, (moment, basket) in enumerate(orders, start=1):
        stamp = moment.strftime("%Y-%m-%dT%H:%M:%S") + "+07:00"
        for sku, quantity in sorted(basket):
            price, cost, _ = catalog[sku]
            lines.append(f"{ID_PREFIX}{number:06d},{stamp},{sku},{quantity},{price},{cost},COMPLETED")
    return lines


def render(lines):
    return "\r\n".join(lines) + "\r\n"  # CRLF and a final newline, like the base files


def main(argv):
    text = render(generate())
    target = DATA / OUT_NAME
    if "--check" in argv:
        if not target.exists() or target.read_bytes().decode("utf-8") != text:
            sys.exit(f"ERROR: {target} differs from the generated output; run the generator without --check")
        print(f"OK: {target.name} matches the generator ({text.count(chr(10)) - 1} rows)")
        return
    target.write_bytes(text.encode("utf-8"))
    rows = text.count("\n") - 1
    orders = len({line.split(",", 1)[0] for line in text.splitlines()[1:]})
    print(f"wrote {target} ({rows} rows, {orders} orders)")


if __name__ == "__main__":
    main(sys.argv[1:])

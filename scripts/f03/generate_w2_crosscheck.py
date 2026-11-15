"""Regenerates src/test/resources/f03/w2_crosscheck.json from paspm-experiments (reference implementation).

Usage (from BE/):  python3 -I scripts/f03/generate_w2_crosscheck.py <path-to-paspm-experiments>
The Java test WeightV2CrossCheckTest must reproduce every value exactly.
"""
import json, sys
from decimal import Decimal
from fractions import Fraction
from pathlib import Path

root = Path(sys.argv[1]).resolve()
sys.path.insert(0, str(root))
from src.expiry import Batch, u_expiry          # noqa: E402
from src.weighting import compute_weight        # noqa: E402

N = 30
CASES = [
    # name, lambda, alpha, beta, units sold in N days, batches [(id, qty, days_to_push)], M
    ("golden_single_batch", "0.5", "0.6", "0.4", 750, [(1, 120, 3)], "0"),
    ("golden_two_batches_reverse", "0.5", "0.6", "0.4", 450, [(2, 60, 5), (1, 40, 2)], "0.9"),
    ("half_up_1_025", "0.25", "1", "0", 0, [], "0"),
    ("lambda_zero", "0", "0.5", "0.5", 10, [(1, 9, 1)], "1"),
    ("max_weight", "0.5", "0.5", "0.5", 1, [(1, 50, 1)], "1"),
    ("report4_example", "0.23", "0.5", "0.5", 0, [], "1"),
    ("thirds", "0.3", "0.7", "0.3", 31, [(7, 10, 4), (3, 25, 4), (12, 8, 9)], "0.2"),
    ("push_day_passed", "0.4", "0.6", "0.4", 300, [(5, 30, 0), (6, 20, -2)], "0.5"),
    ("tie_by_integer_id", "0.35", "0.55", "0.45", 61, [(10, 40, 2), (9, 40, 2)], "0.75"),
]
out = []
for name, lam, alpha, beta, units, batches, m in CASES:
    rate = Fraction(units, N)
    u = u_expiry([Batch(f"{bid:012d}", qty, days) for bid, qty, days in batches], rate)
    if name == "half_up_1_025":
        u = Fraction(1, 10)  # direct weight check from tests/test_fixture_small.py
    w = compute_weight(Decimal(lam), Decimal(alpha), Decimal(beta), u, Decimal(m))
    out.append({"name": name, "lambda": lam, "alpha": alpha, "beta": beta, "salesWindowDays": N, "unitsSold": units,
                "batches": [{"batchId": b, "quantity": q, "daysToPush": d} for b, q, d in batches], "seasonWeight": m,
                "uExpiryNumerator": u.numerator, "uExpiryDenominator": u.denominator, "weight": str(w)})
target = Path(__file__).resolve().parents[2] / "src/test/resources/f03/w2_crosscheck.json"
target.write_text(json.dumps({"source": "paspm-experiments src/expiry.py + src/weighting.py", "cases": out}, indent=2) + "\n")
print(target, len(out))

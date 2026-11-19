# DEC-20261119-05 — SPMF input falls back to whole đồng (HALF_UP) only on int32 overflow

| Field | Value |
|---|---|
| Date | 2026-11-19 (Asia/Ho_Chi_Minh) |
| Source | Owner decision of 2026-11-19 ("int32, option B"); implemented on branch `Khoi/feat/f05-spmf-whole-dong-fallback`, record [ROC-20261119-003](../../record-of-changes/2026-11-19/03-f05-spmf-whole-dong-fallback.md) |
| Status | OWNER_DECISION — implemented on a feature branch; PR to `dev` |
| Amends | [DEC-20261117-01](../2026-11-17/DEC-20261117-01-efim-aware-lossless-int32-guard.md) ("quantization not approved"), for the SPMF input only |

## Decision
- Frozen F03 snapshots stay **lossless** (scale 6). Nothing stored in a snapshot is rounded.
- The SPMF input is built exactly as before (scale-6/GCD, `F03_SCALE6_GCD_INT32_DENSE_V1`). If, and only if, it fails an EFIM int32 bound (transaction utility, Product TWU or converted minUtil), a **whole-unit** input is built: each item rounded to **1 đồng** `HALF_UP`, TU/TWU/total recomputed from the rounded items, `ABSOLUTE` minUtil `ceil(value)`, `PERCENTAGE` minUtil `ceil(pct × roundedTotal / 100)`, and the guard applied again.
- If the whole-unit input still overflows, the request is rejected with HTTP 400 naming `WHOLE_UNIT_HALF_UP`. Coarser units (for example thousands of đồng) are **not** approved and need a new decision.
- The request records the mode in its frozen input (`conversionVersion: F03_WHOLE_UNIT_HALF_UP_INT32_DENSE_V1`, `conversionMode: WHOLE_UNIT_HALF_UP`, `roundingUnit: 1`). Readers dispatch on `conversionVersion`; historical requests are read unchanged.

## Reason
Realistic VND-scale data must be mineable. Under the lossless conversion the GCD can be small (sub-đồng prices and decimal weights), so the normalized values overflow EFIM's int32 accumulators although the đồng amounts themselves fit. Rounding to 1 đồng only for SPMF removes that artificial precision while the snapshot, F06 evidence and reports stay exact.

## Measured evidence (2026-11-19, disposable MySQL 8.4, real SPMF 2.67a on Java 25)
| Dataset | Lossless input | Result now |
|---|---|---|
| Realistic-utility dense fixture (synthetic, 4,100 orders / 18,040 rows / 1,559 products) | divisor 100; max TU 26,225,721; max Product TWU 3,217,593,703 (> int32) → was 400 | **Accepted, `WHOLE_UNIT_HALF_UP`**: divisor 1, max TU 2,623, max Product TWU 321,751, total 5,325,436. `PERCENTAGE 1` (minUtil 53,255 đồng): COMPLETED, EFIM 291 ms, 3 patterns, F06 7 recommendations. `ABSOLUTE 500`: OUTPUT_LIMIT (> 2,000 patterns) |
| Demo seed, full range 2026-10-01..04, as of 2026-10-05 | divisor 10,000; bounds fit | Stays **`LOSSLESS`**. `PERCENTAGE 0.12` (minUtil 62,342.13 đồng): COMPLETED, 180 patterns, EFIM 385 ms |
| Same demo matrix, forced whole-đồng (test-only) | — | 0.12 %: 180 vs 180 patterns, all shared, top-50 identical in order. 0.08 %: 1,147 vs 1,147, all shared, top-50 identical |

The realistic fixture's prices are 0.50–200.00 units, not VND; for it the 0.5-đồng rounding bound is about 0.2 % of an average item (≈ 295 units). For VND amounts the relative error is far smaller.

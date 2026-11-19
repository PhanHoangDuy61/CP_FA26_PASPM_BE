# DEC-20261117-01 — Lossless EFIM-aware int32 guard; quantization not approved

**Status:** OWNER DECISION — implemented and locally verified on `Khoi/feat/f05-efim-aware-int32-guard`; committed locally, not published.
**Date:** 2026-11-17 / Asia/Ho_Chi_Minh.

## Decision

Keep F05's scale-6/GCD conversion exact. Remove the rule that requires the global normalized dataset utility to fit signed int32. Instead, reject a new request only when an EFIM-required signed-int32 quantity exceeds its range: an individual transaction utility, a Product transaction-weighted utility (TWU), or the positive converted `minUtil`. Keep all existing size and runtime protections. Quantization / cent rounding is not approved.

Represent global normalized totals and percentage-threshold arithmetic as checked signed 64-bit values. The public `RequestView.totalScaledUtility` schema is `integer/int64`. F06 reads and validates the same exact frozen total with signed-64-bit arithmetic. Preserve the frozen JSON property name, conversion version and bytes for legacy values. No schema column stores this aggregate and no migration or historical rewrite is needed.

## Technical basis

The inspected pinned SPMF 2.67a EFIM implementation parses TU and item utility as `int`; `Transaction` holds TU, prefix utility and item utility in int fields/arrays. `AlgoEFIM` uses int `minUtil`, int `utilityBinArrayLU` and `utilityBinArraySU`, int projected utility state and int pattern utility. EFIM does not accumulate one global database-utility total. For PASPM's nonnegative values, transaction utility is bounded by input TU; a Product's TWU is the sum of the TU for transactions containing it; projected sums and a candidate's local/subtree/pattern utility are bounded by that candidate Product TWU. The builder therefore checks each TU, accumulates each Product TWU in checked `long` before checking int32, and keeps `minUtil` positive int32. Signed-64-bit overflow is still rejected.

## Consequences and limits

- The 1,600-product, 20,000-matrix-row, timeout, child-heap, pattern-count, output-size and process-log bounds are unchanged.
- A global total can exceed `Integer.MAX_VALUE` if every EFIM accumulator remains in range. Exact numeric support does not mean every large fixture is admissible: an item TWU over int32 still fails.
- `totalScaledUtility` is a non-breaking JSON numeric widening for the documented bound: at most 20,000 transaction rows each with int32 TU gives at most 42,949,672,940,000, below JavaScript's `2^53` exact-integer limit. Clients must still avoid int32 assumptions.
- No new scoring/conversion version is needed. Scale, GCD, frozen matrix contents, JSON property names and exact numeric meaning do not change. F06's version-aware reader accepts old int-sized totals and validates new long totals exactly.
- Demo seed full-range snapshot passes the actual EFIM bounds, but its realistic 4,100-order fixture has max Product TWU 3,217,593,703 and remains correctly rejected before request persistence. Authentic FoodMart F03 evidence is still unavailable.

## Alternatives not selected

The old global-total int32 guard was safe but unnecessarily strict for EFIM. Fixed-cent conversion would be lossy, can change threshold membership, and would require versioned evidence/error policy; it is not approved. No production scale limit, dataset size bound or resource cap is raised by this decision.

## Verification reference

Implementation, test evidence, exact demo metrics, OpenAPI widening, no-migration check and limitations are recorded in [ROC-20261117-015](../../record-of-changes/2026-11-17/15-f05-efim-aware-int32-guard.md) and the [F05 scalability benchmark addendum](../../testing/F05_FOODMART_SCALABILITY_BENCHMARK.md#efim-aware-exact-int32-guard--2026-11-17).

> **Superseded in part by [DEC-20261119-05](../2026-11-19/DEC-20261119-05-spmf-whole-dong-fallback.md) (2026-11-19):** the SPMF input may fall back to whole-đồng HALF_UP rounding when the exact input overflows int32; snapshots stay lossless.

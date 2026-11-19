# F08 layout-size sensitivity (RQ3 evidence) — 2026-11-19

Simulated offline replay only (`simulated: true`); not a real-user A/B test and not a forecast. Decision: [DEC-20261119-06](../decisions/2026-11-19/DEC-20261119-06-layout-caps-topk-baseline-equal-size.md). Record: [ROC-20261119-004](../record-of-changes/2026-11-19/04-f07-zone-caps-baseline-topk.md).

## Setup

| Item | Value |
|---|---|
| Code | Branch `Khoi/feat/f07-zone-caps-baseline-topk` (backend JAR built from commit `24ab62d`), `OFFLINE_REPLAY_V2` equal-size comparison |
| Environment | Disposable MySQL 8.4.13 container and Mailpit container on loopback; Java 17.0.21 backend; SPMF 2.67a on Java 25 |
| Data | Demo seed (`scripts/seed`, `SEED_AS_OF=2026-11-19`): 1,559 products, 4,141 orders dated 2026-10-01..04 |
| Snapshot | One-day snapshot 2026-10-02, as of 2026-10-05 (same for every size) |
| Mining | EFIM, `PERCENTAGE 0.25`, `LOSSLESS` input: 210 patterns, 181 ms |
| Recommendations | F06 generated 377 candidates (210 `BANNER`, 167 `CROSS_SELL`; no `BUNDLE` or `FBT` at this threshold); all 377 were ELIGIBLE and approved. Same pool for every size |
| Evaluation window | 2026-10-03..2026-10-04 (store-local), 1,801 completed F03-eligible transactions outside the snapshot; dataset hash identical for every size |
| Size S | Every zone cap overridden to S through `PASPM_LAYOUT_MAX_{BLOCKS,PRODUCTS}_<ZONE>=S`, so baseline A holds `K = S` products and B may hold up to S products |
| B selection | Approved recommendations in order of utility (descending), then id; a block is skipped if it shares a product with a chosen block or would exceed S; stop at exactly S distinct products |

## Results

| Size S | N (B products) | \|A\| = K | A products compared | hitRate A | hitRate B | capturedUtility A | capturedUtility B | bundleConversion B | Score B | Score A (top N) | Score A (full) |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 20 | 20 | 20 | 20 | 0.058856 | 0.053859 | 0.016795 | 0.018136 | 0.000000 | 507,688.55 | 622,307.90 | 622,307.90 |
| 35 | **21** | 35 | 21 | 0.059967 | 0.055525 | 0.017131 | 0.019189 | 0.000000 | 552,598.55 | 660,634.23 | 963,117.01 |
| 50 | **21** | 50 | 21 | 0.059967 | 0.055525 | 0.017131 | 0.019189 | 0.000000 | 552,598.55 | 660,634.23 | 1,338,706.77 |

Dataset hash (all three reports): `8a424d121a02b0bdacbd365a265cc2b3f5e8e13cee270a20ce81d3c406c88f8a`. Evaluation transactions: 1,801 in each report.

## Findings

- **Sizes 35 and 50 could not be reached.** All 377 approved recommendations together contain only **21 distinct products** (the 210 high-utility patterns are combinations of the same 21 products), so B holds at most 21 products. The actual N (21) is reported; nothing was padded. Larger B needs more distinct products in the approved pool, e.g. a lower minUtil or a multi-day snapshot.
- **Equal-size comparison removes the size effect from A.** At S = 35 and 50 the full A score grows with the number of products shown (963,117 → 1,338,707), while A's top-21 score — the value compared with B — stays 660,634. With the V1 rule A would have been measured with 35 or 50 products against B's 21.
- At equal size, B has a lower hit rate than A's frequency baseline (0.0539 vs 0.0589 at N = 20; 0.0555 vs 0.0600 at N = 21) but a higher captured utility (0.0181 vs 0.0168; 0.0192 vs 0.0171).
- `bundleConversion` is 0 for B because B contains no `BUNDLE` blocks (none were generated at this threshold); A has no BUNDLE blocks by construction.

## Limitations

Demo seed (synthetic FoodMart-shaped data), one snapshot, one two-day window, one minUtil; no confidence intervals. A run at S = 35 was interrupted once before its report was written and was then repeated; only the three completed reports (ids 1–3) are listed.

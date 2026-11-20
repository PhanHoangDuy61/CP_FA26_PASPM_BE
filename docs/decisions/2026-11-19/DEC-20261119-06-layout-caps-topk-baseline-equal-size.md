# DEC-20261119-06 — Layout zone capacities, top-K baseline A and equal-size A/B comparison

| Field | Value |
|---|---|
| Date | 2026-11-19 (Asia/Ho_Chi_Minh) |
| Source | Owner decision of 2026-11-19; implemented on branch `Khoi/feat/f07-zone-caps-baseline-topk`, record [ROC-20261119-004](../../record-of-changes/2026-11-19/04-f07-zone-caps-baseline-topk.md) |
| Status | OWNER_DECISION — implemented on a feature branch; PR to `dev` |
| Related | [F07/F08 API](../../api/F07_F08_LAYOUT_CAMPAIGN_API.md); sets the values that DEC-20261119-03 left open (that file is on branch `Khoi/docs/decisions-20261119`) |

## Decision
1. **Zone capacities** (`paspm.layout.max-blocks-by-zone` / `max-products-by-zone`, environment-overridable):

   | Zone | max blocks | max products |
   |---|---:|---:|
   | BANNER | 3 | 3 |
   | BUNDLE | 4 | 16 |
   | CROSS_SELL | 2 | 8 |
   | FBT | 2 | 8 |
   | BASELINE | 35 | 35 |

2. **Baseline A** stores only the **top-K** products by `COUNT(DISTINCT transaction_id) DESC, product_id ASC`, with `K` = the `BASELINE` product capacity (35). Rule version `F07_BASELINE_V2`; `baseline_params` stores `k` and `candidateProducts`. Without a `BASELINE` capacity the V1 "all products" baseline is kept. Existing A layouts and revisions are not modified, and the `BASELINE` cap is validated only for V2 baselines.
3. **Equal-size simulation**: F08 compares B with the **first N products of A** in placement order, `N` = distinct products in B's revision (all of A when `N > |A|`). Report `algorithmVersion: OFFLINE_REPLAY_V2`; `assumptions` store `comparisonMode`, `comparedProductCountN`, `baselineProductCount`, `baselineProductsCompared`, `baselineK`, `nExceedsBaseline`. Old `OFFLINE_REPLAY_V1` reports stay as stored.
4. F07 shows the utility-score difference against A's same-size prefix: `LayoutView.equalSizeBaselineScore` (additive) next to A's full `weightedUtilityScore`.

## Rationale
- Showing every snapshot product in A made A's hit rate and captured utility close to 100 %, so the comparison measured nothing.
- The layout score and the replay metrics grow with the number of products shown; only layouts of the same size are comparable.
- Capacities bound the public homepage before F09 Storefront.

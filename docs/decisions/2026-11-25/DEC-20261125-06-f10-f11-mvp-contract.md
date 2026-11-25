# DEC-20261125-06 — F10 Bundle Cart and F11 Dashboard MVP

**Decision date:** 2026-11-25 (Asia/Ho_Chi_Minh).

**Authority:** Product Owner confirmation in the task conversation. **Implementation:** PLANNED.

## Decision

1. F09 follows `origin/Khoi/merge/f09-storefront-be`. Keep the applied V29 on dev. F09 V30 removes the legacy `store_slug` and `storefront_enabled` columns. Reserve V31 for F10.
2. Every `BUNDLE` block in the selected Layout B of an ACTIVE campaign is eligible for the whole-bundle action. F09 home supplies an opaque `bundleKey` bound to Store, campaign, layout revision and block. The backend validates that scope and the current selection. Cart eligibility is independent of recommendation provenance.
3. For an event on a resolved block, classify it as `MINED` only when the complete block matches exactly one `APPROVED`, `readyForConsumption=true` BUNDLE recommendation of the same Store and snapshot. Otherwise classify it as `MANUAL`. Classify the full block before skipping any component.
4. Whole-bundle add merges eligible components into ordinary cart lines. Customers can edit or remove each line independently. `addedFromBundle` is optional display metadata. A Store and customer share one persistent cart, capped at 99 units per Product. Price is current and stock is checked when writing. Skip out-of-stock components and report their Product IDs. If none can be added, return 409 and record `BUNDLE_ADD_REJECTED`. There are no bundle groups.
5. Every mutation requires `If-Match`. A stale version returns `409 CART_VERSION_CONFLICT`; the frontend reloads the cart and disables the action while a request is pending. This MVP has no idempotency table or `Idempotency-Key` header.
6. F11 at `/store/dashboard` serves `STORE_ADMIN` and `MERCHANDISER` in their own Store. Three independent panels show frozen F03 snapshot weighted utility, saved simulated F08 reports including `priorityCoverage`, and successful `BUNDLE_ADDED` actions split by `MINED` and `MANUAL`. The secondary KPI sums actual Product units added from bundles. Date range defaults to 30 Store-local days, maximum 90, with optional campaign filtering.

Guest carts, checkout, orders, payments, bundle discounts, `BUNDLE_VIEWED`, daily deals and benchmark charts are outside the MVP. F09/F10 display VND as whole đồng.

## Precedence and open design details

This decision refines DEC-20261117-03 and supersedes the earlier F10 all-or-nothing, group removal and idempotent-add provisions. F08 simulation and historical reports remain governed by their own decisions. The [F10 contract](../../api/F10_BUNDLE_CART_API.md) marks multi-bundle quantity and the handling of positive-but-insufficient stock or cap overflow per component as draft details pending confirmation. See the [F11 contract](../../api/F11_DASHBOARD_API.md).

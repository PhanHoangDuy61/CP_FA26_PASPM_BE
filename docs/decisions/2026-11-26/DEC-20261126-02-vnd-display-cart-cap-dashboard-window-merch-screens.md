# DEC-20261126-02 — VND display rounding, F10 single-item cap, F11 90-day window, Merchandiser screens

| Field | Value |
|---|---|
| Date | 2026-11-26 (Asia/Ho_Chi_Minh) |
| Source | Product Owner answers (Nguyễn Minh Khôi, Team Leader / acting Product Owner) to Q-02, Q-03, Q-13 and Q-14 of the black-box test pack, given in the task conversation on 2026-11-26 |
| Status | OWNER_DECISION. Documentation updated on branch `Khoi/docs/blackbox-test-cases`; F10/F11 code is PLANNED; the FE defect is open |
| Number | `-02` because DEC-20261126-01 (UML/ERD baseline) is already used; checked across `origin/dev` and every `origin/*` branch |
| Refines | [DEC-20261125-06](../2026-11-25/DEC-20261125-06-f10-f11-mvp-contract.md) (F10/F11 MVP) |

## Decision

1. **VND display.** Money keeps 2 decimals in storage and in API responses. Every UI displays VND as whole đồng, rounded **HALF_UP**: 21,000.50 đ is shown as `21.001 ₫`, 21,000.49 đ as `21.000 ₫`. Stored and returned values are never changed by display rounding. This applies to all staff and storefront screens (F02, F09, F10 and later F11 if it shows money).
2. **Merchandiser screens.** On FE `dev` a Merchandiser cannot open several screens that the role is allowed to use (role mapping grants F03 only and the sidebar is disabled). This is a **defect**, not intended behaviour. It must be fixed before the black-box test run (entry criterion in the test plan).
3. **F10 99-unit cap, single item.** `POST /items` or `PATCH /items/{itemId}` that would make a Product line exceed 99 units rejects the **whole request** with `409 CART_QUANTITY_LIMIT`; the cart and its version do not change, and nothing is clamped. Whole-bundle add is unchanged (DEC-20261125-06): each component adds as much as possible, `added = min(requested, sellableStock, 99 − current line quantity)`.
4. **F11 date window.** `from`..`to` are inclusive Store-local dates. A window of exactly **90 days** is accepted; **91 days or more** returns **400**. The default (both omitted) remains the last 30 days including today.

## Consequences

- `docs/api/F10_BUNDLE_CART_API.md`, `docs/api/F11_DASHBOARD_API.md`, `docs/frontend_handoff/F09_HANDOFF.md`, `F10_HANDOFF.md` and `F11_HANDOFF.md` state these rules.
- Black-box cases TC-F02-009, TC-F10-002, TC-F10-004, TC-F11-006 and the F09/F10 money-display checks use them; `docs/testing/blackbox/TEST_PLAN.md` lists the Merchandiser screen defect as a known defect and entry criterion.
- Not decided here: a single-item add or increase above the **sellable stock** (not the cap). The contract returns `409 INSUFFICIENT_SELLABLE_STOCK` but does not say whether part of the quantity is added. It stays open as Q-13b in the test pack's PO questions.

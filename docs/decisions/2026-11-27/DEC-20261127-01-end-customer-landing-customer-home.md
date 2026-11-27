# DEC-20261127-01 — End Customer lands on the Store chooser `/customer-home` after sign-in

| Field | Value |
|---|---|
| Date | 2026-11-27 (Asia/Ho_Chi_Minh) |
| Source | Product Owner decision (Nguyễn Minh Khôi, Team Leader / acting Product Owner), given in the task conversation on 2026-11-27 after the live smoke test (defect #3, check `UI-F01-landing-routes`, black-box case TC-F01-001) |
| Status | OWNER_DECISION. Documentation and black-box case updated on branch `Khoi/docs/smoke-followups`. No code change: FE `dev` already behaves this way |
| Number | `-01`: the first decision of 2026-11-27. Checked across `origin/dev` and every `origin/*` branch (no `docs/decisions/2026-11-27` folder existed) |
| Supersedes | The End Customer part of **Q-06 "Landing page per role"** in [DEC-20261126-03](../2026-11-26/DEC-20261126-03-blackbox-test-round-scope-and-remaining-answers.md) (End Customer → `/storefront`). The other three landing pages of Q-06 are unchanged |
| Related | DEC-20261126-03 (Q-06, Q-08), DEC-20261123-01 (Store slug and public Store list), F09 storefront API |

## Decision

After a successful sign-in the End Customer lands on **`/customer-home`**: the Store chooser, which lists the ACTIVE Stores (`GET /api/storefront/stores`). The End Customer opens a Store from there; the Store's storefront is `/storefront`. `/customer-home` is public (it also works without signing in), so the customer's first screen is the same as the guest's.

| Role | Landing page after sign-in |
|---|---|
| System Admin | `/admin/overview` (unchanged) |
| Store Admin | `/store/inventory` (unchanged) |
| Merchandiser | its F03 workspace, `/f03/configuration` (unchanged) |
| End Customer | **`/customer-home`** (was `/storefront` in DEC-20261126-03) |

## Why

The live smoke test on 2026-11-27 found that FE `dev` sends the End Customer to `/customer-home` (`defaultScreen: 'customer-home'` for the End-Customer role). An End Customer has no Store (the Store comes from the chosen storefront, never from the account), so a Store chooser is the right first screen; `/storefront` needs a Store to show anything. The PO accepted the behaviour as the intended one instead of treating it as a defect.

## Consequences

- Black-box case **TC-F01-001** expects `/customer-home` for the End Customer (regenerated test pack, ROC-20261127-003); [PO_QUESTIONS.md](../../testing/blackbox/PO_QUESTIONS.md) Q-06 and the Q-06 row of DEC-20261126-03 point here.
- The "return to where the customer was" behaviour of the FE (a customer who signed in from a Store page or a product page goes back to that page, not to `/customer-home`) is a separate FE rule and is not changed by this decision.
- No backend change; no contract change; no new test beyond the updated case.

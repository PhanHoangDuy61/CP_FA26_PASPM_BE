# ROC-20261126-009 — Black-box test cases, traceability and test plan

| Field | Value |
|---|---|
| Record ID | ROC-20261126-009 |
| Date / timezone | 2026-11-26 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Opus 5.5), requested by Nguyễn Minh Khôi |
| Type | Documentation (testing) |
| Features | F01–F09 (implemented), F10–F11 (PLANNED) |
| Branch / base HEAD | `Khoi/docs/blackbox-test-cases` / `origin/dev` @ `b94584b` |
| Execution status | COMPLETED (design only; no case executed) |
| Release status | UNRELEASED — branch pushed (update 2 pushed after `5fbf473`), no PR opened |
| Daily summary | [README of the day](README.md) |

## 1. Request and scope

Draft black-box test cases for PASPM, documents only, no code change. Sources: SRS v0.4 (FR/BR/UC), the UML pack use cases, screen flow and Store state (branch `Khoi/docs/diagrams-update-20261126`, not yet on `dev`), `docs/api/*`, the RBAC matrix, feature READMEs, frontend handoffs and DEC files. Precedence follows DEC-20261125-02 (latest PO decision wins; branch `Khoi/docs/source-of-truth-rule`, not yet on `dev`).

Numbering: records 001–008 of 2026-11-26 exist on branch `Khoi/docs/diagrams-update-20261126` (checked across `origin/dev` and every `origin/*` branch), so this record is 009.

## 2. Change details

### Documentation

- 246 test cases (248 after update 2) `TC-Fxx-nnn`, each with trace, role, preconditions, test data, steps, expected result, priority, type and status NOT RUN. UI steps use the routes of FE `dev` @ `7f298bc`; `[API]` steps are used only where no UI path exists.
- Techniques: equivalence partitioning, boundary values, decision tables (role × action and business conditions), state transitions (Store, batch, account, layout, campaign, recommendation), negative and security cases (RBAC per role, cross-Store 404, `STORE_INACTIVE` 403/409, 409 version conflicts).
- Traceability: 57/57 FR, 33/33 UC and 33/33 BR of SRS v0.4 have at least one case; the diagram FR page is mapped to SRS IDs; requirements without an executable black-box case are listed with the reason.
- Test plan: scope, environment (local Docker stack + demo seed), accounts, data setup, entry/exit criteria, how results are recorded.
- 17 PO questions and 4 documentation inconsistencies.
- Two behaviours left open by the docs were read from code and stated as such: editing Layout A returns 409; repeated SKU lines with a different price, cost or status are a row error.

| Feature | Cases | High | Medium | Low |
|---|---:|---:|---:|---:|
| F01 | 51 | 28 | 17 | 6 |
| F02 | 37 | 19 | 16 | 2 |
| F03 | 29 | 16 | 11 | 2 |
| F04 | 15 | 11 | 2 | 2 |
| F05 | 26 | 14 | 8 | 4 |
| F06 | 18 | 9 | 8 | 1 |
| F07 | 15 | 11 | 3 | 1 |
| F08 | 19 | 13 | 4 | 2 |
| F09 | 13 | 8 | 3 | 2 |
| F10 (PLANNED) | 16 | 10 | 6 | 0 |
| F11 (PLANNED) | 9 | 4 | 4 | 1 |
| **Total** | **248** | **143** | **82** | **23** |

## 3. Files changed

| Path | Change | Content |
|---|---|---|
| [../../testing/blackbox/TEST_CASES.csv](../../testing/blackbox/TEST_CASES.csv) | Added | One row per case, UTF-8 |
| [../../testing/blackbox/TEST_CASES.md](../../testing/blackbox/TEST_CASES.md) | Added | Same cases, grouped by feature, with a summary |
| [../../testing/blackbox/TRACEABILITY.md](../../testing/blackbox/TRACEABILITY.md) | Added | FR / UC / BR / NFR → case IDs; untested items and why |
| [../../testing/blackbox/TEST_PLAN.md](../../testing/blackbox/TEST_PLAN.md) | Added | Plan |
| [../../testing/blackbox/PO_QUESTIONS.md](../../testing/blackbox/PO_QUESTIONS.md) | Added | Questions for the PO |
| [../../testing/README.md](../../testing/README.md) | Modified | Link to the black-box pack |
| [README.md](README.md), [../README.md](../README.md) | Added / Modified | Daily summary and index row |
| [DEC-20261126-02-vnd-display-cart-cap-dashboard-window-merch-screens.md](../../decisions/2026-11-26/DEC-20261126-02-vnd-display-cart-cap-dashboard-window-merch-screens.md) | Added (update 2) | PO answers Q-02, Q-03, Q-13, Q-14 |
| [../../api/F10_BUNDLE_CART_API.md](../../api/F10_BUNDLE_CART_API.md), [../../api/F11_DASHBOARD_API.md](../../api/F11_DASHBOARD_API.md), [../../api/API_CHANGELOG.md](../../api/API_CHANGELOG.md) | Modified (update 2) | 99-cap whole refusal, 90-day window, VND display; changelog entry |
| [../../frontend_handoff/F09_HANDOFF.md](../../frontend_handoff/F09_HANDOFF.md), [F10_HANDOFF.md](../../frontend_handoff/F10_HANDOFF.md), [F11_HANDOFF.md](../../frontend_handoff/F11_HANDOFF.md) | Modified (update 2) | VND HALF_UP display; cap error handling; date picker limit |

## 4. Verification

| Check | Result |
|---|---|
| CSV and MD generated from one source; 246 rows each (248 after update 2) | PASS |
| Every FR, UC and BR of SRS v0.4 traced to at least one case | PASS (0 missing) |
| Every case ID cited in TEST_PLAN, TRACEABILITY and PO_QUESTIONS exists and matches its title | PASS |
| Test case execution | NOT RUN |
| Maven | NOT RUN (documentation only) |

## 5. Open items

- Open PO questions (Q-01, Q-04 to Q-12, Q-13b, Q-15 to Q-17) in [PO_QUESTIONS.md](../../testing/blackbox/PO_QUESTIONS.md).
- `Khoi/docs/diagrams-update-20261126` is now on `dev` (PR #37, `fb48d55`) with records 001–008 of this day: this branch's day README and index row will conflict and must be combined when this branch is brought up to date with `dev`.
- Known defect KD-01 (Merchandiser screens on FE `dev`) must be fixed before the run.
- F10/F11 cases run only after those features are merged.

## 6. Update 2 — PO answers applied (2026-11-26)

The PO answered four questions in the task conversation; recorded as [DEC-20261126-02](../../decisions/2026-11-26/DEC-20261126-02-vnd-display-cart-cap-dashboard-window-merch-screens.md) (number checked across `origin/dev` and every `origin/*` branch: only DEC-20261126-01 existed).

| Answer | Applied to |
|---|---|
| VND displayed as whole đồng HALF_UP (21,000.50 → 21.001 ₫); stored values unchanged | TC-F02-009 (now boundary .50/.49); new TC-F09-013 and TC-F10-016 (displayed subtotal = rounded server subtotal); TC-F10-002; F09 and F10 handoffs; F10 API |
| Merchandiser screens missing on FE `dev` are a defect to fix before testing | TEST_PLAN.md §7 entry criterion and §9 known defect KD-01 |
| F10 single-item add/change above 99 → whole request 409 `CART_QUANTITY_LIMIT`; bundle add unchanged | TC-F10-004 (99 / 100, 98+1 / 98+2, PATCH 99 / 100, no clamping); F10 API and handoff |
| F11 window: 90 days inclusive accepted, 91 → 400 | TC-F11-006 (2026-09-01..11-29 vs 2026-08-31..11-29, plus a 1-day window); F11 API and handoff |

Also: PO_QUESTIONS.md gains a status column (4 ANSWERED, Q-13b split off and OPEN); TRACEABILITY.md gains §6 "Owner decisions → test cases" (former §6 is now §7) and notes that the UML pack is on `dev` since PR #37; API_CHANGELOG entry. Case IDs of existing cases are unchanged; the two new cases were appended at the end of F09 and F10.

| Check | Result |
|---|---|
| Regenerated CSV/MD: 248 rows, all NOT RUN | PASS |
| Every FR, UC and BR still traced | PASS (0 missing) |
| Cited case IDs exist and match titles; relative links | PASS |
| Test case execution; Maven | NOT RUN |

# ROC-20261126-009 — Black-box test cases, traceability and test plan

| Field | Value |
|---|---|
| Record ID | ROC-20261126-009 |
| Date / timezone | 2026-11-26 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Opus 5.5), requested by Nguyễn Minh Khôi |
| Type | Documentation (testing) |
| Features | F01–F11 (F10/F11 implemented on `dev` since PR #38, #44, #45; cases aligned in update 5) |
| Branch / base HEAD | `Khoi/docs/blackbox-test-cases` / `origin/dev` @ `b94584b`; `origin/dev` @ `fb48d55` merged in update 3, `114576d` in update 5 |
| Execution status | COMPLETED (design only; no case executed) |
| Release status | UNRELEASED — branch pushed (`5fbf473`, update 2 `c836795`, update 3 `6c391ce` after the merge `56a0225`, update 4 `a9a3fd9`, update 5 after the merge `8973967`); delivered by a PR into `dev` |
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

- ~~Open PO questions~~: all answered (update 3).
- ~~Day README and index conflict with `dev`~~: resolved by the merge in update 3.
- Before the round (TEST_PLAN.md §7): FE defects KD-01 and KD-02 fixed; DEC-20261125-02 merged into `dev`; test-data preparation script TDP-01 written (separate code task) and run.
- F10/F11 cases run only after those features are merged; Q-13b also applies to the F10 implementation.
- Diagram follow-up (Q-12): only the FR page label was changed. AD07 ("Declare CTR/conversion assumptions", "Compare simulated utility, CTR, conversion and estimated revenue") and R2-07.07 (`MetricType` with CTR, CONVERSION, ESTIMATED_REVENUE) still model CTR; ARCHITECTURE_SDD already lists both pages for rewrite. Redrawing them is a separate diagram task.

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

## 7. Update 3 — remaining PO answers, documentation conflicts, merge with `dev` (2026-11-26)

The PO answered every remaining question; recorded as [DEC-20261126-03](../../decisions/2026-11-26/DEC-20261126-03-blackbox-test-round-scope-and-remaining-answers.md) (a new number so DEC-20261126-02 stays as recorded; numbering checked across `origin/dev` and every `origin/*` branch).

| Change | Where |
|---|---|
| Merged `origin/dev` @ `fb48d55` (PR #37, diagrams). Conflicts in the day README and the ROC index resolved by keeping all records 001–009; the index row now says 001–008 MERGED_TO_DEV | merge commit `56a0225`; [README.md](README.md); [../README.md](../README.md) |
| Q-04: removed screens must be unreachable, defect KD-02 | TC-F03-028, TC-F05-025; TEST_PLAN.md §7, §9 |
| Q-05 / Q-08: Merchandiser mining and F09 on the API this round | TC-F05-006, TC-F09-001, TC-F09-013; TEST_PLAN.md §1, §4 |
| Q-06 / Q-07: landing pages; activation in `/store/layout` | TC-F01-001, TC-F08-009 (traces) |
| Q-09: binary MiB; multipart 413 accepted | TC-F01-027 (5 MiB / +1 byte added), TC-F01-044 |
| Q-10: no manual fixture for the 10,000 cap | TRACEABILITY.md §7 |
| Q-11: threshold on the shorter name (`Tea` vs `Teas` no confirmation) | TC-F02-006 |
| Q-12: CTR not part of PASPM; diagram FR08.04 relabelled "Compare simulated A/B metrics" | `docs/diagrams/PASPM_Unified_UML2_Editable.drawio` (one cell value; XML parses); TC-F08-017; TRACEABILITY.md §5, §7 |
| Q-13b: add above sellable stock refused as a whole | TC-F10-006 (add 4 refused, add 3 accepted); F10 API, F10 handoff, API_CHANGELOG |
| Q-15: scheduler only "enable returns 503" | TC-F05-022 rewritten to that check only; TC-F05-021 and TC-F05-023 titled `[OUT OF ROUND]` (IDs kept); schedule step removed from TC-F05-024 |
| Q-16: Stores B/C from a test-data preparation script (TDP-01, separate task) | TEST_PLAN.md §5, §6 (script requirements), §7 entry criterion |
| Q-01 / Q-17: source-of-truth DEC merged before the round; exit criteria accepted | TEST_PLAN.md §7 |
| Doc conflict 1: seed creates the Store by API (checked in `seed_demo.py`) | `docs/setup/STAGING.md` |
| Doc conflict 2: historical sections labelled Superseded (4 places) | `docs/api/F05_MINING_API.md` |
| Doc conflict 3: DEC-20261125-01 reference corrected | commit `e2749f3` on branch `Khoi/docs/source-of-truth-rule` (the file is not on `dev` or this branch) |
| Doc conflict 4: Layout A placements/READY return 409 (checked in `LayoutCampaignService.requireB`) | `docs/api/F07_F08_LAYOUT_CAMPAIGN_API.md` |
| PO_QUESTIONS.md: every question ANSWERED with its decision; conflicts listed as resolved | `docs/testing/blackbox/PO_QUESTIONS.md` |
| Generator made self-contained (`outputs/blackbox-tools/`, outside the repo): imports `cases.py` from its own folder, `--work-dir` for `trace_index.json`, README with re-run steps | not in the repository |

| Check | Result |
|---|---|
| Refactored generator, before any case edit, with `/tmp/bbx` deleted: output byte-identical to the committed files | PASS |
| Default and `--work-dir` runs produce identical files | PASS |
| 248 cases; existing IDs unchanged (none added, none removed in update 3) | PASS |
| Every FR, UC and BR still traced | PASS (0 missing) |
| Cited case IDs exist; relative links; `git diff --check` | PASS |
| Test case execution; Maven | NOT RUN |

## 8. Update 4 — Run mining screen (Q-05 updated) and known issue KI-01 (2026-11-26)

| Change | Where |
|---|---|
| The PO updated Q-05: the Run mining screen exists on FE `dev` ("Product Combination Analysis" → `/mining?view=setup` → "Run Analysis"); Merchandiser runs mining for its own Store, Store Admin sees history only. Checked in FE `dev` @ `0cd57a6` (`F05WorkspaceView`, `MiningSubmissionPanel`, route `f05-mining` → `/mining`) | Addendum in DEC-20261126-03; PO_QUESTIONS.md Q-05 |
| F05 cases switched from API to UI steps; IDs kept | TC-F05-006 (submit), TC-F05-007 (Store Admin has no Run tab; extra fields and Store Admin POST stay API), TC-F05-010 (λ error shown), TC-F05-020 (Store Admin history) |
| Known issue KI-01: FE browser test for keyboard Store selection on Users & Roles fails (reported by Codex, not reproduced here); not an entry criterion | TEST_PLAN.md §9 (affected cases and workaround) |
| Test plan version 0.4; F05 scope line; traceability text | TEST_PLAN.md, TRACEABILITY.md (via `outputs/blackbox-tools/trace.py`) |

| Check | Result |
|---|---|
| 248 cases; IDs unchanged; every FR, UC and BR traced | PASS |
| Cited case IDs exist; relative links; `git diff --check` | PASS |
| Test case execution; Maven | NOT RUN |

## 9. Update 5 — merge with `dev` after F10/F11 landed; F10/F11 cases aligned (2026-11-26)

PO answer of 2026-11-26: take `dev`'s API docs as the base (F10/F11 are implemented there), re-apply only the PO rules `dev` lacks, check each rule against the code and report disagreements as defects instead of hiding them.

| Change | Where |
|---|---|
| Merge `origin/dev` @ `114576d` (`8973967`). Conflicts in four API docs resolved by taking `dev`'s text, then adding what it lacked: DEC-20261126-02/-03 links on the 99 cap and the sellable-stock refusal, "nothing is clamped", `bundleQuantity` and any field other than `bundleKey` → 400, VND whole đồng HALF_UP with the server `subtotal` formatted as such; F11 window and handoff cite DEC-20261126-02 | `docs/api/F10_BUNDLE_CART_API.md`, `F11_DASHBOARD_API.md`, `docs/frontend_handoff/F11_HANDOFF.md`, `docs/api/API_CHANGELOG.md` (new entry; no route or schema change, OpenAPI 118 / 147) |
| Duplicate F09 HALF_UP checklist item removed (dev's and this branch's) | `docs/frontend_handoff/F09_HANDOFF.md` |
| Record READMEs: all records kept (001–014); 2026-11-26 index row counts 14 | `docs/record-of-changes/README.md`, `2026-11-26/README.md` |
| TC-F10-001…016 and TC-F11-001…009: `[PLANNED]` removed, IDs kept; routes, `If-Match` (no Idempotency-Key), error codes and messages, `BundleAddResult` / `SnapshotUtilityPage` / `BundleAdditions` fields aligned with the code; event checks made black-box through F11 counts; UI steps marked for the round in which the FE calls the real API | `outputs/blackbox-tools/cases.py`, `gen.py`, `trace.py` → TEST_CASES.md/.csv, TRACEABILITY.md |
| Test plan 0.5: F10/F11 scope, TDP-01 implemented (PR #47), Flyway V31, entry/exit criteria without `[PLANNED]` | TEST_PLAN.md |
| `scripts/seed/README.md` links TEST_PLAN §6 again (the file is now on `dev`) | `scripts/seed/README.md` |

PO rules checked against `dev` @ `114576d` (backend agrees with every rule):

| Rule | Code |
|---|---|
| Single-item add/PATCH above 99 → 409 `CART_QUANTITY_LIMIT`, whole request | `CartService.java:61-62` (add: target = line + requested, checked before any write), `:136-138` (PATCH increase), `:192-193` |
| Above sellable stock → 409 `INSUFFICIENT_SELLABLE_STOCK`, whole request | `CartService.java:195-196`; reductions skip the check (`:136`) |
| One bundle per click, body `{bundleKey}` only | `CartContracts.java:31-33` (`AddBundle` has only `bundleKey`), `application.yml:14` (`fail-on-unknown-properties: true`), one unit per component `CartService.java:111-112` |
| Only `If-Match` version, 409 `CART_VERSION_CONFLICT`, no idempotency key | `CartController.java:55,69,84,93`, `CartService.java:172-176`; no idempotency code in `storefront/cart` |
| F11 default 30 days, max 90 inclusive, 91 → 400 | `DashboardService.java:23-24,83,89-91` |
| VND whole đồng HALF_UP (UI) | FE `dev` @ `f38d3ad` does not follow it; see defects |

Candidate defects found (not fixed here; the PO decides):

| ID | Defect | Where (FE `dev` @ `f38d3ad`) |
|---|---|---|
| C-01 | F02 money is shown with up to 20 decimals, not whole đồng HALF_UP | `src/features/store/f02.forms.ts:5` |
| C-02 | F04 sales transactions show VND with up to 2 decimals | `src/features/f04/components/SalesTransactionsView.tsx:13` |
| C-03 | The storefront catalog formats prices with the `vi-VN` default (up to 3 decimals), not whole đồng; the file is local catalog data, not the real F09 API | `src/features/customer/data/customerCatalog.ts:122` |

| Check | Result |
|---|---|
| 248 cases; IDs unchanged; only the 25 F10/F11 cases changed; `trace.py` `missing: []` | PASS |
| No `[PLANNED]` left in TEST_CASES; relative links; `git diff --check` | PASS |
| Test case execution | NOT RUN |

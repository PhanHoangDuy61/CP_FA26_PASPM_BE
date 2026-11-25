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
| Release status | UNRELEASED — branch pushed, no PR opened |
| Daily summary | [README of the day](README.md) |

## 1. Request and scope

Draft black-box test cases for PASPM, documents only, no code change. Sources: SRS v0.4 (FR/BR/UC), the UML pack use cases, screen flow and Store state (branch `Khoi/docs/diagrams-update-20261126`, not yet on `dev`), `docs/api/*`, the RBAC matrix, feature READMEs, frontend handoffs and DEC files. Precedence follows DEC-20261125-02 (latest PO decision wins; branch `Khoi/docs/source-of-truth-rule`, not yet on `dev`).

Numbering: records 001–008 of 2026-11-26 exist on branch `Khoi/docs/diagrams-update-20261126` (checked across `origin/dev` and every `origin/*` branch), so this record is 009.

## 2. Change details

### Documentation

- 246 test cases `TC-Fxx-nnn`, each with trace, role, preconditions, test data, steps, expected result, priority, type and status NOT RUN. UI steps use the routes of FE `dev` @ `7f298bc`; `[API]` steps are used only where no UI path exists.
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
| F09 | 12 | 8 | 2 | 2 |
| F10 (PLANNED) | 15 | 10 | 5 | 0 |
| F11 (PLANNED) | 9 | 4 | 4 | 1 |
| **Total** | **246** | **143** | **80** | **23** |

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

## 4. Verification

| Check | Result |
|---|---|
| CSV and MD generated from one source; 246 rows each | PASS |
| Every FR, UC and BR of SRS v0.4 traced to at least one case | PASS (0 missing) |
| Every case ID cited in TEST_PLAN, TRACEABILITY and PO_QUESTIONS exists and matches its title | PASS |
| Test case execution | NOT RUN |
| Maven | NOT RUN (documentation only) |

## 5. Open items

- PO questions Q-01 to Q-17 in [PO_QUESTIONS.md](../../testing/blackbox/PO_QUESTIONS.md).
- When merging with `Khoi/docs/diagrams-update-20261126`, combine this day's README (records 001–008 there, 009 here) and the index row.
- F10/F11 cases run only after those features are merged.

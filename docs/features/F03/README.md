# F03 — Utility Profiling and Weight Rules

> Last verified Backend: `dev` @ `11973a0` (merge of PR #11; source tree identical to `a4f1115`, whose code passed the full verification script on `45dbd80`, 23/23 stages). Evidence: [Core release readiness](../../reports/F03_F05_CORE_RELEASE_READINESS_2026-11-02.md), [audit matrix](../../reports/F03_F05_API_AUDIT_MATRIX_2026-11-02.md).
>
> **2026-11-15:** current scoring is `W2_EXPIRY_SEASON_V1` (branch `Khoi/feat/f05-progress-f03-weight-v2`, not merged).
>
> **Requirements caveat (legacy v1.2 only):** the formally approved Weight Rules v1.2 source file is **not tracked** in the repository ([gap notice](../../requirements/README.md#f03-v12-source-gap)). [F03_UTILITY_API.md](../../api/F03_UTILITY_API.md) is the written v1.2 implementation contract; it is not a substitute approval.

## Current status — branch `Khoi/feat/f05-progress-f03-weight-v2` (2026-11-15, not merged)

Code paths are relative to `src/main/java/vn/edu/fpt/paspm/` on `c8c837a` (code identical to `e38b55f`). Requirements: [F03 Weight v2 requirements](../../requirements/F03_WEIGHT_V2_REQUIREMENTS.md) (derived, pending formal approval). Decisions: [owner decision register 2026-11-15](../../decisions/OWNER_DECISIONS_F03_F05_2026-11-15.md).

| Rule | Behaviour | Evidence |
|---|---|---|
| Formula | `W = 1 + λ[α·U_expiry + β·M]`, exact, rounded half-up to 2 decimals | `utility/UtilityMath.java:31-39` |
| Legacy formula kept | `W = 1 + λ(αS + βE + γM)` used only for pre-V25 data | `utility/UtilityMath.java:18` |
| U_expiry | FEFO by push day then integer batch id; capacity `v × days_to_push`; push day ≤ analysis date → 0 selling days | `utility/ExpiryRiskCalculator.java:59`, `:74-75` |
| Eligible batch | ACTIVE, remaining > 0, not expired (expiry > analysis date), has a push day | `utility/ExpiryRiskCalculator.java:41`, `:45` |
| Missing push day | Excluded, warning `MISSING_PUSH_DAY`, never blocks | `utility/ExpiryRiskCalculator.java:45`, `:84`; `utility/UtilityMatrixBuilder.java:66` (always ready) |
| v(i) | Units in the N days ending on and including the analysis date ÷ N; N = 30 default | `utility/WeightV2Calculator.java:30`; `utility/UtilityVersionedContracts.java:17` |
| v = 0 | U = 0, warning `SLOW_MOVING_NO_RECENT_SALES`; status remains `MISSING_PUSH_DAY` when no eligible batch exists, otherwise `SLOW_MOVING`. Both warnings coexist after the 2026-11-16 fix | `utility/ExpiryRiskCalculator.java:60` |
| M | Highest weight among active assigned seasons (P2); active = analysis date in [start, end] (P3) | `utility/WeightV2Calculator.java:39-43`; `utility/SeasonRepository.java:69` |
| λ range | [0, λ_max]; System Admin sets only λ_max | `utility/UtilityConfigurationService.java:38-42`; `utility/UtilityConfigurationController.java:17-20` |
| Store config | Merchandiser sets α, β (sum 1), λ, N (1..365) | `utility/UtilityConfigurationService.java:31-33`; `utility/UtilityContracts.java:36-42` |
| Seasons | CRUD + assignment; Merchandiser writes, Store Admin reads; duplicate name 409; delete with products 409; foreign product 404 | `utility/SeasonController.java:14-42`; `utility/SeasonService.java:32`, `:51`, `:63` |
| 410 routes | `PUT /api/utility/config/expiry-window` → batch `pushDay`; `PATCH /api/utility/products/{id}/scores` → season assignment | `utility/UtilityConfigurationController.java:30-33`; `utility/ProductScoreService.java:46` |
| Snapshot freeze | λ check 409, seasons copied by value | `utility/UtilityFreezer.java:23-25`; `utility/UtilitySnapshotService.java:39`; `utility/UtilitySnapshotRepository.java:43` |
| Old snapshots | Selected by `scoringVersion`: `F03_LINEAR_V1` and `EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1` → legacy records, `W2_EXPIRY_SEASON_V1` → W2, other → rejected | `utility/UtilityJsonVersions.java:29-31` |
| V25 conversion | Owner rule `OWNER_2026_11_15_V1`: α ← old expiry, β ← old season, stock dropped, defaults 0.5/0.5, λ clamped to [0, λ_max], N = 30, version + 1; evidence in `utility_weight_v2_conversion` | `utility/WeightV2Conversion.java:14-16`, `:44-49`; `db/migration/V25__f03_weight_v2_expiry_season.java:80`, `:150` |

**Limitations / known gaps (2026-11-15):** no real database has been converted (only synthetic Stores in `WeightV2MigrationTest`); a rolling upgrade with old instances is **NOT VERIFIED** and expected to fail on config reads; an unknown or corrupt stored `scoringVersion` surfaces as HTTP 500 rather than a mapped error (finding in [record 005](../../record-of-changes/2026-11-15/05-docs-sync-f03-w2-f05-progress.md)); the F03 ERD drawing still shows three weights (redraw needed).

**Verification:** final gate 645 tests, 640 PASS, 0 FAIL, 5 SKIP, exit 0; JPA supplementary 5/5 ([record 004](../../record-of-changes/2026-11-15/04-f03-weight-v2-expiry-season.md)). NOT RUN: Linux SPMF orphan cleanup, GitHub CI, interactive Swagger UI, real-data conversion.

## 1. Purpose and scope
Turns Store sales history (F04), batch push days (F02) and Store seasons into a frozen, weighted utility matrix that F05 mines. **Current scoring version `W2_EXPIRY_SEASON_V1` (2026-11-15):** `W = 1 + λ[α·U_expiry + β·M]`, rounded half-up to 2 decimals; item and transaction utilities stay `BigDecimal` scale 6 `HALF_UP`. Snapshots made before V25 keep their own formula (`F03_LINEAR_V1` or `EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1`) and are only read, never recomputed.

## 2. Actors and effective RBAC
| Role | Capability |
|---|---|
| `SYSTEM_ADMIN` | λ upper bound only (`/api/system/utility/lambda-policy`, GET/PUT `{lambdaMax}`; lower bound fixed at 0) |
| `MERCHANDISER` | Store α (expiry), β (season), λ ∈ [0, λ_max], sales window N; season CRUD and product assignment; preview, snapshots, snapshot matrix |
| `STORE_ADMIN` | read Store config, product scores and seasons; expiry-push query; owns batch `pushDay` through F02 |
| `END_CUSTOMER` | none |

System Admin cannot use Store-bound F03 routes; foreign Product, season or snapshot ids answer 404. `F03F05RbacMatrixTest` covers all 20 F03 operations role by role.

## 3. Implemented functionality (20 operations)
Lambda policy (2), Store config (3: read, weights, removed window → 410), product scores (2: read, removed PATCH → 410), preview, snapshot create/read/matrix (3), push-day expiry query (1), seasons (8: list, read, create, update, delete, list products, assign, unassign). No snapshot list or delete endpoint; Store snapshot listing for mining is `GET /api/mining/snapshots` (F05).

## 4. Business rules (W2)
- **U_expiry = Q_risk / Q_eligible** by FEFO over eligible batches (ACTIVE, remaining > 0, not expired, with a push day), ordered by push day then integer `batch_id`; selling capacity `v(i) × days_to_push`; push day on/before the analysis date puts the whole batch at risk (P4). Golden: 0.375 and 0.25.
- **v(i)** = units of COMPLETED, F03-eligible transactions in the N days ending on and including the analysis date (Store zone) ÷ N; N = 30 by default, editable by the Merchandiser (Q6). v = 0 → U = 0 with `SLOW_MOVING_NO_RECENT_SALES` (Q7).
- **Push day** per inventory batch, entered by the Store Admin; `product.expiry_push_days` is accepted by the Product API but ignored (P5). A batch without one only warns and is excluded (Q1). No backfill.
- **M** = highest weight among active seasons the product is assigned to (P2), active = analysis date in `[startDate, endDate]` inclusive (P3), else 0. A season with products cannot be deleted (409, Q8).
- α + β = 1, each in [0,1]; λ ∈ [0, λ_max] → otherwise 400. A λ above a lowered λ_max makes `lambdaValid=false`, blocks new snapshots (409) and new Merchandiser mining requests; it is never clamped.
- Snapshots freeze λ, α, β, N, λ_max, the active seasons by value (`utility_snapshot_season` + `FrozenV2.seasons`), U_expiry, M, W and warnings. Missing push days and slow movers never block a snapshot.
- Source is `F04_DATABASE_V1`; utilities must fit signed 64-bit after ×10⁶; no silent clamping.

Legacy rule (pre-V25 snapshots only): binary `E` from `Product.expiryPushDays`, three weights α (stock), β (expiry), γ (season) and policy `[lambdaMin, lambdaMax]`. See the legacy section of the [API contract](../../api/F03_UTILITY_API.md).

## 5. Main workflows
λ_max (System Admin) → α, β, λ, N and seasons (Merchandiser) → batch push days (Store Admin, F02) → preview → create READY snapshot → mining by the Merchandiser (own Store) or the System Admin (F05) → F06 consumes mining results and re-checks live inventory.

## 6. API documentation
[F03 API contract (W2 + legacy)](../../api/F03_UTILITY_API.md) · [F03→F05→F06 integration](../../api/F03_V12_F05_F06_INTEGRATION.md) · [Frontend handoff](../../frontend_handoff/F03_HANDOFF.md) · [W2 breaking changes](../../frontend_handoff/F03_W2_BREAKING_CHANGES.md) · [runtime OpenAPI](../../api/openapi.json).

## 7. Entities and migrations
`system_configuration['UTILITY_LAMBDA_POLICY']` (V4, rewritten to W2 by V25), `store.utility_config` (rewritten by V25 with the owner conversion rule; evidence in `utility_weight_v2_conversion`), `season`, `product_season`, `utility_snapshot_season`, `inventory_batch.push_day`, matrix `expiry_risk`, `season_weight`, `season_id` (V25), `utility_snapshot` and `utility_matrix_item` (V5). Product score columns (V4) and `expiry_push_days` (V8) are kept and unused. Java migration V11 manages historical matrix storage. Details: [V24/V25 schema notes](../../architecture/F03_W2_SCHEMA_V24_V25.md). Original F03 ERD: `docs/diagrams/PASPM_Complete_ERD_F03_Three_Weights_v4.drawio` (preserved, not updated).

## 8. Dependencies
F01 (role, Store), F02 (Product, batches, push day), F04 (transactions for base utility and v(i)). Consumed by F05 (frozen matrix, scheduled snapshots use the same freezer) and F06 (current U_expiry in eligibility, push-day expiry alerts, ended-season warning).

F01 session reconciliation (2026-11-12, dev V20 retained + additive V21): browser clients can restore their Bearer; utility role gates and Store isolation remain unchanged. Session revocation does not recalculate, delete or alter immutable snapshots. See [shared session impact and evidence](../F01/F01_SESSION_MANAGEMENT.md#5-tác-động-tới-feature-khác).

## 9. Tests and evidence
W2: `WeightV2CalculationTest` (golden values, rounding, bounds, P2, cross-check with `paspm-experiments`), `WeightV2MigrationTest` (owner conversion rule, frozen JSON byte-identical, H2 + MySQL), `VersionedUtilityJsonTest`, `VersionedUtilityPersistence*Test`, `UtilityIntegrationTest`, `RetailCatalogUtility*Test`, `PushDayIntegrationTest`, `F03F05RbacMatrixTest`. Legacy readers: `ExpiryBinaryRulesTest`, `ExpiryFefoOrderingTest`, `UtilitySnapshotRoutingTest`, `WeightRulesV12MigrationTest` (now up to V24). Run results: [record of changes 2026-11-15](../../record-of-changes/2026-11-15/04-f03-weight-v2-expiry-season.md).

## 10. Known limitations
No snapshot list/update/delete endpoint; no trigger enforcing immutability against direct SQL; existing batches have no push day until the Store Admin sets one; product score columns and `expiry_push_days` remain in the schema unused; the v1.2 source gap above (legacy only).

## 11. Pending decisions
Owner decisions Q1–Q10 and the conversion rule are recorded in the [decision register](../../decisions/OWNER_DECISIONS_F03_F05_2026-11-15.md). Assumptions applied pending owner confirmation: P2 (highest weight), P3 (inclusive active dates), P4 (push day reached → whole batch at risk), P5 (`expiryPushDays` accepted but ignored), P6 (same paths, new bodies). Report text changes listed in the record of changes.

## 12. Related diagrams
[Diagram index](../../diagrams/README.md) and the editable [27-page Draw.io pack](../../diagrams/PASPM_Unified_27_Diagrams_UML2_Editable.drawio) (QA: [UML27 report](../../reports/UML27_DIAGRAM_QA_2026-11-02.md)); relevant pages: Use Case UC09 and UC31, AD01 (F03 snapshot step), State R2-06.02 (Utility Snapshot), Context (M1, A2 flows), System Architecture.

## 13. Historical references
[Original v4 backlog](reference_v4/PASPM_F03_Three_Weights_v4_API_Swagger_Backlog.md), [issues](reference_v4/PASPM_F03_Three_Weights_v4_GitHub_Issues/README.md), [original F03→F05 handoff](reference_v4/F03_F05_HANDOFF.md) (stale downstream text), [legacy matrix compatibility](../../architecture/LEGACY_UTILITY_MATRIX_COMPATIBILITY.md).

## 2026-11-16 follow-up
Missing-push-day and no-recent-sales warning codes coexist. Unsupported stored scoring versions return coded 409 conflicts; immutable legacy JSON remains untouched. See the current API contract.

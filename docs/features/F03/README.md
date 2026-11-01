# F03 — Utility Profiling and Weight Rules

> Last verified Backend: `dev` @ `11973a0` (merge of PR #11; source tree identical to `a4f1115`, whose code passed the full verification script on `45dbd80`, 23/23 stages). Evidence: [Core release readiness](../../reports/F03_F05_CORE_RELEASE_READINESS_2026-11-02.md), [audit matrix](../../reports/F03_F05_API_AUDIT_MATRIX_2026-11-02.md).
>
> **Requirements caveat:** the formally approved Weight Rules v1.2 source file is **not tracked** in the repository ([gap notice](../../requirements/README.md#f03-v12-source-gap)). [F03_UTILITY_API.md](../../api/F03_UTILITY_API.md) is the written v1.2 implementation contract; it is not a substitute approval.

## 1. Purpose and scope
Turns Store sales history (F04) plus Store weights and product priorities into a frozen, weighted utility matrix that F05 mines. `W = 1 + λ(αS + βE + γM)`; item and transaction utilities are `BigDecimal` scale 6 `HALF_UP`.

## 2. Actors and effective RBAC
| Role | Capability |
|---|---|
| `SYSTEM_ADMIN` | global λ bounds (`/api/system/utility/lambda-policy`, GET/PUT) only |
| `MERCHANDISER` | Store weights and selected λ, Stock/Seasonal scores, preview, snapshots, snapshot matrix |
| `STORE_ADMIN` | read Store config, read Product scores, expiry-push query; deprecated Store expiry window; owns Product `expiryPushDays` through F02 |
| `END_CUSTOMER` | none |

System Admin cannot use Store-bound F03 routes; foreign Product or snapshot ids answer 404. Verified role by role for all 12 routes by `F03F05RbacMatrixTest`.

## 3. Implemented functionality (12 operations)
Lambda policy (2), Store config (3: read, weights, deprecated window), Product scores (2), preview, snapshot create/read/matrix (3), independent expiry-push batches (1). There is no snapshot list or delete endpoint; Store snapshot listing for mining is `GET /api/mining/snapshots` (F05).

## 4. Business rules
- Global λ policy `[lambdaMin, lambdaMax]` is versioned; changing it never clamps Store values or rewrites snapshots.
- Store weights: enabled coefficients `α, β, γ ∈ [0,1]` sum exactly to 1; all disabled gives `W = 1`; λ must lie inside the policy.
- Stock (`S`) and Seasonal (`M`) Priority Scores are manual, `[0,1]`, at most 6 decimals, partial `PATCH`.
- **Binary Expiry Score `E` (scoring version `EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1`):** `T = Product.expiryPushDays` (null/omitted = 0, no Store fallback); `E = 1` iff `T > 0` and the earliest sellable batch has `daysRemaining ≤ T`. Quantity ratios never dilute E; non-expiring products have `E = 0` (`NOT_APPLICABLE`).
- **FEFO order:** `expiry_date → received_at → batch_id`.
- Snapshots are immutable: configuration, scores, FEFO evidence, `sourceHash` and `matrixHash` are frozen; reads never recompute. Historical `F03_LINEAR_V1` snapshots stay readable and unchanged.
- Source is `F04_DATABASE_V1` (COMPLETED Store transactions with historical prices); utilities must fit signed 64-bit after ×10⁶; no silent clamping.

## 5. Main workflows
Policy (System Admin) → Store weights and scores (Merchandiser) → Product thresholds (Store Admin, F02) → preview → create READY snapshot → hand `snapshotId` to the System Admin for F05 mining → F06 consumes mining results and re-checks live inventory.

## 6. API documentation
[F03 API contract](../../api/F03_UTILITY_API.md) · [F03→F05→F06 integration and data flow](../../api/F03_V12_F05_F06_INTEGRATION.md) · [Frontend handoff](../../frontend_handoff/F03_HANDOFF.md) · [runtime OpenAPI](../../api/openapi.json).

## 7. Entities and migrations
`system_configuration` (V4, key `UTILITY_LAMBDA_POLICY`, versioned), Store weight columns on `store`, `product.stock_priority_score`, `seasonal_priority_score` (V4) and `expiry_push_days` (V8), `utility_snapshot` and `utility_matrix_item` (V5). Java migration V11 (`V11__historical_utility_matrix_storage`) manages historical matrix storage and the legacy recovery registry (`utility_snapshot_storage`, `utility_matrix_item_legacy_v5`). Original F03 ERD: `docs/diagrams/PASPM_Complete_ERD_F03_Three_Weights_v4.drawio` (preserved).

## 8. Dependencies
F01 (role, Store), F02 (Product, batches, threshold), F04 (transactions). Consumed by F05 (frozen matrix) and F06 (evidence, expiry push).

## 9. Tests and evidence
`UtilityIntegrationTest`, `WeightRulesV12IntegrationTest`, `WeightRulesV12MigrationTest`, `UtilityCalculationTest`, `ExpiryBinaryRulesTest`, `ExpiryFefoOrderingTest`, `UtilitySnapshotRoutingTest`, `RetailCatalogUtility*Test`, `F03F05RbacMatrixTest` — all part of the 23/23 script run (H2 and MySQL 8.4). Historical: [linear report](reference_v4/F03_SWAGGER_TEST_REPORT.md).

## 10. Known limitations
No snapshot list/update/delete endpoint; no database trigger enforcing immutability for direct SQL; the legacy Store expiry window is deprecated and ignored by scoring; the v1.2 source gap above.

## 11. Pending decisions
None for F03 itself. Business sign-off of the v1.2 text depends on the missing source.

## 12. Related diagrams
[Diagram index](../../diagrams/README.md) and the editable [27-page Draw.io pack](../../diagrams/PASPM_Unified_27_Diagrams_UML2_Editable.drawio) (QA: [UML27 report](../../reports/UML27_DIAGRAM_QA_2026-11-02.md)); relevant pages: Use Case UC09 and UC31, AD01 (F03 snapshot step), State R2-06.02 (Utility Snapshot), Context (M1, A2 flows), System Architecture.

## 13. Historical references
[Original v4 backlog](reference_v4/PASPM_F03_Three_Weights_v4_API_Swagger_Backlog.md), [issues](reference_v4/PASPM_F03_Three_Weights_v4_GitHub_Issues/README.md), [original F03→F05 handoff](reference_v4/F03_F05_HANDOFF.md) (stale downstream text), [legacy matrix compatibility](../../architecture/LEGACY_UTILITY_MATRIX_COMPATIBILITY.md).

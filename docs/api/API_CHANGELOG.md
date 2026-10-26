# API Changelog

## 2026-10-21 — Backend Retail Catalog access and current F03 Product Weight

- GET Product/Category list/detail now permits Store Admin and Merchandiser in their own Store. Product/Category mutations and batch/summary APIs remain StoreAdmin-only; SystemAdmin/customer remain denied.
- GET Product scores also permits Store Admin. Merchandiser-only PATCH retains manual S/M input and adds recalculated current E/W to its response. Added read-only expiryStatus/evidence/T, Store-local date/timezone, configVersion, lambdaValid/ready/warnings; nullable W explicitly represents incomplete current calculation. Existing routes and manual fields are preserved.
- Reused shared F03 calculators; no new migration, new endpoint, Frontend change, inventory mutation, historical snapshot rewrite or mining execution. Unknown score inputs/calculated fields are rejected explicitly.
- Java17 clean verify:232/232 PASS, including22 new H2/MySQL test executions and strengthened F03/F05 immutability regression. Real standalone MySQL HTTP checks32/32 PASS; interactive Swagger UI11/11 PASS. Actual runtime OpenAPI remains59 paths/73 operations.
- [Verification and actual evidence](../testing/reports/RETAIL_CATALOG_UTILITY_VERIFICATION.md), [F03 API](F03_UTILITY_API.md), [role matrix](F01_F02_RBAC_MATRIX.md).

## 2026-10-20 — F06 recommendation contract and frontend handoff

- Documented eight implemented Store-scoped operations: generation, list/detail, approve/reject, config GET/PUT and independent expiry-push GET. Merchandiser generates/reviews; Store Admin configures; both read.
- Added source-derived nested response types, nullability, exact-count support/confidence rules, frozen policy/provenance, deterministic run ranking, chronological history, optimistic review/configuration conflicts and current eligibility checks.
- Added React/TypeScript integration workflow with F01 authentication, role-specific screens, lossless numeric handling, current expiry boundaries and downstream APPROVED + readyForConsumption gate. Approval has no publishing, reservation, campaign, discount or stock side effect.
- Verified Java17 clean build:210 tests PASS (63 new F06 +147 regression), fresh/upgrade MySQL and27 Swagger UI cases. Exported actual runtime OpenAPI59 paths/73 operations. Kept F03 expiry Evidence separate from RecTransactionEvidence to preserve schema compatibility. See [verification](../testing/reports/F06_VERIFICATION.md).
- Contracts: [F06 API](F06_RECOMMENDATION_API.md), [frontend handoff](../frontend_handoff/F06_HANDOFF.md).

## 2026-10-20 — F05 P0 deployment fixes

- Corrected V7/F04 identifier quoting for MySQL8.4; V7 checksum changes from -1762009830 to -1490036030. Existing environments require controlled review/reconciliation; no automatic repair or baseline. See [recovery guide](../setup/FLYWAY_MYSQL_V7_RECOVERY.md).
- New mining admission preflight checks the same pinned SPMF2.67a JAR, independent Java25+ executable, real entrypoint and temporary storage. Both runtime paths now default empty; configure PASPM_MINING_SPMF_JAR and PASPM_MINING_JAVA_EXECUTABLE.
- Invalid dependency returns503 before creating work with actionable SPMF_* diagnostic prefixes in the existing error string. Historical reads, endpoints, DTO shapes, algorithms and numerical conversion are unchanged.
- Verified backend Java17 with real Java25 child, fresh MySQL8.4 V1–V9, persisted equal patterns, and147 tests. See [P0 report](../audits/F05_P0_DEPLOYMENT_FIX_REPORT.md) and [setup](../setup/F05_SPMF_RUNTIME_SETUP.md).

## 2026-10-19 — F05 High-Utility Mining P0

- Added eight Store-scoped operations: mining snapshot selection, asynchronous request creation/history/detail/runs, run detail/patterns/benchmark. Merchandiser creates; Merchandiser/StoreAdmin reads; SystemAdmin/EndCustomer denied.
- Added V9 using the existing MINING_REQUEST, MINING_RUN, HIGH_UTILITY_PATTERN, PATTERN_ITEM ERD entities; durable frozen inputs, idempotency, bounded worker, restart recovery and atomic normalized results.
- Integrated actual external SPMF2.67a HUI-Miner/EFIM with exact scale6/GCD conversion, shared threshold, resource limits and exact result comparison. Backend remains release17; this pinned JAR needs a Java25 child JVM.
- Exported final runtime OpenAPI (52 paths, 65 operations, 8 F05 operations); documented nullable run metrics/support and validated algorithm-array bounds. Added F05 API/F06/React handoff and real sanitized examples.
- Verified113 automated tests and23 Swagger UI cases; both algorithms produce8 equal patterns on the same96-unit fixture at minUtil30. Native MySQL V9 upgrade passed on isolated prepared baseline8. Fresh MySQL V1–V9 provisioning remains blocked by preexisting V7 unquoted row_number; original V7 unchanged.
- No Frontend/F03 scoring/F04 ingestion/F06 recommendation/P1 scheduling implementation changed.

## 2026-10-19 — Final Weight Business Rules v1.2 (breaking scoring semantics)

- Product POST/PUT accepts optional strict nonnegative integer expiryPushDays (omitted/null→0); Product responses expose it. Store Admin owns the field.
- New E is binary using the earliest future-expiry eligible INVENTORY_BATCH and Product threshold only. No Store fallback, linear scoring or quantity average.
- Store expiry-window PUT remains deprecated for compatibility; its saved value has no scoring effect.
- Preview/matrix add expiryEvidence; new snapshot configuration freezes timezone/scoring version; snapshot warnings are persisted/read. Historical rows, scores, hashes and JSON remain unchanged.
- Added GET /api/utility/expiry-push-batches for Store Admin/Merchandiser, Store-scoped, independent of Mining.
- F02 summary excludes expiry today (expired uses <=today); frontend should use the new boundary.
- Added Flyway V8 (Product threshold/check, nullable matrix evidence and snapshot warnings). F04 import/historical prices are unchanged; F05/F06 engines remain outside this change.
- Numerical fixtures: T7→E1/W1.435/17220; T3 or0→E0/W1.335/16020. See the v1.2 verification report for executed checks; prior sections below are historical release records.

## 2026-10-19 — F04 transaction data pipeline

- Added authenticated Store-scoped CSV import, import batch/error reads, and transaction list/detail APIs.
- CSV upload is `STORE_ADMIN` only; `STORE_ADMIN` and `MERCHANDISER` can read imports and transactions. Store scope is always derived from the F01 identity.
- Added response schemas for import counters, validation errors, historical transaction lines and F03 eligibility.
- Exported the running application's OpenAPI 3.1.0 document after Flyway V7 startup; six F04 routes are present in `openapi.json`.
- CSV processing is synchronous, bounded, idempotent by Store/source/order, and does not mutate inventory. Excel and external POS API synchronization remain deferred.
- Swagger UI was opened and the transaction-list `Try it out` request returned the expected `401` without a token. Authenticated import was not run because this isolated H2 environment had no Store Admin credential or matching product fixture; controller behavior is covered by MockMvc integration tests.

## 2026-10-19 — F03 weight example and snapshot verification alignment

- Clarified the existing Preview API contract and React handoff with the exact T=30 and T=10 reference calculations, including frozen historical base utility.
- Added deterministic test cases for both reference calculations, direct System Admin policy GET coverage, and old/new snapshot values after score/T/policy updates.
- Read the seven-page PASPM Weight Report and documented its unresolved production decisions and the synthetic-data boundary.
- No route, DTO, authorization, migration or runtime behavior changed. New verification ran through Maven/MockMvc; it does not claim a new interactive Swagger UI run.

## 2026-10-18 — F01 authenticated password change

- Added `POST /api/auth/password/change` for an authenticated user to verify their current password and change only their own password.
- Reuses BCrypt policy, row locking and the existing `token_version`; all existing access tokens are revoked and no replacement token is returned.
- Kept the public `PASSWORD_RESET` OTP endpoints separate and unchanged.
- Updated the F01 API contract, RBAC matrix, frontend handoff and README. Runtime OpenAPI export is maintained in `openapi.json`.

## 2026-10-17 — F03 contract and Frontend handoff verification

- Expanded `F03_UTILITY_API.md` with field-level request/response contracts, implemented routes, source-derived role rules, validations, utility/expiry math, snapshot persistence and F04/F05 dependency gaps.
- Expanded the React handoff with screen-to-route mapping, TypeScript types, form/error behavior, setup and mining handoff details; added synthetic JSON examples for implemented endpoints.
- Extended the existing shared RBAC matrix with F03 routes instead of creating a duplicate matrix.
- Compared all 11 F03 controller operations with the existing runtime-generated OpenAPI 3.1.0 export and source DTOs. The export already included all routes, so it was validated and preserved rather than replaced with a new or partial specification.
- Recorded that MockMvc tests exercise F03 runtime routes, while interactive Swagger UI “Try it out” was NOT RUN in this documentation task.
- Recorded implementation gaps: no default F04 validated transaction provider (Preview/Snapshot return 503), no snapshot-history list route, no F05 `MINING_REQUEST` FK/export, and OpenAPI response-nullability/role metadata gaps.

No Backend API behavior, source code, or database migration was changed as part of this documentation verification.

## 2026-10-17 — F03 Utility API contract

Published the implemented F03 contract and runtime OpenAPI export in `docs/api/openapi.json`.

- Added versioned global lambda policy GET/PUT for `SYSTEM_ADMIN`.
- Added Store config GET; Merchandiser weight/lambda PUT; Store Admin integer expiry-window PUT.
- Added per-Product stock/seasonal priority score GET/PATCH for `MERCHANDISER`.
- Added utility preview, immutable snapshot create/read, and paginated snapshot matrix read for `MERCHANDISER`.
- Confirmed Store scope comes from authenticated F01 identity. Cross-Store record access returns 404.
- Confirmed expiry T is a positive JSON integer; quoted numbers and fractions are invalid.
- Confirmed enabled coefficients must sum exactly to 1, and lambda is checked against the current versioned system bounds.
- Snapshot contract freezes analysis date/configuration and reports unavailable production F04 source with 503 until that dependency is implemented.

No endpoint contract changes were made as part of this documentation export; this entry records the implemented F03 API surface for Frontend integration. Existing F01/F02 contracts were not changed by the handoff documents.

## 2026-10-17 — F01/F02 API handoff documentation (documentation only)

- Added source- and runtime-verified F01 auth/user contract and F02 category/product/inventory contract.
- Added role matrix, React handoffs and synthetic JSON examples.
- No Backend API behavior, schema or migration changed in this documentation task.
- Explicitly documented missing logout/refresh/password-change flows, F02 Store Admin-only permissions, lack of CORS configuration, and inventory summary/Product-status behavior.
- OpenAPI remains the full shared runtime export; no endpoints were fabricated.

### Contract metadata and integration gaps

- OpenAPI `info.version`: `0.1.0`; F01/F02 handoff checked against the 2026-10-17 runtime export.
- Missing F01 endpoints: logout, refresh and self-profile editing. Authenticated password change and OTP reset are implemented in source; Google operations are conditional on external Google/SMTP configuration.
- Missing F02 endpoints: deletion and stock adjustment/movement history. No cross-origin CORS mapping is present in current Backend source.
- OpenAPI does not mark nullable `UserView.name`, `UserView.storeId`, `ProductView.category`, or `BatchView.expiryDate`; handoff types document actual null behavior.

## 2026-10-26 — F04 CSV staging validation correction

No endpoint, DTO, authorization or idempotency-key change. Invalid overlong order IDs use NULL in the staging lookup projection while raw row evidence remains exact; unrelated valid orders return the existing 201 summary contract. See [F04 improvement report](../features/F04/F04_IMPROVEMENT_REPORT.md). F04 is file-based CSV/Excel scope; external API/POS ingestion is excluded.

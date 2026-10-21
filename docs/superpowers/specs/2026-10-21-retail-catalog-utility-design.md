# Backend Catalog access + current F03 Product priorities

Date: 2026-10-21, Asia/Ho_Chi_Minh. Status: approved by user on 2026-10-21 ("Duyệt, triển khai Backend"). User narrowed scope to Backend APIs only; no Frontend source changes.

## Root cause and existing boundaries

No AGENTS.md was found in the workspace or inspected parents. CodeGraph traced CatalogAccess/StoreScope, ProductScoreService/Repository and shared F03 calculation dependencies. ProductController and CategoryController currently restrict all operations, including GET, to STORE_ADMIN. Merchandiser therefore cannot load the real F02 Catalog. ProductScoreController currently permits only Merchandiser GET/PATCH and returns only S/M. Existing transaction preview cannot return a current W for Products without source transactions. Weight config already has the correct ownership: Store Admin/Merchandiser read; Merchandiser writes; only SystemAdmin controls lambda bounds.

Retail is a portal label, not an additional F01 Backend role. Both existing Frontends also have demo-only access guards/authentication, but those are outside the user's clarified scope and will not be changed or claimed fixed.

## Approaches

1. **Recommended:** reuse current Product/Category GET and score GET/PATCH, extend the current score response with computed current values. This fixes Backend access, supports Products without transactions and creates no duplicate route.
2. New Product-priority endpoint: avoid because it duplicates the existing score read.
3. Use only transaction preview: insufficient because Products without source transactions have no matrix row.

## Authorized operations

| Operation | STORE_ADMIN | MERCHANDISER | SYSTEM_ADMIN / END_CUSTOMER |
|---|---|---|---|
| Product GET list/detail | Read own Store | Read own Store | Denied |
| Category GET list/detail | Read own Store | Read own Store | Denied |
| Product/Category mutations | Existing permissions retained | Denied | Denied |
| Product scores GET | Read own Store | Read own Store | Denied |
| Product scores PATCH | Denied | Edit S/M in own Store | Denied |
| Utility config GET | Existing read | Existing read | Denied |
| Utility config PUT | Denied | Existing write | Denied |
| System lambda policy GET/PUT | Denied | Denied | Only SYSTEM_ADMIN |

Store scope remains derived from F01 identity via CatalogAccess/StoreScope. Existing foreign-record404 and list isolation remain. No client Store selector is introduced. Batch APIs and inventory write permissions remain unchanged.

## Existing score response extension

GET/PATCH `/api/utility/products/{productId}/scores` retain productId, stockPriorityScore and seasonalPriorityScore and add read-only:

- expiryScore: decimal|null, shared current E.
- weightValue: decimal|null, shared current W.
- expiryStatus: string and expiryEvidence: shared ExpiryScoreCalculator.Evidence.
- expiryPushDays: integer, Product-level threshold.
- asOfDate: Store-local current ISO date; timeZone: configured Store timezone.
- configVersion: Store utility config version.
- lambdaValid: boolean against current System policy.
- ready: boolean, whether a valid current Weight can be returned.
- warnings: string array explaining unavailable values.

Read current Product, batches, scores/config/policy coherently in a repeatable-read transaction. PATCH saves supplied S/M then returns the recalculated read model in the same transaction. Reuse ExpiryScoreCalculator and UtilityMath; no formula duplication, new migration or configuration table. Product without transactions still has a current result.

Invalid current lambda or missing enabled scores/required expiry metadata gives weightValue=null, ready=false and explicit warnings. Do not clamp, invent scores or use zero for an unknown E. All criteria disabled yields W=1 when current policy validation succeeds. Preserve shared non-expiring, disabled-threshold, expired/blocked-batch and inactive-Product rules. Current results are previews, never historical snapshot rewrites.

PATCH accepts only manual stockPriorityScore/seasonalPriorityScore, existing partial semantics, [0,1], at most six fractional digits, no explicit null. Calculated fields, stock quantities, expiry thresholds, coefficients and storeId are not score writes; reject unknown fields. No new optimistic version contract is introduced: existing atomic partial SQL updates and config locking are retained.

## Verification

- First reproduce Merchandiser Product/Category GET403 and absent computed score fields with failing Backend tests, then apply narrow fixes.
- Test role/store matrix, anonymous401, foreign404, list/filter isolation, forged Store ID, denied Product/Category mutations and StoreAdmin S/M/config writes.
- Test score0/1, partial updates, invalid ranges/null/precision/calculated fields. Fixture S0.8/M0.9/E1, alpha0.5/beta0.2/gamma0.3/lambda0.5 must return W1.435; E0 returns1.335. Cover no transactions, unavailable metadata, invalid lambda and all-disabled criteria.
- Verify existing inventory/F03 immutable snapshot/F05 run-pattern state is unchanged after score/config changes. Run relevant regressions and Maven build, with isolated MySQL verification where available.
- Verify actual Swagger/OpenAPI and representative API calls against an isolated development runtime; export actual schema and update API contract, RBAC matrix and Backend handoff. Never expose credentials or reset shared data.

## Scope and baseline

No Frontend implementation, GitHub push/merge, F04/F05 algorithms, inventory mutation, PRODUCT_LOT, old migration edit or historical snapshot rewrite. Preserve existing dirty application.yml and unrelated documents.

Before changes: 25 relevant Backend tests PASS (CatalogIntegrationTest, UtilityIntegrationTest, UtilityCalculationTest); existing FE25 tests and FE build also PASS, solely baseline checks before scope was narrowed. No new feature tests or API changes have been implemented yet.

<!-- docs-reorganization:notice -->
> **NEEDS_REVIEW — documentation review 2026-10-26.** Endpoint coverage counts/prose describe older test stages and do not certify every current route. See [current navigation](README.md). Original results and business-rule text below are retained; this notice is not a new execution or approval.
<!-- /docs-reorganization:notice -->

> **2026-11-15 verification (branch `Khoi/feat/f05-progress-f03-weight-v2` @ `c8c837a`).** The F02 batch, F03, F05 and season rows were re-checked against code (paths relative to `src/main/java/vn/edu/fpt/paspm/`): `security/SecurityConfig.java:93-95` (system mining → SYSTEM_ADMIN; `POST /api/mining/requests` → MERCHANDISER; benchmark `denyAll`); `mining/MiningController.java:18`, `:24`; `mining/MiningProgressController.java:22-29`; `utility/SeasonController.java:15`, `:19`, `:22`, `:34` (class-level MERCHANDISER, reads STORE_ADMIN + MERCHANDISER); `utility/UtilityConfigurationController.java:17-30`; `utility/ProductScoreController.java:11`, `:16`, `:20`; `catalog/inventory/InventoryBatchController.java:26`. Executable check: `F03F05RbacMatrixTest` (42 routes) in the final gate. Older coverage prose below stays NEEDS_REVIEW.

> **Update 2026-11-27 (smoke test, ROC-20261127-003).** Both F05 benchmark routes were removed on 2026-11-16 (DEC-20261116-04) and are no longer mapped: every signed-in role gets **404**, not 403. The `denyAll` wording in the paragraph above, and "2 legacy routes forbidden" in the readiness table, describe the state on 2026-11-15 and are historical. The two benchmark rows below are corrected.

# F01 / F02 / F03 / F04 / F05 / F06 / F09 RBAC Matrix

Derived from controller `@PreAuthorize`, `SecurityConfig`, `StoreScope`, and feature service/repository checks (updated 2026-10-23 for F06; F05 rows added 2026-11-02 and generated from the runtime OpenAPI; F04 rows added 2026-11-02 from the controller annotations and `F04_TRANSACTION_API.md`, effective rules checked by `F03F05RbacMatrixTest`). “Public” means the Spring security route is explicitly permitted; “authenticated” requires a valid active bearer token. An authenticated-but-wrong role is 403. Store-scoped foreign or absent resources are hidden as 404 after the controller role gate.

| Feature | Endpoint | System Admin | Store Admin | Merchandiser | End Customer |
|---|---|---|---|---|---|
| F01 Login | POST `/api/auth/login` | Public | Public | Public | Public |
| F01 Refresh session | POST `/api/auth/refresh` | Public; refresh cookie + CSRF required | same | same | same |
| F01 Logout current session | POST `/api/auth/logout` | Authenticated own session + CSRF | same | same | same |
| F01 Logout all sessions | POST `/api/auth/logout-all` | Authenticated own account + CSRF | same | same | same |
| F01 Self-registration OTP request | POST `/api/auth/register` | Public | Public | Public | Public |
| F01 Complete self-registration | POST `/api/auth/register/complete` | Public (eligible pending account only) | same | same | same |
| F01 OTP request | POST `/api/auth/otp/request` | Public (eligible pending account) | same | same | same |
| F01 OTP resend | POST `/api/auth/otp/resend` | Public (eligible pending account) | same | same | same |
| F01 Verify self-registration OTP | POST `/api/auth/otp/verify` | Public | Public | Public | Public |
| F01 Activate invited account | POST `/api/auth/activate` | Public (valid invitation OTP) | same | same | same |
| F01 Google registration | POST `/api/auth/google/register` | Public, Google config required | same | same | same |
| F01 Google login | POST `/api/auth/google` | Public, linked identity/config required | same | same | same |
| F01 Link Google identity | POST `/api/auth/google/link` | Authenticated own account | Authenticated own account | Authenticated own account | Authenticated own account |
| F01 Current user | GET `/api/auth/me` | Authenticated own account | same | same | same |
| F01 Change own password | POST `/api/auth/password/change` | Authenticated own account; current password required | same | same | same |
| F01 User list | GET `/api/users` | Allowed, global | Forbidden | Forbidden | Forbidden |
| F01 User detail | GET `/api/users/{id}` | Allowed, global | Forbidden | Forbidden | Forbidden |
| F01 Invite/create account | POST `/api/users` | Allowed; role/Store constraints apply (409 `STORE_INACTIVE` for an INACTIVE Store) | Forbidden | Forbidden | Forbidden |
| F01 Store list / picker | GET `/api/system/stores` | Allowed; ACTIVE by default (`status=INACTIVE\|ALL`) | Forbidden | Forbidden | Forbidden |
| F01 Create Store | POST `/api/system/stores` | Allowed | Forbidden | Forbidden | Forbidden |
| F01 Store detail / update | GET, PUT `/api/system/stores/{storeId}` | Allowed (any Store) | Forbidden | Forbidden | Forbidden |
| F01 Deactivate / reactivate Store | PATCH `/api/system/stores/{storeId}/status` | Allowed | Forbidden | Forbidden | Forbidden |
| F01 Own Store profile | GET `/api/store/profile` | Forbidden | Own Store | Own Store | Forbidden |
| F01 Edit own Store contact | PUT `/api/store/profile` | Forbidden | Own Store (address, phone) | Forbidden | Forbidden |
| F01 Read Store image | GET `/api/stores/{storeId}/images/{kind}` | Any Store (INACTIVE included) | Own Store; other Store 404 | Own Store; other Store 404 | Forbidden |
| F01 Upload / remove Store image | PUT, DELETE `/api/stores/{storeId}/images/{kind}` | Any Store | Own Store; other Store 404 | Forbidden | Forbidden |
| F01 Public Store image | GET `/api/public/stores/{storeId}/images/{fileName}` | Public (ACTIVE Store, current file only) | same | same | same |
| All authenticated routes | — | — | 403 `STORE_INACTIVE` when the own Store is INACTIVE | same | — |
| F01 Update account name | PUT `/api/users/{id}` | Allowed, global | Forbidden | Forbidden | Forbidden |
| F01 Activate/deactivate | PATCH `/api/users/{id}/status` | Allowed, global; OTP cannot be bypassed | Forbidden | Forbidden | Forbidden |
| F01 Assign role/Store | PUT `/api/users/{id}/role` | Allowed; valid role/store scope | Forbidden | Forbidden | Forbidden |
| F01 List roles | GET `/api/roles` | Allowed | Forbidden | Forbidden | Forbidden |
| F02 List categories | GET `/api/categories` | Forbidden by controller role | Allowed, own Store | Allowed, own Store only | Forbidden |
| F02 Category detail | GET `/api/categories/{id}` | Forbidden | Allowed, own Store; foreign→404 | Allowed, own Store; foreign record→404 | Forbidden |
| F02 Create category | POST `/api/categories` | Forbidden | Allowed, own Store | Forbidden | Forbidden |
| F02 Update category | PUT `/api/categories/{id}` | Forbidden | Allowed, own Store; foreign→404 | Forbidden | Forbidden |
| F02 Category status | PATCH `/api/categories/{id}/status` | Forbidden | Allowed, own Store; foreign→404 | Forbidden | Forbidden |
| F02 List/search products | GET `/api/products` | Forbidden | Allowed, own Store only | Allowed, own Store only | Forbidden |
| F02 Product detail | GET `/api/products/{id}` | Forbidden | Allowed, own Store; foreign→404 | Allowed, own Store; foreign record→404 | Forbidden |
| F02 Create product | POST `/api/products` | Forbidden | Allowed, Store inferred from token | Forbidden | Forbidden |
| F02 Update product | PUT `/api/products/{id}` | Forbidden | Allowed, own Store; foreign→404 | Forbidden | Forbidden |
| F02 Product status | PATCH `/api/products/{id}/status` | Forbidden | Allowed, own Store; foreign→404; soft-deleted→404 | Forbidden | Forbidden |
| F02 Delete product (soft, 2026-11-24, DEC-20261124-03) | DELETE `/api/products/{id}` | Forbidden | Allowed, own Store only; foreign / unknown / already deleted→404; 409 `PRODUCT_HAS_SELLABLE_STOCK` or `PRODUCT_IN_LIVE_CAMPAIGN` | Forbidden | Forbidden |
| F02 List batches | GET `/api/products/{id}/batches` | Forbidden | Allowed, own Store; foreign→404 | Forbidden | Forbidden |
| F02 Create batch (optional `pushDay` ≤ expiry, 2026-11-15) | POST `/api/products/{id}/batches` | Forbidden | Allowed, own Store product | Forbidden | Forbidden |
| F02 Batch detail | GET `/api/inventory-batches/{id}` | Forbidden | Allowed, own Store; foreign→404 | Forbidden | Forbidden |
| F02 Update batch (`remainingQuantity`, `status`, `pushDay` since 2026-11-15) | PATCH `/api/inventory-batches/{id}` | Forbidden | Allowed, own Store; foreign→404 | Forbidden | Forbidden |
| F02 Inventory summary | GET `/api/products/{id}/inventory-summary` | Forbidden | Allowed, own Store; foreign→404 | Forbidden | Forbidden |
| F01 Password reset request | POST `/api/auth/password-reset/request` | Public | Public | Public | Public |
| F01 Password reset confirmation | POST `/api/auth/password-reset/confirm` | Public, valid OTP required | Public, valid OTP required | Public, valid OTP required | Public, valid OTP required |
| F01 Avatar read | GET `/api/users/{id}/avatar` | Own account only | Own account only | Own account only | Own account only |
| F01 Avatar replace | PUT `/api/users/me/avatar` | Own account only | Own account only | Own account only | Own account only |
| F01 Avatar remove | DELETE `/api/users/me/avatar` | Own account only | Own account only | Own account only | Own account only |
| F03 Lambda policy | GET `/api/system/utility/lambda-policy` | Allowed, global | Forbidden | Forbidden | Forbidden |
| F03 Lambda policy (W2: `{lambdaMax}` only) | PUT `/api/system/utility/lambda-policy` | Allowed, global | Forbidden | Forbidden | Forbidden |
| F03 Store utility config | GET `/api/utility/config` | Forbidden | Allowed, own Store | Allowed, own Store | Forbidden |
| F03 Weight config α, β, λ, N (W2) | PUT `/api/utility/config` | Forbidden | Forbidden | Allowed, own Store | Forbidden |
| ~~F03 Legacy Store window~~ — **route deleted 2026-11-16** ([DEC-20261116-03](../decisions/2026-11-16/DEC-20261116-03-p6-delete-obsolete-routes.md)); 404 for every authenticated role (410 until then) | ~~PUT `/api/utility/config/expiry-window`~~ | — | — | — | — |
| F03 Product score read | GET `/api/utility/products/{productId}/scores` | Forbidden | Allowed, own Store | Allowed, own Store; foreign Product→404 | Forbidden |
| ~~F03 Product score update~~ — **route deleted 2026-11-16**; GET stays, so PATCH is 405 for every authenticated role (410 until then) | ~~PATCH `/api/utility/products/{productId}/scores`~~ | — | — | — | — |
| F03 Season list/read | GET `/api/utility/seasons`, `/api/utility/seasons/{seasonId}`, `/api/utility/seasons/{seasonId}/products` | Forbidden | Allowed, own Store | Allowed, own Store; foreign season→404 | Forbidden |
| F03 Season write | POST `/api/utility/seasons`; PUT/DELETE `/api/utility/seasons/{seasonId}` | Forbidden | Forbidden | Allowed, own Store; foreign→404 | Forbidden |
| F03 Season assignment | PUT/DELETE `/api/utility/seasons/{seasonId}/products/{productId}` | Forbidden | Forbidden | Allowed, own Store; foreign product/season→404 | Forbidden |
| F03 Utility preview | POST `/api/utility/preview` | Forbidden | Forbidden | Allowed, own Store; F04 source required | Forbidden |
| F03 Snapshot create | POST `/api/utility/snapshots` | Forbidden | Forbidden | Allowed, own Store; F04 source required | Forbidden |
| F03 Snapshot detail | GET `/api/utility/snapshots/{id}` | Forbidden | Forbidden | Allowed, own Store; foreign snapshot→404 | Forbidden |
| F03 Snapshot matrix | GET `/api/utility/snapshots/{id}/matrix` | Forbidden | Forbidden | Allowed, own Store; foreign snapshot→404 | Forbidden |
| F03 Independent expiry pushes | GET `/api/utility/expiry-push-batches` | Forbidden | Allowed, own Store | Allowed, own Store | Forbidden |
| F03 Snapshot history list | GET `/api/utility/snapshots` | NOT IMPLEMENTED | NOT IMPLEMENTED | NOT IMPLEMENTED | NOT IMPLEMENTED |
| F04 CSV import | POST `/api/transactions/imports/csv` | Forbidden | Allowed, own Store (multipart `file`) | Forbidden | Forbidden |
| F04 Excel import | POST `/api/transactions/imports/excel` | Forbidden | Allowed, own Store (multipart `file`, `.xlsx`/`.xls`) | Forbidden | Forbidden |
| F04 Import list | GET `/api/transactions/imports` | Forbidden | Allowed, own Store | Allowed, own Store | Forbidden |
| F04 Import detail | GET `/api/transactions/imports/{id}` | Forbidden | Allowed, own Store; foreign→404 | Allowed, own Store; foreign→404 | Forbidden |
| F04 Import errors | GET `/api/transactions/imports/{id}/errors` | Forbidden | Allowed, own Store; foreign→404 | Allowed, own Store; foreign→404 | Forbidden |
| F04 Transaction list | GET `/api/transactions` | Forbidden | Allowed, own Store | Allowed, own Store | Forbidden |
| F04 Transaction detail | GET `/api/transactions/{id}` | Forbidden | Allowed, own Store; foreign→404 | Allowed, own Store; foreign→404 | Forbidden |
| F05 | GET `/api/mining/requests` | Forbidden | Allowed, own Store; foreign→404 | Allowed, own Store; foreign→404 | Forbidden |
| F05 | POST `/api/mining/requests` (since 2026-11-15, P1) | Forbidden | Forbidden | Allowed, own Store; current System Admin mining configuration; 409 if snapshot λ > current λ_max | Forbidden |
| F05 | GET `/api/mining/requests/{id}/progress` (2026-11-15) | Forbidden | Allowed, own Store; foreign→404 | Allowed, own Store; foreign→404 | Forbidden |
| F05 | GET `/api/mining/requests/{id}` | Forbidden | Allowed, own Store; foreign→404 | Allowed, own Store; foreign→404 | Forbidden |
| F05 | GET `/api/mining/requests/{id}/runs` | Forbidden | Allowed, own Store; foreign→404 | Allowed, own Store; foreign→404 | Forbidden |
| F05 | GET `/api/mining/runs/{id}` | Forbidden | Allowed, own Store; foreign→404 | Allowed, own Store; foreign→404 | Forbidden |
| F05 | GET `/api/mining/runs/{id}/benchmark` (**removed**, DEC-20261116-04) | Not mapped → **404** | Not mapped → **404** | Not mapped → **404** (observed on the 2026-11-27 smoke test) | Not mapped → **404** |
| F05 | GET `/api/mining/runs/{id}/patterns` | Forbidden | Allowed, own Store; foreign→404 | Allowed, own Store; foreign→404 | Forbidden |
| F05 | GET `/api/mining/snapshots` | Forbidden | Allowed, own Store; foreign→404 | Allowed, own Store; foreign→404 | Forbidden |
| F05 | GET `/api/system/mining/config` | Allowed | Forbidden | Forbidden | Forbidden |
| F05 | PUT `/api/system/mining/config` | Allowed | Forbidden | Forbidden | Forbidden |
| F05 | POST `/api/system/mining/requests` | Allowed | Forbidden | Forbidden | Forbidden |
| F05 | GET `/api/system/mining/schedules` | Allowed | Forbidden | Forbidden | Forbidden |
| F05 | POST `/api/system/mining/schedules` | Allowed | Forbidden | Forbidden | Forbidden |
| F05 | GET `/api/system/mining/schedules/{id}` | Allowed | Forbidden | Forbidden | Forbidden |
| F05 | PUT `/api/system/mining/schedules/{id}` | Disable/edit allowed; `enabled=true` → **503** | Forbidden | Forbidden | Forbidden |
| F05 | GET `/api/system/mining/schedules/{id}/occurrences` | Allowed | Forbidden | Forbidden | Forbidden |
| F05 | GET `/api/system/mining/schedules/{id}/occurrences/{occurrenceId}` | Allowed | Forbidden | Forbidden | Forbidden |
| F05 | GET `/api/system/mining/stores/{storeId}/requests` | Allowed by default (D1 `REQUESTS`); 403 if the operator removes this group | Forbidden | Forbidden | Forbidden |
| F05 | GET `/api/system/mining/stores/{storeId}/requests/{requestId}` | Allowed by default (D1 `REQUESTS`); 403 if the operator removes this group | Forbidden | Forbidden | Forbidden |
| F05 | GET `/api/system/mining/stores/{storeId}/requests/{requestId}/progress` (2026-11-15) | Allowed by default (D1 `REQUESTS`), audited; 403 if the operator removes this group | Forbidden | Forbidden | Forbidden |
| F05 | GET `/api/system/mining/stores/{storeId}/requests/{requestId}/runs` | Allowed by default (D1 `RUNS`); 403 if the operator removes this group | Forbidden | Forbidden | Forbidden |
| F05 | GET `/api/system/mining/stores/{storeId}/runs/{runId}/benchmark` (**removed**, DEC-20261116-04) | Not mapped → **404** | Not mapped → **404** | Not mapped → **404** | Not mapped → **404** |
| F05 | GET `/api/system/mining/stores/{storeId}/runs/{runId}/patterns` | **403 by default** (D1 group `PATTERNS`); allowed only if the instance lists the group | Forbidden | Forbidden | Forbidden |
| F05 | GET `/api/system/mining/stores/{storeId}/snapshots` | Allowed by default (D1 `SNAPSHOTS`); 403 if the operator removes this group | Forbidden | Forbidden | Forbidden |
| F06 Generate recommendations | POST `/api/recommendations/generate` | Forbidden | Forbidden | Allowed, own Store; completed own-Store mining run required | Forbidden |
| F06 List recommendations | GET `/api/recommendations` | Forbidden | Allowed, own Store | Allowed, own Store | Forbidden |
| F06 Recommendation detail | GET `/api/recommendations/{id}` | Forbidden | Allowed, own Store; foreign→404 | Allowed, own Store; foreign→404 | Forbidden |
| F06 Approve recommendation | POST `/api/recommendations/{id}/approve` | Forbidden | Forbidden | Allowed, own Store; pending state/version and current eligibility required | Forbidden |
| F06 Reject recommendation | POST `/api/recommendations/{id}/reject` | Forbidden | Forbidden | Allowed, own Store; pending state/version required | Forbidden |
| F06 Read recommendation policy | GET `/api/recommendations/config` | Forbidden | Allowed, own Store | Allowed, own Store | Forbidden |
| F06 Update recommendation policy | PUT `/api/recommendations/config` | Forbidden | Allowed, own Store; expectedVersion required | Forbidden | Forbidden |
| F06 Independent expiry push | GET `/api/recommendations/expiry-push` | Forbidden | Allowed, own Store | Allowed, own Store | Forbidden |
| F09 public Store list | GET `/api/storefront/stores` | Public, ACTIVE Stores only | Public | Public | Public |
| F09 public Homepage | GET `/api/storefront/{storeSlug}/home` | Public; INACTIVE/unknown slug → 404 | Public | Public | Public |
| F09 public Product list/detail | GET `/api/storefront/{storeSlug}/products[/{productId}]` | Public; only ACTIVE, non-deleted Products of that ACTIVE Store | Public | Public | Public |

## Authorization details

Role is global to the account; `STORE_ADMIN` and `MERCHANDISER` have a `store_id`, while `SYSTEM_ADMIN` and `END_CUSTOMER` are global/no Store. Store Admin and Merchandiser assignment requires an existing Store. System Admin and End Customer assignment requires no Store. Public self-registration accepts only email and always creates `END_CUSTOMER`; request fields such as `roleCode` are rejected as unknown. System Admin can invite privileged roles and assign roles/Stores through protected user management endpoints.

F01 `/api/users` is global System Admin administration, so it can list/read/manage accounts across Stores. F02 does **not** grant System Admin access: Product/Category GET list/detail permits Store Admin/Merchandiser, while all mutations and inventory endpoints stay StoreAdmin-only, even though the shared StoreScope helper contains a System Admin branch. Do not infer endpoint permission from helper behavior alone.

F09 public GET routes are explicitly permitted without a JWT (`security/SecurityConfig.java`) and resolve Store scope only by the slug of an ACTIVE Store (DEC-20261125-04). They return a separate public DTO. There is no F09 write route; Store status and slug are managed through F01 Store management.

F03 policy routes are explicitly System Admin-only. F03 Store settings use distinct permissions: Store Admin owns batch `pushDay`; Merchandiser changes enabled criteria, coefficients and selected lambda. Both can read the combined Store config. Product score reads permit both Store roles; score PATCH and Preview/snapshot operations remain Merchandiser-only. Current score reads return E/W without transaction history; invalid current lambda yields200 with ready=false/weightValue=null rather than clamping. All Store-bound F03 calls derive Store identity from the authenticated user; request bodies and query strings cannot select another Store. A tightened lambda policy leaves existing Store configuration unchanged and is surfaced through `lambdaValid=false`; Preview/Snapshot rejects the stale lambda with 409 until a Merchandiser updates it.

The database identity claims one Store and no request accepts a Store selector. Repositories scope category/product/batch reads and writes through the authenticated Store. F02 cross-Store records resolve to 404. Missing token is 401; authenticated insufficient role is 403. OpenAPI contains a global bearer scheme but Springdoc does not expose these role rules per operation; this source-derived table is authoritative for frontend navigation.

## Readiness evidence

**Current (branch `Khoi/feat/f05-progress-f03-weight-v2` @ `c8c837a`): 124 operations — F01 27, F02 15, F03 20, F04 8, F05 26, F06 8, F07/F08 20.** Historical: the runtime OpenAPI export recorded 27 F01, 15 F02, 12 F03, 6 F04, 24 F05 and 8 F06 operations (92 total). The original F01/F02 table below was a historical baseline; existing tests exercise F01 login/current-user/self-registration/OTP/invitation/password-change/role and token-revocation behavior, and F02 authentication, category/product, batch, summary and Store isolation behavior; not every operation is separately exercised in a live browser. Current F01 refresh tests cover rotation/replay, concurrent use, CSRF, cookie policy and per-device logout. F02 browser verification from the prior F02 QA run is summarized in `docs/PASPM_F02_Inventory_Batch_Backlog_v3/F02_SWAGGER_TEST_REPORT.md`.

Integration-ready: F01 access/refresh, password/OTP/user APIs and F02 all 15 operations. Google operations are conditional on the Google client ID, mail delivery and valid Google-issued ID tokens. Browser cross-origin use is blocked until a same-origin proxy or deployment CORS policy is provided; Backend source contains no CORS mapping. General self-profile editing beyond avatar operations is not implemented. F02 delete and stock-movement/adjustment history APIs do not exist and are not implied by the implemented contract.

## Endpoint readiness counts

| Feature | Implemented | Contract verified (source + runtime OpenAPI) | Ready for Frontend integration | Conditional / blocked at runtime | Not implemented workflows outside these endpoints |
|---|---:|---:|---:|---|---|
| F01 | 27 | 27 routes in runtime export | 27 implemented | Google register/login/link require Google client configuration and mail for OTP; in the default unconfigured setup those return 503 | general profile editing beyond avatar |
| F02 | 15 | 15 | 15 | Browser cross-origin integration needs a same-origin proxy or deployment CORS policy; API endpoints themselves are available | delete endpoints, stock adjustment/movement history |
| F03 | 12 | 12 | 12 (one deprecated) | Preview/Snapshot need persisted eligible F04 rows and valid config; snapshot reads need a known ID | snapshot history list |
| F04 | 6 | 6 | 6 | Requires valid Store-scoped transaction data | Excel import, external POS synchronization |
| F05 | 23 | 23 | 16 open to integration; 5 System Admin Store reads gated (D1, 403), 2 legacy routes forbidden (2026-11-15; removed since, now 404) | Mining execution requires the configured SPMF runtime; schedule activation returns 503 | Store list for System Admin (D2), recurring scheduling |
| F06 | 8 | 8 | 8 (backend handoff) | Generation requires a completed own-Store F05 run; expiry-push is independent of Mining | frontend implementation, scheduling, reservation, campaign/publication workflows |

“Contract verified” means route, method, request/response model and security declaration were compared with the maintained OpenAPI plus controller/service source. Existing MockMvc tests cover F01 authentication/OTP/invite/password change/token revocation and F02 CRUD/Store isolation/summary. F06's recorded verification is in [F06_VERIFICATION.md](../testing/reports/F06_VERIFICATION.md); its 27 Swagger UI cases are recorded evidence, not a new UI execution in this documentation update. Current v1.2 verification ran `mvn -Dpaspm.avatar.directory=/tmp/paspm-test-avatars -Dpaspm.v12.export-dir=/tmp/paspm-v12-examples verify`; see its report for actual results. Historical baseline coverage counts below are not new interactive-execution claims.

**Legacy (superseded 2026-11-15 by Weight v2, 20 operations):** F03 v1.2 contains12 operations including the independent Store-scoped expiry-push query. F04_DATABASE_V1 is connected to persisted validated transaction data; the older unimplemented-provider limitation no longer applies. New Preview/Snapshot use Product-level binary expiry and frozen evidence; old snapshots remain readable. F05 provides eight Store-scoped mining operations. F06 provides eight Store-scoped recommendation operations; Merchandiser generates/reviews, Store Admin manages policy, both read own-Store recommendations, and System Admin/End Customer are denied. F06's expiry-push endpoint does not depend on Mining. Frontend integration remains unrun; see the F06 verification report for its separate execution channels and limitations.

## Per-operation readiness matrix

“Verified” below means implementation and runtime OpenAPI contract agree. “Test execution” counts existing Maven MockMvc/service tests from the current `mvn -q verify`, not Swagger UI activity in this documentation task. The F01 coverage count is conservative: 14 of 18 operation paths were directly requested by tests; the remaining four are called out in Notes (the OTP request alias shares a handler with resend). All 15 F02 operation paths appear in CatalogIntegrationTest requests, including authorization and cross-Store negative requests.

| Feature | Endpoint | Implemented | Verified | FE Ready | Test execution / notes |
|---|---|---|---|---|---|
| F01 | `GET /api/users/{id}` | PASS | PASS | Yes | No direct MockMvc request in current tests. |
| F01 | `PUT /api/users/{id}` | PASS | PASS | Yes | No direct MockMvc request in current tests. |
| F01 | `PUT /api/users/{id}/role` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `GET /api/products/{id}` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `PUT /api/products/{id}` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `GET /api/categories/{id}` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `PUT /api/categories/{id}` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `GET /api/users` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `POST /api/users` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `GET /api/products` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `POST /api/products` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `GET /api/products/{id}/batches` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `POST /api/products/{id}/batches` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `GET /api/categories` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `POST /api/categories` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `POST /api/auth/register` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `POST /api/auth/register/complete` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `POST /api/auth/otp/verify` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `POST /api/auth/otp/resend` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `POST /api/auth/otp/request` | PASS | PASS | Yes | Alias shares the tested OTP handler with /resend; this path was not called directly. |
| F01 | `POST /api/auth/login` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `POST /api/auth/google` | PASS | PASS | Conditional: Google/SMTP configuration. | Google disabled test returns 503; successful Google sign-in requires configuration. |
| F01 | `POST /api/auth/google/register` | PASS | PASS | Conditional: Google/SMTP configuration. | Google disabled test returns 503; registration logic also has service-level tests. |
| F01 | `POST /api/auth/google/link` | PASS | PASS | Conditional: Google/SMTP configuration. | Only unauthenticated 401 exercised; authorized link flow not tested. |
| F01 | `POST /api/auth/activate` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `PATCH /api/users/{id}/status` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `PATCH /api/products/{id}/status` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `GET /api/inventory-batches/{id}` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `PATCH /api/inventory-batches/{id}` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `PATCH /api/categories/{id}/status` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `GET /api/roles` | PASS | PASS | Yes | No direct MockMvc request in current tests. |
| F02 | `GET /api/products/{id}/inventory-summary` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `GET /api/auth/me` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F06 | `POST /api/recommendations/generate` | PASS | PASS | Yes | Merchandiser only; recorded F06 automated verification and Swagger UI evidence. |
| F06 | `GET /api/recommendations` | PASS | PASS | Yes | Store Admin/Merchandiser, own Store; recorded F06 pagination and isolation coverage. |
| F06 | `GET /api/recommendations/{id}` | PASS | PASS | Yes | Store Admin/Merchandiser, own Store; foreign records return 404. |
| F06 | `POST /api/recommendations/{id}/approve` | PASS | PASS | Yes | Merchandiser only; pending/version and live eligibility checks. |
| F06 | `POST /api/recommendations/{id}/reject` | PASS | PASS | Yes | Merchandiser only; pending/version check; rejection allowed when ineligible. |
| F06 | `GET /api/recommendations/config` | PASS | PASS | Yes | Store Admin/Merchandiser, own Store. |
| F06 | `PUT /api/recommendations/config` | PASS | PASS | Yes | Store Admin only; own Store and expectedVersion required. |
| F06 | `GET /api/recommendations/expiry-push` | PASS | PASS | Yes | Store Admin/Merchandiser; current Store-local date, independent of Mining. |

Reconciled F01 on 2026-11-12: logout/logout-all require the active JWT family and its stored CSRF cookie/header, without requiring the raw refresh cookie. V20 retained; V21 additive binding. No Store/role permissions changed.

## 2026-11-15 progress routes (Checkpoint 1 note; committed as `3bdfee3` on the feature branch and now also in the main table above)

| Route | SYSTEM_ADMIN | STORE_ADMIN | MERCHANDISER | END_CUSTOMER |
|---|---|---|---|---|
| GET `/api/mining/requests/{id}/progress` | 403 | Own Store | Own Store | 403 |
| GET `/api/system/mining/stores/{storeId}/requests/{requestId}/progress` | REQUESTS group, audited | 403 | 403 | 403 |

Anonymous requests: 401. Foreign Store records: 404 after authorization. New POST Store submission is Merchandiser-only; no algorithm/minUtil overrides; the System Admin route remains unchanged. Earlier F05 23-row inventory is historical; two progress routes are additive. REQUESTS is default-enabled, not default-denied; an operator can remove it.

## 2026-11-16 — Additive F02 route
| Method / path | SYSTEM_ADMIN | STORE_ADMIN | MERCHANDISER | END_CUSTOMER | Anonymous |
|---|---|---|---|---|---|
| DELETE /api/inventory-batches/{id}/push-day | 403 | 200 own Store; 404 foreign/unknown | 403 | 403 | 401 |
Regenerated OpenAPI verified: 103 paths / 125 operations (124 baseline + this route).

## 2026-11-26 — F10 customer cart

| Method / path (under `/api/storefront/{storeSlug}/cart`) | END_CUSTOMER | SYSTEM_ADMIN / STORE_ADMIN / MERCHANDISER | Anonymous |
|---|---|---|---|
| GET cart root | 200 own cart; 404 unknown/INACTIVE Store | 403 | 401 |
| POST `/items` | 200 own cart; 404 foreign or hidden Product | 403 | 401 |
| PATCH or DELETE `/items/{itemId}` | 200 own cart; 404 foreign or unknown line | 403 | 401 |
| DELETE cart root | 200 own cart; 404 unknown/INACTIVE Store | 403 | 401 |
| POST `/bundles` | 200 own cart; 404 foreign or invalid key; 409 stale key or all components skipped | 403 | 401 |

All mutations require `If-Match` and reject a stale version for an in-scope resource with 409. [F10 contract](F10_BUNDLE_CART_API.md).

## 2026-11-26 — F11 dashboard reads

| Method / path | STORE_ADMIN / MERCHANDISER | SYSTEM_ADMIN / END_CUSTOMER | Anonymous |
|---|---|---|---|
| GET `/api/dashboard/snapshot-utilities` | 200 same-Store READY headers; 404 foreign, absent or unavailable selected snapshot | 403 | 401 |
| GET `/api/dashboard/bundle-additions` | 200 same-Store successful bundle-event aggregates; 404 foreign or absent campaign | 403 | 401 |

An inactive staff Store returns 403 `STORE_INACTIVE`. F11 reuses the existing own-Store F08 campaign and simulation-report GET operations. [F11 contract](F11_DASHBOARD_API.md).

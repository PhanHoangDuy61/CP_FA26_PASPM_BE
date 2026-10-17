# F03 Utility API Contract

Reviewed against the F03 controllers, services, repositories, V4/V5 migrations, the latest conceptual and logical ERD, the runtime-generated OpenAPI export, and existing tests on 2026-10-17. This is documentation work only; no Backend behavior or migrations were changed. The shared OpenAPI document is [`openapi.json`](openapi.json). All dates are ISO `YYYY-MM-DD`, timestamps are ISO date-time, JSON decimals are numbers, and IDs are 32-bit integers unless noted.

## Scope and source status

The F03 package is `vn.edu.fpt.paspm.utility`. CodeGraph was available and used to trace Controller → Service → Repository, F01 `AuthService` / `CatalogAccess` / `StoreScope`, F02 Product and `INVENTORY_BATCH`, snapshot persistence, and existing tests. The indexed project reported 756 files, 9,029 nodes and 16,634 edges. The latest ERD file is `docs/ERD_Diagram/PASPM_Complete_ERD_F03_Three_Weights_v4.drawio`; it contains both “Complete Conceptual ERD — F03 Weights v4” and “Complete Logical ERD — F03 Weights v4”. It uses `INVENTORY_BATCH`; it explicitly says no `PRODUCT_LOT`.

The running OpenAPI export is OpenAPI 3.1.0, API version `0.1.0`, with 11 F03 operations and a global HTTP Bearer JWT security requirement. Springdoc does not expose `@PreAuthorize` roles per operation, so the role table below is derived from the actual controller annotations. It emits generic response descriptions for 400/401/403/404/409/429/503 on every operation; that does not mean every such response is reachable on every F03 endpoint. The F03-specific utility code has no rate limiter.

## Authentication, authorization and Store scope

Every F03 route requires a valid active F01 JWT Bearer token. Missing, invalid, expired, inactive or revoked tokens are rejected with 401. An authenticated user with the wrong role receives 403. Handled application errors use `{ "error": string }`; a Spring Security 401 response may have no JSON body. The authenticated user's Store is resolved from the current database user through `CatalogAccess.currentStoreId`; clients cannot choose a `storeId`. Product and snapshot reads/writes scope their repository query to that Store. An absent or other-Store Product/snapshot is returned as 404.

| Role | F03 permissions enforced by Backend |
|---|---|
| `SYSTEM_ADMIN` | GET/PUT global lambda policy only. No access to Store utility settings, Product scores or snapshots. |
| `STORE_ADMIN` | GET Store utility config and current policy; PUT own Store expiry window T. |
| `MERCHANDISER` | GET Store config/policy; PUT weights and selected lambda; GET/PATCH own-Store Product scores; preview; create/read snapshots and matrix. |
| `END_CUSTOMER` | No F03 route. |

## Endpoint inventory

| Method and path | Purpose | Authentication / role | Request → response | Success and meaningful errors |
|---|---|---|---|---|
| `GET /api/system/utility/lambda-policy` | Read global permitted lambda bounds | Bearer; `SYSTEM_ADMIN` | none → `LambdaPolicy` | 200; 401/403 |
| `PUT /api/system/utility/lambda-policy` | Replace global bounds and increment policy version | Bearer; `SYSTEM_ADMIN` | `PolicyRequest` → `LambdaPolicy` | 200; 400 invalid bounds/precision; 401/403 |
| `GET /api/utility/config` | Read caller Store config, T, policy and `lambdaValid` | Bearer; `STORE_ADMIN` or `MERCHANDISER` | none → `ConfigView` | 200; 401/403 |
| `PUT /api/utility/config` | Replace enabled criteria, coefficients and selected lambda | Bearer; `MERCHANDISER` | `WeightRequest` → `ConfigView` | 200; 400 invalid coefficients/lambda/body; 401/403 |
| `PUT /api/utility/config/expiry-window` | Set positive integer T while preserving weight settings | Bearer; `STORE_ADMIN` | `ExpiryWindowRequest` → `ConfigView` | 200; 400 invalid T/body; 401/403 |
| `GET /api/utility/products/{productId}/scores` | Read manual Stock/Season scores | Bearer; `MERCHANDISER`; own Store | none → `Scores` | 200; 401/403; 404 absent/other-Store Product |
| `PATCH /api/utility/products/{productId}/scores` | Partially update supplied manual scores | Bearer; `MERCHANDISER`; own Store | `ScorePatch` → `Scores` | 200; 400 empty/invalid/null score; 401/403; 404 absent/other-Store Product |
| `POST /api/utility/preview` | Build a paginated, non-persistent matrix for a date range | Bearer; `MERCHANDISER`; caller Store | `PreviewRequest` → `MatrixPage` | 200 including `ready=false`; 400 bad range/source rows; 401/403; 409 stale lambda; 503 F04 source unavailable |
| `POST /api/utility/snapshots` | Create immutable snapshot and rows atomically | Bearer; `MERCHANDISER`; caller Store | `RangeRequest` → `SnapshotView` | 201; 400 incomplete/empty matrix or invalid range; 401/403; 409 stale lambda; 503 F04 source unavailable |
| `GET /api/utility/snapshots/{id}` | Read frozen snapshot metadata | Bearer; `MERCHANDISER`; own Store | none → `SnapshotView` | 200; 401/403; 404 absent/other-Store snapshot |
| `GET /api/utility/snapshots/{id}/matrix?page=0&pageSize=20` | Read persisted matrix rows for a snapshot | Bearer; `MERCHANDISER`; own Store | optional paging → `MatrixPage` | 200; 400 invalid paging; 401/403; 404 absent/other-Store snapshot |

The following sections give the field-level contract and link to synthetic JSON examples. Examples use the actual response envelope and property names; identifiers and hashes are illustrative fixture data.

## DTOs and validation

| DTO | Fields and constraints |
|---|---|
| `LambdaPolicy` | Response `{lambdaMin:number,lambdaMax:number,version:int64}`. Initial migration value is `0`, `0.5`, version `1`. |
| `PolicyRequest` | Required `lambdaMin:number`, `lambdaMax:number`; both >=0, each at most 6 fractional digits. Service requires `lambdaMax >= lambdaMin`. |
| `WeightRequest` | All required: `enableStock:boolean`, `enableExpiry:boolean`, `enableSeason:boolean`; `alpha`, `beta`, `gamma` in [0,1], max 6 fractional digits; `lambda >= 0`, max 6 fractional digits and inside current policy. If any criterion is enabled, enabled coefficients must sum exactly to 1. All-disabled is allowed; effective coefficients become zero and Weight is 1. Unknown request properties are rejected by Jackson. |
| `WeightConfig` | Response fields: the three enable flags; `alpha`, `beta`, `gamma`, `lambda`; `expiryWindowDays:int32`; `version:int64`; `scoringVersion:string`. Absent Store JSON uses defaults: all disabled, zero coefficients/lambda, T=30, version=1, `F03_LINEAR_V1`. |
| `ConfigView` | `{config:WeightConfig,lambdaPolicy:LambdaPolicy,lambdaValid:boolean}`. A tightened policy does not change the stored lambda; `lambdaValid=false` reports that it is outside the new bounds. |
| `ExpiryWindowRequest` | Required `expiryWindowDays:int32`, JSON integer >=1. A strict deserializer rejects fractions, quoted numeric strings and null. |
| `ScorePatch` | Optional `stockPriorityScore:number` and/or `seasonalPriorityScore:number`; at least one must be supplied. Each is in [0,1], at most 6 fractional digits. Omitted values are preserved; explicit null is rejected. |
| `Scores` | `{productId:int32,stockPriorityScore:number|null,seasonalPriorityScore:number|null}`. Score columns are nullable until assigned. |
| `RangeRequest` | Required `rangeStart:date`, `rangeEnd:date`, `asOfDate:date`; `rangeStart <= rangeEnd`. No Store or dataset identifier is accepted. |
| `PreviewRequest` | The three `RangeRequest` dates plus optional `page:int32 >=0` (default 0) and `pageSize:int32 1..100` (default 20). |
| `FrozenConfiguration` | `{config:WeightConfig,lambdaPolicy:LambdaPolicy,effectiveCoefficients:{alpha:number,beta:number,gamma:number},analysisDate:date,source:string,utilityScale:int32,roundingMode:string,itemMapping:string}`. |
| `MatrixItem` | `{transactionId:int32,productId:int32,algorithmItemId:int32,quantity:int32,baseUtility:number,stockScore:number|null,expiryScore:number|null,seasonalScore:number|null,expiryStatus:string,weightValue:number|null,weightedUtility:number|null,transactionUtility:number|null}`. `algorithmItemId` currently equals `productId`. |
| `MatrixPage` | `{items:MatrixItem[],page:int32,pageSize:int32,totalElements:int64,totalWeightedUtility:number|null,sourceHash:string,matrixHash:string,warnings:string[],ready:boolean,configuration:FrozenConfiguration}`. Incomplete preview can contain null scores, weight/utility and total. |
| `SnapshotView` | `{snapshotId:int32,storeId:int32,createdBy:int32,rangeStart:date,rangeEnd:date,asOfDate:date,configuration:FrozenConfiguration,sourceHash:string,matrixHash:string,totalWeightedUtility:number,roundingScale:int32,status:string,createdAt:date-time}`. Created snapshots have status `READY`. |
| Handled error | `{error:string}`. Examples include `Invalid request fields`, validation/business messages, `Forbidden`, and F04 source unavailability. Framework authentication failures may not include this body. |

OpenAPI currently omits nullability for `Scores`' priority fields and the nullable score/weight/utility/total fields in preview `MatrixItem`/`MatrixPage`. Treat those as nullable in TypeScript even though the generated schemas do not say so. The source has no standalone `ExpiryPriorityResult` DTO or endpoint; E is represented by `MatrixItem.expiryScore` and `expiryStatus`.

## Endpoint details and examples

### Lambda policy — System Admin

`GET /api/system/utility/lambda-policy` has no parameters or body and returns `LambdaPolicy` (200). `PUT` accepts the full `PolicyRequest` and returns the persisted policy (200). Bounds are stored in `system_configuration` under `UTILITY_LAMBDA_POLICY`; each successful PUT increments `version`, including a write with unchanged bounds. Lower bounds below zero and `lambdaMax < lambdaMin` return 400. Tightening policy does not clamp or rewrite Store configs and does not alter old snapshots. See [`lambda_policy_response.json`](../frontend_handoff/F03_examples/lambda_policy_response.json) and [`lambda_policy_update_request.json`](../frontend_handoff/F03_examples/lambda_policy_update_request.json).

### Store utility configuration — Store Admin and Merchandiser

`GET /api/utility/config` returns the caller Store's `ConfigView` (200). `PUT /api/utility/config` is Merchandiser-only and replaces all three enable flags, all coefficient fields, and lambda. It preserves the current T and increments the Store config version. It returns 400 when any field is invalid, enabled coefficients do not sum exactly to 1, or lambda is outside current policy. `PUT /api/utility/config/expiry-window` is Store Admin-only; it changes T, preserves weight fields and lambda, increments config version, and returns `ConfigView`. It accepts a required positive integer only. If the saved lambda is already stale, T can still be changed and the returned `lambdaValid` remains false. Store settings use existing `STORE.utility_config`; no duplicate settings table is created. See the request/response examples [`utility_config_response.json`](../frontend_handoff/F03_examples/utility_config_response.json), [`utility_config_update_request.json`](../frontend_handoff/F03_examples/utility_config_update_request.json), and [`expiry_window_update_request.json`](../frontend_handoff/F03_examples/expiry_window_update_request.json).

### Product Stock and Seasonal scores — Merchandiser

`GET /api/utility/products/{productId}/scores` returns `Scores` (200). `PATCH` accepts either or both score fields, merges supplied values, and returns the persisted `Scores` (200). Empty body, explicit null, values outside [0,1], excess precision, or malformed JSON return 400. Other-Store/unknown Product returns 404. These routes update only the corresponding Product score columns; they do not change stock or batches. See [`product_scores_response.json`](../frontend_handoff/F03_examples/product_scores_response.json), [`stock_priority_update_request.json`](../frontend_handoff/F03_examples/stock_priority_update_request.json), and [`seasonal_priority_update_request.json`](../frontend_handoff/F03_examples/seasonal_priority_update_request.json).

### Utility preview — Merchandiser

`POST /api/utility/preview` accepts `PreviewRequest`; no config, scores, Store ID or Product ID list is accepted. The backend reads persisted Store settings, Product scores, F02 Products and batches, and the transaction source, returning a paginated `MatrixPage` (200). Preview does not write a snapshot. `ready` and `warnings` must be checked before showing totals. Missing enabled manual score or required expiry metadata yields null affected calculation fields and can make preview not ready. A Product with no eligible batch gets E=0 and `NO_SELLABLE_BATCH`; this warning alone does not make the matrix incomplete. If the configured lambda is outside current policy, preview returns 409. Invalid date order/paging/prepared source rows return 400. The normal provider returns 503 until F04 supplies validated transactions. See [`utility_preview_request.json`](../frontend_handoff/F03_examples/utility_preview_request.json) and [`utility_preview_response.json`](../frontend_handoff/F03_examples/utility_preview_response.json).

### Snapshot create, metadata and matrix — Merchandiser

`POST /api/utility/snapshots` accepts `RangeRequest` (no paging) and returns `SnapshotView` with 201. It locks the current policy and Store config consistently, rechecks lambda, builds rows and stores the snapshot header and all rows in one transaction. It rejects an incomplete matrix or a matrix with no eligible rows using 400; stale lambda returns 409; unavailable F04 transaction input returns 503. `GET /api/utility/snapshots/{id}` returns stored metadata (200); `/matrix` returns persisted rows with `page` default 0 and `pageSize` default 20, range 1..100 (200). Invalid paging returns 400; missing or other-Store snapshot returns 404. No `GET /api/utility/snapshots` history/list endpoint exists, so the Frontend cannot discover snapshot IDs through this API. See [`utility_snapshot_create_request.json`](../frontend_handoff/F03_examples/utility_snapshot_create_request.json), [`utility_snapshot_response.json`](../frontend_handoff/F03_examples/utility_snapshot_response.json), and [`utility_matrix_response.json`](../frontend_handoff/F03_examples/utility_matrix_response.json).

## Business calculations and data behavior

For enabled criteria, the code computes `W_i = 1 + lambda × (alpha × S_i + beta × E_i + gamma × M_i)` using enabled coefficients only. Disabled criteria have effective coefficient zero. All-disabled configuration gives `W_i=1`. S and M are manually set through the score API; E is calculated server-side. Weight is rounded to 18 fractional digits using `HALF_UP`.

For a Product requiring expiry, the calculator selects only `INVENTORY_BATCH` rows with status `ACTIVE` and `remaining_quantity > 0`. It compares `expiry_date` with the supplied `asOfDate`, never the current date. Expiry on or before `asOfDate` is excluded. For a future batch, `daysRemaining = DAYS.between(asOfDate, expiryDate)` and `E_batch = max(0, 1 - daysRemaining/T)`. Future batches beyond T remain in the weighted-average denominator with score zero. Product E is the remaining-quantity-weighted average across eligible batches, rounded to 18 fractional digits with `HALF_UP`. `requires_expiry=false` returns `E=0`, `NOT_APPLICABLE`. No eligible batch returns `E=0`, `NO_SELLABLE_BATCH`. An active positive-quantity batch requiring expiry but lacking `expiry_date` yields `E=null`, `MISSING_METADATA`; it is not fabricated as a score. If expiry is enabled this makes the preview incomplete and prevents snapshot creation. If expiry is disabled, the metadata warning does not by itself prevent readiness.

F03 compares `LocalDate` values and does not convert transaction instants or derive `asOfDate` from a Store timezone; callers provide the analysis date explicitly. F02's Inventory Summary boundary is a separate API rule.

F03 consumes a `TransactionSource` supplied by F04. Its contract is preprocessed transactions containing Store ID, transaction ID, transaction date, eligibility flag, and unique Product items with quantity and historical unit price/cost or a precomputed item `sourceUtility`. The requested `rangeStart` and `rangeEnd` are inclusive. Ineligible/out-of-range transactions are skipped. A Store mismatch, null date, duplicate/invalid transaction ID, empty eligible transaction, duplicate/invalid Product item, non-positive quantity, missing/negative historical prices or costs, negative utility, or a value beyond the F05 signed 64-bit scaled representation fails with 400. When `sourceUtility` is provided it is treated as an already-calculated item total; otherwise base utility is `quantity × (historical unit price - historical unit cost)`. Current Product selling/cost prices are not substituted. Negative margin/utility is rejected because it is incompatible with the current F05 contract.

Base, each item weighted utility, transaction utility, and total weighted utility are rounded to 6 decimals using `HALF_UP`; `utilityScale=6` is returned in frozen config. The implementation verifies that the six-decimal unscaled integer fits signed 64-bit. `transactionUtility` is the sum of weighted item utilities for the transaction and is repeated on each row in that transaction. Preview's `totalWeightedUtility` is the sum across all matrix rows. Weighted Utility is an analysis priority adjustment, not guaranteed revenue or profit improvement.

## Snapshot persistence, immutability and F05 handoff

V4 adds nullable `product.stock_priority_score` and `product.seasonal_priority_score` with [0,1] checks, and a versioned `system_configuration` record. Store-level settings reuse `STORE.utility_config`. V5 adds `utility_snapshot` and `utility_matrix_item`. A snapshot freezes Store and creator IDs, range and analysis dates, full Store configuration (T, selected lambda, enable flags, coefficients, config/scoring version), policy bounds and policy version, effective coefficients, source type, utility scale, rounding mode, Product ID mapping, source/matrix SHA-256 hashes, total, rounding scale, status and timestamp. Rows persist transaction/Product IDs, quantity, base utility, S/E/M, expiry status, weight and weighted utility. Matrix reads calculate each row's `transactionUtility` from persisted weighted rows; it is not a stored column.

The API has no snapshot update/delete operations and detail/matrix reads use frozen stored values without querying current config, policy, inventory or source. Integration tests verify an existing snapshot remains unchanged after lambda-policy, T and Product score changes and verify cross-Store reads return 404. Static code inspection shows weight config and batch changes likewise do not recalculate stored rows. No database trigger prevents an out-of-band SQL update; immutability is enforced by the implemented API and persisted snapshot design.

Current schema/data gaps against the ERD/F04/F05 handoff: V5 intentionally cannot add `utility_snapshot.transaction_id` FK because F04 `SALES_TRANSACTION` does not exist; no validated production TransactionSource exists; there is no dataset ID or analysis-range/dataset identity beyond range, source type and content hash; F05 `MINING_REQUEST` and its `utility_snapshot_id` FK are absent until F05 migration. Matrix mapping is `PRODUCT_ID_V1` (`algorithmItemId == productId`); F03 returns decimal utilities with scale 6, not an already-converted mining text file or integer transaction database. F05 must consume the selected snapshot and fetch every matrix page, apply its exact ×10^6 integer scale with range checks, and preserve one transaction's item rows before running HUI-Miner/EFIM. F03 neither imports CSV nor runs a mining algorithm.

## Endpoint readiness matrix

“Runtime tested” below means an existing automated MockMvc HTTP request, not a live Swagger UI call. “Contract verified” means source and the runtime-generated OpenAPI were compared.

| Endpoint | Implemented | Contract verified | Runtime MockMvc | Swagger UI | FE readiness |
|---|---|---|---|---|---|
| `GET /api/system/utility/lambda-policy` | PASS | PASS | NOT RUN directly | NOT RUN | READY |
| `PUT /api/system/utility/lambda-policy` | PASS | PASS | PASS | NOT RUN | READY |
| `GET /api/utility/config` | PASS | PASS | PASS | NOT RUN | READY |
| `PUT /api/utility/config` | PASS | PASS | PASS | NOT RUN | READY |
| `PUT /api/utility/config/expiry-window` | PASS | PASS | PASS | NOT RUN | READY |
| `GET /api/utility/products/{productId}/scores` | PASS | PASS | PASS | NOT RUN | READY |
| `PATCH /api/utility/products/{productId}/scores` | PASS | PASS | PASS | NOT RUN | READY |
| `POST /api/utility/preview` | PASS | PASS | PASS with fixture | NOT RUN | CONDITIONAL — F04 provider |
| `POST /api/utility/snapshots` | PASS | PASS | PASS with fixture | NOT RUN | CONDITIONAL — F04 provider |
| `GET /api/utility/snapshots/{id}` | PASS | PASS | PASS with fixture | NOT RUN | CONDITIONAL — requires a known snapshot ID |
| `GET /api/utility/snapshots/{id}/matrix` | PASS | PASS | PASS with fixture | NOT RUN | CONDITIONAL — requires a known snapshot ID |

## Verification status

| Check | Result | Evidence |
|---|---|---|
| CodeGraph architecture and dependency trace | PASS | CodeGraph index contained 756 files; F03 controller/service/repository/source/test paths traced. |
| Source and OpenAPI route/schema comparison | PASS, 11/11 operations | Runtime OpenAPI includes all 11 routes, global bearer security and request/response DTO schemas; controllers provide the roles. |
| Existing F03 runtime tests | PASS for exercised cases; 10/11 HTTP operations | `UtilityIntegrationTest` directly requests every route except `GET /api/system/utility/lambda-policy`; it exercises config/policy PUT/T role and validation, score read/write/store scope, preview, snapshot create/read/matrix, cross-Store and immutability cases. Calculation and fixture tests cover expiry/math. |
| Interactive Swagger UI calls in this documentation task | NOT RUN | The contract and route list were inspected in the runtime-generated OpenAPI document; no browser “Try it out → Execute” run is claimed. |
| Production Preview/Snapshot transaction flow | BLOCKED by dependency | Normal `UnavailableTransactionSource` returns 503 until F04 supplies the validated transaction provider. The test-only fixture is not production data. |

## Not implemented / not yet integrated

- Snapshot history listing (`GET /api/utility/snapshots`) and independent expiry-score endpoint are not implemented. E is exposed only in preview/snapshot matrix rows.
- F04 validated transaction tables/provider and dataset identity are absent; Preview/Snapshot are not usable with real transactions in the default profile.
- F05 Mining Request, snapshot foreign key, integer mining export, HUI-Miner/EFIM execution and mining result APIs are outside F03 and absent.
- OpenAPI does not encode per-operation roles or response nullability. Treat role descriptions above as source-verified and nullable fields as indicated here.

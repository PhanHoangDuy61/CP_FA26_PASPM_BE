# F03 Utility API — Final Weight Rules v1.2

Authoritative business specification: [F03_WEIGHT_RULES_v1_2.md](../requirements/F03_WEIGHT_RULES_v1_2.md). This contract describes current controllers/DTOs. Prior linear or quantity-weighted expiry formulas are historical only. Base URL: `http://localhost:8080` (PORT override supported); Swagger `/swagger`, OpenAPI `/v3/api-docs`. All routes below require F01 Bearer authentication. Store identity comes from the authenticated database user; no Store selector is accepted.

## Endpoint inventory and roles

| Method | Endpoint | Role | Request → response | Success |
|---|---|---|---|---|
| GET | `/api/system/utility/lambda-policy` | SYSTEM_ADMIN | none → LambdaPolicy | 200 |
| PUT | `/api/system/utility/lambda-policy` | SYSTEM_ADMIN | PolicyRequest → LambdaPolicy | 200 |
| GET | `/api/utility/config` | STORE_ADMIN, MERCHANDISER | none → ConfigView | 200 |
| PUT | `/api/utility/config` | MERCHANDISER | WeightRequest → ConfigView | 200 |
| PUT | `/api/utility/config/expiry-window` | STORE_ADMIN | ExpiryWindowRequest → ConfigView | 200; **deprecated, no scoring effect** |
| GET | `/api/utility/products/{productId}/scores` | STORE_ADMIN, MERCHANDISER | none → Scores | 200 |
| PATCH | `/api/utility/products/{productId}/scores` | MERCHANDISER | ScorePatch → Scores | 200 |
| POST | `/api/utility/preview` | MERCHANDISER | PreviewRequest → MatrixPage | 200 |
| POST | `/api/utility/snapshots` | MERCHANDISER | RangeRequest → SnapshotView | 201 |
| GET | `/api/utility/snapshots/{id}` | MERCHANDISER | none → SnapshotView | 200 |
| GET | `/api/utility/snapshots/{id}/matrix` | MERCHANDISER | page/pageSize query → MatrixPage | 200 |
| GET | `/api/utility/expiry-push-batches` | STORE_ADMIN, MERCHANDISER | optional productId/asOfDate/page/pageSize → PushPage | 200 |

END_CUSTOMER has no permission. SYSTEM_ADMIN cannot use Store-bound F03 actions. STORE_ADMIN manages Product T through the existing F02 Product POST/PUT APIs, not the Merchandiser score patch. All foreign Product/snapshot IDs resolve to 404. Insufficient role → 403; missing/invalid token → 401. Requests with unknown JSON fields are rejected; bodies cannot forge storeId or calculated E.

## Settings and manual scores

`PolicyRequest`: required nonnegative decimal lambdaMin/lambdaMax (up to 24 integer and 6 fractional digits); max>=min. `LambdaPolicy`: lambdaMin:number, lambdaMax:number, version:int64. Changing bounds increments version and never clamps Store values or rewrites snapshots.

```json
{"lambdaMin":0,"lambdaMax":0.5}
```

`WeightRequest`: required booleans enableStock/enableExpiry/enableSeason; required alpha/beta/gamma in [0,1], at most 6 fractional digits; required lambda>=0 within the active system range, up to 24 integer/6 fractional digits. Enabled coefficients sum exactly to 1. All disabled means W=1. Disabled coefficients contribute zero.

```json
{"enableStock":true,"enableExpiry":true,"enableSeason":true,"alpha":0.5,"beta":0.2,"gamma":0.3,"lambda":0.5}
```

`ConfigView` contains config:WeightConfig, lambdaPolicy:LambdaPolicy, lambdaValid:boolean. `WeightConfig` has the WeightRequest fields plus version:int64, scoringVersion:string and deprecated expiryWindowDays:int32|null. New current configs advertise `EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1`; expiryWindowDays has **no active scoring meaning**, and no default Store threshold is supplied for new unconfigured Stores. Existing legacy values can still be displayed for compatibility.

Legacy `ExpiryWindowRequest` still requires positive integer expiryWindowDays and preserves old role/validation behavior; this only stores a deprecated value. Frontend must migrate the threshold form to F02 `ProductRequest.expiryPushDays`. New snapshots derive T only from each Product, even when a legacy Store window is positive.

`ScorePatch` accepts stockPriorityScore and/or seasonalPriorityScore, decimals [0,1], max 6 fractional digits. Send at least one field; omitted preserves previous value; explicit null is rejected. `Scores` retains productId:int32 and the two manual number|null values, independent of stock quantity. GET is readable by Store Admin/Merchandiser; PATCH remains Merchandiser-only and returns the saved inputs plus recalculated current results. Unknown fields are rejected explicitly (`ScorePatch.additionalProperties=false`). E, W, expiryPushDays, stockQuantity, coefficients and storeId are not writable through ScorePatch.

```json
{"stockPriorityScore":0.8,"seasonalPriorityScore":0.9}
```

## Current Product E/W (2026-10-21 additive response)

GET/PATCH scores now returns a **current preview**, including Products with no transaction history. It uses the same ExpiryScoreCalculator and UtilityMath as F03, with current scoped Product/batches/configuration and Store-local today in a repeatable-read transaction. No snapshot, mining or inventory write occurs when reading/calculating these fields.

| Field | JSON type | Meaning |
|---|---|---|
| productId | integer | Existing scoped Product identity |
| stockPriorityScore / seasonalPriorityScore | number or null | Existing manual S/M |
| expiryScore | number or null | Read-only shared E, normally0/1; required expiry metadata may be unknown |
| weightValue | number or null | Read-only current W; null if ready=false |
| expiryStatus | string | AVAILABLE, NOT_APPLICABLE, NO_SELLABLE_BATCH or MISSING_METADATA |
| expiryEvidence | Evidence object | Product threshold, earliest eligible expiry/date difference, analysis date/reason/batches; same shared structure documented below |
| expiryPushDays | integer | Read-only Product T; no Store fallback |
| asOfDate / timeZone | ISO date string / string | Current Store calendar convention |
| configVersion | integer(int64) | Store configuration version used |
| lambdaValid / ready | boolean / boolean | Current policy validity / whether current W can be calculated |
| warnings | string[] | Missing enabled score/metadata, no sellable batch, or stale lambda explanation |

All added fields are response-only in OpenAPI. Explicit nulls are retained; do not replace unknown E/W with0. `ready` describes calculation completeness, **not** sellability, approval or snapshot eligibility. A valid numeric W with NO_SELLABLE_BATCH warning does not authorize sale. When enabled input is missing, or lambda is outside current bounds, return200 with weightValue=null/ready=false and warnings. Manual values may still be saved; do not clamp lambda. All-disabled criteria yield W1 when current lambda policy is valid. Disabled coefficients do not contribute.

Illustrative successful response (IDs/configVersion are examples; full executed response is linked from the verification report):

```json
{"productId":1,"stockPriorityScore":0.8,"seasonalPriorityScore":0.9,"expiryScore":1,"weightValue":1.435,"expiryStatus":"AVAILABLE","expiryEvidence":{"expiryPushDays":7,"earliestEligibleExpiryDate":"2026-10-26","daysRemaining":5,"asOfDate":"2026-10-21","reason":"WITHIN_THRESHOLD","batches":[{"productId":1,"batchId":1,"expiryDate":"2026-10-26","remainingQuantity":10,"expiryPushDays":7,"daysRemaining":5,"pushRequired":true,"pushRequiredQuantity":10,"asOfDate":"2026-10-21"}]},"expiryPushDays":7,"asOfDate":"2026-10-21","timeZone":"Asia/Ho_Chi_Minh","configVersion":2,"lambdaValid":true,"ready":true,"warnings":[]}
```

With S0.8/M0.9, alpha0.5/beta0.2/gamma0.3/lambda0.5, T7/D5 gives E1/W1.435. T3/D5 gives E0/W1.335. These are test inputs, not production defaults. Saving S/M or Store config affects future previews/snapshots only; existing snapshot JSON/matrix/hash and F05 persisted results remain unchanged.

## Preview request and deterministic calculation

```json
{"rangeStart":"2026-10-01","rangeEnd":"2026-10-31","asOfDate":"2026-10-19","page":0,"pageSize":20}
```

RangeRequest requires three ISO LocalDate strings rangeStart/rangeEnd/asOfDate; rangeStart<=rangeEnd. Preview adds optional page (default0, >=0) and pageSize (default20, 1..100). Snapshot creation accepts RangeRequest only; matrix GET uses page/pageSize with the same defaults/limits.

The normal source is `F04_DATABASE_V1`: eligible COMPLETED Store transactions with historical item quantity/price/cost. Current Product prices never replace them. Duplicate or foreign prepared rows, missing prices/costs and negative/overflow utility are rejected. There is no CSV body, dataset ID, score override or arbitrary Product selector in Preview.

For an ACTIVE Product requiring expiry, use ACTIVE positive-quantity INVENTORY_BATCH rows with known `expiryDate > asOfDate`. Select minimum expiry date (FEFO), then batchId for ties. `T=Product.expiryPushDays` (missing/null effective0); `E=1` iff T>0 and earliest daysRemaining<=T. Quantity ratios never dilute E. Non-expiring Product has E=0/NOT_APPLICABLE. Inactive Product has no current sellable batch for assessment; historical transaction rows are retained with NO_SELLABLE_BATCH warning.

`W=1+lambda*(alpha*S+beta*E+gamma*M)`. Utilities are BigDecimal scale6 HALF_UP, weights scale18. The source hash fingerprints historical rows; matrix hash additionally fingerprints saved configuration, analysis date, scores and frozen batch evidence. Row/Product ordering is stable. `algorithmItemId=productId`, mapping PRODUCT_ID_V1. Item, transaction and dataset utilities must fit signed64 after multiplying by 10^6; no silent clamping.

## Response data types

| DTO | Fields |
|---|---|
| MatrixPage | items:MatrixItem[], page:int32, pageSize:int32, totalElements:int64, totalWeightedUtility:number|null, sourceHash:string, matrixHash:string, warnings:string[], ready:boolean, configuration:FrozenConfiguration |
| MatrixItem | transactionId/productId/algorithmItemId/quantity:int32, baseUtility:number, stockScore/expiryScore/seasonalScore:number|null, expiryStatus:string, weightValue/weightedUtility/transactionUtility:number|null, expiryEvidence:Evidence|null |
| FrozenConfiguration | config:WeightConfig, lambdaPolicy:LambdaPolicy, effectiveCoefficients:{alpha,beta,gamma:number}, analysisDate:date, source:string, utilityScale:int32, roundingMode:string, itemMapping:string, timeZone:string|null |
| Evidence | expiryPushDays:int32, earliestEligibleExpiryDate:date|null, daysRemaining:int64|null, asOfDate:date, reason:string, batches:PushBatch[] |
| PushBatch | productId/batchId/expiryPushDays:int32, expiryDate:date, remainingQuantity/daysRemaining/pushRequiredQuantity:int64, pushRequired:boolean, asOfDate:date |
| PushPage | items:PushBatch[], page/pageSize:int32, totalElements:int64, asOfDate:date, timeZone:string, warnings:string[] |
| SnapshotView | snapshotId/storeId/createdBy:int32, rangeStart/rangeEnd/asOfDate:date, configuration:FrozenConfiguration, sourceHash/matrixHash:string, totalWeightedUtility:number, roundingScale:int32, status:string, createdAt:UTC date-time, warnings:string[] |

`totalElements` counts matrix rows, not transactions. `transactionUtility` repeats the sum of the entire transaction's frozen rows even across pages. Dataset total also covers all pages. Do not sum repeated transactionUtility once per item. PushPage totals count batches.

Example matrix item (synthetic identities; official arithmetic):

```json
{"transactionId":1,"productId":21,"algorithmItemId":21,"quantity":3,"baseUtility":12000,"stockScore":0.8,"expiryScore":1,"seasonalScore":0.9,"expiryStatus":"AVAILABLE","weightValue":1.435,"weightedUtility":17220,"transactionUtility":17220,"expiryEvidence":{"expiryPushDays":7,"earliestEligibleExpiryDate":"2026-10-24","daysRemaining":5,"asOfDate":"2026-10-19","reason":"WITHIN_THRESHOLD","batches":[{"productId":21,"batchId":31,"expiryDate":"2026-10-24","remainingQuantity":20,"expiryPushDays":7,"daysRemaining":5,"pushRequired":true,"pushRequiredQuantity":20,"asOfDate":"2026-10-19"},{"productId":21,"batchId":32,"expiryDate":"2026-11-28","remainingQuantity":80,"expiryPushDays":7,"daysRemaining":40,"pushRequired":false,"pushRequiredQuantity":0,"asOfDate":"2026-10-19"}]}}
```

With identical data and Product T=3 or0, E=0, W=1.335, weighted utility=16020. T=0 uses evidence reason THRESHOLD_DISABLED when inventory is otherwise valid.

## Status, warnings and immutable history

- AVAILABLE: valid assessment; evidence reason WITHIN_THRESHOLD, OUTSIDE_THRESHOLD or THRESHOLD_DISABLED explicitly distinguishes the cases.
- MISSING_METADATA: active positive-quantity required-expiry batch has null expiry. E=null for T>0; E=0 for T=0 but metadata is still invalid. If expiry criterion is enabled, weight/weighted utility/transaction total are null, ready=false and snapshot creation returns400. If disabled, computation may proceed with warning.
- NO_SELLABLE_BATCH: E=0 plus warning. Historical transaction utility is retained; this is not current availability or permission to sell expired stock.
- NOT_APPLICABLE: requiresExpiry=false, E=0; no invented dates or push records.

New snapshot rows store JSON evidence and validation warnings. Configuration freezes analysisDate, timezone, new scoringVersion, coefficients and global policy/version. GET reads stored data and never recomputes from Product/batches/settings. Old F03_LINEAR_V1 scores, hashes and utility remain intact; old evidence/timeZone may be null and old warnings empty. No list/update/delete snapshot endpoint exists, and there is no direct-SQL immutability trigger.

## Independent current push query and F06

`GET /api/utility/expiry-push-batches?productId=21&page=0&pageSize=20` defaults to today's configured Store-local date. Optional `asOfDate=YYYY-MM-DD` is an analytical query, not proof of current sellability. It returns only known-expiry ACTIVE positive-quantity batches of ACTIVE requires-expiry Products with positive T whose daysRemaining are 1..T. It uses the same calculator as Preview. Ordering: expiryDate, productId, batchId. It works with no transactions, snapshots, minUtil or Mining results. Product filter outside the Store gives404; unfiltered foreign Store sees only its own rows. Missing critical metadata on candidate Products is warned explicitly.

F06 must use a fresh current-date query/check before recommending or presenting a bundle, then enforce FEFO fulfillment as appropriate. A historical snapshot push flag does not guarantee stock is still sellable or will clear before expiry.

## HTTP errors

| Status | Meaning |
|---|---|
| 400 | Invalid JSON/fields/range/pagination, coefficients or lambda, missing enabled scores/metadata for snapshot, empty snapshot matrix, invalid historical utility |
| 401 | Missing/invalid/expired/revoked authentication |
| 403 | Wrong role |
| 404 | Missing or foreign Product/snapshot |
| 409 | Saved lambda no longer fits updated policy; persistence conflict |
| 503 | Required dependency unavailable (not the normal F04 source state) |

Handled business/validation envelope: `{"error":"Enabled coefficients must sum exactly to 1"}` or `{"error":"Invalid request fields"}`. F01/Spring Security 401/403 may have an empty framework body; frontend must key off HTTP status. Never expect tokens/credentials in examples.

F05 is currently only MiningEngine interface: no runnable snapshot export adapter, Mining Request APIs, HUI-Miner/EFIM or snapshot-list APIs are implemented. See [F03_F05_HANDOFF](../PASPM_F03_ERD_and_Tasks_v4/F03_F05_HANDOFF.md) and [F05/F06 integration](F03_V12_F05_F06_INTEGRATION.md). Verification details are in the v1.2 report; endpoint descriptions are not claims of interactive Swagger execution.

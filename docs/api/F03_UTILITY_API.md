# F03 Utility API

Contract generated from the running Spring Boot application on 2026-10-17. The matching OpenAPI document is [`openapi.json`](openapi.json). All routes use JSON unless noted. IDs are 32-bit integers; dates are ISO `YYYY-MM-DD`; decimals are JSON numbers. Store identity is derived from the authenticated JWT and F01 `StoreScope`, never from request data.

## Authentication and roles

Send `Authorization: Bearer <accessToken>` on every route. Tokens are issued by the existing F01 login flow. Unauthenticated or invalid tokens return 401. Store data is scoped to the current account; cross-store records are hidden as 404.

| Role | Allowed F03 actions |
|---|---|
| `SYSTEM_ADMIN` | Read/update global lambda policy and its versioned permitted range. No Store-specific T, weight, score, preview, or snapshot writes. |
| `STORE_ADMIN` | Read their Store config and policy; update that Store's expiry window T. Cannot change lambda policy, weights, scores, or snapshots. |
| `MERCHANDISER` | Read Store config/policy; choose lambda and three-weight configuration; read/update Product stock and seasonal scores; preview and create/read snapshots and matrix. Cannot change T or system bounds. |

OpenAPI contains a global HTTP Bearer JWT security scheme. Role differences are enforced by Spring method security (`@PreAuthorize`) and are documented here because Springdoc does not encode these annotations as per-operation role declarations.

## Shared models

`WeightConfig`: `enableStock:boolean`, `enableExpiry:boolean`, `enableSeason:boolean`, `alpha:number`, `beta:number`, `gamma:number`, `lambda:number`, `expiryWindowDays:integer`, `version:integer`, `scoringVersion:string`.

`LambdaPolicy`: `lambdaMin:number`, `lambdaMax:number`, `version:integer`.

`ConfigView`: `{config:WeightConfig, lambdaPolicy:LambdaPolicy, lambdaValid:boolean}`. `lambdaValid=false` means the stored Store lambda is outside the latest System Admin policy; it was not clamped. New preview/snapshot requests return 409 until the Merchandiser selects an allowed lambda.

`FrozenConfiguration`: `{config:WeightConfig, lambdaPolicy:LambdaPolicy, effectiveCoefficients:{alpha:number,beta:number,gamma:number}, analysisDate:date, source:string, utilityScale:integer, roundingMode:string, itemMapping:string}`.

`MatrixItem`: `{transactionId:integer, productId:integer, algorithmItemId:integer, quantity:integer, baseUtility:number, stockScore:number|null, expiryScore:number|null, seasonalScore:number|null, expiryStatus:string, weightValue:number, weightedUtility:number, transactionUtility:number}`. `algorithmItemId` currently equals `productId` (`PRODUCT_ID_V1`).

`MatrixPage`: `{items:MatrixItem[], page:integer, pageSize:integer, totalElements:integer, totalWeightedUtility:number|null, sourceHash:string|null, matrixHash:string|null, warnings:string[], ready:boolean, configuration:FrozenConfiguration}`. On an incomplete preview, total and affected row values can be null; inspect `warnings` and `ready` before displaying a total.

`SnapshotView`: `{snapshotId:integer, storeId:integer, createdBy:integer, rangeStart:date, rangeEnd:date, asOfDate:date, configuration:FrozenConfiguration, sourceHash:string, matrixHash:string, totalWeightedUtility:number, roundingScale:integer, status:string, createdAt:date-time}`.

## Endpoint reference

### Global lambda policy

**GET `/api/system/utility/lambda-policy` — `SYSTEM_ADMIN`** (response DTO: `LambdaPolicy`)

Response 200 `LambdaPolicy`. Initial policy is `lambdaMin=0`, `lambdaMax=0.5`, `version=1`.

**PUT `/api/system/utility/lambda-policy` — `SYSTEM_ADMIN`** (request DTO: `PolicyRequest`; response: `LambdaPolicy`)

Request (all fields required):

```json
{"lambdaMin":0,"lambdaMax":0.5}
```

Both values are decimal numbers with at most six fractional digits. Require `lambdaMin >= 0` and `lambdaMax >= lambdaMin`. A successful write returns the persisted policy with incremented `version`; it never rewrites Store selections or saved snapshots.

Example response:

```json
{"lambdaMin":0,"lambdaMax":0.5,"version":2}
```

### Read Store configuration

**GET `/api/utility/config` — `STORE_ADMIN` or `MERCHANDISER`**

No body. Returns `ConfigView` for the caller's Store. Example:

```json
{
  "config":{"enableStock":true,"enableExpiry":true,"enableSeason":true,"alpha":0.5,"beta":0.2,"gamma":0.3,"lambda":0.5,"expiryWindowDays":30,"version":1,"scoringVersion":"F03_LINEAR_V1"},
  "lambdaPolicy":{"lambdaMin":0,"lambdaMax":0.5,"version":1},
  "lambdaValid":true
}
```

### Set three-weight configuration and selected lambda

**PUT `/api/utility/config` — `MERCHANDISER`** (request DTO: `WeightRequest`)

All fields required. T, Store ID, and lambda bounds are not accepted fields (unknown JSON properties are rejected).

```json
{"enableStock":true,"enableExpiry":true,"enableSeason":true,"alpha":0.5,"beta":0.2,"gamma":0.3,"lambda":0.5}
```

Each coefficient is in [0,1], at most six fractional digits. The sum of coefficients for enabled criteria must equal exactly 1. No normalization is performed. Disabled criteria contribute effective coefficient zero; if all are disabled, the computed Weight is 1. Lambda must be within the current system policy and have at most six fractional digits. Existing T is preserved.

Response 200 is the updated `ConfigView`.

### Set Store expiry window T

**PUT `/api/utility/config/expiry-window` — `STORE_ADMIN`** (request DTO: `ExpiryWindowRequest`)

`expiryWindowDays` is a required positive JSON integer (int32). Fractional numbers, quoted numeric strings, null and values below 1 are rejected. Example request/response:

```json
{"expiryWindowDays":30}
```

Response 200 is `ConfigView`; configured weights and selected lambda are preserved. The default is 30 days. The calculation reads the frozen Store T; it does not hardcode 30.

### Read or update Product priority scores

**GET `/api/utility/products/{productId}/scores` — `MERCHANDISER`**

Response 200 `Scores`, e.g. `{"productId":17,"stockPriorityScore":0.8,"seasonalPriorityScore":0.9}`. Scores can be null before they are set.

**PATCH `/api/utility/products/{productId}/scores` — `MERCHANDISER`** (request DTO: `ScorePatch`)

At least one field is required. Either score may be sent alone; an omitted score is preserved. Explicit null is invalid. Each score is in [0,1] with at most six fractional digits. Example:

```json
{"stockPriorityScore":0.8}
```

Response 200 is the persisted `Scores`. This endpoint does not edit stock or inventory.

### Preview utility

**POST `/api/utility/preview` — `MERCHANDISER`** (request DTO: `PreviewRequest`)

Required dates: `rangeStart`, `rangeEnd`, `asOfDate`. Optional `page` defaults to 0 and must be >=0; `pageSize` defaults to 20 and must be 1..100.

```json
{"rangeStart":"2026-10-01","rangeEnd":"2026-10-31","asOfDate":"2026-10-17","page":0,"pageSize":20}
```

Dates use ISO local dates. Transaction range is inclusive and interpreted in `Asia/Ho_Chi_Minh`. Expiry eligibility uses the supplied analysis date: a batch expires on or before `asOfDate` is not eligible; only ACTIVE batches with remaining quantity >0 and expiry date strictly after `asOfDate` enter the quantity-weighted score. Historical requests do not use today's date.

Response 200 is `MatrixPage`. Example (shortened to one row):

```json
{
  "items":[{"transactionId":1,"productId":17,"algorithmItemId":17,"quantity":3,"baseUtility":12000,"stockScore":0.8,"expiryScore":0.6,"seasonalScore":0.9,"expiryStatus":"AVAILABLE","weightValue":1.395,"weightedUtility":16740,"transactionUtility":16740}],
  "page":0,"pageSize":20,"totalElements":1,"totalWeightedUtility":16740,
  "sourceHash":"…","matrixHash":"…","warnings":[],"ready":true,
  "configuration":{"config":{"enableStock":true,"enableExpiry":true,"enableSeason":true,"alpha":0.5,"beta":0.2,"gamma":0.3,"lambda":0.5,"expiryWindowDays":100,"version":2,"scoringVersion":"F03_LINEAR_V1"},"lambdaPolicy":{"lambdaMin":0,"lambdaMax":0.5,"version":1},"effectiveCoefficients":{"alpha":0.5,"beta":0.2,"gamma":0.3},"analysisDate":"2026-10-17","source":"DEV_FIXTURE","utilityScale":6,"roundingMode":"HALF_UP","itemMapping":"PRODUCT_ID_V1"}
}
```

Weight formula: `1 + lambda * (alpha*S + beta*E + gamma*M)`, using effective coefficients for enabled criteria. Base utility uses historical item price/cost and quantity, or an item total from the source adapter; current Product prices are not substituted. Eligible expiry score per batch is `max(0, 1 - daysUntilExpiry/T)`; Product E is the remaining-quantity-weighted average. Non-expiring Products get E=0 / `NOT_APPLICABLE`; no eligible batch gets E=0 / `NO_SELLABLE_BATCH`. Required missing expiry metadata produces `MISSING_METADATA`, a preview warning and no complete total when expiry scoring is enabled. Missing enabled manual scores also make the preview incomplete. Check `ready` and `warnings`.

### Create and read immutable snapshots

**POST `/api/utility/snapshots` — `MERCHANDISER`** (request DTO: `RangeRequest`)

Body uses the same required `rangeStart`, `rangeEnd`, `asOfDate` as preview, without pagination:

```json
{"rangeStart":"2026-10-01","rangeEnd":"2026-10-31","asOfDate":"2026-10-17"}
```

Response 201 `SnapshotView`. Incomplete or empty matrices are rejected. Snapshot captures T, policy bounds/version, selected lambda, coefficients, enabled flags, scores, analysis date and output rows; later config or inventory changes do not recalculate it.

**GET `/api/utility/snapshots/{id}` — `MERCHANDISER`**

Response 200 `SnapshotView`. Foreign Store or nonexistent ID returns 404. Example based on the executed isolated QA fixture (IDs and hashes are fixture-specific):

```json
{"snapshotId":1,"storeId":1,"createdBy":4,"rangeStart":"2026-10-01","rangeEnd":"2026-10-31","asOfDate":"2026-10-17","configuration":{"config":{"enableStock":true,"enableExpiry":true,"enableSeason":true,"alpha":0.5,"beta":0.2,"gamma":0.3,"lambda":0.5,"expiryWindowDays":30,"version":9,"scoringVersion":"F03_LINEAR_V1"},"lambdaPolicy":{"lambdaMin":0,"lambdaMax":0.5,"version":4},"effectiveCoefficients":{"alpha":0.5,"beta":0.2,"gamma":0.3},"analysisDate":"2026-10-17","source":"DEV_FIXTURE","utilityScale":6,"roundingMode":"HALF_UP","itemMapping":"PRODUCT_ID_V1"},"sourceHash":"d43e282cd16c316364c94793a6c01131ea6b29c986cd90f098d2c247d7eca83e","matrixHash":"d1fd432876a91fa898eea1a8ae2ed8dc7510a6f4387ea02ee3c8e42628d0b055","totalWeightedUtility":73675,"roundingScale":6,"status":"READY","createdAt":"2026-10-17T08:05:14.681290Z"}
```

**GET `/api/utility/snapshots/{id}/matrix?page=0&pageSize=20` — `MERCHANDISER`**

`page` defaults 0; `pageSize` defaults 20, valid 1..100. Response 200 `MatrixPage` from persisted rows. `totalElements` counts all rows in the snapshot, while `items` is the requested page. Existing snapshots are read without current config, policy, inventory or transaction source.

## Error responses

The implemented error envelope is always a JSON object with a string `error` field for handled errors. Examples:

```json
{"error":"Invalid request fields"}
```

```json
{"error":"Selected lambda is outside the current system policy; update the Store weight configuration"}
```

| Status | Meaning / common F03 cases |
|---|---|
| 400 | Invalid body/field, coefficient sum, invalid score, date range, pagination, missing data for snapshot, or no eligible source rows. Handled field/deserialization errors use `{"error":"Invalid request fields"}`. |
| 401 | Missing/invalid/expired bearer token. |
| 403 | Valid account lacks required role. |
| 404 | Product/snapshot absent or outside authenticated Store. |
| 409 | Database uniqueness/constraint conflict, or Store's saved lambda is outside current policy when previewing/creating. |
| 429 | Rate limit response is declared in OpenAPI; enforcement depends on deployment configuration. |
| 503 | Required service/source unavailable. With current non-fixture profile, F04 transaction provider is not implemented; preview/create return 503. |

Internal 500 errors may use Spring's standard error handling; do not rely on internal messages. OpenAPI currently declares generic response descriptions; examples above come from the shared exception handler and executed behavior.

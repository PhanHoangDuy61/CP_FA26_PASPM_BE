> **Status 2026-11-02 — branch `Khoi/feat/f03-f05-admin-mining`, not yet merged into `dev`.** The contract below is the current branch contract, verified by `scripts/verify-option-b-macos.sh --run` (23/23 stages, see the [audit matrix](../reports/F03_F05_API_AUDIT_MATRIX_2026-11-02.md)). Two things are closed by design: the five System Admin Store-read routes answer **403** until Product Owner decision D1 (per-group gate), and schedule activation answers **503** until decisions D3–D10 and the activation acceptance are done. `POST /api/mining/requests` and `GET /api/mining/runs/{id}/benchmark` are forbidden for every role. Sections that still describe the retired Merchandiser submit flow are labelled *legacy*.

<!-- docs-reorganization:notice -->
> **NEEDS_REVIEW — documentation review 2026-10-26.** Useful endpoint contracts coexist with dated deployment/verification summaries; source availability is separate from historical failure evidence. See [current navigation](../features/F05/README.md). Original results and business-rule text below are retained; this notice is not a new execution or approval.
<!-- /docs-reorganization:notice -->

# F05 Mining API Contract

## CURRENT BRANCH CONTRACT — Frontend handoff (2026-11-01)

This block supersedes any older text below that says a Merchandiser may submit mining or read benchmarks. Verified against `SecurityConfig`, the `@PreAuthorize` annotations and the merged-tree test run recorded in the [implementation report](../reports/PASPM_F03_F05_Implementation_Report.md); `openapi.json` is the runtime export of the merged backend (the two forbidden operations are marked `deprecated`).

| Method | Route | Allowed role | Status |
|---|---|---|---|
| GET | `/api/mining/snapshots` | MERCHANDISER, STORE_ADMIN (own Store) | Ready |
| GET | `/api/mining/requests`, `/api/mining/requests/{id}`, `/api/mining/requests/{id}/runs` | MERCHANDISER, STORE_ADMIN (own Store) | Ready |
| GET | `/api/mining/runs/{id}`, `/api/mining/runs/{id}/patterns` | MERCHANDISER, STORE_ADMIN (own Store) | Ready |
| POST | `/api/mining/requests` | nobody (`denyAll`, always 403) | **Forbidden — do not call** |
| GET | `/api/mining/runs/{id}/benchmark` | nobody (`denyAll`, always 403) | **Forbidden — use the System Admin route** |
| GET / PUT | `/api/system/mining/config` | SYSTEM_ADMIN | Ready (versioned configuration) |
| GET | `/api/system/mining/stores/{storeId}/snapshots` | SYSTEM_ADMIN | **PENDING_BUSINESS_APPROVAL — disabled by default (HTTP 403 for every role); group `SNAPSHOTS`**; READY snapshots of the selected Store (`Page<SnapshotView>`) |
| GET | `/api/system/mining/stores/{storeId}/requests`, `/requests/{requestId}` | SYSTEM_ADMIN | **PENDING_BUSINESS_APPROVAL — disabled by default (403); group `REQUESTS`**; Store history and request status (`Page<RequestView>`, `RequestView`) |
| GET | `/api/system/mining/stores/{storeId}/requests/{requestId}/runs` | SYSTEM_ADMIN | **PENDING_BUSINESS_APPROVAL — disabled by default (403); group `RUNS`**; `RunView[]` |
| GET | `/api/system/mining/stores/{storeId}/runs/{runId}/patterns` | SYSTEM_ADMIN | **PENDING_BUSINESS_APPROVAL — disabled by default (403); group `PATTERNS`**; `Page<PatternView>`, utility DESC then canonical key ASC |
| POST | `/api/system/mining/requests` | SYSTEM_ADMIN | Ready; 202, idempotent per Store by `requestKey` |
| GET | `/api/system/mining/stores/{storeId}/runs/{runId}/benchmark` | SYSTEM_ADMIN | Ready |
| POST / GET | `/api/system/mining/schedules`, `/api/system/mining/schedules/{id}` | SYSTEM_ADMIN | Ready; new schedules are created **disabled** |
| PUT | `/api/system/mining/schedules/{id}` | SYSTEM_ADMIN | Disable/edit works; **`enabled=true` returns 503** |
| GET | `/api/system/mining/schedules/{id}/occurrences`, `.../occurrences/{occurrenceId}` | SYSTEM_ADMIN | Ready (history/read-only) |

System Admin read routes (added 2026-11-01): the Store is explicit in the path; an unknown Store, or a request or run that belongs to another Store, returns 404; `page>=0`, `pageSize` 1..100 else 400; DTOs are the same as the Store-scoped routes. They are read-only. The approved requirements contain no explicit sentence authorizing System Admin to read Store results, so Product Owner confirmation is pending (**PENDING_BUSINESS_APPROVAL**, decision D1 in [the register](../decisions/PO_DECISION_REGISTER_F05_2026-11-01.md)). **Access control:** besides `SYSTEM_ADMIN` RBAC, each route group is refused with `403 {"error":"System Admin reads of Store mining results (<GROUP>) are pending Product Owner approval (decision D1)"}` unless the instance lists the group in `paspm.mining.system-store-read.routes` (env `PASPM_MINING_SYSTEM_STORE_READ_ROUTES`, default empty). Groups: `SNAPSHOTS`, `REQUESTS` (list and detail), `RUNS`, `PATTERNS`; opening one never opens another; an unknown name stops the backend at startup. Decision D1 options: (a) all four, (b) `REQUESTS,RUNS`, (c) none. The refusal happens before any Store lookup, so it does not reveal which Store ids exist. Do not open groups in a released environment until D1 is approved. Tests: `SystemStoreReadPolicyTest`, `MiningSystemStoreReadGateTest` (default refusal, role matrix), `MiningSystemStoreReadGranularGateTest` (option b) and `MiningIntegrationTest.systemAdminReadsExplicitStoreSnapshots…` (all groups open). The benchmark route is not gated. Full workflow: [OPTION_B_BACKEND_API_HANDOFF.md](../frontend_handoff/OPTION_B_BACKEND_API_HANDOFF.md).

Scheduler state: the background dispatcher starts only when the instance sets `paspm.mining.schedule-dispatch-enabled=true` (default `false`). Leave it unset; no scheduled run happens and the enable API stays 503. Retry/backoff, lookback, time-zone, monthly-overflow, catch-up and config-change policies still await Product Owner approval and are not applied.

Frontend rule: a 403 on a role's own allowed route means a Store or role mismatch; never offer "Run mining" to a Merchandiser or Store Admin.

Verified implementation: Flyway V1–V17 (F05 tables from V9; configuration, durable dispatch and scheduling from V13–V17), F03 frozen snapshots, external SPMF 2.67a. This contract is based on the controllers, `SecurityConfig` and the runtime OpenAPI export (`openapi.json`), not on proposed routes. The older statement that "all eight operations executed via Swagger UI" is a dated 2026-10 result for the retired Merchandiser flow; see [verification](../features/F05/reference/F05_VERIFICATION.md) for that history only.

## Authentication and authorization

All routes require the F01 Bearer JWT (`Authorization: Bearer <accessToken>`). Roles:

| Role | Allowed F05 operations |
|---|---|
| SYSTEM_ADMIN | Mining configuration (read/write), manual Store-targeted mining request, benchmark, schedule administration and history. The five Store-read routes only when the instance lists the group in `paspm.mining.system-store-read.routes` (decision D1). **No** access to the Store-scoped `/api/mining/**` routes (403). |
| MERCHANDISER | Read-only, own Store: snapshots, requests, runs, patterns. May create the F03 snapshots that F05 consumes. **Cannot** submit mining or read benchmarks. |
| STORE_ADMIN | Read-only, own Store, same routes as Merchandiser. |
| END_CUSTOMER | None. |

The Store of a Store user comes from the authenticated database user (`CatalogAccess`/`StoreScope`); never send `storeId` to `/api/mining/**`. System Admin routes carry `storeId` explicitly in the path or body. Anonymous or invalid token: 401; forbidden role: 403; missing or foreign snapshot/request/run: 404 (an unknown Store in a System Admin path is also 404). Lists return only the caller's Store, and request, run and pattern rows are protected by composite Store foreign keys. IDs are JSON integer numbers.

Base URL depends on the environment (local default `http://localhost:8080`). OpenAPI: `GET /v3/api-docs`; Swagger UI `/swagger-ui/index.html`. The committed `openapi.json` is a runtime export; select your own deployment base URL.

## Endpoint inventory

| Method | Route | Role | Response DTO | Purpose / side effect |
|---|---|---|---|---|
| GET | `/api/system/mining/config` | SYSTEM_ADMIN | `ConfigView` | Active algorithm and minUtil; `version=0` and null fields before first configuration |
| PUT | `/api/system/mining/config` | SYSTEM_ADMIN | `ConfigView` | Append a new version (`expectedVersion` optimistic lock; stale → 409). Existing requests keep their own frozen inputs |
| POST | `/api/system/mining/requests` | SYSTEM_ADMIN | `RequestView` | Durably capture frozen input under the current configuration version; enqueue bounded async work; 202; idempotent by `requestKey` |
| GET | `/api/system/mining/stores/{storeId}/runs/{runId}/benchmark` | SYSTEM_ADMIN | `BenchmarkView` | Measurement metadata and exact comparison status |
| GET | `/api/system/mining/stores/{storeId}/snapshots` | SYSTEM_ADMIN + D1 group `SNAPSHOTS` | `Page<SnapshotView>` | READY snapshots of the Store — **403 by default** |
| GET | `/api/system/mining/stores/{storeId}/requests`, `/requests/{requestId}` | SYSTEM_ADMIN + `REQUESTS` | `Page<RequestView>`, `RequestView` | Store history and status — **403 by default** |
| GET | `/api/system/mining/stores/{storeId}/requests/{requestId}/runs` | SYSTEM_ADMIN + `RUNS` | `RunView[]` | Algorithm runs — **403 by default** |
| GET | `/api/system/mining/stores/{storeId}/runs/{runId}/patterns` | SYSTEM_ADMIN + `PATTERNS` | `Page<PatternView>` | Raw patterns — **403 by default** |
| POST, GET | `/api/system/mining/schedules`, `/{id}` | SYSTEM_ADMIN | schedule `View` | Create (always disabled) / read |
| PUT | `/api/system/mining/schedules/{id}` | SYSTEM_ADMIN | schedule `View` | Edit / disable; `enabled=true` → 503 |
| GET | `/api/system/mining/schedules/{id}/occurrences`, `/{occurrenceId}` | SYSTEM_ADMIN | `Page<OccurrenceView>`, `OccurrenceView` | Read-only history |
| GET | `/api/mining/snapshots` | MERCHANDISER, STORE_ADMIN | `Page<SnapshotView>` | READY snapshot picker (own Store) |
| GET | `/api/mining/requests`, `/requests/{id}`, `/requests/{id}/runs` | MERCHANDISER, STORE_ADMIN | `Page<RequestView>`, `RequestView`, `RunView[]` | Own-Store history, status, runs (run ID ascending) |
| GET | `/api/mining/runs/{id}`, `/runs/{id}/patterns` | MERCHANDISER, STORE_ADMIN | `RunView`, `Page<PatternView>` | Run state/metrics; patterns by utility DESC, canonical key ASC |
| POST | `/api/mining/requests` | nobody | – | **Forbidden (`denyAll`, 403 for every role).** Retired Merchandiser submit |
| GET | `/api/mining/runs/{id}/benchmark` | nobody | – | **Forbidden (`denyAll`, 403 for every role).** Use the System Admin benchmark route |

GET routes return 200. `POST /api/system/mining/requests` returns 202 with durable request/run identifiers; it does not wait for mining, and the status may already be terminal if work finishes quickly. There is no delete, cancel, retry or upload route. A rerun needs a new `requestKey`. GET calls do not mutate snapshots, configurations or Products. Writes touch only F05 tables (configuration versions, requests, runs, patterns, schedules, occurrences). Backend temporary SPMF files never appear in DTOs.

### Availability by state

| State | Routes | Behaviour |
|---|---|---|
| Implemented and verified | config, manual request, benchmark, Store-scoped reads, schedule create/read/edit/disable, occurrence history | Works with the right role |
| Approved but disabled by configuration | – | None: no decision in D1–D10 has been approved |
| Unapproved-policy dependent | five System Admin Store reads (D1), Store picker endpoint (D2, not built), schedule activation and background dispatch (D3–D10) | 403 / absent / 503; dispatcher off unless `paspm.mining.schedule-dispatch-enabled=true` on that instance |
| Deprecated or forbidden | `POST /api/mining/requests`, `GET /api/mining/runs/{id}/benchmark` | 403 for every role; `deprecated` in OpenAPI |


## Request validation

### `PUT /api/system/mining/config` (SYSTEM_ADMIN)

All four fields are required; unknown fields are rejected (400).

| Field | JSON type | Validation |
|---|---|---|
| algorithm | enum string | `HUI_MINER` or `EFIM` — the algorithm used by single-algorithm manual requests |
| minUtilMode | enum string | `PERCENTAGE` or `ABSOLUTE` |
| minUtilValue | decimal JSON number | Greater than 0, at most 24 integer and 6 fractional digits; `PERCENTAGE` additionally ≤ 100 (400 `Percentage minUtil must be in (0,100]`) |
| expectedVersion | integer | ≥ 0; must equal the current version (0 before the first configuration), otherwise 409 `Mining configuration version conflict` |

```http
PUT /api/system/mining/config
Authorization: Bearer <system admin token>
Content-Type: application/json

{"algorithm":"EFIM","minUtilMode":"PERCENTAGE","minUtilValue":0.5,"expectedVersion":0}
```

Response `ConfigView` (200): `{"version":1,"algorithm":"EFIM","minUtilMode":"PERCENTAGE","minUtilValue":0.5,"changedBy":1,"changedAt":"2026-11-02T08:00:00Z"}`. Every change inserts a new `mining_config_version` row; history is never rewritten. A change affects only requests created afterwards.

### `POST /api/system/mining/requests` (SYSTEM_ADMIN)

Required: `storeId`, `snapshotId`, `requestKey`; optional `compareAlgorithms`. Unknown fields are rejected.

| Field | JSON type | Validation |
|---|---|---|
| storeId | integer int32 | Positive, strict integer (strings and fractions rejected); unknown Store → 404 `Store not found` |
| snapshotId | integer int32 | Positive, strict integer; must be a READY F03 snapshot of that Store, otherwise 404 `Utility snapshot not found` |
| requestKey | string UUID | Unique per Store. Replaying the same `snapshotId`/`compareAlgorithms` returns the existing request (202); reuse with different values → 409 |
| compareAlgorithms | boolean, optional | `true` runs HUI-Miner and EFIM on identical bytes and compares them; absent or `false` runs the algorithm of the active configuration only |

The algorithm(s), `minUtilMode` and `minUtilValue` are **not** in the body: they come from the active mining configuration, read under a row lock when the request is captured, and are persisted with the request (the pinned configuration version cannot change afterwards). If no configuration exists yet the call returns 409 `Configure mining policy before execution`. A runtime that is not usable returns 503 with a diagnostic prefix (see "Backend deployment configuration").

```http
POST /api/system/mining/requests
Authorization: Bearer <system admin token>
Content-Type: application/json

{"storeId":3,"snapshotId":7,"requestKey":"009f3ef1-d4ba-4291-9854-b74c6878d26b","compareAlgorithms":true}
```

Response `RequestView` (202). The minUtil conversion described below is applied to the policy values.

### Retired body (legacy, route forbidden)

The former `POST /api/mining/requests` accepted `requestKey`, `snapshotId`, `algorithms`, `minUtilMode` and `minUtilValue` from a Merchandiser. That route now answers 403 for every role. Do not build a form for it.

Snapshot validation: complete frozen metadata/analysis date, READY status, Store ownership, known PRODUCT_ID_V1 mapping, scale6/HALF_UP, valid 64-hex source/matrix hashes, nonempty matrix, positive transaction/Product IDs and quantities, unique item per transaction, exact nonnegative weighted utilities, matching transaction/dataset totals, supported precision and bounded sizes. Product/transaction ownership is checked without reading their prices/scores. Old/new scoringVersion values are preserved metadata; neither expiry nor Weight is recalculated.

The version-specific original F03 matrixHash is retained as provenance. It is not regenerated from a newer DTO, which would falsely invalidate historical JSON. Validation checks persisted metadata/numerical consistency; the new inputHash pins the exact normalized execution bytes. This does not claim cryptographic reauthentication of all legacy snapshot content.

GET path `id` is a signed int64 request/run identifier. Missing/foreign/nonexistent values resolve to 404. Lists/pattern pages accept integer `page>=0`, `pageSize=1..100` (defaults 0/20). No server filter or custom sort parameter is implemented. Pattern pagination controls display only; it does not change minUtil or stop mining at Top N.

## Deterministic conversion and minUtil

1. Read only existing stored weighted utilities, transaction IDs and Product mapping in a repeatable-read transaction. Freeze input/mapping and snapshot metadata into MINING_REQUEST before enqueueing. A running worker never reloads current Product/configuration or F03 matrix.
2. Require stored values exact at scale6; F03 already applied HALF_UP. Reject unsupported finer precision, negative/null values or signed64 overflow; do not silently truncate or recalculate values.
3. Find GCD of all scale6 integers, using divisor1 for an all-zero dataset. Divide exactly by GCD. Dense SPMF IDs are assigned to sorted real Product IDs; transactions and IDs are sorted.
4. Recompute transaction utility solely by summing these normalized item utilities. Dataset total must fit signed int32 because verified HUI-Miner/EFIM implementations use int32 TWU/utility arrays and threshold APIs.
5. PERCENTAGE: `ceil(minUtilValue * totalScaledUtility / 100)`. ABSOLUTE: `ceil(minUtilValue * 10^6 / divisor)`. Require positive int32. Record minUtilSpmf and `effectiveMinUtility = minUtilSpmf * divisor / 10^6`.
6. Both algorithms receive exactly the same text bytes/hash, dense mapping and threshold. Pattern utilities return in original units using the same exact divisor; no new rounding is introduced.

Zero-valued items were actually verified with both algorithms. Positive absolute thresholds on all-zero datasets can complete with no patterns. Percentage on total0 is rejected. A threshold above the maximum possible utility is valid and may yield an empty result. Suggested 0.2/0.5/1 percent presets are not business restrictions.

For the executed five-transaction fixture: original total96, scale6, divisor1000000, totalScaledUtility96. Absolute30 and percentage31.25 both produce minUtilSpmf30/effectiveMinUtility30. Each algorithm returns eight equivalent patterns.

## Response states, nullability and semantics

Request status: PENDING, RUNNING, COMPLETED, FAILED. Run status additionally includes TIMED_OUT. Comparison status: NOT_REQUESTED (one algorithm), PENDING, MATCH (exact complete canonical set+utility equality), FAILED (mismatch), BLOCKED (run failure/capacity/recovery prevents comparison). Successful empty results can be COMPLETED/MATCH with patternCount0. Completed individual runs can remain visible when the overall comparison/request failed; do not label that benchmark successful.

Run startedAt is null before claim. completedAt is null until terminal. Runtime, memory, environment and patternCount are null until measured/successful; failed runs may have no measurements. failureCode/failureMessage are null on successful/pending runs. Support is always null because the selected SPMF output does not measure it. Null is not zero. `expiryWindowDays` and timeZone inside historical F03 snapshot metadata can be null; frozen scores/evidence follow the F03 contract.

`utility` is weighted frozen utility summed for the Products when all co-occur, not revenue/support or a recommendation. `productIds` are real sorted Product IDs; dense SPMF IDs remain internal. Repeated equivalent patterns in different runs have different pattern IDs. Utilities/divisors and long IDs are JSON numbers; Frontend must preserve large-integer/decimal precision.

### Field/type inventory from runtime OpenAPI

### CreateRequest (legacy DTO of the forbidden route; kept for reference)

| Field | Type | Notes |
|---|---|---|
| requestKey | string (uuid) | Required |
| snapshotId | integer (int32) | Required |
| algorithms | string[] | Required |
| minUtilMode | string | Required; PERCENTAGE, ABSOLUTE |
| minUtilValue | number | Required; Positive value, at most 6 decimal places; PERCENTAGE in (0,100], ABSOLUTE in frozen weighted utility units |

### RequestView

| Field | Type | Notes |
|---|---|---|
| requestId | integer (int64) | See state/business rules above |
| storeId | integer (int32) | See state/business rules above |
| requestedBy | integer (int32) | See state/business rules above |
| requestKey | string (uuid) | See state/business rules above |
| snapshotId | integer (int32) | See state/business rules above |
| algorithms | string[] | See state/business rules above |
| minUtilMode | string | PERCENTAGE, ABSOLUTE |
| minUtilValue | number | See state/business rules above |
| minUtilSpmf | integer (int32) | See state/business rules above |
| effectiveMinUtility | number | See state/business rules above |
| status | string | PENDING, RUNNING, COMPLETED, FAILED |
| comparisonStatus | string | NOT_REQUESTED, PENDING, MATCH, FAILED, BLOCKED |
| snapshotHash | string | See state/business rules above |
| inputHash | string | See state/business rules above |
| conversionVersion | string | See state/business rules above |
| utilityScale | integer (int32) | See state/business rules above |
| roundingMode | string | See state/business rules above |
| divisor | integer (int64) | See state/business rules above |
| totalScaledUtility | integer (int32) | See state/business rules above |
| transactionCount | integer (int32) | See state/business rules above |
| distinctItems | integer (int32) | See state/business rules above |
| inputBytes | integer (int32) | See state/business rules above |
| snapshot | SnapshotView | See state/business rules above |
| createdAt | string (date-time) | See state/business rules above |

### RunView

| Field | Type | Notes |
|---|---|---|
| runId | integer (int64) | See state/business rules above |
| requestId | integer (int64) | See state/business rules above |
| storeId | integer (int32) | See state/business rules above |
| snapshotId | integer (int32) | See state/business rules above |
| algorithm | string | HUI_MINER, EFIM |
| attemptNo | integer (int32) | See state/business rules above |
| libraryVersion | string | See state/business rules above |
| status | string | PENDING, RUNNING, COMPLETED, FAILED, TIMED_OUT |
| minUtilSpmf | integer (int32) | See state/business rules above |
| inputHash | string | See state/business rules above |
| conversionVersion | string | See state/business rules above |
| startedAt | string/null (date-time) | Nullable |
| completedAt | string/null (date-time) | Nullable |
| runtimeMillis | integer/null (int64) | Nullable |
| memoryMiB | number/null (double) | Nullable |
| memoryMethod | string | See state/business rules above |
| runtimeMethod | string | See state/business rules above |
| environment | string/null | Nullable |
| patternCount | integer/null (int32) | Nullable |
| failureCode | string/null | Nullable |
| failureMessage | string/null | Nullable |

### PatternView

| Field | Type | Notes |
|---|---|---|
| patternId | integer (int64) | See state/business rules above |
| runId | integer (int64) | See state/business rules above |
| productIds | integer (int32)[] | See state/business rules above |
| utility | number | See state/business rules above |
| utilitySpmf | integer (int32) | See state/business rules above |
| patternSize | integer (int32) | See state/business rules above |
| supportCount | integer/null (int32) | Nullable; Not measured by SPMF; always null |

### BenchmarkView

| Field | Type | Notes |
|---|---|---|
| run | RunView | See state/business rules above |
| snapshotHash | string | See state/business rules above |
| transactionCount | integer (int32) | See state/business rules above |
| distinctItems | integer (int32) | See state/business rules above |
| inputBytes | integer (int32) | See state/business rules above |
| divisor | integer (int64) | See state/business rules above |
| utilityScale | integer (int32) | See state/business rules above |
| effectiveMinUtility | number | See state/business rules above |
| comparisonStatus | string | See state/business rules above |
| repetitionPolicy | string | See state/business rules above |

### ManualRequest

| Field | Type | Notes |
|---|---|---|
| storeId | integer (int32) | Required, positive |
| snapshotId | integer (int32) | Required, positive, READY snapshot of that Store |
| requestKey | string (uuid) | Required |
| compareAlgorithms | boolean | Optional; default false |

### ConfigRequest / ConfigView (`/api/system/mining/config`)

| Field | Type | Notes |
|---|---|---|
| algorithm | string | `HUI_MINER`, `EFIM`; null in the view at version 0 |
| minUtilMode | string | `PERCENTAGE`, `ABSOLUTE`; null at version 0 |
| minUtilValue | number | > 0; null at version 0 |
| expectedVersion | integer | Request only; must equal the current `version` |
| version | integer (int64) | View only; 0 = never configured |
| changedBy | integer/null | View only; System Admin user id |
| changedAt | string/null (date-time) | View only |

### PageRequestView

| Field | Type | Notes |
|---|---|---|
| items | RequestView[] | See state/business rules above |
| page | integer (int32) | See state/business rules above |
| pageSize | integer (int32) | See state/business rules above |
| totalElements | integer (int64) | See state/business rules above |

### PageSnapshotView

| Field | Type | Notes |
|---|---|---|
| items | SnapshotView[] | See state/business rules above |
| page | integer (int32) | See state/business rules above |
| pageSize | integer (int32) | See state/business rules above |
| totalElements | integer (int64) | See state/business rules above |

### PagePatternView

| Field | Type | Notes |
|---|---|---|
| items | PatternView[] | See state/business rules above |
| page | integer (int32) | See state/business rules above |
| pageSize | integer (int32) | See state/business rules above |
| totalElements | integer (int64) | See state/business rules above |

### SnapshotView

| Field | Type | Notes |
|---|---|---|
| snapshotId | integer (int32) | See state/business rules above |
| storeId | integer (int32) | See state/business rules above |
| createdBy | integer (int32) | See state/business rules above |
| rangeStart | string (date) | See state/business rules above |
| rangeEnd | string (date) | See state/business rules above |
| asOfDate | string (date) | See state/business rules above |
| configuration | FrozenConfiguration | See state/business rules above |
| sourceHash | string | See state/business rules above |
| matrixHash | string | See state/business rules above |
| totalWeightedUtility | number | See state/business rules above |
| roundingScale | integer (int32) | See state/business rules above |
| status | string | See state/business rules above |
| createdAt | string (date-time) | See state/business rules above |
| warnings | string[] | See state/business rules above |

### FrozenConfiguration

| Field | Type | Notes |
|---|---|---|
| config | WeightConfig | See state/business rules above |
| lambdaPolicy | LambdaPolicy | See state/business rules above |
| effectiveCoefficients | EffectiveCoefficients | See state/business rules above |
| analysisDate | string (date) | See state/business rules above |
| source | string | See state/business rules above |
| utilityScale | integer (int32) | See state/business rules above |
| roundingMode | string | See state/business rules above |
| itemMapping | string | See state/business rules above |
| timeZone | string | Nullable |

### WeightConfig

| Field | Type | Notes |
|---|---|---|
| enableStock | boolean | See state/business rules above |
| enableExpiry | boolean | See state/business rules above |
| enableSeason | boolean | See state/business rules above |
| alpha | number | See state/business rules above |
| beta | number | See state/business rules above |
| gamma | number | See state/business rules above |
| lambda | number | See state/business rules above |
| expiryWindowDays | integer (int32) | Nullable; Legacy Store setting; ignored by v1.2 scoring. Product.expiryPushDays is authoritative |
| version | integer (int64) | See state/business rules above |
| scoringVersion | string | See state/business rules above |

### LambdaPolicy

| Field | Type | Notes |
|---|---|---|
| lambdaMin | number | See state/business rules above |
| lambdaMax | number | See state/business rules above |
| version | integer (int64) | See state/business rules above |

### EffectiveCoefficients

| Field | Type | Notes |
|---|---|---|
| alpha | number | See state/business rules above |
| beta | number | See state/business rules above |
| gamma | number | See state/business rules above |

## Per-endpoint examples and behavior

### GET `/api/mining/snapshots`

MERCHANDISER/STORE_ADMIN, own Store. No request body and no business-data mutation.

Query: page=0, pageSize=20 by default; bounds above.

Response `Page<SnapshotView>`; full executed JSON: [snapshot_list_response.json](../frontend_handoff/F05_examples/snapshot_list_response.json). These files contain complete real response bodies with synthetic QA IDs; replace IDs for your Store.

Success 200. Errors: 401/403; detail routes/snapshot submission may return 404. Invalid payload/pagination:400; POST key conflict:409; unavailable/unverified SPMF:503. Verification: automated tests in the [audit matrix](../reports/F03_F05_API_AUDIT_MATRIX_2026-11-02.md); the 2026-10 Swagger UI execution is historical.

### POST `/api/mining/requests` — FORBIDDEN

`denyAll()`: 403 for every role (anonymous 401). Retired Merchandiser submit; kept mapped only so the refusal is explicit. Use `POST /api/system/mining/requests` (System Admin).

### POST `/api/system/mining/requests`

SYSTEM_ADMIN only. Body `ManualRequest` (see "Request validation"). Creates a durable request and one run per algorithm (one, or two when `compareAlgorithms=true`).

Response `RequestView` (202). Errors: 400 invalid body; 401/403; 404 unknown Store or snapshot; 409 `requestKey` reused with different values, or no mining configuration yet; 503 SPMF runtime unavailable (diagnostic prefix). Tests: `MiningIntegrationTest.systemAdminSubmitsFrozenPolicyForAnExplicitStoreWithoutLegacyBypass`, `concurrentDuplicateSubmissionsCreateOnlyOneRequestAndOneRunPerAlgorithm`, `MiningHttpMySqlTest`.

### GET `/api/mining/requests`

MERCHANDISER/STORE_ADMIN, own Store. No request body and no business-data mutation.

Query: page=0, pageSize=20 by default; bounds above.

Response `Page<RequestView>`; full executed JSON: [request_list_response.json](../frontend_handoff/F05_examples/request_list_response.json). These files contain complete real response bodies with synthetic QA IDs; replace IDs for your Store.

Success 200. Errors: 401/403; detail routes/snapshot submission may return 404. Invalid payload/pagination:400; POST key conflict:409; unavailable/unverified SPMF:503. Verification: automated tests in the [audit matrix](../reports/F03_F05_API_AUDIT_MATRIX_2026-11-02.md); the 2026-10 Swagger UI execution is historical.

### GET `/api/mining/requests/{id}`

MERCHANDISER/STORE_ADMIN, own Store. No request body and no business-data mutation.

Path id: request ID for `/requests/{id}` routes; run ID for `/runs/{id}` routes.

No additional query parameters.

Response `RequestView`; full executed JSON: [request_response.json](../frontend_handoff/F05_examples/request_response.json). These files contain complete real response bodies with synthetic QA IDs; replace IDs for your Store.

Success 200. Errors: 401/403; detail routes/snapshot submission may return 404. Invalid payload/pagination:400; POST key conflict:409; unavailable/unverified SPMF:503. Verification: automated tests in the [audit matrix](../reports/F03_F05_API_AUDIT_MATRIX_2026-11-02.md); the 2026-10 Swagger UI execution is historical.

### GET `/api/mining/requests/{id}/runs`

MERCHANDISER/STORE_ADMIN, own Store. No request body and no business-data mutation.

Path id: request ID for `/requests/{id}` routes; run ID for `/runs/{id}` routes.

No additional query parameters.

Response `RunView[]`; full executed JSON: [runs_response.json](../frontend_handoff/F05_examples/runs_response.json). These files contain complete real response bodies with synthetic QA IDs; replace IDs for your Store.

Success 200. Errors: 401/403; detail routes/snapshot submission may return 404. Invalid payload/pagination:400; POST key conflict:409; unavailable/unverified SPMF:503. Verification: automated tests in the [audit matrix](../reports/F03_F05_API_AUDIT_MATRIX_2026-11-02.md); the 2026-10 Swagger UI execution is historical.

### GET `/api/mining/runs/{id}`

MERCHANDISER/STORE_ADMIN, own Store. No request body and no business-data mutation.

Path id: request ID for `/requests/{id}` routes; run ID for `/runs/{id}` routes.

No additional query parameters.

Response `RunView`; full executed JSON: [run_response.json](../frontend_handoff/F05_examples/run_response.json). These files contain complete real response bodies with synthetic QA IDs; replace IDs for your Store.

Success 200. Errors: 401/403; detail routes/snapshot submission may return 404. Invalid payload/pagination:400; POST key conflict:409; unavailable/unverified SPMF:503. Verification: automated tests in the [audit matrix](../reports/F03_F05_API_AUDIT_MATRIX_2026-11-02.md); the 2026-10 Swagger UI execution is historical.

### GET `/api/mining/runs/{id}/patterns`

MERCHANDISER/STORE_ADMIN, own Store. No request body and no business-data mutation.

Path id: request ID for `/requests/{id}` routes; run ID for `/runs/{id}` routes.

Query: page=0, pageSize=20 by default; bounds above.

Response `Page<PatternView>`; full executed JSON: [patterns_response.json](../frontend_handoff/F05_examples/patterns_response.json). These files contain complete real response bodies with synthetic QA IDs; replace IDs for your Store.

Success 200. Errors: 401/403; detail routes/snapshot submission may return 404. Invalid payload/pagination:400; POST key conflict:409; unavailable/unverified SPMF:503. Verification: automated tests in the [audit matrix](../reports/F03_F05_API_AUDIT_MATRIX_2026-11-02.md); the 2026-10 Swagger UI execution is historical.

### GET `/api/mining/runs/{id}/benchmark` — FORBIDDEN

`denyAll()`: 403 for every role. Benchmarks are a System Admin duty.

### GET `/api/system/mining/stores/{storeId}/runs/{runId}/benchmark`

SYSTEM_ADMIN. Not covered by the D1 gate. Response `BenchmarkView`; example: [benchmark_response.json](../frontend_handoff/F05_examples/benchmark_response.json). 404 when the Store is unknown or the run belongs to another Store. Note that a System Admin can only learn a `runId` through the D1-gated routes (`REQUESTS`/`RUNS`) or from a Store user.


## Error examples

Application exceptions use the existing format:

```json
{"error":"Mining run not found"}
```

404 covers unknown/foreign records. Example 409: `{"error":"requestKey already used for a different mining request"}`. Example 400: `{"error":"Percentage minUtil must be in (0,100]"}`. Examples captured from Swagger are in `F05_examples/error_*_response.json`. Security middleware may return an empty 401 body; do not require a JSON error body for every 401.

POST validation may fail with 400 before creating a request. Once accepted, execution failures are durable run statuses rather than a later HTTP error on the original POST. Examples: SPMF_FAILED, TIMED_OUT, OUTPUT_LIMIT, INVALID_OUTPUT, INVALID_METRICS, INTERRUPTED, EXECUTION_FAILED. GET itself still returns200 for an owned FAILED/TIMED_OUT run. Messages are sanitized and do not expose process logs, server paths, credentials or tokens.

## Lifecycle and benchmark methodology

Each Backend instance has one worker and eight waiting slots. The database is the durable dispatch authority: a full local queue leaves a PENDING request eligible for a later poll, rather than automatically failing it. Workers claim a time-limited database lease with a fencing token; expired owned RUNNING work can be recovered, while unowned historical RUNNING records require investigation. Terminal requests are not automatically replayed. HTTP requests use requestKey and normalized payload for idempotency. Worker startup requires Flyway. See the [worker architecture](../architecture/F05_WORKER_DISPATCH.md) for transaction boundaries, recovery limitations and the still-disabled scheduler.

Each successful run persists the complete normalized pattern batch and COMPLETED status atomically. Failed parsing/transaction rollback leaves no partial successful patterns. Pattern uniqueness uses per-run canonical-key hash; each pattern item has a Store-scoped Product FK.

Sequential child JVMs share input/minUtil, limits, binary fingerprint and environment. Runtime includes JVM startup and mining/IO, excludes common input preparation and Backend verification/persistence. SPMF sampled Java heap MiB is not RSS. One cold execution each, no warmup or repeated-trial performance conclusion. Pattern validation shares the execution timeout. Parent persistence is not an algorithm benchmark measurement. Exact canonical itemset and normalized integer utility equality is required for MATCH; order/count alone is insufficient. Conversion version and memory/runtime measurement methods are persisted for historical accuracy.

## Backend deployment configuration

The external JAR is not bundled in Git/backend artifact. Verified SPMF Main version2.67a, SHA256 `4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a`, class major69 (Java25). Backend compiler release is17; configure a separate Java25 child executable if the Backend runs on17. A Java17-only host cannot execute this JAR. JAR licensing is upstream GPLv3; this change does not redistribute it. [Official SPMF distribution/license guidance](https://www.philippe-fournier-viger.com/spmf/developers.php).

| Property | Default / supported values |
|---|---|
| paspm.mining.spmf-jar | Empty; trusted absolute readable JAR path required; fingerprint pinned |
| paspm.mining.java-executable | Empty; explicitly configure a trusted absolute Java25+ executable independently from Backend Java17 |
| paspm.mining.timeout-millis | 60000; 1..300000 |
| paspm.mining.heap-mib | 256; 32..2048 |
| paspm.mining.max-patterns | 2000; 1..10000 |

Hard limits: 20,000 snapshot rows, 256 Products, 4MiB algorithm output, 64KiB process log. Numeric overflow rejected before enqueueing. Child commands use ProcessBuilder fixed arguments, never shell concatenation; temp files are cleaned on completion/failure/interruption. No temporary paths are exposed through APIs. Configure database connection/session timestamps consistently, normally UTC (`forceConnectionTimeZoneToSession=true` was used in isolated MySQL QA).

The 2026-10-20 P0 correction quotes row_number in V7 and F04 SQL. Fresh MySQL8.4 V1–V9 and F04/F05 regression tests now pass. Existing applied V7 history needs controlled checksum reconciliation; see [V7 recovery](../setup/FLYWAY_MYSQL_V7_RECOVERY.md). No automatic repair or baselining occurs.

Before admitting new work, runtime preflight validates the pinned JAR, separately configured Java25+, actual SPMF version entrypoint and temporary storage. Invalid runtime returns HTTP503 using the existing error envelope, with diagnostic prefixes SPMF_JAR_NOT_CONFIGURED, SPMF_JAR_NOT_FOUND, SPMF_JAR_NOT_READABLE, SPMF_JAR_HASH_MISMATCH, SPMF_JAVA_NOT_FOUND, SPMF_JAVA_VERSION_UNSUPPORTED or SPMF_RUNTIME_UNAVAILABLE. Historical read APIs remain available. See [portable setup](../setup/F05_SPMF_RUNTIME_SETUP.md) for environment variables, caching and bounds.

F06 raw-pattern semantics and deferred scheduling are documented in [F05_F06_HANDOFF.md](../features/F05/reference/F05_F06_HANDOFF.md). No Frontend or F06 implementation was changed.


## Scheduler administration on this branch (2026-11-01, unchanged 2026-11-02)

All routes require a valid SYSTEM_ADMIN bearer token. `POST /api/system/mining/schedules` creates a disabled schedule; `GET /api/system/mining/schedules?page=0&pageSize=20` lists schedules; `GET /api/system/mining/schedules/{id}` reads one; `PUT /api/system/mining/schedules/{id}` updates it with `schedule`, `enabled` and optimistic `expectedVersion`. An enable request returns **503** until acceptance is complete. `GET /api/system/mining/schedules/{id}/occurrences` lists history; `GET /api/system/mining/schedules/{id}/occurrences/{occurrenceId}` reads an occurrence. A missing or mismatched ID returns 404. Unauthorized users receive 401 or 403.

A schedule requires positive integer `storeId`, `frequency` WEEKLY or MONTHLY, `localTime`, IANA `timeZone`, integer `lookbackDays` 1–3660, and the matching `dayOfWeek` (1–7) or `dayOfMonth` (1–31); `compareAlgorithms` is optional. Occurrence details include the saved schedule/config version, Store, time window, analysis date, snapshot/request IDs, status, failure code, `attemptCount` and `nextRetryAt`. This describes source behavior; independent multi-container and interactive Swagger acceptance remain outstanding.

The acceptance continuation exercised real HTTP with synthetic, test-signed System Admin and other-role JWTs: create 201; list/detail/history 200; missing occurrence 404; disable update 200; activation 503; STORE_ADMIN, MERCHANDISER and END_CUSTOMER each received 403 across six administrative operations. See [test evidence](../testing/evidence/F05_SCHEDULER_CRASH_ACCEPTANCE_2026-11-01.txt). These results do not approve scheduler activation or scheduling policies.

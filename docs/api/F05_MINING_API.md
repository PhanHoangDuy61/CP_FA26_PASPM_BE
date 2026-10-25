<!-- docs-reorganization:notice -->
> **NEEDS_REVIEW — documentation review 2026-10-26.** Useful endpoint contracts coexist with dated deployment/verification summaries; source availability is separate from historical failure evidence. See [current navigation](../features/F05/README.md). Original results and business-rule text below are retained; this notice is not a new execution or approval.
<!-- /docs-reorganization:notice -->

# F05 Mining API Contract

Verified implementation: JDBC/Flyway V9, F03 frozen snapshots, external SPMF 2.67a. This contract is based on implemented DTOs/controllers and the runtime OpenAPI export, not proposed routes. All eight operations executed via Swagger UI. See [verification and deployment blocker](../features/F05/reference/F05_VERIFICATION.md).

## Authentication and authorization

All routes below require the existing F01 Bearer JWT. MERCHANDISER may create and read within their Store. STORE_ADMIN may read within their Store and cannot create. SYSTEM_ADMIN and END_CUSTOMER have no F05 API permission. Store is derived from the active authenticated user using CatalogAccess/StoreScope. Never send storeId. No cross-Store operational grant was invented.

Anonymous/invalid token 401; forbidden role 403; missing or foreign snapshot/request/run/pattern/benchmark 404. List queries only return the caller's Store. Request records, runs and pattern items are also protected by composite Store foreign keys. IDs are JSON integer numbers, not paths to files.

Base URL depends on environment, usually localhost:8080; actual isolated QA was 127.0.0.1:18085. OpenAPI `GET /v3/api-docs`, Swagger `/swagger-ui/index.html`. The committed export contains the actual QA server URL; clients must select their deployment base URL.

## Endpoint inventory

| Method | Route | Response DTO | Purpose / side effect |
|---|---|---|---|
| GET | `/api/mining/snapshots` | `Page<SnapshotView>` | READY snapshot picker; validates numerical eligibility at submission |
| POST | `/api/mining/requests` | `RequestView` | Durably capture frozen input; enqueue bounded async work; 202 |
| GET | `/api/mining/requests` | `Page<RequestView>` | Store history, newest first |
| GET | `/api/mining/requests/{id}` | `RequestView` | Request state, immutable snapshot and conversion provenance |
| GET | `/api/mining/requests/{id}/runs` | `RunView[]` | Separate algorithm runs, run ID ascending |
| GET | `/api/mining/runs/{id}` | `RunView` | Run state and metrics |
| GET | `/api/mining/runs/{id}/patterns` | `Page<PatternView>` | Raw Products/utility; sorted utility DESC, canonical key lexical ASC |
| GET | `/api/mining/runs/{id}/benchmark` | `BenchmarkView` | Measurement metadata and exact comparison status |

GET routes return 200. POST returns 202 with durable request/run identifiers accessible through the runs route. It does not wait for mining. The response status may already be terminal if work finishes quickly or capacity is unavailable; inspect it.

No DELETE, cancellation, retry, upload, configuration or scheduling route exists. GET calls do not mutate snapshots, configurations or Products. POST writes only F05 request/run/result tables. Backend temporary SPMF files never appear in request or response DTOs.

## Request validation

`POST /api/mining/requests` body: all five fields required, unknown fields rejected.

| Field | JSON type | Validation |
|---|---|---|
| requestKey | string UUID | Stable per intended submission, unique per Store; replay identical normalized payload is idempotent 202; reuse for changed payload 409 |
| snapshotId | integer int32 | Positive, strict integer (fraction/string coercion rejected), existing READY own-Store F03 snapshot |
| algorithms | array of enum strings | One or two distinct values: HUI_MINER, EFIM; no nulls; input order is normalized |
| minUtilMode | enum string | PERCENTAGE or ABSOLUTE |
| minUtilValue | decimal JSON number | Positive, up to 24 integer/6 fractional digits; percentage additionally <=100; converted threshold must fit positive int32 |

Actual executed request:

```json
{
  "requestKey": "009f3ef1-d4ba-4291-9854-b74c6878d26b",
  "snapshotId": 2,
  "algorithms": [
    "HUI_MINER",
    "EFIM"
  ],
  "minUtilMode": "ABSOLUTE",
  "minUtilValue": 30
}
```

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

### CreateRequest

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

Success 200. Errors: 401/403; detail routes/snapshot submission may return 404. Invalid payload/pagination:400; POST key conflict:409; unavailable/unverified SPMF:503. Verification: executed through Swagger UI.

### POST `/api/mining/requests`

MERCHANDISER only. JSON body: CreateRequest above. Creates durable request and one run per selected algorithm.

No additional query parameters.

Response `RequestView`; full executed JSON: [create_response.json](../frontend_handoff/F05_examples/create_response.json). These files contain complete real response bodies with synthetic QA IDs; replace IDs for your Store.

Success 202. Errors: 401/403; detail routes/snapshot submission may return 404. Invalid payload/pagination:400; POST key conflict:409; unavailable/unverified SPMF:503. Verification: executed through Swagger UI.

### GET `/api/mining/requests`

MERCHANDISER/STORE_ADMIN, own Store. No request body and no business-data mutation.

Query: page=0, pageSize=20 by default; bounds above.

Response `Page<RequestView>`; full executed JSON: [request_list_response.json](../frontend_handoff/F05_examples/request_list_response.json). These files contain complete real response bodies with synthetic QA IDs; replace IDs for your Store.

Success 200. Errors: 401/403; detail routes/snapshot submission may return 404. Invalid payload/pagination:400; POST key conflict:409; unavailable/unverified SPMF:503. Verification: executed through Swagger UI.

### GET `/api/mining/requests/{id}`

MERCHANDISER/STORE_ADMIN, own Store. No request body and no business-data mutation.

Path id: request ID for `/requests/{id}` routes; run ID for `/runs/{id}` routes.

No additional query parameters.

Response `RequestView`; full executed JSON: [request_response.json](../frontend_handoff/F05_examples/request_response.json). These files contain complete real response bodies with synthetic QA IDs; replace IDs for your Store.

Success 200. Errors: 401/403; detail routes/snapshot submission may return 404. Invalid payload/pagination:400; POST key conflict:409; unavailable/unverified SPMF:503. Verification: executed through Swagger UI.

### GET `/api/mining/requests/{id}/runs`

MERCHANDISER/STORE_ADMIN, own Store. No request body and no business-data mutation.

Path id: request ID for `/requests/{id}` routes; run ID for `/runs/{id}` routes.

No additional query parameters.

Response `RunView[]`; full executed JSON: [runs_response.json](../frontend_handoff/F05_examples/runs_response.json). These files contain complete real response bodies with synthetic QA IDs; replace IDs for your Store.

Success 200. Errors: 401/403; detail routes/snapshot submission may return 404. Invalid payload/pagination:400; POST key conflict:409; unavailable/unverified SPMF:503. Verification: executed through Swagger UI.

### GET `/api/mining/runs/{id}`

MERCHANDISER/STORE_ADMIN, own Store. No request body and no business-data mutation.

Path id: request ID for `/requests/{id}` routes; run ID for `/runs/{id}` routes.

No additional query parameters.

Response `RunView`; full executed JSON: [run_response.json](../frontend_handoff/F05_examples/run_response.json). These files contain complete real response bodies with synthetic QA IDs; replace IDs for your Store.

Success 200. Errors: 401/403; detail routes/snapshot submission may return 404. Invalid payload/pagination:400; POST key conflict:409; unavailable/unverified SPMF:503. Verification: executed through Swagger UI.

### GET `/api/mining/runs/{id}/patterns`

MERCHANDISER/STORE_ADMIN, own Store. No request body and no business-data mutation.

Path id: request ID for `/requests/{id}` routes; run ID for `/runs/{id}` routes.

Query: page=0, pageSize=20 by default; bounds above.

Response `Page<PatternView>`; full executed JSON: [patterns_response.json](../frontend_handoff/F05_examples/patterns_response.json). These files contain complete real response bodies with synthetic QA IDs; replace IDs for your Store.

Success 200. Errors: 401/403; detail routes/snapshot submission may return 404. Invalid payload/pagination:400; POST key conflict:409; unavailable/unverified SPMF:503. Verification: executed through Swagger UI.

### GET `/api/mining/runs/{id}/benchmark`

MERCHANDISER/STORE_ADMIN, own Store. No request body and no business-data mutation.

Path id: request ID for `/requests/{id}` routes; run ID for `/runs/{id}` routes.

No additional query parameters.

Response `BenchmarkView`; full executed JSON: [benchmark_response.json](../frontend_handoff/F05_examples/benchmark_response.json). These files contain complete real response bodies with synthetic QA IDs; replace IDs for your Store.

Success 200. Errors: 401/403; detail routes/snapshot submission may return 404. Invalid payload/pagination:400; POST key conflict:409; unavailable/unverified SPMF:503. Verification: executed through Swagger UI.

## Error examples

Application exceptions use the existing format:

```json
{"error":"Mining run not found"}
```

404 covers unknown/foreign records. Example 409: `{"error":"requestKey already used for a different mining request"}`. Example 400: `{"error":"Percentage minUtil must be in (0,100]"}`. Examples captured from Swagger are in `F05_examples/error_*_response.json`. Security middleware may return an empty 401 body; do not require a JSON error body for every 401.

POST validation may fail with 400 before creating a request. Once accepted, execution failures are durable run statuses rather than a later HTTP error on the original POST. Examples: SPMF_FAILED, TIMED_OUT, OUTPUT_LIMIT, INVALID_OUTPUT, INVALID_METRICS, INTERRUPTED, QUEUE_FULL, EXECUTION_FAILED. GET itself still returns200 for an owned FAILED/TIMED_OUT run. Messages are sanitized and do not expose process logs, server paths, credentials or tokens.

## Lifecycle and benchmark methodology

Single bounded worker with eight waiting slots. POST commits request/runs before enqueueing. Database compare-and-set claims prevent duplicate processing; requestKey + normalized payload handles HTTP retries. If queueing fails, persist FAILED/QUEUE_FULL. Terminal requests are not replayed. Startup recovery changes unfinished PENDING/RUNNING jobs to FAILED/INTERRUPTED; retry intentionally with a new UUID. Worker is enabled with Flyway; F01-only test contexts with Flyway disabled do not require mining tables. This MVP is single-instance; a shared database with multiple backend workers requires a future lease design.

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

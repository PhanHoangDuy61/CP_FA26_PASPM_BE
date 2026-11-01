# PASPM Backend API Handoff for Frontend — F03 Utility, F05 Mining (Option B baseline)

Status (2026-11-01, branch `Khoi/feat/f03-f05-admin-mining`, merge of `origin/dev` `6d91215`): **CORE_API_VERIFIED · FRONTEND_INTEGRATION_READY for the verified surface only · OPTION_B_NOT_READY.** The verified surface is everything in sections 1–4, 6, 7 and the System Admin configuration/manual-request/benchmark/schedule-administration routes; it was exercised by `scripts/verify-option-b-macos.sh --run` (evidence in section 11). Two things are deliberately **not** open to the Frontend: (1) the five System Admin read routes of section 5 are **disabled (HTTP 403 for every role)** until Product Owner decision D1; (2) schedule activation returns **503**. `docs/api/openapi.json` is the runtime export of this backend. No endpoint or field below is invented; every one was read from the controllers, records and `SecurityConfig` and cross-checked against the runtime OpenAPI.

| Status | Meaning here |
|---|---|
| `CORE_API_VERIFIED` | Authentication, F03 utility/snapshot, F05 configuration, manual System Admin request, status/runs/patterns/benchmark, F06 generation and review pass the automated suites on H2 and on disposable MySQL 8.4 with real SPMF (Java 25). |
| `FRONTEND_INTEGRATION_READY` | The Frontend may build against the verified surface now. Excluded: the five gated read routes (403), schedule activation (503), a Store picker (no endpoint, decision D2). |
| `OPTION_B_NOT_READY` | Product Owner decisions D1–D10 are open; real scheduler activation and end-to-end acceptance have not been run; skipped suites (multi-JVM, clone API) were not executed on this merge commit. |

## 1. Base URL and authentication
- Base URL: configure per environment (`VITE_API_BASE_URL` or equivalent); local default `http://localhost:8080`. Swagger UI `/swagger-ui/index.html`, OpenAPI `/v3/api-docs`.
- Login: `POST /api/auth/login` body `{"email","password"}` → `{"accessToken","expiresIn","tokenType"}`. Send `Authorization: Bearer <accessToken>` on every other call. `GET /api/auth/me` returns the current account. The Store is taken from the token, never from the URL, except the System Admin routes below that take `storeId` explicitly.
- Pagination: `?page=0&pageSize=20`; response `{items,page,pageSize,totalElements}`.

## 2. Errors
- Envelope: `{"error":"<message>"}` for 400 (`Invalid request fields` for malformed/invalid bodies), 403 (`Forbidden`), 409, 413, 503. 401 is returned for a missing/invalid token.
- 400 validation · 401 unauthenticated · 403 wrong role · 404 not found or other Store's resource · 409 version/idempotency conflict · 503 SPMF runtime unavailable or feature not available.
- Optimistic locking: send the `version`/`expectedVersion` you last read. A stale value returns **409**; reload and retry. Never reuse a `requestKey` (UUID) with a different payload (409); the same key and payload returns the existing request.

## 3. Role to endpoint matrix
| Endpoint | SYSTEM_ADMIN | STORE_ADMIN | MERCHANDISER |
|---|---|---|---|
| GET/PUT `/api/system/utility/lambda-policy` | yes | no | no |
| GET `/api/utility/config` | no | yes | yes |
| PUT `/api/utility/config` | no | no | yes |
| PUT `/api/utility/config/expiry-window` (**deprecated, ignored by scoring**) | no | yes | no |
| GET `/api/utility/products/{productId}/scores` | no | yes | yes |
| PATCH `/api/utility/products/{productId}/scores` | no | no | yes |
| POST `/api/utility/preview`, POST `/api/utility/snapshots`, GET `/api/utility/snapshots/{id}`, GET `/api/utility/snapshots/{id}/matrix` | no | no | yes |
| GET `/api/utility/expiry-push-batches` | no | yes | yes |
| GET `/api/mining/snapshots`, `/requests`, `/requests/{id}`, `/requests/{id}/runs`, `/runs/{id}`, `/runs/{id}/patterns` | no | yes | yes |
| GET/PUT `/api/system/mining/config` | yes | no | no |
| GET `/api/system/mining/stores/{storeId}/snapshots`, `/requests`, `/requests/{requestId}`, `/requests/{requestId}/runs`, `/runs/{runId}/patterns` (**PENDING_BUSINESS_APPROVAL — disabled, 403 for every role by default**) | yes | no | no |
| POST `/api/system/mining/requests` | yes | no | no |
| GET `/api/system/mining/stores/{storeId}/runs/{runId}/benchmark` | yes | no | no |
| `/api/system/mining/schedules` (POST, GET, GET/PUT `{id}`, GET `{id}/occurrences`, GET `{id}/occurrences/{occurrenceId}`) | yes | no | no |
| `/api/recommendations/*` (F06) | see F06 handoff | config PUT | generate/approve/reject |

**Never call (always 403 for every role; shown as `deprecated` in OpenAPI):** `POST /api/mining/requests` and `GET /api/mining/runs/{id}/benchmark`.

## 4. F03 Utility and Snapshot workflow (Merchandiser)
1. `GET /api/utility/config` → `{config:{enableStock,enableExpiry,enableSeason,alpha,beta,gamma,lambda,version,scoringVersion,...},lambdaPolicy:{lambdaMin,lambdaMax,version},lambdaValid}`.
2. `PUT /api/utility/config` body `{enableStock,enableExpiry,enableSeason,alpha,beta,gamma,lambda}` (weights 0..1, enabled weights must sum to 1; lambda must lie inside the System Admin policy, default policy 0..0.5).
3. `PATCH /api/utility/products/{productId}/scores` body only `{stockPriorityScore?,seasonalPriorityScore?}` in [0,1]; explicit `null` or any other field is 400. Response `Scores` also carries read-only `expiryScore`, `weightValue`, `expiryStatus`, `ready`, warnings. Expiry is calculated by the Backend from `Product.expiryPushDays`; the Frontend never submits it.
4. `POST /api/utility/preview` body `{rangeStart,rangeEnd,asOfDate,page?,pageSize?(1..100)}` (ISO dates) → `MatrixPage` (not saved).
5. `POST /api/utility/snapshots` body `{rangeStart,rangeEnd,asOfDate}` → **201** `SnapshotView` (`snapshotId`, `status`, `totalWeightedUtility`, hashes, frozen `configuration`). Snapshots are immutable; change settings and create a new one. Only `status=READY` snapshots can be mined.
6. `GET /api/utility/snapshots/{id}` and `/matrix?page&pageSize` read the frozen data.

## 5. F05 System Admin workflow (Configuration → Store → Snapshot → Request → Status → Runs → Patterns → Benchmark)
All System Admin routes carry the Store explicitly in the path or body. An unknown Store, or a request/run that does not belong to the Store in the path, returns **404**; a non-positive id returns 400. Every list takes `?page=0&pageSize=20` (`page>=0`, `pageSize` 1..100, otherwise 400) and returns `{items,page,pageSize,totalElements}`. Response DTOs are identical to the Store-scoped routes (`SnapshotView`, `RequestView`, `RunView`, `PatternView`).

1. **Configuration.** `GET /api/system/mining/config` → `{version,algorithm,minUtilMode,minUtilValue,changedBy,changedAt}`; at `version=0` every other field is `null`. `PUT` body `{algorithm:"HUI_MINER"|"EFIM",minUtilMode:"PERCENTAGE"|"ABSOLUTE",minUtilValue,expectedVersion}`; `minUtilValue>0`, at most 6 decimals, PERCENTAGE ≤ 100; stale `expectedVersion` → 409. Existing requests keep their own frozen inputs. Submitting before any configuration → 409.
2. **Store selection.** The Frontend supplies `storeId` (the Backend has no Store-picker endpoint for System Admin in this scope; use the Store source your admin UI already has).
3. **READY Snapshot selection.** `GET /api/system/mining/stores/{storeId}/snapshots` → `Page<SnapshotView>` (READY only, newest first). Snapshots are created by that Store's Merchandiser through F03 (section 4).
4. **Manual request.** `POST /api/system/mining/requests` body `{storeId,snapshotId,requestKey(UUID),compareAlgorithms?}` → **202** `RequestView`. Uses the current global config; `compareAlgorithms=true` runs both algorithms and compares. 503 with a diagnostic prefix (`SPMF_JAR_*`, `SPMF_JAVA_*`, `SPMF_RUNTIME_UNAVAILABLE`) when the SPMF runtime is not configured. Same key + payload returns the existing request; same key + different payload → 409.
5. **Status polling.** `GET /api/system/mining/stores/{storeId}/requests/{requestId}` until `status` is `COMPLETED` or `FAILED`; `comparisonStatus` ∈ NOT_REQUESTED, PENDING, MATCH, FAILED, BLOCKED. `GET /api/system/mining/stores/{storeId}/requests` lists the Store's history, newest first.
6. **Runs.** `GET /api/system/mining/stores/{storeId}/requests/{requestId}/runs` → `RunView[]` (`status` PENDING, RUNNING, COMPLETED, FAILED, TIMED_OUT; `failureCode`/`failureMessage` sanitized).
7. **Patterns.** `GET /api/system/mining/stores/{storeId}/runs/{runId}/patterns?page&pageSize` → `Page<PatternView>`.
8. **Benchmark.** See section 6.

Store users (Merchandiser, Store Admin) keep their unchanged Store-scoped read routes: `GET /api/mining/{snapshots,requests,requests/{id},requests/{id}/runs,runs/{id},runs/{id}/patterns}`. System Admin receives 403 on those. The two legacy routes in section 3 stay closed.

**Release status: PENDING_BUSINESS_APPROVAL — gated.** A System Admin token is **not enough**: unless the backend instance is started with `paspm.mining.system-store-read-enabled=true` (env `PASPM_MINING_SYSTEM_STORE_READ_ENABLED=true`, default `false`), every call to the five routes answers `403 {"error":"System Admin reads of Store mining results are pending Product Owner approval (decision D1)"}`, also for unknown Store ids. Enable the flag only in a development/integration backend. The Frontend must treat this 403 as "feature not released" (hide or disable the System Admin results screen), not as a role error. Do not ship the screen until the Product Owner approves decision D1 in `docs/decisions/PO_DECISION_REGISTER_F05_2026-11-01.md`.

**Consequence the Frontend and the Product Owner must know:** with the gate closed, a System Admin can submit a manual request (step 4, `202` with the `requestId`) and read the benchmark of a run that is already known (section 6), but cannot poll the request status (step 5), list its runs or read patterns (steps 6–7) — and without the run list there is no `runId` to ask the benchmark route for. The Benchmark Hub screen therefore cannot be completed from System Admin routes alone until D1 (at least option "b": status and runs) is approved or the flag is enabled in a non-released environment. Store users (Merchandiser, Store Admin) are unaffected: they keep their own Store-scoped reads.

*Authorization note:* the approved requirements name System Admin as the mining operator (configuration, manual execution, benchmark) but contain no explicit sentence authorizing System Admin to read Store mining results. The five read routes are therefore treated as a not-yet-granted permission: they exist and are tested (`MiningSystemStoreReadGateTest` for the default refusal, `MiningIntegrationTest` with the flag on for the behaviour), but the code does not release them.

## 6. F05 Benchmark (System Admin)
Step 8 of the workflow above.
`GET /api/system/mining/stores/{storeId}/runs/{runId}/benchmark` → `BenchmarkView{run,snapshotHash,transactionCount,distinctItems,inputBytes,divisor,utilityScale,effectiveMinUtility,comparisonStatus,repetitionPolicy}`. Runtime and memory are measurements from the recorded run, not guarantees.

## 7. Consuming results (Store users, and F06)
- `GET /api/mining/runs/{id}/patterns?page&pageSize` → `PatternView{patternId,runId,productIds[],utility,utilitySpmf,patternSize,supportCount}`. Sorted by utility descending then Product IDs ascending. Empty while PENDING/FAILED; check the run status first. `utility` is frozen weighted utility, not revenue; `supportCount` is always `null`; `pageSize` only controls display.
- F06: `POST /api/recommendations/generate` (Merchandiser) body `{miningRunId, types:[BUNDLE|CROSS_SELL|FBT|BANNER] (1..4)}`, using a COMPLETED run id; unknown fields are rejected. Review with `GET /api/recommendations/{id}`, `POST .../approve`, `POST .../reject`. See the F06 handoff for DTOs.

## 8. Scheduler restrictions (System Admin only)
- `POST /api/system/mining/schedules` body `{storeId,frequency:"WEEKLY"|"MONTHLY",dayOfWeek(1..7) | dayOfMonth(1..31),localTime,timeZone(IANA),lookbackDays(1..3660),compareAlgorithms?}` → **201** `View{scheduleId,...,enabled,nextFireAt,version}`. New schedules are always **disabled**.
- `PUT /api/system/mining/schedules/{id}` body `{schedule:{...same as above},enabled,expectedVersion(>=1)}`. Stale version → 409. **`enabled=true` returns 503** ("Scheduled mining dispatch is not available") until the Product Owner approves; the UI must hide or disable the enable toggle and show a "not yet available" message.
- `GET .../schedules`, `GET .../{id}`, `GET .../{id}/occurrences` and `.../occurrences/{occurrenceId}` are read-only history (`OccurrenceView` includes status, failureCode, attemptCount, nextRetryAt).
- The server-side dispatcher is off unless `paspm.mining.schedule-dispatch-enabled=true` on that instance (default false). Do not rely on it. Lookback, time zone, monthly overflow, catch-up, retry/backoff and config-change policies are **pending Product Owner approval**; the UI must not promise any of them.

## 9. Known gaps for Frontend planning
1. The five System Admin read routes are implemented and tested but **gated off by default** (see section 5); D1 decides whether and for whom they are released. This also blocks a complete Benchmark Hub flow (section 5, consequence paragraph).
2. `docs/api/openapi.json` is the runtime export of the merged backend; regenerate it with `scripts/verify-option-b-macos.sh --run` after any controller change.
3. **No Store picker endpoint exists.** The Backend has no Store-listing route. `GET /api/users` (System Admin) returns each user's `storeId` but no Store name, so it can at most supply raw ids. Until decision D2 is made, the Store field is a typed id.
4. Snapshot listing loads each READY snapshot individually (pre-existing behaviour, at most 100 per page).

## 10. End-to-end HTTP example
```
POST /api/auth/login                      (merchandiser)           -> token M
PUT  /api/utility/config                  M {"enableStock":true,"enableExpiry":true,"enableSeason":true,"alpha":0.5,"beta":0.2,"gamma":0.3,"lambda":0.5}
PATCH /api/utility/products/12/scores     M {"stockPriorityScore":0.8,"seasonalPriorityScore":0.9}
POST /api/utility/snapshots               M {"rangeStart":"2026-01-01","rangeEnd":"2026-03-31","asOfDate":"2026-03-31"}  -> 201 {snapshotId:7,status:"READY"}
POST /api/auth/login                      (system admin)           -> token S
PUT  /api/system/mining/config            S {"algorithm":"EFIM","minUtilMode":"PERCENTAGE","minUtilValue":0.5,"expectedVersion":0}
POST /api/system/mining/requests          S {"storeId":3,"snapshotId":7,"requestKey":"<uuid>","compareAlgorithms":true} -> 202 {requestId:41,status:"PENDING"}
# The next four reads (snapshots, requests/{id}, requests/{id}/runs, runs/{runId}/patterns) are GATED: 403 unless the backend runs with paspm.mining.system-store-read-enabled=true (decision D1).
GET  /api/system/mining/stores/3/snapshots                  S  -> READY snapshots (pick 7)
GET  /api/system/mining/stores/3/requests/41                S  (poll) -> COMPLETED, comparisonStatus MATCH
GET  /api/system/mining/stores/3/requests/41/runs           S  -> RunView[] (2)
GET  /api/system/mining/stores/3/runs/{runId}/patterns?page=0&pageSize=20  S -> Page<PatternView>
GET  /api/system/mining/stores/3/runs/{runId}/benchmark    S       -> BenchmarkView
POST /api/recommendations/generate        M {"miningRunId":{runId},"types":["BUNDLE"]}
```
Values are illustrative; IDs differ per environment.

## 11. Verification evidence (merged tree, 2026-11-01)
Run: `scripts/verify-option-b-macos.sh --run` against merge commit `caa54b4` plus the uncommitted gate for the five read routes and the script fix; JDK 17.0.21 Temurin for the backend, Java 25 + SPMF 2.67a for the child, disposable MySQL 8.4.13. Evidence directory: `outputs/verification-evidence/final/option-b-20261101T160402Z` (outside the repository). A first run on `caa54b4` alone (before the gate) also passed 23/23: `…/merged/option-b-20261101T155351Z`. Results of the bare-branch runs before the merge are historical and are not evidence for this tree.

| Stage | Result | Counts |
|---|---|---|
| `mvn.verify` (H2) | PASS | 443 run, 0 failed, 0 errors, **155 SKIPPED** (MySQL-only suites) |
| `test.flag` | PASS | 6 run |
| `test.mysql_group` | PASS | 51 run, 0 skipped |
| `test.full_optin` (MySQL + SPMF) | PASS | 453 run, 0 failed, **10 SKIPPED** = 5 `MiningMultiJvmMySqlTest` + 5 `PilotCloneApiIntegrationTest`; not executed on this tree |
| `flyway.fresh`, `flyway.v11`, `flyway.rerun`, `flyway.upgrade` | PASS | V1–V17 once each; second start identical; upgrade from the `origin/dev` schema keeps seeded data and checksums |
| `jpa.validate`, `openapi.export` | PASS | 88 operations in the runtime OpenAPI |

Not verified: Windows and clean-machine setup, Linux/Docker multi-JVM and SIGKILL cleanup on the merged tree (earlier Linux evidence predates the merge), a real scheduler activation (503 by design), any Frontend end-to-end flow, and Product Owner decisions D1–D10.

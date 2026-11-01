# PASPM Backend API Handoff for Frontend — F03 Utility, F05 Mining (Option B baseline)

Status (re-verified 2026-11-02 on exact HEAD `45dbd80`, branch `Khoi/feat/f03-f05-admin-mining`, merge of `origin/dev` `6d91215`; sections 11.3 and 11.4): **CORE_API_VERIFIED · FRONTEND_INTEGRATION_READY for the verified surface only · OPTION_B_NOT_READY.** The verified surface is everything in sections 1–4, 6, 7 and the System Admin configuration/manual-request/benchmark/schedule-administration routes; it was exercised by `scripts/verify-option-b-macos.sh --run` (evidence in section 11). Two things are deliberately **not** open to the Frontend: (1) the five System Admin read routes of section 5 are **disabled (HTTP 403 for every role)** until Product Owner decision D1; (2) schedule activation returns **503**. `docs/api/openapi.json` is the runtime export of this backend. No endpoint or field below is invented; every one was read from the controllers, records and `SecurityConfig` and cross-checked against the runtime OpenAPI.

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

**Release status: PENDING_BUSINESS_APPROVAL — gated per route group.** A System Admin token is **not enough**. Each group is refused with `403 {"error":"System Admin reads of Store mining results (<GROUP>) are pending Product Owner approval (decision D1)"}` — also for unknown Store ids — unless the backend instance lists the group in `paspm.mining.system-store-read.routes` (env `PASPM_MINING_SYSTEM_STORE_READ_ROUTES`; default empty = nothing open; unknown names stop the backend at startup). Groups: `SNAPSHOTS` (`/snapshots`), `REQUESTS` (`/requests`, `/requests/{requestId}`), `RUNS` (`/requests/{requestId}/runs`), `PATTERNS` (`/runs/{runId}/patterns`). Opening one group never opens another. Decision D1 maps to: (a) all four groups; (b) `REQUESTS,RUNS`; (c) none. The Frontend must treat this 403 as "feature not released" (hide or disable that part of the System Admin results screen), not as a role error. Do not ship the screen until the Product Owner approves decision D1 (see `docs/decisions/PO_DECISION_PACKAGE_D1_D10_2026-11-01.md`); for development, run a non-released backend with the groups you are building against.

**Consequence the Frontend and the Product Owner must know:** with the gate closed, a System Admin can submit a manual request (step 4, `202` with the `requestId`) and read the benchmark of a run that is already known (section 6), but cannot poll the request status (step 5), list its runs or read patterns (steps 6–7) — and without the run list there is no `runId` to ask the benchmark route for. The Benchmark Hub screen therefore cannot be completed from System Admin routes alone until D1 (at least option "b": `REQUESTS,RUNS`) is approved or those groups are opened in a non-released environment. Store users (Merchandiser, Store Admin) are unaffected: they keep their own Store-scoped reads.

*Authorization note:* the approved requirements name System Admin as the mining operator (configuration, manual execution, benchmark) but contain no explicit sentence authorizing System Admin to read Store mining results. The five read routes are therefore treated as a not-yet-granted permission: they exist and are tested (`SystemStoreReadPolicyTest` for parsing, `MiningSystemStoreReadGateTest` for the default refusal, `MiningSystemStoreReadGranularGateTest` for option b, `MiningIntegrationTest` with all groups open for the behaviour), but the code does not release them.

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

## 10. End-to-end Core flow (F03 → F05 → F06) that works without any PO decision
The same sequence is executed automatically by `RecommendationIntegrationTest.completedRealMiningGeneratesFourTypesAndDirectionalFrozenEvidence` (H2) and `RecommendationMySqlIntegrationTest` (MySQL) with the real SPMF child: configuration → snapshot → manual System Admin request → worker → COMPLETED/MATCH → patterns → F06 generation. Status and results are read through the **Store user's own routes**, which are open; the System Admin read routes are not needed.
```
POST /api/auth/login                      (merchandiser, own Store)                -> token M
PUT  /api/utility/config                  M {"enableStock":true,"enableExpiry":true,"enableSeason":true,"alpha":0.5,"beta":0.2,"gamma":0.3,"lambda":0.5}
PATCH /api/utility/products/12/scores     M {"stockPriorityScore":0.8,"seasonalPriorityScore":0.9}
POST /api/utility/preview                 M {"rangeStart":"2026-01-01","rangeEnd":"2026-03-31","asOfDate":"2026-03-31"}   -> MatrixPage (not saved)
POST /api/utility/snapshots               M {"rangeStart":"2026-01-01","rangeEnd":"2026-03-31","asOfDate":"2026-03-31"}   -> 201 {snapshotId:7,status:"READY"}
POST /api/auth/login                      (system admin)                           -> token S
GET  /api/system/mining/config            S -> {version:0,...}                      (first use)
PUT  /api/system/mining/config            S {"algorithm":"EFIM","minUtilMode":"ABSOLUTE","minUtilValue":30,"expectedVersion":0}
POST /api/system/mining/requests          S {"storeId":3,"snapshotId":7,"requestKey":"<uuid>","compareAlgorithms":true} -> 202 {requestId:41,status:"PENDING"}
GET  /api/mining/requests/41              M (poll every 1-2 s)                      -> COMPLETED, comparisonStatus MATCH
GET  /api/mining/requests/41/runs         M -> RunView[2] (HUI_MINER, EFIM)         -> note the runIds
GET  /api/mining/runs/{runId}/patterns?page=0&pageSize=20   M -> Page<PatternView>
GET  /api/system/mining/stores/3/runs/{runId}/benchmark     S -> BenchmarkView      (the runId comes from the Merchandiser; see D1)
POST /api/recommendations/generate        M {"miningRunId":{runId},"types":["BUNDLE","CROSS_SELL","FBT","BANNER"]}
GET  /api/recommendations/{id}            M -> PENDING_REVIEW candidate with frozen evidence
```
Gated alternative (only on an instance that lists the groups in `paspm.mining.system-store-read.routes`, i.e. after decision D1): the System Admin may replace the Merchandiser reads with `GET /api/system/mining/stores/3/{snapshots,requests,requests/41,requests/41/runs,runs/{runId}/patterns}`; each group otherwise answers 403. Values are illustrative; IDs differ per environment.

## 11. Verification evidence (2026-11-01)
Evidence directories are outside the repository (`outputs/verification-evidence/…`). Results of earlier or bare-branch runs are historical and are not evidence for the current code.

**11.1 Full script on merge commit `caa54b4` + the code described in this handoff** — `scripts/verify-option-b-macos.sh --run`, JDK 17.0.21 Temurin for the backend, Java 25 + SPMF 2.67a for the child, disposable MySQL 8.4.13; evidence `final2/option-b-20261101T163334Z`:

| Stage | Result | Counts |
|---|---|---|
| `mvn.verify` (H2) | PASS | 447 run, 0 failed, 0 errors, **155 SKIPPED** (MySQL-only suites) |
| `test.flag` | PASS | 6 run |
| `test.mysql_group` | PASS | 51 run, 0 skipped |
| `test.full_optin` (MySQL + SPMF) | PASS | 457 run, 0 failed, **10 SKIPPED** (the two suites of 11.2, run separately) |
| `flyway.fresh`, `flyway.v11`, `flyway.rerun`, `flyway.upgrade` | PASS | V1–V17 once each; second start identical; upgrade from the `origin/dev` schema keeps seeded data and checksums |
| `jpa.validate`, `openapi.export` | PASS | 88 operations in the runtime OpenAPI |

**11.2 The 10 opt-in tests, run separately on the exported source of commit `d0522f1`** (`git archive`, so no working-tree content; the later per-group gate does not touch these suites). Evidence `optin-d0522f1/`:

| Suite | Environment | Result |
|---|---|---|
| `MiningMultiJvmMySqlTest` (5 methods; the real-SPMF SIGKILL case repeats twice = 6 executions) | macOS, JDK 17, Java 25 child, MySQL 8.4.13 | **PASS 6/6**, 245 s. macOS has no kernel parent-death guarantee, so this proves lease, fencing, recovery and durable dispatch only |
| same | Linux amd64 container (Docker Desktop on Apple Silicon, emulated), `--init`, `PASPM_TEST_REQUIRE_REAP=true`, JDK 17.0.20.1, JRE 25.0.1, MySQL 8.4.13 | **PASS 6/6**, 402 s. After SIGKILL of Backend A: `orphan_alive_after_backend_sigkill=false`, `reaped_by_container_init=true`, the other instance's SPMF `survived=true`, fence 1→2, 8 patterns |
| `PilotCloneApiIntegrationTest` (5) | macOS, MySQL 8.4.13 with `log_bin_trust_function_creators=1`, restricted user confined to one disposable schema | **PASS 5/5** |

A first Linux attempt failed in 2 s with `MySQL verification requires an empty local server URL`: my runner used a container DNS name instead of loopback. That was a test-environment fault, not a product defect; the corrected run shares the MySQL container's network namespace (`127.0.0.1`). Evidence of the failed attempt is kept.

**11.3 Re-verification on exact HEAD `0a2d27e` (2026-11-02)** — `scripts/verify-option-b-macos.sh --run`, same toolchain as 11.1, evidence `final-0a2d27e/option-b-20261101T170051Z`: **23/23 stages PASS**, exit 0 (`mvn.verify` 447 run / 0 failed / 155 skipped; `test.flag` 6; `test.mysql_group` 51 / 0 skipped; `test.full_optin` 457 / 0 failed / 10 skipped; `jpa.validate`; `openapi.export`; Flyway fresh V1–V17, V11 Java migration, restart-identical history, upgrade from the dev schema). The fresh runtime OpenAPI has the same 88 operations and the same components as `docs/api/openapi.json`. The 10 skipped opt-in tests were **not** re-run in this pass (results of 11.2 stand for `d0522f1`). Added afterwards, outside that run: `F03F05RbacMatrixTest` (26 routes × anonymous, System Admin, Store Admin, Merchandiser, End Customer; 3 tests passing, red/green checked) — see the [audit matrix](../reports/F03_F05_API_AUDIT_MATRIX_2026-11-02.md).

**11.4 Core release candidate, HEAD `45dbd80` (2026-11-02)** — the script passed 23/23 again (451 H2 tests, 461 full opt-in, 0 failures; 88 OpenAPI operations identical to the committed file); the 10 opt-in tests ran on the exported source: macOS multi-JVM 6/6 and clone-API 5/5, Linux Docker multi-JVM 6/6 with `orphan_alive_after_backend_sigkill=false`. The only Linux failure (`SpmfRuntimeTest`, container user is root) passes as a non-root user. Full table, safe defaults, CI facts and rollback: [Core release readiness](../reports/F03_F05_CORE_RELEASE_READINESS_2026-11-02.md).

**Not verified:** Linux on native hardware or the final production image (the Linux run is amd64 under emulation), Windows and clean-machine setup, a remote GitHub CI run (the branch is not pushed), a real scheduler activation (503 by design), any Frontend end-to-end flow, and Product Owner decisions D1–D10.

## 12. Integration readiness by flow (F03 / F05)

Legend — **NOW**: verified and open, integrate. **LIMITED**: works, with the stated limit. **LOCKED**: answers 403 by design until Product Owner decision D1 (gate in section 5). **503**: refused by design until scheduler activation. **FORBIDDEN**: always 403 for every role, never call. **DEPRECATED**: still answers but has no effect, do not build. **MISSING**: no Backend endpoint exists. Evidence per row is the named automated test class on the merged tree (section 11); no row has been exercised through a real Frontend yet.

| # | Flow | Role | Endpoints | State | Notes / evidence |
|---|---|---|---|---|---|
| 1 | Sign in, current account | all | `POST /api/auth/login`, `GET /api/auth/me` | NOW | `AuthIntegrationTest` |
| 2 | Read Store utility settings and lambda range | Merchandiser, Store Admin | `GET /api/utility/config` | NOW | `UtilityIntegrationTest` |
| 3 | Change Store weights and lambda | Merchandiser | `PUT /api/utility/config` | NOW | weights 0..1, enabled weights sum to 1, lambda inside policy |
| 4 | Manual S/M product scores, read E/W | Merchandiser (write), Store Admin (read) | `GET`/`PATCH /api/utility/products/{productId}/scores` | NOW | only `stockPriorityScore`/`seasonalPriorityScore` accepted |
| 5 | Utility preview (not saved) | Merchandiser | `POST /api/utility/preview` | NOW | |
| 6 | Create / read immutable snapshot and matrix | Merchandiser | `POST /api/utility/snapshots`, `GET …/{id}`, `GET …/{id}/matrix` | NOW | snapshot must be `READY` to be mined; a new setting needs a new snapshot |
| 7 | Expiry-push batches | Merchandiser, Store Admin | `GET /api/utility/expiry-push-batches` | NOW | independent of mining |
| 8 | Global lambda policy | System Admin | `GET`/`PUT /api/system/utility/lambda-policy` | NOW | |
| 9 | Deprecated expiry window | Store Admin | `PUT /api/utility/config/expiry-window` | DEPRECATED | answers normally but is ignored by scoring; do not build UI |
| 10 | Mining configuration (algorithm, minUtil) | System Admin | `GET`/`PUT /api/system/mining/config` | NOW | send `expectedVersion`; stale → 409; first use is version 0 |
| 11 | Submit a manual mining request | System Admin | `POST /api/system/mining/requests` | LIMITED | 202, idempotent by `requestKey`; `storeId` must be typed (MISSING Store picker, D2); the `snapshotId` must come from the Merchandiser's snapshot response because the System Admin snapshot list is LOCKED; `MiningIntegrationTest` |
| 12 | Follow a request, its runs and results as a Store user | Merchandiser, Store Admin | `GET /api/mining/snapshots`, `/requests`, `/requests/{id}`, `/requests/{id}/runs`, `/runs/{id}`, `/runs/{id}/patterns` | NOW | own Store only; this is how status and patterns of a System Admin request are visible today |
| 13 | Benchmark of a run | System Admin | `GET /api/system/mining/stores/{storeId}/runs/{runId}/benchmark` | LIMITED | route is open, but the `runId` has to be known: the run list is LOCKED (row 14) |
| 14 | Status polling, run list, snapshot list, patterns for System Admin | System Admin | `GET /api/system/mining/stores/{storeId}/requests`, `…/requests/{requestId}`, `…/requests/{requestId}/runs`, `…/snapshots`, `…/runs/{runId}/patterns` | LOCKED | groups `REQUESTS`, `RUNS`, `SNAPSHOTS`, `PATTERNS`; tested with the groups open and closed (`MiningSystemStoreReadGateTest`, `MiningSystemStoreReadGranularGateTest`, `MiningIntegrationTest`) |
| 15 | Create, list, view, edit, disable a schedule | System Admin | `POST`/`GET /api/system/mining/schedules`, `GET`/`PUT …/{id}` | LIMITED | schedules are created disabled; `PUT` with `enabled=true` → 503; `MiningAdminConfigurationTest` |
| 16 | Enable a schedule | System Admin | `PUT /api/system/mining/schedules/{id}` with `enabled=true` | 503 | show "not yet available"; the dispatcher is off by default |
| 17 | Schedule run history | System Admin | `GET …/{id}/occurrences`, `GET …/{id}/occurrences/{occurrenceId}` | NOW | read-only; includes status, attempt count, retry time, linked snapshot/request ids |
| 18 | Legacy merchant submit and Store benchmark | any | `POST /api/mining/requests`, `GET /api/mining/runs/{id}/benchmark` | FORBIDDEN | `deprecated` in OpenAPI; 403 for every role |
| 19 | Recommendations from a COMPLETED run | Merchandiser | `POST /api/recommendations/generate` and the F06 review routes | NOW | `RecommendationIntegrationTest`, `RecommendationHardeningIntegrationTest`; see the F06 handoff |

**What the Frontend can build now:** rows 1–8, 10, 12, 15 (disabled schedules only), 17, 19, plus the submit button of row 11. **What it must hide or disable:** rows 14 (until D1) and 16 (until activation); never build rows 9 and 18. **What is missing in the Backend:** a Store list for System Admin (row 11, decision D2).

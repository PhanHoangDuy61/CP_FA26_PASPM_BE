> **Status 2026-11-02 — F03/F05 Core is merged into `dev` (PR #11, `11973a0`).** Verified surface: see [OPTION_B_BACKEND_API_HANDOFF.md](OPTION_B_BACKEND_API_HANDOFF.md) (status table and the per-flow readiness table in its section 12). Closed by design: the five System Admin Store-read routes (403 until decision D1, per group) and schedule activation (503). This file explains how to build the F05 screens; the route/role truth is [F05_MINING_API.md](../api/F05_MINING_API.md).

# F05 Mining — Frontend Integration Handoff

## Who does what

| Actor | Does | Cannot |
|---|---|---|
| SYSTEM_ADMIN | Sets the global mining policy (algorithm, minUtil), starts a mining request for a chosen Store, reads benchmarks, reads Store snapshots / requests / runs (not patterns), picks a Store, manages schedules and their history | Use the Store-scoped `/api/mining/**` routes (403); read raw patterns (403 by decision D1) |
| MERCHANDISER | Creates F03 snapshots; reads own-Store snapshots, requests, runs and patterns | Submit mining (403), read benchmarks (403) |
| STORE_ADMIN | Reads own-Store snapshots, requests, runs and patterns | Submit mining, read benchmarks |
| END_CUSTOMER | – | Everything (403) |

Send the F01 JWT in `Authorization: Bearer <token>`. Store users never send `storeId`; the System Admin routes carry `storeId` explicitly. Missing or invalid JWT: 401. A foreign or unknown snapshot/request/run: 404. Do not infer mining access from a role's F01 permissions.

## Readiness and environment

Mining runs asynchronously on a Java 17 backend with an external Java 25 SPMF child ([SPMF setup](../setup/F05_SPMF_RUNTIME_SETUP.md)). When the runtime is not configured, submitting returns HTTP 503 with a diagnostic prefix and history stays readable. Default base URL `http://localhost:8080`; OpenAPI `GET /v3/api-docs`, Swagger `/swagger-ui/index.html`; the committed [OpenAPI export](../api/openapi.json) is a runtime export of this backend.

## Integration sequence and screen mapping

| Screen / action | API | Role | State |
|---|---|---|---|
| Global policy form | `GET`/`PUT /api/system/mining/config` (send `expectedVersion`) | System Admin | Ready |
| Store selection | `GET /api/system/stores?page=0&pageSize=100` → `{items:[{storeId,name}],page,pageSize,totalElements}` ordered by name | System Admin | Ready (D2) |
| Snapshot choice | System Admin: `GET /api/system/mining/stores/{storeId}/snapshots` (D1); or take the `snapshotId` from the Merchandiser's F03 snapshot response | System Admin | Ready |
| Run button | `POST /api/system/mining/requests` body `{storeId,snapshotId,requestKey,compareAlgorithms?}` | System Admin | Ready |
| Status polling | System Admin: `GET /api/system/mining/stores/{storeId}/requests/{requestId}` (D1); Store users: `GET /api/mining/requests/{id}` | both | Ready |
| Algorithm runs | System Admin `…/requests/{requestId}/runs` (D1); Store users `GET /api/mining/runs…` as `GET /api/mining/requests/{id}/runs` | both | Ready |
| Patterns | **Store users only**: `GET /api/mining/runs/{id}/patterns`. System Admin `…/runs/{runId}/patterns` always answers 403 (decision D1) — do not render a pattern table for System Admin | Store Admin, Merchandiser | Ready |
| Benchmark | `GET /api/system/mining/stores/{storeId}/runs/{runId}/benchmark` (get `runId` from the runs route above) | System Admin | Ready |
| Schedules | `POST`/`GET /api/system/mining/schedules`, `GET`/`PUT …/{id}`, `GET …/{id}/occurrences` | System Admin | Ready. Activation (`enabled:true`) works only where the Backend enables scheduling (default off → 503): show "scheduling is not enabled in this environment" on 503 |

A 403 on a System Admin results route means an operator removed that group from the Backend configuration; the body is `{"error":"System Admin reads of Store mining results (<GROUP>) are disabled by configuration (decision D1)"}`. Do not call `POST /api/mining/requests` or `GET /api/mining/runs/{id}/benchmark` (403 for every role).

**Schedule form (D3–D5):** `timeZone` is fixed to `Asia/Ho_Chi_Minh` (send it; any other value is a 400). `lookbackDays` is optional, 7–365; offer presets 30, 60, 90, 180 and a free input; leave it out for the default (30 weekly, 90 monthly). Monthly day 29/30/31 runs on the month's last day when the day does not exist — say so in the UI.

**Occurrence history (D6–D9):** statuses `PENDING`, `PROCESSING`, `DISPATCHED`, `COMPLETED`, `FAILED`, `SKIPPED`. Show `failureMessage` for `FAILED` (final cause) and `skipReason` for `SKIPPED` ("missed while the scheduler was unavailable; a later run replaced it"). `PENDING` with `nextRetryAt` is a retry (3 attempts: 15 s, 30 s). `actorId` is the system scheduler, not a person — label it "Scheduler". See [F05_MINING_API.md](../api/F05_MINING_API.md#scheduler-administration-decisions-d1d10-confirmed-2026-11-02-activation-gated) for the full field list.

F03 snapshot creation remains `POST /api/utility/snapshots` for the Merchandiser. F05 never creates a snapshot by itself (the disabled scheduler would). A READY snapshot can still fail F05 numerical or size validation; display the Backend error.

All list endpoints accept `page >= 0`, `pageSize = 1..100`, defaults 0/20. Patterns: utility descending, then canonical Product key ascending. History: request ID descending. There are no server filters or custom sort. "Top N" is page 0 with `pageSize` N and only limits display, never minUtil.

## Form and response examples

```json
{"algorithm":"EFIM","minUtilMode":"PERCENTAGE","minUtilValue":0.5,"expectedVersion":0}
```

```json
{"storeId":3,"snapshotId":7,"requestKey":"009f3ef1-d4ba-4291-9854-b74c6878d26b","compareAlgorithms":true}
```

Use `crypto.randomUUID()` once per deliberate submission and keep it for transport retries: the same key with the same `snapshotId`/`compareAlgorithms` returns the existing request (202) without enqueueing duplicate work; the same key with different values is 409. A deliberate rerun needs a new UUID. There is no cancel, retry or delete route. A missing configuration answers 409 `Configure mining policy before execution`.

Percentage is in percent units (`0.5` = 0.5%, allowed `(0,100]`, at most 6 decimals); absolute is frozen weighted utility units. The response shows `minUtilSpmf`, `effectiveMinUtility`, `divisor`, `utilityScale`, `totalScaledUtility` so the UI can display the exact effective threshold, including upward rounding. Do not recompute conversions, Product weights or expiry scores in React.

Sample responses (captured against synthetic data): [create_response.json](F05_examples/create_response.json), [request_response.json](F05_examples/request_response.json), [runs_response.json](F05_examples/runs_response.json), [patterns_response.json](F05_examples/patterns_response.json), [benchmark_response.json](F05_examples/benchmark_response.json), [snapshot_list_response.json](F05_examples/snapshot_list_response.json). The older `create_request.json` and `percentage_request.json` show the retired Merchandiser body and must not be used. Full field tables: [F05_MINING_API.md](../api/F05_MINING_API.md).

## State and error handling

- Request `PENDING`/`RUNNING`: execution has not finished; an empty pattern page is not final evidence of zero patterns.
- Run `COMPLETED`, patternCount 0: valid empty mining result. Request can finish COMPLETED/MATCH with both outputs empty.
- Run `FAILED` or `TIMED_OUT`: show the sanitized failure; do not render partial patterns as successful output.
- Request `FAILED`, comparisonStatus `FAILED`: algorithms completed but exact itemsets/utilities did not match. Do not show a successful comparison.
- Comparison `PENDING`: wait; `NOT_REQUESTED`: only one algorithm selected; `BLOCKED`: at least one algorithm did not complete or capacity/recovery prevented comparison.
- Queue full: a committed PENDING request remains eligible for durable polling; do not interpret 202 alone as a completed mining success. Monitor request and run status and consult the current worker architecture for recovery limitations.
- Restart or instance loss: a request whose worker lease expires is recovered and re-run by another (or the restarted) instance, with a higher fencing token so the stale worker cannot write results; poll the status instead of resubmitting. Legacy RUNNING rows without a lease owner are never assumed abandoned and need operator investigation. A terminal FAILED request is not replayed automatically; a deliberate retry needs a new UUID.
- 400: fix inputs or select a compatible snapshot. 401: use existing login/session recovery. 403: hide/disable forbidden actions. 404: unavailable/foreign record, do not disclose Store details. 409: resolve requestKey conflict. 503: deployment dependency unavailable; keep configuration details out of user forms.

Common application error envelope is `{"error":"message"}`. Authentication middleware may return an empty 401 body; handle both JSON and empty responses. Unknown JSON fields (including storeId) are rejected. No credential is present in handoff examples.

## Utility and benchmark display

`PatternView.utility` is summed frozen weighted utility for the Products when they co-occur in a transaction. It is not revenue, current stock, margin percentage or support. `utilitySpmf` is the normalized integer; display `utility` as the business value. `supportCount` is null because the selected SPMF text output does not measure it; do not display zero or infer support from utility.

`memoryMiB` is SPMF's sampled Java heap metric, not peak RSS. `runtimeMillis` measures child JVM wall time including startup and algorithm IO, excluding common input preparation and Backend parsing/persistence. Runs are sequential, one cold execution per algorithm, no warmup. This is useful for this input/environment; do not present a universal “faster algorithm” claim. Compare only when inputHash, snapshot, minUtilSpmf, conversionVersion and environment match and comparisonStatus is MATCH. The frozen Product mapping is shared; output order may differ.

Java long IDs/divisor and decimal utility values are JSON numbers. Preserve precision using a lossless numeric strategy when values exceed JavaScript's safe integer or decimal precision; do not round a minUtil field through binary floating-point arithmetic. Timestamps are ISO-8601 instants; format for the user's timezone (Store's F03 analysis timezone is stored separately). Configure database JDBC connection/session timezone consistently with the backend, normally UTC.

## Operational limits and known limitations

- Maximum snapshot rows 20,000; distinct Products 256. Conversion must fit signed 64-bit before GCD and signed 32-bit dataset total/threshold after GCD. No silent rounding or filtering.
- Each backend instance has one worker and eight waiting slots; the database is the durable dispatch authority, so a full queue leaves a PENDING request for a later poll. Several instances may run against one database: work is claimed with a time-limited lease and fencing token, and an expired lease is recovered by another instance ([worker architecture](../architecture/F05_WORKER_DISPATCH.md)). Algorithms of one request run sequentially.
- Default timeout 60 s per algorithm, child heap 256 MiB, at most 2,000 patterns; output cap 4 MiB; process log cap 64 KiB.
- Historical snapshots remain usable if valid; later F03 changes affect only new snapshots. F05 keeps its own frozen execution copy.
- SPMF is not bundled. The verified 2.67a JAR needs a Java 25 child executable; the backend is compiled for Java 17.
- Scheduling: implemented on the Scheduler branch (not yet on `dev`), locally verified, **not release accepted** and **off by default**: enabling a schedule returns 503 and the dispatcher does not run unless the Backend instance sets `paspm.mining.schedule-dispatch-enabled=true`. Decisions D1–D10 are confirmed ([register](../decisions/PO_DECISION_REGISTER_F05_2026-11-01.md)). Do not promise scheduled execution in production until release sign-off.
- F06 recommendations are separate ([F06 handoff](F06_HANDOFF.md)); they combine COMPLETED raw patterns with current inventory and Merchandiser review. `E_i=1` does not guarantee appearance in a pattern.
- Not verified: Frontend end-to-end flows, native Linux production image, remote CI.

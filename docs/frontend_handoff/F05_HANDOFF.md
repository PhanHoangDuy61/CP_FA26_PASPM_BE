# F05 Mining — Frontend Integration Handoff

## Readiness and environment

Eight implemented F05 operations are ready for integration against a migrated development backend with the verified SPMF dependency configured. All eight were invoked with Swagger UI; see the [executed verification report](../PASPM_F05_High_Utility_Mining/F05_VERIFICATION.md). The 2026-10-20 P0 patch resolves fresh MySQL V1–V9 startup; existing applied V7 checksums require the [controlled reconciliation procedure](../setup/FLYWAY_MYSQL_V7_RECOVERY.md). Runtime configuration and diagnostic HTTP503 codes are documented in [SPMF setup](../setup/F05_SPMF_RUNTIME_SETUP.md). Backend remains Java17 with an explicitly configured Java25+ child executable.

Default local base URL follows the existing backend configuration, normally `http://localhost:8080`; the actual isolated QA run used `http://127.0.0.1:18085`. Configure the React API base URL for the target environment. OpenAPI: `GET /v3/api-docs`; Swagger: `/swagger-ui/index.html`. The committed [OpenAPI export](../api/openapi.json) contains the actual QA server URL; override that server URL when generating/using the client elsewhere.

Send the existing F01 JWT in `Authorization: Bearer <token>`. Reuse the F01 login/current-user flow and error handling. Store ownership comes from the authenticated user; never send `storeId`. No additional Frontend authentication or upload API is needed. Backend configuration and dependency paths are deployment concerns, not form inputs.

## Role differences

| Role | Select/read Store snapshots | Create mining request | Read requests, runs, patterns, benchmark |
|---|---|---|---|
| MERCHANDISER | Own Store | Own Store | Own Store |
| STORE_ADMIN | Own Store | Forbidden, 403 | Own Store |
| SYSTEM_ADMIN | Forbidden, 403 | Forbidden, 403 | Forbidden, 403 |
| END_CUSTOMER | Forbidden, 403 | Forbidden, 403 | Forbidden, 403 |

Missing/invalid JWT: 401. A permitted Store user requesting another Store's snapshot/request/run/patterns/benchmark gets 404. Cross-Store list results contain only the caller's Store. Do not infer that System Admin has mining access from their F01 account-management permissions.

## Integration sequence and screen mapping

| Screen/action | Actual API | What to show |
|---|---|---|
| Snapshot picker | GET `/api/mining/snapshots?page=0&pageSize=20` | READY snapshots: range, analysis date, frozen scoring version, utility total, warnings |
| Configure algorithms and minUtil | Local form; no separate configuration endpoint | HUI_MINER, EFIM, or both; PERCENTAGE/ABSOLUTE; positive decimal value |
| Run button, Merchandiser only | POST `/api/mining/requests` | Persist UUID requestKey locally before submission; use same key on transport retry |
| History | GET `/api/mining/requests?page=0&pageSize=20` | Newest request first; status and comparisonStatus |
| Poll status | GET `/api/mining/requests/{id}` | Poll every 1–2 seconds while PENDING/RUNNING; stop when COMPLETED/FAILED |
| Algorithm run panel | GET `/api/mining/requests/{id}/runs` | Separate HUI-Miner and EFIM statuses/metrics |
| Run detail | GET `/api/mining/runs/{id}` | FailureCode/message, timestamps, patternCount |
| Raw pattern table | GET `/api/mining/runs/{id}/patterns?page=0&pageSize=20` | Product IDs, utility, size; support shown as “not measured” |
| Comparison panel | GET `/api/mining/runs/{id}/benchmark` for each run | Runtime ms, sampled Java heap MiB, methodology, input/threshold provenance, exact comparison status |

F03 snapshot creation remains `POST /api/utility/snapshots` for Merchandiser, using F03's real date-range DTO. F05 never automatically creates a snapshot. Snapshot selection is available to Store Admin through the F05 picker even though F03 snapshot endpoints remain Merchandiser-only. A READY snapshot can still fail F05 numerical/size validation; display the Backend error.

All list endpoints accept `page >= 0`, `pageSize = 1..100`, defaults 0/20. Pattern order is utility descending then canonical Product-key lexical ascending. History is request ID descending. There are no server status/Product/size filters and no custom sort parameter in this P0. Filter the loaded page locally only if labeled as such; do not imply that filtering one page searches the entire dataset. “Top N” can use page 0/pageSize N up to 100 and only limits display, never minUtil.

## Form and response examples

[create_request.json](F05_examples/create_request.json) and [create_response.json](F05_examples/create_response.json) are actual sanitized Swagger request/response from QA. Replace snapshotId and UUID for the target Store. The captured create response was PENDING; another execution may already be RUNNING/COMPLETED when returned. The API always returns persisted IDs.

```json
{
  "requestKey": "009f3ef1-d4ba-4291-9854-b74c6878d26b",
  "snapshotId": 2,
  "algorithms": ["HUI_MINER", "EFIM"],
  "minUtilMode": "ABSOLUTE",
  "minUtilValue": 30
}
```

Use `crypto.randomUUID()` once per deliberate submission. Retrying the identical payload with that key returns the existing request (202) and does not enqueue duplicate work. The same key with a changed snapshot/algorithm set/minUtil yields 409. To rerun a failed/completed request intentionally, create a new UUID. There is no retry/cancel/delete endpoint. Algorithm order is normalized for idempotency; duplicate algorithms are invalid.

Percentage is in percent units: `0.5` means 0.5%, not 50%. Allowed `(0,100]`, at most 6 decimal places. Absolute is original frozen weighted utility units, positive and at most 6 places. Suggested presets 0.2%, 0.5%, 1% are optional UI choices. The API returns `minUtilSpmf`, `effectiveMinUtility`, `divisor`, `utilityScale`, `totalScaledUtility` so the UI can display the exact effective threshold, including upward rounding. For the executed 96-unit fixture, 31.25% became 30 and matched absolute 30.

Do not recalculate the conversion, Product weights or expiry scores in React. Backend uses exact scale-6 conversion and lossless GCD normalization; if an input is outside the common SPMF int32 range, submission fails with 400. Some legacy READY matrices may not fit. An all-zero dataset has no meaningful positive percentage threshold; positive absolute mode may complete with zero results.

Full response field/type tables, statuses and validations are in [F05_MINING_API.md](../api/F05_MINING_API.md). Examples include [request detail](F05_examples/request_response.json), [runs](F05_examples/runs_response.json), [patterns](F05_examples/patterns_response.json), [benchmark](F05_examples/benchmark_response.json), [snapshot list](F05_examples/snapshot_list_response.json) and [percentage input](F05_examples/percentage_request.json).

## State and error handling

- Request `PENDING`/`RUNNING`: execution has not finished; an empty pattern page is not final evidence of zero patterns.
- Run `COMPLETED`, patternCount 0: valid empty mining result. Request can finish COMPLETED/MATCH with both outputs empty.
- Run `FAILED` or `TIMED_OUT`: show the sanitized failure; do not render partial patterns as successful output.
- Request `FAILED`, comparisonStatus `FAILED`: algorithms completed but exact itemsets/utilities did not match. Do not show a successful comparison.
- Comparison `PENDING`: wait; `NOT_REQUESTED`: only one algorithm selected; `BLOCKED`: at least one algorithm did not complete or capacity/recovery prevented comparison.
- Queue full: POST may return 202 with an already FAILED persisted request and `QUEUE_FULL` runs. Do not interpret 202 alone as a completed mining success.
- Restart: unfinished persisted runs become FAILED/INTERRUPTED. A deliberate retry requires a new UUID; the Backend does not silently resume jobs.
- 400: fix inputs or select a compatible snapshot. 401: use existing login/session recovery. 403: hide/disable forbidden actions. 404: unavailable/foreign record, do not disclose Store details. 409: resolve requestKey conflict. 503: deployment dependency unavailable; keep configuration details out of user forms.

Common application error envelope is `{"error":"message"}`. Authentication middleware may return an empty 401 body; handle both JSON and empty responses. Unknown JSON fields (including storeId) are rejected. No credential is present in handoff examples.

## Utility and benchmark display

`PatternView.utility` is summed frozen weighted utility for the Products when they co-occur in a transaction. It is not revenue, current stock, margin percentage or support. `utilitySpmf` is the normalized integer; display `utility` as the business value. `supportCount` is null because the selected SPMF text output does not measure it; do not display zero or infer support from utility.

`memoryMiB` is SPMF's sampled Java heap metric, not peak RSS. `runtimeMillis` measures child JVM wall time including startup and algorithm IO, excluding common input preparation and Backend parsing/persistence. Runs are sequential, one cold execution per algorithm, no warmup. This is useful for this input/environment; do not present a universal “faster algorithm” claim. Compare only when inputHash, snapshot, minUtilSpmf, conversionVersion and environment match and comparisonStatus is MATCH. The frozen Product mapping is shared; output order may differ.

Java long IDs/divisor and decimal utility values are JSON numbers. Preserve precision using a lossless numeric strategy when values exceed JavaScript's safe integer or decimal precision; do not round a minUtil field through binary floating-point arithmetic. Timestamps are ISO-8601 instants; format for the user's timezone (Store's F03 analysis timezone is stored separately). Configure database JDBC connection/session timezone consistently with the backend, normally UTC.

## Operational limits and known limitations

- Maximum snapshot rows 20,000; distinct Products 256. Conversion must fit signed 64-bit before GCD and signed 32-bit dataset total/threshold after GCD. No silent rounding beyond the frozen scale or filtering to fit limits.
- One active request worker, up to 8 queued requests. Algorithms selected together execute sequentially. Single backend instance only; distributed workers/leases are not implemented.
- Default timeout 60 seconds per algorithm including bounded output verification; default child heap 256 MiB; at most 2,000 patterns. File output cap 4 MiB; process log cap 64 KiB. Backend configuration may adjust timeout/heap/pattern limits within documented bounds.
- Historical snapshots remain usable if valid; active Product/configuration changes affect only new F03 snapshots. F05 captures its own frozen execution copy.
- SPMF is externally configured, not bundled. The verified 2.67a JAR requires a Java 25 child executable; Backend classes are compiled for Java 17. See Backend setup in the API contract.
- Recurring scheduling, cancellation, automatic retries and clustering are not implemented. F06 recommendations are implemented separately; see the [F06 frontend handoff](F06_HANDOFF.md). F06 combines raw patterns with current inventory/business rules and Merchandiser review. E_i=1 does not guarantee appearance in a pattern.
- Full clean MySQL startup is blocked by preexisting V7; V9 and APIs were independently validated on a prepared isolated baseline8 QA schema. H2 full V1–V9 migrations and existing F01–F04 regression tests pass.

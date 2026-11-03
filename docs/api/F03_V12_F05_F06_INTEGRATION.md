<!-- docs-reorganization:notice -->
> **NEEDS_REVIEW — documentation review 2026-10-26.** Old absent-F06 and V7-blocker statements do not describe all current source/recovery state. See [current navigation](../features/F06/README.md). Original results and business-rule text below are retained; this notice is not a new execution or approval.
<!-- /docs-reorganization:notice -->

# F03 v1.2 → F05/F06 integration contract

## Implemented boundaries

F04 DatabaseTransactionSource supplies Store-scoped eligible COMPLETED transaction IDs, Product IDs, quantities and historical unit_price/unit_cost to F03. No ingestion change is required for v1.2. F03's six-decimal BigDecimal base/weighted item utilities and transaction/dataset totals are frozen in snapshots. Product IDs remain algorithmItemId under PRODUCT_ID_V1. Snapshot sourceHash identifies historical input; matrixHash identifies frozen scoring configuration, date, scores and batch evidence.

F05 consumes frozen snapshots through a Store-scoped JDBC adapter, creates durable requests/runs and executes HUI-Miner/EFIM through the verified external SPMF 2.67a runner. The System Admin starts the request (`POST /api/system/mining/requests`) under the globally configured algorithm and minUtil; Store users read results. See [Mining API](F05_MINING_API.md) and [F05 → F06 contract](../features/F05/reference/F05_F06_HANDOFF.md). No F03 calculation behaviour changed. Fresh MySQL 8.4 provisioning (Flyway V1–V17) passes in `scripts/verify-option-b-macos.sh`; the V7 recovery procedure applies only to databases that already applied the old V7 checksum.

## F05 consumer obligations

Consume known snapshotId via Store-authorized metadata/matrix APIs. Fetch every matrix page, verify READY, matrix/source hashes, itemMapping and scoringVersion; do not recalculate Product T, S/E/M or W. Accept original historical F03_LINEAR_V1 and new EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1 according to their stored values. Preserve the analysis date/timezone and warnings. Legacy evidence/timeZone may be null. Repeated transactionUtility is a transaction total, not an additional item utility.

Scale frozen item utilities by exactly10^6 using exact decimal/integer conversion and signed64 overflow checks. Preserve HALF_UP rounding already performed by F03; do not rescale per run or silently clamp to32bit. F05 now implements a lossless GCD reduction after the exact scale6 conversion, then rejects normalized dataset totals above signed32 capacity; this preserves utility values and common algorithm compatibility. Ensure the exported file hash matches its bytes; the JSON matrixHash is a matrix identity and is not automatically the hash of a differently formatted mining file. F05 reads the active mining configuration (algorithm, minUtil mode and value), persists its converted absolute threshold/library/conversion provenance and exposes `GET /api/mining/snapshots` for READY snapshot selection. Its input byte hash is separate from the original matrixHash.

## F06 independent push contract

Implemented GET `/api/utility/expiry-push-batches` is authorized for STORE_ADMIN or MERCHANDISER; Store comes from F01. Optional productId, asOfDate, page>=0 and pageSize1..100. Default asOfDate is today's configured Store-local date, default Asia/Ho_Chi_Minh. Ordering is expiryDate/productId/batchId. It returns exact batch IDs, expiry dates, quantities, configured T, daysRemaining, push flags/quantity, date and warnings, using the same calculator as Preview. No transaction, snapshot or Mining pattern is required.

Current eligibility: ACTIVE Product requiring expiry with positive Product T, ACTIVE batch, remaining>0, expiry strictly after today's Store-local date, daysRemaining<=T. Missing expiry is not push-eligible and produces warnings on candidate Products. Expired/today/blocked/empty batches and inactive Products cannot appear in current push results.

F06 Recommendation Engine is implemented; see the [F06 API contract](F06_RECOMMENDATION_API.md) and [approved rules](../requirements/F06_RECOMMENDATION_RULES_v1_1.md). Its `GET /api/recommendations/expiry-push` endpoint reads current expiry alerts independently of Mining, while recommendation candidates consume frozen F05 evidence and recheck current Product status and sellable batch quantity. F03's `/api/utility/expiry-push-batches` remains a separate API. An explicit historical `asOfDate` or frozen snapshot is analytical evidence, not current sale permission. Use FEFO; no clearance-before-expiration guarantee is implied. F02 summary does not itself enforce Product activation; the independent push query does, and non-push bundle Products require their own current-status/sellability check.

## End-to-end data flow F03 → F05 → F06 (current branch)

| Step | Actor | Call | What is persisted / frozen |
|---|---|---|---|
| 1 | System Admin | `PUT /api/system/utility/lambda-policy` | Global λ bounds (versioned) |
| 2 | Merchandiser | `PUT /api/utility/config`, `PATCH /api/utility/products/{id}/scores`; Store Admin sets Product `expiryPushDays` through F02 | Store weights, manual S/M, Product threshold T |
| 3 | Merchandiser | `POST /api/utility/snapshots` | Immutable snapshot: F04 historical rows, frozen config, scores, FEFO expiry evidence, `sourceHash`/`matrixHash` |
| 4 | System Admin | `PUT /api/system/mining/config` | Versioned algorithm and minUtil policy |
| 5 | System Admin | `POST /api/system/mining/requests` `{storeId,snapshotId,requestKey,compareAlgorithms?}` | Mining request pinned to the snapshot and config version; frozen SPMF input and threshold; one run per algorithm |
| 6 | Worker (every backend instance) | database polling, lease + fencing token, SPMF child (Java 25) | Run status, metrics, patterns written atomically with COMPLETED |
| 7 | Merchandiser, Store Admin (System Admin reads requests and runs but **not** patterns, decision D1) | `GET /api/mining/requests/{id}`, `…/runs`, `…/patterns` | Read-only |
| 8 | System Admin | `GET /api/system/mining/stores/{storeId}/runs/{runId}/benchmark` | Read-only comparison of the two runs |
| 9 | Merchandiser | `POST /api/recommendations/generate` `{miningRunId,types}` with a COMPLETED run | F06 recommendations built from the raw patterns plus current inventory; Merchandiser approves or rejects |

The scheduler replaces steps 3 and 5 for a schedule: occurrence reservation → fresh F03 snapshot (created by the scheduler principal, not by a Merchandiser) → mining request → worker → completion reconciliation. It is implemented on the Scheduler branch, locally verified and off by default (activation returns 503 unless `paspm.mining.schedule-dispatch-enabled=true`); the F03 snapshot it creates is as immutable as a manual one and its F06 consumption is unchanged. Policies D3–D10 are confirmed ([register](../decisions/PO_DECISION_REGISTER_F05_2026-11-01.md)).

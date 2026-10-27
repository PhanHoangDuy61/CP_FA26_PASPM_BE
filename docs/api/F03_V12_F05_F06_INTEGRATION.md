<!-- docs-reorganization:notice -->
> **NEEDS_REVIEW — documentation review 2026-10-26.** Old absent-F06 and V7-blocker statements do not describe all current source/recovery state. See [current navigation](../features/F06/README.md). Original results and business-rule text below are retained; this notice is not a new execution or approval.
<!-- /docs-reorganization:notice -->

# F03 v1.2 → F05/F06 integration contract

## Implemented boundaries

F04 DatabaseTransactionSource supplies Store-scoped eligible COMPLETED transaction IDs, Product IDs, quantities and historical unit_price/unit_cost to F03. No ingestion change is required for v1.2. F03's six-decimal BigDecimal base/weighted item utilities and transaction/dataset totals are frozen in snapshots. Product IDs remain algorithmItemId under PRODUCT_ID_V1. Snapshot sourceHash identifies historical input; matrixHash identifies frozen scoring configuration, date, scores and batch evidence.

F05 P0 now consumes frozen snapshots through a Store-scoped JDBC adapter, creates durable requests/runs and executes HUI-Miner/EFIM through the verified external SPMF2.67a runner. See [implemented Mining API](F05_MINING_API.md) and [F05 → F06 contract](../features/F05/reference/F05_F06_HANDOFF.md). No F03 calculation behavior changed. Native clean MySQL provisioning still has the documented preexisting V7 blocker.

## F05 consumer obligations

Consume known snapshotId via Store-authorized metadata/matrix APIs. Fetch every matrix page, verify READY, matrix/source hashes, itemMapping and scoringVersion; do not recalculate Product T, S/E/M or W. Accept original historical F03_LINEAR_V1 and new EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1 according to their stored values. Preserve the analysis date/timezone and warnings. Legacy evidence/timeZone may be null. Repeated transactionUtility is a transaction total, not an additional item utility.

Scale frozen item utilities by exactly10^6 using exact decimal/integer conversion and signed64 overflow checks. Preserve HALF_UP rounding already performed by F03; do not rescale per run or silently clamp to32bit. F05 now implements a lossless GCD reduction after the exact scale6 conversion, then rejects normalized dataset totals above signed32 capacity; this preserves utility values and common algorithm compatibility. Ensure the exported file hash matches its bytes; the JSON matrixHash is a matrix identity and is not automatically the hash of a differently formatted mining file. F05 receives requested minUtil, persists its converted absolute threshold/library/conversion provenance and exposes `GET /api/mining/snapshots` for READY snapshot selection. Its input byte hash is separate from the original matrixHash.

## F06 independent push contract

Implemented GET `/api/utility/expiry-push-batches` is authorized for STORE_ADMIN or MERCHANDISER; Store comes from F01. Optional productId, asOfDate, page>=0 and pageSize1..100. Default asOfDate is today's configured Store-local date, default Asia/Ho_Chi_Minh. Ordering is expiryDate/productId/batchId. It returns exact batch IDs, expiry dates, quantities, configured T, daysRemaining, push flags/quantity, date and warnings, using the same calculator as Preview. No transaction, snapshot or Mining pattern is required.

Current eligibility: ACTIVE Product requiring expiry with positive Product T, ACTIVE batch, remaining>0, expiry strictly after today's Store-local date, daysRemaining<=T. Missing expiry is not push-eligible and produces warnings on candidate Products. Expired/today/blocked/empty batches and inactive Products cannot appear in current push results.

F06 Recommendation Engine is implemented; see the [F06 API contract](F06_RECOMMENDATION_API.md) and [approved rules](../requirements/F06_RECOMMENDATION_RULES_v1_1.md). Its `GET /api/recommendations/expiry-push` endpoint reads current expiry alerts independently of Mining, while recommendation candidates consume frozen F05 evidence and recheck current Product status and sellable batch quantity. F03's `/api/utility/expiry-push-batches` remains a separate API. An explicit historical `asOfDate` or frozen snapshot is analytical evidence, not current sale permission. Use FEFO; no clearance-before-expiration guarantee is implied. F02 summary does not itself enforce Product activation; the independent push query does, and non-push bundle Products require their own current-status/sellability check.

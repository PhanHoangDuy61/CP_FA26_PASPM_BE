# F05 → F06 Raw Mining Contract

F06 Recommendation Engine is implemented on `dev`. This document defines the Store-scoped source evidence it consumes; see the [F06 API contract](../api/F06_RECOMMENDATION_API.md), [approved rules](../requirements/F06_RECOMMENDATION_RULES_v1_1.md), and [frontend handoff](../frontend_handoff/F06_HANDOFF.md) for the current recommendation behavior and review workflow.

Use Store-scoped `GET /api/mining/runs/{id}` and `GET /api/mining/runs/{id}/patterns`, then the run's `requestId` with `GET /api/mining/requests/{id}`. Store Admin/Merchandiser can read; F06 authorization is defined separately in the [F06 API contract](../api/F06_RECOMMENDATION_API.md). Cross-Store reads return 404. The implemented F06 in-process consumer uses Store-scoped mining reads; do not add an unscoped “all patterns” query.

| F06 information | Real field/source |
|---|---|
| Run identity/Store/snapshot/algorithm | RunView.runId, storeId, snapshotId, algorithm |
| Request/scaling provenance | RunView.requestId, minUtilSpmf, inputHash, conversionVersion, libraryVersion |
| Original requested/effective threshold | RequestView.minUtilMode, minUtilValue, effectiveMinUtility, divisor, utilityScale, roundingMode |
| Pattern identity | PatternView.patternId and runId |
| Products | PatternView.productIds; ascending real Product IDs, never dense SPMF IDs |
| Utility and size | PatternView.utility, utilitySpmf, patternSize |
| Support | PatternView.supportCount=null; not measured; never infer zero |
| Immutable calculation metadata | RequestView.snapshot.configuration (original scoringVersion/coefficients/lambda/policy/date/timeZone), snapshot.sourceHash/matrixHash, request.snapshotHash/inputHash |
| Reliability | RunView.status=COMPLETED, RequestView.comparisonStatus; avoid FAILED comparisons |

Raw pattern utility is the sum of frozen item utilities for transactions containing all pattern Products. It is not automatically a current margin or recommendation score. The complete canonical itemset/utility comparison is saved when both algorithms run. Each F06 generation request names one completed run. If equivalent HUI-Miner and EFIM results are generated separately, F06 deduplicates equivalent candidates while preserving each run/pattern as provenance; it does not count equivalent algorithm output as additional transaction evidence.

Read pages until totalElements is covered; pageSize<=100. Pattern IDs are unique per run, so equivalent sets in two runs have different pattern IDs. F06 preserves run+snapshot provenance on recommendation sources. Existing records are immutable results; Product settings/prices/status can change and must not rewrite these records.

Expiry push is independent of Mining. F06 exposes its own `GET /api/recommendations/expiry-push` current-alert view and does not require a snapshot or pattern; F03's `GET /api/utility/expiry-push-batches` remains a separate utility API. A Product with E_i=1 is not guaranteed to belong to a high-utility pattern. Do not filter all mining output only by current expiry priority. F06 combines raw patterns, versioned Store policy, current Product/inventory state and a separate Merchandiser review process.

Verified examples are in `../frontend_handoff/F05_examples/`: completed request, algorithm runs, normalized patterns, benchmark. These are actual executed synthetic QA data, not recommendations.

## F05-12 — P1 recurring scheduling backlog

Deferred by user scope. A later design must select newly created eligible immutable snapshots, persist schedules/history, deduplicate schedule+snapshot+algorithm+threshold, bound concurrency, acquire distributed leases if deployment is clustered, define retry/cancel policy and preserve historical snapshots. Do not auto-generate snapshots or mutate a past execution. This P1 work does not block the manual P0 workflow.

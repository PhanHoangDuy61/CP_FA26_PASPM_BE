<!-- docs-reorganization:notice -->
> **NEEDS_REVIEW — documentation review 2026-10-26.** F06 Backend APIs and rules now exist; future/permissional wording below is retained for review. See [current navigation](../../F06/README.md). Original results and business-rule text below are retained; this notice is not a new execution or approval.
<!-- /docs-reorganization:notice -->

# F05 → F06 Raw Mining Contract

F06 is a future consumer; no Recommendation, Bundle, approval or campaign implementation was added.

Use Store-scoped `GET /api/mining/runs/{id}` and `GET /api/mining/runs/{id}/patterns`, then the run's `requestId` with `GET /api/mining/requests/{id}`. Store Admin/Merchandiser can read, but F06's own permissions/approval policy must be designed separately. Cross-Store reads return 404. A future in-process consumer can reuse the Store-scoped MiningRepository query contract; do not add an unscoped “all patterns” query.

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

Raw pattern utility is the sum of frozen item utilities for transactions containing all pattern Products. It is not automatically a current margin or recommendation score. The complete canonical itemset/utility comparison is saved when both algorithms run. F06 should choose one completed run as its source to avoid treating equivalent HUI-Miner and EFIM patterns as independent evidence.

Read pages until totalElements is covered; pageSize<=100. Pattern IDs are unique per run, so equivalent sets in two runs have different pattern IDs. Preserve run+snapshot provenance in any future F06 output. Existing records are immutable results; Product settings/prices/status can change and must not rewrite these records.

Expiry push is independent. F06 may use the existing `GET /api/utility/expiry-push-batches` (StoreAdmin/Merchandiser; F03 contract) to obtain current eligible batches. A Product with E_i=1 is not guaranteed to belong to a high-utility pattern. Do not filter all mining output only by current expiry priority. Combine raw patterns, Store rules, current Product/inventory state, independent expiry-push requirements and a separate Merchandiser review process.

Verified examples are in `../frontend_handoff/F05_examples/`: completed request, algorithm runs, normalized patterns, benchmark. These are actual executed synthetic QA data, not recommendations.

## F05-12 — P1 recurring scheduling backlog

Deferred by user scope. A later design must select newly created eligible immutable snapshots, persist schedules/history, deduplicate schedule+snapshot+algorithm+threshold, bound concurrency, acquire distributed leases if deployment is clustered, define retry/cancel policy and preserve historical snapshots. Do not auto-generate snapshots or mutate a past execution. This P1 work does not block the manual P0 workflow.

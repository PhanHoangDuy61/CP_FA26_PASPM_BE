# DEC-20261116-04 — F05 new mining is EFIM-only; comparison and benchmarks removed

| Field | Value |
|---|---|
| Date | 2026-11-16 (Asia/Ho_Chi_Minh) |
| Source | Owner decision of 2026-11-16. Implemented by commit `cd90e58` ("Enforce EFIM-only mining and remove benchmarks"), merged into `dev` by `2a944c5`. Implementation record: [ROC-20261116-008](../../record-of-changes/2026-11-16/08-f05-efim-only-and-benchmark-removal.md) |
| Status | OWNER_DECISION — approved 2026-11-16; implemented on `dev` |
| Supersedes | Two-algorithm requests (HUI-Miner + EFIM), `compareAlgorithms`, the algorithm choice in the System Admin minUtil configuration, both benchmark routes, and the `COMPARING` progress stage for new requests, as described in the F05 docs of 2026-10-19 to 2026-11-15 |

## Decision
- Every new mining request, whether manual (System Admin or Merchandiser) or scheduled, creates exactly **one EFIM run**.
- The minUtil configuration no longer has an algorithm.
- `compareAlgorithms` and the schedule `compare` input are rejected (400).
- `GET /api/mining/runs/{id}/benchmark` and `GET /api/system/mining/stores/{storeId}/runs/{runId}/benchmark` are removed.
- Historical requests, HUI-Miner runs, comparison statuses, metrics and patterns remain readable unchanged. No migration rewrites them. V24 keeps `COMPARING` in its CHECK constraint and is not edited.

## Code evidence (`origin/dev` `2a944c5`)
- Manual requests: both public paths, `createMerchandiser` (`src/main/java/vn/edu/fpt/paspm/mining/MiningService.java:54`) and `createAdministrative` (`:57`), build `List.of(EFIM)` at `:75`. The internal `create` method rejects anything except `[EFIM]` at `:30-31`.
- Scheduled requests: `src/main/java/vn/edu/fpt/paspm/mining/MiningScheduledRequest.java:38` builds `List.of(EFIM)`.
- Runs: `src/main/java/vn/edu/fpt/paspm/mining/MiningRepository.java:34` inserts one `mining_run` per requested algorithm, so a new request has one run.

## Can `COMPARING` still be reached?
**Not for a new request.** `src/main/java/vn/edu/fpt/paspm/mining/MiningWorker.java:84` sets `COMPARING` only when the request has two runs (`runs.size()==2`), and the paths above create only one.

It can still be reached by a **legacy request created before `cd90e58` with two runs** that is still `PENDING`, or `RUNNING` with an expired lease, when the new version starts:
- `src/main/java/vn/edu/fpt/paspm/mining/MiningDispatchLease.java:21` reclaims such a request;
- the worker then runs both stored runs and compares them;
- `MiningDispatchLease.java:42` still accepts `COMPARING` as an active stage.

This keeps in-flight legacy work completable. Clients must keep handling `COMPARING` and `comparisonStatus` on historical requests.

## Tests
`MiningIntegrationTest`, `MiningScheduledActivationE2EMySqlTest`, `LegacyReaderMySqlTest` and `F03F05RbacMatrixTest` were repaired after the merge to assert EFIM-only behaviour. See [ROC-20261116-010 §2](../../record-of-changes/2026-11-16/10-remove-legacy-product-fields-and-routes.md).

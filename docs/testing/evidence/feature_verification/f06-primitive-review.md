# F06 primitive review

Scope: FrozenEvidenceReader, TransactionEvidence, RecommendationExpiryService, RecommendationExpiryController and their three test classes. Read-only source review using code-review-and-quality skill; no edits to repository files, commits, builds, or test reruns. Existing evidence reports state 14 evidence tests and 5 expiry integration tests passed. Inspected related existing repository/calculator code solely to establish behavior.

## Verdicts

- Spec compliance: request changes for the frozen-source completeness validation below. Expiry and transaction evidence satisfy their scoped contracts.
- Code quality: request changes for the same P2 integrity gap. No P0/P1 issues identified. Adapter boundaries are appropriate, SQL parameters are bound, returned evidence is copied, and existing F03/F05/shared expiry logic is reused without writes.

## Actionable findings

### [P2] Verify completed-run pattern count before accepting the frozen source

File: `/Users/khoinm/Desktop/khoi/fpt/ki9/FA26SE048_HUPAS/BE/src/main/java/vn/edu/fpt/paspm/recommendation/FrozenEvidenceReader.java`, lines 68–85 (particularly line 83).

The paging termination trusts the current pattern table count and never compares collected patterns against `run.itemsetCount()`, which F05 records atomically when completing the run. If any high_utility_pattern rows are missing, the reader accepts a truncated source; deleting every pattern makes a run that originally completed with patterns look like a legitimate zero-pattern result. The brief requires inconsistent persisted sources to fail with sanitized 409 while allowing genuinely completed zero-pattern runs. Validate the recorded completed itemset count against the retrieved total/collected unique pattern IDs, rejecting null/negative/inconsistent count as appropriate. Add regression cases for a recorded nonzero count with no patterns and a partially missing pattern list; keep a genuine zero-count/zero-pattern case valid. The existing zero-pattern test currently leaves RunView.itemsetCount at 1 and therefore masks this gap.

## Checked behavior

- Store ownership resolves through MiningRepository first; foreign/missing runs retain 404; failed runs and malformed persisted source content become 409.
- Snapshot identity/store/hash/configuration and frozen conversion metadata are checked; frozen text SHA agrees with request/run hash; dense mapping is bijective; zero-utility membership is retained; immutable matrix transaction membership and row limits are checked.
- Pattern items are checked against Store, canonical product membership, size and dense mapping. Nullable SPMF support is ignored.
- Support counts presence once; group/anchor arithmetic, six-decimal HALF_UP precision, empty evidence and zero-anchor availability are explicit. Defensive copies prevent later basket mutation.
- Expiry requires no mining source, scopes Product/Store, checks HTTP RBAC, rejects client asOfDate, uses current Store-local date and shared calculator, excludes expired/today/blocked/empty/inactive/non-expiring/disabled products, retains missing-metadata warnings and known qualifying batches, sorts FEFO, validates pagination, and emits matching page/item checkedAt.
- All inspected new production code is read-only. No source evidence or inventory mutation found.

## Validation limits

Evidence reader tests use real H2 reader SQL/JSON decoding with mocked MiningRepository and UtilitySnapshotRepository boundaries. They establish adapter behavior but not the entire F03→F05 persistence route; root integration/MySQL verification remains relevant. No tests were rerun per review instructions.

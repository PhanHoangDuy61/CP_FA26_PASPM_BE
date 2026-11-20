# ROC-20261117-015 — F05 EFIM-aware lossless int32 guard

**Date/timezone:** 2026-11-17 / Asia/Ho_Chi_Minh
**Status:** COMPLETED; MERGED into `dev` by `530651b` on 2026-11-17 (was: "COMPLETED LOCALLY, COMMITTED, UNPUBLISHED"; updated 2026-11-19, [ROC-20261119-001](../2026-11-19/01-record-owner-decisions.md))
**Branch/base:** `Khoi/feat/f05-efim-aware-int32-guard`, based on `739a6d934ad371f7e911cdf9d4a8da04f5caa787`
**Decision:** [DEC-20261117-01](../../decisions/2026-11-17/DEC-20261117-01-efim-aware-lossless-int32-guard.md)

## Summary

Implemented the owner-approved lossless option B: the global normalized utility total no longer has to fit int32. F05 retains exact scale-6/GCD conversion and checks the quantities EFIM 2.67a actually keeps in signed int: per-transaction utility, per-Product TWU, and positive converted `minUtil`. Global totals and percentage calculations now use checked `long`; `RequestView.totalScaledUtility` is OpenAPI int64; F06 validates long frozen totals. No quantization, migration, frozen-data rewrite, endpoint change or limit increase was made.

## Step 0 — measured demo seed ranges

Seed worktree commit `331fe910900ce6689211cef5976645785aea6713`; CSV SHA-256 values: `Transactions_F04_Import_Test_Part1.csv` `5e45ffbdccd4947a1e4c2ee2528e6ae58e8b7364b999288c268d3dcc39b2a8ff`, `Transactions_F04_Import_Test_Part2.csv` `7357315a4d485901a5f2344d8d39eb3b2d16514e0e33880df39deecaca07c46f`. Snapshot analysis date 2026-10-05. Exact frozen scale-6/GCD measurements below are normalized integers. `TU` and `TWU` are maxima; `minUtil` is PERCENTAGE 0.25, ceiling-rounded. `I` is signed `Integer.MAX_VALUE=2,147,483,647`.

| Range | Rows / transactions / Products | GCD | Total | max TU | max TWU | minUtil | EFIM guard |
|---|---|---:|---:|---:|---:|---:|---|
| Full | 18,319 / 4,141 / 1,559 | 10,000 | 5,196,907,628 (>I) | 5,189,374 (<I) | 43,859,587 (<I) | 12,992,270 (<I) | Pass |
| Day 1 | 4,066 / 900 / 1,444 | 10,000 | 1,163,610,588 | 3,746,798 | 17,550,210 | 2,909,027 | Pass |
| Day 2 | 6,243 / 1,440 / 1,527 | 10,000 | 1,791,992,761 | 5,189,374 | 20,203,259 | 4,479,982 | Pass |
| Day 3 | 6,389 / 1,440 / 1,530 | 10,000 | 1,779,174,736 | 4,347,417 | 18,543,286 | 4,447,937 | Pass |
| Day 4 | 1,621 / 361 / 1,002 | 10,000 | 462,129,543 | 3,401,688 | 11,865,908 | 1,155,324 | Pass |
| Days 1–2 | 10,309 / 2,340 / 1,554 | 10,000 | 2,955,603,349 (>I) | 5,189,374 | 28,883,771 | 7,389,009 | Pass |
| Days 2–3 | 12,632 / 2,880 / 1,559 | 10,000 | 3,571,167,497 (>I) | 5,189,374 | 33,169,667 | 8,927,919 | Pass |
| Days 3–4 | 8,010 / 1,801 / 1,551 | 10,000 | 2,241,304,279 (>I) | 4,347,417 | 21,603,172 | 5,603,261 | Pass |
| Days 1–3 | 16,698 / 3,780 / 1,559 | 10,000 | 4,734,778,085 (>I) | 5,189,374 | 41,349,131 | 11,836,946 | Pass |
| Days 2–4 | 14,253 / 3,241 / 1,559 | 10,000 | 4,033,297,040 (>I) | 5,189,374 | 34,952,669 | 10,083,243 | Pass |

All measured TU, Product TWU and minUtil values are below I; only the full and multi-day global totals cross I. This is demo seed data and not authentic FoodMart. The seed's verification states total weighted utility `54,013,051.55`, while the persisted full-range F03 snapshot is `51,969,076.28` (`5,196,907,628 × 10,000 / 1,000,000`). The 2,043,975.27 discrepancy is unresolved and should be reconciled before treating those aggregates as equivalent.

For W=1.3, an exact constructed scenario with its own GCD 1,300,000 fit int32 in each range, but this is not a universal bound because normalization divisor varies. A conservative upper bound uses scale-6 weighted values and forced divisor 1. For full, days 1, days 2, days 3, days 4, days 1–2, days 2–3, days 3–4, days 1–3, days 2–4 respectively, raw `(total, max TU, max TWU, 0.25% minUtil)` is:

| Range | Conservative W=1.3 raw bound `(total, TU, TWU, minUtil)`; each may be compared with I |
|---|---|
| Full | `(65,707,556,200,000; 64,132,900,000; 557,939,200,000; 164,268,890,500)` |
| Day 1 | `(14,711,013,200,000; 47,815,300,000; 223,945,800,000; 36,777,533,000)` |
| Day 2 | `(22,661,826,200,000; 64,132,900,000; 254,701,200,000; 56,654,565,500)` |
| Day 3 | `(22,495,647,200,000; 53,818,700,000; 236,841,800,000; 56,239,118,000)` |
| Day 4 | `(5,839,069,600,000; 43,599,400,000; 150,358,000,000; 14,597,674,000)` |
| Days 1–2 | `(37,372,839,400,000; 64,132,900,000; 365,840,800,000; 93,432,098,500)` |
| Days 2–3 | `(45,157,473,400,000; 64,132,900,000; 424,797,100,000; 112,893,683,500)` |
| Days 3–4 | `(28,334,716,800,000; 53,818,700,000; 272,123,800,000; 70,836,792,000)` |
| Days 1–3 | `(59,868,486,600,000; 64,132,900,000; 526,611,800,000; 149,671,216,500)` |
| Days 2–4 | `(50,996,543,000,000; 64,132,900,000; 443,926,600,000; 127,491,357,500)` |

These are safe upper bounds on normalized values, not exact W=1.3 conversion results; all exceed I and therefore cannot decide admissibility. The actual guard evaluates each snapshot's frozen numbers and actual divisor.

## Step 1 — SPMF bound argument

Pinned JAR: SPMF 2.67a SHA-256 `4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a`.

| EFIM value | Bytecode type/path | Sufficient bound used |
|---|---|---|
| Parsed transaction TU and item utilities | `Dataset.createTransaction(String)` parses with `Integer.parseInt`; `Transaction.transactionUtility`, `Transaction.prefixUtility`, `Transaction.utilities` are int/int[] | Each TU ≤ I; each item utility is nonnegative and ≤ its TU |
| Product TWU and initial LU/SU bins | `AlgoEFIM` `utilityBinArrayLU` / `utilityBinArraySU` are int arrays initialized from transactions | Checked sum of TU over transactions containing that item ≤ I |
| Projected transaction TU/prefix state | EFIM projection/backtracking uses int TU and prefix fields | For a projected candidate, sum belongs only to transactions containing it, therefore ≤ that Product TWU ≤ I |
| Dynamic local/subtree utility bins | `AlgoEFIM` `utilityBinArrayLU`, `utilityBinArraySU` updates in projection/search | Accumulated for a candidate's matching transaction set; bounded by its Product TWU for nonnegative PASPM utilities |
| Pattern utility and output | `AlgoEFIM.backtrackingEFIM` local utility sums and `output(int,int)` | Pattern includes each selected item and its transaction contributions are a subset of that item's TWU; ≤ I |
| minUtil | `AlgoEFIM.minUtil` and `runAlgorithm(int,...)` | Positive converted threshold ≤ I |
| Global database total | No single global-total accumulator found in the EFIM execution path | PASPM can retain this aggregate as long; it is not an EFIM int accumulator |

Negative utility is already forbidden by PASPM, which is necessary to use these monotone sum bounds. Product TWU is computed with checked long and compared before serialization; transaction TU and minUtil are also checked. A small real EFIM run with global total 3,000,000,001 and two Product TWUs of 1,500,000,001 completed; EFIM returned pattern utilities summing to 3,000,000,000, matching independent long recomputation.

## Implementation and API

- `MiningInputBuilder.Dataset.totalUtility`, the `RequestView.totalScaledUtility`, percentage threshold arithmetic and F06 frozen evidence totals now use signed long with checked arithmetic.
- Removed only the global total int32 guard. Per-transaction TU, per-Product TWU and converted minUtil have separate 400 errors; item/TWU overflow is rejected before enqueueing.
- `FrozenEvidenceReader` accepts and exactly validates totals above int32. The legacy F06 read test captures the stored frozen-input JSON before reading and verifies the stored bytes are unchanged afterward; the existing numeric property continues to deserialize exactly. No frozen format version was needed: property name, scale-6/GCD representation and numeric semantics did not change.
- `totalScaledUtility` changed from OpenAPI `integer/int32` to `integer/int64`; routes and JSON field name are unchanged. At current bounds, a crude max is `20,000 × Integer.MAX_VALUE = 42,949,672,940,000`, below JS `2^53`, but clients must not assume int32.
- No database column persists this total; it is in frozen input JSON. No migration was needed; V1–V26 are untouched.
- Decision: [DEC-20261117-01](../../decisions/2026-11-17/DEC-20261117-01-efim-aware-lossless-int32-guard.md). Public contract and benchmark: [F05 API](../../api/F05_MINING_API.md#lossless-efim-numeric-bounds--2026-11-17), [benchmark](../../testing/F05_FOODMART_SCALABILITY_BENCHMARK.md#efim-aware-exact-int32-guard--2026-11-17).

## Demo acceptance

On disposable MySQL, the demo seed's full 2026-10-01..04 F03 snapshot (as of 2026-10-05) was accepted at `PERCENTAGE 0.25`: F05 request/run COMPLETED, EFIM runtime 236 ms, 0 patterns, 100 output bytes, sampled child heap 16.6228 MiB, F06 generation HTTP 200 with zero recommendations. The normalized total `5,196,907,628` exceeds int32; the EFIM-specific TU/TWU/minUtil bounds pass. This demonstrates the global-total guard removal through F06.

At the lowest tested threshold under the pattern cap, `ABSOLUTE 38,360.409900` / `minUtilSpmf 3,836,041` completed in 386 ms with 2,000 patterns and 92,758 output bytes (sampled heap 19.122848 MiB); F06 created 2,006 recommendation rows (2,003 eligible and 3 ineligible from stored frozen eligibility JSON). The next lower tested threshold `38,360.399900` / `3,836,040` failed `OUTPUT_LIMIT` (“Pattern count exceeds configured limit”); F06 was not invoked for the failed run. An API paginated list verification was interrupted as slow, so every recommendation page is NOT RUN.

The separate realistic-utility synthetic fixture remains rejected because its Product TWU is `3,217,593,703` (> I); this is the correct remaining EFIM accumulator limit. EFIM and F06 for that rejected fixture are NOT RUN. Authentic FoodMart F03 acceptance remains NOT RUN because canonical order grouping and approved inventory evidence are unavailable.

## Tests and gates

Test-first red run: `MiningInputTest`, 8 tests against old implementation, failed as expected for the newly specified acceptance cases. After implementation, focused `MiningInputTest`, `FrozenEvidenceReaderTest`, `SpmfMiningEngineTest`: 34/34 PASS; `MiningIntegrationTest#openApiIncludesEveryMiningRoute`: 1/1 PASS. Relevant methods include `MiningInputTest#efimInt32GuardAcceptsMaxTransactionUtilityAndRejectsOneAbove`, `#efimInt32GuardChecksProductTwuAtExactBoundary`, `#globalTotalMayExceedInt32WhenEveryProductTwuFits`, `#convertedPercentageMinUtilAboveInt32IsRejectedClearly`; `SpmfMiningEngineTest#actualEfimAcceptsGlobalLongTotalWhenTransactionAndTwuBoundsFit`; and `FrozenEvidenceReaderTest#readsNewFrozenEvidenceWithLongTotalExactly`, `#rejectsTamperedLongFrozenTotal`, `#readsFrozenMembershipIncludingZeroUtilityAndIgnoresNullableSpmfSupportAndLiveEdits`. The last F06 method compares the persisted frozen-input JSON before/after the read. `MiningIntegrationTest#workerReadsFrozenRequestCopyInsteadOfReloadingMutableSource` covers F05's frozen request use.

An earlier full-opt-in attempt after the guard change had 1 failure: the realistic FoodMart-shaped test still expected the removed global-total error. This was a stale assertion, not an implementation failure. `FoodMartScaleSyntheticE2EMySqlIntegrationTest` was updated to expect the independently verified Product-TWU error; the focused disposable-MySQL class then passed 3/3, and the complete verifier was rerun afterward.

Final `bash scripts/verify-option-b-macos.sh --run` after that assertion update: exit 0, 24/24 stages PASS. Default Maven: 667 discovered, 0 failures, 0 errors, 211 skipped. Flag/migration: 6/6. MySQL group: 72/72. Full opt-in: 677 discovered, 0 failures, 0 errors, 27 skipped (650 pass). Multi-JVM stage in the verifier: 23 invocations, 20 pass and 3 skipped because its optional natural-lease/legacy-JAR properties were not enabled. The supplementary run enabled those properties and supplied the c8d778f legacy jar: 23/23 PASS, split `MiningMultiJvmMySqlTest` 6/6, `MiningSchedulerMultiJvmMySqlTest` 12/12, `MiningSchedulerUpgradeCompatibilityMySqlTest` 5/5. JPA clone: 5/5 PASS on a disposable, isolated schema.

Environment: macOS 26.5.2 arm64, Maven 3.9.12, backend Java 17.0.21, SPMF child Java 25, pinned SPMF 2.67a JAR, disposable MySQL 8.4.13. No production/shared DB was used. The full verifier and supplementary runs stopped their disposable database containers afterward.

The prompt baseline was full opt-in 668 total / 641 pass / 27 skip, multi-JVM 23/23, MySQL 72/72 and JPA 5/5. Current full opt-in is 677 total / 650 pass / 27 skip: +9 executed tests and +9 passes, with the same skips. Multi-JVM, MySQL and JPA match their baselines. The three multi-JVM skips in the full script are not treated as passes; their separate opt-in run is the evidence that covers them. All disposable DB containers were stopped. No network action, commit, push, merge or deploy occurred.

### External evidence (outside Git)

Evidence remains in the task's external temporary evidence directories; no raw execution JSON or logs are committed. SHA-256:

| Evidence file | SHA-256 |
|---|---|
| `paspm-f05-int32-step0-metrics.json` | `7f229f8e5bd97faf74279891cb5dac40b3b7b6df804f0d7335905eec8673450e` |
| Final `summary.tsv` | `17afe892add16f896e8c384957200ab6b1cadfaeb92dafbb70e74a67678f07fa` |
| Final runtime OpenAPI export | `2ddce534d25a7d6f9bbd89c8a342d88ae92df1ec8b2d1a39e6477d14858fbf69` |
| Demo full-range F05 mining response | `8364157a093f8bff8aeca334e1b5bda6490c5872737fa1acb65c8a28f35d8ced` |
| Demo READY snapshots response | `4de15361651a088353d9fd5d9a86f230c911dee192831df5038f63cf4087ddca` |
| Demo F06 generation response | `bd7f8df80ca1fe7655ac8c948d5cf2599b64b7fd5f74b52e48bd0318da5a9e12` |
| Demo minUtil threshold-search results | `2bf011670da60c9ee07016e302d521563828a3958da86a8c9d1e133d5dbf0c6c` |
| Lowest threshold result | `cb72891df79ba0522479a0ecc5847890bb3b1027ece794d46274c5ffdc2c6032` |
| Supplementary all-class multi-JVM evidence | `a5808f428311723421dbc26d7e867399f833f24ca543fd7dd53fbaaf82e5996b` |
| Realistic dense utility MySQL result JSON | `95934de84d78930c6ed03650f1f8d68471057c3cd3df493150cbafaa591c3845` |
| Demo-scale E2E JSON (separate earlier synthetic 1,659-transaction fixture; not the 4,141-order demo seed) | `d8a1a9a82448594477e7ae5d83d4e6602f10b03e4f2120c6b46ff403c358cc17` |

The per-request demo acceptance responses are also preserved outside Git as separate JSON files; no credential-bearing config is included in this record.

## Changed files

- Source: `MiningInputBuilder.java`, `MiningContracts.java`.
- Tests: `MiningInputTest.java`, `SpmfMiningEngineTest.java`, `MiningIntegrationTest.java`, `FrozenEvidenceReaderTest.java`, `FoodMartScaleSyntheticE2EMySqlIntegrationTest.java`.
- F05/F06 docs and contract: `docs/features/F05/README.md`, `docs/features/F05/reference/F05_GAP_ANALYSIS.md`, `docs/features/F06/README.md`, `docs/api/F05_MINING_API.md`, `docs/api/API_CHANGELOG.md`, `docs/api/F03_V12_F05_F06_INTEGRATION.md`, `docs/api/F03_UTILITY_API.md` (broken anchor fix), `docs/api/openapi.json`, `docs/frontend_handoff/F05_HANDOFF.md`, `docs/testing/F05_FOODMART_SCALABILITY_BENCHMARK.md`.
- Decision and indexes: `docs/decisions/2026-11-17/DEC-20261117-01-efim-aware-lossless-int32-guard.md`, this record, `docs/record-of-changes/2026-11-17/README.md`, `docs/record-of-changes/README.md`, `docs/README.md`, `docs/api/README.md`.
- Link-only fixes to achieve a zero-broken-link audit: `docs/release/F03_F05_MERGE_PLAN.md`. The investigation draft Record 014 was removed and replaced by Record 015.
- No Flyway migration changed; no frontend code or word report changed.

## Changed files and status

Implementation source, tests, API contract, OpenAPI export, feature docs, Frontend handoff, benchmark and change records were updated in the existing isolated worktree. No Flyway file changed. All tests described above were actually executed. `git diff --check`, link validation and private-path scans are the final documentation checks; their actual result is recorded after execution in the final report.

## Final documentation validation

- Markdown relative-link scan: **1,276 internal links, 0 broken** (245 Markdown files scanned; 50 external links were not fetched).
- `git diff --check`: PASS.
- Tracked and untracked file scan for private absolute-path strings: zero hits.
- Flyway migration paths changed: 0.
- OpenAPI export: 100 paths / 121 operations, `RequestView.totalScaledUtility` integer/int64, semantic JSON equality with the captured runtime export.
- The broader legacy `docs/audits/tools/validate_documentation.py` command exited 1 because its historical reorganization/preservation baseline also reports 8 frozen-byte differences, 36 Markdown content changes, and 82 protected-file differences in this already-diverged worktree. Its Markdown link subcheck itself reports zero broken internal links. The OpenAPI change is intentional; the other preservation findings are outside this task's baseline and were not modified to satisfy that checker.

**COMPLETED locally; both approved commits were local and unpublished at the time of this record.** **Update 2026-11-19:** they were pushed with the fixup `bb4262d` (`docs(api): restore OpenAPI servers URL after export`) and merged into `dev` by `530651b` on 2026-11-17 ([ROC-20261119-001](../2026-11-19/01-record-owner-decisions.md)). Commit sequence:

1. `feat(f05,f06): EFIM-aware lossless int32 guard with long totals` — source/tests commit `823b4b5`.
2. `docs(f05): record EFIM-aware guard decision and demo capacity` — this documentation commit.

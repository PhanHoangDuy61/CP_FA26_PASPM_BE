# F06 Hardening Report

Execution date: 2026-10-26, Asia/Ho_Chi_Minh. Baseline: `dev` commit `c06d43ec59269b8190f5920b5db526ee4e7f19d7`. Delivery branch: `Khoi/test/f06-hardening`. Implementation commit: `27e37801a04b9744f781374b45789ae90c8d057c`.

## Scope and behavior

The eight existing F06 operations, DTOs, role policies, Store ownership, review transitions, live eligibility and frozen utility ranking are preserved. No Flyway V1–V11 file or schema is changed. Generation and review remain transactional; inventory, sales facts, F03 snapshots and F05 mining evidence are read-only inputs.

Two measured query costs are reduced:

1. List hydration previously executed three relation SELECTs per recommendation: item membership, source provenance and review history. These are now loaded by Store in chunks of at most 500 recommendation IDs. The existing base/count query, ordering, filters and live eligibility cache are retained. Detail/review still use their original single-record relation reads.
2. Generation loaded the actor record again for each newly created candidate. A lazy request-local actor ID now reuses the first validated lookup. Initial StoreScope and authorization checks still execute; reused-only and zero-candidate generation does not add an unnecessary actor lookup.

No production limit is increased. No new index is introduced. See [measured results](../../testing/F04_F06_PERFORMANCE_REPORT.md).

## Coverage added

`RecommendationHardeningIntegrationTest` runs against H2 and is inherited by `RecommendationHardeningMySqlIntegrationTest` on a separately created MySQL schema. Stress construction substitutes only frozen-reader/rule output; real HTTP security, service transactions, repository writes, foreign keys, review logic and live inventory checks execute. These bounded synthetic fixtures are not presented as authentic SPMF mining outputs.

| Area | Executed assertion |
|---|---|
| Generation limits | 9,999 and exactly 10,000 unique identities succeed; 10,001 returns HTTP 409 |
| Cap rollback | After exceeding the cap, no new recommendation/item/source rows persist and an existing recommendation remains byte-identical |
| SQL-failure rollback | Failure injected after real source persistence rolls back candidates and provenance; prior candidates and frozen tables remain intact |
| Ranked scope | 10,001 scoped candidates return 409; narrowing type/review status yields the valid filtered result; successful 9,999/10,000 run lists retain one-based ranks |
| Pagination/order | Creation-desc pages are stable; no overlap at page boundaries; 501 candidates exercise two relation chunks with distinct Products, provenance and review history at positions 499/500/501 and Store isolation |
| Review validation | Missing/null/blank/over-1,000-character reason; null/nonpositive/string/fractional version; stale version; maximum int64 version; exact 1,000-character reason |
| Review atomicity | Failure injected after compare-and-set and history insertion restores version/status and leaves no audit row |
| Review race | Concurrent approval versus rejection has exactly one 200 winner, one 409 loser, version 2 and one immutable history row |
| RBAC/isolation | Missing/invalid token, System Admin/Customer, forbidden Store Admin generation/review, foreign Store generation/review/read, forged Store body |
| Historical preservation | Snapshot/mining/pattern/sales/inventory fingerprints remain equal around generation/review failure and boundary tests |
| Query regression | 25/100 list rows have five relation SELECTs including count rather than 77/302; 501 rows prove bounded chunking |

Existing integration tests continue to cover rule thresholds, exact support/confidence, equivalent-source reuse, repeated decisions, concurrent generation/configuration, current stock/expiry behavior and OpenAPI response fields.

Two new tests in the existing real-SPMF integration suite use the actual `FrozenEvidenceReader`, immutable F03 matrix and local HUI-Miner/EFIM runs. They verify comparison states and reject tampered Product mapping with 409 and no candidates. They run on both H2 and disposable MySQL; these source-policy cases do not use the stress doubles.

## F05 source policy review

Observed implementation and F06 v1.1 both accept a **completed individual own-Store run**, subject to all frozen hash, snapshot membership, conversion, Product mapping and pattern-count checks. `FrozenEvidenceReader` does not impose an overall request-status/comparison-status veto.

| Individual run / context | Actual expected result and verification |
|---|---|
| COMPLETED, comparison MATCH | Generation succeeds with valid frozen evidence |
| COMPLETED, comparison NOT_REQUESTED | Generation succeeds; a comparison is not required by current F06 policy |
| COMPLETED, comparison FAILED, request FAILED | Generation still succeeds for the completed valid run; current behavior explicitly characterized |
| PENDING / RUNNING / FAILED / TIMED_OUT | 409; existing real-source regression verifies each state |
| Invalid frozen hash or Product mapping | 409; no candidate persisted |
| Foreign Store run | 404 before source interpretation |

**PRODUCT_OWNER_REVIEW_REQUIRED:** confirm whether future policy should veto a valid completed individual run when a separate algorithm/comparison failed. Current approved wording is individual-run based; older integration prose can suggest broader comparison acceptance. A blanket veto would exclude usable independently completed evidence, while retaining it can expose candidates from an unconfirmed cross-algorithm comparison. This change preserves the current rule and flags the risk; it does not approve or implement a new rejection rule.

## Verification and readiness

Full Java 17 `mvn clean verify` completed with **364 total: 359 PASS, 0 FAIL, 0 ERROR, 5 SKIP**. The five skipped `PilotCloneApiIntegrationTest` cases require a separately approved populated clone; dedicated new-schema Product/Category MySQL tests ran. The F06 package contains **96 PASS / 0 FAIL / 0 ERROR / 0 SKIP** across 10 suites, including the existing 15-case frozen-reader suite and 33 new H2/MySQL test executions.

An initial full run had 313 PASS, 0 FAIL, 46 ERROR, 5 SKIP because concurrent test suites exhausted the disposable server's initial 60-connection capacity. That failure is retained separately. The successful retest limited only the test invocation's Hikari pools to max 3/min 1 and used the disposable server's raised capacity. No production configuration was changed. Final chunk association strengthening was independently rerun on H2 and MySQL after this full verification.

[Full current counts](../../testing/evidence/f06_hardening/FULL_VERIFY_RETEST.json), [initial environment failure](../../testing/evidence/f06_hardening/FULL_VERIFY_INITIAL_ENVIRONMENT_FAILURE.json), [performance evidence](../../testing/evidence/f06_hardening/PERFORMANCE.json) and [actual MySQL query plans](../../testing/evidence/f06_hardening/MYSQL_EXPLAIN_ANALYZE.json) preserve actual results without private command paths/credentials. No original source database is connected; all MySQL tests allocate schemas on a dedicated disposable MySQL 8.4 instance.

The local SPMF binary uses the approved SHA-256 `4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a`; Backend Java 17 and child Java 25 are separate. No JAR is committed or published. These are local results, not current GitHub-hosted CI or production acceptance. Independent review and the comparison-veto product decision remain pending.

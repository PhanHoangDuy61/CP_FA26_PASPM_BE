# F04 / F06 Performance Review

Execution date: 2026-10-26, Asia/Ho_Chi_Minh. Backend Java 17, MySQL 8.4.11, synthetic disposable data. These measurements establish reproducible local observations, not production capacity or an SLO. Existing Flyway versions are immutable and no index is added.

## F04 — profile after correctness changes

F04 data was actually executed on branch `Khoi/fix/f04-csv-validation`; its canonical evidence is `docs/testing/evidence/f04_csv_correctness/F04_PERFORMANCE.json` in that independent CSV PR. This F06 branch does not import the CSV source/migration changes. No before/after F04 performance improvement is claimed.

Single-client direct service invocation, one 100-row warmup. No network/RBAC timing. Fixture bytes are allocated before measurement. Heap is sampled every 10ms; this is sampled JVM heap growth, not whole-process RSS. CPU is current request-thread time, not MySQL CPU.

| Rows | Upload bytes | Elapsed ms | Rows/sec | Thread CPU ms | Sampled heap growth bytes | Avg committed order transaction ms |
|---:|---:|---:|---:|---:|---:|---:|
| 100 | 6,059 | 101.315 | 987.02 | 50.140 | 11,534,296 | 0.580780 |
| 1,000 | 61,959 | 817.146 | 1,223.77 | 314.221 | 49,283,048 | 0.462670 |
| 10,000 | 638,959 | 6,621.551 | 1,510.22 | 1,686.744 | 52,284,360 | 0.375262 |
| 10,000, padded identifiers | 5,158,959 | 6,524.475 | 1,532.69 | 1,572.856 | 91,988,480 | 0.352978 |

The padded file is approximately 98.4% of the 5MiB upload limit. Each case commits exactly one transaction per valid order and executes `rows + 4` SELECTs: one idempotency lookup per order, one product lookup, one batch read and two actor/Store reads. The 10,000-row cases execute10,004 SELECTs and 10,000 committed order transactions. Per-order writes and commits are an observable cost; changing atomicity or duplicate handling needs a separate benchmark/design and is not justified by this single-client baseline alone.

MySQL EXPLAIN ANALYZE on the tested unique order identity and Product lookup reports a constant lookup (`Rows fetched before execution`) using existing uniqueness/indexing. No missing index is demonstrated. The performance fixture contains no inventory; separate correctness tests are the evidence for nonempty inventory preservation.

CSV parsing streams records, but the existing service retains parsed rows and validation grouping up to configured limits. Excel performance is **NOT_MEASURED** in this branch. Multiple-client throughput, database CPU, RSS, connection-pool saturation and p95/p99 are **NOT_MEASURED**.

## F06 — measured N+1 relation reads

The regression uses actual authenticated GET `/api/recommendations?pageSize=N`, real JDBC/Flyway tables and 25 or 100 synthetic candidates sharing one Product group. One candidate has review history. The JDBC wrapper counts actual prepared SELECT execute calls; it does not log parameter values. Three successive requests provide a median latency. Baseline and optimized runs use the same fixture construction. Machine load/warm caches can affect timing; SQL count reduction is the deterministic result.

| Database / candidates | Baseline SELECTs | After SELECTs | Baseline relation SELECTs | After relation SELECTs | Baseline median ms | After median ms |
|---|---:|---:|---:|---:|---:|---:|
| H2 /25 | 82 | 10 | 77 | 5 | 13.379 | 1.956 |
| H2 /100 | 307 | 10 | 302 | 5 | 21.758 | 4.166 |
| MySQL 8.4 /25 | 82 | 10 | 77 | 5 | 22.886 | 5.921 |
| MySQL 8.4 /100 | 307 | 10 | 302 | 5 | 44.410 | 10.506 |

Baseline relation cost is count+base+one item/source/history query per recommendation (`2+3N`). The optimized page uses count+base+one item/source/history query per 500-ID chunk (`2+3×ceil(N/500)`). Tests also exercise 501 selected records and prove two item, two source and two history reads with distinguishable Product/source/history association at the split and Store filters. For an ordinary100-row page there are five relation SELECTs and five unchanged identity/live-eligibility SELECTs.

A second measured generation amplification loaded `app_user` 28 times for 25 created candidates. The failing counter regression proves the issue. Lazy actor reuse reduces this to 4 reads; all initial authorization and StoreScope checks remain active. H2's observed single-run elapsed time changed 107.127ms→7.294ms; this is an illustrative cold/local observation, not a latency guarantee. MySQL generation before/after wall-clock comparison is **NOT_MEASURED**; optimized 25-candidate generation observed 107.728ms. Identity/source lookups and writes remain per candidate.

No API fields, thresholds, limits, ordering, review policy, frozen utility or live eligibility rules are changed. The measured fixes are limited to repository relation hydration and repeated actor identity reads.

## Plans and remaining risks

Actual MySQL EXPLAIN ANALYZE plans for the base list and item/source/history batch queries are retained in the [F06 evidence package](evidence/f06_hardening/MYSQL_EXPLAIN_ANALYZE.json). The fixture has100 candidates and one actual review history row. Existing Store/identity/foreign-key indexes cover these tested access paths; no new index is proposed from an empty or hypothetical query plan.

Live eligibility still performs Product and batch reads for each unique Product group, and frozen source validation reads pattern mappings per pattern. These remain potential costs for diverse/large datasets; they were not proven dominant by the shared-group page fixture. A full 10,000-candidate ranked scope is functionally tested, but its p95/p99, CPU/RAM and concurrent-load behavior are **NOT_MEASURED**. The relation fetch uses bounded 500-ID IN clauses; total result content is still bounded by the existing 10,000-scope cap, not a new paging contract.

Before production acceptance, execute realistic mixed-Product multi-client workloads, p95/p99 latency/throughput, memory/connection-pool pressure and server CPU/IO. Review the F05 comparison-veto ambiguity separately. Local results are **LOCAL_VERIFIED / REVIEW_PENDING**, not GitHub CI or production acceptance.

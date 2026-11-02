# Known limitations and likely reviewer questions (2026-11-04)

## A. Known limitations (state them before they are asked)

| # | Limitation | Evidence / pointer |
|---|---|---|
| 1 | Scheduled mining is **off by default** and not released: `RELEASE_SIGNOFF_PENDING`, no approver recorded; GitHub CI **NOT RUN**; native Linux **NOT RUN** | register, readiness report |
| 2 | The formal Product Owner identity of whoever confirmed D1–D10 is unverified | decision register |
| 3 | Rollback of the old executable after activation is unsafe (it starts on the V19 schema and would run schedules as a human); the plan is roll-forward | V18/V19 report §3–§5 |
| 4 | A frozen (not dead) scheduler instance keeps its occurrence locked until the process is killed; no automatic takeover | readiness report R3 |
| 5 | Reservation uses the JVM clock, leases use the database clock; instances need NTP | readiness report R5 |
| 6 | MySQL `TIMESTAMP` fire times end 2038-01-19 | schema doc |
| 7 | No alerting beyond the stored `failureMessage`, logs and SQL queries (decision D7: no e-mail/SMS) | roll-forward plan §5 step 8 |
| 8 | SPMF is an external GPLv3 jar pinned by SHA-256, run in a separate Java 25 process; kernel-level child cleanup exists on Linux only (macOS = development) | F05 setup docs |
| 9 | Only HUI-Miner and EFIM are implemented (see report review: the reports describe TKQ/FEACP) | `PROJECT_REPORT_CONSISTENCY_REVIEW.md` |
| 10 | F07–F11 (layout, A/B, storefront, dashboards) and Excel import are PLANNED, not implemented; no cancellation of a running mining request exists | UML pages tagged PLANNED |
| 11 | Two instances started together with bootstrap credentials race on the first administrator (pre-existing, unrelated to F05) | native Linux report §7 |
| 12 | Diagrams: F02/F03 ERDs and the Mermaid ERD do not show V14–V19; the F05 ERD covers scheduler tables only; the draw.io files were not opened in the diagrams.net web editor | UML QA report §6 |
| 13 | The Frontend repository is not part of this review package | – |

## B. Likely reviewer questions, with short answers

| Question | Answer (with evidence) |
|---|---|
| Why does the system mine with HUI-Miner and EFIM when the report talks about TKQ and FEACP? | The Backend implements the two SPMF algorithms that Report 2 says the Capstone Project Register approved (HUI-Miner, EFIM) and compares their results (`comparisonStatus`). No TKQ/FEACP code exists. The reports are the open inconsistency — see `PROJECT_REPORT_CONSISTENCY_REVIEW.md`. Do not claim TKQ/FEACP are implemented |
| Why can System Admin not read patterns? | Decision D1: System Admin supervises (snapshots, requests, runs) but business results stay with Store roles; always HTTP 403, tested (`SystemStoreReadPolicyTest`, E2E) |
| What happens if the server dies mid-run? | Nine kill points were tested with real `kill -9`: committed work is recovered by another instance; uncommitted work is rolled back; leases and fencing tokens stop stale owners; zero duplicates/orphans (readiness + native Linux reports, commit `501f980`) |
| How do you stop duplicate scheduled runs? | unique `(schedule_id, scheduled_at)`; deterministic request key unique per Store; row lock on the schedule; lease + fence on the occurrence |
| Why is the scheduler off by default? | Business decision D10 requires release sign-off first; the flag makes activation answer HTTP 503 and keeps the dispatcher from starting. Verified in a real process |
| What is `SKIPPED`? | A missed fire time that a later one superseded (D6). It owns no snapshot, request, run or actor; it is history, never executed |
| Why a fresh F03 snapshot per scheduled run instead of reusing one? | The window ends the day before each fire time; reusing an old snapshot would mine stale data. F03 remains the only way to create a snapshot, the scheduler calls it internally |
| Who is the "scheduler principal"? | A credential-free machine account (`system-scheduler@paspm.invalid`, role `SYSTEM_SCHEDULER`, `INACTIVE`), hidden from user/role APIs; it makes audit show *executor* separately from *creator* and *last editor* (D9) |
| How do F03, F05 and F06 connect? | F03 freezes a READY snapshot (hashes, rows); F05 admits only that frozen input (or its own fresh scheduled snapshot); F06 generates reviewable candidates from COMPLETED F05 runs with frozen evidence; approval never publishes (AD01, AD05, Sequence AD05) |
| Which ERD is current? | The F05 scheduler ERD (new) for V14–V19; F02/F03 ERDs are earlier-iteration references and do not show V14–V19; no single V1–V19 ERD exists yet |
| Why Java 17 and Java 25? | The Backend targets Java 17; the approved SPMF 2.67a classes need Java 25, so mining runs in a separate child process configured by `PASPM_MINING_JAVA_EXECUTABLE` |
| What is not tested? | GitHub CI, native Linux, a MySQL server restart, long network partitions, clock steps; the Frontend |
| What did you not change? | V1–V17, the F02/F03 ERDs, anything for F07–F11, the Frontend |
| Is it released? | No. Sign-off pending, nobody recorded as approver |

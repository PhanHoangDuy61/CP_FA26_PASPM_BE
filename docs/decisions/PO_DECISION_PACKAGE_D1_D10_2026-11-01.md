# F05 — Product Owner decision package D1–D10

Prepared 2026-11-01 for the Product Owner. Companion to the working register [PO_DECISION_REGISTER_F05_2026-11-01.md](PO_DECISION_REGISTER_F05_2026-11-01.md) and the scheduler proposals in [F05_SCHEDULER_POLICY_DECISIONS.md](../reports/F05_SCHEDULER_POLICY_DECISIONS.md).

## How to read this document

| Label | Meaning |
|---|---|
| **CURRENT** | What the code does today. It is an engineering default, **not** an approved policy. |
| **PROPOSAL** | Engineering's suggested answer. It is **not** a decision and changes nothing until you approve it. |
| **DECISION** | Your answer. Every DECISION box below is intentionally blank: **no decision has been taken**. |

Nothing in this package is approved. Approving means filling the DECISION box (decision, approver, date), after which engineering records it in the register, implements what is missing, and re-runs the acceptance evidence. The scheduler enable request stays **HTTP 503** and the dispatcher stays **OFF** until D3–D10 are decided and the activation acceptance of D10 passes.

## Source of truth

`docs/FA26_SE048.pdf` assigns the Data/System Administrator: (1) configure `minUtil` and the active algorithm (HUI-Miner or EFIM); (2) scheduled mining jobs that re-scan transaction data **weekly or monthly**; (3) a benchmark view comparing HUI-Miner and EFIM (run time, RAM). The document contains **no sentence** about System Admin reading Store mining results, choosing a Store, time zones, lookback length, catch-up or retry. D1–D10 therefore fill gaps in the requirements; they are business decisions, not implementation details.

## Summary

| ID | Question in one line | Blocks | Engineering proposal | Needs new work if proposal accepted |
|---|---|---|---|---|
| D1 | May System Admin read Store mining results? | 5 read routes; Benchmark Hub completeness | (b) open `REQUESTS,RUNS`; optionally `SNAPSHOTS`; keep `PATTERNS` closed | none (configuration only) |
| D2 | Where does System Admin get the Store list? | Admin screens | (a) read-only `GET /api/system/stores` | small endpoint + tests |
| D3 | How long a lookback? | Enable | keep complete days ending yesterday; offer 30/60/90 presets | none |
| D4 | Which time zone? | Enable | one approved Store calendar zone, schedule zone derived from it | validation + docs |
| D5 | Monthly day 29–31 | Enable | clamp to last day (current) | none |
| D6 | Catch-up after downtime | Enable | run only the latest missed fire, record the rest as skipped | status + logic + tests |
| D7 | Retry and backoff | Enable | keep 3 attempts, 15 s then 30 s; add alerting | alerting only |
| D8 | Effect of config/schedule edits | Enable | keep "freeze at reservation"; decide cancel-on-disable | optional cancel path |
| D9 | Recorded actor of automatic runs | Enable | dedicated service account | account + migration |
| D10 | What proves the scheduler is ready? | Lifting the 503 | the acceptance list in D10 | evidence runs |

---

## D1 — May SYSTEM_ADMIN read Store mining results?

**Why it matters.** Reading results exposes one Store's mining data to a cross-Store role. The Benchmark Hub requirement needs a `runId`, and the only way to find it is to read the request and its runs.

**CURRENT.** Five read routes exist under `/api/system/mining/stores/{storeId}/…`. They are **refused with HTTP 403 for every role, including System Admin**, unless an operator lists the group in `paspm.mining.system-store-read.routes`. Merchandiser and Store Admin keep their own Store-scoped reads. The benchmark, manual request and configuration routes are not gated.

| Group (config value) | Route(s) it opens |
|---|---|
| `SNAPSHOTS` | `GET …/stores/{storeId}/snapshots` |
| `REQUESTS` | `GET …/requests`, `GET …/requests/{requestId}` (status polling) |
| `RUNS` | `GET …/requests/{requestId}/runs` |
| `PATTERNS` | `GET …/runs/{runId}/patterns` |

| Option | Config value | Effect |
|---|---|---|
| (a) approve all | `SNAPSHOTS,REQUESTS,RUNS,PATTERNS` | full System Admin results screens |
| (b) status and runs only | `REQUESTS,RUNS` (+ `SNAPSHOTS` if you also approve choosing a snapshot) | Benchmark Hub works end to end; patterns remain visible only to the Store's own users |
| (c) reject | empty (today's default) | System Admin is an operator only; benchmark needs a `runId` supplied by a Store user |

**Consequence of doing nothing.** With the gate closed System Admin can submit a manual request and read a benchmark for a known run, but cannot poll status or list runs, so the Benchmark Hub cannot be completed from System Admin routes.

**PROPOSAL.** Option (b) with `REQUESTS,RUNS`, plus `SNAPSHOTS` only if you want System Admin to pick the snapshot for a manual run (otherwise a Merchandiser tells them the snapshot id). Keep `PATTERNS` closed: the benchmark view needs counts and timings, not the pattern list (data minimisation).

**Edge cases.** An approval must cover which roles besides System Admin (none today); whether audit logging of cross-Store reads is required; an unknown Store id answers the same 403 as a known one while a group is closed.

**DECISION (D1):** option ____ · groups opened ____ · approver ____ · date ____

## D2 — Where does System Admin get the list of Stores?

**CURRENT.** No Store-listing endpoint. `GET /api/users` returns `storeId` per user but no Store names; the Frontend would take a typed id.

| Option | Effect |
|---|---|
| (a) add read-only `GET /api/system/stores` (id, name) | reliable Store picker; one small endpoint |
| (b) reuse an admin list "when one exists" | none exists yet; blocks the screen |
| (c) typed `storeId` | error-prone; a wrong id reaches a real Store |

**PROPOSAL.** (a), SYSTEM_ADMIN only, id and name only, paginated. **Needs:** a new endpoint with RBAC and tests (separate small change).

**DECISION (D2):** option ____ · approver ____ · date ____

## D3 — Lookback window

**CURRENT.** Per schedule `lookbackDays` 1–3660; the window is that many **complete** days ending the day before the analysis date, in the schedule's zone; stored on the occurrence.

**Options.** Fixed set (30/60/90) or free range; an upper bound; calendar-aligned weeks/months.

**PROPOSAL.** Keep the model. API accepts 1–3660 (no code change); the UI offers 30/60/90 as presets. Decide separately what an occurrence with too little data should do: today the occurrence fails with a 400-class error (no eligible transaction items, or incomplete score/expiry data) and is not retried; an empty snapshot is never created.

**Edge cases.** New Store with little history; late imports that arrive after the window was frozen (the snapshot is immutable); a window crossing a time-zone change.

**DECISION (D3):** presets ____ · max ____ · min-data rule ____ · approver ____ · date ____

## D4 — Time zone

**CURRENT.** Each schedule stores an IANA zone. The Store itself has **no zone field**; F03/F04 use one system-wide value `paspm.store-time-zone` (default `Asia/Ho_Chi_Minh`). A schedule may therefore be defined in a zone different from the zone used to read transactions. DST gap moves forward; overlap uses the earlier offset.

**Options.** (i) one system zone; (ii) per-schedule zone (today); (iii) per-Store zone (new field).

**PROPOSAL.** (i) now: schedules must use the approved Store calendar zone, validated by the API; revisit (iii) if Stores in several zones are planned. An earlier approval of `Asia/Ho_Chi_Minh` for expiry does not by itself approve this policy.

**Needs.** API validation and documentation; no migration for (i).

**DECISION (D4):** option ____ · zone ____ · approver ____ · date ____

## D5 — Monthly overflow (day 29–31)

**CURRENT.** The missing day clamps to the last day of the month.

**Options.** Clamp (current) · skip the month · restrict to days 1–28.

**PROPOSAL.** Clamp: one run every month, predictable. Document in the UI ("31 → last day").

**DECISION (D5):** option ____ · approver ____ · date ____

## D6 — Catch-up after downtime

**CURRENT.** Every missed fire time becomes its own occurrence (up to 5 reserved per poll); nothing is merged or skipped, so a long outage produces a backlog of consecutive runs.

**Options.** Run all · run only the latest · skip with an alert · maximum catch-up age.

**PROPOSAL.** Run only the most recent missed fire; record older ones as visibly **skipped** in the occurrence history. Limits load after an outage and avoids near-duplicate results. **Needs:** a skipped status, reservation logic change, migration and tests (not implemented).

**Edge cases.** Outage longer than the schedule period; re-enabling after a long pause; two instances recovering at once.

**DECISION (D6):** option ____ · max age ____ · approver ____ · date ____

## D7 — Retry and backoff

**CURRENT.** Retryable failures (HTTP 5xx-class or transient database errors) are attempted up to **3 attempts in total**, with a wait of **15 s after the first and 30 s after the second**; the third failure, or any non-retryable error, is terminal (`FAILED`, `RETRY_EXHAUSTED` when the retry budget ends). *(The working register previously listed a third delay of 60 s; there is no fourth attempt, so that was wrong and has been corrected.)*

**Options.** Attempt count and delays; whether a human must re-queue; alerting.

**PROPOSAL.** Keep 3 attempts and 15/30 s for **preparation** transients. Define who is alerted on a terminal failure and whether a manual re-run is the remedy. **Needs:** alerting/monitoring only.

**DECISION (D7):** attempts ____ · delays ____ · alerting ____ · approver ____ · date ____

## D8 — Effect of changing configuration or a schedule

**CURRENT.** An occurrence freezes the schedule version and the mining-config version when it is reserved; later edits affect future occurrences only. Disabling stops future reservations; an already reserved occurrence still runs (a changed or disabled schedule version is rejected at preparation).

**Options.** Freeze (current) vs apply latest at execution; whether disabling cancels pending occurrences.

**PROPOSAL.** Keep freeze (auditability). Decide explicitly whether "disable" must also cancel a reserved but unstarted occurrence. **Needs:** only if cancel is chosen (new terminal status).

**DECISION (D8):** freeze/latest ____ · cancel on disable ____ · approver ____ · date ____

## D9 — Who is the recorded actor of an automatic run?

**CURRENT.** The schedule's last updater (`updated_by`), which must still be an active System Admin; deactivating that person stops their schedules' occurrences.

**Options.** That user · the schedule creator · a dedicated service account.

**PROPOSAL.** A dedicated service account for automatic runs, so audit shows "scheduler" and a leaver does not silently stop jobs. **Needs:** an account type or seeded account, a migration or configuration, tests.

**DECISION (D9):** option ____ · approver ____ · date ____

## D10 — Activation acceptance

**CURRENT.** Enable requests return 503 by design; the dispatcher is off unless `paspm.mining.schedule-dispatch-enabled=true`.

**PROPOSAL (acceptance list to lift the 503).**
1. D3–D9 decided and implemented where they require work.
2. Scheduler crash/lease acceptance repeated on the final image and the merged commit (multi-instance, native Linux).
3. Full verification (`scripts/verify-option-b-macos.sh --run` or CI equivalent) green on the release commit, with skipped suites listed.
4. One supervised activation in a non-production environment with real SPMF on MySQL 8.4 and a monitored run.
5. Product Owner sign-off recorded in the register with date and approver.

**DECISION (D10):** acceptance list accepted / amended ____ · approver ____ · date ____

---

## After you decide

1. Fill the DECISION boxes (decision, approver, date).
2. Engineering updates the register, `F05_MINING_API.md`, the Option B handoff and the OpenAPI, implements the work marked "Needs", and re-runs verification.
3. For D1 the change is a deployment setting (`paspm.mining.system-store-read.routes` / `PASPM_MINING_SYSTEM_STORE_READ_ROUTES`) recorded with the approval, not a code edit.

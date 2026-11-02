# F05 Scheduled Mining — D8 / D9 / D10 review, acceptance and release-readiness record (2026-11-02)

This report is evidence, not an approval. It records what was run, on which commit, in which environment, and what is still open. Companion reports: [native Linux acceptance](F05_NATIVE_LINUX_ACCEPTANCE_2026-11-02.md) · [V18/V19 schema and roll-forward](F05_V18_V19_SCHEMA_ROLLFORWARD_2026-11-02.md) · [decision register](../decisions/PO_DECISION_REGISTER_F05_2026-11-01.md) · [acceptance matrix](F05_SCHEDULER_ACCEPTANCE_MATRIX.md).

## 0. Result labels (honest, per item)

| Label | Meaning here | State |
|---|---|---|
| `BUSINESS_CHOICE_CONFIRMED` | the business choices D1–D10 (D3 amended) were stated in the working conversation on 2026-11-02 | **yes** — no approver name or signature recorded or invented |
| `TECHNICALLY_VERIFIED` | implemented and tested by the automated and process-level evidence below | **yes, locally** (macOS arm64 host; Docker Linux/amd64 under emulation as a non-root user) |
| `NATIVE_LINUX_VERIFIED` | the evidence above repeated on a real Linux kernel | **no — `NATIVE_LINUX_NOT_RUN`** |
| `CI_NOT_RUN` | GitHub CI on this branch | **branch never pushed** |
| `RELEASE_SIGNOFF_PENDING` | an authorised human has not approved the release | **pending** |
| `RELEASE_APPROVED` | an authorised human approved the release | **not granted; nobody is recorded as approver** |

## 1. Source commits

| Role | Commit |
|---|---|
| Base | `origin/dev` `5c9d3dc` |
| Option B implementation (D1–D10) | `ce419d0` … `89e6ee5` (12 commits, see the register) |
| This task, **production code** | `28fac97` — the dispatcher logs a catch-up (WARN with the number of skipped fire times) and each reservation (INFO). No other production file changed |
| This task, tests | `884a83c` (crash-point matrix, natural-lease and stale-owner tests, upgrade/compatibility class, D9 credential-flow tests, partial-V18 characterisation) |
| This task, scripts/docs | `9dfe2d9`, `5b5f4f4`, `bc259a4` and later documentation commits |

**Tested source commit** for every run below: the source tree of `9dfe2d9` (identical in `5b5f4f4`; only `docs/`, `scripts/verify-native-linux.sh` and `docs/diagrams/` differ in later commits). The earlier `e8bd0fe`-based Linux run in the acceptance matrix is superseded by the Linux run in this task. If the commit hash differs from a report's text, compare with `git diff <tested>..HEAD -- src pom.xml` (empty).

## 2. D8 — schedule edits and disabling

Approved behaviour: no new work is reserved after disabling; versions are frozen for reserved work; a Mining Request that is already committed finishes; a reserved occurrence **without** a committed request may end `FAILED/HTTP_409` when its schedule was disabled or edited before the preparation began. **It is not documented, and was not found, that every reserved occurrence completes.**

| Lifecycle boundary | Observed behaviour | Test (result) |
|---|---|---|
| Disabled **before** reservation | no occurrence is created, cursor cleared | `MiningScheduleChangeSemanticsTest` (PASS) |
| Disable racing the reservation (12 rounds, concurrent PUT vs reserve) | either a complete reservation or none; never a half-state; no run after the disable commit | `MiningScheduleChangeSemanticsTest` (PASS) |
| Disabled **after** reservation, **before** preparation | occurrence `FAILED/HTTP_409`, "Schedule is disabled or has changed"; no snapshot, no request | `MiningOccurrenceFailurePathsTest` (PASS) |
| Edited after reservation, before preparation | same `FAILED/HTTP_409` | `MiningOccurrenceFailurePathsTest` (PASS) |
| Disabled **after the Mining Request committed** | the request runs to `COMPLETED` with its frozen configuration; the occurrence is reconciled `COMPLETED` | `MiningIntegrationTest` + real two-process `…killedAfterTheRequestCommitted…` (the schedule is disabled over HTTP while the dead owner's request is recovered; PASS) |
| Schedule **updated while the occurrence is being prepared** | the PUT **waits** on the schedule row lock held by the preparation (verified: still blocked after 3 s); after the preparation commits the PUT succeeds (version 2→3). The occurrence completes with the **frozen** schedule version and the frozen date range; the edit applies to future occurrences | new `…updatingTheScheduleWhileAnOccurrenceIsBeingPrepared…` (real Backend, MySQL; PASS) |
| Mining configuration (policy) changed during execution | the request keeps its `config_version` and threshold; the occurrence keeps the version it reserved | `MiningIntegrationTest`, `MiningScheduleChangeSemanticsTest` (PASS) |
| Concurrent disable and dispatch | see the 12-round race above; a replay of an occurrence that already owns its request re-links it even while the schedule is disabled | `MiningIntegrationTest` (PASS) |

The distinction a reader needs: an edit that **arrives before** the preparation transaction takes the schedule row lock fails the occurrence (`HTTP_409`); an edit that **arrives while** the preparation holds the lock waits and cannot invalidate it; after the request commits nothing the administrator does stops it.

## 3. D9 — scheduler service identity

| Requirement | Evidence | Result |
|---|---|---|
| Automatic executions use the dedicated identity | occurrence `actor_id`, `utility_snapshot.created_by`, `mining_request.requested_by` = principal (`MiningScheduleReservationTest`, real HTTP + MySQL E2E) | PASS |
| Independent of the last human editor | `MiningOccurrenceFailurePathsTest` (deactivated creator/updater do not block); E2E (editor deactivated before the run) | PASS |
| Disabling a human admin does not stop a valid schedule | same | PASS |
| No password, no public login | `SchedulerPrincipalHttpTest`: login → 4xx; **new**: password-reset request + confirm and registration give it no challenge, no password, no verified e-mail; status stays `INACTIVE` | PASS |
| `SYSTEM_ADMIN` authorisation cannot be reached through the machine identity | **new**: a token minted internally for the principal is refused (4xx) on five protected routes and on schedule creation | PASS |
| Hidden from `/api/users`, `/api/roles`; immutable | `SchedulerPrincipalHttpTest`; **new** real-process check: user list, role list and `GET /api/users/{id}` (404) on a fresh MySQL schema | PASS |
| Audit separates creator, last updater and executor | `created_by`/`updated_by` (human) vs `actor_id`/`created_by`/`requested_by` (principal) in one E2E run | PASS |
| Stable across restart and migration | **new**: kill both Backends, restart → same `user_id`; V17→V19 upgrade adds exactly one row and leaves existing users untouched | PASS |
| No duplicate identity under concurrent startup | **new**: two Backends started together on an empty schema → 19 history rows, one role, one principal. Also `role.code` and `app_user.email` are `UNIQUE` | PASS |
| Fresh **and** upgraded databases | fresh: above; upgraded: `…upgradeOfAV17SchemaWithInFlightSchedulerRows…` and the script's "upgrade from origin/dev" stage | PASS |
| Legacy rows reserved before D9 with a human actor | not executed as that human: `FAILED/HTTP_403`, visible message; unowned `PROCESSING` rows untouched | PASS (documented) |

## 4. D10 — activation and release safety

### Default environment (`paspm.mining.schedule-dispatch-enabled` not set, i.e. `false`)

| Expectation | Evidence | Result |
|---|---|---|
| Dispatcher inactive; enabling answers 503 with no write | `ScheduledMiningDisabledByDefaultTest`, `MiningScheduleAdministrationHttpTest`; **new real Backend process with the shipped configuration**: `PUT enabled=true` → 503, `schedule_version` unchanged | PASS |
| No automatic occurrence, snapshot or request | the same real process with an **enabled schedule 14 days overdue** inserted directly in the database: after 20 s (four polls would have fired) 0 occurrences, 0 snapshots, 0 requests, and no log line from a dispatcher thread | PASS |
| Existing rows cannot bypass the safeguard | same | PASS |
| Manual F05 Mining still works | `MiningIntegrationTest` (26 tests, default flag) ; the real process also serves `GET /api/system/mining/config` 200 | PASS |
| A process that would schedule under another Store zone refuses to start | **new**: flag on + `paspm.store-time-zone=UTC` → the JVM exits with code 1 and logs `requires paspm.store-time-zone=Asia/Ho_Chi_Minh`; flag off + `UTC` starts normally | PASS |

### Explicit acceptance environment (flag `true`)

| Expectation | Evidence | Result |
|---|---|---|
| Authenticated System Admin activates a valid schedule; other roles are rejected | `MiningScheduledActivationE2EMySqlTest` (real login tokens over a socket), `F03F05RbacMatrixTest` (32 routes) | PASS |
| Weekly and monthly execute | E2E 3/3 (the monthly case also records two `SKIPPED` months) | PASS |
| Fresh immutable F03 snapshot; HUI-Miner and EFIM through real SPMF; results and history persist | E2E: 8 patterns per run, `comparisonStatus=MATCH`, history API | PASS |
| `SKIPPED`, `FAILED`, `COMPLETED` accurate | `MiningScheduleCatchUpTest`, failure-path tests, E2E | PASS |
| Restart and multi-instance recovery | nine real `kill -9` crash points + natural lease expiry + stale owner (see the [native Linux report](F05_NATIVE_LINUX_ACCEPTANCE_2026-11-02.md)) | PASS on macOS and on emulated Linux (non-root), **not native** |
| The flag is never on by default | grep: only the property default `false` in `MiningOccurrenceDispatcher`, `MiningScheduleService`, `ScheduledMiningZoneGuard`; the new tests pass it explicitly on the command line | verified |

## 5. Final review findings

Method: CodeGraph (`SchedulerPrincipal` has 10 callers in four production files — reservation, snapshot, request, service — all covered by the new and existing tests), reading `MiningOccurrenceDispatcher`, `MiningOccurrenceLease`, `MiningOccurrenceProcessor`, `MiningScheduledSnapshot`, `MiningScheduledRequest`, `MiningScheduleReservation`, `MiningScheduleService`, `SchedulerPrincipal`, `ScheduledMiningZoneGuard` and V18/V19, then process-level tests for what reading cannot prove.

| # | Finding | Severity | Resolution |
|---|---|---|---|
| R1 | Catch-up and reservation produced **no log line**; skipping weeks of fire times was visible only in the history API | medium (observability) | **fixed** in `28fac97` with a regression test written first (red → green) |
| R2 | The startup refusal in `ScheduledMiningZoneGuard` was proven only by calling its static check | low | proven with a real JVM (exit code 1, reason logged) |
| R3 | A stalled (not dead) owner keeps its transaction and therefore the occurrence row lock. Other instances cannot take the occurrence over after the lease expires; their claim attempts hit the 50 s InnoDB lock wait and log a WARN each poll. Observed: stalled 385 s, takeover only after the owner resumed, the owner's commit was **refused by the fence** (`Occurrence lease expired before dispatch commit`), the other instance produced the only result. A frozen-but-never-resuming JVM therefore stalls its occurrence until the process is killed or MySQL closes the connection | operational | safe (no duplicate, no corruption); documented in the monitoring query ("`PROCESSING` with `lease_until` ten minutes in the past → kill the stuck instance") |
| R4 | `enqueue` runs before the surrounding occurrence transaction commits; the worker's claim then waits for that row lock and sees nothing if the transaction rolls back | informational | by design, no data effect; confirmed by the stale-owner test (the rolled-back request never ran) |
| R5 | The reservation cursor compares against the JVM clock (`Instant.now()`), leases against the database clock | low | instances need NTP; a skewed JVM can reserve a fire time early or late by the skew; documented |
| R6 | Old executable accepted by Flyway on the new schema | operational | see the roll-forward report: rollback unsafe, roll forward |
| R7 | Schema: no defect needing V20 | – | see the schema review |
| R8 | Spring self-invocation: the `reserve`/`reserveOne` proxy issue found earlier is covered by `MiningScheduleReservationTest`; the other transactional entry points (`MiningOccurrenceProcessor.process`, lease operations) are called through the Spring proxy or use `TransactionTemplate` | – | no other instance found |
| R9 | Cross-Store isolation | – | composite foreign keys keep every child reference inside one Store; E2E: foreign Store 404 on snapshot/request/run reads, patterns closed to System Admin, Store-scoped patterns 8 for the Store's Merchandiser |

## 6. Residual risks and open issues

1. **GitHub CI has not run** (`CI_NOT_RUN`). The multi-process, E2E and upgrade suites are opt-in and are not in a CI group; the existing CI integration job additionally needs `PASPM_CI_SPMF_ARTIFACT_URL` and fails on skipped tests (five inherited clone-API skips on `dev`).
2. **Native Linux not run.** Parent-death behaviour of the SPMF launcher was exercised under Docker amd64 emulation on Apple Silicon (VM kernel `linuxkit`); a real host may differ. `scripts/verify-native-linux.sh` is ready and refuses to claim native evidence from this environment.
3. Hung-owner behaviour (R3) and NTP dependence (R5).
4. MySQL `TIMESTAMP` horizon 2038-01-19 for fire times (pre-existing).
5. A V18 that stops half way needs a restore, not a hand repair (reproduced).
6. Rollback of the executable after activation is unsafe (measured).
7. No e-mail/SMS/webhook alert exists by decision D7: operators must watch the history, the queries in the roll-forward plan and the log.
8. The 27-page UML pack and the Draw.io ERDs do not yet show V18/V19 or the new states; the focused Mermaid ERD does. That synchronisation is a separate task (`UML_PENDING` for the new schema facts).
9. The identity of the person who confirmed D1–D10 as "Product Owner" is unverified.

## 7. Release sign-off record

Nothing below is filled in on anyone's behalf.

| Item | Status | Evidence / note |
|---|---|---|
| Business decisions D1–D10 | `BUSINESS_CHOICE_CONFIRMED` | working-conversation instructions of 2026-11-02, D3 amended the same day |
| Approval authority verification | **NOT VERIFIED** | whether the confirmer is the formal Product Owner / authorised release approver must be checked by the project |
| Tested Backend commit | source tree of `9dfe2d9` (see §1) | branch not pushed |
| Technical verification | `TECHNICALLY_VERIFIED` (local) | [final regression](#8-final-regression) |
| Native Linux acceptance | `NATIVE_LINUX_NOT_RUN` | [report](F05_NATIVE_LINUX_ACCEPTANCE_2026-11-02.md) |
| V18/V19 migration review | `MIGRATION_REVIEWED` · `ROLLFORWARD_DOCUMENTED` | [report](F05_V18_V19_SCHEMA_ROLLFORWARD_2026-11-02.md) |
| D8 / D9 / D10 acceptance | `D8_D9_D10_VERIFIED` (local) | §2–§4 |
| GitHub CI | `CI_NOT_RUN` | |
| Open issues and risks | §6 | |
| **Release decision** | **`RELEASE_SIGNOFF_PENDING`** | |
| Release approver (name, role) | _________________________ | to be completed by the authorised approver |
| Approval decision | ☐ approved  ☐ rejected  ☐ approved with conditions | |
| Conditions | _________________________ | |
| Approval date | ____ / ____ / ________ | |

`RELEASE_APPROVED` may be written only after an authorised human has completed the last four rows and the CI and native-Linux rows are green.

<!--FINAL-REGRESSION-->

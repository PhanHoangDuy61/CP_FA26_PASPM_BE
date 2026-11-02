# F05 Flyway V18/V19 — migration review, compatibility matrix and roll-forward plan (2026-11-02)

Status: **MIGRATION_REVIEWED · ROLLFORWARD_DOCUMENTED · LOCAL_VERIFIED** (MySQL 8.4.13 on macOS arm64 + Docker; H2). `CI_NOT_RUN`. No deployment was performed and none is authorised. Schema reference and ERD: [F05_SCHEDULER_SCHEMA_V14_V19.md](../architecture/F05_SCHEDULER_SCHEMA_V14_V19.md).

Source state: Backend branch `Khoi/feat/f05-scheduled-mining-option-b-completion`; the code reviewed is `89e6ee5` plus the local commits listed in the [release-readiness report](F05_D8_D9_D10_RELEASE_READINESS_2026-11-02.md). **No migration file was added or changed in this task** (V18 and V19 are exactly as committed in `f86a29f`; V1–V17 are byte-identical to `origin/dev`).

## 1. Audit of what V18 and V19 change

### V18 `scheduler_skipped_status_and_failure_visibility` (D6, D7)

| Statement | Table.column | Effect | Existing rows | Reviewed |
|---|---|---|---|---|
| `DROP CONSTRAINT ck_occurrence_status` + `ADD CONSTRAINT … CHECK(status IN (…,'SKIPPED'))` | `mining_schedule_occurrence.status` | adds the value `SKIPPED` | all existing values stay valid | OK. Brief metadata lock; the occurrence table is small. The window between the two statements has no status check, which is harmless during a stopped-Scheduler migration |
| `ADD skip_reason VARCHAR(60) NULL` | same | reason for a skipped fire time | `NULL` | OK. The only value written is 37 characters |
| `ADD failure_message VARCHAR(500) NULL` | same | administrator-visible final cause | `NULL` | OK. Writer clips to 500; the only copied source (`mining_run.failure_message`) is 250 characters, so a strict-mode "data too long" cannot stop reconciliation |
| `ADD CONSTRAINT ck_occurrence_skip_reason CHECK((status='SKIPPED' AND skip_reason IS NOT NULL) OR (status<>'SKIPPED' AND skip_reason IS NULL))` | same | a reason exactly when skipped | every existing row has `skip_reason NULL` and `status<>'SKIPPED'` → satisfied | OK. `status` is `NOT NULL`, so the check has no three-valued-logic hole |

No new index, no foreign key, no default change. `SKIPPED` rows reuse `uq_schedule_occurrence (schedule_id, scheduled_at)` and `uq_schedule_request_key (store_id, request_key)`: the deterministic key is generated for skipped fire times too, so a skipped slot can never be reserved again.

### V19 `system_scheduler_principal` (D9)

| Statement | Effect | Reviewed |
|---|---|---|
| `INSERT INTO role` `SYSTEM_SCHEDULER` | one new role; `role.code` is `UNIQUE` | OK. Not assignable (`UserRepository.findRoleId` refuses it), excluded from `/api/roles` |
| `INSERT INTO app_user … 'system-scheduler@paspm.invalid', status 'INACTIVE'` | one machine account; `app_user.email` is `UNIQUE`; `password_hash` and `email_verified_at` stay `NULL`; no `external_identity` | OK. Unloginable; password-reset (needs `ACTIVE`+password) and registration (needs `PENDING_VERIFICATION`) cannot issue it a challenge; hidden from `/api/users`; a token minted for it authorises nothing (all verified, see §3) |

Concurrent first start cannot create a second principal: Flyway serialises the migration and `role.code`/`app_user.email` are unique.

### Cross-checks requested

| Topic | Finding |
|---|---|
| Flyway ordering and checksum stability | V1–V17 unchanged (`git diff origin/dev HEAD -- src/main/resources/db/migration` = V18, V19 only); `flyway validate` after the upgrade passes; history checksums of V1–V17 identical before/after (`MiningMigrationMySqlTest`, verification script stage "upgrade") |
| Which decisions depend on the schema | D6: `SKIPPED`, `skip_reason`, `ck_occurrence_skip_reason`. D7: `failure_message`. D9: role + user row, existing `actor_id`/`requested_by`/`created_by` foreign keys. D3, D4, D5, D8, D10 need **no** schema change |
| Fencing / uniqueness | unchanged from V16/V17; the V18 `SKIPPED` rows take no lease and are never claimed |
| Audit relationships | `created_by`/`updated_by` stay human; `actor_id`, `utility_snapshot.created_by`, `mining_request.requested_by` = principal for scheduled runs; asserted over real HTTP + MySQL in the E2E test |
| Historical data | see §2 scenario B and the schema document |

### Findings

| # | Finding | Severity | Action |
|---|---|---|---|
| F1 | No schema defect that needs a forward migration. Candidate hardening (CHECKs that a `SKIPPED` row has no `snapshot_id`/`request_id`/`actor_id`; `UNIQUE (store_id, request_id)` and `UNIQUE (store_id, snapshot_id)` on the occurrence) would be defence in depth for invariants that the code and the existing unique request key already enforce and tests pin | none | **No V20 added.** Recorded as optional hardening in the schema document |
| F2 | MySQL DDL is not transactional: a V18 that stops half way leaves a partly changed schema and a failed history row. Reproduced (`MiningMigrationMySqlTest.aV18ThatFailsHalfWayLeaves…`): statements before the failure stay applied, V19 is never applied, and the next `migrate` is refused | operational | restore from the verified backup (§5); never hand-edit `flyway_schema_history` |
| F3 | **The old executable does not refuse the new schema.** Flyway's default `ignoreMigrationPatterns=*:future` lets `origin/dev` start against a V19 database. Measured in §2 scenario C | operational | rollback of the executable is **unsafe** once scheduling was ever activated; use roll-forward (§5, §6) |
| F4 | `next_fire_at` and `scheduled_at` are MySQL `TIMESTAMP` (limit 2038-01-19). Pre-existing V14 design, not changed by V18/V19 | low, long horizon | documented as a known limit; own migration if ever needed |
| F5 | A pre-D9 `PENDING` occurrence owned by a human is not executed as that human; it ends `FAILED/HTTP_403` with a visible message. Dev could never activate scheduling (HTTP 503), so such rows exist only if inserted by other means | low | tested (§2 scenario B); documented |

## 2. Fresh and upgrade evidence (real MySQL 8.4.13 and real Backend processes)

All rows below are `LOCAL_VERIFIED`: macOS arm64, JDK 17, Java 25 SPMF child, disposable schemas. Evidence files: `outputs/verification-evidence/sched-p2/` (not committed; names in the readiness report).

| Scenario | Test | Result |
|---|---|---|
| Fresh V1–V19, once each | verification script "flyway fresh"; `MiningSchedulerUpgradeCompatibilityMySqlTest.twoBackendsStartingTogether…` | PASS: 19 successful history rows, 19 distinct versions |
| **A.** Two Backends start together on an empty schema | same test | PASS: both up, **one** principal, one role, principal hidden from `/api/users`, `/api/roles` (list) and 404 on `/api/users/{id}`; after killing both and restarting: same principal id, history still 19 rows |
| Upgrade to V19 with seeded data: from V12 (verification script stage "flyway.upgrade": V1–V12 plus seeded rows, then V13–V19, earlier history rows and checksums unchanged) and from `origin/dev`'s actual V17 (`MiningMigrationMySqlTest.upgradeFromV17…`) | PASS: for V17, 2 migrations executed, `validate` passes, old rows byte-identical, existing users untouched, `lookback_days=3660` kept, `SKIPPED` rows storable, the three invalid `SKIPPED` shapes rejected by the check |
| **B.** V17 schema with in-flight scheduler rows, then the **new Backend** with the dispatcher on | `…upgradeOfAV17SchemaWithInFlightSchedulerRowsIsHandledSafelyByTheNewBackend` | PASS: `DISPATCHED`+committed request → `COMPLETED` by reconciliation; `PENDING` owned by a human → `FAILED/HTTP_403` with message, no snapshot/request; `PROCESSING` without owner → untouched; `FAILED`/`COMPLETED` history unchanged; no new request or snapshot; one principal; history shows the cause over HTTP |
| Restart after migration | same + `…twoBackends…` | PASS |
| `SKIPPED` persistence and retrieval | `MiningScheduleCatchUpTest`, E2E, multi-JVM, `…characterizeTheOldBackend…` (history API lists a `SKIPPED` row) | PASS |
| Partly applied V18 (§1 F2) | `MiningMigrationMySqlTest.aV18ThatFailsHalfWayLeaves…` | PASS (characterisation) |
| Existing F01–F06 | full default suite and opt-in suites, see the readiness report | see report |
| Restore mechanics | `mysqldump --single-transaction --routines --triggers` into a new schema, `CHECKSUM TABLE` on six tables | all identical (rehearsal of the mechanics only; the operator's approved backup tool and retention remain theirs) |

### C. Old executable on the new schema (measured, not assumed)

`origin/dev` `5c9d3dc` built to a jar and started against a V19 schema (`…characterizeTheOldBackendRunningOnTheNewSchema`):

| Observation | Result |
|---|---|
| Does it start? | **Yes** (Flyway ignores applied-but-unknown "future" migrations) |
| Occurrence history API with a `SKIPPED` row present | `200`, row listed (the API passes the status string through) |
| `/api/users` and `/api/roles` | **expose the scheduler principal** (the old code has no D9 hiding or immutability) |
| Dispatcher switched on, one missed fire | the old code reserves an occurrence whose `actor_id` is the **human** `updated_by`, not the principal — D9 is bypassed; D3/D4/D6/D7 do not exist in that code |

## 3. Compatibility matrix

| Backend \ Schema | Old schema (V17, `origin/dev`) | New schema (V19) |
|---|---|---|
| **Old Backend** (`origin/dev` `5c9d3dc`) | **Supported.** Today's `dev`: activation HTTP 503, dispatcher off by default | **UNSUPPORTED — rollback of the executable is unsafe.** It starts (measured) but exposes/allows editing the machine principal and, if its dispatcher is on, runs schedules as the human editor and ignores D3/D4/D6/D7. With the default (dispatcher off) and no active schedules the practical damage is the principal exposure only; do not rely on that |
| **New Backend** (this branch) | **Supported by roll-forward.** Starting the new Backend runs Flyway V18+V19 (measured, also with in-flight rows and two simultaneous starts). Prefer to migrate with the dispatcher off and one instance, then start the rest | **Supported.** Target state |

Mixed fleets (old and new instances on the new schema at the same time) are unsupported: stop every old instance before the first new instance migrates.

## 4. Why rollback to the old executable is not offered

1. The old code does not understand D9: after V19 it would show and could alter the machine account, and with its dispatcher on it would again run as a human administrator.
2. It has no `SKIPPED` semantics: skipped slots stay in the table; the old catch-up would create occurrences for the *following* fire times as if nothing had been decided.
3. Making the old version "work" would mean deleting or rewriting `SKIPPED` rows or removing V19's data. **That is not recommended and not provided**: it destroys the audit trail of what D6 decided not to run.

Therefore: **roll forward to a corrective release**; or, if the data itself is damaged, restore the verified backup and accept the loss since the backup.

## 5. Safe roll-forward plan (no step is performed by this task)

Pre-conditions: the release has been approved (see the readiness report; today `RELEASE_SIGNOFF_PENDING`), GitHub CI is green on the exact commit, `PASPM_CI_SPMF_ARTIFACT_URL` is configured, native-Linux evidence exists for the exact commit.

1. **Back up** with the operator's approved procedure, then **prove** it restores: restore into a scratch schema and compare `CHECKSUM TABLE` for at least `app_user`, `role`, `mining_schedule`, `mining_schedule_occurrence`, `mining_request`, `flyway_schema_history`. Record who verified and when. (`mysqldump --single-transaction --routines --triggers` was rehearsed on a disposable schema.)
2. **Keep scheduling off.** Do not set `paspm.mining.schedule-dispatch-enabled=true` anywhere. Activation answers HTTP 503 and no background thread starts.
3. **Check for unsafe in-flight state before migrating** (read-only):
   ```sql
   SELECT status, COUNT(*) FROM mining_schedule_occurrence GROUP BY status;
   SELECT occurrence_id, status, lease_owner, lease_until FROM mining_schedule_occurrence WHERE status IN ('PENDING','PROCESSING');
   SELECT request_id, status, lease_owner, lease_until FROM mining_request WHERE status IN ('PENDING','RUNNING');
   ```
   Expected on a database that never activated scheduling: no rows. Rows `PROCESSING` with `lease_owner IS NULL` are left untouched by the new code and need a human decision; do not delete them.
4. **Stop all old instances**, then apply Flyway by starting **one** new instance (its startup runs V18 and V19; simultaneous starters are safe but a single migrator makes the log unambiguous). Watch for Flyway errors; on a failed V18 do not repair by hand: restore the backup (finding F2).
5. **Verify the principal and the schedule data:**
   ```sql
   SELECT COUNT(*) FROM app_user u JOIN role r ON r.role_id=u.role_id
    WHERE r.code='SYSTEM_SCHEDULER' AND u.email='system-scheduler@paspm.invalid' AND u.status='INACTIVE' AND u.password_hash IS NULL;   -- must be 1
   SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;                                            -- 19 and 18, success=1
   SELECT COUNT(*) FROM mining_schedule WHERE enabled = TRUE;                                                                            -- review each row
   ```
   Over HTTP (System Admin): `/api/users` and `/api/roles` must not list the principal; `GET /api/system/mining/schedules` works; enabling answers 503 while the flag is off.
6. **Lease and reconciliation behaviour** is exercised with the flag **off**: nothing runs. It is exercised in the acceptance environment, not in production, by the opt-in suites listed in the readiness report.
7. **Enable only through the approved activation control**, in a separate, explicitly approved change: set the flag to `true` **and** keep `paspm.store-time-zone=Asia/Ho_Chi_Minh` (a different zone makes the process refuse to start), then activate schedules one at a time as System Admin. This is decision D10 and needs the sign-off record.
8. **Monitor** the first occurrences:
   ```sql
   SELECT status, COUNT(*) FROM mining_schedule_occurrence WHERE created_at > NOW() - INTERVAL 1 DAY GROUP BY status;
   SELECT occurrence_id, schedule_id, failure_code, failure_message FROM mining_schedule_occurrence WHERE status='FAILED' AND updated_at > NOW() - INTERVAL 1 DAY;
   SELECT occurrence_id, attempt_count, lease_until FROM mining_schedule_occurrence WHERE status='PROCESSING' AND lease_until < NOW() - INTERVAL 10 MINUTE;   -- stuck owner
   SELECT occurrence_id, request_id FROM mining_schedule_occurrence WHERE status='DISPATCHED' AND updated_at < NOW() - INTERVAL 1 HOUR;                   -- request not finishing
   ```
   Application log (WARN/ERROR, logger `vn.edu.fpt.paspm.mining`): `Scheduled mining skipped N older missed fire time(s)`, `Scheduled occurrence … failed permanently`, `Scheduled occurrence dispatch failed; next poll will retry`.
9. **Stop procedure** if something is wrong: set the flag back to `false` and restart (no new reservations, no dispatching, activation 503); disable individual schedules through the API (allowed with the flag off); committed Mining Requests keep running because the worker does not depend on the flag. Then fix forward.

## 6. Corrective-release and restore rules

- A corrective release must be a **new** version number higher than V19. V18 and V19 are never edited once applied in a shared environment.
- Restore of the backup is for data damage only; it discards everything written after the backup, including `SKIPPED` history and finished runs.
- Never run `UPDATE`/`DELETE` on `mining_schedule_occurrence` to make an older executable accept the table.

## 7. Not covered

Native Linux and GitHub CI are `NOT_RUN`; the production backup tool, monitoring stack and alert routing belong to the operators; the approved release approver has not signed (see the readiness report).

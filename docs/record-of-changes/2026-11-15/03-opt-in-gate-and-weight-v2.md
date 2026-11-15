# ROC-20261115-003 — Checkpoint 1 approval and opt-in verification gate

| Field | Value |
|---|---|
| Date / timezone | 2026-11-15 / Asia/Ho_Chi_Minh |
| Branch / base | Khoi/feat/f05-progress-f03-weight-v2 / 8f94f94 |
| Status | Opt-in gate PASS locally; local commits authorized; Phase 2 next |
| Release | UNRELEASED; no new push/merge/force/deploy authorization |

## Owner decisions
Checkpoint 1 approved. Before Phase 2, run the full opt-in suites (MySQL, multi-JVM lease/recovery, SPMF), report pass/fail/skip and all not-executed cases. Three proposed local commits are authorized only if verification passes. Then continue Phases 2–6 and stop at Checkpoint 2.

Keep rejecting new Merchandiser requests whose frozen lambda exceeds the current maximum. Explicit message: `The lambda policy changed: this snapshot exceeds the current lambda maximum. Create a new snapshot within the current permitted range before submitting mining.` The targeted API test now asserts that exact text. Idempotent replay and System Admin behavior remain unchanged.

F07/F08 merge/push 8f94f94 was authorized by the user's merge instruction and their separate reply “Cho phép push riêng merge F07/F08 lên dev”. That approval does not authorize any further publication.

## Environment and execution
New disposable mysql:8.4 container, actual server 8.4.13, localhost port 60655, tmpfs database storage. No source/shared database used.

The Docker host-network client probe from verify-option-b-macos.sh failed with connection refused. A probe inside the test container network succeeded. No Docker global setting changed. Native mysql/mysqldump 8.4.11 already installed on the macOS host are used for backup/restore tests, retaining the script's Maven arguments, temporary directories and restore provisioner. This is an explicitly recorded tooling adaptation, not a claim that the unmodified shell script passed.

Full opt-in command uses Java 17 and `clean verify`, `-Dpaspm.test.mysql=true`, `-Dpaspm.test.java17-backend=true`, explicit Java 17/Java 25/SPMF paths, isolated tmp/avatar directories and root ERROR logging exactly as in the script. Its flags do not enable multi-JVM. The final rerun adds `-Dpaspm.test.multijvm=true`, `-Dpaspm.test.natural-lease=true` and the verified baseline executable to the same full-suite command.

Baseline executable from exact commit 8f94f94 was built with Java 17 `-DskipTests package` in a separate extracted tree (BUILD SUCCESS). This is build evidence only; the executable will be supplied for the optional old-executable compatibility check.

## Verification results
First full opt-in: 631 discovered, 604 executed, 598 PASS, 6 FAIL, 0 ERROR, 27 SKIPPED. Six migration assertions still expected V21 (or its fixed delta), although the resolved schema is V24. No migration SQL or data-preservation assertions changed. Tests now check the resolved pending count, successful validation and no pending migrations. The same stale version count in the multi-JVM upgrade test was corrected before its execution.

Focused migration retest: 11 executed, 11 PASS, 0 FAIL/ERROR/SKIP; BUILD SUCCESS. The first full opt-in plus multi-JVM/natural-lease rerun was INTERRUPTED: 368 completed test cases, all passing in completed XML reports, but no Maven completion or final exit code. It is NOT an acceptance PASS. Five upgrade/old-executable cases and six mining multi-JVM cases completed; the scheduler multi-JVM class did not finish. Its raw evidence explicitly records surviving SPMF children after SIGKILL on macOS, then test cleanup; this is not Linux orphan-cleanup acceptance.

A second full rerun is IN_PROGRESS, supervised independently with an explicit final exit-code/status file. At that point no Phase 2 code or local commits had started.

Raw logs remain in /private/tmp/paspm-phase2-optin and may include synthetic credentials; do not publish them. Only sanitized counts, test identities and relevant failure categories belong in this record.

## Pending owner clarification for Phase 3
The conversion from the live three-component configuration to the new two-component configuration needs an explicit decision. Proposed: retain lambda, normalize enabled legacy expiry/season coefficients, use 0.5/0.5 if both are zero or config is absent, default N=30, increment version. Owner has been asked; this proposal is NOT approval. Frozen configurations will never be rewritten. Independent verification continues.

## Final opt-in result (before Phase 2)

| Execution | Discovered | Executed | PASS | FAIL | ERROR | SKIPPED |
|---|---:|---:|---:|---:|---:|---:|
| Full opt-in + multi-JVM + natural lease + baseline executable | 632 | 627 | 627 | 0 | 0 | 5 |
| Additional synthetic JPA suite, initial environment attempt | 5 | 0 business cases | 0 | 0 | 5 context errors | 0 |
| Additional synthetic JPA suite, corrected environment | 5 | 5 | 5 | 0 | 0 | 0 |

Full `clean verify` completed BUILD SUCCESS at 12:50:00 Asia/Ho_Chi_Minh, duration 27:17, Maven exit 0. The source/test/migration fingerprint manifest was unchanged during the run. All 632 discovered cases have executed successfully across the full run and the separate five-case retest; do not describe the full Maven run itself as zero-skipped.

Evidence: [full counts, case identities and exact Maven argument list](F05_FULL_OPTIN_COUNTS.json), [separate JPA counts](F05_SYNTHETIC_JPA_COUNTS.json), [multi-JVM event evidence](F05_MULTI_JVM_EVIDENCE.txt), [original failed run](F05_OPTIN_INITIAL_COUNTS.json).

The five skips were all `PilotCloneApiIntegrationTest`, because `paspm.test.jpa-clone` is not enabled by the reference script: duplicate SKU/validation, simulated food-store inventory, metadata/status preservation, product round-trip/defaults and cross-Store/role checks. They were subsequently enabled on a newly provisioned disposable schema with synthetic fixtures. This is NOT real-data clone verification. The first supplemental attempt failed before business execution because the schema-only account could not create migration triggers under MySQL binary logging. A different fresh synthetic schema was migrated by the disposable-container administrator, then the five tests passed with the schema-only account. No production/shared database, global MySQL security setting, source-data copy, Flyway repair or privilege expansion was used.

Selected completed groups: MiningIntegrationTest 30/30, MiningMySqlIntegrationTest 30/30, MiningMultiJvmMySqlTest 6/6, MiningSchedulerMultiJvmMySqlTest 12/12, MiningSchedulerUpgradeCompatibilityMySqlTest 5/5, LegacyRecoveryMySqlTest 11/11, MiningProgressMigrationTest 2/2. All have zero failures/errors/skips. Real SPMF HUI-Miner/EFIM and the Java 17 runtime checks are included in the full result.

Natural lease evidence: snapshot-preparation crash recovered after 304 seconds without changing lease_until; a stalled owner resumed after 384 seconds and its stale commit was refused, with the new owner producing exactly one snapshot/request and no duplicate results. Queue saturation, post-commit/pre-enqueue recovery, lease revocation, MySQL outage/reconnect and concurrent startup also executed. Evidence distinguishes accelerated lease tests from natural-expiry tests.

### Limits and NOT RUN
- Linux Docker parent-death/orphan-cleanup acceptance was NOT RUN. macOS SIGKILL scenarios observed surviving SPMF children and explicitly cleaned them up; their recovery PASS is not Linux orphan-cleanup PASS.
- GitHub-hosted CI, Windows execution and interactive Swagger UI were NOT RUN in this gate.
- No real-data clone fidelity claim: supplemental JPA data is synthetic.
- The unmodified Option B shell script was NOT RUN end-to-end: native MySQL 8.4 CLI replaced its unavailable Docker-host-network client; its old fixed V19 history summary is not used. Its full-opt-in Maven flags were retained, with multi-JVM/natural-lease/legacy-executable opt-ins added.
- Scheduler activation stays HTTP 503. No production readiness, merge or deployment approval follows from this local gate.

### Documentation checks
134 changed-document relative Markdown links resolved; `git diff --check` passed. Raw Maven logs/private database configuration remain outside Git. The SPMF banner and pinned SHA-256 were rechecked successfully.

## Authorized local checkpoint commits
- `3bdfee3` — `feat(f05): expose fenced mining progress and legacy-safe metadata`.
- `bd60c75` — `feat(f05): allow scoped merchandiser submissions under system policy`.
- This record is included in the third documentation commit: `docs(f05): synchronize progress contracts and checkpoint evidence`.

No push, merge, rebase, tag, force operation or deployment was performed in this checkpoint-approval session. Phase 2 can now proceed. Phase 3 configuration conversion still awaits the owner clarification above.

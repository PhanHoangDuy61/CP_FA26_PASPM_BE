# ROC-20261115-002 — F05 progress and Merchandiser submission

| Trường | Giá trị |
|---|---|
| Record ID | ROC-20261115-002 |
| Date / timezone | 2026-11-15 / Asia/Ho_Chi_Minh |
| Features | F05; kế hoạch F03 |
| Execution status | COMPLETED trong phạm vi phiên; xem giới hạn bên dưới |
| Release status | UNRELEASED — chờ duyệt Checkpoint 1 |
| Daily summary | [Tổng hợp ngày](README.md) |

Date: 2026-11-15, Asia/Ho_Chi_Minh. Branch: Khoi/feat/f05-progress-f03-weight-v2. Base: 8f94f94. Implementation remains uncommitted/unpublished.

## Added
- Additive V24 mining progress metadata and Store/request-qualified current-run FK. V1–V23 unchanged. Existing rows receive LEGACY; frozen payloads untouched.
- GET own-Store and System Admin progress routes sharing a scalar DTO; no frozen JSON decoding.
- Fenced stage updates at claim, algorithm start, pre-save, comparison and terminal transitions. SAVING commits before result persistence.

## Changed
- Owner replaced "System Admin is the only mining submitter": Merchandiser may submit own-Store snapshotId/requestKey using the current System Admin mining configuration. Explicit algorithm/minUtil/Store overrides rejected.
- Owner confirmed new Merchandiser submissions are rejected when frozen lambda exceeds the current maximum; require a valid snapshot, never rewrite old data. Matching idempotent replay returns existing work. System Admin rights remain unchanged.
- System Admin policy and immutable snapshot captured in the same transaction; requested_by remains the caller.

## Security and preservation
Store Admin cannot submit. Foreign resources return 404. System progress uses REQUESTS gate and existing audit logger. No scheduler flag changes; default activation remains HTTP 503. No F03 formulas, F06 generation, historical JSON or Word reports changed.

## Technical progress semantics
Completed run=2 steps; current SAVING run=1; completed comparison (MATCH/FAILED)=1. BLOCKED comparison and failed algorithms add zero. total=2*n plus 1 for two algorithms. Percent is completed work, not success/time; mismatch may be FAILED at 100%, timeout may finish below 100%, recovery may decrease progress. elapsedMs comes from DB time for the current stage; legacy timing null. timeoutMs only RUNNING; attemptNo=request lease_fence.

## Assumptions pending owner confirmation (future F03 phases; not implemented)
- P2: highest weight among assigned active seasons.
- P3: active dates inclusive on snapshot analysis date, shared by preview/scheduler.
- P4: push day on/before analysis date makes all remaining eligible batch stock at risk (zero-sales exception per Q7).
- P5: accept expiryPushDays but ignore for calculation and deprecate in OpenAPI.
- P6: reuse existing paths with changed bodies; document frontend breaking changes.

## Verification
Final results below were collected after the last source/test change. Intermediate runs remain listed separately.
- Initial merge baseline: 609 total, 409 pass, 2 fail, 198 skipped. Stale migration-count tests fixed in the authorized F07/F08 merge; merged baseline: 411 pass / 0 fail / 198 skipped.
- Red tests: two new API tests failed as expected (missing progress 404, forbidden submission 403).
- First green attempt: progress passed, submission still 403 because SecurityConfig retained denyAll; fixed that exact matcher.
- Focused regression after matcher and contract-test updates: 38/38 pass.
- H2 V24 migration + closed gate: 3 pass, 1 opt-in MySQL case skipped.
- First dedicated disposable MySQL run: 33/33 pass, MySQL 8.4.13.
- An added test observer incorrectly required four completed steps during the timeout case, preventing its terminal transition. Scope of that observer was corrected; production behavior was not changed for this test failure.
- Final review moved the Merchandiser lambda check after existing snapshot metadata validation to preserve 400 responses for incomplete snapshots. Both roles retain the existing READY/numerical validation path.

## Tooling
Superpowers workflow instructions read and applied for planning/debugging/test-first verification. CodeGraph queried for MiningService/MiningDispatchLease relationships; its root index also contains old worktrees and does not index this isolated worktree. Current source and executable tests, not index results, are the verification authority. No tooling installation.

## Documentation and future report changes
Updated F05 README/API/handoff, endpoint RBAC matrix, OpenAPI and API changelog. F03 Weight v2 and V25 are deferred to Checkpoint 1 approval. Word files untouched. Required future report edits: SRS BR-05/3.4/mining use cases/screen-role table; Report 1 roles; Report 4 4.1 (1.115 becomes 1.12 under future Weight v2) and who runs mining.

## Delivery
Only the F07/F08 merge was committed/pushed. F05 changes must remain local until owner approval. Stop at Checkpoint 1 before Phase 2. No deployment.

## Final Checkpoint 1 evidence
- Final Java 17 `clean verify`: **621 discovered, 418 PASS, 0 failures/errors, 203 skipped**, BUILD SUCCESS. Per-class counts: [VERIFY_COUNTS](F05_CHECKPOINT1_VERIFY_COUNTS.json).
- Final disposable MySQL group: **33 PASS / 0 failures / 0 skipped** (32 tests actually use MySQL, plus one H2 migration test). Per-class counts: [MYSQL_COUNTS](F05_CHECKPOINT1_MYSQL_COUNTS.json). Container MySQL 8.4.13 stopped afterwards; no shared database used.
- Compared with the merged baseline (609 / 411 pass / 198 skipped): +12 discovered = four new mining cases in H2 and their four opt-in MySQL inherited cases, two closed-gate tests, one H2 migration and one opt-in MySQL migration. Net +7 default PASS, +5 default skipped. Of 203 default skipped tests, 32 were independently executed in the MySQL group; **171 remain NOT RUN** in this session. The other H2 migration in that group also appears in default verification and is not an additional unique test.
- The final full run completed 2026-11-15 11:22:46 Asia/Ho_Chi_Minh. The earlier successful full run was followed by a snapshot-validation ordering change and is not the final-source result.
- Runtime Springdoc export regenerated through MockMvc: **98 paths / 116 operations**. Assertions verify both progress routes, the active Store POST body and still-forbidden benchmark. This is not an interactive Swagger UI test.
- Java 17 Maven command: `mvn --batch-mode --no-transfer-progress clean verify -Dpaspm.test.spmf-java=/opt/homebrew/opt/openjdk@25/bin/java -Dpaspm.test.spmf-jar=/Users/khoinm/Desktop/khoi/fpt/ki9/FA26SE048_HUPAS/spmf_run/spmf.jar -Dlogging.level.root=ERROR -Dpaspm.test.openapi-export=/private/tmp/paspm-progress-openapi.json` with JAVA_HOME pointing at the verified temporary Corretto 17 installation. Paths are execution provenance, not portable setup requirements.
- MySQL command: same Java/SPMF flags, `test -Dtest=MiningMySqlIntegrationTest,MiningDispatchLeaseMySqlTest,MiningProgressMigrationTest -Dpaspm.test.mysql=true`; `PASPM_TEST_MYSQL_SERVER=jdbc:mysql://127.0.0.1:59019/`, root with empty disposable-container password, `PASPM_CI_DISPOSABLE_MYSQL=true`. That port belonged exclusively to container `paspm-progress-v24-20261115` created in this session.
- `git diff --check` passed. Existing migrations compare unchanged against base 8f94f94. No tracked source changes in the original BE checkout; its pre-existing documentation changes remain preserved.
- Native Linux/multi-container fault injection, all remaining opt-in suites, hosted CI, interactive Swagger UI, Frontend and production startup are **NOT RUN** here. Do not infer overall production/merge readiness from this checkpoint.

## State and proposed commits (not executed)
Phase 0 complete; Phase 1 and Phase 1b implemented and verified within the evidence above. **STOP AT CHECKPOINT 1.** No Phase 2–6 implementation, no V25, no automatic feature commit/push/merge/deploy.

Suggested messages after approval:
1. `feat(f05): expose fenced mining progress and legacy-safe metadata`
2. `feat(f05): allow scoped merchandiser submissions under system policy`
3. `docs(f05): synchronize progress contracts and checkpoint evidence`

The code is in `/private/tmp/paspm-f05-progress-weight-v2`, not the original BE working directory, to preserve that directory's uncommitted documentation.

Final source/test tree SHA-256: `4f74681875083f373bb92ae01c89ae397766ba283237c49d6400f3501e1e8252`.

## All Maven executions in this task

| Local log stem | Total | PASS | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| `paspm-f07-merge-baseline` | 609 | 409 | 2 | 0 | 198 |
| `paspm-f07-merge-retest` | 12 | 11 | 0 | 0 | 1 |
| `paspm-f07-merge-verify` | 609 | 411 | 0 | 0 | 198 |
| `paspm-progress-red` | 2 | 0 | 2 | 0 | 0 |
| `paspm-progress-green` | 2 | 1 | 1 | 0 | 0 |
| `paspm-progress-focused` | 38 | 32 | 6 | 0 | 0 |
| `paspm-progress-focused2` | 38 | 38 | 0 | 0 | 0 |
| `paspm-progress-migration` | 4 | 3 | 0 | 0 | 1 |
| `paspm-progress-mysql` | 33 | 33 | 0 | 0 | 0 |
| `paspm-progress-final-verify` | 621 | 417 | 1 | 0 | 203 |
| `paspm-progress-final2-verify` | 621 | 418 | 0 | 0 | 203 |
| `paspm-progress-mysql-final` | 33 | 33 | 0 | 0 | 0 |
| `paspm-progress-final3-verify` | 621 | 418 | 0 | 0 | 203 |

Raw logs remain local under `/private/tmp/`; failed HTTP-test logs may contain synthetic test tokens and are not published as documentation. The first final-MySQL attempt was rejected before execution by an automatic-approval usage limit; after the owner said continue, the retried approval succeeded and the final run above executed.

## Changed files at Checkpoint 1

- `docs/api/API_CHANGELOG.md`
- `docs/api/F01_F02_RBAC_MATRIX.md`
- `docs/api/F05_MINING_API.md`
- `docs/api/README.md`
- `docs/api/openapi.json`
- `docs/features/F05/README.md`
- `docs/frontend_handoff/F05_HANDOFF.md`
- `docs/record-of-changes/2026-11-15/02-f05-progress-checkpoint-1.md`
- `docs/record-of-changes/2026-11-15/F05_CHECKPOINT1_MYSQL_COUNTS.json`
- `docs/record-of-changes/2026-11-15/F05_CHECKPOINT1_VERIFY_COUNTS.json`
- `docs/record-of-changes/2026-11-15/README.md`
- `docs/superpowers/plans/2026-11-15-f05-progress-f03-weight-v2.md`
- `src/main/java/vn/edu/fpt/paspm/mining/MiningContracts.java`
- `src/main/java/vn/edu/fpt/paspm/mining/MiningController.java`
- `src/main/java/vn/edu/fpt/paspm/mining/MiningDispatchLease.java`
- `src/main/java/vn/edu/fpt/paspm/mining/MiningProgressController.java`
- `src/main/java/vn/edu/fpt/paspm/mining/MiningProgressReader.java`
- `src/main/java/vn/edu/fpt/paspm/mining/MiningRepository.java`
- `src/main/java/vn/edu/fpt/paspm/mining/MiningService.java`
- `src/main/java/vn/edu/fpt/paspm/mining/MiningWorker.java`
- `src/main/java/vn/edu/fpt/paspm/security/SecurityConfig.java`
- `src/main/resources/db/migration/V24__f05_mining_progress.sql`
- `src/test/java/vn/edu/fpt/paspm/mining/MiningDispatchLeaseMySqlTest.java`
- `src/test/java/vn/edu/fpt/paspm/mining/MiningDispatchLeaseTest.java`
- `src/test/java/vn/edu/fpt/paspm/mining/MiningIntegrationTest.java`
- `src/test/java/vn/edu/fpt/paspm/mining/MiningProgressClosedGateTest.java`
- `src/test/java/vn/edu/fpt/paspm/mining/MiningProgressMigrationTest.java`
- `src/test/java/vn/edu/fpt/paspm/mining/MiningSystemStoreReadGranularGateTest.java`
- `src/test/java/vn/edu/fpt/paspm/utility/F03F05RbacMatrixTest.java`

## Owner decision — Checkpoint 1 approval, 2026-11-15
Owner approved Checkpoint 1 and retained rejection of new Merchandiser requests above the current lambda maximum. The error must explicitly explain that the lambda policy changed and a new snapshot is needed. Updated error: `The lambda policy changed: this snapshot exceeds the current lambda maximum. Create a new snapshot within the current permitted range before submitting mining.`

Before Phase 2, execute full opt-in/MySQL/multi-JVM/SPMF verification on disposable databases. Only after the verification gate passes, three local commits are authorized; no push, merge or force operation is authorized for the new work. F07/F08 merge/push 8f94f94 was separately authorized by the owner's merge instruction and explicit “Cho phép push riêng merge F07/F08 lên dev” reply.

## Addendum 2026-11-15 (ROC-20261115-005, docs sync) — what was later committed and verified

This record is kept as written; the following facts were added afterwards.

| Item | Value |
|---|---|
| Commits (local, owner-authorized after the gate, not pushed) | `3bdfee3` feat(f05): expose fenced mining progress and legacy-safe metadata · `bd60c75` feat(f05): allow scoped merchandiser submissions under system policy · `982094c` docs(f05): synchronize progress contracts and checkpoint evidence |
| Migration | V24 `src/main/resources/db/migration/V24__f05_mining_progress.sql` (stage, stage_started_at, current_run_id; `LEGACY` default) |
| Counted verification | Phase 1 full opt-in gate (record 003): **632 tests, 627 PASS, 0 FAIL, 5 SKIP**, Maven exit 0; JPA supplementary **5/5** |
| Invalid / not counted | first full opt-in (6 FAIL, stale V21 assertions, superseded); interrupted rerun (368 completed, no Maven exit code); first JPA attempt (5 context errors, environment) — details in record 003 |
| NOT RUN | Linux SPMF orphan cleanup (macOS evidence only), GitHub CI (including the `integration` job, which needs `PASPM_CI_SPMF_ARTIFACT_URL`), interactive Swagger UI, real-data conversion/clone, Windows, Frontend |

"Implementation remains uncommitted/unpublished" above was true at the time; the commits above now exist on branch `Khoi/feat/f05-progress-f03-weight-v2` only.

# ROC-20261117-013 — F05 dense acceptance and output-cap enforcement

| Field | Value |
|---|---|
| Record ID | ROC-20261117-013 |
| Date / timezone | 2026-11-17 / Asia/Ho_Chi_Minh |
| Type | Backend implementation, test, verification, documentation |
| Features | F04 fixture import, F03 snapshot, F05 mining, F06 evidence consumption |
| Branch / base | `Khoi/test/f05-foodmart-dense-acceptance`, based on local `origin/dev` `1d1aeb3b8081117b718b6a7b07bc4a655cee74d1` |
| Execution status | COMPLETED for this acceptance scope |
| Release status | UNRELEASED; local commits only, no push, merge or deployment |
| Daily summary | [2026-11-17](README.md) |

## 1. Request and scope

Continue [Record 012](../2026-11-16/12-f05-foodmart-scale.md) on a new local branch. Verify the dense synthetic FoodMart-shaped lifecycle, the 2,000-pattern result, F06 handling, JPA-clone coverage and whether child output can exceed 4 MiB. Do not increase limits, edit migrations, use authentic/production data, touch Frontend or publish Git changes.

The local `origin/dev` ref was checked without fetching and matched the requested base commit. Work ran in the new isolated worktree; the primary `BE` checkout and Frontend were not changed.

## 2. Changes

### Added / changed

- Added `MiningProcessLauncher`, applying the existing 4 MiB limit as a kernel `ulimit -f` ceiling on Linux and macOS. Linux uses 512-byte blocks; macOS uses 1,024-byte blocks. The existing Linux parent-death wrapper is retained. Other operating systems continue to use the existing process polling guard.
- Updated `SpmfMiningEngine` to check the file size at the cap before interpreting the child exit code. A kernel `SIGXFSZ` at the limit is reported as `OUTPUT_LIMIT`; the partial result is not accepted. Successful `environment` metadata now includes `outputBytes=<actual bytes>` for measurement. No response field or OpenAPI schema was added.
- Added producer-volume and real EFIM output-limit tests; extended mining persistence/F06 tests to verify a cap failure leaves no partial patterns and F06 rejects the failed run.
- Extended the opt-in MySQL E2E fixture to 4,100 synthetic orders, 18,040 transaction-item rows and 1,559 products, then exercised F04 import → F03 snapshot → F05 EFIM → F06 Bundle evidence. It uploads two valid CSV parts (9,840 and 8,200 rows) so the configured 10,000-row per-file F04 limit is unchanged.

No Flyway migration, historical snapshot/request/recommendation data, Frontend code, or OpenAPI schema changed.

## 3. Dense fixture and measurements

The fixture is explicitly synthetic and is not the authentic FoodMart data extract.

| Measurement | Observed result |
|---|---:|
| F04 accepted orders / transaction-item rows | 4,100 / 18,040 |
| F03 snapshot | READY; 18,040 matrix rows; 1,559 distinct products |
| Average products per order | 4.4 |
| Scale / GCD divisor | scale 6 / 10,000,000 |
| Largest converted transaction utility | 5 (int32 headroom 2,147,483,642) |
| Total converted utility | 18,040 (int32 headroom 2,147,465,607) |
| F05 | EFIM COMPLETED; 152 ms; 3 patterns; 141 output bytes |
| F06 | Bundle evidence AVAILABLE; support count 250 |
| Runtime protections | heap 256 MiB; timeout 60,000 ms; pattern cap 2,000; output cap 4,194,304 bytes |

The canonical combined synthetic CSV fixture is 1,371,109 bytes, SHA-256 `80cbb5bb6cae381c5b11e6f81ef234d3c8ad73e4dcd9895aac6324df63bb5102`. EFIM input, snapshot, request, run, patterns and F06 evidence were produced by the actual application paths against disposable MySQL 8.4.13. The test does not establish authentic FoodMart acceptance.

## 4. Pattern cap and F06

`SpmfMiningEngineTest#actualEfimFailsAtThePatternCapInsteadOfReturningTruncatedResults` ran real SPMF EFIM on a one-transaction/12-item fixture with utility 1 and `minUtil=1`. Once output exceeded the 2,000-pattern cap, F05 returned `OUTPUT_LIMIT` and failed the run; it did not return a truncated successful result.

`MiningIntegrationTest#durableFailureAndTimeoutStatusesContainNoPartialPatterns` verified the durable request/run failure, zero persisted patterns, and F06 `FrozenEvidenceReader` rejection with HTTP 409. Both focused tests passed. The full opt-in run also executed the EFIM and F06 cases.

## 5. Output overshoot

The output cap is now enforced by the OS on Linux/macOS, independently of input or producer write size. A producer attempted 4,259,840 bytes (cap + 64 KiB) and 20,971,520 bytes (cap + 16 MiB); both produced exactly 4,194,304 bytes, with 0-byte overshoot. A second 20,971,520-byte write under Linux-container `ulimit -f 8192` also produced exactly 4,194,304 bytes; the writer exited 153 after `SIGXFSZ`. A real 18-item EFIM output crossing the same cap was classified as `OUTPUT_LIMIT`.

This verifies the file ceiling on this macOS host and a Linux container; it is not target-server/production image acceptance. The 64 KiB process-log threshold is still checked by the Backend polling loop. Non-POSIX fallback behavior and Windows overshoot were not tested.

## 6. JPA clone tests

`PilotCloneApiIntegrationTest`: **5 PASS / 0 FAIL / 0 ERROR / 0 SKIP**, using a new disposable MySQL schema and a schema-only application account. Flyway used the disposable container administrator only for migration because MySQL binary logging rejected V11 function creation for the restricted account (error 1419); the app/test connection remained restricted to the marked schema. No source schema or shared database was used. The first attempt was not counted; the corrected fresh schema run passed all five tests.

## 7. Verification in this session

| Run | Actual result |
|---|---|
| Dense E2E class, disposable MySQL 8.4.13, Java 17 + Java 25 SPMF | 2 PASS / 0 FAIL / 0 ERROR / 0 SKIP; dense lifecycle plus existing scale lifecycle |
| Pattern-cap EFIM test | 1 PASS / 0 FAIL / 0 ERROR / 0 SKIP |
| F06 failed-run evidence test | 1 PASS / 0 FAIL / 0 ERROR / 0 SKIP |
| Real EFIM 4 MiB output-cap test | 1 PASS / 0 FAIL / 0 ERROR / 0 SKIP |
| Producer output-size test | 1 PASS / 0 FAIL / 0 ERROR / 0 SKIP; two requested sizes, exact cap both times |
| JPA clone API | 5 PASS / 0 FAIL / 0 ERROR / 0 SKIP |
| `scripts/verify-option-b-macos.sh --run` | Exit 0; runner **24/24 stages PASS** |
| Default `clean verify` stage | 658 discovered: 448 PASS / 0 FAIL / 0 ERROR / 210 SKIP |
| Dispatcher flag + migration-versioning stage | 6 PASS / 0 FAIL / 0 ERROR / 0 SKIP |
| MySQL group | 72 PASS / 0 FAIL / 0 ERROR / 0 SKIP |
| Full opt-in stage | 668 discovered: 641 PASS / 0 FAIL / 0 ERROR / 27 SKIP; includes dense E2E 2/2 and output-limit tests |
| Explicit multi-JVM stage | 23 invocations: 20 PASS / 0 FAIL / 0 ERROR / 3 SKIP |
| Flyway / schema validation / runtime OpenAPI stages | Fresh V1–V26, V11, rerun-identical history, upgrade preserving seeded data, `ddl-auto=validate`, runtime OpenAPI: PASS |

The full-opt-in 27 skips are 22 opt-in multi-JVM cases and 5 clone API cases; the clone API cases passed in the separate JPA run. In the explicit multi-JVM stage, the 3 skips were two natural-lease methods requiring `paspm.test.natural-lease=true` and one legacy-upgrade method requiring `paspm.test.legacy-jar`; those properties were not supplied by the verifier command. They are NOT reported as passes in this record. The full-opt-in database-outage test deliberately disconnected MySQL and logged expected connection errors while it recovered; its test passed 1/1.

### Commands and properties used

Focused MySQL runs used Java 17 for Maven/backend, Java 25 for the SPMF child, the pinned SPMF 2.67a JAR and `-Dpaspm.test.mysql=true` against only the task-created disposable MySQL container. The dense run added `-Dpaspm.test.foodmart-e2e=true` and `-Dpaspm.f05.foodmart-e2e.output=<external-evidence>/dense` with `-Dtest=FoodMartScaleSyntheticE2EMySqlIntegrationTest`.

Focused engine/persistence selectors were `-Dtest=SpmfMiningEngineTest#actualEfimFailsAtThePatternCapInsteadOfReturningTruncatedResults`, `-Dtest=SpmfMiningEngineTest#actualEfimOutputBeyondFourMiBStopsAtTheKernelFileCap`, `-Dtest=MiningIntegrationTest#durableFailureAndTimeoutStatusesContainNoPartialPatterns` and `-Dtest=MiningProcessOutputLimitTest`; each ran with Maven offline, Java 17 Backend and the pinned Java 25/SPMF properties where actual mining was required.

The JPA run used `-Dtest=PilotCloneApiIntegrationTest -Dpaspm.test.jpa-clone=true -Dpaspm.test.jpa-clone-config=<private-disposable-config>`, `PASPM_JPA_PILOT_MUTATIONS_ALLOWED=true`, and the recorded Java 17 / Java 25 / SPMF test properties. `SPRING_FLYWAY_URL`, `SPRING_FLYWAY_USER` and `SPRING_FLYWAY_PASSWORD` directed migration to the new disposable schema; secret values are not retained. The application DataSource used the schema-only clone credentials checked by `PilotCloneTarget`.

The full command was `bash scripts/verify-option-b-macos.sh --run`, with `PASPM_JAVA17_HOME`, `PASPM_JAVA25_HOME`, `SPMF_JAR_PATH`, `SPMF_JAVA_PATH` and `PASPM_EVIDENCE_ROOT` set to the verified runtimes/artifact and external evidence directory. The runner created and stopped its own disposable MySQL 8.4 container. Exact arguments and raw output are retained in the external evidence `commands.log` and stage logs.

### Non-counted attempts

- Initial dense test was blocked before MySQL assertions by sandbox networking; subsequent execution on disposable MySQL ran the test.
- A dense fixture setup attempt exposed a test helper SKU-prefix mismatch; fixed and rerun.
- A subsequent F04 import correctly rejected 18,040 rows in one CSV because the existing per-file maximum is 10,000. The fixture was split into two valid imports without increasing that limit.
- One compile attempt found a stale local variable in the test helper; fixed before the final focused and full runs.
- Initial JPA clone migration with the schema-only account failed at V11 with MySQL error 1419 under binary logging. The retest used a fresh disposable schema, admin credentials only for Flyway, and the schema-only app account; 5/5 passed.

## 8. Evidence and hashes

Evidence is outside Git under `outputs/evidence/2026-11-17/f05-dense-acceptance/`. `manifest.json` contains hashes for the dense E2E result, focused logs, JPA run, full verifier summary and full-opt-in/multi-JVM logs. No credentials or database dumps are retained. The verifier container and the task-created temporary MySQL container were stopped after testing.

## 9. Limitations and next actions

- The original record 012 history remains unchanged. Its dense synthetic, pattern-cap, output-overshoot and JPA-clone limitations are closed by the evidence above.
- Authentic FoodMart F03 snapshot → F05 → F06 acceptance remains **NOT RUN**. The available public extract does not provide canonical F04 order grouping plus approved W2 inventory/batch-season evidence.
- Production Linux image/target-server child cleanup, memory under container limits, peak RSS and hosted GitHub CI remain **NOT VERIFIED / NOT RUN**. The local Linux file-limit probe does not close those platform/CI items.
- Two natural-lease and one legacy JAR multi-JVM cases were skipped by the explicit multi-JVM verifier run because their opt-in properties were absent; no new execution is claimed here.
- No limits were increased: 1,600 products, 20,000 rows, 256 MiB child heap, 60-second timeout, 2,000 patterns, 4 MiB algorithm output and 64 KiB process log remain unchanged.

Owner-approved local commit sequence for this work:

1. `feat(f05): enforce output cap and verify dense mining acceptance`
2. `docs(f05): record dense acceptance and output-limit verification`

## 10. Owner follow-up — realistic utility range, Windows fallback, and opt-in multi-JVM tests

This dated addendum records the next owner-requested verification on the same local branch. It supersedes only the relevant “NOT RUN” statements above for the two natural-lease cases and the legacy-JAR compatibility case. Record 012 remains unchanged; this addendum links it as history.

### Realistic-utility synthetic variant

The disposable-MySQL E2E created a separate labelled synthetic fixture with 4,100 orders, 18,040 transaction-item/matrix rows, 1,559 products and 4.4 products/order. Unit prices ranged from 0.50 to 200.00, quantities from 1 to 10. The snapshot used `W2_EXPIRY_SEASON_V1`, with active push-day and missing-push-day batches and active-season assignments. It was not authentic FoodMart data. The test fixture configured the current System Admin mining policy as `ABSOLUTE 500.0`; no value was lowered to force a mining run.

| Conversion measurement | Actual value | Comparison with `Integer.MAX_VALUE` (2,147,483,647) |
|---|---:|---:|
| Scale-6 GCD divisor | 100 | — |
| Largest transaction utility after conversion | 26,225,721 | 2,121,257,926 headroom |
| Largest item TWU after conversion | 3,217,593,703 | exceeds by 1,070,110,056 |
| Total utility after conversion | 53,254,362,673 | exceeds by 51,106,879,026 |

F05 rejected the submission with HTTP 400 and exact error `Lossless converted dataset exceeds SPMF int32 capacity`; the request was not persisted. EFIM duration, pattern count and output bytes are **NOT RUN / NOT APPLICABLE** because conversion rejected the input before an F05 request or child process existed. F06 consumption for this realistic-utility variant is **NOT RUN**. This is the correct bounded outcome: the input limit was not raised and no lossy conversion was attempted. The detailed generated measurement is in external evidence `outputs/evidence/2026-11-17/f05-dense-acceptance/followup/realistic-utilities/foodmart-dense-realistic-utilities.json`.

### Windows/non-POSIX launcher behavior

`MiningProcessLauncher.command` detects macOS and Linux for POSIX `ulimit` wrapping. For other OS names, including `Windows 11`, it returns the original argument vector unchanged. `SpmfMiningEngine` then launches that direct argv with `ProcessBuilder`; its existing polling checks the output file against 4 MiB and the process log against 64 KiB while the child is alive, followed by final size checks. The fallback does not invoke a shell or fail because `ulimit` is unavailable.

`MiningProcessOutputLimitTest#nonPosixLauncherLeavesCommandUnwrappedAndCanStartChildJvm` mocked `os.name` as Windows 11 and successfully launched a Java 17 child with the returned command (1 PASS). The complete `MiningProcessOutputLimitTest` also passed 2/2 on macOS. A native Windows execution is **NOT RUN**; Windows overshoot under the polling fallback is not measured and has no kernel file-size ceiling in this implementation.

### Three formerly skipped multi-JVM tests

With disposable MySQL 8.4.13 and the exact opt-in prerequisites `-Dpaspm.test.natural-lease=true` and `-Dpaspm.test.legacy-jar=<origin/dev-c8d778f-jar>`, the selected test classes produced these final class results: `MiningMultiJvmMySqlTest` 6/6 PASS after correcting child SPMF environment propagation; `MiningSchedulerMultiJvmMySqlTest` 12/12 PASS; `MiningSchedulerUpgradeCompatibilityMySqlTest` 5/5 PASS. The requested formerly skipped methods passed:

1. `MiningSchedulerMultiJvmMySqlTest#crashPoint3_killedWhilePreparingTheSnapshotRecoversAfterTheLeaseExpiresNaturally` — recovered after 309 seconds; no manual timestamp edit; fence 2; one request and one snapshot.
2. `MiningSchedulerMultiJvmMySqlTest#staleOwnerThatWakesUpAfterItsLeaseExpiredNaturallyCannotCommitAndTheNewOwnerProducesTheOnlyResult` — stale owner resumed after a natural 384-second lease interval; its commit was fenced, winner fence 2; one request, one snapshot and one successful result.
3. `MiningSchedulerUpgradeCompatibilityMySqlTest#characterizeTheOldBackendRunningOnTheNewSchema` — legacy JAR characterization PASS within the 5/5 class run.

The first combined invocation ran all 23 selected tests but failed six `MiningMultiJvmMySqlTest` cases because its spawned Backend JVMs did not inherit `PASPM_MINING_SPMF_JAR` and `PASPM_MINING_JAVA_EXECUTABLE`; those cases returned 503 before their lease assertions. That invalid invocation is retained as **17 PASS / 6 FAIL / 0 ERROR / 0 SKIP**, not hidden. Rerunning that class with both required environment variables gave 6/6 PASS. Scheduler and upgrade classes in the combined invocation were already 12/12 and 5/5 PASS. Thus all 23 unique test methods have passing corrected class evidence, but there was no single green 23-test invocation. External logs retain both runs and the rerun evidence.

### Verification accounting and remaining scope

- Realistic-utility E2E: 1 PASS / 0 FAIL / 0 ERROR / 0 SKIP; rejection was the asserted expected result.
- Windows-mocked fallback: 1 PASS / 0 FAIL / 0 ERROR / 0 SKIP; full output-limit test class: 2 PASS / 0 FAIL / 0 ERROR / 0 SKIP.
- Corrected multi-JVM classes: 23 unique tests PASS (6 + 12 + 5); see the invocation history above for the initial environment-missing failure and class-only retest.
- Previous complete verifier `scripts/verify-option-b-macos.sh --run` remains the earlier 24/24-stage evidence. It was not rerun after this test-only follow-up; production implementation files were unchanged in this addendum. The new realistic E2E, Windows fallback, and corrected multi-JVM tests were run separately as listed above.
- No fresh authentic FoodMart snapshot was created. Native Windows, target deployment image/cgroup memory and peak RSS, hosted GitHub CI, and a full verifier rerun after the added test methods remain **NOT RUN / NOT VERIFIED**.
- The request was rejected during conversion, so the reported `minUtil=ABSOLUTE 500.0` is the current fixture System Admin policy value, but no EFIM thresholded mining was attempted.

Follow-up evidence is outside Git at `outputs/evidence/2026-11-17/f05-dense-acceptance/followup/`; `manifest.json` covers eight evidence files and commands text, SHA-256 `fe13ffd0093898d339c63a264a3cf737051ee5de6672624950ae845ddeb58415`. No secrets or database dumps are included. The disposable MySQL container was stopped.

The branch remains local and unpublished. The owner-approved commit sequence is:

1. `feat(f05): enforce output cap and verify dense mining acceptance`
2. `docs(f05): record dense acceptance and output-limit verification`

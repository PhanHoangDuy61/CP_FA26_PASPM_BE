# F05 Progress and F03 Weight v2 Implementation Plan

**Status:** Phase 0 complete; Phases 1/1b implemented; final verify 418 PASS / 203 skipped, dedicated MySQL group 33 PASS. Checkpoint 1 approved on 2026-11-15. Full opt-in gate now PASS: 627 PASS / 5 skipped in clean verify, all five subsequently PASS on a fresh synthetic JPA schema; multi-JVM and natural leases included. Source commits 3bdfee3 and bd60c75 are local only. Phase 2 is authorized. Phase 3 live-config conversion clarification remains pending.

**Goal:** Add fenced scalar mining progress and own-Store Merchandiser submission, then introduce immutable-version-compatible expiry/season weighting after Checkpoint 1 approval.

**Architecture:** Keep the existing JDBC repository, durable dispatcher, lease/fence and worker. Store progress separately from request status; derive progress counts from mining runs. Reuse administrative submission with Store resolved from the authenticated caller, preserving the System Admin route.

**Tech stack:** Java 17, Spring Boot 3.5.7, Flyway, disposable H2/MySQL 8.4, pinned SPMF 2.67a using Java 25.

## Authorization and constraints
- Owner separately authorized the F07/F08 merge/push already completed. At Checkpoint 1 approval, three local Phase 0/1/1b commits were authorized only after full opt-in verification passes. No new push, merge, force operation or deployment is authorized.
- Do not edit V1–V23, frozen JSON, historical snapshots or mining data.
- Keep schedule activation disabled (503); no Frontend or Word report changes.
- Store isolation: cross-Store identifiers return 404.
- Preserve local documentation changes and all unrelated worktrees.
- Work inline; do not start other agents.

## Decisions and unresolved interpretation
- Merchandiser may submit for their own Store; Store Admin may not. System Admin retains all routes and rights.
- Use the current System Admin mining policy, including algorithm and minUtil; do not expose override fields to Merchandiser. The existing System Admin compareAlgorithms option remains unchanged.
- Owner additionally stated mining must remain within the System Admin lambda maximum. Existing F05 submits immutable snapshots without revalidating their lambda against the current policy. **Owner confirmed 2026-11-15:** block new Merchandiser requests using an older higher-lambda snapshot and require a valid new snapshot. Never rewrite it. Existing idempotent replays return their original request. System Admin rights remain unchanged.
- P2–P6 are assumptions pending owner confirmation: maximum active-season weight; inclusive active dates; push day <= analysis date means whole batch at risk; deprecated expiryPushDays accepted and ignored; existing paths with changed bodies.

## Phase 0 — Merge and baseline
- [x] Fetch remote refs; original dev 28a6b27 lacked V23.
- [x] Owner authorized merging F07/F08 d651230 into dev.
- [x] Isolate merge work from BE's existing uncommitted docs.
- [x] Run Java 17 baseline with the two SPMF properties used by scripts/verify-option-b-macos.sh.
- [x] Investigate two migration-test failures caused by stale fixed migration counts. Replace counts with verified pending migration counts, retain data/checksum assertions and verify V21 explicitly.
- [x] Full merge-candidate verify: 609 total / 411 pass / 0 fail / 198 skipped. Merge 8f94f94 pushed to dev and confirmed by ls-remote.
- [x] Created Khoi/feat/f05-progress-f03-weight-v2 from origin/dev at 8f94f94 in /private/tmp/paspm-f05-progress-weight-v2.
- Proposed next migrations: V24__f05_mining_progress.sql, V25__f03_two_component_weight.sql. Recheck availability before writing.
- The old verification shell script has hardcoded V19/V12 history expectations. Its Maven baseline command is reused; its outdated Flyway summary is not evidence for V23.

## Phase 1 — F05 progress
Files: mining/MiningDispatchLease.java, MiningRepository.java, MiningWorker.java, MiningContracts.java, MiningService.java, MiningController.java, MiningSystemController.java; new scalar progress reader and V24.
1. Add failing H2 tests for legacy rows, own/foreign Store progress, both role routes, system REQUESTS gating, no frozen JSON parsing and database-clock elapsed time.
2. V24 adds stage, stage_started_at and current_run_id without changing status or its CHECK. Existing rows stay LEGACY; new requests explicitly QUEUED. Use a Store/request-qualified relationship for the current run if supported by existing unique keys.
3. claim sets the initial stage atomically with lease acquisition. After claimRun set RUNNING with run id under guarded(); before complete set SAVING in a separate short guarded transaction; before two-run comparison set COMPARING; terminal sets COMPLETED/FAILED atomically with status and lease release.
4. Read only request/run scalar columns. totalSteps=2*algorithm count + comparison step for two algorithms. A completed run contributes two steps; active SAVING contributes its completed algorithm step. Failed/timed-out runs do not imply successful work; BLOCKED comparison contributes no successful comparison step. Document percent as completed work, not success or time prediction. Terminal failed requests can finish below 100%.
5. elapsedMs uses database time minus stage_started_at, bounded at zero; timeoutMs only RUNNING; attemptNo uses lease_fence. LEGACY with absent stage timing has null elapsed.
6. Verify one/two-algorithm sequences, timeout, stale writes, lease recovery, Store isolation and system audit/REQUESTS gate. Verify scheduler remains disabled.

## Phase 1b — Merchandiser submission
Files: MiningController.java, MiningContracts.java, MiningService.java; tests F03F05RbacMatrixTest and mining integration suites.
1. Implement the approved historical-lambda restriction only for new Merchandiser requests.
2. Request accepts snapshotId and requestKey only; resolve Store from JWT, invoke the same policy-pinning/capture path as administrative submission with compare=false.
3. Preserve actor attribution, READY checks, 404 isolation, idempotency conflict 409, frozen input and durable queue semantics. No System Admin route removed or weakened.
4. Test own Store 202, foreign snapshot 404, Store Admin 403, current policy pinning, request-key conflict, injected policy fields rejection, progress on the new request, System Admin regression and the approved lambda rule.
5. Update F05 README, API, RBAC, handoff, OpenAPI and record of changes; explicitly replace the prior System-Admin-only submitter decision.
6. Run affected tests and full baseline; report counts and skipped classes.

**CHECKPOINT 1 approved:** Run full opt-in + separate multi-JVM verification first. If the gate passes, make the three authorized local commits and continue Phases 2–6. Stop at CHECKPOINT 2.

## Later phases — gated, not implemented
2. Version-aware readers for snapshot config, mining utility config, expiry evidence, recommendation eligibility and live config/policy. Preserve old records and strict unknown-property rejection. Byte-for-byte legacy fixtures before/after migrations.
3. Add V25: nullable batch push_day without backfill; Store-scoped season and product_season, immutable season copies/evidence, new expiry statuses; rewrite only live configs. Preserve product score columns.
4. Implement W2_EXPIRY_SEASON_V1: FEFO ordered by push day then numeric batch id, sales-rate lookback N, zero-sales warning, active-season maximum and half-up W to two decimals. Golden expiry values 0.375 and 0.25; compare W against experiment. Document date-window inclusion. Season CRUD/assignment roles and 409/404 rules.
5. Update F02 batch pushDay, scheduled snapshot consumer (still disabled), F06 expiry/evidence/fingerprint and ended-season warning, without automatic re-mining or regeneration. Check whether F07/F08 consumes priority coverage before changes.
6. Final full H2/SPMF and disposable MySQL verification, source-grounded OpenAPI/contracts/handoff/RBAC/ERD notes, change records, compatibility tests. No Word edits; list SRS BR-05/3.4/use cases/screen roles, Report 1 roles and Report 4 4.1 rounding/roles corrections. Propose commits only, then stop.

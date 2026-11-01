# F03 / F05 Core release readiness — 2026-11-02

**Result: CORE_RELEASE_READY as a candidate for a Draft PR** (local verification complete on commit `45dbd80` plus the documentation commit that follows it). **Merge is not established**: GitHub CI has not been run (the branch is not pushed and `gh` is not installed on this host), no human review exists, and the Product Owner decisions D1–D10 are open. This is not "Option B complete".

Branch `Khoi/feat/f03-f05-admin-mining`; `origin/dev` = `6d91215` (already contained in the branch since merge `caa54b4`; fetched again 2026-11-02, no newer commit). The diagram work lives on `Khoi/docs/diagrams-27-uml2-f03-f05-sync` (`ae55fd9`) and is **not** part of this release.

## 1. Release scope

| In the release | State |
|---|---|
| F03: lambda policy, Store weights, S/M scores, Product `expiryPushDays` v1.2 binary expiry, preview, immutable snapshots, matrix, expiry-push query, FEFO `expiry_date → received_at → batch_id` | Enabled, verified |
| F05: System Admin mining configuration (versioned, optimistic lock), manual Store-targeted request, HUI-Miner / EFIM, benchmark, durable DB dispatch, lease + heartbeat + fencing, Linux parent-death launcher, Store-scoped reads for Merchandiser and Store Admin, F06 consumption | Enabled, verified |
| Scheduler: schedule CRUD and history, occurrence reservation / lease / retry / reconciliation, dispatcher | Present but **off**: enable → HTTP 503, dispatcher flag default `false` |
| System Admin reads of Store results (5 routes) | Present but **403 by default** (decision D1), per-group gate |
| Legacy `POST /api/mining/requests`, `GET /api/mining/runs/{id}/benchmark` | Forbidden for every role (`denyAll`) |
| Migrations | V13–V17 added; V1–V12 byte-identical to `origin/dev` |
| Optional deployment files | `Dockerfile`, `compose.backend.yml`, `.dockerignore` (image definition only; nothing is deployed; secrets come from required environment variables) |
| Out of release | Diagrams, Frontend, ERD/Draw.io files, `.DS_Store`, scheduling policy decisions |

Files changed against `origin/dev`: 126 at the time of the first audit (src/main: 14 added + 7 modified mining files, 3 utility, 1 security; 21 test classes; 5 migrations; docs; script; CI group list). No `.jar`, `.env`, key, log or binary file; no credential-like string in the diff (scanned).

## 2. Safe defaults (what an instance does when nothing is configured)

| Property / env | Default | Effect |
|---|---|---|
| `paspm.mining.schedule-dispatch-enabled` | `false` | No dispatcher thread polls; enabled schedules in the database are inert |
| enabling a schedule over HTTP | HTTP 503 `Scheduled mining dispatch is not available` | Cannot be changed by any role |
| `paspm.mining.system-store-read.routes` (`PASPM_MINING_SYSTEM_STORE_READ_ROUTES`) | empty | `SNAPSHOTS`, `REQUESTS`, `RUNS`, `PATTERNS` all refused with 403; unknown names stop startup |
| `paspm.mining.spmf-jar`, `paspm.mining.java-executable` | empty | Mining submit returns 503 with a diagnostic prefix; history stays readable |
| `paspm.mining.timeout-millis` / `heap-mib` / `max-patterns` | 60000 / 256 / 2000 | child limits |
| worker | 1 active, 8 queued, poll 1 s, lease 30 s, heartbeat 5 s | per instance |

An operator must not set the first row to `true`, nor list read groups, before the corresponding approvals (see the PO package).

## 3. Safety verification of the disabled scheduler

| Claim | Evidence (current HEAD) |
|---|---|
| Flag default is `false` and a disabled dispatcher never touches reservation | `MiningOccurrenceDispatcherFlagTest` (3 tests, incl. an enabled control that does poll) |
| An **enabled, due** schedule already in the database stays inert with the shipped configuration (no occurrence, no snapshot, no request, dispatcher not started) and the HTTP enable still answers 503 | new `ScheduledMiningDisabledByDefaultTest`; red-checked: with the flag on it fails (`expected 0 but was 1`) |
| Nothing else calls the occurrence processor | source trace: `MiningOccurrenceProcessor.process` is invoked only by `MiningOccurrenceDispatcher` |
| Only System Admin may call schedule routes; no other role can enable | `MiningAdminConfigurationTest` (6 schedule routes × Merchandiser, Store Admin, End Customer = 403), `F03F05RbacMatrixTest` |
| Manual mining unaffected | `MiningIntegrationTest`, `MiningMySqlIntegrationTest`, `MiningHttpMySqlTest` |
| A request already created by an earlier occurrence still completes (worker is independent of the dispatcher flag) | by design; documented, not a new test |

## 4. System Admin permission-gate verification

`SystemStoreReadPolicyTest` (default none, case-insensitive exact names, bad names fail startup), `MiningSystemStoreReadGateTest` (all five 403 by default, also for unknown Stores, other roles 403, 401 anonymous), `MiningSystemStoreReadGranularGateTest` (`REQUESTS,RUNS` opens exactly those), `MiningIntegrationTest` with all groups open (behaviour, Store isolation, 404 cross-Store). The benchmark and manual-request routes are not gated.

## 5. Test evidence

Every row below names the commit it applies to. Skipped tests are not counted as passed.

| Category | Result | Evidence |
|---|---|---|
| Maven verify, Java 17 (H2 suites) | **PASS** 451 run, 0 failed, 0 errors; **155 SKIPPED** (opt-in MySQL/SPMF/multi-JVM/clone classes, executed in the rows below) | `scripts/verify-option-b-macos.sh --run` on `45dbd80`, exit 0, 23/23 stages, `outputs/verification-evidence/core-45dbd80/` |
| Scheduler feature-flag + migration versioning | **PASS** 6 run | same |
| F03/F05 RBAC matrix, route-group policy, scheduler-off | **PASS** (inside the 451) | `F03F05RbacMatrixTest` 3, `ScheduledMiningDisabledByDefaultTest` 1, gate suites |
| CI `backend` group simulated locally | **PASS** 175 run, 0 skipped | `outputs/verification-evidence/ci-backend-group.log` (before the last test-only commit) |
| MySQL 8.4.13 group | **PASS** 51 run, 0 skipped | script stage `test.mysql_group` |
| Full opt-in (MySQL + SPMF + Java 17 backend) | **PASS** 461 run, 0 failed; **10 SKIPPED** (5 multi-JVM, 5 clone-API — run separately below) | stage `test.full_optin` |
| Flyway fresh V1–V17 / Java V11 / restart / upgrade from dev schema | **PASS** | stages `flyway.*`: each version once, history identical after restart, V1–V12 checksums and seeded data unchanged by V13–V17 |
| F04 V12 and F05 V13–V17 compatibility | **PASS** | `flyway.upgrade`, `TransactionRawEvidenceMigrationMySqlTest`, `RecommendationMigrationTest` |
| JPA `ddl-auto=validate` against the fresh schema | **PASS** | stage `jpa.validate` |
| HUI-Miner and EFIM, F03 → F05 → F06 | **PASS** | `MiningIntegrationTest`, `MiningMySqlIntegrationTest`, `MiningHttpMySqlTest`, `RecommendationIntegrationTest` / `RecommendationMySqlIntegrationTest` (config → snapshot → manual request → worker → patterns → generation) |
| Runtime OpenAPI | **PASS** 88 operations, identical to `docs/api/openapi.json` (paths, summaries, components) | stage `openapi.export` + comparison |
| Multi-JVM recovery, macOS | **PASS** 6/6 (exported `45dbd80`, JDK 17, Java 25 child). macOS has no kernel parent-death: the evidence shows `orphan_alive_after_backend_sigkill=true`, so this proves lease/fencing/recovery only | `core-optin/macos/` |
| Clone-API regression, macOS | **PASS** 5/5 (restricted user, one disposable schema) | `core-optin/macos/` |
| Linux Docker (amd64 under emulation on Apple Silicon): multi-JVM, SIGKILL / parent-death, Linux launcher, SPMF engine/HTTP, scheduler MySQL, lease, migration, F06 MySQL, RBAC matrix, scheduler-off | **71 of 72 PASS**. Multi-JVM 6/6 with `orphan_alive_after_backend_sigkill=false`, `reaped_by_container_init=true`, the other instance's SPMF survived, fence 1→2, 8 patterns | `core-linux/` |
| the 1 Linux failure | `SpmfRuntimeTest.unreadableJarAndUnrunnableExecutableAreRejected`: the container user is **root**, for whom `chmod 000` does not make a file unreadable (test assumption, pre-existing on `origin/dev`). Re-run as uid 10001: **PASS** (8 run, 0 failed; 1 skipped because `paspm.test.java17` was not set) | classified environment fault, not a product defect |
| Native Linux hardware / production image | **NOT RUN** | |
| Windows / clean-machine setup | **NOT RUN** | |
| GitHub CI | **NOT RUN** (branch not pushed) | see section 8 |

A first full-script run on `d315d6c` failed `ScheduledMiningDisabledByDefaultTest`: its JVM-wide "no dispatcher thread" assertion saw threads from other test contexts. The product behaviour (zero occurrences) held; the assertion was a test defect and was scoped to the test's own context in `45dbd80`.

## 6. Endpoint inventory, RBAC, migrations

* Operations: 88 in the runtime OpenAPI (F01 24, F02 15, F03 12, F04 6, F05 23, F06 8). Per-operation roles and states: [RBAC matrix](../api/F01_F02_RBAC_MATRIX.md) (F05 rows added), [audit matrix](F03_F05_API_AUDIT_MATRIX_2026-11-02.md), [F03 API](../api/F03_UTILITY_API.md), [F05 API](../api/F05_MINING_API.md).
* Migration history on this branch: V1–V10 SQL, V11 Java, V12 F04 `preserve_large_raw_import_rows` (from `origin/dev`, unchanged), **V13** mining administration config, **V14** scheduled mining tables, **V15** frozen occurrence context, **V16** request leases and fencing, **V17** occurrence recovery. No existing migration file is modified (`git diff origin/dev...HEAD -- src/main/resources/db/migration` lists only the five additions).

## 7. Known limitations

1. System Admin cannot discover `runId` / poll status through System Admin routes until D1 opens `REQUESTS,RUNS`; Store users can.
2. No Store-list endpoint (D2).
3. Scheduler is not operational; lookback, time zone, monthly overflow, catch-up, retry/backoff, change-effect, actor, activation acceptance (D3–D10) are unapproved.
4. `supportCount` is always null (SPMF output has no support); utility is frozen weighted utility, not revenue.
5. Child-process cleanup on kernel parent death is guaranteed only on Linux; macOS and Windows have none.
6. Worker capacity is one request per instance, eight queued; at most 256 Products and 20,000 matrix rows per mining input.
7. The Weight Rules v1.2 source file is not tracked; the API document is the written contract.
8. SPMF is GPLv3 and external; the JAR is pinned by SHA-256 and not bundled.

## 8. CI facts the reviewer needs

* `.github/scripts/test-summary.py` makes the `integration` job fail on **any skipped test**. On `origin/dev` the five `PilotCloneApiIntegrationTest` cases are already skipped there (they need a restricted-user schema and `-Dpaspm.test.jpa-clone=true`, which the workflow does not provide), so this is an inherited condition, not introduced here. Whether the required check is currently green on `dev` could not be checked (no GitHub access).
* This release adds five multi-JVM tests behind `-Dpaspm.test.multijvm=true`; the workflow now passes that flag so the zero-skip gate does not reject them. This change **cannot be verified without a GitHub run**; the same suite passes on Linux in the container above.
* The integration job also needs the repository variable `PASPM_CI_SPMF_ARTIFACT_URL` (an approved SPMF location); `docs/setup/BACKEND_CI.md` records that none existed. Without it that job fails by design (`SPMF_INTEGRATION_CI_BLOCKED`).
* Job and check names were not changed, so branch protection configuration keeps matching.
* The backend group now also runs the RBAC matrix, gate suites, admin configuration and scheduler-off suites (175 tests, 0 skipped locally).

## 9. Deployment and rollback

* Deploy order: back up the database, deploy the application (Flyway applies V13–V17), keep `schedule-dispatch-enabled` unset and `system-store-read.routes` empty, configure `PASPM_MINING_SPMF_JAR` and `PASPM_MINING_JAVA_EXECUTABLE` (Java 25+) if mining is wanted, verify `GET /v3/api-docs`.
* Flyway migrations are forward-only; there are no down scripts. The verified rollback path is restoring the pre-deployment database backup together with the previous application version. Running the previous application against a database that already has V13–V17 was **not tested** (Flyway's treatment of applied-but-unknown migrations was not exercised), so do not rely on it.
* Never run the schedule dispatcher or open read groups on an instance that has not been approved for it; if mining must be stopped, unset the SPMF paths: new submissions return 503 and history remains readable.
* No deployment was performed or authorised.

## 10. Pending Product Owner decisions

D1–D10 in [the package](../decisions/PO_DECISION_PACKAGE_D1_D10_2026-11-01.md) and [register](../decisions/PO_DECISION_REGISTER_F05_2026-11-01.md): none approved, all DECISION boxes blank. No approval evidence was found in the repository.

## 11. Suggested Draft PR description

> **F03/F05 Core: System Admin mining administration, durable dispatch, scheduler present but disabled**
>
> * F03 v1.2 utility (binary expiry, FEFO, immutable snapshots) and F05 System Admin configuration, manual mining, benchmark, lease/fencing worker, Linux parent-death launcher, Store-scoped reads.
> * Flyway V13–V17 added; V1–V12 unchanged. 23 F05 operations (88 total).
> * Scheduler: enable → 503, dispatcher default off, pinned by tests. System Admin Store reads: 403 by default (D1), per-group gate. Legacy submit/benchmark routes: 403.
> * Not included: scheduling policies (D3–D10), Store list (D2), diagrams, Frontend.
> * Evidence: `docs/reports/F03_F05_CORE_RELEASE_READINESS_2026-11-02.md`. macOS script 23/23, MySQL 8.4, multi-JVM and clone tests, Linux Docker multi-JVM/SIGKILL.
> * Reviewer notes: CI section 8 (zero-skip gate, SPMF artifact variable); rollback section 9.

## 12. Option B backlog after this release

Approve D1–D10 → apply policies and the "Needs" work (Store list, catch-up status, service account, alerting) → enable and test schedule activation over real HTTP, weekly/monthly, recovery, multi-container → synchronise docs/OpenAPI → finish the UML 2.0 audit and deployment diagram on the diagram branch → release of the remaining F05 changes. The scheduler code, PO register and package stay in the repository for that work.

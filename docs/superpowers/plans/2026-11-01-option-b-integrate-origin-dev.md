# Option B — Integrate origin/dev and Final Backend Regression Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring `origin/dev` (F04 V12 + F06 hardening) into `Khoi/feat/f03-f05-admin-mining` without losing any uncommitted work, then prove the integrated tree with the Option B verification script.

**Architecture:** HEAD equals the merge base (`c06d43e`), so the branch has **zero commits of its own**; all F03/F05 work is uncommitted. Commit it first (explicit pathspecs), then a normal `git merge origin/dev` (no rebase, no force), resolving the 3 migration-test conflicts by hand.

**Tech Stack:** Git, Maven (Java 17), Flyway 11, MySQL 8.4 (disposable Docker), Java 25 + SPMF 2.67a child, `scripts/verify-option-b-macos.sh`.

**Spec:** user brief "PASPM OPTION B — Safe Integration of origin/dev"; `docs/decisions/PO_DECISION_REGISTER_F05_2026-11-01.md`; `docs/frontend_handoff/OPTION_B_BACKEND_API_HANDOFF.md`.

## Global Constraints

- Phase 2 starts only on **explicit user authorization**; no commit, merge, push or deploy before it.
- No `reset --hard`, `clean`, `stash`, `rebase`, `--force`, `checkout --`, ours/theirs auto-resolution, Flyway repair, or database deletion.
- Flyway order: V1–V10, Java V11, F04 V12 (unchanged, byte-identical to origin/dev), F05 V13–V17 (unchanged).
- Never touch branch `Khoi/docs/diagrams-27-uml2-editable` (`3e369f0`), the untracked 27-page `.drawio`, or the deleted F02 ERD.
- Scheduler enable stays HTTP 503; `paspm.mining.schedule-dispatch-enabled` stays `false`; five System Admin read routes stay PENDING_BUSINESS_APPROVAL.
- Do not edit test expectations to hide a failure; every changed number must be derived from the discovered migration list.
- Run the verification with `PASPM_JAVA17_HOME`, `PASPM_JAVA25_HOME`, `SPMF_JAR_PATH`, `PASPM_EVIDENCE_ROOT` (see memory note `paspm-verify-host-env`).

## Review Focus

- Mode-only changes: the verify script `chmod +x`-ed `.github/scripts/mysql-client.sh` and `provision-restore.py` (CI already does this at runtime); do not commit them by accident.
- `git add -A` / `git add .` would stage `.DS_Store`, the untracked draw.io and the ERD deletion.
- A DB that applied the OLD numbering (V12–V16 = F05) breaks after the merge; it must be recreated, never repaired.
- Dev's `RecommendationIntegrationTest` still used the old merchant route `POST /api/mining/requests` (denyAll here); the textual merge must keep the System Admin `mine()` helper.
- Dev's hardening tests insert `COMPLETED` rows directly into `mining_request`/`mining_run`; V13–V17 columns are nullable or defaulted, so this is compatible (verified statically only).

---

## Assessment record (Phase 1, executed read-only)

| Item | Finding |
|---|---|
| Branch / HEAD | `Khoi/feat/f03-f05-admin-mining` @ `c06d43e` (= merge base); 0 commits ahead, 11 behind `origin/dev` (`6d91215`, confirmed by `git ls-remote`) |
| Index | nothing staged |
| Tracked changes | 32 paths (30 content, 1 deletion = F02 ERD, 2 are mode-only 644→755) |
| Untracked | 90 files (86 status entries) incl. 3 `.DS_Store` and the 27-page draw.io; ignored: `.env`, `.idea/`, `target/` |
| Stash | `stash@{0}` belongs to `Khoi/Fear/jpa-product-metadata-pilot` — untouched, unrelated |
| Local `dev` branch | stale (`c06d43e`); always use `origin/dev` |
| Prunable worktrees under `/tmp` and `/sessions/...` | leave alone; do **not** run `git worktree prune` |
| Overlap (dev-changed ∩ locally dirty) | 6 files: `.github/scripts/test-groups.py`, `docs/frontend_handoff/F05_HANDOFF.md`, `FlywayRecoveryTest`, `JpaPilotMigrationMySqlTest`, `RecommendationMigrationTest`, `RecommendationIntegrationTest` |
| Rehearsal (throwaway copy in `/tmp`, snapshot-commit + `merge --no-commit origin/dev`) | exactly 3 content conflicts; the other 3 overlap files auto-merge and were checked for meaning |
| Flyway order in merged tree | V1…V10, V11.java, V12 (dev), V13…V17; no duplicates; V12 identical to origin/dev; V13–V17 identical to working tree |
| Diagram scope | dev changes no `.drawio`; `3e369f0` = `origin/dev` + 1 commit and holds a **different** 27-page file (961,880 B vs 894,697 B local); it is not an ancestor of HEAD or origin/dev |
| Preservation backup (done) | `outputs/preservation-20261101/` : binary patch of tracked changes (applies via `git apply --check`), tar of the 90 untracked files (no `.env`), status, refs, stash, worktree lists, draw.io sha256 |

### Conflict inventory (3 files, 7 hunks) and resolution rule

Migrations executed after V7 in the merged tree: V8, V9, V10, V11, V12, V13–V17 = **10**; total successful history rows = **17**; V1→latest executes **17**.

| File | Hunks | Branch side | Dev side | Resolution |
|---|---|---|---|---|
| `FlywayRecoveryTest` | 2 | 10 / 10 + 17 rows | 5 / 5 + 12 rows (dev-only tree) | keep **10 / 17**; dev changed numbers only |
| `JpaPilotMigrationMySqlTest` | 3 | 17, 12, `"17"` ×2 | 12, 7, `"12"` ×2 | keep **17 / 12 / "17"**; same reason |
| `RecommendationMigrationTest` | 2 | `.target("12")` pin, asserts 12 and 3 | unpinned, asserts 12 and 3 | **decision needed** (below) |

Decision for `RecommendationMigrationTest` — **A (recommended):** keep dev's unpinned "migrate to latest" semantics and assert **17** (fresh) and **8** (upgrade from V9 = V10…V17) plus current version `"17"`; this also proves F05 migrations preserve the F06 historical snapshot. **B:** keep the branch's `.target("12")` pin, which yields dev's own numbers 12 and 3 but stops covering V13–V17 in this test.

---

### Task 1: Commit the current implementation (needs authorization)

**Files:** none edited; stage by explicit pathspec list built from `git status --porcelain`.

**Interfaces:**
- Produces: local commits C1 (code, tests, migrations, CI helper content, Dockerfile, verify script) and C2 (docs and evidence) on the current branch; branch `backup/f03-f05-pre-merge-20261101` pointing at C2.

- [ ] **Step 1:** Re-run `git status --porcelain=v1` and diff against `outputs/preservation-20261101/git-status-porcelain.txt`; any new path must be reviewed before staging.
- [ ] **Step 2:** `chmod 644 .github/scripts/mysql-client.sh .github/scripts/provision-restore.py` (mode-only noise; CI runs `chmod +x` itself).
- [ ] **Step 3:** `git add -- src/ .github/scripts/test-groups.py Dockerfile .dockerignore compose.backend.yml scripts/verify-option-b-macos.sh`; verify `git diff --cached --stat` has no `.DS_Store`, `.drawio` or `docs/` entries; commit C1.
- [ ] **Step 4:** `git add -- README.md docs/api docs/architecture docs/decisions docs/features docs/frontend_handoff docs/reports docs/setup docs/superpowers docs/testing`; verify nothing under `docs/diagrams/` is staged; commit C2. Both commits end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- [ ] **Step 5:** `git branch backup/f03-f05-pre-merge-20261101`; `git status --porcelain` must now list only the 3 `.DS_Store`, the untracked draw.io and the ERD deletion.

### Task 2: Merge origin/dev (needs authorization)

**Interfaces:**
- Consumes: C1/C2. Produces: one merge commit on the branch.

- [ ] **Step 1:** `git merge --no-ff --no-commit origin/dev`; expected output: conflicts in exactly the 3 files above.
- [ ] **Step 2:** Resolve `FlywayRecoveryTest` and `JpaPilotMigrationMySqlTest` to the branch-side numbers per the table; resolve `RecommendationMigrationTest` per the user's A/B choice. Do not use `-X ours/theirs`.
- [ ] **Step 3:** `git grep -n -E '^(<<<<<<<|=======|>>>>>>>)' -- src docs .github` returns nothing; `git diff --name-only --diff-filter=U` is empty.
- [ ] **Step 4:** Verify discovered migrations: the file list is V1–V10, V11.java, V12, V13–V17 and `git diff origin/dev -- src/main/resources/db/migration/V12__f04_preserve_large_raw_import_rows.sql` is empty; `git diff HEAD -- src/main/resources/db/migration/V13* … V17*` is empty.
- [ ] **Step 5:** commit the merge with a message listing the three resolutions; stop before any push.

### Task 3: Post-integration verification on the real merged tree

- [ ] **Step 1:** `PASPM_EVIDENCE_ROOT=$PWD/../outputs/verification-evidence PASPM_JAVA17_HOME=… PASPM_JAVA25_HOME=… SPMF_JAR_PATH=… scripts/verify-option-b-macos.sh --run`.
- [ ] **Step 2:** Expected: `mvn.verify`, `test.flag`, `test.mysql_group`, `test.full_optin`, `flyway.fresh`, `flyway.upgrade`, `flyway.rerun`, `flyway.v11`, `jpa.validate`, `openapi.export` all PASS; record skipped counts (131 H2-run skips, 10 opt-in skips) as SKIPPED, never as PASS. Do not reuse the earlier combined-tree numbers.
- [ ] **Step 3:** On any FAIL, read `maven.log`/`fresh-start1.log` first and find the root cause (superpowers:systematic-debugging) before touching code.

### Task 4: Release-boundary checks (Phase 4)

- [ ] **Step 1:** Tests that must be green and not skipped: `MiningOccurrenceDispatcherFlagTest` (dispatcher OFF by default, `:false`), `MiningAdminConfigurationTest.schedulesRequireSystemAdminAndValidateStoreAndIanaZone` (enable → 503), `MiningIntegrationTest.enforcesRolesOwnershipPaginationAndStrictInput`, `…systemAdminReadsExplicitStoreSnapshots…WithoutCrossStoreLeak` (Store isolation, 404 cross-Store), `MiningDispatchLeaseTest` (fencing), `MiningIntegrationTest.committedRequestIsDiscoveredWithoutEnqueue` (durable dispatch).
- [ ] **Step 2:** In runtime OpenAPI, `POST /api/mining/requests` and `GET /api/mining/runs/{id}/benchmark` are `deprecated` and answer 403 for every role; the five `/api/system/mining/stores/{storeId}/…` read routes carry the `PENDING_BUSINESS_APPROVAL` summary.
- [ ] **Step 3:** `git diff c06d43e -- src/main/java/vn/edu/fpt/paspm/mining/MiningDispatchLease.java src/main/java/vn/edu/fpt/paspm/mining/MiningWorker.java` shows no change introduced by the merge (merge must not alter lease/fencing code).

### Task 5: OpenAPI and Option B handoff (Phase 5)

- [ ] **Step 1:** Replace `docs/api/openapi.json` with the `openapi.runtime.json` from the Task 3 run (indent 2, trailing newline).
- [ ] **Step 2:** Update `docs/frontend_handoff/OPTION_B_BACKEND_API_HANDOFF.md`: remove "UNVERIFIED/provisional", mark each route VERIFIED or PENDING_BUSINESS_APPROVAL from Task 3/4 evidence, keep D1–D10 pending and the 503 wording.
- [ ] **Step 3:** Report whether the Backend is technically ready for Frontend integration; do not declare Option B complete while D1–D10, real scheduler activation and end-to-end acceptance are open.

## Remaining risks

1. Any local MySQL that applied the old F05 numbering needs to be recreated by its owner (no repair).
2. `RecommendationMigrationTest` A/B choice changes how much migration coverage that test owns.
3. Hand-made merge of 3 tests is unverified until Task 3 runs; the rehearsal proves only that conflicts are limited to these files.
4. `origin/dev` could move before Task 2; re-run `git ls-remote origin refs/heads/dev` and, if changed, repeat the rehearsal.
5. The local untracked draw.io differs from the copy on `3e369f0`; it stays uncommitted here and will conflict (add/add) when the diagram branch is eventually merged — a separate decision.
6. `verify-option-b-macos.sh` mutates tracked file modes on the real tree; consider copying helpers instead of `chmod` in a follow-up.

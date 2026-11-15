# ROC-20261116-008 — Known Flyway checksum mismatches (V20, V7) and schema range in setup docs

| Field | Value |
|---|---|
| Record ID | ROC-20261116-008 |
| Date / timezone | 2026-11-16 / Asia/Ho_Chi_Minh |
| Type | Documentation |
| Features | Shared setup (F01 V20/V21, F04 V7); schema range V1–V25 |
| Branch / base HEAD | `Khoi/docs/flyway-checksum-troubleshooting` from `origin/dev` `3e438f8` (PR #18 and PR #19 merged) |
| Execution status | COMPLETED — documentation only |
| Release status | UNRELEASED — committed on this branch; the push result is reported in the session (this file is written before the push); no PR opened, nothing merged |
| Daily summary | [README of the day](README.md) |

## 1. Request and scope

A team member's backend failed to start with `Migration checksum mismatch for migration version 20 -> Applied to database : 89726244 -> Resolved locally : 1793889682`. Verify the root cause from Git history, document the known mismatches and a safe local fix, and update stale schema-range statements. Only files under `docs/` change; no database is touched.

## 2. Verified facts

| Fact | Command / evidence |
|---|---|
| `origin/dev` = `3e438f8` | `git fetch origin`; `git rev-parse --short origin/dev` |
| `9d33d88` (2026-11-11) on `origin/Khoi/Fear/f01-session-auth-and-change-records` (and others); its V20 creates `auth_session`, `auth_refresh_token` with `csrf_hash CHAR(64)` (lines 2, 12, 15) | `git branch -r --contains 9d33d88`; `git show 9d33d88:src/main/resources/db/migration/V20__f01_refresh_sessions.sql` |
| `42b8a9c` (2026-11-05) V20 creates `auth_refresh_session`, `auth_refresh_token` (lines 1, 16) | `git show 42b8a9c:…/V20__f01_refresh_sessions.sql` |
| Merge `c8b10ba` (2026-11-12) has parents `42b8a9c`, `689526b`; `9d33d88` reaches `dev` through `689526b`, but `dev`'s V20 blob equals `42b8a9c` (`844cd1e`) | `git show -s --format=%p c8b10ba`; `git merge-base --is-ancestor 9d33d88 689526b`; `git rev-parse origin/dev:…/V20__f01_refresh_sessions.sql` |
| V21 adds `csrf_hash BINARY(32)` to `auth_refresh_token` | `src/main/resources/db/migration/V21__f01_refresh_csrf_binding.sql:3` |
| V7: `e10b706` (2026-10-19) first version; `d7aa379` (2026-10-20) current; `dev`'s V7 blob equals `d7aa379` (`647fcec`) | `git log --format='%h %ad %s' -- …/V7__f04_transaction_import_pipeline.sql`; `git rev-parse` |
| Project Flyway version 11.7.2 | `mvn -o dependency:list` → `org.flywaydb:flyway-core:jar:11.7.2` |

## 3. Checksums (all equal the reported values)

Method: each file version was extracted with `git show <commit>:<path>` into a temporary folder, then a 15-line Java class called Flyway 11.7.2's own `org.flywaydb.core.internal.resolver.ChecksumCalculator.calculate(new FileSystemResource(null, file, UTF_8, false))` from `flyway-core-11.7.2.jar` in the local Maven repository (compiled and run with JDK 17). This is the calculator Flyway uses for SQL migrations. No database was used.

| File version | Computed checksum |
|---|---|
| V20 at `9d33d88` (old) | `89726244` |
| V20 at `42b8a9c` and at `origin/dev` (current) | `1793889682` |
| V7 at `e10b706` (old) | `-1762009830` |
| V7 at `d7aa379` and at `origin/dev` (current) | `-1490036030` |

The V7 values also match the existing [V7 recovery](../../setup/FLYWAY_MYSQL_V7_RECOVERY.md) document, line 9.

## 4. Commands checked on a throwaway database

On 2026-11-16 a separate Compose project (`paspmflywaycheck`, copy of `compose.yml` with its own volume, no published port, random throwaway passwords) ran: backup with `docker compose exec -T … mysqldump …` (plain ASCII dump containing the test table), the in-container dump + `docker compose cp` (same content), `DROP DATABASE paspm; CREATE DATABASE paspm;` (volume kept; `SHOW GRANTS FOR paspm` still lists `GRANT ALL PRIVILEGES ON paspm.*`; the `paspm` user created a table), and a restore from the dump. Then `docker compose down -v`: 0 containers and 0 volumes left. `docker compose exec --help` documents `-T, --no-TTY`. The PowerShell syntax was **NOT VERIFIED** (no PowerShell on the machine). **No real, shared or team database was connected to, repaired or dropped.**

## 5. Files changed (all under `docs/`)

| File | Old → new |
|---|---|
| `setup/LOCAL_SETUP_TROUBLESHOOTING.md` | New section "Known Flyway checksum mismatches" (lookup table, reading your values, why `repair` is not a fix, guarded local recreate, prevention); table row links to it; "A destructive reset is intentionally not part of this guide" → reset is not a default fix, the only one is the guarded local recreate; "`dev` carries V13–V17 for F05" → V9, V13–V19 and V24, older text dated |
| `setup/LOCAL_SETUP_COMMON.md` | "the current mainline schema includes V1–V21" → V1–V25 with one line each for V22–V25 (cited); "Scheduler branch (not yet on `dev`) … V1–V19" → on `dev`, dated |
| `setup/LOCAL_SETUP_MACOS.md`, `setup/LOCAL_SETUP_WINDOWS.md` | "`dev` includes Flyway V1–V17. F05 … V13–V17" → V1–V25; F05 V9, V13–V19, V24; link to the new section |
| `setup/F05_SPMF_RUNTIME_SETUP.md` | "through V23 … V23 is not assumed deployed" → through V25 (V24 mining progress, V25 F03 Weight v2); V25 not assumed deployed |
| `setup/STAGING.md` | "applies Flyway V1-V19" → V1–V25 (old value noted) |
| `record-of-changes/2026-11-16/08-…` (this), `2026-11-16/README.md`, `record-of-changes/README.md` | record, day summary, index |

Unchanged on purpose: `DOCUMENTATION_MAP.md` (it lists setup files, not sections, and has no `LOCAL_SETUP_*` entry, so the conditional link does not apply); `setup/BACKEND_CI.md:17` ("FlywayV1–V11" names a CI suite category, not the schema range); `setup/FLYWAY_MYSQL_V7_RECOVERY.md` V1–V9 ranges (dated 2026-10-20 decision); root `README.md` (no migration range).

## 6. Verification (run before commit)

| Check | Result |
|---|---|
| Relative links under `docs/` (all `*.md`, fenced code ignored) | 1,081 checked, **0 broken** |
| `git status` / `git diff --stat` | `docs/` only: 8 modified files + this record |
| `git grep` for absolute local paths (the macOS temp-directory and home-directory prefixes) on the staged tree | **0 hits** |
| `git diff --cached --check` | clean |

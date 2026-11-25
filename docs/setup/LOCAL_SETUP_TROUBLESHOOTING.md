# Local setup troubleshooting

Start with [common setup](LOCAL_SETUP_COMMON.md), then [Windows](LOCAL_SETUP_WINDOWS.md) or [macOS](LOCAL_SETUP_MACOS.md). Never run repair, drop a schema, delete a volume or weaken security as the default fix.

| Symptom | Likely cause → check → safe fix |
|---|---|
| Backend reports wrong Java or `JAVA_HOME` invalid | `java -version` and `javac -version` differ → select JDK 17 for `JAVA_HOME`, prepend its `bin` to PATH, reopen the terminal if necessary. On macOS check `/usr/libexec/java_home -V`; on Windows check `where.exe java`. |
| Maven not found / wrapper cannot execute | Use `sh ./mvnw -version` on macOS and `.\mvnw.cmd -version` in PowerShell. First use needs access to the configured Maven distribution and dependencies; check network/proxy. Do not change system execution policy. |
| Docker daemon unavailable | Run `docker info` and `docker compose version`; start Docker Desktop, wait for it to finish starting and verify host prerequisites. |
| Backend stops at Flyway V11: `You do not have the SUPER privilege and binary logging is enabled` (Error 1419) | MySQL was started without `--log-bin-trust-function-creators=1`. Add it with `compose.override.yml` or `docker run`, see [common setup](LOCAL_SETUP_COMMON.md#mysql-must-run-with---log-bin-trust-function-creators1). |
| MySQL connection refused | `docker compose ps` and `docker compose logs mysql` → start the local MySQL service, then check `DB_URL`, `DB_USER`, matching `DB_PASSWORD` and port. Do not print or share environment secrets. |
| 8080 already in use | macOS: `lsof -nP -iTCP:8080 -sTCP:LISTEN`; Windows: `Get-NetTCPConnection -LocalPort 8080 -State Listen` → stop only your own process or set local `PORT` to another free port. |
| MySQL port 3306 occupied | macOS: `lsof -nP -iTCP:3306 -sTCP:LISTEN`; Windows: `Get-NetTCPConnection -LocalPort 3306 -State Listen` → stop only your own conflicting service or explicitly adjust local Compose port mapping and `DB_URL`. Confirm which database you are accessing. |
| Flyway migration/checksum failure | Inspect startup logs and `flyway_schema_history`; compare with [known checksum mismatches](#known-flyway-checksum-mismatches); confirm the database and branch, backup and review [V7 recovery](FLYWAY_MYSQL_V7_RECOVERY.md). Do not edit an applied migration or use `repair` without a separately approved recovery plan. |
| Missing config / JWT key too short | Check that the variables described in [common setup](LOCAL_SETUP_COMMON.md#configuration-and-defaults) are set **in the Backend process environment**. Compose `.env` does not automatically load into Spring Boot. JWT/OTP secrets need at least 32 UTF-8 bytes; do not print them. |
| 401 vs 403 vs 404 | 401: log in again and use current Bearer token; 403: confirm role and endpoint; 404: check requested ID and Store scope. Inspect `/api/auth/me` using the authorized token; never bypass security or send tokens to others. |
| `SPMF_JAR_NOT_FOUND` or hash mismatch | Verify an **absolute** JAR path exists; use `shasum -a 256` on macOS or `Get-FileHash -LiteralPath ... -Algorithm SHA256` in PowerShell and compare with the approved pin. Obtain the approved artifact from your team; do not substitute the latest download. |
| `SPMF_JAVA_VERSION_UNSUPPORTED` / Java child cannot start | Run the separately configured `PASPM_MINING_JAVA_EXECUTABLE -version`, quoting the full path. It must be Java 25+ while Backend uses JDK 17. Check temp-directory write/delete permissions and [runtime guide](F05_SPMF_RUNTIME_SETUP.md). |
| Windows path contains spaces | Assign complete paths to PowerShell variables; invoke the executable with `& $env:PASPM_MINING_JAVA_EXECUTABLE` and pass each argument separately. Do not use Command Prompt syntax in PowerShell. |
| Apple Silicon vs Intel binary error | Run `uname -m`, select the matching publisher build and check both `java -version` commands; never assume every Mac uses `/opt/homebrew`. |
| Scheduler enable returns 503 | Expected while scheduler dispatch/recovery is incomplete. Read [F05 status](../features/F05/README.md); do not remove the guard. |
| Mining remains PENDING/RUNNING | Inspect request/run status, Backend logs and runtime readiness. Poll/lease periods are code constants, not configurable environment variables. Historical unowned RUNNING requests require the [review procedure](../architecture/F05_LEGACY_RUNNING_RECONCILIATION.md); do not globally reset them. |
| Linux container reports process supervision unavailable | Check that Python 3 and the `ctypes` module are present, Linux `prctl` is allowed and the Java executable is not setuid/setgid or capability-bearing. The Linux parent-death path fails closed rather than launching unprotected. Verify the actual container policy; macOS/Windows do not have this guarantee. |


## Known Flyway checksum mismatches

Verified 2026-11-16 with Flyway 11.7.2 (the version this project resolves: `mvn dependency:list` → `org.flywaydb:flyway-core:jar:11.7.2`), by running Flyway's own `org.flywaydb.core.internal.resolver.ChecksumCalculator` on each file version taken from Git history (`git show <commit>:<path>`). No database was used. Details: [record ROC-20261116-008](../record-of-changes/2026-11-16/11-flyway-checksum-troubleshooting.md).

| Version | Applied checksum (old) | Current checksum | Old file came from | Why it happened | Safe fix |
|---|---|---|---|---|---|
| V20 | `89726244` | `1793889682` | `9d33d88` (2026-11-11), branch `Khoi/Fear/f01-session-auth-and-change-records`: creates `auth_session` and `auth_refresh_token` with `csrf_hash CHAR(64)` (`git show 9d33d88:src/main/resources/db/migration/V20__f01_refresh_sessions.sql`, lines 2, 12, 15) | The database ran the **pre-merge F01 session branch** (2026-11-11 to 2026-11-12). `dev` kept the other V20 from `42b8a9c` (2026-11-05: `auth_refresh_session` + `auth_refresh_token`, lines 1 and 16) through merge `c8b10ba` (2026-11-12, parents `42b8a9c` and `689526b`), which also added V21 | Local disposable database: **recreate the schema** (steps below). Anything else: stop and ask. **Never `repair`** (see below) |
| V7 | `-1762009830` | `-1490036030` | `e10b706` (2026-10-19), first F04 version | The database applied V7 before `d7aa379` (2026-10-20), which back-quoted the reserved word `row_number` in five places without changing the schema ([V7 recovery](FLYWAY_MYSQL_V7_RECOVERY.md), lines 5–9). Which database ran the old file, and when, is **NOT VERIFIED** — only the file history is | Local disposable database: recreate the schema (steps below). Data you need or a shared database: follow [V7 recovery](FLYWAY_MYSQL_V7_RECOVERY.md) State B1 (controlled `repair` after verification and approval) or B2 if `success = 0`. On MySQL 8.4 the old V7 fails with error 1064 (same file, line 5), so a MySQL row with the old checksum is usually a failed one |

The current files on `dev` (`3e438f8`) are the "current" versions: `git rev-parse origin/dev:<path>` equals the blob of `42b8a9c` for V20 and of `d7aa379` for V7.

### How to read your own values

1. The startup log names the version and both values:

   ```
   Migration checksum mismatch for migration version 20
   -> Applied to database : 89726244
   -> Resolved locally    : 1793889682
   ```

2. Read-only check of the history (run it against **your** database; it changes nothing):

   ```sql
   SELECT installed_rank, version, description, checksum, success
   FROM flyway_schema_history ORDER BY installed_rank;
   ```

   With the local Compose database, as in the [macOS](LOCAL_SETUP_MACOS.md) and [Windows](LOCAL_SETUP_WINDOWS.md) guides: `docker compose exec mysql mysql -u paspm -p paspm -e 'SELECT installed_rank,version,description,checksum,success FROM flyway_schema_history ORDER BY installed_rank;'`

If your version and both numbers match a row of the table, follow that row. **If they match no row, do not guess: ask the team** before changing anything.

### Why `repair` is not a fix for V20

`flyway repair` only rewrites the stored checksum in `flyway_schema_history`; it does not change tables ([V7 recovery](FLYWAY_MYSQL_V7_RECOVERY.md), line 18). A database with the old V20 has `auth_session` instead of `auth_refresh_session`, so the session code that reads and writes `auth_refresh_session` (`src/main/java/vn/edu/fpt/paspm/auth/AuthSessionService.java:96`) cannot work, and the next migration V21 (`ALTER TABLE auth_refresh_token ADD COLUMN csrf_hash BINARY(32) NULL`, `src/main/resources/db/migration/V21__f01_refresh_csrf_binding.sql:3`) fails because the old V20 already created `csrf_hash`. Login and refresh would break. General rule: **`repair` never fixes a schema that differs from the migrations**; it is only for history metadata when the schema is already proven identical (V7 State B1).

### Safe fix — local, disposable Compose database only

The local database is the `mysql` service of `compose.yml` with database and user `paspm` (`compose.yml:2-6`), a named volume `paspm_mysql` (`compose.yml:10`) and port `127.0.0.1:3306` (`compose.yml:9`).

> **Stop and ask** if the database holds data you need, or is shared, staging or production. Do not drop it and do not use `repair`.

1. **Confirm it is your own local database.** Your `DB_URL` points to `localhost:3306/paspm` or `127.0.0.1:3306/paspm` ([common setup](LOCAL_SETUP_COMMON.md), line 18), `docker compose ps` lists your `mysql` service, and nobody else uses it.
2. **Back up.** `-T` disables the pseudo-TTY that `docker compose exec` allocates by default (`docker compose exec --help`), so the dump is written unchanged.

   macOS / Linux (bash, zsh):

   ```bash
   docker compose exec -T mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines paspm' > backup_paspm_<date>.sql
   ```

   Windows (PowerShell): avoid redirecting the dump through PowerShell, which may change the file encoding; write it inside the container and copy it out.

   ```powershell
   docker compose exec -T mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines paspm > /tmp/backup_paspm.sql'
   docker compose cp mysql:/tmp/backup_paspm.sql .\backup_paspm_<date>.sql
   ```

   `MYSQL_ROOT_PASSWORD` is read inside the container (set from your `.env` by `compose.yml:8`); the single quotes keep your shell from expanding it.
3. **Recreate only the schema:**

   ```bash
   docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE paspm; CREATE DATABASE paspm;"'
   ```

   This keeps the Docker volume and the `paspm` user with its `GRANT ALL PRIVILEGES ON paspm.*`. Do **not** run `docker compose down -v` or delete the volume.
4. **Start the backend on `dev`.** Flyway applies V1–V26 from scratch (V11, V25 and V26 are Java migrations under `src/main/java/db/migration/`). With `SPRING_PROFILES_ACTIVE=dev` the demo Stores, accounts, products and inventory are seeded again ([common setup](LOCAL_SETUP_COMMON.md), lines 10 and 24; `src/main/java/vn/edu/fpt/paspm/catalog/CatalogDevSeeder.java:23`).

Steps 2 and 3 were run on a throwaway Compose project with its own volume and no published port, then removed (2026-11-16, record above): the bash and the in-container backups both contained the test table, the dump restored, and after step 3 the `paspm` user could still create tables. The PowerShell syntax itself is **NOT VERIFIED** (no PowerShell available); it uses the same single-quoted form as the [Windows guide](LOCAL_SETUP_WINDOWS.md), line 82.

### Prevention

- Never edit a migration that any database has already applied; add a new one.
- Announce the next migration number to the team before you use it.
- On a branch that adds migrations, point `DB_URL` at a separate local schema (for example `.../paspm_<branch>`), so `paspm` only ever sees `dev` migrations.
- After pulling `dev` with new migrations: a local database that ran an **earlier, pre-merge version** of any of them must be recreated the same way (backup, then steps 1–4).

To stop local services without deleting the named volume: `docker compose down`. A destructive reset is not a default fix; the only reset in this guide is the guarded local-schema recreate in [Known Flyway checksum mismatches](#known-flyway-checksum-mismatches).

`dev` carries the F05 migrations V9, V13–V19 and V24 (V7, V12 and V22 are F04); see [common setup](LOCAL_SETUP_COMMON.md) for V1–V26. The text that follows dates from 2026-11-01, when `dev` had V13–V17. **Renumbering notice (2026-11-01):** earlier builds of this branch numbered the F05 migrations V12–V16. A *disposable local* database that already applied them will fail Flyway validation (history shows V12 `mining_administration_config`). Recreate that local schema (it holds no shared data); do not use `repair` and never touch a shared, staging or production database this way. Verify schema identity and Flyway history before investigating a migration error. Never use `repair` or reset as a first response. Linux Docker image acceptance is still pending; the scheduler activation 503 is expected.

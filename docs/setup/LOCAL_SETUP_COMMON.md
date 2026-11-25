# Local setup — common reference

This guide describes `dev` as of PR #11 (`11973a0`). Branches that are not merged, and uncommitted local changes, will not appear in a fresh clone. Windows and macOS fresh-machine procedures have **not** been fully verified on their target OS; see [Windows](LOCAL_SETUP_WINDOWS.md), [macOS](LOCAL_SETUP_MACOS.md), and [troubleshooting](LOCAL_SETUP_TROUBLESHOOTING.md).

## What you need

- Git; a full JDK 17 for Spring Boot and Maven. The repository supplies Maven Wrapper 3.3.4 and downloads Maven 3.9.9 on first use; network access or a prepopulated trusted Maven cache is needed. Use `mvnw.cmd` in PowerShell and `sh ./mvnw` in zsh. The latter does not depend on executable file permissions.
- Docker Desktop with Compose for the supplied local MySQL 8.4 and Mailpit services. Follow Docker's installation guide for [Windows](https://docs.docker.com/desktop/setup/install/windows-install/) or [Mac](https://docs.docker.com/desktop/setup/install/mac-install/). The repo has no Backend Dockerfile/service; run Backend on the host.
- For F05 mining only: separately installed Java 25+ executable and the approved SPMF 2.67a JAR. Obtain the exact artifact from your team's approved source; the [upstream page](https://www.philippe-fournier-viger.com/spmf/download.php) can change and the latest JAR is not necessarily approved. The JAR is not in this Backend repository. Verify SHA-256 `4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a` and banner `-- SPMF version 2.67a --`. See [SPMF setup](F05_SPMF_RUNTIME_SETUP.md). Never commit binaries or machine-specific runtime paths.
- The sample `dev` profile seeds demo Stores, accounts, products and inventory. Use it **only in an isolated local database**; it is not necessary to start the application and must not be used with real data.

## Configuration and defaults

Compose reads `.env`; Spring Boot does **not** automatically import it. `.env` is Git-ignored. Copy `.env.example` only if `.env` does not already exist; fill local-only values. `compose.yml` fixes database and user names to `paspm` and binds MySQL to `127.0.0.1:3306`. It requires `DB_PASSWORD` and `MYSQL_ROOT_PASSWORD`; changing those values later does not reconfigure an already initialized volume.

| Name | Use and default |
|---|---|
| `DB_URL` | Backend JDBC URL; default `jdbc:mysql://localhost:3306/paspm?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`. Set explicitly to `127.0.0.1` if needed. |
| `DB_USER`, `DB_PASSWORD` | MySQL identity; user defaults to `paspm`; password must be set. Use the same password in Compose and the Backend process. |
| `MYSQL_ROOT_PASSWORD` | Required by Compose for local MySQL root. It is not a Backend setting. |
| `JWT_SECRET`, `OTP_SECRET` | Required separate local secrets, at least 32 UTF-8 bytes each. Generate private values; do not commit, paste or share them. |
| `BOOTSTRAP_EMAIL`, `BOOTSTRAP_PASSWORD` | Optional together. On first startup create an active SYSTEM_ADMIN only if that email does not already exist. Password: at least 12 characters including uppercase, lowercase and digit. Changing environment variables does not reset an existing account. |
| `PORT` | Backend HTTP port, defaults to 8080. |
| `SPRING_PROFILES_ACTIVE` | Optional Spring setting. `dev` enables the publicly known demo accounts and sample data; only use on an isolated database. No profile is required by default. |
| `PASPM_MINING_SPMF_JAR`, `PASPM_MINING_JAVA_EXECUTABLE` | Absolute JAR and **Java 25+** executable paths for mining. Both default empty. `JAVA_HOME` selects the separate Backend JDK 17 and does not configure these. Missing/invalid mining runtime rejects new mining requests with 503 while other APIs remain available. |
| `PASPM_MINING_HEAP_MIB`, `PASPM_MINING_TIMEOUT_MILLIS`, `PASPM_MINING_MAX_PATTERNS` | Optional: defaults 256 MiB, 60000 ms, 2000; see [SPMF setup](F05_SPMF_RUNTIME_SETUP.md) for bounds. |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_TLS`, `MAIL_FROM` | Optional mail settings. Defaults are localhost:1025, no authentication/TLS, and `no-reply@paspm.local`, matching Compose Mailpit. |
| `TOKEN_TTL_SECONDS`, `OTP_TTL_SECONDS`, `OTP_COOLDOWN_SECONDS` | Access JWT defaults to 3600 seconds (valid range 60–3600); OTP defaults are 600 and 60 seconds. |
| `REFRESH_TOKEN_ABSOLUTE_TTL_SECONDS`, `REFRESH_TOKEN_IDLE_TTL_SECONDS` | Refresh session defaults to 30 days absolute and 7 days idle; absolute range is 1 hour–90 days, idle must be at least 60 seconds and no longer than absolute. |
| `REFRESH_COOKIE_SECURE` | Defaults to `true`; keep true in HTTPS deployments. For local plain-HTTP browser testing only, set `false` (cookie names lose the `__Host-` prefix and Secure attribute). |
| `PASPM_MEDIA_ROOT` | Optional root for Store icons and banners; default is `.paspm/media` under the launching user's home (DEC-20261123-01). |
| `PROFILE_IMAGE_DIRECTORY` | Optional local avatar directory; default is `.paspm/profile-images` under the launching user's home (unchanged). The Docker image sets it to `/var/lib/paspm/media/avatars`, on the same volume as Store images. |
| `PASPM_STORE_TIME_ZONE` | Optional Store-time-zone override; application code defaults to `Asia/Ho_Chi_Minh`. |
| `GOOGLE_CLIENT_ID` | Optional; empty by default. |

Current worker capacity is one active request and eight queued per instance; polling is one second, lease 30 seconds, heartbeat five seconds. These values are currently constants, not supported environment settings. Linux Docker process supervision is under verification; Windows/macOS development does not have the Linux parent-death guarantee. Scheduler activation is deliberately **503**, not a setup error. Successful startup does not certify scheduler or process-recovery acceptance.

## MySQL runs with `--log-bin-trust-function-creators=1`

Migration V11 creates triggers. The application user `paspm` has no `SUPER` privilege and MySQL 8.4 enables binary logging, so MySQL must run with this server option; without it Flyway stops at V11 with `You do not have the SUPER privilege and binary logging is enabled` (Error 1419) and the Backend does not start.

**With Compose, nothing to do:** the option is already in the `mysql` service of `compose.yml` (`command: ["--log-bin-trust-function-creators=1"]`). If you started MySQL from an older `compose.yml`, run `docker compose up -d mysql mailpit` again: Compose recreates the container with the option, and your named volume `paspm_mysql` and its data are kept. Check it with `docker compose exec mysql mysql -uroot -p -e "SELECT @@log_bin_trust_function_creators"` (enter `MYSQL_ROOT_PASSWORD`; the value must be `1`).

**Without Compose (plain `docker run`, a disposable database).** Pass the option after the image name. Replace the two passwords with local-only values.

macOS / Linux (bash, zsh):

```bash
docker run -d --name paspm-mysql -p 127.0.0.1:3306:3306 \
  -e MYSQL_ROOT_PASSWORD=<local-root-password> -e MYSQL_DATABASE=paspm \
  -e MYSQL_USER=paspm -e MYSQL_PASSWORD=<local-db-password> \
  mysql:8.4 --log-bin-trust-function-creators=1
```

Windows (PowerShell):

```powershell
docker run -d --name paspm-mysql -p 127.0.0.1:3306:3306 `
  -e MYSQL_ROOT_PASSWORD=<local-root-password> -e MYSQL_DATABASE=paspm `
  -e MYSQL_USER=paspm -e MYSQL_PASSWORD=<local-db-password> `
  mysql:8.4 --log-bin-trust-function-creators=1
```

Tests that create their own disposable MySQL (`-Dpaspm.test.mysql=true`) connect as root and do not need the option.

If the Backend already failed at V11 on your local database, the migration may be half applied. For a disposable local database follow the reset in [troubleshooting](LOCAL_SETUP_TROUBLESHOOTING.md#safe-fix--local-disposable-compose-database-only); otherwise ask before touching it.

## Flyway, accounts and verification

Flyway runs automatically on Backend startup; the current mainline schema (after the P5/P6 merge, 2026-11-16) includes **V1–V26** (V11, V25 and V26 are Java migrations in `src/main/java/db/migration/`). V20 adds F01 refresh sessions and V21 their CSRF binding; F05 migrations are V9, V13–V19 and V24; V12 is F04 `f04_preserve_large_raw_import_rows`. Added after V21:

- **V22** — F04 asynchronous imports: idempotency, timestamps and lease columns on `import_batch` (`src/main/resources/db/migration/V22__f04_async_transaction_imports.sql:1-10`).
- **V23** — F07/F08 layouts and campaigns: `campaign`, `storefront_layout`, `layout_placement`, `comparison_report` and related tables (`src/main/resources/db/migration/V23__f07_f08_layout_campaign.sql:7`, `:47`, `:102`, `:131`).
- **V24** — F05 mining progress: `mining_request.stage`, `stage_started_at`, `current_run_id` (`src/main/resources/db/migration/V24__f05_mining_progress.sql:2-4`).
- **V25** — F03 Weight v2: `inventory_batch.push_day`, `season`, `product_season`, `utility_snapshot_season`, `utility_weight_v2_conversion`, and a one-time conversion of live Store weight settings (`src/main/java/db/migration/V25__f03_weight_v2_expiry_season.java:21`, `:24`, `:46`, `:59`, `:80`).
- **V26** — removes the legacy product columns `expiry_push_days`, `stock_priority_score` and `seasonal_priority_score`; back up valued databases before upgrading (`src/main/java/db/migration/V26__f03_drop_legacy_product_scoring_columns.java:18`).

A local database that ran a pre-merge version of any migration fails validation with a checksum mismatch: see [known checksum mismatches](LOCAL_SETUP_TROUBLESHOOTING.md#known-flyway-checksum-mismatches). Hibernate validates rather than creates schema. On a **new isolated schema**, inspect startup logs and `flyway_schema_history` for successful migrations. For an existing database check its identity and migration history first; see [V7 recovery](FLYWAY_MYSQL_V7_RECOVERY.md). Do not run `repair`, delete a schema or remove a volume as a routine fix.

**Scheduler (on `dev` since its merge; this sentence said "not yet on `dev`" when written on 2026-11-02):** Flyway V18 (SKIPPED status, `skip_reason`, `failure_message`) and V19 (role `SYSTEM_SCHEDULER` and its credential-free machine user) are part of `dev` (`src/main/resources/db/migration/V18__scheduler_skipped_status_and_failure_visibility.sql`, `src/main/resources/db/migration/V19__system_scheduler_principal.sql`). **Scheduled Mining stays off by default.** To exercise it locally set `PASPM_MINING_SCHEDULE_DISPATCH_ENABLED=true` (property `paspm.mining.schedule-dispatch-enabled`), keep `PASPM_STORE_TIME_ZONE` unset or `Asia/Ho_Chi_Minh`, and configure the SPMF jar and Java 25 executable ([F05_SPMF_RUNTIME_SETUP.md](F05_SPMF_RUNTIME_SETUP.md)). Without the flag, enabling a schedule answers 503 and no background thread starts. Never set the flag in a shared or production environment before release sign-off.

Bootstrap SYSTEM_ADMIN logs in via Swagger `POST /api/auth/login` with the locally configured email/password, then uses the returned `accessToken` in **Authorize**. `GET /api/auth/me` verifies the token. `GET /api/system/utility/lambda-policy` checks an F03 System Admin endpoint; `GET /api/system/mining/config` checks F05 administration. Missing token is 401; an authenticated wrong role is 403. Do not paste tokens into reports.

For Store-scoped F03 calls, use a Store Admin or Merchandiser assigned to the target Store. The dev profile creates a demo Merchandiser account `merchandiser@dev.paspm.local` with the publicly documented **development-only** password `PaspmDev123!`; do not use it outside an isolated local database. Log in as that user and call `GET /api/utility/config`. Real snapshot/mining verification also requires valid eligible F04 sales transactions, a READY snapshot, mining configuration and the approved SPMF runtime. A fresh database does not automatically contain those transactions; use the [F04 contract](../api/F04_TRANSACTION_API.md) if present or Swagger and the [F05 contract](../api/F05_MINING_API.md) to prepare them. Configure the mining policy as SYSTEM_ADMIN, then submit the System Admin Store-targeted request. Do not assume example fixture counts apply to your data.

To exercise both algorithms through Swagger after preparing eligible data: as SYSTEM_ADMIN read `GET /api/system/mining/config`, then send `PUT /api/system/mining/config` with the returned `version` as `expectedVersion`, for example `{"algorithm":"HUI_MINER","minUtilMode":"ABSOLUTE","minUtilValue":30,"expectedVersion":0}` when the current version really is 0. Pick a threshold valid for your own snapshot. As the Store's MERCHANDISER, create a READY snapshot with `POST /api/utility/snapshots` for the imported date range, then switch back to SYSTEM_ADMIN and submit `POST /api/system/mining/requests` with `{"storeId":1,"snapshotId":1,"requestKey":"a9657ac1-293d-420c-9c99-4df0349bdc91","compareAlgorithms":true}`. Replace both IDs with real IDs and generate a **new UUID** for each request (`uuidgen` on macOS; `[guid]::NewGuid()` in PowerShell). This asks for HUI-Miner and EFIM; inspect both run statuses, patterns and comparison status using the current [F05 API](../api/F05_MINING_API.md). A 503 can mean that the JAR/runtime is unavailable; it is not a successful test.

The `dev,utility-fixture` profiles have additional explicit password/date requirements and synthetic data; they are QA-only and not required for first startup. Test results and the disabled scheduler are documented in the [current F05 feature status](../features/F05/README.md).

## Browser session integration (working-tree update 2026-11-11)

V20 adds persisted refresh sessions. For IntelliJ local HTTP, set the following in **Run Configuration → Environment variables** (the `.env` file is not automatically loaded):

```text
REFRESH_COOKIE_SECURE=false
PASPM_SESSION_ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000
```

Leave `cookie-secure` at its default true for HTTPS deployment. Use the dev policy: TOKEN_TTL_SECONDS defaults3600, REFRESH_TOKEN_ABSOLUTE_TTL_SECONDS defaults2592000 and REFRESH_TOKEN_IDLE_TTL_SECONDS defaults604800; refresh extends idle expiry only up to absolute expiry. The earlier PASPM_SESSION_REFRESH_TTL_SECONDS setting is not supported by the reconciled build. Do not put JWT/OTP/database secrets in Frontend environment variables. With the existing FE_Kien Vite proxy, open http://localhost:3000, sign in and reload: the browser restores credentials through POST `/api/auth/refresh` and GET `/api/auth/me`. Logout is now a server operation. See [session contract](../api/F01_AUTH_USER_API.md#session-integration-update--2026-11-11) and [executed verification](../testing/reports/F01_SESSION_VERIFICATION.md).

If another Backend is listening on 8080, stop the known local process before starting IntelliJ. Do not run two Backends on the same port. Flyway applies V20 on startup; back up a development database before upgrading. No repair, reset or edit of applied migrations is needed for this change.

V21 adds server-side CSRF binding to published V20. Existing V20 refresh cookies require a new sign-in; no historical token is fabricated. A database that applied the competing local V20 has different tables and checksum: **do not start this build against it or run repair automatically**. Use a fresh/disposable database or a separately approved conversion procedure. This reconciliation task does not modify that local source database.

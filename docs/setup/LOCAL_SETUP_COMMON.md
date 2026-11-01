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
| `TOKEN_TTL_SECONDS`, `OTP_TTL_SECONDS`, `OTP_COOLDOWN_SECONDS` | Optional authentication timing overrides; defaults are 900, 600 and 60 seconds. |
| `PROFILE_IMAGE_DIRECTORY` | Optional local avatar directory; default is `.paspm/profile-images` under the launching user's home. |
| `PASPM_STORE_TIME_ZONE` | Optional Store-time-zone override; application code defaults to `Asia/Ho_Chi_Minh`. |
| `GOOGLE_CLIENT_ID` | Optional; empty by default. |

Current worker capacity is one active request and eight queued per instance; polling is one second, lease 30 seconds, heartbeat five seconds. These values are currently constants, not supported environment settings. Linux Docker process supervision is under verification; Windows/macOS development does not have the Linux parent-death guarantee. Scheduler activation is deliberately **503**, not a setup error. Successful startup does not certify scheduler or process-recovery acceptance.

## Flyway, accounts and verification

Flyway runs automatically on Backend startup; `dev` includes V1–V17. F05 mining migrations are V13–V17; V12 is F04 `f04_preserve_large_raw_import_rows`. Hibernate validates rather than creates schema. On a **new isolated schema**, inspect startup logs and `flyway_schema_history` for successful migrations. For an existing database check its identity and migration history first; see [V7 recovery](FLYWAY_MYSQL_V7_RECOVERY.md). Do not run `repair`, delete a schema or remove a volume as a routine fix.

Bootstrap SYSTEM_ADMIN logs in via Swagger `POST /api/auth/login` with the locally configured email/password, then uses the returned `accessToken` in **Authorize**. `GET /api/auth/me` verifies the token. `GET /api/system/utility/lambda-policy` checks an F03 System Admin endpoint; `GET /api/system/mining/config` checks F05 administration. Missing token is 401; an authenticated wrong role is 403. Do not paste tokens into reports.

For Store-scoped F03 calls, use a Store Admin or Merchandiser assigned to the target Store. The dev profile creates a demo Merchandiser account `merchandiser@dev.paspm.local` with the publicly documented **development-only** password `PaspmDev123!`; do not use it outside an isolated local database. Log in as that user and call `GET /api/utility/config`. Real snapshot/mining verification also requires valid eligible F04 sales transactions, a READY snapshot, mining configuration and the approved SPMF runtime. A fresh database does not automatically contain those transactions; use the [F04 contract](../api/F04_TRANSACTION_API.md) if present or Swagger and the [F05 contract](../api/F05_MINING_API.md) to prepare them. Configure the mining policy as SYSTEM_ADMIN, then submit the System Admin Store-targeted request. Do not assume example fixture counts apply to your data.

To exercise both algorithms through Swagger after preparing eligible data: as SYSTEM_ADMIN read `GET /api/system/mining/config`, then send `PUT /api/system/mining/config` with the returned `version` as `expectedVersion`, for example `{"algorithm":"HUI_MINER","minUtilMode":"ABSOLUTE","minUtilValue":30,"expectedVersion":0}` when the current version really is 0. Pick a threshold valid for your own snapshot. As the Store's MERCHANDISER, create a READY snapshot with `POST /api/utility/snapshots` for the imported date range, then switch back to SYSTEM_ADMIN and submit `POST /api/system/mining/requests` with `{"storeId":1,"snapshotId":1,"requestKey":"a9657ac1-293d-420c-9c99-4df0349bdc91","compareAlgorithms":true}`. Replace both IDs with real IDs and generate a **new UUID** for each request (`uuidgen` on macOS; `[guid]::NewGuid()` in PowerShell). This asks for HUI-Miner and EFIM; inspect both run statuses, patterns and comparison status using the current [F05 API](../api/F05_MINING_API.md). A 503 can mean that the JAR/runtime is unavailable; it is not a successful test.

The `dev,utility-fixture` profiles have additional explicit password/date requirements and synthetic data; they are QA-only and not required for first startup. Test results and the disabled scheduler are documented in the [current F05 feature status](../features/F05/README.md).

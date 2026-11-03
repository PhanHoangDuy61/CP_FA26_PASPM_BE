# Staging deployment (Linux server, Docker Compose)

Status: **procedure rehearsed locally on Docker Desktop (macOS, Apple silicon); never run on a real server.** No staging server
exists yet. See [Rehearsal evidence](#rehearsal-evidence-2026-11-04) for what was and was not exercised.

Files used (all in the repository root unless noted):

| File | Purpose |
|---|---|
| `Dockerfile` | Backend image: Temurin 17 JRE for the backend, a separate Java 25 JRE and the pinned `spmf.jar` for the SPMF child process |
| `compose.yml` | MySQL 8.4 and Mailpit (the SMTP server that receives the OTP e-mails) |
| `compose.backend.yml` | The backend service (image from `PASPM_BACKEND_IMAGE`, port bound to `127.0.0.1`) |
| `compose.staging.yml` | Staging overrides: MySQL flag required by migration V11, optional Swagger switches |
| `.env.staging.example` | Placeholder variables for the server `.env` |
| `scripts/staging/build-image.sh` | JAR + Java 25 runtime + SPMF checksum + `docker build` |
| `scripts/staging/healthcheck.sh` | Post-deployment checks |
| `scripts/staging/backup-db.sh`, `restore-db.sh` | Pre-deployment backup and rollback restore |
| `scripts/seed/` | Demo seed (accounts, store, products, inventory, two transaction files) |

## 1. Prerequisites

* Linux server (Ubuntu 22.04/24.04 or similar), at least 4 GB RAM (the backend container is limited to 1 GB and each mining run
  uses a 256 MiB heap), 20 GB free disk, a user that may run Docker.
* Docker Engine 24+ with the Compose v2 plugin (`docker compose version`); BuildKit is the default builder.
* `git`, `curl`, `python3` (3.9+, used by the seed script), `gzip`.
* To build on the server: JDK **17** (Temurin) and Maven 3.9. Alternatively build the JAR elsewhere and copy
  `target/paspm-backend-0.1.0.jar` to the same path on the server (use `--skip-jar`).
* The approved **SPMF 2.67a** `spmf.jar`, SHA-256 `4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a`
  (the build refuses any other file; see `docs/setup/F05_SPMF_RUNTIME_SETUP.md` and `.github/scripts/provision-spmf.py`).
* Internet access to Docker Hub for `mysql:8.4`, `axllent/mailpit:v1.27.8`, `eclipse-temurin:17-jre-jammy` and
  `eclipse-temurin:25-jre-jammy`.
* Ports `3306`, `1025`, `8025` and the backend port (default `8080`) free on `127.0.0.1`. All published ports are bound to
  loopback only; reach the backend through an SSH tunnel (`ssh -L 8080:127.0.0.1:8080 user@server`) or put a TLS reverse proxy in
  front of it (not part of this kit).
* Do **not** point any of this at an existing MySQL server that holds real data. The compose stack creates its own database volume.

## 2. Configuration (`.env`)

```bash
git clone https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE.git && cd CP_FA26_PASPM_BE
git checkout review-2            # the tag, or the commit that was approved for staging
cp .env.staging.example .env && chmod 600 .env
$EDITOR .env
```

| Variable | Meaning |
|---|---|
| `DB_PASSWORD` | Password of the MySQL application user `paspm` |
| `MYSQL_ROOT_PASSWORD` | MySQL root password (used by backup, restore and the seed script) |
| `JWT_SECRET`, `OTP_SECRET` | At least 32 bytes each (`openssl rand -hex 32`), different from each other |
| `BOOTSTRAP_EMAIL`, `BOOTSTRAP_PASSWORD` | First System Admin, created only when the `app_user` table is empty; 12-72 characters with lower case, upper case and a digit |
| `PASPM_BACKEND_IMAGE` | Image to run, for example `paspm-backend:2026-11-04-<commit>`; keep the previous tag for rollback |
| `PASPM_BACKEND_HOST_PORT` | Host port on `127.0.0.1` (default 8080) |
| `COMPOSE_PROJECT_NAME` (optional) | Compose project name; change it to run a second stack side by side |
| `SPRINGDOC_API_DOCS_ENABLED`, `SPRINGDOC_SWAGGER_UI_ENABLED` (optional) | `false` hides `/v3/api-docs` and `/swagger`, which are public by default |

Spring Boot does not read `.env`; compose passes the values into the containers. Scheduled mining stays off:
`paspm.mining.schedule-dispatch-enabled` is not set by any compose file and defaults to `false`, so enabling a schedule answers
HTTP 503. Do not change that without the Product Owner's approval.

## 3. Build the JAR and the image

```bash
export JAVA_HOME=/path/to/jdk-17
export SPMF_JAR_PATH=/path/to/spmf.jar
TAG=$(date -u +%Y%m%d)-$(git rev-parse --short HEAD)
scripts/staging/build-image.sh "$TAG"          # add --skip-jar to reuse target/paspm-backend-0.1.0.jar
```

The script runs `mvn -DskipTests package` (tests are run separately by CI and `mvn verify`), copies the Linux **Java 25** JRE out of
`eclipse-temurin:25-jre-jammy`, verifies the SPMF checksum and runs `docker build` with the two named contexts the Dockerfile
expects (`java25`, `spmf`). The architecture is that of the build host (use `PLATFORM=linux/amd64` to force it). Then set
`PASPM_BACKEND_IMAGE=paspm-backend:$TAG` in `.env`.

## 4. Start the services

Before any deployment on a server that already has data, take the backup first (section 7).

```bash
docker compose -f compose.yml -f compose.backend.yml -f compose.staging.yml up -d
docker compose -f compose.yml -f compose.backend.yml -f compose.staging.yml ps
```

On an empty volume the backend applies Flyway V1-V19 and creates the bootstrap System Admin. If port 3306 is already used on the
host, add a fourth file with `services: { mysql: { ports: !override ["127.0.0.1:33306:3306"] } }` (needs Compose 2.24+).

## 5. Seed the demo data (empty database only)

```bash
cp scripts/seed/.env.seed.example scripts/seed/.env.seed && chmod 600 scripts/seed/.env.seed
$EDITOR scripts/seed/.env.seed     # SEED_SYSADMIN_* must equal BOOTSTRAP_*; set SEED_ACCOUNT_PASSWORD
scripts/seed/seed.sh
```

The script uses the REST API for accounts (invitation and self-registration, OTP codes read from Mailpit), category, products,
inventory batches and the two CSV imports. The only direct SQL is one `INSERT INTO store`, because the backend has no endpoint
that creates a store. The product rows are parsed from `scripts/seed/data/FoodMart_Product_Direct_Insert.sql` (the SQL file itself is
not executed). Running it again changes nothing (see the evidence below). The OTP codes are read from Mailpit, so the stack must be the
compose one (a real SMTP server cannot be read back). Seeded data: 1 store, 4 accounts (one per role; the System Admin is the bootstrap account) plus the internal scheduler account,
1 category, 1,559 products with one batch of 1,000 units each, and 4,141 imported orders (18,319 order lines, 3 days of October 2026).

## 6. Health check

```bash
HC_ADMIN_EMAIL=<BOOTSTRAP_EMAIL> HC_ADMIN_PASSWORD=<BOOTSTRAP_PASSWORD> scripts/staging/healthcheck.sh
```

It checks that MySQL is healthy and the backend container runs, that `GET /api/auth/me` without a token answers 401 (the backend is up
and Spring Security is active), that the latest Flyway version is applied without failed rows, and that the System Admin can log in and
call `GET /api/system/stores`. Exit code 0 means every check passed. There is no actuator endpoint in the backend.
To confirm the safety gate: `PUT /api/system/mining/schedules/{id}` with `"enabled": true` must answer `503`.

## 7. Rollback

Flyway has **no down migrations**. An older image started on a database that a newer release already migrated would run against a
schema it was not written for (Flyway's default settings ignore newer applied migrations instead of refusing to start; this was not
tested), so rolling back the image alone is safe only when the new release applied no migration. Procedure:

1. **Before every deployment**: `scripts/staging/backup-db.sh` (writes `backups/paspm-<UTC time>.sql.gz` and prints its SHA-256; keep a copy
   off the server). Keep the previous image (`docker image ls paspm-backend`).
2. Deploy the new tag (sections 3 and 4).
3. If it fails or must be withdrawn:

```bash
sed -i 's#^PASPM_BACKEND_IMAGE=.*#PASPM_BACKEND_IMAGE=paspm-backend:<previous-tag>#' .env
docker compose -f compose.yml -f compose.backend.yml -f compose.staging.yml stop backend
scripts/staging/restore-db.sh backups/paspm-<timestamp>.sql.gz --yes      # DROPS database paspm, then loads the backup
docker compose -f compose.yml -f compose.backend.yml -f compose.staging.yml up -d backend
scripts/staging/healthcheck.sh
```

   Data entered after the backup is lost; that is the price of having no down migrations. If the new release applied **no** migration
   (compare `SELECT MAX(version+0) FROM flyway_schema_history` before and after), the database restore can be skipped.

To start from scratch, `docker compose ... down -v` also deletes the MySQL volume.

## Rehearsal evidence (2026-11-04)

Host: macOS, Docker Desktop 28.4.0 (Compose 2.39.2), Apple silicon (arm64). Commit `5b30eb1`. Fresh containers and volume,
compose project `paspm-r2stack`; MySQL published on `127.0.0.1:33306` through an extra override because the host already runs its
own MySQL on 3306 (that server was not touched). All containers, volumes and test images were removed afterwards.

| Step | Result |
|---|---|
| `scripts/staging/build-image.sh rehearsal-1` (arm64, native) | Built; SPMF checksum `OK`; Java 25.0.5 inside the image |
| `docker compose up -d` with `compose.yml` + `compose.backend.yml` only | **Backend exits**: Flyway V11 fails, `You do not have the SUPER privilege and binary logging is enabled` (application user `paspm`). Fixed by `compose.staging.yml` |
| Same with `compose.staging.yml`, empty volume | Flyway applied 19 migrations; backend started in 4.1 s |
| `scripts/staging/healthcheck.sh` | 6 of 6 checks passed |
| `scripts/seed/seed.sh`, first run | Everything created: 1,559 products, 1,559 batches, imports `COMPLETED` (9,999 rows / 2,272 orders and 8,320 rows / 1,869 orders, 0 rejected) |
| `scripts/seed/seed.sh`, second run | Nothing created; record counts identical |
| Mining in the container (a 44-order smoke import, then a snapshot and a System Admin request, both algorithms) | Request `COMPLETED`, comparison `MATCH`, 6 itemsets each, 161 ms and 118 ms |
| `PUT /api/system/mining/schedules/1` with `enabled: true` | HTTP 503 `Scheduled mining dispatch is not available` |
| Rollback: backup, switch to a new tag, damage the data, switch back, restore | Active products 0 -> 1,559, categories 2 -> 1, snapshots 2 -> 2; health check 6 of 6 |
| `linux/amd64` image built and run **under emulation** | Backend started in 11 s; mining request `COMPLETED`/`MATCH` (HUI-Miner 799 ms, EFIM 781 ms, `arch=amd64`). Emulated, so this is **not** native Linux evidence |

Not exercised: a real Linux server, a real release with a new migration (the rollback used the same image under two tags), a real SMTP
server, TLS or a reverse proxy, the multi-process crash and recovery tests on native Linux (risk R2 of the Design Brief).

## Known issues found while preparing this procedure

1. `compose.yml` + `compose.backend.yml` alone cannot start the backend (V11 needs `--log-bin-trust-function-creators=1` for the non-root
   database user); `compose.staging.yml` carries the flag. CI runs the migrations as MySQL root, which hides this.
2. The seeded FoodMart data cannot be mined as a whole: the API accepts at most 256 distinct products per request, and every day of
   the 4,141-order data set contains about 1,500 distinct products (`Snapshot exceeds 256 distinct products`, HTTP 400). The demo scenario
   DS-02a needs a smaller product set or a different data subset.
3. The seed files contain no unknown SKU or conflicting order, so scenario DS-01b (`COMPLETED_WITH_ERRORS`) needs an extra file.
4. Avatar files are written inside the container (`/var/lib/paspm/profile-images`); they are lost when the container is recreated.
5. `GOOGLE_CLIENT_ID` is read by the backend but not passed through by `compose.backend.yml`; Google sign-in therefore stays disabled.
6. `/swagger` and `/v3/api-docs` are public (`SecurityConfig`); use `SPRINGDOC_*` above to hide them.
7. The backend has no health endpoint or compose health check.

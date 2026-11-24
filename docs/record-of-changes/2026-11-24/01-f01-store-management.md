# ROC-20261124-001 — F01 Store management: profile, status, central guard, branding images

| Field | Value |
|---|---|
| Record ID | ROC-20261124-001 |
| Date / timezone | 2026-11-23 to 2026-11-24 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Opus) |
| Type | Implementation / Database / Security / Documentation |
| Features | F01 (Store management, new UC34/UC35), with effects on F05 (System Admin jobs, scheduler) and every Store-scoped route (central guard) |
| Branch / base HEAD | `Khoi/feat/store-management` / `origin/dev` `6d2a877` |
| Execution status | COMPLETED (backend). Frontend not started (Checkpoint 3) |
| Release status | PR_OPEN — branch pushed; PR to `dev`, not merged (waiting for the owner at Checkpoint 4) |
| Daily summary | [README of the day](README.md) |

## 1. Request and scope
- The owner (Product Owner) asked for Store management, gave the answers at Checkpoints 1 and 2 (2026-11-23), and added the requirement for branding images.
- The rules are in [DEC-20261123-01](../../decisions/2026-11-23/DEC-20261123-01-store-management.md).
- Scope: the backend only. Phase 5 (frontend) waits for Checkpoint 3.
- Diagram changes were made on copies outside the repository (`outputs/store-mgmt/diagrams/`). Their 36-page baseline is not on `dev` yet.

## 2. Change details

### Added
- `catalog.store` package:
  - `StoreAdministrationController` (System Admin), which replaces `SystemStoreController` and keeps the same GET route;
  - `StoreProfileController`, `StoreImageController`, `PublicStoreImageController`;
  - `StoreService`, `StoreImageService`, `StoreRepository` (JDBC), `StoreContracts`, `StoreImageKind`.
- `common.media`: `MediaStorage`, `LocalMediaStorage` and `MediaConfiguration` (root `paspm.media.root` = `PASPM_MEDIA_ROOT`, default `~/.paspm/media`).
- `security.StoreStatusGuard` (central rule, 403/409 `STORE_INACTIVE`) and `StoreInactiveAuthenticationException`.
- Tests:
  - `StoreMigrationV27Test` and `StoreMigrationV27MySqlTest`;
  - `StoreInactiveGuardHttpTest`;
  - `StoreAdministrationHttpTest`, `StoreProfileHttpTest`;
  - `StoreImageHttpTest`, `LocalMediaStorageTest`, `MediaFileMissingHttpTest`.

  All are registered in `.github/scripts/test-groups.py`.

### Changed
- `ApiException` can carry a machine `code`, which `ApiError` adds to the body.
- `User` carries `storeStatus`. `UserRepository` reads it through a LEFT JOIN on reads; row locks read it separately, so a login never locks the `store` row.
- `SecurityConfig`:
  - the JWT converter rejects users of an INACTIVE Store, and a custom entry point answers with 403 JSON;
  - `GET /api/public/stores/*/images/*` is public.
- `AuthSessionService`: `issue`, `refresh` and `accessToken` call the guard. The refresh session is kept.
- `MiningService` (manual request) and `MiningScheduleService` (create; enable, checked after the 503 gate) return 409 `STORE_INACTIVE`.
- `MiningScheduleReservation`: due fire times of an INACTIVE Store are recorded as SKIPPED `STORE_INACTIVE`.
- `UserRepository.validateRoleStoreScope`: assigning a user to an INACTIVE Store returns 409.
- Avatars:
  - `LocalAvatarObjectStorage` now delegates to `LocalMediaStorage`; keys and validation are unchanged.
  - **The local default stays `~/.paspm/profile-images`** (Checkpoint 3 fix: an earlier draft had moved it).
  - `AvatarService` answers **404** instead of 503 when the stored file is missing.
- Store image reads answer 404 for a missing file.
- The public image route accepts only UUID file names (`LocalMediaStorage.isFileName`).
- `Dockerfile`: `PASPM_MEDIA_ROOT=/var/lib/paspm/media` and `PROFILE_IMAGE_DIRECTORY=/var/lib/paspm/media/avatars`; both folders are owned by `paspm`.
- `compose.backend.yml`: named volume `paspm_media`, plus both variables.
- `scripts/seed/seed_demo.py` creates the Store with `POST /api/system/stores` instead of SQL.
- Test fixtures that inserted the same Store name more than once per database now use unique names:
  - `TransactionExcelImportIntegrationTest`, `CatalogIntegrationTest`, `VersionedUtilityPersistenceTest`;
  - `MiningMultiJvmMySqlTest`, `MiningSchedulerMultiJvmMySqlTest`.

  `SystemStoreListHttpTest` was updated for the additive fields and the ACTIVE default.

### Database
- **V27** (`V27__f01_store_profile_and_status.java`, Java) adds these columns to `store`:
  - `slug`, backfilled with `slugify(name)-{id}`, then made NOT NULL, `uk_store_slug` and given a UUID default;
  - `status` (`ck_store_status`), `address`, `phone`, `icon_key`, `banner_key`;
  - `version`, `created_at`, `updated_at`;
  - `deactivated_at`, and `deactivated_by` (`fk_store_deactivated_by` → `app_user`).
- V1–V26 are untouched; checksums are verified by the migration tests.
- It runs on a database that already has duplicate Store names (H2 and MySQL tests).

### Documentation
- New: `docs/api/F01_STORE_MANAGEMENT_API.md`, and the DEC.
- Updated:
  - API: the F01 API, the RBAC matrix, the F05 API, the API README (107/133), `API_CHANGELOG.md`, `openapi.json`;
  - handoffs and feature pages: the F01 handoff (Store section), the F05 handoff, the F01 and F05 feature READMEs;
  - product docs: `BRD_VISION`, `SRS`, `GLOSSARY`, `ARCHITECTURE_SDD`;
  - setup: `LOCAL_SETUP_COMMON` and `STAGING` (media root and volume);
  - `scripts/seed` README and `.env.seed.example`;
  - both `paspm-context` skill copies (still identical).

## 3. Files changed
See `git status` on the branch: 6 new main classes plus V27, 8 new test classes, about 20 modified source, test and script files, and about 20 docs.

## 4. API and compatibility impact
- **OpenAPI:** 102 paths / 123 operations → **107 / 133** (+5 paths, +10 operations). Only additive response fields.
- **Behaviour changes:**
  - The picker lists ACTIVE Stores by default.
  - Users of an INACTIVE Store get 403 `STORE_INACTIVE`.
  - System Admin jobs and user assignment for an INACTIVE Store return 409.
  - The local avatar folder does not move. In Docker, avatars and Store images share the `paspm_media` volume.
  - A missing avatar or Store image file now returns 404 (it used to return 503).
- Request DTOs for existing routes are unchanged. Store isolation: Store users reach their own Store only, and another Store returns 404.

## 5. Verification executed in this session

All Maven runs used **Java 17** (Temurin 17.0.21, the same major version as CI) on macOS (arm64). MySQL suites ran against a disposable `mysql:8.4` container on port 55190, with an 8.4.11 client.

| Command / method | Environment | Actual result | Evidence |
|---|---|---|---|
| `./mvnw -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py backend)" -Dlogging.level.root=ERROR` | H2 | PASS — 421 tests, 0 failures, 0 errors, 0 skipped (also re-run on commit `9ca8a0c` after the rebase onto `dev` `6d2a877`, which was a no-op) | Local Maven output |
| Same command with the `mysql` group, plus `-Dpaspm.test.mysql=true -DargLine=-Djava.io.tmpdir=…`, `PASPM_CI_DISPOSABLE_MYSQL=true` and an executable copy of `provision-restore.py` outside the repo | MySQL 8.4 | PASS — 99 tests, 0 failures, 0 errors, 0 skipped | Local Maven output |
| Integration group: the full suite with the CI opt-ins (`paspm.test.mysql`, `java17-backend`, `multijvm`, `java17`, `spmf-jar`, `spmf-java`); SPMF 2.67a (pinned SHA) on Homebrew OpenJDK 25.0.1 | MySQL 8.4, Java 25 child | **842 tests, 3 failures, 0 errors, 12 skipped**. 2 failures were in `LegacyProductColumnsV26MigrationTest`, which expected the latest version to be 26; fixed in this change and re-run 4/4 PASS on MySQL. 1 failure, `JpaPilotMigrationMySqlTest`:84 (product POST with `sku` → 400), is **pre-existing on `dev` `6d2a877`**: reproduced on a clean `origin/dev` worktree; caused by `24b9b01`. **Final run on commit `9ca8a0c`: 842 tests, 1 failure (that pre-existing one), 0 errors, 12 skipped.** The 12 skips are opt-in suites whose properties the CI command does not set either (`foodmart-e2e`, `stability-matrix`, `natural-lease`, `legacy-jar`, `jpa-clone`) | Local Maven output |
| `scripts/staging/build-image.sh store-smoke` (JAR, Linux Java 25 JRE from `eclipse-temurin:25-jre-jammy`, SPMF SHA check, `docker build`) | Docker Desktop, arm64 | PASS — image built | — |
| Compose smoke test: `compose.yml` + `compose.backend.yml` + `compose.staging.yml` + a local port-remap override, isolated project, empty volumes. Upload a Store icon and an avatar; `docker compose restart backend`; `up -d --force-recreate backend` | Docker | PASS — the public GET returns 200 with identical bytes before the restart, after it, and after the re-create, with headers `public, max-age=31536000, immutable` and `nosniff`. The avatar returns 200 after the re-create. Files are at `/var/lib/paspm/media/stores/1/<uuid>.png` and `/var/lib/paspm/media/avatars/<uuid>.png` on volume `paspm_media`. HEAD on the public route returns 401 (only GET is public) | — |
| `scripts/seed/seed.sh` on that fresh database | Docker stack | **PARTIAL**. The Store is created through `POST /api/system/stores` (slug `foodmart-benchmark`, ACTIVE). System Admin login, the three invited/OTP accounts and the category are created. It then stops at `POST /api/products` with 400 `Invalid request fields`: the seed still sends `sku`, which `24b9b01` removed. This is the known deferred demo-seed issue (SRS open item 5) and is not fixed here | Seed log |
| Runtime OpenAPI export | H2 | 107 paths / 133 operations; every `$ref` resolves | `docs/api/openapi.json` diff |

## 6. Previous evidence referenced
- The first backend and mysql group runs of 2026-11-24 (417 and 99 tests, all passing) used **Java 21** (sdkman `current`), not Java 17 as CI does. They are superseded by the Java 17 runs in §5 and are not counted as CI-equivalent evidence.

## 7. Limitations and next actions
- **Checkpoint 3** (owner): approve the backend, then the frontend (`Khoi/feat/store-management-ui`).
- **Diagrams:** the updated copies (UML 40 pages, ERD) are outside the repo. Their 36-page baseline (`Khoi/docs/diagrams-final-clean`) is not on `dev`, so they belong to the diagram PR.
- **Interpretations to confirm with the owner:**
  - Scheduled occurrences reserved before deactivation still run (treated as in-flight).
  - Image uploads increment `version`.
  - The slug column has a UUID default for raw SQL inserts.
- **Cross-Store access answers 404, not 403.** A STORE_ADMIN of Store A who targets Store B's images gets 404; this is the project's Store isolation rule. `/api/store/profile` has no Store parameter, so it always resolves to the caller's own Store. The owner asked to confirm 403; the difference is reported at Checkpoint 4.
- **Pre-existing on `dev`, not fixed here:** `JpaPilotMigrationMySqlTest` (integration) and the demo seed's product step both still send `sku` (`24b9b01`).
- **Separate tasks:** drop `store.mining_schedule`; consider a DB UNIQUE on the normalized name; fix the SKU product docs (`24b9b01`).

## 8. Delivery
- Commit `9ca8a0c` plus this record update on `Khoi/feat/store-management`, pushed without force. A PR into `dev` was opened. Merge: NOT PERFORMED.
- Source state: committed on the worktree branch `Khoi/feat/store-management`. The main checkout `BE/` (including its uncommitted local `application.yml`) is untouched.

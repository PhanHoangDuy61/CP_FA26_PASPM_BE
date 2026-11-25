---
name: paspm-backend-workflow
description: Use when implementing, fixing or testing code in the PASPM backend repo (Java 17, Spring Boot 3.5, MySQL 8.4, Flyway, SPMF/EFIM) — new or changed endpoints, business rules, migrations, tests, OpenAPI or feature docs. Gives the step-by-step workflow, the test commands CI actually runs, and the docs that must change in the same PR.
---

# PASPM backend workflow

Load `paspm-context` first. This skill is the "how"; that skill is the "what" and the rules.

## 1. Start clean

1. Inspect the repo state:
   - Run `git status` and `git branch --show-current`.
   - Do not touch the human's uncommitted work. If the working tree is dirty, use a separate worktree (`git worktree add`) instead of stashing.
2. Branch from the latest `origin/dev`:
   - Run `git fetch origin`.
   - Create a new branch `<member>/<type>/<topic>` from `origin/dev`, for example `Duy/feat/f09-storefront-api`.
3. Read the context for the area you touch:
   - the feature docs (`docs/features/Fxx/README.md`, `docs/api/Fxx_*.md`, `docs/frontend_handoff/Fxx_HANDOFF.md`);
   - the related DEC files;
   - the newest record of changes.
4. **Decide whether this is a business change.**
   - If the change alters a business rule, role permission, limit, formula or contract that is not already decided, stop and ask the owner.
   - Once decided, it gets a DEC file (`docs/decisions/YYYY-MM-DD/DEC-YYYYMMDD-NN-topic.md`, same format as the existing files).

## 2. Code conventions to keep

- **Packages:** code lives under `vn.edu.fpt.paspm.<domain>`. The domains are `auth`, `user`, `catalog`, `transaction`, `utility`, `mining`, `recommendation`, `layout`, `security`, `common` and `deployment`.
- **Persistence:** JPA is used for products and categories only. Everything else uses JDBC (`JdbcTemplate`). Hibernate only validates the schema; Flyway owns it.
- **Store scope:**
  - Resolve the Store from the JWT.
  - A foreign or absent Store resource returns 404, and that check runs after the role check (401 → 403 → 404).
  - Child tables reference the parent with composite keys `(store_id, id)`.
- **Errors:** throw `ApiException(status, message)`, which produces `{"error": "…"}`. Add `code` only for machine-handled cases, as the existing ones do (`UNSUPPORTED_SCORING_VERSION`, `PRODUCT_NAME_CONFIRMATION_REQUIRED`).
- **Request DTOs are strict.**
  - Unknown fields return 400, so adding a required field or removing an accepted one is a **breaking change** for the frontend.
  - Prefer additive response fields.
- **Concurrency:** versioned resources take `expectedVersion`; a stale version returns 409.
- **Precision:** money has scale 2; utility has scale 6 HALF_UP; W has 2 decimals HALF_UP; scaled mining totals are int64.
- **Frozen data:**
  - Never rewrite stored JSON or historical rows.
  - A new format gets a new version tag (for example `scoringVersion`, `conversionVersion`, `ruleVersion`, `algorithm_version`), and readers dispatch on it.
- **Flyway:**
  - The next migration is the next free version after the highest in `src/main/resources/db/migration` and `src/main/java/db/migration`. As of 2026-11-25 V30 is the highest (F09) and V31 is reserved for F10, so new work takes V32. Check every `origin/*` branch before claiming a number.
  - V1–V26 are frozen, because changing them breaks checksums on every teammate's database.

## 3. Test-first, and register the test with CI

1. Write the failing test first and watch it fail for the right reason. Then write the minimal code and watch it pass.
2. **Register every new test class in `.github/scripts/test-groups.py`.**
   - The `backend` and `mysql` CI jobs run only the classes named there, and the script fails if a named class is missing.
   - H2/unit suites go in `backend`. Suites that need `-Dpaspm.test.mysql=true` go in `mysql`.
   - The `integration` job runs the whole suite.
3. Commands (on Windows use `mvnw.cmd`):

   ```bash
   ./mvnw -B test -Dtest=YourNewTest
   ./mvnw -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py backend)"
   ./mvnw -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py mysql)" -Dpaspm.test.mysql=true
   ```

   - The first command is a focused test. The second is the CI backend group (H2, no SPMF). The third is the CI MySQL group, which needs a disposable MySQL 8.4 and its client tools.
   - The MySQL group and the full integration suite need what CI provides. Mirror the setup steps and opt-ins in `.github/workflows/backend-verification.yml`:
     - a disposable MySQL with `mysql`/`mysqldump` on `PATH`;
     - `PASPM_TEST_RESTORE_PROVISIONER` pointing to an **executable** copy of `.github/scripts/provision-restore.py`;
     - the SPMF JAR and Java 25 for mining tests.
   - See `docs/setup/BACKEND_CI.md` and `docs/setup/LOCAL_SETUP_*.md`.
4. Known local pitfall: `provision-restore.py` is mode 100644 in git, and CI runs `chmod +x` before using it. Locally, copy it outside the repo and make the copy executable. Do not change the tracked file mode.
5. Report results with the exact command and the counts (tests, failures, errors, skipped). Name any pre-existing failure. Anything not run is `NOT RUN`.

## 4. Docs that change in the same PR

| You changed | Update |
|---|---|
| Endpoint, request or response shape | `docs/api/Fxx_*.md`. Regenerate `docs/api/openapi.json` with the runtime export hook (e.g. `-Dpaspm.test.export-openapi=true` on `TransactionExcelImportIntegrationTest`), keep the existing formatting, and insert only the real diff. Update the path/operation counts in `docs/api/README.md` and `API_CHANGELOG.md`, the RBAC matrix, and `docs/frontend_handoff/Fxx_HANDOFF.md`, flagging every **breaking** change |
| Business rule, limit, formula, role | DEC file (owner-approved), `docs/features/Fxx/README.md`, and the affected `docs/product/*` (SRS rules or limits, GLOSSARY terms, BRD decision log) |
| Schema | New migration from V32 (V30 is used by F09, V31 is reserved for F10), plus `docs/product/ARCHITECTURE_SDD.md` §5 (tables, FKs, migrations). Note the ERD diagram that needs an update |
| Anything | A record-of-changes entry: `docs/record-of-changes/YYYY-MM-DD/NN-topic.md` from `TEMPLATE.md`, plus the day README and the index row. Follow the rules in `docs/record-of-changes/README.md` |

## 5. Pull request

- Push your branch and open a PR into `dev`. If `gh` is unavailable, give the human the compare URL `https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/compare/dev...<branch>?expand=1`.
- The PR body covers:
  - the summary;
  - the decisions implemented (DEC ids);
  - API changes, marked additive or breaking;
  - migrations;
  - tests, with the commands and counts;
  - what is NOT RUN;
  - limitations;
  - which docs were updated.
- Merge only when CI is green and the human asked. The `integration` job needs the repository variable `PASPM_CI_SPMF_ARTIFACT_URL`; if it is missing, say so instead of treating the job as passed.
- After the merge, check whether `docs/product/*` still states every changed fact correctly.

## 6. Do not

- Edit V1–V26, push to `dev`, force-push, or merge without green CI.
- Enable the scheduler (it must stay 503), or change `paspm.refresh-cookie-secure` defaults.
- Commit secrets, `.env` files or local `application-*.yml` with real values.
- "Fix" the deferred demo-seed issue or `priorityCoverage` as a side effect of another task.

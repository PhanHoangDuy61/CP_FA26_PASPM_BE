# Review 2 setup report (branch `Khoi/docs/review-2-setup`)

Date: 2026-11-04. Base: `dev` = `origin/dev` = `5b30eb1` ("Merge pull request #13 ... f05-scheduled-mining-option-b-completion",
2026-11-02 20:01 +07), the same commit as the 3 Nov 2026 verification baseline. Everything below was run in this session unless it is
marked **NOT RUN** or **NOT VERIFIED**. No backend source file, historical migration (V1–V19) or historical ERD file was changed; nothing
was committed, pushed, merged or tagged.

## 1. Step 0 — switching `BE/` to `dev`

| Item | Result |
|---|---|
| Initial `git status` | Branch `Khoi/feat/f03-f05-admin-mining` (a4f1115); deleted `docs/diagrams/PASPM_Complete_ERD_F02_Inventory_Batch_v3.drawio`; untracked `PASPM_Unified_27_Diagrams_UML2_Editable.drawio`, `..._old.drawio`, `.$...drawio.bkp`, `.$...drawio.dtmp`, `.DS_Store` files — as expected |
| Preservation | `outputs/preservation-2026-11-04/`: `BE_git_diff.patch`, `BE_status_porcelain.txt` and the four untracked files; copies verified byte-identical (SHA-256 `644dcabe…07217bad` Editable, `cc957281…c2e0f500` old and `.bkp`, `e224c822…ed3fbc52` `.dtmp`) |
| `git fetch origin --prune --tags` | OK. **No tags exist** locally or on origin |
| `git switch dev` | OK; local `dev` was at `c06d43e` (PR #6, 26 Oct), 56 commits behind; local changes carried over |
| `git pull --ff-only origin dev` | First attempt refused: untracked `PASPM_Unified_27_Diagrams_UML2_Editable.drawio` would be overwritten by the tracked file of the same name. After approval the intermediate file was **moved** (not deleted) to `outputs/preservation-2026-11-04/BE/moved-before-pull/docs/diagrams/` (SHA-256 re-verified), then the fast-forward succeeded |
| HEAD now | `5b30eb17f51263ca3461c607622cb3c3e09f3982`, same as the baseline `5b30eb1`; `git log 5b30eb1..HEAD` is **empty** |
| `git status` after | `## dev...origin/dev`; the F02 ERD deletion is still uncommitted; `_old.drawio`, `.bkp`, `.dtmp` and `.DS_Store` untouched. The final diagram file `outputs/diagram-final-clean-wt/.../PASPM_Unified_27_Diagrams_UML2_Editable.drawio` (SHA-256 `833e3d09…c5722`) and its worktree were not touched; no worktree was pruned or removed |
| Worktree | `outputs/review2-wt`, new branch `Khoi/docs/review-2-setup` at `5b30eb1` |

**CI history (`gh run list`)**: **NOT VERIFIED** — `gh` is not installed (not installed on request). The repository's own documentation
(`docs/setup/BACKEND_CI.md`) states that hosted runs 39879145935 (failed 6 MySQL restore fixtures) and 39909707415 (foundation 135 PASS,
MySQL 34 PASS, integration blocked) happened on 2026-10-23; that is an unverified claim from the repo, not something this session saw.
The `integration` job needs the repository variable `PASPM_CI_SPMF_ARTIFACT_URL`. Check the Actions tab.

## 2. What changed (all untracked/modified in the worktree; nothing staged)

| Path | What |
|---|---|
| `docs/review-2/` | Registration form, SRS (Report 3), SDD (Report 4) copied unchanged (SHA-256 in `README.md`); `README.md`; this report. BM01–BM03 not copied. PDF export **NOT RUN** (no LibreOffice/Word) |
| `docs/diagrams/mermaid/*.mmd` (17) and `rendered/*.svg` (17) | Final diagrams as Mermaid (architecture, conceptual and logical ERD, six state diagrams, AD01–AD08) |
| `docs/diagrams/README.md` | New section: Mermaid file → draw.io page → report figure; legacy `.mmd` files marked superseded with a comparison table. Legacy files untouched |
| `scripts/seed/` | `seed.sh`, `seed_demo.py`, `.env.seed.example`, `data/` (the two CSV files and the product SQL, copied from `csv_test_and_train/`, hashes identical) |
| `scripts/staging/` | `build-image.sh`, `healthcheck.sh`, `backup-db.sh`, `restore-db.sh` |
| `docs/setup/STAGING.md` | Staging procedure, rehearsal evidence, known issues |
| `.env.staging.example`, `compose.staging.yml` | Placeholders for the server `.env`; compose override (MySQL flag needed by V11, optional Swagger switches). Outside `docs/`/`scripts/`, but configuration only |
| `.gitignore` | `+.env.seed`, `.env.staging`, `backups/` |

Size note: the three Word files are 4.5 MB, 8.9 MB and 15.8 MB (29 MB of binary in git history once committed).

## 3. Verification

| Check | Result |
|---|---|
| `mvn clean verify` (JDK 17.0.21 Temurin, `-Dpaspm.test.spmf-java=<JDK 25> -Dpaspm.test.spmf-jar=<pinned jar> -Dlogging.level.root=ERROR`, the same arguments the 3 Nov script used) | **552 run, 373 passed, 0 failed, 0 errors, 179 skipped**; BUILD SUCCESS in 1 min 29 s. **Identical to the baseline** (552 / 373 / 0 / 179). The worktree is the same commit (`5b30eb1`) and no source file changed, so there is no difference to explain by commit. The skipped tests are opt-in suites (MySQL, multi-JVM, Java 17 backend), not passes |
| Same command **without** the SPMF arguments (plain `mvn verify`) | 552 run, 30 failures, 11 errors, 179 skipped (331 passed). All 41 are `SpmfRuntimeTest`, `SpmfMiningEngineTest`, `MiningIntegrationTest`, `RecommendationIntegrationTest`: `SPMF_JAR_NOT_FOUND` or HTTP 503 because the tests look for `../spmf_run/spmf.jar` and a Java 25 executable. Environment-dependent, not a regression; see finding F9 |
| Full opt-in suites (MySQL, multi-JVM, Java 17 backend) | **NOT RUN** in this session (run on 3 Nov: 562 run, 0 failed, 27 skipped; MySQL group 56 run, 0 skipped) |
| Flyway on a fresh disposable MySQL 8.4.13 | 19 migrations applied (V11 is a Java migration); **31 tables, 304 columns, 59 foreign keys** from `information_schema` (excluding `flyway_schema_history`) |
| Clean draw.io Logical ERD vs the real schema | **No difference**: 31/31 tables, 304/304 columns (name, type, nullability, order), PK and FK flags, and 59/59 relationships |
| Mermaid | All 17 files rendered with `@mermaid-js/mermaid-cli` **12.2.0** (via `npx`, nothing installed globally; Chrome from the Mac): no parse error in the final run (the first run failed on empty decision-diamond labels, which were fixed with a single-space label), SVGs 0.2–1.2 MB with labels; 5 renders were opened and looked at (AD03, AD08, mining request and occurrence states, architecture, conceptual ERD); the others were checked by label count only. No visible `F0x`, "planned" or note text in any render |
| Mermaid vs draw.io | Generated from the draw.io XML with a script and checked by count: nodes, edges and lanes per page equal; state diagram edge counts equal (7, 2, 19, 9, 5, 7) |
| Seed script, disposable MySQL + backend (JAR, port 18090) and again on the compose stack | First run created everything; second run created nothing. Counts after both runs: store 1, app_user 5 (4 + internal scheduler), category 1, product 1,559, inventory_batch 1,559, import_batch 2, import_staging_row 18,319, sales_transaction 4,141, transaction_item 18,319 |
| Import summaries | Part1: batch `COMPLETED`, rows 9,999 (valid 9,999, rejected 0), orders 2,272 (accepted 2,272, rejected/duplicate/conflict 0). Part2: `COMPLETED`, rows 8,320 (valid 8,320, rejected 0), orders 1,869 (accepted 1,869, 0/0/0). Second run: both files skipped (already imported); no new batch |
| Data source used | REST API for accounts, category, products, batches and imports. SQL only for `INSERT INTO store` (no store endpoint exists). `FoodMart_Product_Direct_Insert.sql` is parsed for product data, not executed |
| Staging rehearsal (local Docker) | See `docs/setup/STAGING.md`: image build arm64 native, stack up, health check 6/6, seed twice, mining `COMPLETED`/`MATCH` in the container, schedule enabling answers 503, backup/rollback/restore, and the `linux/amd64` image under emulation (**emulated, not native**) |
| Runtime OpenAPI | 89 operations / 72 paths; identical to `docs/api/openapi.json` except the `servers[0].url` port |
| Scope of the diff | Only `docs/`, `scripts/`, `.env.staging.example`, `compose.staging.yml` and `.gitignore` (`git diff --stat` shows `.gitignore` and `docs/diagrams/README.md`; the rest is untracked) |
| Cleanup | All disposable containers (MySQL ×2, Mailpit ×2), the compose volume, the three test image tags, `.env`, backups and temporary build directories were removed. Public base images stay in the Docker cache (`mysql:8.4`, `axllent/mailpit:v1.27.8`, `eclipse-temurin:25-jre-jammy`). The host MySQL on port 3306 was never used |

## 4. Report versus code on `dev` (`5b30eb1`)

Only differences, gaps and unverifiable points are listed. Everything not listed and checked matched (see the list after the table).

| # | Report | Section | Report says | Code on dev says | File:line |
|---|---|---|---|---|---|
| 1 | SDD | 1.3 CI/CD | The workflow runs "SPMF downloader security tests, the Java 17 test suite and the MySQL 8.4 test group" | Four test jobs plus a required gate: the omitted one is `integration` (full suite on Java 17 with a separate Java 25, pinned SPMF, MySQL 8.4, all opt-ins). The MySQL job is still titled "Flyway V1-V11" | `.github/workflows/backend-verification.yml:45`, `:92`, `:174` |
| 2 | SDD | 1.3 Staging (planned) | "build the JAR and the image, then start the services" with `compose.yml` + `compose.backend.yml` | With those two files the backend does not start on MySQL 8.4: migration V11 creates triggers and the non-root user has no SUPER privilege (binary logging on). Needs `--log-bin-trust-function-creators=1` (added as `compose.staging.yml`) | `compose.yml:3`; `LegacyRecoverySchema.java:102,105` |
| 3 | SDD | 2 Table Descriptions (12 tables) | Foreign keys written as single columns, e.g. `product`: `category_id → category` | The database uses composite keys `(category_id, store_id)`; all 59 constraints exist; the SDD text of §2 does say composite keys are used, the per-table lists simplify them | `V3__f02_catalog_inventory_batch.sql:30` |
| 4 | SDD | 3.1.2 Class Specifications, `SnapshotStatus` | Enumeration: READY | The schema allows `READY` and `FAILED` (SDD §2 table 10 says so too); the code only ever writes `READY` | `V5__f03_immutable_utility_snapshots.sql:18`; `UtilitySnapshotRepository.java:23` |
| 5 | SDD | 1.3 Front-end | React 19, React Router 7, TypeScript 6, Vite 8, Tailwind 4, Vitest, Playwright 1.64 | **NOT VERIFIED** against `FE-FA26` `dev`. The local frontend checkout (`FE_Kien`, branch `feature/F04-transaction-pipeline`, 8594488) declares `typescript ^7.0.2` and has no `react-router`, `vitest` or `@playwright/test` dependency | `FE_Kien/package.json` (outside this repository) |
| 6 | SRS | 3.6.4 Mining Schedules | "Lookback is 7–365 days" | Correct; the SRS omits that an omitted lookback defaults to 30 days (weekly) or 90 days (monthly) | `MiningScheduleController.java:22` |
| 7 | SRS | 3.6.1 / 4.2.3 | "One request accepts at most 256 products and 20,000 matrix rows" | Correct. Consequence for the demo: the 4,141-order FoodMart data (three days, about 1,500 distinct products per day) cannot be mined through the API: HTTP 400 "Snapshot exceeds 256 distinct products" | `MiningInputBuilder.java:15,52` |
| 8 | Design Brief | A2 DS-02a | READY snapshot of the seeded store → request `COMPLETED`, comparison MATCH | Not reachable with the seed data (row 7). Mining worked in the container only with a 44-order, 50-product smoke import | `MiningInputBuilder.java:52` |
| 9 | Design Brief | A2 DS-01b | "CSV with an unknown SKU and a conflicting order" → `COMPLETED_WITH_ERRORS` | The two provided CSV files import with 0 rejected rows and 0 conflicts; the exception file does not exist yet. The behaviour itself is in the code | `TransactionImportRepository.java:168` |
| 10 | Design Brief | 0 Header | Repository link "(tag review-2)" | Tag `review-2` does not exist yet (`git tag -l` is empty) | — |
| 11 | Design Brief | R3 / G-list | "CI workflow has not run on the review-2 tag" | Consistent; whether it ever ran on GitHub is **NOT VERIFIED** (no `gh`) | — |

Checked and matching (no row): 31 tables / 304 columns / 59 foreign keys; 89 REST operations (runtime OpenAPI); Java 17, Spring Boot 3.5.7,
springdoc 2.8.14, commons-csv 1.14.1, MySQL 8.4, H2 tests, Mailpit, SPMF 2.67a (SHA-256 pin) with a Java 25+ child; Java package list
and the two mutually dependent package groups (Identity/Shared Kernel and Catalog/Mining); JPA for product and category only; Google
JWKS URL and SMTP; all state values of the six state diagrams against the SQL CHECK constraints (import batch, staging row, snapshot,
mining request/run/comparison, schedule occurrence incl. SKIPPED, recommendation review) and the lease-reclaim and reset transitions;
OTP (10 min, 60 s cooldown, 5 attempts), login limits (30/IP and 10/email per 15 min), password rule (12–72, three classes), 5 MiB and
10,000-row import limits, 60 s / 256 MiB / 2,000-pattern mining limits, three schedule attempts at 15 s and 30 s, schedule enabling
answers 503 by default (also confirmed in the container), request-key conflict 409, recommendation defaults (bundle 2–4, confidence
0.50, support 2 / 0.05), reason ≤ 1,000 characters, 10,000-candidate cap, role matrix of the controllers, all 18 message texts
(MSG01–MSG18 exist verbatim in the code, checked by string search).
**NOT VERIFIED** in this session: class and sequence diagram contents against the code (operation names of the class
specifications), screen-level behaviour of the prototype, and every SRS non-functional sentence not listed above.

### Legacy Mermaid files (`docs/diagrams/*.mmd`) vs the final draw.io pages

See the table in `docs/diagrams/README.md`. Summary: none of the 16 legacy files can replace a final page. `04-logical-erd-v1-v11` has 26
tables against 31; `05c`/`05d` lack the lease-reclaim transitions; `05b` has extra states and notes; `06-activity-workflows` is a different
set of eight diagrams; `01`, `02`, `03` differ in actors, owners and Flyway version; `05a`, `05e`, `05f` and `08` have the same
structure with different wording or notes.

## 5. Findings (reported, not fixed)

| # | Finding | Evidence |
|---|---|---|
| F1 | Backend cannot start with the documented compose files (V11 triggers vs. SUPER privilege). CI hides it by using MySQL root | Rehearsal log; `compose.staging.yml` is a configuration workaround |
| F2 | `CatalogDevSeeder` creates accounts with the hard-coded password `PaspmDev123!` under the `dev` Spring profile; the profile must never be enabled on staging | `CatalogDevSeeder.java:61` |
| F3 | `/swagger` and `/v3/api-docs` are public (`permitAll`) | `SecurityConfig.java:87` |
| F4 | `compose.backend.yml` does not pass `GOOGLE_CLIENT_ID` (it is in `.env.example`), so Google sign-in cannot be enabled in the container | `compose.backend.yml:10-23` |
| F5 | Avatars are stored in the container file system, not in a volume | `Dockerfile:21` |
| F6 | No health endpoint (no actuator) and no compose health check for the backend | `pom.xml` |
| F7 | No endpoint creates a store; only a read-only picker exists | `SystemStoreController.java` |
| F8 | Seed data vs the 256-product mining limit (table rows 7–8) | `MiningInputBuilder.java:52` |
| F9 | Plain `mvn verify` without SPMF arguments fails 41 tests; tests default to `../spmf_run/spmf.jar`, which only resolves when the checkout sits directly under the project folder | Maven log |
| F10 | The workflow job title still says "Flyway V1-V11" | `backend-verification.yml:45` |
| F11 | The committed `docs/diagrams/PASPM_Unified_27_Diagrams_UML2_Editable.drawio` on `dev` is the old 27-page version; the final 36-page file exists only in the uncommitted worktree `outputs/diagram-final-clean-wt` | file hashes in section 1 |
| F12 | In the final UML file only the Screen Flow page contains a feature code, in real route paths (`/f03/utility`, `/f03/expiry-window`, …). That page is not part of the Mermaid pack | page `R2-03 — Screen Flow` |
| F13 | The conceptual ERD page draws six entities (Layout, Placement, Campaign, ComparisonReport, Cart, CartItem) that have no table in the schema; `conceptual-erd.mmd` repeats them because the transcription must not drop anything. SRS 3.1.5 says these are not yet in the schema | conceptual page vs `information_schema` |

## 6. Open items

| Item | Status |
|---|---|
| GitHub Actions history of `backend-verification.yml` | **NOT VERIFIED** (no `gh`; check the Actions tab) |
| CI run on the `review-2` tag / on the PR | **NOT RUN** (nothing pushed) |
| Real staging server | **NOT RUN**: none exists. Needed from the team: host and SSH access, OS and CPU architecture, Docker/Compose versions, public hostname or tunnel and TLS plan, where backups are kept, the approved SPMF artifact location, SMTP choice (Mailpit only works for the seed) |
| Native Linux acceptance (`scripts/verify-native-linux.sh`, crash and recovery tests, risk R2) | **NOT RUN** (the `amd64` image ran under emulation only) |
| PDF export of SRS and SDD | **NOT RUN** (no LibreOffice/Word); export from Word |
| Rollback with a release that adds a migration | **NOT RUN** (rehearsal used the same image under two tags) |
| Full opt-in test suites (MySQL, multi-JVM) in this session | **NOT RUN** |
| Frontend technology versions of the SDD against `FE-FA26` `dev` | **NOT VERIFIED** |
| BM02 self-review | Not started on purpose: run it in a new Claude Code session on a checkout of the `review-2` tag with the standard prompt |
| Demo data for DS-01b (unknown SKU, conflicting order) and DS-02a (a mineable subset) | Missing (findings F8, row 9) |

## 7. Proposed commands (not run)

See the end of the session message; repeated here for the record. Review the diff first (`git -C outputs/review2-wt status`).

```bash
cd ~/Desktop/khoi/fpt/ki9/FA26SE048_HUPAS/outputs/review2-wt
git add .gitignore .env.staging.example compose.staging.yml docs scripts
git status --short                      # check that only the files in section 2 are staged
git commit -m "docs: Review 2 setup - documents, Mermaid diagrams, demo seed and staging kit"
git push -u origin Khoi/docs/review-2-setup
# open a pull request Khoi/docs/review-2-setup -> dev in the GitHub UI (gh is not installed)
```

After the pull request is merged and CI passes on `dev`:

```bash
git -C ~/Desktop/khoi/fpt/ki9/FA26SE048_HUPAS/BE fetch origin
git -C ~/Desktop/khoi/fpt/ki9/FA26SE048_HUPAS/BE tag -a review-2 -m "SWP490 Review 2" <dev-commit>
git -C ~/Desktop/khoi/fpt/ki9/FA26SE048_HUPAS/BE push origin review-2
```

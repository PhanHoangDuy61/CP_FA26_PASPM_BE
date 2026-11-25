# ROC-20261115-004 — F03 Weight v2 (expiry risk + season), Phases 2–6

| Field | Value |
|---|---|
| Record ID | ROC-20261115-004 |
| Date / timezone | 2026-11-15 / Asia/Ho_Chi_Minh |
| Type | Implementation / Database / Documentation |
| Features | F03 (main), F02, F05, F06; F07/F08 checked (no change) |
| Branch / base HEAD | `Khoi/feat/f05-progress-f03-weight-v2`, base `8f94f94`; Phases 0–1b committed locally as `3bdfee3`, `bd60c75`, `982094c`. Phases 2–6 are **uncommitted** |
| Execution status | COMPLETED to Checkpoint 2 (see §5 for results and limits) |
| Release status | UNRELEASED — no commit, push, merge or deploy for Phases 2–6 |
| Daily summary | [README of the day](README.md) |

## 1. Request and scope

Implement F03 `W2_EXPIRY_SEASON_V1` per the owner decisions of 2026-11-15 (Q1–Q10, P1–P6) in `outputs/prompts/PROMPT_Codex_F03_F05.md`, plus the config-conversion rule approved in the resume instruction of 2026-11-15. Never edit V1–V24; F03 gets V25. Frozen JSON must read back identically; `fail-on-unknown-properties` stays global. Scheduler enable stays 503; Store isolation (404) stays; SYSTEM_ADMIN/MERCHANDISER mining rights from Phase 1b stay; the λ_max block on new Merchandiser requests stays. Disposable databases only; Word reports not edited.

## 2. Owner decisions recorded

**Live config conversion — owner decision, 2026-11-15 (APPROVED):**
1. Keep the Store's λ, clamped into [0, current λ_max]; record clamped Stores in the migration evidence.
2. α (expiry) ← old expiry coefficient, β (season) ← old seasonal coefficient, only if enabled; old stock coefficient dropped; normalise to α + β = 1.
3. Both 0/disabled, or no config: α = β = 0.5.
4. N = 30; version + 1; `scoringVersion = W2_EXPIRY_SEASON_V1`.
5. Frozen JSON in snapshots, mining requests and recommendations is never touched.

Implemented as `WeightV2Conversion` (rule id `OWNER_2026_11_15_V1`) called by V25; evidence rows in `utility_weight_v2_conversion` (one per Store, one for the policy).

**Assumptions applied, pending owner confirmation:**
- **P2** — a product in several active seasons gets the highest weight (ties: lowest seasonId).
- **P3** — a season is active when the analysis date lies in [start_date, end_date] inclusive; preview, manual and scheduled snapshots use the same rule.
- **P4** — a push day on or before the analysis date puts the whole remaining batch at risk.
- **P5** — the Product API still accepts `expiryPushDays` (stored, validated ≥ 0) but ignores it; marked deprecated in OpenAPI.
- **P6** — route paths kept, bodies changed; breaking changes documented for the Frontend.

**Implementation choices made where the prompt was silent (flag for review):**
- v(i) window **includes** the analysis date: [analysisDate − (N−1), analysisDate], Store time zone.
- `salesWindowDays` validated 1..365; omitted on update keeps the current value. A Store with no saved config reads as α = β = 0.5, λ = 0, N = 30, version 1.
- Removed operations answer **410 Gone** (Store expiry window, product score PATCH) rather than disappearing (P6).
- Season names are unique per Store **case-insensitively** (service check + DB unique).
- An expired batch is one with expiry_date ≤ analysis date (unchanged from v1.2).
- With v(i) = 0 the experiment's FEFO would give U = 1; Q7 overrides it to U = 0 (`SLOW_MOVING`).
- One existing test fixture (`MiningIntegrationTest.oldScoringMetadataCanBeMinedWithoutRecalculation`) used the synthetic version `EXPIRY_LINEAR_V1`, which no shipped code ever wrote (checked with `git grep` across history). The unknown-version rule rejects it, so the fixture now uses the real legacy version `F03_LINEAR_V1`. **Before deploying, check real databases for any other `scoringVersion` value** (query in §6).

## 3. Change details

- **Phase 2 (safety layer):** version-aware readers (`UtilityJsonVersions`, `RecommendationJsonVersions`) for Store config, λ policy, frozen snapshot config (also inside mining requests), matrix expiry evidence and recommendation eligibility. Legacy records kept; W2 records added; selection by `scoringVersion`; unknown version and unknown fields rejected.
- **Phase 3 (data):** V25 Java migration — `inventory_batch.push_day`, `season`, `product_season`, `utility_snapshot_season`, matrix `expiry_risk`/`season_weight`/`season_id`, expiry-status CHECK replaced by `ck_utility_matrix_expiry_status_w2`, owner conversion of live configs and the policy, evidence table. Schema notes: [V24/V25](../../architecture/F03_W2_SCHEMA_V24_V25.md).
- **Phase 4 (calculation):** `ExpiryRiskCalculator` (exact FEFO), `UtilityMath.weightV2` (exact, 2-dp half-up; legacy `weight` kept), `WeightV2Calculator`, `UtilityFreezer`, season API (8 operations), new config/policy/score contracts, matrix builder on W2.
- **Phase 5 (consumers):** F02 `pushDay`; scheduled snapshots via the shared freezer; F06 `EligibilityV2`, push-day expiry alerts, ended-season warning, one-time `inventoryChanged`; F07/F08 not changed (priority coverage is not computed by the code).
- **Phase 6:** docs below, OpenAPI regenerated, full verification.

## 4. Files changed

See the Checkpoint 2 report in the session for the per-phase list. Documentation: [F03 README](../../features/F03/README.md), [F05 README](../../features/F05/README.md), [F06 README](../../features/F06/README.md), [F03 API](../../api/F03_UTILITY_API.md), [F02 API](../../api/F02_PRODUCT_INVENTORY_API.md), [F05 API](../../api/F05_MINING_API.md), [F06 API](../../api/F06_RECOMMENDATION_API.md), [RBAC matrix](../../api/F01_F02_RBAC_MATRIX.md), [API changelog](../../api/API_CHANGELOG.md), [OpenAPI](../../api/openapi.json), [W2 breaking changes](../../frontend_handoff/F03_W2_BREAKING_CHANGES.md), [schema/ERD notes](../../architecture/F03_W2_SCHEMA_V24_V25.md).

## 5. Verification (all local, macOS, Java 17 backend, disposable MySQL 8.4.13; logs in `<evidence-dir>/`, not published)

| Run | Scope | Result | Counted |
|---|---|---|---|
| Phase 2 suites (first) | F03, F05, F06 packages, H2 + MySQL group | 389 run, 2 ERROR (`EXPIRY_LINEAR_V1` fixture, see §2), 24 SKIP (opt-in groups not enabled) | no — superseded |
| Phase 2 suites (rerun, V25 files parked) | same | 389 run, 0 FAIL, 0 ERROR, 24 SKIP, exit 0 | yes — Phase 2 gate |
| V25 migration test | `WeightV2MigrationTest` H2 + MySQL | 2/2 PASS after two fixes (H2 JSON-string unwrap in V25; test mapper date format) | yes |
| W2 unit + JSON + migration | `WeightV2CalculationTest`, `WeightV2MigrationTest`, `VersionedUtilityJsonTest` | 21/21 PASS | yes |
| Phase 4 suites, attempt 1 | F02–F08 packages | **interrupted / contaminated**: main classes were recompiled during the run; stopped; log `phase4-suites-INTERRUPTED-contaminated.log` | no |
| Phase 4+5 suites | utility, mining, recommendation, catalog, layout, persistence | 485 run, 11 FAIL, 4 ERROR, 29 SKIP → fixed (legacy test expectations; V25 CHECK lookup after V11 recovery; dangling OpenAPI `$ref`) | no — superseded |
| Targeted retests | failing classes | 87 run → 3 FAIL/1 ERROR → fixed (CHECK name collision on MySQL) → 47/47 PASS | yes |
| Phase 6 full gate, attempt 1 | full opt-in | aborted after start (moved to a detached supervisor) | no |
| Phase 6 full gate, attempt 2 | full opt-in | **invalid environment**: disposable MySQL tmpfs full (MySQL error 3675) → 101 ERROR; exit 1 | no |
| Phase 6 full gate, attempt 3 | full opt-in, new disposable MySQL | 645 run, 2 FAIL (`LegacyReaderMySqlTest` store fingerprint includes the intentionally rewritten `utility_config`) → test fixed, class 2/2 PASS | no — superseded |
| **Phase 6 full gate, final** | `clean verify` with the Phase 1 Maven arguments (MySQL, Java 17 backend, SPMF/Java 25, multi-JVM, natural lease, 8f94f94 legacy jar) | **645 tests, 640 PASS, 0 FAIL, 0 ERROR, 5 SKIP; Maven exit 0; 27:13; source fingerprint unchanged** | **yes** — [counts](F03_W2_FULL_OPTIN_COUNTS.json) |
| JPA supplementary | `PilotCloneApiIntegrationTest`, new synthetic schema | attempt 1: 5 ERROR (mutation switch not set, invocation error); attempt 2: **5/5 PASS**, exit 0 | attempt 2 — [counts](F03_W2_SYNTHETIC_JPA_COUNTS.json) |

**Comparison with the Phase 1 gate (632 tests, 627 PASS, 5 SKIP):** +13 tests. Added 32 (`WeightV2CalculationTest` 12, `WeightV2MigrationTest` 2, `VersionedUtilityJsonTest` 7, `VersionedUtilityPersistence[MySql]Test` 3+3, `PushDayIntegrationTest` 5); removed 10 (`WeightRulesV12IntegrationTest`, retired v1.2 binary rule; its still-valid checks moved to `PushDayIntegrationTest` and the versioned readers); `RetailCatalogUtility[MySql]IntegrationTest` 11 → 8 each (−6) and `UtilityIntegrationTest` 12 → 9 (−3), rewritten for W2. The same 5 `PilotCloneApiIntegrationTest` skips as in Phase 1 (`paspm.test.jpa-clone` not set by the gate); they pass in the supplementary run.

**Environment changes:** the first disposable container (`paspm-phase2-gate-20261115`, port 60655) ran out of tmpfs after ~394 accumulated test schemas; it was stopped (not removed; its synthetic contents were discarded). A new disposable `mysql:8.4` container `paspm-phase6-gate-20261115` (port 54633, same image/settings) was used for the counted runs. Only the MySQL port differs from the Phase 1 environment.

**Conversion results:** only synthetic Stores were converted (`WeightV2MigrationTest` on H2 and MySQL; recomputed with the production class for the table in the Checkpoint 2 report). No real or shared database was used, so no real Store was converted.

**NOT RUN:** Linux SPMF orphan cleanup (macOS only); GitHub CI; Windows; interactive Swagger UI; any real-data migration or clone; the unmodified Option B shell script end-to-end (same adaptation as record 003); Frontend changes.

## 6. Pre-deploy checks (NOT RUN — no real database was used)

```sql
-- any scoringVersion other than the three supported ones would now be rejected on read
SELECT snapshot_id, JSON_EXTRACT(config_snapshot,'$.config.scoringVersion') v FROM utility_snapshot
 WHERE JSON_EXTRACT(config_snapshot,'$.config.scoringVersion') IS NOT NULL
   AND JSON_UNQUOTE(JSON_EXTRACT(config_snapshot,'$.config.scoringVersion')) NOT IN ('F03_LINEAR_V1','EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1','W2_EXPIRY_SEASON_V1');
-- same check for mining_request.utility_config_snapshot ($.configuration.config.scoringVersion) and store.utility_config ($.scoringVersion)
```

## 7. Report changes needed (Word reports not edited)

Listed in the Checkpoint 2 report: SRS BR-05, §3.4, mining use cases and screen authorization table; Report 1 roles; Report 4 §4.1 (formula, 1.115 → 1.12, who runs mining).

## Addendum 2026-11-15 (ROC-20261115-005, docs sync) — commits, deploy checklist, report changes

| Item | Value |
|---|---|
| Commits (local, owner-approved at Checkpoint 2, not pushed) | `e38b55f` feat(f03): Weight v2 (FEFO expiry + seasons), versioned readers, V25 conversion; F02 pushDay; F05/F06 consumers · `c8c837a` docs(f03,f05,f06): document Weight v2, season API, breaking changes and gate evidence |
| Fingerprint | commit `e38b55f` tree (`pom.xml` + `src/**`, 282 files) = phase6c gate fingerprint `8ada1b722bfaefbf139762be71df86523f4850c02fd7142e92747b95080f8cf6` |
| Migration | V25 `src/main/java/db/migration/V25__f03_weight_v2_expiry_season.java` |
| Counted verification | phase6c gate **645 tests, 640 PASS, 0 FAIL, 0 ERROR, 5 SKIP**, exit 0; JPA supplementary **5/5** |
| Not counted | listed in §5 (contaminated, aborted, MySQL disk full, fingerprint test fix, JPA switch missing) |
| Fixture change | `EXPIRY_LINEAR_V1` → `F03_LINEAR_V1` approved by the owner at Checkpoint 2 ([DEC-20261115-11](../../decisions/2026-11-15/DEC-20261115-11-expiry-linear-fixture-change.md)) |
| Container | `paspm-phase6-gate-20261115` stopped and removed after the commits (synthetic data only) |
| NOT RUN | Linux SPMF orphan cleanup (macOS evidence only), GitHub CI (including the `integration` job, which needs `PASPM_CI_SPMF_ARTIFACT_URL`), interactive Swagger UI, real-data conversion/clone, Windows, Frontend |

"Phases 2–6 are uncommitted" in the header table was true at the time of writing.

### Before deploy (checklist, none done)
- [ ] Run the read-only `scoringVersion` query of §6 on every real database (snapshots, `mining_request.utility_config_snapshot`, `store.utility_config`); any value outside `F03_LINEAR_V1`, `EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1`, `W2_EXPIRY_SEASON_V1` would be rejected on read.
- [ ] Stop **all** old backend instances before V25 runs; a rolling upgrade with old and new instances was **not tested** (old `WeightConfig` reader cannot read W2 configs).
- [ ] Back up, run V25, then read `utility_weight_v2_conversion` (λ clamped, defaults applied) and keep it as conversion evidence.
- [ ] Store Admins enter batch push days; until then every batch only warns `MISSING_PUSH_DAY` and U_expiry ignores it.
- [ ] Release the Frontend together with the backend (14 breaking changes, [list](../../frontend_handoff/F03_W2_BREAKING_CHANGES.md)).
- [ ] GitHub CI `integration` job: set `PASPM_CI_SPMF_ARTIFACT_URL` (not run in this work).

### Report changes needed (Word reports not edited here)
- SRS: BR-05 (Merchandiser submits mining for its own Store with the System Admin configuration), §3.4 (W2 formula, λ ∈ [0, λ_max], α + β = 1, 2-dp rounding, FEFO U_expiry, seasons, push day per batch), mining use cases (Merchandiser as submitter), screen authorization table (season screens, batch push day, Merchandiser mining submit; removed score editor and expiry window).
- Report 1: roles — System Admin sets λ_max and the mining configuration; Merchandiser sets α, β, λ, N, seasons and runs mining for its Store; Store Admin enters push days.
- Report 4 §4.1: λ owner (System Admin sets only λ_max; Merchandiser picks λ), M from Store seasons instead of a per-product score, the worked example 1.115 → **1.12**, who runs mining.

### 2026-11-16 follow-up to the before-deploy checklist
Validate the additive DELETE `/api/inventory-batches/{id}/push-day` action, combined warning codes, structured ended-season fields and 409 unsupported-version handling against the regenerated OpenAPI before deployment. See [record 006](../2026-11-16/06-f03-f05-followup-fixes.md). Earlier verification remains historical.

## Addendum 2026-11-16 (ROC-20261116-010) — P2–P6 resolved

The assumptions P2–P6 recorded here as "pending owner confirmation" were resolved by the owner on 2026-11-16: P2, P3 and P4 approved as built ([DEC-20261116-01](../../decisions/2026-11-16/DEC-20261116-01-p2-p4-approved.md)); P5 changed to full removal of the legacy product fields with migration V26 ([DEC-20261116-02](../../decisions/2026-11-16/DEC-20261116-02-p5-remove-legacy-product-fields.md)); P6 changed for `PUT /api/utility/config/expiry-window` and `PATCH /api/utility/products/{productId}/scores`, which are deleted instead of returning 410 ([DEC-20261116-03](../../decisions/2026-11-16/DEC-20261116-03-p6-delete-obsolete-routes.md)). Implementation: [record 010](../2026-11-16/10-remove-legacy-product-fields-and-routes.md). The text above is kept unchanged as history.

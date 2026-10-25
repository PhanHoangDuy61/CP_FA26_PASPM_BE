# Hybrid JPA delivery status — 2026-10-25 (Asia/Ho_Chi_Minh)

## Branches and review packages

- Dev inspected baseline:783725c7c8aac810f43fce4971a3b45ce6562a94; Catalog baseline7 PASS.
- Original Product Pilot12d348e73caccde8e2985ad58b311c0b1cbed308 preserved, including its user's dirty checkout/private config.
- Product integration:`Khoi/feat/product-jpa-integration`, pushed202761a81abf8ce627f55a4dd2eaa8194070546b. Normal merge retains Pilot/dev histories. Product PR base:`dev`. Creation attempt via GitHub App returned403; no actual PR created.
- [Create Product PR manually](https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/compare/dev...Khoi%2Ffeat%2Fproduct-jpa-integration?expand=1). Body:[PRODUCT_JPA_INTEGRATION_PR.md](PRODUCT_JPA_INTEGRATION_PR.md).
- Category:`Khoi/feat/category-jpa-crud-pilot`, based on verified Product202761a. Category PR base MUST be `Khoi/feat/product-jpa-integration` while Product is unmerged. Category publication/PR attempt result is recorded in the follow-up delivery entry below.
- [Create stacked Category PR manually](https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/compare/Khoi%2Ffeat%2Fproduct-jpa-integration...Khoi%2Ffeat%2Fcategory-jpa-crud-pilot?expand=1). Body:[CATEGORY_JPA_PR.md](CATEGORY_JPA_PR.md).

## Actual executed tests

No test was omitted to manufacture a pass. Local full suites use Java17 Backend, Java25 child SPMF, exact approved SPMF2.67a SHA256 `4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a`, synthetic MySQL8.4.13 and H2. Source V5 connection is not used.

| Run | Tests | PASS | FAIL | ERROR | SKIP |
|---|---:|---:|---:|---:|---:|
| Dev Catalog baseline |7|7|0|0|0|
| Product first focused |45|38|2|5|0|
| Product focused retest |47|47|0|0|0|
| Product final clean verify |302|302|0|0|0|
| Category original new-test baseline |28|12|8|8|0|
| Category baseline with actual API/exception contract |28|16|4|8|0|
| Category/Product focused |63|63|0|0|0|
| Category full clean verify |330|330|0|0|0|

Runs overlap; do not sum them as distinct cases. Product first failures were obsolete V10 migration expectations and restricted disposable-clone DDL setup, not a production workaround. Category initial test assumptions were corrected before implementing JPA; remaining red cases proved missing JPA behavior. Final full330 = prior302+28 Category executions.

Actual task runner commands (the private harness supplies the exact safe options listed in the Product report; no credentials in commands):

```sh
python3 /private/tmp/paspm-hybrid-integration/run.py product-after.log test -Dtest=ProductPersistenceIntegrationTest,ProductPersistenceMySqlIntegrationTest,JpaPilotMigrationMySqlTest,PilotCloneApiIntegrationTest,PilotCloneSafetyTest
python3 /private/tmp/paspm-hybrid-integration/run.py product-full.log clean verify
python3 /private/tmp/paspm-hybrid-integration/run.py category-red-corrected.log test -Dtest=CategoryPersistenceIntegrationTest,CategoryPersistenceMySqlIntegrationTest
python3 /private/tmp/paspm-hybrid-integration/run.py category-after.log test -Dtest=CategoryPersistenceIntegrationTest,CategoryPersistenceMySqlIntegrationTest,ProductPersistenceIntegrationTest,ProductPersistenceMySqlIntegrationTest
python3 /private/tmp/paspm-hybrid-integration/run.py category-full.log clean verify
```

These harness/config files are private local task tools, not committed production scripts or a generic command that may target source data. Product report documents the Maven equivalent and required opt-ins. MySQL CLI/restore wrappers copied unchanged from proven PR3 tooling were used outside Git; the Product/Category branches do not silently absorb the unmerged CI PR. Raw Surefire properties and raw DB dumps are excluded. Committed results contain case IDs/counts, not test tokens/options/passwords.

## Verification boundaries

LOCAL_VERIFIED and READY_FOR_REVIEW: Product and Category source, transaction safety, Store isolation, inventory/S/M preservation, F04 REQUIRES_NEW/idempotency, F05/F06 regression and supported recovery/FlywayV11. Fresh V1→V11, emptyV5, synthetic populatedV5 recovery→V11, existingV10→V11 execute. Unsupported bareV7 remains a negative failure characterization, distinct from supported acceptance. Existing approved recovery logic and migration bytes are unchanged.

Independent technical AI reviews: Product and Category completed; no unresolved verified required production defect. Optional coverage notes remain recorded. This is not human GitHub approval.

BLOCKED_REMOTE_CI: PR3 is still open/unmerged; approved immutable SPMF hosting URL remains unavailable. Product202761a PR-triggered workflow lookup returned no runs. Also provision the disposable Pilot clone with DBA migration before schema-limited runtime tests and enable all expected opt-ins in accepted CI. Never call local330 a GitHub-hosted result or mark unexecuted required CI green.

REVIEW_PENDING: eligible independent human identity/permission not established. Candidate PhanHoangDuy61 and author khoinm04 are different account names; that does not prove distinct eligible humans. No self-approval/branch-protection bypass. Previous merged PR1/2 are not retroactively approved.

REAL_DATA_BLOCKED: all data in this task are synthetic. No genuine authorized production dataset was verified. Original user checkout branch/head/status/config/migration fingerprints were unchanged. Original sourceV5 database received no task connection/write; no new source fingerprint audit is claimed.

NOT READY_FOR_MERGE: no PR merge, deployment, source repair or SPMF upload performed.

## Owner actions

1. Open Product PR againstdev and Category stacked PR against Product using the prepared bodies if App403 persists.
2. Resolve/accept CI3 separately; approve immutable SPMF artifact hosting and provision controlled Pilot clone CI without exposing secrets. Run all required jobs on these exact heads with no unexpected skips.
3. Assign an eligible independent human reviewer and inspect the committed technical evidence.
4. Authorize merge only after actual remote gates pass. Then authorize Category retarget/rebase todev; do not mix Product changes into an undisclosed Category/dev PR.
5. Supply an authorized real dataset/clone later if genuine data acceptance is required.

# Product Pilot readiness after independent historical recovery

Updated:2026-10-23T00:34:04.776583+07:00. **Recovery READY_FOR_REVIEW; READY_FOR_MERGE: NO.**

## Independent branch

`Khoi/Fear/fix-legacy-utility-matrix-migration` starts from dev3d58a73. It contains the B+C callback/preflight/backup workflow, additiveV11, protected archival/routing schema, compatible F03/F05 reads and F06 provenance refusal. It has no Product/Category JPA implementation dependency and does not merge the Pilot.

The previous populated-V5 1452 engineering gap is resolved for the supported registered migration path in isolated verification. A plain unregistered Flyway builder remains unsupported for populated legacy upgrades and is negatively characterized. This is not permission to mutate the configured V5 source or deploy.

## Executed gates

| Gate | Actual status |
|---|---|
|Feature Maven verify|254PASS,zero failures/errors/skips|
|Detached Pilot +scoped patch|299PASS,zero failures/errors/skips|
|Original V1–V10 bytes/checksums|Unchanged|
|Historical rows/hashes/Store identity|Preserved; six-table fingerprint and exact mining checks|
|Actual partialV7 failure/recovery|Failed copy retained; separate verified clean target succeeds|
|Original source|SELECT-only/read-only; V5 schema/history/data/config unchanged|
|Independent agent review|Findings corrected/retested; record linked below|
|Human review and remote CI|PENDING; not implied by local tests|
|Production operations/genuine backfill|NOT_AUTHORIZED / NOT_RUN|
|Automatic Product merge / Category migration|NOT_PERFORMED|

F06 historical recommendations remain limited by verified transaction-membership evidence. No association confidence/support is fabricated. Controlled interruption tests do not represent exhaustive OS-crash or production-scale operational proof.

PR status is recorded in the [migration manifest](../testing/evidence/historical_matrix_recovery/implementation/MIGRATION_MANIFEST.json); use [recovery design](V5_V7_HISTORICAL_MATRIX_RECOVERY_DESIGN.md), [operations](LEGACY_UTILITY_MATRIX_COMPATIBILITY.md), [test report](../testing/V5_V10_POPULATED_MATRIX_RECOVERY_TEST_PLAN.md) and [review history](../testing/evidence/historical_matrix_recovery/implementation/REVIEW_SUMMARY.md) for review. If API PR creation is403, a manual compare link and exact PR body are provided; no merge follows automatically.

## Delivery status

Feature branch pushed successfully. The actual GitHub API PR creation attempt returned **403 Resource not accessible by integration**; no PR was created and nothing was merged. [Create the PR manually](https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/compare/dev...Khoi%2FFear%2Ffix-legacy-utility-matrix-migration?expand=1) into `dev`, using the [prepared exact title and description](HISTORICAL_MATRIX_RECOVERY_PR_DESCRIPTION.md). Human review and remote CI remain pending. Dedicated test MySQL is stopped and temporary plaintext credentials are removed.

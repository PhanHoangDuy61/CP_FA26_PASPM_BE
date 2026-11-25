# ROC-20261121-001 — Merge F09 Storefront into dev

| Field | Value |
|---|---|
| Date / timezone | 2026-11-21 / Asia/Ho_Chi_Minh |
| Type | Feature integration / conflict resolution / verification |
| Feature | F09, with F02 Product display metadata and F07/F08 layout rules |
| Source branch | `Duy/Feature/F09_Storefront` at `0107582` |
| Target branch | Local `dev` at `6d2a877` before merge |
| Merge commit | `68d6f0b` |
| Release status | Merged locally; not pushed or deployed |

## Summary

Integrated F09 into the current local `dev`, which contained 30 commits beyond the F09 branch's base. Resolved conflicts by preserving server-generated, read-only Product SKU and current F07/F08 zone-cap, top-K baseline and equal-size simulation behavior while adding F09 Product `imageUrl`/`description` fields and Storefront APIs. Regenerated the runtime OpenAPI export.

## Verification

| Check | Result |
|---|---|
| `.\mvnw.cmd '-Dtest=StorefrontIntegrationTest,CatalogIntegrationTest,ProductPersistenceIntegrationTest,MigrationVersioningTest,LayoutCampaignIntegrationTest' '-Dpaspm.f02.export-dir=docs/api' test` | PASS, 51 tests; 0 failures, 0 errors, 0 skipped (Java 17, H2) |
| Runtime OpenAPI export | PASS, 107 paths / 128 operations; all five F09 API paths present |
| `git diff --cached --check` before merge commit | PASS |
| MySQL migration, hosted CI, browser/UI | NOT RUN |

The test run does not verify the V29 migration (renumbered from V27 during F01/F02 integration) on MySQL. F09 also retains its documented 503 response for an active public layout until the required recommendation-zone capacities are configured.

## Git state

Merge commit `68d6f0b` has parents `6d2a877` and `0107582`. Local `dev` is ahead of `origin/dev`; no push, PR or deployment was performed.

# ROC-20261125 — F07 product-level Layout B editor, Maven Wrapper, F09 Storefront merge, priority coverage

2026-11-25 / Asia/Ho_Chi_Minh.

Numbers for this day are fixed across branches: 001 F07 product-level Layout B editor, 002 Maven Wrapper, 003 source-of-truth rule, 004 demo seed, 005 F09 Storefront merge, 006 priority coverage. 003 and 004 live on their own branches.

## Record 001 — F07/F08 product placements

[Record 001](01-f07-product-layout-editor.md):
- individual placement contract and snapshot-product search;
- provenance-preserving revision saves and independent eligibility;
- F08 bundle grouping from saved Layout B `BUNDLE + blockNo`;
- real editor/campaign/simulation/report frontend flow;
- DEC and product/API documentation updates.

Verification: frontend lint/build and 568 tests passed; 34 focused backend integration tests passed. The full backend CI test group and browser review remain unrun.

Release status: **UNRELEASED**, feature branch only. No push.

## Record 002 — Executable Maven Wrapper

[Record 002](02-mvnw-executable.md) records the Git mode fix, setup guide updates and local wrapper verification.

## Record 005 — F09 Storefront MVP merged with dev

[Record 005](05-f09-storefront-merge-into-dev.md) merges Duy Phan's `Duy/Feature/F09_Storefront` with `dev` (`82b0ced`, then `516183d`). `dev` already held an old F09 merge (`68d6f0b`, pushed without a PR): its V29 is kept unchanged and new V30 drops `store.store_slug` / `store.storefront_enabled`. Public Store = ACTIVE Store by `store.slug` ([DEC-20261125-04](../../decisions/2026-11-25/DEC-20261125-04-f09-public-store-follows-store-status.md)), no visibility endpoint, `AVAILABLE | OUT_OF_STOCK`, soft-deleted products and the DEC-20261124-01 expiry boundary applied to every public read. Backend group 466 tests PASS on Java 17; MySQL 8.4 fresh V1→V30 and upgrade dev-V29→V30 PASS.

## Record 006 — Frozen snapshot priority coverage

[Record 006](06-f07-f08-priority-coverage.md) records the F07/F08 implementation of DEC-20261117-02 and the approved empty-layout and oversized-B behavior.

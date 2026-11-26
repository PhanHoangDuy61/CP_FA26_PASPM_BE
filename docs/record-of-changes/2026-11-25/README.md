# ROC-20261125 — F07 Layout B editor, Maven Wrapper, demo seed / SKU mapping, F09 Storefront merge, priority coverage, F10/F11 contracts, source-of-truth rule and SPMF (GPL v3) CI/CD

2026-11-25 / Asia/Ho_Chi_Minh. Records of this day are delivered through pull requests into `dev` ([DEC-20261119-01](../../decisions/2026-11-19/DEC-20261119-01-dev-changes-only-through-pr.md)).

Numbers for this day are fixed across branches: 001 F07 product-level Layout B editor, 002 Maven Wrapper, 003 source-of-truth rule, 004 demo seed / SKU mapping, 005 F09 Storefront merge, 006 priority coverage, 007 SPMF CI artifact, 008 F10/F11 planned contracts, 009 OpenAPI and feature status sync, 010 F07 OpenAPI annotations. 003 and 007 live on their own branches until merged.

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

## Record 003 — Source-of-truth rule: the latest PO decision wins

[Record 003](03-source-of-truth-rule.md) replaces the "docs làm chuẩn" precedence in `AGENTS.md`, `CLAUDE.md`, both `paspm-context` skill copies and `BRD_VISION.md` §9.3 with [DEC-20261125-02](../../decisions/2026-11-25/DEC-20261125-02-source-of-truth-latest-po-decision.md): docs record decisions as of their date, the latest PO decision wins, and code/docs differences are reported to the PO and then recorded as a DEC. Docs only; Maven NOT RUN.

## Record 004 — Demo seed, FoodMart mapping and JpaPilotMigrationMySqlTest

[Record 004](04-sku-foodmart-seed.md) records [DEC-20261119-07](../../decisions/2026-11-19/DEC-20261119-07-server-generated-product-sku.md) (server-generated SKU), fixes the demo seed (products by name, `mapping.csv`, import-ready CSV copies, no `batchCode`, new push-day mix) and `JpaPilotMigrationMySqlTest`, and registers that test in the `mysql` group. Seed: two runs on a fresh MySQL 8.4, 0 rejected orders, second run idempotent. Backend group 441 PASS, mysql group 105 PASS on Java 17.

## Record 005 — F09 Storefront MVP merged with dev

[Record 005](05-f09-storefront-merge-into-dev.md) merges Duy Phan's `Duy/Feature/F09_Storefront` with `dev` (`82b0ced`, then `516183d`, then `2d1280b`). `dev` already held an old F09 merge (`68d6f0b`, pushed without a PR): its V29 is kept unchanged and new V30 drops `store.store_slug` / `store.storefront_enabled`. Public Store = ACTIVE Store by `store.slug` ([DEC-20261125-04](../../decisions/2026-11-25/DEC-20261125-04-f09-public-store-follows-store-status.md)), no visibility endpoint, `AVAILABLE | OUT_OF_STOCK`, soft-deleted products and the DEC-20261124-01 expiry boundary applied to every public read. Backend group 466 tests PASS on Java 17; MySQL 8.4 fresh V1→V30 and upgrade dev-V29→V30 PASS.

## Record 006 — Frozen snapshot priority coverage

[Record 006](06-f07-f08-priority-coverage.md) records the F07/F08 implementation of DEC-20261117-02 and the approved empty-layout and oversized-B behavior.

## Record 007 — SPMF notices, license in the Docker image, amd64 build, process-boundary guard

[Record 007](07-spmf-gpl-ci-artifact.md) implements [DEC-20261125-03](../../decisions/2026-11-25/DEC-20261125-03-spmf-gpl-distribution-and-process-boundary.md):
- `THIRD_PARTY_NOTICES.md` and `third_party/spmf/` (GPL v3 text, `SOURCE.md`); the Docker image copies them next to the jar and labels the image;
- `scripts/staging/build-image.sh` builds `linux/amd64` by default; the build was run on an arm64 Mac without an emulation failure;
- `SpmfProcessBoundaryTest` and `SpmfThirdPartyNoticesTest`; the rule "SPMF only as a separate process" in `CLAUDE.md`, `AGENTS.md` and both `paspm-context` skills;
- no API, schema or runtime change.

Backend group: 449 tests, 0 failures. The owner created the Release `spmf-2.67a` (verified from the public API) and set `PASPM_CI_SPMF_ARTIFACT_URL`; still to do: Release immutability (API shows `immutable: false`) and a re-run of the `integration` job.

Record 007 also carries the CI skipped-test policy ([DEC-20261125-05](../../decisions/2026-11-25/DEC-20261125-05-ci-skipped-test-allowlist.md), section 10 of the record).

## Record 008 — F10/F11 planned MVP contracts

[Record 008](08-f10-f11-mvp-contract.md) records the approved F10/F11 design, planned APIs and frontend handoffs.

## Record 009 — OpenAPI and feature status sync

[Record 009](09-openapi-status-sync.md) captures the runtime comparison on dev and the F07 schema question.

## Record 010 — F07 OpenAPI annotations

[Record 010](10-f07-openapi-annotations.md) lists explicit operation IDs and verifies the placement schema metadata.

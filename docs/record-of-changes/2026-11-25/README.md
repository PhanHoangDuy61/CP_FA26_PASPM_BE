# ROC-20261125 — F07 Layout B editor, Maven Wrapper, demo seed / SKU mapping and priority coverage; SPMF (GPL v3) CI/CD

2026-11-25 / Asia/Ho_Chi_Minh. Records of this day are delivered through pull requests into `dev` ([DEC-20261119-01](../../decisions/2026-11-19/DEC-20261119-01-dev-changes-only-through-pr.md)). Other records of this day (003 source-of-truth rule, 005 F09 Storefront, SPMF CI artifact) live on their own branches until merged.

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

## Record 004 — Demo seed, FoodMart mapping and JpaPilotMigrationMySqlTest

[Record 004](04-sku-foodmart-seed.md) records [DEC-20261119-07](../../decisions/2026-11-19/DEC-20261119-07-server-generated-product-sku.md) (server-generated SKU), fixes the demo seed (products by name, `mapping.csv`, import-ready CSV copies, no `batchCode`, new push-day mix) and `JpaPilotMigrationMySqlTest`, and registers that test in the `mysql` group. Seed: two runs on a fresh MySQL 8.4, 0 rejected orders, second run idempotent. Backend group 441 PASS, mysql group 105 PASS on Java 17.

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

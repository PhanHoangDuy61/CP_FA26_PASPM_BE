# ROC-20261125 — F07 product-level Layout B editor; SPMF (GPL v3) CI/CD

Owner-approved per-product Layout B editing across backend and frontend.

## Record 001 — F07/F08 product placements

[Record 001](01-f07-product-layout-editor.md):
- individual placement contract and snapshot-product search;
- provenance-preserving revision saves and independent eligibility;
- F08 bundle grouping from saved Layout B `BUNDLE + blockNo`;
- real editor/campaign/simulation/report frontend flow;
- DEC and product/API documentation updates.

Verification: frontend lint/build and 568 tests passed; 34 focused backend integration tests passed. The full backend CI test group and browser review remain unrun.

Release status: **UNRELEASED**, feature branch only. No push.

## Record 002 — SPMF notices, license in the Docker image, amd64 build, process-boundary guard

[Record 002](02-spmf-gpl-ci-artifact.md) implements [DEC-20261125-01](../../decisions/2026-11-25/DEC-20261125-01-spmf-gpl-distribution-and-process-boundary.md):
- `THIRD_PARTY_NOTICES.md` and `third_party/spmf/` (GPL v3 text, `SOURCE.md`); the Docker image copies them next to the jar and labels the image;
- `scripts/staging/build-image.sh` builds `linux/amd64` by default; the build was run on an arm64 Mac without an emulation failure;
- `SpmfProcessBoundaryTest` and `SpmfThirdPartyNoticesTest`; the rule "SPMF only as a separate process" in `CLAUDE.md`, `AGENTS.md` and both `paspm-context` skills;
- no API, schema or runtime change.

Backend group: 449 tests, 0 failures. The owner created the Release `spmf-2.67a` (verified from the public API) and set `PASPM_CI_SPMF_ARTIFACT_URL`; still to do: Release immutability (API shows `immutable: false`) and a re-run of the `integration` job.

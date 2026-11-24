# ROC-20261125 — SPMF (GPL v3) CI/CD: notices, license in the image, amd64 build

2026-11-25 / Asia/Ho_Chi_Minh. Branch `Khoi/chore/spmf-ci-artifact` from `dev` `82b0ced` (PR #28). Committed and pushed; PR not opened.

## Record 001 — SPMF notices, license in the Docker image, amd64 build, process-boundary guard

[Record 001](01-spmf-gpl-ci-artifact.md) implements [DEC-20261125-01](../../decisions/2026-11-25/DEC-20261125-01-spmf-gpl-distribution-and-process-boundary.md):
- `THIRD_PARTY_NOTICES.md` and `third_party/spmf/` (GPL v3 text, `SOURCE.md`); the Docker image copies them next to the jar and labels the image;
- `scripts/staging/build-image.sh` builds `linux/amd64` by default; the build was run on an arm64 Mac without an emulation failure;
- `SpmfProcessBoundaryTest` and `SpmfThirdPartyNoticesTest`; the rule "SPMF only as a separate process" in `CLAUDE.md`, `AGENTS.md` and both `paspm-context` skills;
- no API, schema or runtime change.

Backend group: 449 tests, 0 failures. The owner created the Release `spmf-2.67a` (verified from the public API) and set `PASPM_CI_SPMF_ARTIFACT_URL`; still to do: Release immutability (API shows `immutable: false`) and a re-run of the `integration` job.

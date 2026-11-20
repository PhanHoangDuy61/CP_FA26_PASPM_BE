# DEC-20261119-01 — `dev` changes only through a pull request with green CI

| Field | Value |
|---|---|
| Date | 2026-11-19 (Asia/Ho_Chi_Minh) |
| Source | Owner decision of 2026-11-19; recorded in [ROC-20261119-001](../../record-of-changes/2026-11-19/01-record-owner-decisions.md) |
| Status | OWNER_DECISION — process rule, effective 2026-11-19 |
| Supersedes | The practice of merging locally and pushing the merge commit to `dev` (used for `705e3d7`, `ac29811` and `530651b`) |

## Decision
- `dev` only changes through a pull request into `dev` whose CI ("Backend verification", `.github/workflows/backend-verification.yml`) is green.
- No direct push to `dev` and no force-push. Work happens on new branches `<member>/<type>/<topic>`.

## Notes
- The CI `integration` job needs the repository variable `PASPM_CI_SPMF_ARTIFACT_URL`; it was recorded as missing on 2026-11-16 ([ROC-20261116-012](../../record-of-changes/2026-11-16/12-f05-foodmart-scale.md)). Until it is set, "green CI" cannot include that job; the repository admin must set it.
- Whether GitHub branch protection enforces this rule was not checked from this repository (no API access recorded); configuring it is a repository-admin task.

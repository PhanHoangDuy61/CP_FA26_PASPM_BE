# ROC-20261125-002 — Executable Maven Wrapper

| Field | Value |
|---|---|
| Date / timezone | 2026-11-25 / Asia/Ho_Chi_Minh |
| Agent / type | Codex / Build tooling |
| Branch / base | `Khoi/chore/mvnw-executable` / cached `origin/dev` at `5436bf7` |
| Execution status | COMPLETED locally |
| Release status | UNRELEASED |
| Daily summary | [2026-11-25](README.md) |

## Change

Set the Git mode of `mvnw` to `100755` for direct use on macOS and Linux. Its contents and the Windows wrapper are unchanged. Updated the [common setup](../../setup/LOCAL_SETUP_COMMON.md), [macOS setup](../../setup/LOCAL_SETUP_MACOS.md) and [troubleshooting guide](../../setup/LOCAL_SETUP_TROUBLESHOOTING.md). Historical verification records retain the commands actually used. This change has no CI workflow, API or database impact.

## Verification

- `git ls-files -s mvnw mvnw.cmd` reported modes `100755` and `100644`, respectively.
- `./mvnw -v` exited 0 on macOS arm64 with Apache Maven 3.9.9 with both Java 17.0.21 and Java 24.0.2. No application build or test suite was run.
- `git fetch origin` failed with a GitHub DNS resolution error. The branch uses the cached base. Hosted CI is NOT RUN.

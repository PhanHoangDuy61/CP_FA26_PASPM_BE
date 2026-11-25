# ROC-20261125-002 — SPMF (GPL v3): notices, license in the image, amd64 build, process-boundary guard

| Field | Value |
|---|---|
| Record ID | ROC-20261125-002 |
| Date / timezone | 2026-11-25 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Sonnet 5.5) |
| Type | Implementation (build and CI support) / Documentation |
| Features | F05 runtime (SPMF), CI, Docker deployment. No API, schema or runtime behaviour change |
| Branch / base HEAD | `Khoi/chore/spmf-ci-artifact` / `origin/dev` `82b0ced` (merge of PR #28) |
| Execution status | COMPLETED. The GitHub Release and the Actions variable were created by the owner, not by this change (section 7) |
| Release status | BRANCH_PUSHED — committed and pushed to `origin/Khoi/chore/spmf-ci-artifact`; PR not opened, not merged |
| Daily summary | [README of the day](README.md) |

## 1. Request and scope
- The Product Owner asked to finish CI/CD for the SPMF dependency (GPL v3). Phase 1 was an audit and Phase 2 a plan; on 2026-11-25 the owner approved all 7 recommendations and asked for part C only: notices and license files, Dockerfile copy and `LABEL`, an amd64 build script, `SpmfProcessBoundaryTest`, a DEC, docs and this record.
- Rules: [DEC-20261125-01](../../decisions/2026-11-25/DEC-20261125-01-spmf-gpl-distribution-and-process-boundary.md).
- Audit result that shaped the work: the repository is public, so the credential-free downloader works with a Release asset and no token workflow is needed (part B was skipped by the owner); SPMF is only ever started with `ProcessBuilder` and `java -jar` (`SpmfMiningEngine.java:50-52`, `SpmfRuntime.java:76`), with no import and no Maven dependency; the CI `integration` job fails today because `PASPM_CI_SPMF_ARTIFACT_URL` is unset (seen on PR #28's head).
- The main checkout and its uncommitted `application.yml` were never touched (all work is in the worktree `outputs/spmf-ci-wt`).

## 2. Change details

### Added
- `THIRD_PARTY_NOTICES.md` (SPMF only), `third_party/spmf/LICENSE-GPL-3.0.txt` (the copy inside SPMF 2.67a, 35,147 bytes, SHA-256 `8ceb4b9e…b903`), `third_party/spmf/SOURCE.md`, and `.gitattributes` (`third_party/spmf/* -text`, so the license bytes stay identical on Windows).
- Tests `SpmfProcessBoundaryTest` (no SPMF import in `src/main` and `src/test`, no SPMF in `pom.xml`, the scanner itself is checked on synthetic input) and `SpmfThirdPartyNoticesTest` (license hash, notices carry the same pin as `provision-spmf.py`, the Dockerfile copies and labels, the build script defaults to amd64); both registered in the `backend` group of `.github/scripts/test-groups.py`.
- DEC-20261125-01.

### Changed
- `Dockerfile`: copies `third_party/spmf/` and `THIRD_PARTY_NOTICES.md` to `/opt/paspm/licenses/` next to the jar and adds `LABEL`s (`paspm.third-party.spmf.version|license|sha256|source|usage`).
- `scripts/staging/build-image.sh`: `PLATFORM` defaults to `linux/amd64` (`linux/arm64` allowed), `docker buildx build --platform … --load`, checks the image architecture, lists `/opt/paspm/licenses`, verifies the banner `-- SPMF version 2.67a --` inside the image (`SKIP_SMOKE=1` skips it), refuses to run if the license files are missing.
- Rule "SPMF runs only as a separate process; never import it" added to `CLAUDE.md`, `AGENTS.md`, both `paspm-context` skills and `F05_SPMF_RUNTIME_SETUP.md`.
- Docs: `SPMF_ARTIFACT_HOSTING.md` (APPROVED, final URL and asset list), `BACKEND_CI.md`, `STAGING.md`, `docs/product/ARCHITECTURE_SDD.md` (mining row), `BRD_VISION.md` (risk row and decision log).

### Fixed / Security / Database
- N/A. No schema change, no API change, no change to `provision-spmf.py` (SHA-256 pin, size limit and redirect policy untouched).

## 3. Files changed

| Path | Added / Modified | Content |
|---|---|---|
| `THIRD_PARTY_NOTICES.md`, `third_party/spmf/*`, `.gitattributes` | Added | notices, license, source note |
| `Dockerfile`, `scripts/staging/build-image.sh` | Modified | license copy, labels, amd64 |
| `src/test/java/vn/edu/fpt/paspm/mining/SpmfProcessBoundaryTest.java`, `SpmfThirdPartyNoticesTest.java` | Added | guards |
| `.github/scripts/test-groups.py` | Modified | two classes in `backend` |
| `CLAUDE.md`, `AGENTS.md`, `.claude/skills/paspm-context/SKILL.md`, `.agents/skills/paspm-context/SKILL.md` | Modified | the rule |
| `docs/decisions/2026-11-25/DEC-20261125-01-…md`, `docs/setup/{F05_SPMF_RUNTIME_SETUP,SPMF_ARTIFACT_HOSTING,BACKEND_CI,STAGING}.md`, `docs/product/{ARCHITECTURE_SDD,BRD_VISION}.md` | Added / Modified | decision and facts |
| `docs/record-of-changes/2026-11-25/*`, `docs/record-of-changes/README.md` | Added / Modified | this record |

## 4. API and compatibility impact
None for clients. The Docker image gains files under `/opt/paspm/licenses/` and labels, and the default image platform changes from "the build host" to `linux/amd64` (use `PLATFORM=linux/arm64` for the old behaviour on an arm64 host).

## 5. Verification executed in this session
Java 17 (`JAVA_HOME=~/.sdkman/candidates/java/17.0.21.0+9-tem`), `sh ./mvnw` (the wrapper is mode 100644).

| Command / method | Environment | Actual result | Evidence |
|---|---|---|---|
| `-Dtest='SpmfProcessBoundaryTest,SpmfThirdPartyNoticesTest'` before the implementation | Java 17 | red: `SpmfThirdPartyNoticesTest` 4/4 failed (files, Dockerfile and script missing); `SpmfProcessBoundaryTest` 4/4 passed at once because the property already held (its scanner is checked on synthetic input) | |
| same, after the implementation | Java 17 | PASS 8/8 | |
| `sh ./mvnw -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py backend)"` | Java 17, H2 | **449 tests, 0 failures, 0 errors, 0 skipped** (441 before + 8 new); BUILD SUCCESS; `python3 .github/scripts/test-summary.py` agrees | |
| `SPMF_JAR_PATH=…/spmf.jar bash scripts/staging/build-image.sh spmf-ci-amd64 --skip-jar` (jar from the backend run) | Docker Desktop 28.4 on **arm64 macOS**, buildx 0.28, builder `desktop-linux`; Java 25 JRE `eclipse-temurin:25-jre-jammy` (25.0.5+8) | **PASS, no emulation failure.** `built paspm-backend:spmf-ci-amd64 (amd64)`; the Dockerfile `RUN` steps ran under emulation, including the SHA-256 check and `java -version` of Java 25 | log of the run |
| Inspection of the built image | same | `Architecture=amd64`, `User=10001:10001`, size 256,864,455 bytes; `uname -m` = `x86_64`; `/opt/paspm/spmf.jar` SHA-256 `4aa46838…dfe6a`; `/opt/paspm/licenses/spmf/LICENSE-GPL-3.0.txt` SHA-256 `8ceb4b9e…b903`; `SOURCE.md` and `THIRD_PARTY_NOTICES.md` present and world-readable; five `paspm.third-party.spmf.*` labels | |
| `java -jar /opt/paspm/spmf.jar version` and a small `run EFIM` on a 5-row utility file inside the image | same, amd64 under emulation | banner `-- SPMF version 2.67a --`; EFIM wrote its output file (`@FILETYPE="High-utility itemsets"`, 12 lines) in about 1.5 s of wall time | |
| MySQL group, full integration group, hosted CI | | **NOT RUN** (no code, schema or runtime path changed; the integration job needs the Release and the variable) | |

## 6. Previous evidence referenced
- Phase 1 audit and plan of the same day (`side-session-notes/…/spmf-ci-artifact-plan.md`): PR #28's `integration` check failed with "SPMF provisioning unavailable"; the repository is public (unauthenticated API: `private: false`); a Release URL redirects to `release-assets.githubusercontent.com`, which `provision-spmf.py:43` allows; the official SPMF site serves 2.69 (2026-11-22); the local `spmf.zip` is the official 2.67a source (3,051 `.java` files, `SPMF_VERSION = "2.67a"`).

## 7. Limitations and next actions
- **Owner actions:** the owner reported on 2026-11-25 that the Release `spmf-2.67a` is created, the downloaded `spmf.jar` hash matches and `PASPM_CI_SPMF_ARTIFACT_URL` is set. **Verified here from the public API:** the Release exists (not a draft, not a pre-release, published 2026-11-24T17:40:10Z UTC) with the four assets and sizes `spmf.jar` 15,907,956 / `LICENSE-GPL-3.0.txt` 35,147 / `SOURCE.md` 1,935 / `spmf-2.67a-src.zip` 34,107,808; their SHA-256 digests match the pins (`4aa46838…dfe6a`, `8ceb4b9e…b903`, `48599087…9c10`; `SOURCE.md` `0bfbfd80…2e7a` equals the repository copy); a fresh unauthenticated download of the asset URL gives 15,907,956 bytes with SHA-256 `4aa46838…dfe6a`. **Not verifiable without a token:** the value of the Actions variable. **Still open:** the API reports `immutable: false`, so Release immutability (DEC-20261125-01 §4) is not on yet. **Still to do:** re-run the `integration` job of an open PR and read its result; no green `integration` run is claimed here.
- The binary/source link is evidenced by names only (all 3,051 sources have a class in the jar; 38 jar classes have no same-named source file, normal for multi-class files); no rebuild was done (DEC §3).
- No CI job builds the image and no registry is chosen. Building on a host of another architecture works through emulation (shown here on arm64), but a native amd64 host is not tested.
- Windows checkouts: `.gitattributes` keeps the license bytes; `SpmfThirdPartyNoticesTest` would catch a change. Not run on Windows.

## 8. Delivery
Commit: one commit on `Khoi/chore/spmf-ci-artifact` (hooks not bypassed), made at the owner's request after the Release was created. Push: `git push -u origin Khoi/chore/spmf-ci-artifact`, no force. PR: **NOT OPENED, NOT MERGED**; `gh` is not installed, compare URL `https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/compare/dev...Khoi/chore/spmf-ci-artifact?expand=1`; the PR body draft is `outputs/pr-bodies/PR-spmf-ci-artifact.md`. The test image `paspm-backend:spmf-ci-amd64` and `target/staging-build` were deleted after the commit. The main checkout is untouched.

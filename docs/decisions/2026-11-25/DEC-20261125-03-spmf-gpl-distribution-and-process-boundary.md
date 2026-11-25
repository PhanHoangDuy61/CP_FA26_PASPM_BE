# DEC-20261125-03 — SPMF (GPL v3): public Release, license in the image, separate-process rule

| Field | Value |
|---|---|
| Date | 2026-11-25 (Asia/Ho_Chi_Minh) |
| Source | Product Owner answers to the 7 questions of the "finish CI/CD for SPMF" task (2026-11-25), which approve the plan in `docs/setup/SPMF_ARTIFACT_HOSTING.md` (package of 2026-10-25, NEEDS_OWNER_APPROVAL until now) |
| Status | OWNER_DECISION, implemented on branch `Khoi/chore/spmf-ci-artifact`; PR to `dev` pending. The owner created the Release `spmf-2.67a` and set the Actions variable on 2026-11-25; Release immutability (§4) is still to be switched on |
| Feature | F05 (mining runtime), CI, Docker deployment |
| Related | DEC-20261119-01 (PR-only `dev`), DEC-20261119-05 (whole-đồng input), `docs/setup/SPMF_ARTIFACT_HOSTING.md`, `docs/setup/BACKEND_CI.md` |

## Context
- SPMF is licensed under the GNU General Public License v3. PASPM needs its `spmf.jar` in CI (the `integration` job) and in the Docker image.
- Before this decision no Release existed and the Actions variable `PASPM_CI_SPMF_ARTIFACT_URL` was unset, so the CI `integration` job failed with "SPMF provisioning unavailable" (observed on PR #28's head, 2026-11-25).
- The official SPMF site now serves version 2.69 (2026-11-22) through "latest" links, so the approved 2.67a can no longer be fetched from it.
- The repository is public, so a Release asset is downloadable without a token and the credential-free downloader `provision-spmf.py` works unchanged.

## Decision
1. **Public redistribution is approved** for SPMF 2.67a through a GitHub Release of this repository, **with** the GPL v3 text and the corresponding source.
2. **Release assets** (tag **`spmf-2.67a`**, not marked "latest"): `spmf.jar` (15,907,956 bytes, SHA-256 `4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a`), `LICENSE-GPL-3.0.txt` (SHA-256 `8ceb4b9ee5adedde47b31e975c1d90c73ad27b6b165a1dcd80c7c545eb65b903`), `SOURCE.md`, and `spmf-2.67a-src.zip` (the official source distribution downloaded on 2026-10-17, 34,107,808 bytes, SHA-256 `48599087a24bd43fc1f32dac05cd2390f3895da437e092a7c0b7e6c7426a9c10`).
3. **Binary-to-source evidence is accepted at the level of names** (all 3,051 source files have a class in the jar; `AlgoEFIM` and `AlgoHUIMiner` are in both). It is not a rebuild. `SOURCE.md` says "downloaded from the official site on 2026-10-17" and "unmodified".
4. **Release immutability is switched on after the download check** (the downloaded asset must hash to the pin first), because an immutable Release cannot be corrected.
5. **CI variable.** `PASPM_CI_SPMF_ARTIFACT_URL` = `https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/releases/download/spmf-2.67a/spmf.jar` (credential-free, no query, fixed tag). The SHA-256 pin, the size limit and the redirect policy of `provision-spmf.py` stay unchanged. **No token workflow** (the repository is public); the private-repository design is not implemented.
6. **Docker image.** The license, `SOURCE.md` and `THIRD_PARTY_NOTICES.md` are copied to `/opt/paspm/licenses/` next to `/opt/paspm/spmf.jar`; `LABEL`s record the version, license, pin, source URL and "unmodified, separate process". The image is built for **linux/amd64** by default (`scripts/staging/build-image.sh`, `docker buildx build --platform linux/amd64 --load`), which checks the architecture and the banner `-- SPMF version 2.67a --` inside the image.
7. **Notices.** `THIRD_PARTY_NOTICES.md` lists **SPMF only** (SPMF version, GPL v3, SHA-256, source link, "run as a separate process, unmodified"); `third_party/spmf/` keeps the license and `SOURCE.md` (the license is stored byte for byte: `.gitattributes` marks it `-text`).
8. **Process-boundary rule.** Backend code must **never import an SPMF class**, never declare SPMF in `pom.xml` and never bundle it. SPMF runs only as `java -jar spmf.jar run EFIM …` in a child process (Java 25) and exchanges files and command-line arguments. The rule is written in `CLAUDE.md`, `AGENTS.md`, both `paspm-context` skills, `F05_SPMF_RUNTIME_SETUP.md` and `THIRD_PARTY_NOTICES.md`, and enforced by `SpmfProcessBoundaryTest` (scans `src/main`, `src/test` and `pom.xml`).
9. **Version pin.** SPMF stays at 2.67a, although 2.69 exists. Changing the version needs a new DEC, a new Release and new pins.

The licence reading ("a separate program that exchanges files and arguments is not a derived work") is the project's working assumption, not legal advice.

## Consequences
- No API, schema or runtime behaviour changes.
- New files: `THIRD_PARTY_NOTICES.md`, `third_party/spmf/{LICENSE-GPL-3.0.txt, SOURCE.md}`, `.gitattributes`; changed: `Dockerfile`, `scripts/staging/build-image.sh`, docs.
- Tests: `SpmfProcessBoundaryTest`, `SpmfThirdPartyNoticesTest` (registered in the `backend` group).
- The CI `integration` job was red and "Required Backend verification" reported `SPMF_INTEGRATION_CI_BLOCKED` until the Release and the variable existed; it must be re-run, and only a green run counts as passed.
- Not in scope: a CI job that builds the image; a registry for the image; a token-based download.

# SPMF 2.67a — owner approval and artifact hosting package

Prepared 2026-10-25, Asia/Ho_Chi_Minh. **NEEDS_OWNER_APPROVAL**. No Release/tag/artifact was created, published or uploaded. No URL is approved/configured by this package. Use only the existing binary; do not rebuild or substitute a later release.

## Verified artifact identity and remaining source evidence

- Workspace path: `spmf_run/spmf.jar` (relative from Backend: `../spmf_run/spmf.jar`). Size15907956 bytes.
- SHA256: `4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a`.
- Actual banner: `-- SPMF version 2.67a --`; Main.class major69, requires Java25. Backend stays Java17.
- Workspace README records the official distribution as download origin. This is a local provenance statement, not an independently authenticated upstream signature or historic upstream checksum.
- The JAR includes GPL3 license copies but **zero .java source files**. The workspace test_files folder contains examples/license text, not verified complete Corresponding Source for this binary. Exact corresponding source/build information remains **UNVERIFIED**. No latest source was downloaded or relabeled2.67a. The inspected [official download page](https://www.philippe-fournier-viger.com/spmf/download.php) links moving spmf.zip/spmf.jar and lists2.67 release notes; that alone does not prove exact2.67a source correspondence.

The upstream [SPMF developer guidance](https://www.philippe-fournier-viger.com/spmf/developers.php) identifies GPL3 licensing. The [GPLv3 text, sections4/6(d)](https://www.gnu.org/licenses/gpl-3.0-standalone.html) and [FSF explanation of source availability](https://www.gnu.org/licenses/gpl-faq.en.html#AnonFTPAndSendSources) require preservation of applicable notices/license and adequate access to the source corresponding to distributed object code. For network distribution, equivalent source access can be provided with the binary or through clear adjacent directions to another qualifying source location; the distributor remains responsible for availability. A generic moving upstream download page or unrelated repository's automatically generated source ZIP does not establish that exact correspondence.

Before publication the owner must identify the matching complete2.67a source/build materials, retain copyright/license/warranty notices, verify the intended distribution route and record approval. This package does not infer permission to redistribute from possession of the JAR, from local tests, or from the fact PASPM uses a separate JVM. It does not determine a license for PASPM itself.

## Option A — GitHub Release, preferred after approval

Use an owner-controlled public artifact repository only if the owner approves public redistribution and the accompanying source/license package. Backend repository has no Releases at inspection time. A dedicated runtime-artifact repository can keep third-party provenance separate; its creation/location is an owner decision, not an action already performed.

1. Repository **Settings → General → Releases → Enable release immutability** before creating the intended Release. GitHub's [immutability instructions](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/establish-provenance-and-integrity/prevent-release-changes) say this applies to future Releases. If the setting is unavailable, obtain an owner-approved immutable alternative; a tag alone does not establish immutable assets.
2. **Releases → Draft a new release**. Choose a fixed new tag; proposed label only: `paspm-spmf-2.67a-sha256-4aa46838`. Do not mark a moving latest URL as the CI source.
3. Prepare all assets before publishing: the exact existing `spmf.jar`, SHA256 text, applicable GPL3 license/notices, and verified corresponding source/build materials or explicit compliant equivalent-access instructions. A PASPM source ZIP generated from the tag does not substitute for SPMF source.
4. Fill the release notes using the checklist below. Publish only after owner approval and completeness review; immutable Releases prevent later asset replacement/addition/deletion, so an incomplete package must not be published first. See [GitHub Release management](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository).
5. Independently download the published fixed asset, compare the approved SHA, and run its version plus HUI-Miner/EFIM smoke checks under Java25. Verify source/license links work for the same recipients. Keep the approval/provenance record.
6. Copy the **fixed-tag asset URL**, not a temporary signed CDN URL, into the Actions variable after approval.

URL format only (not a published or approved PASPM URL):

```text
https://github.com/<owner>/<artifact-repository>/releases/download/<fixed-tag>/spmf.jar
```

Required release-note facts:

- Original software/version: SPMF2.67a; original author/copyright notices preserved.
- Exact binary SHA256 above; Main.class69; Java25+ runtime.
- Actual upstream provenance evidence and any gaps; whether binary is unmodified.
- Applicable GPLv3 text/notices and exact corresponding source/build information or equivalent-access location.
- Owner approval reference, date and chosen hosting/distribution scope.
- Immutable Release status and fixed asset URL after publication.

These are fields to complete with verified facts, not claims that source availability, licensing review or publishing has occurred.

## Option B — managed HTTPS artifact storage

Use a team-controlled store with a fixed version/content-addressed path and owner-approved retention/object-lock or no-overwrite policy. Keep the same exact JAR, license/notices, corresponding-source evidence, audit record and post-publication checksum/banner checks. Restricted storage is not an automatic exemption from applicable distribution obligations.

Current downloader supports credential-free HTTPS on port443 and same-origin query-free redirects. A custom storage host cannot redirect to arbitrary external hosts or reuse the GitHub CDN exception. If authenticated/private retrieval, a different CDN or signed custom-host redirect is required, approve that exact host/authentication design separately; do not paste tokens/signed URLs into repository variables or widen an allowlist without review.

## Downloader policy prepared for GitHub Releases

The owner-configured source URL must remain HTTPS, query-free, fragment-free and userinfo-free. Only a fixed-tag GitHub Release download source may redirect to exact `release-assets.githubusercontent.com`; signed query parameters are allowed only at that CDN. Custom hosting remains same-origin. Every redirect and final response is checked; HTTP downgrade, unapproved hosts and non-GET/HEAD redirects are refused. The source URL's owner approval is still required; this transport exception is not approval of any repository's binary.

The downloader retains the exact SHA pin,64MiB cap, owner-only temporary file, atomic final rename and cleanup on failure. Destination is new in a private isolated CI directory; concurrent publishers to the same destination are unsupported. Full workflow additionally executes the real2.67a banner check under Java25 before Backend tests. Do not use the moving upstream latest JAR.

## Owner GitHub UI actions

1. Approve and publish a complete OptionA or OptionB package, including the exact corresponding-source/license requirements; provide the resulting immutable asset URL and approval reference. No upload is authorized yet.
2. Backend repository **Settings → Secrets and variables → Actions → Variables → New repository variable** (or edit existing): name `PASPM_CI_SPMF_ARTIFACT_URL`; value is the approved fixed HTTPS source URL. No token, password or signed query. Current integration cannot read this setting directly (401); no value was logged or changed.
3. The GitHub App cannot update PR metadata (403); owner/author can use the title/body in [prepared PR description](../testing/BACKEND_CI_REMOTE_FIX_PR_DESCRIPTION.md). On [PR#3](https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/pull/3), establish a different eligible human reviewer. Owner can verify **Settings → Collaborators/Manage access**, confirm identities with the team, and request that person through **Reviewers**. Reviewer must read the latest diff and submit an actual review; no earlier merged PR is retroactively approved.
4. After the variable is set, re-run the latest PR/head workflow using available authorized automation, or open its Actions run and choose **Re-run all jobs**. Verify the SHA/checksum/banner, separate Java17/25, full tests zero failures/errors/skips and the required aggregate. The workflow may not appear in the manual Run workflow menu while it is absent from default main; the PR/push workflow's existing run is the reliable rerun entry point.

No merge is authorized by these instructions. READY_FOR_MERGE remains NO until approved artifact hosting, full hosted integration and eligible independent review all complete.

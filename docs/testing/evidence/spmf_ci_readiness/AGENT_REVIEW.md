# Agent review: scoped SPMF redirect fix

Reviewed the current working tree against baseline `541217847ee4c6068b6ed61877b5ba58548ab227` in `/tmp/paspm-legacy-matrix-recovery`. Scope: `.github/scripts/provision-spmf.py`, its new artifact-independent policy tests, the signed-response positive case in `test_ci_helpers.py`, and `backend-verification.yml`. No applicable AGENTS.md was found. This is an agent security/code review, not independent human GitHub approval or merge authorization.

## Strengths

- Input and redirect destinations share explicit HTTPS, host, port, userinfo, fragment, control-character, and backslash validation. The updated `is not None` userinfo check also rejects an empty userinfo component.
- The redirect policy is bound to the original configured source. Only the fixed-release GitHub source shape enables the exact `release-assets.githubusercontent.com` delegation; a CDN hop cannot expand the host set. Custom origins retain unsigned same-host redirects. Signed CDN queries are allowed during transport, while configured signed URLs remain rejected.
- Final response URLs are checked before any bytes are read. The existing SHA-256 pin and 64 MiB final-body limit remain unchanged. Thus redirect permission does not substitute for artifact identity verification.
- Downloading uses an unpredictable private temporary file in the destination directory. Exceptions from transport, final URL validation, size validation, or checksum validation leave no destination published, and the `finally` block removes the temporary name. Successful publication closes the file before same-filesystem atomic replacement. The positive signed-response helper verifies the real approved bytes and mode 0600.
- CLI failures omit exception text, avoiding disclosure of signed URLs. The new policy job needs neither the approved artifact nor repository configuration, and the aggregate job explicitly requires its success.

## Findings

No blocking correctness or security findings in this scoped change. No critical or important issues identified.

## Publication and race assessment

The temporary file creation, hashing, cleanup, and atomic publication preserve the baseline safeguards. Concurrent readers see the complete verified artifact when replacement succeeds; partial downloads are never published. The initial destination-existence check and subsequent `os.replace` do not provide an atomic no-overwrite guarantee if another process creates the destination during the download. This behavior already exists in the baseline, and the workflow uses an isolated runner-temp path with one provisioning step. It is not a regression or a blocker for the authorized CI use. A shared-directory/multiple-writer use would need a stronger publication contract. This change does not add that use.

## Verification and limits

- Independently ran `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s .github/scripts/tests -p 'test_spmf_download.py' -v`: all 20 tests passed.
- Independently ran `git diff --check`: passed.
- Inspected local standard-library redirect handling and the preserved original-source policy across redirect chains.
- Inspected the actual-approved-byte signed final-response test; parent reports the complete 34-helper suite passes with the approved local JAR. I did not rerun that artifact-dependent suite.
- No network, database, Java application, migration, publishing, or merge action was performed for this review. The parent-reported 302-to-200 HEAD probe and 13 Java tests are external evidence, not checks independently repeated by this reviewer.

Optional future coverage: a mocked final response at a disallowed host would directly guard the final-URL defense-in-depth check; a raised read exception would directly guard midstream cleanup. The current implementation visibly handles both paths, so these are nonblocking test suggestions.

## Assessment

The scoped downloader fix is technically ready for the owner's next authorized preparation/review step. Publishing and merging remain outside this review, and independent human review remains a separate gate.

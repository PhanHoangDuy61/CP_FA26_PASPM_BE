# Independent agent review record

**Current result: approved for review; all important findings corrected and retested.** Human review, CI and merge/deployment authorization remain separate gates.

# Task 1 independent review

Reviewed commit `0e4adbb` against base `3d58a73`, task brief, report and complete diff; inspected actual implementation and unchanged parent-table constraints. No edits to implementation, no credentials/source database access, and no test reruns. Reported 11 passing tests are accepted as execution evidence, not independent execution.

**Spec verdict: request changes. Quality verdict: request changes.** The registered callback, explicit opt-in, atomic archive swap, canonical preservation comparisons and durable phase commits are substantial and appropriate. V1–V10 remain unchanged and no sales are manufactured. Two safety requirements remain unenforced.

## Required findings

### [P1] Freeze ownership on referenced parents as well as archived rows

Location: `src/main/java/vn/edu/fpt/paspm/deployment/legacy/LegacyRecoverySchema.java:63–70` (archive/header/registry guard installation); related archive product FK at lines 24–25.

The guards freeze matrix rows, snapshot headers and routing, but do not guard `product.store_id` or `app_user.store_id`. Both are independent mutable columns in V3/V1; the archive FK references only `product_id`, and the header creator FK references only `user_id`. After successful recovery, an ordinary DML update such as `UPDATE product SET store_id=2 WHERE product_id=1` succeeds for an archived product with no category/modern dependent composite references (the implementation test fixture has exactly that shape). Updating the archived snapshot creator’s Store similarly succeeds, although that does not itself change the frozen snapshot `store_id`; it invalidates the current-creator ownership condition imposed by `LegacyDataset.inspect` rather than moving the snapshot. No privileged DDL, disabled FK checks or DBA capability is needed. The preflight only checks ownership once. Historical Store joins can thereafter omit the product, associate it with another Store, or fail subsequent ownership validation, violating frozen ownership and Store isolation. The report’s limitation describes parent mutations as privileged, which is inaccurate for these updates.

Fix: minimally install a product BEFORE UPDATE guard that rejects `NEW.store_id <> OLD.store_id` when `EXISTS (SELECT 1 FROM utility_matrix_item_legacy_v5 WHERE product_id=OLD.product_id)`. A composite archived product ownership FK would also work but needs an explicit archived Store field. For creators, either guard Store transfers only if immutable creator membership is truly required, or explicitly model the creator’s original membership separately and stop interpreting their current membership as historical ownership. The snapshot’s existing frozen Store header is already sound. Keep unrelated modern parents mutable where appropriate. Add post-recovery tests for ordinary archived-product Store updates on both supported engines. No existing public API for Store transfer was found: `catalog/product/ProductRepository.java:94–96` edits product fields while using Store only as a WHERE scope. This finding concerns the explicitly required database invariant under ordinary DML, not a demonstrated HTTP exploit. Creator transfer is a semantic clarification rather than a separate blocker.

### [P1] Bind the restore proof to execution of the supplied dump

Location: `src/main/java/vn/edu/fpt/paspm/deployment/legacy/LegacyBackupVerifier.java:17–20` and `:27–28`.

`generate` verifies that an already-existing second schema has matching matrix/header data, then independently hashes whatever file is passed as `dump`. `verify` repeats those independent checks. Nothing proves that the second schema was restored from that file. A preexisting matching copy plus an empty or unrelated file produces a valid private manifest and authorizes the irreversible transition; no file tampering after generation is necessary. Thus the code establishes matching-copy availability and file stability, but not a restorable-backup proof. The real dump/restore performed by the tests exercises a correct caller, not rejection of this unsafe accepted caller state.

Fix: have the verification workflow restore the exact hashed artifact into a newly created isolated schema, verify successful process completion and compare that restored dataset, then bind the resulting execution evidence and target identities into the manifest. Alternatively require a trustworthy restore-execution attestation produced by such a workflow, rather than allowing the same arbitrary input fields to stand in for proof. Keep original/configured database read-only. Add a negative case with a matching restored schema and empty/unrelated SQL file; it must refuse.

## Other assessment

Durable known-state handling is mostly careful: PREPARED/STAGED/RENAMED/COMPLETE are explicit, AFTER_RENAME resumes using the archive, source fingerprints and manifest are checked again, populated live/stage rows and partial V7 objects refuse. Failure during initial journal creation deliberately fails closed; the report accurately discloses its operational limitation. Trigger-installation failures stop migration rather than silently continuing. The tests cover controlled exceptions at phase boundaries, not every OS-crash/DDL boundary, and the report appropriately avoids claiming otherwise.

Optional quality improvement: expand the highly compressed one-line methods and separate journal-state validation, schema metadata validation and registry population. These are safety-sensitive decisions that should be readily auditable. This is not a blocker independent of the correctness findings above.

Reader changes and F06 policy remain Task 2 and were not assessed as missing from this core task.


## Re-review of fixes at `7bf89e1`

**Updated Task 1 spec verdict: approve. Updated quality verdict: approve for the scoped core task. Both original P1 findings are closed.** Reviewed the complete fix diff, actual changed code and appended implementation report. No implementation edits, test reruns, credentials access or source-database access. The reported 17 passing covering tests (zero failures/errors/skips) and three prior red assertions support this assessment; they are implementer execution evidence.

- **Archived Product ownership — closed.** `LegacyRecoverySchema.java:61–63` installs a BEFORE UPDATE guard that rejects a Store change whenever the old product ID is referenced by the archive. The existing product FK prevents ordinary deletion or primary-key replacement. This is the minimal constraint requested; unrelated products remain mutable. MySQL transition tests and both-engine schema tests now assert blocked archived transfers, and schema tests assert allowed unrelated transfers. The creator clarification in `LegacyDataset.java:9–17` is coherent: original creator ID and snapshot Store remain frozen in the immutable header; current user Store membership is no longer incorrectly treated as historical membership. Existing creator-parent existence is still validated, and canonical header fingerprints still contain `created_by` and `store_id`.

- **Artifact-to-restore binding — closed.** `LegacyBackupVerifier.java:24–56` now requires an empty independent schema, hashes the supplied artifact, executes it, checks its hash again, compares the resulting restored dataset and rechecks target stability before creating the v2 manifest. `:60–85` restricts restore-account grants to the exact verification schema and refuses broad/role/grant-option cases. `:88–106` separately checks the actual supplied credentials and explicitly selects the TCP host/port/database. The controlled client runs without alternate login paths, local infile, or interactive client commands; its temporary credential file is private and removed, and output is suppressed/drained. Nonzero exit and timeout refuse. The previous preexisting-matching-schema plus arbitrary-file path now refuses before execution, and the negative artifact cases test empty, unrelated, and cross-schema SQL. The positive recovery path now actually restores through this verifier rather than relying on the test caller’s external restore.

No new critical or merge-blocking regression found in this fix. The workflow retains the documented trust boundary of an owner-controlled private manifest, quiesced migration writers and disposable restricted verification schemas. Unsupported CLI/socket/TLS variants fail rather than establishing a false success; broader platform support is outside this tested core task. Partial failed restore schemas must be discarded and recreated, as documented. Reader/F06 work remains Task 2, and this approval does not claim that work or independent full-suite/CI completion.


# Independent Task 2 review

Verdict: approve the scoped implementation; no important correctness or security findings. Final integration acceptance still requires the parent-owned full regression command to finish successfully.

Reviewed commit 7e8503d against base 7bf89e1, the Task 2 brief, report, source, tests, and available test outputs. This was read-only code review: no Maven, database access, private credential access, or source edits. Core migration was outside the review scope.

## Specification

- UtilitySnapshotRepository lines 49–81 resolve an owned snapshot, validate registry Store/kind, use only two literal allowlisted table names, reject mixed-table storage, and check Product ownership. Modern rows additionally require actual same-Store sales parents. There is no UNION, and page transaction totals remain snapshot-local (lines 98–103), so identical legacy transaction IDs in separate Stores cannot merge.
- Public service matrix reads retain the ownership check and now use the Store-aware count overload. Mining capture uses the same route for counts/pages/ownership and retains the frozen input builder and thresholds. Legacy projection returns NULL expiry evidence without synthesizing it.
- Legacy saveItems fails before the INSERT (lines 32–36). Modern create inserts its registry row in the surrounding transaction; existing service snapshot creation remains transactional. No changes invent sales, renumber legacy IDs, or recalculate persisted frozen fields.
- FrozenEvidenceReader calls assertAssociationEvidence before parsing or constructing transaction evidence. The repository rejects archive routes unconditionally and requires both frozen F04_DATABASE_V1 provenance and, when present, matching registry namespace. A coincident numeric sales ID therefore cannot legitimize legacy association evidence. Missing registry routes require explicit frozen F04 provenance plus same-Store real sales ownership rather than treating absence as authenticity.
- Existing DTOs and repository constructor remain intact. Existing modern total mismatch remains 400; legacy/inconsistent storage is safely rejected. Authentication stays in existing service/access paths.

## Test evidence and limits

LegacyReaderMySqlTest contains the requested four-row/two-Store fixture, shared historical transaction ID, multi-product transaction, six original-field fingerprints, actual migration, actual repository/service reads, and actual MiningService capture. Its two algorithms × two Stores × before/after loops execute eight real SPMF runs and compare exact Dataset records and patterns. Empty-matrix migration verifies six-table preservation with populated catalog and headers.

The pre-migration Dataset is constructed from explicit fixture expectations at lines 53–57, rather than through a pre-V7 repository reader. This is a valid independent expected-result oracle, combined with actual database fingerprints and post-migration reads, but documentation should describe it accurately. Auth/access and worker dispatch are mocked; completed run rows are explicitly seeded for the rejection check. The report correctly states these limits and does not claim legacy HTTP or worker execution.

Available Surefire reports show the two legacy MySQL tests and five routing tests passing, as well as passing modern F03/F04/F05/F06 suites. Because the parent is running the final command, these files alone are not treated as proof of that entire command's successful completion. The report also correctly retracts the invalid claimed behavioral-red run.

## Quality

The change is small and centralized, preserves constructor compatibility, uses parameterized values, and does not introduce dependencies or unrelated refactoring. Route verification deliberately performs repeated ownership/total queries for count/page/mining; these are bounded SQL operations against snapshot keys. Avoid speculative caching because route integrity must remain fail-closed. Dense formatting follows the surrounding repository style; no readability issue warrants blocking this scoped fix.


# Independent whole-branch review

Reviewed code range: `3d58a73..7e8503d` in `/tmp/paspm-legacy-matrix-recovery`.
Verdict: **REQUEST CHANGES — one P2 compatibility finding.** This is a review verdict, not merge/deployment approval.

## Required finding

**[P2] Preserve read access for valid empty V5 snapshots.**

Location: `src/main/java/vn/edu/fpt/paspm/utility/UtilitySnapshotRepository.java:60–64`; related producer: `src/main/java/vn/edu/fpt/paspm/deployment/legacy/LegacyRecoveryCallback.java:24`.

For a V5 database with existing legacy snapshot headers, zero matrix rows and valid zero frozen totals, the callback deliberately allows the normal upgrade without creating registry entries. V11 creates an empty registry. Both `page` and `countItems` now call `route`, which rejects every missing-registry snapshot unless its frozen source is `F04_DATABASE_V1`. Consequently those preserved V5 snapshot headers can still be fetched, but their F03 matrix endpoint now fails with 409 rather than returning the correct empty matrix. The new `emptyV5MatrixPreservesExistingCatalogAndSnapshotWithoutOptIn` test creates exactly this valid state and checks preservation, but never exercises its post-upgrade F03 matrix read. This also makes empty-header behavior depend on whether some other snapshot happened to contain rows and triggered full archive registration.

Provide a narrowly proven read-only route for a zero-total snapshot with no rows in either matrix table, or register these headers explicitly during the safe upgrade path. Do not relax provenance checks for populated snapshots, allow new writes into unknown historical snapshots, or grant F06 association evidence. Extend the empty-V5 test to check same-Store F03 service/page/count reads and cross-Store refusal after migration. Finding is based on deterministic source control flow; I did not run a reproduction test per review constraints.

## Reviewed safeguards and compatibility

- Callback is a Spring bean, targets before-each V7 only, checks exact successful V1–V6 history and partial V7 objects, and leaves Flyway validation enabled. The integration test exercises actual Boot callback discovery and refusal before V7 DDL.
- Backup verification performs the supplied dump restore into a separate empty schema, verifies schema-scoped credentials and target identity, rechecks exact dump hash and matrix/header canonical fingerprints, and binds a private manifest to both targets. It does not claim to fully verify every business table in an operational backup.
- Journal checkpoints and atomic two-table rename avoid row deletion/fabricated sales parents; mismatched state fails closed. Tests simulate controlled exceptions at journal boundaries, not a real OS crash or power failure.
- Archive DML, archived header/routing mutations and archived Product Store reassignment are guarded. Ordinary runtime reads remain available. Privileged administrative DDL protection remains an operational grant requirement.
- Populated legacy pages/counts remain snapshot/Store scoped, preserve source IDs and frozen values, and return absent modern expiry evidence honestly. Mining capture uses that reader, and F06 refuses legacy association evidence even if modern sales IDs alias legacy numbers.
- The new V11 and application changes do not modify applied V1–V10 migration files. No Category migration, automatic merge or deployment is present in the reviewed range.

## Evidence limits

Supplied sanitized reports record feature 252 passing tests and detached Product Pilot compatibility 297 passing tests, with no skips/failures/errors. I inspected their summaries and the relevant integration-test source; I did not rerun tests or access source databases or credentials. Reader evidence records four synthetic legacy rows across two Stores and eight real SPMF engine executions comparing complete datasets and exact pattern maps. The legacy service test mocks authentication/access and the worker, persists F05 capture, and manually marks runs completed to check F06 refusal; it is not a full legacy HTTP-to-worker end-to-end run. Original-database immutability is not independently remeasured by this review.

Final documentation consistency review remains pending the parent's final documentation package. No stale draft wording is included as a finding here.

## Re-review of P2 correction — commit 2553e54

**Updated production-code verdict: APPROVE FOR REVIEW.** This supersedes the request-changes verdict above; the P2 finding is resolved. This is not merge/deployment approval. Final full-suite rerun and final documentation consistency review remain pending.

Reviewed `/tmp/paspm-recovery-work/final-reader-fix-package.txt` against the original finding. Unknown, unregistered snapshots now receive an in-memory legacy/read-only route only when both physical matrix tables contain no rows and the frozen total is exactly zero. No registry entry is inserted by reading. Existing `legacy` guards reject both `saveItems` and association evidence, including empty save calls. Populated unknown provenance and nonzero missing matrices still fail closed. Store ownership is checked before this fallback, preserving existing cross-Store refusal.

The new MySQL assertions exercise actual upgraded empty V5 snapshots through repository count/page and the F03 service, plus write/F06 refusals. The routing test also checks that a nonzero header invalidates the empty fallback; existing tests retain unknown populated/archive and modern ownership coverage. Parent supplied red-to-green evidence (two expected empty-route 409 failures before correction, then 35 covering tests passing with no skips); I did not rerun tests. No new important finding in this fix.

A test-only actual MySQL failed-V7 recovery acceptance extension and final whole-suite results are to be assessed separately when supplied. The earlier limits concerning controlled exceptions, mocked authentication/worker in legacy service tests, original-database proof, and operational grants remain applicable.

## Final source, test and documentation review — 9c19f2a

**Final verdict: APPROVE FOR REVIEW; no outstanding important findings. READY_FOR_MERGE is not granted.** The earlier P2 is resolved by 2553e54. This final verdict supersedes the pending source/test/documentation portions above; it does not approve production deployment, source mutation, Product Pilot merge or Category migration.

Reviewed the test-only 9c19f2a diff and the five final design/compatibility/readiness/test documents, together with TEST_RESULTS.json, PRODUCT_PILOT_COMPATIBILITY.json, REPLAY_RESULTS.json, SOURCE_PRESERVATION.json and MIGRATION_MANIFEST.json. The added test intentionally reproduces actual SQL error 1452 on the original V7 FK using an unregistered Flyway instance, confirms partial objects and failed V7 history remain, verifies supported retry refusal without modification, and then successfully migrates a separately restored clean target through the current production callback. It rechecks the failed copy afterward. The clean target uses the migration identity; exact artifact restoration and verification remain schema restricted. No repair, original-copy reset or fabricated sales is used.

Final supplied inventories are internally consistent: feature 254 PASS and detached Product Pilot plus scoped recovery patch 299 PASS, both zero failures/errors/skips. The parent reports both Maven verify processes exited zero. The tested feature commit is 9c19f2ab8e40d21a68378fde72bb2311ef349884. Replay evidence has equal six-table before/after fingerprints, exact dataset records and complete pattern maps for two Stores and eight real engine executions. Source-preservation JSON has equal before/after recorded metadata/history/counts/configuration/migration hashes, read-only connection flags and SELECT/ROLLBACK-only actions; its source is explicitly empty V5, and populated acceptance fixtures are explicitly synthetic. These are supplied evidence reviewed for consistency, not independent database probes or test reruns by this reviewer.

Documentation correctly distinguishes registered positive migration acceptance from the still-failing unsupported plain Flyway path; controlled exceptions from exhaustive OS-crash testing; real engine execution/F05 capture from mocked legacy worker/authentication and manually completed F06 refusal checks; modern HTTP/JPA regressions from a full legacy HTTP-to-worker workflow; and resolved engineering blockers from human review, remote CI and deployment approval. It also documents the empty-snapshot fallback, private manifests, restricted restore grants, quiesced writers, immutable Java migration release discipline, partial-DDL clean-copy recovery and administrative DDL limitations.

Cleanup and PR bookkeeping were explicitly PENDING in the reviewed manifest and remain the parent's operational follow-up. Updating those fields to actual observed outcomes does not change this code review verdict. No production code changes were introduced in the last test addition. I performed only read-only source/evidence inspection and this review-report update; no tests, credentials or databases were accessed.

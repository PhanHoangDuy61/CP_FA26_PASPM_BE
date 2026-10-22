# V5→V7 historical Utility Matrix recovery — implemented B+C design

Updated: 2026-10-23T00:34:04.776583+07:00. Branch `Khoi/Fear/fix-legacy-utility-matrix-migration`, based on `dev` `3d58a73`. Tested code commit `9c19f2ab8e40d21a68378fde72bb2311ef349884`.

**Status: IMPLEMENTED / TESTED / VERIFIED in isolation; READY_FOR_REVIEW. READY_FOR_MERGE: NO.** Production deployment, source mutation, real-data backfill, Product Pilot merge and Category migration remain outside authorization.

## Root cause and identity

V5 `utility_matrix_item.transaction_id` is a positive prepared-source ID scoped to a snapshot. The unique key is `(snapshot_id,transaction_id,product_id)`; different Stores can reuse the same transaction number. V5 has no sales table or sales FK. Historical fixture providers used Store-local IDs; no authentic retail source export/mapping was located in the inspected workspace.

V7 creates F04 `sales_transaction` and `transaction_item`, then adds `fk_utility_matrix_transaction` to the old matrix. Newly-created sales tables have no matching historical parents, producing MySQL 1452 / SQLState23000. Earlier successful DDL persists. Numeric equality would not authenticate Store/source identity even if a coincident sale exists. F04 identity is a global generated sale key backed by `(store_id,source_system,source_order_id)`, occurrence/status/eligibility, prices/costs and content hashes. Frozen utility cannot reconstruct those facts.

Affected tables: `utility_snapshot`, `utility_matrix_item`, `sales_transaction`, `transaction_item`; F04 import/staging, F05 requests/input and F06 readers depend on them. Existing V1–V10 scripts are byte-identical. The original configured source remains V5 and unchanged.

## Options and selected approach

| Criterion | A: authentic mapping/backfill | B: immutable archive/live split | C: preflight/refusal gate |
|---|---|---|---|
| Historical values | Preserve originals; mapping separate | Original physical rows renamed, not rewritten | Refuse inconsistent evidence/state |
| FK integrity | Require proven Store/source relationship | Archive snapshot/Product FKs; live sales FK plus Store guards | Validate prerequisites; never invent parents |
| F03 immutability | Original IDs/JSON/hashes stay frozen | Archive/header/routing DML protected | Fingerprints/checkpoints before continuation |
| F04 semantics | Only genuine validated orders | No synthetic parents during recovery | Partial F04 DDL refused |
| F05 reproducibility | Mapping cannot replace frozen IDs | Route frozen rows; exact datasets/patterns preserved | Stop on ownership/storage inconsistencies |
| Upgraded installations | Additive, evidence-dependent | V11 is additive; no V7 replay | Unknown populated provenance fails closed on reads |
| Rollback / partial DDL | Requires authentic reconciliation | Restore/cutover from verified backup, durable journal | Never blanket repair/clean |
| Effort/risk | High; wrong mapping corrupts meaning | Implemented; reader cutover and DBA operations matter | Implemented; maintenance discipline required |
| Current disposition | NOT_IMPLEMENTED; no genuine records | SELECTED with C | SELECTED with B |

B+C preserves historical evidence and current F04 semantics. A requires separate data-owner approval and authentic source records. No source records or legacy mappings are fabricated.

## Final storage and constraints

- `utility_snapshot`: existing immutable headers, original Store/actor IDs, dates, JSON, source/matrix hashes, totals and timestamps remain unchanged. Current creator Store membership is not retroactively interpreted as original membership.
- `utility_matrix_item_legacy_v5`: original V5 table renamed atomically, retaining all original item IDs and V5 fields/types/keys. Store is derived from the immutable snapshot; no invented sales FK. Legacy reference key is `(snapshot_id,transaction_id)`.
- `utility_matrix_item`: empty staged replacement with original snapshot/Product FKs restored explicitly, identity range above historical max, original V7 sale FK and V8 optional expiry evidence. New F04 snapshots write here.
- `utility_snapshot_storage(snapshot_id,store_id,storage_kind,source_namespace)`: fixed `LEGACY_V5_ARCHIVE`/`F04_LIVE` route, snapshot/Store integrity and composite FK after V9. Legacy source is `V5:<frozen source>` or `UNVERIFIED:SNAPSHOT:<id>`; it does not establish retail authenticity.
- `utility_legacy_recovery_journal`: target identity, original hashes/counts, manifest SHA and durable PREPARED/STAGED/RENAMED/COMPLETE phases.
- Database guards reject archive INSERT/UPDATE/DELETE, archived header/routing UPDATE/DELETE, archived Product Store transfer and inconsistent registry ownership. Modern matrix INSERT/UPDATE must agree with snapshot/Product/actual sale Store. Privileged DDL/trigger removal is not covered by DML guards; runtime grants must exclude it.

## Integrated ordering and version guards

1. Quiesce writers; positively identify target/history/checksums and approved backup. Verification tool restores the exact hashed backup into a NEW EMPTY, separately restricted schema, compares canonical source/restored data, then writes an owner-only v2 manifest. Existing matching data plus an unrelated file is rejected.
2. Spring Boot discovers `LegacyRecoveryConfiguration`; its callback executes `beforeEachMigrate` only for V7, requiring exact successful history1–6 and normal Flyway validation. Fresh/valid empty matrices follow the supported original path without populated-recovery opt-in.
3. Populated recovery requires explicit enablement, target-bound manifest and retained restored verification database. Before DDL, reject partial F04 objects, wrong ownership, orphan/duplicate identities, invalid totals/hashes, unknown archive/stage or inconsistent journal.
4. Record/commit PREPARED, create empty stage, restore FKs, verify columns/CHECKs/indexes/FKs and identity range. Record STAGED; atomically rename original→archive and stage→live. Compare canonical archive/header fingerprints; install guards/routing and commit completion.
5. Continue unmodified V7–V10, then additive Java V11. V11 normalizes/validates archive/routing structures, composite registry ownership and modern Store guards on fresh/recovered/upgraded systems. It does not replay old SQL or silently classify unknown old populated sources as F04.
6. Compatible readers must be deployed before traffic resumes. Plain external Flyway builders/CLI do not discover Spring beans automatically; explicitly register the callback or use the application integration. Never bypass validation/out-of-order rules.

V11 is a Java migration with Flyway's default null Java checksum. Its implementation/helper must remain immutable after release; subsequent schema changes require a new version. The evidence records exact tested source and original SQL hashes.

## Application compatibility

Store-scoped routing selects one allowlisted table, never a UNION. Legacy pages retain original transaction/Product ordering, sums and NULL projection for absent V8 expiry evidence. Counts/pages reject mixed storage and invalid ownership/totals. `saveItems` refuses historical routes; new snapshot/header+live route registration is transactional.

A valid unregistered historical EMPTY snapshot remains readable only after proving BOTH matrix tables empty and frozen total zero. This route is read-only, is not labeled F04, and cannot authorize F06 or later item writes. Unknown populated unregistered sources remain a diagnosed semantic-audit case. Demonstrable pre-registry F04 snapshots retain their live read path.

F05 validates ownership through the route-aware repository and captures unchanged frozen rows. Existing mining requests/input/run/pattern records are not regenerated. F06 refuses legacy/unverified association membership with409 before deriving support/confidence; coincident numeric sales IDs do not override that gate. Current F04-backed behavior remains supported.

## Recovery and approvals

Known checkpoints resume only when journal, target, backup, data and schema agree. Failure during initial journal creation without its durable record refuses rather than guessing. Original V7 SQL failure leaves failed history/partial objects: keep that copy, restore an approved clean backup into a DIFFERENT target, generate its new target-bound manifest, and rerun supported B+C there. No source/MySQL repair or destructive reset is performed. Controlled H2 checksum-repair regression remains an in-memory test only.

After V7/new traffic, neither reverse renaming nor an old application binary is a safe automatic rollback. Reconcile post-backup writes and use a reviewed restore/PITR/cutover or forward repair. Production scale, metadata-lock/disk capacity, TLS/socket/custom backup routines and every OS-kill/DDL boundary are NOT_RUN. Tests simulate controlled callback exceptions and an actual failed MySQL V7 chain.

Team Lead approval covers this implementation/testing/branch work. Production deployment/source mutation require separate DBA/operations/data-owner approval; genuine backfill A and any merge require separate review/authorization. No Category migration began.

## Verification

Feature Maven verify: **254 PASS,0FAIL,0ERROR,0SKIPPED**. Detached Product Pilot plus scoped patch: **299 PASS,0FAIL,0ERROR,0SKIPPED**. Real MySQL8.4.11, Java17 backend and approved SPMF2.67a/Java25 child executed. All populated data is simulated; original source before/after metadata/history/counts/configuration/SQL hashes match.

See [test results](../testing/V5_V10_POPULATED_MATRIX_RECOVERY_TEST_PLAN.md), [operations and compatibility](LEGACY_UTILITY_MATRIX_COMPATIBILITY.md), [source proof](../testing/evidence/historical_matrix_recovery/implementation/SOURCE_PRESERVATION.json) and [test inventory](../testing/evidence/historical_matrix_recovery/implementation/TEST_RESULTS.json). Earlier13 experimental assertions remain historical evidence in POC_RESULTS.json, not acceptance substitutes.

Primary engine/tool references: [MySQL implicit DDL commits](https://dev.mysql.com/doc/refman/8.4/en/implicit-commit.html), [CREATE TABLE LIKE](https://dev.mysql.com/doc/refman/8.4/en/create-table-like.html), [Flyway callback events](https://documentation.red-gate.com/flyway/reference/callback-events), [repair scope](https://documentation.red-gate.com/flyway/reference/commands/repair).

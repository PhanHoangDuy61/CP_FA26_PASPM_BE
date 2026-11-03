# Fresh read-only review — Recovery PR #1

Reviewed range: `3d58a73543db3b5923289f901f9ffecba6228d23..3a9c66632cd52f2a0a6720977aff10828d5176e6`.
Checkout inspected: `/tmp/paspm-legacy-matrix-recovery`; HEAD matches the requested head. The checkout was clean when inspected and was not modified. The externally reported merge SHA was supplied by the parent; this code review does not independently establish GitHub merge, approval, CI, or branch-protection status.

Method: read changed production source and tests, original V5–V10 SQL, and the recovery design/compatibility/test documentation. Applied the `code-review-and-quality` skill and `requesting-code-review/code-reviewer.md` review instructions. No database connections, private credentials, original database reads, external review actions, builds, or tests were performed. `git diff --check` passed for the reviewed range.

## Strengths and verified source behavior

- The callback is exposed as a Spring configuration bean (`src/main/java/vn/edu/fpt/paspm/deployment/legacy/LegacyRecoveryConfiguration.java:8–10`) and selects BEFORE_EACH_MIGRATE for version 7 (`LegacyRecoveryCallback.java:13`). Its clean-history and partial-V7 checks precede recovery DDL (`:18–25`); the Boot discovery test exercises missing-opt-in refusal before V7 objects are created (`src/test/java/vn/edu/fpt/paspm/deployment/LegacyRecoveryMySqlTest.java:21`). Normal Flyway validation is not disabled. Plain external Flyway builders require explicit callback registration, which the documentation discloses.
- Fresh/empty matrices follow the original V7 path after integrity inspection (`LegacyRecoveryCallback.java:24`). Already-applied migrations are not replayed; V11 adds archive/registry/integrity support. The reviewed diff does not change V1–V10 SQL.
- Original matrix rows are moved through a multi-table atomic rename rather than rewritten or given sales parents (`LegacyRecoveryCallback.java:41–52`). The stage restores snapshot/Product FKs and compares column, index, CHECK, and FK shape (`:43–46`, `:71–83`). No recovery code inserts fabricated sales transactions.
- Canonical fingerprints include the original row/header identity and frozen fields (`LegacyDataset.java:13–22`). Snapshot-local transaction grouping is retained by the routed page query, which does not UNION the archive with live rows (`src/main/java/vn/edu/fpt/paspm/utility/UtilitySnapshotRepository.java:100–109`). Store ownership, mixed storage and totals are checked before serving/capturing rows (`:49–85`).
- The supplied dump is actually executed into an empty separate schema, with exact-schema-only grants and separately checked CLI credentials (`LegacyBackupVerifier.java:24–42`, `:60–106`). The private manifest binds target/restore server UUID plus schema, file SHA and canonical dataset; migration rechecks those bindings (`:138–162`). Credentials use a private temporary client-options file, client output is suppressed, alternate login paths/local infile are disabled, and the file is deleted (`:98–130`).
- Journal binding and allowed phases are validated on restart; phase commits are explicit and durable; unknown/mixed states and partial V7 objects refuse (`LegacyRecoveryCallback.java:18–23`, `:30–50`, `:65–70`). A crash between journal-table creation and its initial durable record refuses rather than inferring success, as documented. No production Flyway repair path was added; the remaining repair invocation is the preexisting controlled H2 regression test.
- Archive DML, archived headers/routes and archived Product Store transfers are guarded (`LegacyRecoverySchema.java:57–73`); modern rows are checked against actual snapshot/Product/sales Store ownership (`:75–84`). Administrative DDL/trigger removal and quiesced writers remain explicitly documented operational prerequisites.
- F05 captures routed frozen rows and preserves its existing builder/algorithm path (`src/main/java/vn/edu/fpt/paspm/mining/MiningService.java:41–44`). F06 asserts provenance before reading association evidence (`src/main/java/vn/edu/fpt/paspm/recommendation/FrozenEvidenceReader.java:30`; `UtilitySnapshotRepository.java:90–96`). Coincident numeric sales IDs cannot turn a legacy route into supported association evidence.
- Focused tests cover controlled interruption checkpoints, actual failed-V7 refusal/clean-copy recovery, artifact/restore tampering, invalid ownership/totals, staged metadata tampering, duplicate Store-local transaction IDs, immutable guards, route isolation, empty historical reads, frozen mining datasets and legacy F06 refusal. I inspected these tests; their runtime result is left to the parent's verification run.

## Verified defects

None found in this reviewed range.

| Severity | Verified findings |
|---|---:|
| CRITICAL | 0 |
| HIGH | 0 |
| MEDIUM | 0 |
| LOW | 0 |

## Assessment

No source-level blocker was established in this review. This is an agent code-review result only: it is not an eligible independent human GitHub approval, a CI pass, a migration acceptance run, or permission to advance Product/Category work. Real GitHub review/CI gates and the parent's actual verification results must be assessed separately. The documented simulated-data and operational restore limitations remain; REAL_DATA_BLOCKED is unchanged.

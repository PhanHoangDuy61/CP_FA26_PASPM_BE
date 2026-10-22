# Preserve historical Utility Matrix data during V5 upgrades

Populated V5 matrices contain source-local transaction IDs, so V7 previously fails when it treats them as F04 sales keys. A registered pre-V7 callback now verifies an exact backup restore and archives original rows before V7; additive V11 installs archival/routing support on fresh and already upgraded databases.

Store-scoped F03/F05 readers preserve historical IDs, utility values and frozen metadata. F06 rejects unverifiable historical association evidence. No V1–V10 SQL files are changed and no historical sales are fabricated.

Validation: Java 17 Maven verify passed 254/254 tests on this independent dev-based branch and 299/299 on a detached Product Pilot compatibility copy, with zero failures/errors/skips. Isolated MySQL 8.4 tests cover fresh, empty/populated V5, duplicate IDs across Stores, actual failed-V7 refusal and separate clean-copy recovery. Eight real HUI-Miner/EFIM executions preserve exact datasets and pattern maps. Six-table fingerprints match; the configured original V5 database was checked read-only and remains unchanged.

All populated fixtures are synthetic. Controlled interruption checkpoints are tested; exhaustive OS-crash/production-scale recovery and genuine historical backfill are not. Legacy service tests mock authentication/worker dispatch and do not claim full legacy HTTP-to-worker execution. F06 historical support/confidence requires authentic membership evidence.

Independent review found no remaining important findings. Human review and remote CI are still required. This PR does not authorize deployment, Product Pilot merge or Category migration.

Review the recovery design, compatibility runbook, populated-matrix test report and sanitized evidence under docs/architecture and docs/testing/evidence/historical_matrix_recovery/implementation.

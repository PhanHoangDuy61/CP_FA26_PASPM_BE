# Flyway V7 MySQL recovery and controlled checksum reconciliation

## Decision and exact patch

Flyway Community 11.7.2, MySQL 8.4.11. The fresh-schema reproduction failed with SQLState 42000 / error 1064 at CREATE TABLE import_staging_row, specifically row_number INT NOT NULL. MySQL had applied V1–V6 and V7's preceding DDL; MySQL DDL is not transactional.

Option A was explicitly approved by the user on 2026-10-20 and implemented: enclose row_number in backticks at five locations: column definition, uq_import_staging_batch_row, ck_import_staging_row_number, and both staging indexes. The column name, type, constraints, indexes and data remain semantically unchanged. The same identifier is quoted in F04 JDBC INSERT/UPDATE/SELECT/ORDER BY SQL; ResultSet column label remains row_number.

This changes V7's Flyway checksum from -1762009830 to -1490036030 with Flyway 11.7.2. Corrected code must NOT be rolled out blindly to a database with the old checksum. No automatic repair/baseline is added to startup. Approval covers this repository patch/procedure, not repair of any deployed database.

## Alternatives assessed

- V10 cannot fix SQL which prevents V7 from executing.
- A B9 baseline SQL script is supported by Flyway: fresh schemas execute the latest baseline; existing migration histories ignore it. It would duplicate V1–V9 schema and seeds, require equivalence tests, and bypass the requested V1→V9 clean path. This is a larger change than quoting one identifier, so it is not selected.
- baselineOnMigrate remains disabled. It must not assert that schema objects exist when they do not.
- Controlled historical correction is selected together with explicit reconciliation on existing compatible schemas.

Official references: [repair](https://documentation.red-gate.com/flyway/reference/commands/repair), [baseline migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/baseline-migrations). Repair aligns history metadata; it does not repair schema objects or undo partial MySQL DDL.

## State A — Fresh database

Deploy corrected SQL and start normally. V1–V9 must migrate and validate. No baseline or repair is needed. Tests create dedicated paspm_p0_test_* schemas and never reuse/delete existing schemas.

## State B1 — V7 already applied with old checksum

1. Stop writes/mining and schedule maintenance.
2. Back up schema/data including flyway_schema_history. Record database identity, server version, application commit, original migrations/checksums and row counts.
3. Use the same Flyway 11.7.2 and complete migration locations as the application. Run info/validate first and save results. Verify ONLY V7 has the expected checksum difference.
4. Inspect SHOW CREATE TABLE for F04/F05 tables, constraints and indexes. Confirm row_number is INT NOT NULL and all prior objects match a corrected reference schema. Compare row counts/business keys. Any unexpected mismatch stops the procedure.
5. Rehearse on an isolated copy and compare data. Obtain explicit approval for that deployed environment. Only then may an operator run Flyway repair with the same complete migration locations. Do not manually UPDATE history.
6. Run validate/migrate. Confirm old migrations were not replayed, F04 data stayed intact, F05 works. Retain evidence.

Illustrative CLI operations AFTER approval, with connection/secrets externally supplied and a matching Flyway CLI:
    flyway info
    flyway validate
    flyway repair
    flyway validate
    flyway migrate

The repo does not provision a Flyway CLI or startup repair hook. Review actual CLI configuration before running it.

Rollback: before later migrations, stop writes and restore the pre-change backup plus the matching original application/migrations. Reverting code alone after metadata repair reintroduces mismatch. After later migrations/new writes, use a reviewed forward repair or coordinated restore; do not overwrite new data blindly.

## State B2 — V7 failed part-way

A failed history row and some V7 tables may exist. Repair alone removes the failed history row while retaining DDL, so a rerun can fail on existing objects.

- Do not auto-repair, drop objects, reset a shared database or delete history.
- For disposable test data, create a different empty database and apply corrected migrations.
- For valuable/deployed data: stop writes, back up, inventory every V7 object/data row, compare to a fresh reference, prepare an environment-specific reconciliation or restore plan, rehearse on a copy, and obtain approval before execution.
- A checksum-alignment test on a complete schema does not authorize partial-schema cleanup.

## Verification

Tests cover fresh MySQL, real F04/F05 operations, isolated MySQL V7→V9 upgrade with preserved staging data, and genuine original-V7 H2 history which rejects the new checksum then passes controlled repair with data intact. Historical repair is restricted to a named in-memory H2 test connection. No deployed MySQL history is repaired.

See docs/audits/F05_P0_DEPLOYMENT_FIX_REPORT.md for executed results/checksum values.

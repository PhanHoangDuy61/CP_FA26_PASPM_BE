# Hybrid persistence decisions — 2026-10-25

- Reuse Product's existing scoped entity/repository/transactional bridge. JPA owns Product metadata writes; Product catalog/projections and Inventory remain JDBC. F03 S/M fields are unmapped, avoiding dirty-write overwrite.
- One Boot-managed JpaTransactionManager and datasource serves JPA/JDBC, verified with actual shared connections, commit/rollback/isolation and F04 independent inner commit. No second manager is introduced.
- Store ownership columns are non-updatable; write APIs are explicitly Store-scoped. No entity relationship/cascade is introduced. Existing composite Store foreign keys remain Flyway-owned.
- Flush before JDBC readback; refresh managed metadata after native writes. Column-only status DML flushes and clears managed context. Dependent Category code must not rely on held entities surviving that clear.
- Hibernate uses validate; OSIV disabled; no schema migration is needed for metadata mapping. Original V1–V11/recovery archive semantics stay unchanged.
- Development/review readiness differs from merge readiness. Owner permits stacked Category development after local Product acceptance; remote artifact/reviewer gates cannot be fabricated or bypassed.

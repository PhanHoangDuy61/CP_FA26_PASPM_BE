<!-- docs-reorganization:notice -->
> **NEEDS_REVIEW — documentation review 2026-10-26.** Technical Hybrid choices remain useful; dated branch-dependency clauses are not current merge status. See [current navigation](README.md). Original results and business-rule text below are retained; this notice is not a new execution or approval.
<!-- /docs-reorganization:notice -->

# Hybrid persistence decisions — 2026-10-25

- Reuse Product's existing scoped entity/repository/transactional bridge. JPA owns Product metadata writes; Product catalog/projections and Inventory remain JDBC. F03 S/M fields are unmapped, avoiding dirty-write overwrite.
- One Boot-managed JpaTransactionManager and datasource serves JPA/JDBC, verified with actual shared connections, commit/rollback/isolation and F04 independent inner commit. No second manager is introduced.
- Store ownership columns are non-updatable; write APIs are explicitly Store-scoped. No entity relationship/cascade is introduced. Existing composite Store foreign keys remain Flyway-owned.
- Flush before JDBC readback; refresh managed metadata after native writes. Column-only status DML flushes and clears managed context. Dependent Category code must not rely on held entities surviving that clear.
- Hibernate uses validate; OSIV disabled; no schema migration is needed for metadata mapping. Original V1–V11/recovery archive semantics stay unchanged.
- Development/review readiness differs from merge readiness. Owner permits stacked Category development after local Product acceptance; remote artifact/reviewer gates cannot be fabricated or bypassed.

- Category now uses JPA for existing simple CRUD/read DTOs on a separate Product-dependent branch. Scalar projections preserve native-write visibility; no bidirectional mapping or cascade is introduced. Category status/name changes preserve referenced Products. Duplicate names remain allowed under unchanged V3 schema. Product tests explicitly select ProductEntity when both entities are mapped.
- Public Category writes still validate in the existing service/DTO layer. Unknown client Store fields are rejected400 by existing JSON policy; JPA operations additionally require authenticated Store predicates.

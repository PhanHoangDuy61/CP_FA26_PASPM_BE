# Architecture and Persistence

## Current source baseline

At dev `6601f62`, Product metadata writes and Category CRUD use scoped JPA entities/repositories/transactional bridges. Product catalog projections, Inventory and F03–F06 analytical/import/mining paths retain JDBC. Boot supplies one `JpaTransactionManager` for the shared datasource. Hibernate uses `validate`, OSIV is disabled, and Store ownership is not SQL-updatable. This is selective Hybrid persistence, not a full JDBC rewrite.

Source: [configuration](../../src/main/java/vn/edu/fpt/paspm/common/persistence/PersistenceConfiguration.java), [Product bridge](../../src/main/java/vn/edu/fpt/paspm/catalog/product/persistence/ProductMetadataPersistence.java), [Category bridge](../../src/main/java/vn/edu/fpt/paspm/catalog/category/persistence/CategoryPersistence.java), [settings](../../src/main/resources/persistence.properties).

## Architecture references

- [Hybrid decisions](HYBRID_PERSISTENCE_DECISIONS.md): useful implementation choices; old branch-progression wording is dated.
- [Migration plan](JDBC_TO_JPA_MIGRATION_PLAN.md): NEEDS_REVIEW where it describes unmerged/not-started work.
- [Product integration report](PRODUCT_JPA_INTEGRATION_REPORT.md), [Category report](CATEGORY_JPA_IMPLEMENTATION_REPORT.md): historical local verification, not current hosted acceptance.
- [Core foundation report](MIG_JPA_CORE_00_IMPLEMENTATION_REPORT.md), [Product Pilot report](MIG_JPA_F02_01_IMPLEMENTATION_REPORT.md): earlier implementation evidence.
- [Legacy utility compatibility](LEGACY_UTILITY_MATRIX_COMPATIBILITY.md), [recovery design](V5_V7_HISTORICAL_MATRIX_RECOVERY_DESIGN.md): historical evidence/current routing boundaries.

## Database and mining

Flyway [SQL migrations](../../src/main/resources/db/migration) and [Java V11 migration](../../src/main/java/db/migration/V11__historical_utility_matrix_storage.java) govern the physical schema. [Diagrams](../diagrams/README.md) preserve business models; the newest editable diagram lacks V11 archive/routing and is not a complete physical-schema export.

SPMF is an external pinned binary with a separately configured Java 25 child process; Backend remains Java 17. [Runtime setup](../setup/F05_SPMF_RUNTIME_SETUP.md), [mining contract](../api/F05_MINING_API.md), [recommendation contract](../api/F06_RECOMMENDATION_API.md). No public binary hosting is needed for this documentation task.

[Archived PR/delivery packages](../archive/README.md) retain original decisions and branch identities. They do not define current merge status. [Testing index](../testing/README.md) separates local/CI/real-data evidence.

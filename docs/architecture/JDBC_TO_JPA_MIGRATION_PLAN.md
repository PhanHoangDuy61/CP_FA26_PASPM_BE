# Controlled Hybrid persistence progression — owner policy 2026-10-25

Selective JPA metadata CRUD with JDBC retained for catalog/inventory/analytics/F03–F06 is the approved architecture. The latest owner instruction authorizes ongoing implementation and isolated local verification while SPMF hosting and human review are separately resolved. Merge gates stay independent.

1. Existing Product Pilot12d348e is merged into a new feature branch from dev783725c, preserving Recovery1/V11. Full local302 tests pass; prepare Product PR into dev, no merge.
2. Category development may start now from the verified Product integration branch. Create `Khoi/feat/category-jpa-crud-pilot`; keep existing API semantics, Store scope, no cascade/delete invention and no schema change unless necessary. Test actual JPA/JDBC behavior and full regression.
3. Category PR targets Product branch until Product is accepted/merged; disclose dependency. Retarget only when separately authorized.
4. Remote full CI, approved artifact provisioning, independent eligible human review and merge authorization remain required before any merge. Original V5 source remains untouched; all test data is synthetic; REAL_DATA_BLOCKED persists.

See [Product report](PRODUCT_JPA_INTEGRATION_REPORT.md). No Inventory/analytical/F03–F06 rewrite or new Stock Movement scope is authorized.

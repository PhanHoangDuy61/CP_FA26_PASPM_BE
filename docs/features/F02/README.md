# F02 — Product, Category and Inventory

## Current backend update — 2026-11-24 (branch `Khoi/feat/f02-expiry-pushday-product-delete`, PR to `dev` pending)

Rules: [DEC-20261124-01](../../decisions/2026-11-24/DEC-20261124-01-batch-expiry-boundary-effective-status.md), [DEC-20261124-02](../../decisions/2026-11-24/DEC-20261124-02-push-day-missing-warning.md), [DEC-20261124-03](../../decisions/2026-11-24/DEC-20261124-03-product-soft-delete.md). Migration V28. Contract: [F02 API](../../api/F02_PRODUCT_INVENTORY_API.md); frontend notes: [F02 handoff](../../frontend_handoff/F02_HANDOFF.md).

| Item | Behaviour | Evidence |
|---|---|---|
| Expiry boundary | A batch is expired only when today (`Asia/Ho_Chi_Minh`) is **after** `expiryDate`; on the expiry date it is still sellable. Computed on read, never stored | `catalog/inventory/BatchExpiryRules.java`; `BatchExpiryRulesTest` |
| `effectiveStatus`, `daysToExpiry` | `BatchView` adds `effectiveStatus` (`ACTIVE|BLOCKED|EXPIRED`, EXPIRED wins over BLOCKED) and `daysToExpiry` (0 on the expiry date). The stored `status` stays `ACTIVE|BLOCKED` | `catalog/CatalogContracts.java` (`BatchView.derive`); `CatalogIntegrationTest` |
| Summary buckets | expired = any stored status with `expiry < today`; blocked = BLOCKED and not expired; sellable = ACTIVE and not expired; `batchesMissingPushDay` added | `catalog/inventory/InventorySummaryRepository.java` |
| Push day | No default and no auto-fill (the BE never had one); `warnings: ["MISSING_PUSH_DAY"]` when the product requires expiry, `pushDay` is null and the batch is not expired | `catalog/inventory/BatchExpiryRules.java` (`warnings`) |
| F03 / F06 | Same boundary in `ExpiryRiskCalculator`, `PushDayRule`, `ProductEligibilityService`; frozen snapshots untouched; `scoringVersion` **not** bumped (PO decision; effective date and "no mixing" rule in DEC-20261124-01 §6) | `ExpiryBoundaryF03F06Test` |
| Product soft delete | `DELETE /api/products/{id}`; 409 `PRODUCT_HAS_SELLABLE_STOCK` / `PRODUCT_IN_LIVE_CAMPAIGN`; excluded before pagination; no restore; SKU never reused | `catalog/product/ProductService.java` (`delete`); `ProductSoftDeleteIntegrationTest` |

## Current backend follow-up — 2026-11-19

The current branch adds allowlisted/stable sorting for all product-list display fields; database-generated, read-only Product/Batch IDs; Product SKU generation from the database Product ID; backend-generated per-Product batch codes (`BATCH-YYYYMMDD-NNN`); a confirm-and-retry warning for similar product names; and expiry-date correction/clearing for inventory batches. Batch source fields (`batchCode`, `receivedAt`, `initialQuantity`) remain immutable. No migration was needed, and this repository has no frontend UI; see the current [API contract](../../api/F02_PRODUCT_INVENTORY_API.md) and [frontend handoff](../../frontend_handoff/F02_HANDOFF.md).

F09 adds optional Product display metadata in V27: `imageUrl` (absolute HTTP(S), max 2,048 characters) and `description` (max 2,000). F02 returns both fields; image upload is not supported. SKU remains backend-generated and read-only.

> Historical verification record: Backend `dev` @ `11973a0` (merge of PR #11; source tree identical to `a4f1115`, whose code passed the full verification script on `45dbd80`). See the 2026-11-19 follow-up above for current changes and tests.

## Historical status — branch `Khoi/feat/f03-remove-legacy-product-fields` (2026-11-16)

Code paths are relative to `src/main/java/vn/edu/fpt/paspm/`; V26 is `src/main/java/db/migration/V26__f03_drop_legacy_product_scoring_columns.java`.

| Item | Behaviour | Evidence |
|---|---|---|
| `expiryPushDays` on products | **Removed** from `ProductRequest` and `ProductView`; sending it → 400 (unknown field) | `catalog/CatalogContracts.java` (`ProductRequest`, `ProductView`); `LegacyProductFieldsRemovedTest` |
| JPA / JDBC | `ProductEntity`, `ProductMetadataPersistence`, `ProductRepository` no longer read or write the column | `catalog/product/persistence/ProductEntity.java`; `catalog/product/ProductRepository.java` |
| V26 | Drops `product.expiry_push_days`, `product.stock_priority_score`, `product.seasonal_priority_score` and their CHECKs; other product data unchanged | `LegacyProductColumnsV26MigrationTest` (H2 + MySQL, from V25 with data and fresh) |
| Batch `pushDay` | Unchanged: the only push-day input (create, PATCH, `DELETE /api/inventory-batches/{id}/push-day`) | [F02 API](../../api/F02_PRODUCT_INVENTORY_API.md) |

**Known gaps:** V26 cannot be undone without a backup taken before it; the F02 ERD drawing still shows the dropped columns (not edited). Decision: [DEC-20261116-02](../../decisions/2026-11-16/DEC-20261116-02-p5-remove-legacy-product-fields.md).

## Status 2026-11-15 (history) — branch `Khoi/feat/f05-progress-f03-weight-v2`

Code paths below are relative to `src/main/java/vn/edu/fpt/paspm/` on `c8c837a` (code identical to `e38b55f`).

| Item | Behaviour on the branch | Evidence |
|---|---|---|
| Batch `pushDay` | Optional `DATE` on `BatchCreateRequest`, `BatchPatchRequest` and `BatchView` | `catalog/CatalogContracts.java:57`, `:63`, `:69` |
| Rule | `pushDay` must be on or before `expiryDate`, else 400 (also a DB CHECK) | `catalog/inventory/InventoryBatchService.java:68-70`; `db/migration/V25__f03_weight_v2_expiry_season.java:22` (under `src/main/java/`) |
| PATCH | `pushDay` counts toward "at least one batch field"; null means "not provided" (cannot clear) | `catalog/inventory/InventoryBatchService.java:52-53`, `:63-64` |
| Role | Store Admin only, own Store | `catalog/inventory/InventoryBatchController.java:26` |
| `product.expiryPushDays` | *(2026-11-15)* still accepted and stored, deprecated (P5), ignored. **Superseded 2026-11-16: removed, column dropped by V26** | `catalog/CatalogContracts.java:38`; no reader in `utility/` or `recommendation/` (W2 reads `inventory_batch.push_day`: `utility/UtilityProductRepository.java:29`) |
| Backfill | None: existing batches have `push_day = NULL` until a Store Admin sets it | `db/migration/V25__f03_weight_v2_expiry_season.java:21` |

Verification: `PushDayIntegrationTest` (5 cases) inside the final gate (645 tests, 640 PASS, 5 SKIP; [record 004](../../record-of-changes/2026-11-15/04-f03-weight-v2-expiry-season.md)).

## 1. Purpose and scope
Store-scoped product catalogue, categories and inventory batches. Stock is never stored on the product: it is derived from batch remaining quantities. The Store Admin owns each batch's `pushDay`, which F03 Weight v2 uses. Product `expiryPushDays` was removed with V26 on 2026-11-16; it is not in the current request or response contract.

## 2. Actors and effective RBAC
| Role | Capability |
|---|---|
| `STORE_ADMIN` | create/update/activate categories and products; receive, read and adjust batches; inventory summary (own Store) |
| `MERCHANDISER` | read-only: `GET` categories and products (list and detail), own Store |
| `SYSTEM_ADMIN`, `END_CUSTOMER` | no F02 access (403) |

Foreign or absent records answer 404 after the role gate. Matrix: [RBAC matrix](../../api/F01_F02_RBAC_MATRIX.md).

## 3. Implemented functionality (17 operations)
Categories 5, products 5 (`GET` list with search/filter/sort/page, detail, create, full update, status), batches 6 (list/create, detail/PATCH, and clear-expiry/clear-push-day DELETE routes), inventory summary 1 — 17 operations total. Product and Category writes use JPA; catalogue reads and inventory use JDBC (hybrid persistence, see [architecture decisions](../../architecture/HYBRID_PERSISTENCE_DECISIONS.md)).

## 4. Business rules
- SKU is generated as `SKU-` plus the database Product ID padded to 11 digits; clients omit it from create/update requests and treat it as read-only. Batch code is generated as `BATCH-YYYYMMDD-NNN` with a Product-scoped sequence. Similar product names are confirmable warnings, not unique constraints.
- `requiresExpiry=true` products need an expiry date on every batch; enabling it while a batch lacks one is 400.
- `PATCH` batch: `remainingQuantity` 0..initial, status `ACTIVE|BLOCKED`, `pushDay` and/or `expiryDate`; null/omitted dates are unchanged, source receipt fields are immutable, and `pushDay` must not exceed expiry date.
- Batch `expiryDate` can be cleared only for a product with `requiresExpiry=false`; batch `pushDay` and optional expiry are cleared by separate idempotent DELETE routes.
- Batch `pushDay` (2026-11-15): optional date, ≤ `expiryDate`, Store Admin only; a batch without one only produces an F03 warning.
- `expiryPushDays`: deprecated 2026-11-15 (P5) and **removed 2026-11-16** (DEC-20261116-02): no longer part of the product request or response; sending it → 400.
- Sellable stock counts ACTIVE batches with remaining > 0 whose expiry is on or after today (Store time zone); a batch expiring today is still sellable (changed 2026-11-24, DEC-20261124-01; it was "strictly after today").
- Products and categories are deactivated, never deleted.

## 5. Main workflows
Create category → create product → receive batches with a `pushDay` when the product expires → read inventory summary. F04 CSV rows resolve products by SKU; F03 Weight v2 reads batches ordered by push day then batch id.

## 6. API documentation
[F02 API contract](../../api/F02_PRODUCT_INVENTORY_API.md) · [Frontend handoff](../../frontend_handoff/F02_HANDOFF.md) · [runtime OpenAPI](../../api/openapi.json).

## 7. Entities and migrations
`category`, `product`, `inventory_batch` (V3); V8 adds `expiry_push_days` (legacy since V25, **dropped by V26**; V4 score columns also dropped by V26); **V25 adds `inventory_batch.push_day` + CHECK** and `product_season`; later features add FKs to these tables (F04 transaction items, F05 pattern items). Schema notes: [V24/V25](../../architecture/F03_W2_SCHEMA_V24_V25.md).

## 8. Dependencies
Uses F01 (role, Store). Consumed by F03 (batch push days and FEFO), F04 (SKU resolution), F06 (current sellable quantity, push-day expiry alerts).

F01 session reconciliation (2026-11-12, dev V20 retained + additive V21): catalog APIs still require a valid Bearer with their existing role and Store checks. Refresh/logout does not grant END_CUSTOMER catalog access or change stock, expiry or persistence rules. See [shared session impact and evidence](../F01/F01_SESSION_MANAGEMENT.md#5-tác-động-tới-feature-khác).

## 9. Tests and evidence
`CatalogIntegrationTest`, `CatalogDevSeederTest`, `CategoryPersistence*Test`, `ProductPersistence*Test` (H2 and MySQL), `JpaPilotMigrationMySqlTest`, `PilotCloneApiIntegrationTest` (opt-in, 5/5 on 2026-11-02), `ExpiryFefoOrderingTest` (FEFO order `expiry_date → received_at → batch_id`). Historical Swagger evidence: [reference_v3](reference_v3/F02_SWAGGER_TEST_REPORT.md).

## 10. Known limitations
No Product/Category deletion and no stock movement or adjustment history. Batch DELETE routes only clear optional dates. The `dev` profile seeds demo data and a development-only account; never enable it on a shared database.

Known gaps (2026-11-15, history): a batch `pushDay` could not be cleared through PATCH (fixed 2026-11-16 by `DELETE …/push-day`); all pre-V25 batches start without a push day; the deprecated `expiryPushDays` column remained in the schema (dropped by V26 on 2026-11-16). The F02 ERD drawing does not show `push_day` (diagram not edited, see [schema notes](../../architecture/F03_W2_SCHEMA_V24_V25.md)).

## 11. Pending decisions
None open. P5 (accept but ignore `expiryPushDays`) was an assumption on 2026-11-15 and was **changed on 2026-11-16 to full removal** ([DEC-20261116-02](../../decisions/2026-11-16/DEC-20261116-02-p5-remove-legacy-product-fields.md)); Q1 (push day per batch) is an owner decision of 2026-11-15. See [decision register](../../decisions/OWNER_DECISIONS_F03_F05_2026-11-15.md).

## 12. Related diagrams
[Diagram index](../../diagrams/README.md) and the editable [27-page Draw.io pack](../../diagrams/PASPM_Unified_27_Diagrams_UML2_Editable.drawio) (QA: [UML27 report](../../reports/UML27_DIAGRAM_QA_2026-11-02.md)); relevant pages: Use Case UC17–UC20, AD01; the F02 ERD is `docs/diagrams/PASPM_Complete_ERD_F02_Inventory_Batch_v3.drawio` (preserved unchanged).

## 13. Historical references
[Original v3 backlog](reference_v3/PASPM_F02_Inventory_Batch_API_Swagger_Backlog_v3.md), [issues](reference_v3/F02_GitHub_Issues/README.md), [Product JPA report](../../architecture/PRODUCT_JPA_INTEGRATION_REPORT.md), [Category JPA report](../../architecture/CATEGORY_JPA_IMPLEMENTATION_REPORT.md). Old all-StoreAdmin and expiry-today clauses are superseded.

## 2026-11-16 follow-up
Store Admin can clear a batch push day with DELETE `/api/inventory-batches/{id}/push-day`; idempotent, returns BatchView, foreign/unknown 404. PATCH null retains the value. See the current API contract.

## 2026-11-25 — F09 product display metadata (V29)
Product create/update accepts optional `imageUrl` (absolute HTTP(S) URL; no upload) and `description` (maximum 2,000 characters); F02 views return both (`product.image_url`, `product.description`, migration V29). F02 responses remain administrative and include `costPrice` / `stockQuantity`. The public storefront uses a separate DTO with neither field: [F09 API contract](../../api/F09_STOREFRONT_API.md).

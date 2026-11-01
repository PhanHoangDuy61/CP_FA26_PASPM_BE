# F02 — Product, Category and Inventory

> Last verified Backend: `dev` @ `11973a0` (merge of PR #11; source tree identical to `a4f1115`, whose code passed the full verification script on `45dbd80`). This documentation task ran no new tests.

## 1. Purpose and scope
Store-scoped product catalogue, categories and inventory batches. Stock is never stored on the product: it is derived from batch remaining quantities. Product `expiryPushDays` (the F03 v1.2 threshold) is owned here.

## 2. Actors and effective RBAC
| Role | Capability |
|---|---|
| `STORE_ADMIN` | create/update/activate categories and products; receive, read and adjust batches; inventory summary (own Store) |
| `MERCHANDISER` | read-only: `GET` categories and products (list and detail), own Store |
| `SYSTEM_ADMIN`, `END_CUSTOMER` | no F02 access (403) |

Foreign or absent records answer 404 after the role gate. Matrix: [RBAC matrix](../../api/F01_F02_RBAC_MATRIX.md).

## 3. Implemented functionality (15 operations)
Categories 5, products 5 (`GET` list with search/filter/sort/page, detail, create, full update, status), batches 4 (`GET`/`POST /api/products/{id}/batches`, `GET`/`PATCH /api/inventory-batches/{id}`), inventory summary 1. Product and Category writes use JPA; catalogue reads and inventory use JDBC (hybrid persistence, see [architecture decisions](../../architecture/HYBRID_PERSISTENCE_DECISIONS.md)).

## 4. Business rules
- SKU unique per Store (409); batch code unique per product (409).
- `requiresExpiry=true` products need an expiry date on every batch; enabling it while a batch lacks one is 400.
- `PATCH` batch: `remainingQuantity` 0..initial, status `ACTIVE|BLOCKED`; at least one field.
- `expiryPushDays`: integer ≥ 0, omitted or null stored as 0, **no Store fallback**; a full `PUT` that omits it resets it to 0. Only Store Admin writes it.
- Sellable stock counts ACTIVE batches with remaining > 0 whose expiry is strictly after today (Store time zone); a batch expiring today is not sellable.
- Products and categories are deactivated, never deleted.

## 5. Main workflows
Create category → create product (with `expiryPushDays` when it expires) → receive batches → read inventory summary. F04 CSV rows resolve products by SKU; F03 reads batches in FEFO order.

## 6. API documentation
[F02 API contract](../../api/F02_PRODUCT_INVENTORY_API.md) · [Frontend handoff](../../frontend_handoff/F02_HANDOFF.md) · [runtime OpenAPI](../../api/openapi.json).

## 7. Entities and migrations
`category`, `product`, `inventory_batch` (V3); V8 adds the Product binary-expiry priority column `expiry_push_days`; later features add FKs to these tables (F03 scores, F04 transaction items, F05 pattern items).

## 8. Dependencies
Uses F01 (role, Store). Consumed by F03 (threshold and FEFO batches), F04 (SKU resolution), F06 (current sellable quantity).

## 9. Tests and evidence
`CatalogIntegrationTest`, `CatalogDevSeederTest`, `CategoryPersistence*Test`, `ProductPersistence*Test` (H2 and MySQL), `JpaPilotMigrationMySqlTest`, `PilotCloneApiIntegrationTest` (opt-in, 5/5 on 2026-11-02), `ExpiryFefoOrderingTest` (FEFO order `expiry_date → received_at → batch_id`). Historical Swagger evidence: [reference_v3](reference_v3/F02_SWAGGER_TEST_REPORT.md).

## 10. Known limitations
No delete endpoints and no stock movement or adjustment history (only batch `PATCH`). The `dev` profile seeds demo data and a development-only account; never enable it on a shared database.

## 11. Pending decisions
None recorded for F02.

## 12. Related diagrams
`docs/diagrams/README.md` indexes the 27-page Draw.io pack: Use Case UC17–UC20, AD01; the F02 ERD is `docs/diagrams/PASPM_Complete_ERD_F02_Inventory_Batch_v3.drawio` (preserved unchanged).

## 13. Historical references
[Original v3 backlog](reference_v3/PASPM_F02_Inventory_Batch_API_Swagger_Backlog_v3.md), [issues](reference_v3/F02_GitHub_Issues/README.md), [Product JPA report](../../architecture/PRODUCT_JPA_INTEGRATION_REPORT.md), [Category JPA report](../../architecture/CATEGORY_JPA_IMPLEMENTATION_REPORT.md). Old all-StoreAdmin and expiry-today clauses are superseded.

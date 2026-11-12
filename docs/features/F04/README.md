# F04 — Transaction Import

> Updated 2026-11-12: additive async CSV submit/progress API and Flyway V22; the existing synchronous route remains supported.

## 1. Purpose and scope
File-based import of historical sales into Store-scoped transactions that feed F03. Current `dev` scope includes synchronous and asynchronous CSV uploads. Excel, external APIs, POS integration and webhooks remain outside this branch.

## 2. Actors and effective RBAC
| Role | Capability |
|---|---|
| `STORE_ADMIN` | upload CSV; read imports, import errors and transactions (own Store) |
| `MERCHANDISER` | read imports, import errors and transactions (own Store); cannot upload |
| `SYSTEM_ADMIN`, `END_CUSTOMER` | none (403) |

The Store comes from the account; a file or request can never choose a Store. Foreign import or transaction ids answer 404. The RBAC matrix gained the six F04 rows in this synchronisation: [RBAC matrix](../../api/F01_F02_RBAC_MATRIX.md).

## 3. Implemented functionality (8 operations)
`POST /api/transactions/imports/csv` (legacy synchronous, multipart `file`, 201), `POST /api/transactions/imports/csv/async` (multipart `file` + required `Idempotency-Key`, 202), `GET /api/transactions/imports/{id}/progress`, `GET /api/transactions/imports`, `GET /api/transactions/imports/{id}`, `GET /api/transactions/imports/{id}/errors`, `GET /api/transactions`, `GET /api/transactions/{id}`. **Excel upload is not on `dev`**.

## 4. Business rules
- Header exactly `source_order_id,occurred_at,sku,quantity,unit_price,unit_cost,status`; UTF-8; `occurred_at` ISO-8601 with an explicit UTC offset.
- Status per order: `COMPLETED`, `CANCELLED`, `REFUNDED`, `PARTIAL_REFUND`; all lines of an order must agree.
- Limits 5 MiB and 10,000 data rows (`paspm.transaction.import.max-file-bytes`, `max-rows`).
- Each valid order is persisted atomically; invalid orders do not erase valid ones (`COMPLETED_WITH_ERRORS`). Idempotency key: Store + `CSV_UPLOAD` + order id: same content is a duplicate, different content is a conflict, history is kept.
- Async batch idempotency is Store + request key + SHA-256 of exact file bytes. Reusing the same key/file returns the same batch; same key/different file is 409; a new key intentionally creates a new batch, with order-level duplicate/conflict rules still applied.
- Async rows, batch lifecycle, statistics and errors are stored in the database. A fenced worker lease resumes interrupted batches; only measured processed rows / known parsed rows produce a percentage. No local temporary file is used.
- Only `COMPLETED` orders whose unit prices are all at least unit cost are F03-eligible; others stay as history. Imports never change inventory batches.
- SKUs must exist in the importing Store (F02).

## 5. Main workflows
Async upload → save `importBatchId` → poll store-scoped progress until `COMPLETED`, `COMPLETED_WITH_ERRORS`, or `FAILED` → read rejected rows → browse transactions. Clients may continue to use the old synchronous endpoint and its unchanged `ImportSummary` response. F03 preview/snapshot reads eligible rows through `DatabaseTransactionSource` (`F04_DATABASE_V1`).

## 6. API documentation
[F04 API contract](../../api/F04_TRANSACTION_API.md) · [Frontend handoff](../../frontend_handoff/F04_HANDOFF.md) · [runtime OpenAPI](../../api/openapi.json) · [improvement and regression report](F04_IMPROVEMENT_REPORT.md).

## 7. Entities and migrations
`api_connection`, `import_batch`, `sales_transaction`, `transaction_item`, `import_staging_row` (V7); V12 `f04_preserve_large_raw_import_rows`; V22 `f04_async_transaction_imports` adds async status, idempotency, progress and lease/result fields. Existing migrations are not modified. `api_connection` exists in the schema but no API uses it.

## 8. Dependencies
F01 (role, Store), F02 (SKU resolution). Feeds F03 (and through it F05, F06 evidence).

F01 session reconciliation (2026-11-12, dev V20 retained + additive V21): browser import/query calls continue using Bearer and existing Store authorization. No change to transaction history, file parsing, idempotency or rollback semantics. See [shared session impact and evidence](../F01/F01_SESSION_MANAGEMENT.md#5-tác-động-tới-feature-khác).

## 9. Tests and evidence
`TransactionImportIntegrationTest` now covers synchronous compatibility, async acceptance/polling, idempotency, Store scope, invalid files, persisted readback and lease fencing/replay; related parser/correctness and migration tests are also run for this change. MySQL variants and the earlier [performance report](../../testing/F04_F06_PERFORMANCE_REPORT.md) retain their dated scope.

## 10. Known limitations
No Excel endpoint on `dev`; no external ingestion by design; upload is limited to one file per call and 10,000 rows by default; rejected rows are inspectable but not repairable through the API. A processing failure is terminal; a deliberate client retry uses a new idempotency key, while a worker/process interruption is recovered after lease expiry.

## 11. Pending decisions
Excel endpoint delivery and merge (tracked on its own branch). No Product Owner decision is pending for CSV.

## 12. Related diagrams
[Diagram index](../../diagrams/README.md) and the editable [27-page Draw.io pack](../../diagrams/PASPM_Unified_27_Diagrams_UML2_Editable.drawio) (QA: [UML27 report](../../reports/UML27_DIAGRAM_QA_2026-11-02.md)); relevant pages: Use Case UC21 (includes UC30), AD02 transaction processing, State R2-06.01 (Import Batch) and R2-06.06 (Staging Row), Sequence AD02, Context (S2 flows).

## 13. Historical references
[Design and approved CSV policy](reference/F04_DESIGN.md), [implementation plan](reference/F04_IMPLEMENTATION_PLAN.md), [Swagger report](reference/F04_SWAGGER_TEST_REPORT.md), [original gap analysis](reference/F04_GAP_ANALYSIS.md) (pre-implementation baseline, not current absence).

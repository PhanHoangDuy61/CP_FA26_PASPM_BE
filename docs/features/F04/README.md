# F04 — Transaction Import

> Last verified Backend: `dev` @ `11973a0` (merge of PR #11; source tree identical to `a4f1115`, whose code passed the full verification script on `45dbd80`). This documentation task ran no new tests.

## 1. Purpose and scope
File-based import of historical sales into Store-scoped transactions that feed F03. Approved scope is CSV and Excel files. External APIs, POS integration, polling and webhooks are explicitly **out of scope** and their absence does not make F04 incomplete.

## 2. Actors and effective RBAC
| Role | Capability |
|---|---|
| `STORE_ADMIN` | upload CSV; read imports, import errors and transactions (own Store) |
| `MERCHANDISER` | read imports, import errors and transactions (own Store); cannot upload |
| `SYSTEM_ADMIN`, `END_CUSTOMER` | none (403) |

The Store comes from the account; a file or request can never choose a Store. Foreign import or transaction ids answer 404. The RBAC matrix gained the six F04 rows in this synchronisation: [RBAC matrix](../../api/F01_F02_RBAC_MATRIX.md).

## 3. Implemented functionality (6 operations)
`POST /api/transactions/imports/csv` (multipart `file`, 201), `GET /api/transactions/imports`, `GET /api/transactions/imports/{id}`, `GET /api/transactions/imports/{id}/errors`, `GET /api/transactions`, `GET /api/transactions/{id}`. **Excel upload is not on `dev`**: the format contract is approved, the endpoint lives on a separate unmerged branch.

## 4. Business rules
- Header exactly `source_order_id,occurred_at,sku,quantity,unit_price,unit_cost,status`; UTF-8; `occurred_at` ISO-8601 with an explicit UTC offset.
- Status per order: `COMPLETED`, `CANCELLED`, `REFUNDED`, `PARTIAL_REFUND`; all lines of an order must agree.
- Limits 5 MiB and 10,000 data rows (`paspm.transaction.import.max-file-bytes`, `max-rows`).
- Each valid order is persisted atomically; invalid orders do not erase valid ones (`COMPLETED_WITH_ERRORS`). Idempotency key: Store + `CSV_UPLOAD` + order id: same content is a duplicate, different content is a conflict, history is kept.
- Only `COMPLETED` orders whose unit prices are all at least unit cost are F03-eligible; others stay as history. Imports never change inventory batches.
- SKUs must exist in the importing Store (F02).

## 5. Main workflows
Upload CSV → read import summary and rejected rows → browse transactions → F03 preview/snapshot reads eligible rows through `DatabaseTransactionSource` (`F04_DATABASE_V1`).

## 6. API documentation
[F04 API contract](../../api/F04_TRANSACTION_API.md) · [Frontend handoff](../../frontend_handoff/F04_HANDOFF.md) · [runtime OpenAPI](../../api/openapi.json) · [improvement and regression report](F04_IMPROVEMENT_REPORT.md).

## 7. Entities and migrations
`api_connection`, `import_batch`, `sales_transaction`, `transaction_item`, `import_staging_row` (V7); V12 `f04_preserve_large_raw_import_rows` (from `origin/dev`, unchanged by F05). `api_connection` exists in the schema but no API uses it.

## 8. Dependencies
F01 (role, Store), F02 (SKU resolution). Feeds F03 (and through it F05, F06 evidence).

F01 session reconciliation (2026-11-12, dev V20 retained + additive V21): browser import/query calls continue using Bearer and existing Store authorization. No change to transaction history, file parsing, idempotency or rollback semantics. See [shared session impact and evidence](../F01/F01_SESSION_MANAGEMENT.md#5-tác-động-tới-feature-khác).

## 9. Tests and evidence
`TransactionCsvParserTest`, `TransactionOrderValidatorTest`, `TransactionOrderIdentifierTest`, `TransactionImportIntegrationTest`, `TransactionImportCorrectnessIntegrationTest`, MySQL variants, `TransactionRawEvidenceMigrationMySqlTest` (V12 compatibility), `DatabaseTransactionSourceTest`; all inside the 23/23 script run. [Performance report](../../testing/F04_F06_PERFORMANCE_REPORT.md) (dated measurements).

## 10. Known limitations
No Excel endpoint on `dev`; no external ingestion by design; upload is limited to one file per call and 10,000 rows by default; rejected rows are summarised, not repairable through the API.

## 11. Pending decisions
Excel endpoint delivery and merge (tracked on its own branch). No Product Owner decision is pending for CSV.

## 12. Related diagrams
[Diagram index](../../diagrams/README.md) and the editable [27-page Draw.io pack](../../diagrams/PASPM_Unified_27_Diagrams_UML2_Editable.drawio) (QA: [UML27 report](../../reports/UML27_DIAGRAM_QA_2026-11-02.md)); relevant pages: Use Case UC21 (includes UC30), AD02 transaction processing, State R2-06.01 (Import Batch) and R2-06.06 (Staging Row), Sequence AD02, Context (S2 flows).

## 13. Historical references
[Design and approved CSV policy](reference/F04_DESIGN.md), [implementation plan](reference/F04_IMPLEMENTATION_PLAN.md), [Swagger report](reference/F04_SWAGGER_TEST_REPORT.md), [original gap analysis](reference/F04_GAP_ANALYSIS.md) (pre-implementation baseline, not current absence).

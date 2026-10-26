# F04 Excel File Contract and Template

Approved by the project owner in the 2026-10-26 task response. Implemented route on the Excel feature branch: `POST /api/transactions/imports/excel`. The CSV route remains `POST /api/transactions/imports/csv`. Both use multipart field `file`, require F01 Bearer authentication and STORE_ADMIN, and return 201 ImportSummary through the same order-processing pipeline.

## Workbook and columns

Accept `.xlsx` OOXML and `.xls` OLE2/BIFF workbooks. Exactly one worksheet may contain nonblank content; empty additional worksheets are allowed. A second sheet containing even a header is nonempty and causes 400. The selected worksheet must contain this exact row 1 header and at least one nonblank data row:

| Column | Header | Accepted cell representation |
|---|---|---|
| A | source_order_id | Text; required by the shared validator; normalized maximum 150 UTF-16 units |
| B | occurred_at | Text containing ISO8601 with explicit UTC offset |
| C | sku | Text; preserve leading zeros; resolve within the authenticated Store |
| D | quantity | Number or numeric text; a positive 32-bit whole number after shared validation |
| E | unit_price | Number or decimal text; nonnegative DECIMAL12,2 range without rounding |
| F | unit_cost | Same money rules |
| G | status | Text; COMPLETED/CANCELLED/REFUNDED/PARTIAL_REFUND via the existing normalization |

Blank fields become empty strings and receive existing per-order validation errors. Blank rows are skipped; error row numbers are actual 1-based worksheet rows. Formatting does not turn numeric IDs/SKUs into text: a cell displaying 0001 but storing numeric 1 is rejected. Set those columns to text and enter the actual string.

A native Excel date/serial in occurred_at is rejected. Use text such as `2026-10-19T09:00:00+07:00`. Numeric quantity/money cells are read from their stored numeric value, not formatted display/currency/locale text. An integer numeric value such as 1.0 normalizes exactly to 1; no fractional rounding is performed. Invalid numeric quantities or text date/money values reach the common validator as per-order business errors. Extremely large exponents are checked before any potentially huge decimal expansion.

## Error and atomicity rules

- 400: corrupted/unsupported/empty workbook, wrong headers, multiple nonempty sheets, unsupported cell types or active content, parser row/structure limits. The whole workbook is rejected before staging business rows; a created batch is marked FAILED.
- 413: compressed upload exceeds the configured file-byte limit, including an inaccurate MultipartFile size declaration detected while reading.
- 201: structurally accepted workbook processed. Valid independent orders may succeed while business-invalid orders are rejected atomically; use row/order counters and the existing errors endpoint.
- 401/403: missing/invalid token or insufficient role. Record reads outside the caller Store remain 404.
- A genuine database write failure remains a failed import and rolls back the affected order; no partial successful summary is fabricated.

Use the existing nullable `ImportError.sourceOrderId`, `rowNumber` and `errorDetails`. Raw parsed text fields remain exact in staging JSON; numeric cells are represented by their stored value converted to the shared row string model. The original binary workbook is not retained as an evidence artifact. No new error/history/raw-data endpoint is added.

## CSV and Excel identity compatibility

Both formats intentionally use the existing persisted namespace `source_system='CSV_UPLOAD'`. This historical name is a compatibility key, not a claim that Excel bytes were CSV. No existing record is relabeled or rewritten and no source format is added to the identity hash.

The key remains `(Store, CSV_UPLOAD, normalized source_order_id)`. Equal normalized content across CSV→Excel or Excel→CSV is DUPLICATE; differing content is CONFLICT. Neither case overwrites historical quantities/prices/costs/hash. Import batch filename retains the uploaded extension. Only the existing COMPLETED/nonnegative-margin eligibility rules apply to F03; importing history never updates inventory.

## Limits and unsupported content

Shared defaults: 5 MiB upload and 10,000 nonblank data rows. Configuration keys remain `paspm.transaction.import.max-file-bytes` and `paspm.transaction.import.max-rows`.

| Excel key prefix: paspm.transaction.import.excel. | Default | Purpose |
|---|---:|---|
| max-expanded-bytes |33554432|Actual total ZIP expansion 32 MiB |
| max-entry-bytes |8388608|Actual individual entry 8 MiB |
| max-entries |128|ZIP/OLE structure count |
| max-cell-characters |32767|UTF-16 cell-text bound |
| max-shared-strings |100000|Shared-string entry cap |
| max-styles |1024|Style record cap |
| max-scanned-rows |100000|All scanned rows, including blank rows |

ZIP names are checked for duplicates/path anomalies. Unsafe compression ratios are rejected; external XML entities/DTDs/relationships are not resolved. Formula cells, cached formula results, conditional formatting/data validation formula structures, external workbook links, macros, embedded/encrypted content and unsupported non-worksheet streams are rejected. Advanced parts such as comments, tables, drawings and defined names are unsupported in this deliberately restricted transaction template. Ordinary text/number values and supported formatting suffice.

The reader uses file-backed XLSX/SAX and XLS event parsing with BIFF preflight; it does not construct a full user-model workbook in production. Temporary uploads are removed after parsing. No global POI limits are changed per request. Technical limits reduce risk but do not constitute a process sandbox or a complete hostile-document CPU/heap guarantee; enforce deployment-level request/concurrency/resource controls before production.

## Actual sample files

- [transactions-template.xlsx](examples/transactions-template.xlsx)
- [transactions-template.xls](examples/transactions-template.xls)

Each has one Transactions sheet, the seven headers and one synthetic row: order ID text 0001, SKU text 00123, explicit-offset timestamp, quantity 1, unit price 10.00, unit cost 6.00 and COMPLETED. Create/choose Product SKU 00123 in the authorized Store before importing or replace it with a matching Store SKU. Change the order ID for a new order; importing the unchanged template again or across formats is intentionally a duplicate. Dates/values are illustrative historical facts, not current-date requirements. Both actual files are read by parser and HTTP regression tests.

## Library and verification provenance

POI 6.0.0 is pinned and was compiled/executed with Backend Java 17. Official sources: [download/license](https://poi.apache.org/download.html), [secure processing guidance](https://poi.apache.org/security.html), [streaming XLSX API](https://poi.apache.org/apidocs/dev/org/apache/poi/xssf/eventusermodel/XSSFReader.html), [HSSF event model](https://poi.apache.org/apidocs/dev/org/apache/poi/hssf/eventusermodel/package-summary.html). Dependency resolution follows Spring Boot's managed versions; execution is not a claim of a full dependency vulnerability audit. See the current [improvement report](F04_IMPROVEMENT_REPORT.md) for actual test counts, remaining limits and PR dependency.

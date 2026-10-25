# F04 CodeGraph Index Report

Execution date: 2026-10-19 (Asia/Ho_Chi_Minh)

CodeGraph was available for the Backend workspace. The initial architecture review traced the existing `TransactionSource` seam and its use by F03 `UtilityMatrixBuilder`, then checked the F01 Store access integration used by F04. After implementation, the index reflected the F04 transaction package and its connections:

- `TransactionImportController` → `TransactionImportService` → `TransactionCsvParser`, `TransactionOrderValidator`, `TransactionImportRepository` and `CatalogAccess`.
- `TransactionController` → Store-scoped `TransactionQueryRepository` and `CatalogAccess`.
- `DatabaseTransactionSource` implements the existing F03 `TransactionSource` interface and is consumed by the utility calculation path.
- Multipart limits are configured from `TransactionImportProperties`.

Post-implementation CodeGraph status reported 775 indexed files, 9,581 nodes and 17,870 edges. An `codegraph_explore` query found the F04 parser, importer and database adapter and returned their source plus relationship edges. CodeGraph updates through the workspace index watcher; the available integration exposes status/query tools and no separate manual refresh command. No CodeGraph database artifacts were added to Git.

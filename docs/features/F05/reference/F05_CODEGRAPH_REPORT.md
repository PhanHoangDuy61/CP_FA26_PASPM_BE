# F05 CodeGraph analysis and synchronization

Executed with the installed CodeGraph CLI0.9.9 and exposed MCP integration. No tool was installed or globally reconfigured. Existing workspace index includes BE and FE source; no Frontend implementation was modified.

Before implementation, semantic exploration traced UtilitySnapshotRepository/UtilityMatrixBuilder/UtilityMatrixContracts, UtilitySettingsRepository, CatalogAccess/StoreScope, DatabaseTransactionSource and the unused MiningEngine boundary. Confirmed F04 historical transaction source feeds F03, frozen matrix pages use PRODUCT_ID_V1, F03 applies scale6/HALF_UP, and there were no physical mining tables, runner or F06 engine.

After implementation, CodeGraph's active file watcher indexed the new mining source. Executed `codegraph_explore` for MiningController/MiningService/MiningWorker/MiningRepository/SpmfMiningEngine/MiningInputBuilder. Observed relationships:

- MiningController → MiningService.
- MiningService → MiningWorker, MiningEngine, UtilitySnapshotRepository, CatalogAccess/AuthService.
- MiningWorker → MiningEngine and MiningRepository.
- SpmfMiningEngine implements MiningEngine.
- MiningRepository → UtilitySettingsRepository for the project's JSON codec.
- MiningIntegrationTest references the new worker/API/repository boundaries.

These relationships use existing F01 scope and F03 frozen persistence. F05 does not reference UtilityMath, ExpiryScoreCalculator, current Product price/scores or current inventory in its mining computation. The only Product/transaction query outside snapshot reads validates Store ownership.

Final executed MCP status:791 indexed files,10,030 nodes,19,037 edges,20.92MB; Java692, TypeScript24, TSX39, JavaScript6 (plus existing tooling/configuration files). No pending-sync warning was reported. This is a workspace graph, not a count of F05 files. Graph files remain in their existing standard ignored location; no generated graph database was added to Git.

CodeGraph cross-file resolution is best-effort static analysis. Graph discovery confirms structural integration; Maven, real SPMF execution, MySQL/H2 checks and Swagger provide the behavioral evidence. No claim of F06 implementation or full semantic proof is inferred from the graph.

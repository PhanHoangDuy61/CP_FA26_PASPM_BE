<!-- docs-reorganization:notice -->
> **NEEDS_REVIEW — documentation review 2026-10-26.** F05/F06 implementation exists; stale absent-engine prose and missing final-source links remain unresolved. See [current navigation](../README.md). Original results and business-rule text below are retained; this notice is not a new execution or approval.
<!-- /docs-reorganization:notice -->

# F03 → F04/F05 handoff (v1.2)

The current contract is [F03_V12_F05_F06_INTEGRATION.md](../../../api/F03_V12_F05_F06_INTEGRATION.md), with [F03 Utility API](../../../api/F03_UTILITY_API.md) and [final business specification](../../../requirements/README.md#f03-v12-source-gap).

F04 is implemented: DatabaseTransactionSource reads validated persisted COMPLETED transactions with historical quantity/price/cost; V7 owns the transaction FK. F03 never substitutes current Product price/cost. The normal provider is F04_DATABASE_V1; optional dev fixtures are not production data.

F05 consumes known immutable snapshot IDs and all frozen matrix pages. New snapshots use EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1; old F03_LINEAR_V1 values remain readable and unchanged. Product-ID mapping is stable, utility scale is6 HALF_UP, and exact signed64 scaled conversion is required. Matrix JSON identity/hash is distinct from any future mining-file byte hash. Do not recompute weights.

F05 P0 has now implemented the frozen snapshot adapter, V9 Mining Request/Run/pattern persistence, asynchronous APIs, actual HUI-Miner/EFIM execution and benchmark comparison. See [F05 Mining API](../../../api/F05_MINING_API.md) and [verification](../../F05/reference/F05_VERIFICATION.md); these additions are separate from F03 scoring. F06 similarly lacks a Recommendation Engine, but the implemented Store-scoped expiry-push API supplies independent current batch data. Read the linked integration contract for required current sellability checks.

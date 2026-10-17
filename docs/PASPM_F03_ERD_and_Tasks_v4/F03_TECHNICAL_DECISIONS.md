# F03 Technical Decisions

## Confirmed business decisions

2026-10-17 user confirmation supersedes original v4 proposals: configurable positive integer T belongs to Store Admin (default 30); System Admin sets versioned lambda bounds (initial [0,0.5]); Merchandiser selects lambda/coefficients and manual S/M. Policy changes do not clamp settings. New snapshots validate current policy, historical snapshots do not read live config.

## Architecture

- Existing backend uses JdbcTemplate, not JPA entities. F03 follows that repository/service/controller pattern under `utility`. F01 CatalogAccess/StoreScope and JWT roles remain authoritative.
- No persisted global settings mechanism exists in V1-V3. V4 adds a single generic `system_configuration` table with the `UTILITY_LAMBDA_POLICY` key, JSON value and version. Store-level settings reuse `STORE.utility_config`; no duplicate Store config table or score endpoints.
- `Retail Planner` uses existing MERCHANDISER role. Policy write: SYSTEM_ADMIN; T write: STORE_ADMIN; weights/S/M/preview/snapshots: MERCHANDISER. Config read returns current policy to Store Admin/Merchandiser.
- Settings writes and snapshot capture acquire policy lock before Store lock. Snapshot calculations run inside REPEATABLE_READ and persist header/items in one transaction. No snapshot update/delete API exists.

## Precision and F05 handoff

- S/M/config inputs allow six fractional digits; enabled coefficient sum is exact decimal 1. Disabled coefficients contribute 0, with no automatic normalization.
- Per-batch expiry division uses DECIMAL128; the quantity-weighted expiry average and final weight use 18 fractional digits HALF_UP. Base and weighted item utility use six digits HALF_UP; transaction and dataset totals sum the rounded weighted item values. Snapshot saves rounding scale/mode and effective coefficients.
- F05 receives Product IDs unchanged as positive mining item IDs (`PRODUCT_ID_V1`). Utilities use a lossless six-place signed 64-bit scaled integer representation: multiply by 10^6 and require exact long conversion. Item, transaction and dataset totals outside that range are rejected. An F05 engine restricted to 32-bit integers needs an approved adapter; F03 does not silently rescale.
- Source-native item utility is used as the item total; historical prices/costs are used for business rows. No current Product price is used. Providers must supply positive quantities; missing quantities, negative utility, invalid or duplicate prepared items are rejected instead of inferred/merged.
- Source/matrix ordering is transaction ID then Product ID. SHA-256 hashes exclude snapshot ID/time and include frozen configuration/date/range and source content.

## Expiry boundaries and warnings

- Approved v4 eligibility is ACTIVE, remaining_quantity>0 and expiry_date>asOfDate. Thus expiry on the analysis date is excluded in F03. Existing F02 current-stock expiry boundary is unchanged.
- Non-expiring Product: E=0/NOT_APPLICABLE. No eligible batch: E=0/NO_SELLABLE_BATCH with a warning; historical transactions stay in the matrix. Required missing expiry: E=null/MISSING_METADATA and warning. Snapshot is rejected when the expiry criterion is enabled; disabled expiry contributes zero.
- Missing enabled manual score produces a preview warning and null utility/total; no READY snapshot can be created. No partial sum is presented as a complete total.

## Explicit dependencies and ERD differences

- Latest ERD is actually located in `docs/ERD_Diagram/` (capitalized), including Conceptual, Logical and F03 Zoom pages. Original files are preserved.
- F04 tables SALES_TRANSACTION/TRANSACTION_ITEM and F05 MINING_REQUEST do not exist in current migrations. F03 does not create their schemas. `TransactionSource` is the F04 boundary; default provider returns 503. Only explicit `dev,utility-fixture` profiles enable generated test transactions.
- V5 preserves ERD transaction_id but cannot add its FK to an absent SALES_TRANSACTION table. F04 must add it, validate source statuses/duplicate handling and supply a production provider. F05 must add MINING_REQUEST.utility_snapshot_id FK in its migration. F03 schema completion remains PARTIAL until these dependencies exist.
- Matrix adds quantity to preserve the original task contract. algorithmItemId and transaction totals are derived from immutable stored items. Snapshot items reference existing Product and snapshot IDs; service validation enforces the same Store.
- No migration rollback is run on shared data; isolated tests and atomic-write rollback are verified.

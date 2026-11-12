# F07/F08 layout and campaign schema

The current working-tree migration [V23](../../src/main/resources/db/migration/V23__f07_f08_layout_campaign.sql) adds layout, campaign, revision, placement, report, evaluation-membership, and campaign-history storage. Earlier migrations are unchanged. V23 was not present in the starting Git history for this task; deploy environments must apply it through the normal Flyway migration process before using these APIs.

## Tables and invariants

- `campaign` is Store- and utility-snapshot-scoped. It stores A/B IDs, selected layout, decision/report link, lifecycle, optimistic version, actors and lifecycle timestamps.
- `storefront_layout` is unique per campaign role and bound to the same Store/snapshot as its campaign. A and B are distinct. A is immutable in service operations; B can be edited only while DRAFT.
- `layout_placement` is Store/layout/snapshot/product-scoped, references a same-Store/snapshot recommendation when present, and has unique product and zone-position constraints.
- `storefront_layout_revision` stores immutable placement JSON and SHA-256 per layout version. Simulation report-layout rows reference the exact revision version.
- `comparison_report` is immutable evidence with source namespace, date range, dataset hash, sample count, algorithm and assumptions, simulated marker, runner and timestamp. `comparison_report_transaction` persists evaluation transaction IDs.
- `campaign_history` records status transitions and the previously active campaign for rollback.
- Withdrawal clears the selected-layout decision in the same versioned update; the report itself remains immutable and available for audit.
- Composite foreign keys preserve Store/snapshot/campaign/layout/recommendation/product boundaries. Report-layout foreign keys bind each metric row to the exact layout revision that was evaluated.
- There is no unique index that can safely encode “at most one ACTIVE campaign per Store” while retaining campaign identity. Activate and rollback therefore lock the owning Store row and perform transitions in one database transaction.
- Current recommendation eligibility is not persisted as timeless truth. Eligibility is checked live for preview and again before READY, publish, activation, decision, and rollback; invalid saved blocks remain visible and block those actions.

## Simulation data provenance

The report dataset consists of F04 `COMPLETED`, `f03_eligible=TRUE` transactions in the inclusive Store-local date range. The range must begin strictly after the snapshot's `range_end`; the service also excludes transaction IDs in `utility_matrix_item` for that snapshot. Distinct transaction IDs are the split key, not basket contents. The hash is SHA-256 over source namespace/version, Store, snapshot, date/time range, split description, and ordered transaction/item IDs and price/cost/quantity values. The evaluation IDs are stored separately to support audit. Existing product/transaction evidence is not copied into a second mining dataset.

Baseline A ranks products by the count of distinct transactions in the snapshot that contain each product, descending, with `product_id` ascending as the deterministic tie-break. The frequency ranking is separate from the weighted utility score shown by F07.

## Configuration and operations

Shared capacity is configured in `paspm.layout.max-blocks-by-zone` and `paspm.layout.max-products-by-zone`; absent entries are intentionally unbounded. Both F07 and the F09 active-layout reader use `GET /api/layouts/policy` and the same rule component. No default capacities are invented by this migration.

Apply with the project's normal Flyway startup/upgrade procedure. There is no new worker or external dependency; preview, simulation, and lifecycle requests run synchronously. See [F07/F08 API contract](../api/F07_F08_LAYOUT_CAMPAIGN_API.md) and [layout/campaign diagrams](../diagrams/README.md).

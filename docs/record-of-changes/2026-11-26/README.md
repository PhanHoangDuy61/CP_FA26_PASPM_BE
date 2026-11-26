# ROC-20261126 — Diagrams brought in line with `dev`

2026-11-26 / Asia/Ho_Chi_Minh. Branch `Khoi/docs/diagrams-update-20261126`; one record per phase. Delivered through a pull request into `dev` ([DEC-20261119-01](../../decisions/2026-11-19/DEC-20261119-01-dev-changes-only-through-pr.md)); no PR has been opened.

## Record 001 — Diagram baseline

[Record 001](01-diagram-baseline.md) lands the 36-page UML pack and the ERD V1–V26 ([DEC-20261126-01](../../decisions/2026-11-26/DEC-20261126-01-uml-erd-baseline-in-repository.md)) and ports the scheduler wording. Documentation only; Maven NOT RUN.

## Record 002 — Store management (V27) in the diagrams

[Record 002](02-store-management-v27.md) draws Store management (V27, DEC-20261123-01): four new UML pages, four changed pages and the `store` table of the ERD. Documentation only; Maven NOT RUN.

## Record 003 — F02 expiry, push day and soft delete (V28)

[Record 003](03-f02-expiry-pushday-soft-delete-v28.md) draws the F02 rules of V28 ([DEC-20261124-01](../../decisions/2026-11-24/DEC-20261124-01-batch-expiry-boundary-effective-status.md), -02, -03): a new AD10 activity page, FR and use-case labels, and `product.deleted_at/deleted_by` in the ERD. Documentation only; Maven NOT RUN.

## Record 004 — F09 Storefront (V29 + V30) in the diagrams

[Record 004](04-f09-storefront-v29-v30.md) draws the public Storefront of F09 ([DEC-20261125-04](../../decisions/2026-11-25/DEC-20261125-04-f09-public-store-follows-store-status.md)): slug-based access, `ProductCard` with availability, and the V29 product columns (V30 drops the old Store columns). Documentation only; Maven NOT RUN.

## Record 005 — EFIM-only mining and Weight v2

[Record 005](05-efim-only-and-weight-v2.md) removes HUI-Miner, compare mode and the benchmark from the UML pack ([DEC-20261116-04](../../decisions/2026-11-16/DEC-20261116-04-f05-efim-only-mining.md)) and adds the Weight v2 activity page AD11. Documentation only; Maven NOT RUN.

## Record 006 — F07/F08 per-product Layout B and priority coverage

[Record 006](06-f07-f08-per-product-layout.md) draws the product-level Layout B of F07/F08 ([DEC-20261125-01](../../decisions/2026-11-25/DEC-20261125-01-f07-product-level-layout-b.md)): AD06, its sequence and class pages and three ERD notes. Documentation only; Maven NOT RUN.

## Record 007 — F10/F11 planned contracts and SPMF boundary

[Record 007](07-f10-f11-planned-and-spmf-boundary.md) marks F10/F11 as planned ([DEC-20261125-06](../../decisions/2026-11-25/DEC-20261125-06-f10-f11-mvp-contract.md)) and draws the SPMF process boundary ([DEC-20261125-03](../../decisions/2026-11-25/DEC-20261125-03-spmf-gpl-distribution-and-process-boundary.md)). Documentation only; Maven NOT RUN.

## Record 008 — PO answers: rename, Mermaid archived, FK edges, review

[Record 008](08-po-answers-rename-archive-fk-review.md) applies the PO answers of 2026-11-26 (pack renamed, Mermaid mirrors archived, FK edges drawn) and the page-by-page review fixes. Documentation only; Maven NOT RUN.

## Record 010 — F10 Part 1 ordinary cart

Record 009 belongs to the separate black-box test cases branch. [Record 010](10-f10-cart-core.md) records the V31 migration, ordinary cart operations and verification.


## Record 011 — F10 Part 2 whole-bundle add

[Record 011](11-f10-bundle-add.md) records the F09 bundle key, versioned bundle cart endpoint, bundle events and the single-item rejection boundary tests.

## Record 012 — AD07 / R2-08.07 / R2-07.07 as the offline replay

[Record 012](12-ab-diagrams-offline-replay.md) redraws AD07 and its sequence and class pages as the synchronous offline replay (`OFFLINE_REPLAY_V4`) after DEC-20261126-03. Documentation only; Maven NOT RUN.

## Record 014 — F11 dashboard Backend

[Record 014](14-f11-dashboard-api.md) records the READY snapshot headers, successful bundle-add metrics, existing F08 report reads and verification. Record 012 (A/B diagrams) and Record 013 (TDP-01 test Store seed) belong to separate branches.

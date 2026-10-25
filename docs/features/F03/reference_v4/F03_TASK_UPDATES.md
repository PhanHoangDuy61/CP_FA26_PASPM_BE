<!-- docs-reorganization:notice -->
> **SUPERSEDED — documentation review 2026-10-26.** This approval record belongs to the original v4 linear design, not new source scoring. See [current navigation](../README.md). Original results and business-rule text below are retained; this notice is not a new execution or approval.
<!-- /docs-reorganization:notice -->

# F03 Task Updates — confirmed 2026-10-17

This supplement overrides the proposed fixed T/lambda limits in the original v4 backlog and applies to F03-01 through F03-10. Original ERD/backlog artifacts remain available.

- F03-01: reuse STORE.utility_config; add one persisted versioned system policy using a generic configuration key because no database system configuration architecture exists. Default policy [0,0.5]. Do not create F04/F05 tables; list unavailable FKs as dependencies.
- F03-02: separate System Admin policy edits, Store Admin T edits, and Merchandiser weights/selected-lambda edits. Validate current permitted policy; preserve settings owned by other roles. Bounds updates never rewrite Store selections.
- F03-03/F03-10: canonical shared scores endpoint, strict partial update, no inventory mutation.
- F03-04: approved linear formula; T read from Store config; fixed asOfDate; preserve v4 missing/non-expiring rules.
- F03-05/F03-06: historical source adapter, explicit fixtures while F04 absent; deterministic Decimal128/six-place HALF_UP output; reject unsafe/negative utilities.
- F03-07: expose effective policy and warnings with preview; reject stale lambda, no persistence.
- F03-08: freeze T, policy version and bounds, coefficients/lambda, product scores/date and matrix; revalidate current bounds atomically before writing a new snapshot. Historical snapshots never read current settings.
- F03-09: execute updated role matrix, policy changes/stale selections, T changes and immutable snapshot cases; document Swagger evidence and F04/F05 blockers honestly.

See F03_API_CONTRACT.md, F03_SWAGGER_TEST_CASES.md and F03_IMPLEMENTATION_PLAN.md for the updated implementation contract.

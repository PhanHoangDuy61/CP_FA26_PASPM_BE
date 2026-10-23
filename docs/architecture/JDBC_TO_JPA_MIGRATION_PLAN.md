# JDBC-to-JPA controlled progression — current gates

Updated 2026-10-23 (Asia/Ho_Chi_Minh). This record covers progression after Recovery PR #1; it does not authorize a broader persistence rewrite.

1. **Recovery:** PR #1 was externally merged into dev (`691371b`). Fresh local recovery verification: 254 PASS. Independent human review remains REVIEWER_PENDING; remote CI remains NOT_RUN/SPMF_INTEGRATION_CI_BLOCKED. Complete those gates and verify required policy or an explicit approved equivalent before Product progression.
2. **Product Pilot:** existing implementation is on `Khoi/Fear/jpa-product-metadata-pilot` (`12d348e`). No new integration or Product PR is performed here. The earlier detached 299-case compatibility result is historical. A later authorized integration must reconcile with current dev, preserve applied migration bytes and rerun the Pilot's real JPA/JDBC shared-transaction, rollback, persistence, concurrency and F01–F06 regression coverage. Current dev CI explicitly reports Pilot JPA tests unavailable.
3. **Product review/merge:** require actual independent review and all required CI at the proposed head. The latest instruction explicitly forbids merging in this task. Do not infer permission from a previous recovery merge or passing local tests.
4. **Category:** NOT_STARTED. Entry requires approved Product integration and its required gates, followed by the separately authorized Category plan. No Category entity/repository implementation is introduced by this task.

Original V5 database is read-only; populated acceptance uses disposable synthetic fixtures. REAL_DATA_BLOCKED remains until authentic data is available for an approved isolated rehearsal. No automatic Flyway repair, reset, source schema change or invented historical transaction backfill is permitted. Unrelated modules and Frontend are outside scope.

Evidence and remaining decisions: [review gate report](../testing/PR1_REVIEW_GATE_REPORT.md), [CI setup](../setup/BACKEND_CI.md), [migration verification](../testing/JPA_V5_TO_V10_MIGRATION_VERIFICATION.md).

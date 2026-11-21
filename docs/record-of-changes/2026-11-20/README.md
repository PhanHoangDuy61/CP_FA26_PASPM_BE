# ROC-20261120 — F09 Storefront MVP

2026-11-20 / Asia/Ho_Chi_Minh. UNRELEASED, local-only.

## Record 001 — F09 Storefront MVP

[Record 001](01-f09-storefront-mvp.md) adds opt-in public Store selection, Homepage, Product list/detail and eligible recommendation placements, plus SYSTEM_ADMIN visibility control. It adds Flyway V27 and public Product display metadata; it does not implement Related Products, cart, checkout, or other F10 behavior.

Verification is H2/local only. The targeted F09/F02/F07/F08/migration tests passed; the broader F06 recommendation integration attempt was blocked by the missing configured SPMF JAR. MySQL migration execution and full-suite verification were not run. No commit, push, merge, or deployment occurred.

# F05 scheduler and Linux image — working plan

Branch `Khoi/feat/f03-f05-admin-mining`; preserve all existing uncommitted work and unrelated diagrams. Do not commit, push, merge, deploy or touch a shared/source database.

1. Characterize existing scheduler APIs, recurrence, Store timezone, V13–V15 migrations and transactional snapshot/request linking. Keep the enable=503 guard until every acceptance condition is verified.
2. Complete schedule listing and occurrence detail, with System Admin-only authorization and pagination tests.
3. Design a persistent occurrence claim and retry/recovery model in the next Flyway version; preserve occurrence identity, Store, snapshot and pinned configuration; prevent stale owners from writing. Specify bounded catch-up, retry/backoff, monthly overflow and timezone behavior as assumptions pending PO review.
4. Implement bounded polling and deterministic reservation using existing services. Reconcile request/run terminal state; prove fresh snapshot and no duplicate logical request/output. Test multiple instances and crash points, including disable/update, queue full and policy changes.
5. Create a portable production-oriented Linux Backend Dockerfile with Java 17, Java 25, Python 3 and an externally supplied approved SPMF JAR; never commit the binary or secrets. Use init for reaping, not as the parent-death mechanism. Validate image as non-root and test the actual image with two Backend containers and disposable MySQL.
6. Re-run F03–F06, authorization, MySQL/Flyway and full Maven verify. Record exact results and skipped cases. Only remove 503 if all required safety tests pass; otherwise retain it and report NOT_READY.
7. Reconcile OpenAPI, F05 API/feature/handoff, setup and reports only to demonstrated behavior. Preserve Windows/macOS clean-machine verification limits.

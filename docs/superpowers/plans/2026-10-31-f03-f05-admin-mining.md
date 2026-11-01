# F03/F05 Administrative Mining Implementation Plan

**Goal:** Align F03 batch ordering and F05 administration with the specified responsibilities while preserving frozen history and Store isolation.

**Architecture:** Keep Store reads scoped; add explicit System Admin operations with validated Store selection and versioned global mining policy. Scheduled executions must create fresh snapshots for explicit Store/date windows and persist deterministic schedule occurrences before invoking the existing asynchronous worker. Retain all historical records.

**Tech Stack:** Java 17, Spring Boot/Security, JDBC, Flyway, JUnit, MySQL/H2.

## Steps

1. Add failing FEFO tie and null-constraint tests, then carry `received_at` through repository and sort by expiry, received timestamp, batch ID. Preserve serialized historical evidence.
2. Define auditable versioned admin configuration and server-validated scoped Store execution. Add role, isolation, validation, idempotency and frozen-provenance tests before implementing a new migration and API; close legacy privilege bypass without changing F06 consumption.
3. Design weekly/monthly schedules with validated timezone, durable occurrence uniqueness, recovery and explicit fresh snapshot range. Test concurrency, queue failure, restart and immutability before enabling execution. No pretend-retry of an outdated frozen snapshot.
4. Run isolated tests including F03, F04, F05, F06 and migration checks. Verify without touching existing configured database.
5. Update only current API/handoff/RBAC documentation for implemented and verified behavior; preserve historical sources, generate OpenAPI from runtime when available and report incomplete items explicitly.

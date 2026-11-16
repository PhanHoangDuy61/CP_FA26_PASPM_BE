# F05 Dense Acceptance and Resource-Guard Verification Plan

> **Status:** COMPLETED for local acceptance on 2026-11-17. Actual outcomes and limitations are recorded in [Record 013](../../record-of-changes/2026-11-17/13-f05-dense-acceptance.md).

**Goal:** Close the accepted Record 012 limitations with a dense, explicitly synthetic F04→F03→F05→F06 run, pattern-cap and output-cap evidence, JPA-clone execution, and a clean full verifier.

**Architecture:** Extend the existing opt-in MySQL end-to-end fixture so the same application path can generate a FoodMart-shaped synthetic transaction matrix below the existing 20,000-row bound. Preserve the 1,600-product and 2,000-pattern caps. Test output-limit behavior through the real engine boundary; only add the smallest bounded-write change if measurements prove the current overshoot is not independent of input size. Run the existing guarded JPA-clone suite only against a newly created disposable schema.

**Tech Stack:** Java 17 / Spring Boot 3.5.7, Maven, MySQL 8.4 disposable Docker container, Java 25 SPMF 2.67a with pinned SHA-256, JUnit 5, Flyway.

## Global Constraints

- Do not use network access or fetch.
- Base is `origin/dev` at `1d1aeb3b8081117b718b6a7b07bc4a655cee74d1`.
- Work only in `<isolated-worktree>` on `Khoi/test/f05-foodmart-dense-acceptance`.
- Do not change the main `BE` checkout, Frontend, or old worktree.
- Do not raise product, row, pattern, heap, timeout, or output limits.
- Use disposable MySQL only; preserve all evidence outside Git.
- No push, merge or deployment. The initial handoff proposed separate implementation/test and docs commits. A later owner instruction authorized those two local commits after the realistic-utility, Windows fallback and opt-in multi-JVM follow-up.

## Tasks

1. **Reconcile prerequisites and evidence.** Read Records 004, 006, 010 and 012; capture exact Java/SPMF, Docker/MySQL and JPA-clone invocation requirements; verify the clone configuration can target only a new disposable schema.
2. **Dense synthetic lifecycle.** Add a parameterized or dedicated opt-in test fixture with 1,559 products, approximately 4,100 imported orders, approximately 18,000 transaction/matrix rows, and average approximately 4.4 items/order. Run the real F04 import, F03 snapshot, F05 EFIM, persisted result, and F06 evidence reader. Record conversion metadata, max transaction utility and total utility after scale-6/GCD conversion, actual EFIM duration, pattern count and bytes.
3. **Pattern-cap contract.** Establish the actual 2,000-pattern outcome at the engine boundary (truncation/flag or failure), assert that an incomplete run cannot be consumed by F06, and document the observed status and persistence behavior.
4. **JPA clone suite.** Provision a fresh loopback disposable MySQL schema and invoke all five `PilotCloneApiIntegrationTest` methods using the recorded clone opt-in, properties file, mutation consent, and test flags. If a named prerequisite is absent, retain the safety gate and report that exact prerequisite.
5. **Output cap.** Use a deterministic output writer/process harness with increasing output volumes to quantify bytes past 4 MiB. If the overshoot grows with producer burst/input size, add the smallest bounded-write protection and a regression test; keep the 4 MiB cap unchanged. **Resolution:** POSIX kernel file-size limit (Linux/macOS) stops writes exactly at the cap and EFIM reports `OUTPUT_LIMIT`.
6. **Final verification and docs.** Run one fresh `scripts/verify-option-b-macos.sh --run` to completion with disposable MySQL, Java 17 backend, Java 25 SPMF and recorded flags. Keep per-stage evidence outside Git. Add Record 013 for 2026-11-17 linking Record 012 and update the benchmark report with measured outcomes and any NOT RUN reason. Run documentation link and diff checks.
7. **Handoff.** Report measured counts, tests, environmental limitations, files changed, and commit hashes. Do not publish.

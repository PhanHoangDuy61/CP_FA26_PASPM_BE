# F06 Recommendation Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development for independent expiry/evidence tasks and review; execute tightly coupled persistence/API steps inline. Track actual tests and review in this file.

**Goal:** Reviewable four-type recommendations from completed F05 runs, independent expiry alerts, Merchandiser-only review and real frontend API handoff.
**Architecture:** JDBC feature module; read-only frozen evidence adapter; shared expiry calculator; Store-scoped transactional generation and review; immutable source/policy evidence separated from current eligibility.
**Tech Stack:** Java17, SpringBoot3.5.7, JdbcTemplate, Flyway, SpringSecurity, springdoc, JUnit/MockMvc, isolated MySQL8.4.

## Global constraints

- Approved 2026-10-20: Bundle maximum4, FBT count>=2 AND ratio>=0.05, Cross-sell confidence>=0.50, minimum sellable quantity1. Store Admin config writes; Merchandiser generate/approve/reject; both read; SystemAdmin/Customer no management.
- Version policy and freeze values per candidate. Rank eligibility, utility DESC in compatible snapshot scope, support DESC, canonical key/type/anchor. No weighted business formula.
- Read F03/F05 only. No mining/calculation changes, inventory writes, publishing, campaigns, prices/discounts, Frontend changes or destructive database operations.
- Current date in Asia/Ho_Chi_Minh via UtilityTimeZone; expiry today excluded. Reuse ExpiryScoreCalculator.
- Preserve local application.yml and unrelated dirty files. New branch from3f6faaa; do not push/merge without a later request.

### Task 1: Independent expiry / live eligibility (F06-06)
Files: recommendation/RecommendationExpiryService.java, RecommendationExpiryController.java, ProductEligibilityService.java; tests/recommendation/RecommendationExpiryIntegrationTest.java.
- [x] Write HTTP tests for no mining, threshold0/3/7, D=T/T+1/0, expired/blocked/empty/non-expiring/missing, checkedAt and foreign Product404.
- [x] Run `mvn -Dtest=RecommendationExpiryIntegrationTest test`; verify absent route404 before implementation.
- [x] Implement current-only batch alerts using UtilityProductRepository + ExpiryScoreCalculator; keep warnings and paginated FEFO results. Live eligibility returns state/products/checkedAt/date/timezone and checks active Product, required-expiry metadata and sellable batch sums.
- [x] Run focused tests; record review and results.

### Task 2: Frozen source/evidence (F06-02/04)
Files: recommendation/FrozenEvidenceReader.java, TransactionEvidence.java; tests/recommendation/TransactionEvidenceTest.java, FrozenEvidenceReaderTest.java.
Interfaces: Source contains RunView, SnapshotView, minUtil mode/value/effective value, input hash, pattern list and TransactionEvidence; measure(group,anchor) returns counts/BigDecimal ratios and explicit availability.
- [x] Test six-transaction fixture AB,AB,A,B,B,C, directional2/3 versus1/2, repeated item presence, empty/zero denominator/mapping/hash mismatch.
- [x] Verify failing tests first, then implement Store-scoped read-only adapter using actual F05 frozen_input and immutable matrix membership; no live sales.
- [x] Run focused tests and review evidence/provenance boundaries.

### Task 3: Schema, config, rule generation and API (F06-01/03/05/07/09)
Files: V10__f06_recommendations.sql; recommendation/RecommendationContracts.java, RecommendationPolicyService.java, RecommendationRules.java, RecommendationRepository.java, RecommendationService.java, RecommendationController.java; tests/recommendation/RecommendationIntegrationTest.java.
- [x] Write generation API tests before source: all four types, completed/failed/foreign/missing/empty runs, singletons, null support, frozen evidence and exact boundaries, deterministic ranking, config validation and RBAC.
- [x] Run focused tests; expect404 for new API before implementation.
- [x] Create scoped recommendation/config/item/source/history schema with non-destructive V10 and composite FKs/uniqueness. Adapt old migration regression assertions from hardcoded latest9 to current latest10 without changing old migrations.
- [x] Implement approved default policy1; Store-lock config CAS and candidate generation to serialize dedup; policy copies are immutable. Cross-sell one anchor versus remaining group; FBT requires positive co-occurrence and approved exact count/ratio; Banner permits singleton; Bundle2..max. Return explicit skipped-rule reasons.
- [x] Preserve compatible same-snapshot/input/threshold algorithm provenance via recommendation_source. Separate different snapshots/types/anchors/policy versions. Generation repeated does not reopen resolved candidates.
- [x] List chronology generally; snapshot-scoped ranking is recalculated using live eligibility with stable total-order ties, before pagination. Do not compare different input/threshold groups as a common score.
- [x] Run focused suite, inspect DB persistence and source immutability.

### Task 4: Review/audit/current consumer contract (F06-08)
Files: recommendation/RecommendationReviewService.java plus controller/repository/contracts; expanded RecommendationIntegrationTest.
- [x] Tests before code: StoreAdmin/customer/system403, foreign404, expectedVersion409, concurrent approvals single winner/history, rejected cannot approve, inventory changes before/after approval.
- [x] Implement atomic PENDING_REVIEW->APPROVED/REJECTED with reason, actor,time,version/history and current eligibility gate; expose approved current consumption check with no activation side effects.
- [x] Run focused tests, assert inventory/F03/F05 unchanged and review history durable.

### Task 5: Verification, ERD and handoff (F06-10/11)
Files: latest editable ERD conceptual/logical pages, docs/api/F06_RECOMMENDATION_API.md, openapi.json, API_CHANGELOG.md, docs/frontend_handoff/F06_HANDOFF.md; requirements/F06_RECOMMENDATION_RULES_v1_1.md; F06_VERIFICATION.md/evidence.
- [x] Test V1->V10 and V9->V10 on isolated MySQL; preserve V9 data. Run F06 suite on MySQL and real F03->F05->F06 HTTP path using Java17/backend and Java25/SPMF.
- [x] Run full clean verify, exact existing147 plus F06 tests; investigate any failures before claiming success.
- [x] Start backend on isolated database, export runtime OpenAPI, execute Swagger UI operations with redacted evidence when browser available. Report automatic HTTP and UI separately.
- [x] Update ERD names/types/FKs to actual V10; verify XML schema extraction. Document real DTOs, roles, payloads, precision, errors, integration order, demo FE differences and limitations.
- [x] Independent final spec/security/concurrency review; fix meaningful findings and rerun covering tests; final Vietnamese task matrix and exact evidence.

## Execution ledger

- Baseline: Java17 verify147 PASS, MySQL8.4 and real SPMF; evidence docs/requirements/evidence/f06-readiness.json.
- Business policy package approved by user, no remaining threshold decision.

- Complete: primitive19+expiry5 focused green; core expanded38 green; final Java17/MySQL clean verify210/210;27 Swagger UI cases, standalone final schema export; F06 ERD and frontend handoff delivered.
- Review: primitive pattern-count P2 fixed with5 regressions; core approve. Concurrency/schema discrepancies reproduced and retested.
- Metadata final: three request schemas now additionalProperties=false; final full run21:20:40+07:00 PASS210/210. No business policy decisions remain.

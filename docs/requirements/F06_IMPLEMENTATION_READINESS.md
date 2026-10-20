# F06 — Implementation readiness and proposed technical design

Date: 2026-10-20; timezone Asia/Ho_Chi_Minh. Baseline: dev, 3f6faaa.
Status: **HISTORICAL READINESS AUDIT, preserved from before implementation**. Current implementation and verification: [F06_IMPLEMENTATION_SUMMARY.md](F06_IMPLEMENTATION_SUMMARY.md), [F06_VERIFICATION.md](F06_VERIFICATION.md). Earlier pending/NOT IMPLEMENTED entries below describe the audit time, not the completed feature.
This is a repository audit and proposed implementation design, not evidence that F06 has been implemented.

## Source authority

- User-supplied task: “Implement F06 – Recommendation Engine (Approved Business Plan v1.1)”, attachment e88f88b5-f055-47f4-a315-f4fc629a32e1. Its four business decisions are approved.
- No separate detailed F06 v1.1 plan, F06 issue set, or previously implemented recommendation module was found in the inspected repository. The execution-task mapping below is a proposed mapping, not a quotation from an unavailable plan.
- Latest editable ERD: docs/ERD_Diagram/PASPM_Complete_ERD_F03_Three_Weights_v4.drawio; conceptual and logical pages titled Final Weights v1.2 + F05 P0. F02 ERD was also inspected and is older. No edits to either ERD yet.
- Actual sources: src/main/java/vn/edu/fpt/paspm/{security,catalog,utility,mining,transaction}; Flyway V1–V9; F05_F06_HANDOFF.md; F03_V12_F05_F06_INTEGRATION.md; F05_DOCUMENT_COMPLIANCE_AUDIT.md; F05_P0_DEPLOYMENT_FIX_REPORT.md; API/handoff conventions.
- No AGENTS.md found in the repository or inspected parent paths.
- Existing dirty application.yml and unrelated untracked F03/requirements documents are preserved. No credentials were copied to this report.

## Gap analysis

| Requirement | Current implementation | Required change | Risk | Evidence |
|---|---|---|---|---|
| Completed mining source | Store-scoped MiningRepository.run/patterns; nullable supportCount | Read-only F06 source adapter; reject non-COMPLETED runs and inconsistent provenance | Wrong dataset or cross-Store source | MiningRepository.java:40–65; MiningContracts.RunView/PatternView |
| Frozen transaction evidence | mining_request.frozen_input retains exact SPMF text, dense-to-Product mapping, hash and transactionCount; utility_matrix_item retains immutable membership | Verify source identity and input hash; derive sets of real Product IDs per frozen transaction; calculate counts/ratios without live sales | Fabricated confidence if nullable support is treated as zero | MiningInputBuilder.Dataset/build; UtilitySnapshotRepository.saveItems/page; V5/V9 |
| Recommendation persistence | RECOMMENDATION only in conceptual/logical ERD; no physical table or Java module | V10 and recommendation module using the existing logical entity name; scoped products, evidence/provenance, review history and concurrency version | ERD presently omits Store, BUNDLE type, evidence and version | Latest ERD RECOMMENDATION table; complete V1–V9 migration inventory |
| Four types | No backend rules | Explicit BUNDLE/CROSS_SELL/FBT/BANNER rule components, each with evidence validation | Thresholds/maximum bundle size not approved | User task parts D/G; no rule policy in existing backend |
| Live sellability | F02 ACTIVE/BLOCKED batches, expiry <= local date excluded; Product activation checked separately | Reuse date/batch rules; validate active Product, stock and required-expiry metadata | F02 summary allows null expiry without checking requiresExpiry; unsafe to trust summary alone | InventorySummaryRepository; UtilityProductRepository; ExpiryScoreCalculator |
| Independent expiry push | GET /api/utility/expiry-push-batches already uses binary Product T policy; no mining dependency | Reuse calculator/read services; current-only F06 read view with checkedAt, warnings/status and actual batch IDs | Historical asOfDate cannot authorize sale; missing metadata must remain explicit | ExpiryPushService/Controller; ExpiryBinaryRulesTest; WeightRulesV12IntegrationTest |
| Deterministic ranking | F05 raw patterns ordered by utility within run; no F06 ranking | Versioned, explained ranking without invented weighted formula | Comparing incompatible snapshots | MiningRepository.patterns; user task part G |
| Deduplication | F05 uniqueness is per run only | F06 unique scoped candidate identity + all run/pattern provenance; idempotent generation | Losing second-algorithm provenance or merging distinct snapshots | V9 uq_high_utility_pattern_key; F05 handoff |
| Review/RBAC | F01 roles and StoreScope exist; no F06 review lifecycle | MERCHANDISER only approve/reject; versioned atomic transition/history; live validation on approval and downstream read | StoreScope alone permits System Admin; controller/service role guard also required | SecurityConfig; CatalogAccess; StoreScope; approved task RBAC |
| Config ownership | STORE.utility_config is F03-owned; system_configuration holds lambda policy only | Separate Store-owned F06 rule config, only if ownership approved; immutable policy copy/version on candidate | Overwriting F03 config; unapproved write permission | V1/V4; UtilitySettingsRepository |
| Downstream | No implemented F07–F10 backend modules found | Approved business contract with current eligibility; no activation side effects | Approval mistaken for publication or guaranteed stock | Java package/migration inventory; ERD layout/campaign concepts |
| Frontend | FE and FE_Kien contain recommendation views; inspected FE view uses demo state and allows Store Admin review | Backend handoff with Merchandiser-only review; no Frontend implementation changes in this task | Demo metrics/permissions are not real API contracts | FE/src/pages/recommendations/RecommendationPages.tsx, useDemo/canReview |
| Deployment | Prior P0 fixes quote V7 row_number and validate external Java25 SPMF; backend targets Java17 | Recheck baseline on isolated MySQL; V10 fresh/upgrade verification after implementation | Existing DB old V7 checksum still needs separate approved recovery | F05_P0_DEPLOYMENT_FIX_REPORT; FlywayRecoveryTest; SpmfRuntimeTest |

## Policy decision register (approved after audit)

The following values are **proposals awaiting explicit approval**, not approved requirements or production defaults.

| Parameter | Approved existing value | Proposal submitted to user | Dependent component |
|---|---|---|---|
| FBT minimum support | None found | supportCount >= 2 AND supportRatio >= 0.05 | FBT qualification |
| Cross-sell minimum confidence | None found | confidence >= 0.50; preserve direction-specific denominator | CROSS_SELL qualification |
| Maximum Bundle size | None found | 4 Products; minimum 2 from approved task | BUNDLE qualification |
| Ranking weights/policy | Rule based approved; ordering absent | No weights. eligibility ELIGIBLE / NEEDS_RECHECK / INELIGIBLE, then utility DESC within one snapshot, supportCount DESC, canonical Product key ASC | Final rank contract |
| Minimum sellable stock | None found | At least 1 eligible unit of each Product | Candidate eligibility and approval |
| Store Admin rule configuration | Read approved; configuration ownership unconfirmed | Store Admin edits own Store rule policy; Merchandiser reads and applies it; neither gains extra review rights | Config PUT API |
| Research algorithm scope | HUI-Miner and EFIM approved and implemented | Preserve both. No newer supplied authoritative research decision was found that changes their scope | No change to F05 algorithms |

If the user supplies different policies, update this table and the design before implementing dependent rules. Do not activate an unapproved fallback.

## F05 → F06 read-only data contract

1. Resolve authenticated current Store through CatalogAccess/StoreScope with explicit F06 role checks; client storeId is not an authority.
2. Resolve MiningRepository.run(store, runId): absent/foreign -> 404; PENDING/RUNNING/FAILED/TIMED_OUT -> 409. Only COMPLETED produces candidates. Empty pattern set is a valid empty generation result.
3. Join mining_request by both request_id and store_id, resolve utility_snapshot_id and persisted utility_config_snapshot, input_snapshot_hash and frozen_input. Validate snapshot identity, original matrix hash, dataset text SHA-256 and transactionCount. Never rebuild/scalecalculate input or run algorithms in F06.
4. Read high_utility_pattern and pattern_item in the same Store and validate canonical Product IDs, pattern size, no duplicate/missing mappings, and Product membership in the frozen dataset. Keep source algorithm, library/conversion version, snapshotId, source/matrix/input hashes, minUtilSpmf and original request threshold/effective utility metadata. All F03/F05 data are read only.
5. Parse the frozen dataset's first colon-delimited section into a set of real Product IDs for each transaction using Dataset.products. Keep zero-utility items: presence is not utility. Counts use transactions, never quantities or repeated item rows. Compare with the immutable source membership where needed to detect mismatch; never use current transaction_item as historical evidence.
6. support(X)=count of frozen transaction sets containing X; supportRatio=support/transactionCount; confidence(anchor -> targets)=support(anchor union targets)/support(anchor). Use exact integer counts and BigDecimal with documented precision for JSON. Compare policy thresholds by exact cross-multiplication so display rounding cannot change eligibility. Missing membership/zero denominator yields explicit unavailable evidence, never a fabricated zero-confidence assertion.
7. Fixture T1=AB,T2=AB,T3=A,T4=B,T5=B,T6=C must give A=3,B=4,AB=2,A->B=2/3,B->A=1/2,AB ratio=1/3. Frozen supportCount remains untouched/null in F05.

## Proposed implementation approaches

1. **Recommended: JDBC feature module plus small explicit rule classes.** Fits the current modular monolith; one read-only mining/evidence adapter, one live eligibility adapter, transactional recommendation persistence and dedicated review service. Minimal dependencies, easy provenance/audit tests.
2. One large recommendation service with embedded SQL/rules: fewer files but mixes evidence, concurrency, ranking and authorization; harder to test safely.
3. Heavy rules framework or asynchronous recommendation service: adds lifecycle/infrastructure complexity without an approved requirement; unnecessary for this scope.

## Proposed technical design for review

- Package recommendation: RecommendationController/Contracts, RecommendationService, RecommendationRepository, MiningEvidenceReader, TransactionEvidence, ProductEligibilityService, RecommendationRules/RankingPolicy, RecommendationReviewService and ExpiryPush query adapter. Naming may be refined while preserving these responsibilities.
- Generation takes a completed run and requested types, validates frozen evidence, creates full-group candidates rather than inventing arbitrary subsets, checks live products, deduplicates, ranks, explains and persists. CROSS_SELL preserves a single anchor and directional targets. FBT is generated only with approved support policy and valid co-occurrence. BANNER is a reviewable group suggestion, not an image/layout. BUNDLE has no price/discount behavior.
- Candidate identity includes Store, snapshot identity/input hash, rule-policy version, type, canonical sorted Product group and anchor. Equivalent HUI-Miner/EFIM evidence may share a candidate only within a compatible same snapshot/input/threshold context; preserve every source run/pattern separately. Different snapshots or changed policy versions remain separate.
- Persist review status independently of generation-time eligibility and last inventory check. Current read/approval/approved-consumer checks refresh live eligibility without rewriting frozen evidence. Missing required expiry metadata -> NEEDS_RECHECK; inactive/out-of-stock -> INELIGIBLE. No reservation or guarantee of future stock is implied.
- PENDING_REVIEW -> APPROVED or REJECTED only. Merchandiser only, own Store, expectedVersion required, review reason recorded, atomic update plus review-history insert. Approval rejects current ineligible/uncertain products. No transitions out of resolved states without a later approved requirement. No downstream side effects.
- Independent expiry read always uses today's configured Store-local date and shared ExpiryScoreCalculator. No F05 requirement, no artificial Pattern, no Store-window fallback. Return actual batch alerts plus checkedAt/timezone/warnings; test excluded batches, boundaries and zero patterns.
- Proposed V10: create recommendation (reuse ERD name), recommendation_item, recommendation_source, recommendation_review_history, and Store-scoped recommendation_rule_config if config ownership is approved. Use Store composite foreign keys, candidate identity uniqueness, positive quantities/version, constrained types/review states, JSON frozen evidence/policy and audit fields. Existing conceptual BUNDLE is a downstream executable bundle, so candidate type BUNDLE does not automatically create a BUNDLE row. Existing V1–V9 remain unchanged.
- Update latest conceptual/logical ERD to add BUNDLE to RECOMMENDATION.type and represent actual new schema/relationships; preserve existing F03/F05 entities and diagrams. Add a focused F06 logical page if necessary for readability.
- Suggested F06 routes are not existing endpoints yet: POST /api/recommendations/generate; GET /api/recommendations; GET /api/recommendations/{id}; POST /api/recommendations/{id}/approve and /reject; GET /api/recommendations/expiry-push; GET/PUT /api/recommendations/config only with approved ownership. Existing F03 /api/utility/expiry-push-batches stays compatible. Actual contract/handoff/OpenAPI will be generated after implementation, not presented as implemented now.
- List requires a snapshot/source scope for numerical ranking; never rank unrelated snapshots on raw utility. General history lists use chronology/identity. Tie order must include canonical Product key, type and anchor to make it total and reproducible.
- Frontend deliverable is integration documentation against real APIs; existing views are demo implementations. Do not modify FE/FE_Kien or claim completed UI integration.

## Proposed execution-task mapping and verification

No separate F06-01..11 issue definitions are present; this mapping follows the stages in the user's task.

| Task | Intended increment | Required verification | Current status |
|---|---|---|---|
| F06-01 | Schema/migration and editable ERD | Fresh MySQL V1–V10; V9->V10 history/data retention; FKs/uniqueness | NOT IMPLEMENTED |
| F06-02 | Read-only completed-run/frozen evidence contract | Missing/foreign/running/failed/empty/malformed source | DESIGN |
| F06-03 | Bundle rule | Size, mappings, stock, no prices/publication | POLICY PENDING |
| F06-04 | Directional Cross-sell | Exact six-transaction fixture, null support, missing evidence | POLICY PENDING |
| F06-05 | FBT and Banner rules | Evidence thresholds/types; no invented frequency or assets | POLICY PENDING |
| F06-06 | Independent expiry/current eligibility | T=0/3/7, D=T/T+1/0, missing/blocked/expired/empty, no mining/zero patterns, Store isolation | DESIGN; existing shared primitives available |
| F06-07 | Ranking/explanations/deduplication | Stable order, policy version, both algorithm sources, different snapshots | POLICY PENDING |
| F06-08 | Review lifecycle/audit/concurrency | Merchandiser-only, stale/ineligible, atomic conflict/history, no side effects | DESIGN |
| F06-09 | APIs/RBAC/config | 400/401/403/404/409, forged Store, pagination, policy version | CONFIG OWNERSHIP PENDING |
| F06-10 | Verification | Java17 Maven, fresh/upgrade MySQL, F03/F04/F05 regression, HTTP/OpenAPI, actual Swagger UI when available | BASELINE 147 PASS; F06 NOT RUN |
| F06-11 | API/FE handoff | Runtime OpenAPI export, real DTO/examples, RBAC differences, API changelog | NOT IMPLEMENTED |

Tests will be written before source for each approved increment. Runtime verification and report must distinguish automatic API tests, interactive Swagger UI, and Frontend integration. Baseline results are recorded separately after execution. No F06 endpoint or test is claimed PASS in this readiness report.

## Executed readiness verification

2026-10-20 14:17:30 +07:00: **Maven verify BUILD SUCCESS; 147 tests, 0 failures, 0 errors, 0 skipped; 45.441 seconds**. Backend Java17.0.20.1, real approved external SPMF with Java25.0.1, isolated fresh local MySQL8.4.11 schemas plus H2 regression suites. Existing migrations V1–V9 and F03/F04/F05 tests executed; no F06 schema or API exists yet, so these are baseline results only.

The first baseline invocation failed with 24 errors because the filesystem sandbox denied local MySQL socket connections (Operation not permitted). The unchanged verification was rerun with approved local connection permissions and passed. No application source or database recovery/repair was needed. Existing/shared databases were neither reset nor deleted.

See [sanitized evidence and all executed test method names](evidence/f06-readiness.json). Interactive F06 Swagger, F06 integration and Frontend integration are NOT RUN. Do not reuse the baseline PASS counts as F06 acceptance results.

Review status: user was asked to approve or replace the concrete policy proposal. Dependent F06 rules remain paused under the task's stop condition for missing approved thresholds. No application source, migration, Frontend, existing OpenAPI or historical ERD has been edited during readiness. Two new Markdown reports and the evidence JSON were added; preexisting working-tree changes remain untouched.

## Approval — 2026-10-20

User explicitly approved the submitted policy package: Bundle maximum4; FBT count>=2 AND ratio>=0.05; directional Cross-sell confidence>=0.50; minimum sellable quantity1; eligibility/utility/support/canonical ordering; own-Store STORE_ADMIN config writes and MERCHANDISER-only generation/review. Earlier pending labels describe the audit before this approval and are superseded. The JDBC feature-module design is used to implement the already approved business scope. No open business threshold remains.

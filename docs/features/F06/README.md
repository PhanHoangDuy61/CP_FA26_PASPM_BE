# F06 — Recommendation Engine

> Last verified Backend: `dev` @ `11973a0` (merge of PR #11; source tree identical to `a4f1115`, whose code passed the full verification script on `45dbd80`). This documentation task ran no new tests.

## Current status — branch `Khoi/feat/f05-progress-f03-weight-v2` (2026-11-15, not merged)

Code paths are relative to `src/main/java/vn/edu/fpt/paspm/` on `c8c837a` (code identical to `e38b55f`).

| Item | Behaviour | Evidence |
|---|---|---|
| Expiry push | `GET /api/recommendations/expiry-push`: a sellable, unexpired batch is due when its push day ≤ Store-local today; batches without a push day only warn | `recommendation/RecommendationExpiryService.java:43`; `utility/PushDayRule.java:23` |
| Eligibility record | New and current eligibility is `EligibilityV2` (`scoringVersion`, no `expiryPushDays`); `expiryPriorityScore` = current U_expiry | `recommendation/ProductEligibilityService.java:44`, `:56` |
| Fingerprint | W2 version, date, minimum quantity, product status/requiresExpiry, each batch incl. push day | `recommendation/ProductEligibilityService.java:28`, `:39` |
| One-time `inventoryChanged` | Frozen vs current fingerprint compare; the format changed, so every pre-W2 recommendation shows `true` once (accepted by the owner) | `recommendation/RecommendationService.java:81`; `recommendation/RecommendationContracts.java:59` |
| Old eligibility JSON | No `scoringVersion` → legacy `RecEligibility`, read byte-identical; `W2_EXPIRY_SEASON_V1` → `EligibilityV2`; other → rejected | `recommendation/RecommendationJsonVersions.java:21-22` |
| Season ended | A season frozen in the recommendation's snapshot with `end_date` before today adds an `explanation` line | `recommendation/RecommendationRepository.java:42`; `recommendation/RecommendationService.java:80` |
| No automatic re-run | No code path in `recommendation/` triggers mining or generation; generation is only `POST /api/recommendations/generate` | `recommendation/RecommendationController.java:20` |

**Superseded 2026-11-15:** the expiry-push rule `0 < daysRemaining ≤ Product.expiryPushDays` and fingerprints containing `expiryPushDays` (§4 below) are legacy.

**Verification:** final branch gate 645 tests, 640 PASS, 5 SKIP ([record 004](../../record-of-changes/2026-11-15/04-f03-weight-v2-expiry-season.md)); `RecommendationIntegrationTest`, `RecommendationExpiryIntegrationTest` and MySQL subclasses adapted to push days.

**Known gaps:** eligibility computes U_expiry with one sales aggregate per product (`utility/UtilityProductRepository.java:34`), not measured for large lists; the season warning was text-only at the 2026-11-15 baseline; the 2026-11-16 follow-up adds structured `seasonEnded` / `endedSeasons` while retaining the text.

## 1. Purpose and scope
Turns a **COMPLETED own-Store F05 mining run** into persisted, reviewable recommendation candidates (`BUNDLE`, `CROSS_SELL`, `FBT`, `BANNER`). Candidates carry frozen F03/F05 evidence and are rechecked against live F02 inventory. Approving a candidate does **not** publish a layout, activate a campaign or reserve stock. F06 never runs SPMF.

## 2. Actors and effective RBAC
| Role | Capability |
|---|---|
| `MERCHANDISER` | generate, approve, reject (own Store); read |
| `STORE_ADMIN` | read; update the Store's rule policy (`PUT /api/recommendations/config`, `expectedVersion`) |
| `SYSTEM_ADMIN`, `END_CUSTOMER` | none (403) |

Foreign ids answer 404. Matrix: [RBAC matrix](../../api/F01_F02_RBAC_MATRIX.md).

## 3. Implemented functionality (8 operations)
`POST /generate`, `GET /api/recommendations` (filters `miningRunId`, `snapshotId`, `type`, `reviewStatus`; page 0, size 1..100), `GET /{id}`, `POST /{id}/approve`, `POST /{id}/reject`, `GET`/`PUT /config`, and the independent `GET /api/recommendations/expiry-push` (current Store-local date only; `asOfDate` is rejected with 400).

## 4. Business rules
- Rules `F06_RULES_V1_1` (approved 2026-10-20). Default policy version 1: max bundle size 4 (2..256), minimum support count 2 and ratio 0.05 for FBT, cross-sell confidence ≥ 0.50, minimum sellable quantity 1; the first update creates version 2. Existing candidates keep the policy captured at generation.
- Generation is idempotent and deterministic; equivalent HUI-Miner and EFIM evidence can reuse a candidate; incomplete or invalid evidence, or exceeding the candidate cap, gives 409 with no partial rows (rollback tested).
- Review: `PENDING_REVIEW → APPROVED | REJECTED` only, Merchandiser only, `reason` required (≤ 1000 characters), `expectedVersion` optimistic lock, history kept; live eligibility is rechecked before approval, rejection is allowed even when ineligible.
- Live eligibility: Product ACTIVE, ACTIVE batches with remaining quantity ≥ the captured minimum, and an expiry strictly after today for expiry-required products. `inventoryChanged` compares fingerprints, not only quantity (W2 fingerprint since 2026-11-15, see status table).
- The expiry-push operation needs no mining run. **Legacy (until 2026-11-15):** `0 < daysRemaining ≤ Product.expiryPushDays`. **Current:** batch push day ≤ today.
- An individual COMPLETED run with valid evidence is accepted even if the request's overall comparison FAILED (policy recorded as under review in the AD05 diagram note).

## 5. Main workflows
Mining run COMPLETED (F05) → `generate` → review list/detail with current eligibility → approve or reject → downstream consumers must re-check eligibility before use.

## 6. API documentation
[F06 API contract](../../api/F06_RECOMMENDATION_API.md) · [Frontend handoff](../../frontend_handoff/F06_HANDOFF.md) · [approved rules v1.1](../../requirements/F06_RECOMMENDATION_RULES_v1_1.md) · [developer guide](../../requirements/F06_DEVELOPER_GUIDE.md) · [runtime OpenAPI](../../api/openapi.json).

## 7. Entities and migrations
V10: `recommendation_rule_config`, `recommendation`, `recommendation_item`, `recommendation_source`, `recommendation_review_history`.

## 8. Dependencies
F01 (roles), F02 (live inventory, batch push days), F03 (frozen snapshot evidence, U_expiry calculator, snapshot seasons), F05 (completed runs and patterns).

F01 session reconciliation (2026-11-12, dev V20 retained + additive V21): generation/review/config APIs retain existing role and Store authorization. END_CUSTOMER login does not grant F06 access; a visible demo storefront is not proof of a public recommendation API. Rule policy, frozen evidence and live eligibility are unchanged. See [shared session impact and evidence](../F01/F01_SESSION_MANAGEMENT.md#5-tác-động-tới-feature-khác).

## 9. Tests and evidence
`RecommendationIntegrationTest` (real SPMF: configuration → snapshot → manual request → run → generation, H2) and its MySQL subclass, `RecommendationHardeningIntegrationTest`, `RecommendationExpiryIntegrationTest`, `RecommendationRulesTest`, `FrozenEvidenceReaderTest`, `TransactionEvidenceTest`, `RecommendationMigrationTest` — inside the 23/23 script run (Linux Docker `RecommendationMySqlIntegrationTest` 16/16). Dated reports: [hardening](F06_HARDENING_REPORT.md), [implementation summary](F06_IMPLEMENTATION_SUMMARY.md), [performance](../../testing/F04_F06_PERFORMANCE_REPORT.md), [verification](../../testing/reports/F06_VERIFICATION.md).

## 10. Known limitations
No layout publication, reservation, campaign or scheduling workflow; candidate generation is capped and a ranked list scope above 10,000 candidates returns 409 (narrow the filters); relation queries have documented workload limits ([performance report](../../testing/F04_F06_PERFORMANCE_REPORT.md)); it depends on F05 availability for new runs, while reads and expiry-push work without SPMF.

## 11. Pending decisions
None for F06. Whether a comparison-FAILED request may feed generation is recorded as "policy under review" in the diagrams and rules text; no approval changes it.

## 12. Related diagrams
[Diagram index](../../diagrams/README.md) and the editable [27-page Draw.io pack](../../diagrams/PASPM_Unified_27_Diagrams_UML2_Editable.drawio) (QA: [UML27 report](../../reports/UML27_DIAGRAM_QA_2026-11-02.md)); relevant pages: Use Case UC10–UC11, AD05, State R2-06.05 (Recommendation Review), Sequence AD05, Context (M3, S3 flows).

## 13. Historical references
[Readiness plan with appended approval](../../archive/F06/F06_IMPLEMENTATION_READINESS.md). Historical F03/F05 texts saying F06 is absent are not authoritative.

## 2026-11-15 — F03 Weight v2 impact
Eligibility is stored as `EligibilityV2` (no `expiryPushDays`, current U_expiry as `expiryPriorityScore`); old eligibility JSON is read unchanged by a version-aware reader. Expiry alerts use batch push days. Pre-W2 recommendations show `inventoryChanged = true` once (fingerprint format change, accepted). An ended snapshot season adds an `explanation` warning; there is no automatic re-run (Q5). Details: [F06 API](../../api/F06_RECOMMENDATION_API.md#2026-11-15--f03-weight-v2-knock-on-q5).

## 2026-11-16 follow-up
Recommendation views add structured `seasonEnded` / `endedSeasons` alongside the existing explanation, using frozen season dates and the current Store date. No automatic rerun.

# F06 Developer Guide

## Purpose and current repository state

This guide is for understanding and safely extending the PASPM F06 Recommendation Engine in the current `dev` branch. F06 is already implemented in this branch: its Java module, Flyway V10 migration, API contract, runtime OpenAPI export, business rules, frontend handoff, examples and verification report are present. Treat this as a guide to the existing implementation, not a new implementation plan.

The implementation report records 210 automated tests and 27 Swagger UI cases as passing on 2026-10-20. Those are the report's recorded results; they were not rerun when this guide was written. Frontend integration, scheduling, inventory reservation and campaign/publication workflows are outside the completed backend scope.

## Read these contracts first

1. [F06 rules v1.1](F06_RECOMMENDATION_RULES_v1_1.md) — approved business semantics, thresholds, evidence, ranking and review rules.
2. [F06 API contract](../api/F06_RECOMMENDATION_API.md) — exact routes, DTOs, nullability, validation, authorization and error behavior.
3. [F06 frontend handoff](../frontend_handoff/F06_HANDOFF.md) — UI flow, examples, role behavior and client safety requirements.
4. [F05 to F06 handoff](../PASPM_F05_High_Utility_Mining/F05_F06_HANDOFF.md) — raw mining pattern semantics and provenance available to F06.
5. [F03/F05/F06 integration contract](../api/F03_V12_F05_F06_INTEGRATION.md) — frozen utility snapshots and independent expiry-push boundary.
6. [F06 verification report](F06_VERIFICATION.md) and [implementation summary](F06_IMPLEMENTATION_SUMMARY.md) — recorded implementation and test evidence.

Use the API contract and approved rules as the behavioral source of truth. The implementation-readiness audit is historical; its old `NOT IMPLEMENTED` labels predate the code now on `dev`.

## How one recommendation is produced

```text
F01 JWT and Store scope
  -> completed own-Store F05 run and persisted pattern rows
  -> validate frozen request, snapshot, hashes, mapping and pattern count
  -> read frozen F05 input + immutable F03 matrix membership
  -> calculate distinct-basket support and directional confidence
  -> apply the versioned Store rule policy
  -> check current Product and batch eligibility
  -> persist candidate, source provenance and explanations
  -> Merchandiser reviews with optimistic version check
  -> downstream reads detail and rechecks current eligibility before use
```

The generation path is implemented primarily in `recommendation/RecommendationController`, `RecommendationService`, `FrozenEvidenceReader`, `TransactionEvidence`, `RecommendationRules`, `ProductEligibilityService` and `RecommendationRepository`. Review and configuration are separated into `RecommendationReviewService` and `RecommendationPolicyService`. The independent current expiry endpoint is implemented by `RecommendationExpiryController` and `RecommendationExpiryService`.

### Boundaries that must stay intact

- Generate from a completed F05 run in the caller's Store. Do not run SPMF from F06 or query patterns without Store scope.
- Treat HUI utility as frozen weighted utility. It is not current margin, revenue, purchase probability or support.
- F05's `supportCount` is null because mining did not measure support. Derive historical co-occurrence only from persisted frozen transaction membership, validating it against the stored matrix. Do not use live sales to rewrite historical evidence.
- Count a Product set at most once per transaction. Basket quantity does not multiply support. Compute threshold decisions using exact counts; rounded ratios are for display only.
- Preserve every equivalent HUI-Miner/EFIM source, but do not create duplicate candidates for equivalent evidence. Do not merge different snapshots, thresholds, utilities or policy versions.
- Freeze source, scoring, mining and rule-policy provenance with each candidate. Later config or inventory changes must not rewrite historical evidence or review history.
- Check current sellability separately from frozen mining evidence. Eligibility is time-sensitive and must be rechecked at approval and again at downstream activation/fulfillment.
- Expiry push is independent of Mining. An expiring Product must not disappear from the push workflow because it was absent from HUI results, and a high expiry score is not proof of HUI membership.

## Current business behavior

The supported candidate types are `BUNDLE`, `CROSS_SELL`, `FBT` and `BANNER`.

| Type | Current rule |
|---|---|
| BUNDLE | Preserve a complete pattern with at least two Products and no more than configured `maxBundleSize`; do not invent subsets, price or discount. |
| CROSS_SELL | Build directional candidates per anchor Product. Targets are the other Products; require positive co-occurrence and the configured confidence threshold. |
| FBT | Require at least two Products and both configured support-count and support-ratio thresholds. |
| BANNER | Create a reviewable suggestion from an eligible source pattern, including a singleton; do not generate artwork, layout or publication. |

The initial policy is bundle size 4, FBT support count 2 and ratio 0.05, cross-sell confidence 0.50, and minimum sellable quantity 1 per Product. Store Admin owns versioned policy updates; Merchandiser generates and reviews recommendations. Existing candidates retain the policy version captured at generation.

F06 review is `PENDING_REVIEW` to either `APPROVED` or `REJECTED`. Review requires a reason and `expectedVersion`; approval is allowed only after a fresh eligible check. A decision is final. Approval does not publish, reserve stock, create a campaign or guarantee future availability.

## API and authorization map

The implementation exposes eight operations under `/api/recommendations`:

| Operation | Permission |
|---|---|
| `POST /generate` | MERCHANDISER |
| `GET /` and `GET /{id}` | MERCHANDISER or STORE_ADMIN, own Store |
| `POST /{id}/approve` and `POST /{id}/reject` | MERCHANDISER |
| `GET /config` | MERCHANDISER or STORE_ADMIN, own Store |
| `PUT /config` | STORE_ADMIN, own Store |
| `GET /expiry-push` | MERCHANDISER or STORE_ADMIN, own Store |

Store identity always comes from the authenticated F01 principal. Never accept a Store ID from the request. Foreign records should remain indistinguishable from missing records (404), according to the existing contract.

## Persistence and change management

F06 uses Flyway V10 and five Store-scoped tables: `recommendation_rule_config`, `recommendation`, `recommendation_item`, `recommendation_source` and `recommendation_review_history`. Preserve Store-composite foreign keys, candidate identity uniqueness, source provenance and append-only review history.

When extending behavior:

1. Decide whether the change alters business semantics, wire contract, persistence or only presentation. Update the approved rules first if semantics change.
2. Add or revise focused tests for the exact behavior and boundary conditions before changing production code.
3. Keep transaction evidence in `FrozenEvidenceReader`/`TransactionEvidence`, pure type rules in `RecommendationRules`, live stock checks in `ProductEligibilityService`, and persistence/audit in the repository/services that own those concerns.
4. For a schema change, add a new Flyway migration after V10; do not rewrite V1–V10. Update the editable ERD and migration tests.
5. Preserve Store scoping and optimistic concurrency on review/config changes. Recheck live eligibility inside approval, not only in the browser or a prior GET.
6. Update `F06_RECOMMENDATION_API.md`, `F06_HANDOFF.md`, examples, shared `openapi.json`, `API_CHANGELOG.md` and verification evidence whenever the exposed contract changes.
7. Keep verification claims separate: automated tests, MySQL fresh/upgrade checks, Swagger UI execution and frontend integration are distinct activities.

The documented generation and ranking safety limit is 10,000 candidates. Return a clear conflict when the limit is exceeded; do not silently truncate results or rank only the first page.

## Suggested verification checklist

- Source: reject missing, foreign, pending/failed, incomplete or hash-inconsistent runs; accept a valid zero-pattern completed run as an empty generation.
- Evidence: validate frozen membership/mapping; check distinct-basket support, zero-utility membership and exact directional confidence; represent unavailable evidence as null, not zero.
- Rules: cover min/max bundle size, cross-sell anchors, both FBT thresholds, banner singleton behavior, skipped reasons and idempotent regeneration.
- Inventory: Product inactive, no stock, blocked/empty/expired/today-expiry batches, missing metadata, expiry date rollover and independent push with no mining run.
- Review: only one concurrent reviewer wins; stale `expectedVersion` conflicts; approval rechecks eligibility; rejection remains possible when ineligible; final decisions are not reopened.
- Security: all roles, missing/invalid/revoked JWT, forged Store IDs and cross-Store reads/writes.
- Regression: F01–F05 tests, V1–V10 fresh/upgrade migration checks, runtime OpenAPI and actual example payloads.

## Known scope limits

There is no F06 scheduler, retry/cancel API, publication, campaign, discount, inventory reservation, automatic approval or review reopening. Do not add these as side effects to generation or approval. Design them as separate, explicitly scoped workflows if the product requires them.

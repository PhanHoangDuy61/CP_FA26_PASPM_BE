# F06 core review

Reviewed all new recommendation production classes, V10 SQL and all five recommendation test classes against the original attached F06 task, approved policy document and implementation plan. Used code-review-and-quality workflow. Read existing F02 sellability, F03 snapshot, shared expiry and F05 persistence/input code only to establish contracts. No repository edits, commits, builds or test execution; application.yml was not read. Documentation under concurrent editing is excluded. Parent reports 38 passing F06 tests, including the real F03→F05→F06 route and concurrency; MySQL verification is pending separately.

## Verdict

- Spec compliance: APPROVE for reviewed core behavior. No new actionable P0–P3 findings identified.
- Code quality: APPROVE. The implementation fits the JDBC feature-module architecture and approved scope. No new dependencies or divergent mining/expiry formulas were introduced.
- The known OpenAPI Evidence schema-name collision is excluded from this verdict because the parent is already implementing its regression/fix. Verify that separately before overall completion.

## Prior finding closure

The previous P2 missing-pattern completeness finding is fixed in FrozenEvidenceReader: nonnegative completed patternCount is required, repository total must match it, pattern IDs must be unique and belong to the source run, and the final collected count must agree. Tests now distinguish a genuinely completed zero-pattern run from all/partially missing patterns and null/negative recorded counts. The additional positive co-occurrence check rejects impossible persisted patterns without calculating utility again.

## Evidence and generation

- Ownership is resolved before interpretation; only completed own-Store runs are accepted. Malformed/inconsistent persisted source is sanitized to 409 while missing/foreign run remains 404.
- Frozen text/request/run hashes agree; snapshot identity, configuration, mapping and matrix membership are validated. Zero utility retains item presence. Source support never depends on nullable F05 support_count or current sales.
- Support counts presence once, confidence uses the directional anchor denominator, unavailable/zero denominators are explicit, and six-decimal HALF_UP output is separate from exact count comparisons used for policy acceptance.
- Bundle, FBT and Cross-sell size/evidence gates and Banner singleton handling follow approved rules. Confidence and support thresholds use exact multiplication rather than rounded ratios. Skipped rules have explicit reasons.
- Every new candidate starts PENDING_REVIEW. Policy/evidence/generation eligibility and explanation are persisted as snapshots; current eligibility is recomputed separately.

## Identity, provenance and ranking

- Candidate identity includes snapshot, input hash, effective converted threshold, utility, rule/policy versions, canonical group, type and anchor. This preserves distinct snapshots/policies/directions/types and avoids silently treating different utility evidence as identical.
- Equivalent HUI-Miner/EFIM results reuse a candidate while adding run/pattern provenance, including algorithm/library/conversion/minUtil/source hashes. Repeat generation preserves resolved review state.
- Store-row locking serializes generation/config updates; database identity uniqueness is a second invariant. Multi-source attachment occurs inside the same generation transaction.
- Ranking is performed before pagination only within a requested source run, using live eligibility, utility descending, support descending, canonical group/type/anchor and final ID ties. General history stays chronological, avoiding unrelated utility comparisons. Policy minimum-stock differences remain respected in cached eligibility checks.

## Eligibility, expiry and review

- Product status, Store scope, ACTIVE batches, positive remaining quantity and current Store-local expiry date control live sellability; required-expiry missing metadata yields NEEDS_RECHECK and prevents approval/readiness. Old snapshot quantities are not used as stock.
- Approval/rejection requires MERCHANDISER, own Store, matching expectedVersion and PENDING_REVIEW. Row locking plus conditional update and unique resulting-version history protects concurrent review. History records actor, reason, transition and current check.
- APPROVED remains a business decision; readyForConsumption additionally requires current ELIGIBLE state. Subsequent stock changes invalidate readiness without rewriting historical evidence or reopening decisions. Inventory is explicitly not reserved.
- Independent expiry uses only UtilityProductRepository/shared ExpiryScoreCalculator. Per-batch boundaries, missing warnings, FEFO, current-only date, matching checkedAt, pagination, RBAC and foreign Product404 are covered. No mining dependency exists.

## Schema and boundaries

- V10 only adds F06 tables, constraints and indexes. Store-scoped composite foreign keys protect recommendation products, anchors, snapshot, source references and histories. Unique identity/history keys and review-field/transition checks reinforce service behavior.
- Reviewed F06 production SQL writes only F06 recommendation/config/source/item/history tables. No mining invocation, F03/F05 mutation, inventory mutation, discount/campaign/storefront creation or automatic activation exists.
- Existing F03/F05 production files are not modified in the current working-tree status. The modified deployment migration-regression test is the expected latest-version adjustment; unrelated application.yml was not inspected.

## Remaining verification, not new findings

MySQL fresh/upgrade migration and runtime tests, complete regression suite and OpenAPI schema collision fix remain parent verification responsibilities. Static review does not substitute for those executions. Query volume grows with patterns/products because existing repository boundaries use per-pattern/item and per-product batch reads; source/run ranking caps bound the reviewed generation/list scopes, and no unsupported performance claims are made.

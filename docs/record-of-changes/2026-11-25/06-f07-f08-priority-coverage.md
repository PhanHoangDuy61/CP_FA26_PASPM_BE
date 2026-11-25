# ROC-20261125-006 — Frozen snapshot priority coverage

| Field | Value |
|---|---|
| Date / timezone | 2026-11-25 / Asia/Ho_Chi_Minh |
| Agent / type | Codex / Implementation |
| Features | F07 Layout Simulator, F08 Campaign Simulation |
| Branch / base | `Khoi/feat/f07-f08-priority-coverage` / `4ef7fbb` (`origin/dev`) |
| Delivery | Feature branch |

## Scope and owner decisions

Implement [DEC-20261117-02](../../decisions/2026-11-17/DEC-20261117-02-priority-coverage-definition.md). A displayed product is counted once and qualifies only when its frozen F03 snapshot weight W is strictly greater than 1. Use the existing six-decimal HALF_UP rate convention.

The owner confirmed these two edge rules on 2026-11-25:

- Follow DEC-20261119-06 when B has more distinct products than A: compare the full A with B, set `nExceedsBaseline=true` and use the actual product count for each coverage denominator.
- An empty layout returns `priorityCoverage: null` and `priorityCoverageDenominator: 0`.

## Implementation and compatibility

- Layout detail and preview report coverage over the full layout. An unavailable or inconsistent frozen W produces a null rate with the actual denominator.
- New simulation reports use `OFFLINE_REPLAY_V4`. They persist coverage and denominators for the compared A prefix and the full B in the existing report columns. Coverage depends on displayed products and is available with zero evaluation transactions. Historical reports retain their stored fields.
- Additive response update, with no route, permission, migration or frontend code change. See the [F07/F08 API](../../api/F07_F08_LAYOUT_CAMPAIGN_API.md), [OpenAPI](../../api/openapi.json) and [frontend handoff](../../frontend_handoff/F07_F08_HANDOFF.md).

## Verification

Local macOS arm64, Eclipse Temurin Java 17.0.21 pinned with `JAVA_HOME`, Maven 3.9.9, test-owned H2 databases.

| Command | Result |
|---|---|
| `./mvnw -o -B test '-Dtest=LayoutCampaignIntegrationTest#priorityCoverageUsesFrozenWeightsAndHandlesEmptyLayoutsAndAnOversizedB+equalSizeSimulationComparesBWithTheTopNOfAAndReportsNAndTheSizeOfA'` on unchanged production code | 2 tests, 2 expected assertion failures, 0 errors, 0 skipped |
| `./mvnw -o -B test -Dtest=LayoutCampaignIntegrationTest` after implementation | 17 tests, 0 failures, 0 errors, 0 skipped |
| `./mvnw -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py backend)"` on final source | 465 tests, 0 failures, 0 errors, 0 skipped. BUILD SUCCESS |

The backend group verifies frozen W, distinct product counting, six-decimal rounding, full A versus top-N A, B larger than A, empty layout and evaluation set, unavailable W, persisted coverage and historical null fields. MySQL, browser tests and hosted CI are NOT RUN.

Runtime OpenAPI export: `./mvnw -o -B test '-Dtest=TransactionExcelImportIntegrationTest#openApiDocumentsBothMultipartImportRoutes' -Dpaspm.test.export-openapi=true` passed 1/1. The checked-in spec includes only the four coverage property changes from that export, with the same 113 paths and 140 operations.

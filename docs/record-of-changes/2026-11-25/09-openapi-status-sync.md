# ROC-20261125-009 — OpenAPI and feature status sync

| Field | Value |
|---|---|
| Date / timezone | 2026-11-25 / Asia/Ho_Chi_Minh |
| Type | Documentation |
| Branch / base | `Khoi/docs/sync-openapi-and-status` / dev `cded4ab` |
| Release | UNRELEASED |
| Daily index | [2026-11-25](README.md) |

## Scope and changes

- Compared the [checked-in OpenAPI](../../api/openapi.json) with the runtime export. Both expose **112 paths / 139 operations**, with identical path/method sets. Retained the file's formatting and applied only semantic changes to the export server, CSV async `Idempotency-Key` schema, F07 placement schemas, operation ID, summaries and response metadata.
- Updated counts and delivery state in the [SRS](../../product/SRS.md), [API index](../../api/README.md), [changelog](../../api/API_CHANGELOG.md) and [feature index and READMEs](../../features/README.md). F10/F11 contracts are on dev via PR #34, and their implementation stays **PLANNED**.
- Scanned 56 local origin refs. DEC 01–06 and ROC 001–008 are used for this date. This uses ROC 009. No business decision, code, migration or API behavior changed.

## Verification

`./mvnw -o -B '-Dtest=TransactionExcelImportIntegrationTest#openApiDocumentsBothMultipartImportRoutes' -Dpaspm.test.export-openapi=true test` on Temurin 17.0.21.1 and disposable H2. **1 test, 1 passed, 0 failures, 0 errors, 0 skipped.** Flyway applied V1–V30 in that test context. Parsed the resulting file and checked semantic equality with the runtime export. Full regression, MySQL and hosted CI were **NOT RUN**.

## Owner question

Should a separate Backend PR explicitly annotate the F07 DTOs so generated clients see the real Layout B constraints? The generated schema omits the allowed zone values, positive required `blockNo` and `position`, and nullable `sourceRecommendationId`/legacy response alias `recommendationId`. The [F07/F08 contract](../../api/F07_F08_LAYOUT_CAMPAIGN_API.md) documents the service behavior. This sync reproduces the current runtime document.

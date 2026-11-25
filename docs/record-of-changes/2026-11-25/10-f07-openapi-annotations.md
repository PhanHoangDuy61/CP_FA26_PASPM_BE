# ROC-20261125-010 — F07 schema annotations and stable operation IDs

| Field | Value |
|---|---|
| Date / timezone | 2026-11-25 / Asia/Ho_Chi_Minh |
| Type | API metadata |
| Branch / base | `Khoi/chore/f07-openapi-annotations` / `2bcbe69` (PR #35) |
| Release | UNRELEASED |
| Daily index | [2026-11-25](README.md) |

## Scope

- Annotated F07 Layout B zones, positive required `blockNo` and `position`, and nullable request and response provenance. The existing Bean Validation annotations, route mappings, RBAC and service behavior are unchanged.
- Gave each of the 47 auto-suffixed runtime operations a stable explicit ID. `GET /api/layouts/products` uses `layoutSnapshotProducts`, replacing `products_2` after PR #35. Regenerating clients can rename these 47 methods.
- Scanned 57 local origin refs. ROC 001–009 already exist across branches, so this change uses ROC 010. No new business decision or migration.
- Before and after: **112 paths / 139 operations**. The runtime comparison against the export stored on `origin/Khoi/docs/sync-openapi-and-status` (`55c2512`, same Backend base) found 47 ID changes and nine F07 metadata changes. The [checked-in export](../../api/openapi.json) applies this annotation delta while preserving its formatting. PR #35 landed during verification. The branch was rebased onto that docs-only merge, and the final export fully matches the captured runtime document. The source tree is identical to the tested tree.

## Operation ID migration

| Method and path | Prior runtime ID | Explicit ID |
|---|---|---|
| `POST /api/auth/activate` | `activate_1` | `activateAccount` |
| `GET /api/categories` | `list_4` | `listCategories` |
| `POST /api/categories` | `create_7` | `createCategory` |
| `GET /api/categories/{id}` | `get_4` | `getCategory` |
| `PUT /api/categories/{id}` | `update_4` | `updateCategory` |
| `PATCH /api/categories/{id}/status` | `updateStatus_1` | `updateCategoryStatus` |
| `GET /api/inventory-batches/{id}` | `get_5` | `getInventoryBatch` |
| `PATCH /api/inventory-batches/{id}` | `update_5` | `updateInventoryBatch` |
| `GET /api/layouts/policy` | `policy_2` | `getLayoutPolicy` |
| `GET /api/layouts/products` | `products_2` | `layoutSnapshotProducts` |
| `GET /api/layouts/{id}/preview` | `preview_1` | `previewLayout` |
| `POST /api/mining/requests` | `create_6` | `createStoreMiningRequest` |
| `GET /api/mining/requests/{id}` | `request_1` | `getStoreMiningRequest` |
| `GET /api/mining/requests/{id}/runs` | `runs_1` | `listStoreMiningRequestRuns` |
| `GET /api/mining/runs/{id}/patterns` | `patterns_1` | `listMiningRunPatterns` |
| `GET /api/mining/snapshots` | `snapshots_1` | `listStoreMiningSnapshots` |
| `GET /api/products` | `list_2` | `listProducts` |
| `POST /api/products` | `create_4` | `createProduct` |
| `DELETE /api/products/{id}` | `delete_1` | `deleteProduct` |
| `GET /api/products/{id}` | `get_3` | `getProduct` |
| `PUT /api/products/{id}` | `update_3` | `updateProduct` |
| `GET /api/products/{id}/batches` | `list_3` | `listProductBatches` |
| `POST /api/products/{id}/batches` | `create_5` | `createProductBatch` |
| `GET /api/recommendations` | `list_8` | `listRecommendations` |
| `GET /api/recommendations/config` | `config_2` | `getRecommendationConfig` |
| `PUT /api/recommendations/config` | `config_3` | `updateRecommendationConfig` |
| `GET /api/recommendations/expiry-push` | `list_9` | `listRecommendationExpiryPush` |
| `GET /api/recommendations/{id}` | `get_10` | `getRecommendation` |
| `GET /api/storefront/{storeSlug}/products` | `products_1` | `listStorefrontProducts` |
| `GET /api/system/mining/config` | `get_2` | `getMiningConfig` |
| `PUT /api/system/mining/config` | `update_2` | `updateMiningConfig` |
| `POST /api/system/mining/requests` | `create_3` | `createSystemMiningRequest` |
| `GET /api/system/mining/schedules` | `list_1` | `listMiningSchedules` |
| `POST /api/system/mining/schedules` | `create_2` | `createMiningSchedule` |
| `GET /api/system/mining/schedules/{id}` | `get_1` | `getMiningSchedule` |
| `PUT /api/system/mining/schedules/{id}` | `update_1` | `updateMiningSchedule` |
| `GET /api/system/mining/stores/{storeId}/requests` | `requests_1` | `listSystemStoreMiningRequests` |
| `PUT /api/system/utility/lambda-policy` | `policy_1` | `updateLambdaPolicy` |
| `GET /api/transactions` | `list_6` | `listTransactions` |
| `GET /api/transactions/imports` | `list_7` | `listTransactionImports` |
| `GET /api/transactions/imports/{id}` | `get_9` | `getTransactionImport` |
| `GET /api/transactions/{id}` | `get_8` | `getTransaction` |
| `PUT /api/utility/config` | `config_1` | `updateUtilityConfig` |
| `GET /api/utility/expiry-push-batches` | `list_5` | `listExpiryPushBatches` |
| `GET /api/utility/products/{productId}/scores` | `get_7` | `getProductScores` |
| `POST /api/utility/seasons` | `create_1` | `createSeason` |
| `GET /api/utility/snapshots/{id}` | `get_6` | `getUtilitySnapshot` |

## Verification

Environment: Temurin 17.0.21.1, Maven Wrapper in offline mode, disposable H2.

| Command or check | Actual result |
|---|---|
| `./mvnw -o -B '-Dtest=TransactionExcelImportIntegrationTest#openApiDocumentsBothMultipartImportRoutes,LayoutCampaignIntegrationTest' -Dpaspm.test.export-openapi=true test` | Exit 0. Export 1 PASS, LayoutCampaignIntegrationTest 17 PASS. 0 failures, 0 errors, 0 skips. |
| `./mvnw -o -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py backend)"` | Exit 0. 466 PASS, 0 failures, 0 errors, 0 skips. The backend group also includes the focused classes. |
| Export and doc checks | Identical path/method sets before and after. All 139 IDs are unique and none has a generated numeric suffix. 252 local OpenAPI references, 119 relative links and six JSON examples validated. |
| Source diff | The 20 source files differ only in Swagger annotations and associated formatting/imports. |

The first focused attempt stopped during compilation because some controllers had a duplicate `@Operation`. The retest above passed after attaching `operationId` to the existing annotation. Logs and raw export are under `outputs/evidence/f07-openapi-annotations/` in the project workspace outside Git. MySQL and hosted CI were **NOT RUN**.

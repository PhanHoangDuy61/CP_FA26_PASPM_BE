# Báo cáo Backend — Retail Catalog và current F03 E/W

Ngày kiểm chứng: **2026-10-21, Asia/Ho_Chi_Minh**. Phạm vi được người dùng chốt: **chỉ Backend API**, không sửa Frontend. Thiết kế được duyệt trước implementation. Nhánh `Khoi/fix/retail-catalog-utility-access` tách từ F06 commit68d2844; không push/merge trong công việc này.

## 1. Nguyên nhân và cách sửa

ProductController/CategoryController trước đây giới hạn STORE_ADMIN ở cấp class, áp dụng cả GET. Vì thế Merchandiser không thể tải Catalog thật. Test RED tái hiện chính xác expected200/actual403. `Retail` không phải role F01 mới; quyền Store dùng STORE_ADMIN/MERCHANDISER và membership lấy từ tài khoản đã xác thực.

Bốn GET list/detail Product/Category nay cho hai Store role, vẫn gọi CatalogAccess/StoreScope và repository scope như cũ. Quyền POST/PUT/PATCH Product/Category, inventory/batches/summary không mở cho Merchandiser. Foreign ID vẫn404; query storeId không chuyển được scope. F01/Spring Security không bị tắt.

API scores trước chỉ trả S/M và chỉ Merchandiser đọc được. Nay GET cho Store Admin/Merchandiser, PATCH vẫn chỉ Merchandiser. GET/PATCH trả current E/W từ **ExpiryScoreCalculator + UtilityMath có sẵn**, không cần Product có giao dịch. Shared formula, ngày Store, threshold Product và missing-data rules không bị viết lại.

## 2. API và role thực tế

| Operation | Store Admin | Merchandiser | System Admin / Customer |
|---|---|---|---|
| GET /api/products và /api/products/{id} | own Store | own Store |403 |
| GET /api/categories và /api/categories/{id} | own Store | own Store |403 |
| POST/PUT/PATCH Product/Category | quyền cũ |403 |403 |
| GET /api/utility/products/{id}/scores | own Store | own Store |403 |
| PATCH /api/utility/products/{id}/scores |403 | own Store, S/M only |403 |
| GET /api/utility/config | own Store | own Store |403 |
| PUT /api/utility/config |403 | own Store |403 |
| System lambda policy |403 |403 | chỉ System Admin |

Thiếu/invalid token401. Foreign Product/Category/scores404. E/W/T/stockQuantity/storeId không phải input của score PATCH; unknown field400. Scores trong[0,1], tối đa6 chữ số thập phân, partial update giữ giá trị không gửi, explicit null400. Existing config validation giữ nguyên: enabled coefficients sum1, lambda theo System policy; không cấp quyền bounds cho Merchandiser.

## 3. Kết quả tính hiện tại

Giữ productId, stockPriorityScore và seasonalPriorityScore cũ. Bổ sung response-only expiryScore, weightValue, expiryStatus, expiryEvidence, expiryPushDays, asOfDate/timeZone, configVersion, lambdaValid, ready và warnings. OpenAPI đánh dấu readOnly cho các field mới và nullable cho S/M/E/W.

Fixture S0.8/M0.9, alpha0.5/beta0.2/gamma0.3/lambda0.5, ACTIVE batch quantity10, expiry=Store today+5:

| Trường hợp | E | W | Kết quả |
|---|---|---|---|
| Product T7 |1 |1.435 | PASS |
| Product T3 |0 |1.335 | PASS |
| T3, PATCH S0.5/M0.9 qua Swagger |0 |1.26 | PASS, GET xác nhận persistence |
| T7, S0/M0.9 |1 |1.235 | PASS |
| T7, S0/M1 |1 |1.25 | PASS |
| T7, S1/M0 |1 |1.35 | PASS |
| T7 thiếu required expiry metadata, expiry enabled |null |null | PASS, readyfalse/warnings |
| Thiếu enabled S hoặc M | theo batch |null | PASS, readyfalse/warnings |
| Stored lambda0.5, System max0.1 | theo batch |null | PASS, lambdaValidfalse, không clamp |
| Tất cả criteria disabled, policy hợp lệ | shared E |1 | PASS |

`ready` chỉ nói có tính được current Weight, không phải chứng nhận tồn bán được hoặc APPROVED. Expired/today/blocked/empty batches không góp vào expiry priority. NO_SELLABLE_BATCH có warning; non-expiring NOT_APPLICABLE; inactive Product được đánh giá với không có eligible batches. Current preview không thay thế historical snapshot.

## 4. Kiểm thử tự động

**Java17 clean verify PASS:232 tests,0 failures,0 errors,0 skipped.** Hoàn tất13:32:02+07:00, thời gian01:05. Gồm210 regression có sẵn và22 lượt test mới (11 case chạy cả H2 và MySQL8.4.11). SPMF regression vẫn dùng child Java25 đã có; Backend không đổi khỏi Java17. [Tổng hợp và toàn bộ method names](evidence/retail-catalog-automated.json).

| New test method (chạy H2 + MySQL) | Hành vi xác nhận | Kết quả |
|---|---|---|
| merchandiserReadsOnlyTheirStoreCatalogWithFiltersAndPagination | list/search/category/status/paging/sort, Store isolation, forged Store query không đổi scope, foreign404 | PASS |
| catalogReadDoesNotGrantProductCategoryOrInventoryWrites | valid mutation requests vẫn403 | PASS |
| catalogRejectsUnauthenticatedAndNonStoreBusinessRoles |401, System/customer403, StoreAdmin read200 | PASS |
| currentScoresUseBackendFormulaWithoutTransactionHistory | E/W hai fixture, không transaction, date/timezone/configVersion | PASS |
| partialBoundaryWritesRecalculateButNeverChangeInventory |0/1, partial input, GET persistence, inventory row unchanged | PASS |
| scoreReadAndWritePermissionsStaySeparatedAndStoreScoped | read/write roles, foreign404, invalid token401, ownership of bounds/config | PASS |
| scorePatchRejectsInvalidOrCalculatedInputsWithoutChangingSavedValues | range/null/precision/unknown E/W/T/stock/storeId400, row unchanged | PASS |
| missingEnabledInputsReturnExplicitIncompleteCurrentPreview | null E/W, readyfalse và warnings | PASS |
| sharedExpiryAndDisabledCriteriaRulesRemainIntact | blocked/empty/today/non-expiring/inactive/all-disabled | PASS |
| policyChangesInvalidateCurrentWeightWithoutClampingStoredLambda | stale bounds, rejected invalid selection, recovery by valid lambda | PASS |
| openApiMarksCalculatedFieldsReadOnlyAndNullable | runtime schemas/types/readOnly/strict input | PASS |

Existing CatalogIntegrationTest cập nhật quyền mới và vẫn kiểm tra Product/Category CRUD, batch/quantity/validation/Store access. UtilityIntegrationTest giữ kiểm tra old/new snapshot và historical utility. RecommendationIntegrationTest được tăng cường: tạo snapshot thật, chạy HUI-Miner/EFIM thật, gọi score PATCH và config PUT, rồi so sánh nguyên trạng các bảng utility_snapshot, utility_matrix_item, mining_request, mining_run, high_utility_pattern, pattern_item, sales_transaction, transaction_item và inventory batches. Chạy cả H2/MySQL, PASS. Không tự chạy lại mining do score/config save.

Flyway fresh/upgrade V1–V10 PASS trên schema MySQL mới; task này **không thêm migration** và không sửa migration cũ. Không reset/repair dữ liệu dùng chung.

## 5. Backend thực tế và Swagger UI

Runtime Java17/MySQL riêng trên127.0.0.1:18087, cấu hình/credentials ngẫu nhiên chỉ lưu ngoài Git trong/tmp. Store A/B và5 role fixtures giả lập; Product hiện tại không cần transaction.

**32/32 HTTP cases PASS**, gồm coefficients save, E1/W1.435, PATCH/GET persistence, E0/W1.335, roles, cross-Store, invalid score, protected E/W, invalid config/lambda, policy invalidation/recovery và stock vẫn10. Đây là HTTP client checks, **không** được ghi nhận là Swagger UI. [Mỗi request/response/status/role/time thực tế](evidence/retail-catalog-http.json).

**11/11 Swagger UI cases PASS**, thực hiện bằng Authorize → Try it out → Execute, đọc Server response thực tế:

| ID | Method/endpoint | Expected/actual | Hành vi |
|---|---|---|---|
| UI01 | GET /api/products |200/200 | Merchandiser thấy đúng1 Product Store A |
| UI02 | GET /api/categories |200/200 | Chỉ category Store A |
| UI03 | GET /api/products/1 |200/200 | Product detail, stock10 |
| UI04 | GET /api/categories/1 |200/200 | Category detail |
| UI05 | GET /api/utility/products/1/scores |200/200 | E0/W1.335, readytrue |
| UI06 | PATCH scores |200/200 | S0.5/M0.9 → W1.26 |
| UI07 | GET scores |200/200 | Saved S0.5/W1.26 persisted |
| UI08 | PATCH scores có expiryScore |400/400 | Không cho ghi E |
| UI09 | GET /api/products/2 |404/404 | Không lộ Product Store B |
| UI10 | PATCH scores |200/200 | S0.8/M0.9 → E0/W1.335 |
| UI11 | GET scores |200/200 | Xác nhận persistence lần cuối |

[Swagger payload/response/time thực tế](evidence/retail-catalog-swagger-ui.json). Timestamps Swagger lưu ISO UTC kèm timeZone; quy đổi sang Asia/Ho_Chi_Minh là cùng ngày2026-10-21. [Ảnh Server response200, S0.8/M0.9/E0/W1.335](evidence/retail-catalog-swagger-scores.png) đã kiểm tra không chứa token/password/Authorization header.

Actual OpenAPI export: [openapi.json](../api/openapi.json),59 paths/73 operations, không thêm route trùng. Nullability/readOnly/strict ScorePatch schema đã được automated và runtime checks xác nhận.

## 6. RED/GREEN và các lỗi trong quá trình kiểm chứng

- RED Catalog: expected200 got403. Sửa bốn GET method permissions; GREEN10 targeted tests.
- RED scores:11 tests có7 failure do thiếu computed fields và StoreAdmin GET403. Sửa additive response/shared calculation/read permission; GREEN36 targeted tests.
- Một số test setup ban đầu dùng invalid mutation payload (validation400 trước method RBAC) hoặc gắn Store cho global user. Đã sửa fixture hợp lệ để kiểm tra đúng permission, không sửa production validation/F01.
- Full run đầu bị sandbox chặn localhost sockets; không tính là pass. Chạy lại có quyền localhost.
- Regression history test mới ban đầu query store_id trực tiếp ở utility_matrix_item (bảng không có cột đó). Sửa test join/scope qua utility_snapshot; không thay schema. Final full run232 PASS.

## 7. Files và tài liệu

Application source6 file:
- catalog/product/ProductController.java và catalog/category/CategoryController.java: method-level scoped read permission.
- utility/ProductScoreController.java: separated read/write roles và Swagger description.
- utility/ProductScoreService.java: transactional current read model dùng shared calculators.
- utility/ProductScoreRepository.java: giữ atomic partial SQL update; bỏ query đọc trùng đã thay bằng UtilityProductRepository.
- utility/UtilityContracts.java: additive Scores DTO/readOnly/nullability và strict ScorePatch input.

Tất cả path source ở `src/main/java/vn/edu/fpt/paspm/`. Test files: `src/test/java/vn/edu/fpt/paspm/utility/RetailCatalogUtilityIntegrationTest.java`, `RetailCatalogUtilityMySqlIntegrationTest.java`, existing `catalog/CatalogIntegrationTest.java`, `recommendation/RecommendationIntegrationTest.java`.

Updated [F03 API](../api/F03_UTILITY_API.md), [F02 API](../api/F02_PRODUCT_INVENTORY_API.md), [RBAC matrix](../api/F01_F02_RBAC_MATRIX.md), [F03 handoff](../frontend_handoff/F03_HANDOFF.md), [F02 handoff](../frontend_handoff/F02_HANDOFF.md), [changelog](../api/API_CHANGELOG.md), shared OpenAPI, F03_examples README và3 actual current-score JSON examples. Thiết kế/kế hoạch ở docs/superpowers/specs và plans theo ngày2026-10-21.

CodeGraph đã dùng trước/sau sửa: ProductScoreService→UtilityProductRepository/UtilitySettingsRepository/ExpiryScoreCalculator/UtilityTimeZone, current GET/PATCH routes; graph820 files/10688 nodes/20916 edges. Không có pending-sync banner khi kiểm tra. Static graph không thay thế test runtime. Rà soát source theo correctness/RBAC/Store boundary/read-only calculation/parameterized SQL/transaction và contract: không phát hiện blocker; không tuyên bố có independent reviewer.

## 8. Giới hạn còn lại

Backend scope hoàn tất, không còn business decision chờ duyệt. Frontend implementation/browser Retail Portal flow **NOT RUN/out of scope theo yêu cầu mới**; menu/route/demo auth hiện có trong FE và FE_Kien chưa được sửa. Không được hiểu Backend PASS là Portal đã hoàn tất tích hợp. Không có screenshot Product editor vì không sửa Frontend; ảnh đính kèm chỉ là Swagger API thực tế.

Không thêm optimistic version API cho scores/config: dùng cơ chế partial SQL update/DB transaction và config locking hiện có. Response current Weight có thể thay đổi ở lần đọc sau khi config, batch hoặc ngày Store thay đổi; snapshot lịch sử vẫn bất biến. Một số GET inventory endpoints tiếp tục chỉ cho StoreAdmin theo policy cũ, không được mở rộng ngầm. Không triển khai production, không push/merge. Private application.yml và file cá nhân/unrelated F03 giữ nguyên ngoài commit.

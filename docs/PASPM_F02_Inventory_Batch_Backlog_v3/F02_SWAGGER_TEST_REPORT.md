# PASPM F02 — Swagger UI Test Report

## Tóm tắt dễ đọc

Đợt kiểm tra này dùng Swagger UI để gửi request trực tiếp tới backend đang chạy. Hệ thống dùng database H2 tạm thời dành riêng cho lần kiểm tra, nên dữ liệu thử không ghi vào database dùng chung.

Trong **72 ca kiểm tra**, có **70 ca đạt**, **không còn lỗi chưa xử lý**, **1 ca bị Swagger UI chặn trước khi gửi request**, và **1 ca chưa chạy**. Vì vậy, kết quả rất tốt nhưng chưa thể gọi là toàn bộ bộ kiểm thử đã hoàn tất.

| Nhóm | Kết quả | Diễn giải |
|---|---:|---|
| Migration và dữ liệu mẫu | 5 đạt, 1 chưa chạy | Backend khởi động được và Flyway tạo schema từ V1 đến V3. Chưa thử khởi động lại trên cùng database đã migrate. |
| Phân quyền và cách ly Store | 7 đạt | Không đăng nhập được thì nhận 401; role không đủ quyền nhận 403; Store khác không nhìn thấy hoặc sửa được dữ liệu của nhau (404). |
| Category | 7 đạt | Tạo, xem, cập nhật, khóa/mở và kiểm tra dữ liệu sai đều cho kết quả mong đợi. |
| Product đọc | 8 đạt | Danh sách, tìm kiếm, lọc, phân trang và tồn kho tính từ các batch hoạt động đúng. |
| Product tạo/cập nhật | 10 đạt | Kiểm tra giá, SKU trùng, category không hợp lệ, và việc không cho tự sửa tồn kho đều đạt. |
| Inventory Batch | 15 đạt | Tạo batch, cập nhật số lượng/trạng thái, kiểm tra ngày hết hạn và dữ liệu sai đều đạt. |
| Product status | 5 đạt | Chuyển ACTIVE/INACTIVE, lọc theo trạng thái và từ chối trạng thái không hợp lệ đều đạt. |
| Inventory Summary | 13 đạt, 1 bị chặn | Các số lượng tồn kho khớp fixture trước/sau cập nhật. Swagger UI không gửi được giá trị ngày âm vì tự chặn ở trình duyệt. |

### Hai ca còn lại

- **Chưa chạy lại ứng dụng trên cùng database đã migrate:** lần kiểm tra dùng H2 trong bộ nhớ. Ứng dụng xác nhận áp dụng được ba migration, nhưng database mất khi ứng dụng dừng nên chưa kiểm tra kịch bản khởi động lần hai trên dữ liệu đã tồn tại.
- **Lookahead âm (`expiringWithinDays=-1`):** Swagger UI đánh dấu giá trị này không hợp lệ và dừng trước khi gửi request. Vì không có HTTP response từ backend, ca này được ghi là **BLOCKED**, không tính là đạt hay lỗi.

### Lỗi đã tìm thấy và sửa

Khi Store A gửi Product gắn với Category thuộc Store B, API trước đó trả 400 kèm nội dung cho biết Category thuộc Store khác. Điều này không phù hợp với quy tắc che dữ liệu của Store khác. Sau khi sửa, API trả 404 chung. Kiểm thử tự động hồi quy và lần chạy lại request qua Swagger đều xác nhận kết quả 404.

Một test tạo Category ban đầu thiếu trường `status`, dù API yêu cầu trường này. Mình đã sửa test case để gửi `status=ACTIVE`; request đúng trả 201 và GET xác nhận Category đã được lưu.

### Kiểm tra build và phạm vi

Lệnh `mvn -q verify` kết thúc thành công: **25 test tự động, không có lỗi, thất bại hay bị bỏ qua**. Flyway áp dụng V1–V3 trên H2 tạm. CodeGraph cũng truy được các API F02 tới service và lớp `StoreScope` dùng chung của F01.

Không có thay đổi Frontend. Báo cáo chi tiết theo từng mã test, endpoint, HTTP status và phản hồi thực tế nằm ở các bảng phía dưới.

**Run window:** 2026-10-16, approximately 22:00–22:16 Asia/Ho_Chi_Minh.
**Environment:** Spring Boot `dev` profile, port `8798`, isolated in-memory H2 database, Flyway enabled. No shared or production database was used. Swagger UI was opened at `http://localhost:8798/swagger-ui/index.html` and each HTTP result below came from Swagger UI **Try it out → Execute**. Store A admin, Store B admin, and Merchandiser credentials/tokens are omitted.
**Scope result:** 72 designed cases: **70 PASS, 0 unresolved FAIL, 1 BLOCKED, 1 NOT RUN**. One defect was observed, fixed, and retested successfully; its initial failure is recorded below.

## Execution results

`Run` means 2026-10-16, Asia/Ho_Chi_Minh. Evidence labels point to the actual Swagger responses summarized in the same row or to the named startup/build check. Request bodies are summarized and contain no credentials or tokens.

`E-*` entries are references to the observed request/response and follow-up GET recorded in the corresponding row; no screenshot was exported. Startup, Flyway, and Maven evidence comes from the actual application/test output.

### F02-01 — Flyway, schema, and development seed

| TC ID | Endpoint / method; input | Expected HTTP / behavior | Actual HTTP / behavior | Result / evidence |
|---|---|---|---|---|
| F02-DB-01 | Spring Boot startup; clean isolated H2 | Startup succeeds; CATEGORY, PRODUCT, INVENTORY_BATCH migrate; STORE reused | Startup succeeded; Flyway applied V1–V3 and reached v3 | PASS — E-DB-01 (startup log) |
| F02-DB-02 | Restart against an already-migrated persistent database | Flyway validates without checksum failure; F01 data retained | Not executed against the same persistent database | NOT RUN — E-DB-02 |
| F02-DB-03 | `GET /api/categories`, `GET /api/products`, `GET /api/products/{id}/batches` | Store-scoped dev seed includes active, expired, blocked, and non-expiring examples | Store A/B lists were isolated; seed product/batch rows included those statuses and expiry types | PASS — E-DB-03 / E-SEC |
| F02-DB-04 | `POST /api/products`; same SKU A then Store B | Duplicate SKU in A → 409; same SKU in B → 201 | A duplicate → 409; same SKU created in B → 201 | PASS — E-DB-04 |
| F02-DB-05 | `POST /api/products/{id}/batches`; duplicate code in same product, then another product | Same-product duplicate → 409; another product may reuse code | Same-product duplicate → 409; other product → 201 | PASS — E-DB-05 |
| F02-DB-06 | Applied V3 schema / database constraint checks | Store ownership and numeric constraints; no `PRODUCT.stock_quantity` or `STOCK_MOVEMENT` | V3 migrated; integration constraint check rejected cross-store category/negative price; migration has no independently editable stock or movement table | PASS — E-DB-06 |

### Security and Store isolation

| TC ID | Endpoint / method; input | Expected HTTP / behavior | Actual HTTP / behavior | Result / evidence |
|---|---|---|---|---|
| F02-SEC-01 | `GET /api/products`, `GET /api/categories`; no token | 401; no data | Both returned 401 | PASS — E-SEC-01 |
| F02-SEC-02 | Category GET, Product POST, Batch PATCH; Merchandiser token | 403 | All three returned 403 | PASS — E-SEC-02 |
| F02-SEC-03 | GET Store A Product/Category/Batch as Store B admin | 404 for all foreign records | All three returned 404 | PASS — E-SEC-03 |
| F02-SEC-04 | Product status, Category status, Batch quantity writes across Store boundary | 404; foreign data unchanged | Cross-store writes returned 404; subsequent Store A summary remained consistent | PASS — E-SEC-04 |
| F02-SEC-05 | Store-scoped Product list and Category/Batch reads | Each admin sees only its Store | Store A and B product lists contained only their own rows; Category and Batch reads were Store-scoped | PASS — E-SEC-05 |
| F02-SEC-06 | POST Category/Product/Batch with extra forged `storeId` | Cannot create data in another Store | All three requests returned 400; no supplied Store ID accepted | PASS — E-SEC-06 |
| F02-SEC-07 | `GET /api/products`; invalid bearer token | 401 | Returned 401 | PASS — E-SEC-07 |

### F02-02 — Category management

| TC ID | Endpoint / method; input | Expected HTTP / behavior | Actual HTTP / behavior | Result / evidence |
|---|---|---|---|---|
| F02-CAT-01 | `POST /api/categories`, name + `status=ACTIVE`; then GET detail | 201; response ID and persisted fields match | Initial payload omitted required status and returned 400; corrected payload returned 201 and GET returned the created category | PASS after corrected request — E-CAT-01; test-case input corrected |
| F02-CAT-02 | `GET /api/categories`, `GET /api/categories/{id}` | 200; new Store A category appears | Both returned 200 and matching category fields | PASS — E-CAT-02 |
| F02-CAT-03 | `PUT /api/categories/{id}`, valid name/status | 200/204; updated value persists | Returned 200; GET detail showed updated name | PASS — E-CAT-03 |
| F02-CAT-04 | `PATCH /api/categories/{id}/status`, `INACTIVE` | 200/204; status persists; no hard delete | Returned 200; GET returned `INACTIVE` | PASS — E-CAT-04 |
| F02-CAT-05 | Category POST and PUT with whitespace-only name | 400; no update | POST and PUT each returned 400 `Invalid request fields` | PASS — E-CAT-05 |
| F02-CAT-06 | GET/PUT/PATCH category ID `999999` | 404 | All three returned 404 | PASS — E-CAT-06 |
| F02-CAT-07 | Deactivate category referenced by Product; GET Product afterward | Category and Product still exist | Category became inactive; Product GET still returned the linked category and Product | PASS — E-CAT-07 |

### F02-03 — Product list, search, and detail

| TC ID | Endpoint / method; input | Expected HTTP / behavior | Actual HTTP / behavior | Result / evidence |
|---|---|---|---|---|
| F02-READ-01 | `GET /api/products` as Store A admin | 200; only Store A records | Returned 200 with Store A seed and test records only | PASS — E-READ-01 |
| F02-READ-02 | `GET /api/products?search=...`, SKU then name | 200; matching Product returned | SKU and case-insensitive name searches each returned the expected Product | PASS — E-READ-02 |
| F02-READ-03 | `GET /api/products?categoryId={categoryAId}` | 200; only matching category products | Returned 200 with only the Product for the selected category | PASS — E-READ-03 |
| F02-READ-04 | Product status transition and list filters `ACTIVE`/`INACTIVE` | 200; each filter matches status | Both filters returned the corresponding rows after status change | PASS — E-READ-04 |
| F02-READ-05 | `GET /api/products?page=0&pageSize=2`, then page 1 | 200; page metadata consistent and no repeated row | Both pages returned 200; page size 2, totalElements 7, and the observed IDs did not overlap | PASS — E-READ-05 |
| F02-READ-06 | `GET /api/products/{id}` after batches have quantities [4,6,3,10,5] | Derived `stockQuantity=28` | Product detail returned `stockQuantity=28`, matching batch remaining quantities | PASS — E-READ-06 |
| F02-READ-07 | `GET /api/products/999999` | 404 | Returned 404 `Product not found` | PASS — E-READ-07 |
| F02-READ-08 | OpenAPI security and Product routes | Product management is protected; no public cost-price route | Swagger listed F02 Product operations with bearer authorization; no public F02 Product route was present | PASS — E-READ-08 |

### F02-04 — Product create and update

| TC ID | Endpoint / method; input | Expected HTTP / behavior | Actual HTTP / behavior | Result / evidence |
|---|---|---|---|---|
| F02-WRITE-01 | `POST /api/products`, valid required fields and Store A category | 201; persisted Product; initial stock zero | Returned 201; Product detail had expected fields and `stockQuantity=0` | PASS — E-WRITE-01 |
| F02-WRITE-02 | `PUT /api/products/{id}`, valid changed name/prices; then GET | 200/204; values persist | PUT returned 200; subsequent GET returned changed values and unchanged derived stock | PASS — E-WRITE-02 |
| F02-WRITE-03 | `POST /api/products`, duplicate Store A SKU | 409; no new Product | Returned 409 | PASS — E-WRITE-03 |
| F02-WRITE-04 | `POST /api/products`, real Store B `categoryId` under Store A token | 404; hide foreign category | First run returned 400 with a Store-specific error. Fixed `ProductService` to return generic 404; regression test passed and Swagger retest returned 404 `Category not found` | PASS after fix — E-WRITE-04; initial failure in `F02_SWAGGER_ISSUES.md` |
| F02-WRITE-05 | Product POST with negative selling price and negative cost price | 400 | Each request returned 400; no Product was created | PASS — E-WRITE-05 |
| F02-WRITE-06 | Product POST missing required name | 400 | Returned 400 `Invalid request fields` | PASS — E-WRITE-06 |
| F02-WRITE-07 | Product POST selling/cost price zero | 201 when zero is allowed | Returned 201 with both prices equal to zero | PASS — E-WRITE-07 |
| F02-WRITE-08 | Product POST includes `stockQuantity=999`; then GET | Reject/ignore safely; stock unchanged | Request returned 400; GET still derived stock from batches (`28`) | PASS — E-WRITE-08 |
| F02-WRITE-09 | Update non-perishable Product with expiry-less Batch to `requiresExpiry=true` | Reject inconsistent state | Returned 400 `Cannot require expiry while a batch has no expiry date` | PASS — E-WRITE-09 |
| F02-WRITE-10 | Product POST with nullable `categoryId` | 201; no Store scope bypass | Returned 201 with `category: null` | PASS — E-WRITE-10 |

### F02-05 — Inventory Batch

| TC ID | Endpoint / method; input | Expected HTTP / behavior | Actual HTTP / behavior | Result / evidence |
|---|---|---|---|---|
| F02-BATCH-01 | `POST /api/products/{id}/batches`, valid perishable batch, expiry today+10 | 201; remaining=initial; ACTIVE | Five fixture batches returned 201; remaining equaled initial and status was ACTIVE | PASS — E-BATCH-01 |
| F02-BATCH-02 | `GET /api/products/{id}/batches` | 200; created batches visible | Returned 200 with all five fixture batches and their quantities/statuses | PASS — E-BATCH-02 |
| F02-BATCH-03 | `PATCH /api/inventory-batches/{id}`, remaining 8→3; then GET | 200/204; absolute remaining value persists | PATCH returned 200; GET returned remaining quantity 3 | PASS — E-BATCH-03 |
| F02-BATCH-04 | PATCH Batch `BLOCKED`, then `ACTIVE`; GET | 200/204; both states persist | Both PATCH calls returned 200; GET returned ACTIVE after the second change | PASS — E-BATCH-04 |
| F02-BATCH-05 | Create expiry-tracked batch without expiry date | 400; no Batch created | Returned 400 `Expiry date is required for this product` | PASS — E-BATCH-05 |
| F02-BATCH-06 | Non-perishable Product, null expiry, quantity 9 | 201; batch active and sellable | Returned 201; subsequent summary reported the 9 units sellable | PASS — E-BATCH-06 |
| F02-BATCH-07 | Duplicate batch code in same Product | 409 | Returned 409 | PASS — E-BATCH-07 |
| F02-BATCH-08 | PATCH remaining quantity `-1` | 400; quantity unchanged | Returned 400; existing quantity remained unchanged | PASS — E-BATCH-08 |
| F02-BATCH-09 | PATCH remaining quantity above initial | 400 | Returned 400 `Remaining quantity cannot exceed initial quantity` | PASS — E-BATCH-09 |
| F02-BATCH-10 | Create batch with quantity 0 and -1 | 400 | Both requests returned 400 | PASS — E-BATCH-10 |
| F02-BATCH-11 | PATCH status `EXPIRED` | 400; only ACTIVE/BLOCKED accepted | Returned 400 `Invalid batch status` | PASS — E-BATCH-11 |
| F02-BATCH-12 | Read expired active batch and Summary | Batch remains readable; expired units excluded from sellable | Expired batch was returned in list; Summary classified 4 as expired and excluded them from sellable | PASS — E-BATCH-12 |
| F02-BATCH-13 | Store B token PATCH Store A batch | 404; no change | Returned 404; Store A follow-up Summary remained consistent | PASS — E-BATCH-13 |
| F02-BATCH-14 | GET and POST batches for nonexistent Product | 404; no orphan | Both GET and POST returned 404 | PASS — E-BATCH-14 |
| F02-BATCH-15 | PATCH remaining 9→0; GET and Summary | 200/204; zero persists and derived stock drops immediately | GET showed 0; Summary changed from total/sellable 17 to 8 | PASS — E-BATCH-15 |

### F02-06 — Product status

| TC ID | Endpoint / method; input | Expected HTTP / behavior | Actual HTTP / behavior | Result / evidence |
|---|---|---|---|---|
| F02-STATUS-01 | `PATCH /api/products/{id}/status`, ACTIVE→INACTIVE | 200/204; GET reflects inactive | Returned 200 with INACTIVE | PASS — E-STATUS-01 |
| F02-STATUS-02 | Same endpoint, INACTIVE→ACTIVE | 200/204; GET reflects active | Returned 200 with ACTIVE | PASS — E-STATUS-02 |
| F02-STATUS-03 | List with ACTIVE/INACTIVE filters after transitions | 200; results match state | Both filters returned the appropriate products | PASS — E-STATUS-03 |
| F02-STATUS-04 | PATCH status `UNKNOWN` | 400; old status remains | Returned 400 `Invalid product status` | PASS — E-STATUS-04 |
| F02-STATUS-05 | Store A token PATCH Store B Product status | 404; no change | Returned 404 `Product not found` | PASS — E-STATUS-05 |

### F02-08 — Inventory Summary

| TC ID | Endpoint / method; input | Expected HTTP / behavior | Actual HTTP / behavior | Result / evidence |
|---|---|---|---|---|
| F02-SUM-01 | Summary for Product with no batches | 200; every quantity is zero | Returned 200 with all five quantities equal to 0 | PASS — E-SUM-01 |
| F02-SUM-02 | Fixture; `expiringWithinDays=7` before update | 200; total 33, sellable 24, expired 4, blocked 5, soon 14 | Returned exactly 33/24/4/5/14 | PASS — E-SUM-02 |
| F02-SUM-03 | Same fixture; `expiringWithinDays=0` | 200; expiring soon includes today: 6 | Returned expiringSoonQuantity 6 | PASS — E-SUM-03 |
| F02-SUM-04 | Active batch expired yesterday, remaining 4 | Expired=4; not sellable | Summary returned expiredQuantity 4 and sellableQuantity excluded those units | PASS — E-SUM-04 |
| F02-SUM-05 | Blocked batch expired yesterday, remaining 5 | Blocked=5; no double count as expired | Summary returned blockedQuantity 5 and expiredQuantity remained 4 | PASS — E-SUM-05 |
| F02-SUM-06 | Active batch expires today, remaining 6; lookahead 0 | Sellable and expiring soon include 6 | Summary returned sellable 24 and expiringSoon 6 | PASS — E-SUM-06 |
| F02-SUM-07 | Active batch expires today+3, remaining 8; lookahead 7 | Included in sellable and expiring soon | Initial fixture Summary included the 8 units in sellable 24 and expiringSoon 14 | PASS — E-SUM-07 |
| F02-SUM-08 | Active batch expires today+10, remaining 10; lookahead 7 | Sellable, not expiring soon | Initial Summary had sellable 24 and expiringSoon 14, excluding the +10-day batch from soon | PASS — E-SUM-08 |
| F02-SUM-09 | Non-perishable active batch, null expiry, remaining 9 | 200; 9/9/0/0/0 | Returned total 9, sellable 9, expired 0, blocked 0, soon 0 | PASS — E-SUM-09 |
| F02-SUM-10 | PATCH fixture batch 8→3; read Summary | 200; total 28, sellable 19, expired 4, blocked 5, soon 9 | Returned exactly 28/19/4/5/9 | PASS — E-SUM-10 |
| F02-SUM-11 | `GET .../inventory-summary?expiringWithinDays=-1` | 400 | Swagger UI marked the query input invalid (`minimum 0`) and did not send an HTTP request | BLOCKED — E-SUM-11; API response not claimed |
| F02-SUM-12 | Store A token Summary for Store B Product | 404 | Returned 404 `Product not found` | PASS — E-SUM-12 |
| F02-SUM-13 | Summary without `expiringWithinDays` | Default is 7; with updated fixture soon=9 | Returned 200 and expiringSoonQuantity 9, matching the 7-day default | PASS — E-SUM-13 |
| F02-SUM-14 | Compare quantity groups | total = blocked + expired + sellable | Baseline: 33=5+4+24; after update: 28=5+4+19 | PASS — E-SUM-14 |

## Defect and verification notes

- **Resolved defect:** A product write using a real foreign-Store category returned 400 and disclosed “Category does not belong to this Store.” The ownership lookup in `ProductService` treated “not found in this Store” as invalid input. It now returns generic 404 `Category not found`. A regression integration test first failed with 400, then passed after the fix. The updated application was restarted and the same request was rerun through Swagger UI, returning 404. Details: `F02_SWAGGER_ISSUES.md`.
- **Test-case correction:** `CategoryRequest` requires both `name` and `status`, but F02-CAT-01 originally omitted status. The approved test case now sends `status=ACTIVE`; the initial incomplete request returned 400 and the corrected request returned 201.
- **Summary field name:** The API contract uses `sellableQuantity`; backlog fixture prose sometimes says “saleableQuantity.” Assertions above use the actual OpenAPI/DTO field name.
- **Timezone and boundary:** Store timezone was set to `Asia/Ho_Chi_Minh`; fixture dates were calculated relative to the Store-local date 2026-10-16. Expiration today was included as sellable and in expiring-soon, including a zero-day lookahead.
- **Build and migration:** Final `mvn -q verify` passed after the fix. Surefire reported 25 tests across CatalogIntegrationTest (7), CatalogDevSeederTest (1), AuthIntegrationTest (14), and GoogleRegistrationTest (3), with 0 failures, errors, or skips. Flyway validated and applied V1–V3 on clean isolated H2 during application startup and integration-test startup. A separate same-persistent-database restart was not run.
- **CodeGraph:** Available and responsive. The index reported 134 files, 2,379 nodes, and 5,729 edges. Exploration traced Product/Category/Batch controllers through services and repositories, and confirmed `CatalogAccess` delegates store resolution to existing `StoreScope`; F02 controllers require `STORE_ADMIN`. The updated `ProductService` was present in the index response. No CodeGraph files were added to Git.
- **Interactive Swagger status:** F02 endpoints and bearer authorization appeared in Swagger UI; category, product, batch, summary, validation, and security requests were executed there. The negative lookahead case remains BLOCKED by Swagger UI client-side validation, and the already-migrated persistent-database restart case remains NOT RUN.

## Counts

| Status | Count |
|---|---:|
| PASS | 70 |
| FAIL (unresolved) | 0 |
| BLOCKED | 1 |
| NOT RUN | 1 |
| **Total test cases** | **72** |

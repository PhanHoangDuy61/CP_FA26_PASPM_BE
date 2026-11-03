# PASPM F02 — Swagger UI Test Cases (Model A / Inventory Batch)

**Phạm vi:** F02-01..F02-08, Backend API + Swagger UI, không FE/Postman/E2E; không STOCK_MOVEMENT, không nguyên liệu/recipe, không Custom Weight.
**Nguồn:** `PASPM_F02_Inventory_Batch_API_Swagger_Backlog_v3.md` và `PASPM_Complete_ERD_F02_Inventory_Batch_v3.drawio`.
**Trạng thái ban đầu:** TẤT CẢ `NOT RUN`. Đây là test design, **không phải kết quả đã chạy**.

## Chuẩn bị môi trường và dữ liệu
- Chạy trên **development/test DB**, không chạy trên production. Sao lưu/ghi nhận dữ liệu nếu test trên DB có người khác sử dụng.
- Cần F01 hoạt động: token Store Admin của **Store A**, Store Admin của **Store B**, và token role **không có quyền** F02. Không ghi token/mật khẩu vào báo cáo.
- Mở Swagger UI ở URL thực tế của project; kiểm tra `/v3/api-docs` (hoặc path tùy cấu hình), OpenAPI security scheme, Authorize.
- Chuẩn bị **Store A/B** có dữ liệu seed tách biệt; Category A/B, Product A/B, perishable Product P1 (`requiresExpiry=true`) và non-perishable P2 (`requiresExpiry=false`).
- **Không hardcode ID**: lấy ID từ response vừa tạo; ghi vào biến thao tác/bảng ghi chép test.
- `today` = LocalDate theo timezone của Store. Nếu Store chưa có timezone, thống nhất **timezone MVP** với team trước khi tính expected.
- Dùng **một fixture riêng biệt cho SUMMARY** để không bị ảnh hưởng bởi các test Create/Patch khác. Ngày nên tạo động tương đối với today, không hardcode ngày trong quá khứ.
- HTTP lỗi mục tiêu theo backlog: `400` invalid, `401` unauthenticated, `403` sai role, `404` nonexistent/other-store, `409` trùng dữ liệu. Nếu API contract thực tế khác, ghi rõ difference, không tự mặc định PASS.
- Response cập nhật `200/204` tùy API contract đã công bố. Kiểm tra persisted value bằng GET; nếu POST thành công mục tiêu là `201`.

## Bộ fixture tính tồn kho (dùng riêng cho F02-08)
**Product P1:** `requiresExpiry=true`, status ACTIVE, thuộc Store A. Tạo 5 batch không trùng code:

| Batch | Status | expiryDate | remainingQuantity | Phân loại |
|---|---|---|---:|---|
| P1-A | ACTIVE | today − 1 day | 4 | expired |
| P1-B | ACTIVE | today | 6 | saleable + expiringSoon |
| P1-C | ACTIVE | today + 3 days | 8 | saleable + expiringSoon |
| P1-D | ACTIVE | today + 10 days | 10 | saleable |
| P1-E | BLOCKED | today − 1 day | 5 | blocked (KHÔNG cộng expired) |

**Expected `GET .../inventory-summary?expiringWithinDays=7`:** `totalQuantity=33`, `saleableQuantity=24`, `expiredQuantity=4`, `blockedQuantity=5`, `expiringSoonQuantity=14`.
**Expected `expiringWithinDays=0`:** `expiringSoonQuantity=6` (expiry today còn được tính bán).
**Sau PATCH P1-C từ 8 về 3:** `totalQuantity=28`, `saleableQuantity=19`, `expiredQuantity=4`, `blockedQuantity=5`, `expiringSoonQuantity=9`.
**Product P2:** `requiresExpiry=false`, một batch ACTIVE `expiryDate=null`, `remainingQuantity=9`: `total=9`, `saleable=9`, `expired=0`, `blocked=0`, `expiringSoon=0`.

## Danh sách test case
**Hướng dẫn ghi kết quả:** Mỗi test ghi `Actual HTTP`, `Actual response`, `PASS/FAIL/BLOCKED/NOT RUN`, bằng chứng link/ảnh đã che thông tin nhạy cảm vào `F02_SWAGGER_TEST_REPORT.md`.

### F02-01 — Flyway, schema, seed (xác minh DB + GET khi API chạy)
**Endpoint/đối tượng:** `Flyway startup, DB schema và API đọc`

| TC ID | Prio | Tiền điều kiện | Thực hiện trên Swagger/DB | Kết quả mong đợi |
|---|---|---|---|---|
| F02-DB-01 | P0 | DB sạch có migration F01 | Khởi động backend với DB dev/test sạch | Migration mới thành công; STORE không được tạo trùng; 3 bảng CATEGORY/PRODUCT/INVENTORY_BATCH tồn tại |
| F02-DB-02 | P0 | V1 đã áp dụng | Chạy lại ứng dụng trên DB đã migrate | Không sửa V1; Flyway không lỗi checksum; dữ liệu F01 vẫn còn |
| F02-DB-03 | P0 | Đã có dữ liệu seed | GET danh sách category/product/batch bằng Store Admin A | Seed thuộc đúng Store; có batch ACTIVE còn hạn, expired, BLOCKED và non-perishable |
| F02-DB-04 | P0 | Đã có product với SKU X tại Store A | Thử tạo SKU X ở cùng Store A, sau đó Store B | Cùng Store bị chặn (409 theo contract); Store khác có thể dùng SKU X |
| F02-DB-05 | P0 | Đã có product với batchCode Y | Thử tạo batchCode Y cùng product và product khác | Trùng trong 1 product bị chặn (409); product khác được phép dùng cùng batchCode |
| F02-DB-06 | P0 | Có DB quyền kiểm tra schema | Đối chiếu cột/constraint với ERD v3 | Không có PRODUCT.stock_quantity; có FK và check hợp lệ; không có STOCK_MOVEMENT |

### F02 — Security/Store isolation (áp dụng xuyên suốt F02-02..08)
**Endpoint/đối tượng:** `Toàn bộ /api/categories, /api/products, /api/inventory-batches`

| TC ID | Prio | Tiền điều kiện | Thực hiện trên Swagger/DB | Kết quả mong đợi |
|---|---|---|---|---|
| F02-SEC-01 | P0 | Không gửi access token | Gọi GET /api/products, GET /api/categories | 401; không rò dữ liệu |
| F02-SEC-02 | P0 | Token người dùng không có quyền quản lý F02 | Gọi POST /api/products và PATCH batch | 403 theo RBAC hiện hành |
| F02-SEC-03 | P0 | Store A/B có dữ liệu tách biệt | Store A gọi GET Category/Product/Batch của Store B bằng ID | 404 theo backlog v3 (không lộ sự tồn tại record) |
| F02-SEC-04 | P0 | Token Store Admin A | Store A gọi PUT/PATCH vào product, category, batch thuộc Store B | 404 theo backlog v3; dữ liệu Store B không thay đổi |
| F02-SEC-05 | P0 | Token Store Admin A | GET list/search sản phẩm/danh mục có seed của cả Store | Chỉ trả record thuộc Store A; totalItems cũng chỉ tính Store A |
| F02-SEC-06 | P0 | Body giả mạo storeId của Store B | POST Category/Product/Batch hợp lệ với storeId tùy ý | Không thể tạo record tại Store B; storeId lấy từ principal hoặc input bị từ chối |
| F02-SEC-07 | P0 | Token hết hạn/không hợp lệ | Authorize bằng token sai rồi gọi GET | 401; không truy vấn thành công |

### F02-02 — Category Management
**Endpoint/đối tượng:** `GET/POST/PUT/PATCH /api/categories`

| TC ID | Prio | Tiền điều kiện | Thực hiện trên Swagger/DB | Kết quả mong đợi |
|---|---|---|---|---|
| F02-CAT-01 | P0 | Admin A hợp lệ | POST category name="Đồ uống", status="ACTIVE" | 201; response có id; GET detail khớp |
| F02-CAT-02 | P0 | Category mới tạo | GET list và GET /api/categories/{id} | 200; tìm thấy category đúng Store |
| F02-CAT-03 | P0 | Category đang ACTIVE | PUT đổi name thành "Đồ uống lạnh" | 200/204 theo contract; GET phản ánh tên mới |
| F02-CAT-04 | P0 | Category đang ACTIVE | PATCH status thành INACTIVE | 200/204; GET phản ánh INACTIVE; không hard-delete |
| F02-CAT-05 | P0 | Admin A hợp lệ | POST/PUT với name rỗng hoặc chỉ khoảng trắng | 400; dữ liệu không thay đổi |
| F02-CAT-06 | P0 | ID category không tồn tại | GET/PUT/PATCH id ngẫu nhiên | 404 theo contract |
| F02-CAT-07 | P0 | Category có Product tham chiếu | PATCH category INACTIVE, sau đó GET product | Category và Product vẫn tồn tại; không xóa cứng |

### F02-03 — Product List/Search/Detail
**Endpoint/đối tượng:** `GET /api/products và GET /api/products/{id}`

| TC ID | Prio | Tiền điều kiện | Thực hiện trên Swagger/DB | Kết quả mong đợi |
|---|---|---|---|---|
| F02-READ-01 | P0 | Store A có >= 3 products | GET /api/products | 200; chỉ Product Store A |
| F02-READ-02 | P0 | Product sku DRINK-001, tên Nước cam | GET list ?search=DRINK-001; sau đó ?search=nước | 200; kết quả tìm đúng SKU/tên theo search contract |
| F02-READ-03 | P0 | Có hai category trong Store A | GET list ?categoryId={categoryAId} | 200; chỉ products thuộc Category A |
| F02-READ-04 | P0 | Có Product ACTIVE và INACTIVE | GET ?status=ACTIVE, ?status=INACTIVE | Bộ lọc đúng; inactive không bị xóa dữ liệu |
| F02-READ-05 | P0 | Có >= 3 products | GET ?page=...&pageSize=... hai trang liên tiếp | Danh sách, số lượng và totalItems nhất quán theo API contract; không lặp record |
| F02-READ-06 | P0 | Product A có các batches với remaining [4,6] | GET /api/products/{id} hoặc list nếu có stockQuantity | Nếu DTO có stockQuantity, bằng 10 (tổng remaining), không lấy từ cột Product |
| F02-READ-07 | P0 | ID product không tồn tại | GET /api/products/{id} | 404 |
| F02-READ-08 | P0 | Giá vốn là dữ liệu nhạy cảm | Kiểm tra OpenAPI/API quản trị và public routes | F02 là API quản trị; không vô tình xuất costPrice qua endpoint công khai |

### F02-04 — Product Create/Update
**Endpoint/đối tượng:** `POST /api/products, PUT /api/products/{id}`

| TC ID | Prio | Tiền điều kiện | Thực hiện trên Swagger/DB | Kết quả mong đợi |
|---|---|---|---|---|
| F02-WRITE-01 | P0 | Category A hợp lệ | POST product SKU=FOOD-001, sellingPrice=30000, costPrice=16000, unit="portion", requiresExpiry=true | 201; GET thấy đúng field; chưa tự có batch |
| F02-WRITE-02 | P0 | Product vừa tạo | PUT đổi tên/giá hợp lệ | 200/204; GET xác nhận dữ liệu được lưu |
| F02-WRITE-03 | P0 | Store A đã có SKU FOOD-001 | POST sản phẩm trùng SKU trong Store A | 409 theo contract; không thêm record |
| F02-WRITE-04 | P0 | Category thuộc Store B | POST Product Store A với categoryId Store B | Bị từ chối (404 theo store-hiding policy); không lưu sai Store |
| F02-WRITE-05 | P0 | Admin A hợp lệ | POST Product với sellingPrice=-1 hoặc costPrice=-1 | 400; không lưu |
| F02-WRITE-06 | P0 | Admin A hợp lệ | POST Product thiếu name, sku hoặc unit theo schema thực tế | 400; field validation rõ ràng |
| F02-WRITE-07 | P0 | Admin A hợp lệ | POST Product sellingPrice=0, costPrice=0 | Thành công nếu hợp đồng cho phép >=0; không tự áp đặt >0 |
| F02-WRITE-08 | P0 | Admin A hợp lệ | POST/PUT gửi stockQuantity=999 qua body | Không đổi stock; extra field bị từ chối hoặc bỏ qua có kiểm soát theo DTO |
| F02-WRITE-09 | P0 | Product non-perishable hiện có batch ACTIVE expiryDate=null | PUT requiresExpiry=true khi batch cũ có expiryDate=null | Không được tạo trạng thái mâu thuẫn; 400/409 theo contract đã chốt |
| F02-WRITE-10 | P0 | Product chưa có category | POST/PUT categoryId=null nếu schema cho phép | Product tạo/cập nhật thành công, giữ store scope |

### F02-05 — Inventory Batch
**Endpoint/đối tượng:** `GET/POST /api/products/{productId}/batches; PATCH /api/inventory-batches/{batchId}`

| TC ID | Prio | Tiền điều kiện | Thực hiện trên Swagger/DB | Kết quả mong đợi |
|---|---|---|---|---|
| F02-BATCH-01 | P0 | Product perishable tại Store A | POST batchCode=LOT-01, initialQuantity=20, expiryDate=today+10 | 201; remainingQuantity mặc định 20; status ACTIVE |
| F02-BATCH-02 | P0 | Product có LOT-01 | GET /api/products/{id}/batches | 200; thấy LOT-01 đúng remaining/status |
| F02-BATCH-03 | P0 | Batch còn lại 20 | PATCH remainingQuantity=12 | 200/204; GET list trả đúng 12 (giá trị tuyệt đối, không phải trừ 12) |
| F02-BATCH-04 | P0 | Batch ACTIVE còn 12 | PATCH status=BLOCKED rồi ACTIVE | GET list trả đúng trạng thái; trạng thái chỉ ACTIVE/BLOCKED |
| F02-BATCH-05 | P0 | Product perishable | POST batch thiếu expiryDate/null | 400; không tạo batch |
| F02-BATCH-06 | P0 | Product non-perishable | POST batch expiryDate=null, initialQuantity=9 | 201; batch ACTIVE; số lượng này sellable |
| F02-BATCH-07 | P0 | Product có LOT-01 | POST batch trùng batchCode trong cùng Product | 409 theo contract; không tạo thêm |
| F02-BATCH-08 | P0 | Batch có initialQuantity=20 | PATCH remainingQuantity=-1 | 400; số lượng giữ nguyên |
| F02-BATCH-09 | P0 | Batch có initialQuantity=20 | PATCH remainingQuantity=21 | 400; số lượng giữ nguyên |
| F02-BATCH-10 | P0 | Product bất kỳ | POST initialQuantity=0 hoặc -1 | 400; không tạo batch |
| F02-BATCH-11 | P0 | Batch ACTIVE | PATCH status=EXPIRED/UNKNOWN | 400; không phát sinh status thứ ba |
| F02-BATCH-12 | P0 | Product perishable có batch expiryDate=yesterday | GET batches và Inventory Summary | Batch vẫn tồn tại/đọc được; expiredQuantity tăng, saleableQuantity không tăng |
| F02-BATCH-13 | P0 | Store A có batch thuộc Store B | PATCH batch Store B với token A | 404 theo contract; remaining/status không đổi |
| F02-BATCH-14 | P0 | Product không tồn tại | GET/POST /api/products/{id}/batches | 404; POST không tạo batch mồ côi |
| F02-BATCH-15 | P0 | Batch có initialQuantity=20 | PATCH remainingQuantity=0 | 200/204; được phép hết hàng; summary cập nhật về 0 phần batch này |

### F02-06 — Product Status (P1)
**Endpoint/đối tượng:** `PATCH /api/products/{id}/status`

| TC ID | Prio | Tiền điều kiện | Thực hiện trên Swagger/DB | Kết quả mong đợi |
|---|---|---|---|---|
| F02-STATUS-01 | P1 | Product ACTIVE | PATCH status=INACTIVE | 200/204; GET detail trả INACTIVE |
| F02-STATUS-02 | P1 | Product INACTIVE | PATCH status=ACTIVE | 200/204; GET detail trả ACTIVE |
| F02-STATUS-03 | P1 | Product ACTIVE/INACTIVE | GET /api/products?status=ACTIVE/INACTIVE | Filter khớp sau mỗi thao tác |
| F02-STATUS-04 | P1 | Product hợp lệ | PATCH status=UNKNOWN | 400; status cũ giữ nguyên |
| F02-STATUS-05 | P1 | Product Store B | Token A PATCH status product B | 404; Product Store B không thay đổi |

### F02-08 — Inventory Summary (Derived Stock)
**Endpoint/đối tượng:** `GET /api/products/{id}/inventory-summary?expiringWithinDays=N`

| TC ID | Prio | Tiền điều kiện | Thực hiện trên Swagger/DB | Kết quả mong đợi |
|---|---|---|---|---|
| F02-SUM-01 | P0 | Product P1 không có batch | GET summary P1 | 200; total/saleable/expired/blocked/expiringSoon đều 0 |
| F02-SUM-02 | P0 | Product P1 có bộ fixture chuẩn ở mục dưới | GET summary ?expiringWithinDays=7 | 200; total=33, saleable=24, expired=4, blocked=5, expiringSoon=14 |
| F02-SUM-03 | P0 | Cùng fixture P1 | GET summary ?expiringWithinDays=0 | expiringSoon=6 (batch hết hạn hôm nay vẫn tính sellable) |
| F02-SUM-04 | P0 | Batch ACTIVE expiryDate=yesterday còn 4 | GET summary | expiredQuantity=4; không nằm trong saleable |
| F02-SUM-05 | P0 | Batch BLOCKED expiryDate=yesterday còn 5 | GET summary | blockedQuantity=5; không cộng lại vào expiredQuantity |
| F02-SUM-06 | P0 | Batch ACTIVE expiryDate=today còn 6 | GET summary ?expiringWithinDays=0 | 6 vẫn saleable và expiringSoon (hạn sử dụng inclusive) |
| F02-SUM-07 | P0 | Batch ACTIVE expiryDate=today+3 còn 8 | GET summary ?expiringWithinDays=7 | 8 tính vào saleable và expiringSoon |
| F02-SUM-08 | P0 | Batch ACTIVE expiryDate=today+10 còn 10 | GET summary ?expiringWithinDays=7 | 10 tính saleable, không tính expiringSoon |
| F02-SUM-09 | P0 | Product P2 non-perishable, ACTIVE batch null-expiry remaining=9 | GET summary P2 | total=9; saleable=9; expired=0; blocked=0; expiringSoon=0 |
| F02-SUM-10 | P0 | Fixture P1; batch today+3 ban đầu remaining=8 | PATCH batch remaining=3; GET summary P1 | total=28; saleable=19; expired=4; blocked=5; expiringSoon=9 |
| F02-SUM-11 | P0 | Có fixture P1 | GET summary ?expiringWithinDays=-1 | 400, không trả giá trị summary sai |
| F02-SUM-12 | P0 | Product thuộc Store B | Token A GET summary Product Store B | 404 theo backlog, không rò số lượng |
| F02-SUM-13 | P0 | Product P1, query expiringWithinDays không cung cấp | GET summary không truyền expiringWithinDays | Kiểm tra default thực tế: nếu đã thống nhất default 7 thì số liệu bằng SUM-02; nếu chưa có default, ghi open API-contract decision (không tự coi PASS) |
| F02-SUM-14 | P0 | Cùng fixture P1 | Đối chiếu tổng 3 nhóm số lượng | totalQuantity = blockedQuantity + expiredQuantity + saleableQuantity (33=5+4+24) |

## Quy trình thực thi đề xuất (không làm bẩn fixture Summary)
1. Xác minh Migration/seed và Swagger OpenAPI.
2. Đăng nhập Store Admin A qua F01; chạy success cases Category → Product → Batch → Summary.
3. Tạo **fixture Summary riêng**, đo số ban đầu, PATCH 8 → 3, kiểm lại.
4. Chạy negative cases với request payload invalid; xác nhận không thay đổi DB.
5. Đổi sang token Store B và role thiếu quyền; kiểm tra cross-store/403/401.
6. Chạy Product Status P1 nếu F02-06 đã code. Nếu chưa, đánh dấu BLOCKED/PENDING chứ không PASS.
7. Khi test PATCH/PUT trả 204, dùng GET và/hoặc DB để xác minh giá trị lưu thực.

## Giới hạn của kiểm thử qua Swagger UI
- Swagger UI có thể gọi API và quan sát response, nhưng **không tự chứng minh** tính an toàn khi nhiều request đồng thời. Các case concurrency cần test backend riêng; không đánh dấu đã kiểm chứng chỉ nhờ PATCH tuần tự.
- Migration/schema constraint là DB/startup verification, **không phải** Swagger UI tests; báo cáo tách hai loại bằng chứng.
- Nếu Codex không có browser/UI tool, kiểm tra OpenAPI một cách riêng biệt và đánh dấu interactive Swagger cases `BLOCKED`; **không dùng curl/Postman thay thế rồi ghi PASS Swagger UI**.
- Không tự ý bật tắt Spring Security chỉ để chạy test. Không sửa production data.

## Mẫu báo cáo thực thi
| TC ID | Run date/time (timezone) | Request/ID (redacted) | HTTP actual | Actual short result | Status | Evidence |
|---|---|---|---|---|---|---|
| F02-CAT-01 | — | — | — | — | NOT RUN | — |

## Tổng quan
- **Số test case thiết kế: 72**.
- **P0/P1:** Ưu tiên P0 trước; P1 F02-06 vẫn phải kiểm tra nếu feature đã triển khai.
- **PASS:** Chỉ khi đã chạy và expected đúng. **BLOCKED:** Thiếu F01/tokens/DB/browser hoặc endpoint chưa triển khai. **FAIL:** Đã chạy và kết quả sai.

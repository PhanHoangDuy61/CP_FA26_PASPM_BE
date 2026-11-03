# F02 — Product & Inventory Management (Ready-to-sell Food & Beverages)

**Scope:** Database + Backend API + Swagger UI manual verification. **Chưa làm FE, Postman hoặc E2E.**

**Đã chốt mô hình:** Quản lý tồn kho theo sản phẩm bán sẵn (Model A). **Không có STOCK_MOVEMENT**, không quản lý nguyên liệu/recipe.

**Schema tham chiếu:** `PASPM_Complete_ERD_F02_Inventory_Batch_v3.drawio` (21 bảng, 35 quan hệ).

## Quy tắc chung (P0)
- F01 migration đã tạo `STORE`. F02 chỉ tham chiếu Store, không tạo bảng Store thêm lần nữa.
- Bảng `PRODUCT` không lưu `stock_quantity`; mọi tồn kho được suy ra từ `INVENTORY_BATCH`. UI sau này có thể dùng `stockQuantity` dạng field trong response (derived).
- Hạn sử dụng thuộc từng batch, không thuộc Product. Product có `requires_expiry`; non-perishable có thể không có ngày hết hạn.
- Batch `status` chỉ là `ACTIVE | BLOCKED`; hết hạn là tình trạng tính từ `expiry_date` và ngày hiện tại của cửa hàng.
- Chưa tự động trừ kho khi bán, chưa lưu lịch sử stock adjustment; chỉnh lượng tồn batch là thay đổi tuyệt đối qua PATCH.
- Inventory Weight (DOC), Expiry Weight và Seasonality Weight **không tính trong F02**; F02 chỉ cung cấp dữ liệu để F03 sử dụng.
- `store_id` xác định từ người đăng nhập; quản lý phân quyền của Store từ F01.
- Dùng tiền kiểu DECIMAL, giá/tồn kho không âm, không cho record của cửa hàng khác truy cập.
- Ngày hết hạn xử lý theo ngày địa phương của Store. Nếu chưa có store timezone, nhóm phải thống nhất một timezone chuẩn cho MVP trước khi implement.
- Quy tắc HTTP đề xuất: 400 invalid input, 401 unauthenticated, 403 unauthorized role, 404 not found/other-store, 409 duplicates.

## Bảng task

| ID | Task | Priority |
|---|---|---|
| F02-01 | Catalog + Inventory Batch Migration & Seed | P0 |
| F02-02 | Category Management APIs | P0 |
| F02-03 | Product List / Search / Detail APIs | P0 |
| F02-04 | Product Create / Update APIs | P0 |
| F02-05 | Inventory Batch Management APIs | P0 |
| F02-06 | Product Activation / Deactivation API | P1 |
| F02-08 | Inventory Summary API (Derived Stock) | P0 |
| F02-07 | Swagger Verification — Complete F02 | P0 |

# [F02-01] Catalog + Inventory Batch Migration & Seed

**Epic:** F02 – Product & Inventory Management  
**Priority:** P0  
**Type:** Backend / Database / Swagger  
**Dependencies:** F01-01 (STORE shared migration)

**Mục tiêu:** Cập nhật schema F02 theo tồn kho sản phẩm bán sẵn và lô hàng. Không tạo bảng `STORE` lần thứ hai.

**Công việc**
- [ ] Migration mới (ví dụ `V2__f02_catalog_inventory_batch.sql`): tạo `CATEGORY`, `PRODUCT`, `INVENTORY_BATCH` theo Logical ERD v3.
- [ ] `PRODUCT`: `product_id`, `store_id`, `category_id?`, `sku`, `name`, `unit`, `requires_expiry`, `selling_price`, `cost_price`, `status`.
- [ ] `INVENTORY_BATCH`: `batch_id`, `product_id`, `batch_code`, `received_at`, `expiry_date?`, `initial_quantity`, `remaining_quantity`, `status`, `created_at`, `updated_at`.
- [ ] **Không lưu `PRODUCT.stock_quantity`**; số lượng tổng hợp được tính từ các batch.
- [ ] Unique `(store_id, sku)` ở Product; unique `(product_id, batch_code)` ở Inventory Batch; PK/FK đầy đủ.
- [ ] CHECK/validation: giá >= 0, quantity nguyên >= 0; `remaining_quantity <= initial_quantity`; `initial_quantity > 0`.
- [ ] `requires_expiry=true` thì mỗi batch phải có `expiry_date`; nếu false có thể null.
- [ ] Seed tối thiểu 2 stores, categories/products và các batch: còn hạn, gần hết hạn, đã hết hạn, không có hạn, bị khóa.
- [ ] Đảm bảo Product–Category cùng Store (service; ưu tiên DB composite FK nếu phù hợp thiết kế).

**Kiểm tra trên Swagger/DB**
- [ ] Startup + migration thành công, dữ liệu seed hiển thị bằng GET khi các API đã sẵn sàng.
- [ ] Thử tạo SKU trùng / batch_code trùng của cùng Product → bị từ chối.
- [ ] FK không cho tạo batch với product không tồn tại.
- [ ] Không chỉnh sửa migration V1 đã chạy, không tạo lại STORE.

## Definition of Done (API + Swagger only)
- [ ] API chạy với database development/test, migration áp dụng được.
- [ ] Swagger UI hiển thị endpoint, schema/DTO, error responses và auth nếu cần.
- [ ] Chạy thủ công các ca Swagger trong task, ghi kết quả thực tế vào issue/PR.
- [ ] Dữ liệu thay đổi được kiểm tra bằng GET lại hoặc truy vấn DB.
- [ ] Code build thành công, PR được review/merge.
- [ ] Không yêu cầu Frontend, Postman hoặc E2E trong sprint này.


---

# [F02-02] Category Management APIs

**Epic:** F02 – Product & Inventory Management  
**Priority:** P0  
**Type:** Backend / Database / Swagger  
**Dependencies:** F02-01, F01-04 (RBAC)

**Endpoints**
- `GET /api/categories`, `GET /api/categories/{id}`
- `POST /api/categories`, `PUT /api/categories/{id}`
- `PATCH /api/categories/{id}/status`

**Công việc**
- [ ] Chỉ thao tác Category thuộc Store từ authentication context.
- [ ] DTO: `name`, `status`; validate name không rỗng.
- [ ] Không xóa cứng Category đang được Product tham chiếu; sử dụng trạng thái inactive.

**Swagger test**
- [ ] Store Admin POST → `201`, GET thấy dữ liệu, PUT/PATCH cập nhật thành công.
- [ ] Tên rỗng → `400`, id không tồn tại → `404`.
- [ ] Không token → `401`, sai role → `403`; Store A không xem/sửa Category Store B.

## Definition of Done (API + Swagger only)
- [ ] API chạy với database development/test, migration áp dụng được.
- [ ] Swagger UI hiển thị endpoint, schema/DTO, error responses và auth nếu cần.
- [ ] Chạy thủ công các ca Swagger trong task, ghi kết quả thực tế vào issue/PR.
- [ ] Dữ liệu thay đổi được kiểm tra bằng GET lại hoặc truy vấn DB.
- [ ] Code build thành công, PR được review/merge.
- [ ] Không yêu cầu Frontend, Postman hoặc E2E trong sprint này.


---

# [F02-03] Product List / Search / Detail APIs

**Epic:** F02 – Product & Inventory Management  
**Priority:** P0  
**Type:** Backend / Database / Swagger  
**Dependencies:** F02-01; F01-04 (RBAC); dữ liệu batch seed

**Endpoints**
- `GET /api/products?search=&categoryId=&status=&page=&pageSize=`
- `GET /api/products/{id}`

**Công việc**
- [ ] Dùng store scope; search theo SKU/tên, filter danh mục/trạng thái, phân trang.
- [ ] DTO quản trị: `id`, `sku`, `name`, `unit`, `requiresExpiry`, `category`, `sellingPrice`, `costPrice`, `status`.
- [ ] `stockQuantity` trong response, nếu cần cho FE mock, là **giá trị tổng hợp từ các batch**, không phải cột Product.
- [ ] Không mở public API chứa `costPrice` trong sprint này.

**Swagger test**
- [ ] GET list + paging + search + filter đúng.
- [ ] GET detail đọc Product và category đúng Store.
- [ ] GET ở Store A không lộ Product Store B; id không tồn tại → `404`.
- [ ] `stockQuantity` khớp tổng `remainingQuantity` qua các batch của Product.

## Definition of Done (API + Swagger only)
- [ ] API chạy với database development/test, migration áp dụng được.
- [ ] Swagger UI hiển thị endpoint, schema/DTO, error responses và auth nếu cần.
- [ ] Chạy thủ công các ca Swagger trong task, ghi kết quả thực tế vào issue/PR.
- [ ] Dữ liệu thay đổi được kiểm tra bằng GET lại hoặc truy vấn DB.
- [ ] Code build thành công, PR được review/merge.
- [ ] Không yêu cầu Frontend, Postman hoặc E2E trong sprint này.


---

# [F02-04] Product Create / Update APIs

**Epic:** F02 – Product & Inventory Management  
**Priority:** P0  
**Type:** Backend / Database / Swagger  
**Dependencies:** F02-01, F02-02, F01-04

**Endpoints**
- `POST /api/products`
- `PUT /api/products/{id}`

**Công việc**
- [ ] Request: `sku`, `name`, `categoryId?`, `unit`, `requiresExpiry`, `sellingPrice`, `costPrice`, `status`.
- [ ] **Không nhận `stockQuantity` trong request tạo/sửa Product**. Tồn kho được tạo/cập nhật qua Inventory Batch APIs.
- [ ] Validate giá >= 0, SKU không trùng trong cùng Store, Category thuộc đúng Store.
- [ ] Thay đổi `requiresExpiry` trên Product đã có batch cần được kiểm soát (không cho tạo trạng thái trái dữ liệu batch).
- [ ] Lấy storeId từ auth context, không tin `storeId` gửi từ client.

**Swagger test**
- [ ] POST hợp lệ → `201`; PUT → `200/204`; GET trả dữ liệu mới.
- [ ] Duplicate SKU → `409` (hoặc contract đã thống nhất), giá âm → `400`.
- [ ] Category store khác và Product store khác bị từ chối.
- [ ] Gửi `stockQuantity` như tham số ghi phải bị bỏ qua có kiểm soát hoặc từ chối theo DTO contract (không thay đổi tồn kho).

## Definition of Done (API + Swagger only)
- [ ] API chạy với database development/test, migration áp dụng được.
- [ ] Swagger UI hiển thị endpoint, schema/DTO, error responses và auth nếu cần.
- [ ] Chạy thủ công các ca Swagger trong task, ghi kết quả thực tế vào issue/PR.
- [ ] Dữ liệu thay đổi được kiểm tra bằng GET lại hoặc truy vấn DB.
- [ ] Code build thành công, PR được review/merge.
- [ ] Không yêu cầu Frontend, Postman hoặc E2E trong sprint này.


---

# [F02-05] Inventory Batch Management APIs

**Epic:** F02 – Product & Inventory Management  
**Priority:** P0  
**Type:** Backend / Database / Swagger  
**Dependencies:** F02-01, F02-03, F02-04, F01-04

**Endpoints**
- `GET /api/products/{productId}/batches`
- `POST /api/products/{productId}/batches`
- `PATCH /api/inventory-batches/{batchId}`

**Công việc**
- [ ] Quản lý lô sản phẩm bán sẵn; không quản lý nguyên liệu và không tạo `STOCK_MOVEMENT`.
- [ ] Tạo lô: `batchCode`, `receivedAt`, `expiryDate?`, `initialQuantity`. Mặc định `remainingQuantity = initialQuantity`, `status = ACTIVE`.
- [ ] PATCH cập nhật số lượng còn lại (giá trị tuyệt đối) và/hoặc `status = ACTIVE | BLOCKED`. Không cộng/trừ tự động theo đơn hàng trong F02.
- [ ] Chặn tồn âm, `remainingQuantity > initialQuantity`; trùng batchCode trong cùng Product.
- [ ] `requiresExpiry=true` thì bắt buộc có expiryDate; trường hợp không cần hạn sử dụng được phép null.
- [ ] Batch thuộc Product thuộc Store đang đăng nhập; mọi GET/PATCH bảo đảm store scope.
- [ ] Dữ liệu hết hạn **không bị xóa**; tình trạng hết hạn suy ra từ `expiryDate`, không cần cột `EXPIRED` lưu cố định.

**Swagger test**
- [ ] POST batch → `201`, GET danh sách thấy batch; PATCH remainingQuantity/status → đọc lại đúng.
- [ ] Batch đã quá hạn vẫn đọc được nhưng không được tính vào `saleableQuantity`.
- [ ] Product khác Store / batch khác Store → không đọc/sửa được.
- [ ] expiryDate null khi requiresExpiry=true, quantity âm, remaining > initial → `400`.

## Definition of Done (API + Swagger only)
- [ ] API chạy với database development/test, migration áp dụng được.
- [ ] Swagger UI hiển thị endpoint, schema/DTO, error responses và auth nếu cần.
- [ ] Chạy thủ công các ca Swagger trong task, ghi kết quả thực tế vào issue/PR.
- [ ] Dữ liệu thay đổi được kiểm tra bằng GET lại hoặc truy vấn DB.
- [ ] Code build thành công, PR được review/merge.
- [ ] Không yêu cầu Frontend, Postman hoặc E2E trong sprint này.


---

# [F02-06] Product Activation / Deactivation API

**Epic:** F02 – Product & Inventory Management  
**Priority:** P1  
**Type:** Backend / Database / Swagger  
**Dependencies:** F02-03, F02-04, F01-04

**Endpoint**
- `PATCH /api/products/{id}/status`

**Công việc**
- [ ] Cho phép chuyển `ACTIVE` / `INACTIVE` theo trạng thái nhóm đã thống nhất.
- [ ] Không hard-delete Product hoặc Batch; giữ tham chiếu giao dịch lịch sử.
- [ ] Không cho sửa Product của Store khác.
- [ ] Khi Product INACTIVE, frontend/storefront sau này không được xem là sản phẩm có thể bán (đối chiếu API public ở giai đoạn tích hợp).

**Swagger test**
- [ ] ACTIVE → INACTIVE → ACTIVE, GET và list filter trả kết quả đúng.
- [ ] Giá trị status sai → `400`; Store khác bị từ chối.

## Definition of Done (API + Swagger only)
- [ ] API chạy với database development/test, migration áp dụng được.
- [ ] Swagger UI hiển thị endpoint, schema/DTO, error responses và auth nếu cần.
- [ ] Chạy thủ công các ca Swagger trong task, ghi kết quả thực tế vào issue/PR.
- [ ] Dữ liệu thay đổi được kiểm tra bằng GET lại hoặc truy vấn DB.
- [ ] Code build thành công, PR được review/merge.
- [ ] Không yêu cầu Frontend, Postman hoặc E2E trong sprint này.


---

# [F02-08] Inventory Summary API (Derived Stock)

**Epic:** F02 – Product & Inventory Management  
**Priority:** P0  
**Type:** Backend / Database / Swagger  
**Dependencies:** F02-05, F02-03

**Endpoint**
- `GET /api/products/{id}/inventory-summary?expiringWithinDays=7`

**Mục tiêu:** Trả dữ liệu tồn kho tính từ các lô cho F03 Custom Weight, không lưu cột stock tổng trong Product.

**Công thức / quy tắc**
- `totalQuantity` = tổng `remainingQuantity` của mọi batch thuộc Product.
- `blockedQuantity` = tổng remaining của batch có status BLOCKED.
- `expiredQuantity` = tổng remaining của batch ACTIVE có expiryDate < ngày hiện tại của cửa hàng.
- `saleableQuantity` = tổng remaining của batch ACTIVE có expiryDate null hoặc >= ngày hiện tại của cửa hàng.
- `expiringSoonQuantity` = phần saleable có expiryDate nằm trong [hôm nay, hôm nay + N ngày].
- Kiểm tra: `totalQuantity = blockedQuantity + expiredQuantity + saleableQuantity`.
- Ngày hết hạn được coi là **ngày cuối cùng còn bán** (inclusive); đây là convention kỹ thuật cần chốt cho cửa hàng, không thay thế kiểm soát an toàn thực phẩm.

**Swagger test**
- [ ] Seed một product có lô còn hạn, hết hạn, BLOCKED: kiểm tra 5 giá trị tổng hợp chính xác.
- [ ] Batch expiryDate null (non-perishable) vẫn có thể bán nếu ACTIVE.
- [ ] Đổi remainingQuantity qua F02-05 → GET summary lập tức cập nhật.
- [ ] Không thấy dữ liệu của Store khác; expiringWithinDays âm → `400`.

## Definition of Done (API + Swagger only)
- [ ] API chạy với database development/test, migration áp dụng được.
- [ ] Swagger UI hiển thị endpoint, schema/DTO, error responses và auth nếu cần.
- [ ] Chạy thủ công các ca Swagger trong task, ghi kết quả thực tế vào issue/PR.
- [ ] Dữ liệu thay đổi được kiểm tra bằng GET lại hoặc truy vấn DB.
- [ ] Code build thành công, PR được review/merge.
- [ ] Không yêu cầu Frontend, Postman hoặc E2E trong sprint này.


---

# [F02-07] Swagger Verification — Complete F02

**Epic:** F02 – Product & Inventory Management  
**Priority:** P0  
**Type:** Backend / Database / Swagger  
**Dependencies:** F02-02..F02-05, F02-08; F01-02/F01-04; F02-06 nếu làm

**Mục tiêu:** Chạy manual toàn bộ F02 trên Swagger UI, ghi lại kết quả Pass/Fail. Không yêu cầu Postman / FE / E2E ở giai đoạn này.

**Swagger test checklist**
- [ ] Authorize với Store Admin token từ F01; verify no token → 401, sai role → 403.
- [ ] Category POST → GET → PUT → PATCH status.
- [ ] Product POST → GET list/search/filter/detail → PUT.
- [ ] Batch POST → GET → PATCH và GET inventory-summary đối chiếu số liệu.
- [ ] Case lỗi: SKU trùng, batchCode trùng, giá âm, batch âm, remaining > initial, thiếu expiryDate của perishable Product.
- [ ] Case lô hết hạn, lô BLOCKED và không yêu cầu expiry; kiểm tra saleable chính xác.
- [ ] Store A không xem/sửa Product, Category, Batch của Store B.
- [ ] Ghi response/status code, kết quả DB, ảnh/chứng cứ Swagger vào PR/issue.
- [ ] P1 F02-06 nếu chưa triển khai thì đánh dấu Pending, không ghi PASS giả.

## Definition of Done (API + Swagger only)
- [ ] API chạy với database development/test, migration áp dụng được.
- [ ] Swagger UI hiển thị endpoint, schema/DTO, error responses và auth nếu cần.
- [ ] Chạy thủ công các ca Swagger trong task, ghi kết quả thực tế vào issue/PR.
- [ ] Dữ liệu thay đổi được kiểm tra bằng GET lại hoặc truy vấn DB.
- [ ] Code build thành công, PR được review/merge.
- [ ] Không yêu cầu Frontend, Postman hoặc E2E trong sprint này.

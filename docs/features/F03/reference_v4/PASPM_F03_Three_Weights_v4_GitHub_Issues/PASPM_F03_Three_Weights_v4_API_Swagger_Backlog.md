> **Confirmed update — 2026-10-17:** Apply `F03_TASK_UPDATES.md` and `F03_API_CONTRACT.md` in the F03 documentation directory. Store Admin configures T (default 30); System Admin owns versioned lambda bounds (initial [0,0.5]); Merchandiser owns selected lambda/weights/scores. Fixed limits and proposed formulas below are superseded by the confirmed update. Snapshots freeze T, policy bounds/version, weights, scores and analysis date.

# PASPM — F03 Utility Profiling: Backend API + Swagger (v4)

**Nguồn:** ERD v3 có INVENTORY_BATCH; cập nhật v4 cho 3 tiêu chí Weight. Bổ sung chỉ tiêu do nhóm đề xuất, không phải tất cả chi tiết đã được chốt trong FA26_SE048.

**Phạm vi:** Migration/Backend/API/Swagger manual verification. Không làm FE, Postman, E2E, không chạy HUI-Miner/EFIM (thuộc F05), không import CSV (thuộc F04), không tính minUtil (thuộc F05).

**Công thức:** W_i = 1 + lambda × (alpha×S_i + beta×E_i + gamma×M_i); weightedUtility = baseUtility × W_i.
- S_i: điểm tồn kho do Merchandiser nhập [0,1]; KHÔNG phải số lượng tồn kho.
- M_i: điểm mùa vụ do Merchandiser nhập [0,1].
- E_i: tính tự động từ INVENTORY_BATCH theo ngày snapshot; đề xuất weighted mean theo remaining_quantity của lô bán được.
- Enabled weights cộng thành 1; tắt tất cả → W=1. Lambda 0–0.5 và T=30 ngày chỉ là đề xuất cần chốt.

**ERD v4:** PRODUCT thêm hai điểm S,M. STORE.utility_config giữ config tổng. INVENTORY_BATCH giữ nguyên. UTILITY_SNAPSHOT và UTILITY_MATRIX_ITEM mới lưu matrix bất biến. MINING_REQUEST tham chiếu utility_snapshot_id. Không thêm PRODUCT_LOT.

**Lưu ý nghiệp vụ:** Không xóa giao dịch lịch sử chỉ vì hôm nay sản phẩm hết hạn/hết hàng. Tình trạng còn được bán phải được truyền đến F06 để lọc recommendation. Nếu metadata bắt buộc bị thiếu, Preview báo warning và Snapshot từ chối khi enableExpiry.

**Thứ tự:** F03-01 → 02 → (03, 04, 05, 10) → 06 → 07 → 08 → 09.

---

# [F03-01] Utility Schema & Data Contracts

**Epic:** F03 – Utility Profiling  
**Priority:** P0  
**Type:** Backend / Database  

## Description
Cập nhật ERD/migration và DTO để F03 lưu ba điểm ưu tiên, cấu hình Weight và Utility Matrix bất biến.

## Endpoints / Deliverables
- Migration, DTO và interfaces; chưa cần endpoint riêng.

## Implementation Requirements
- [ ] Thêm PRODUCT.stock_priority_score và seasonal_priority_score: DECIMAL nullable, CHECK 0..1. Không lưu E trên PRODUCT.
- [ ] Giữ INVENTORY_BATCH; không tạo PRODUCT_LOT. Chuẩn hóa cấu trúc STORE.utility_config có enabled flags, alpha, beta, gamma, lambda, expiryWindowDays, scoringVersion.
- [ ] Tạo UTILITY_SNAPSHOT và UTILITY_MATRIX_ITEM theo ERD v4; thêm MINING_REQUEST.utility_snapshot_id FK.
- [ ] Cho input_snapshot_uri cũ nullable (nếu migration hiện tại đã có); ghi rõ quy tắc chuyển dữ liệu cũ.
- [ ] Áp dụng unique(snapshot_id, transaction_id, product_id), các FK và ràng buộc Store sở hữu dữ liệu.

## Acceptance Criteria
- [ ] Migration chạy và rollback được; không tạo trùng bảng F01/F02/F04.
- [ ] Product score không nằm ngoài [0,1]; khác Store không thể truy cập dữ liệu.
- [ ] Snapshot/matrix liên kết được với Mining Request; giao dịch nguồn không bị ghi đè.

## Dependencies
F01 RBAC; F02 Product/Inventory Batch; thống nhất contract Transaction của F04.

## Swagger Manual Test Flow
- Xem schema DTO sau khi F03-02/03/07 hoàn thành; xác nhận migration và FK qua DB.

## Definition of Done
- [ ] Service/API chạy trên DB/fixture và không ghi đè dữ liệu gốc.
- [ ] Swagger thể hiện DTO, authorization, ví dụ request/response và HTTP errors.
- [ ] Các ca Swagger của task có bằng chứng Pass/Fail trong PR.
- [ ] Build thành công; PR đã được review và merge. Không đợi FE/Postman/E2E.

---

# [F03-02] Weight Configuration APIs

**Epic:** F03 – Utility Profiling  
**Priority:** P0  
**Type:** Backend API  

## Description
Cho Merchandiser cấu hình tiêu chí tồn kho, hạn sử dụng, mùa vụ và các tỷ trọng chung theo Store.

## Endpoints / Deliverables
- GET /api/utility/config
- PUT /api/utility/config

## Implementation Requirements
- [ ] Nhận enableStock, enableExpiry, enableSeason, alpha, beta, gamma, lambda, expiryWindowDays.
- [ ] Các tỷ trọng hiệu lực của tiêu chí đang bật phải có tổng bằng 1 (sai số decimal đã chốt). Khi tắt cả ba, W=1.
- [ ] Nếu chỉ bật một tiêu chí, hệ số hiệu lực của nó bằng 1; tiêu chí tắt có hiệu lực 0. Không tự lưu lại các giá trị đã chỉnh vào snapshot cũ.
- [ ] Validate alpha/beta/gamma trong [0,1], lambda trong [0,0.5] theo ngưỡng đề xuất, expiryWindowDays > 0; lưu phiên bản config.

## Acceptance Criteria
- [ ] GET/PUT/GET phản ánh cấu hình mới; chỉ Merchandiser đúng Store được sửa.
- [ ] Ba tỷ trọng 0.5/0.2/0.3 hợp lệ; tổng 1.2 và lambda âm trả 400.
- [ ] Tắt tất cả trả trạng thái hiệu lực W=1; snapshot cũ không đổi.

## Dependencies
F03-01; F01-04 RBAC.

## Swagger Manual Test Flow
- Authorize Merchandiser → GET → PUT (0.5/0.2/0.3) → GET; thử sai tổng, lambda, role và Store.

## Definition of Done
- [ ] Service/API chạy trên DB/fixture và không ghi đè dữ liệu gốc.
- [ ] Swagger thể hiện DTO, authorization, ví dụ request/response và HTTP errors.
- [ ] Các ca Swagger của task có bằng chứng Pass/Fail trong PR.
- [ ] Build thành công; PR đã được review và merge. Không đợi FE/Postman/E2E.

---

# [F03-03] Manual Stock Priority Score API

**Epic:** F03 – Utility Profiling  
**Priority:** P0  
**Type:** Backend API  

## Description
Cho Merchandiser tự nhập S_i theo Product. Đây là điểm ưu tiên, không phải số lượng tồn kho.

## Endpoints / Deliverables
- GET /api/utility/products/{productId}/scores
- PATCH /api/utility/products/{productId}/scores

## Implementation Requirements
- [ ] PATCH nhận stockPriorityScore (0..1), chỉ cập nhật trường được gửi; giữ nguyên seasonalPriorityScore.
- [ ] Lưu điểm tại PRODUCT.stock_priority_score và thời gian/người thay đổi nếu cơ chế audit của repo hỗ trợ.
- [ ] Không tính S từ DOC; không thay đổi INVENTORY_BATCH.remaining_quantity.
- [ ] Product và người sửa phải thuộc cùng Store; NULL biểu thị chưa cấu hình.

## Acceptance Criteria
- [ ] PATCH S=0.8 và GET trả lại 0.8.
- [ ] S=-0.1 hoặc 1.1 trả 400; Product Store khác trả 403/404.
- [ ] Tồn kho và snapshot đã tạo không thay đổi khi chỉnh S.

## Dependencies
F03-01; F02 Product; F01-04 RBAC.

## Swagger Manual Test Flow
- PATCH S=0.8 → GET; thử giá trị vượt ngưỡng và Product Store khác.

## Definition of Done
- [ ] Service/API chạy trên DB/fixture và không ghi đè dữ liệu gốc.
- [ ] Swagger thể hiện DTO, authorization, ví dụ request/response và HTTP errors.
- [ ] Các ca Swagger của task có bằng chứng Pass/Fail trong PR.
- [ ] Build thành công; PR đã được review và merge. Không đợi FE/Postman/E2E.

---

# [F03-04] Expiry Score from INVENTORY_BATCH

**Epic:** F03 – Utility Profiling  
**Priority:** P0  
**Type:** Business Logic  

## Description
Tính E_i từ các lô bán được trong INVENTORY_BATCH tại ngày tạo snapshot.

## Endpoints / Deliverables
- IExpiryScoreCalculator (internal service); xem qua POST /api/utility/preview.

## Implementation Requirements
- [ ] Chỉ xét lô ACTIVE, remaining_quantity > 0, expiry_date > asOfDate cho PRODUCT.requires_expiry=true.
- [ ] Theo công thức đề xuất: E_batch=max(0,1-daysUntilExpiry/T); E_product=Σ(q_batch×E_batch)/Σq_batch, T=expiryWindowDays.
- [ ] Nếu requires_expiry=false, ghi expiry_status=NOT_APPLICABLE và điểm đóng góp E=0.
- [ ] Nếu không còn lô bán được, ghi NO_SELLABLE_BATCH, E=0 và chuyển cờ cho F06 lọc; không xóa giao dịch lịch sử khỏi matrix.
- [ ] Nếu cần hạn dùng nhưng thiếu metadata bắt buộc, trả warning trong preview và không cho tạo READY snapshot khi enableExpiry; không tạo ngày giả.

## Acceptance Criteria
- [ ] Hai lô: 20 đơn vị còn 5 ngày, 80 đơn vị còn 40 ngày, T=30 → E≈0.1667.
- [ ] Lô BLOCKED, hết hạn, remaining=0 không được đưa vào trung bình.
- [ ] E trong [0,1]; asOfDate cố định; không thêm PRODUCT_LOT.

## Dependencies
F03-01, F03-02; F02 INVENTORY_BATCH; fixture hạn dùng.

## Swagger Manual Test Flow
- Preview Product nhiều lô; thử lô expired, blocked, không đủ metadata và non-expiry Product.

## Definition of Done
- [ ] Service/API chạy trên DB/fixture và không ghi đè dữ liệu gốc.
- [ ] Swagger thể hiện DTO, authorization, ví dụ request/response và HTTP errors.
- [ ] Các ca Swagger của task có bằng chứng Pass/Fail trong PR.
- [ ] Build thành công; PR đã được review và merge. Không đợi FE/Postman/E2E.

---

# [F03-05] Base Utility Calculator

**Epic:** F03 – Utility Profiling  
**Priority:** P0  
**Type:** Business Logic  

## Description
Tính utility gốc từ các dòng giao dịch đã được F04 validate/preprocess.

## Endpoints / Deliverables
- IBaseUtilityCalculator (internal service); kết quả xuất qua Utility Preview.

## Implementation Requirements
- [ ] Với CSV nghiệp vụ: baseUtility=quantity×(unit_price−unit_cost) theo giá tại giao dịch.
- [ ] Với dữ liệu FoodMart utility gốc: dùng utility nguồn đã được lưu đúng format; không suy ngược giá và số lượng.
- [ ] Không lấy giao dịch hủy/lỗi; nếu item trùng trong cùng transaction, gộp theo quy tắc F04.
- [ ] Utility âm hoặc thiếu dữ liệu phải được báo rõ; không tự chuyển âm thành 0. Chốt cách chặn dataset không phù hợp F05 tiêu chuẩn.

## Acceptance Criteria
- [ ] Quantity=3, price=10000, cost=6000 → baseUtility=12000.
- [ ] S/M hoặc config thay đổi không làm thay baseUtility gốc.
- [ ] Dữ liệu thiếu/canceled không lọt vào dataset sẵn sàng mining.

## Dependencies
F03-01; F04 cleaned transactions hoặc fixture độc lập.

## Swagger Manual Test Flow
- Preview với fixture 3 giao dịch; đối chiếu baseUtility và trường hợp invalid.

## Definition of Done
- [ ] Service/API chạy trên DB/fixture và không ghi đè dữ liệu gốc.
- [ ] Swagger thể hiện DTO, authorization, ví dụ request/response và HTTP errors.
- [ ] Các ca Swagger của task có bằng chứng Pass/Fail trong PR.
- [ ] Build thành công; PR đã được review và merge. Không đợi FE/Postman/E2E.

---

# [F03-06] Weighted Utility & Matrix Builder

**Epic:** F03 – Utility Profiling  
**Priority:** P0  
**Type:** Business Logic  

## Description
Tạo ma trận utility từ Base Utility và S/E/M tại cùng một thời điểm tham chiếu.

## Endpoints / Deliverables
- IUtilityMatrixBuilder (internal service) cho Preview và Snapshot APIs.

## Implementation Requirements
- [ ] W_i=1+lambda×(alpha×S_i+beta×E_i+gamma×M_i); bỏ đóng góp tiêu chí tắt.
- [ ] weightedUtility(i,t)=baseUtility(i,t)×W_i; weighted TU mỗi giao dịch bằng tổng weighted item utility của giao dịch.
- [ ] Tạo một hàng (transactionId, productId) cho mỗi item hợp lệ; thứ tự item ổn định và unique trong transaction.
- [ ] Định nghĩa decimal precision và một quy tắc làm tròn thống nhất giữa Preview/Snapshot/F05.
- [ ] Nếu bật stock/season nhưng score tương ứng NULL, trả thông tin lỗi; không mặc định tự đặt bằng 0.
- [ ] Đóng băng điểm S/E/M, trạng thái hạn dùng, W_i và base/weighted utility khi ghi matrix.

## Acceptance Criteria
- [ ] Base=12000, S=.8, E=.6, M=.9; alpha=.5,beta=.2,gamma=.3,lambda=.5 → W=1.395; weighted=16740.
- [ ] Không bật tiêu chí → W=1; weighted=base.
- [ ] Cùng input + asOfDate + config cho matrix giống nhau, hash giống nhau.

## Dependencies
F03-02/03/04/05/10; hợp đồng F04.

## Swagger Manual Test Flow
- So sánh Utility Preview với fixture; kiểm tra TU bằng tổng utility item và lỗi thiếu S/M.

## Definition of Done
- [ ] Service/API chạy trên DB/fixture và không ghi đè dữ liệu gốc.
- [ ] Swagger thể hiện DTO, authorization, ví dụ request/response và HTTP errors.
- [ ] Các ca Swagger của task có bằng chứng Pass/Fail trong PR.
- [ ] Build thành công; PR đã được review và merge. Không đợi FE/Postman/E2E.

---

# [F03-07] Utility Preview API

**Epic:** F03 – Utility Profiling  
**Priority:** P0  
**Type:** Backend API  

## Description
Cho người dùng xem trước tác động của S/E/M và giá trị utility trước khi lưu snapshot.

## Endpoints / Deliverables
- POST /api/utility/preview

## Implementation Requirements
- [ ] Request chọn rangeStart, rangeEnd, asOfDate; đọc config hiện tại của Store; không nhận storeId tùy ý.
- [ ] Response có productId, baseUtility, S/E/M, expiryStatus, W, weightedUtility, transactionTU, totalTU và warning.
- [ ] Hỗ trợ giới hạn dòng/pagination của Preview; không lưu snapshot và không chạy mining.
- [ ] Thiếu F04 thật thì dùng seed/fixture đã định nghĩa và ghi rõ nguồn test.

## Acceptance Criteria
- [ ] Preview trả kết quả tái lập được theo fixture và thời điểm cố định.
- [ ] S=0.8, M=0.9 thay đổi thì W và weightedUtility đổi, baseUtility giữ nguyên.
- [ ] Range sai trả 400; dữ liệu khác Store không truy cập được; không phát sinh Mining Run.

## Dependencies
F03-06; F01-04; F04 data hoặc fixture.

## Swagger Manual Test Flow
- POST preview hợp lệ; thử đổi S/M, mốc thời gian, role và range sai.

## Definition of Done
- [ ] Service/API chạy trên DB/fixture và không ghi đè dữ liệu gốc.
- [ ] Swagger thể hiện DTO, authorization, ví dụ request/response và HTTP errors.
- [ ] Các ca Swagger của task có bằng chứng Pass/Fail trong PR.
- [ ] Build thành công; PR đã được review và merge. Không đợi FE/Postman/E2E.

---

# [F03-08] Immutable Snapshot & Matrix APIs

**Epic:** F03 – Utility Profiling  
**Priority:** P0  
**Type:** Backend API / DB  

## Description
Lưu Utility Snapshot và toàn bộ hàng Utility Matrix vào database để F05 đọc lại đúng dữ liệu.

## Endpoints / Deliverables
- POST /api/utility/snapshots
- GET /api/utility/snapshots/{id}
- GET /api/utility/snapshots/{id}/matrix

## Implementation Requirements
- [ ] Tạo UTILITY_SNAPSHOT: storeId, createdBy, range, asOfDate, configSnapshot, sourceHash, matrixHash, roundingScale, totalWeightedUtility, status.
- [ ] Ghi UTILITY_MATRIX_ITEM: snapshotId, transactionId, productId, baseUtility, S/E/M, expiryStatus, weightValue, weightedUtility.
- [ ] Ghi trong transaction DB; READY snapshot bất biến, không PUT/PATCH; nếu lỗi lưu phải rollback.
- [ ] Chỉ nhận dữ liệu giao dịch hợp lệ, không có utility âm nếu chưa có chính sách xử lý phù hợp cho F05.
- [ ] Giao contract `snapshotId` cho F05; MINING_REQUEST.utility_snapshot_id dùng FK; không cần file TXT người dùng upload.

## Acceptance Criteria
- [ ] POST tạo snapshot; GET và GET matrix trả đủ rows và đúng totalTU.
- [ ] Sau khi sửa điểm, cấu hình hoặc Inventory Batch, snapshot cũ không đổi.
- [ ] Store A không đọc snapshot Store B; matrixHash ổn định cho nội dung như nhau.
- [ ] F05 có thể truy xuất matrix theo snapshotId, không phụ thuộc dữ liệu live.

## Dependencies
F03-01, F03-06, F03-07; F04 fixture/API.

## Swagger Manual Test Flow
- Create snapshot → GET header → GET matrix; sửa S/M rồi GET snapshot cũ; thử Store B và input không hợp lệ.

## Definition of Done
- [ ] Service/API chạy trên DB/fixture và không ghi đè dữ liệu gốc.
- [ ] Swagger thể hiện DTO, authorization, ví dụ request/response và HTTP errors.
- [ ] Các ca Swagger của task có bằng chứng Pass/Fail trong PR.
- [ ] Build thành công; PR đã được review và merge. Không đợi FE/Postman/E2E.

---

# [F03-09] Swagger Verification & F05 Handoff

**Epic:** F03 – Utility Profiling  
**Priority:** P0  
**Type:** QA / Backend  

## Description
Kiểm tra end-to-end F03 trên Swagger UI; chuẩn bị contract để F05 chạy HUI-Miner/EFIM.

## Endpoints / Deliverables
- Swagger manual test evidence, matrix fixture, data contract README; không làm Postman/FE.

## Implementation Requirements
- [ ] Test config → cập nhật S/M → preview → create snapshot → read matrix.
- [ ] Test E từ INVENTORY_BATCH với nhiều lô, lô hết hạn, bị khóa và sản phẩm không cần expiry.
- [ ] Đối chiếu từng công thức bằng phép tính tay và totalTU; xác nhận snapshot bất biến.
- [ ] Kiểm tra RBAC, tenant isolation, missing metadata, utility âm, scale/rounding và duplicate matrix rows.
- [ ] Ghi hợp đồng F05: snapshotId, transactionId, productId, weightedUtility, algorithmItemId mapping.

## Acceptance Criteria
- [ ] Có bảng Swagger Pass/Fail + response/status và seed data đủ để tái lập.
- [ ] F03 không chạy HUI-Miner/EFIM và không cấu hình minUtil.
- [ ] F05 có thể đọc lại đúng một Utility Matrix bất biến trên DB.

## Dependencies
F03-01 đến F03-08 và F03-10; F01 RBAC.

## Swagger Manual Test Flow
- Chạy đầy đủ ca ghi trong F03-02 đến F03-08 và F03-10; chụp minh chứng ca fail/pass.

## Definition of Done
- [ ] Service/API chạy trên DB/fixture và không ghi đè dữ liệu gốc.
- [ ] Swagger thể hiện DTO, authorization, ví dụ request/response và HTTP errors.
- [ ] Các ca Swagger của task có bằng chứng Pass/Fail trong PR.
- [ ] Build thành công; PR đã được review và merge. Không đợi FE/Postman/E2E.

---

# [F03-10] Manual Seasonal Priority Score API

**Epic:** F03 – Utility Profiling  
**Priority:** P0  
**Type:** Backend API  

## Description
Cho Merchandiser nhập M_i theo Product; mùa vụ là điểm ưu tiên nghiệp vụ do người dùng chọn.

## Endpoints / Deliverables
- GET /api/utility/products/{productId}/scores (dùng chung F03-03)
- PATCH /api/utility/products/{productId}/scores

## Implementation Requirements
- [ ] PATCH nhận seasonalPriorityScore (0..1), chỉ cập nhật trường được gửi; giữ stockPriorityScore.
- [ ] Lưu vào PRODUCT.seasonal_priority_score; không suy mùa vụ từ thời gian giao dịch nếu chưa có quy tắc nguồn.
- [ ] Lấy Store từ token, không cho sửa Product khác Store; snapshot cũ không đổi.
- [ ] Khi người dùng đổi M, Preview/Snapshot mới dùng M mới.

## Acceptance Criteria
- [ ] PATCH M=0.9 → GET trả 0.9 và S cũ giữ nguyên.
- [ ] M âm hoặc >1 trả 400; không có quyền trả 401/403.
- [ ] F03-06 dùng đúng M=0.9 để tính Weighted Utility.

## Dependencies
F03-01, F03-02, F03-03 (shared endpoint), F01-04.

## Swagger Manual Test Flow
- PATCH M=0.9 → GET → Preview; thử M=1.2 và user khác Store.

## Definition of Done
- [ ] Service/API chạy trên DB/fixture và không ghi đè dữ liệu gốc.
- [ ] Swagger thể hiện DTO, authorization, ví dụ request/response và HTTP errors.
- [ ] Các ca Swagger của task có bằng chứng Pass/Fail trong PR.
- [ ] Build thành công; PR đã được review và merge. Không đợi FE/Postman/E2E.

---

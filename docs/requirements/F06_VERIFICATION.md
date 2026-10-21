# F06 — Báo cáo kiểm chứng thực tế

**Kết quả: Backend API + Swagger PASS.** Ngày2026-10-20, timezone Asia/Ho_Chi_Minh. Frontend integration: **NOT RUN**; handoff đã bàn giao, không sửa Frontend.

## Automated verification

- Maven clean verify bằng backend Corretto Java17.0.20.1 và child SPMF Java25.0.1: **210 tests,0 failures,0 errors,0 skipped; BUILD SUCCESS**, kết thúc21:20:40+07:00, thời gian01:05.
- **63 test F06 mới +147 regression**. RecommendationIntegrationTest14 + MySQL14; ExpiryIntegrationTest5 + MySQL5; TransactionEvidenceTest4; FrozenEvidenceReaderTest15; RecommendationRulesTest4; MySQL migration2.
- MySQL8.4.11: freshV1–V10 và V9→V10 đúng1 migration, giữ STORE.utility_config, snapshot JSON/tổng utility và dữ liệu lịch sử. Regression FlywayRecoveryTest chỉ cập nhật số migration latest3/V10, không sửa V1–V9.
- Real F03→F05→F06: tạo snapshot6 transactions, chạy pinned SPMF HUI-Miner/EFIM, generate8 candidates, tính confidence hai chiều, merge provenance hai algorithms, giữ source/F03/F05 bất biến sau live edits.
- Concurrent generation idempotent; concurrent config/review có1 winner; reviewhistory atomic; Store/role isolation, strict JSON và live eligibility được kiểm tra trên H2 và MySQL.
- Danh sách tất cả testcase và schema test nằm trong [automated evidence](evidence/f06-automated-results.json). Baseline147 trước F06 được giữ riêng trong f06-readiness.json.

## Swagger UI — Authorize → Try it out → Execute

Backend standalone Java17 trên cổng18086, schema MySQL local mới riêng. Tài khoản Merchant/Admin Store A, Merchant Store B, Customer/SystemAdmin được tạo chỉ cho QA; credential/token giữ ngoài repo. Không dùng Postman hoặc Frontend E2E. Các request dưới đây được thực hiện bằng nút Swagger UI; JSON/HTTP đọc lại database được báo riêng, không thay thế UI.

**27/27 PASS**,0 FAIL/BLOCKED/NOT RUN trong danh sách này; gồm24 ca F06 và3 ca chuẩn bị F03/F05. Đăng nhập F01 được thực hiện qua Swagger trước khi đổi Authorize; response token không lưu vào báo cáo.

| ID | Role | Method / endpoint | Expected / actual | Hành vi xác nhận |
|---|---|---|---|---|
| UI01 | MERCHANDISER | GET `/api/recommendations/expiry-push` | 200 / 200 PASS | Độc lập trước Mining:1 batch, D=5,T=7, quantity10, pushRequired=true |
| UI02 | MERCHANDISER | POST `/api/utility/snapshots` | 201 / 201 PASS | Tạo immutable snapshot từ6 giao dịch |
| UI03 | MERCHANDISER | POST `/api/mining/requests` | 202 / 202 PASS | Nhận mining request202, cả HUI-Miner/EFIM |
| UI04 | MERCHANDISER | GET `/api/mining/requests/1/runs` | 200 / 200 PASS | Hai runCOMPLETED |
| UI05 | MERCHANDISER | POST `/api/recommendations/generate` | 200 / 200 PASS | 8 candidates từ đủ4 loại |
| UI06 | MERCHANDISER | POST `/api/recommendations/generate` | 200 / 200 PASS | Generate lại:created0,reused8 |
| UI07 | MERCHANDISER | GET `/api/recommendations?miningRunId=1&page=0&pageSize=1` | 200 / 200 PASS | Rank1, total8, pageSize1 |
| UI08 | MERCHANDISER | GET `/api/recommendations/1` | 200 / 200 PASS | PENDING_REVIEW/ELIGIBLE, chưa ready downstream |
| UI09 | MERCHANDISER | POST `/api/recommendations/1/approve` | 200 / 200 PASS | APPROVED, version2,1 history, ready=true |
| UI10 | MERCHANDISER | GET `/api/recommendations/1` | 200 / 200 PASS | GET xác nhận approval persisted |
| UI11 | MERCHANDISER | POST `/api/recommendations/2/reject` | 200 / 200 PASS | REJECTED, version2 |
| UI12 | MERCHANDISER | GET `/api/recommendations/2` | 200 / 200 PASS | GET xác nhận rejection persisted |
| UI13 | MERCHANDISER | POST `/api/recommendations/2/approve` | 409 / 409 PASS | Từ chối reopen REJECTED→APPROVED |
| UI14 | MERCHANDISER | GET `/api/recommendations/config` | 200 / 200 PASS | GET defaults4/2/.05/.50/1,version1 |
| UI15 | MERCHANDISER | PUT `/api/recommendations/config` | 403 / 403 PASS | Merchandiser không sửa config |
| UI16 | STORE_ADMIN | PUT `/api/recommendations/config` | 200 / 200 PASS | StoreAdmin PUT version2 |
| UI17 | STORE_ADMIN | GET `/api/recommendations/config` | 200 / 200 PASS | GET xác nhận configversion2 |
| UI18 | STORE_ADMIN | POST `/api/recommendations/3/approve` | 403 / 403 PASS | StoreAdmin không có quyền approve |
| UI19 | MERCHANDISER(Store B) | GET `/api/recommendations/1` | 404 / 404 PASS | Store B không đọc record Store A |
| UI20 | MERCHANDISER(Store B) | POST `/api/recommendations/3/approve` | 404 / 404 PASS | Store B không approve record Store A |
| UI21 | MERCHANDISER(Store B) | GET `/api/recommendations` | 200 / 200 PASS | Store B list0, không lộ Store A |
| UI22 | END_CUSTOMER | GET `/api/recommendations` | 403 / 403 PASS | Customer không quản lý F06 |
| UI23 | SYSTEM_ADMIN | GET `/api/recommendations` | 403 / 403 PASS | SystemAdmin không có quyền nghiệp vụ ngầm |
| UI24 | MERCHANDISER | POST `/api/recommendations/generate` | 400 / 400 PASS | JSON storeId giả bị400 |
| UI25 | No token | GET `/api/recommendations` | 401 / 401 PASS | Thiếu token401 |
| UI26 | Invalid token | GET `/api/recommendations` | 401 / 401 PASS | Token sai401 |
| UI27 | MERCHANDISER | GET `/api/recommendations/1` | 200 / 200 PASS | GET cuối:APPROVED/ready=true, policy_snapshot vẫnversion1 sau configversion2 |

Payload, response thực tế và thời gian từng ca: [f06-swagger-ui.json](evidence/f06-swagger-ui.json). Ảnh [APPROVED/version2](evidence/f06-swagger-approved.png) không chứa Authorization header. Các ô ví dụ mặc định của Swagger không được dùng làm kết quả; chỉ lấy Server response của request đã Execute.

Sau POST/PUT review/config đã GET lại (UI10/UI12/UI17/UI27). [MySQL readback](evidence/f06-mysql-readback.txt) xác nhận8 recommendations,8 provenance rows,2 reviewhistory, configversion2 nhưng các candidate vẫn policyversion1, inventory30,8 patterns/support vẫnnull, snapshot total80 và hash gốc. UI05/06 đối chiếu cả response và persistence này. UI27 được chạy lại sau khi restart bằng JAR build cuối lúc21:22; APPROVED/history/ready và policy cũ vẫn tồn tại. Thời gian chính xác trong JSON evidence.

## Defects phát hiện và retest

| Defect | Bằng chứng ban đầu | Sửa | Retest |
|---|---|---|---|
| Reader không đối chiếu đủ pattern với count run | Review P2;5 regression fail | Check recorded count/page count/unique IDs/final count và positive frozen support |19 primitive tests PASS; full210 PASS |
| Concurrent generation trả409 thay vì reuse | RecommendationIntegrationTest concurrent case FAIL, expected200 actual409 | Generation READ_COMMITTED sau Store lock; policy update đọc version FOR UPDATE | Concurrent generation/config PASS trên H2/MySQL; full210 PASS |
| OpenAPI Evidence trùng tên với F03 expiry Evidence | Schema regression FAIL trước fix | Đặt component RecTransactionEvidence, khai báo nullable; giữ F03 Evidence | Schema regression PASS; actual runtime export59paths/73ops |
| OpenAPI chưa ghi rõ cấm request property dư | Regression additionalProperties FAIL (expected false, actual default true) | Ghi additionalProperties=false trên ba request DTO; runtime validation không đổi | PASS final clean verify210/210 và standalone runtime schema export |

Lỗi automation UI khi đọc response trước khi Swagger render xong được xử lý bằng chờ DOM và đọc lại response gốc; không ghi null body/loading hay Example Value thành PASS. Đây không phải API defect. Những entry tạm thiếu body không nằm trong JSON evidence cuối.

## Các kênh kiểm chứng và giới hạn

| Kênh | Trạng thái |
|---|---|
| Java17 Maven build và regression | PASS210/210 |
| MySQL fresh/upgrade/persistence | PASS, schema cô lập mới |
| F06 Store/RBAC và review concurrency | PASS automated + UI |
| OpenAPI | PASS runtime59paths/73ops,8 F06 ops; schema F03 được giữ |
| Interactive Swagger | PASS27/27 |
| CodeGraph | PASS: graph818files/10607nodes/20700edges, phát hiện module/dependencies mới |
| ERD XML/column/type/FK validation | PASS5tables/13FK; [evidence](evidence/f06-erd-schema-check.json) |
| ERD rendered visual QA | NOT RUN; không tuyên bố đã render kiểm tra bố cục |
| Frontend integration | NOT RUN; API handoff và fixtures sẵn sàng, demo code không sửa |
| Deployed database repair/rollout | NOT RUN; V7 checksum cũ cần operator workflow riêng |

Không còn quyết định business threshold chờ duyệt. Giới hạn kỹ thuật10000 candidates/ranked scope được mô tả trong contract. Inventory không reserve; downstream phải recheck GET detail trước khi dùng. Xem [core review](evidence/f06-core-review.md); review cuối approve, P2 ban đầu đã sửa và retest.

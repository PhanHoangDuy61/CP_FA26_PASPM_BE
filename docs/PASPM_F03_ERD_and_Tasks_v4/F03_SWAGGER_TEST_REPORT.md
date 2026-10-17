# F03 — Báo cáo thực thi Swagger và backend

Ngày thực thi: **17/10/2026, Asia/Ho_Chi_Minh**. Đây là kết quả đã chạy, không phải kế hoạch kiểm thử.

## Kết luận

- **36/36 nhóm test thiết kế PASS** theo phạm vi và phương thức ghi ở bảng dưới; FAIL=0, BLOCKED=0, NOT RUN=0 trong 36 nhóm này.
- **120 lượt gọi HTTP chức năng qua Swagger UI**, bao phủ 11 endpoint F03. Thống kê actual HTTP: 200×70, 201×5, 400×28, 401×2, 403×6, 404×5, 409×2, 503×2. Các lỗi 4xx/503 được mong đợi trong test âm, không đồng nghĩa FAIL.
- **44 automated tests PASS**, 0 failure/error/skip: 25 test sẵn có và 19 test F03 (6 calculation, 11 integration, 2 fixture).
- **Trạng thái tính năng tổng thể: PARTIAL**. Adapter giao dịch F04 thật, FK transaction_id và FK từ MINING_REQUEST/F05 chưa thể hoàn thành do module/bảng nguồn chưa tồn tại. Hai điểm tích hợp F04 và F05 được ghi BLOCKED riêng, không được tính là PASS trong 36 nhóm kiểm tra hiện có.

## Môi trường và phương thức

Backend thật chạy cổng 8799; Swagger UI `http://localhost:8799/swagger-ui/index.html`, OpenAPI `/v3/api-docs`. CSDL H2 file riêng trong thư mục tạm; không xóa/reset CSDL dùng chung. Profile `dev,utility-fixture` chỉ cung cấp giao dịch QA; đã khởi động lại bằng profile `dev` để kiểm tra dependency503. Hai Store, System Admin, Store Admin và Merchandiser được tạo trong fixture riêng. Không lưu mật khẩu, JWT, OTP hoặc chuỗi kết nối trong báo cáo.

Các request UI được thực hiện bằng **Authorize → Try it out → Execute**, đọc HTTP status và response thực. Sau thao tác ghi thành công, dùng GET hoặc preview đọc lại kết quả đã lưu. DB constraints, rollback, source ordering, missing source values và overflow còn được xác minh bằng automated tests; không gọi những kiểm tra đó là Swagger UI.

120 lượt không bao gồm login, hai lượt thiết lập token bị đọc response cũ, hoặc hai GET tải bằng chứng. Lỗi thiết lập token đã được sửa, xác minh bằng `/api/auth/me`, rồi chạy lại các case liên quan. Không dùng Postman/curl làm bằng chứng Swagger.

## Quy ước endpoint và bằng chứng

`policy` = `/api/system/utility/lambda-policy`; `config` = `/api/utility/config`; `expiry-window` = `/api/utility/config/expiry-window`; `scores` = `/api/utility/products/{productId}/scores`; `preview` = `/api/utility/preview`; `snapshot` = `/api/utility/snapshots[/{id}]`; `matrix` = `/api/utility/snapshots/{id}/matrix`.

Mỗi dòng dưới có expected status/behavior theo `F03_SWAGGER_TEST_CASES.md`; cột kết quả ghi actual đã kiểm tra. Bằng chứng `UI` là lịch sử thực thi browser của phiên này; `AUTO` là Surefire XML tại `target/surefire-reports` và bản tổng hợp lưu cùng tài liệu. Các tệp JSON là response tải trực tiếp từ nút Download của Swagger.

| ID | Endpoint / phương thức | Input / expected behavior | Actual status và behavior | Kết quả / bằng chứng |
|---|---|---|---|---|
| CFG-01 | GET policy | Initial bounds | 200; min=0,max=.5,version=1 | PASS — UI |
| CFG-02 | PUT/GET policy | System Admin updates bounds | 200; persisted bounds, version incremented | PASS — UI |
| CFG-03 | PUT/GET policy | Negative minimum; reversed bounds | 400; previous policy retained | PASS — UI |
| CFG-04 | PUT policy | Merchandiser / Store Admin | 403 for both roles | PASS — UI |
| CFG-05 | PUT expiry-window; GET config | T=45 | 200; T persisted; coefficients preserved | PASS — UI |
| CFG-06 | PUT expiry-window; GET config | 0,-1,1.5,null; string "30"; wrong role | 400 for invalid inputs; 403 Merchandiser; integer 100 persisted | PASS — UI |
| CFG-07 | PUT/GET config | Merchandiser selects coefficients/lambda | 200; settings persisted, T=45 retained | PASS — UI |
| CFG-08 | PUT config | Wrong coefficient sum, alpha -.1/1.1, lambda -.1/.6 | 400; no normalization | PASS — UI |
| CFG-09 | PUT config | Forged lambdaMax, T, storeId | 400; unknown fields rejected | PASS — UI |
| CFG-10 | PUT policy; GET config; POST preview/snapshot | Bounds .1.. .2 while selected lambda=.5 | 200 policy; lambdaValid=false; 409 preview/create; no clamp | PASS — UI |
| CFG-11 | PUT config; POST snapshot | Correct selected lambda to .2 | 200 config; 201 snapshot; total=62470 | PASS — UI |
| SCR-01 | PATCH/GET scores | S=.8 then M=.9 | 200; both values retained; omitted fields preserved | PASS — UI |
| SCR-02 | PATCH/GET scores | -.1,1.1,null,empty patch | 400; previous values preserved | PASS — UI |
| SCR-03 | GET/PATCH scores | Store B requests Store A Product | 404 for read and write | PASS — UI |
| CALC-01 | POST preview | All criteria disabled | 200; W=1; total=55000 | PASS — UI |
| CALC-02 | PUT config; POST preview | Stock only / expiry only / season only | 200; totals=77000 / 56250 / 79750 | PASS — UI |
| CALC-03 | POST preview | Base12000,S=.8,E=.6,M=.9; coefficients .5/.2/.3; lambda=.5 | 200; W=1.395; weighted=16740 | PASS — UI |
| EXP-01 | POST preview | 20 units expiry+5;80 expiry+40;T30 | 200; E=.166666666666666667; blocked/expired quantities excluded | PASS — UI |
| EXP-02 | POST preview; F02 batch create/GET | Blocked, expired, zero remaining, expiry on analysis date | 200 preview; today batch created201 and persisted; reference E=.6 remained | PASS — UI |
| EXP-03 | POST preview | Non-expiring Coffee | 200; E=0; NOT_APPLICABLE | PASS — UI |
| EXP-04 | POST preview | Block remaining eligible Milk batches | 200; E=0; NO_SELLABLE_BATCH; all 4 historical rows retained; total60930 | PASS — UI |
| EXP-05 | POST preview/snapshot | Required expiry missing; enable/disable expiry | 200 warning ready=false/null total; create400 when enabled; disabled preview200,total4640; create201 | PASS — UI |
| UTIL-01 | POST preview | Historical qty3,price10000,cost6000 | 200; base12000 independent of current Product price | PASS — UI |
| UTIL-02 | POST preview/snapshot; calculator tests | Negative and duplicate source; missing historical values | UI400 negative/duplicate; automated missing-value and overflow rejection | PASS — UI + AUTO |
| UTIL-03 | POST snapshot; source-order integration test | Same source/date/config twice; reverse source order | UI201 twice with identical hashes/totals; automated reordered source hash matched | PASS — UI + AUTO |
| UTIL-04 | POST preview; GET matrix | Invalid date range/page size | 400; invalid range also covered by integration test | PASS — UI + AUTO |
| SNAP-01 | POST preview/create; GET snapshot/matrix | Create snapshot1 then retrieve | 200/201; total73675; four persisted rows match preview | PASS — UI |
| SNAP-02 | GET snapshot/matrix after writes/restart | Change T,bounds,lambda,S/M,batches; restart app | 200; snapshot1 retains T30,lambda.5,policyv4,total73675 and original hashes | PASS — UI |
| SNAP-03 | POST snapshot; rollback integration test | Stale lambda; injected persistence failure | UI409; automated rollback leaves no partial header/items | PASS — UI + AUTO |
| SNAP-04 | GET matrix | Pagination; TU; quantity; product mapping | 200; pages do not overlap; TU16220/40050/17405; mapping=productId | PASS — UI |
| SNAP-05 | GET snapshot/matrix | Foreign Store; nonexistent snapshot | 404; both metadata and rows protected | PASS — UI |
| SEC-01 | GET config | No token; invalid token | 401 for both | PASS — UI |
| SEC-02 | Configuration/score/snapshot writes | Roles without required permission | 403; Store isolation verified separately | PASS — UI |
| DEP-01 | POST preview/snapshot; GET old snapshot | Restart without utility-fixture profile | 503 for new calculations; old snapshot200 and unchanged | PASS — UI |
| DB-01 | Application startup / Flyway | Fresh isolated H2 file DB; subsequent restart | V1-V5 migrate successfully; subsequent V5 validation/no pending migration | PASS — Runtime + AUTO |
| DB-02 | H2 integration tests | Invalid score/check/duplicate matrix row | Database constraints reject invalid writes | PASS — AUTO |

## Đối chiếu số học và dữ liệu lưu

Fixture chính có 3 giao dịch, 4 dòng; tổng base utility=55000. Với T30, lambda=.5, alpha=.5,beta=.2,gamma=.3, tổng weighted utility=73675. Không dùng giá Product hiện tại để tính lại lịch sử.

| Transaction | Product | Quantity | Base | Weight | Weighted | TU |
|---|---|---:|---:|---:|---:|---:|
| 1 | Milk | 3 | 12000 | 1.351666666666666667 | 16220 | 16220 |
| 2 | Coffee | 2 | 30000 | 1.335 | 40050 | 40050 |
| 3 | Milk | 1 | 3000 | 1.351666666666666667 | 4055 | 17405 |
| 3 | Coffee | 1 | 10000 | 1.335 | 13350 | 17405 |

Ví dụ chuẩn riêng: T100, batch còn80 hết hạn sau40 ngày → E=.6. Base12000, S=.8,M=.9 và lambda=.5 → W=1+.5×(.5×.8+.2×.6+.3×.9)=1.395 → weighted16740. Đã thực thi qua UI.

Snapshot1 giữ nguyên sau khi đổi T, policy, lambda, scores, quantity và restart backend. Snapshot3 dùng bounds mới [.1,.2], lambda=.2,total62470; snapshot1 vẫn giữ policyv4,bounds[0,.5],lambda=.5,total73675. Sau thay đổi điểm/batch/T, preview mới61130; sau block hết batch đủ điều kiện, preview60930. Các giá trị thuộc các cấu hình khác nhau, không phải cùng một fixture bất biến.

Bằng chứng response gốc: [metadata snapshot1](evidence/F03_SNAPSHOT_1.json), [matrix snapshot1](evidence/F03_SNAPSHOT_1_MATRIX.json). Source hash: `d43e282cd16c316364c94793a6c01131ea6b29c986cd90f098d2c247d7eca83e`. Matrix hash: `d1fd432876a91fa898eea1a8ae2ed8dc7510a6f4387ea02ee3c8e42628d0b055`.

## Build, migration và OpenAPI

`mvn -q verify` exit0; 44 tests PASS. Fresh Flyway V1–V5 succeeded, restart validated V5 without pending migration. OpenAPI liệt kê đủ 11 operation, T là integer/int32 tối thiểu1; JSON string/fraction bị400. Mô tả503 phản ánh dependency không khả dụng. Các lần sửa và retest được ghi trong `F03_SWAGGER_ISSUES.md`.

## Giới hạn

Không thực thi tích hợp dữ liệu F04 thật hay F05 mining; không triển khai Frontend hoặc thuật toán mining. Kiểm thử UI sử dụng dữ liệu QA được tạo có kiểm soát và ngày phân tích 2026-10-17. Historical snapshot luôn sử dụng ngày đã lưu. Bằng chứng UI từng request nằm trong lịch sử công cụ của phiên; hai JSON response và tổng hợp Maven được lưu bền vững trong thư mục evidence, không có 120 screenshot riêng.

# ROC-20261115 — Tổng hợp thay đổi ngày 15/11/2026

| Trường | Giá trị |
|---|---|
| Date / timezone | 2026-11-15 / Asia/Ho_Chi_Minh |
| Phạm vi | Merge F07/F08; F05 mining progress và quyền Merchandiser; kế hoạch F03 Weight v2 |
| Trạng thái thực hiện | COMPLETED trong phạm vi Checkpoint 1; toàn bộ kế hoạch F03/F05 vẫn PARTIAL |
| Trạng thái phát hành | F07/F08 đã merge/push dev tại `8f94f94`; phần F05 mới UNRELEASED |

## Các phiên

1. [ROC-20261115-001 — Merge F07/F08 và sửa kiểm thử migration](01-f07-f08-merge.md).
2. [ROC-20261115-002 — F05 progress, quyền Merchandiser và Checkpoint 1](02-f05-progress-checkpoint-1.md).

3. [ROC-20261115-003 — Duyệt Checkpoint 1 và kiểm thử opt-in](03-opt-in-gate-and-weight-v2.md).

## Added

- Migration mới **V24** bổ sung stage, thời điểm bắt đầu stage và current run cho mining request; bản ghi cũ nhận LEGACY.
- Hai API tiến độ: `GET /api/mining/requests/{id}/progress` và `GET /api/system/mining/stores/{storeId}/requests/{requestId}/progress`.
- Tiến độ được ghi dưới lease/fence; SAVING được ghi bằng transaction ngắn trước khi lưu kết quả. API đọc cột scalar, không giải mã frozen JSON.

## Changed / Security

- `POST /api/mining/requests` cho phép **Merchandiser của Store đó** gửi snapshotId/requestKey; thuật toán và minUtil lấy từ cấu hình hiện hành của System Admin.
- Không cho gửi Store ID, thuật toán hoặc minUtil để ghi đè. Store Admin vẫn không được submit; Store khác trả 404; quyền System Admin giữ nguyên.
- Quyết định đã duyệt: chặn yêu cầu mới của Merchandiser khi λ trong snapshot vượt λ_max hiện tại, yêu cầu snapshot hợp lệ. Không sửa snapshot cũ; retry cùng requestKey đã tồn tại trả lại yêu cầu gốc.
- Scheduler vẫn giữ cấu hình mặc định tắt, API bật lịch trả 503.

## Fixed / Database

- Sửa hai test migration có số lượng cố định từ V21, vẫn giữ kiểm tra checksum và bảo toàn dữ liệu; bản sửa thuộc merge F07/F08.
- Không sửa migration V1–V23. V25 cho F03 mới chỉ được dự kiến, chưa tạo.
- Chỉ dùng H2 và MySQL 8.4.13 dùng một lần. Container MySQL kiểm thử đã dừng.

## Verification — bằng chứng của phiên triển khai, không chạy lại trong lượt cập nhật docs

| Lượt chạy | Tổng | PASS | FAIL / ERROR | SKIPPED |
|---|---:|---:|---:|---:|
| Baseline trước sửa test migration | 609 | 409 | 2 / 0 | 198 |
| Baseline merge F07/F08 sau sửa | 609 | 411 | 0 / 0 | 198 |
| Maven clean verify trên bản cuối F05 | 621 | 418 | 0 / 0 | 203 |
| Nhóm MySQL riêng trên bản cuối | 33 | 33 | 0 / 0 | 0 |

Nhóm riêng gồm **32 test MySQL và 1 test H2**, có test trùng với lượt mặc định nên không cộng thẳng thành tổng test độc lập. Trong 203 test bị bỏ qua ở lượt mặc định, 32 đã chạy riêng; **171 vẫn NOT RUN**.

Bằng chứng: [merge F07/F08](F07_F08_MERGE_TEST_COUNTS.json), [Maven verify F05](F05_CHECKPOINT1_VERIFY_COUNTS.json), [nhóm MySQL](F05_CHECKPOINT1_MYSQL_COUNTS.json). Các lượt thất bại trung gian và nguyên nhân/retest được giữ trong record 02.

OpenAPI đã xuất từ Springdoc qua MockMvc: **98 paths / 116 operations**. Chưa chạy Swagger UI tương tác, GitHub CI hay Linux multi-container trong phạm vi này.

## Documentation / Compatibility

Đã cập nhật trong nhánh F05: API contract, README F05, RBAC matrix, frontend handoff, API changelog, OpenAPI và kế hoạch. Body Store POST hiện chỉ nhận snapshotId/requestKey; body thuật toán/minUtil cũ bị từ chối 400. F03 Weight hai thành phần và season API **chưa có**.

P2–P6 vẫn được ghi là **assumption pending owner confirmation**, chưa coi là business approval. Không sửa Word reports; các mục cần cập nhật sau được liệt kê trong record 02.

## Delivery và vị trí code

- F07/F08: đã merge/push riêng lên dev tại `8f94f94`; không deploy.
- F05: nhánh `Khoi/feat/f05-progress-f03-weight-v2`, worktree `/private/tmp/paspm-f05-progress-weight-v2`; hai source commit local `3bdfee3`, `bd60c75`; docs được commit riêng theo phê duyệt Checkpoint 1. Chưa push.
- Theo yêu cầu cập nhật docs, bản ghi ngày này được đồng bộ vào `BE/docs/record-of-changes/2026-11-15/` để đọc từ workspace chính. **Sao chép record không có nghĩa code F05 đã được đưa vào checkout BE.** Các thay đổi docs ngày 14/11 được giữ nguyên.
- Lượt cập nhật record này chỉ chỉnh tài liệu, kiểm tra JSON/link và đối chiếu bản sao; không chạy lại Maven/MySQL.
- Checkpoint 1 đã được duyệt. Gate opt-in đã đạt: 627 PASS trong full verify + 5 PASS riêng trên dữ liệu JPA giả lập; xem record 03. Được phép tạo ba commit local, sau đó tiếp tục Phase 2; không push/merge/deploy phần mới.

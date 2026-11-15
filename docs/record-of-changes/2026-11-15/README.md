# ROC-20261115 — Tổng hợp thay đổi ngày 15/11/2026

| Trường | Giá trị |
|---|---|
| Date / timezone | 2026-11-15 / Asia/Ho_Chi_Minh |
| Phạm vi | Merge F07/F08; F05 mining progress và quyền Merchandiser; F03 Weight v2 (V25) và consumers; docs sync |
| Trạng thái thực hiện | COMPLETED đến Checkpoint 2 (F03/F05) và docs sync 005; xem cập nhật cuối ngày |
| Trạng thái phát hành | F07/F08 đã merge/push dev tại `8f94f94`; F05/F03 COMMITTED local (`3bdfee3`, `bd60c75`, `982094c`, `e38b55f`, `c8c837a`), chưa push; docs sync 005 chưa commit |

## Các phiên

1. [ROC-20261115-001 — Merge F07/F08 và sửa kiểm thử migration](01-f07-f08-merge.md).
2. [ROC-20261115-002 — F05 progress, quyền Merchandiser và Checkpoint 1](02-f05-progress-checkpoint-1.md).

3. [ROC-20261115-003 — Duyệt Checkpoint 1 và kiểm thử opt-in](03-opt-in-gate-and-weight-v2.md).
4. [ROC-20261115-004 — F03 Weight v2 (expiry risk + season), Phases 2–6, Checkpoint 2](04-f03-weight-v2-expiry-season.md) — V25, owner conversion rule, seasons, push days, F06 knock-on; UNRELEASED, uncommitted.
5. [ROC-20261115-005 — Docs sync: feature docs, decisions and records for F03 Weight v2 and F05 progress](05-docs-sync-f03-w2-f05-progress.md) — documentation only, uncommitted.

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
- Không sửa migration V1–V23. (Cập nhật 004: V25 cho F03 đã được tạo trong record 004; V1–V24 không bị sửa.)
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

## End-of-day update (ROC-20261115-005)

| Work | Record | Commits | Migration | Counted tests | NOT RUN |
|---|---|---|---|---|---|
| Checkpoint 1 — F05 progress, Merchandiser submission | [002](02-f05-progress-checkpoint-1.md), gate [003](03-opt-in-gate-and-weight-v2.md) | `3bdfee3`, `bd60c75`, `982094c` | V24 | full opt-in 632 / **627 PASS** / 5 SKIP, exit 0; JPA 5/5 | Linux SPMF orphan cleanup, GitHub CI, Swagger UI, real-data clone |
| Checkpoint 2 — F03 Weight v2 + F02/F05/F06 consumers | [004](04-f03-weight-v2-expiry-season.md) | `e38b55f`, `c8c837a` | V25 | phase6c 645 / **640 PASS** / 5 SKIP, exit 0; JPA 5/5 | same + real-data conversion |
| Docs sync | [005](05-docs-sync-f03-w2-f05-progress.md) | none (proposed) | — | docs checks only | — |

Invalid runs (not counted) are listed in records 003 §Verification and 004 §5. Before-deploy checklist and report changes: [record 004 addendum](04-f03-weight-v2-expiry-season.md#before-deploy-checklist-none-done). Decisions: [owner decision register](../../decisions/OWNER_DECISIONS_F03_F05_2026-11-15.md).

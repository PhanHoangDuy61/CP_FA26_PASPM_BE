# ROC-20261111-004 — Feature branch publication và merge reconciliation

| Trường | Giá trị |
|---|---|
| Record ID | `ROC-20261111-004` |
| Date / timezone | 2026-11-11 / Asia/Ho_Chi_Minh |
| Agent | Codex |
| Type | Git integration / Verification |
| Features | F01 và tài liệu thay đổi |
| Starting branch / HEAD | `dev` / `5b30eb1` |
| Feature branch | `Khoi/Fear/f01-session-auth-and-change-records` |
| Target observed after fetch | `origin/dev` / `42b8a9c` |
| Execution status | PARTIAL — bản local được lưu riêng; merge cần quyết định reconciliation |
| Release status | FEATURE_BRANCH — không merge/deploy |
| Daily summary | [2026-11-11](README.md) |

## 1. Request and scope

Người dùng yêu cầu tách nhánh, push rồi merge vào dev. Quyền commit/push/merge đã được cấp cho thay đổi F01 và docs đang làm. Không coi đó là quyền ghi đè một implementation mới của người khác, thay SQL đã có trên dev, repair database hoặc force-push.

## 2. Change details

### Added / Changed

Tạo nhánh feature từ HEAD local; chọn riêng source/tests/V20 F01, env example không chứa secret, docs và evidence. Sao chép screenshot Customer đã loại credential vào `docs/testing/evidence/f01_session/` để link hoạt động sau khi clone.

### Security

Không stage application.yml vì có cấu hình local riêng; không stage diagram đã xóa, Draw.io temp/backup hay .DS_Store. Kiểm tra các staged text không chứa secret từ environment riêng. Review độc lập cho bản local không phát hiện lỗi chặn nội bộ; kết luận đó không áp dụng như approval cho việc merge vào dev mới.

### Database

Không sửa database, không repair, không sửa migration đang có trên dev.

## 3. Remote changes and merge blocker

Fetch phát hiện:

- `a89459d`: merge PR #14, bổ sung docs/staging kit.
- `42b8a9c`: implementation refresh sessions khác đã có trên dev.

Hai nhánh đều thêm cùng tên/version `V20__f01_refresh_sessions.sql` nhưng schema khác:

| Local feature | Remote dev |
|---|---|
| auth_session | auth_refresh_session |
| Hex CHAR(64) token_hash; csrf_hash | BINARY(32) token_hash; schema token_id/consumed_at khác |
| RefreshSessionService + SessionController | AuthSessionService và endpoints trong AuthController |
| Absolute family expiry, stored CSRF binding, JWT sid revocation checks | Có idle expiry/cleanup; các kiểm tra session/CSRF/Origin không tương đương |

Giữ cả hai controller gây duplicate mappings; chọn một bên V20 có thể làm checksum/schema không tương thích. Không resolve bằng ours/theirs cho toàn bộ auth hoặc migration.

Đã hỏi người dùng lựa chọn: ưu tiên giữ V20/implementation đã có trên dev và hợp nhất bổ sung local bằng migration mới nếu cần; hoặc lập phương án chuyển đổi trước khi thay thế; hoặc chỉ push bản local và tạm dừng merge. Chưa nhận quyết định tại thời điểm ghi record này. Đây không phải lỗi quyền GitHub hay kết quả auto-review rejection.

## 4. Verification

Fresh staged snapshot, tracked config only: Maven verify exit0, **577 discovered / 387 PASS / 190 SKIPPED**, zero failures/errors. Backend Java17 and SPMF Java25. [Per-suite evidence](../../testing/reports/F01_SESSION_PUBLISH_VERIFY_COUNTS.json).

Phát hiện tổng cũ 578/388 có một XML MiningAutomaticSchedulerMySqlTest còn sót, không còn source test; đã ghi đính chính trong report và docs hiện hành. Báo cáo/evidence cũ không bị sửa số âm thầm. Một assertion kiểm đếm kỳ vọng 578 đã fail vì chênh lệch này; Maven trước đó đã exit0 và không có test failures. Đây là lỗi kỳ vọng tổng hợp, không phải ca kiểm thử fail bị bỏ qua.

Review và kiểm tra này không xác nhận bản đã reconcile với remote. Không chạy hosted CI hoặc deploy. Các báo cáo local trước giữ nguyên ngày và phạm vi.

## 5. Delivery

Đã commit và push `9d33d88` lên `origin/Khoi/Fear/f01-session-auth-and-change-records`. Theo dõi remote branch đã được thiết lập. Record này và đính chính được bổ sung trong commit docs kế tiếp; không nhận SHA của nó trước khi tạo commit.

[Feature branch](https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/tree/Khoi/Fear/f01-session-auth-and-change-records).

Merge vào dev **BLOCKED_PENDING_RECONCILIATION_DECISION**. Cần đối chiếu DTO/config/security và migration mới theo lựa chọn được duyệt, sau đó kiểm thử toàn bộ kết quả merge. Không triển khai feature branch đè lên database đã áp dụng V20 của dev.

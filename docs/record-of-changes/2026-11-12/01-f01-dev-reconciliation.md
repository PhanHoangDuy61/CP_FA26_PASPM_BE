# ROC-20261112-001 — Reconcile F01 với dev và tích hợp Git

| Trường | Giá trị |
|---|---|
| Record ID | ROC-20261112-001 |
| Date / timezone | 2026-11-12 / Asia/Ho_Chi_Minh |
| Agent | Codex |
| Type | Security / Database / Integration / Documentation |
| Features | F01; F02–F06 dùng chung xác thực |
| Bases | dev42b8a9c và local feature689526b |
| Work branch | Khoi/Fear/f01-session-dev-reconciliation |
| Execution status | COMPLETED — local verification; Git integration pending below |
| Release status | PENDING_AUTHORIZED_MERGE — không deploy |
| Daily summary | [2026-11-12](README.md) |

## 1. Request and authorization

Người dùng đã yêu cầu tách nhánh → push → merge vào dev và chốt: **“Giữ V20 và implementation trên dev; hợp nhất bổ sung từ local, dùng migration mới nếu cần (khuyến nghị)”**. Không xin lại quyền cho commit/push/merge; không coi quyền đó là quyền sửa DB hoặc force-push.

Thực hiện trên worktree riêng. Workspace BE gốc giữ nguyên cấu hình private, diagram xóa sẵn và các file ngoài phạm vi; backend/database cũ không bị restart/migrate bởi việc hợp nhất.

## 2. Change details

### Added

V21 csrf_hash BINARY(32) NULL trên auth_refresh_token; Origin guard; tests cho V20→V21 giữ nguyên dữ liệu/checksum, idle expiry, unbound legacy tokens, duplicate cookies và các trường hợp refresh401.

### Changed

Giữ AuthSessionService/AuthCredentials, controller/service API và thời hạn absolute/idle + cleanup từ dev. Loại RefreshSessionService/SessionController/SessionHttpSupport của nhánh local để tránh duplicate routes và schema. Auth session JDBC vẫn theo kiến trúc hiện có.

### Fixed / Security

Bind CSRF với refresh credential; logout kiểm tra CSRF thuộc family JWT và không bắt raw refresh cookie, giữ contract dev. JWT sid kiểm tra user/version, revoked_at, absolute/idle expiry. Thêm Origin/duplicate-cookie rejection, no-store và clear cookies khi refresh401;403 không phá phiên hợp lệ. OpenAPI public login/google/refresh.

### Database

Published V20 SHA-256 `534003c0baf641960aac34ff5d5ad03b35a468f46d283593bf742e692d7c0002` không đổi. V21 không backfill/giả mạo CSRF; cột cũ NULL khiến refresh401 và yêu cầu sign-in lại. Không có repair, reset hoặc migration trên DB nguồn.

### Documentation / CI

Canonical F01 API/guide/handoff/setup chuyển sang schema/settings dev+V21. Giữ các record/evidence ngày11 làm lịch sử; cập nhật trạng thái superseded. Các test mới được đưa vào backend/MySQL CI selector; không tự coi hosted CI đã chạy.

## 3. Files and compatibility

Source: AuthController/AuthService/AuthSessionService/SecurityConfig, SessionOriginGuard; V21; session/migration tests; .github/scripts/test-groups.py; env example; canonical F01 docs/OpenAPI và record/report ngày12. V1–V20 và code nghiệp vụ F02–F06 không sửa.

Giữ TOKEN_TTL_SECONDS=3600, REFRESH_TOKEN_ABSOLUTE_TTL_SECONDS=2592000, REFRESH_TOKEN_IDLE_TTL_SECONDS=604800, REFRESH_COOKIE_SECURE=true; thêm PASPM_SESSION_ALLOWED_ORIGINS. Settings local PASPM_SESSION_COOKIE_SECURE/REFRESH_TTL_SECONDS cũ không dùng. Frontend không sửa.

## 4. Verification

- Red ban đầu trên dev canonical: 11 ca, 8 failures đúng các khoảng trống CSRF/Origin/session revocation/OpenAPI.
- Green sau tích hợp: Auth28 + Session14 + V21 migration1 =43 PASS.
- Hai red cases tiếp theo phát hiện refresh401 chưa clear cookie (token service failure và thiếu cookie); sửa controller rồi chạy clean verify cuối.
- Engineering review độc lập không còn blocker sau patch cookie; đây không phải human PR approval.

Final clean verify: 594 discovered /399 PASS /195 SKIPPED, zero errors/failures. Disposable MySQL8.4.13:29 PASS /0 SKIPPED. Actual Java17 HTTP11 PASS; OpenAPI92/Swagger200. Trong195 skip mặc định,28 ca đã chạy riêng,167 ca còn lại không được xác nhận. Không chạy interactive browser/Swagger hoặc hosted CI trong lượt này.

Kết quả cuối và command/evidence: [reconciliation report](../../testing/reports/F01_SESSION_RECONCILIATION_2026-11-12.md).

## 5. Limits and follow-up

Không deploy. Không chuyển đổi database local đã áp dụng V20 của implementation bị loại; chỉ áp dụng tự động V21 lên published dev V20. Google thật/production ingress/browser trên bản cuối và hosted CI chỉ được ghi PASS nếu có thực thi cụ thể trong report. Không thay PO policy hoặc scheduler gate.

## 6. Git delivery

Pending final gate verification and authorized push/merge. Record này ghi trạng thái tại commit; Git history/remote refs là bằng chứng cuối cho việc merge. Không force-push hoặc bypass branch protection.

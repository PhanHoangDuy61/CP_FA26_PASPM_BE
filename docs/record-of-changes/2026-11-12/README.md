# Record of Changes — 2026-11-12

| Trường | Giá trị |
|---|---|
| Daily ID | ROC-20261112 |
| Date / timezone | 2026-11-12 / Asia/Ho_Chi_Minh |
| Project / agent | PASPM Backend / Codex |
| Scope | F01 dev reconciliation, verification và Git integration |
| Base | Published dev42b8a9c + local feature689526b |
| Status | MERGED_TO_DEV — code7304e29 đã push; không deploy |

## Session records

| ID | Record | Nội dung |
|---|---|---|
| ROC-20261112-001 | [01 — F01 dev reconciliation](01-f01-dev-reconciliation.md) | Giữ V20/AuthSessionService của dev, bổ sung V21 và kiểm thử, merge theo quyền đã cấp |

## Added

V21 csrf_hash nullable; SessionOriginGuard; migration-preservation và session edge-case tests; CI selectors cho test mới.

## Changed / Fixed / Security

Tích hợp trên AuthSessionService hiện có, bỏ implementation local trùng; kiểm tra JWT sid/family, server-bound CSRF, Origin, duplicate cookies, invalid-refresh cookie clearing và Swagger public requirements. Không thay rule của F02–F06.

## Database

V20 giữ byte-identical. V21 không rewrite token/history; token V20 chưa bound cần đăng nhập lại. Chỉ thử trên MySQL container riêng; không sửa DB nguồn/local đang chạy.

## Verification

Clean verify399 PASS/195 SKIPPED; MySQL29 PASS/0 SKIPPED; actual HTTP11 PASS, OpenAPI92/Swagger200. Đây là kiểm tra local, không phải hosted CI/interactive Swagger.

[Reconciliation report](../../testing/reports/F01_SESSION_RECONCILIATION_2026-11-12.md) ghi kết quả cuối, phân biệt skipped và kiểm thử độc lập. Không dùng kết quả branch local cũ để chứng nhận schema mới.

## Delivery and limits

Theo quyền người dùng: branch → push → merge vào dev khi gates pass, không deploy. Database đã áp dụng local V20 khác không được repair hoặc dùng build mới tự động; cần quy trình chuyển đổi riêng.

[Parent index](../README.md) · [Template](../TEMPLATE.md).

Code merge7304e29 confirmed on remote dev; post-merge47/47 PASS. [Closing evidence](../../testing/reports/F01_SESSION_RECONCILIATION_MERGED_COUNTS.json). Full verify399PASS/195SKIPPED, independent MySQL29PASS and actual HTTP11PASS retain their separate scopes; no hosted CI claim.

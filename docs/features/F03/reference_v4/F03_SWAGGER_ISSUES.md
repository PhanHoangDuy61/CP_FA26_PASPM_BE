# F03 — Defects, retest và dependency

Ngày: 2026-10-17, Asia/Ho_Chi_Minh.

| ID | Phát hiện thực tế | Nguyên nhân / sửa hẹp | Retest | Trạng thái |
|---|---|---|---|---|
| F03-B01 | PATCH một score trả400 khi bỏ qua score còn lại | Record/null binding không phân biệt omitted với explicit null; chuyển ScorePatch sang setters chỉ reject explicit null | Automated partial PATCH và UI PATCH S=.8 rồi M=.9, GET giữ cả hai, 200; null400 | FIXED |
| F03-B02 | T được OpenAPI mô tả string dù nghiệp vụ yêu cầu integer | BigDecimal/schema annotation; đổi Integer với StrictIntegerDeserializer, @Min(1) | Schema integer/int32; UI T100200;1.5 và string"30"400; automated schema assertion PASS | FIXED |
| F03-B03 | Mô tả503 chỉ nói email/Google | Response description dùng chung chưa bao quát F04 | Đổi description thành configuration/dependency unavailable; OpenAPI assertion PASS; runtime F04 absent503 | FIXED |
| QA-01 | Hai lượt setup dùng nhầm token do đọc response Swagger cũ | Helper chưa clear/wait response mới | Clear trước Execute, đọc bảng mới, GET/me đối chiếu roles; chạy lại case bị ảnh hưởng | RESOLVED; không phải backend defect |
| DEP-F04 | Không có production TransactionSource hoặc SALES_TRANSACTION | F04 chưa tồn tại trong repository | Profile dev trả503; fixture profile hoạt động; snapshot cũ vẫn đọc200 | BLOCKED integration |
| DEP-F05 | Không có MINING_REQUEST hoặc consumer mining | Ngoài scope F03 | Handoff contract và scaled64bit được kiểm tra; chưa chạy mining | BLOCKED integration |

Không phát hiện endpoint defect chưa giải quyết trong bộ test đã thực thi. Khởi động qua Maven plugin ban đầu gặp sandbox cache/bind permission; sử dụng main class thực với test-runtime classpath và quyền bind được cho phép. Không tắt security hoặc sửa dữ liệu dùng chung.

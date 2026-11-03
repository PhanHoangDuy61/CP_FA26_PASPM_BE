# F03 CodeGraph Report

2026-10-17. CodeGraph CLI0.9.9 và MCP đã được dùng trước triển khai để đọc kiến trúc, sau triển khai để cập nhật/kiểm tra graph. Index dùng storage chuẩn tại workspace `.codegraph`, không thêm DB/index vào Git.

Baseline:734 files,8626 nodes,15769 edges. Lần kiểm tra cuối: **756 files,9029 nodes,16643 edges**, SQLite WAL/FTS5. Ngôn ngữ Java,TS/TSX,JS có trong graph, bao gồm source Backend và Frontend có sẵn; Frontend chỉ được index, không chỉnh sửa. Generated/dependencies/Git/secret và env files được loại theo cấu hình index đã kiểm tra.

Đã tra cứu CatalogAccess, StoreScope, AuthService, catalog repository/service conventions; xác nhận chưa có F04 transaction implementation. Sau triển khai, graph tìm thấy UtilitySnapshotController → UtilitySnapshotService, UtilityMatrixBuilder, UtilitySettingsRepository và các class utility mới; Store ownership tiếp tục đi qua CatalogAccess/StoreScope của F01. StrictIntegerDeserializer được cập nhật vào graph cuối. CodeGraph chỉ phản ánh dependency source; không thay thế kiểm thử runtime/security.

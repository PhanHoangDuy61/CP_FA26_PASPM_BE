# PASPM Backend

Backend cho FA26_SE048 — Product Arrangement Support Platform. Source hiện tại có các module F01–F06, Java 17/Spring Boot, Hybrid JPA + JDBC, MySQL/Flyway và SPMF chạy bằng child JVM riêng.

## Getting Started — Local Development (Windows / macOS)

Chọn hướng dẫn [Windows PowerShell](docs/setup/LOCAL_SETUP_WINDOWS.md) hoặc [macOS zsh](docs/setup/LOCAL_SETUP_MACOS.md); xem [cấu hình chung](docs/setup/LOCAL_SETUP_COMMON.md) và [xử lý lỗi](docs/setup/LOCAL_SETUP_TROUBLESHOOTING.md). Backend dùng JDK 17; F05 cần JAR SPMF 2.67a được duyệt và Java 25+ riêng. Scheduler hiện trả 503 khi bật và chưa sẵn sàng. Các bước cài mới Windows/macOS chưa được kiểm chứng đầy đủ trên máy sạch.

Đích triển khai dự kiến là Linux Docker; `compose.yml` hiện chỉ cung cấp MySQL và Mailpit, chưa có Backend Dockerfile. Cơ chế Linux dọn tiến trình SPMF và các điều kiện triển khai đang được kiểm chứng; xem [kiến trúc worker](docs/architecture/F05_WORKER_DISPATCH.md) trước khi triển khai.

- **Bắt đầu từ [cổng tài liệu](docs/README.md).**
- [Chạy local](docs/setup/LOCAL_DEVELOPMENT.md) và [SPMF runtime](docs/setup/F05_SPMF_RUNTIME_SETUP.md).
- [API contracts](docs/api/README.md), [Frontend handoff](docs/frontend_handoff/README.md).
- [Kiến trúc](docs/architecture/README.md), [testing](docs/testing/README.md), [bản đồ đường dẫn](docs/DOCUMENTATION_MAP.md).

Migration vật lý do Flyway quản lý; Hibernate chỉ validate schema. Tài liệu và kết quả test có phạm vi/commit riêng; xem trạng thái và giới hạn ở từng index.

[README F01 ban đầu](docs/archive/F01/ROOT_README_BASELINE.md) được giữ nguyên như hồ sơ lịch sử.

Docker Linux: xem [SPMF runtime và cách build image](docs/setup/F05_SPMF_RUNTIME_SETUP.md#linux-docker-image-working-tree-acceptance-pending). Kiểm thử hai container và crash recovery của image cuối cùng chưa hoàn tất; scheduler vẫn trả 503 khi bật.

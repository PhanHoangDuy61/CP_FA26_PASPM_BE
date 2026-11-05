# PASPM Backend

Backend cho FA26_SE048 — Product Arrangement Support Platform. Source hiện tại có các module F01–F06, Java 17/Spring Boot, Hybrid JPA + JDBC, MySQL/Flyway và SPMF chạy bằng child JVM riêng.

F01 access JWT mặc định hết hạn sau 3600 giây; refresh session mặc định 30 ngày tuyệt đối/7 ngày không hoạt động, dùng opaque token trong HttpOnly cookie và CSRF double-submit. Cấu hình trong [hướng dẫn setup chung](docs/setup/LOCAL_SETUP_COMMON.md) và contract [F01](docs/api/F01_AUTH_USER_API.md).

## Getting Started — Local Development (Windows / macOS)

Chọn hướng dẫn [Windows PowerShell](docs/setup/LOCAL_SETUP_WINDOWS.md) hoặc [macOS zsh](docs/setup/LOCAL_SETUP_MACOS.md); xem [cấu hình chung](docs/setup/LOCAL_SETUP_COMMON.md) và [xử lý lỗi](docs/setup/LOCAL_SETUP_TROUBLESHOOTING.md). Backend dùng JDK 17; F05 cần JAR SPMF 2.67a được duyệt và Java 25+ riêng. Scheduler hiện trả 503 khi bật và chưa sẵn sàng. Các bước cài mới Windows/macOS chưa được kiểm chứng đầy đủ trên máy sạch.

Đích triển khai dự kiến là Linux Docker; `compose.yml` cung cấp MySQL và Mailpit, còn `Dockerfile` và `compose.backend.yml` là định nghĩa image Backend tùy chọn (JRE 25 và SPMF được ghim bằng SHA-256, chưa được kiểm chứng trên môi trường production). Cơ chế Linux dọn tiến trình SPMF (`PR_SET_PDEATHSIG`) đã được kiểm thử trong container Linux; xem [kiến trúc worker](docs/architecture/F05_WORKER_DISPATCH.md) trước khi triển khai.

- **Bắt đầu từ [cổng tài liệu](docs/README.md).**
- [Chạy local](docs/setup/LOCAL_DEVELOPMENT.md) và [SPMF runtime](docs/setup/F05_SPMF_RUNTIME_SETUP.md).
- [API contracts](docs/api/README.md), [Frontend handoff](docs/frontend_handoff/README.md).
- [Kiến trúc](docs/architecture/README.md), [testing](docs/testing/README.md), [bản đồ đường dẫn](docs/DOCUMENTATION_MAP.md).

Migration vật lý do Flyway quản lý; Hibernate chỉ validate schema. Tài liệu và kết quả test có phạm vi/commit riêng; xem trạng thái và giới hạn ở từng index.

[README F01 ban đầu](docs/archive/F01/ROOT_README_BASELINE.md) được giữ nguyên như hồ sơ lịch sử.

Docker Linux: xem [SPMF runtime và cách build image](docs/setup/F05_SPMF_RUNTIME_SETUP.md#linux-docker-image-on-dev-production-acceptance-pending). Scheduler vẫn trả 503 khi bật và bộ phát (dispatcher) mặc định tắt; xem [báo cáo sẵn sàng phát hành Core](docs/reports/F03_F05_CORE_RELEASE_READINESS_2026-11-02.md).

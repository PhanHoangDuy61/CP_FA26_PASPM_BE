# PASPM Backend

Backend cho FA26_SE048 — Product Arrangement Support Platform. Source hiện tại có các module F01–F06, Java 17/Spring Boot, Hybrid JPA + JDBC, MySQL/Flyway và SPMF chạy bằng child JVM riêng.

- **Bắt đầu từ [cổng tài liệu](docs/README.md).**
- [Chạy local](docs/setup/LOCAL_DEVELOPMENT.md) và [SPMF runtime](docs/setup/F05_SPMF_RUNTIME_SETUP.md).
- [API contracts](docs/api/README.md), [Frontend handoff](docs/frontend_handoff/README.md).
- [Kiến trúc](docs/architecture/README.md), [testing](docs/testing/README.md), [bản đồ đường dẫn](docs/DOCUMENTATION_MAP.md).

Migration vật lý do Flyway quản lý; Hibernate chỉ validate schema. Tài liệu và kết quả test có phạm vi/commit riêng; xem trạng thái và giới hạn ở từng index.

[README F01 ban đầu](docs/archive/F01/ROOT_README_BASELINE.md) được giữ nguyên như hồ sơ lịch sử.

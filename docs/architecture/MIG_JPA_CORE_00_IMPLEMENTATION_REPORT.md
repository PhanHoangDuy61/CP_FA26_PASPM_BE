# MIG-JPA-CORE-00 — Implementation Report

Ngày thực thi: 2026-10-22, Asia/Ho_Chi_Minh. Branch `Khoi/Fear/jpa-product-metadata-pilot`; base HEAD `3d58a73543db3b5923289f901f9ffecba6228d23`. Changes ở pilot branch, chưa merge/push hoặc deploy.

## Quyết định và phạm vi

**APPROVED / IMPLEMENTED / TESTED trên DB test riêng.** Người dùng đã duyệt giữ Java17, Spring Boot3.5.7, MySQL, Flyway, Spring Security và hybrid pilot CORE-00 + Product metadata ONLY. Không cần đổi theo stack .NET trong RP2 final. Task gốc: attachment `dda164a7-faa6-46c5-b8ba-0a3a1b0e9142`. Sau probe nguồn V5/rỗng, người dùng duyệt thay real populated-dataset verification bằng simulated food-store dataset trên DB tách biệt; không duyệt migrate/repair DB nguồn.

Các khuyến nghị module khác trong audit vẫn **NOT APPROVED / NOT IMPLEMENTED**. Audit ngày21/10 là historical baseline, không dùng thay evidence hôm nay.

## Foundation đã triển khai

| File | Thay đổi |
|---|---|
| `pom.xml` | Thêm spring-boot-starter-data-jpa theo Boot BOM, không pin Hibernate hoặc nâng Boot |
| `common/persistence/PersistenceConfiguration.java` | Nạp safe persistence.properties; để Boot auto-configure một manager, không thêm manager cạnh tranh |
| `src/main/resources/persistence.properties` | `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false` |
| `AuthIntegrationTest.java` | Chỉ sửa test schema setup từ subset V1/V2/V6 sang đầy đủ Flyway, tắt SQL init; giữ auth assertions |

Effective POM export: `/tmp/paspm-jpa-pilot/effective-pom.xml`, starter3.5.7; actual resolved Hibernate ORM6.6.33.Final. Backend runtime17.0.20.1, SPMF child runtime25 riêng. Không đổi Spring Boot3.5.7 hoặc source migrations. `application.yml` giữ nguyên SHA256 so với đầu task, bao gồm private edits từ trước; không đưa secrets vào patch.

Actual wiring được kiểm tra trên H2 và MySQL:

- Chỉ một PlatformTransactionManager bean: **JpaTransactionManager**.
- EntityManagerFactory và manager trỏ cùng HikariDataSource mà JdbcTemplate dùng.
- JpaDialect = HibernateJpaDialect.
- DataSourceUtils.getConnection và Hibernate Session.doWork nhận **cùng connection object trong transaction thật**, không chỉ so sánh tên bean.
- Actual JDBC isolation READ_COMMITTED và REPEATABLE_READ được assert; MySQL-only test kiểm chứng một independent native commit không lọt vào RR snapshot trước khi transaction ngoài kết thúc.
- F04 dùng đúng TransactionTemplate REQUIRES_NEW; inner JDBC commit vẫn tồn tại sau outer JPA rollback. Không đổi thành nested/savepoint.
- F01 noRollbackFor, F03 RR snapshot và F06 RC/CAS không sửa source; existing regression suites vẫn pass.

Không có Flyway migration mới, không bảng/cột/index mới của production, không Hibernate create/update/drop. Flyway V1–V10 SHA256 giữ nguyên. Fresh/upgrade test paths đã chạy trên schema test riêng. Historical recovery test chỉ repair H2 fixture theo thiết kế test có sẵn, không repair DB nguồn.

## TDD và kiểm chứng

RED thật trước implementation: `foundation-red.log` expected JpaTransactionManager, actual JdbcTransactionManager; `product-mapping-red-verified.log` chưa có entity; `product-writer-red-final.log` ORM insert expected1 actual0. Không coi RED là production defect.

Final Maven command (paths/fixtures riêng, không credential):

```text
JAVA_HOME=<JDK17> PASPM_JPA_PILOT_MUTATIONS_ALLOWED=true
PASPM_MINING_SPMF_JAR=<existing-spmf.jar> PASPM_MINING_JAVA_EXECUTABLE=<JDK25-java>
mvn -o -Dpaspm.avatar.directory=<temporary-directory> -Dpaspm.test.mysql=true
-Dpaspm.test.jpa-clone=true -Dpaspm.test.java17-backend=true
-Dpaspm.test.java17=<JDK17-java> -Dpaspm.test.spmf-jar=<existing-spmf.jar>
-Dpaspm.test.spmf-java=<JDK25-java> clean verify
```

**274 tests PASS, failures0/errors0/skipped0, BUILD SUCCESS**, kết thúc04:35:29+07:00. Existing baseline hôm nay232/232 trước thay đổi; final giữ toàn bộ232 và thêm42. Counts: focused JPA H2=16, fresh MySQL=17, clone API=5, clone safety=4. Evidence [JPA_PILOT_RESULTS.json](../testing/evidence/jpa_pilot/JPA_PILOT_RESULTS.json).

Một lần clone context ban đầu lỗi thiếu test avatar directory; đã bổ sung thư mục tạm trong test configuration và retest thành công. Không sửa private application config để che lỗi. Maven help:effective-pom offline ban đầu thiếu plugin cache; export online sau đó thành công, không phải application build failure.

## Logs, limitations và rollback

- Baseline: `/tmp/paspm-jpa-pilot/baseline-verify.log` (232 PASS).
- Focused TDD: `foundation-red.log`, `product-mapping-red-verified.log`, `product-writer-red-final.log`, `product-focused-mysql-final.log` trong cùng thư mục.
- Final: `/tmp/paspm-jpa-pilot/final-verify-food.log` (274 PASS); sanitized persistent evidence tại link trên.
- Flyway11.7.2 vẫn cảnh báo tested support đến MySQL8.1 khi local là8.4.11; migrations thực tế pass. Không tự nâng tooling.
- Không test live Google provider hoặc interactive Swagger UI; không chạy Frontend/Postman. Thử nghiệm thực trên records nguồn chưa chạy vì nguồn rỗng, theo scope update đã chuyển sang simulation.
- **Chưa chạy bản app mới vào source V5.** Deployment cần backup và quy trình nâng V6–V10 được phê duyệt riêng; không coi schema nguồn đã sẵn sàng.

Rollback code: quay lại baseline JDBC artifact/code trên branch riêng, gỡ starter/config/entity/adapter đồng bộ. Không database rollback cho pilot vì production DDL không đổi. Dữ liệu mô phỏng ở disposable DB không được import về nguồn. Giữ branch unmerged cho tới khi người dùng yêu cầu tích hợp; không tự migrate Category/F01/F03.

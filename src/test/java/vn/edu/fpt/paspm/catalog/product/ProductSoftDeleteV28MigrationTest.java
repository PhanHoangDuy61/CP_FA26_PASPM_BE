package vn.edu.fpt.paspm.catalog.product;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** DEC-20261124-03: V28 only adds nullable deleted_at/deleted_by (+CHECK, FK, index); every existing product and checksum is untouched. */
class ProductSoftDeleteV28MigrationTest {
    @Test void v28AddsSoftDeleteColumnsWithoutTouchingExistingProducts() {
        verify("jdbc:h2:mem:product_v28_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    }

    static void verify(String url, String user, String password) {
        Flyway.configure().dataSource(url, user, password).target("27").load().migrate();
        var db = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        db.update("INSERT INTO store(name) VALUES ('V28 store')");
        int store = db.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        db.update("INSERT INTO app_user(store_id,role_id,email,name,status) VALUES (?,(SELECT role_id FROM role WHERE code='STORE_ADMIN'),?,'V28 admin','ACTIVE')",
                store, "v28-" + UUID.randomUUID() + "@example.test");
        int actor = db.queryForObject("SELECT MAX(user_id) FROM app_user", Integer.class);
        for (String status : List.of("ACTIVE", "INACTIVE")) {
            db.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,?,'each',FALSE,10,6,?)",
                    store, "SKU-" + status, "Existing " + status, status);
        }
        List<Map<String, Object>> before = db.queryForList("SELECT * FROM product ORDER BY product_id");
        var checksums = db.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE success=TRUE ORDER BY installed_rank");

        var latest = Flyway.configure().dataSource(url, user, password).load();
        assertTrue(latest.migrate().migrationsExecuted >= 1, "V28 must be pending after V27");
        latest.validate();
        assertEquals(checksums, db.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE success=TRUE ORDER BY installed_rank").stream()
                .filter(row -> row.get("version") == null || Integer.parseInt(String.valueOf(row.get("version"))) < 28).toList(),
                "V1-V27 checksums are unchanged");
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='28' AND success=TRUE", Integer.class));

        List<Map<String, Object>> after = db.queryForList("SELECT * FROM product ORDER BY product_id");
        assertEquals(before.size(), after.size());
        for (int i = 0; i < before.size(); i++) {
            for (var column : before.get(i).entrySet()) assertEquals(column.getValue(), after.get(i).get(column.getKey()), column.getKey());
            assertNull(after.get(i).get("deleted_at"));
            assertNull(after.get(i).get("deleted_by"));
        }

        int product = (Integer) after.get(0).get("product_id");
        assertThrows(Exception.class, () -> db.update("UPDATE product SET deleted_at=CURRENT_TIMESTAMP WHERE product_id=?", product), "deleted_at needs deleted_by");
        assertThrows(Exception.class, () -> db.update("UPDATE product SET deleted_by=? WHERE product_id=?", actor, product), "deleted_by needs deleted_at");
        assertThrows(Exception.class, () -> db.update("UPDATE product SET deleted_at=CURRENT_TIMESTAMP,deleted_by=999999 WHERE product_id=?", product), "deleted_by is an FK");
        assertEquals(1, db.update("UPDATE product SET deleted_at=CURRENT_TIMESTAMP,deleted_by=?,status='INACTIVE' WHERE product_id=?", actor, product));
        assertNotNull(db.queryForObject("SELECT deleted_at FROM product WHERE product_id=?", Object.class, product));

        assertEquals(0, latest.migrate().migrationsExecuted);
    }
}

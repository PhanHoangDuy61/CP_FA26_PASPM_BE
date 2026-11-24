package vn.edu.fpt.paspm.catalog.store;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * DEC-20261123-01 §1: V27 adds the Store profile/status columns, backfills {@code slugify(name)-{id}} for every existing row
 * BEFORE {@code slug} becomes NOT NULL + UNIQUE, and must run on a database that already holds duplicate Store names.
 */
class StoreMigrationV27Test {
    static final String SLUG = "[a-z0-9]+(-[a-z0-9]+)*";

    @Test void v27BackfillsUniqueSlugsOnDuplicateNamesAndAddsTheProfileColumns() {
        verify("jdbc:h2:mem:store_v27_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    }

    static void verify(String url, String user, String password) {
        var v26 = Flyway.configure().dataSource(url, user, password).target("26").load();
        v26.migrate();
        var db = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        String longName = "Siêu thị " + "x".repeat(140);
        List<String> names = List.of("Cửa hàng Đà Nẵng", "Cửa hàng Đà Nẵng", "Store A", "store a", "  !!!  ", longName);
        List<Integer> ids = new ArrayList<>();
        for (String name : names) {
            db.update("INSERT INTO store(name,utility_config) VALUES (?,?)", name, "{\"k\":\"" + name.length() + "\"}");
            ids.add(db.queryForObject("SELECT MAX(store_id) FROM store", Integer.class));
        }
        var configBefore = untouched(db);
        var checksums = db.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE success=TRUE ORDER BY installed_rank");

        var latest = Flyway.configure().dataSource(url, user, password).load();
        assertTrue(latest.migrate().migrationsExecuted >= 1, "V27 must be pending after V26");
        latest.validate();
        // V1-V26 (and the baseline row) keep their checksums; later migrations such as V28 may follow V27, so filter in Java
        assertEquals(checksums, db.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE success=TRUE ORDER BY installed_rank").stream()
                .filter(row -> row.get("version") == null || Integer.parseInt(String.valueOf(row.get("version"))) < 27).toList());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='27' AND success=TRUE", Integer.class));

        // name, utility_config and mining_schedule are untouched
        assertEquals(configBefore, untouched(db));

        List<String> expected = List.of(
                "cua-hang-da-nang-" + ids.get(0), "cua-hang-da-nang-" + ids.get(1),
                "store-a-" + ids.get(2), "store-a-" + ids.get(3), "store-" + ids.get(4));
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), db.queryForObject("SELECT slug FROM store WHERE store_id=?", String.class, ids.get(i)));
        }
        String longSlug = db.queryForObject("SELECT slug FROM store WHERE store_id=?", String.class, ids.get(5));
        assertTrue(longSlug.length() <= 50 && longSlug.matches(SLUG) && longSlug.startsWith("sieu-thi-x") && longSlug.endsWith("-" + ids.get(5)), longSlug);

        for (Map<String, Object> row : db.queryForList("SELECT * FROM store")) {
            assertEquals("ACTIVE", row.get("status"));
            assertEquals(1L, ((Number) row.get("version")).longValue());
            assertNotNull(row.get("created_at"));
            assertNotNull(row.get("updated_at"));
            for (String column : List.of("address", "phone", "icon_key", "banner_key", "deactivated_at", "deactivated_by")) {
                assertNull(row.get(column), column);
            }
        }
        assertEquals(new HashSet<>(db.queryForList("SELECT slug FROM store", String.class)).size(),
                db.queryForObject("SELECT COUNT(*) FROM store", Integer.class), "every slug is distinct");

        // A raw insert that omits slug (seed scripts, older tests) still gets a valid, unique slug.
        db.update("INSERT INTO store(name) VALUES ('Raw insert')");
        db.update("INSERT INTO store(name) VALUES ('Raw insert 2')");
        var raw = db.queryForList("SELECT slug FROM store WHERE name LIKE 'Raw insert%'", String.class);
        assertEquals(2, new HashSet<>(raw).size());
        raw.forEach(slug -> assertTrue(slug.matches(SLUG) && slug.length() <= 50, slug));

        // Constraints: slug NOT NULL + UNIQUE, status CHECK, deactivated_by FK
        assertThrows(Exception.class, () -> db.update("INSERT INTO store(name,slug) VALUES ('Dup slug', ?)", expected.get(2)));
        assertThrows(Exception.class, () -> db.update("INSERT INTO store(name,slug) VALUES ('Null slug', NULL)"));
        assertThrows(Exception.class, () -> db.update("UPDATE store SET status='CLOSED' WHERE store_id=?", ids.get(0)));
        assertThrows(Exception.class, () -> db.update("UPDATE store SET deactivated_by=999999 WHERE store_id=?", ids.get(0)));
        db.update("UPDATE store SET status='INACTIVE' WHERE store_id=?", ids.get(0));
        assertEquals("INACTIVE", db.queryForObject("SELECT status FROM store WHERE store_id=?", String.class, ids.get(0)));

        assertEquals(0, latest.migrate().migrationsExecuted);
    }

    /** name, utility_config (JSON: bytes on H2, text on MySQL) and mining_schedule, compared as text. */
    private static List<String> untouched(JdbcTemplate db) {
        return db.query("SELECT store_id,name,utility_config,mining_schedule FROM store ORDER BY store_id", (rs, n) -> {
            Object config = rs.getObject("utility_config");
            String json = config instanceof byte[] bytes ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8) : String.valueOf(config);
            return rs.getInt("store_id") + "|" + rs.getString("name") + "|" + json + "|" + rs.getString("mining_schedule");
        });
    }
}

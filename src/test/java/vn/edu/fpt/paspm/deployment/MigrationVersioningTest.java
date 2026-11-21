package vn.edu.fpt.paspm.deployment;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** DB-free guard: Flyway versions must be unique and F05 mining migrations must sit after F04's V12. */
class MigrationVersioningTest {
    private static final Path SQL = Path.of("src/main/resources/db/migration");
    private static final Path JAVA = Path.of("src/main/java/db/migration");
    private static final Pattern NAME = Pattern.compile("V(\\d+)__(.+)\\.(sql|java)");

    private static Map<Integer, List<String>> versions() throws IOException {
        Map<Integer, List<String>> found = new TreeMap<>();
        for (Path dir : List.of(SQL, JAVA)) {
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.list(dir)) {
                for (Path p : (Iterable<Path>) files::iterator) {
                    Matcher m = NAME.matcher(p.getFileName().toString());
                    if (m.matches()) found.computeIfAbsent(Integer.parseInt(m.group(1)), k -> new ArrayList<>()).add(m.group(2));
                }
            }
        }
        return found;
    }

    @Test void f05MiningMigrationsOccupyV13ToV19InOrder() throws IOException {
        Map<Integer, List<String>> v = versions();
        assertEquals(List.of("mining_administration_config"), v.get(13));
        assertEquals(List.of("scheduled_mining"), v.get(14));
        assertEquals(List.of("freeze_scheduled_occurrence_context"), v.get(15));
        assertEquals(List.of("mining_request_leases"), v.get(16));
        assertEquals(List.of("mining_occurrence_recovery"), v.get(17));
        assertEquals(List.of("scheduler_skipped_status_and_failure_visibility"), v.get(18));
        assertEquals(List.of("system_scheduler_principal"), v.get(19));
    }

    @Test void noFlywayVersionIsUsedTwice() throws IOException {
        versions().forEach((version, names) -> assertEquals(1, names.size(), "Flyway version " + version + " is used by " + names));
    }

    @Test void f04V12IsNotTakenByMining() throws IOException {
        List<String> v12 = versions().getOrDefault(12, List.of());
        assertTrue(v12.isEmpty() || v12.equals(List.of("f04_preserve_large_raw_import_rows")), "V12 belongs to F04 only, found " + v12);
    }

    @Test void f09V27UpgradesExistingStoresAndProductsSafely() {
        String url = "jdbc:h2:mem:f09_v27_upgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER";
        var before = Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("26")).load();
        before.migrate();
        var dataSource = new DriverManagerDataSource(url, "sa", "");
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO store(name) VALUES ('Existing Store')");
        int store = jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        jdbc.update("INSERT INTO store(name) VALUES ('Existing Store')");
        int duplicate = jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) "
                + "VALUES (?, 'EXISTING', 'Existing Product', 'each', FALSE, 10, 2, 'ACTIVE')", store);
        int product = jdbc.queryForObject("SELECT MAX(product_id) FROM product WHERE store_id=?",
                Integer.class, store);

        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();

        assertEquals(vn.edu.fpt.paspm.storefront.StoreSlug.generate("Existing Store", store), jdbc.queryForObject(
                "SELECT store_slug FROM store WHERE store_id=?", String.class, store));
        assertEquals(vn.edu.fpt.paspm.storefront.StoreSlug.generate("Existing Store", duplicate), jdbc.queryForObject(
                "SELECT store_slug FROM store WHERE store_id=?", String.class, duplicate));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(DISTINCT store_slug) FROM store WHERE store_id IN (?,?)", Integer.class, store, duplicate));
        assertFalse(jdbc.queryForObject(
                "SELECT storefront_enabled FROM store WHERE store_id=?", Boolean.class, store));
        assertNull(jdbc.queryForObject("SELECT image_url FROM product WHERE product_id=?", String.class, product));
        assertNull(jdbc.queryForObject("SELECT description FROM product WHERE product_id=?", String.class, product));
    }
}

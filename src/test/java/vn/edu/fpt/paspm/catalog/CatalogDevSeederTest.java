package vn.edu.fpt.paspm.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalogseed;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
        "paspm.otp-secret=test-only-otp-key-with-at-least-thirty-two-bytes",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=true", "paspm.jwt-secret=01234567890123456789012345678901",
        "paspm.bootstrap-email=", "paspm.bootstrap-password="
})
@ActiveProfiles("dev")
class CatalogDevSeederTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogDevSeeder seeder;

    @Test
    void developmentSeedCreatesTwoStoresAndIsIdempotent() throws Exception {
        assertTrue(count("SELECT COUNT(*) FROM store WHERE name LIKE 'F02 Demo Store %'") >= 2);
        assertTrue(count("SELECT COUNT(*) FROM product WHERE sku LIKE 'F02-%'") >= 3);
        assertTrue(count("SELECT COUNT(*) FROM inventory_batch WHERE batch_code LIKE 'F02-%'") >= 5);
        assertEquals(1, count("SELECT COUNT(*) FROM app_user WHERE email='storeadmin@dev.paspm.local' AND status='ACTIVE'"));
        assertEquals(1, count("SELECT COUNT(*) FROM app_user WHERE email='storeadmin-b@dev.paspm.local' AND status='ACTIVE'"));
        assertEquals(1, count("SELECT COUNT(*) FROM app_user WHERE email='merchandiser@dev.paspm.local' AND status='ACTIVE'"));

        int storesBefore = count("SELECT COUNT(*) FROM store WHERE name LIKE 'F02 Demo Store %'");
        int batchesBefore = count("SELECT COUNT(*) FROM inventory_batch WHERE batch_code LIKE 'F02-%'");
        ApplicationArguments args = new DefaultApplicationArguments(new String[0]);
        seeder.run(args);
        assertEquals(storesBefore, count("SELECT COUNT(*) FROM store WHERE name LIKE 'F02 Demo Store %'"));
        assertEquals(batchesBefore, count("SELECT COUNT(*) FROM inventory_batch WHERE batch_code LIKE 'F02-%'"));
    }

    private int count(String sql) { return jdbc.queryForObject(sql, Integer.class); }
}

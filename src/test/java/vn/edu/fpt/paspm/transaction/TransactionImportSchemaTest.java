package vn.edu.fpt.paspm.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:f04schema;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "paspm.jwt-secret=01234567890123456789012345678901",
        "paspm.otp-secret=test-only-otp-key-with-at-least-thirty-two-bytes",
        "paspm.bootstrap-email=", "paspm.bootstrap-password="
})
class TransactionImportSchemaTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void flywayCreatesStoreScopedImportAndTransactionTables() {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM api_connection", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM import_batch", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM import_staging_row", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sales_transaction", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM transaction_item", Integer.class));

        assertTrue(jdbc.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE LOWER(TABLE_NAME) = 'sales_transaction'
                  AND LOWER(CONSTRAINT_NAME) = 'uq_sales_transaction_idempotency'
                """, Integer.class) > 0);
    }
}

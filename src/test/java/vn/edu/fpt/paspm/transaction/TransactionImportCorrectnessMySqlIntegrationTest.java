package vn.edu.fpt.paspm.transaction;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;

/** Same HTTP/transaction assertions on a fresh isolated MySQL schema. */
@EnabledIfSystemProperty(named="paspm.test.mysql", matches="true")
class TransactionImportCorrectnessMySqlIntegrationTest extends TransactionImportCorrectnessIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) { MySqlTestDatabase.create().register(registry); }
}

package vn.edu.fpt.paspm;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;

/** Standard opt-in creates an independent schema; never uses a source database. */
@EnabledIfSystemProperty(named="paspm.test.mysql", matches="true")
@SpringBootTest(properties={
    "paspm.jwt-secret=session-test-key-at-least-thirty-two-bytes",
    "paspm.otp-secret=session-test-otp-at-least-thirty-two-bytes",
    "paspm.bootstrap-email=", "paspm.bootstrap-password=",
    "paspm.refresh-cookie-secure=false"
})
class SessionMySqlIntegrationTest extends SessionHttpIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        MySqlTestDatabase.create().register(registry);
    }
}

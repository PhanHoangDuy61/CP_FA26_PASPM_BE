package vn.edu.fpt.paspm;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/** Opt-in only: PASPM_SESSION_MYSQL_URL must name a disposable database. */
@EnabledIfEnvironmentVariable(named="PASPM_SESSION_MYSQL_URL", matches="jdbc:mysql:.*")
@SpringBootTest(properties={
    "spring.datasource.url=${PASPM_SESSION_MYSQL_URL}",
    "spring.datasource.username=${PASPM_SESSION_MYSQL_USER}",
    "spring.datasource.password=${PASPM_SESSION_MYSQL_PASSWORD}",
    "paspm.jwt-secret=session-test-key-at-least-thirty-two-bytes",
    "paspm.otp-secret=session-test-otp-at-least-thirty-two-bytes",
    "paspm.bootstrap-email=", "paspm.bootstrap-password=",
    "paspm.session.cookie-secure=false"
})
class SessionMySqlIntegrationTest extends SessionHttpIntegrationTest { }

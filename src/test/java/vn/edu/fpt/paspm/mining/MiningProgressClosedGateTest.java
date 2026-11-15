package vn.edu.fpt.paspm.mining;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:progressclosed;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true","paspm.jwt-secret=01234567890123456789012345678901","paspm.otp-secret=01234567890123456789012345678901","paspm.bootstrap-email=","paspm.bootstrap-password=","paspm.mining.system-store-read.routes=RUNS"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class MiningProgressClosedGateTest {
    @org.springframework.beans.factory.annotation.Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired vn.edu.fpt.paspm.user.UserRepository users;
    @org.springframework.beans.factory.annotation.Autowired vn.edu.fpt.paspm.auth.AuthService auth;
    String token(String role,Integer store) {
        var u=users.createPendingUser("Progress gate",java.util.UUID.randomUUID()+"@example.invalid",role,store,null,null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",u.id());
        return auth.token(users.findById(u.id())).accessToken();
    }
    @Test void closedRequestsGroupRefusesProgressBeforeLookup() throws Exception {
        String bearer="Bearer "+token("SYSTEM_ADMIN",null);
        mvc.perform(get("/api/system/mining/stores/999999/requests/1/progress").header("Authorization",bearer))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("REQUESTS")));
    }
    @Test void anonymousProgressIsUnauthorized() throws Exception {
        mvc.perform(get("/api/system/mining/stores/999999/requests/1/progress")).andExpect(status().isUnauthorized());
    }
}

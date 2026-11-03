package vn.edu.fpt.paspm.mining;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;

/**
 * Decision D9 (confirmed 2026-11-02): scheduled Mining runs as a dedicated machine principal. It is an audit identity only: not loginable,
 * credential-free, hidden from and immutable through account management, and not assignable to any person.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:schedulerprincipal;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
    "paspm.jwt-secret=01234567890123456789012345678901", "paspm.otp-secret=01234567890123456789012345678901",
    "paspm.bootstrap-email=", "paspm.bootstrap-password="
})
@AutoConfigureMockMvc
class SchedulerPrincipalHttpTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired SchedulerPrincipal principal;

    String adminBearer() {
        var user = users.createPendingUser("Principal admin", UUID.randomUUID() + "@example.test", "SYSTEM_ADMIN", null, null, null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?", user.id());
        return "Bearer " + auth.token(users.findById(user.id())).accessToken();
    }

    @Test void migrationSeedsExactlyOneCredentialFreeNonLoginablePrincipalWithItsOwnRole() {
        var rows = jdbc.queryForList("SELECT u.*,r.code role_code FROM app_user u JOIN role r ON r.role_id=u.role_id WHERE r.code='SYSTEM_SCHEDULER'");
        assertEquals(1, rows.size());
        var row = rows.get(0);
        assertEquals(SchedulerPrincipal.EMAIL, row.get("email"));
        assertEquals("INACTIVE", row.get("status"), "never ACTIVE, so no authentication path accepts it");
        assertNull(row.get("password_hash"));
        assertNull(row.get("email_verified_at"));
        assertNull(row.get("store_id"));
        assertNull(row.get("verification_purpose"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM external_identity WHERE user_id=?", Integer.class, row.get("user_id")));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM verification_challenge WHERE user_id=?", Integer.class, row.get("user_id")));
        assertEquals(((Number) row.get("user_id")).intValue(), principal.id());
        assertDoesNotThrow(() -> principal.require(principal.id()));
    }

    @Test void onlyThePrincipalMayRunAnOccurrence() {
        int human = jdbc.queryForObject("SELECT MIN(user_id) FROM app_user u JOIN role r ON r.role_id=u.role_id WHERE r.code='SYSTEM_ADMIN'", Integer.class);
        var e = assertThrows(vn.edu.fpt.paspm.common.ApiException.class, () -> principal.require(human));
        assertEquals(403, e.status());
        assertThrows(vn.edu.fpt.paspm.common.ApiException.class, () -> principal.require(987654321));
    }

    @Test void accountManagementNeverListsShowsOrChangesThePrincipalAndNoOneCanBeGivenItsRole() throws Exception {
        String admin = adminBearer();
        int id = principal.id();
        mvc.perform(get("/api/users?size=100").header("Authorization", admin)).andExpect(status().isOk()).andExpect(content().string(not(containsString("system-scheduler"))));
        mvc.perform(get("/api/users?role=SYSTEM_SCHEDULER").header("Authorization", admin)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/roles").header("Authorization", admin)).andExpect(status().isOk()).andExpect(content().string(not(containsString("SYSTEM_SCHEDULER"))));
        mvc.perform(get("/api/users/" + id).header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/users/" + id + "/status").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}")).andExpect(status().isNotFound());
        mvc.perform(put("/api/users/" + id + "/role").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"roleCode\":\"SYSTEM_ADMIN\",\"storeId\":null}")).andExpect(status().isNotFound());
        mvc.perform(put("/api/users/" + id).header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hijacked\"}")).andExpect(status().isNotFound());
        jdbc.update("INSERT INTO store(name) VALUES (?)", "Principal " + UUID.randomUUID());
        var user = users.createPendingUser("Someone", UUID.randomUUID() + "@example.test", "MERCHANDISER", jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class), null, null);
        mvc.perform(put("/api/users/" + user.id() + "/role").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"roleCode\":\"SYSTEM_SCHEDULER\",\"storeId\":null}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Machine\",\"email\":\"" + UUID.randomUUID() + "@example.test\",\"roleCode\":\"SYSTEM_SCHEDULER\"}")).andExpect(status().isBadRequest());
        assertEquals("INACTIVE", jdbc.queryForObject("SELECT status FROM app_user WHERE user_id=?", String.class, id));
        assertEquals("PASPM Scheduled Mining", jdbc.queryForObject("SELECT name FROM app_user WHERE user_id=?", String.class, id));
    }

    @Test void thePrincipalCannotObtainSessionsThroughTheLoginFlows() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + SchedulerPrincipal.EMAIL + "\",\"password\":\"anything-at-all-123A!\"}")).andExpect(status().is4xxClientError());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM verification_challenge WHERE user_id=?", Integer.class, principal.id()));
    }


    @Test void passwordResetAndRegistrationFlowsNeverGiveThePrincipalACredentialOrAChallenge() throws Exception {
        String email = SchedulerPrincipal.EMAIL;
        mvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"));
        mvc.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\",\"otp\":\"123456\",\"password\":\"Brand-New-Password-123\"}")).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"));
        var row = jdbc.queryForMap("SELECT status,password_hash,email_verified_at FROM app_user WHERE user_id=?", principal.id());
        assertEquals("INACTIVE", row.get("status")); assertNull(row.get("password_hash")); assertNull(row.get("email_verified_at"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM verification_challenge WHERE user_id=?", Integer.class, principal.id()));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\",\"password\":\"Brand-New-Password-123\"}")).andExpect(status().is4xxClientError());
    }

    @Test void aTokenMintedInternallyForThePrincipalAuthorizesNothing() throws Exception {
        String token = "Bearer " + auth.token(users.findById(principal.id())).accessToken();
        for (String path : new String[] {"/api/system/mining/schedules", "/api/system/mining/config", "/api/system/stores", "/api/users", "/api/mining/requests"})
            mvc.perform(get(path).header("Authorization", token)).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/system/mining/schedules").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().is4xxClientError());
    }
}

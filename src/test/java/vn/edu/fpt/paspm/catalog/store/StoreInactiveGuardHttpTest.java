package vn.edu.fpt.paspm.catalog.store;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.mining.MiningScheduleReservation;
import vn.edu.fpt.paspm.user.UserRepository;

/**
 * DEC-20261123-01 §4: one central guard rejects users of an INACTIVE Store with 403 STORE_INACTIVE (request authentication,
 * login, token issuance and refresh); the paths it cannot cover get their own checks (409 STORE_INACTIVE for new System Admin
 * work and user assignment, SKIPPED occurrences for the scheduler). Deactivation here is a direct SQL update so the guard is
 * tested independently of the Store administration API.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:storeinactiveguard;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "paspm.jwt-secret=01234567890123456789012345678901", "paspm.otp-secret=01234567890123456789012345678901",
        "paspm.bootstrap-email=", "paspm.bootstrap-password=", "paspm.refresh-cookie-secure=false",
        "paspm.media.root=/tmp/paspm-store-guard-media", "paspm.avatar.directory=/tmp/paspm-store-guard-media/avatars"})
@AutoConfigureMockMvc
class StoreInactiveGuardHttpTest {
    static final String PASSWORD = "StoreGuard123!";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder passwords;
    @Autowired MiningScheduleReservation reservation;
    final ObjectMapper json = new ObjectMapper();

    int store() {
        jdbc.update("INSERT INTO store(name) VALUES (?)", "Guard " + UUID.randomUUID());
        return jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
    }

    String email(String role, Integer store) {
        String email = UUID.randomUUID() + "@example.test";
        var user = users.createPendingUser("Guard " + role, email, role, store, passwords.encode(PASSWORD), null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?", user.id());
        return email;
    }

    String bearer(String email) {
        return "Bearer " + auth.token(users.findByEmail(email).orElseThrow()).accessToken();
    }

    void deactivate(int store) {
        jdbc.update("UPDATE store SET status='INACTIVE' WHERE store_id=?", store);
    }

    MvcResult login(String email) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD)))).andReturn();
    }

    @Test void requestsOfAnInactiveStoresUsersGet403StoreInactiveAndWorkAgainAfterReactivation() throws Exception {
        int store = store(), other = store();
        String storeAdmin = email("STORE_ADMIN", store), merchandiser = email("MERCHANDISER", store);
        String otherAdmin = email("STORE_ADMIN", other), systemAdmin = email("SYSTEM_ADMIN", null);
        String adminToken = bearer(storeAdmin), merchToken = bearer(merchandiser);
        mvc.perform(get("/api/auth/me").header("Authorization", adminToken)).andExpect(status().isOk());

        deactivate(store);
        for (String token : new String[]{adminToken, merchToken}) {
            mvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("STORE_INACTIVE")).andExpect(jsonPath("$.error").value("Store is inactive"));
            mvc.perform(get("/api/products").header("Authorization", token)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("STORE_INACTIVE"));
        }
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(otherAdmin))).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(systemAdmin))).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-token")).andExpect(status().isUnauthorized());

        jdbc.update("UPDATE store SET status='ACTIVE' WHERE store_id=?", store);
        mvc.perform(get("/api/auth/me").header("Authorization", adminToken)).andExpect(status().isOk());
    }

    @Test void loginRefreshAndTokenIssuanceRefuseAnInactiveStoresUsers() throws Exception {
        int store = store();
        String email = email("MERCHANDISER", store);
        MvcResult session = login(email);
        assertEquals(200, session.getResponse().getStatus());
        String csrf = json.readTree(session.getResponse().getContentAsString()).path("csrfToken").asText();

        deactivate(store);
        MvcResult refused = login(email);
        assertEquals(403, refused.getResponse().getStatus());
        assertEquals("STORE_INACTIVE", json.readTree(refused.getResponse().getContentAsString()).path("code").asText());
        var wrong = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email, "password", "WrongPassword123!")))).andReturn();
        assertEquals(401, wrong.getResponse().getStatus(), "a wrong password never reveals the Store status");

        mvc.perform(post("/api/auth/refresh").cookie(session.getResponse().getCookie("PASPM-REFRESH"), session.getResponse().getCookie("PASPM-CSRF"))
                .header("X-CSRF-Token", csrf)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STORE_INACTIVE"));
        var error = assertThrows(ApiException.class, () -> auth.token(users.findByEmail(email).orElseThrow()));
        assertEquals(403, error.status());
        assertEquals("STORE_INACTIVE", error.code());
    }

    @Test void systemAdminCannotCreateNewWorkForAnInactiveStoreOrAssignUsersToIt() throws Exception {
        int store = store();
        String system = bearer(email("SYSTEM_ADMIN", null));
        String staff = email("STORE_ADMIN", store());
        deactivate(store);

        mvc.perform(post("/api/system/mining/requests").header("Authorization", system).contentType("application/json")
                .content("{\"storeId\":" + store + ",\"snapshotId\":1,\"requestKey\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STORE_INACTIVE"));
        mvc.perform(post("/api/system/mining/schedules").header("Authorization", system).contentType("application/json")
                .content("{\"storeId\":" + store + ",\"frequency\":\"WEEKLY\",\"dayOfWeek\":1,\"localTime\":\"09:00\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"lookbackDays\":7}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STORE_INACTIVE"));
        mvc.perform(post("/api/users").header("Authorization", system).contentType("application/json")
                .content("{\"name\":\"New staff\",\"email\":\"" + UUID.randomUUID() + "@example.test\",\"roleCode\":\"MERCHANDISER\",\"storeId\":" + store + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STORE_INACTIVE"));
        int staffId = users.findByEmail(staff).orElseThrow().id();
        mvc.perform(put("/api/users/" + staffId + "/role").header("Authorization", system).contentType("application/json")
                .content("{\"roleCode\":\"STORE_ADMIN\",\"storeId\":" + store + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STORE_INACTIVE"));

        // History stays readable for the System Admin
        mvc.perform(get("/api/system/mining/stores/" + store + "/requests").header("Authorization", system)).andExpect(status().isOk());
    }

    @Test void schedulerRecordsDueFireTimesOfAnInactiveStoreAsSkippedAndMovesOn() {
        int store = store();
        jdbc.update("INSERT INTO app_user(role_id,email,name,status) SELECT role_id,?,'Guard scheduler','ACTIVE' FROM role WHERE code='SYSTEM_ADMIN'", UUID.randomUUID() + "@example.test");
        int actor = jdbc.queryForObject("SELECT MAX(user_id) FROM app_user", Integer.class);
        if (jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1", Long.class) == 0) {
            jdbc.update("INSERT INTO mining_config_version(config_version,algorithm,min_util_mode,min_util_value,changed_by) VALUES(1,'EFIM','ABSOLUTE',10,?)", actor);
            jdbc.update("UPDATE mining_config_head SET config_version=1 WHERE config_id=1");
        }
        jdbc.update("INSERT INTO mining_schedule(store_id,created_by,updated_by,frequency,day_of_week,local_time,time_zone,lookback_days,enabled,next_fire_at) VALUES (?,?,?,'WEEKLY',1,'09:00','Asia/Ho_Chi_Minh',7,TRUE,?)",
                store, actor, actor, Timestamp.from(Instant.parse("2026-11-02T02:00:00Z")));
        long schedule = jdbc.queryForObject("SELECT MAX(schedule_id) FROM mining_schedule", Long.class);
        deactivate(store);

        Instant now = Instant.parse("2026-11-16T03:00:00Z");
        var outcome = reservation.reserve(now);
        assertNull(outcome.occurrenceId(), "no occurrence is reserved for an INACTIVE Store");
        assertEquals(3, outcome.skipped());
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM mining_schedule_occurrence WHERE schedule_id=? AND status='SKIPPED' AND skip_reason='STORE_INACTIVE'", Integer.class, schedule));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mining_schedule_occurrence WHERE schedule_id=? AND status<>'SKIPPED'", Integer.class, schedule));
        assertTrue(jdbc.queryForObject("SELECT next_fire_at FROM mining_schedule WHERE schedule_id=?", Timestamp.class, schedule).toInstant().isAfter(now));
        assertNull(reservation.reserve(now).occurrenceId());
    }
}

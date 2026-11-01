package vn.edu.fpt.paspm.mining;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;

/** Real authenticated HTTP against the schedule administration routes (activation stays 503; nothing here enables a schedule). */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:scheduleadminhttp;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
    "paspm.jwt-secret=01234567890123456789012345678901", "paspm.otp-secret=01234567890123456789012345678901",
    "paspm.bootstrap-email=", "paspm.bootstrap-password="
})
@AutoConfigureMockMvc
class MiningScheduleAdministrationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired ObjectMapper json;
    int store;
    String system;

    @BeforeEach void setup() {
        jdbc.update("INSERT INTO store(name) VALUES (?)", "SchedHttp " + UUID.randomUUID());
        store = jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        system = token("SYSTEM_ADMIN", null);
    }

    String token(String role, Integer scope) {
        var user = users.createPendingUser("Sched http", UUID.randomUUID() + "@example.test", role, scope, null, null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?", user.id());
        return auth.token(users.findById(user.id())).accessToken();
    }

    String weekly() { return "{\"storeId\":" + store + ",\"frequency\":\"WEEKLY\",\"dayOfWeek\":1,\"localTime\":\"09:00\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"lookbackDays\":7}"; }
    String monthly() { return "{\"storeId\":" + store + ",\"frequency\":\"MONTHLY\",\"dayOfMonth\":31,\"localTime\":\"09:00\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"lookbackDays\":30,\"compareAlgorithms\":true}"; }
    ResultActions postSchedule(String body) throws Exception { return mvc.perform(post("/api/system/mining/schedules").header("Authorization", "Bearer " + system).contentType(MediaType.APPLICATION_JSON).content(body)); }
    ResultActions putSchedule(long id, String schedule, boolean enabled, long version) throws Exception {
        return mvc.perform(put("/api/system/mining/schedules/" + id).header("Authorization", "Bearer " + system).contentType(MediaType.APPLICATION_JSON)
            .content("{\"schedule\":" + schedule + ",\"enabled\":" + enabled + ",\"expectedVersion\":" + version + "}"));
    }
    long create(String body) throws Exception { return json.readTree(postSchedule(body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("scheduleId").asLong(); }

    @Test void createReturnsADisabledScheduleWithoutAFireTimeAndBothRecurrenceKindsAreSupported() throws Exception {
        postSchedule(weekly()).andExpect(status().isCreated()).andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.nextFireAt").doesNotExist())
            .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.frequency").value("WEEKLY")).andExpect(jsonPath("$.dayOfWeek").value(1)).andExpect(jsonPath("$.compareAlgorithms").value(false));
        postSchedule(monthly()).andExpect(status().isCreated()).andExpect(jsonPath("$.dayOfMonth").value(31)).andExpect(jsonPath("$.compareAlgorithms").value(true)).andExpect(jsonPath("$.enabled").value(false));
    }

    @Test void invalidRecurrenceLookbackTimeAndStoreInputsAreRejectedWith400Or404() throws Exception {
        postSchedule(weekly().replace("\"dayOfWeek\":1", "\"dayOfWeek\":8")).andExpect(status().isBadRequest());
        postSchedule(weekly().replace("\"dayOfWeek\":1", "\"dayOfWeek\":0")).andExpect(status().isBadRequest());
        postSchedule(monthly().replace("\"dayOfMonth\":31", "\"dayOfMonth\":32")).andExpect(status().isBadRequest());
        postSchedule(monthly().replace("\"dayOfMonth\":31", "\"dayOfMonth\":0")).andExpect(status().isBadRequest());
        postSchedule(weekly().replace("\"dayOfWeek\":1", "\"dayOfMonth\":5")).andExpect(status().isBadRequest());
        postSchedule(weekly().replace("\"dayOfWeek\":1", "\"dayOfWeek\":1,\"dayOfMonth\":5")).andExpect(status().isBadRequest());
        postSchedule(monthly().replace("\"dayOfMonth\":31", "\"dayOfWeek\":2")).andExpect(status().isBadRequest());
        postSchedule(weekly().replace("\"lookbackDays\":7", "\"lookbackDays\":0")).andExpect(status().isBadRequest());
        postSchedule(weekly().replace("\"lookbackDays\":7", "\"lookbackDays\":3661")).andExpect(status().isBadRequest());
        postSchedule(weekly().replace("\"lookbackDays\":7", "\"lookbackDays\":7.5")).andExpect(status().isBadRequest());
        postSchedule(weekly().replace("\"09:00\"", "\"09:00:30\"")).andExpect(status().isBadRequest());
        postSchedule(weekly().replace("\"09:00\"", "\"25:00\"")).andExpect(status().isBadRequest());
        postSchedule(weekly().replace("\"frequency\":\"WEEKLY\"", "\"frequency\":\"DAILY\"")).andExpect(status().isBadRequest());
        postSchedule(weekly().replace("Asia/Ho_Chi_Minh", "UTC+7")).andExpect(status().isBadRequest());
        postSchedule(weekly().replace("}", ",\"unknown\":1}")).andExpect(status().isBadRequest());
        postSchedule(weekly().replace("\"storeId\":" + store, "\"storeId\":\"1\"")).andExpect(status().isBadRequest());
        postSchedule(weekly().replace("\"storeId\":" + store, "\"storeId\":0")).andExpect(status().isBadRequest());
        postSchedule(weekly().replace("\"storeId\":" + store, "\"storeId\":987654321")).andExpect(status().isNotFound());
    }

    @Test void updateWithAStaleVersionIs409AndTheScheduleIsUnchanged() throws Exception {
        long id = create(weekly());
        putSchedule(id, weekly().replace("\"lookbackDays\":7", "\"lookbackDays\":14"), false, 1).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.lookbackDays").value(14));
        putSchedule(id, weekly().replace("\"lookbackDays\":7", "\"lookbackDays\":21"), false, 1).andExpect(status().isConflict());
        mvc.perform(get("/api/system/mining/schedules/" + id).header("Authorization", "Bearer " + system)).andExpect(jsonPath("$.lookbackDays").value(14)).andExpect(jsonPath("$.version").value(2));
        putSchedule(id, weekly(), false, 0).andExpect(status().isBadRequest());
    }

    @Test void twoConcurrentEditsWithTheSameVersionHaveExactlyOneWinner() throws Exception {
        long id = create(weekly());
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { gate.await(); return putSchedule(id, weekly().replace("\"lookbackDays\":7", "\"lookbackDays\":10"), false, 1).andReturn().getResponse().getStatus(); });
            var b = pool.submit(() -> { gate.await(); return putSchedule(id, weekly().replace("\"lookbackDays\":7", "\"lookbackDays\":20"), false, 1).andReturn().getResponse().getStatus(); });
            gate.countDown();
            var statuses = java.util.List.of(a.get(), b.get()).stream().sorted().toList();
            assertEquals(java.util.List.of(200, 409), statuses);
        } finally { pool.shutdownNow(); }
        mvc.perform(get("/api/system/mining/schedules/" + id).header("Authorization", "Bearer " + system)).andExpect(jsonPath("$.version").value(2));
    }

    @Test void theStoreOfAScheduleCannotBeChangedAndUnknownIdsAre404() throws Exception {
        long id = create(weekly());
        jdbc.update("INSERT INTO store(name) VALUES (?)", "Other " + UUID.randomUUID());
        int other = jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        putSchedule(id, weekly().replace("\"storeId\":" + store, "\"storeId\":" + other), false, 1).andExpect(status().isBadRequest());
        putSchedule(id + 1000, weekly(), false, 1).andExpect(status().isNotFound());
        mvc.perform(get("/api/system/mining/schedules/" + (id + 1000)).header("Authorization", "Bearer " + system)).andExpect(status().isNotFound());
        mvc.perform(get("/api/system/mining/schedules/" + (id + 1000) + "/occurrences").header("Authorization", "Bearer " + system)).andExpect(status().isNotFound());
    }

    @Test void enablingIsRefusedWithoutAnySideEffectEvenForAnOtherwiseValidEdit() throws Exception {
        long id = create(weekly());
        putSchedule(id, weekly().replace("\"lookbackDays\":7", "\"lookbackDays\":99"), true, 1).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").value("Scheduled mining dispatch is not available"));
        mvc.perform(get("/api/system/mining/schedules/" + id).header("Authorization", "Bearer " + system))
            .andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.lookbackDays").value(7)).andExpect(jsonPath("$.nextFireAt").doesNotExist());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mining_schedule_occurrence WHERE schedule_id=?", Integer.class, id));
    }

    @Test void disablingAnEnabledRowClearsTheFireTimeBumpsTheVersionAndKeepsItsOccurrenceHistory() throws Exception {
        long id = create(weekly());
        jdbc.update("UPDATE mining_schedule SET enabled=TRUE,next_fire_at=TIMESTAMP '2100-01-01 00:00:00' WHERE schedule_id=?", id);
        jdbc.update("INSERT INTO mining_config_version(config_version,algorithm,min_util_mode,min_util_value,changed_by) SELECT COALESCE(MAX(config_version),0)+1,'EFIM','ABSOLUTE',1,(SELECT MAX(user_id) FROM app_user) FROM mining_config_version");
        long cfg = jdbc.queryForObject("SELECT MAX(config_version) FROM mining_config_version", Long.class);
        jdbc.update("INSERT INTO mining_schedule_occurrence(schedule_id,store_id,scheduled_at,schedule_version,config_version,request_key,status) VALUES(?,?,TIMESTAMP '2026-11-02 02:00:00',1,?,?,'FAILED')", id, store, cfg, UUID.randomUUID().toString());
        putSchedule(id, weekly(), false, 1).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.nextFireAt").doesNotExist()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(get("/api/system/mining/schedules/" + id + "/occurrences").header("Authorization", "Bearer " + system)).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].status").value("FAILED"));
    }

    @Test void listAndHistoryPaginationBoundsAreEnforced() throws Exception {
        long id = create(weekly());
        for (String path : new String[] {"/api/system/mining/schedules", "/api/system/mining/schedules/" + id + "/occurrences"}) {
            mvc.perform(get(path).header("Authorization", "Bearer " + system).param("page", "-1")).andExpect(status().isBadRequest());
            mvc.perform(get(path).header("Authorization", "Bearer " + system).param("pageSize", "0")).andExpect(status().isBadRequest());
            mvc.perform(get(path).header("Authorization", "Bearer " + system).param("pageSize", "101")).andExpect(status().isBadRequest());
            mvc.perform(get(path).header("Authorization", "Bearer " + system).param("page", "0").param("pageSize", "100")).andExpect(status().isOk());
        }
    }

    @Test void aTokenOfADeactivatedSystemAdminIsRejected() throws Exception {
        String other = token("SYSTEM_ADMIN", null);
        long actorId = jdbc.queryForObject("SELECT MAX(user_id) FROM app_user", Long.class);
        jdbc.update("UPDATE app_user SET status='INACTIVE',token_version=token_version+1 WHERE user_id=?", actorId);
        mvc.perform(get("/api/system/mining/schedules").header("Authorization", "Bearer " + other)).andExpect(status().isUnauthorized());
        postSchedule(weekly()).andExpect(status().isCreated());
    }
}

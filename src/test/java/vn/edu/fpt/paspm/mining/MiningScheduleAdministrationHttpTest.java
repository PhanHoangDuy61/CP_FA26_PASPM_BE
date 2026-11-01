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
        for (String bad : new String[] {"-1", "0", "1", "6", "366", "3660", "3661", "\"30\""})
            postSchedule(weekly().replace("\"lookbackDays\":7", "\"lookbackDays\":" + bad)).andExpect(status().isBadRequest());
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

    // D3 (confirmed 2026-11-02, amended): lookbackDays 7..365 inclusive; omitted -> 30 for WEEKLY and 90 for MONTHLY
    @Test void lookbackAcceptsTheInclusiveBoundariesAndDefaultsByFrequency() throws Exception {
        for (String ok : new String[] {"7", "30", "60", "90", "180", "365"})
            postSchedule(weekly().replace("\"lookbackDays\":7", "\"lookbackDays\":" + ok)).andExpect(status().isCreated()).andExpect(jsonPath("$.lookbackDays").value(Integer.parseInt(ok)));
        postSchedule(weekly().replace(",\"lookbackDays\":7", "")).andExpect(status().isCreated()).andExpect(jsonPath("$.lookbackDays").value(30));
        postSchedule(monthly().replace(",\"lookbackDays\":30", "")).andExpect(status().isCreated()).andExpect(jsonPath("$.lookbackDays").value(90));
        postSchedule(weekly().replace("\"lookbackDays\":7", "\"lookbackDays\":null")).andExpect(status().isCreated()).andExpect(jsonPath("$.lookbackDays").value(30));
        long id = create(monthly());
        putSchedule(id, monthly().replace(",\"lookbackDays\":30", ""), false, 1).andExpect(status().isOk()).andExpect(jsonPath("$.lookbackDays").value(90));
        putSchedule(id, monthly().replace("\"lookbackDays\":30", "\"lookbackDays\":6"), false, 2).andExpect(status().isBadRequest());
        putSchedule(id, monthly().replace("\"lookbackDays\":30", "\"lookbackDays\":366"), false, 2).andExpect(status().isBadRequest());
    }

    // D3: schedules stored before the range was narrowed are kept exactly as stored and are never silently clamped
    @Test void aScheduleStoredWithALegacyLookbackIsReadUnchangedAndCanOnlyBeRepairedByAValidEdit() throws Exception {
        long id = create(weekly());
        jdbc.update("UPDATE mining_schedule SET lookback_days=3660 WHERE schedule_id=?", id);
        mvc.perform(get("/api/system/mining/schedules/" + id).header("Authorization", "Bearer " + system)).andExpect(jsonPath("$.lookbackDays").value(3660));
        putSchedule(id, weekly().replace("\"lookbackDays\":7", "\"lookbackDays\":3660"), false, 1).andExpect(status().isBadRequest());
        mvc.perform(get("/api/system/mining/schedules/" + id).header("Authorization", "Bearer " + system)).andExpect(jsonPath("$.lookbackDays").value(3660)).andExpect(jsonPath("$.version").value(1));
        putSchedule(id, weekly().replace("\"lookbackDays\":7", "\"lookbackDays\":365"), false, 1).andExpect(status().isOk()).andExpect(jsonPath("$.lookbackDays").value(365));
    }

    // D4 (confirmed 2026-11-02): the scheduler uses the Store calendar zone, Asia/Ho_Chi_Minh in this release
    @Test void aTimeZoneThatDiffersFromTheStoreCalendarZoneIsRejectedOnCreateAndUpdate() throws Exception {
        for (String zone : new String[] {"UTC", "America/New_York", "Pacific/Auckland", "Asia/Bangkok", "Asia/Saigon"}) {
            postSchedule(weekly().replace("Asia/Ho_Chi_Minh", zone)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Asia/Ho_Chi_Minh")));
        }
        long id = create(weekly());
        putSchedule(id, weekly().replace("Asia/Ho_Chi_Minh", "UTC"), false, 1).andExpect(status().isBadRequest());
        mvc.perform(get("/api/system/mining/schedules/" + id).header("Authorization", "Bearer " + system)).andExpect(jsonPath("$.timeZone").value("Asia/Ho_Chi_Minh")).andExpect(jsonPath("$.version").value(1));
    }

    @Test void aLegacyScheduleInAnotherZoneIsReadUnchangedAndCanOnlyBeRepairedByEditingItToTheStoreZone() throws Exception {
        long id = create(weekly());
        jdbc.update("UPDATE mining_schedule SET time_zone='Pacific/Auckland' WHERE schedule_id=?", id);
        mvc.perform(get("/api/system/mining/schedules/" + id).header("Authorization", "Bearer " + system)).andExpect(jsonPath("$.timeZone").value("Pacific/Auckland"));
        putSchedule(id, weekly().replace("Asia/Ho_Chi_Minh", "Pacific/Auckland"), false, 1).andExpect(status().isBadRequest());
        putSchedule(id, weekly(), false, 1).andExpect(status().isOk()).andExpect(jsonPath("$.timeZone").value("Asia/Ho_Chi_Minh"));
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

    // D6/D7: the history API tells an administrator what was skipped and why a run failed
    @Test void occurrenceHistoryExposesSkippedReasonsAndFinalFailureMessages() throws Exception {
        long id = create(weekly());
        jdbc.update("INSERT INTO mining_config_version(config_version,algorithm,min_util_mode,min_util_value,changed_by) SELECT COALESCE(MAX(config_version),0)+1,'EFIM','ABSOLUTE',1,(SELECT MAX(user_id) FROM app_user) FROM mining_config_version");
        long cfg = jdbc.queryForObject("SELECT MAX(config_version) FROM mining_config_version", Long.class);
        jdbc.update("INSERT INTO mining_schedule_occurrence(schedule_id,store_id,scheduled_at,schedule_version,config_version,request_key,status,skip_reason) VALUES(?,?,TIMESTAMP '2026-10-26 02:00:00',1,?,?,'SKIPPED',?)", id, store, cfg, UUID.randomUUID().toString(), MiningScheduleReservation.SKIP_SUPERSEDED);
        jdbc.update("INSERT INTO mining_schedule_occurrence(schedule_id,store_id,scheduled_at,schedule_version,config_version,request_key,status,failure_code,failure_message,attempt_count) VALUES(?,?,TIMESTAMP '2026-11-02 02:00:00',1,?,?,'FAILED','HTTP_400','No eligible transaction items in the selected range',1)", id, store, cfg, UUID.randomUUID().toString());
        var history = json.readTree(mvc.perform(get("/api/system/mining/schedules/" + id + "/occurrences").header("Authorization", "Bearer " + system)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(2, history.path("totalElements").asInt());
        var failed = history.path("items").get(0);
        assertEquals("FAILED", failed.path("status").asText());
        assertEquals("HTTP_400", failed.path("failureCode").asText());
        assertEquals("No eligible transaction items in the selected range", failed.path("failureMessage").asText());
        assertTrue(failed.path("skipReason").isNull());
        var skipped = history.path("items").get(1);
        assertEquals("SKIPPED", skipped.path("status").asText());
        assertEquals(MiningScheduleReservation.SKIP_SUPERSEDED, skipped.path("skipReason").asText());
        assertTrue(skipped.path("failureMessage").isNull());
        assertTrue(skipped.path("snapshotId").isNull() && skipped.path("requestId").isNull());
        long skippedId = skipped.path("occurrenceId").asLong();
        mvc.perform(get("/api/system/mining/schedules/" + id + "/occurrences/" + skippedId).header("Authorization", "Bearer " + system))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SKIPPED")).andExpect(jsonPath("$.skipReason").value(MiningScheduleReservation.SKIP_SUPERSEDED));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("UPDATE mining_schedule_occurrence SET skip_reason=NULL WHERE occurrence_id=?", skippedId), "a SKIPPED row always carries its reason");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("UPDATE mining_schedule_occurrence SET skip_reason='X' WHERE occurrence_id<>?", skippedId), "only SKIPPED rows may carry one");
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

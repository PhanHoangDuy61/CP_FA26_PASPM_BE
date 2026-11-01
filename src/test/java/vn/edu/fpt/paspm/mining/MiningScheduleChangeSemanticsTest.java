package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
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
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;

/**
 * Decision D8 (confirmed 2026-11-02): a schedule disabled or edited stops further reservations; work already committed keeps its frozen
 * schedule version and configuration version. A reserved-but-unprepared occurrence of a disabled or edited schedule ends FAILED/HTTP_409
 * (see MiningOccurrenceFailurePathsTest); once its Mining Request exists it runs to completion (see MiningIntegrationTest).
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:schedulechange;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
    "paspm.jwt-secret=01234567890123456789012345678901", "paspm.otp-secret=01234567890123456789012345678901",
    "paspm.bootstrap-email=", "paspm.bootstrap-password=", "paspm.mining.spmf-jar=", "paspm.mining.java-executable="
})
@AutoConfigureMockMvc
class MiningScheduleChangeSemanticsTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired ObjectMapper json;
    @Autowired MiningScheduleReservation reservation;
    @Autowired MiningOccurrenceDispatcher dispatcher;
    int store; String bearer; int admin;
    static final Instant DUE = Instant.parse("2026-11-02T02:00:00Z");
    static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM mining_schedule_occurrence");
        jdbc.update("DELETE FROM mining_schedule");
        jdbc.update("INSERT INTO store(name) VALUES (?)", "Change " + UUID.randomUUID());
        store = jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        var user = users.createPendingUser("Change admin", UUID.randomUUID() + "@example.test", "SYSTEM_ADMIN", null, null, null);
        admin = user.id();
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?", user.id());
        bearer = "Bearer " + auth.token(users.findById(user.id())).accessToken();
        if (jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1", Long.class) == 0) {
            jdbc.update("INSERT INTO mining_config_version(config_version,algorithm,min_util_mode,min_util_value,changed_by) VALUES(1,'EFIM','ABSOLUTE',1,?)", admin);
            jdbc.update("UPDATE mining_config_head SET config_version=1 WHERE config_id=1");
        }
    }

    String body() { return "{\"storeId\":" + store + ",\"frequency\":\"WEEKLY\",\"dayOfWeek\":1,\"localTime\":\"09:00\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"lookbackDays\":7}"; }
    /** The activation gate is closed in this class, so an enabled row is staged directly, as the acceptance tests do through the open gate. */
    long enabledSchedule() throws Exception {
        long id = json.readTree(mvc.perform(post("/api/system/mining/schedules").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("scheduleId").asLong();
        jdbc.update("UPDATE mining_schedule SET enabled=TRUE,next_fire_at=? WHERE schedule_id=?", Timestamp.from(DUE), id);
        return id;
    }
    int putSchedule(long id, boolean enabled, long version) throws Exception {
        return mvc.perform(put("/api/system/mining/schedules/" + id).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
            .content("{\"schedule\":" + body() + ",\"enabled\":" + enabled + ",\"expectedVersion\":" + version + "}")).andReturn().getResponse().getStatus();
    }
    long occurrences(long id) { return jdbc.queryForObject("SELECT COUNT(*) FROM mining_schedule_occurrence WHERE schedule_id=?", Long.class, id); }

    @Test void disablingBeforeAnyReservationMeansNothingIsEverReserved() throws Exception {
        long id = enabledSchedule();
        assertEquals(200, putSchedule(id, false, 1));
        assertNull(jdbc.queryForObject("SELECT next_fire_at FROM mining_schedule WHERE schedule_id=?", Timestamp.class, id));
        assertTrue(reservation.reserveOne(NOW.plusSeconds(86400L * 30)).isEmpty());
        assertEquals(0, occurrences(id));
    }

    @Test void editingTheScheduleDoesNotChangeTheVersionsAnAlreadyReservedOccurrenceFroze() throws Exception {
        long id = enabledSchedule();
        long config = jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1", Long.class);
        assertTrue(reservation.reserveOne(NOW).isPresent());
        var before = jdbc.queryForMap("SELECT schedule_version,config_version,range_start,range_end,analysis_date FROM mining_schedule_occurrence WHERE schedule_id=?", id);
        assertEquals(200, putSchedule(id, false, 1));
        long next = jdbc.queryForObject("SELECT MAX(config_version) FROM mining_config_version", Long.class) + 1;
        jdbc.update("INSERT INTO mining_config_version(config_version,algorithm,min_util_mode,min_util_value,changed_by) VALUES(?,'HUI_MINER','ABSOLUTE',9,?)", next, admin);
        jdbc.update("UPDATE mining_config_head SET config_version=? WHERE config_id=1", next);
        try {
            var after = jdbc.queryForMap("SELECT schedule_version,config_version,range_start,range_end,analysis_date FROM mining_schedule_occurrence WHERE schedule_id=?", id);
            assertEquals(before, after, "later edits affect future work only");
            assertEquals(1L, ((Number) after.get("schedule_version")).longValue());
            assertEquals(config, ((Number) after.get("config_version")).longValue());
        } finally { jdbc.update("UPDATE mining_config_head SET config_version=? WHERE config_id=1", config); }
    }

    @Test void aDisableRacingTheReservationNeverLeavesAnOccurrenceThatCouldRunAfterTheDisable() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            int reservedFirst = 0, disabledFirst = 0;
            for (int round = 0; round < 12; round++) {
                setup();
                long id = enabledSchedule();
                var gate = new CountDownLatch(1);
                var reserve = pool.submit(() -> { gate.await(); return reservation.reserveOne(NOW).isPresent(); });
                var disable = pool.submit(() -> { gate.await(); return putSchedule(id, false, 1); });
                gate.countDown();
                boolean reserved = reserve.get();
                assertEquals(200, disable.get(), "the edit always succeeds; the reservation serialises with it on the schedule row");
                assertEquals(reserved ? 1 : 0, occurrences(id));
                assertFalse(jdbc.queryForObject("SELECT enabled FROM mining_schedule WHERE schedule_id=?", Boolean.class, id));
                assertNull(jdbc.queryForObject("SELECT next_fire_at FROM mining_schedule WHERE schedule_id=?", Timestamp.class, id), "no fire time survives the disable");
                if (reserved) {
                    reservedFirst++;
                    assertEquals(1L, jdbc.queryForObject("SELECT schedule_version FROM mining_schedule_occurrence WHERE schedule_id=?", Long.class, id));
                    dispatcher.poll();
                    var row = jdbc.queryForMap("SELECT status,failure_code FROM mining_schedule_occurrence WHERE schedule_id=?", id);
                    assertEquals("FAILED", row.get("status"), "reserved before the disable, never prepared after it");
                    assertEquals("HTTP_409", row.get("failure_code"));
                } else disabledFirst++;
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=?", Integer.class, store));
                assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot WHERE store_id=?", Integer.class, store));
            }
            assertEquals(12, reservedFirst + disabledFirst);
        } finally { pool.shutdownNow(); }
    }
}

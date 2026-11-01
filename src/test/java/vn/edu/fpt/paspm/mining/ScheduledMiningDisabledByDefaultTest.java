package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;

/**
 * Release safety for the Core release: with the shipped configuration (no
 * {@code paspm.mining.schedule-dispatch-enabled}) a schedule that is already enabled and due in the database must stay
 * inert, i.e. nothing is reserved, no snapshot or request is created and this context's dispatcher never starts. This is the
 * "restart with an enabled schedule" situation; the HTTP enable path stays 503.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:schedoff;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
    "paspm.jwt-secret=01234567890123456789012345678901", "paspm.otp-secret=01234567890123456789012345678901",
    "paspm.bootstrap-email=", "paspm.bootstrap-password="
})
@AutoConfigureMockMvc
class ScheduledMiningDisabledByDefaultTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired MiningOccurrenceDispatcher dispatcher;

    String token(String role, Integer store) {
        var user = users.createPendingUser("Sched off", UUID.randomUUID() + "@example.test", role, store, null, null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?", user.id());
        return auth.token(users.findById(user.id())).accessToken();
    }

    @Test void anEnabledDueScheduleStaysInertWithDefaultConfigurationAndEnableOverHttpIsStill503() throws Exception {
        jdbc.update("INSERT INTO store(name) VALUES (?)", "SchedOff " + UUID.randomUUID());
        int store = jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        String system = token("SYSTEM_ADMIN", null);
        // a real mining configuration exists, so a running dispatcher would have no reason to refuse the reservation
        mvc.perform(put("/api/system/mining/config").header("Authorization", "Bearer " + system).contentType(MediaType.APPLICATION_JSON)
            .content("{\"algorithm\":\"EFIM\",\"minUtilMode\":\"ABSOLUTE\",\"minUtilValue\":1,\"expectedVersion\":0}")).andExpect(status().isOk());
        int actor = jdbc.queryForObject("SELECT MAX(u.user_id) FROM app_user u JOIN role r ON r.role_id=u.role_id WHERE r.code='SYSTEM_ADMIN'", Integer.class);
        jdbc.update("INSERT INTO mining_schedule(store_id,created_by,updated_by,frequency,day_of_week,local_time,time_zone,lookback_days,enabled,next_fire_at) VALUES(?,?,?,?,?,?,?,?,TRUE,?)",
            store, actor, actor, "WEEKLY", 1, "09:00", "Asia/Ho_Chi_Minh", 7, Timestamp.from(Instant.now().minusSeconds(3600)));
        long schedule = jdbc.queryForObject("SELECT MAX(schedule_id) FROM mining_schedule", Long.class);
        int snapshotsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot", Integer.class);
        int requestsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM mining_request", Integer.class);

        Thread.sleep(8000); // longer than one 5-second dispatcher poll

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mining_schedule_occurrence WHERE schedule_id=?", Integer.class, schedule));
        assertEquals(snapshotsBefore, jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot", Integer.class));
        assertEquals(requestsBefore, jdbc.queryForObject("SELECT COUNT(*) FROM mining_request", Integer.class));
        // scoped to this application context: other contexts of the same JVM may legitimately run a dispatcher
        assertFalse(((java.util.concurrent.atomic.AtomicBoolean) ReflectionTestUtils.getField(dispatcher, "started")).get(),
            "this context's schedule dispatcher must not have started polling when the flag is off");
        // the due time was not advanced either: nothing touched the schedule
        assertEquals(1L, jdbc.queryForObject("SELECT schedule_version FROM mining_schedule WHERE schedule_id=?", Long.class, schedule));

        String body = "{\"storeId\":" + store + ",\"frequency\":\"WEEKLY\",\"dayOfWeek\":1,\"localTime\":\"09:00\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"lookbackDays\":7}";
        mvc.perform(put("/api/system/mining/schedules/" + schedule).header("Authorization", "Bearer " + system).contentType(MediaType.APPLICATION_JSON)
            .content("{\"schedule\":" + body + ",\"enabled\":true,\"expectedVersion\":1}")).andExpect(status().isServiceUnavailable());
    }
}

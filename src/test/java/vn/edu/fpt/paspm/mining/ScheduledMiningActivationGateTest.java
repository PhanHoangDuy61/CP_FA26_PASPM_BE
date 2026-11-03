package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;

/**
 * Decision D10, gate ON (an explicitly enabled acceptance environment): the public API activates a schedule, persists it enabled with a
 * computed fire time, and the dispatcher thread is running. The gate-OFF counterpart is {@link MiningScheduleAdministrationHttpTest} and
 * {@link ScheduledMiningDisabledByDefaultTest}. This does not prove release readiness: see the acceptance matrix.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:activationgateon;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
    "paspm.jwt-secret=01234567890123456789012345678901", "paspm.otp-secret=01234567890123456789012345678901",
    "paspm.bootstrap-email=", "paspm.bootstrap-password=", "paspm.mining.spmf-jar=", "paspm.mining.java-executable=",
    "paspm.mining.schedule-dispatch-enabled=true", "paspm.store-time-zone=Asia/Ho_Chi_Minh"
})
@AutoConfigureMockMvc
class ScheduledMiningActivationGateTest {
    static final ZoneId HCM = ZoneId.of("Asia/Ho_Chi_Minh");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired ObjectMapper json;
    @Autowired MiningOccurrenceDispatcher dispatcher;
    int store; String admin;

    @BeforeEach void setup() {
        jdbc.update("INSERT INTO store(name) VALUES (?)", "Gate on " + UUID.randomUUID());
        store = jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        admin = token("SYSTEM_ADMIN", null);
        if (jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1", Long.class) == 0) {
            jdbc.update("INSERT INTO mining_config_version(config_version,algorithm,min_util_mode,min_util_value,changed_by) VALUES(1,'EFIM','ABSOLUTE',1,(SELECT MAX(user_id) FROM app_user))");
            jdbc.update("UPDATE mining_config_head SET config_version=1 WHERE config_id=1");
        }
    }
    String token(String role, Integer scope) {
        var user = users.createPendingUser("Gate on", UUID.randomUUID() + "@example.test", role, scope, null, null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?", user.id());
        return auth.token(users.findById(user.id())).accessToken();
    }
    String weekly() { return "{\"storeId\":" + store + ",\"frequency\":\"WEEKLY\",\"dayOfWeek\":3,\"localTime\":\"06:30\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"lookbackDays\":30}"; }
    String monthly() { return "{\"storeId\":" + store + ",\"frequency\":\"MONTHLY\",\"dayOfMonth\":31,\"localTime\":\"23:45\",\"timeZone\":\"Asia/Ho_Chi_Minh\"}"; }
    long create(String body) throws Exception {
        return json.readTree(mvc.perform(post("/api/system/mining/schedules").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("scheduleId").asLong();
    }
    ResultActions put(long id, String schedule, boolean enabled, long version) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/system/mining/schedules/" + id).header("Authorization", "Bearer " + admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"schedule\":" + schedule + ",\"enabled\":" + enabled + ",\"expectedVersion\":" + version + "}"));
    }
    Instant fireTime(long id) { return jdbc.queryForObject("SELECT next_fire_at FROM mining_schedule WHERE schedule_id=?", Timestamp.class, id).toInstant(); }

    @Test void theSchedulerThreadRunsOnlyBecauseTheGateIsExplicitlyOn() {
        assertTrue(((AtomicBoolean) ReflectionTestUtils.getField(dispatcher, "started")).get());
    }

    @Test void weeklyActivationPersistsTheScheduleEnabledWithTheNextWednesdayFireTimeInTheStoreZone() throws Exception {
        long id = create(weekly());
        Instant before = Instant.now();
        put(id, weekly(), true, 1).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.nextFireAt").exists());
        Instant after = Instant.now();
        Instant fire = fireTime(id);
        assertTrue(fire.isAfter(before) && !fire.isAfter(after.plus(Duration.ofDays(7))), "within the next week: " + fire);
        var local = fire.atZone(HCM);
        assertEquals(DayOfWeek.WEDNESDAY, local.getDayOfWeek());
        assertEquals(LocalTime.of(6, 30), local.toLocalTime());
        assertEquals(MiningScheduleTime.nextWeekly(before, HCM, DayOfWeek.WEDNESDAY, LocalTime.of(6, 30)), fire, "same rule as the unit-tested recurrence");
        assertTrue(jdbc.queryForObject("SELECT enabled FROM mining_schedule WHERE schedule_id=?", Boolean.class, id));
    }

    @Test void monthlyActivationUsesTheDefaultNinetyDayLookbackAndTheLastValidDayOfShortMonths() throws Exception {
        long id = create(monthly());
        mvc.perform(get("/api/system/mining/schedules/" + id).header("Authorization", "Bearer " + admin)).andExpect(jsonPath("$.lookbackDays").value(90));
        put(id, monthly(), true, 1).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.lookbackDays").value(90));
        var local = fireTime(id).atZone(HCM);
        assertEquals(LocalTime.of(23, 45), local.toLocalTime());
        assertEquals(Math.min(31, java.time.YearMonth.from(local).lengthOfMonth()), local.getDayOfMonth(), "day 31 clamps to the month's last day");
    }

    @Test void editingAnEnabledScheduleRecomputesTheFireTimeAndDisablingClearsIt() throws Exception {
        long id = create(weekly());
        put(id, weekly(), true, 1).andExpect(status().isOk());
        put(id, weekly().replace("\"dayOfWeek\":3", "\"dayOfWeek\":5"), true, 2).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3));
        assertEquals(DayOfWeek.FRIDAY, fireTime(id).atZone(HCM).getDayOfWeek());
        put(id, weekly(), false, 3).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.nextFireAt").doesNotExist());
        assertNull(jdbc.queryForObject("SELECT next_fire_at FROM mining_schedule WHERE schedule_id=?", Timestamp.class, id));
    }

    @Test void activationIsRefusedWithoutSideEffectsWhenItCouldNeverRun() throws Exception {
        long id = create(weekly());
        long head = jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1", Long.class);
        jdbc.update("UPDATE mining_config_head SET config_version=0 WHERE config_id=1");
        try {
            put(id, weekly(), true, 1).andExpect(status().isConflict());
        } finally { jdbc.update("UPDATE mining_config_head SET config_version=? WHERE config_id=1", head); }
        mvc.perform(get("/api/system/mining/schedules/" + id).header("Authorization", "Bearer " + admin)).andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.version").value(1));
        // validation still applies to an activation request
        put(id, weekly().replace("\"lookbackDays\":30", "\"lookbackDays\":6"), true, 1).andExpect(status().isBadRequest());
        put(id, weekly().replace("Asia/Ho_Chi_Minh", "UTC"), true, 1).andExpect(status().isBadRequest());
        put(id, weekly(), true, 7).andExpect(status().isConflict());
        mvc.perform(get("/api/system/mining/schedules/" + id).header("Authorization", "Bearer " + admin)).andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.version").value(1));
    }

    @Test void aLegacyScheduleOutsideThePolicyCanBeActivatedOnlyTogetherWithAValidEdit() throws Exception {
        long id = create(weekly());
        jdbc.update("UPDATE mining_schedule SET lookback_days=2000,time_zone='Pacific/Auckland' WHERE schedule_id=?", id);
        put(id, weekly().replace("\"lookbackDays\":30", "\"lookbackDays\":2000"), true, 1).andExpect(status().isBadRequest());
        put(id, weekly(), true, 1).andExpect(status().isOk()).andExpect(jsonPath("$.lookbackDays").value(30)).andExpect(jsonPath("$.timeZone").value("Asia/Ho_Chi_Minh"));
    }

    @Test void onlySystemAdminCanActivate() throws Exception {
        long id = create(weekly());
        for (String role : List.of("STORE_ADMIN", "MERCHANDISER", "END_CUSTOMER")) {
            String other = token(role, role.equals("END_CUSTOMER") ? null : store);
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/system/mining/schedules/" + id).header("Authorization", "Bearer " + other)
                .contentType(MediaType.APPLICATION_JSON).content("{\"schedule\":" + weekly() + ",\"enabled\":true,\"expectedVersion\":1}")).andExpect(status().isForbidden());
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/system/mining/schedules/" + id).contentType(MediaType.APPLICATION_JSON)
            .content("{\"schedule\":" + weekly() + ",\"enabled\":true,\"expectedVersion\":1}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/system/mining/schedules/" + id).header("Authorization", "Bearer " + admin)).andExpect(jsonPath("$.enabled").value(false));
    }
}

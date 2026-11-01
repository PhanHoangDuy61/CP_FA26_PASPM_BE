package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.fpt.paspm.common.ApiException;

/**
 * Occurrence reservation scenarios on the real schema. D3 (lookback 7..365), D4 (Store calendar zone) and D5 (month-end clamp) are
 * confirmed decisions. Catch-up (D6) is covered by its own scenarios.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:reservationscenarios;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
    "paspm.jwt-secret=01234567890123456789012345678901", "paspm.otp-secret=01234567890123456789012345678901",
    "paspm.bootstrap-email=", "paspm.bootstrap-password="
})
class MiningScheduleReservationScenariosTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MiningScheduleReservation reservation;

    int store, actor;

    void base() {
        // reserveOne() takes whichever schedule is due, so each scenario starts from an empty scheduler
        jdbc.update("DELETE FROM mining_schedule_occurrence");
        jdbc.update("DELETE FROM mining_schedule");
        jdbc.update("INSERT INTO store(name) VALUES (?)", "Scenario " + UUID.randomUUID());
        store = jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        jdbc.update("INSERT INTO app_user(role_id,email,name,status) SELECT role_id,?,'Scenario','ACTIVE' FROM role WHERE code='SYSTEM_ADMIN'", UUID.randomUUID() + "@example.test");
        actor = jdbc.queryForObject("SELECT MAX(user_id) FROM app_user", Integer.class);
        if (jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1", Long.class) == 0) {
            jdbc.update("INSERT INTO mining_config_version(config_version,algorithm,min_util_mode,min_util_value,changed_by) VALUES(1,'EFIM','ABSOLUTE',1,?)", actor);
            jdbc.update("UPDATE mining_config_head SET config_version=1 WHERE config_id=1");
        }
    }

    long schedule(String frequency, Integer dow, Integer dom, String time, String zone, int lookback, boolean enabled, Instant next) {
        jdbc.update("INSERT INTO mining_schedule(store_id,created_by,updated_by,frequency,day_of_week,day_of_month,local_time,time_zone,lookback_days,enabled,next_fire_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            store, actor, actor, frequency, dow, dom, time, zone, lookback, enabled, next == null ? null : Timestamp.from(next));
        return jdbc.queryForObject("SELECT MAX(schedule_id) FROM mining_schedule", Long.class);
    }

    List<Map<String, Object>> occurrences(long schedule) { return jdbc.queryForList("SELECT * FROM mining_schedule_occurrence WHERE schedule_id=? ORDER BY scheduled_at", schedule); }
    Instant nextFire(long schedule) { return jdbc.queryForObject("SELECT next_fire_at FROM mining_schedule WHERE schedule_id=?", Timestamp.class, schedule).toInstant(); }

    @Test void currentBehaviourCatchUpAfterDowntimeCreatesOneOccurrencePerMissedFireInOrderThenStops() {
        base();
        // weekly Monday 09:00 Asia/Ho_Chi_Minh (UTC+7 = 02:00Z); backend was down for five fires
        long s = schedule("WEEKLY", 1, null, "09:00", "Asia/Ho_Chi_Minh", 7, true, Instant.parse("2026-10-05T02:00:00Z"));
        Instant now = Instant.parse("2026-11-02T03:00:00Z");
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) { var id = reservation.reserveOne(now); if (id.isEmpty()) break; ids.add(id.get()); }
        assertEquals(5, ids.size(), "Oct 5, 12, 19, 26 and Nov 2");
        var rows = occurrences(s);
        assertEquals(List.of("2026-10-05T02:00:00Z", "2026-10-12T02:00:00Z", "2026-10-19T02:00:00Z", "2026-10-26T02:00:00Z", "2026-11-02T02:00:00Z"),
            rows.stream().map(r -> ((Timestamp) r.get("scheduled_at")).toInstant().toString()).toList());
        assertEquals(List.of("2026-10-05", "2026-10-12", "2026-10-19", "2026-10-26", "2026-11-02"), rows.stream().map(r -> r.get("analysis_date").toString()).toList());
        assertEquals("2026-10-26", rows.get(4).get("range_start").toString());
        assertEquals("2026-11-01", rows.get(4).get("range_end").toString());
        assertEquals(Instant.parse("2026-11-09T02:00:00Z"), nextFire(s));
        assertTrue(reservation.reserveOne(now).isEmpty(), "nothing is due any more");
        assertEquals(5, occurrences(s).size());
    }

    @Test void reservingAgainNeverDuplicatesALogicalOccurrenceAndTheRequestKeyIsDeterministic() {
        base();
        long s = schedule("WEEKLY", 1, null, "09:00", "Asia/Ho_Chi_Minh", 7, true, Instant.parse("2026-11-02T02:00:00Z"));
        reservation.reserveOne(Instant.parse("2026-11-02T03:00:00Z"));
        var row = occurrences(s).get(0);
        String expected = UUID.nameUUIDFromBytes(("PASPM-SCHEDULE-V1|" + s + "|" + Instant.parse("2026-11-02T02:00:00Z")).getBytes(StandardCharsets.UTF_8)).toString();
        assertEquals(expected, row.get("request_key"));
        assertThrows(DuplicateKeyException.class, () -> jdbc.update("INSERT INTO mining_schedule_occurrence(schedule_id,store_id,scheduled_at,schedule_version,config_version,request_key,status) VALUES(?,?,?,1,1,?,'PENDING')",
            s, store, Timestamp.from(Instant.parse("2026-11-02T02:00:00Z")), UUID.randomUUID().toString()), "the (schedule, scheduled_at) pair is unique");
        assertTrue(reservation.reserveOne(Instant.parse("2026-11-02T03:00:00Z")).isEmpty());
        assertEquals(1, occurrences(s).size());
    }

    // D4: the Store calendar zone is the only zone the scheduler runs in; a schedule stored in another zone is kept but inert
    @Test void aLegacyScheduleInAnotherZoneIsNeverReservedAndKeepsItsFireTime() {
        base();
        Instant due = Instant.parse("2026-11-01T11:30:00Z");
        long s = schedule("WEEKLY", 1, null, "00:30", "Pacific/Auckland", 7, true, due);
        assertTrue(reservation.reserveOne(Instant.parse("2026-11-01T12:00:00Z")).isEmpty());
        assertEquals(0, occurrences(s).size());
        assertEquals(due, nextFire(s), "nothing consumed or reinterpreted");
    }

    @Test void theAnalysisDateIsTheStoreCalendarDateEvenWhenUtcIsStillTheDayBefore() {
        base();
        // Monday 2026-11-02 00:30 in Ho Chi Minh (UTC+7) is still Sunday 2026-11-01 17:30 UTC
        long s = schedule("WEEKLY", 1, null, "00:30", "Asia/Ho_Chi_Minh", 7, true, Instant.parse("2026-11-01T17:30:00Z"));
        reservation.reserveOne(Instant.parse("2026-11-01T18:00:00Z"));
        var row = occurrences(s).get(0);
        assertEquals("2026-11-02", row.get("analysis_date").toString());
        assertEquals("2026-10-26", row.get("range_start").toString());
        assertEquals("2026-11-01", row.get("range_end").toString());
        assertEquals("Asia/Ho_Chi_Minh", row.get("time_zone"));
    }

    // D3 (amended 2026-11-02): lookback 7..365; out-of-range rows are kept exactly as stored and never truncated into a window
    @Test void lookbackBoundariesSevenAndThreeHundredSixtyFiveGiveTheExactWindow() {
        base();
        long seven = schedule("MONTHLY", null, 15, "09:00", "Asia/Ho_Chi_Minh", 7, true, Instant.parse("2026-11-15T02:00:00Z"));
        long max = schedule("MONTHLY", null, 15, "10:00", "Asia/Ho_Chi_Minh", 365, true, Instant.parse("2026-11-15T03:00:00Z"));
        Instant now = Instant.parse("2026-11-16T00:00:00Z");
        reservation.reserveOne(now); reservation.reserveOne(now);
        var a = occurrences(seven).get(0);
        assertEquals("2026-11-08", a.get("range_start").toString());
        assertEquals("2026-11-14", a.get("range_end").toString());
        var b = occurrences(max).get(0);
        assertEquals(LocalDate.parse("2026-11-14").minusDays(364).toString(), b.get("range_start").toString());
        assertEquals("2026-11-14", b.get("range_end").toString());
    }

    @Test void aLegacyLookbackOutsideTheApprovedRangeIsNeverReservedOrTruncated() {
        base();
        Instant due = Instant.parse("2026-11-02T02:00:00Z");
        List<Long> legacy = new ArrayList<>();
        for (int days : new int[] {1, 6, 366, 3660}) legacy.add(schedule("WEEKLY", 1, null, "09:00", "Asia/Ho_Chi_Minh", days, true, due));
        long valid = schedule("WEEKLY", 1, null, "09:00", "Asia/Ho_Chi_Minh", 7, true, due.plusSeconds(60));
        Instant now = Instant.parse("2026-11-02T03:00:00Z");
        assertTrue(reservation.reserveOne(now).isPresent(), "the valid schedule behind the legacy rows is still served");
        assertTrue(reservation.reserveOne(now).isEmpty());
        for (long id : legacy) { assertEquals(0, occurrences(id).size()); assertEquals(due, nextFire(id)); }
        assertEquals(1, occurrences(valid).size());
    }

    @Test void monthlyDay31ClampsToFebruaryEndThenReturnsToDay31() {
        base();
        long s = schedule("MONTHLY", null, 31, "09:00", "Asia/Ho_Chi_Minh", 7, true, Instant.parse("2027-01-31T02:00:00Z"));
        Instant now = Instant.parse("2027-03-01T00:00:00Z");
        while (reservation.reserveOne(now).isPresent()) { /* drain */ }
        var fires = occurrences(s).stream().map(r -> ((Timestamp) r.get("scheduled_at")).toInstant().toString()).toList();
        assertEquals(List.of("2027-01-31T02:00:00Z", "2027-02-28T02:00:00Z"), fires);
        assertEquals(Instant.parse("2027-03-31T02:00:00Z"), nextFire(s), "no drift: March keeps day 31");
    }

    // D5 (confirmed 2026-11-02): a missing day runs on the last valid day of the month; each logical fire time is unique
    @Test void monthlyDay29And30UseTheLastValidDayInLeapAndNonLeapFebruaryAndThirtyDayMonths() {
        base();
        long d29 = schedule("MONTHLY", null, 29, "09:00", "Asia/Ho_Chi_Minh", 7, true, Instant.parse("2027-01-29T02:00:00Z"));
        long d30 = schedule("MONTHLY", null, 30, "10:00", "Asia/Ho_Chi_Minh", 7, true, Instant.parse("2028-01-30T03:00:00Z"));
        Instant now = Instant.parse("2028-05-01T00:00:00Z");
        // each reservation advances only the schedule it served, so drain per schedule by catching up one at a time
        for (int i = 0; i < 40; i++) if (reservation.reserveOne(now).isEmpty()) break;
        var nonLeap = occurrences(d29).stream().map(r -> ((Timestamp) r.get("scheduled_at")).toInstant().toString()).toList();
        assertTrue(nonLeap.contains("2027-02-28T02:00:00Z"), "non-leap February clamps to the 28th: " + nonLeap);
        assertFalse(nonLeap.stream().anyMatch(t -> t.startsWith("2027-03-01") || t.startsWith("2027-03-02")), "never spills into March");
        var leap = occurrences(d30).stream().map(r -> ((Timestamp) r.get("scheduled_at")).toInstant().toString()).toList();
        assertEquals(List.of("2028-01-30T03:00:00Z", "2028-02-29T03:00:00Z", "2028-03-30T03:00:00Z", "2028-04-30T03:00:00Z"), leap,
            "leap February clamps to the 29th and 30-day April keeps day 30");
        assertEquals(leap.size(), new java.util.HashSet<>(leap).size(), "every logical fire time is unique");
    }

    @Test void disabledFutureOrUnscheduledSchedulesAreNeverReserved() {
        base();
        Instant now = Instant.parse("2026-11-02T03:00:00Z");
        long disabled = schedule("WEEKLY", 1, null, "09:00", "Asia/Ho_Chi_Minh", 7, false, Instant.parse("2026-10-26T02:00:00Z"));
        long future = schedule("WEEKLY", 1, null, "09:00", "Asia/Ho_Chi_Minh", 7, true, Instant.parse("2026-11-09T02:00:00Z"));
        long noFireTime = schedule("WEEKLY", 1, null, "09:00", "Asia/Ho_Chi_Minh", 7, true, null);
        for (int i = 0; i < 3; i++) reservation.reserveOne(now);
        assertEquals(0, occurrences(disabled).size());
        assertEquals(0, occurrences(future).size());
        assertEquals(0, occurrences(noFireTime).size());
    }

    @Test void withoutAnyMiningConfigurationReservationFailsAndLeavesTheScheduleUntouched() {
        base();
        long s = schedule("WEEKLY", 1, null, "09:00", "Asia/Ho_Chi_Minh", 7, true, Instant.parse("2026-11-02T02:00:00Z"));
        long head = jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1", Long.class);
        jdbc.update("UPDATE mining_config_head SET config_version=0 WHERE config_id=1");
        try {
            var e = assertThrows(ApiException.class, () -> reservation.reserveOne(Instant.parse("2026-11-02T03:00:00Z")));
            assertEquals(409, e.status());
            assertEquals(0, occurrences(s).size());
            assertEquals(Instant.parse("2026-11-02T02:00:00Z"), nextFire(s), "the failed reservation rolled back, the fire time is not consumed");
        } finally {
            jdbc.update("UPDATE mining_config_head SET config_version=? WHERE config_id=1", head);
        }
    }

    @Test void theOccurrencePinsTheConfigurationVersionActiveAtReservationNotALaterOne() {
        base();
        long s = schedule("WEEKLY", 1, null, "09:00", "Asia/Ho_Chi_Minh", 7, true, Instant.parse("2026-11-02T02:00:00Z"));
        reservation.reserveOne(Instant.parse("2026-11-02T03:00:00Z"));
        long pinned = ((Number) occurrences(s).get(0).get("config_version")).longValue();
        long next = jdbc.queryForObject("SELECT MAX(config_version) FROM mining_config_version", Long.class) + 1;
        jdbc.update("INSERT INTO mining_config_version(config_version,algorithm,min_util_mode,min_util_value,changed_by) VALUES(?,'HUI_MINER','ABSOLUTE',2,?)", next, actor);
        long head = jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1", Long.class);
        jdbc.update("UPDATE mining_config_head SET config_version=? WHERE config_id=1", next);
        try {
            assertEquals(pinned, ((Number) occurrences(s).get(0).get("config_version")).longValue());
            assertNotEquals(next, pinned);
        } finally {
            jdbc.update("UPDATE mining_config_head SET config_version=? WHERE config_id=1", head);
        }
    }
}

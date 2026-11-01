package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Decision D6 (confirmed 2026-11-02): after downtime only the latest missed fire time per schedule runs; every older one is recorded
 * as SKIPPED with a reason, atomically with the cursor advance, and never owns a snapshot, request or run.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:catchup;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
    "paspm.jwt-secret=01234567890123456789012345678901", "paspm.otp-secret=01234567890123456789012345678901",
    "paspm.bootstrap-email=", "paspm.bootstrap-password=", "paspm.mining.spmf-jar=", "paspm.mining.java-executable="
})
class MiningScheduleCatchUpTest {
    static final ZoneId HCM = ZoneId.of("Asia/Ho_Chi_Minh");
    @Autowired JdbcTemplate jdbc;
    @Autowired MiningScheduleReservation reservation;
    @Autowired MiningOccurrenceDispatcher dispatcher;
    int store, admin;

    void base() {
        jdbc.update("DELETE FROM mining_schedule_occurrence");
        jdbc.update("DELETE FROM mining_schedule");
        jdbc.update("INSERT INTO store(name) VALUES (?)", "CatchUp " + UUID.randomUUID());
        store = jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        jdbc.update("INSERT INTO app_user(role_id,email,name,status) SELECT role_id,?,'CatchUp admin','ACTIVE' FROM role WHERE code='SYSTEM_ADMIN'", UUID.randomUUID() + "@example.test");
        admin = jdbc.queryForObject("SELECT MAX(user_id) FROM app_user", Integer.class);
        if (jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1", Long.class) == 0) {
            jdbc.update("INSERT INTO mining_config_version(config_version,algorithm,min_util_mode,min_util_value,changed_by) VALUES(1,'EFIM','ABSOLUTE',1,?)", admin);
            jdbc.update("UPDATE mining_config_head SET config_version=1 WHERE config_id=1");
        }
    }

    /** Weekly Monday 09:00 Ho Chi Minh (02:00Z) whose cursor is {@code first}. */
    long weekly(Instant first) {
        var local = first.atZone(HCM);
        jdbc.update("INSERT INTO mining_schedule(store_id,created_by,updated_by,frequency,day_of_week,local_time,time_zone,lookback_days,enabled,next_fire_at) VALUES (?,?,?,'WEEKLY',?,?,'Asia/Ho_Chi_Minh',7,TRUE,?)",
            store, admin, admin, local.getDayOfWeek().getValue(), local.toLocalTime().toString(), Timestamp.from(first));
        return jdbc.queryForObject("SELECT MAX(schedule_id) FROM mining_schedule", Long.class);
    }
    List<Map<String, Object>> rows(long schedule) { return jdbc.queryForList("SELECT * FROM mining_schedule_occurrence WHERE schedule_id=? ORDER BY scheduled_at", schedule); }
    Instant cursor(long schedule) { return jdbc.queryForObject("SELECT next_fire_at FROM mining_schedule WHERE schedule_id=?", Timestamp.class, schedule).toInstant(); }
    long count(long schedule, String status) { return jdbc.queryForObject("SELECT COUNT(*) FROM mining_schedule_occurrence WHERE schedule_id=? AND status=?", Long.class, schedule, status); }
    static final Instant MON = Instant.parse("2026-10-05T02:00:00Z");

    @Test void onlyTheLatestMissedFireRunsAndTheOlderOnesAreRecordedAsSkippedWithAReason() {
        base();
        long s = weekly(MON);
        var outcome = reservation.reserve(Instant.parse("2026-11-02T03:00:00Z"));
        assertNotNull(outcome.occurrenceId());
        assertEquals(4, outcome.skipped());
        var rows = rows(s);
        assertEquals(List.of("SKIPPED", "SKIPPED", "SKIPPED", "SKIPPED", "PENDING"), rows.stream().map(r -> (String) r.get("status")).toList());
        assertEquals(List.of("2026-10-05T02:00:00Z", "2026-10-12T02:00:00Z", "2026-10-19T02:00:00Z", "2026-10-26T02:00:00Z", "2026-11-02T02:00:00Z"),
            rows.stream().map(r -> ((Timestamp) r.get("scheduled_at")).toInstant().toString()).toList());
        for (var skipped : rows.subList(0, 4)) {
            assertEquals(MiningScheduleReservation.SKIP_SUPERSEDED, skipped.get("skip_reason"));
            assertNull(skipped.get("actor_id"));
            assertNull(skipped.get("snapshot_id"));
            assertNull(skipped.get("request_id"));
            assertNull(skipped.get("range_start"));
            assertEquals(0, ((Number) skipped.get("attempt_count")).intValue());
            assertNull(skipped.get("lease_owner"));
            assertNull(skipped.get("failure_code"));
            assertNull(skipped.get("next_retry_at"));
        }
        var latest = rows.get(4);
        assertNull(latest.get("skip_reason"));
        assertEquals("2026-11-02", latest.get("analysis_date").toString());
        assertEquals("2026-10-26", latest.get("range_start").toString());
        assertEquals("2026-11-01", latest.get("range_end").toString());
        assertEquals(Instant.parse("2026-11-09T02:00:00Z"), cursor(s), "the cursor moved past the run occurrence");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=?", Integer.class, store));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot WHERE store_id=?", Integer.class, store));
        // idempotent: nothing is due any more
        var again = reservation.reserve(Instant.parse("2026-11-02T03:00:00Z"));
        assertNull(again.occurrenceId());
        assertEquals(0, again.skipped());
        assertEquals(5, rows(s).size());
    }

    @Test void skippedKeysAreTheSameStableKeysAnOrdinaryOccurrenceWouldHave() {
        base();
        long s = weekly(MON);
        reservation.reserve(Instant.parse("2026-10-20T00:00:00Z"));
        var rows = rows(s);
        assertEquals(List.of("SKIPPED", "SKIPPED", "PENDING"), rows.stream().map(r -> (String) r.get("status")).toList());
        for (var r : rows) {
            Instant at = ((Timestamp) r.get("scheduled_at")).toInstant();
            assertEquals(UUID.nameUUIDFromBytes(("PASPM-SCHEDULE-V1|" + s + "|" + at).getBytes(StandardCharsets.UTF_8)).toString(), r.get("request_key"));
        }
    }

    @Test void aSingleDueFireIsNeverSkippedAndNothingFutureIsTouched() {
        base();
        long s = weekly(MON);
        var outcome = reservation.reserve(MON);               // exactly at the fire time
        assertNotNull(outcome.occurrenceId());
        assertEquals(0, outcome.skipped());
        assertEquals(0, count(s, "SKIPPED"));
        assertEquals(MON.plus(Duration.ofDays(7)), cursor(s));
    }

    @Test void aLongBacklogIsRecordedInBoundedBatchesAndResumesUntilTheLatestFire() {
        base();
        int weeks = 1200;                                      // roughly 23 years of downtime
        long s = weekly(MON);
        Instant now = MON.plus(Duration.ofDays(7L * weeks)).plusSeconds(3600);
        var first = reservation.reserve(now);
        assertNull(first.occurrenceId(), "the first batch only skips");
        assertEquals(MiningScheduleReservation.MAX_SKIPS_PER_TRANSACTION, first.skipped());
        assertEquals(MON.plus(Duration.ofDays(7L * MiningScheduleReservation.MAX_SKIPS_PER_TRANSACTION)), cursor(s), "progress was committed");
        assertEquals(MiningScheduleReservation.MAX_SKIPS_PER_TRANSACTION, count(s, "SKIPPED"));
        int calls = 1; Long pending = null; int skipped = first.skipped();
        while (true) {
            var next = reservation.reserve(now);
            if (!next.progressed()) break;
            calls++; skipped += next.skipped();
            if (next.occurrenceId() != null) pending = next.occurrenceId();
        }
        assertEquals(3, calls, "500 + 500 + 200 skipped and the latest reserved");
        assertEquals(weeks, skipped);
        assertNotNull(pending);
        assertEquals(weeks, count(s, "SKIPPED"));
        assertEquals(1, count(s, "PENDING"));
        assertEquals(weeks + 1, rows(s).size());
        assertEquals(MON.plus(Duration.ofDays(7L * weeks)), ((Timestamp) rows(s).get(weeks).get("scheduled_at")).toInstant(), "the one that runs is the latest fire time");
    }

    @Test void skipsAndTheReservationCommitOrRollBackTogether() {
        base();
        long s = weekly(MON);
        // an unrelated row already owns the latest fire time, so the final insert violates the unique key
        jdbc.update("INSERT INTO mining_schedule_occurrence(schedule_id,store_id,scheduled_at,schedule_version,config_version,request_key,status,failure_code) VALUES(?,?,?,1,1,?,'FAILED','HTTP_400')",
            s, store, Timestamp.from(Instant.parse("2026-10-19T02:00:00Z")), UUID.randomUUID().toString());
        assertThrows(DuplicateKeyException.class, () -> reservation.reserve(Instant.parse("2026-10-20T00:00:00Z")));
        assertEquals(1, rows(s).size(), "the SKIPPED rows written before the failure were rolled back");
        assertEquals(MON, cursor(s), "the cursor was not advanced");
    }

    @Test void concurrentInstancesRecordEveryFireTimeExactlyOnce() throws Exception {
        base();
        long s = weekly(MON);
        Instant now = Instant.parse("2026-11-02T03:00:00Z");
        var pool = Executors.newFixedThreadPool(4);
        var gate = new CountDownLatch(1);
        try {
            var calls = new ArrayList<java.util.concurrent.Future<MiningScheduleReservation.Outcome>>();
            for (int i = 0; i < 4; i++) calls.add(pool.submit(() -> { gate.await(); return reservation.reserve(now); }));
            gate.countDown();
            int reserved = 0, skipped = 0;
            for (var call : calls) { var o = call.get(); if (o.occurrenceId() != null) reserved++; skipped += o.skipped(); }
            assertEquals(1, reserved, "exactly one instance reserved the run occurrence");
            assertEquals(4, skipped, "exactly four skip records in total");
        } finally { pool.shutdownNow(); }
        assertEquals(5, rows(s).size());
        assertEquals(4, count(s, "SKIPPED"));
        assertEquals(1, count(s, "PENDING"));
        assertEquals(Instant.parse("2026-11-09T02:00:00Z"), cursor(s));
    }

    @Test void theDispatcherNeverClaimsSkippedOccurrencesAndOnlyTheLatestIsPrepared() {
        base();
        Instant first = Instant.now().minus(Duration.ofDays(21)).atZone(HCM).withSecond(0).withNano(0).toInstant();
        long s = weekly(first);
        dispatcher.poll();                                     // flag off in tests: poll() is driven by hand, as the scheduler thread would
        var rows = rows(s);
        assertEquals(4, rows.size());
        assertEquals(3, rows.stream().filter(r -> "SKIPPED".equals(r.get("status"))).count());
        for (var r : rows.subList(0, 3)) {
            assertEquals("SKIPPED", r.get("status"));
            assertEquals(0, ((Number) r.get("attempt_count")).intValue(), "a skipped occurrence is never attempted");
        }
        var latest = rows.get(3);
        assertEquals(1, ((Number) latest.get("attempt_count")).intValue(), "the latest was claimed once");
        assertEquals("FAILED", latest.get("status"), "no eligible data in this Store: explicit failure, not a fake snapshot");
        assertEquals("HTTP_400", latest.get("failure_code"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot WHERE store_id=?", Integer.class, store));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=?", Integer.class, store));
    }
}

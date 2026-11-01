package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Drives the real occurrence pipeline deterministically through {@code MiningOccurrenceDispatcher.poll()} with the
 * dispatcher flag OFF (so no background thread runs). No SPMF runtime is configured, which makes the final submission
 * step fail with a retryable 503 after the F03 snapshot has been written inside the same transaction.
 * The expectations describe CURRENT behaviour (policies D6–D8 are not approved) and the technical guarantees that hold
 * for every policy: failure classification, durable retry state, no orphan snapshot and no partial request.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:occfailures;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
    "paspm.jwt-secret=01234567890123456789012345678901", "paspm.otp-secret=01234567890123456789012345678901",
    "paspm.bootstrap-email=", "paspm.bootstrap-password=",
    "paspm.mining.spmf-jar=", "paspm.mining.java-executable="
})
class MiningOccurrenceFailurePathsTest {
    static final ZoneId HCM = ZoneId.of("Asia/Ho_Chi_Minh");
    @Autowired JdbcTemplate jdbc;
    @Autowired MiningOccurrenceDispatcher dispatcher;

    record Fixture(int store, int actor, long schedule, long occurrence) {}

    int actor(String status) {
        jdbc.update("INSERT INTO app_user(role_id,email,name,status) SELECT role_id,?,'Occurrence test',? FROM role WHERE code='SYSTEM_ADMIN'", UUID.randomUUID() + "@example.test", status);
        return jdbc.queryForObject("SELECT MAX(user_id) FROM app_user", Integer.class);
    }

    void ensureConfig(int actor) {
        if (jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1", Long.class) == 0) {
            jdbc.update("INSERT INTO mining_config_version(config_version,algorithm,min_util_mode,min_util_value,changed_by) VALUES(1,'EFIM','ABSOLUTE',1,?)", actor);
            jdbc.update("UPDATE mining_config_head SET config_version=1 WHERE config_id=1");
        }
    }

    Fixture fixture(boolean withTransactions, String actorStatus) {
        jdbc.update("INSERT INTO store(name) VALUES (?)", "OccFail " + UUID.randomUUID());
        int store = jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        int actor = actor(actorStatus);
        ensureConfig(actor);
        LocalDate today = LocalDate.now(HCM);
        if (withTransactions) {
            for (int i = 0; i < 3; i++) {
                jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,'Occurrence test','each',FALSE,10,0,'ACTIVE')", store, "O" + i + UUID.randomUUID());
            }
            var products = jdbc.queryForList("SELECT product_id FROM product WHERE store_id=? ORDER BY product_id", Integer.class, store);
            for (int[] ids : new int[][] {{0, 1}, {0, 1}, {0}, {1}, {2}}) {
                String key = UUID.randomUUID().toString();
                jdbc.update("INSERT INTO sales_transaction(store_id,source_system,source_order_id,occurred_at,status,f03_eligible,total_amount,content_sha256) VALUES (?,'OCC_TEST',?,?,'COMPLETED',TRUE,20,?)", store, key, today.atStartOfDay(), "b".repeat(64));
                int tx = jdbc.queryForObject("SELECT transaction_id FROM sales_transaction WHERE store_id=? AND source_order_id=?", Integer.class, store, key);
                for (int i : ids) jdbc.update("INSERT INTO transaction_item(store_id,transaction_id,product_id,quantity,unit_price,unit_cost) VALUES (?,?,?,1,10,0)", store, tx, products.get(i));
            }
        }
        jdbc.update("INSERT INTO mining_schedule(store_id,created_by,updated_by,frequency,day_of_week,local_time,time_zone,lookback_days,enabled,next_fire_at) VALUES (?,?,?,'WEEKLY',1,'09:00','Asia/Ho_Chi_Minh',7,TRUE,?)",
            store, actor, actor, Timestamp.from(Instant.parse("2100-01-01T00:00:00Z")));
        long schedule = jdbc.queryForObject("SELECT MAX(schedule_id) FROM mining_schedule", Long.class);
        jdbc.update("INSERT INTO mining_schedule_occurrence(schedule_id,store_id,scheduled_at,schedule_version,config_version,request_key,status,actor_id,range_start,range_end,analysis_date,time_zone,compare_algorithms) VALUES(?,?,?,1,1,?,'PENDING',?,?,?,?,'Asia/Ho_Chi_Minh',FALSE)",
            schedule, store, Timestamp.from(Instant.parse("2026-11-02T02:00:00Z")), UUID.randomUUID().toString(), actor, today, today, today.plusDays(1));
        long occurrence = jdbc.queryForObject("SELECT MAX(occurrence_id) FROM mining_schedule_occurrence", Long.class);
        return new Fixture(store, actor, schedule, occurrence);
    }

    Map<String, Object> row(long occurrence) { return jdbc.queryForMap("SELECT * FROM mining_schedule_occurrence WHERE occurrence_id=?", occurrence); }
    int count(String table, int store) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE store_id=?", Integer.class, store); }

    void assertNothingPersistedForTheStore(Fixture f) {
        assertEquals(0, count("utility_snapshot", f.store()), "no snapshot (orphan) may survive a failed occurrence");
        assertEquals(0, count("mining_request", f.store()));
        assertNull(row(f.occurrence()).get("snapshot_id"));
        assertNull(row(f.occurrence()).get("request_id"));
    }

    @Test void aStoreWithoutEligibleTransactionsFailsTheOccurrenceOnceWithoutRetry() {
        var f = fixture(false, "ACTIVE");
        dispatcher.poll();
        var r = row(f.occurrence());
        assertEquals("FAILED", r.get("status"));
        assertEquals("HTTP_400", r.get("failure_code"));
        assertEquals(1, ((Number) r.get("attempt_count")).intValue());
        assertNull(r.get("next_retry_at"));
        assertNull(r.get("lease_owner"));
        assertNothingPersistedForTheStore(f);
        dispatcher.poll();
        assertEquals(1, ((Number) row(f.occurrence()).get("attempt_count")).intValue(), "a terminal occurrence is not attempted again");
    }

    @Test void anInactiveScheduleOwnerIsRefusedAndNothingIsCreated() {
        var f = fixture(true, "INACTIVE");
        dispatcher.poll();
        var r = row(f.occurrence());
        assertEquals("FAILED", r.get("status"));
        assertEquals("HTTP_403", r.get("failure_code"));
        assertNothingPersistedForTheStore(f);
    }

    @Test void aScheduleEditedAfterReservationIsRejectedAtPreparation() {
        var f = fixture(true, "ACTIVE");
        jdbc.update("UPDATE mining_schedule SET schedule_version=schedule_version+1 WHERE schedule_id=?", f.schedule());
        dispatcher.poll();
        var r = row(f.occurrence());
        assertEquals("FAILED", r.get("status"));
        assertEquals("HTTP_409", r.get("failure_code"));
        assertNothingPersistedForTheStore(f);
    }

    @Test void aScheduleDisabledAfterReservationIsRejectedAtPreparationCurrentBehaviourPendingD8() {
        var f = fixture(true, "ACTIVE");
        jdbc.update("UPDATE mining_schedule SET enabled=FALSE WHERE schedule_id=?", f.schedule());
        dispatcher.poll();
        var r = row(f.occurrence());
        assertEquals("FAILED", r.get("status"));
        assertEquals("HTTP_409", r.get("failure_code"));
        assertNothingPersistedForTheStore(f);
    }

    @Test void anOccurrenceRecordedInAnotherZoneIsRefusedRatherThanAnalysedOnTheWrongCalendarDay() {
        var f = fixture(true, "ACTIVE");
        jdbc.update("UPDATE mining_schedule_occurrence SET time_zone='Pacific/Auckland' WHERE occurrence_id=?", f.occurrence());
        dispatcher.poll();
        var r = row(f.occurrence());
        assertEquals("FAILED", r.get("status"));
        assertEquals("HTTP_409", r.get("failure_code"));
        assertEquals("Pacific/Auckland", r.get("time_zone"), "the historical zone is not rewritten");
        assertNothingPersistedForTheStore(f);
    }

    @Test void anUnavailableSpmfRuntimeIsRetriedWithBackoffThenTerminalAndRollsBackTheSnapshot() {
        var f = fixture(true, "ACTIVE");
        dispatcher.poll();
        var r = row(f.occurrence());
        assertEquals("PENDING", r.get("status"), "retryable failure returns the occurrence to PENDING");
        assertEquals("RETRYABLE_FAILURE", r.get("failure_code"));
        assertEquals(1, ((Number) r.get("attempt_count")).intValue());
        assertNull(r.get("lease_owner"));
        long delay1 = (((Timestamp) r.get("next_retry_at")).getTime() - System.currentTimeMillis()) / 1000;
        assertTrue(delay1 > 5 && delay1 <= 16, "first retry after about 15 s, was " + delay1);
        assertNothingPersistedForTheStore(f);

        dispatcher.poll();
        assertEquals(1, ((Number) row(f.occurrence()).get("attempt_count")).intValue(), "not due yet: no new attempt before next_retry_at");

        jdbc.update("UPDATE mining_schedule_occurrence SET next_retry_at=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP) WHERE occurrence_id=?", f.occurrence());
        dispatcher.poll();
        r = row(f.occurrence());
        assertEquals("PENDING", r.get("status"));
        assertEquals(2, ((Number) r.get("attempt_count")).intValue());
        long delay2 = (((Timestamp) r.get("next_retry_at")).getTime() - System.currentTimeMillis()) / 1000;
        assertTrue(delay2 > 20 && delay2 <= 31, "second retry after about 30 s, was " + delay2);
        assertNothingPersistedForTheStore(f);

        jdbc.update("UPDATE mining_schedule_occurrence SET next_retry_at=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP) WHERE occurrence_id=?", f.occurrence());
        dispatcher.poll();
        r = row(f.occurrence());
        assertEquals("FAILED", r.get("status"), "the third failed attempt is terminal");
        assertEquals(3, ((Number) r.get("attempt_count")).intValue());
        assertNull(r.get("next_retry_at"));
        assertNothingPersistedForTheStore(f);

        dispatcher.poll();
        assertEquals(3, ((Number) row(f.occurrence()).get("attempt_count")).intValue(), "no fourth attempt");
    }

    @Test void theFailedOccurrenceIsReadableThroughTheHistoryQueryShape() {
        var f = fixture(false, "ACTIVE");
        dispatcher.poll();
        var history = jdbc.queryForList("SELECT occurrence_id,status,failure_code,attempt_count,next_retry_at FROM mining_schedule_occurrence WHERE schedule_id=? ORDER BY occurrence_id DESC", f.schedule());
        assertEquals(1, history.size());
        assertEquals("HTTP_400", history.get(0).get("failure_code"));
    }
}

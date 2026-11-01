package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:schedulereservation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true","paspm.jwt-secret=01234567890123456789012345678901","paspm.otp-secret=01234567890123456789012345678901","paspm.bootstrap-email=","paspm.bootstrap-password=","paspm.avatar.directory=/tmp/paspm-schedule-reservation-avatars"})
class MiningScheduleReservationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MiningScheduleReservation reservation;

    @Test void concurrentInstancesReserveOnlyOneOccurrenceAndFreezeScopeAndPolicy() throws Exception {
        jdbc.update("INSERT INTO store(name) VALUES (?)","Reservation "+UUID.randomUUID());
        int store=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        jdbc.update("INSERT INTO app_user(role_id,email,name,status) SELECT role_id,?,'Scheduler test','ACTIVE' FROM role WHERE code='SYSTEM_ADMIN'",UUID.randomUUID()+"@example.test");
        int actor=jdbc.queryForObject("SELECT MAX(user_id) FROM app_user",Integer.class);
        jdbc.update("INSERT INTO mining_config_version(config_version,algorithm,min_util_mode,min_util_value,changed_by) VALUES(1,'EFIM','ABSOLUTE',10,?)",actor);
        jdbc.update("UPDATE mining_config_head SET config_version=1 WHERE config_id=1");
        jdbc.update("INSERT INTO mining_schedule(store_id,created_by,updated_by,frequency,day_of_week,local_time,time_zone,lookback_days,enabled,next_fire_at) VALUES (?,?,?,'WEEKLY',1,'09:00','Asia/Ho_Chi_Minh',7,TRUE,?)",store,actor,actor,Timestamp.from(Instant.parse("2026-11-02T02:00:00Z")));
        int schedule=jdbc.queryForObject("SELECT MAX(schedule_id) FROM mining_schedule",Integer.class);
        var gate=new java.util.concurrent.CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->{gate.await();return reservation.reserveOne(Instant.parse("2026-11-02T02:01:00Z"));});
            var b=pool.submit(()->{gate.await();return reservation.reserveOne(Instant.parse("2026-11-02T02:01:00Z"));});
            gate.countDown(); assertEquals(1,(a.get().isPresent()?1:0)+(b.get().isPresent()?1:0));
        } finally {pool.shutdownNow();}
        var row=jdbc.queryForMap("SELECT * FROM mining_schedule_occurrence WHERE schedule_id=?",schedule);
        assertEquals(store,((Number)row.get("store_id")).intValue());
        assertEquals(1L,((Number)row.get("config_version")).longValue());
        assertEquals(actor,((Number)row.get("actor_id")).intValue());
        assertEquals("2026-10-26",row.get("range_start").toString());
        assertEquals("2026-11-01",row.get("range_end").toString());
        assertEquals("2026-11-02",row.get("analysis_date").toString());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mining_schedule_occurrence WHERE schedule_id=?",Integer.class,schedule));
        jdbc.update("UPDATE mining_schedule SET enabled=FALSE,lookback_days=1 WHERE schedule_id=?",schedule);
        assertEquals("2026-10-26",jdbc.queryForObject("SELECT range_start FROM mining_schedule_occurrence WHERE schedule_id=?",java.time.LocalDate.class,schedule).toString());
        assertTrue(reservation.reserveOne(Instant.parse("2026-11-10T00:00:00Z")).isEmpty());
    }
}

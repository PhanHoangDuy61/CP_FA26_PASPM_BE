package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;

@EnabledIfSystemProperty(named="paspm.test.mysql",matches="true")
class MiningMigrationMySqlTest {
    @Test void upgradeFromV14PreservesExistingOccurrenceWithoutInventingContext() {
        var db=MySqlTestDatabase.create();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(db.url(),db.user(),db.password()));
        Flyway.configure().dataSource(db.url(),db.user(),db.password()).target("14").load().migrate();
        jdbc.update("INSERT INTO store(store_id,name) VALUES (93,'Synthetic occurrence store')");
        jdbc.update("INSERT INTO app_user(user_id,role_id,email,name,status) SELECT 93,role_id,'synthetic93@example.test','Test','ACTIVE' FROM role WHERE code='SYSTEM_ADMIN'");
        jdbc.update("INSERT INTO mining_config_version(config_version,algorithm,min_util_mode,min_util_value,changed_by) VALUES (1,'EFIM','ABSOLUTE',10,93)");
        jdbc.update("INSERT INTO mining_schedule(schedule_id,store_id,created_by,updated_by,frequency,day_of_week,local_time,time_zone,lookback_days) VALUES (93,93,93,93,'WEEKLY',1,'09:00','Asia/Ho_Chi_Minh',7)");
        jdbc.update("INSERT INTO mining_schedule_occurrence(occurrence_id,schedule_id,store_id,scheduled_at,schedule_version,config_version,request_key,status) VALUES(93,93,93,'2026-01-01 00:00:00',1,1,'00000000-0000-0000-0000-000000000093','PENDING')");
        var latest=Flyway.configure().dataSource(db.url(),db.user(),db.password()).target("15").load();
        assertEquals(1,latest.migrate().migrationsExecuted);latest.validate();
        assertEquals("15",latest.info().current().getVersion().toString());
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM mining_schedule_occurrence WHERE occurrence_id=93",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT config_version FROM mining_schedule_occurrence WHERE occurrence_id=93",Integer.class));
        assertNull(jdbc.queryForObject("SELECT actor_id FROM mining_schedule_occurrence WHERE occurrence_id=93",Integer.class));
        assertNull(jdbc.queryForObject("SELECT range_start FROM mining_schedule_occurrence WHERE occurrence_id=93",java.sql.Date.class));
    }
    @Test void upgradeFromV17AddsSkippedStatusFailureMessageAndTheSchedulerPrincipalWithoutTouchingHistory() {
        var db=MySqlTestDatabase.create();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(db.url(),db.user(),db.password()));
        Flyway.configure().dataSource(db.url(),db.user(),db.password()).target("17").load().migrate();
        jdbc.update("INSERT INTO store(store_id,name) VALUES (94,'Synthetic V18 store')");
        jdbc.update("INSERT INTO app_user(user_id,role_id,email,name,status) SELECT 94,role_id,'synthetic94@example.test','Test','ACTIVE' FROM role WHERE code='SYSTEM_ADMIN'");
        jdbc.update("INSERT INTO mining_config_version(config_version,algorithm,min_util_mode,min_util_value,changed_by) VALUES (1,'EFIM','ABSOLUTE',10,94)");
        jdbc.update("INSERT INTO mining_schedule(schedule_id,store_id,created_by,updated_by,frequency,day_of_week,local_time,time_zone,lookback_days) VALUES (94,94,94,94,'WEEKLY',1,'09:00','Asia/Ho_Chi_Minh',3660)");
        jdbc.update("INSERT INTO mining_schedule_occurrence(occurrence_id,schedule_id,store_id,scheduled_at,schedule_version,config_version,request_key,status,failure_code) VALUES(94,94,94,'2026-01-01 00:00:00',1,1,'00000000-0000-0000-0000-000000000094','FAILED','HTTP_400')");
        int usersBefore=jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Integer.class);
        String rowBefore=jdbc.queryForObject("SELECT CONCAT(status,'|',failure_code,'|',config_version) FROM mining_schedule_occurrence WHERE occurrence_id=94",String.class);
        var latest=Flyway.configure().dataSource(db.url(),db.user(),db.password()).load();
        assertEquals(2,latest.migrate().migrationsExecuted);latest.validate();
        assertEquals("19",latest.info().current().getVersion().toString());
        assertEquals(rowBefore,jdbc.queryForObject("SELECT CONCAT(status,'|',failure_code,'|',config_version) FROM mining_schedule_occurrence WHERE occurrence_id=94",String.class));
        assertNull(jdbc.queryForObject("SELECT failure_message FROM mining_schedule_occurrence WHERE occurrence_id=94",String.class));
        assertNull(jdbc.queryForObject("SELECT skip_reason FROM mining_schedule_occurrence WHERE occurrence_id=94",String.class));
        assertEquals(3660,jdbc.queryForObject("SELECT lookback_days FROM mining_schedule WHERE schedule_id=94",Integer.class),"a legacy lookback is kept exactly as stored (D3)");
        jdbc.update("INSERT INTO mining_schedule_occurrence(schedule_id,store_id,scheduled_at,schedule_version,config_version,request_key,status,skip_reason) VALUES(94,94,'2026-01-08 00:00:00',1,1,'00000000-0000-0000-0000-000000000095','SKIPPED','MISSED_SUPERSEDED_BY_LATER_OCCURRENCE')");
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("INSERT INTO mining_schedule_occurrence(schedule_id,store_id,scheduled_at,schedule_version,config_version,request_key,status) VALUES(94,94,'2026-01-15 00:00:00',1,1,'00000000-0000-0000-0000-000000000096','SKIPPED')"));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("INSERT INTO mining_schedule_occurrence(schedule_id,store_id,scheduled_at,schedule_version,config_version,request_key,status,skip_reason) VALUES(94,94,'2026-01-22 00:00:00',1,1,'00000000-0000-0000-0000-000000000097','PENDING','X')"));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("INSERT INTO mining_schedule_occurrence(schedule_id,store_id,scheduled_at,schedule_version,config_version,request_key,status) VALUES(94,94,'2026-01-29 00:00:00',1,1,'00000000-0000-0000-0000-000000000098','UNKNOWN')"));
        assertEquals(usersBefore+1,jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Integer.class),"V19 adds exactly the scheduler principal");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM app_user u JOIN role r ON r.role_id=u.role_id WHERE r.code='SYSTEM_SCHEDULER' AND u.status='INACTIVE' AND u.password_hash IS NULL AND u.email='system-scheduler@paspm.invalid'",Integer.class));
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM app_user WHERE user_id=94",String.class),"existing accounts are untouched");
    }
    @Test void upgradeFromV13AddsDisabledScheduleStorageWithoutChangingConfig() {
        var db=MySqlTestDatabase.create();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(db.url(),db.user(),db.password()));
        Flyway.configure().dataSource(db.url(),db.user(),db.password()).target("13").load().migrate();
        jdbc.update("INSERT INTO store(store_id,name) VALUES(92,'Synthetic scheduler store')");
        var current=Flyway.configure().dataSource(db.url(),db.user(),db.password()).target("14").load();
        assertEquals(1,current.migrate().migrationsExecuted);current.validate();
        assertEquals("14",current.info().current().getVersion().toString());
        assertEquals("Synthetic scheduler store",jdbc.queryForObject("SELECT name FROM store WHERE store_id=92",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mining_schedule",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mining_schedule_occurrence",Integer.class));
    }
    @Test void upgradeFromV12KeepsFrozenSnapshotAndAddsNullablePolicyReference() {
        var db=MySqlTestDatabase.create();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(db.url(),db.user(),db.password()));
        var old=Flyway.configure().dataSource(db.url(),db.user(),db.password()).target("12").load();
        assertEquals(12,old.migrate().migrationsExecuted);
        jdbc.update("INSERT INTO store(store_id,name) VALUES(91,'Synthetic migration store')");
        jdbc.update("INSERT INTO app_user(user_id,role_id,email,name,status) SELECT 91,role_id,'synthetic91@example.test','Test','ACTIVE' FROM role WHERE code='SYSTEM_ADMIN'");
        jdbc.update("INSERT INTO utility_snapshot(snapshot_id,store_id,created_by,range_start,range_end,as_of_date,config_snapshot,source_hash,matrix_hash,total_weighted_utility,rounding_scale,status) VALUES (91,91,91,'2026-01-01','2026-01-01','2026-01-01','{}',?, ?,0,6,'READY')","a".repeat(64),"b".repeat(64));
        jdbc.update("INSERT INTO mining_request(request_id,store_id,requested_by,utility_snapshot_id,request_key,payload_hash,algorithms,min_util_mode,min_util_value,min_util_spmf,input_snapshot_hash,frozen_input,utility_config_snapshot,status,comparison_status) VALUES (91,91,91,91,'00000000-0000-0000-0000-000000000091',?,'EFIM','ABSOLUTE',1,1,?,'{}','{}','COMPLETED','NOT_REQUESTED')","c".repeat(64),"d".repeat(64));
        String requestBefore=jdbc.queryForObject("SELECT CONCAT(payload_hash,input_snapshot_hash,status,algorithms,min_util_value) FROM mining_request WHERE request_id=91",String.class);
        String before=jdbc.queryForObject("SELECT CONCAT(source_hash,matrix_hash,status,total_weighted_utility) FROM utility_snapshot WHERE snapshot_id=91",String.class);
        var current=Flyway.configure().dataSource(db.url(),db.user(),db.password()).target("13").load();
        assertEquals(1,current.migrate().migrationsExecuted);current.validate();
        assertEquals("13",current.info().current().getVersion().toString());
        assertEquals(before,jdbc.queryForObject("SELECT CONCAT(source_hash,matrix_hash,status,total_weighted_utility) FROM utility_snapshot WHERE snapshot_id=91",String.class));
        assertEquals(requestBefore,jdbc.queryForObject("SELECT CONCAT(payload_hash,input_snapshot_hash,status,algorithms,min_util_value) FROM mining_request WHERE request_id=91",String.class));
        assertNull(jdbc.queryForObject("SELECT config_version FROM mining_request WHERE request_id=91",Long.class));
        assertEquals(0,jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1",Long.class));
        assertEquals("YES",jdbc.queryForObject("SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='mining_request' AND column_name='config_version'",String.class));
    }
}

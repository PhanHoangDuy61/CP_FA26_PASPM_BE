package vn.edu.fpt.paspm.mining;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;
import static org.junit.jupiter.api.Assertions.*;

class MiningProgressMigrationTest {
    @Test void v24PreservesEveryExistingRequestColumnAndSnapshot() {
        verify("jdbc:h2:mem:progress_upgrade_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
    }
    @Test @EnabledIfSystemProperty(named="paspm.test.mysql",matches="true")
    void mysqlV24PreservesEveryExistingRequestColumnAndSnapshot() {
        var db=MySqlTestDatabase.create();verify(db.url(),db.user(),db.password());
    }
    private void verify(String url,String user,String password) {
        Flyway.configure().dataSource(url,user,password).target("23").load().migrate();
        var db=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        db.update("INSERT INTO store(name) VALUES ('Legacy progress fixture')");
        int store=db.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        db.update("INSERT INTO app_user(email,role_id,status,store_id) SELECT ?,role_id,'ACTIVE',? FROM role WHERE code='MERCHANDISER'",UUID.randomUUID()+"@example.invalid",store);
        int actor=db.queryForObject("SELECT MAX(user_id) FROM app_user",Integer.class);
        db.update("INSERT INTO utility_snapshot(store_id,created_by,range_start,range_end,as_of_date,config_snapshot,source_hash,matrix_hash,total_weighted_utility,rounding_scale,status) VALUES (?,?,'2026-11-01','2026-11-01','2026-11-01',? ,?,?,1,6,'READY')",store,actor,"{\"legacy\":true}","a".repeat(64),"b".repeat(64));
        int snapshot=db.queryForObject("SELECT MAX(snapshot_id) FROM utility_snapshot",Integer.class);
        db.update("INSERT INTO mining_request(store_id,requested_by,utility_snapshot_id,request_key,payload_hash,algorithms,min_util_mode,min_util_value,min_util_spmf,input_snapshot_hash,frozen_input,utility_config_snapshot,comparison_status,status) VALUES (?,?,?,?,?,'HUI_MINER','ABSOLUTE',1,1,?,?,?,'NOT_REQUESTED','COMPLETED')",store,actor,snapshot,UUID.randomUUID().toString(),"c".repeat(64),"d".repeat(64),"{\"frozen\":true}","{\"old\":true}");
        long id=db.queryForObject("SELECT MAX(request_id) FROM mining_request",Long.class);
        var before=db.queryForMap("SELECT * FROM mining_request WHERE request_id=?",id);
        String frozenBefore=db.queryForObject("SELECT frozen_input FROM mining_request WHERE request_id=?",String.class,id);
        String snapshotBefore=db.queryForObject("SELECT config_snapshot FROM utility_snapshot WHERE snapshot_id=?",String.class,snapshot);
        var flyway=Flyway.configure().dataSource(url,user,password).target("24").load();
        assertEquals(1,flyway.migrate().migrationsExecuted);flyway.validate();
        var after=db.queryForMap("SELECT * FROM mining_request WHERE request_id=?",id);
        for(var entry:before.entrySet()) {
            if(entry.getValue() instanceof byte[] bytes)assertArrayEquals(bytes,(byte[])after.get(entry.getKey()),entry.getKey());
            else assertEquals(entry.getValue(),after.get(entry.getKey()),entry.getKey());
        }
        assertEquals(frozenBefore,db.queryForObject("SELECT frozen_input FROM mining_request WHERE request_id=?",String.class,id));
        assertEquals(snapshotBefore,db.queryForObject("SELECT config_snapshot FROM utility_snapshot WHERE snapshot_id=?",String.class,snapshot));
        var progress=new MiningProgressReader(db,60000).get(store,id);
        assertEquals("LEGACY",progress.stage());assertNull(progress.stageStartedAt());assertNull(progress.elapsedMs());
        assertEquals(0,flyway.migrate().migrationsExecuted);
    }
}

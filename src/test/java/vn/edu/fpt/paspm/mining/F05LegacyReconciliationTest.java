package vn.edu.fpt.paspm.mining;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Verify the documented inventory is read-only and excludes healthy leases. */
class F05LegacyReconciliationTest {
    @Test void documentedQueryReportsOnlyUnownedRunningRequestsAndTheirOutputs() throws Exception {
        var db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        db.execute("CREATE TABLE mining_request(store_id INT,request_id BIGINT,utility_snapshot_id INT,config_version BIGINT,status VARCHAR(12),lease_owner VARCHAR(36),lease_fence BIGINT,lease_until TIMESTAMP)");
        db.execute("CREATE TABLE mining_run(store_id INT,request_id BIGINT,run_id BIGINT,algorithm VARCHAR(12),attempt_no INT,status VARCHAR(12),itemset_count INT)");
        db.execute("CREATE TABLE high_utility_pattern(store_id INT,run_id BIGINT,pattern_id BIGINT)");
        db.update("INSERT INTO mining_request VALUES(1,11,100,NULL,'RUNNING',NULL,0,NULL),(2,22,200,4,'RUNNING','live-owner',1,CURRENT_TIMESTAMP),(1,33,100,NULL,'COMPLETED',NULL,1,NULL)");
        db.update("INSERT INTO mining_run VALUES(1,11,101,'HUI_MINER',1,'COMPLETED',2),(1,11,102,'EFIM',1,'RUNNING',NULL),(2,22,201,'EFIM',1,'RUNNING',NULL)");
        db.update("INSERT INTO high_utility_pattern VALUES(1,101,1),(1,101,2)");
        String doc=Files.readString(Path.of("docs/architecture/F05_LEGACY_RUNNING_RECONCILIATION.md"));
        String sql=doc.split("```sql",2)[1].split("```",2)[0].trim();
        var rows=db.queryForList(sql);
        assertEquals(2,rows.size());
        assertTrue(rows.stream().allMatch(row->((Number)row.get("REQUEST_ID")).longValue()==11));
        assertTrue(rows.stream().allMatch(row->row.get("CONFIG_VERSION")==null));
        assertEquals(2,((Number)rows.get(0).get("PERSISTED_PATTERNS")).intValue());
        assertEquals(0,((Number)rows.get(1).get("PERSISTED_PATTERNS")).intValue());
        assertEquals("RUNNING",db.queryForObject("SELECT status FROM mining_request WHERE request_id=22",String.class));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM high_utility_pattern",Integer.class));
    }
}

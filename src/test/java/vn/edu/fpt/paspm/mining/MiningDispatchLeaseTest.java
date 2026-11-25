package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class MiningDispatchLeaseTest {
    JdbcTemplate jdbc; MiningDispatchLease leases;
    @BeforeEach void setup(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE mining_request(request_id BIGINT PRIMARY KEY,store_id INT NOT NULL,status VARCHAR(12) NOT NULL,lease_owner VARCHAR(36),lease_fence BIGINT NOT NULL DEFAULT 0,lease_until TIMESTAMP NULL)");
        jdbc.execute("CREATE TABLE output_rows(request_id BIGINT NOT NULL, output_value VARCHAR(20))");
        jdbc.update("INSERT INTO mining_request(request_id,store_id,status) VALUES(10,1,'PENDING')");
        leases=new MiningDispatchLease(jdbc,new DataSourceTransactionManager(ds));
    }
    @Test void onlyExpiredOwnershipCanBeReclaimedAndStaleOwnerCannotCommit() {
        var first=leases.claim(1,10,"owner-a",60).orElseThrow();
        assertTrue(leases.claim(1,10,"owner-b",60).isEmpty());
        assertTrue(leases.renew(first,60));
        jdbc.update("UPDATE mining_request SET lease_until=DATEADD('SECOND',-1,CURRENT_TIMESTAMP) WHERE request_id=10");
        var second=leases.claim(1,10,"owner-b",60).orElseThrow();
        assertTrue(second.fence()>first.fence());
        assertFalse(leases.renew(first,60));
        assertThrows(IllegalStateException.class,()->leases.finish(first,()->jdbc.update("INSERT INTO output_rows VALUES(10,'stale')")));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM output_rows",Integer.class));
        leases.finish(second,()->jdbc.update("INSERT INTO output_rows VALUES(10,'valid')"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM output_rows",Integer.class));
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM mining_request WHERE request_id=10",String.class));
        assertTrue(leases.claim(1,10,"owner-c",60).isEmpty());
    }
    @Test void rollbackPreventsOutputWhenLeaseExpiresDuringFinalization() {
        var claim=leases.claim(1,10,"owner-a",60).orElseThrow();
        assertThrows(IllegalStateException.class,()->leases.finish(claim,()->{
            jdbc.update("INSERT INTO output_rows VALUES(10,'partial')");
            jdbc.update("UPDATE mining_request SET lease_until=DATEADD('SECOND',-1,CURRENT_TIMESTAMP) WHERE request_id=10");
        }));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM output_rows",Integer.class));
        assertEquals("RUNNING",jdbc.queryForObject("SELECT status FROM mining_request WHERE request_id=10",String.class));
    }
}

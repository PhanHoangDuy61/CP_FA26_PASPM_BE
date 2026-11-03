package vn.edu.fpt.paspm.mining;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class MiningOccurrenceLeaseTest {
    @Test void retryBackoffIsDurableAndStopsAfterThirdAttempt(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE mining_schedule_occurrence(occurrence_id BIGINT PRIMARY KEY,status VARCHAR(20),lease_owner VARCHAR(36),lease_fence BIGINT DEFAULT 0,lease_until TIMESTAMP,attempt_count INT DEFAULT 0,next_retry_at TIMESTAMP,failure_code VARCHAR(40),failure_message VARCHAR(500),updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO mining_schedule_occurrence(occurrence_id,status) VALUES(1,'PENDING'),(2,'PENDING')");
        var leases=new MiningOccurrenceLease(jdbc,new DataSourceTransactionManager(ds));
        for(int attempt=1;attempt<=3;attempt++) {
            var claim=leases.claim(1,"owner-"+attempt).orElseThrow();
            assertEquals(attempt,claim.fence());
            assertTrue(leases.fail(claim,"RETRYABLE_FAILURE",true));
            assertEquals(attempt,jdbc.queryForObject("SELECT attempt_count FROM mining_schedule_occurrence WHERE occurrence_id=1",Integer.class));
            if(attempt<3) {
                assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM mining_schedule_occurrence WHERE occurrence_id=1",String.class));
                assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mining_schedule_occurrence WHERE occurrence_id=1 AND next_retry_at>CURRENT_TIMESTAMP",Integer.class));
                assertTrue(leases.claim(1,"other-owner").isEmpty());
                jdbc.update("UPDATE mining_schedule_occurrence SET next_retry_at=DATEADD('SECOND',-1,CURRENT_TIMESTAMP) WHERE occurrence_id=1");
            }
        }
        assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM mining_schedule_occurrence WHERE occurrence_id=1",String.class));
        assertEquals("RETRY_EXHAUSTED",jdbc.queryForObject("SELECT failure_code FROM mining_schedule_occurrence WHERE occurrence_id=1",String.class),"D7: the third retryable failure is RETRY_EXHAUSTED");
        assertNull(jdbc.queryForObject("SELECT next_retry_at FROM mining_schedule_occurrence WHERE occurrence_id=1",java.sql.Timestamp.class));
        assertTrue(leases.claim(1,"other-owner").isEmpty());
        var terminal=leases.claim(2,"owner-terminal").orElseThrow();
        assertTrue(leases.fail(terminal,"HTTP_400",false));
        assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM mining_schedule_occurrence WHERE occurrence_id=2",String.class));
        assertTrue(leases.claim(2,"other-owner").isEmpty());
    }
    @Test void expiredClaimIsReclaimedAndStaleOwnerCannotCompleteOrRetry(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE mining_schedule_occurrence(occurrence_id BIGINT PRIMARY KEY,status VARCHAR(20),lease_owner VARCHAR(36),lease_fence BIGINT DEFAULT 0,lease_until TIMESTAMP,attempt_count INT DEFAULT 0,next_retry_at TIMESTAMP,failure_code VARCHAR(40),failure_message VARCHAR(500),updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO mining_schedule_occurrence(occurrence_id,status) VALUES(1,'PENDING')");
        var leases=new MiningOccurrenceLease(jdbc,new DataSourceTransactionManager(ds));
        var first=leases.claim(1,"owner-a").orElseThrow();assertTrue(leases.claim(1,"owner-b").isEmpty());
        jdbc.update("UPDATE mining_schedule_occurrence SET lease_until=DATEADD('SECOND',-1,CURRENT_TIMESTAMP) WHERE occurrence_id=1");
        var second=leases.claim(1,"owner-b").orElseThrow();assertTrue(second.fence()>first.fence());
        assertFalse(leases.fail(first,"RETRYABLE",true));
        assertTrue(leases.fail(second,"RETRYABLE",true));
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM mining_schedule_occurrence WHERE occurrence_id=1",String.class));
        assertTrue(leases.claim(1,"owner-c").isEmpty(),"retry backoff must be durable");
    }
}

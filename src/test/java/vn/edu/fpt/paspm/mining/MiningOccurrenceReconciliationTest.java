package vn.edu.fpt.paspm.mining;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class MiningOccurrenceReconciliationTest {
    @Test void pendingBacklogDoesNotStarveTerminalOccurrenceAndStoreScopeIsEnforced(){
        var db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        db.execute("CREATE TABLE mining_request(request_id BIGINT,store_id INT,status VARCHAR(12))");
        db.execute("CREATE TABLE mining_schedule_occurrence(occurrence_id BIGINT,store_id INT,request_id BIGINT,status VARCHAR(20),failure_code VARCHAR(40),updated_at TIMESTAMP)");
        for(int i=1;i<=101;i++){db.update("INSERT INTO mining_request VALUES(?,1,'RUNNING')",i);db.update("INSERT INTO mining_schedule_occurrence VALUES(?,1,?,'DISPATCHED',NULL,CURRENT_TIMESTAMP)",i,i);}
        db.update("INSERT INTO mining_request VALUES(102,1,'COMPLETED'),(103,2,'COMPLETED')");
        db.update("INSERT INTO mining_schedule_occurrence VALUES(102,1,102,'DISPATCHED',NULL,CURRENT_TIMESTAMP),(103,1,103,'DISPATCHED',NULL,CURRENT_TIMESTAMP)");
        var dispatcher=new MiningOccurrenceDispatcher(db,null,null,null,false);
        try{dispatcher.reconcile();}finally{dispatcher.close();}
        assertEquals("COMPLETED",db.queryForObject("SELECT status FROM mining_schedule_occurrence WHERE occurrence_id=102",String.class));
        assertEquals("DISPATCHED",db.queryForObject("SELECT status FROM mining_schedule_occurrence WHERE occurrence_id=103",String.class));
        assertEquals(101,db.queryForObject("SELECT COUNT(*) FROM mining_schedule_occurrence WHERE occurrence_id<=101 AND status='DISPATCHED'",Integer.class));
    }
}

package vn.edu.fpt.paspm.mining;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.paspm.common.ApiException;

/** Atomically links the fresh snapshot and request while the occurrence is fenced. */
@Service
public class MiningOccurrenceProcessor {
    private final JdbcTemplate jdbc;private final MiningScheduledSnapshot snapshots;private final MiningScheduledRequest requests;private final MiningOccurrenceLease leases;
    public MiningOccurrenceProcessor(JdbcTemplate jdbc,MiningScheduledSnapshot snapshots,MiningScheduledRequest requests,MiningOccurrenceLease leases){this.jdbc=jdbc;this.snapshots=snapshots;this.requests=requests;this.leases=leases;}
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public void process(MiningOccurrenceLease.Claim claim){
        Long schedule=jdbc.query("SELECT schedule_id FROM mining_schedule_occurrence WHERE occurrence_id=?",(r,n)->r.getLong(1),claim.occurrenceId()).stream().findFirst().orElseThrow(()->new ApiException(404,"Occurrence not found"));
        jdbc.queryForObject("SELECT schedule_id FROM mining_schedule WHERE schedule_id=? FOR UPDATE",Long.class,schedule);
        var current=jdbc.query("SELECT request_id,store_id FROM mining_schedule_occurrence WHERE occurrence_id=? AND schedule_id=? AND status='PROCESSING' AND lease_owner=? AND lease_fence=? AND lease_until>CURRENT_TIMESTAMP FOR UPDATE",(r,n)->new Object[]{r.getObject(1,Long.class),r.getInt(2)},claim.occurrenceId(),schedule,claim.owner(),claim.fence());
        if(current.isEmpty())throw new IllegalStateException("Occurrence lease is no longer active");
        Long existing=(Long)current.get(0)[0];int store=(Integer)current.get(0)[1];
        if(existing==null){snapshots.prepare(claim.occurrenceId());requests.submit(claim.occurrenceId());}
        else {
            int valid=jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE request_id=? AND store_id=?",Integer.class,existing,store);
            if(valid!=1)throw new ApiException(409,"Occurrence request unavailable");
            jdbc.update("UPDATE mining_schedule_occurrence SET status='DISPATCHED' WHERE occurrence_id=?",claim.occurrenceId());
        }
        leases.finishDispatch(claim);
    }
}

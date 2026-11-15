package vn.edu.fpt.paspm.mining;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.edu.fpt.paspm.common.ApiException;
import static vn.edu.fpt.paspm.mining.MiningContracts.ProgressView;

/** Lightweight polling: one consistent scalar query, no frozen JSON deserialization. */
@Service
public class MiningProgressReader {
    private final JdbcTemplate jdbc;
    private final long timeout;
    public MiningProgressReader(JdbcTemplate jdbc,@Value("${paspm.mining.timeout-millis:60000}") long timeout) {
        this.jdbc=jdbc;this.timeout=timeout;
    }
    public ProgressView get(int store,long id) {
        return jdbc.query("""
            SELECT q.request_id,q.store_id,q.status,q.stage,q.stage_started_at,q.current_run_id,
                   q.lease_fence,q.comparison_status,CURRENT_TIMESTAMP AS db_now,
                   COUNT(r.run_id) AS algorithm_count,
                   COALESCE(SUM(CASE WHEN r.status='COMPLETED' THEN 2
                       WHEN q.stage='SAVING' AND r.run_id=q.current_run_id AND r.status='RUNNING' THEN 1 ELSE 0 END),0) AS done,
                   MAX(CASE WHEN r.run_id=q.current_run_id THEN r.algorithm ELSE NULL END) AS algorithm
            FROM mining_request q LEFT JOIN mining_run r ON r.store_id=q.store_id AND r.request_id=q.request_id
            WHERE q.store_id=? AND q.request_id=?
            GROUP BY q.request_id,q.store_id,q.status,q.stage,q.stage_started_at,q.current_run_id,q.lease_fence,q.comparison_status
            """,(r,n)->{
                int algorithms=r.getInt("algorithm_count");
                int total=2*algorithms+(algorithms==2?1:0);
                String comparison=r.getString("comparison_status"),stage=r.getString("stage");
                int done=r.getInt("done")+(algorithms==2&&(comparison.equals("MATCH")||comparison.equals("FAILED"))?1:0);
                var started=r.getTimestamp("stage_started_at");
                Long elapsed=started==null?null:Math.max(0L,Duration.between(started.toInstant(),r.getTimestamp("db_now").toInstant()).toMillis());
                return new ProgressView(id,store,r.getString("status"),stage,started==null?null:started.toInstant(),
                    r.getObject("current_run_id",Long.class),r.getString("algorithm"),r.getLong("lease_fence"),done,total,
                    total==0?0:Math.min(100,100*done/total),elapsed,stage.equals("RUNNING")?timeout:null,comparison);
            },store,id).stream().findFirst().orElseThrow(()->new ApiException(404,"Mining request not found"));
    }
}

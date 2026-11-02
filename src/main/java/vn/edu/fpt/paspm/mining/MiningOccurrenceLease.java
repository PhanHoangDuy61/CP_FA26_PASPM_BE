package vn.edu.fpt.paspm.mining;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable occurrence ownership. Unowned legacy PROCESSING rows are never assumed abandoned. */
@Service
public class MiningOccurrenceLease {
    static final int MAX_ATTEMPTS=3;
    static final long LEASE_SECONDS=300;
    record Claim(long occurrenceId,String owner,long fence) {}
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;
    public MiningOccurrenceLease(JdbcTemplate jdbc,PlatformTransactionManager manager){this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);}
    Optional<Claim> claim(long id,String owner){
        if(owner==null||owner.isBlank()||owner.length()>36)throw new IllegalArgumentException("Invalid occurrence owner");
        return tx.execute(status->{
            Instant now=jdbc.queryForObject("SELECT CURRENT_TIMESTAMP",Timestamp.class).toInstant();
            int changed=jdbc.update("UPDATE mining_schedule_occurrence SET status='PROCESSING',lease_owner=?,lease_fence=lease_fence+1,lease_until=?,attempt_count=attempt_count+1,updated_at=CURRENT_TIMESTAMP WHERE occurrence_id=? AND attempt_count<? AND ((status='PENDING' AND (next_retry_at IS NULL OR next_retry_at<=CURRENT_TIMESTAMP)) OR (status='PROCESSING' AND lease_owner IS NOT NULL AND lease_until<=CURRENT_TIMESTAMP))",owner,Timestamp.from(now.plusSeconds(LEASE_SECONDS)),id,MAX_ATTEMPTS);
            if(changed==0)return Optional.empty();
            Long fence=jdbc.queryForObject("SELECT lease_fence FROM mining_schedule_occurrence WHERE occurrence_id=? AND lease_owner=?",Long.class,id,owner);
            return Optional.of(new Claim(id,owner,fence));
        });
    }
    static final String RETRY_EXHAUSTED="RETRY_EXHAUSTED";
    static final int MESSAGE_LIMIT=500;
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(MiningOccurrenceLease.class);
    static String clip(String message){return message==null?null:message.length()<=MESSAGE_LIMIT?message:message.substring(0,MESSAGE_LIMIT-3)+"...";}
    boolean fail(Claim c,String code,boolean retryable){return fail(c,code,retryable,null);}
    /**
     * Decision D7: at most {@link #MAX_ATTEMPTS} preparation attempts, 15 s then 30 s apart. A retryable failure of the last attempt is
     * terminal {@code FAILED/RETRY_EXHAUSTED}; a non-retryable one is terminal at once. The final cause is stored in {@code failure_message}.
     */
    boolean fail(Claim c,String code,boolean retryable,String message){
        return Boolean.TRUE.equals(tx.execute(status->{
            var values=jdbc.query("SELECT attempt_count FROM mining_schedule_occurrence WHERE occurrence_id=? AND status='PROCESSING' AND lease_owner=? AND lease_fence=? AND lease_until>CURRENT_TIMESTAMP FOR UPDATE",(r,n)->r.getInt(1),c.occurrenceId(),c.owner(),c.fence());
            if(values.isEmpty())return false;
            int count=values.get(0);boolean retry=retryable&&count<MAX_ATTEMPTS;
            String finalCode=retryable&&!retry?RETRY_EXHAUSTED:code;
            String detail=clip(retryable&&!retry?"Retries exhausted after "+count+" attempts: "+(message==null?code:message):message);
            if(!retry)log.error("Scheduled occurrence {} failed permanently: code={} message={}",c.occurrenceId(),finalCode,detail);
            Timestamp next=retry?Timestamp.from(jdbc.queryForObject("SELECT CURRENT_TIMESTAMP",Timestamp.class).toInstant().plusSeconds(15L*(1L<<(count-1)))):null;
            return jdbc.update("UPDATE mining_schedule_occurrence SET status=?,failure_code=?,failure_message=?,next_retry_at=?,lease_owner=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP WHERE occurrence_id=? AND status='PROCESSING' AND lease_owner=? AND lease_fence=? AND lease_until>CURRENT_TIMESTAMP",retry?"PENDING":"FAILED",finalCode,detail,next,c.occurrenceId(),c.owner(),c.fence())==1;
        }));
    }
    void finishDispatch(Claim c){
        int changed=jdbc.update("UPDATE mining_schedule_occurrence SET lease_owner=NULL,lease_until=NULL,next_retry_at=NULL,failure_code=NULL,failure_message=NULL,updated_at=CURRENT_TIMESTAMP WHERE occurrence_id=? AND status='DISPATCHED' AND lease_owner=? AND lease_fence=? AND lease_until>CURRENT_TIMESTAMP",c.occurrenceId(),c.owner(),c.fence());
        if(changed!=1)throw new IllegalStateException("Occurrence lease expired before dispatch commit");
    }
}

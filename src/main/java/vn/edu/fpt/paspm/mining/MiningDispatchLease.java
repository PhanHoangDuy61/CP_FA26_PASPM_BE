package vn.edu.fpt.paspm.mining;

import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Database-coordinated request ownership and fenced persistence. */
@Service
public class MiningDispatchLease {
    public record Claim(int storeId,long requestId,String owner,long fence) {}
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;
    public MiningDispatchLease(JdbcTemplate jdbc,PlatformTransactionManager manager){this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);}
    public Optional<Claim> claim(int store,long id,String owner,int seconds){
        validate(owner,seconds);
        return tx.execute(status->{
            Timestamp now=jdbc.queryForObject("SELECT CURRENT_TIMESTAMP",Timestamp.class);
            Timestamp expiry=Timestamp.from(now.toInstant().plusSeconds(seconds));
            int changed=jdbc.update("UPDATE mining_request SET status='RUNNING',lease_owner=?,lease_fence=lease_fence+1,lease_until=? WHERE store_id=? AND request_id=? AND (status='PENDING' OR (status='RUNNING' AND lease_owner IS NOT NULL AND lease_until<=CURRENT_TIMESTAMP))",owner,expiry,store,id);
            if(changed==0)return Optional.empty();
            Long fence=jdbc.queryForObject("SELECT lease_fence FROM mining_request WHERE store_id=? AND request_id=? AND lease_owner=?",Long.class,store,id,owner);
            return Optional.of(new Claim(store,id,owner,fence));
        });
    }
    public boolean renew(Claim c,int seconds){
        validate(c.owner(),seconds);
        Timestamp now=jdbc.queryForObject("SELECT CURRENT_TIMESTAMP",Timestamp.class);
        return jdbc.update("UPDATE mining_request SET lease_until=? WHERE store_id=? AND request_id=? AND status='RUNNING' AND lease_owner=? AND lease_fence=? AND lease_until>CURRENT_TIMESTAMP",Timestamp.from(now.toInstant().plusSeconds(seconds)),c.storeId(),c.requestId(),c.owner(),c.fence())==1;
    }
    /** Hold the request row only while persisting state, never during mining execution. */
    public void guarded(Claim c,Runnable action){
        tx.executeWithoutResult(status->{
            requireCurrent(c);
            action.run();
            requireCurrent(c);
        });
    }
    private void requireCurrent(Claim c){
        int active=jdbc.query("SELECT request_id FROM mining_request WHERE store_id=? AND request_id=? AND status='RUNNING' AND lease_owner=? AND lease_fence=? AND lease_until>CURRENT_TIMESTAMP FOR UPDATE",(r,n)->r.getLong(1),c.storeId(),c.requestId(),c.owner(),c.fence()).size();
        if(active!=1)throw new IllegalStateException("Mining lease is no longer active");
    }
    public void terminal(Claim c,String result,String comparison){
        tx.executeWithoutResult(status->{
            requireCurrent(c);
            if(jdbc.update("UPDATE mining_request SET status=?,comparison_status=?,lease_owner=NULL,lease_until=NULL WHERE store_id=? AND request_id=? AND status='RUNNING' AND lease_owner=? AND lease_fence=? AND lease_until>CURRENT_TIMESTAMP",result,comparison,c.storeId(),c.requestId(),c.owner(),c.fence())!=1)throw new IllegalStateException("Mining lease expired before completion");
        });
    }
    /** The callback must perform database writes in this same transaction only. */
    public void finish(Claim c,Runnable persist){
        tx.executeWithoutResult(status->{
            int active=jdbc.query("SELECT request_id FROM mining_request WHERE store_id=? AND request_id=? AND status='RUNNING' AND lease_owner=? AND lease_fence=? AND lease_until>CURRENT_TIMESTAMP FOR UPDATE",(r,n)->r.getLong(1),c.storeId(),c.requestId(),c.owner(),c.fence()).size();
            if(active!=1)throw new IllegalStateException("Mining lease is no longer active");
            persist.run();
            if(jdbc.update("UPDATE mining_request SET status='COMPLETED',lease_owner=NULL,lease_until=NULL WHERE store_id=? AND request_id=? AND status='RUNNING' AND lease_owner=? AND lease_fence=? AND lease_until>CURRENT_TIMESTAMP",c.storeId(),c.requestId(),c.owner(),c.fence())!=1)throw new IllegalStateException("Mining lease expired before commit");
        });
    }
    private static void validate(String owner,int seconds){
        if(owner==null||owner.isBlank()||owner.length()>36||seconds<1||seconds>3600)throw new IllegalArgumentException("Invalid mining lease");
    }
}

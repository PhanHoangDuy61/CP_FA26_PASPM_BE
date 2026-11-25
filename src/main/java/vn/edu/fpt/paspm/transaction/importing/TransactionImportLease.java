package vn.edu.fpt.paspm.transaction.importing;

import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TransactionImportLease {
    public record Claim(int storeId, int batchId, String owner, long fence) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public TransactionImportLease(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
    }

    public Optional<Claim> claim(int storeId, int batchId, String owner, int leaseSeconds) {
        Timestamp now = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class);
        Timestamp until = Timestamp.from(now.toInstant().plusSeconds(leaseSeconds));
        return transaction.execute(status -> {
            int changed = jdbc.update("""
                    UPDATE import_batch SET status='PROCESSING',stage='IMPORTING',processing_started_at=COALESCE(
                        processing_started_at,CURRENT_TIMESTAMP),updated_at=CURRENT_TIMESTAMP,lease_owner=?,
                        lease_fence=lease_fence+1,lease_until=?
                    WHERE store_id=? AND import_batch_id=? AND (status='QUEUED' OR
                        (status='PROCESSING' AND lease_owner IS NOT NULL AND lease_until<=CURRENT_TIMESTAMP))
                    """, owner, until, storeId, batchId);
            if (changed == 0) return Optional.empty();
            Long fence = jdbc.queryForObject("SELECT lease_fence FROM import_batch WHERE store_id=? AND import_batch_id=? AND lease_owner=?",
                    Long.class, storeId, batchId, owner);
            return Optional.of(new Claim(storeId, batchId, owner, fence));
        });
    }

    public boolean renew(Claim claim, int leaseSeconds) {
        Timestamp now = jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class);
        return jdbc.update("""
                UPDATE import_batch SET lease_until=?,updated_at=CURRENT_TIMESTAMP
                WHERE store_id=? AND import_batch_id=? AND status='PROCESSING' AND lease_owner=? AND lease_fence=?
                    AND lease_until>CURRENT_TIMESTAMP
                """, Timestamp.from(now.toInstant().plusSeconds(leaseSeconds)), claim.storeId(), claim.batchId(),
                claim.owner(), claim.fence()) == 1;
    }

    public void guarded(Claim claim, Runnable action) {
        transaction.executeWithoutResult(status -> {
            assertCurrent(claim);
            action.run();
            assertCurrent(claim);
        });
    }

    public void terminal(Claim claim, Runnable action) {
        transaction.executeWithoutResult(status -> {
            assertCurrent(claim);
            action.run();
        });
    }

    public void assertCurrent(Claim claim) {
        int current = jdbc.query("""
                SELECT import_batch_id FROM import_batch
                WHERE store_id=? AND import_batch_id=? AND status='PROCESSING' AND lease_owner=? AND lease_fence=?
                    AND lease_until>CURRENT_TIMESTAMP FOR UPDATE
                """, (rs, row) -> rs.getInt(1), claim.storeId(), claim.batchId(), claim.owner(), claim.fence()).size();
        if (current != 1) throw new IllegalStateException("Import batch lease is no longer active");
    }

    public void releaseForRetry(Claim claim) {
        jdbc.update("""
                UPDATE import_batch SET status='QUEUED',stage='QUEUED',lease_owner=NULL,lease_until=NULL,
                    updated_at=CURRENT_TIMESTAMP
                WHERE store_id=? AND import_batch_id=? AND status='PROCESSING' AND lease_owner=? AND lease_fence=?
                """, claim.storeId(), claim.batchId(), claim.owner(), claim.fence());
    }
}

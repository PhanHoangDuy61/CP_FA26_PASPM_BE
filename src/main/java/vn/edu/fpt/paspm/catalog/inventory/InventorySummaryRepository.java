package vn.edu.fpt.paspm.catalog.inventory;

import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import vn.edu.fpt.paspm.catalog.CatalogContracts.InventorySummary;

@Repository
public class InventorySummaryRepository {
    private final JdbcTemplate jdbc;

    public InventorySummaryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * DEC-20261124-01: expired = {@code expiry_date < today} whatever the stored status (EXPIRED wins over BLOCKED), so the
     * expired / blocked / sellable buckets never overlap and a batch expiring today is still sellable.
     * DEC-20261124-02: the push-day warning count uses the F02 rule (expiry-tracked product, no push day, not expired).
     */
    public InventorySummary summarize(int productId, LocalDate today, LocalDate expiringThrough) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(b.remaining_quantity), 0) AS total_quantity,
                       COALESCE(SUM(CASE WHEN b.expiry_date IS NOT NULL AND b.expiry_date < ?
                                         THEN b.remaining_quantity ELSE 0 END), 0) AS expired_quantity,
                       COALESCE(SUM(CASE WHEN b.status='BLOCKED' AND (b.expiry_date IS NULL OR b.expiry_date >= ?)
                                         THEN b.remaining_quantity ELSE 0 END), 0) AS blocked_quantity,
                       COALESCE(SUM(CASE WHEN b.status='ACTIVE' AND (b.expiry_date IS NULL OR b.expiry_date >= ?)
                                         THEN b.remaining_quantity ELSE 0 END), 0) AS sellable_quantity,
                       COALESCE(SUM(CASE WHEN b.status='ACTIVE' AND b.expiry_date >= ? AND b.expiry_date <= ?
                                         THEN b.remaining_quantity ELSE 0 END), 0) AS expiring_soon_quantity,
                       COALESCE(SUM(CASE WHEN p.requires_expiry = TRUE AND b.push_day IS NULL
                                              AND (b.expiry_date IS NULL OR b.expiry_date >= ?)
                                         THEN 1 ELSE 0 END), 0) AS batches_missing_push_day
                FROM inventory_batch b JOIN product p ON p.product_id = b.product_id
                WHERE b.product_id=?
                """, (rs, row) -> new InventorySummary(productId,
                rs.getLong("total_quantity"), rs.getLong("sellable_quantity"),
                rs.getLong("expired_quantity"), rs.getLong("blocked_quantity"),
                rs.getLong("expiring_soon_quantity"), rs.getLong("batches_missing_push_day")),
                today, today, today, today, expiringThrough, today, productId);
    }
}

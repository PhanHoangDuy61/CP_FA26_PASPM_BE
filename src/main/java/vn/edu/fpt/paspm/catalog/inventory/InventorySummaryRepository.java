package vn.edu.fpt.paspm.catalog.inventory;

import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import vn.edu.fpt.paspm.catalog.CatalogContracts.InventorySummary;

@Repository
public class InventorySummaryRepository {
    private final JdbcTemplate jdbc;

    public InventorySummaryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public InventorySummary summarize(int productId, LocalDate today, LocalDate expiringThrough) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(remaining_quantity), 0) AS total_quantity,
                       COALESCE(SUM(CASE WHEN status='BLOCKED' THEN remaining_quantity ELSE 0 END), 0) AS blocked_quantity,
                       COALESCE(SUM(CASE WHEN status='ACTIVE' AND expiry_date IS NOT NULL AND expiry_date < ?
                                         THEN remaining_quantity ELSE 0 END), 0) AS expired_quantity,
                       COALESCE(SUM(CASE WHEN status='ACTIVE' AND (expiry_date IS NULL OR expiry_date >= ?)
                                         THEN remaining_quantity ELSE 0 END), 0) AS sellable_quantity,
                       COALESCE(SUM(CASE WHEN status='ACTIVE' AND expiry_date >= ? AND expiry_date <= ?
                                         THEN remaining_quantity ELSE 0 END), 0) AS expiring_soon_quantity
                FROM inventory_batch WHERE product_id=?
                """, (rs, row) -> new InventorySummary(productId,
                rs.getLong("total_quantity"), rs.getLong("sellable_quantity"),
                rs.getLong("expired_quantity"), rs.getLong("blocked_quantity"),
                rs.getLong("expiring_soon_quantity")), today, today, today, expiringThrough, productId);
    }
}

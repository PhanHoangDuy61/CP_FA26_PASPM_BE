package vn.edu.fpt.paspm.catalog.inventory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import vn.edu.fpt.paspm.catalog.CatalogContracts.BatchView;

@Repository
public class InventoryBatchRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<BatchView> mapper = (rs, row) -> new BatchView(
            rs.getInt("batch_id"), rs.getInt("product_id"), rs.getString("batch_code"),
            rs.getTimestamp("received_at").toInstant(), rs.getObject("expiry_date", LocalDate.class),
            rs.getInt("initial_quantity"), rs.getInt("remaining_quantity"), rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());

    public InventoryBatchRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<BatchView> findForProduct(int storeId, int productId) {
        return jdbc.query("""
                SELECT b.* FROM inventory_batch b JOIN product p ON p.product_id=b.product_id
                WHERE p.store_id=? AND b.product_id=? ORDER BY b.received_at DESC, b.batch_id DESC
                """, mapper, storeId, productId);
    }

    public BatchView find(int storeId, int batchId) {
        return jdbc.query("""
                SELECT b.* FROM inventory_batch b JOIN product p ON p.product_id=b.product_id
                WHERE p.store_id=? AND b.batch_id=?
                """, mapper, storeId, batchId).stream().findFirst().orElse(null);
    }

    public BatchView findForUpdate(int storeId, int batchId) {
        return jdbc.query("""
                SELECT b.* FROM inventory_batch b JOIN product p ON p.product_id=b.product_id
                WHERE p.store_id=? AND b.batch_id=? FOR UPDATE
                """, mapper, storeId, batchId).stream().findFirst().orElse(null);
    }

    public BatchView create(int productId, String batchCode, Instant receivedAt,
                            LocalDate expiryDate, int initialQuantity) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO inventory_batch(product_id, batch_code, received_at, expiry_date,
                                                initial_quantity, remaining_quantity, status)
                    VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')
                    """, new String[] {"batch_id"});
            statement.setInt(1, productId); statement.setString(2, batchCode.trim());
            statement.setTimestamp(3, java.sql.Timestamp.from(receivedAt));
            if (expiryDate == null) statement.setNull(4, java.sql.Types.DATE); else statement.setObject(4, expiryDate);
            statement.setInt(5, initialQuantity); statement.setInt(6, initialQuantity);
            return statement;
        }, key);
        return jdbc.query("SELECT * FROM inventory_batch WHERE batch_id=?", mapper, key.getKey().intValue())
                .get(0);
    }

    public void update(int batchId, Integer remainingQuantity, String status) {
        jdbc.update("""
                UPDATE inventory_batch SET remaining_quantity=COALESCE(?, remaining_quantity),
                    status=COALESCE(?, status), updated_at=CURRENT_TIMESTAMP WHERE batch_id=?
                """, remainingQuantity, status, batchId);
    }
}

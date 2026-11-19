package vn.edu.fpt.paspm.catalog.inventory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import vn.edu.fpt.paspm.catalog.CatalogContracts.BatchView;

@Repository
public class InventoryBatchRepository {
    private static final DateTimeFormatter BATCH_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern GENERATED_BATCH_CODE = Pattern.compile("^BATCH-\\d{8}-(\\d{3,})$");
    private final JdbcTemplate jdbc;
    private final RowMapper<BatchView> mapper = (rs, row) -> new BatchView(
            rs.getInt("batch_id"), rs.getInt("product_id"), rs.getString("batch_code"),
            rs.getTimestamp("received_at").toInstant(), rs.getObject("expiry_date", LocalDate.class),
            rs.getInt("initial_quantity"), rs.getInt("remaining_quantity"), rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
            rs.getObject("push_day", LocalDate.class));

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

    public String generateBatchCode(int storeId, int productId, LocalDate receivedDate) {
        List<Integer> owned = jdbc.query("""
                SELECT product_id FROM product WHERE store_id=? AND product_id=? FOR UPDATE
                """, (rs, row) -> rs.getInt(1), storeId, productId);
        if (owned.isEmpty()) return null;

        List<String> existingCodes = jdbc.query("""
                SELECT batch_code FROM inventory_batch WHERE product_id=? FOR UPDATE
                """, (rs, row) -> rs.getString(1), productId);
        BigInteger sequence = BigInteger.ZERO;
        for (String code : existingCodes) {
            Matcher matcher = GENERATED_BATCH_CODE.matcher(code);
            if (matcher.matches()) sequence = sequence.max(new BigInteger(matcher.group(1)));
        }
        String date = receivedDate.format(BATCH_DATE);
        return "BATCH-" + date + "-" + String.format(Locale.ROOT, "%03d", sequence.add(BigInteger.ONE));
    }

    public BatchView create(int productId, String batchCode, Instant receivedAt,
                            LocalDate expiryDate, int initialQuantity) {
        return create(productId, batchCode, receivedAt, expiryDate, initialQuantity, null);
    }

    public BatchView create(int productId, String batchCode, Instant receivedAt,
                            LocalDate expiryDate, int initialQuantity, LocalDate pushDay) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO inventory_batch(product_id, batch_code, received_at, expiry_date,
                                                initial_quantity, remaining_quantity, status, push_day)
                    VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                    """, new String[] {"batch_id"});
            statement.setInt(1, productId); statement.setString(2, batchCode.trim());
            statement.setTimestamp(3, java.sql.Timestamp.from(receivedAt));
            if (expiryDate == null) statement.setNull(4, java.sql.Types.DATE); else statement.setObject(4, expiryDate);
            statement.setInt(5, initialQuantity); statement.setInt(6, initialQuantity);
            if (pushDay == null) statement.setNull(7, java.sql.Types.DATE); else statement.setObject(7, pushDay);
            return statement;
        }, key);
        return jdbc.query("SELECT * FROM inventory_batch WHERE batch_id=?", mapper, key.getKey().intValue())
                .get(0);
    }

    public void clearPushDay(int storeId, int batchId) {
        jdbc.update("""
                UPDATE inventory_batch SET push_day=NULL, updated_at=CURRENT_TIMESTAMP
                WHERE batch_id=? AND product_id IN (SELECT product_id FROM product WHERE store_id=?)
                AND push_day IS NOT NULL
                """, batchId, storeId);
    }

    public void clearExpiryDate(int storeId, int batchId) {
        jdbc.update("""
                UPDATE inventory_batch SET expiry_date=NULL, updated_at=CURRENT_TIMESTAMP
                WHERE batch_id=? AND product_id IN (SELECT product_id FROM product WHERE store_id=?)
                AND expiry_date IS NOT NULL
                """, batchId, storeId);
    }

    public void update(int batchId, Integer remainingQuantity, String status) { update(batchId, remainingQuantity, status, null); }

    public void update(int batchId, Integer remainingQuantity, String status, LocalDate pushDay) {
        update(batchId, remainingQuantity, status, pushDay, null);
    }

    public void update(int batchId, Integer remainingQuantity, String status, LocalDate pushDay, LocalDate expiryDate) {
        jdbc.update("""
                UPDATE inventory_batch SET remaining_quantity=COALESCE(?, remaining_quantity),
                    status=COALESCE(?, status), push_day=COALESCE(?, push_day),
                    expiry_date=COALESCE(?, expiry_date), updated_at=CURRENT_TIMESTAMP WHERE batch_id=?
                """, remainingQuantity, status, pushDay, expiryDate, batchId);
    }
}

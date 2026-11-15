package vn.edu.fpt.paspm.utility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.paspm.common.ApiException;

@Repository
public class UtilityProductRepository {
    public record Product(int id,boolean requiresExpiry,BigDecimal stockScore,BigDecimal seasonalScore,int expiryPushDays,String status) {}
    private static final String COLUMNS="product_id,requires_expiry,stock_priority_score,seasonal_priority_score,expiry_push_days,status";
    private final RowMapper<Product> mapper=(rs,row)->new Product(rs.getInt(1),rs.getBoolean(2),rs.getBigDecimal(3),rs.getBigDecimal(4),rs.getInt(5),rs.getString(6));
    private final JdbcTemplate jdbc;
    public UtilityProductRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Product product(int storeId,int id) {
        return jdbc.query("SELECT "+COLUMNS+" FROM product WHERE store_id=? AND product_id=?",mapper,storeId,id)
            .stream().findFirst().orElseThrow(()->new ApiException(404,"Product not found"));
    }
    public List<Product> pushCandidates(int storeId,Integer productId) {
        // W2: push days live on batches; Product.expiryPushDays is ignored (P5).
        String sql="SELECT "+COLUMNS+" FROM product WHERE store_id=? AND status='ACTIVE' AND requires_expiry=TRUE";
        return productId==null?jdbc.query(sql+" ORDER BY product_id",mapper,storeId)
            :jdbc.query(sql+" AND product_id=?",mapper,storeId,productId);
    }
    /** Batches for the W2 FEFO simulation, including the Store Admin's push day. */
    public List<ExpiryRiskCalculator.Batch> riskBatches(int storeId,int productId) {
        return jdbc.query("SELECT b.batch_id,b.push_day,b.expiry_date,b.remaining_quantity,b.status FROM inventory_batch b JOIN product p ON p.product_id=b.product_id WHERE p.store_id=? AND b.product_id=? ORDER BY b.batch_id",
            (rs,row)->new ExpiryRiskCalculator.Batch(rs.getInt(1),rs.getObject(2,LocalDate.class),rs.getObject(3,LocalDate.class),rs.getLong(4),rs.getString(5)),storeId,productId);
    }
    /** v(i) numerator for one product: units of COMPLETED, F03-eligible transactions in [fromInclusive, toExclusive). */
    public long unitsSold(int storeId,int productId,java.time.Instant fromInclusive,java.time.Instant toExclusive) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(i.quantity),0) FROM sales_transaction t JOIN transaction_item i ON i.store_id=t.store_id AND i.transaction_id=t.transaction_id WHERE t.store_id=? AND i.product_id=? AND t.status='COMPLETED' AND t.f03_eligible=TRUE AND t.occurred_at>=? AND t.occurred_at<?",
            Long.class,storeId,productId,java.sql.Timestamp.from(fromInclusive),java.sql.Timestamp.from(toExclusive));
    }
    public List<ExpiryScoreCalculator.Batch> batches(int storeId,int productId) {
        return jdbc.query("SELECT b.batch_id,b.expiry_date,b.remaining_quantity,b.status,b.received_at FROM inventory_batch b JOIN product p ON p.product_id=b.product_id WHERE p.store_id=? AND b.product_id=? ORDER BY b.expiry_date,b.received_at,b.batch_id",
            (rs,row)->new ExpiryScoreCalculator.Batch(rs.getInt(1),rs.getObject(2,LocalDate.class),rs.getLong(3),rs.getString(4),
                java.util.Objects.requireNonNull(rs.getObject(5,java.time.LocalDateTime.class),"inventory_batch.received_at is required")),storeId,productId);
    }
}

package vn.edu.fpt.paspm.utility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.paspm.common.ApiException;

@Repository
public class UtilityProductRepository {
    public record Product(int id,boolean requiresExpiry,BigDecimal stockScore,BigDecimal seasonalScore) {}
    private final JdbcTemplate jdbc;
    public UtilityProductRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Product product(int storeId,int id) {
        return jdbc.query("SELECT product_id,requires_expiry,stock_priority_score,seasonal_priority_score FROM product WHERE store_id=? AND product_id=?",
            (rs,row)->new Product(rs.getInt(1),rs.getBoolean(2),rs.getBigDecimal(3),rs.getBigDecimal(4)),storeId,id).stream().findFirst().orElseThrow(()->new ApiException(404,"Product not found"));
    }
    public List<ExpiryScoreCalculator.Batch> batches(int storeId,int productId) {
        return jdbc.query("SELECT b.expiry_date,b.remaining_quantity,b.status FROM inventory_batch b JOIN product p ON p.product_id=b.product_id WHERE p.store_id=? AND b.product_id=? ORDER BY b.batch_id",
            (rs,row)->new ExpiryScoreCalculator.Batch(rs.getObject(1,LocalDate.class),rs.getLong(2),rs.getString(3)),storeId,productId);
    }
}

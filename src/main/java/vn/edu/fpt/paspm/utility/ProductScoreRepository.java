package vn.edu.fpt.paspm.utility;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.paspm.utility.UtilityContracts.*;

@Repository
public class ProductScoreRepository {
    private final JdbcTemplate jdbc;
    public ProductScoreRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean update(int store,int id,ScorePatch request) {
        return jdbc.update("UPDATE product SET stock_priority_score=COALESCE(?,stock_priority_score),seasonal_priority_score=COALESCE(?,seasonal_priority_score) WHERE store_id=? AND product_id=?",request.getStockPriorityScore(),request.getSeasonalPriorityScore(),store,id)==1;
    }
}

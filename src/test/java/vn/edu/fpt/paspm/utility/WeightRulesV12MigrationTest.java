package vn.edu.fpt.paspm.utility;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class WeightRulesV12MigrationTest {
    @Test void upgradesPopulatedV7WithoutRewritingHistoricalSnapshotsOrBackfillingStoreThreshold() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:v12upgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(source).target("7").load().migrate();
        var jdbc=new JdbcTemplate(source);
        jdbc.update("INSERT INTO store(store_id,name,utility_config) VALUES (1,'Old Store',?)","{\"expiryWindowDays\":30}");
        jdbc.update("INSERT INTO app_user(user_id,store_id,role_id,email,name,status) VALUES (1,1,(SELECT role_id FROM role WHERE code='MERCHANDISER'),'migration@example.test','Old User','ACTIVE')");
        jdbc.update("INSERT INTO product(product_id,store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (1,1,'MILK','Milk','bottle',TRUE,10000,6000,'ACTIVE')");
        jdbc.update("INSERT INTO sales_transaction(transaction_id,store_id,source_system,source_order_id,occurred_at,status,f03_eligible,total_amount,content_sha256) VALUES (1,1,'TEST','OLD-1',CURRENT_TIMESTAMP,'COMPLETED',TRUE,30000,?)","a".repeat(64));
        jdbc.update("INSERT INTO utility_snapshot(snapshot_id,store_id,created_by,range_start,range_end,as_of_date,config_snapshot,source_hash,matrix_hash,total_weighted_utility,rounding_scale,status) VALUES (1,1,1,DATE '2026-10-01',DATE '2026-10-31',DATE '2026-10-19',?,?,?,16220,6,'READY')",
            "{\"config\":{\"scoringVersion\":\"F03_LINEAR_V1\",\"expiryWindowDays\":30}}","b".repeat(64),"c".repeat(64));
        jdbc.update("INSERT INTO utility_matrix_item(snapshot_id,transaction_id,product_id,quantity,base_utility,stock_score,expiry_score,seasonal_score,expiry_status,weight_value,weighted_utility) VALUES (1,1,1,3,12000,.8,.166666666666666667,.9,'AVAILABLE',1.351666666666666667,16220)");
        var config=jdbc.queryForObject("SELECT config_snapshot FROM utility_snapshot WHERE snapshot_id=1",String.class);
        var storeConfig=jdbc.queryForObject("SELECT utility_config FROM store WHERE store_id=1",String.class);
        Flyway.configure().dataSource(source).load().migrate();
        assertEquals(0,jdbc.queryForObject("SELECT expiry_push_days FROM product WHERE product_id=1",Integer.class));
        assertEquals(config,jdbc.queryForObject("SELECT config_snapshot FROM utility_snapshot WHERE snapshot_id=1",String.class));
        assertEquals(storeConfig,jdbc.queryForObject("SELECT utility_config FROM store WHERE store_id=1",String.class));
        assertEquals("c".repeat(64),jdbc.queryForObject("SELECT matrix_hash FROM utility_snapshot WHERE snapshot_id=1",String.class));
        assertEquals(0,new BigDecimal("16220").compareTo(jdbc.queryForObject("SELECT weighted_utility FROM utility_matrix_item WHERE snapshot_id=1",BigDecimal.class)));
        assertEquals(0,new BigDecimal(".166666666666666667").compareTo(jdbc.queryForObject("SELECT expiry_score FROM utility_matrix_item WHERE snapshot_id=1",BigDecimal.class)));
        assertNull(jdbc.queryForObject("SELECT expiry_evidence FROM utility_matrix_item WHERE snapshot_id=1",String.class));
        assertNull(jdbc.queryForObject("SELECT validation_warnings FROM utility_snapshot WHERE snapshot_id=1",String.class));
    }
}

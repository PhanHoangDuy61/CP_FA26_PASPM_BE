package vn.edu.fpt.paspm.recommendation;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;

@EnabledIfSystemProperty(named="paspm.test.mysql",matches="true")
class RecommendationMigrationTest {
    @Test void freshMySqlMigratesAllElevenVersions() {
        var db=MySqlTestDatabase.create();var flyway=Flyway.configure().dataSource(db.url(),db.user(),db.password()).load();
        assertEquals(12,flyway.migrate().migrationsExecuted);flyway.validate();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(db.url(),db.user(),db.password()));
        for(String table:List.of("recommendation","recommendation_item","recommendation_source","recommendation_review_history","recommendation_rule_config"))
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
    }
    @Test void mysqlV9UpgradePreservesHistoricalSnapshotAndStoreConfiguration() {
        var db=MySqlTestDatabase.create();var old=Flyway.configure().dataSource(db.url(),db.user(),db.password()).target("9").load();assertEquals(9,old.migrate().migrationsExecuted);
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(db.url(),db.user(),db.password()));
        jdbc.update("INSERT INTO store(name,utility_config) VALUES ('F06 historical upgrade','{\"version\":7,\"legacy\":true}')");
        int store=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        jdbc.update("INSERT INTO app_user(store_id,role_id,email,name,status) VALUES (?,(SELECT role_id FROM role WHERE code='MERCHANDISER'),'migration@example.test','Migration','ACTIVE')",store);
        int user=jdbc.queryForObject("SELECT MAX(user_id) FROM app_user",Integer.class);
        jdbc.update("INSERT INTO utility_snapshot(store_id,created_by,range_start,range_end,as_of_date,config_snapshot,source_hash,matrix_hash,total_weighted_utility,rounding_scale,status) VALUES (?,?,CURRENT_DATE,CURRENT_DATE,CURRENT_DATE,'{\"legacy\":true}',?,?,123,6,'READY')",store,user,"a".repeat(64),"b".repeat(64));
        String configuration=jdbc.queryForObject("SELECT utility_config FROM store WHERE store_id=?",String.class,store);
        String snapshot=jdbc.queryForObject("SELECT config_snapshot FROM utility_snapshot WHERE store_id=?",String.class,store);
        var current=Flyway.configure().dataSource(db.url(),db.user(),db.password()).load();assertEquals(3,current.migrate().migrationsExecuted);current.validate();
        assertEquals(configuration,jdbc.queryForObject("SELECT utility_config FROM store WHERE store_id=?",String.class,store));
        assertEquals(snapshot,jdbc.queryForObject("SELECT config_snapshot FROM utility_snapshot WHERE store_id=?",String.class,store));
        assertEquals(123,jdbc.queryForObject("SELECT total_weighted_utility FROM utility_snapshot WHERE store_id=?",Integer.class,store));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM recommendation",Integer.class));
    }
}

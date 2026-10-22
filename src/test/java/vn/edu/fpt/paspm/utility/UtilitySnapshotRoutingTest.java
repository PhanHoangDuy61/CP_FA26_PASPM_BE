package vn.edu.fpt.paspm.utility;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import vn.edu.fpt.paspm.common.ApiException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class UtilitySnapshotRoutingTest {
 JdbcTemplate jdbc; UtilitySnapshotRepository repository;
 @BeforeEach void setup(){
  jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
  jdbc.execute("CREATE TABLE utility_snapshot(snapshot_id INT,store_id INT,total_weighted_utility DECIMAL(20,6),config_snapshot VARCHAR)");
  jdbc.execute("CREATE TABLE utility_snapshot_storage(snapshot_id INT,store_id INT,storage_kind VARCHAR,source_namespace VARCHAR)");
  jdbc.execute("CREATE TABLE product(product_id INT,store_id INT)");jdbc.execute("CREATE TABLE sales_transaction(transaction_id INT,store_id INT)");
  String columns="(snapshot_id INT,transaction_id INT,product_id INT,quantity INT,base_utility DECIMAL,stock_score DECIMAL,expiry_score DECIMAL,seasonal_score DECIMAL,expiry_status VARCHAR,weight_value DECIMAL,weighted_utility DECIMAL)";
  jdbc.execute("CREATE TABLE utility_matrix_item_legacy_v5"+columns);jdbc.execute("CREATE TABLE utility_matrix_item"+columns);jdbc.execute("ALTER TABLE utility_matrix_item ADD expiry_evidence VARCHAR");
  jdbc.update("INSERT INTO utility_snapshot VALUES(1,1,2,'{}'),(2,2,3,'{}')");jdbc.update("INSERT INTO product VALUES(11,1),(22,2)");
  jdbc.update("INSERT INTO utility_snapshot_storage VALUES(1,1,'LEGACY_V5_ARCHIVE','V5:TEST'),(2,2,'LEGACY_V5_ARCHIVE','V5:TEST')");
  jdbc.update("INSERT INTO utility_matrix_item_legacy_v5 VALUES(1,7,11,1,2,NULL,NULL,NULL,'NOT_APPLICABLE',1,2),(2,7,22,1,3,NULL,NULL,NULL,'NOT_APPLICABLE',1,3)");
  repository=new UtilitySnapshotRepository(jdbc,new UtilitySettingsRepository(jdbc,JsonMapper.builder().findAndAddModules().build()));
 }
 @Test void legacyCountAndPagesAreStoreScopedAndHaveNoInventedExpiryEvidence(){assertEquals(1,repository.countItems(1,1));var rows=repository.page(1,1,0,1);assertEquals(11,rows.get(0).productId());assertNull(rows.get(0).expiryEvidence());assertTrue(repository.page(1,1,1,1).isEmpty());assertEquals(404,assertThrows(ApiException.class,()->repository.countItems(2,1)).status());}
 @Test void archiveWritesAndAssociationMembershipAreRejectedBeforeAnyMutation(){assertEquals(409,assertThrows(ApiException.class,()->repository.saveItems(1,List.of())).status());jdbc.update("INSERT INTO sales_transaction VALUES(7,1)");assertEquals(409,assertThrows(ApiException.class,()->repository.assertAssociationEvidence(1,1)).status());}
 @Test void mixedRouteAndWrongStoreRegistryFailClosed(){jdbc.update("INSERT INTO utility_matrix_item(snapshot_id,transaction_id,product_id,weighted_utility) VALUES(1,7,11,2)");assertEquals(409,assertThrows(ApiException.class,()->repository.page(1,1,0,10)).status());jdbc.update("DELETE FROM utility_matrix_item");jdbc.update("UPDATE utility_snapshot_storage SET store_id=2 WHERE snapshot_id=1");assertThrows(ApiException.class,()->repository.countItems(1,1));}
 @Test void missingLegacyRegistryCannotBecomeModern(){jdbc.update("DELETE FROM utility_snapshot_storage");assertThrows(ApiException.class,()->repository.page(1,1,0,10));}
 @Test void unregisteredEmptyHistoricalSnapshotAllowsOnlyReadOperations(){
  jdbc.update("DELETE FROM utility_snapshot_storage");jdbc.update("DELETE FROM utility_matrix_item_legacy_v5");
  jdbc.update("UPDATE utility_snapshot SET total_weighted_utility=0");
  assertEquals(0,repository.countItems(1,1));assertTrue(repository.page(1,1,0,10).isEmpty());
  assertEquals(409,assertThrows(ApiException.class,()->repository.saveItems(1,List.of())).status());
  assertEquals(409,assertThrows(ApiException.class,()->repository.assertAssociationEvidence(1,1)).status());
  jdbc.update("UPDATE utility_snapshot SET total_weighted_utility=2 WHERE snapshot_id=1");
  assertEquals(409,assertThrows(ApiException.class,()->repository.countItems(1,1)).status());
 }
 @Test void absentRegistryRequiresActualFrozenSourceAndSalesOwnership(){
  jdbc.update("DELETE FROM utility_snapshot_storage");jdbc.update("DELETE FROM utility_matrix_item_legacy_v5");
  jdbc.update("INSERT INTO utility_matrix_item(snapshot_id,transaction_id,product_id,weighted_utility) VALUES(1,7,11,2)");
  jdbc.update("INSERT INTO sales_transaction VALUES(7,1)");
  assertEquals(409,assertThrows(ApiException.class,()->repository.countItems(1,1)).status());
  jdbc.update("UPDATE utility_snapshot SET config_snapshot=? WHERE snapshot_id=1","{\"source\":\"F04_DATABASE_V1\"}");
  assertEquals(1,repository.countItems(1,1));
  repository.saveItems(1,List.of());assertEquals("F04_LIVE",jdbc.queryForObject("SELECT storage_kind FROM utility_snapshot_storage WHERE snapshot_id=1",String.class));
  jdbc.update("UPDATE sales_transaction SET store_id=2");assertEquals(409,assertThrows(ApiException.class,()->repository.countItems(1,1)).status());
 }

}

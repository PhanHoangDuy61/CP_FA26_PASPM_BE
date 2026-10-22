package vn.edu.fpt.paspm.deployment;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import vn.edu.fpt.paspm.deployment.legacy.LegacyRecoverySchema;
import static org.junit.jupiter.api.Assertions.*;
class LegacyRecoverySchemaTest {
 @Test @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="paspm.test.mysql",matches="true")
 void mysqlGuardsAndFreshSchema() throws Exception {
  var db=MySqlTestDatabase.create();
  try(var c=DriverManager.getConnection(db.url(),db.user(),db.password())) { verify(c); }
 }

 @Test void freshAndRepeatedInstallationFreezesArchiveAndLegacyHeaders() throws Exception {
  try(Connection c=DriverManager.getConnection("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE","sa","")) {
   verify(c);
  }
 }
 private void verify(Connection c) throws Exception {
   var s=c.createStatement();
   s.execute("CREATE TABLE store(store_id INT PRIMARY KEY)");
   s.execute("CREATE TABLE product(product_id INT PRIMARY KEY)");
   s.execute("CREATE TABLE utility_snapshot(snapshot_id INT PRIMARY KEY,store_id INT NOT NULL)");
   s.execute("INSERT INTO store VALUES(1),(2)"); s.execute("INSERT INTO product VALUES(1)");
   s.execute("INSERT INTO utility_snapshot VALUES(1,1),(2,1)");
   LegacyRecoverySchema.createStage(c);
   s.execute("INSERT INTO utility_matrix_item_v5_empty_stage VALUES(1,1,1,1,1,1,NULL,NULL,NULL,'AVAILABLE',1,1)");
   s.execute("ALTER TABLE utility_matrix_item_v5_empty_stage RENAME TO utility_matrix_item_legacy_v5");
   LegacyRecoverySchema.ensureArchiveAndRegistry(c);
   LegacyRecoverySchema.ensureArchiveAndRegistry(c);
   assertThrows(SQLException.class,()->s.execute("INSERT INTO utility_matrix_item_legacy_v5 VALUES(2,1,2,1,1,1,NULL,NULL,NULL,'AVAILABLE',1,1)"));
   assertThrows(SQLException.class,()->s.execute("UPDATE utility_matrix_item_legacy_v5 SET quantity=2 WHERE matrix_item_id=1"));
   assertThrows(SQLException.class,()->s.execute("DELETE FROM utility_matrix_item_legacy_v5 WHERE matrix_item_id=1"));
   assertThrows(SQLException.class,()->s.execute("INSERT INTO utility_snapshot_storage VALUES(2,2,'F04_LIVE','source')"));
   s.execute("INSERT INTO utility_snapshot_storage VALUES(1,1,'LEGACY_V5_ARCHIVE','source')");
   assertThrows(SQLException.class,()->s.execute("UPDATE utility_snapshot SET store_id=1 WHERE snapshot_id=1"));
   assertThrows(SQLException.class,()->s.execute("DELETE FROM utility_snapshot WHERE snapshot_id=1"));
   assertThrows(SQLException.class,()->s.execute("UPDATE utility_snapshot_storage SET storage_kind='F04_LIVE' WHERE snapshot_id=1"));
   assertThrows(SQLException.class,()->s.execute("DELETE FROM utility_snapshot_storage WHERE snapshot_id=1"));
   assertEquals(1,s.executeUpdate("UPDATE utility_snapshot SET store_id=1 WHERE snapshot_id=2"));
 }
}

package vn.edu.fpt.paspm.deployment;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import java.sql.*;
import java.util.UUID;
import vn.edu.fpt.paspm.deployment.legacy.LegacyDataset;
import static org.junit.jupiter.api.Assertions.*;
class LegacyPreflightTest {
 @Test void registeredCallbackRefusesPartialV7BeforeCreatingMoreObjects() throws Exception {
  String url="jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
  Flyway.configure().dataSource(url,"sa","").target("5").load().migrate();
  try(Connection c=DriverManager.getConnection(url,"sa","");Statement st=c.createStatement()){st.execute("CREATE TABLE api_connection (id INT)");}
  var callback=new vn.edu.fpt.paspm.deployment.legacy.LegacyRecoveryCallback(false,null,null,null,null);
  assertThrows(Exception.class,()->Flyway.configure().dataSource(url,"sa","").callbacks(callback).target("7").load().migrate());
  try(Connection c=DriverManager.getConnection(url,"sa","");var rs=c.getMetaData().getTables(null,null,"sales_transaction",null)){assertFalse(rs.next());}
 }

 @Test void rejectsOrphanSnapshotEvenWhenReferentialChecksDisabled() throws Exception {
  String url="jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
  Flyway.configure().dataSource(url,"sa","").target("5").load().migrate();
  try(Connection c=DriverManager.getConnection(url,"sa",""); Statement s=c.createStatement()) {
   s.execute("SET REFERENTIAL_INTEGRITY FALSE");
   s.execute("INSERT INTO utility_snapshot VALUES (1,91,92,'2020-01-01','2020-01-01','2020-01-01','{}',REPEAT('a',64),REPEAT('b',64),0,6,'READY',CURRENT_TIMESTAMP)");
   assertThrows(IllegalStateException.class,()->LegacyDataset.inspect(c,"utility_matrix_item"));
  }
 }
}

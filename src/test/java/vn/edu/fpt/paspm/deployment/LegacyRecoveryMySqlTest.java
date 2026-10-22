package vn.edu.fpt.paspm.deployment;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.flywaydb.core.Flyway;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import vn.edu.fpt.paspm.deployment.legacy.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="paspm.test.mysql",matches="true")
class LegacyRecoveryMySqlTest {
 @TempDir Path dir;
 record Fixture(MySqlTestDatabase target,MySqlTestDatabase restored,Path dump,Path manifest,LegacyDataset before){}
 @Test void springBootDiscoversGuardAndRefusesWithoutOptInBeforeV7Ddl()throws Exception {
  var db=MySqlTestDatabase.create();at5(db);seed(db);
  new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,FlywayAutoConfiguration.class))
   .withUserConfiguration(LegacyRecoveryConfiguration.class).withPropertyValues("spring.datasource.url="+db.url(),"spring.datasource.username="+db.user(),"spring.datasource.password="+db.password())
   .run(context->{assertNotNull(context.getStartupFailure());assertTrue(rootMessages(context.getStartupFailure()).contains("explicit opt-in"));});
  try(var c=connect(db)){assertFalse(LegacyRecoveryCallback.exists(c,"api_connection"));assertFalse(LegacyRecoveryCallback.exists(c,"utility_legacy_recovery_journal"));assertEquals(2,LegacyDataset.count(c,"utility_matrix_item"));}
 }
 @Test void realDumpRestoreMigrationAndEveryJournalBoundaryPreserveBothStores()throws Exception {
  for(String stop:List.of("NONE","PREPARED","STAGED","AFTER_RENAME","COMPLETE")) {
   Fixture f=fixture();
   var interrupt=new LegacyRecoveryCallback(true,f.manifest.toString(),f.restored.url(),f.restored.user(),f.restored.password()) {protected void checkpoint(String phase){if(phase.equals(stop))throw new IllegalStateException("Simulated process interruption at "+phase);}};
   if(!stop.equals("NONE")){assertThrows(Exception.class,()->latest(f.target,interrupt).migrate());try(var c=connect(f.target)){assertFalse(LegacyRecoveryCallback.exists(c,"api_connection"));}}
   var result=latest(f.target,callback(f)).migrate();assertTrue(result.success);
   latest(f.target,callback(f)).validate();
   try(var c=connect(f.target);var s=c.createStatement()){
    assertEquals(f.before,LegacyDataset.inspect(c,"utility_matrix_item_legacy_v5"));
    assertThrows(SQLException.class,()->s.executeUpdate("UPDATE product SET store_id=2 WHERE product_id=1"));
    s.executeUpdate("UPDATE app_user SET store_id=2 WHERE user_id=1");
    assertEquals(f.before,LegacyDataset.inspect(c,"utility_matrix_item_legacy_v5"));assertEquals(0,LegacyDataset.count(c,"sales_transaction"));assertEquals(0,LegacyDataset.count(c,"utility_matrix_item"));assertEquals(2,LegacyDataset.count(c,"utility_snapshot_storage"));
    assertThrows(SQLException.class,()->s.executeUpdate("UPDATE utility_matrix_item_legacy_v5 SET quantity=2"));
    assertThrows(SQLException.class,()->s.executeUpdate("DELETE FROM utility_snapshot WHERE snapshot_id=1"));
    assertThrows(SQLException.class,()->s.executeUpdate("UPDATE utility_snapshot_storage SET storage_kind='F04_LIVE'"));
   }
  }
 }
 @Test void partialV7FailureIsLeftIntactWhileCleanVerifiedRestoreCompletesRecovery()throws Exception {
  Fixture f=fixture();
  Exception original=assertThrows(Exception.class,()->Flyway.configure().dataSource(f.target.url(),f.target.user(),f.target.password()).load().migrate());
  assertTrue(sqlCause(original,1452,"fk_utility_matrix_transaction"),rootMessages(original));
  try(var c=connect(f.target)){
   assertEquals(f.before,LegacyDataset.inspect(c,"utility_matrix_item"));
   assertTrue(LegacyRecoveryCallback.exists(c,"api_connection"));
   assertTrue(LegacyRecoveryCallback.exists(c,"sales_transaction"));
   assertFalse(LegacyRecoveryCallback.exists(c,"utility_legacy_recovery_journal"));
   assertEquals(0,LegacyDataset.count(c,"sales_transaction"));
   assertEquals(1,failedV7Rows(c));
  }

  assertThrows(Exception.class,()->latest(f.target,callback(f)).migrate());
  try(var c=connect(f.target)){
   assertEquals(f.before,LegacyDataset.inspect(c,"utility_matrix_item"));
   assertTrue(LegacyRecoveryCallback.exists(c,"api_connection"));
   assertTrue(LegacyRecoveryCallback.exists(c,"sales_transaction"));
   assertFalse(LegacyRecoveryCallback.exists(c,"utility_legacy_recovery_journal"));
   assertEquals(0,LegacyDataset.count(c,"sales_transaction"));
   assertEquals(1,failedV7Rows(c));
  }

  var verification=restrictedRestore();
  Path cleanManifest=dir.resolve(UUID.randomUUID()+".properties");
  try(var target=connect(f.restored);var emptyVerification=connect(verification)){
   assertEquals(f.before,LegacyDataset.inspect(target,"utility_matrix_item"));
   LegacyBackupVerifier.generate(target,emptyVerification,f.dump,"approved-clean-restore-test",cleanManifest,
    new LegacyBackupVerifier.RestoreCredentials(verification.user(),verification.password()));
  }
  var cleanCallback=new LegacyRecoveryCallback(true,cleanManifest.toString(),verification.url(),verification.user(),verification.password());
  var cleanTarget=new MySqlTestDatabase(f.restored.url(),f.target.user(),f.target.password());
  assertTrue(latest(cleanTarget,cleanCallback).migrate().success);
  latest(cleanTarget,cleanCallback).validate();
  try(var c=connect(f.restored)){
   assertEquals(f.before,LegacyDataset.inspect(c,"utility_matrix_item_legacy_v5"));
   assertEquals(0,LegacyDataset.count(c,"utility_matrix_item"));
   assertEquals(0,LegacyDataset.count(c,"sales_transaction"));
  }
  try(var c=connect(f.target)){
   assertEquals(f.before,LegacyDataset.inspect(c,"utility_matrix_item"));
   assertTrue(LegacyRecoveryCallback.exists(c,"api_connection"));
   assertTrue(LegacyRecoveryCallback.exists(c,"sales_transaction"));
   assertFalse(LegacyRecoveryCallback.exists(c,"utility_legacy_recovery_journal"));
   assertEquals(0,LegacyDataset.count(c,"sales_transaction"));
   assertEquals(1,failedV7Rows(c));
  }
 }
 @Test void dumpTamperAndRestoreTamperCannotAuthorizeTransition()throws Exception {
  Fixture f=fixture();Files.writeString(f.dump,"--tampered",StandardOpenOption.APPEND);
  assertThrows(Exception.class,()->latest(f.target,callback(f)).migrate());
  try(var c=connect(f.target)){assertFalse(LegacyRecoveryCallback.exists(c,"utility_legacy_recovery_journal"));assertEquals(f.before,LegacyDataset.inspect(c,"utility_matrix_item"));}
  Fixture g=fixture();try(var c=connect(g.restored);var s=c.createStatement()){s.executeUpdate("UPDATE utility_snapshot SET matrix_hash=REPEAT('c',64)");}
  assertThrows(Exception.class,()->latest(g.target,callback(g)).migrate());
  try(var c=connect(g.target)){assertFalse(LegacyRecoveryCallback.exists(c,"utility_legacy_recovery_journal"));}
 }
 @Test void invalidOwnershipAndTotalsFailClosedEvenWithForeignKeysDisabled()throws Exception {
  for(String mutation:List.of("UPDATE utility_matrix_item SET product_id=999 WHERE matrix_item_id=1", "UPDATE utility_matrix_item SET product_id=2 WHERE matrix_item_id=1", "UPDATE utility_matrix_item SET snapshot_id=999 WHERE matrix_item_id=1", "UPDATE utility_snapshot SET created_by=999 WHERE snapshot_id=1", "DELETE FROM store WHERE store_id=1", "UPDATE utility_snapshot SET total_weighted_utility=99 WHERE snapshot_id=1")) {
   Fixture f=fixture();try(var c=connect(f.target);var st=c.createStatement()){st.execute("SET FOREIGN_KEY_CHECKS=0");st.executeUpdate(mutation);st.execute("SET FOREIGN_KEY_CHECKS=1");}
   assertThrows(Exception.class,()->latest(f.target,callback(f)).migrate(),mutation);
   try(var c=connect(f.target)){assertFalse(LegacyRecoveryCallback.exists(c,"utility_legacy_recovery_journal"));assertFalse(LegacyRecoveryCallback.exists(c,"api_connection"));assertEquals(2,LegacyDataset.count(c,"utility_matrix_item"));}
  }
 }
 @Test void changedStagedConstraintsAndUnknownStageAreRefused()throws Exception {
  Fixture f=fixture();
  var stop=new LegacyRecoveryCallback(true,f.manifest.toString(),f.restored.url(),f.restored.user(),f.restored.password()){protected void checkpoint(String phase){if(phase.equals("STAGED"))throw new IllegalStateException("stop");}};
  assertThrows(Exception.class,()->latest(f.target,stop).migrate());
  try(var c=connect(f.target);var st=c.createStatement();var r=st.executeQuery("SELECT constraint_name FROM information_schema.table_constraints WHERE table_schema=DATABASE() AND table_name='utility_matrix_item_f04_stage' AND constraint_type='CHECK' LIMIT 1")){assertTrue(r.next());try(var alter=c.createStatement()){alter.execute("ALTER TABLE utility_matrix_item_f04_stage DROP CHECK `"+r.getString(1)+"`");}}
  assertThrows(Exception.class,()->latest(f.target,callback(f)).migrate());
  try(var c=connect(f.target)){assertFalse(LegacyRecoveryCallback.exists(c,"utility_matrix_item_legacy_v5"));assertEquals(f.before,LegacyDataset.inspect(c,"utility_matrix_item"));}
  Fixture g=fixture();try(var c=connect(g.target);var st=c.createStatement()){st.execute("CREATE TABLE utility_matrix_item_f04_stage LIKE utility_matrix_item");}
  assertThrows(Exception.class,()->latest(g.target,callback(g)).migrate());
  try(var c=connect(g.target)){assertFalse(LegacyRecoveryCallback.exists(c,"utility_legacy_recovery_journal"));}
 }
 @Test void modernCrossStoreInsertIsRejectedAfterV11()throws Exception {
  var db=MySqlTestDatabase.create();at5(db);seed(db);
  try(var c=connect(db);var st=c.createStatement()){st.executeUpdate("DELETE FROM utility_matrix_item");st.executeUpdate("UPDATE utility_snapshot SET total_weighted_utility=0");}
  latest(db,new LegacyRecoveryCallback(false,null,null,null,null)).migrate();
  try(var c=connect(db);var st=c.createStatement()) {
   st.executeUpdate("INSERT INTO sales_transaction(transaction_id,store_id,source_system,source_order_id,occurred_at,status,total_amount,content_sha256) VALUES(77,2,'SYNTHETIC_TEST','A',CURRENT_TIMESTAMP,'COMPLETED',1,REPEAT('a',64))");
   assertThrows(SQLException.class,()->st.executeUpdate("INSERT INTO utility_matrix_item(snapshot_id,transaction_id,product_id,quantity,base_utility,expiry_status,weight_value,weighted_utility) VALUES(1,77,1,1,1,'NOT_APPLICABLE',1,1)"));
  }
 }
 @Test void freshAndEmptyV5AndAlreadyV10AreAdditive()throws Exception {
  for(String starting:List.of("0","5","10")) {
   var db=MySqlTestDatabase.create();if(!starting.equals("0"))Flyway.configure().dataSource(db.url(),db.user(),db.password()).target(starting).load().migrate();
   latest(db,new LegacyRecoveryCallback(false,null,null,null,null)).migrate();
   try(var c=connect(db)){assertEquals(0,LegacyDataset.count(c,"utility_matrix_item_legacy_v5"));assertEquals(0,LegacyDataset.count(c,"utility_snapshot_storage"));assertFalse(LegacyRecoveryCallback.exists(c,"utility_legacy_recovery_journal"));}
   assertEquals(0,latest(db,new LegacyRecoveryCallback(false,null,null,null,null)).migrate().migrationsExecuted);
  }
 }
 @Test void matchingCopyCannotProveAnUnrelatedDump()throws Exception {
  Fixture f=fixture();Path unrelated=Files.createTempFile(dir,"unrelated-",".sql");
  try(var target=connect(f.target);var restored=connect(f.restored)) {
   assertThrows(Exception.class,()->LegacyBackupVerifier.generate(target,restored,unrelated,"approved-test",dir.resolve("unsafe.properties"),new LegacyBackupVerifier.RestoreCredentials(f.restored.user(),f.restored.password())));
  }
 }
 @Test void emptyUnrelatedOrCrossSchemaArtifactsNeverProduceManifest()throws Exception {
  var targetDb=MySqlTestDatabase.create();at5(targetDb);seed(targetDb);
  String schema=targetDb.url().substring(targetDb.url().lastIndexOf('/')+1).split("\\?")[0];
  for(String sql:List.of("", "CREATE TABLE unrelated(id INT);", "DELETE FROM `"+schema+"`.utility_matrix_item;")) {
   var restoreDb=restrictedRestore();Path dump=Files.createTempFile(dir,"invalid-artifact-",".sql"),manifest=dir.resolve(UUID.randomUUID()+".properties");Files.writeString(dump,sql);
   try(var target=connect(targetDb);var restored=connect(restoreDb)) {
    var before=LegacyDataset.inspect(target,"utility_matrix_item");
    assertThrows(Exception.class,()->LegacyBackupVerifier.generate(target,restored,dump,"approved-test",manifest,new LegacyBackupVerifier.RestoreCredentials(restoreDb.user(),restoreDb.password())));
    assertEquals(before,LegacyDataset.inspect(target,"utility_matrix_item"));assertFalse(Files.exists(manifest));
   }
  }
  var broad=MySqlTestDatabase.create();Path dump=Files.createTempFile(dir,"empty-",".sql");
  try(var target=connect(targetDb);var restored=connect(broad)) {
   assertThrows(Exception.class,()->LegacyBackupVerifier.generate(target,restored,dump,"approved-test",dir.resolve("broad.properties"),new LegacyBackupVerifier.RestoreCredentials(broad.user(),broad.password())));
  }
 }
 static String rootMessages(Throwable t){StringBuilder b=new StringBuilder();while(t!=null){b.append(t.getMessage());t=t.getCause();}return b.toString();}
 static boolean sqlCause(Throwable t,int code,String constraint){
  while(t!=null){if(t instanceof SQLException sql&&sql.getErrorCode()==code&&sql.getMessage().contains(constraint))return true;t=t.getCause();}
  return false;
 }
 static int failedV7Rows(Connection c)throws SQLException{
  try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE version='7' AND success=0")){r.next();return r.getInt(1);}
 }
 Fixture fixture()throws Exception {
  var target=MySqlTestDatabase.create();at5(target);seed(target);var restored=restrictedRestore();
  Path dump=Files.createTempFile(dir,"legacy-",".sql"),manifest=dir.resolve(UUID.randomUUID()+".properties");
  String schema=target.url().substring(target.url().lastIndexOf('/')+1).split("\\?")[0],restoreSchema=restored.url().substring(restored.url().lastIndexOf('/')+1).split("\\?")[0];
  String server=System.getenv("PASPM_TEST_MYSQL_SERVER");String port=server.substring(server.lastIndexOf(':')+1).replace("/","");
  ProcessBuilder backup=new ProcessBuilder("mysqldump","--host=127.0.0.1","--port="+port,"--user="+target.user(),"--single-transaction","--set-gtid-purged=OFF","--no-tablespaces",schema).redirectOutput(dump.toFile()).redirectError(dir.resolve("dump.stderr").toFile());backup.environment().put("MYSQL_PWD",target.password());assertEquals(0,backup.start().waitFor(),"Actual backup failed");

  try(var c=connect(target);var r=connect(restored)){LegacyDataset before=LegacyDataset.inspect(c,"utility_matrix_item");LegacyBackupVerifier.generate(c,r,dump,"approved-isolated-synthetic-test",manifest,new LegacyBackupVerifier.RestoreCredentials(restored.user(),restored.password()));return new Fixture(target,restored,dump,manifest,before);}
 }
 MySqlTestDatabase restrictedRestore()throws Exception {
  var db=MySqlTestDatabase.create();String schema=db.url().substring(db.url().lastIndexOf('/')+1).split("\\?")[0];
  String username="bc_restore_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
  String password=UUID.randomUUID().toString().replace("-","");
  String helper=System.getenv("PASPM_TEST_RESTORE_PROVISIONER");assertNotNull(helper,"Opt-in backup tests require disposable restore-account provisioner");
  var process=new ProcessBuilder(helper).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
  try(var input=process.getOutputStream()){input.write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(Map.of("schema",schema,"username",username,"password",password)));}
  assertTrue(process.waitFor(30,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,process.exitValue());
  return new MySqlTestDatabase(db.url(),username,password);
 }
 static Connection connect(MySqlTestDatabase db)throws SQLException{return DriverManager.getConnection(db.url(),db.user(),db.password());}
 static void at5(MySqlTestDatabase db){Flyway.configure().dataSource(db.url(),db.user(),db.password()).target("5").load().migrate();}
 static Flyway latest(MySqlTestDatabase db,LegacyRecoveryCallback callback){return Flyway.configure().dataSource(db.url(),db.user(),db.password()).callbacks(callback).load();}
 static LegacyRecoveryCallback callback(Fixture f){return new LegacyRecoveryCallback(true,f.manifest.toString(),f.restored.url(),f.restored.user(),f.restored.password());}
 static void seed(MySqlTestDatabase db)throws SQLException {
  try(var c=connect(db);var s=c.createStatement()){
   s.executeUpdate("INSERT INTO store(store_id,name) VALUES(1,'legacy A'),(2,'legacy B')");
   s.executeUpdate("INSERT INTO app_user(user_id,store_id,role_id,email,name,status) VALUES(1,1,2,'a@synthetic.test','A','ACTIVE'),(2,2,2,'b@synthetic.test','B','ACTIVE')");
   s.executeUpdate("INSERT INTO product(product_id,store_id,sku,name,unit,selling_price,cost_price) VALUES(1,1,'A','A','each',3,1),(2,2,'B','B','each',3,1)");
   s.executeUpdate("INSERT INTO utility_snapshot(snapshot_id,store_id,created_by,range_start,range_end,as_of_date,config_snapshot,source_hash,matrix_hash,total_weighted_utility,rounding_scale,status) VALUES(1,1,1,'2020-01-01','2020-01-01','2020-01-01','{}',REPEAT('a',64),REPEAT('b',64),2,6,'READY'),(2,2,2,'2020-01-01','2020-01-01','2020-01-01','{}',REPEAT('a',64),REPEAT('b',64),3,6,'READY')");
   s.executeUpdate("INSERT INTO utility_matrix_item(matrix_item_id,snapshot_id,transaction_id,product_id,quantity,base_utility,expiry_status,weight_value,weighted_utility) VALUES(1,1,7,1,1,2,'NOT_APPLICABLE',1,2),(2,2,7,2,1,3,'NOT_APPLICABLE',1,3)");
  }
 }
}

package vn.edu.fpt.paspm.deployment;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import vn.edu.fpt.paspm.deployment.legacy.*;
import vn.edu.fpt.paspm.utility.*;
import vn.edu.fpt.paspm.mining.*;
import vn.edu.fpt.paspm.common.ApiException;
import java.nio.file.*;
import java.time.*;
import java.math.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="paspm.test.mysql",matches="true")
class LegacyReaderMySqlTest {
 @TempDir Path dir;
 @Test void emptyV5MatrixPreservesExistingCatalogAndSnapshotWithoutOptIn() throws Exception {
  var target=MySqlTestDatabase.create();LegacyRecoveryMySqlTest.at5(target);LegacyRecoveryMySqlTest.seed(target);
  var jdbc=new JdbcTemplate(new DriverManagerDataSource(target.url(),target.user(),target.password()));
  jdbc.update("DELETE FROM utility_matrix_item");
  jdbc.update("UPDATE utility_snapshot SET total_weighted_utility=0");
  jdbc.update("INSERT INTO category(category_id,store_id,name) VALUES(1,1,'A'),(2,2,'B')");
  jdbc.update("UPDATE product SET category_id=store_id");
  jdbc.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,initial_quantity,remaining_quantity) VALUES(1,'A','2020-01-01',3,2),(2,'B','2020-01-01',4,3)");
  var before=fingerprints(target,false);
  LegacyRecoveryMySqlTest.latest(target,new LegacyRecoveryCallback(false,null,null,null,null)).migrate();
  assertEquals(before,fingerprints(target,true));
  var json=new UtilitySettingsRepository(jdbc,JsonMapper.builder().findAndAddModules().build());
  var repository=new UtilitySnapshotRepository(jdbc,json);
  for(int store:List.of(1,2)) {
   assertEquals(0,repository.countItems(store,store));
   assertTrue(repository.page(store,store,0,10).isEmpty());
   var access=org.mockito.Mockito.mock(vn.edu.fpt.paspm.catalog.CatalogAccess.class);
   var jwt=org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test").header("alg","none").subject(Integer.toString(store)).build();
   org.mockito.Mockito.when(access.currentStoreId(jwt)).thenReturn(store);
   var service=new UtilitySnapshotService(access,json,null,repository,null);
   assertEquals(0,service.matrix(jwt,store,0,10).totalElements());
   int selected=store;
   assertEquals(409,assertThrows(ApiException.class,()->repository.saveItems(selected,List.of())).status());
   assertEquals(409,assertThrows(ApiException.class,()->repository.assertAssociationEvidence(selected,selected)).status());
  }

  for(String table:List.of("utility_matrix_item","utility_matrix_item_legacy_v5","sales_transaction"))
   assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
  for(String table:List.of("store","product","category","inventory_batch","utility_snapshot"))
   assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
 }
 @Test void verifiedRecoveryPreservesExactMiningDatasetsAndEnginePatternsWithoutSales() throws Exception {
  var helper=new LegacyRecoveryMySqlTest();helper.dir=dir;
  var target=MySqlTestDatabase.create();LegacyRecoveryMySqlTest.at5(target);LegacyRecoveryMySqlTest.seed(target);
  var jdbc=new JdbcTemplate(new DriverManagerDataSource(target.url(),target.user(),target.password()));
  jdbc.update("INSERT INTO product(product_id,store_id,sku,name,unit,selling_price,cost_price) VALUES(3,1,'A2','A2','each',5,1)");
  jdbc.update("INSERT INTO utility_matrix_item(matrix_item_id,snapshot_id,transaction_id,product_id,quantity,base_utility,expiry_status,weight_value,weighted_utility) VALUES(3,1,7,3,1,4,'NOT_APPLICABLE',1,4),(4,1,8,1,1,2,'NOT_APPLICABLE',1,2)");
  jdbc.update("UPDATE utility_snapshot SET total_weighted_utility=8 WHERE snapshot_id=1");
  var json=new UtilitySettingsRepository(jdbc,JsonMapper.builder().findAndAddModules().build());
  var date=LocalDate.of(2020,1,1);
  var config=new UtilityMatrixContracts.FrozenConfiguration(UtilityContracts.WeightConfig.defaults(),null,null,date,"DEV_FIXTURE",6,"HALF_UP","PRODUCT_ID_V1","Asia/Ho_Chi_Minh");
  jdbc.update("UPDATE utility_snapshot SET config_snapshot=?",json.write(config));
  jdbc.update("INSERT INTO category(category_id,store_id,name) VALUES(1,1,'A'),(2,2,'B')");
  jdbc.update("UPDATE product SET category_id=store_id");
  jdbc.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,initial_quantity,remaining_quantity) VALUES(1,'A','2020-01-01',3,2),(2,'B','2020-01-01',4,3)");
  var repository=new UtilitySnapshotRepository(jdbc,json);var builder=new MiningInputBuilder();
  var before=new TreeMap<Integer,MiningInputBuilder.Dataset>();
  for(int store:List.of(1,2)){
   var rows=new ArrayList<UtilityMatrixContracts.MatrixItem>();
   if(store==1){rows.add(row(7,1,2,6));rows.add(row(7,3,4,6));rows.add(row(8,1,2,2));}else rows.add(row(7,2,3,3));
   before.put(store,builder.build(new UtilityMatrixContracts.SnapshotView(store,store,store,date,date,date,config,"a".repeat(64),"b".repeat(64),BigDecimal.valueOf(store==1?8:3),6,"READY",Instant.EPOCH,List.of()),rows));
  }
  var engine=new SpmfMiningEngine(System.getProperty("paspm.test.spmf-jar"),System.getProperty("paspm.test.spmf-java"),60000,256,2000);
  var patterns=new TreeMap<String,Map<String,Integer>>();
  for(var entry:before.entrySet())for(var algorithm:MiningEngine.Algorithm.values())patterns.put(entry.getKey()+algorithm.name(),engine.execute(algorithm,entry.getValue(),1).patterns());
  LegacyDataset original;try(var c=LegacyRecoveryMySqlTest.connect(target)){original=LegacyDataset.inspect(c,"utility_matrix_item");}
  Map<String,String> fingerprints= fingerprints(target,false);
  var restored=helper.restrictedRestore();Path dump=dir.resolve("backup.sql"),manifest=dir.resolve("manifest.properties");
  String schema=target.url().substring(target.url().lastIndexOf('/')+1).split("\\?")[0];
  String server=System.getenv("PASPM_TEST_MYSQL_SERVER");String port=server.substring(server.lastIndexOf(':')+1).replace("/","");
  var backup=new ProcessBuilder("mysqldump","--host=127.0.0.1","--port="+port,"--user="+target.user(),"--single-transaction","--set-gtid-purged=OFF","--no-tablespaces",schema).redirectOutput(dump.toFile()).redirectError(dir.resolve("dump.stderr").toFile());
  backup.environment().put("MYSQL_PWD",target.password());assertEquals(0,backup.start().waitFor());
  try(var c=LegacyRecoveryMySqlTest.connect(target);var r=LegacyRecoveryMySqlTest.connect(restored)) {LegacyBackupVerifier.generate(c,r,dump,"approved-synthetic-reader-test",manifest,new LegacyBackupVerifier.RestoreCredentials(restored.user(),restored.password()));}
  LegacyRecoveryMySqlTest.latest(target,new LegacyRecoveryCallback(true,manifest.toString(),restored.url(),restored.user(),restored.password())).migrate();
  try(var c=LegacyRecoveryMySqlTest.connect(target)){assertEquals(original,LegacyDataset.inspect(c,"utility_matrix_item_legacy_v5"));}
  var afterFingerprints=fingerprints(target,true);assertEquals(fingerprints,afterFingerprints);
  var evidence=new TreeMap<String,Object>();evidence.put("before",fingerprints);evidence.put("after",afterFingerprints);evidence.put("datasets",before);evidence.put("exactPatterns",patterns);

  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sales_transaction",Integer.class));
  for(int store:List.of(1,2)) {
   assertEquals(store==1?3:1,repository.countItems(store,store));var rows=repository.page(store,store,0,10);assertEquals(store,rows.get(0).productId());assertNull(rows.get(0).expiryEvidence());assertTrue(repository.page(store,store,store==1?3:1,1).isEmpty());
   if(store==1)assertEquals(3,repository.page(store,store,1,1).get(0).productId());
   repository.assertMiningOwnership(store,store);
   var access=org.mockito.Mockito.mock(vn.edu.fpt.paspm.catalog.CatalogAccess.class);
   var jwt=org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test").header("alg","none").subject(Integer.toString(store)).build();
   org.mockito.Mockito.when(access.currentStoreId(jwt)).thenReturn(store);
   var service=new UtilitySnapshotService(access,json,null,repository,null);
   assertEquals(store==1?3:1,service.matrix(jwt,store,0,1).totalElements());
   assertEquals(404,assertThrows(ApiException.class,()->service.matrix(jwt,3-Integer.parseInt(jwt.getSubject()),0,1)).status());
   var auth=org.mockito.Mockito.mock(vn.edu.fpt.paspm.auth.AuthService.class);
   org.mockito.Mockito.when(auth.current(jwt)).thenReturn(new vn.edu.fpt.paspm.user.User(store,"test@example.test","Test","MERCHANDISER",store,"ACTIVE",null,Instant.EPOCH,1,null,null));
   var miningRepository=new MiningRepository(jdbc,json);
   var miningService=new MiningService(miningRepository,builder,engine,org.mockito.Mockito.mock(MiningWorker.class),repository,access,auth,jdbc,new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
   var captured=miningService.create(jwt,new MiningContracts.CreateRequest(UUID.randomUUID(),store,List.of(MiningEngine.Algorithm.HUI_MINER,MiningEngine.Algorithm.EFIM),MiningContracts.MinUtilMode.ABSOLUTE,BigDecimal.ONE));
   assertEquals(before.get(store),json.read(jdbc.queryForObject("SELECT frozen_input FROM mining_request WHERE request_id=?",String.class,captured.requestId()),MiningInputBuilder.Dataset.class));
   jdbc.update("UPDATE mining_run SET status='COMPLETED',itemset_count=0 WHERE request_id=?",captured.requestId());
   var reader=new vn.edu.fpt.paspm.recommendation.FrozenEvidenceReader(jdbc,miningRepository,repository,json);
   for(var run:miningRepository.runs(store,captured.requestId()))assertEquals(409,assertThrows(ApiException.class,()->reader.read(run.storeId(),run.runId())).status());
   var after=builder.build(repository.find(store,store),rows);assertEquals(before.get(store),after);
   assertEquals(builder.threshold(before.get(store),MiningContracts.MinUtilMode.ABSOLUTE,BigDecimal.ONE),builder.threshold(after,MiningContracts.MinUtilMode.ABSOLUTE,BigDecimal.ONE));
   for(var algorithm:MiningEngine.Algorithm.values())assertEquals(patterns.get(store+algorithm.name()),engine.execute(algorithm,after,1).patterns());
   int selected=store;assertEquals(404,assertThrows(ApiException.class,()->repository.page(3-selected,selected,0,10)).status());
   assertEquals(409,assertThrows(ApiException.class,()->repository.saveItems(selected,rows)).status());
   assertEquals(409,assertThrows(ApiException.class,()->repository.assertAssociationEvidence(selected,selected)).status());
  }
  String runtimeCredentials=System.getProperty("paspm.test.runtime-credentials");
  if(runtimeCredentials!=null) {
   var properties=new Properties();try(var stream=Files.newInputStream(Path.of(runtimeCredentials))){properties.load(stream);}
   var runtimeJdbc=new JdbcTemplate(new DriverManagerDataSource(target.url(),properties.getProperty("username"),properties.getProperty("password")));
   assertTrue(runtimeJdbc.queryForList("SHOW GRANTS",String.class).stream().noneMatch(grant->grant.toUpperCase(Locale.ROOT).contains("SUPER")));
   var runtimeRepository=new UtilitySnapshotRepository(runtimeJdbc,new UtilitySettingsRepository(runtimeJdbc,JsonMapper.builder().findAndAddModules().build()));
   assertEquals(3,runtimeRepository.countItems(1,1));assertEquals(3,runtimeRepository.page(1,1,0,10).size());
   assertThrows(org.springframework.dao.DataAccessException.class,()->runtimeJdbc.update("UPDATE utility_matrix_item_legacy_v5 SET quantity=2 WHERE matrix_item_id=1"));
  }
  jdbc.update("INSERT INTO sales_transaction(transaction_id,store_id,source_system,source_order_id,occurred_at,status,total_amount,content_sha256) VALUES(7,1,'SYNTHETIC_TEST','alias',CURRENT_TIMESTAMP,'COMPLETED',1,REPEAT('a',64))");
  assertEquals(409,assertThrows(ApiException.class,()->repository.assertAssociationEvidence(1,1)).status());
  jdbc.update("INSERT INTO utility_matrix_item(snapshot_id,transaction_id,product_id,quantity,base_utility,expiry_status,weight_value,weighted_utility) VALUES(1,7,1,1,2,'NOT_APPLICABLE',1,2)");
  assertEquals(409,assertThrows(ApiException.class,()->repository.page(1,1,0,1)).status());
  evidence.put("fixtureLegacyRows",4);evidence.put("stores",2);evidence.put("engineExecutions",8);evidence.put("threshold",1);
  evidence.put("verificationPhase","all-reader-assertions-passed");
  String evidencePath=System.getProperty("paspm.test.legacy-reader-evidence");if(evidencePath!=null)Files.writeString(Path.of(evidencePath),JsonMapper.builder().findAndAddModules().build().writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
 }
 static UtilityMatrixContracts.MatrixItem row(int tx,int product,int utility,int total) {
  return new UtilityMatrixContracts.MatrixItem(tx,product,product,1,BigDecimal.valueOf(utility),null,null,null,"NOT_APPLICABLE",BigDecimal.ONE,BigDecimal.valueOf(utility),BigDecimal.valueOf(total),null);
 }
 final Map<String,String> canonicalColumns=new TreeMap<>();
 Map<String,String> fingerprints(MySqlTestDatabase db,boolean archived)throws Exception {
  var result=new TreeMap<String,String>();
  try(var c=LegacyRecoveryMySqlTest.connect(db)) {
   for(String table:List.of("store","product","category","inventory_batch")) {
    if(!canonicalColumns.containsKey(table))try(var statement=c.createStatement();var rows=statement.executeQuery("SELECT * FROM "+table+" WHERE 1=0")) {
     var columns=new ArrayList<String>();for(int i=1;i<=rows.getMetaData().getColumnCount();i++)columns.add(rows.getMetaData().getColumnName(i));canonicalColumns.put(table,String.join(",",columns));
    }
    result.put(table,LegacyDataset.hash(c,"SELECT "+canonicalColumns.get(table)+" FROM "+table+" ORDER BY 1"));
   }
   result.put("utility_snapshot",LegacyDataset.hash(c,"SELECT "+LegacyDataset.SNAPSHOT_COLUMNS+" FROM utility_snapshot ORDER BY snapshot_id"));
   result.put("utility_matrix_item",LegacyDataset.hash(c,"SELECT "+LegacyDataset.MATRIX_COLUMNS+" FROM "+(archived?"utility_matrix_item_legacy_v5":"utility_matrix_item")+" ORDER BY matrix_item_id"));
  }
  return result;
 }

}

package vn.edu.fpt.paspm.deployment.legacy;

import java.nio.file.Path;
import java.sql.*;
import org.flywaydb.core.api.callback.*;

/** Registered before V7, whose unchanged transaction FK must only see an empty live matrix. */
public class LegacyRecoveryCallback implements Callback {
 private static final String ARCHIVE="utility_matrix_item_legacy_v5",STAGE="utility_matrix_item_f04_stage",JOURNAL="utility_legacy_recovery_journal";
 private final boolean enabled;
 private final String manifest,restoreUrl,restoreUser,restorePassword;
 public LegacyRecoveryCallback(boolean enabled,String manifest,String restoreUrl,String restoreUser,String restorePassword){this.enabled=enabled;this.manifest=manifest;this.restoreUrl=restoreUrl;this.restoreUser=restoreUser;this.restorePassword=restorePassword;}
 @Override public boolean supports(Event event,Context context){return event==Event.BEFORE_EACH_MIGRATE&&context.getMigrationInfo()!=null&&"7".equals(context.getMigrationInfo().getVersion().toString());}
 @Override public boolean canHandleInTransaction(Event event,Context context){return false;}
 @Override public String getCallbackName(){return "paspm-verified-legacy-v5-recovery";}
 @Override public void handle(Event event,Context context){try{recover(context.getConnection());}catch(Exception e){throw new IllegalStateException("Historical matrix recovery refused: "+e.getMessage(),e);}}
 private void recover(Connection c)throws Exception {
  for(String table:new String[]{"api_connection","import_batch","sales_transaction","transaction_item","import_staging_row"})if(exists(c,table))throw new IllegalStateException("Partial V7 object "+table+"; preserve forensic copy and restore a verified clean backup into a separate target");
  java.util.Set<String> versions=new java.util.HashSet<>();
  try(var st=c.createStatement();var r=st.executeQuery("SELECT version,success FROM flyway_schema_history WHERE version IS NOT NULL")){while(r.next()){if(!r.getBoolean(2)||!versions.add(r.getString(1)))throw new IllegalStateException("Unexpected or failed migration history");}}
  if(!versions.equals(java.util.Set.of("1","2","3","4","5","6")))throw new IllegalStateException("Expected exact clean V1 through V6 history before V7");
  boolean journal=exists(c,JOURNAL),archive=exists(c,ARCHIVE),stage=exists(c,STAGE);
  if(!journal&&(archive||stage||exists(c,"utility_snapshot_storage")))throw new IllegalStateException("Unknown recovery objects without journal");
  if(!journal&&LegacyDataset.count(c,"utility_matrix_item")==0){LegacyDataset.inspect(c,"utility_matrix_item");return;}
  if(!enabled||manifest==null||restoreUrl==null)throw new IllegalStateException("Populated V5 requires explicit opt-in and target-bound verified backup manifest with restored-copy connection");
  if(!c.getMetaData().getDatabaseProductName().equals("MySQL"))throw new IllegalStateException("Populated recovery requires MySQL atomic RENAME TABLE; H2 is supported for fresh migrations only");
  LegacyDataset original=LegacyDataset.inspect(c,archive?ARCHIVE:"utility_matrix_item");
  String manifestSha;
  try(var restore=DriverManager.getConnection(restoreUrl,restoreUser,restorePassword)){restore.setReadOnly(true);manifestSha=LegacyBackupVerifier.verify(c,restore,Path.of(manifest),original);}
  if(!journal){
   exec(c,"CREATE TABLE "+JOURNAL+" (recovery_id INT PRIMARY KEY, phase VARCHAR(32) NOT NULL, target_identity VARCHAR(255) NOT NULL, matrix_sha256 CHAR(64) NOT NULL, snapshot_sha256 CHAR(64) NOT NULL, manifest_sha256 CHAR(64) NOT NULL, matrix_rows BIGINT NOT NULL, snapshot_rows BIGINT NOT NULL)");
   try(var p=c.prepareStatement("INSERT INTO "+JOURNAL+" VALUES (1,'PREPARED',?,?,?,?,?,?)")){p.setString(1,LegacyBackupVerifier.identity(c));p.setString(2,original.matrixSha256());p.setString(3,original.snapshotSha256());p.setString(4,manifestSha);p.setLong(5,original.matrixRows());p.setLong(6,original.snapshots());p.executeUpdate();}
   c.commit();checkpoint("PREPARED");
  }
  String phase;
  try(var s=c.createStatement();var r=s.executeQuery("SELECT * FROM "+JOURNAL)){if(!r.next()||r.getInt("recovery_id")!=1||!LegacyBackupVerifier.identity(c).equals(r.getString("target_identity"))||!original.matrixSha256().equals(r.getString("matrix_sha256"))||!original.snapshotSha256().equals(r.getString("snapshot_sha256"))||!manifestSha.equals(r.getString("manifest_sha256"))||original.matrixRows()!=r.getLong("matrix_rows")||original.snapshots()!=r.getLong("snapshot_rows"))throw new IllegalStateException("Recovery journal fingerprint/binding mismatch");phase=r.getString("phase");if(r.next())throw new IllegalStateException("Unexpected recovery journal rows");}
  if(!java.util.Set.of("PREPARED","STAGED","RENAMED","COMPLETE").contains(phase))throw new IllegalStateException("Unknown recovery phase");
  if(archive){if(stage||LegacyDataset.count(c,"utility_matrix_item")!=0||phase.equals("PREPARED"))throw new IllegalStateException("Mixed or inconsistent renamed state");validateStage(c,"utility_matrix_item",ARCHIVE);}
  else {
   if(phase.equals("RENAMED")||phase.equals("COMPLETE"))throw new IllegalStateException("Journal claims missing archive");
   if(!stage){if(!phase.equals("PREPARED"))throw new IllegalStateException("Missing staged table");exec(c,"CREATE TABLE "+STAGE+" LIKE utility_matrix_item");}
   if(LegacyDataset.count(c,STAGE)!=0)throw new IllegalStateException("Staged live table is not empty");
   // CREATE TABLE LIKE deliberately omits foreign keys. Restore both before the atomic swap.
   ensureForeignKey(c,"fk_recovery_stage_snapshot","snapshot_id","utility_snapshot","snapshot_id");
   ensureForeignKey(c,"fk_recovery_stage_product","product_id","product","product_id");
   validateStage(c,STAGE,"utility_matrix_item");
   phase(c,"STAGED");checkpoint("STAGED");
   try(var st=c.createStatement();var r=st.executeQuery("SELECT COALESCE(MAX(matrix_item_id),0)+1 FROM utility_matrix_item")){r.next();exec(c,"ALTER TABLE "+STAGE+" AUTO_INCREMENT="+r.getLong(1));}
   exec(c,"RENAME TABLE utility_matrix_item TO "+ARCHIVE+", "+STAGE+" TO utility_matrix_item");
   checkpoint("AFTER_RENAME");phase(c,"RENAMED");
  }
  if(!original.equals(LegacyDataset.inspect(c,ARCHIVE)))throw new IllegalStateException("Archive preservation mismatch");
  LegacyRecoverySchema.ensureArchiveAndRegistry(c);
  try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM utility_snapshot_storage r LEFT JOIN utility_snapshot s ON s.snapshot_id=r.snapshot_id WHERE r.storage_kind<>'LEGACY_V5_ARCHIVE' OR s.snapshot_id IS NULL OR r.store_id<>s.store_id")){r.next();if(r.getLong(1)!=0)throw new IllegalStateException("Conflicting storage registry");}
  try(var st=c.createStatement();var rs=st.executeQuery("SELECT s.snapshot_id,s.store_id,s.config_snapshot,r.source_namespace FROM utility_snapshot s LEFT JOIN utility_snapshot_storage r ON r.snapshot_id=s.snapshot_id")) {
   var json=new com.fasterxml.jackson.databind.ObjectMapper();
   while(rs.next()) {
    int id=rs.getInt(1);String source=json.readTree(rs.getString(3)).path("source").asText("");
    String namespace=source.isBlank()?"UNVERIFIED:SNAPSHOT:"+id:"V5:"+source;
    if(namespace.length()>255)throw new IllegalStateException("Frozen source namespace too long");
    if(rs.getString(4)!=null){if(!namespace.equals(rs.getString(4)))throw new IllegalStateException("Conflicting frozen source namespace");}
    else try(var p=c.prepareStatement("INSERT INTO utility_snapshot_storage VALUES (?,?,'LEGACY_V5_ARCHIVE',?)")){p.setInt(1,id);p.setInt(2,rs.getInt(2));p.setString(3,namespace);p.executeUpdate();}
   }
  }
  if(LegacyDataset.count(c,"utility_snapshot_storage")!=original.snapshots())throw new IllegalStateException("Storage registry incomplete");
  phase(c,"COMPLETE");checkpoint("COMPLETE");
 }
 /** Test seam supports deterministic process-interruption rehearsals; production uses the no-op. */
 protected void checkpoint(String phase) {}
 private static void phase(Connection c,String phase)throws SQLException {try(var p=c.prepareStatement("UPDATE "+JOURNAL+" SET phase=? WHERE recovery_id=1")){p.setString(1,phase);p.executeUpdate();}c.commit();}
 private static void validateStage(Connection c,String staged,String original)throws SQLException {
  if(!columnShape(c,staged).equals(columnShape(c,original)))throw new IllegalStateException("Staged column shape mismatch");
  String checks="SELECT cc.check_clause FROM information_schema.table_constraints tc JOIN information_schema.check_constraints cc ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name WHERE tc.table_schema=DATABASE() AND tc.table_name=? AND tc.constraint_type='CHECK' ORDER BY cc.check_clause";
  String indexes="SELECT non_unique,index_type,GROUP_CONCAT(CONCAT(column_name,':',COALESCE(sub_part,0),':',COALESCE(collation,'')) ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=? GROUP BY index_name,non_unique,index_type ORDER BY 1,2,3";
  if(!metadata(c,checks,staged).equals(metadata(c,checks,original))||!metadata(c,indexes,staged).equals(metadata(c,indexes,original)))throw new IllegalStateException("Staged index or CHECK shape mismatch");
  String keys="SELECT column_name,referenced_table_name,referenced_column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name=? AND referenced_table_name IS NOT NULL ORDER BY 1,2,3";
  if(!metadata(c,keys,staged).equals(metadata(c,keys,original)))throw new IllegalStateException("Staged foreign key shape mismatch");
 }
 private static String metadata(Connection c,String sql,String table)throws SQLException {StringBuilder b=new StringBuilder();try(var p=c.prepareStatement(sql)){p.setString(1,table);try(var r=p.executeQuery()){while(r.next()){for(int i=1;i<=r.getMetaData().getColumnCount();i++)b.append(r.getString(i)).append('|');b.append('\n');}}}return b.toString();}
 private static String columnShape(Connection c,String table)throws SQLException{StringBuilder b=new StringBuilder();try(var p=c.prepareStatement("SELECT column_name,column_type,is_nullable,column_default,extra FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? ORDER BY ordinal_position")){p.setString(1,table);try(var r=p.executeQuery()){while(r.next())for(int i=1;i<=5;i++)b.append(r.getString(i)).append('|');}}return b.toString();}
 private static void ensureForeignKey(Connection c,String name,String column,String parent,String parentColumn)throws SQLException {
  try(var p=c.prepareStatement("SELECT referenced_table_name,referenced_column_name,column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name=? AND constraint_name=?")){p.setString(1,STAGE);p.setString(2,name);try(var r=p.executeQuery()){if(r.next()){if(!parent.equals(r.getString(1))||!parentColumn.equals(r.getString(2))||!column.equals(r.getString(3)))throw new IllegalStateException("Unexpected staged foreign key");return;}}}
  exec(c,"ALTER TABLE "+STAGE+" ADD CONSTRAINT "+name+" FOREIGN KEY ("+column+") REFERENCES "+parent+"("+parentColumn+")");
 }
 public static boolean exists(Connection c,String table)throws SQLException{try(var r=c.getMetaData().getTables(c.getCatalog(),null,table,new String[]{"TABLE"})){return r.next();}}
 private static void exec(Connection c,String sql)throws SQLException{try(var s=c.createStatement()){s.execute(sql);}}
}

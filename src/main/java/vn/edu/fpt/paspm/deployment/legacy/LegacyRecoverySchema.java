package vn.edu.fpt.paspm.deployment.legacy;

import java.sql.*;
import java.util.Locale;

/** Additive archive storage. DDL intentionally fails closed when guard installation is denied. */
public final class LegacyRecoverySchema {
 private LegacyRecoverySchema() {}
 private static final String ARCHIVE_DDL = """
CREATE TABLE IF NOT EXISTS utility_matrix_item_legacy_v5 (
    matrix_item_id INT AUTO_INCREMENT PRIMARY KEY,
    snapshot_id INT NOT NULL,
    transaction_id INT NOT NULL,
    product_id INT NOT NULL,
    quantity INT NOT NULL,
    base_utility DECIMAL(30,6) NOT NULL,
    stock_score DECIMAL(24,18) NULL,
    expiry_score DECIMAL(24,18) NULL,
    seasonal_score DECIMAL(24,18) NULL,
    expiry_status VARCHAR(30) NOT NULL,
    weight_value DECIMAL(38,18) NOT NULL,
    weighted_utility DECIMAL(30,6) NOT NULL,
    CONSTRAINT archive_v5_uq_utility_matrix_item UNIQUE(snapshot_id,transaction_id,product_id),
    CONSTRAINT archive_v5_fk_utility_matrix_snapshot FOREIGN KEY(snapshot_id) REFERENCES utility_snapshot(snapshot_id),
    CONSTRAINT archive_v5_fk_utility_matrix_product FOREIGN KEY(product_id) REFERENCES product(product_id),
    CONSTRAINT archive_v5_ck_utility_matrix_quantity CHECK(quantity > 0),
    CONSTRAINT archive_v5_ck_utility_matrix_transaction CHECK(transaction_id > 0),
    CONSTRAINT archive_v5_ck_utility_matrix_base CHECK(base_utility >= 0),
    CONSTRAINT archive_v5_ck_utility_matrix_weight CHECK(weight_value >= 1),
    CONSTRAINT archive_v5_ck_utility_matrix_weighted CHECK(weighted_utility >= 0),
    CONSTRAINT archive_v5_ck_utility_matrix_stock CHECK(stock_score BETWEEN 0 AND 1),
    CONSTRAINT archive_v5_ck_utility_matrix_expiry CHECK(expiry_score BETWEEN 0 AND 1),
    CONSTRAINT archive_v5_ck_utility_matrix_season CHECK(seasonal_score BETWEEN 0 AND 1),
    CONSTRAINT archive_v5_ck_utility_matrix_expiry_status CHECK(expiry_status IN ('AVAILABLE','NOT_APPLICABLE','NO_SELLABLE_BATCH','MISSING_METADATA'))
);
""";
 public static void createStage(Connection c) throws SQLException {
  execute(c,ARCHIVE_DDL.replace("IF NOT EXISTS utility_matrix_item_legacy_v5", "utility_matrix_item_v5_empty_stage").replace("archive_v5_", "stage_v5_"));
 }
 public static void ensureArchiveAndRegistry(Connection c) throws SQLException {
  String engine=c.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
  boolean mysql=engine.contains("mysql"), h2=engine.contains("h2");
  if (!mysql && !h2) throw new SQLException("Legacy archive requires MySQL or H2");
  execute(c,ARCHIVE_DDL);
  foreignKey(c,"snapshot_id","utility_snapshot","snapshot_id","archive_v5_snapshot_fk");
  foreignKey(c,"product_id","product","product_id","archive_v5_product_fk");
  execute(c,"""
CREATE TABLE IF NOT EXISTS utility_snapshot_storage (
 snapshot_id INT PRIMARY KEY,
 store_id INT NOT NULL,
 storage_kind VARCHAR(24) NOT NULL CHECK(storage_kind IN ('LEGACY_V5_ARCHIVE','F04_LIVE')),
 source_namespace VARCHAR(255) NOT NULL,
 CONSTRAINT fk_storage_snapshot FOREIGN KEY(snapshot_id) REFERENCES utility_snapshot(snapshot_id),
 CONSTRAINT fk_storage_store FOREIGN KEY(store_id) REFERENCES store(store_id)
)
""");
  for(String event:new String[]{"INSERT","UPDATE"})
   guard(c,mysql,"archive_registry_owner_"+event.toLowerCase(Locale.ROOT),"utility_snapshot_storage",event,
    "NOT EXISTS (SELECT 1 FROM utility_snapshot WHERE snapshot_id=NEW.snapshot_id AND store_id=NEW.store_id)",
    "try(var p=c.prepareStatement(\"SELECT 1 FROM utility_snapshot WHERE snapshot_id=? AND store_id=?\")){p.setObject(1,newRow[0]);p.setObject(2,newRow[1]);try(var r=p.executeQuery()){if(!r.next())throw new java.sql.SQLException(\"Snapshot Store ownership mismatch\");}}");
  for(String event:new String[]{"INSERT","UPDATE","DELETE"})
   guard(c,mysql,"archive_v5_"+event.toLowerCase(Locale.ROOT),"utility_matrix_item_legacy_v5",event,"TRUE","throw new java.sql.SQLException(\"Historical archive is immutable\");");
  for(String event:new String[]{"UPDATE","DELETE"}) {
   guard(c,mysql,"archive_header_"+event.toLowerCase(Locale.ROOT),"utility_snapshot",event,
    "EXISTS (SELECT 1 FROM utility_snapshot_storage WHERE snapshot_id=OLD.snapshot_id AND storage_kind='LEGACY_V5_ARCHIVE')",
    "try(var p=c.prepareStatement(\"SELECT 1 FROM utility_snapshot_storage WHERE snapshot_id=? AND storage_kind='LEGACY_V5_ARCHIVE'\")){p.setObject(1,oldRow[0]);try(var r=p.executeQuery()){if(r.next())throw new java.sql.SQLException(\"Historical header is immutable\");}}");
   guard(c,mysql,"archive_registry_"+event.toLowerCase(Locale.ROOT),"utility_snapshot_storage",event,
    "OLD.storage_kind='LEGACY_V5_ARCHIVE'",
    "if(\"LEGACY_V5_ARCHIVE\".equals(oldRow[2]))throw new java.sql.SQLException(\"Historical routing is immutable\");");
  }
 }
 /** V9 provides the Store/snapshot candidate key. Existing unknown provenance is not relabeled. */
 public static void ensureModernIntegrity(Connection c)throws SQLException {
  try(var st=c.createStatement();var r=st.executeQuery("SELECT COUNT(*) FROM utility_matrix_item m LEFT JOIN utility_snapshot s ON s.snapshot_id=m.snapshot_id LEFT JOIN product p ON p.product_id=m.product_id LEFT JOIN sales_transaction t ON t.transaction_id=m.transaction_id WHERE s.snapshot_id IS NULL OR p.product_id IS NULL OR t.transaction_id IS NULL OR p.store_id<>s.store_id OR t.store_id<>s.store_id")){r.next();if(r.getLong(1)!=0)throw new SQLException("Modern matrix Store ownership invalid; require separate semantic audit");}
  boolean mysql=c.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL");
  for(String event:new String[]{"INSERT","UPDATE"})
   guard(c,mysql,"live_matrix_owner_"+event.toLowerCase(Locale.ROOT),"utility_matrix_item",event,
    "NOT EXISTS (SELECT 1 FROM utility_snapshot s JOIN product p ON p.store_id=s.store_id JOIN sales_transaction t ON t.store_id=s.store_id WHERE s.snapshot_id=NEW.snapshot_id AND p.product_id=NEW.product_id AND t.transaction_id=NEW.transaction_id)",
    "try(var p=c.prepareStatement(\"SELECT 1 FROM utility_snapshot s JOIN product p ON p.store_id=s.store_id JOIN sales_transaction t ON t.store_id=s.store_id WHERE s.snapshot_id=? AND p.product_id=? AND t.transaction_id=?\")){p.setObject(1,newRow[1]);p.setObject(2,newRow[3]);p.setObject(3,newRow[2]);try(var r=p.executeQuery()){if(!r.next())throw new java.sql.SQLException(\"Modern matrix Store ownership mismatch\");}}");
  boolean present=false;try(var r=c.getMetaData().getImportedKeys(c.getCatalog(),c.getSchema(),"utility_snapshot_storage")){while(r.next())if("fk_storage_snapshot_store".equalsIgnoreCase(r.getString("FK_NAME")))present=true;}
  if(!present)execute(c,"ALTER TABLE utility_snapshot_storage ADD CONSTRAINT fk_storage_snapshot_store FOREIGN KEY(store_id,snapshot_id) REFERENCES utility_snapshot(store_id,snapshot_id)");
 }
 private static void foreignKey(Connection c,String column,String parent,String parentColumn,String name)throws SQLException {
  try(var rs=c.getMetaData().getImportedKeys(c.getCatalog(),c.getSchema(),"utility_matrix_item_legacy_v5")) {
   while(rs.next())if(column.equalsIgnoreCase(rs.getString("FKCOLUMN_NAME")) && parent.equalsIgnoreCase(rs.getString("PKTABLE_NAME")))return;
  }
  execute(c,"ALTER TABLE utility_matrix_item_legacy_v5 ADD CONSTRAINT "+name+" FOREIGN KEY ("+column+") REFERENCES "+parent+"("+parentColumn+")");
 }
 private static void guard(Connection c,boolean mysql,String name,String table,String event,String condition,String javaBody)throws SQLException {
  if(mysql) {
   try(var p=c.prepareStatement("SELECT action_statement,event_manipulation,event_object_table,action_timing FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name=?")) {
    p.setString(1,name);try(var r=p.executeQuery()){if(r.next()) {
     String expected="BEGIN IF "+condition+" THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical archive is immutable'; END IF; END";
     if(!expected.replaceAll("\\s+", " ").equalsIgnoreCase(r.getString(1).replaceAll("\\s+", " ")) || !event.equalsIgnoreCase(r.getString(2)) || !table.equalsIgnoreCase(r.getString(3)) || !"BEFORE".equalsIgnoreCase(r.getString(4)))
      throw new SQLException("Unexpected historical guard definition: "+name);
     return;
    }}
   }
   execute(c,"CREATE TRIGGER "+name+" BEFORE "+event+" ON "+table+" FOR EACH ROW BEGIN IF "+condition+" THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical archive is immutable'; END IF; END");
  } else {
   String source="org.h2.api.Trigger create() { return new org.h2.api.Trigger() { public void fire(java.sql.Connection c,Object[] oldRow,Object[] newRow) throws java.sql.SQLException {"+javaBody+"} }; }";
   execute(c,"CREATE TRIGGER IF NOT EXISTS "+name+" BEFORE "+event+" ON "+table+" FOR EACH ROW AS '"+source.replace("'","''")+"'");
  }
 }
 private static void execute(Connection c,String sql)throws SQLException {try(var s=c.createStatement()){s.execute(sql);}}
}

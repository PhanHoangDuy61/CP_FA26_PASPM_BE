package vn.edu.fpt.paspm.deployment.legacy;

import java.sql.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Canonical length-prefixed fields, ordered by immutable primary key.
 * Creator identity and snapshot Store are frozen; current user membership is not historical evidence.
 */
public record LegacyDataset(long matrixRows, long snapshots, String matrixSha256, String snapshotSha256) {
 public static final String MATRIX_COLUMNS="matrix_item_id,snapshot_id,transaction_id,product_id,quantity,base_utility,stock_score,expiry_score,seasonal_score,expiry_status,weight_value,weighted_utility";
 public static final String SNAPSHOT_COLUMNS="snapshot_id,store_id,created_by,range_start,range_end,as_of_date,config_snapshot,source_hash,matrix_hash,total_weighted_utility,rounding_scale,status,created_at";
 public static LegacyDataset inspect(Connection c,String table) throws SQLException {
  if(!table.equals("utility_matrix_item")&&!table.equals("utility_matrix_item_legacy_v5"))throw new IllegalArgumentException("Invalid matrix table");
  reject(c,"SELECT COUNT(*) FROM utility_snapshot s LEFT JOIN store t ON t.store_id=s.store_id LEFT JOIN app_user u ON u.user_id=s.created_by WHERE t.store_id IS NULL OR u.user_id IS NULL OR s.snapshot_id<=0 OR s.store_id<=0 OR s.created_by<=0 OR s.range_end<s.range_start OR s.rounding_scale<>6 OR s.total_weighted_utility<0 OR s.status NOT IN ('READY','FAILED')", "Invalid snapshot, Store or creator ownership");
  reject(c,"SELECT COUNT(*) FROM "+table+" m LEFT JOIN utility_snapshot s ON s.snapshot_id=m.snapshot_id LEFT JOIN product p ON p.product_id=m.product_id LEFT JOIN store t ON t.store_id=p.store_id WHERE s.snapshot_id IS NULL OR p.product_id IS NULL OR t.store_id IS NULL OR p.store_id<>s.store_id OR m.matrix_item_id<=0 OR m.transaction_id<=0 OR m.product_id<=0 OR m.quantity<=0 OR m.base_utility<0 OR m.weight_value<1 OR m.weighted_utility<0 OR m.stock_score NOT BETWEEN 0 AND 1 OR m.expiry_score NOT BETWEEN 0 AND 1 OR m.seasonal_score NOT BETWEEN 0 AND 1 OR m.expiry_status NOT IN ('AVAILABLE','NOT_APPLICABLE','NO_SELLABLE_BATCH','MISSING_METADATA')", "Invalid matrix identity, ownership or values");
  reject(c,"SELECT COUNT(*) FROM (SELECT snapshot_id,transaction_id,product_id FROM "+table+" GROUP BY snapshot_id,transaction_id,product_id HAVING COUNT(*)>1) d","Duplicate legacy identity");
  reject(c,"SELECT COUNT(*) FROM utility_snapshot s LEFT JOIN (SELECT snapshot_id,SUM(weighted_utility) total FROM "+table+" GROUP BY snapshot_id) m ON m.snapshot_id=s.snapshot_id WHERE s.total_weighted_utility<>COALESCE(m.total,0)","Snapshot total differs from frozen matrix");
  try(var s=c.createStatement();var r=s.executeQuery("SELECT source_hash,matrix_hash FROM utility_snapshot")){while(r.next())if(!r.getString(1).matches("[0-9a-fA-F]{64}")||!r.getString(2).matches("[0-9a-fA-F]{64}"))throw new IllegalStateException("Invalid frozen hash");}
  return new LegacyDataset(count(c,table),count(c,"utility_snapshot"),hash(c,"SELECT "+MATRIX_COLUMNS+" FROM "+table+" ORDER BY matrix_item_id"),hash(c,"SELECT "+SNAPSHOT_COLUMNS+" FROM utility_snapshot ORDER BY snapshot_id"));
 }
 public static long count(Connection c,String table)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();return r.getLong(1);}}
 private static void reject(Connection c,String sql,String message)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();if(r.getLong(1)!=0)throw new IllegalStateException(message);}}
 public static String hash(Connection c,String sql)throws SQLException {
  try {var digest=MessageDigest.getInstance("SHA-256");try(var s=c.createStatement();var r=s.executeQuery(sql)){int cols=r.getMetaData().getColumnCount();while(r.next()){for(int i=1;i<=cols;i++){Object value=r.getObject(i);String text=value==null?null:value instanceof BigDecimal decimal?decimal.stripTrailingZeros().toPlainString():value instanceof byte[] bytes?new String(bytes,StandardCharsets.UTF_8):value.toString();byte[] bytes=text==null?new byte[0]:text.getBytes(StandardCharsets.UTF_8);digest.update((text==null?"-1:":bytes.length+":").getBytes(StandardCharsets.UTF_8));digest.update(bytes);}digest.update((byte)'\n');}}return HexFormat.of().formatHex(digest.digest());}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
}

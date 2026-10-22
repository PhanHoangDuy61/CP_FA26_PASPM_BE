package db.migration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import vn.edu.fpt.paspm.deployment.legacy.LegacyRecoverySchema;
public final class V11__historical_utility_matrix_storage extends BaseJavaMigration {
 @Override public void migrate(Context context) throws Exception {
  var c=context.getConnection();
  boolean archive=vn.edu.fpt.paspm.deployment.legacy.LegacyRecoveryCallback.exists(c,"utility_matrix_item_legacy_v5");
  boolean registry=vn.edu.fpt.paspm.deployment.legacy.LegacyRecoveryCallback.exists(c,"utility_snapshot_storage");
  if(archive||registry) {
   if(!archive||!registry||!vn.edu.fpt.paspm.deployment.legacy.LegacyRecoveryCallback.exists(c,"utility_legacy_recovery_journal"))throw new java.sql.SQLException("Unexpected preexisting legacy storage without completed recovery journal");
   try(var st=c.createStatement();var r=st.executeQuery("SELECT recovery_id,phase,matrix_sha256,snapshot_sha256,matrix_rows,snapshot_rows FROM utility_legacy_recovery_journal")){if(!r.next()||r.getInt(1)!=1||!"COMPLETE".equals(r.getString(2)))throw new java.sql.SQLException("Incomplete legacy recovery journal");
    var original=vn.edu.fpt.paspm.deployment.legacy.LegacyDataset.inspect(c,"utility_matrix_item_legacy_v5");
    if(!original.matrixSha256().equals(r.getString(3))||!original.snapshotSha256().equals(r.getString(4))||original.matrixRows()!=r.getLong(5)||original.snapshots()!=r.getLong(6)||r.next())throw new java.sql.SQLException("Legacy preservation differs from recovery journal");}
  }
  LegacyRecoverySchema.ensureArchiveAndRegistry(c);LegacyRecoverySchema.ensureModernIntegrity(c);
 }
 @Override public boolean canExecuteInTransaction() { return false; }
}

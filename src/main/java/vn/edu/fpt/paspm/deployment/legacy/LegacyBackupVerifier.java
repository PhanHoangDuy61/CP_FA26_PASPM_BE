package vn.edu.fpt.paspm.deployment.legacy;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** The operator restores a real SQL dump into a separate schema before running this verifier. */
public final class LegacyBackupVerifier {
 private LegacyBackupVerifier() {}
 public static void generate(Connection target,Connection restored,Path dump,String approval,Path manifest)throws Exception {
  if(approval==null||approval.isBlank())throw new IllegalArgumentException("Owner approval reference required");
  String identity=identity(target), restoreIdentity=identity(restored);
  if(identity.equals(restoreIdentity))throw new IllegalStateException("Restore must be a separate schema");
  LegacyDataset original=LegacyDataset.inspect(target,"utility_matrix_item"),copy=LegacyDataset.inspect(restored,"utility_matrix_item");
  if(!original.equals(copy))throw new IllegalStateException("Restored copy does not match original dataset");
  Properties p=new Properties();p.setProperty("format","PASPM_LEGACY_V5_1");p.setProperty("target",identity);p.setProperty("restored",restoreIdentity);p.setProperty("matrix",original.matrixSha256());p.setProperty("snapshots",original.snapshotSha256());p.setProperty("matrixRows",Long.toString(original.matrixRows()));p.setProperty("snapshotRows",Long.toString(original.snapshots()));p.setProperty("dump",dump.toRealPath().toString());p.setProperty("dumpSha256",fileSha(dump));p.setProperty("ownerApproval",approval);
  Files.createFile(manifest);Files.setPosixFilePermissions(manifest,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));try(var out=Files.newOutputStream(manifest)){p.store(out,"Verified against an independently restored database; preserve both dump and restore through migration");}
 }
 public static String verify(Connection target,Connection restored,Path manifest,LegacyDataset original)throws Exception {
  if(Files.isSymbolicLink(manifest)||!Files.isRegularFile(manifest))throw new IllegalStateException("Manifest must be a private regular file");
  for(var permission:Files.getPosixFilePermissions(manifest))if(permission.name().startsWith("GROUP_")||permission.name().startsWith("OTHERS_"))throw new IllegalStateException("Manifest must be owner-only");
  Properties p=new Properties();try(var in=Files.newInputStream(manifest)){p.load(in);}
  if(!"PASPM_LEGACY_V5_1".equals(p.getProperty("format"))||p.getProperty("ownerApproval","").isBlank()||!identity(target).equals(p.getProperty("target"))||!identity(restored).equals(p.getProperty("restored"))||identity(target).equals(identity(restored)))throw new IllegalStateException("Backup target/restore/approval binding mismatch");
  if(!original.equals(LegacyDataset.inspect(restored,"utility_matrix_item"))||!original.matrixSha256().equals(p.getProperty("matrix"))||!original.snapshotSha256().equals(p.getProperty("snapshots"))||!Long.toString(original.matrixRows()).equals(p.getProperty("matrixRows"))||!Long.toString(original.snapshots()).equals(p.getProperty("snapshotRows")))throw new IllegalStateException("Backup canonical fingerprint mismatch");
  if(!fileSha(Path.of(p.getProperty("dump"))).equals(p.getProperty("dumpSha256")))throw new IllegalStateException("Backup dump SHA mismatch");
  return fileSha(manifest);
 }
 static String identity(Connection c)throws SQLException {try(var s=c.createStatement();var r=s.executeQuery("SELECT @@server_uuid,DATABASE()")){r.next();return r.getString(1)+"/"+r.getString(2);}}
 public static String fileSha(Path file)throws Exception {var digest=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}return HexFormat.of().formatHex(digest.digest());}
 /** Credentials are obtained from environment, never written into the manifest. */
 public static void main(String[] args)throws Exception {
  if(args.length!=3)throw new IllegalArgumentException("Usage: LegacyBackupVerifier dump.sql private-manifest.properties owner-approval-reference");
  try(var target=DriverManager.getConnection(required("PASPM_RECOVERY_TARGET_URL"),required("PASPM_RECOVERY_USER"),required("PASPM_RECOVERY_PASSWORD"));var restored=DriverManager.getConnection(required("PASPM_RECOVERY_RESTORE_URL"),required("PASPM_RECOVERY_RESTORE_USER"),required("PASPM_RECOVERY_RESTORE_PASSWORD"))){generate(target,restored,Path.of(args[0]),args[2],Path.of(args[1]));}
 }
 private static String required(String name){String value=System.getenv(name);if(value==null)throw new IllegalArgumentException("Missing "+name);return value;}
}

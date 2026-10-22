package vn.edu.fpt.paspm.deployment.legacy;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Restores the exact backup artifact into an empty, schema-restricted verification database. */
public final class LegacyBackupVerifier {
    private static final Set<PosixFilePermission> PRIVATE = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    private LegacyBackupVerifier() {}

    public record RestoreCredentials(String username, String password) {
        @Override public String toString() { return "RestoreCredentials[redacted]"; }
    }

    public static void generate(Connection target, Connection restored, Path dump,
            String approval, Path manifest, RestoreCredentials credentials) throws Exception {
        target.setReadOnly(true);
        if (approval == null || approval.isBlank())
            throw new IllegalArgumentException("Owner approval reference required");
        String identity = identity(target), restoreIdentity = identity(restored);
        if (identity.equals(restoreIdentity))
            throw new IllegalStateException("Restore must be a separate schema");
        requireEmptyRestrictedSchema(restored);
        LegacyDataset original = LegacyDataset.inspect(target, "utility_matrix_item");
        String artifactSha = fileSha(dump);
        restoreExactArtifact(restored, credentials, dump);
        if (!artifactSha.equals(fileSha(dump)))
            throw new IllegalStateException("Dump changed during restore; discard verification schema");
        if (!original.equals(LegacyDataset.inspect(restored, "utility_matrix_item")))
            throw new IllegalStateException("Restored artifact does not match original dataset");
        // Detect target changes during the restore as well as changes to the artifact.
        if (!original.equals(LegacyDataset.inspect(target, "utility_matrix_item")))
            throw new IllegalStateException("Target changed during verification; require quiesced writers");
        Properties p = new Properties();
        p.setProperty("format", "PASPM_LEGACY_V5_2");
        p.setProperty("target", identity);
        p.setProperty("restored", restoreIdentity);
        p.setProperty("matrix", original.matrixSha256());
        p.setProperty("snapshots", original.snapshotSha256());
        p.setProperty("matrixRows", Long.toString(original.matrixRows()));
        p.setProperty("snapshotRows", Long.toString(original.snapshots()));
        p.setProperty("dump", dump.toRealPath().toString());
        p.setProperty("dumpSha256", artifactSha);
        p.setProperty("ownerApproval", approval);
        Files.createFile(manifest, PosixFilePermissions.asFileAttribute(PRIVATE));
        try (var out = Files.newOutputStream(manifest)) {
            p.store(out, "Exact artifact restored by verifier into empty restricted schema; retain dump and restore");
        }
    }

    private static void requireEmptyRestrictedSchema(Connection restored) throws SQLException {
        String schema = restored.getCatalog();
        if (schema == null || !schema.matches("[A-Za-z0-9_]+"))
            throw new IllegalStateException("An explicit simple verification schema name is required");
        try (var s = restored.createStatement(); var r = s.executeQuery(
                "SELECT (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()) + "
                + "(SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema=DATABASE()) + "
                + "(SELECT COUNT(*) FROM information_schema.events WHERE event_schema=DATABASE())")) {
            r.next();
            if (r.getLong(1) != 0) throw new IllegalStateException("Verification schema must be empty before restore");
        }
        boolean schemaGrant = false;
        String exactScope = "`" + schema.replace("_", "\\_") + "`.*";
        try (var s = restored.createStatement(); var r = s.executeQuery("SHOW GRANTS")) {
            while (r.next()) {
                String grant = r.getString(1);
                if (grant.startsWith("GRANT USAGE ON *.* TO ") && !grant.contains("WITH GRANT OPTION")) continue;
                int on = grant.indexOf(" ON "), to = grant.indexOf(" TO ");
                if (!grant.startsWith("GRANT ") || on < 0 || to < on
                        || !grant.substring(on + 4, to).equals(exactScope)
                        || grant.contains("WITH GRANT OPTION"))
                    throw new IllegalStateException("Restore account must have only exact verification-schema grants; no global privileges or roles");
                schemaGrant = true;
            }
        }
        if (!schemaGrant) throw new IllegalStateException("Missing verification-schema grant");
    }

    private static void restoreExactArtifact(Connection restored, RestoreCredentials credentials, Path dump)
            throws Exception {
        URI uri = URI.create(restored.getMetaData().getURL().substring("jdbc:".length()));
        String schema = restored.getCatalog();
        // Validate the actual credential connection; supplied credentials must not substitute a broader user.
        try (var actual = DriverManager.getConnection(restored.getMetaData().getURL(),
                credentials.username(), credentials.password())) {
            requireEmptyRestrictedSchema(actual);
            if (!identity(actual).equals(identity(restored))) throw new IllegalStateException("Restore credential target mismatch");
        }
        Path options = Files.createTempFile("paspm-restore-", ".cnf", PosixFilePermissions.asFileAttribute(PRIVATE));
        Process process = null;
        try {
            Files.writeString(options, "[client]\nuser=\"" + option(credentials.username())
                    + "\"\npassword=\"" + option(credentials.password()) + "\"\n");
            ProcessBuilder builder = new ProcessBuilder("mysql", "--defaults-file=" + options,
                    "--no-login-paths", "--protocol=TCP", "--host=" + uri.getHost(), "--port=" + (uri.getPort() < 0 ? 3306 : uri.getPort()),
                    "--binary-mode", "--local-infile=0", "--database=" + schema)
                    .redirectInput(dump.toFile()).redirectOutput(ProcessBuilder.Redirect.DISCARD);
            // Do not inherit alternative credential channels or client history settings.
            builder.environment().remove("MYSQL_PWD");
            builder.environment().put("MYSQL_HISTFILE", "/dev/null");
            process = builder.start();
            Process running = process;
            // Drain stderr with bounded memory; never echo raw SQL/client output or credentials.
            Thread drain = new Thread(() -> {
                try (var in = running.getErrorStream()) {
                    byte[] buffer = new byte[4096];
                    while (in.read(buffer) != -1) { /* discard bounded chunks */ }
                } catch (IOException ignored) { }
            }, "legacy-restore-stderr");
            drain.setDaemon(true);
            drain.start();
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("Backup restore timed out; discard verification schema");
            }
            drain.join(1000);
            if (process.exitValue() != 0)
                throw new IllegalStateException("Backup restore failed; discard verification schema (client output suppressed)");
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(options);
        }
    }

    private static String option(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    public static String verify(Connection target, Connection restored, Path manifest, LegacyDataset original) throws Exception {
        if (Files.isSymbolicLink(manifest) || !Files.isRegularFile(manifest))
            throw new IllegalStateException("Manifest must be a private regular file");
        if (!PRIVATE.containsAll(Files.getPosixFilePermissions(manifest)))
            throw new IllegalStateException("Manifest must be owner-only");
        Properties p = new Properties();
        try (var in = Files.newInputStream(manifest)) { p.load(in); }
        if (!"PASPM_LEGACY_V5_2".equals(p.getProperty("format")) || p.getProperty("ownerApproval", "").isBlank()
                || !identity(target).equals(p.getProperty("target")) || !identity(restored).equals(p.getProperty("restored"))
                || identity(target).equals(identity(restored)))
            throw new IllegalStateException("Backup target/restore/approval binding mismatch");
        if (!original.equals(LegacyDataset.inspect(restored, "utility_matrix_item"))
                || !original.matrixSha256().equals(p.getProperty("matrix"))
                || !original.snapshotSha256().equals(p.getProperty("snapshots"))
                || !Long.toString(original.matrixRows()).equals(p.getProperty("matrixRows"))
                || !Long.toString(original.snapshots()).equals(p.getProperty("snapshotRows")))
            throw new IllegalStateException("Backup canonical fingerprint mismatch");
        if (!fileSha(Path.of(p.getProperty("dump"))).equals(p.getProperty("dumpSha256")))
            throw new IllegalStateException("Backup dump SHA mismatch");
        return fileSha(manifest);
    }

    static String identity(Connection c) throws SQLException {
        try (var s = c.createStatement(); var r = s.executeQuery("SELECT @@server_uuid,DATABASE()")) {
            r.next(); return r.getString(1) + "/" + r.getString(2);
        }
    }
    public static String fileSha(Path file) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 3)
            throw new IllegalArgumentException("Usage: LegacyBackupVerifier dump.sql private-manifest.properties owner-approval-reference");
        var credentials = new RestoreCredentials(required("PASPM_RECOVERY_RESTORE_USER"), required("PASPM_RECOVERY_RESTORE_PASSWORD"));
        try (var target = DriverManager.getConnection(required("PASPM_RECOVERY_TARGET_URL"), required("PASPM_RECOVERY_USER"), required("PASPM_RECOVERY_PASSWORD"));
             var restored = DriverManager.getConnection(required("PASPM_RECOVERY_RESTORE_URL"), credentials.username(), credentials.password())) {
            generate(target, restored, Path.of(args[0]), args[2], Path.of(args[1]), credentials);
        }
    }
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null) throw new IllegalArgumentException("Missing " + name);
        return value;
    }
}

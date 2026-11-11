package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;

/**
 * Opt-in (paspm.test.multijvm) deployment acceptance of Flyway V18/V19 with real Backend processes on MySQL 8.4:
 * simultaneous first start on an empty schema, upgrade of a V17 schema holding in-flight scheduler rows, and (when
 * {@code -Dpaspm.test.legacy-jar=<origin/dev jar>} is given) the OLD executable against the NEW schema.
 * The legacy scenario is a characterization: it records what the old code does so the compatibility matrix is measured, not assumed.
 */
@EnabledIfSystemProperty(named = "paspm.test.multijvm", matches = "true")
class MiningSchedulerUpgradeCompatibilityMySqlTest {
    static final String ADMIN_EMAIL = "upgrade-admin@example.test", ADMIN_PASSWORD = "Upgrade-Admin-123";
    static final String PRINCIPAL_EMAIL = "system-scheduler@paspm.invalid";
    record Backend(Process process, int port, Path log) {}
    final List<Backend> backends = new ArrayList<>();
    final List<String> evidence = new ArrayList<>();
    TestRestTemplate http = new TestRestTemplate();
    String storeZone = "Asia/Ho_Chi_Minh";
    /** The bootstrap administrator is created by check-then-insert; two instances created together race on it (the loser exits, pre-existing, unrelated to F05), so only one gets the credentials. */
    boolean bootstrapCredentials = true;

    static JdbcTemplate jdbc(MySqlTestDatabase db) { return new JdbcTemplate(new DriverManagerDataSource(db.url(), db.user(), db.password())); }
    interface Condition { boolean check() throws Exception; }
    static void waitFor(Condition condition, int millis, String what) throws Exception {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (System.nanoTime() < end) { if (condition.check()) return; Thread.sleep(50); }
        fail("Timed out: " + what);
    }
    /** Launches a Backend and returns at once; {@code legacyJar} non-null runs the OLD executable instead of the current classes. */
    Backend spawn(Path dir, MySqlTestDatabase db, String legacyJar, Boolean dispatch) throws Exception {
        Files.createDirectories(dir);
        int port; try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>(List.of(java));
        if (legacyJar == null) command.addAll(List.of("-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")), MiningMultiJvmBackend.class.getName(), "--spring.main.allow-bean-definition-overriding=true", "--paspm.store-time-zone=" + storeZone));
        else command.addAll(List.of("-jar", legacyJar));
        command.addAll(List.of("--server.port=" + port, "--paspm.avatar.directory=/tmp/paspm-test-avatars"));
        if (dispatch != null) command.add("--paspm.mining.schedule-dispatch-enabled=" + dispatch);   // null: the shipped default, nothing configured
        var pb = new ProcessBuilder(command);
        var env = pb.environment();
        env.put("DB_URL", db.url()); env.put("DB_USER", db.user()); env.put("DB_PASSWORD", db.password());
        env.put("JWT_SECRET", "01234567890123456789012345678901"); env.put("OTP_SECRET", "01234567890123456789012345678901");
        if (bootstrapCredentials) { env.put("BOOTSTRAP_EMAIL", ADMIN_EMAIL); env.put("BOOTSTRAP_PASSWORD", ADMIN_PASSWORD); }
        env.put("PASPM_TEST_BARRIER_DIR", dir.toString());
        env.put("PASPM_MINING_SPMF_JAR", System.getProperty("paspm.test.spmf-jar")); env.put("PASPM_MINING_JAVA_EXECUTABLE", System.getProperty("paspm.test.spmf-java"));
        Path log = dir.resolve("backend.log");
        pb.redirectErrorStream(true).redirectOutput(log.toFile());
        var backend = new Backend(pb.start(), port, log);
        backends.add(backend);
        return backend;
    }
    boolean awaitUp(Backend b, int millis) throws Exception {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (System.nanoTime() < end) {
            if (!b.process.isAlive()) return false;
            try { if (http.getForEntity(url(b) + "/api/users", String.class).getStatusCode().value() == 401) { http = new TestRestTemplate(); return true; } } catch (Exception ignored) { }   // fresh client: a pooled keep-alive connection can be closed by the server while another slow Backend is still starting
            Thread.sleep(100);
        }
        return false;
    }
    String url(Backend b) { return "http://127.0.0.1:" + b.port; }
    HttpHeaders admin(Backend b, JdbcTemplate jdbc) throws Exception {
        waitFor(() -> jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE email=? AND status='ACTIVE'", Integer.class, ADMIN_EMAIL) == 1, 30000, "bootstrap administrator created (runs after the web server is up)");
        var body = Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD);
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        var login = http.exchange(url(b) + "/api/auth/login", HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
        assertTrue(login.getStatusCode().is2xxSuccessful(), "bootstrap administrator login: " + login.getStatusCode());
        var h = new HttpHeaders(); h.setBearerAuth(login.getBody().path("accessToken").asText()); h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
    String get(Backend b, HttpHeaders h, String path) {
        var r = http.exchange(url(b) + path, HttpMethod.GET, new HttpEntity<>(h), String.class);
        return r.getStatusCode().value() + " " + r.getBody();
    }
    void killAll() { for (var b : backends) if (b.process.isAlive()) { b.process.destroyForcibly(); try { b.process.waitFor(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } } }
    void saveEvidence() throws Exception {
        String destination = System.getenv("PASPM_MULTIJVM_EVIDENCE_FILE");
        if (destination != null) { Path p = Path.of(destination); Files.createDirectories(p.getParent()); Files.write(p, evidence, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND); }
    }
    static int principals(JdbcTemplate jdbc) { return jdbc.queryForObject("SELECT COUNT(*) FROM app_user u JOIN role r ON r.role_id=u.role_id WHERE r.code='SYSTEM_SCHEDULER'", Integer.class); }

    @Test void twoBackendsStartingTogetherOnAnEmptySchemaApplyV1ToV19OnceAndCreateOneStableSchedulerPrincipal() throws Exception {
        var db = MySqlTestDatabase.create(); var jdbc = jdbc(db);
        Path base = Files.createTempDirectory("paspm-fresh-start-");
        try {
            var a = spawn(base.resolve("a"), db, null, false);
            bootstrapCredentials = false;
            var b = spawn(base.resolve("b"), db, null, false);   // launched before either is ready: they race on Flyway
            bootstrapCredentials = true;
            assertTrue(awaitUp(a, 120000), "A up"); assertTrue(awaitUp(b, 120000), "B up");
            assertEquals(21, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL", Integer.class));
            assertEquals(21, jdbc.queryForObject("SELECT COUNT(DISTINCT version) FROM flyway_schema_history WHERE version IS NOT NULL", Integer.class));
            assertEquals(1, principals(jdbc));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM role WHERE code='SYSTEM_SCHEDULER'", Integer.class));
            int id = jdbc.queryForObject("SELECT u.user_id FROM app_user u JOIN role r ON r.role_id=u.role_id WHERE r.code='SYSTEM_SCHEDULER'", Integer.class);
            var h = admin(a, jdbc);
            assertFalse(get(a, h, "/api/users?size=100").contains("system-scheduler"), "hidden from the user list");
            assertFalse(get(a, h, "/api/roles").contains("SYSTEM_SCHEDULER"), "hidden from the role list");
            assertTrue(get(a, h, "/api/users/" + id).startsWith("404"), "not addressable through account management");
            a.process.destroyForcibly(); b.process.destroyForcibly(); a.process.waitFor(10, TimeUnit.SECONDS); b.process.waitFor(10, TimeUnit.SECONDS);
            var again = spawn(base.resolve("c"), db, null, false);
            assertTrue(awaitUp(again, 120000), "restart");
            assertEquals(1, principals(jdbc));
            assertEquals(id, jdbc.queryForObject("SELECT u.user_id FROM app_user u JOIN role r ON r.role_id=u.role_id WHERE r.code='SYSTEM_SCHEDULER'", Integer.class), "same identity after restart");
            assertEquals(21, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL", Integer.class), "restart applies nothing");
            evidence.add("fresh_simultaneous_start A_pid=" + a.process.pid() + " B_pid=" + b.process.pid() + " flyway_versions=19 principals=1 principal_id=" + id + " hidden_from_users_and_roles=true stable_after_restart=true PASS");
        } finally { killAll(); saveEvidence(); }
    }

    @Test void upgradeOfAV17SchemaWithInFlightSchedulerRowsIsHandledSafelyByTheNewBackend() throws Exception {
        var db = MySqlTestDatabase.create(); var jdbc = jdbc(db);
        Path base = Files.createTempDirectory("paspm-upgrade-");
        try {
            Flyway.configure().dataSource(db.url(), db.user(), db.password()).target("17").load().migrate();   // the schema of origin/dev
            jdbc.update("INSERT INTO store(store_id,name) VALUES (91,'Upgrade store')");
            jdbc.update("INSERT INTO app_user(user_id,role_id,email,name,status) SELECT 91,role_id,'human91@example.test','Human admin','ACTIVE' FROM role WHERE code='SYSTEM_ADMIN'");
            jdbc.update("INSERT INTO mining_config_version(config_version,algorithm,min_util_mode,min_util_value,changed_by) VALUES (1,'EFIM','ABSOLUTE',10,91)");
            jdbc.update("UPDATE mining_config_head SET config_version=1 WHERE config_id=1");
            jdbc.update("INSERT INTO mining_schedule(schedule_id,store_id,created_by,updated_by,frequency,day_of_week,local_time,time_zone,lookback_days,enabled,next_fire_at) VALUES (91,91,91,91,'WEEKLY',1,'09:00','Asia/Ho_Chi_Minh',7,TRUE,'2030-01-07 02:00:00')");
            jdbc.update("INSERT INTO utility_snapshot(snapshot_id,store_id,created_by,range_start,range_end,as_of_date,config_snapshot,source_hash,matrix_hash,total_weighted_utility,rounding_scale,status) VALUES (91,91,91,'2026-01-01','2026-01-01','2026-01-01','{}',?,?,0,6,'READY')", "a".repeat(64), "b".repeat(64));
            jdbc.update("INSERT INTO mining_request(request_id,store_id,requested_by,utility_snapshot_id,request_key,payload_hash,algorithms,min_util_mode,min_util_value,min_util_spmf,input_snapshot_hash,frozen_input,utility_config_snapshot,status,comparison_status) VALUES (91,91,91,91,'00000000-0000-0000-0000-000000000091',?,'EFIM','ABSOLUTE',1,1,?,'{}','{}','COMPLETED','NOT_REQUESTED')", "c".repeat(64), "d".repeat(64));
            String common = "INSERT INTO mining_schedule_occurrence(occurrence_id,schedule_id,store_id,scheduled_at,schedule_version,config_version,request_key,status,actor_id,range_start,range_end,analysis_date,time_zone,compare_algorithms";
            String tail = ") VALUES (?,91,91,?,1,1,?,?,91,'2026-10-19','2026-10-25','2026-10-26','Asia/Ho_Chi_Minh',FALSE";
            jdbc.update(common + ",snapshot_id,request_id" + tail + ",91,91)", 911, "2026-09-07 02:00:00", "00000000-0000-0000-0000-000000000911", "DISPATCHED");   // committed request, finished
            jdbc.update(common + tail + ")", 912, "2026-10-26 02:00:00", "00000000-0000-0000-0000-000000000912", "PENDING");                                         // reserved before D9: owned by the human admin
            jdbc.update(common + tail + ")", 913, "2026-10-19 02:00:00", "00000000-0000-0000-0000-000000000913", "PROCESSING");                                      // unowned PROCESSING: never assumed abandoned
            jdbc.update(common + ",failure_code" + tail + ",'HTTP_400')", 914, "2026-10-12 02:00:00", "00000000-0000-0000-0000-000000000914", "FAILED");
            jdbc.update(common + tail + ")", 915, "2026-10-05 02:00:00", "00000000-0000-0000-0000-000000000915", "COMPLETED");
            String history = jdbc.queryForObject("SELECT GROUP_CONCAT(version ORDER BY installed_rank) FROM flyway_schema_history WHERE version IS NOT NULL", String.class);
            var snapshot = jdbc.queryForObject("SELECT CONCAT(COUNT(*),'/',MIN(user_id),'/',MAX(user_id)) FROM app_user", String.class);

            var a = spawn(base.resolve("a"), db, null, true);
            assertTrue(awaitUp(a, 120000), "new Backend on the upgraded schema");
            waitFor(() -> "FAILED".equals(jdbc.queryForObject("SELECT status FROM mining_schedule_occurrence WHERE occurrence_id=912", String.class))
                && "COMPLETED".equals(jdbc.queryForObject("SELECT status FROM mining_schedule_occurrence WHERE occurrence_id=911", String.class)), 90000, "dispatcher handled the legacy rows");
            assertEquals(21, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version IS NOT NULL AND success=1", Integer.class));
            var legacyPending = jdbc.queryForMap("SELECT status,failure_code,failure_message,snapshot_id,request_id,attempt_count FROM mining_schedule_occurrence WHERE occurrence_id=912");
            assertEquals("HTTP_403", legacyPending.get("failure_code"), "a pre-D9 occurrence owned by a human is refused, never executed as that human");
            assertNotNull(legacyPending.get("failure_message")); assertNull(legacyPending.get("snapshot_id")); assertNull(legacyPending.get("request_id"));
            var unowned = jdbc.queryForMap("SELECT status,lease_owner,attempt_count FROM mining_schedule_occurrence WHERE occurrence_id=913");
            assertEquals("PROCESSING", unowned.get("status")); assertNull(unowned.get("lease_owner")); assertEquals(0, ((Number) unowned.get("attempt_count")).intValue());
            assertEquals("FAILED/HTTP_400", jdbc.queryForObject("SELECT CONCAT(status,'/',failure_code) FROM mining_schedule_occurrence WHERE occurrence_id=914", String.class));
            assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM mining_schedule_occurrence WHERE occurrence_id=915", String.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mining_request", Integer.class), "no new request"); assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot", Integer.class), "no new snapshot");
            assertEquals(1, principals(jdbc));
            String after = jdbc.queryForObject("SELECT CONCAT(COUNT(*),'/',MIN(user_id),'/',MAX(user_id)) FROM app_user", String.class);
            assertNotEquals(snapshot, after, "the principal and the bootstrap administrator were added");
            assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM app_user WHERE user_id=91", String.class), "existing accounts untouched");
            var h = admin(a, jdbc);
            String listing = get(a, h, "/api/system/mining/schedules/91/occurrences?pageSize=50");
            assertTrue(listing.startsWith("200"), listing); assertTrue(listing.contains("\"status\":\"FAILED\"") && listing.contains("not the scheduler principal"), "history shows the visible cause");
            evidence.add("upgrade_v17_inflight flyway_history_before=[" + history + "] now=19 DISPATCHED+request->COMPLETED PENDING(human actor)->FAILED/HTTP_403 unowned_PROCESSING_untouched=true FAILED_COMPLETED_unchanged=true new_requests=0 new_snapshots=0 principals=1 PASS");
        } finally { killAll(); saveEvidence(); }
    }

    @Test void withTheShippedDefaultConfigurationARealBackendRefusesActivationAndNeverRunsAnEnabledDueScheduleButManualMiningStillWorks() throws Exception {
        var db = MySqlTestDatabase.create(); var jdbc = jdbc(db);
        Path base = Files.createTempDirectory("paspm-default-off-");
        try {
            var a = spawn(base.resolve("a"), db, null, null);                                          // no schedule-dispatch property at all
            assertTrue(awaitUp(a, 120000), "Backend up");
            jdbc.update("INSERT INTO store(store_id,name) VALUES (77,'Default off store')");
            var h = admin(a, jdbc);
            int adminId = jdbc.queryForObject("SELECT user_id FROM app_user WHERE email=?", Integer.class, ADMIN_EMAIL);
            jdbc.update("INSERT INTO mining_config_version(config_version,algorithm,min_util_mode,min_util_value,changed_by) VALUES (1,'EFIM','ABSOLUTE',10,?)", adminId);
            jdbc.update("UPDATE mining_config_head SET config_version=1 WHERE config_id=1");
            // a row that was enabled by other means (restore, SQL) and is long overdue: the shipped configuration must still leave it alone
            jdbc.update("INSERT INTO mining_schedule(schedule_id,store_id,created_by,updated_by,frequency,day_of_week,local_time,time_zone,lookback_days,enabled,next_fire_at) VALUES (77,77,?,?,'WEEKLY',1,'09:00','Asia/Ho_Chi_Minh',7,TRUE,?)",
                adminId, adminId, Timestamp.from(Instant.now().minusSeconds(86400L * 14)));
            String body = "{\"schedule\":{\"storeId\":77,\"frequency\":\"WEEKLY\",\"dayOfWeek\":1,\"localTime\":\"09:00\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"lookbackDays\":7,\"compareAlgorithms\":false},\"enabled\":true,\"expectedVersion\":1}";
            var activation = http.exchange(url(a) + "/api/system/mining/schedules/77", HttpMethod.PUT, new HttpEntity<>(body, h), String.class);
            assertEquals(503, activation.getStatusCode().value());
            assertEquals(1L, jdbc.queryForObject("SELECT schedule_version FROM mining_schedule WHERE schedule_id=77", Long.class), "refused before any write");
            Thread.sleep(20000);                                                                     // several 5 s polls would have happened if a dispatcher were running
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mining_schedule_occurrence", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot", Integer.class)); assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mining_request", Integer.class));
            assertFalse(Files.readString(a.log).contains("paspm-schedule-dispatch"), "no dispatcher thread activity");
            var manualConfig = http.exchange(url(a) + "/api/system/mining/config", HttpMethod.GET, new HttpEntity<>(h), String.class);
            assertEquals(200, manualConfig.getStatusCode().value(), "manual F05 administration still works");
            evidence.add("default_off real_backend activation=503 schedule_version_unchanged=true overdue_enabled_row_occurrences=0 snapshots=0 requests=0 manual_f05_config_api=200 PASS");
        } finally { killAll(); saveEvidence(); }
    }

    @Test void aProcessThatWouldRunTheSchedulerInAnotherStoreZoneRefusesToStartAndTheSameZoneIsHarmlessWhileTheDispatcherIsOff() throws Exception {
        var db = MySqlTestDatabase.create();
        Path base = Files.createTempDirectory("paspm-zone-guard-");
        try {
            storeZone = "UTC";
            var refused = spawn(base.resolve("on"), db, null, true);
            assertTrue(refused.process.waitFor(120, TimeUnit.SECONDS), "the process must exit by itself");
            assertNotEquals(0, refused.process.exitValue());
            assertTrue(Files.readString(refused.log).contains("requires paspm.store-time-zone=Asia/Ho_Chi_Minh"), "the log names the reason");
            var off = spawn(base.resolve("off"), db, null, false);
            assertTrue(awaitUp(off, 120000), "dispatcher off: the Store zone is not checked");
            evidence.add("zone_guard dispatcher_on_with_UTC=exit_" + refused.process.exitValue() + " reason_logged=true dispatcher_off_with_UTC=starts PASS");
        } finally { storeZone = "Asia/Ho_Chi_Minh"; killAll(); saveEvidence(); }
    }

    /** Needs -Dpaspm.test.legacy-jar=<jar built from origin/dev>. */
    @EnabledIfSystemProperty(named = "paspm.test.legacy-jar", matches = ".+")
    @Test void characterizeTheOldBackendRunningOnTheNewSchema() throws Exception {
        String legacy = System.getProperty("paspm.test.legacy-jar");
        var db = MySqlTestDatabase.create(); var jdbc = jdbc(db);
        Path base = Files.createTempDirectory("paspm-legacy-on-new-");
        try {
            Flyway.configure().dataSource(db.url(), db.user(), db.password()).load().migrate();                   // the NEW schema (V19)
            jdbc.update("INSERT INTO store(store_id,name) VALUES (91,'Legacy store')");
            jdbc.update("INSERT INTO app_user(user_id,role_id,email,name,status) SELECT 91,role_id,'human91@example.test','Human admin','ACTIVE' FROM role WHERE code='SYSTEM_ADMIN'");
            jdbc.update("INSERT INTO mining_config_version(config_version,algorithm,min_util_mode,min_util_value,changed_by) VALUES (1,'EFIM','ABSOLUTE',10,91)");
            jdbc.update("UPDATE mining_config_head SET config_version=1 WHERE config_id=1");
            jdbc.update("INSERT INTO mining_schedule(schedule_id,store_id,created_by,updated_by,frequency,day_of_week,local_time,time_zone,lookback_days,enabled,next_fire_at) VALUES (91,91,91,91,'WEEKLY',1,'09:00','Asia/Ho_Chi_Minh',30,TRUE,'2030-01-07 02:00:00')");
            jdbc.update("INSERT INTO mining_schedule_occurrence(schedule_id,store_id,scheduled_at,schedule_version,config_version,request_key,status,skip_reason) VALUES (91,91,'2026-10-12 02:00:00',1,1,'00000000-0000-0000-0000-000000000921','SKIPPED','MISSED_SUPERSEDED_BY_LATER_OCCURRENCE')");
            var old = spawn(base.resolve("old"), db, legacy, false);
            boolean up = awaitUp(old, 120000);
            if (!up) {
                String log = Files.readString(old.log);
                evidence.add("legacy_on_new_schema startup=REFUSED reason=" + (log.contains("not resolved locally") ? "flyway_validate_unresolved_applied_migration" : "other") + " PASS(characterized)");
                return;
            }
            var h = admin(old, jdbc);
            String users = get(old, h, "/api/users?size=100"), roles = get(old, h, "/api/roles"), occurrences = get(old, h, "/api/system/mining/schedules/91/occurrences?pageSize=50");
            boolean exposesPrincipal = users.contains("system-scheduler") && roles.contains("SYSTEM_SCHEDULER");
            old.process.destroyForcibly(); old.process.waitFor(10, TimeUnit.SECONDS);
            // the old executable with the dispatcher switched on and one missed fire
            jdbc.update("UPDATE mining_schedule SET next_fire_at=? WHERE schedule_id=91", Timestamp.from(Instant.now().minusSeconds(120)));
            var dispatching = spawn(base.resolve("old-dispatch"), db, legacy, true);
            assertTrue(awaitUp(dispatching, 120000), "legacy with dispatcher");
            waitFor(() -> jdbc.queryForObject("SELECT COUNT(*) FROM mining_schedule_occurrence WHERE schedule_id=91 AND status<>'SKIPPED'", Integer.class) > 0, 60000, "legacy reserved an occurrence");
            Integer actor = jdbc.queryForObject("SELECT actor_id FROM mining_schedule_occurrence WHERE schedule_id=91 AND status<>'SKIPPED' ORDER BY occurrence_id LIMIT 1", Integer.class);
            int principal = jdbc.queryForObject("SELECT u.user_id FROM app_user u JOIN role r ON r.role_id=u.role_id WHERE r.code='SYSTEM_SCHEDULER'", Integer.class);
            evidence.add("legacy_on_new_schema startup=OK legacy_account_api_exposes_scheduler_principal=" + exposesPrincipal + " legacy_occurrence_history_with_SKIPPED=\"" + occurrences.substring(0, Math.min(60, occurrences.length())).replaceAll("\\s+", " ") + "\""
                + " legacy_dispatcher_actor_is_human=" + (actor != null && actor == 91) + " legacy_dispatcher_actor_is_principal=" + (actor != null && actor == principal) + " PASS(characterized)");
        } finally { killAll(); saveEvidence(); }
    }
}

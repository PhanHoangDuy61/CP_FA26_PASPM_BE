package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;
import vn.edu.fpt.paspm.user.UserRepository;

/**
 * D10 acceptance, opt-in (paspm.test.multijvm): independent Backend processes with the scheduler explicitly enabled share one MySQL 8.4 schema and
 * the real SPMF. Proves multi-instance reservation/processing correctness and recovery from {@code kill -9} at the two commit-relevant points
 * (inside the occurrence transaction; after the request committed). Lease durations are real except the 300 s occurrence lease, whose
 * expiry is simulated by moving {@code lease_until} into the past after the process is dead.
 */
@EnabledIfSystemProperty(named = "paspm.test.multijvm", matches = "true")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "paspm.jwt-secret=01234567890123456789012345678901", "paspm.otp-secret=01234567890123456789012345678901",
    "paspm.bootstrap-email=", "paspm.bootstrap-password=", "paspm.avatar.directory=/tmp/paspm-test-avatars"
})
class MiningSchedulerMultiJvmMySqlTest {
    static final ZoneId HCM = ZoneId.of("Asia/Ho_Chi_Minh");
    static MySqlTestDatabase db;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { db = MySqlTestDatabase.create(); db.register(registry); }
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired MiningWorker parentWorker;
    @Autowired TestRestTemplate parentHttp;
    final TestRestTemplate http = new TestRestTemplate();
    record Node(Process process, int port, Path dir, Path log) {}
    final List<Node> nodes = new ArrayList<>();
    final List<String> evidence = new ArrayList<>();
    HttpHeaders admin;
    int store;

    interface Condition { boolean check() throws Exception; }
    void waitFor(Condition condition, int millis, String what) throws Exception {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (System.nanoTime() < end) { if (condition.check()) return; Thread.sleep(25); }
        fail("Timed out: " + what);
    }
    HttpHeaders headers(String token) { var h = new HttpHeaders(); h.setBearerAuth(token); h.setContentType(MediaType.APPLICATION_JSON); return h; }
    String url(Node n) { return "http://127.0.0.1:" + n.port; }

    Node start(Path dir) throws Exception { return start(dir, db, java.util.Map.of()); }
    /** Starts a Backend JVM against {@code target}; {@code overrides} replaces environment values (for example an unusable SPMF jar). */
    Node start(Path dir, MySqlTestDatabase target, java.util.Map<String, String> overrides) throws Exception {
        Files.createDirectories(dir);
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        String cp = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", cp, MiningMultiJvmBackend.class.getName(),
            "--server.port=" + port, "--spring.main.allow-bean-definition-overriding=true", "--paspm.bootstrap-email=", "--paspm.bootstrap-password=",
            "--paspm.avatar.directory=/tmp/paspm-test-avatars", "--paspm.mining.schedule-dispatch-enabled=true", "--paspm.store-time-zone=Asia/Ho_Chi_Minh");
        var env = pb.environment();
        env.put("DB_URL", target.url()); env.put("DB_USER", target.user()); env.put("DB_PASSWORD", target.password());
        env.put("JWT_SECRET", "01234567890123456789012345678901"); env.put("OTP_SECRET", "01234567890123456789012345678901");
        env.put("PASPM_TEST_BARRIER_DIR", dir.toString());
        env.put("PASPM_MINING_SPMF_JAR", System.getProperty("paspm.test.spmf-jar")); env.put("PASPM_MINING_JAVA_EXECUTABLE", System.getProperty("paspm.test.spmf-java"));
        env.putAll(overrides);
        Path log = dir.resolve("backend.log");
        pb.redirectErrorStream(true).redirectOutput(log.toFile());
        Process p = pb.start();
        var node = new Node(p, port, dir, log);
        nodes.add(node);
        waitFor(() -> {
            if (!p.isAlive()) throw new IllegalStateException("Backend exited; see " + log);
            try { return http.getForEntity(url(node) + "/api/mining/requests", String.class).getStatusCode().value() == 401; } catch (Exception e) { return false; }
        }, 90000, "Backend " + p.pid() + " startup");
        return node;
    }

    ZonedDateTime recentFire() { return Instant.now().atZone(HCM).minusMinutes(2).withSecond(0).withNano(0); }
    void fixture(ZonedDateTime fire) {
        parentWorker.close();
        jdbc.update("INSERT INTO store(name) VALUES ('Scheduler multi JVM')");
        store = jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        var s = users.createPendingUser("Sched admin", UUID.randomUUID() + "@example.test", "SYSTEM_ADMIN", null, null, null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?", s.id());
        admin = headers(auth.token(users.findById(s.id())).accessToken());
        var products = new ArrayList<Integer>();
        for (int i = 1; i <= 7; i++) {
            jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,'Frozen','each',FALSE,100,0,'ACTIVE')", store, "SJ" + i);
            products.add(jdbc.queryForObject("SELECT MAX(product_id) FROM product", Integer.class));
        }
        for (String line : SpmfMiningEngineTest.INPUT.split("\n")) {
            var parts = line.split(":");
            String key = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO sales_transaction(store_id,source_system,source_order_id,occurred_at,status,f03_eligible,total_amount,content_sha256) VALUES (?,'MJ',?,?,'COMPLETED',TRUE,100,?)",
                store, key, Timestamp.from(fire.minusDays(2).toInstant()), "a".repeat(64));
            int tx = jdbc.queryForObject("SELECT transaction_id FROM sales_transaction WHERE store_id=? AND source_order_id=?", Integer.class, store, key);
            var ids = parts[0].split(" "); var util = parts[2].split(" ");
            for (int i = 0; i < ids.length; i++)
                jdbc.update("INSERT INTO transaction_item(store_id,transaction_id,product_id,quantity,unit_price,unit_cost) VALUES (?,?,?,1,?,0)", store, tx, products.get(Integer.parseInt(ids[i]) - 1), Integer.parseInt(util[i]));
        }
        var policy = parentHttp.exchange("/api/system/mining/config", HttpMethod.PUT, new HttpEntity<>("{\"algorithm\":\"HUI_MINER\",\"minUtilMode\":\"ABSOLUTE\",\"minUtilValue\":30,\"expectedVersion\":0}", admin), JsonNode.class);
        assertEquals(HttpStatus.OK, policy.getStatusCode());
    }
    /** Creates and activates a weekly schedule through the given node's public API; returns its id. */
    long activeSchedule(Node via, ZonedDateTime fire) {
        String schedule = "{\"storeId\":" + store + ",\"frequency\":\"WEEKLY\",\"dayOfWeek\":" + fire.getDayOfWeek().getValue() + ",\"localTime\":\"" + fire.toLocalTime()
            + "\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"lookbackDays\":7,\"compareAlgorithms\":true}";
        var created = http.exchange(url(via) + "/api/system/mining/schedules", HttpMethod.POST, new HttpEntity<>(schedule, admin), JsonNode.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        long id = created.getBody().path("scheduleId").asLong();
        var activated = http.exchange(url(via) + "/api/system/mining/schedules/" + id, HttpMethod.PUT, new HttpEntity<>("{\"schedule\":" + schedule + ",\"enabled\":true,\"expectedVersion\":1}", admin), JsonNode.class);
        assertEquals(HttpStatus.OK, activated.getStatusCode(), String.valueOf(activated.getBody()));
        return id;
    }
    void due(long id, Instant cursor) { jdbc.update("UPDATE mining_schedule SET next_fire_at=? WHERE schedule_id=?", Timestamp.from(cursor), id); }
    /** Status of the one occurrence that is not SKIPPED, or null while none has been reserved yet. */
    String status(long id) { return jdbc.queryForList("SELECT status FROM mining_schedule_occurrence WHERE schedule_id=? AND status<>'SKIPPED'", String.class, id).stream().findFirst().orElse(null); }
    void saveEvidence() throws Exception {
        String destination = System.getenv("PASPM_MULTIJVM_EVIDENCE_FILE");
        if (destination != null) { Path p = Path.of(destination); Files.createDirectories(p.getParent()); Files.write(p, evidence, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND); }
    }
    void killAll() { for (Node n : nodes) if (n.process.isAlive()) { n.process.destroyForcibly(); try { n.process.waitFor(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } } }

    @Test void twoSchedulerInstancesReserveSkipAndRunEachFireExactlyOnce() throws Exception {
        var fire = recentFire();
        fixture(fire);
        Path base = Files.createTempDirectory("paspm-sched-multijvm-");
        try {
            Node a = start(base.resolve("a")), b = start(base.resolve("b"));
            long id = activeSchedule(a, fire);
            // two instances poll the same overdue schedule: three missed weekly fires and the current one
            due(id, fire.minusWeeks(3).toInstant());
            waitFor(() -> "COMPLETED".equals(status(id)), 120000, "scheduled occurrence completed");
            Thread.sleep(12000);                                    // more than two polls of both dispatchers: nothing may be duplicated
            assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM mining_schedule_occurrence WHERE schedule_id=?", Integer.class, id));
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM mining_schedule_occurrence WHERE schedule_id=? AND status='SKIPPED' AND skip_reason IS NOT NULL AND request_id IS NULL AND snapshot_id IS NULL", Integer.class, id));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mining_schedule_occurrence WHERE schedule_id=? AND status='COMPLETED' AND attempt_count=1", Integer.class, id));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=?", Integer.class, store));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot WHERE store_id=?", Integer.class, store));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mining_run WHERE store_id=? AND status='COMPLETED'", Integer.class, store));
            assertEquals(16, jdbc.queryForObject("SELECT COUNT(*) FROM high_utility_pattern p JOIN mining_run r ON p.run_id=r.run_id WHERE r.store_id=?", Integer.class, store));
            evidence.add("scheduler_concurrency A_pid=" + a.process.pid() + " B_pid=" + b.process.pid() + " schedule=" + id + " occurrences=4 skipped=3 completed=1 requests=1 snapshots=1 runs=2 patterns=16 PASS");
        } finally { killAll(); saveEvidence(); }
    }

    @Test void killedInsideTheOccurrenceTransactionLeavesNoSnapshotAndAnotherInstanceCompletesItAfterLeaseExpiry() throws Exception {
        var fire = recentFire();
        fixture(fire);
        Path base = Files.createTempDirectory("paspm-sched-multijvm-");
        try {
            Node c = start(base.resolve("c"));
            long id = activeSchedule(c, fire);
            Files.writeString(c.dir.resolve("hold-occurrence"), "hold");
            due(id, fire.toInstant());
            waitFor(() -> Files.exists(c.dir.resolve("occurrence-held")), 60000, "occurrence transaction barrier (snapshot written, request not yet committed)");
            var held = jdbc.queryForMap("SELECT status,lease_owner,lease_fence,attempt_count,snapshot_id,request_id FROM mining_schedule_occurrence WHERE schedule_id=?", id);
            assertEquals("PROCESSING", held.get("status"));
            assertNotNull(held.get("lease_owner"));
            assertEquals(1L, ((Number) held.get("lease_fence")).longValue());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot WHERE store_id=?", Integer.class, store), "the open transaction's snapshot is not visible to other connections");
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=?", Integer.class, store));
            c.process.destroyForcibly();
            assertTrue(c.process.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot WHERE store_id=?", Integer.class, store), "MySQL rolled the dead process's snapshot back: no orphan");
            assertEquals("PROCESSING", status(id), "the dead owner's lease is still valid for 300 s");

            Node b = start(base.resolve("b"));
            Thread.sleep(12000);
            assertEquals("PROCESSING", status(id), "another instance must not steal an unexpired lease");
            assertEquals(1, jdbc.queryForObject("SELECT attempt_count FROM mining_schedule_occurrence WHERE schedule_id=?", Integer.class, id));
            jdbc.update("UPDATE mining_schedule_occurrence SET lease_until=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP(6)) WHERE schedule_id=?", id);   // the 300 s lease runs out
            waitFor(() -> "COMPLETED".equals(status(id)), 120000, "occurrence recovered by the surviving instance");
            var row = jdbc.queryForMap("SELECT lease_fence,attempt_count,snapshot_id,request_id,failure_code FROM mining_schedule_occurrence WHERE schedule_id=?", id);
            assertEquals(2L, ((Number) row.get("lease_fence")).longValue(), "fencing token advanced for the new owner");
            assertEquals(2, ((Number) row.get("attempt_count")).intValue());
            assertNull(row.get("failure_code"));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot WHERE store_id=?", Integer.class, store));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=?", Integer.class, store));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mining_run WHERE store_id=? AND status='COMPLETED'", Integer.class, store));
            evidence.add("scheduler_crash_in_transaction killed_C_pid=" + c.process.pid() + " B_pid=" + b.process.pid() + " schedule=" + id + " snapshots_after_kill=0 lease_not_stolen=true new_fence=2 attempts=2 requests=1 snapshots=1 PASS (300 s lease expiry simulated)");
        } finally { killAll(); saveEvidence(); }
    }

    @Test void killedAfterTheRequestCommittedTheRequestIsRecoveredAndTheOccurrenceIsReconciled() throws Exception {
        var fire = recentFire();
        fixture(fire);
        Path base = Files.createTempDirectory("paspm-sched-multijvm-");
        try {
            Node c = start(base.resolve("c"));
            long id = activeSchedule(c, fire);
            Files.writeString(c.dir.resolve("hold-before-spmf"), "hold");
            due(id, fire.toInstant());
            waitFor(() -> Files.exists(c.dir.resolve("before-spmf")), 60000, "request committed and worker about to start SPMF");
            var committed = jdbc.queryForMap("SELECT status,request_id,snapshot_id FROM mining_schedule_occurrence WHERE schedule_id=?", id);
            long request = ((Number) committed.get("request_id")).longValue();
            assertTrue(List.of("DISPATCHED", "PROCESSING").contains(committed.get("status")));
            long fenceBefore = jdbc.queryForObject("SELECT lease_fence FROM mining_request WHERE request_id=?", Long.class, request);
            c.process.destroyForcibly();
            assertTrue(c.process.waitFor(10, TimeUnit.SECONDS));
            Node b = start(base.resolve("b"));
            // disabling the schedule now must not stop the committed request (D8)
            var disabled = http.exchange(url(b) + "/api/system/mining/schedules/" + id, HttpMethod.PUT, new HttpEntity<>("{\"schedule\":{\"storeId\":" + store + ",\"frequency\":\"WEEKLY\",\"dayOfWeek\":" + fire.getDayOfWeek().getValue()
                + ",\"localTime\":\"" + fire.toLocalTime() + "\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"lookbackDays\":7,\"compareAlgorithms\":true},\"enabled\":false,\"expectedVersion\":2}", admin), JsonNode.class);
            assertEquals(HttpStatus.OK, disabled.getStatusCode());
            waitFor(() -> "COMPLETED".equals(status(id)), 150000, "request recovered by the surviving instance and occurrence reconciled");
            assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM mining_request WHERE request_id=?", String.class, request));
            assertTrue(jdbc.queryForObject("SELECT lease_fence FROM mining_request WHERE request_id=?", Long.class, request) > fenceBefore);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=?", Integer.class, store));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot WHERE store_id=?", Integer.class, store));
            assertEquals(16, jdbc.queryForObject("SELECT COUNT(*) FROM high_utility_pattern p JOIN mining_run r ON p.run_id=r.run_id WHERE r.store_id=?", Integer.class, store));
            evidence.add("scheduler_crash_after_request_commit killed_C_pid=" + c.process.pid() + " B_pid=" + b.process.pid() + " request=" + request + " schedule_disabled_meanwhile=true occurrence=COMPLETED patterns=16 PASS");
        } finally { killAll(); saveEvidence(); }
    }

    // ------------------------------------------------------------------------------------------------------------------------------
    // Crash-point matrix (acceptance of 2026-11-02). Deterministic freeze points use MySQL named locks inside test-only triggers on the
    // disposable schema (no production code is changed): the Backend blocks inside a statement, is killed with SIGKILL, and a different
    // JVM recovers. No commit boundary is invented: where a step is part of one atomic transaction it is covered by the enclosing test.
    // ------------------------------------------------------------------------------------------------------------------------------
    java.sql.Connection holdLock(String name) throws Exception {
        var c = java.sql.DriverManager.getConnection(db.url(), db.user(), db.password());
        try (var st = c.createStatement(); var rs = st.executeQuery("SELECT GET_LOCK('" + name + "',0)")) { rs.next(); assertEquals(1, rs.getInt(1)); }
        return c;
    }
    int blockedOnUserLock() { return jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.threads WHERE processlist_state='User lock'", Integer.class); }
    String blockedStatement() {
        var rows = jdbc.queryForList("SELECT e.sql_text FROM performance_schema.threads t JOIN performance_schema.events_statements_current e ON e.thread_id=t.thread_id WHERE t.processlist_state='User lock'", String.class);
        return rows.isEmpty() ? "" : rows.get(0).replaceAll("\\s+", " ").substring(0, Math.min(70, rows.get(0).length()));
    }
    /** After the owner JVM is dead its blocked statement can never be committed; returns true when the server noticed the dead peer by itself, otherwise the orphaned statement is KILLed. */
    boolean serverNoticedDisconnect() throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < end) { if (blockedOnUserLock() == 0) return true; Thread.sleep(250); }
        for (long id : jdbc.queryForList("SELECT processlist_id FROM performance_schema.threads WHERE processlist_state='User lock'", Long.class)) jdbc.execute("KILL CONNECTION " + id);
        waitFor(() -> blockedOnUserLock() == 0, 10000, "orphaned statement of the dead Backend killed");
        return false;
    }
    void sigkill(Node n) throws Exception { n.process.destroyForcibly(); assertTrue(n.process.waitFor(10, TimeUnit.SECONDS)); }
    java.util.Map<String, Object> occ(long schedule) { return jdbc.queryForMap("SELECT * FROM mining_schedule_occurrence WHERE schedule_id=? AND status<>'SKIPPED'", schedule); }
    int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
    /** Asserts the invariants of a finished scenario and returns them for the evidence line. */
    String integrity(long schedule, int expectedRequests) {
        int snapshots = count("SELECT COUNT(*) FROM utility_snapshot WHERE store_id=?", store), requests = count("SELECT COUNT(*) FROM mining_request WHERE store_id=?", store);
        int duplicates = count("SELECT COUNT(*) FROM (SELECT request_key FROM mining_request WHERE store_id=? GROUP BY request_key HAVING COUNT(*)>1) d", store)
            + count("SELECT COUNT(*) FROM (SELECT schedule_id,scheduled_at FROM mining_schedule_occurrence WHERE schedule_id=? GROUP BY schedule_id,scheduled_at HAVING COUNT(*)>1) d", schedule);
        int orphans = count("SELECT COUNT(*) FROM utility_snapshot s WHERE s.store_id=? AND NOT EXISTS (SELECT 1 FROM mining_schedule_occurrence o WHERE o.store_id=s.store_id AND o.snapshot_id=s.snapshot_id)", store)
            + count("SELECT COUNT(*) FROM mining_request r WHERE r.store_id=? AND NOT EXISTS (SELECT 1 FROM mining_schedule_occurrence o WHERE o.store_id=r.store_id AND o.request_id=r.request_id)", store);
        assertEquals(expectedRequests, requests); assertEquals(expectedRequests, snapshots);
        assertEquals(0, duplicates, "duplicate occurrences/requests"); assertEquals(0, orphans, "orphan snapshots/requests");
        return "snapshots=" + snapshots + " requests=" + requests + " duplicates=" + duplicates + " orphans=" + orphans;
    }
    ProcessHandle spmfChild(Node n) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        var pattern = java.util.regex.Pattern.compile("SPMF_PROCESS_STARTED [^\\n]*pid=([0-9]+)");
        while (System.nanoTime() < end) {
            var match = pattern.matcher(Files.readString(n.log)); long pid = -1; while (match.find()) pid = Long.parseLong(match.group(1));
            // wait for the real SPMF java (HUI-Miner, no "-c"): on Linux the same pid first runs the Python parent-death launcher, and stopping it before its prctl registration would test the documented startup race, not the safety guarantee
            if (pid > 0) { var child = ProcessHandle.of(pid); if (child.isPresent() && child.get().parent().map(p -> p.pid() == n.process.pid()).orElse(false)
                && child.get().info().arguments().map(args -> java.util.Arrays.asList(args).contains("HUI-Miner") && !java.util.Arrays.asList(args).contains("-c")).orElse(false)) return child.get(); }
            Thread.sleep(2);
        }
        throw new AssertionError("actual SPMF child not observed in the process log and ancestry");
    }
    boolean executing(ProcessHandle p) {
        if (!p.isAlive()) return false;
        Path stat = Path.of("/proc", Long.toString(p.pid()), "stat");
        try { if (Files.exists(stat) && Files.readString(stat).matches("(?s)^\\d+ \\(.*\\) Z .*")) return false; } catch (Exception ignored) { }
        return true;
    }
    void signal(long pid, String signal) throws Exception { assertEquals(0, new ProcessBuilder("kill", "-" + signal, Long.toString(pid)).start().waitFor()); }

    @Test void crashPoint1_killedBeforeTheReservationCommitsLeavesNoTraceAndAnotherInstanceReservesOnce() throws Exception {
        var fire = recentFire();
        fixture(fire);
        Path base = Files.createTempDirectory("paspm-sched-cp1-");
        try (var held = holdLock("paspm-hold-reserve")) {
            jdbc.execute("CREATE TRIGGER trg_hold_reserve BEFORE INSERT ON mining_schedule_occurrence FOR EACH ROW SET @h=GET_LOCK('paspm-hold-reserve',300)");
            Node c = start(base.resolve("c"));
            long id = activeSchedule(c, fire);
            due(id, fire.toInstant());
            waitFor(() -> blockedOnUserLock() == 1, 60000, "reservation blocked inside its transaction (schedule row locked, occurrence INSERT not committed)");
            String statement = blockedStatement();
            assertEquals(0, count("SELECT COUNT(*) FROM mining_schedule_occurrence WHERE schedule_id=?", id));
            sigkill(c);
            boolean noticed = serverNoticedDisconnect();
            assertEquals(0, count("SELECT COUNT(*) FROM mining_schedule_occurrence WHERE schedule_id=?", id), "nothing committed by the dead process");
            assertEquals(fire.toInstant(), jdbc.queryForObject("SELECT next_fire_at FROM mining_schedule WHERE schedule_id=?", Timestamp.class, id).toInstant(), "cursor not advanced: the fire time is not consumed");
            jdbc.execute("DROP TRIGGER trg_hold_reserve"); held.close();
            Node b = start(base.resolve("b"));
            waitFor(() -> "COMPLETED".equals(status(id)), 120000, "recovery instance reserves and completes");
            var row = occ(id);
            assertEquals(1L, ((Number) row.get("lease_fence")).longValue()); assertEquals(1, ((Number) row.get("attempt_count")).intValue());
            assertEquals(1, count("SELECT COUNT(*) FROM mining_schedule_occurrence WHERE schedule_id=?", id));
            String inv = integrity(id, 1);
            evidence.add("crash_point=1_before_reservation_commit killed_pid=" + c.process.pid() + " blocked_in=\"" + statement + "\" committed_before_kill=none server_noticed_disconnect=" + noticed + " recovery_pid=" + b.process.pid()
                + " recovery_owner_fence=" + row.get("lease_fence") + " occurrence=COMPLETED attempts=1 " + inv + " PASS");
        } finally { killAll(); saveEvidence(); }
    }

    @Test void crashPoint2_killedAfterTheReservationCommittedBeforeTheOccurrenceIsClaimed() throws Exception {
        var fire = recentFire();
        fixture(fire);
        Path base = Files.createTempDirectory("paspm-sched-cp2-");
        try (var held = holdLock("paspm-hold-claim")) {
            jdbc.execute("CREATE TRIGGER trg_hold_claim BEFORE UPDATE ON mining_schedule_occurrence FOR EACH ROW BEGIN IF OLD.status='PENDING' AND NEW.status='PROCESSING' THEN SET @h=GET_LOCK('paspm-hold-claim',300); END IF; END");
            Node c = start(base.resolve("c"));
            long id = activeSchedule(c, fire);
            due(id, fire.toInstant());
            waitFor(() -> blockedOnUserLock() == 1, 60000, "claim blocked after the reservation committed");
            String statement = blockedStatement();
            var before = occ(id);
            assertEquals("PENDING", before.get("status")); assertEquals(0L, ((Number) before.get("lease_fence")).longValue()); assertNull(before.get("lease_owner")); assertNull(before.get("snapshot_id")); assertNull(before.get("request_id"));
            sigkill(c);
            boolean noticed = serverNoticedDisconnect();
            assertEquals("PENDING", status(id), "the committed reservation survives, unclaimed");
            jdbc.execute("DROP TRIGGER trg_hold_claim"); held.close();
            Node b = start(base.resolve("b"));
            waitFor(() -> "COMPLETED".equals(status(id)), 120000, "recovery instance claims the committed reservation");
            var row = occ(id);
            assertEquals(1L, ((Number) row.get("lease_fence")).longValue(), "no lease was ever committed by the dead process, so no expiry wait");
            String inv = integrity(id, 1);
            evidence.add("crash_point=2_after_reservation_commit_before_claim killed_pid=" + c.process.pid() + " blocked_in=\"" + statement + "\" committed_before_kill=occurrence_PENDING_fence0 server_noticed_disconnect=" + noticed + " recovery_pid=" + b.process.pid()
                + " recovery_owner_fence=1 occurrence=COMPLETED attempts=1 " + inv + " PASS");
        } finally { killAll(); saveEvidence(); }
    }

    /** Crash point 3 with the lease expiring by itself (no edit of lease_until): needs about 5.5 minutes, so it is a separate opt-in. */
    @EnabledIfSystemProperty(named = "paspm.test.natural-lease", matches = "true")
    @Test void crashPoint3_killedWhilePreparingTheSnapshotRecoversAfterTheLeaseExpiresNaturally() throws Exception {
        var fire = recentFire();
        fixture(fire);
        Path base = Files.createTempDirectory("paspm-sched-cp3n-");
        try {
            Node c = start(base.resolve("c"));
            long id = activeSchedule(c, fire);
            Files.writeString(c.dir.resolve("hold-occurrence"), "hold");
            due(id, fire.toInstant());
            waitFor(() -> Files.exists(c.dir.resolve("occurrence-held")), 60000, "snapshot written inside the open occurrence transaction");
            Instant leaseUntil = jdbc.queryForObject("SELECT lease_until FROM mining_schedule_occurrence WHERE schedule_id=?", Timestamp.class, id).toInstant();
            sigkill(c);
            Instant killed = Instant.now();
            assertEquals(0, count("SELECT COUNT(*) FROM utility_snapshot WHERE store_id=?", store));
            Node b = start(base.resolve("b"));
            waitFor(() -> "COMPLETED".equals(status(id)), 420000, "recovery after the natural 300 s lease expiry");
            Instant recovered = Instant.now();
            assertFalse(recovered.isBefore(leaseUntil), "recovered only after lease_until: the lease expired by itself and lease_until was never edited");
            var row = occ(id);
            assertEquals(2L, ((Number) row.get("lease_fence")).longValue()); assertEquals(2, ((Number) row.get("attempt_count")).intValue());
            String inv = integrity(id, 1);
            evidence.add("crash_point=3_during_snapshot_preparation_NATURAL_LEASE_EXPIRY killed_pid=" + c.process.pid() + " recovery_pid=" + b.process.pid() + " lease_until=" + leaseUntil + " killed_at=" + killed + " recovered_at=" + recovered
                + " seconds_kill_to_recovery=" + Duration.between(killed, recovered).toSeconds() + " lease_until_edited=false new_fence=2 attempts=2 " + inv + " PASS");
        } finally { killAll(); saveEvidence(); }
    }

    @Test void crashPoint5_killedBeforeTheWorkerClaimsTheCommittedRequest() throws Exception {
        var fire = recentFire();
        fixture(fire);
        Path base = Files.createTempDirectory("paspm-sched-cp5-");
        try (var held = holdLock("paspm-hold-worker-claim")) {
            jdbc.execute("CREATE TRIGGER trg_hold_worker_claim BEFORE UPDATE ON mining_request FOR EACH ROW BEGIN IF OLD.status='PENDING' AND NEW.status='RUNNING' THEN SET @h=GET_LOCK('paspm-hold-worker-claim',300); END IF; END");
            Node c = start(base.resolve("c"));
            long id = activeSchedule(c, fire);
            due(id, fire.toInstant());
            waitFor(() -> blockedOnUserLock() == 1, 90000, "worker claim blocked after snapshot, request and link committed");
            String statement = blockedStatement();
            var before = occ(id);
            long request = ((Number) before.get("request_id")).longValue();
            assertNotNull(before.get("snapshot_id"));
            assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM mining_request WHERE request_id=?", String.class, request), "request committed but not claimed");
            sigkill(c);
            boolean noticed = serverNoticedDisconnect();
            assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM mining_request WHERE request_id=?", String.class, request));
            jdbc.execute("DROP TRIGGER trg_hold_worker_claim"); held.close();
            Node b = start(base.resolve("b"));
            waitFor(() -> "COMPLETED".equals(status(id)), 150000, "recovery instance claims the committed request and reconciles the occurrence");
            assertEquals(1L, jdbc.queryForObject("SELECT lease_fence FROM mining_request WHERE request_id=?", Long.class, request));
            assertEquals(16, count("SELECT COUNT(*) FROM high_utility_pattern p JOIN mining_run r ON p.run_id=r.run_id WHERE r.store_id=?", store));
            String inv = integrity(id, 1);
            evidence.add("crash_point=5_before_worker_claim killed_pid=" + c.process.pid() + " blocked_in=\"" + statement + "\" committed_before_kill=snapshot+request+link request=" + request + " server_noticed_disconnect=" + noticed + " recovery_pid=" + b.process.pid()
                + " request_fence=1 request=COMPLETED occurrence=COMPLETED patterns=16 " + inv + " PASS");
        } finally { killAll(); saveEvidence(); }
    }

    @Test void crashPoint6_killedDuringRealSpmfExecutionRecoversAndTheChildDoesNotSurviveOnLinux() throws Exception {
        var fire = recentFire();
        fixture(fire);
        Path base = Files.createTempDirectory("paspm-sched-cp6-");
        ProcessHandle child = null;
        try {
            Node c = start(base.resolve("c"));
            long id = activeSchedule(c, fire);
            Files.writeString(c.dir.resolve("hold-before-spmf"), "hold");
            due(id, fire.toInstant());
            waitFor(() -> Files.exists(c.dir.resolve("before-spmf")), 90000, "request committed, worker about to start SPMF");
            long request = ((Number) occ(id).get("request_id")).longValue();
            long fenceBefore = jdbc.queryForObject("SELECT lease_fence FROM mining_request WHERE request_id=?", Long.class, request);
            Files.delete(c.dir.resolve("hold-before-spmf"));
            child = spmfChild(c);
            signal(child.pid(), "STOP");                                  // keeps the real SPMF alive and observable until the Backend dies
            assertTrue(child.isAlive());
            String childOwner = child.info().user().orElse("?"), backendOwner = c.process.info().user().orElse("?");
            assertEquals(backendOwner, childOwner, "the SPMF child runs as the Backend's own (non-privileged) user");
            sigkill(c);
            boolean linux = "Linux".equalsIgnoreCase(System.getProperty("os.name"));
            if (linux) { ProcessHandle observed = child; waitFor(() -> !executing(observed), 15000, "kernel parent-death cleanup of the SPMF child"); }
            boolean orphanAlive = executing(child);
            long childPid = child.pid();
            if (orphanAlive) signal(childPid, "KILL");                    // macOS has no parent-death signal: only the test cleans up
            Node b = start(base.resolve("b"));
            waitFor(() -> "COMPLETED".equals(status(id)), 180000, "request recovered after the worker lease expired and occurrence reconciled");
            long fenceAfter = jdbc.queryForObject("SELECT lease_fence FROM mining_request WHERE request_id=?", Long.class, request);
            assertTrue(fenceAfter > fenceBefore, "new worker fence");
            assertEquals(16, count("SELECT COUNT(*) FROM high_utility_pattern p JOIN mining_run r ON p.run_id=r.run_id WHERE r.store_id=?", store));
            assertEquals(2, count("SELECT COUNT(*) FROM mining_run WHERE store_id=? AND status='COMPLETED'", store));
            String inv = integrity(id, 1);
            evidence.add("crash_point=6_during_spmf killed_pid=" + c.process.pid() + " spmf_pid=" + childPid + " spmf_user=" + childOwner + " backend_user=" + backendOwner + " os=" + System.getProperty("os.name") + " orphan_alive_after_backend_sigkill=" + orphanAlive
                + " parent_death_enforced_by_kernel=" + linux + " recovery_pid=" + b.process.pid() + " request=" + request + " old_fence=" + fenceBefore + " new_fence=" + fenceAfter + " occurrence=COMPLETED patterns=16 " + inv + " PASS");
        } finally {
            if (child != null && child.isAlive()) new ProcessBuilder("kill", "-KILL", Long.toString(child.pid())).start().waitFor();
            killAll(); saveEvidence();
        }
    }

    @Test void crashPoints7and9_killedDuringOccurrenceFinalizationAndThenDuringTheRecoveringInstancesReconciliation() throws Exception {
        var fire = recentFire();
        fixture(fire);
        Path base = Files.createTempDirectory("paspm-sched-cp79-");
        try (var held = holdLock("paspm-hold-final")) {
            jdbc.execute("CREATE TRIGGER trg_hold_final BEFORE UPDATE ON mining_schedule_occurrence FOR EACH ROW BEGIN IF OLD.status='DISPATCHED' AND NEW.status IN ('COMPLETED','FAILED') THEN SET @h=GET_LOCK('paspm-hold-final',300); END IF; END");
            Node c = start(base.resolve("c"));
            long id = activeSchedule(c, fire);
            due(id, fire.toInstant());
            waitFor(() -> blockedOnUserLock() == 1, 150000, "occurrence finalization blocked after the request completed");
            String statementC = blockedStatement();
            var before = occ(id);
            long request = ((Number) before.get("request_id")).longValue();
            assertEquals("DISPATCHED", before.get("status"));
            assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM mining_request WHERE request_id=?", String.class, request));
            sigkill(c);
            boolean noticedC = serverNoticedDisconnect();
            assertEquals("DISPATCHED", status(id), "finalization did not commit");
            // the recovering instance reaches the same step and is killed in the middle of reconciliation too
            Node b = start(base.resolve("b"));
            waitFor(() -> blockedOnUserLock() == 1, 60000, "recovering instance blocked in its reconciliation");
            String statementB = blockedStatement();
            assertEquals("DISPATCHED", status(id));
            sigkill(b);
            boolean noticedB = serverNoticedDisconnect();
            jdbc.execute("DROP TRIGGER trg_hold_final"); held.close();
            Node d = start(base.resolve("d"));
            waitFor(() -> "COMPLETED".equals(status(id)), 60000, "third instance reconciles");
            var row = occ(id);
            assertEquals(1L, ((Number) row.get("lease_fence")).longValue(), "reconciliation does not touch the lease");
            assertEquals(16, count("SELECT COUNT(*) FROM high_utility_pattern p JOIN mining_run r ON p.run_id=r.run_id WHERE r.store_id=?", store));
            String inv = integrity(id, 1);
            evidence.add("crash_point=7_occurrence_finalization killed_pid=" + c.process.pid() + " blocked_in=\"" + statementC + "\" committed_before_kill=request_COMPLETED_occurrence_DISPATCHED server_noticed_disconnect=" + noticedC
                + " | crash_point=9_reconciliation killed_pid=" + b.process.pid() + " blocked_in=\"" + statementB + "\" server_noticed_disconnect=" + noticedB + " | recovery_pid=" + d.process.pid() + " occurrence=COMPLETED fence=1 patterns=16 " + inv + " PASS");
        } finally { killAll(); saveEvidence(); }
    }

    @Test void crashPoint8_killedDuringRetryBackoffTheNextInstanceRunsTheSecondAttemptWithoutEditingAnyTimestamp() throws Exception {
        var fire = recentFire();
        fixture(fire);
        Path base = Files.createTempDirectory("paspm-sched-cp8-");
        try {
            Node c = start(base.resolve("c"), db, java.util.Map.of("PASPM_MINING_SPMF_JAR", base.resolve("missing-spmf.jar").toString()));   // attempt 1 fails retryably: the engine is unavailable
            long id = activeSchedule(c, fire);
            due(id, fire.toInstant());
            waitFor(() -> { var rows = jdbc.queryForList("SELECT status,attempt_count,next_retry_at FROM mining_schedule_occurrence WHERE schedule_id=? AND status<>'SKIPPED'", id); return !rows.isEmpty() && "PENDING".equals(rows.get(0).get("status")) && ((Number) rows.get(0).get("attempt_count")).intValue() == 1 && rows.get(0).get("next_retry_at") != null; }, 90000, "attempt 1 failed and a retry is scheduled");
            var before = occ(id);
            Instant retryAt = ((Timestamp) before.get("next_retry_at")).toInstant();
            assertEquals("RETRYABLE_FAILURE", before.get("failure_code")); assertNotNull(before.get("failure_message"));
            sigkill(c);
            assertEquals("PENDING", status(id)); assertEquals(0, count("SELECT COUNT(*) FROM utility_snapshot WHERE store_id=?", store), "attempt 1 left no snapshot");
            Node b = start(base.resolve("b"));
            waitFor(() -> "COMPLETED".equals(status(id)), 120000, "second attempt by the next instance");
            var row = occ(id);
            assertEquals(2, ((Number) row.get("attempt_count")).intValue()); assertEquals(2L, ((Number) row.get("lease_fence")).longValue()); assertNull(row.get("failure_code"));
            String inv = integrity(id, 1);
            evidence.add("crash_point=8_retry_backoff killed_pid=" + c.process.pid() + " committed_before_kill=attempt1_failed_PENDING_next_retry_at=" + retryAt + " recovery_pid=" + b.process.pid() + " recovery_owner_fence=2 attempts=2 timestamps_edited=false occurrence=COMPLETED " + inv + " PASS");
        } finally { killAll(); saveEvidence(); }
    }

    /**
     * A stalled owner (stop-the-world pause, frozen VM) keeps its transaction and therefore the occurrence row lock; the lease runs out BY ITSELF
     * (no lease_until edit is possible while that lock is held). Needs about 6.5 minutes, so it is part of the natural-lease opt-in.
     */
    @EnabledIfSystemProperty(named = "paspm.test.natural-lease", matches = "true")
    @Test void staleOwnerThatWakesUpAfterItsLeaseExpiredNaturallyCannotCommitAndTheNewOwnerProducesTheOnlyResult() throws Exception {
        var fire = recentFire();
        fixture(fire);
        Path base = Files.createTempDirectory("paspm-sched-stale-");
        try {
            Node c = start(base.resolve("c"));
            Node b = start(base.resolve("b"));
            long id = activeSchedule(c, fire);
            Files.writeString(c.dir.resolve("hold-occurrence"), "hold");
            Files.writeString(b.dir.resolve("hold-occurrence"), "hold");              // if B ever started preparing it would be visible and held too
            due(id, fire.toInstant());
            waitFor(() -> Files.exists(c.dir.resolve("occurrence-held")) || Files.exists(b.dir.resolve("occurrence-held")), 60000, "an owner is inside the occurrence transaction");
            Node stale = Files.exists(c.dir.resolve("occurrence-held")) ? c : b, other = stale == c ? b : c;
            Files.delete(other.dir.resolve("hold-occurrence"));
            signal(stale.process.pid(), "STOP");                                       // the owner stalls while holding the row locks
            Instant leaseUntil = jdbc.queryForObject("SELECT lease_until FROM mining_schedule_occurrence WHERE schedule_id=?", Timestamp.class, id).toInstant();
            Instant killedAt = Instant.now();
            long wait = Duration.between(Instant.now(), leaseUntil.plusSeconds(75)).toMillis();
            Thread.sleep(Math.max(0, wait));                                          // natural expiry plus time for the other instance's claim attempts to hit the row lock
            assertEquals(1L, ((Number) occ(id).get("lease_fence")).longValue(), "the other instance cannot take the occurrence while the stalled owner holds its row lock");
            assertEquals(0, count("SELECT COUNT(*) FROM mining_request WHERE store_id=?", store));
            Files.delete(stale.dir.resolve("hold-occurrence"));
            signal(stale.process.pid(), "CONT");                                       // wakes up with an expired lease and tries to finish
            waitFor(() -> "COMPLETED".equals(status(id)), 240000, "the other instance completes after the stale attempt was rolled back");
            var row = occ(id);
            assertEquals(2L, ((Number) row.get("lease_fence")).longValue()); assertEquals(2, ((Number) row.get("attempt_count")).intValue());
            assertTrue(Files.readString(stale.log).contains("Occurrence lease expired before dispatch commit"), "the stale owner's commit was refused by the fence");
            String inv = integrity(id, 1);
            evidence.add("stale_owner stalled_pid=" + stale.process.pid() + " new_owner_pid=" + other.process.pid() + " expiry=NATURAL lease_until=" + leaseUntil + " stalled_for_seconds=" + Duration.between(killedAt, Instant.now()).toSeconds()
                + " row_lock_blocked_takeover_before_resume=true stale_commit_refused=true winner_fence=2 attempts=2 occurrence=COMPLETED " + inv + " PASS");
        } finally { killAll(); saveEvidence(); }
    }

    @Test void updatingTheScheduleWhileAnOccurrenceIsBeingPreparedWaitsForItAndLeavesTheFrozenRangeAndVersionUntouched() throws Exception {
        var fire = recentFire();
        fixture(fire);
        Path base = Files.createTempDirectory("paspm-sched-d8-");
        try {
            Node c = start(base.resolve("c"));
            long id = activeSchedule(c, fire);
            Files.writeString(c.dir.resolve("hold-occurrence"), "hold");
            due(id, fire.toInstant());
            waitFor(() -> Files.exists(c.dir.resolve("occurrence-held")), 60000, "occurrence preparing (schedule row locked by the preparation)");
            var frozen = occ(id);
            String edit = "{\"schedule\":{\"storeId\":" + store + ",\"frequency\":\"WEEKLY\",\"dayOfWeek\":" + ((fire.getDayOfWeek().getValue() % 7) + 1) + ",\"localTime\":\"" + fire.toLocalTime() + "\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"lookbackDays\":30,\"compareAlgorithms\":true},\"enabled\":true,\"expectedVersion\":2}";
            var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
            var update = pool.submit(() -> http.exchange(url(c) + "/api/system/mining/schedules/" + id, HttpMethod.PUT, new HttpEntity<>(edit, admin), JsonNode.class));
            Thread.sleep(3000);
            assertFalse(update.isDone(), "the edit waits for the schedule row lock held by the preparing occurrence");
            assertEquals(2L, jdbc.queryForObject("SELECT schedule_version FROM mining_schedule WHERE schedule_id=?", Long.class, id));
            Files.delete(c.dir.resolve("hold-occurrence"));
            assertEquals(HttpStatus.OK, update.get(60, TimeUnit.SECONDS).getStatusCode());
            pool.shutdown();
            waitFor(() -> "COMPLETED".equals(status(id)), 120000, "the preparing occurrence completes with the frozen version");
            var row = occ(id);
            assertEquals(((Number) frozen.get("schedule_version")).longValue(), ((Number) row.get("schedule_version")).longValue());
            assertEquals(frozen.get("range_start").toString(), row.get("range_start").toString());
            assertEquals(frozen.get("range_end").toString(), row.get("range_end").toString());
            assertEquals(3L, jdbc.queryForObject("SELECT schedule_version FROM mining_schedule WHERE schedule_id=?", Long.class, id), "the edit applies to future occurrences");
            String inv = integrity(id, 1);
            evidence.add("d8_update_while_preparing node_pid=" + c.process.pid() + " edit_blocked_until_commit=true frozen_schedule_version=" + row.get("schedule_version") + " frozen_range=" + row.get("range_start") + ".." + row.get("range_end") + " schedule_version_after_edit=3 occurrence=COMPLETED " + inv + " PASS");
        } finally { killAll(); saveEvidence(); }
    }
}

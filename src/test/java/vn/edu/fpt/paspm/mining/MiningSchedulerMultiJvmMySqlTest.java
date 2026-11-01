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

    Node start(Path dir) throws Exception {
        Files.createDirectories(dir);
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        String cp = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", cp, MiningMultiJvmBackend.class.getName(),
            "--server.port=" + port, "--spring.main.allow-bean-definition-overriding=true", "--paspm.bootstrap-email=", "--paspm.bootstrap-password=",
            "--paspm.avatar.directory=/tmp/paspm-test-avatars", "--paspm.mining.schedule-dispatch-enabled=true", "--paspm.store-time-zone=Asia/Ho_Chi_Minh");
        var env = pb.environment();
        env.put("DB_URL", db.url()); env.put("DB_USER", db.user()); env.put("DB_PASSWORD", db.password());
        env.put("JWT_SECRET", "01234567890123456789012345678901"); env.put("OTP_SECRET", "01234567890123456789012345678901");
        env.put("PASPM_TEST_BARRIER_DIR", dir.toString());
        env.put("PASPM_MINING_SPMF_JAR", System.getProperty("paspm.test.spmf-jar")); env.put("PASPM_MINING_JAVA_EXECUTABLE", System.getProperty("paspm.test.spmf-java"));
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
}

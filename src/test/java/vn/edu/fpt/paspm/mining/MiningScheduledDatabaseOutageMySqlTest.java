package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;
import vn.edu.fpt.paspm.user.UserRepository;

/**
 * D10 acceptance, opt-in: the Backend reaches MySQL through a TCP proxy owned by the test. While a scheduled occurrence is being reserved,
 * prepared or mined, every live connection is dropped and new ones are refused for several seconds; afterwards the proxy is restored. The
 * MySQL server itself is not restarted. Expected: the Backend reconnects and the schedule's occurrence completes exactly once, with no
 * duplicate request, no orphan snapshot and no failed state. The one wait the design imposes is the 300 s occurrence lease after a dropped
 * preparation transaction; the test moves {@code lease_until} into the past instead of sleeping, exactly as the crash tests do.
 */
@EnabledIfSystemProperty(named = "paspm.test.mysql", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "paspm.jwt-secret=01234567890123456789012345678901", "paspm.otp-secret=01234567890123456789012345678901",
    "paspm.bootstrap-email=", "paspm.bootstrap-password=", "paspm.avatar.directory=/tmp/paspm-outage-avatars",
    "paspm.mining.schedule-dispatch-enabled=true", "paspm.store-time-zone=Asia/Ho_Chi_Minh",
    "paspm.mining.spmf-jar=${paspm.test.spmf-jar}", "paspm.mining.java-executable=${paspm.test.spmf-java}"
})
class MiningScheduledDatabaseOutageMySqlTest {
    static final ZoneId HCM = ZoneId.of("Asia/Ho_Chi_Minh");
    static Proxy proxy;
    static JdbcTemplate direct;

    /** Minimal TCP forwarder that can sever every connection and refuse new ones. */
    static final class Proxy implements AutoCloseable {
        final ServerSocket server; final String upstreamHost; final int upstreamPort;
        final List<Socket> sockets = new CopyOnWriteArrayList<>();
        final AtomicBoolean down = new AtomicBoolean();
        Proxy(String upstreamHost, int upstreamPort) throws IOException {
            this.upstreamHost = upstreamHost; this.upstreamPort = upstreamPort;
            server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            var acceptor = new Thread(this::accept, "outage-proxy-accept"); acceptor.setDaemon(true); acceptor.start();
        }
        int port() { return server.getLocalPort(); }
        void accept() {
            while (!server.isClosed()) {
                try {
                    Socket client = server.accept();
                    if (down.get()) { client.close(); continue; }
                    Socket upstream = new Socket(upstreamHost, upstreamPort);
                    sockets.add(client); sockets.add(upstream);
                    pump(client, upstream); pump(upstream, client);
                } catch (IOException e) { /* closing or upstream refused */ }
            }
        }
        void pump(Socket from, Socket to) {
            var t = new Thread(() -> {
                try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
                    byte[] buffer = new byte[16384]; int n;
                    while ((n = in.read(buffer)) >= 0) { out.write(buffer, 0, n); out.flush(); }
                } catch (IOException ignored) { }
                finally { try { from.close(); } catch (IOException ignored) { } try { to.close(); } catch (IOException ignored) { } }
            }, "outage-proxy-pump");
            t.setDaemon(true); t.start();
        }
        void outage() { down.set(true); for (Socket s : sockets) try { s.close(); } catch (IOException ignored) { } sockets.clear(); }
        void restore() { down.set(false); }
        @Override public void close() throws IOException { server.close(); outage(); }
    }

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) throws Exception {
        var real = MySqlTestDatabase.create();
        var matcher = java.util.regex.Pattern.compile("jdbc:mysql://([^:/]+):([0-9]+)/(.*)").matcher(real.url());
        assertTrue(matcher.matches());
        proxy = new Proxy(matcher.group(1), Integer.parseInt(matcher.group(2)));
        direct = new JdbcTemplate(new DriverManagerDataSource(real.url(), real.user(), real.password()));
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:" + proxy.port() + "/" + matcher.group(3) + "&connectTimeout=2000&socketTimeout=20000");
        registry.add("spring.datasource.username", real::user);
        registry.add("spring.datasource.password", real::password);
        registry.add("spring.datasource.hikari.connection-timeout", () -> "3000");
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;

    HttpHeaders admin() {
        var user = users.createPendingUser("Outage admin", UUID.randomUUID() + "@example.test", "SYSTEM_ADMIN", null, null, null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?", user.id());
        var h = new HttpHeaders(); h.setBearerAuth(auth.token(users.findById(user.id())).accessToken()); h.setContentType(MediaType.APPLICATION_JSON); return h;
    }
    String occurrenceStatus(long schedule) { return direct.queryForList("SELECT status FROM mining_schedule_occurrence WHERE schedule_id=?", String.class, schedule).stream().findFirst().orElse(null); }

    @Test void schedulerAndMiningSurviveDroppedDatabaseConnectionsAndCompleteExactlyOnce() throws Exception {
        ZonedDateTime fire = Instant.now().atZone(HCM).minusMinutes(2).withSecond(0).withNano(0);
        jdbc.update("INSERT INTO store(name) VALUES ('Outage synthetic')");
        int store = jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        var products = new ArrayList<Integer>();
        for (int i = 1; i <= 7; i++) {
            jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,'Frozen','each',FALSE,100,0,'ACTIVE')", store, "OU" + i);
            products.add(jdbc.queryForObject("SELECT MAX(product_id) FROM product", Integer.class));
        }
        for (String line : SpmfMiningEngineTest.INPUT.split("\n")) {
            var parts = line.split(":"); String key = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO sales_transaction(store_id,source_system,source_order_id,occurred_at,status,f03_eligible,total_amount,content_sha256) VALUES (?,'OU',?,?,'COMPLETED',TRUE,100,?)", store, key, Timestamp.from(fire.minusDays(2).toInstant()), "a".repeat(64));
            int tx = jdbc.queryForObject("SELECT transaction_id FROM sales_transaction WHERE store_id=? AND source_order_id=?", Integer.class, store, key);
            var ids = parts[0].split(" "); var util = parts[2].split(" ");
            for (int i = 0; i < ids.length; i++) jdbc.update("INSERT INTO transaction_item(store_id,transaction_id,product_id,quantity,unit_price,unit_cost) VALUES (?,?,?,1,?,0)", store, tx, products.get(Integer.parseInt(ids[i]) - 1), Integer.parseInt(util[i]));
        }
        var admin = admin();
        assertEquals(HttpStatus.OK, http.exchange("/api/system/mining/config", HttpMethod.PUT, new HttpEntity<>("{\"algorithm\":\"HUI_MINER\",\"minUtilMode\":\"ABSOLUTE\",\"minUtilValue\":30,\"expectedVersion\":0}", admin), JsonNode.class).getStatusCode());
        String schedule = "{\"storeId\":" + store + ",\"frequency\":\"WEEKLY\",\"dayOfWeek\":" + fire.getDayOfWeek().getValue() + ",\"localTime\":\"" + fire.toLocalTime() + "\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"lookbackDays\":7,\"compareAlgorithms\":true}";
        long id = http.exchange("/api/system/mining/schedules", HttpMethod.POST, new HttpEntity<>(schedule, admin), JsonNode.class).getBody().path("scheduleId").asLong();
        assertEquals(HttpStatus.OK, http.exchange("/api/system/mining/schedules/" + id, HttpMethod.PUT, new HttpEntity<>("{\"schedule\":" + schedule + ",\"enabled\":true,\"expectedVersion\":1}", admin), JsonNode.class).getStatusCode());

        // outage 1: the schedule becomes due while the database is unreachable
        proxy.outage();
        direct.update("UPDATE mining_schedule SET next_fire_at=? WHERE schedule_id=?", Timestamp.from(fire.toInstant()), id);
        Thread.sleep(8000);
        assertNull(occurrenceStatus(id), "nothing can be reserved while every connection is refused");
        proxy.restore();

        // outage 2: sever the connections as soon as preparation or mining is under way
        long end = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        String seen = null;
        while (System.nanoTime() < end && (seen == null || seen.equals("PENDING"))) { Thread.sleep(25); seen = occurrenceStatus(id); }
        assertNotNull(seen, "the Backend reconnected and reserved the due occurrence");
        if ("PROCESSING".equals(seen) || "DISPATCHED".equals(seen)) {
            proxy.outage(); Thread.sleep(8000); proxy.restore();
        }
        // a preparation transaction killed by the outage keeps its 300 s lease; let the lease run out the way the crash tests do
        Thread.sleep(3000);
        direct.update("UPDATE mining_schedule_occurrence SET lease_until=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP(6)) WHERE schedule_id=? AND status='PROCESSING' AND request_id IS NULL", id);
        end = System.nanoTime() + Duration.ofSeconds(150).toNanos();
        while (System.nanoTime() < end && !"COMPLETED".equals(occurrenceStatus(id)) && !"FAILED".equals(occurrenceStatus(id))) {
            Thread.sleep(250);
            direct.update("UPDATE mining_schedule_occurrence SET lease_until=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP(6)) WHERE schedule_id=? AND status='PROCESSING' AND request_id IS NULL AND lease_until>CURRENT_TIMESTAMP(6)", id);
        }
        assertEquals("COMPLETED", occurrenceStatus(id), String.valueOf(direct.queryForMap("SELECT status,failure_code,failure_message,attempt_count FROM mining_schedule_occurrence WHERE schedule_id=?", id)));
        assertEquals(1, direct.queryForObject("SELECT COUNT(*) FROM mining_schedule_occurrence WHERE schedule_id=?", Integer.class, id));
        assertEquals(1, direct.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=?", Integer.class, store), "no duplicate logical request");
        assertEquals(1, direct.queryForObject("SELECT COUNT(*) FROM utility_snapshot WHERE store_id=?", Integer.class, store), "no orphan snapshot");
        assertEquals(2, direct.queryForObject("SELECT COUNT(*) FROM mining_run WHERE store_id=? AND status='COMPLETED'", Integer.class, store));
        assertEquals(16, direct.queryForObject("SELECT COUNT(*) FROM high_utility_pattern p JOIN mining_run r ON p.run_id=r.run_id WHERE r.store_id=?", Integer.class, store));
        String evidence = System.getenv("PASPM_MULTIJVM_EVIDENCE_FILE");
        if (evidence != null) java.nio.file.Files.writeString(java.nio.file.Path.of(evidence), "database_outage schedule=" + id + " outage_while_due=8s outage_during=" + seen + " 8s occurrence=COMPLETED requests=1 snapshots=1 patterns=16 PASS\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }
}

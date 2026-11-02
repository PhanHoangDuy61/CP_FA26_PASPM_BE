package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;
import vn.edu.fpt.paspm.user.UserRepository;

/**
 * Decision D10 acceptance, opt-in (needs MySQL 8.4, the pinned SPMF jar and a Java 25 runtime): every call below is real HTTP over a socket
 * to the running Backend, with real login tokens. The environment is explicitly enabled
 * ({@code paspm.mining.schedule-dispatch-enabled=true}); only the passage of time is simulated, by moving a schedule's
 * {@code next_fire_at} to an instant that exactly matches its recurrence rule.
 */
@EnabledIfSystemProperty(named = "paspm.test.mysql", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "paspm.jwt-secret=01234567890123456789012345678901", "paspm.otp-secret=01234567890123456789012345678901",
    "paspm.bootstrap-email=", "paspm.bootstrap-password=", "paspm.avatar.directory=/tmp/paspm-activation-e2e-avatars",
    "paspm.mining.schedule-dispatch-enabled=true", "paspm.store-time-zone=Asia/Ho_Chi_Minh",
    "paspm.mining.spmf-jar=${paspm.test.spmf-jar}", "paspm.mining.java-executable=${paspm.test.spmf-java}"
})
class MiningScheduledActivationE2EMySqlTest {
    static final ZoneId HCM = ZoneId.of("Asia/Ho_Chi_Minh");
    static final String PASSWORD = "E2e-Accept-Pass-1!";
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { MySqlTestDatabase.create().register(registry); }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;

    record Actor(int id, String bearer) {}

    Actor login(String role, Integer store) {
        String email = UUID.randomUUID() + "@example.test";
        var user = users.createPendingUser("E2E " + role, email, role, store, passwords.encode(PASSWORD), null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?", user.id());
        var body = http.postForEntity("/api/auth/login", json("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null), JsonNode.class);
        assertEquals(HttpStatus.OK, body.getStatusCode());
        return new Actor(user.id(), body.getBody().path("accessToken").asText());
    }
    HttpEntity<String> json(String body, Actor actor) {
        var h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (actor != null) h.setBearerAuth(actor.bearer());
        return new HttpEntity<>(body, h);
    }
    org.springframework.http.ResponseEntity<JsonNode> call(HttpMethod method, String path, Actor actor, String body) {
        return http.exchange(path, method, json(body, actor), JsonNode.class);
    }
    JsonNode ok(HttpMethod method, String path, Actor actor, String body, HttpStatus expected) {
        var response = call(method, path, actor, body);
        assertEquals(expected, response.getStatusCode(), method + " " + path + " -> " + response.getBody());
        return response.getBody();
    }

    /** A Store with the five reference transactions two days before the analysis date; {@code withData=false} leaves it empty. */
    int store(String tag, boolean withData, ZonedDateTime fire) {
        jdbc.update("INSERT INTO store(name) VALUES (?)", tag + " " + UUID.randomUUID());
        int store = jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        if (!withData) return store;
        var products = new ArrayList<Integer>();
        for (int i = 1; i <= 7; i++) {
            jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,'Frozen','each',FALSE,100,0,'ACTIVE')", store, "E" + i);
            products.add(jdbc.queryForObject("SELECT MAX(product_id) FROM product", Integer.class));
        }
        for (String line : SpmfMiningEngineTest.INPUT.split("\n")) {
            var parts = line.split(":");
            String key = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO sales_transaction(store_id,source_system,source_order_id,occurred_at,status,f03_eligible,total_amount,content_sha256) VALUES (?,'E2E',?,?,'COMPLETED',TRUE,100,?)",
                store, key, Timestamp.from(fire.minusDays(2).toInstant()), "a".repeat(64));
            int tx = jdbc.queryForObject("SELECT transaction_id FROM sales_transaction WHERE store_id=? AND source_order_id=?", Integer.class, store, key);
            var ids = parts[0].split(" ");
            var util = parts[2].split(" ");
            for (int i = 0; i < ids.length; i++)
                jdbc.update("INSERT INTO transaction_item(store_id,transaction_id,product_id,quantity,unit_price,unit_cost) VALUES (?,?,?,1,?,0)", store, tx, products.get(Integer.parseInt(ids[i]) - 1), Integer.parseInt(util[i]));
        }
        return store;
    }
    void policy(Actor admin) {
        long head = jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1", Long.class);
        if (head == 0) ok(HttpMethod.PUT, "/api/system/mining/config", admin, "{\"algorithm\":\"HUI_MINER\",\"minUtilMode\":\"ABSOLUTE\",\"minUtilValue\":30,\"expectedVersion\":0}", HttpStatus.OK);
    }
    /** The latest occurrence time of the recurrence that is already in the past, truncated to the minute. */
    ZonedDateTime recentFire() { return Instant.now().atZone(HCM).minusMinutes(2).withSecond(0).withNano(0); }
    String body(int store, String recurrence, ZonedDateTime fire, int lookback) {
        return "{\"storeId\":" + store + "," + recurrence + ",\"localTime\":\"" + fire.toLocalTime() + "\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"lookbackDays\":" + lookback + ",\"compareAlgorithms\":true}";
    }
    long createSchedule(Actor admin, String schedule) { return ok(HttpMethod.POST, "/api/system/mining/schedules", admin, schedule, HttpStatus.CREATED).path("scheduleId").asLong(); }
    JsonNode activate(Actor admin, long id, String schedule, long version) {
        return ok(HttpMethod.PUT, "/api/system/mining/schedules/" + id, admin, "{\"schedule\":" + schedule + ",\"enabled\":true,\"expectedVersion\":" + version + "}", HttpStatus.OK);
    }
    void timePasses(long id, Instant fire) { jdbc.update("UPDATE mining_schedule SET next_fire_at=? WHERE schedule_id=?", Timestamp.from(fire), id); }
    JsonNode history(Actor admin, long id) { return ok(HttpMethod.GET, "/api/system/mining/schedules/" + id + "/occurrences?pageSize=100", admin, null, HttpStatus.OK); }
    JsonNode awaitTerminalRun(Actor admin, long id, int expectedRows) throws Exception {
        long end = System.nanoTime() + Duration.ofSeconds(120).toNanos();
        JsonNode last = null;
        while (System.nanoTime() < end) {
            last = history(admin, id);
            if (last.path("totalElements").asInt() >= expectedRows && last.path("items").findValues("status").stream().noneMatch(s -> List.of("PENDING", "PROCESSING", "DISPATCHED").contains(s.asText()))) return last;
            Thread.sleep(250);
        }
        fail("scheduled run did not finish: " + last);
        return last;
    }
    int principal() { return jdbc.queryForObject("SELECT u.user_id FROM app_user u JOIN role r ON r.role_id=u.role_id WHERE r.code='SYSTEM_SCHEDULER'", Integer.class); }

    @Test void weeklyScheduleActivatedOverHttpRunsFreshSnapshotAndBothAlgorithmsAsTheSchedulerPrincipal() throws Exception {
        var fire = recentFire();
        int store = store("E2E weekly", true, fire);
        var editor = login("SYSTEM_ADMIN", null);
        var reader = login("SYSTEM_ADMIN", null);
        var merchant = login("MERCHANDISER", store);
        var foreign = login("MERCHANDISER", store("E2E foreign", false, fire));
        policy(editor);
        String schedule = body(store, "\"frequency\":\"WEEKLY\",\"dayOfWeek\":" + fire.getDayOfWeek().getValue(), fire, 7);

        // RBAC on the administration surface, over the wire
        assertEquals(HttpStatus.UNAUTHORIZED, call(HttpMethod.POST, "/api/system/mining/schedules", null, schedule).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, call(HttpMethod.POST, "/api/system/mining/schedules", merchant, schedule).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, call(HttpMethod.GET, "/api/system/stores", merchant, null).getStatusCode());
        // D2: the Store picker names the Store
        boolean listed = false;
        for (int page = 0; !listed && page < 50; page++) {
            var stores = ok(HttpMethod.GET, "/api/system/stores?page=" + page + "&pageSize=100", reader, null, HttpStatus.OK);
            for (var s : stores.path("items")) if (s.path("storeId").asInt() == store) listed = true;
            if (stores.path("items").isEmpty()) break;
        }
        assertTrue(listed, "D2 picker lists the Store");

        long id = createSchedule(editor, schedule);
        assertFalse(ok(HttpMethod.GET, "/api/system/mining/schedules/" + id, reader, null, HttpStatus.OK).path("enabled").asBoolean(), "created disabled");
        Instant activatedAfter = Instant.now();
        var active = activate(editor, id, schedule, 1);
        assertTrue(active.path("enabled").asBoolean());
        assertEquals(2, active.path("version").asInt());
        assertEquals(MiningScheduleTime.nextWeekly(activatedAfter, HCM, fire.getDayOfWeek(), fire.toLocalTime()), Instant.parse(active.path("nextFireAt").asText()));
        assertEquals(editor.id(), active.path("updatedBy").asInt());

        // the human who edited the schedule leaves; the automatic run must not depend on them (D9)
        jdbc.update("UPDATE app_user SET status='INACTIVE',token_version=token_version+1 WHERE user_id=?", editor.id());
        assertEquals(HttpStatus.UNAUTHORIZED, call(HttpMethod.GET, "/api/system/mining/schedules/" + id, editor, null).getStatusCode());

        timePasses(id, fire.toInstant());
        var done = awaitTerminalRun(reader, id, 1);
        assertEquals(1, done.path("totalElements").asInt());
        var occurrence = done.path("items").get(0);
        assertEquals("COMPLETED", occurrence.path("status").asText(), occurrence.toString());
        assertEquals(fire.toInstant(), Instant.parse(occurrence.path("scheduledAt").asText()));
        assertEquals(principal(), occurrence.path("actorId").asInt(), "executed as the scheduler principal, not as " + editor.id());
        assertEquals(2, occurrence.path("scheduleVersion").asInt());
        assertEquals(1, occurrence.path("attemptCount").asInt());
        assertEquals(fire.toLocalDate().toString(), occurrence.path("analysisDate").asText());
        assertEquals(fire.toLocalDate().minusDays(1).toString(), occurrence.path("rangeEnd").asText());
        assertEquals(fire.toLocalDate().minusDays(7).toString(), occurrence.path("rangeStart").asText());
        assertTrue(occurrence.path("failureCode").isNull() && occurrence.path("failureMessage").isNull() && occurrence.path("skipReason").isNull());
        int snapshot = occurrence.path("snapshotId").asInt();
        long request = occurrence.path("requestId").asLong();
        assertTrue(snapshot > 0 && request > 0);
        assertEquals(fire.toInstant().plus(Duration.ofDays(7)), Instant.parse(ok(HttpMethod.GET, "/api/system/mining/schedules/" + id, reader, null, HttpStatus.OK).path("nextFireAt").asText()), "the cursor advanced one week");

        // fresh immutable F03 snapshot and request owned by the machine principal; nothing orphaned
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot WHERE store_id=?", Integer.class, store));
        assertEquals(principal(), jdbc.queryForObject("SELECT created_by FROM utility_snapshot WHERE snapshot_id=?", Integer.class, snapshot));
        assertEquals(principal(), jdbc.queryForObject("SELECT requested_by FROM mining_request WHERE request_id=?", Integer.class, request));
        assertEquals(occurrence.path("configVersion").asLong(), jdbc.queryForObject("SELECT config_version FROM mining_request WHERE request_id=?", Long.class, request));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=?", Integer.class, store), "exactly one logical request");

        // D1: System Admin reads snapshots, requests and runs of the explicit Store; patterns stay closed to System Admin
        String base = "/api/system/mining/stores/" + store;
        var snaps = ok(HttpMethod.GET, base + "/snapshots", reader, null, HttpStatus.OK);
        assertTrue(snaps.path("items").findValues("snapshotId").stream().anyMatch(n -> n.asInt() == snapshot));
        var req = ok(HttpMethod.GET, base + "/requests/" + request, reader, null, HttpStatus.OK);
        assertEquals("COMPLETED", req.path("status").asText());
        assertEquals("MATCH", req.path("comparisonStatus").asText());
        assertEquals(principal(), req.path("requestedBy").asInt());
        var runs = ok(HttpMethod.GET, base + "/requests/" + request + "/runs", reader, null, HttpStatus.OK);
        assertEquals(2, runs.size());
        long runId = 0;
        for (var run : runs) { assertEquals("COMPLETED", run.path("status").asText()); assertEquals(8, run.path("patternCount").asInt()); runId = run.path("runId").asLong(); }
        assertEquals(HttpStatus.FORBIDDEN, call(HttpMethod.GET, base + "/runs/" + runId + "/patterns", reader, null).getStatusCode());
        // Store-scoped pattern access is unchanged and isolated
        var patterns = ok(HttpMethod.GET, "/api/mining/runs/" + runId + "/patterns", merchant, null, HttpStatus.OK);
        assertEquals(8, patterns.path("totalElements").asInt());
        assertEquals(HttpStatus.NOT_FOUND, call(HttpMethod.GET, "/api/mining/runs/" + runId + "/patterns", foreign, null).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, call(HttpMethod.GET, base + "/requests", merchant, null).getStatusCode());

        // D8: disabling afterwards stops further reservations and keeps history
        var disabled = ok(HttpMethod.PUT, "/api/system/mining/schedules/" + id, reader, "{\"schedule\":" + schedule + ",\"enabled\":false,\"expectedVersion\":2}", HttpStatus.OK);
        assertFalse(disabled.path("enabled").asBoolean());
        assertTrue(disabled.path("nextFireAt").isNull());
        Thread.sleep(7000);
        assertEquals(1, history(reader, id).path("totalElements").asInt(), "no further occurrence after the disable");
        assertEquals("COMPLETED", history(reader, id).path("items").get(0).path("status").asText());
    }

    @Test void monthlyScheduleAfterDowntimeRunsOnlyTheLatestFireAndRecordsTheOlderOnesAsSkipped() throws Exception {
        var fire = recentFire();
        int store = store("E2E monthly", true, fire);
        var admin = login("SYSTEM_ADMIN", null);
        policy(admin);
        String schedule = body(store, "\"frequency\":\"MONTHLY\",\"dayOfMonth\":" + fire.getDayOfMonth(), fire, 90);
        long id = createSchedule(admin, schedule);
        activate(admin, id, schedule, 1);
        // the recurrence's own fire times up to now
        var fires = new ArrayList<Instant>();
        Instant at = fire.toInstant().minus(Duration.ofDays(140));
        while (true) {
            at = MiningScheduleTime.nextMonthly(at, HCM, fire.getDayOfMonth(), fire.toLocalTime());
            if (at.isAfter(Instant.now())) break;
            fires.add(at);
        }
        assertEquals(fire.toInstant(), fires.get(fires.size() - 1));
        timePasses(id, fires.get(fires.size() - 3));
        var done = awaitTerminalRun(admin, id, 3);
        var items = done.path("items");
        assertEquals(3, items.size());
        // newest first
        assertEquals("COMPLETED", items.get(0).path("status").asText(), items.toString());
        assertEquals(fires.get(fires.size() - 1), Instant.parse(items.get(0).path("scheduledAt").asText()));
        for (int i = 1; i <= 2; i++) {
            var skipped = items.get(i);
            assertEquals("SKIPPED", skipped.path("status").asText());
            assertEquals(MiningScheduleReservation.SKIP_SUPERSEDED, skipped.path("skipReason").asText());
            assertTrue(skipped.path("snapshotId").isNull() && skipped.path("requestId").isNull() && skipped.path("actorId").isNull());
            assertEquals(0, skipped.path("attemptCount").asInt());
        }
        assertEquals(fires.get(fires.size() - 3), Instant.parse(items.get(2).path("scheduledAt").asText()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=?", Integer.class, store), "skipped fires create no request");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot WHERE store_id=?", Integer.class, store), "skipped fires create no snapshot");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM mining_run WHERE store_id=?", Integer.class, store));
        assertEquals(MiningScheduleTime.nextMonthly(fire.toInstant(), HCM, fire.getDayOfMonth(), fire.toLocalTime()),
            Instant.parse(ok(HttpMethod.GET, "/api/system/mining/schedules/" + id, admin, null, HttpStatus.OK).path("nextFireAt").asText()));
    }

    @Test void aStoreWithoutEligibleDataFailsOnceWithAVisibleReasonAndNoSnapshotOrRequest() throws Exception {
        var fire = recentFire();
        int store = store("E2E empty", false, fire);
        var admin = login("SYSTEM_ADMIN", null);
        policy(admin);
        String schedule = body(store, "\"frequency\":\"WEEKLY\",\"dayOfWeek\":" + fire.getDayOfWeek().getValue(), fire, 30);
        long id = createSchedule(admin, schedule);
        activate(admin, id, schedule, 1);
        timePasses(id, fire.toInstant());
        var occurrence = awaitTerminalRun(admin, id, 1).path("items").get(0);
        assertEquals("FAILED", occurrence.path("status").asText());
        assertEquals("HTTP_400", occurrence.path("failureCode").asText());
        assertEquals("No eligible transaction items in the selected range", occurrence.path("failureMessage").asText());
        assertEquals(1, occurrence.path("attemptCount").asInt(), "business errors are not retried");
        assertTrue(occurrence.path("snapshotId").isNull() && occurrence.path("requestId").isNull());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot WHERE store_id=?", Integer.class, store));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=?", Integer.class, store));
    }
}

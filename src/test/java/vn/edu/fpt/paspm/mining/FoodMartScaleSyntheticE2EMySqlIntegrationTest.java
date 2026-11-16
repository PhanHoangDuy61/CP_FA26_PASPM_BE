package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;
import vn.edu.fpt.paspm.mining.MiningInputBuilder.Dataset;
import vn.edu.fpt.paspm.user.UserRepository;

/**
 * Explicitly synthetic PASPM-scale lifecycle fixture. It does not turn the public FoodMart sales facts
 * into claimed historical orders or manufacture missing FoodMart inventory/expiry/season facts.
 */
@EnabledIfSystemProperty(named = "paspm.test.foodmart-e2e", matches = "true")
@SpringBootTest(properties = {
    "spring.flyway.enabled=true",
    "paspm.jwt-secret=01234567890123456789012345678901",
    "paspm.otp-secret=01234567890123456789012345678901",
    "paspm.bootstrap-email=",
    "paspm.bootstrap-password=",
    "paspm.mining.spmf-jar=${paspm.test.spmf-jar:${user.dir}/../spmf_run/spmf.jar}",
    "paspm.mining.java-executable=${paspm.test.spmf-java:${java.home}/bin/java}",
    "paspm.avatar.directory=/tmp/paspm-foodmart-e2e-avatars",
    "spring.jackson.deserialization.fail-on-unknown-properties=true"
})
@AutoConfigureMockMvc
class FoodMartScaleSyntheticE2EMySqlIntegrationTest {
    private static final int PRODUCT_COUNT = 1_559;
    private static final int REPEATED_PAIR_ORDERS = 100;
    private static final String SKU_PREFIX = "SYN-FM-E2E-";
    private static final String HEADER = "source_order_id,occurred_at,sku,quantity,unit_price,unit_cost,status\n";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        MySqlTestDatabase.create().register(registry);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired AuthService auth;

    @Test
    void syntheticScaleSnapshotIsImportedMinedPersistedAndConsumedByF06() throws Exception {
        int store = createStore("Synthetic FoodMart-scale PASPM E2E");
        String admin = token("STORE_ADMIN", store);
        String merchant = token("MERCHANDISER", store);
        seedProductsAndSellableBatches(store);
        Map<String, Integer> productIds = productIds(store);
        assertEquals(PRODUCT_COUNT, productIds.size());

        LocalDate analysisDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        String occurredAt = analysisDate.atTime(12, 0).atOffset(ZoneOffset.ofHours(7)).toString();
        String csv = syntheticCsv(occurredAt);
        byte[] csvBytes = csv.getBytes(StandardCharsets.UTF_8);
        JsonNode imported = json.readTree(mvc.perform(multipart("/api/transactions/imports/csv")
                .file(new MockMultipartFile("file", "synthetic-foodmart-scale.csv", "text/csv", csvBytes))
                .header("Authorization", "Bearer " + admin))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertEquals("COMPLETED", imported.path("status").asText());
        assertEquals(1_759, imported.path("validCount").asInt());
        assertEquals(1_659, imported.path("acceptedOrderCount").asInt());
        assertEquals(0, imported.path("rejectedCount").asInt());
        assertEquals(1_659, jdbc.queryForObject("SELECT COUNT(*) FROM sales_transaction WHERE store_id=? AND source_system='CSV_UPLOAD'", Integer.class, store));
        assertEquals(1_759, jdbc.queryForObject("SELECT COUNT(*) FROM transaction_item i JOIN sales_transaction t ON t.store_id=i.store_id AND t.transaction_id=i.transaction_id WHERE t.store_id=? AND t.source_system='CSV_UPLOAD'", Integer.class, store));

        String system = token("SYSTEM_ADMIN", null);
        put("/api/system/utility/lambda-policy", system, "{\"lambdaMax\":0.5}").andExpect(status().isOk());
        put("/api/utility/config", merchant, "{\"alpha\":0.5,\"beta\":0.5,\"lambda\":0.5,\"salesWindowDays\":30}")
            .andExpect(status().isOk());
        String snapshotPayload = json.writeValueAsString(Map.of("rangeStart", analysisDate, "rangeEnd", analysisDate, "asOfDate", analysisDate));
        JsonNode snapshot = body(post("/api/utility/snapshots", merchant, snapshotPayload).andExpect(status().isCreated()));
        int snapshotId = snapshot.path("snapshotId").asInt();
        String matrixHash = snapshot.path("matrixHash").asText();
        assertEquals("READY", snapshot.path("status").asText());
        assertEquals("W2_EXPIRY_SEASON_V1", snapshot.path("configuration").path("config").path("scoringVersion").asText());
        assertEquals(1_559, jdbc.queryForObject("SELECT COUNT(DISTINCT product_id) FROM utility_matrix_item WHERE snapshot_id=?", Integer.class, snapshotId));
        assertEquals(1_759, jdbc.queryForObject("SELECT COUNT(*) FROM utility_matrix_item WHERE snapshot_id=?", Integer.class, snapshotId));
        assertEquals(0, new BigDecimal("17590.000000").compareTo(jdbc.queryForObject(
            "SELECT SUM(weighted_utility) FROM utility_matrix_item WHERE snapshot_id=?", BigDecimal.class, snapshotId)));
        assertEquals(matrixHash, jdbc.queryForObject("SELECT matrix_hash FROM utility_snapshot WHERE snapshot_id=? AND store_id=?", String.class, snapshotId, store));

        JsonNode miningConfig = body(get("/api/system/mining/config", system).andExpect(status().isOk()));
        put("/api/system/mining/config", system, "{\"minUtilMode\":\"ABSOLUTE\",\"minUtilValue\":10,\"expectedVersion\":" + miningConfig.path("version").asLong() + "}")
            .andExpect(status().isOk());
        String requestKey = UUID.randomUUID().toString();
        JsonNode requestCreated = body(post("/api/mining/requests", merchant,
            json.writeValueAsString(Map.of("requestKey", requestKey, "snapshotId", snapshotId))).andExpect(status().isAccepted()));
        long requestId = requestCreated.path("requestId").asLong();
        JsonNode terminal = awaitRequest(requestId, merchant, 60);
        assertEquals("COMPLETED", terminal.path("status").asText());
        assertEquals("EFIM", terminal.path("algorithms").get(0).asText());
        assertEquals(1_559, terminal.path("distinctItems").asInt());
        assertEquals(1_659, terminal.path("transactionCount").asInt());
        assertEquals(matrixHash, terminal.path("snapshotHash").asText());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=? AND request_id=? AND status='COMPLETED'", Integer.class, store, requestId));

        String frozenJson = jdbc.queryForObject("SELECT frozen_input FROM mining_request WHERE store_id=? AND request_id=?", String.class, store, requestId);
        Dataset frozen = json.readValue(frozenJson, Dataset.class);
        assertEquals(terminal.path("inputHash").asText(), frozen.hash());
        assertEquals(frozen.hash(), MiningInputBuilder.sha(frozen.text()));
        assertEquals(6, frozen.utilityScale());
        assertEquals("HALF_UP", frozen.roundingMode());
        assertEquals(10_000_000L, frozen.divisor());
        assertEquals(PRODUCT_COUNT, frozen.products().size());
        assertEquals(1_659, frozen.transactionCount());
        assertEquals(1_659, frozen.text().lines().count());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mining_run WHERE store_id=? AND request_id=? AND algorithm='EFIM' AND status='COMPLETED'", Integer.class, store, requestId));

        JsonNode runs = body(get("/api/mining/requests/" + requestId + "/runs", merchant).andExpect(status().isOk()));
        assertEquals(1, runs.size());
        long runId = runs.get(0).path("runId").asLong();
        int patternCount = runs.get(0).path("patternCount").asInt();
        assertEquals("COMPLETED", runs.get(0).path("status").asText());
        assertEquals("EFIM", runs.get(0).path("algorithm").asText());
        assertTrue(patternCount > 0 && patternCount <= 2_000);
        assertEquals(patternCount, jdbc.queryForObject("SELECT COUNT(*) FROM high_utility_pattern WHERE store_id=? AND run_id=?", Integer.class, store, runId));
        List<Map<String, Object>> mappings = jdbc.queryForList("SELECT pi.product_id,pi.algorithm_item_id FROM pattern_item pi JOIN high_utility_pattern p ON p.store_id=pi.store_id AND p.pattern_id=pi.pattern_id WHERE p.store_id=? AND p.run_id=?", store, runId);
        assertTrue(mappings.size() >= 2);
        for (Map<String, Object> mapping : mappings) {
            int productId = ((Number) mapping.get("product_id")).intValue();
            int algorithmItemId = ((Number) mapping.get("algorithm_item_id")).intValue();
            assertEquals(productId, frozen.products().get(algorithmItemId), "persisted dense item mapping must resolve to the frozen Product ID");
        }
        JsonNode progress = body(get("/api/mining/requests/" + requestId + "/progress", merchant).andExpect(status().isOk()));
        assertEquals("COMPLETED", progress.path("status").asText());
        assertEquals(100, progress.path("percent").asInt());

        JsonNode patternPage = body(get("/api/mining/runs/" + runId + "/patterns", merchant).andExpect(status().isOk()));
        assertEquals(patternCount, patternPage.path("totalElements").asInt());
        JsonNode generated = body(post("/api/recommendations/generate", merchant,
            json.writeValueAsString(Map.of("miningRunId", runId, "types", List.of("BUNDLE")))).andExpect(status().isOk()));
        assertTrue(generated.path("createdCount").asInt() >= 1, "F06 should create a Bundle candidate from the persisted EFIM pair pattern");
        long recommendationId = generated.path("recommendationIds").get(0).asLong();
        JsonNode candidate = body(get("/api/recommendations/" + recommendationId, merchant).andExpect(status().isOk()));
        assertEquals("BUNDLE", candidate.path("type").asText());
        assertEquals("AVAILABLE", candidate.path("evidence").path("status").asText());
        assertEquals(100, candidate.path("evidence").path("supportCount").asInt());
        assertEquals(List.of(productIds.get(SKU_PREFIX + "0001"), productIds.get(SKU_PREFIX + "0002")),
            json.convertValue(candidate.path("productIds"), json.getTypeFactory().constructCollectionType(List.class, Integer.class)));

        int otherStore = createStore("Synthetic FoodMart-scale foreign Store");
        String foreignMerchant = token("MERCHANDISER", otherStore);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/utility/snapshots/" + snapshotId).header("Authorization", "Bearer " + foreignMerchant)).andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/mining/requests/" + requestId).header("Authorization", "Bearer " + foreignMerchant)).andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/recommendations/" + recommendationId).header("Authorization", "Bearer " + foreignMerchant)).andExpect(status().isNotFound());

        writeEvidence(csvBytes, snapshotId, matrixHash, requestId, terminal, frozen, runId, patternCount,
            mappings.size(), recommendationId, generated.path("createdCount").asInt());
    }

    private void seedProductsAndSellableBatches(int store) {
        List<Object[]> products = new ArrayList<>(PRODUCT_COUNT);
        for (int i = 1; i <= PRODUCT_COUNT; i++) {
            String sku = sku(i);
            products.add(new Object[] {store, sku, "Synthetic FoodMart Product " + i});
        }
        jdbc.batchUpdate("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,?,'each',FALSE,20.00,10.00,'ACTIVE')", products);
        Map<String, Integer> ids = productIds(store);
        List<Object[]> batches = new ArrayList<>(PRODUCT_COUNT);
        for (int i = 1; i <= PRODUCT_COUNT; i++) batches.add(new Object[] {ids.get(sku(i)), "E2E-" + sku(i)});
        jdbc.batchUpdate("INSERT INTO inventory_batch(product_id,batch_code,received_at,initial_quantity,remaining_quantity,status) VALUES (?,?,CURRENT_TIMESTAMP,1000,1000,'ACTIVE')", batches);
    }

    private Map<String, Integer> productIds(int store) {
        return jdbc.query("SELECT sku,product_id FROM product WHERE store_id=? AND sku LIKE ? ORDER BY sku",
            rs -> { var found = new java.util.LinkedHashMap<String, Integer>(); while (rs.next()) found.put(rs.getString(1), rs.getInt(2)); return found; }, store, SKU_PREFIX + "%");
    }

    private String syntheticCsv(String occurredAt) {
        var csv = new StringBuilder(HEADER.length() + 160_000).append(HEADER);
        for (int i = 1; i <= PRODUCT_COUNT; i++) appendLine(csv, "SYN-FM-E2E-SINGLE-" + i, occurredAt, sku(i));
        for (int i = 1; i <= REPEATED_PAIR_ORDERS; i++) {
            String order = "SYN-FM-E2E-PAIR-" + i;
            appendLine(csv, order, occurredAt, sku(1));
            appendLine(csv, order, occurredAt, sku(2));
        }
        return csv.toString();
    }

    private static void appendLine(StringBuilder csv, String orderId, String occurredAt, String sku) {
        csv.append(orderId).append(',').append(occurredAt).append(',').append(sku).append(",1,10.00,0.00,COMPLETED\n");
    }

    private static String sku(int product) { return SKU_PREFIX + String.format(java.util.Locale.ROOT, "%04d", product); }

    private int createStore(String name) {
        jdbc.update("INSERT INTO store(name) VALUES (?)", name + " " + UUID.randomUUID());
        return jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
    }

    private String token(String role, Integer storeId) {
        var user = users.createPendingUser("Synthetic scale E2E", UUID.randomUUID() + "@example.test", role, storeId, null, null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=? WHERE user_id=?", java.sql.Timestamp.from(Instant.now()), user.id());
        return auth.token(users.findById(user.id())).accessToken();
    }

    private ResultActions put(String path, String token, String payload) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path).header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(payload));
    }

    private ResultActions get(String path, String token) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).header("Authorization", "Bearer " + token));
    }

    private ResultActions post(String path, String token, String payload) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(payload));
    }

    private JsonNode body(ResultActions action) throws Exception {
        return json.readTree(action.andReturn().getResponse().getContentAsString());
    }

    private JsonNode awaitRequest(long requestId, String merchant, long timeoutSeconds) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        JsonNode latest;
        do {
            latest = body(get("/api/mining/requests/" + requestId, merchant));
            String state = latest.path("status").asText();
            if ("COMPLETED".equals(state) || "FAILED".equals(state) || "TIMED_OUT".equals(state)) return latest;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        fail("Mining request did not reach a terminal state within " + timeoutSeconds + " seconds; last state=" + latest.path("status").asText());
        return latest;
    }

    private void writeEvidence(byte[] csv, int snapshotId, String matrixHash, long requestId, JsonNode request,
            Dataset input, long runId, int patternCount, int mappingCount, long recommendationId, int candidateCount) throws Exception {
        String target = System.getProperty("paspm.f05.foodmart-e2e.output");
        if (target == null || target.isBlank()) return;
        Path directory = Path.of(target);
        Files.createDirectories(directory);
        var evidence = Map.ofEntries(
            Map.entry("fixtureType", "SYNTHETIC_PASPM_SCALE_FIXTURE_NOT_AUTHENTIC_FOODMART_SNAPSHOT"),
            Map.entry("productCount", PRODUCT_COUNT), Map.entry("importedTransactions", 1_659),
            Map.entry("importedTransactionItems", 1_759), Map.entry("matrixRows", 1_759),
            Map.entry("csvSha256", sha(csv)), Map.entry("snapshotId", snapshotId),
            Map.entry("snapshotMatrixHash", matrixHash), Map.entry("snapshotStatus", "READY"),
            Map.entry("snapshotWeightedUtilityTotal", "17590.000000"),
            Map.entry("scoringVersion", "W2_EXPIRY_SEASON_V1"),
            Map.entry("requestId", requestId), Map.entry("minUtilMode", "ABSOLUTE"),
            Map.entry("minUtilValue", "10"), Map.entry("minUtilSpmf", request.path("minUtilSpmf").asInt()),
            Map.entry("inputHash", input.hash()), Map.entry("conversionVersion", input.conversionVersion()),
            Map.entry("utilityScale", input.utilityScale()), Map.entry("roundingMode", input.roundingMode()),
            Map.entry("divisor", input.divisor()), Map.entry("transactionCount", input.transactionCount()),
            Map.entry("distinctProductIds", input.products().size()), Map.entry("inputBytes", input.inputBytes()),
            Map.entry("requestStatus", request.path("status").asText()), Map.entry("runId", runId),
            Map.entry("runStatus", "COMPLETED"), Map.entry("algorithm", "EFIM"),
            Map.entry("patternCount", patternCount), Map.entry("persistedPatternItemMappings", mappingCount),
            Map.entry("recommendationId", recommendationId), Map.entry("f06CreatedCount", candidateCount),
            Map.entry("runtimeLimits", Map.of("heapMiB", 256, "timeoutMillis", 60_000, "maxPatterns", 2_000, "maxOutputBytes", 4_194_304))
        );
        Files.writeString(directory.resolve("foodmart-scale-synthetic-e2e.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}

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
    private static final String DENSE_SKU_PREFIX = "SYN-FM-DENSE-";
    private static final String REALISTIC_SKU_PREFIX = "SYN-FM-REALISTIC-";
    private static final int DENSE_ORDERS=4_100, DENSE_ROWS=18_040, DENSE_PAIR_ORDERS=250;
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
        seedProductsAndSellableBatches(store,SKU_PREFIX);
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

    @Test
    void syntheticFoodMartDenseFixtureFlowsThroughF04F03F05AndF06() throws Exception {
        int store=createStore("Synthetic FoodMart dense PASPM E2E");
        String admin=token("STORE_ADMIN",store),merchant=token("MERCHANDISER",store);
        seedProductsAndSellableBatches(store,DENSE_SKU_PREFIX);
        Map<String,Integer> productIds=productIds(store,DENSE_SKU_PREFIX);
        assertEquals(PRODUCT_COUNT,productIds.size());

        LocalDate analysisDate=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        String occurredAt=analysisDate.atTime(12,0).atOffset(ZoneOffset.ofHours(7)).toString();
        byte[] canonicalFixture=denseCsv(occurredAt,0,DENSE_ORDERS).getBytes(StandardCharsets.UTF_8);
        int importedRows=0,importedOrders=0;
        for(int part=0;part<2;part++){
            byte[] csv=denseCsv(occurredAt,part*(DENSE_ORDERS/2),DENSE_ORDERS/2).getBytes(StandardCharsets.UTF_8);
            JsonNode imported=json.readTree(mvc.perform(multipart("/api/transactions/imports/csv")
                    .file(new MockMultipartFile("file","synthetic-foodmart-dense-"+part+".csv","text/csv",csv))
                    .header("Authorization","Bearer "+admin))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
            assertEquals("COMPLETED",imported.path("status").asText());
            importedRows+=imported.path("validCount").asInt();
            importedOrders+=imported.path("acceptedOrderCount").asInt();
            assertEquals(0,imported.path("rejectedCount").asInt());
        }
        assertEquals(DENSE_ROWS,importedRows);
        assertEquals(DENSE_ORDERS,importedOrders);

        String system=token("SYSTEM_ADMIN",null);
        put("/api/system/utility/lambda-policy",system,"{\"lambdaMax\":0.5}").andExpect(status().isOk());
        put("/api/utility/config",merchant,"{\"alpha\":0.5,\"beta\":0.5,\"lambda\":0.5,\"salesWindowDays\":30}").andExpect(status().isOk());
        String snapshotPayload=json.writeValueAsString(Map.of("rangeStart",analysisDate,"rangeEnd",analysisDate,"asOfDate",analysisDate));
        JsonNode snapshot=body(post("/api/utility/snapshots",merchant,snapshotPayload).andExpect(status().isCreated()));
        int snapshotId=snapshot.path("snapshotId").asInt();String matrixHash=snapshot.path("matrixHash").asText();
        assertEquals("READY",snapshot.path("status").asText());
        assertEquals(PRODUCT_COUNT,jdbc.queryForObject("SELECT COUNT(DISTINCT product_id) FROM utility_matrix_item WHERE snapshot_id=?",Integer.class,snapshotId));
        assertEquals(DENSE_ROWS,jdbc.queryForObject("SELECT COUNT(*) FROM utility_matrix_item WHERE snapshot_id=?",Integer.class,snapshotId));

        JsonNode miningConfig=body(get("/api/system/mining/config",system).andExpect(status().isOk()));
        put("/api/system/mining/config",system,"{\"minUtilMode\":\"ABSOLUTE\",\"minUtilValue\":500,\"expectedVersion\":"+miningConfig.path("version").asLong()+"}").andExpect(status().isOk());
        String requestKey=UUID.randomUUID().toString();
        JsonNode accepted=body(post("/api/mining/requests",merchant,json.writeValueAsString(Map.of("requestKey",requestKey,"snapshotId",snapshotId))).andExpect(status().isAccepted()));
        long requestId=accepted.path("requestId").asLong();JsonNode terminal=awaitRequest(requestId,merchant,120);
        assertEquals("COMPLETED",terminal.path("status").asText());
        assertEquals("EFIM",terminal.path("algorithms").get(0).asText());
        assertEquals(DENSE_ORDERS,terminal.path("transactionCount").asInt());
        assertEquals(PRODUCT_COUNT,terminal.path("distinctItems").asInt());

        String frozenJson=jdbc.queryForObject("SELECT frozen_input FROM mining_request WHERE store_id=? AND request_id=?",String.class,store,requestId);
        Dataset frozen=json.readValue(frozenJson,Dataset.class);
        int maxTransactionUtility=0;long totalNormalizedUtility=0;
        for(String line:frozen.text().lines().toList()){
            String[] sections=line.split(":");int transactionUtility=Integer.parseInt(sections[1]);
            maxTransactionUtility=Math.max(maxTransactionUtility,transactionUtility);totalNormalizedUtility=Math.addExact(totalNormalizedUtility,transactionUtility);
        }
        assertEquals(PRODUCT_COUNT,frozen.products().size());
        assertEquals(DENSE_ORDERS,frozen.transactionCount());
        assertEquals(5,maxTransactionUtility);
        assertEquals(DENSE_ROWS,totalNormalizedUtility);
        assertEquals(DENSE_ROWS,frozen.totalUtility());
        assertTrue(totalNormalizedUtility<Integer.MAX_VALUE);
        assertTrue(maxTransactionUtility<Integer.MAX_VALUE);

        JsonNode runs=body(get("/api/mining/requests/"+requestId+"/runs",merchant).andExpect(status().isOk()));
        assertEquals(1,runs.size());JsonNode run=runs.get(0);long runId=run.path("runId").asLong();
        assertEquals("COMPLETED",run.path("status").asText());
        int patternCount=run.path("patternCount").asInt();
        assertTrue(patternCount>0&&patternCount<2_000,"dense benchmark must complete under the configured pattern cap");
        long outputBytes=environmentMetric(run.path("environment").asText(),"outputBytes");
        long efimMillis=run.path("runtimeMillis").asLong();
        assertTrue(efimMillis>0);assertTrue(outputBytes>0&&outputBytes<4L*1024*1024);
        assertEquals(patternCount,jdbc.queryForObject("SELECT COUNT(*) FROM high_utility_pattern WHERE store_id=? AND run_id=?",Integer.class,store,runId));

        JsonNode generated=body(post("/api/recommendations/generate",merchant,json.writeValueAsString(Map.of("miningRunId",runId,"types",List.of("BUNDLE")))).andExpect(status().isOk()));
        assertTrue(generated.path("createdCount").asInt()>=1,"F06 should consume the repeated high-support pair from the dense fixture");
        JsonNode candidate=body(get("/api/recommendations/"+generated.path("recommendationIds").get(0).asLong(),merchant).andExpect(status().isOk()));
        assertEquals("AVAILABLE",candidate.path("evidence").path("status").asText());
        assertEquals(DENSE_PAIR_ORDERS,candidate.path("evidence").path("supportCount").asInt());
        assertEquals(List.of(productIds.get(DENSE_SKU_PREFIX+"0001"),productIds.get(DENSE_SKU_PREFIX+"0002")),
            json.convertValue(candidate.path("productIds"),json.getTypeFactory().constructCollectionType(List.class,Integer.class)));
        writeDenseEvidence(canonicalFixture,analysisDate,snapshotId,matrixHash,requestId,frozen,runId,efimMillis,patternCount,outputBytes,maxTransactionUtility,totalNormalizedUtility,candidate);
    }

    @Test
    void realisticUtilityDenseFixtureReportsInt32AndEfimOutcomeAtCurrentMinUtil() throws Exception {
        int store=createStore("Synthetic FoodMart realistic-utility PASPM E2E");
        String admin=token("STORE_ADMIN",store),merchant=token("MERCHANDISER",store),system=token("SYSTEM_ADMIN",null);
        LocalDate analysisDate=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        seedRealisticProductsAndBatches(store,REALISTIC_SKU_PREFIX,analysisDate);
        Map<String,Integer> productIds=productIds(store,REALISTIC_SKU_PREFIX);
        assertEquals(PRODUCT_COUNT,productIds.size());

        String occurredAt=analysisDate.atTime(12,0).atOffset(ZoneOffset.ofHours(7)).toString();
        int importedRows=0,importedOrders=0;
        for(int part=0;part<2;part++){
            byte[] csv=realisticDenseCsv(occurredAt,part*(DENSE_ORDERS/2),DENSE_ORDERS/2).getBytes(StandardCharsets.UTF_8);
            JsonNode imported=json.readTree(mvc.perform(multipart("/api/transactions/imports/csv")
                    .file(new MockMultipartFile("file","synthetic-foodmart-realistic-"+part+".csv","text/csv",csv))
                    .header("Authorization","Bearer "+admin))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
            assertEquals("COMPLETED",imported.path("status").asText());
            importedRows+=imported.path("validCount").asInt();importedOrders+=imported.path("acceptedOrderCount").asInt();
            assertEquals(0,imported.path("rejectedCount").asInt());
        }
        assertEquals(DENSE_ROWS,importedRows);assertEquals(DENSE_ORDERS,importedOrders);
        BigDecimal minPrice=jdbc.queryForObject("SELECT MIN(i.unit_price) FROM transaction_item i JOIN sales_transaction t ON t.store_id=i.store_id AND t.transaction_id=i.transaction_id WHERE t.store_id=?",BigDecimal.class,store);
        BigDecimal maxPrice=jdbc.queryForObject("SELECT MAX(i.unit_price) FROM transaction_item i JOIN sales_transaction t ON t.store_id=i.store_id AND t.transaction_id=i.transaction_id WHERE t.store_id=?",BigDecimal.class,store);
        Integer minQuantity=jdbc.queryForObject("SELECT MIN(i.quantity) FROM transaction_item i JOIN sales_transaction t ON t.store_id=i.store_id AND t.transaction_id=i.transaction_id WHERE t.store_id=?",Integer.class,store);
        Integer maxQuantity=jdbc.queryForObject("SELECT MAX(i.quantity) FROM transaction_item i JOIN sales_transaction t ON t.store_id=i.store_id AND t.transaction_id=i.transaction_id WHERE t.store_id=?",Integer.class,store);
        assertEquals(0,new BigDecimal("0.50").compareTo(minPrice));assertEquals(0,new BigDecimal("200.00").compareTo(maxPrice));
        assertEquals(1,minQuantity);assertEquals(10,maxQuantity);

        put("/api/system/utility/lambda-policy",system,"{\"lambdaMax\":0.5}").andExpect(status().isOk());
        put("/api/utility/config",merchant,"{\"alpha\":0.5,\"beta\":0.5,\"lambda\":0.5,\"salesWindowDays\":30}").andExpect(status().isOk());
        String snapshotPayload=json.writeValueAsString(Map.of("rangeStart",analysisDate,"rangeEnd",analysisDate,"asOfDate",analysisDate));
        JsonNode snapshot=body(post("/api/utility/snapshots",merchant,snapshotPayload).andExpect(status().isCreated()));
        int snapshotId=snapshot.path("snapshotId").asInt();
        assertEquals("READY",snapshot.path("status").asText());
        assertEquals("W2_EXPIRY_SEASON_V1",snapshot.path("configuration").path("config").path("scoringVersion").asText());
        assertEquals(PRODUCT_COUNT,jdbc.queryForObject("SELECT COUNT(DISTINCT product_id) FROM utility_matrix_item WHERE snapshot_id=?",Integer.class,snapshotId));
        assertEquals(DENSE_ROWS,jdbc.queryForObject("SELECT COUNT(*) FROM utility_matrix_item WHERE snapshot_id=?",Integer.class,snapshotId));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_batch b JOIN product p ON p.product_id=b.product_id WHERE p.store_id=? AND b.push_day IS NOT NULL",Integer.class,store)>0);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM utility_matrix_item WHERE snapshot_id=? AND season_weight>0",Integer.class,snapshotId)>0);
        assertTrue(jdbc.queryForObject("SELECT COUNT(DISTINCT weight_value) FROM utility_matrix_item WHERE snapshot_id=?",Integer.class,snapshotId)>1);

        JsonNode priorConfig=body(get("/api/system/mining/config",system).andExpect(status().isOk()));
        JsonNode currentConfig=body(put("/api/system/mining/config",system,"{\"minUtilMode\":\"ABSOLUTE\",\"minUtilValue\":500,\"expectedVersion\":"+priorConfig.path("version").asLong()+"}").andExpect(status().isOk()));

        List<Map<String,Object>> matrix=jdbc.queryForList("SELECT transaction_id,product_id,weighted_utility FROM utility_matrix_item WHERE snapshot_id=? ORDER BY transaction_id,product_id",snapshotId);
        long divisor=0,totalScaled=0,maxTransactionScaled=0;
        Map<Integer,Long> transactionTotals=new java.util.TreeMap<>();Map<Integer,List<Integer>> productsByTransaction=new java.util.HashMap<>();
        for(Map<String,Object> row:matrix){
            int transactionId=((Number)row.get("transaction_id")).intValue(),productId=((Number)row.get("product_id")).intValue();
            BigDecimal weighted=(BigDecimal)row.get("weighted_utility");long scaled=weighted.setScale(6,java.math.RoundingMode.UNNECESSARY).movePointRight(6).longValueExact();
            divisor=gcdNonNegative(divisor,scaled);totalScaled=Math.addExact(totalScaled,scaled);
            transactionTotals.merge(transactionId,scaled,Math::addExact);productsByTransaction.computeIfAbsent(transactionId,ignored->new ArrayList<>()).add(productId);
        }
        Map<Integer,Long> itemTwuScaled=new java.util.HashMap<>();
        for(var transaction:transactionTotals.entrySet()){
            maxTransactionScaled=Math.max(maxTransactionScaled,transaction.getValue());
            for(int productId:productsByTransaction.get(transaction.getKey()))itemTwuScaled.merge(productId,transaction.getValue(),Math::addExact);
        }
        long maxTransactionUtility=maxTransactionScaled/divisor,totalUtility=totalScaled/divisor;
        long measuredDivisor=divisor;
        long maxItemTwu=itemTwuScaled.values().stream().mapToLong(value->value/measuredDivisor).max().orElse(0);
        assertTrue(divisor>0&&divisor<10_000_000,"varied cent prices, quantities and W2 scores should lower the scale-6 GCD from the prior fixture");

        String requestKey=UUID.randomUUID().toString();
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/mining/requests")
                .header("Authorization","Bearer "+merchant).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("requestKey",requestKey,"snapshotId",snapshotId))))
            .andReturn().getResponse();
        int httpStatus=response.getStatus();String responseBody=response.getContentAsString();
        assertTrue(httpStatus==202||httpStatus==400,"mining request must either be accepted or rejected by the expected input guard; status="+httpStatus+" body="+responseBody);
        long requestId=0,runId=0,efimMillis=-1,outputBytes=-1;int patternCount=-1;String miningStatus=httpStatus==202?"ACCEPTED":"REJECTED",failureCode="";String f06Status="NOT_RUN";
        if(httpStatus==400){
            assertTrue(totalUtility>Integer.MAX_VALUE,"a non-overflow fixture must not be classified as an expected int32 rejection");
            assertTrue(responseBody.contains("Lossless converted dataset exceeds SPMF int32 capacity"),"unexpected exact rejection: "+responseBody);
            failureCode="Lossless converted dataset exceeds SPMF int32 capacity";
        }else{
            assertTrue(totalUtility<=Integer.MAX_VALUE,"builder should accept a losslessly convertible total");
            JsonNode accepted=json.readTree(responseBody);requestId=accepted.path("requestId").asLong();
            JsonNode terminal=awaitRequest(requestId,merchant,120);miningStatus=terminal.path("status").asText();
            JsonNode request=body(get("/api/mining/requests/"+requestId,merchant).andExpect(status().isOk()));
            assertEquals(currentConfig.path("minUtilMode").asText(),request.path("minUtilMode").asText());
            assertEquals(0,currentConfig.path("minUtilValue").decimalValue().compareTo(request.path("minUtilValue").decimalValue()));
            List<Map<String,Object>> runs=jdbc.queryForList("SELECT run_id,status,runtime_millis,environment,pattern_count,failure_code FROM mining_run WHERE store_id=? AND request_id=?",store,requestId);
            if(!runs.isEmpty()){
                Map<String,Object> run=runs.get(0);runId=((Number)run.get("run_id")).longValue();patternCount=run.get("pattern_count")==null?-1:((Number)run.get("pattern_count")).intValue();
                efimMillis=run.get("runtime_millis")==null?-1:((Number)run.get("runtime_millis")).longValue();failureCode=run.get("failure_code")==null?"":run.get("failure_code").toString();
                String environment=run.get("environment")==null?"":run.get("environment").toString();if(environment.contains("outputBytes="))outputBytes=environmentMetric(environment,"outputBytes");
            }
            if("COMPLETED".equals(miningStatus)){
                JsonNode generated=body(post("/api/recommendations/generate",merchant,json.writeValueAsString(Map.of("miningRunId",runId,"types",List.of("BUNDLE")))).andExpect(status().isOk()));
                if(generated.path("createdCount").asInt()>0){
                    JsonNode candidate=body(get("/api/recommendations/"+generated.path("recommendationIds").get(0).asLong(),merchant).andExpect(status().isOk()));
                    f06Status=candidate.path("evidence").path("status").asText();
                }else f06Status="NO_CANDIDATE";
            }
        }
        writeRealisticEvidence(snapshotId,analysisDate,productIds.size(),matrix.size(),minPrice,maxPrice,minQuantity,maxQuantity,divisor,
            maxTransactionUtility,maxItemTwu,totalUtility,currentConfig.path("minUtilMode").asText(),currentConfig.path("minUtilValue").asText(),
            httpStatus,miningStatus,failureCode,requestId,runId,efimMillis,patternCount,outputBytes,f06Status);
        System.out.printf("F05_REALISTIC_DENSE divisor=%d maxTransactionUtility=%d maxItemTWU=%d totalUtility=%d int32=%d status=%d/%s minUtil=%s/%s efimMs=%d patterns=%d outputBytes=%d F06=%s%n",
            divisor,maxTransactionUtility,maxItemTwu,totalUtility,Integer.MAX_VALUE,httpStatus,miningStatus,currentConfig.path("minUtilMode").asText(),
            currentConfig.path("minUtilValue").asText(),efimMillis,patternCount,outputBytes,f06Status);
    }

    private void seedProductsAndSellableBatches(int store) {
        seedProductsAndSellableBatches(store,SKU_PREFIX);
    }

    private void seedProductsAndSellableBatches(int store,String skuPrefix) {
        List<Object[]> products = new ArrayList<>(PRODUCT_COUNT);
        for (int i = 1; i <= PRODUCT_COUNT; i++) {
            String sku = sku(skuPrefix,i);
            products.add(new Object[] {store, sku, "Synthetic FoodMart Product " + i});
        }
        jdbc.batchUpdate("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,?,'each',FALSE,20.00,10.00,'ACTIVE')", products);
        Map<String, Integer> ids = productIds(store,skuPrefix);
        List<Object[]> batches = new ArrayList<>(PRODUCT_COUNT);
        for (int i = 1; i <= PRODUCT_COUNT; i++) batches.add(new Object[] {ids.get(sku(skuPrefix,i)), "E2E-" + sku(skuPrefix,i)});
        jdbc.batchUpdate("INSERT INTO inventory_batch(product_id,batch_code,received_at,initial_quantity,remaining_quantity,status) VALUES (?,?,CURRENT_TIMESTAMP,1000,1000,'ACTIVE')", batches);
    }

    private void seedRealisticProductsAndBatches(int store,String skuPrefix,LocalDate analysisDate) {
        List<Object[]> products=new ArrayList<>(PRODUCT_COUNT);
        for(int i=1;i<=PRODUCT_COUNT;i++){
            int priceCents=50+Math.floorMod(i*7919,19_951);
            BigDecimal selling=BigDecimal.valueOf(priceCents,2),cost=BigDecimal.valueOf(Math.max(1,priceCents*58/100),2);
            products.add(new Object[]{store,sku(skuPrefix,i),"Synthetic Realistic FoodMart Product "+i,selling,cost});
        }
        jdbc.batchUpdate("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,?,'each',TRUE,?,?,'ACTIVE')",products);
        Map<String,Integer> ids=productIds(store,skuPrefix);
        List<Object[]> batches=new ArrayList<>(PRODUCT_COUNT);
        for(int i=1;i<=PRODUCT_COUNT;i++){
            LocalDate pushDay=i%4==0?null:analysisDate.plusDays(1+i%20);
            batches.add(new Object[]{ids.get(sku(skuPrefix,i)),"REALISTIC-"+i,analysisDate.plusDays(60),500,500,pushDay});
        }
        jdbc.batchUpdate("INSERT INTO inventory_batch(product_id,batch_code,received_at,expiry_date,initial_quantity,remaining_quantity,status,push_day) VALUES (?,?,CURRENT_TIMESTAMP,?,?,?,'ACTIVE',?)",batches);
        int actor=jdbc.queryForObject("SELECT MIN(user_id) FROM app_user WHERE store_id=?",Integer.class,store);
        jdbc.update("INSERT INTO season(store_id,name,start_date,end_date,weight,created_by,updated_by) VALUES (?,?,?,?,0.7,?,?)",
            store,"Synthetic active season",analysisDate.minusDays(5),analysisDate.plusDays(5),actor,actor);
        int seasonId=jdbc.queryForObject("SELECT MAX(season_id) FROM season WHERE store_id=?",Integer.class,store);
        List<Object[]> assignments=new ArrayList<>();
        for(int i=2;i<=PRODUCT_COUNT;i+=2)assignments.add(new Object[]{store,seasonId,ids.get(sku(skuPrefix,i)),actor});
        jdbc.batchUpdate("INSERT INTO product_season(store_id,season_id,product_id,assigned_by) VALUES (?,?,?,?)",assignments);
    }

    private Map<String, Integer> productIds(int store) { return productIds(store,SKU_PREFIX); }

    private Map<String, Integer> productIds(int store,String skuPrefix) {
        return jdbc.query("SELECT sku,product_id FROM product WHERE store_id=? AND sku LIKE ? ORDER BY sku",
            rs -> { var found = new java.util.LinkedHashMap<String, Integer>(); while (rs.next()) found.put(rs.getString(1), rs.getInt(2)); return found; }, store, skuPrefix + "%");
    }

    private String syntheticCsv(String occurredAt) {
        var csv = new StringBuilder(HEADER.length() + 160_000).append(HEADER);
        for (int i = 1; i <= PRODUCT_COUNT; i++) appendLine(csv, "SYN-FM-E2E-SINGLE-" + i, occurredAt, sku(SKU_PREFIX,i));
        for (int i = 1; i <= REPEATED_PAIR_ORDERS; i++) {
            String order = "SYN-FM-E2E-PAIR-" + i;
            appendLine(csv, order, occurredAt, sku(SKU_PREFIX,1));
            appendLine(csv, order, occurredAt, sku(SKU_PREFIX,2));
        }
        return csv.toString();
    }

    private String denseCsv(String occurredAt,int firstOrder,int orderCount) {
        var csv=new StringBuilder(1_000_000).append(HEADER);
        for(int order=firstOrder;order<firstOrder+orderCount;order++){
            String orderId="DENSE-"+String.format(java.util.Locale.ROOT,"%05d",order);
            if(order<DENSE_PAIR_ORDERS){
                appendLine(csv,orderId,occurredAt,sku(DENSE_SKU_PREFIX,1));
                appendLine(csv,orderId,occurredAt,sku(DENSE_SKU_PREFIX,2));
                int third=3+(order%(PRODUCT_COUNT-2)),fourth=3+((order+1)%(PRODUCT_COUNT-2));
                appendLine(csv,orderId,occurredAt,sku(DENSE_SKU_PREFIX,third));
                appendLine(csv,orderId,occurredAt,sku(DENSE_SKU_PREFIX,fourth));
            }else{
                int index=order-DENSE_PAIR_ORDERS,items=index<1_640?5:4,start=3+((index*5)%(PRODUCT_COUNT-2));
                for(int offset=0;offset<items;offset++)appendLine(csv,orderId,occurredAt,sku(DENSE_SKU_PREFIX,3+((start-3+offset)%(PRODUCT_COUNT-2))));
            }
        }
        long expectedRows=0;
        for(int order=firstOrder;order<firstOrder+orderCount;order++)expectedRows+=order<DENSE_PAIR_ORDERS?4:(order-DENSE_PAIR_ORDERS)<1_640?5:4;
        long lines=csv.toString().lines().skip(1).count();
        if(lines!=expectedRows)throw new IllegalStateException("Dense fixture row count drift: "+lines+" != "+expectedRows);
        return csv.toString();
    }

    private String realisticDenseCsv(String occurredAt,int firstOrder,int orderCount) {
        var csv=new StringBuilder(1_250_000).append(HEADER);
        for(int order=firstOrder;order<firstOrder+orderCount;order++){
            String orderId="REALISTIC-DENSE-"+String.format(java.util.Locale.ROOT,"%05d",order);
            if(order<DENSE_PAIR_ORDERS){
                appendRealisticLine(csv,orderId,occurredAt,order,1);appendRealisticLine(csv,orderId,occurredAt,order,2);
                int third=3+(order%(PRODUCT_COUNT-2)),fourth=3+((order+1)%(PRODUCT_COUNT-2));
                appendRealisticLine(csv,orderId,occurredAt,order,third);appendRealisticLine(csv,orderId,occurredAt,order,fourth);
            }else{
                int index=order-DENSE_PAIR_ORDERS,items=index<1_640?5:4,start=3+((index*5)%(PRODUCT_COUNT-2));
                for(int offset=0;offset<items;offset++)appendRealisticLine(csv,orderId,occurredAt,order,3+((start-3+offset)%(PRODUCT_COUNT-2)));
            }
        }
        long expectedRows=0;
        for(int order=firstOrder;order<firstOrder+orderCount;order++)expectedRows+=order<DENSE_PAIR_ORDERS?4:(order-DENSE_PAIR_ORDERS)<1_640?5:4;
        if(csv.toString().lines().skip(1).count()!=expectedRows)throw new IllegalStateException("Realistic fixture row count drift");
        return csv.toString();
    }

    private static void appendRealisticLine(StringBuilder csv,String orderId,String occurredAt,int order,int productNo) {
        int priceCents=order==0&&productNo==1?50:order==0&&productNo==2?20_000:50+Math.floorMod(productNo*7_919+order*1_049,19_951);
        int quantity=order==0&&productNo==1?1:order==0&&productNo==2?10:1+Math.floorMod(order*7+productNo*3,10);
        int costCents=Math.max(1,priceCents*58/100);
        csv.append(orderId).append(',').append(occurredAt).append(',').append(sku(REALISTIC_SKU_PREFIX,productNo)).append(',')
            .append(quantity).append(',').append(BigDecimal.valueOf(priceCents,2).toPlainString()).append(',')
            .append(BigDecimal.valueOf(costCents,2).toPlainString()).append(",COMPLETED\n");
    }

    private static long gcdNonNegative(long left,long right) {
        while(right!=0){long remainder=left%right;left=right;right=remainder;}return left;
    }

    private void writeRealisticEvidence(int snapshotId,LocalDate analysisDate,int products,int rows,BigDecimal minPrice,BigDecimal maxPrice,
            int minQuantity,int maxQuantity,long divisor,long maxTransactionUtility,long maxItemTwu,long totalUtility,
            String minUtilMode,String minUtilValue,int httpStatus,String miningStatus,String failureCode,long requestId,long runId,
            long efimMillis,int patternCount,long outputBytes,String f06Status)throws Exception {
        String target=System.getProperty("paspm.f05.foodmart-e2e.output");if(target==null||target.isBlank())return;
        Path directory=Path.of(target).resolve("realistic-utilities");Files.createDirectories(directory);
        var evidence=Map.ofEntries(Map.entry("fixtureType","SYNTHETIC_FOODMART_SHAPED_REALISTIC_UTILITIES_NOT_AUTHENTIC_FOODMART"),
            Map.entry("orders",DENSE_ORDERS),Map.entry("matrixRows",rows),Map.entry("products",products),
            Map.entry("averageProductsPerOrder",(double)rows/DENSE_ORDERS),Map.entry("analysisDate",analysisDate.toString()),
            Map.entry("unitPriceRange",Map.of("min",minPrice.toPlainString(),"max",maxPrice.toPlainString())),
            Map.entry("quantityRange",Map.of("min",minQuantity,"max",maxQuantity)),
            Map.entry("weightVersion","W2_EXPIRY_SEASON_V1"),Map.entry("fixtureIncludesPushDayBatches",true),
            Map.entry("fixtureIncludesBatchesWithoutPushDay",true),Map.entry("fixtureIncludesActiveSeasonAssignments",true),
            Map.entry("conversionScale",6),Map.entry("gcdDivisor",divisor),
            Map.entry("maxTransactionUtilityAfterConversion",maxTransactionUtility),Map.entry("maxItemTwuAfterConversion",maxItemTwu),
            Map.entry("totalUtilityAfterConversion",totalUtility),Map.entry("int32Max",Integer.MAX_VALUE),
            Map.entry("maxTransactionUtilityInt32Headroom",Integer.MAX_VALUE-maxTransactionUtility),
            Map.entry("maxItemTwuInt32Headroom",Integer.MAX_VALUE-maxItemTwu),Map.entry("totalUtilityInt32Headroom",Integer.MAX_VALUE-totalUtility),
            Map.entry("currentMinUtil",Map.of("mode",minUtilMode,"value",minUtilValue)),Map.entry("miningHttpStatus",httpStatus),
            Map.entry("miningStatus",miningStatus),Map.entry("failureCode",failureCode),Map.entry("requestId",requestId),Map.entry("runId",runId),
            Map.entry("efimRuntimeMillis",efimMillis),Map.entry("patternCount",patternCount),Map.entry("outputBytes",outputBytes),
            Map.entry("f06Status",f06Status),Map.entry("runtimeLimits",Map.of("heapMiB",256,"timeoutMillis",60_000,"maxPatterns",2_000,"maxOutputBytes",4_194_304)));
        Files.writeString(directory.resolve("foodmart-dense-realistic-utilities.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
    }

    private static void appendLine(StringBuilder csv, String orderId, String occurredAt, String sku) {
        csv.append(orderId).append(',').append(occurredAt).append(',').append(sku).append(",1,10.00,0.00,COMPLETED\n");
    }

    private static String sku(int product) { return sku(SKU_PREFIX,product); }
    private static String sku(String prefix,int product) { return prefix + String.format(java.util.Locale.ROOT, "%04d", product); }

    private long environmentMetric(String environment,String key){
        for(String part:environment.split(";"))if(part.startsWith(key+"="))return Long.parseLong(part.substring(key.length()+1));
        throw new AssertionError("Missing "+key+" measurement in mining environment: "+environment);
    }

    private void writeDenseEvidence(byte[] csv,LocalDate analysisDate,int snapshotId,String matrixHash,long requestId,Dataset input,
            long runId,long efimMillis,int patternCount,long outputBytes,int maxTransactionUtility,long totalNormalizedUtility,JsonNode candidate)throws Exception{
        String target=System.getProperty("paspm.f05.foodmart-e2e.output");if(target==null||target.isBlank())return;
        Path directory=Path.of(target);Files.createDirectories(directory);
        var evidence=Map.ofEntries(
            Map.entry("fixtureType","SYNTHETIC_FOODMART_SHAPED_NOT_AUTHENTIC_FOODMART"),Map.entry("analysisDate",analysisDate.toString()),
            Map.entry("orders",DENSE_ORDERS),Map.entry("matrixRows",DENSE_ROWS),Map.entry("products",PRODUCT_COUNT),
            Map.entry("averageProductsPerOrder",(double)DENSE_ROWS/DENSE_ORDERS),Map.entry("csvBytes",csv.length),Map.entry("csvSha256",sha(csv)),
            Map.entry("snapshotId",snapshotId),Map.entry("matrixHash",matrixHash),Map.entry("snapshotStatus","READY"),
            Map.entry("conversionVersion",input.conversionVersion()),Map.entry("utilityScale",input.utilityScale()),
            Map.entry("roundingMode",input.roundingMode()),Map.entry("gcdDivisor",input.divisor()),
            Map.entry("maxTransactionUtility",maxTransactionUtility),Map.entry("totalUtilityAfterScale6Gcd",totalNormalizedUtility),
            Map.entry("int32Max",Integer.MAX_VALUE),Map.entry("int32Headroom",Integer.MAX_VALUE-totalNormalizedUtility),
            Map.entry("requestId",requestId),Map.entry("runId",runId),Map.entry("algorithm","EFIM"),
            Map.entry("minUtilValue","500"),Map.entry("efimRuntimeMillis",efimMillis),Map.entry("patternCount",patternCount),
            Map.entry("outputBytes",outputBytes),Map.entry("f06Status",candidate.path("evidence").path("status").asText()),
            Map.entry("f06SupportCount",candidate.path("evidence").path("supportCount").asInt()),
            Map.entry("runtimeLimits",Map.of("heapMiB",256,"timeoutMillis",60_000,"maxPatterns",2_000,"maxOutputBytes",4_194_304))
        );
        Files.writeString(directory.resolve("foodmart-dense-synthetic-e2e.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
    }

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

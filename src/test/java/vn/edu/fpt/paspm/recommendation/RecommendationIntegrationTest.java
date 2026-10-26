package vn.edu.fpt.paspm.recommendation;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f06;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true","paspm.jwt-secret=01234567890123456789012345678901","paspm.otp-secret=01234567890123456789012345678901","paspm.bootstrap-email=","paspm.bootstrap-password=","paspm.mining.spmf-jar=${paspm.test.spmf-jar:${user.dir}/../spmf_run/spmf.jar}","paspm.mining.java-executable=${paspm.test.spmf-java:${java.home}/bin/java}","paspm.avatar.directory=/tmp/paspm-test-avatars","spring.jackson.deserialization.fail-on-unknown-properties=true"})
@AutoConfigureMockMvc
class RecommendationIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    @Autowired UserRepository users; @Autowired AuthService auth;
    int store,other,snapshot; List<Integer> products; String merchant,admin,foreign,customer,system;
    LocalDate day=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    @BeforeEach void setup()throws Exception {
        jdbc.update("INSERT INTO store(name) VALUES (?)","F06 "+UUID.randomUUID());store=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        merchant=token("MERCHANDISER",store);admin=token("STORE_ADMIN",store);customer=token("END_CUSTOMER",null);system=token("SYSTEM_ADMIN",null);
        jdbc.update("INSERT INTO store(name) VALUES (?)","Foreign "+UUID.randomUUID());other=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);foreign=token("MERCHANDISER",other);
        products=new ArrayList<>();
        for(int i=0;i<3;i++) {
            jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,'Recommendation test','each',FALSE,10,0,'ACTIVE')",store,"R"+i);
            int product=jdbc.queryForObject("SELECT MAX(product_id) FROM product",Integer.class);products.add(product);
            jdbc.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,initial_quantity,remaining_quantity,status) VALUES (?,'available',CURRENT_TIMESTAMP,10,10,'ACTIVE')",product);
        }
        for(int[] ids:new int[][]{{0,1},{0,1},{0},{1},{1},{2}}) {
            String key=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO sales_transaction(store_id,source_system,source_order_id,occurred_at,status,f03_eligible,total_amount,content_sha256) VALUES (?,'F06_TEST',?,?,'COMPLETED',TRUE,20,?)",store,key,day.atStartOfDay(),"a".repeat(64));
            int tx=jdbc.queryForObject("SELECT transaction_id FROM sales_transaction WHERE store_id=? AND source_order_id=?",Integer.class,store,key);
            for(int i:ids)jdbc.update("INSERT INTO transaction_item(store_id,transaction_id,product_id,quantity,unit_price,unit_cost) VALUES (?,?,?,1,10,0)",store,tx,products.get(i));
        }
        snapshot=createSnapshot();
    }
    int createSnapshot()throws Exception {return body(postJson("/api/utility/snapshots",merchant,"{\"rangeStart\":\""+day+"\",\"rangeEnd\":\""+day+"\",\"asOfDate\":\""+day+"\"}").andExpect(status().isCreated())).path("snapshotId").asInt();}
    long[] mine(int snapshotId)throws Exception {
        String payload=json.writeValueAsString(Map.of("requestKey",UUID.randomUUID(),"snapshotId",snapshotId,"algorithms",List.of("HUI_MINER","EFIM"),"minUtilMode","ABSOLUTE","minUtilValue",1));
        long request=body(postJson("/api/mining/requests",merchant,payload).andExpect(status().isAccepted())).path("requestId").asLong();
        long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        JsonNode result;
        do {result=body(getJson("/api/mining/requests/"+request,merchant));if(!List.of("PENDING","RUNNING").contains(result.path("status").asText()))break;Thread.sleep(25);}while(System.nanoTime()<end);
        assertEquals("COMPLETED",result.path("status").asText());assertEquals("MATCH",result.path("comparisonStatus").asText());
        JsonNode runs=body(getJson("/api/mining/requests/"+request+"/runs",merchant));return new long[]{runs.get(0).path("runId").asLong(),runs.get(1).path("runId").asLong()};
    }
    ResultActions generate(long run,String who)throws Exception {return postJson("/api/recommendations/generate",who,json.writeValueAsString(Map.of("miningRunId",run,"types",List.of("BUNDLE","CROSS_SELL","FBT","BANNER"))));}
    List<Long> ids(JsonNode result) {List<Long> ids=new ArrayList<>();result.path("recommendationIds").forEach(n->ids.add(n.asLong()));return ids;}
    @Test void completedRealMiningGeneratesFourTypesAndDirectionalFrozenEvidence()throws Exception {
        long run=mine(snapshot)[0];JsonNode generated=body(generate(run,merchant).andExpect(status().isOk()));
        assertEquals(8,generated.path("createdCount").asInt());Set<String> types=new HashSet<>();
        for(long id:ids(generated)) {
            JsonNode candidate=body(getJson("/api/recommendations/"+id,admin).andExpect(status().isOk()));types.add(candidate.path("type").asText());
            assertEquals("PENDING_REVIEW",candidate.path("reviewStatus").asText());assertFalse(candidate.path("readyForConsumption").asBoolean());
            assertEquals("ELIGIBLE",candidate.path("currentEligibility").path("status").asText());assertFalse(candidate.path("explanation").isEmpty());
            assertEquals(snapshot,candidate.path("snapshotId").asInt());assertEquals(1,candidate.path("sources").size());
            if(candidate.path("type").asText().equals("CROSS_SELL")) {
                assertEquals(2,candidate.path("evidence").path("supportCount").asInt());assertEquals(6,candidate.path("evidence").path("totalTransactions").asInt());
                assertEquals(0,new java.math.BigDecimal("0.333333").compareTo(candidate.path("evidence").path("supportRatio").decimalValue()));
                String confidence=candidate.path("anchorProductId").asInt()==products.get(0)?"0.666667":"0.500000";
                assertEquals(0,new java.math.BigDecimal(confidence).compareTo(candidate.path("evidence").path("confidence").decimalValue()));
            }
        }
        assertEquals(Set.of("BUNDLE","CROSS_SELL","FBT","BANNER"),types);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM high_utility_pattern WHERE store_id=? AND support_count IS NOT NULL",Integer.class,store));
        assertEquals(30,jdbc.queryForObject("SELECT SUM(b.remaining_quantity) FROM inventory_batch b JOIN product p ON p.product_id=b.product_id WHERE p.store_id=?",Integer.class,store));
    }
    @Test void generationIsIdempotentAcrossEquivalentAlgorithmsButNotDifferentSnapshots()throws Exception {
        long[] runs=mine(snapshot);JsonNode first=body(generate(runs[0],merchant).andExpect(status().isOk()));
        assertEquals(0,body(generate(runs[0],merchant)).path("createdCount").asInt());
        JsonNode second=body(generate(runs[1],merchant));assertEquals(0,second.path("createdCount").asInt());assertEquals(8,second.path("reusedCount").asInt());
        assertEquals(new HashSet<>(ids(first)),new HashSet<>(ids(second)));
        assertEquals(2,body(getJson("/api/recommendations/"+ids(first).get(0),merchant)).path("sources").size());
        generate(mine(createSnapshot())[0],merchant).andExpect(status().isOk()).andExpect(jsonPath("$.createdCount").value(8));
        assertEquals(16,jdbc.queryForObject("SELECT COUNT(*) FROM recommendation WHERE store_id=?",Integer.class,store));
    }
    @Test void rolesStoreIsolationAndStrictRequestsAreEnforced()throws Exception {
        mvc.perform(get("/api/recommendations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/recommendations").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        for(String who:List.of(customer,system))getJson("/api/recommendations",who).andExpect(status().isForbidden());
        long run=mine(snapshot)[0];generate(run,admin).andExpect(status().isForbidden());generate(run,foreign).andExpect(status().isNotFound());
        long id=ids(body(generate(run,merchant))).get(0);
        getJson("/api/recommendations/"+id,foreign).andExpect(status().isNotFound());getJson("/api/recommendations",foreign).andExpect(jsonPath("$.totalElements").value(0));
        for(String action:List.of("approve","reject")) {
            postJson("/api/recommendations/"+id+"/"+action,admin,"{\"expectedVersion\":1,\"reason\":\"QA\"}").andExpect(status().isForbidden());
            postJson("/api/recommendations/"+id+"/"+action,foreign,"{\"expectedVersion\":1,\"reason\":\"QA\"}").andExpect(status().isNotFound());
        }
        postJson("/api/recommendations/generate",merchant,"{\"miningRunId\":"+run+",\"types\":[\"BUNDLE\"],\"storeId\":"+other+"}").andExpect(status().isBadRequest());
        postJson("/api/recommendations/generate",merchant,"{\"miningRunId\":1.5,\"types\":[\"BUNDLE\"]}").andExpect(status().isBadRequest());
        postJson("/api/recommendations/generate",merchant,"{\"miningRunId\":"+run+",\"types\":[\"BUNDLE\",\"BUNDLE\"]}").andExpect(status().isBadRequest());
        getJson("/api/recommendations?page=-1",merchant).andExpect(status().isBadRequest());
        getJson("/api/recommendations?pageSize=101",merchant).andExpect(status().isBadRequest());
    }
    @Test void reviewPreservesHistoryAndRejectsStaleInventoryAndResolvedTransitions()throws Exception {
        long run=mine(snapshot)[0];long id=ids(body(generate(run,merchant))).get(0);
        jdbc.update("UPDATE inventory_batch SET remaining_quantity=0 WHERE product_id IN (?,?)",products.get(0),products.get(1));
        getJson("/api/recommendations/"+id,merchant).andExpect(jsonPath("$.currentEligibility.status").value("INELIGIBLE")).andExpect(jsonPath("$.inventoryChanged").value(true));
        postJson("/api/recommendations/"+id+"/approve",merchant,"{\"expectedVersion\":1,\"reason\":\"Approve\"}").andExpect(status().isConflict());
        jdbc.update("UPDATE inventory_batch SET remaining_quantity=10 WHERE product_id IN (?,?)",products.get(0),products.get(1));
        postJson("/api/recommendations/"+id+"/approve",merchant,"{\"expectedVersion\":1,\"reason\":\"Checked inventory\"}").andExpect(status().isOk()).andExpect(jsonPath("$.reviewStatus").value("APPROVED")).andExpect(jsonPath("$.version").value(2));
        getJson("/api/recommendations/"+id,merchant).andExpect(jsonPath("$.readyForConsumption").value(true)).andExpect(jsonPath("$.reviewHistory[0].oldStatus").value("PENDING_REVIEW")).andExpect(jsonPath("$.reviewHistory[0].newStatus").value("APPROVED"));
        postJson("/api/recommendations/"+id+"/reject",merchant,"{\"expectedVersion\":2,\"reason\":\"Cannot reopen\"}").andExpect(status().isConflict());
        jdbc.update("UPDATE inventory_batch SET status='BLOCKED' WHERE product_id IN (?,?)",products.get(0),products.get(1));
        getJson("/api/recommendations/"+id,merchant).andExpect(jsonPath("$.reviewStatus").value("APPROVED")).andExpect(jsonPath("$.readyForConsumption").value(false));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_review_history WHERE recommendation_id=?",Integer.class,id));
    }
    @Test void configIsStoreAdminOwnedVersionedAndFrozenOnExistingCandidates()throws Exception {
        JsonNode defaults=body(getJson("/api/recommendations/config",admin).andExpect(status().isOk()));assertEquals(4,defaults.path("maxBundleSize").asInt());
        long run=mine(snapshot)[0];long id=ids(body(generate(run,merchant))).get(0);
        String changed="{\"expectedVersion\":1,\"maxBundleSize\":4,\"minSupportCount\":3,\"minSupportRatio\":0.05,\"minConfidence\":0.75,\"minimumSellableQuantity\":1}";
        mvc.perform(put("/api/recommendations/config").header("Authorization","Bearer "+merchant).contentType(MediaType.APPLICATION_JSON).content(changed)).andExpect(status().isForbidden());
        mvc.perform(put("/api/recommendations/config").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(changed)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(put("/api/recommendations/config").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(changed)).andExpect(status().isConflict());
        JsonNode regenerated=body(generate(run,merchant));assertEquals(5,regenerated.path("createdCount").asInt());assertFalse(regenerated.path("skipped").isEmpty());
        getJson("/api/recommendations/"+id,merchant).andExpect(jsonPath("$.policy.version").value(1)).andExpect(jsonPath("$.policy.minSupportCount").value(2));
        getJson("/api/recommendations/config",foreign).andExpect(jsonPath("$.version").value(1));
        for(String bad:List.of(changed.replace("\"expectedVersion\":1","\"expectedVersion\":2").replace("0.05","-0.1"),changed.replace("0.75","1.01"),changed.replace("\"maxBundleSize\":4","\"maxBundleSize\":1"),changed.replace("\"minimumSellableQuantity\":1","\"minimumSellableQuantity\":0")))
            mvc.perform(put("/api/recommendations/config").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isBadRequest());
    }
    @Test void nonCompletedEmptyAndMalformedSourceRunsNeverProduceUnsupportedCandidates()throws Exception {
        long run=mine(snapshot)[0];
        for(String state:List.of("PENDING","RUNNING","FAILED","TIMED_OUT")) {
            jdbc.update("UPDATE mining_run SET status=? WHERE run_id=?",state,run);generate(run,merchant).andExpect(status().isConflict());
        }
        jdbc.update("UPDATE mining_run SET status='COMPLETED' WHERE run_id=?",run);
        long request=jdbc.queryForObject("SELECT request_id FROM mining_run WHERE run_id=?",Long.class,run);
        jdbc.update("UPDATE mining_request SET input_snapshot_hash=? WHERE request_id=?","0".repeat(64),request);generate(run,merchant).andExpect(status().isConflict());
        generate(Long.MAX_VALUE,merchant).andExpect(status().isNotFound());
        long second=mine(snapshot)[0];jdbc.update("DELETE FROM pattern_item WHERE pattern_id IN (SELECT pattern_id FROM high_utility_pattern WHERE run_id=?)",second);
        generate(second,merchant).andExpect(status().isConflict());
        jdbc.update("DELETE FROM high_utility_pattern WHERE run_id=?",second);jdbc.update("UPDATE mining_run SET itemset_count=0 WHERE run_id=?",second);
        generate(second,merchant).andExpect(status().isOk()).andExpect(jsonPath("$.createdCount").value(0));
    }
    @Test void rankingIsDeterministicAndCrossSnapshotHistoryIsChronological()throws Exception {
        long run=mine(snapshot)[0];generate(run,merchant).andExpect(status().isOk());
        JsonNode first=body(getJson("/api/recommendations?miningRunId="+run,merchant));JsonNode second=body(getJson("/api/recommendations?miningRunId="+run,merchant));
        assertEquals(first.path("items").findValues("recommendationId"),second.path("items").findValues("recommendationId"));
        assertEquals(1,first.path("items").get(0).path("rank").asInt());assertEquals("RANKED_WITHIN_SOURCE_RUN",first.path("ordering").asText());
        getJson("/api/recommendations",merchant).andExpect(jsonPath("$.ordering").value("CREATED_DESC"));
        getJson("/api/recommendations?miningRunId="+run+"&type=CROSS_SELL&reviewStatus=PENDING_REVIEW&pageSize=1",merchant).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items.length()").value(1));
        getJson("/api/recommendations?miningRunId="+run,foreign).andExpect(status().isNotFound());
    }
    @Test void concurrentReviewHasSingleWinnerAndRejectedCannotBeApproved()throws Exception {
        long run=mine(snapshot)[0];long id=ids(body(generate(run,merchant))).get(0);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start=new java.util.concurrent.CountDownLatch(1);List<java.util.concurrent.Future<Integer>> futures=new ArrayList<>();
            for(int i=0;i<2;i++)futures.add(pool.submit(()->{start.await();return postJson("/api/recommendations/"+id+"/reject",merchant,"{\"expectedVersion\":1,\"reason\":\"No placement opportunity\"}").andReturn().getResponse().getStatus();}));
            start.countDown();List<Integer> statuses=new ArrayList<>();for(var f:futures)statuses.add(f.get(10,java.util.concurrent.TimeUnit.SECONDS));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
        } finally {pool.shutdownNow();}
        postJson("/api/recommendations/"+id+"/approve",merchant,"{\"expectedVersion\":2,\"reason\":\"Rejected cannot reopen\"}").andExpect(status().isConflict());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_review_history WHERE recommendation_id=?",Integer.class,id));
        generate(run,merchant).andExpect(status().isOk());getJson("/api/recommendations/"+id,merchant).andExpect(jsonPath("$.reviewStatus").value("REJECTED"));
    }
    @Test void concurrentGenerationRemainsIdempotentAndConfigHasSingleVersionWinner()throws Exception {
        long run=mine(snapshot)[0];var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var gate=new java.util.concurrent.CountDownLatch(1);List<java.util.concurrent.Future<JsonNode>> jobs=new ArrayList<>();
            for(int i=0;i<2;i++)jobs.add(pool.submit(()->{gate.await();return body(generate(run,merchant).andExpect(status().isOk()));}));
            gate.countDown();Set<Long> first=new HashSet<>(ids(jobs.get(0).get(15,java.util.concurrent.TimeUnit.SECONDS)));
            assertEquals(first,new HashSet<>(ids(jobs.get(1).get(15,java.util.concurrent.TimeUnit.SECONDS))));assertEquals(8,first.size());
            String update="{\"expectedVersion\":1,\"maxBundleSize\":4,\"minSupportCount\":2,\"minSupportRatio\":0.05,\"minConfidence\":0.5,\"minimumSellableQuantity\":1}";
            var configGate=new java.util.concurrent.CountDownLatch(1);List<java.util.concurrent.Future<Integer>> updates=new ArrayList<>();
            for(int i=0;i<2;i++)updates.add(pool.submit(()->{configGate.await();return mvc.perform(put("/api/recommendations/config").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(update)).andReturn().getResponse().getStatus();}));
            configGate.countDown();List<Integer> statuses=new ArrayList<>();for(var job:updates)statuses.add(job.get(10,java.util.concurrent.TimeUnit.SECONDS));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
        } finally {pool.shutdownNow();}
    }
    @Test void currentEligibilityExcludesInactiveExpiredBlockedAndMissingExpiry()throws Exception {
        long run=mine(snapshot)[0];long id=ids(body(generate(run,merchant))).get(0);int product=products.get(0);
        jdbc.update("UPDATE product SET status='INACTIVE' WHERE product_id=?",product);
        getJson("/api/recommendations/"+id,merchant).andExpect(jsonPath("$.currentEligibility.status").value("INELIGIBLE"));
        jdbc.update("UPDATE product SET status='ACTIVE',requires_expiry=TRUE,expiry_push_days=7 WHERE product_id=?",product);
        getJson("/api/recommendations/"+id,merchant).andExpect(jsonPath("$.currentEligibility.status").value("NEEDS_RECHECK"));
        postJson("/api/recommendations/"+id+"/approve",merchant,"{\"expectedVersion\":1,\"reason\":\"Missing metadata\"}").andExpect(status().isConflict());
        for(LocalDate expiry:List.of(day.minusDays(1),day)) {
            jdbc.update("UPDATE inventory_batch SET expiry_date=? WHERE product_id=?",expiry,product);
            getJson("/api/recommendations/"+id,merchant).andExpect(jsonPath("$.currentEligibility.status").value("INELIGIBLE"));
        }
        jdbc.update("UPDATE inventory_batch SET expiry_date=?,status='BLOCKED' WHERE product_id=?",day.plusDays(5),product);
        getJson("/api/recommendations/"+id,merchant).andExpect(jsonPath("$.currentEligibility.status").value("INELIGIBLE"));
        jdbc.update("UPDATE inventory_batch SET status='ACTIVE' WHERE product_id=?",product);
        getJson("/api/recommendations/"+id,merchant).andExpect(jsonPath("$.currentEligibility.status").value("ELIGIBLE")).andExpect(jsonPath("$.currentEligibility.products[0].expiryPriorityScore").value(1));
    }
    @Test void frozenEvidenceAndHistoricalUtilityStayUnchangedAfterLiveEdits()throws Exception {
        long run=mine(snapshot)[0];long request=jdbc.queryForObject("SELECT request_id FROM mining_run WHERE run_id=?",Long.class,run);
        String frozen=jdbc.queryForObject("SELECT frozen_input FROM mining_request WHERE request_id=?",String.class,request);
        String config=jdbc.queryForObject("SELECT config_snapshot FROM utility_snapshot WHERE snapshot_id=?",String.class,snapshot);
        String matrix=jdbc.queryForObject("SELECT matrix_hash FROM utility_snapshot WHERE snapshot_id=?",String.class,snapshot);
        var before=body(generate(run,merchant));long id=ids(before).get(0);JsonNode candidate=body(getJson("/api/recommendations/"+id,merchant));
        var immutable=new LinkedHashMap<String,String>();
        for(String table:List.of("utility_snapshot","utility_matrix_item","mining_request","mining_run","high_utility_pattern","pattern_item","sales_transaction","transaction_item")) {
            String query="SELECT * FROM "+table+(table.equals("utility_matrix_item")?
                " WHERE snapshot_id IN (SELECT snapshot_id FROM utility_snapshot WHERE store_id=?)":" WHERE store_id=?");
            immutable.put(query,json.writeValueAsString(jdbc.queryForList(query,store)));
        }
        String inventory=json.writeValueAsString(jdbc.queryForList("SELECT * FROM inventory_batch WHERE product_id IN (SELECT product_id FROM product WHERE store_id=?)",store));
        for(int product:products)
            mvc.perform(patch("/api/utility/products/"+product+"/scores").header("Authorization","Bearer "+merchant)
                .contentType(MediaType.APPLICATION_JSON).content("{\"stockPriorityScore\":0.2,\"seasonalPriorityScore\":0.1}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/utility/config").header("Authorization","Bearer "+merchant).contentType(MediaType.APPLICATION_JSON)
            .content("{\"enableStock\":true,\"enableExpiry\":false,\"enableSeason\":true,\"alpha\":0.7,\"beta\":0,\"gamma\":0.3,\"lambda\":0.5}"))
            .andExpect(status().isOk());
        getJson("/api/utility/products/"+products.get(0)+"/scores",merchant).andExpect(status().isOk()).andExpect(jsonPath("$.weightValue").value(1.085));
        for(var entry:immutable.entrySet())
            assertEquals(entry.getValue(),json.writeValueAsString(jdbc.queryForList(entry.getKey(),store)),entry.getKey());
        assertEquals(inventory,json.writeValueAsString(jdbc.queryForList("SELECT * FROM inventory_batch WHERE product_id IN (SELECT product_id FROM product WHERE store_id=?)",store)));
        jdbc.update("UPDATE transaction_item SET quantity=99,unit_price=99,unit_cost=1 WHERE store_id=?",store);
        jdbc.update("UPDATE product SET selling_price=999,cost_price=10,stock_priority_score=0.9,seasonal_priority_score=0.8,expiry_push_days=99 WHERE store_id=?",store);
        generate(run,merchant).andExpect(status().isOk()).andExpect(jsonPath("$.createdCount").value(0));
        JsonNode after=body(getJson("/api/recommendations/"+id,merchant));assertEquals(candidate.path("evidence"),after.path("evidence"));assertEquals(candidate.path("utility"),after.path("utility"));
        assertEquals(frozen,jdbc.queryForObject("SELECT frozen_input FROM mining_request WHERE request_id=?",String.class,request));
        assertEquals(config,jdbc.queryForObject("SELECT config_snapshot FROM utility_snapshot WHERE snapshot_id=?",String.class,snapshot));
        assertEquals(matrix,jdbc.queryForObject("SELECT matrix_hash FROM utility_snapshot WHERE snapshot_id=?",String.class,snapshot));
    }
    @Test void expiryPushWorksWithCompletedZeroPatternRun()throws Exception {
        long run=mine(snapshot)[0];jdbc.update("DELETE FROM pattern_item WHERE pattern_id IN (SELECT pattern_id FROM high_utility_pattern WHERE run_id=?)",run);
        jdbc.update("DELETE FROM high_utility_pattern WHERE run_id=?",run);jdbc.update("UPDATE mining_run SET itemset_count=0 WHERE run_id=?",run);
        generate(run,merchant).andExpect(status().isOk()).andExpect(jsonPath("$.createdCount").value(0));
        jdbc.update("UPDATE product SET requires_expiry=TRUE,expiry_push_days=7 WHERE product_id=?",products.get(0));
        jdbc.update("UPDATE inventory_batch SET expiry_date=? WHERE product_id=?",day.plusDays(5),products.get(0));
        getJson("/api/recommendations/expiry-push",merchant).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].pushRequired").value(true));
    }
    @Test void completedIndividualRunRemainsEligibleAcrossComparisonPolicies()throws Exception {
        long run=mine(snapshot)[0];long request=jdbc.queryForObject("SELECT request_id FROM mining_run WHERE run_id=?",Long.class,run);
        String frozen=jdbc.queryForObject("SELECT frozen_input FROM mining_request WHERE request_id=?",String.class,request);
        String snapshotHash=jdbc.queryForObject("SELECT matrix_hash FROM utility_snapshot WHERE snapshot_id=?",String.class,snapshot);
        for(String comparison:List.of("MATCH","NOT_REQUESTED","FAILED")) {
            jdbc.update("UPDATE mining_request SET comparison_status=?,status=? WHERE request_id=?",comparison,comparison.equals("FAILED")?"FAILED":"COMPLETED",request);
            generate(run,merchant).andExpect(status().isOk()).andExpect(jsonPath("$.recommendationIds.length()").value(8));
            assertEquals(frozen,jdbc.queryForObject("SELECT frozen_input FROM mining_request WHERE request_id=?",String.class,request));
            assertEquals(snapshotHash,jdbc.queryForObject("SELECT matrix_hash FROM utility_snapshot WHERE snapshot_id=?",String.class,snapshot));
        }
        assertEquals(8,jdbc.queryForObject("SELECT COUNT(*) FROM recommendation WHERE store_id=?",Integer.class,store));
    }
    @Test void inconsistentFrozenProductMappingIsRejectedWithoutPersistingCandidates()throws Exception {
        long run=mine(snapshot)[0];
        long pattern=jdbc.queryForObject("SELECT MIN(pattern_id) FROM high_utility_pattern WHERE run_id=?",Long.class,run);
        int product=jdbc.queryForObject("SELECT MIN(product_id) FROM pattern_item WHERE pattern_id=?",Integer.class,pattern);
        jdbc.update("UPDATE pattern_item SET algorithm_item_id=999 WHERE pattern_id=? AND product_id=?",pattern,product);
        generate(run,merchant).andExpect(status().isConflict());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM recommendation WHERE store_id=?",Integer.class,store));
    }
    @Test void exportsExecutedApiExamplesForHandoff()throws Exception {
        if(System.getProperty("paspm.f06.export-dir")==null)return;
        java.nio.file.Path dir=java.nio.file.Path.of(System.getProperty("paspm.f06.export-dir"));java.nio.file.Files.createDirectories(dir);
        long run=mine(snapshot)[0];String payload=json.writeValueAsString(Map.of("miningRunId",run,"types",List.of("BUNDLE","CROSS_SELL","FBT","BANNER")));
        JsonNode generated=body(postJson("/api/recommendations/generate",merchant,payload).andExpect(status().isOk()));
        java.nio.file.Files.writeString(dir.resolve("generate_request.json"),payload);java.nio.file.Files.writeString(dir.resolve("generate_response.json"),generated.toPrettyString());
        long id=ids(generated).get(0);JsonNode detail=body(getJson("/api/recommendations/"+id,merchant));java.nio.file.Files.writeString(dir.resolve("detail_response.json"),detail.toPrettyString());
        java.nio.file.Files.writeString(dir.resolve("config_response.json"),body(getJson("/api/recommendations/config",admin)).toPrettyString());
        java.nio.file.Files.writeString(dir.resolve("list_response.json"),body(getJson("/api/recommendations?miningRunId="+run,merchant)).toPrettyString());
        String review="{\"expectedVersion\":1,\"reason\":\"QA checked current inventory\"}";
        java.nio.file.Files.writeString(dir.resolve("approve_request.json"),review);
        java.nio.file.Files.writeString(dir.resolve("approve_response.json"),body(postJson("/api/recommendations/"+id+"/approve",merchant,review).andExpect(status().isOk())).toPrettyString());
        java.nio.file.Files.writeString(dir.resolve("conflict_response.json"),body(postJson("/api/recommendations/"+id+"/reject",merchant,review).andExpect(status().isConflict())).toPrettyString());
        jdbc.update("UPDATE product SET requires_expiry=TRUE,expiry_push_days=7 WHERE product_id=?",products.get(0));jdbc.update("UPDATE inventory_batch SET expiry_date=? WHERE product_id=?",day.plusDays(5),products.get(0));
        java.nio.file.Files.writeString(dir.resolve("expiry_push_response.json"),body(getJson("/api/recommendations/expiry-push",merchant).andExpect(status().isOk())).toPrettyString());
    }
    @Test void openApiExposesActualRecommendationOperations()throws Exception {
        JsonNode spec=body(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        for(String path:List.of("/api/recommendations","/api/recommendations/generate","/api/recommendations/{id}","/api/recommendations/{id}/approve","/api/recommendations/{id}/reject","/api/recommendations/config","/api/recommendations/expiry-push"))assertTrue(spec.path("paths").has(path),path);
        assertTrue(spec.path("components").path("schemas").path("RecView").path("properties").path("evidence").path("$ref").asText().endsWith("/RecTransactionEvidence"));
        assertTrue(spec.path("components").path("schemas").path("RecTransactionEvidence").path("properties").path("confidence").path("type").toString().contains("null"));
        assertTrue(spec.path("components").path("schemas").path("Evidence").path("properties").has("earliestEligibleExpiryDate"));
        for(String request:List.of("RecGenerateRequest","RecPolicyRequest","RecReviewRequest"))assertEquals(false,spec.path("components").path("schemas").path(request).path("additionalProperties").asBoolean(true),request);
        if(System.getProperty("paspm.f06.export-dir")!=null) {
            java.nio.file.Path dir=java.nio.file.Path.of(System.getProperty("paspm.f06.export-dir"));java.nio.file.Files.createDirectories(dir);java.nio.file.Files.writeString(dir.resolve("openapi.json"),spec.toPrettyString());
        }
    }
    String token(String role,Integer scope)throws Exception {var u=users.createPendingUser("F06 QA",UUID.randomUUID()+"@example.test",role,scope,null,null);jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",u.id());return auth.token(users.findById(u.id())).accessToken();}
    ResultActions postJson(String path,String who,String payload)throws Exception{return mvc.perform(post(path).header("Authorization","Bearer "+who).contentType(MediaType.APPLICATION_JSON).content(payload));}
    ResultActions getJson(String path,String who)throws Exception{return mvc.perform(get(path).header("Authorization","Bearer "+who));}
    JsonNode body(ResultActions action)throws Exception{return json.readTree(action.andReturn().getResponse().getContentAsString());}
}

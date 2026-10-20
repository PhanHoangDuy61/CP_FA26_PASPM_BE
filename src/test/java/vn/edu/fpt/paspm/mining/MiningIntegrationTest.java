package vn.edu.fpt.paspm.mining;

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
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:mining;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true","paspm.jwt-secret=01234567890123456789012345678901","paspm.otp-secret=01234567890123456789012345678901","paspm.bootstrap-email=","paspm.bootstrap-password=","paspm.mining.spmf-jar=${paspm.test.spmf-jar:${user.dir}/../spmf_run/spmf.jar}","paspm.mining.java-executable=${paspm.test.spmf-java:${java.home}/bin/java}","paspm.avatar.directory=/tmp/paspm-test-avatars"})
@AutoConfigureMockMvc
class MiningIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    @Autowired UserRepository users; @Autowired AuthService auth;
    int store,snapshot;List<Integer> products;String merchant,admin,foreign,customer,system;
    @BeforeEach void setup()throws Exception{
        jdbc.update("INSERT INTO store(name) VALUES (?)","Mining "+UUID.randomUUID());store=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        merchant=token("MERCHANDISER",store);admin=token("STORE_ADMIN",store);customer=token("END_CUSTOMER",null);system=token("SYSTEM_ADMIN",null);
        jdbc.update("INSERT INTO store(name) VALUES (?)","Other "+UUID.randomUUID());foreign=token("MERCHANDISER",jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class));
        products=new ArrayList<>();for(int i=1;i<=7;i++){
            jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,'Frozen','each',FALSE,100,0,'ACTIVE')",store,"M"+i);
            products.add(jdbc.queryForObject("SELECT MAX(product_id) FROM product",Integer.class));
        }
        String[] lines=SpmfMiningEngineTest.INPUT.split("\n");
        for(String line:lines){
            var parts=line.split(":");String key=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO sales_transaction(store_id,source_system,source_order_id,occurred_at,status,f03_eligible,total_amount,content_sha256) VALUES (?,'TEST',?,'2026-10-19 00:00:00','COMPLETED',TRUE,100,?)",store,key,"a".repeat(64));
            int tx=jdbc.queryForObject("SELECT transaction_id FROM sales_transaction WHERE store_id=? AND source_order_id=?",Integer.class,store,key);
            String[] ids=parts[0].split(" "),utils=parts[2].split(" ");
            for(int i=0;i<ids.length;i++)jdbc.update("INSERT INTO transaction_item(store_id,transaction_id,product_id,quantity,unit_price,unit_cost) VALUES (?,?,?,1,?,0)",store,tx,products.get(Integer.parseInt(ids[i])-1),Integer.parseInt(utils[i]));
        }
        var created=postJson("/api/utility/snapshots",merchant,"{\"rangeStart\":\"2026-10-19\",\"rangeEnd\":\"2026-10-19\",\"asOfDate\":\"2026-10-19\"}").andExpect(status().isCreated()).andReturn();
        snapshot=json.readTree(created.getResponse().getContentAsString()).path("snapshotId").asInt();
    }
    @Test void realSnapshotToBothAlgorithmsPersistsEightPatternsAndBenchmark()throws Exception{
        String key=UUID.randomUUID().toString(),payload=payload(key);
        var created=postJson("/api/mining/requests",merchant,payload).andExpect(status().isAccepted()).andReturn();
        var request=json.readTree(created.getResponse().getContentAsString());long id=request.path("requestId").asLong();
        postJson("/api/mining/requests",merchant,payload).andExpect(status().isAccepted()).andExpect(jsonPath("$.requestId").value(id));
        postJson("/api/mining/requests",merchant,payload.replace("\"minUtilValue\":30","\"minUtilValue\":31")).andExpect(status().isConflict());
        await(id);
        getJson("/api/mining/requests/"+id,merchant).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.comparisonStatus").value("MATCH"));
        var runs=json.readTree(getJson("/api/mining/requests/"+id+"/runs",admin).andReturn().getResponse().getContentAsString());
        assertEquals(2,runs.size());for(var run:runs){
            long runId=run.path("runId").asLong();assertEquals("COMPLETED",run.path("status").asText());assertEquals(8,run.path("patternCount").asInt());
            getJson("/api/mining/runs/"+runId+"/patterns",merchant).andExpect(jsonPath("$.totalElements").value(8)).andExpect(jsonPath("$.items[0].utility").value(40));
            getJson("/api/mining/runs/"+runId+"/benchmark",admin).andExpect(jsonPath("$.comparisonStatus").value("MATCH")).andExpect(jsonPath("$.run.memoryMiB").isNumber());
            getJson("/api/mining/runs/"+runId,foreign).andExpect(status().isNotFound());
            getJson("/api/mining/runs/"+runId+"/patterns",foreign).andExpect(status().isNotFound());
            getJson("/api/mining/runs/"+runId+"/benchmark",foreign).andExpect(status().isNotFound());
        }
        assertEquals(16,jdbc.queryForObject("SELECT COUNT(*) FROM high_utility_pattern p JOIN mining_run r ON r.run_id=p.run_id WHERE r.request_id=?",Integer.class,id));
        String before=request.path("inputHash").asText();
        jdbc.update("UPDATE product SET expiry_push_days=99,stock_priority_score=0.9,seasonal_priority_score=0.8,selling_price=999 WHERE store_id=?",store);
        var changedConfig=new vn.edu.fpt.paspm.utility.UtilityContracts.WeightConfig(true,false,true,new java.math.BigDecimal("0.7"),java.math.BigDecimal.ZERO,new java.math.BigDecimal("0.3"),new java.math.BigDecimal("0.5"),null,2,vn.edu.fpt.paspm.utility.ExpiryScoreCalculator.SCORING_VERSION);
        jdbc.update("UPDATE store SET utility_config=? WHERE store_id=?",json.writeValueAsString(changedConfig),store);
        postJson("/api/utility/preview",merchant,"{\"rangeStart\":\"2026-10-19\",\"rangeEnd\":\"2026-10-19\",\"asOfDate\":\"2026-10-19\"}").andExpect(status().isOk()).andExpect(jsonPath("$.totalWeightedUtility").value(137.76));
        var again=json.readTree(postJson("/api/mining/requests",merchant,payload(UUID.randomUUID().toString())).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        assertEquals(before,again.path("inputHash").asText());await(again.path("requestId").asLong());
        // Sanitized executed examples; no JWTs or credentials.
        if(System.getProperty("paspm.f05.export-dir")!=null){
            var dir=java.nio.file.Path.of(System.getProperty("paspm.f05.export-dir"));java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.writeString(dir.resolve("create_request.json"),payload);
            java.nio.file.Files.writeString(dir.resolve("request_response.json"),getJson("/api/mining/requests/"+id,merchant).andReturn().getResponse().getContentAsString());
            java.nio.file.Files.writeString(dir.resolve("runs_response.json"),runs.toPrettyString());
            long runId=runs.get(0).path("runId").asLong();
            for(String suffix:List.of("patterns","benchmark"))java.nio.file.Files.writeString(dir.resolve(suffix+"_response.json"),getJson("/api/mining/runs/"+runId+"/"+suffix,merchant).andReturn().getResponse().getContentAsString());
        }
    }
    @Test void enforcesRolesOwnershipPaginationAndStrictInput()throws Exception{
        mvc.perform(get("/api/mining/requests")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/mining/requests").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        for(String token:List.of(customer,system))getJson("/api/mining/requests",token).andExpect(status().isForbidden());
        postJson("/api/mining/requests",admin,payload(UUID.randomUUID().toString())).andExpect(status().isForbidden());
        postJson("/api/mining/requests",foreign,payload(UUID.randomUUID().toString())).andExpect(status().isNotFound());
        getJson("/api/mining/requests?page=-1",merchant).andExpect(status().isBadRequest());
        getJson("/api/mining/snapshots?pageSize=101",admin).andExpect(status().isBadRequest());
        getJson("/api/mining/snapshots",merchant).andExpect(jsonPath("$.totalElements").value(1));
        for(String changed:List.of(payload(UUID.randomUUID().toString()).replace("}",",\"storeId\":123}"),payload(UUID.randomUUID().toString()).replace("\"minUtilValue\":30","\"minUtilValue\":0"),payload(UUID.randomUUID().toString()).replace("\"snapshotId\":"+snapshot,"\"snapshotId\":1.5"),payload(UUID.randomUUID().toString()).replace("HUI_MINER","OTHER")))postJson("/api/mining/requests",merchant,changed).andExpect(status().isBadRequest());
        long id=json.readTree(postJson("/api/mining/requests",merchant,payload(UUID.randomUUID().toString())).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).path("requestId").asLong();
        getJson("/api/mining/requests/"+id,foreign).andExpect(status().isNotFound());
        getJson("/api/mining/requests/"+id+"/runs",foreign).andExpect(status().isNotFound());
        getJson("/api/mining/requests",foreign).andExpect(jsonPath("$.totalElements").value(0));await(id);
    }
    @Test void oldScoringMetadataCanBeMinedWithoutRecalculation()throws Exception{
        // H2 JSON is read using the project's established JSON decoder.
        var settings=new vn.edu.fpt.paspm.utility.UtilitySettingsRepository(jdbc,json);
        var frozen=settings.read(jdbc.queryForObject("SELECT config_snapshot FROM utility_snapshot WHERE snapshot_id=?",String.class,snapshot),vn.edu.fpt.paspm.utility.UtilityMatrixContracts.FrozenConfiguration.class);
        var old=frozen.config();var legacy=new vn.edu.fpt.paspm.utility.UtilityContracts.WeightConfig(old.enableStock(),old.enableExpiry(),old.enableSeason(),old.alpha(),old.beta(),old.gamma(),old.lambda(),30,1,"EXPIRY_LINEAR_V1");
        var legacyFrozen=new vn.edu.fpt.paspm.utility.UtilityMatrixContracts.FrozenConfiguration(legacy,frozen.lambdaPolicy(),frozen.effectiveCoefficients(),frozen.analysisDate(),frozen.source(),6,"HALF_UP","PRODUCT_ID_V1",null);
        jdbc.update("UPDATE utility_snapshot SET config_snapshot=? WHERE snapshot_id=?",json.writeValueAsString(legacyFrozen),snapshot);
        var result=json.readTree(postJson("/api/mining/requests",merchant,payload(UUID.randomUUID().toString())).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        assertEquals("EXPIRY_LINEAR_V1",result.path("snapshot").path("configuration").path("config").path("scoringVersion").asText());await(result.path("requestId").asLong());
    }
    @Test void openApiIncludesEveryMiningRoute()throws Exception{
        var spec=json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1,spec.path("components").path("schemas").path("CreateRequest").path("properties").path("algorithms").path("minItems").asInt());
        assertTrue(spec.path("components").path("schemas").path("PatternView").path("properties").path("supportCount").path("type").toString().contains("null"));
        for(String path:List.of("/api/mining/snapshots","/api/mining/requests","/api/mining/requests/{id}","/api/mining/requests/{id}/runs","/api/mining/runs/{id}","/api/mining/runs/{id}/patterns","/api/mining/runs/{id}/benchmark"))assertTrue(spec.path("paths").has(path),path);
    }
    @Autowired MiningRepository miningRepository;
    @Autowired vn.edu.fpt.paspm.utility.UtilitySnapshotRepository snapshotRepository;
    @Autowired MiningInputBuilder inputBuilder;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired MiningWorker worker;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean SpmfMiningEngine miningEngine;

    MiningRepository.Stored pending() {
        var frozen=snapshotRepository.find(store,snapshot);
        var input=inputBuilder.build(frozen,snapshotRepository.page(store,snapshot,0,20000));
        var request=new MiningContracts.CreateRequest(UUID.randomUUID(),snapshot,List.of(MiningEngine.Algorithm.HUI_MINER,MiningEngine.Algorithm.EFIM),MiningContracts.MinUtilMode.ABSOLUTE,new java.math.BigDecimal("30"));
        return new org.springframework.transaction.support.TransactionTemplate(transactions).execute(tx->miningRepository.create(store,frozen.createdBy(),request,"a".repeat(64),frozen,input,30));
    }
    @Test void boundedQueueRejectsExcessWorkDurably()throws Exception{
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(call->{entered.countDown();if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException();return new MiningEngine.Output(Map.of(),1,1,"controlled-test");}).when(miningEngine).execute(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyInt());
        var ids=new ArrayList<Long>();
        try{
            for(int i=0;i<10;i++){
                var result=json.readTree(postJson("/api/mining/requests",merchant,payload(UUID.randomUUID().toString())).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
                if(i==0)assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));
                if(i==9){assertEquals("FAILED",result.path("status").asText());for(var run:miningRepository.runs(store,result.path("requestId").asLong()))assertEquals("QUEUE_FULL",run.failureCode());}
                else ids.add(result.path("requestId").asLong());
            }
        } finally { release.countDown(); }
        for(long id:ids)await(id);
    }
    @Test void concurrentDuplicateSubmissionsCreateOnlyOneRequestAndOneRunPerAlgorithm()throws Exception{
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);String body=payload(UUID.randomUUID().toString());
        try{
            var a=pool.submit(()->postJson("/api/mining/requests",merchant,body).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
            var b=pool.submit(()->postJson("/api/mining/requests",merchant,body).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
            long first=json.readTree(a.get(10,java.util.concurrent.TimeUnit.SECONDS)).path("requestId").asLong();long second=json.readTree(b.get(10,java.util.concurrent.TimeUnit.SECONDS)).path("requestId").asLong();
            assertEquals(first,second);await(first);assertEquals(2,miningRepository.runs(store,first).size());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=?",Integer.class,store));
        } finally {pool.shutdownNow();}
    }
    @Test void historicalProvenanceUsesPersistedVersionsAndMeasurementMethods()throws Exception{
        var request=pending();long id=request.view().requestId();var run=miningRepository.runs(store,id).get(0);
        jdbc.update("UPDATE mining_run SET conversion_version='SAVED_CONVERSION',runtime_method='SAVED_RUNTIME',memory_method='SAVED_MEMORY' WHERE run_id=?",run.runId());
        var read=miningRepository.run(store,run.runId());assertEquals("SAVED_CONVERSION",read.conversionVersion());assertEquals("SAVED_RUNTIME",read.runtimeMethod());assertEquals("SAVED_MEMORY",read.memoryMethod());
        miningRepository.recoverInterrupted();
    }
    @Test void restartRecoveryAndCompareAndSetPreventDuplicateExecution()throws Exception{
        var request=pending();long id=request.view().requestId();
        assertTrue(miningRepository.claimRequest(store,id));assertFalse(miningRepository.claimRequest(store,id));
        var run=miningRepository.runs(store,id).get(0);
        assertTrue(miningRepository.claimRun(store,run.runId()));assertFalse(miningRepository.claimRun(store,run.runId()));
        miningRepository.recoverInterrupted();
        assertEquals("FAILED",miningRepository.find(store,id).view().status());
        for(var item:miningRepository.runs(store,id)){assertEquals("FAILED",item.status());assertEquals("INTERRUPTED",item.failureCode());}
        worker.execute(store,id); // terminal request is never re-executed
        assertEquals(0,miningRepository.patterns(store,run.runId(),0,20).totalElements());
    }
    @Test void workerReadsFrozenRequestCopyInsteadOfReloadingMutableSource()throws Exception{
        var request=pending();
        jdbc.update("UPDATE utility_matrix_item SET weighted_utility=weighted_utility+1 WHERE snapshot_id=?",snapshot);
        worker.execute(store,request.view().requestId());
        var completed=miningRepository.find(store,request.view().requestId()).view();
        assertEquals("COMPLETED",completed.status());assertEquals("MATCH",completed.comparisonStatus());assertEquals(96,completed.totalScaledUtility());
        for(var run:miningRepository.runs(store,completed.requestId()))assertEquals(8,run.patternCount());
    }
    @Test void partialPatternWriteRollsBackWithRunStillRunning()throws Exception{
        var request=pending();long id=request.view().requestId();miningRepository.claimRequest(store,id);
        var run=miningRepository.runs(store,id).get(0);miningRepository.claimRun(store,run.runId());
        var patterns=new LinkedHashMap<String,Integer>();patterns.put(products.get(0).toString(),30);patterns.put("2147483647",30);
        assertThrows(Exception.class,()->miningRepository.complete(store,run.runId(),request.input(),new MiningEngine.Output(patterns,10,10,"test")));
        assertEquals(0,miningRepository.patterns(store,run.runId(),0,20).totalElements());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pattern_item WHERE store_id=?",Integer.class,store));
        assertEquals("RUNNING",miningRepository.run(store,run.runId()).status());
        miningRepository.recoverInterrupted();
    }
    @Test void durableFailureAndTimeoutStatusesContainNoPartialPatterns()throws Exception{
        for(String code:List.of("SPMF_FAILED","TIMED_OUT")){
            org.mockito.Mockito.doThrow(new SpmfMiningEngine.ExecutionFailure(code,"Safe test failure")).when(miningEngine).execute(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyInt());
            long id=json.readTree(postJson("/api/mining/requests",merchant,payload(UUID.randomUUID().toString())).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).path("requestId").asLong();
            terminal(id);assertEquals("FAILED",miningRepository.find(store,id).view().status());
            for(var run:miningRepository.runs(store,id)){assertEquals(code.equals("TIMED_OUT")?"TIMED_OUT":"FAILED",run.status());assertEquals(code,run.failureCode());assertEquals(0,miningRepository.patterns(store,run.runId(),0,20).totalElements());}
        }
    }
    @Test void exactResultMismatchIsNotReportedAsSuccessfulBenchmark()throws Exception{
        org.mockito.Mockito.doReturn(new MiningEngine.Output(Map.of(),1,1,"same")).when(miningEngine).execute(org.mockito.ArgumentMatchers.eq(MiningEngine.Algorithm.HUI_MINER),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyInt());
        long id=json.readTree(postJson("/api/mining/requests",merchant,payload(UUID.randomUUID().toString())).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).path("requestId").asLong();
        terminal(id);assertEquals("FAILED",miningRepository.find(store,id).view().status());assertEquals("FAILED",miningRepository.find(store,id).view().comparisonStatus());
    }
    @Test void invalidSnapshotsFailBeforeDurableRequestIsCreated()throws Exception{
        for(String operation:List.of("UPDATE utility_snapshot SET status='FAILED' WHERE snapshot_id=?","UPDATE utility_snapshot SET total_weighted_utility=97 WHERE snapshot_id=?")){
            jdbc.update(operation,snapshot);postJson("/api/mining/requests",merchant,payload(UUID.randomUUID().toString())).andExpect(status().isBadRequest());
            jdbc.update("UPDATE utility_snapshot SET status='READY',total_weighted_utility=96 WHERE snapshot_id=?",snapshot);
        }
        postJson("/api/mining/requests",merchant,payload(UUID.randomUUID().toString()).replace("\"snapshotId\":"+snapshot,"\"snapshotId\":2147483647")).andExpect(status().isNotFound());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=?",Integer.class,store));
    }
    @Test void databasePreventsCrossStorePatternItems()throws Exception{
        var request=pending();long id=request.view().requestId();miningRepository.claimRequest(store,id);var run=miningRepository.runs(store,id).get(0);miningRepository.claimRun(store,run.runId());
        miningRepository.complete(store,run.runId(),request.input(),new MiningEngine.Output(Map.of(products.get(0).toString(),30),10,10,"test"));
        int foreignStore=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO pattern_item(pattern_id,store_id,product_id,algorithm_item_id) SELECT pattern_id,?, ?,2 FROM high_utility_pattern WHERE run_id=?",foreignStore,products.get(1),run.runId()));
        miningRepository.recoverInterrupted();
    }
    @Test void unavailableRuntimeRejectsNewWorkAndPreservesReadableHistory()throws Exception{
        var existing=pending();miningRepository.recoverInterrupted();
        org.mockito.Mockito.doThrow(new vn.edu.fpt.paspm.common.ApiException(503,"SPMF_JAVA_VERSION_UNSUPPORTED: Configure a Java 25 child"))
            .when(miningEngine).requireAvailable();
        postJson("/api/mining/requests",merchant,payload(UUID.randomUUID().toString())).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.startsWith("SPMF_JAVA_VERSION_UNSUPPORTED:")));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=?",Integer.class,store));
        getJson("/api/mining/requests/"+existing.view().requestId(),merchant).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
    }
    @Test void validEmptyResultIsCompletedWithZeroPatterns()throws Exception{
        var body=payload(UUID.randomUUID().toString()).replace("\"minUtilValue\":30","\"minUtilValue\":100");
        long id=json.readTree(postJson("/api/mining/requests",merchant,body).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).path("requestId").asLong();await(id);
        for(var run:miningRepository.runs(store,id)){assertEquals(0,run.patternCount());assertEquals("COMPLETED",run.status());}
    }
    void terminal(long id)throws Exception{long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);while(System.nanoTime()<end){if(!List.of("PENDING","RUNNING").contains(miningRepository.find(store,id).view().status()))return;Thread.sleep(20);}fail("Request never terminated");}
    void await(long id)throws Exception{long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);while(System.nanoTime()<end){String s=jdbc.queryForObject("SELECT status FROM mining_request WHERE request_id=?",String.class,id);if(!List.of("PENDING","RUNNING").contains(s)){assertEquals("COMPLETED",s);return;}Thread.sleep(20);}fail("Mining timed out waiting for request "+id);}
    String payload(String key){return "{\"requestKey\":\""+key+"\",\"snapshotId\":"+snapshot+",\"algorithms\":[\"HUI_MINER\",\"EFIM\"],\"minUtilMode\":\"ABSOLUTE\",\"minUtilValue\":30}";}
    String token(String role,Integer s){var u=users.createPendingUser("Mining QA",UUID.randomUUID()+"@example.test",role,s,null,null);jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",u.id());return auth.token(users.findById(u.id())).accessToken();}
    org.springframework.test.web.servlet.ResultActions postJson(String path,String token,String body)throws Exception{return mvc.perform(post(path).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body));}
    org.springframework.test.web.servlet.ResultActions getJson(String path,String token)throws Exception{return mvc.perform(get(path).header("Authorization","Bearer "+token));}
}

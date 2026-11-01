package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:mining;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true","paspm.jwt-secret=01234567890123456789012345678901","paspm.otp-secret=01234567890123456789012345678901","paspm.bootstrap-email=","paspm.bootstrap-password=","paspm.mining.system-store-read-enabled=true","paspm.mining.spmf-jar=${paspm.test.spmf-jar:${user.dir}/../spmf_run/spmf.jar}","paspm.mining.java-executable=${paspm.test.spmf-java:${java.home}/bin/java}","paspm.avatar.directory=/tmp/paspm-test-avatars"})
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
        configure(30);
    }
    @Autowired MiningScheduledSnapshot scheduledSnapshot;
    @Autowired MiningScheduledRequest scheduledRequest;
    @Autowired MiningOccurrenceDispatcher occurrenceDispatcher;
    @Autowired MiningOccurrenceLease occurrenceLeases;
    @Autowired MiningOccurrenceProcessor occurrenceProcessor;
    @Test void pollDiscoversPendingOccurrenceAndEventuallyCompletes() throws Exception {
        int actor=jdbc.queryForObject("SELECT MAX(u.user_id) FROM app_user u JOIN role r ON r.role_id=u.role_id WHERE r.code='SYSTEM_ADMIN'",Integer.class);
        long version=jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1",Long.class);
        jdbc.update("INSERT INTO mining_schedule(store_id,created_by,updated_by,frequency,day_of_week,local_time,time_zone,lookback_days,enabled) VALUES (?,?,?,'WEEKLY',1,'09:00','Asia/Ho_Chi_Minh',7,TRUE)",store,actor,actor);
        long schedule=jdbc.queryForObject("SELECT MAX(schedule_id) FROM mining_schedule",Long.class);
        jdbc.update("INSERT INTO mining_schedule_occurrence(schedule_id,store_id,scheduled_at,schedule_version,config_version,request_key,status,actor_id,range_start,range_end,analysis_date,time_zone,compare_algorithms) VALUES (?,?,CURRENT_TIMESTAMP,1,?,?,'PENDING',?,'2026-10-19','2026-10-19','2026-10-20','Asia/Ho_Chi_Minh',FALSE)",schedule,store,version,UUID.randomUUID().toString(),actor);
        long occurrence=jdbc.queryForObject("SELECT MAX(occurrence_id) FROM mining_schedule_occurrence",Long.class);
        occurrenceDispatcher.poll();
        Long request=jdbc.queryForObject("SELECT request_id FROM mining_schedule_occurrence WHERE occurrence_id=?",Long.class,occurrence);assertNotNull(request);
        await(request);occurrenceDispatcher.reconcile();
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM mining_schedule_occurrence WHERE occurrence_id=?",String.class,occurrence));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=?",Integer.class,store));
    }
    @Test void dispatcherCreatesFreshSnapshotAndReconcilesCompletedRequest() throws Exception {
        int actor=jdbc.queryForObject("SELECT MAX(u.user_id) FROM app_user u JOIN role r ON r.role_id=u.role_id WHERE r.code='SYSTEM_ADMIN'",Integer.class);
        long version=jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1",Long.class);
        jdbc.update("INSERT INTO mining_schedule(store_id,created_by,updated_by,frequency,day_of_week,local_time,time_zone,lookback_days,enabled) VALUES (?,?,?,'WEEKLY',1,'09:00','Asia/Ho_Chi_Minh',7,TRUE)",store,actor,actor);
        long schedule=jdbc.queryForObject("SELECT MAX(schedule_id) FROM mining_schedule",Long.class);
        jdbc.update("INSERT INTO mining_schedule_occurrence(schedule_id,store_id,scheduled_at,schedule_version,config_version,request_key,status,actor_id,range_start,range_end,analysis_date,time_zone,compare_algorithms) VALUES (?,?,CURRENT_TIMESTAMP,1,?,?,'PENDING',?,'2026-10-19','2026-10-19','2026-10-20','Asia/Ho_Chi_Minh',FALSE)",schedule,store,version,UUID.randomUUID().toString(),actor);
        long occurrence=jdbc.queryForObject("SELECT MAX(occurrence_id) FROM mining_schedule_occurrence",Long.class);
        var old=occurrenceLeases.claim(occurrence,"old-owner").orElseThrow();
        var past=java.sql.Timestamp.from(jdbc.queryForObject("SELECT CURRENT_TIMESTAMP",java.sql.Timestamp.class).toInstant().minusSeconds(1));
        jdbc.update("UPDATE mining_schedule_occurrence SET lease_until=? WHERE occurrence_id=?",past,occurrence);
        var current=occurrenceLeases.claim(occurrence,"new-owner").orElseThrow();
        assertThrows(IllegalStateException.class,()->occurrenceProcessor.process(old));
        assertNull(jdbc.queryForObject("SELECT snapshot_id FROM mining_schedule_occurrence WHERE occurrence_id=?",Integer.class,occurrence));
        configure(100);occurrenceProcessor.process(current);
        var row=jdbc.queryForMap("SELECT * FROM mining_schedule_occurrence WHERE occurrence_id=?",occurrence);
        int fresh=((Number)row.get("snapshot_id")).intValue();long request=((Number)row.get("request_id")).longValue();
        assertNotEquals(snapshot,fresh);assertEquals(version,jdbc.queryForObject("SELECT config_version FROM mining_request WHERE request_id=?",Long.class,request));
        assertEquals(30,miningRepository.find(store,request).view().minUtilSpmf());
        await(request);occurrenceDispatcher.reconcile();
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM mining_schedule_occurrence WHERE occurrence_id=?",String.class,occurrence));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=?",Integer.class,store));
    }
    @Test void occurrenceCreatesFreshSnapshotOnceWithNewTransactions() throws Exception {
        int actor=jdbc.queryForObject("SELECT MAX(u.user_id) FROM app_user u JOIN role r ON r.role_id=u.role_id WHERE r.code='SYSTEM_ADMIN'",Integer.class);
        long version=jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1",Long.class);
        jdbc.update("INSERT INTO mining_schedule(store_id,created_by,updated_by,frequency,day_of_week,local_time,time_zone,lookback_days,enabled) VALUES (?,?,?,'WEEKLY',1,'09:00','Asia/Ho_Chi_Minh',7,TRUE)",store,actor,actor);
        long schedule=jdbc.queryForObject("SELECT MAX(schedule_id) FROM mining_schedule",Long.class);
        jdbc.update("INSERT INTO mining_schedule_occurrence(schedule_id,store_id,scheduled_at,schedule_version,config_version,request_key,status,actor_id,range_start,range_end,analysis_date,time_zone,compare_algorithms) VALUES (?,?,CURRENT_TIMESTAMP,1,?,?,'PENDING',?,'2026-10-19','2026-10-19','2026-10-20','Asia/Ho_Chi_Minh',FALSE)",schedule,store,version,UUID.randomUUID().toString(),actor);
        long occurrence=jdbc.queryForObject("SELECT MAX(occurrence_id) FROM mining_schedule_occurrence",Long.class);
        String key=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO sales_transaction(store_id,source_system,source_order_id,occurred_at,status,f03_eligible,total_amount,content_sha256) VALUES (?,'TEST',?,'2026-10-19 00:00:00','COMPLETED',TRUE,10,?)",store,key,"c".repeat(64));
        int tx=jdbc.queryForObject("SELECT transaction_id FROM sales_transaction WHERE store_id=? AND source_order_id=?",Integer.class,store,key);
        jdbc.update("INSERT INTO transaction_item(store_id,transaction_id,product_id,quantity,unit_price,unit_cost) VALUES (?,?,?,1,10,0)",store,tx,products.get(0));
        long oldRows=snapshotRepository.countItems(store,snapshot);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        int fresh;
        try {
            var gate=new java.util.concurrent.CountDownLatch(1);
            var first=pool.submit(()->{gate.await();return scheduledSnapshot.prepare(occurrence);});
            var second=pool.submit(()->{gate.await();return scheduledSnapshot.prepare(occurrence);});
            gate.countDown(); fresh=first.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(fresh,second.get(10,java.util.concurrent.TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
        assertNotEquals(snapshot,fresh);
        assertEquals(oldRows+1,snapshotRepository.countItems(store,fresh));
        assertEquals(oldRows,snapshotRepository.countItems(store,snapshot));
        assertEquals(fresh,scheduledSnapshot.prepare(occurrence));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot WHERE snapshot_id=?",Integer.class,fresh));
        getJson("/api/system/mining/schedules/"+schedule+"/occurrences",system).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].snapshotId").value(fresh)).andExpect(jsonPath("$.items[0].configVersion").value(version));
        getJson("/api/system/mining/schedules/"+schedule+"/occurrences",merchant).andExpect(status().isForbidden());
        getJson("/api/system/mining/schedules/9999999/occurrences",system).andExpect(status().isNotFound());
        configure(100); // the occurrence must use the already pinned version, not the new head
        MiningContracts.RequestView job;
        var requestPool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var gate=new java.util.concurrent.CountDownLatch(1);
            var first=requestPool.submit(()->{gate.await();return scheduledRequest.submit(occurrence);});
            var second=requestPool.submit(()->{gate.await();return scheduledRequest.submit(occurrence);});
            gate.countDown();job=first.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(job.requestId(),second.get(10,java.util.concurrent.TimeUnit.SECONDS).requestId());
        } finally {requestPool.shutdownNow();}
        assertEquals(30,job.minUtilSpmf());
        assertEquals(version,jdbc.queryForObject("SELECT config_version FROM mining_request WHERE request_id=?",Long.class,job.requestId()));
        assertEquals(job.requestId(),scheduledRequest.submit(occurrence).requestId());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=?",Integer.class,store));
        await(job.requestId());
    }
    @Test void systemAdminSubmitsFrozenPolicyForAnExplicitStoreWithoutLegacyBypass() throws Exception {
        var config=json.readTree(getJson("/api/system/mining/config",system).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long version=config.path("version").asLong();
        String policy="{\"algorithm\":\"HUI_MINER\",\"minUtilMode\":\"ABSOLUTE\",\"minUtilValue\":30,\"expectedVersion\":"+version+"}";
        mvc.perform(put("/api/system/mining/config").header("Authorization","Bearer "+system).contentType(MediaType.APPLICATION_JSON).content(policy)).andExpect(status().isOk());
        postJson("/api/mining/requests",merchant,legacyPayload()).andExpect(status().isForbidden());
        String body="{\"storeId\":"+store+",\"snapshotId\":"+snapshot+",\"requestKey\":\""+UUID.randomUUID()+"\"}";
        postJson("/api/system/mining/requests",merchant,body).andExpect(status().isForbidden());
        var created=json.readTree(postJson("/api/system/mining/requests",system,body).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        assertEquals(store,created.path("storeId").asInt());
        assertEquals("HUI_MINER",created.path("algorithms").get(0).asText());
        assertEquals(version+1,jdbc.queryForObject("SELECT config_version FROM mining_request WHERE request_id=?",Long.class,created.path("requestId").asLong()));
        postJson("/api/system/mining/requests",system,body.replace("\"storeId\":"+store,"\"storeId\":99999999")).andExpect(status().isNotFound());
        getJson("/api/mining/requests/"+created.path("requestId").asLong(),foreign).andExpect(status().isNotFound());
        await(created.path("requestId").asLong());
        long runId=jdbc.queryForObject("SELECT MIN(run_id) FROM mining_run WHERE request_id=?",Long.class,created.path("requestId").asLong());
        getJson("/api/mining/runs/"+runId+"/benchmark",merchant).andExpect(status().isForbidden());
        getJson("/api/system/mining/stores/"+store+"/runs/"+runId+"/benchmark",system).andExpect(status().isOk());
        getJson("/api/system/mining/stores/999999/runs/"+runId+"/benchmark",system).andExpect(status().isNotFound());
    }
    @Test void realSnapshotToBothAlgorithmsPersistsEightPatternsAndBenchmark()throws Exception{
        String key=UUID.randomUUID().toString(),payload=payload(key);
        var created=postJson("/api/system/mining/requests",system,payload).andExpect(status().isAccepted()).andReturn();
        var request=json.readTree(created.getResponse().getContentAsString());long id=request.path("requestId").asLong();
        postJson("/api/system/mining/requests",system,payload).andExpect(status().isAccepted()).andExpect(jsonPath("$.requestId").value(id));
        postJson("/api/system/mining/requests",system,payload.replace("\"compareAlgorithms\":true","\"compareAlgorithms\":false")).andExpect(status().isConflict());
        await(id);
        getJson("/api/mining/requests/"+id,merchant).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.comparisonStatus").value("MATCH"));
        var runs=json.readTree(getJson("/api/mining/requests/"+id+"/runs",admin).andReturn().getResponse().getContentAsString());
        assertEquals(2,runs.size());for(var run:runs){
            long runId=run.path("runId").asLong();assertEquals("COMPLETED",run.path("status").asText());assertEquals(8,run.path("patternCount").asInt());
            getJson("/api/mining/runs/"+runId+"/patterns",merchant).andExpect(jsonPath("$.totalElements").value(8)).andExpect(jsonPath("$.items[0].utility").value(40));
            getJson("/api/system/mining/stores/"+store+"/runs/"+runId+"/benchmark",system).andExpect(jsonPath("$.comparisonStatus").value("MATCH")).andExpect(jsonPath("$.run.memoryMiB").isNumber());
            getJson("/api/mining/runs/"+runId,foreign).andExpect(status().isNotFound());
            getJson("/api/mining/runs/"+runId+"/patterns",foreign).andExpect(status().isNotFound());
            getJson("/api/system/mining/stores/999999/runs/"+runId+"/benchmark",system).andExpect(status().isNotFound());
        }
        assertEquals(16,jdbc.queryForObject("SELECT COUNT(*) FROM high_utility_pattern p JOIN mining_run r ON r.run_id=p.run_id WHERE r.request_id=?",Integer.class,id));
        String before=request.path("inputHash").asText();
        jdbc.update("UPDATE product SET expiry_push_days=99,stock_priority_score=0.9,seasonal_priority_score=0.8,selling_price=999 WHERE store_id=?",store);
        var changedConfig=new vn.edu.fpt.paspm.utility.UtilityContracts.WeightConfig(true,false,true,new java.math.BigDecimal("0.7"),java.math.BigDecimal.ZERO,new java.math.BigDecimal("0.3"),new java.math.BigDecimal("0.5"),null,2,vn.edu.fpt.paspm.utility.ExpiryScoreCalculator.SCORING_VERSION);
        jdbc.update("UPDATE store SET utility_config=? WHERE store_id=?",json.writeValueAsString(changedConfig),store);
        postJson("/api/utility/preview",merchant,"{\"rangeStart\":\"2026-10-19\",\"rangeEnd\":\"2026-10-19\",\"asOfDate\":\"2026-10-19\"}").andExpect(status().isOk()).andExpect(jsonPath("$.totalWeightedUtility").value(137.76));
        var again=json.readTree(postJson("/api/system/mining/requests",system,payload(UUID.randomUUID().toString())).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        assertEquals(before,again.path("inputHash").asText());await(again.path("requestId").asLong());
        // Sanitized executed examples; no JWTs or credentials.
        if(System.getProperty("paspm.f05.export-dir")!=null){
            var dir=java.nio.file.Path.of(System.getProperty("paspm.f05.export-dir"));java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.writeString(dir.resolve("create_request.json"),payload);
            java.nio.file.Files.writeString(dir.resolve("request_response.json"),getJson("/api/mining/requests/"+id,merchant).andReturn().getResponse().getContentAsString());
            java.nio.file.Files.writeString(dir.resolve("runs_response.json"),runs.toPrettyString());
            long runId=runs.get(0).path("runId").asLong();
            for(String suffix:List.of("patterns","benchmark"))java.nio.file.Files.writeString(dir.resolve(suffix+"_response.json"),getJson(suffix.equals("benchmark")?"/api/system/mining/stores/"+store+"/runs/"+runId+"/benchmark":"/api/mining/runs/"+runId+"/patterns",suffix.equals("benchmark")?system:merchant).andReturn().getResponse().getContentAsString());
        }
    }
    @Test void enforcesRolesOwnershipPaginationAndStrictInput()throws Exception{
        mvc.perform(get("/api/mining/requests")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/mining/requests").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        for(String token:List.of(customer,system))getJson("/api/mining/requests",token).andExpect(status().isForbidden());
        postJson("/api/mining/requests",admin,legacyPayload()).andExpect(status().isForbidden());
        postJson("/api/system/mining/requests",foreign,payload(UUID.randomUUID().toString())).andExpect(status().isForbidden());
        postJson("/api/mining/requests",merchant,legacyPayload()).andExpect(status().isForbidden());
        postJson("/api/system/mining/requests",admin,payload(UUID.randomUUID().toString())).andExpect(status().isForbidden());
        getJson("/api/mining/requests?page=-1",merchant).andExpect(status().isBadRequest());
        getJson("/api/mining/snapshots?pageSize=101",admin).andExpect(status().isBadRequest());
        getJson("/api/mining/snapshots",merchant).andExpect(jsonPath("$.totalElements").value(1));
        for(String changed:List.of(payload(UUID.randomUUID().toString()).replace("}",",\"algorithms\":[\"EFIM\"]}"),payload(UUID.randomUUID().toString()).replace("\"snapshotId\":"+snapshot,"\"snapshotId\":1.5")))postJson("/api/system/mining/requests",system,changed).andExpect(status().isBadRequest());
        long version=jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1",Long.class);
        mvc.perform(put("/api/system/mining/config").header("Authorization","Bearer "+system).contentType(MediaType.APPLICATION_JSON).content("{\"algorithm\":\"HUI_MINER\",\"minUtilMode\":\"ABSOLUTE\",\"minUtilValue\":0,\"expectedVersion\":"+version+"}")).andExpect(status().isBadRequest());
        long id=json.readTree(postJson("/api/system/mining/requests",system,payload(UUID.randomUUID().toString())).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).path("requestId").asLong();
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
        var result=json.readTree(postJson("/api/system/mining/requests",system,payload(UUID.randomUUID().toString())).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        assertEquals("EXPIRY_LINEAR_V1",result.path("snapshot").path("configuration").path("config").path("scoringVersion").asText());await(result.path("requestId").asLong());
    }
    @Test void systemAdminReadsExplicitStoreSnapshotsRequestsRunsAndPatternsWithoutCrossStoreLeak()throws Exception{
        int other=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);assertNotEquals(store,other);
        String base="/api/system/mining/stores/"+store;
        mvc.perform(get(base+"/snapshots")).andExpect(status().isUnauthorized());
        for(String token:List.of(merchant,admin,foreign,customer))for(String path:List.of("/snapshots","/requests","/requests/1","/requests/1/runs","/runs/1/patterns"))getJson(base+path,token).andExpect(status().isForbidden());
        var created=json.readTree(postJson("/api/system/mining/requests",system,payload(UUID.randomUUID().toString())).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        long id=created.path("requestId").asLong();await(id);
        var snapshots=json.readTree(getJson(base+"/snapshots?page=0&pageSize=20",system).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(StreamSupport.stream(snapshots.path("items").spliterator(),false).anyMatch(s->s.path("snapshotId").asInt()==snapshot&&s.path("storeId").asInt()==store&&"READY".equals(s.path("status").asText())));
        assertEquals(0,json.readTree(getJson("/api/system/mining/stores/"+other+"/snapshots",system).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("totalElements").asLong());
        var requests=json.readTree(getJson(base+"/requests",system).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(StreamSupport.stream(requests.path("items").spliterator(),false).allMatch(r->r.path("storeId").asInt()==store));
        assertTrue(StreamSupport.stream(requests.path("items").spliterator(),false).anyMatch(r->r.path("requestId").asLong()==id));
        assertEquals(0,json.readTree(getJson("/api/system/mining/stores/"+other+"/requests",system).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("totalElements").asLong());
        getJson(base+"/requests/"+id,system).andExpect(status().isOk()).andExpect(jsonPath("$.requestId").value(id)).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.storeId").value(store));
        var runs=json.readTree(getJson(base+"/requests/"+id+"/runs",system).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(2,runs.size());for(var run:runs){assertEquals(id,run.path("requestId").asLong());assertEquals(store,run.path("storeId").asInt());}
        long runId=runs.get(0).path("runId").asLong();
        var adminPatterns=getJson(base+"/runs/"+runId+"/patterns?page=0&pageSize=3",system).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var tree=json.readTree(adminPatterns);assertEquals(3,tree.path("items").size());assertEquals(3,tree.path("pageSize").asInt());assertTrue(tree.path("totalElements").asLong()>=3);
        assertEquals(adminPatterns,getJson("/api/mining/runs/"+runId+"/patterns?page=0&pageSize=3",merchant).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),"administrative and Store-scoped reads share one representation");
        String wrong="/api/system/mining/stores/"+other;
        getJson(wrong+"/requests/"+id,system).andExpect(status().isNotFound());
        getJson(wrong+"/requests/"+id+"/runs",system).andExpect(status().isNotFound());
        getJson(wrong+"/runs/"+runId+"/patterns",system).andExpect(status().isNotFound());
        getJson(wrong+"/runs/"+runId+"/benchmark",system).andExpect(status().isNotFound());
        for(String path:List.of("/snapshots","/requests","/requests/"+id,"/requests/"+id+"/runs","/runs/"+runId+"/patterns"))getJson("/api/system/mining/stores/99999999"+path,system).andExpect(status().isNotFound());
        getJson(base+"/requests/99999999",system).andExpect(status().isNotFound());
        getJson(base+"/runs/99999999/patterns",system).andExpect(status().isNotFound());
        for(String query:List.of("?page=-1","?pageSize=0","?pageSize=101"))for(String path:List.of("/snapshots","/requests","/runs/"+runId+"/patterns"))getJson(base+path+query,system).andExpect(status().isBadRequest());
        getJson(base+"/requests/abc",system).andExpect(status().isBadRequest());
        getJson("/api/system/mining/stores/0/requests",system).andExpect(status().isBadRequest());
        // The two legacy routes and Store-scoped denial of System Admin stay closed.
        postJson("/api/mining/requests",system,legacyPayload()).andExpect(status().isForbidden());
        getJson("/api/mining/runs/"+runId+"/benchmark",system).andExpect(status().isForbidden());
        getJson("/api/mining/requests/"+id,system).andExpect(status().isForbidden());
        getJson("/api/mining/requests/"+id,foreign).andExpect(status().isNotFound());
    }
    @Test void openApiIncludesEveryMiningRoute()throws Exception{
        var spec=json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1,spec.path("components").path("schemas").path("CreateRequest").path("properties").path("algorithms").path("minItems").asInt());
        assertTrue(spec.path("components").path("schemas").path("PatternView").path("properties").path("supportCount").path("type").toString().contains("null"));
        for(String path:List.of("/api/mining/snapshots","/api/mining/requests","/api/mining/requests/{id}","/api/mining/requests/{id}/runs","/api/mining/runs/{id}","/api/mining/runs/{id}/patterns","/api/mining/runs/{id}/benchmark"))assertTrue(spec.path("paths").has(path),path);
        for(String path:List.of("/api/system/mining/stores/{storeId}/snapshots","/api/system/mining/stores/{storeId}/requests","/api/system/mining/stores/{storeId}/requests/{requestId}","/api/system/mining/stores/{storeId}/requests/{requestId}/runs","/api/system/mining/stores/{storeId}/runs/{runId}/patterns","/api/system/mining/stores/{storeId}/runs/{runId}/benchmark"))assertTrue(spec.path("paths").has(path),path);
        assertTrue(spec.path("paths").path("/api/mining/requests").path("post").path("deprecated").asBoolean(),"forbidden POST must be documented as deprecated");
        assertTrue(spec.path("paths").path("/api/mining/runs/{id}/benchmark").path("get").path("deprecated").asBoolean(),"forbidden benchmark must be documented as deprecated");
        assertFalse(spec.path("paths").path("/api/mining/requests").path("get").path("deprecated").asBoolean(),"active list route must stay active");
        assertFalse(spec.path("paths").path("/api/system/mining/requests").path("post").path("deprecated").asBoolean(),"System Admin submission is the active route");
    }
    @Autowired MiningRepository miningRepository;
    @Autowired vn.edu.fpt.paspm.utility.UtilitySnapshotRepository snapshotRepository;
    @Autowired MiningInputBuilder inputBuilder;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired MiningWorker worker;
    @Autowired MiningDispatchLease dispatchLease;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean SpmfMiningEngine miningEngine;

    @Test void startupDoesNotInterruptRequestOwnedByAnotherInstance() {
        var request=pending(); long id=request.view().requestId();
        var future=java.sql.Timestamp.from(jdbc.queryForObject("SELECT CURRENT_TIMESTAMP",java.sql.Timestamp.class).toInstant().plusSeconds(120));
        jdbc.update("UPDATE mining_request SET status='RUNNING',lease_owner='other-instance',lease_fence=1,lease_until=? WHERE request_id=?",future,id);
        worker.recover();
        assertEquals("RUNNING",miningRepository.find(store,id).view().status());
        assertTrue(miningRepository.runs(store,id).stream().allMatch(r->r.status().equals("PENDING")));
    }

    @Test void committedRequestIsDiscoveredWithoutEnqueue() throws Exception {
        org.mockito.Mockito.doReturn(new MiningEngine.Output(Map.of(),1,1,"durable-test"))
            .when(miningEngine).execute(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyInt());
        var request=pending();
        await(request.view().requestId());
        assertTrue(miningRepository.runs(store,request.view().requestId()).stream().allMatch(r->r.status().equals("COMPLETED")));
    }

    @Test void reclaimedRequestRejectsStaleRunAndPatternWrites() {
        var request=pending();long id=request.view().requestId();
        var a=dispatchLease.claim(store,id,"test-owner-a",60).orElseThrow();
        var run=miningRepository.runs(store,id).get(0);
        assertTrue(miningRepository.claimRun(a,run.runId()));
        var past=java.sql.Timestamp.from(jdbc.queryForObject("SELECT CURRENT_TIMESTAMP",java.sql.Timestamp.class).toInstant().minusSeconds(1));
        jdbc.update("UPDATE mining_request SET lease_until=? WHERE request_id=?",past,id);
        var b=dispatchLease.claim(store,id,"test-owner-b",60).orElseThrow();
        var staleComplete=assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class,()->miningRepository.complete(a,run.runId(),request.input(),new MiningEngine.Output(Map.of(products.get(0).toString(),30),1,1,"stale")));
        assertInstanceOf(IllegalStateException.class,staleComplete.getCause());
        var staleFailure=assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class,()->miningRepository.failRun(a,run.runId(),"FAILED","stale"));
        assertInstanceOf(IllegalStateException.class,staleFailure.getCause());
        assertThrows(IllegalStateException.class,()->dispatchLease.terminal(a,"FAILED","BLOCKED"));
        assertEquals("RUNNING",miningRepository.run(store,run.runId()).status());
        assertEquals(0,miningRepository.patterns(store,run.runId(),0,20).totalElements());
        miningRepository.resetAbandonedRuns(b);
        assertEquals("PENDING",miningRepository.run(store,run.runId()).status());
    }

    MiningRepository.Stored pending() {
        var frozen=snapshotRepository.find(store,snapshot);
        var input=inputBuilder.build(frozen,snapshotRepository.page(store,snapshot,0,20000));
        var request=new MiningContracts.CreateRequest(UUID.randomUUID(),snapshot,List.of(MiningEngine.Algorithm.HUI_MINER,MiningEngine.Algorithm.EFIM),MiningContracts.MinUtilMode.ABSOLUTE,new java.math.BigDecimal("30"));
        return new org.springframework.transaction.support.TransactionTemplate(transactions).execute(tx->miningRepository.create(store,frozen.createdBy(),request,"a".repeat(64),frozen,input,30));
    }
    @Test void boundedQueueLeavesExcessWorkForDurableRetry()throws Exception{
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(call->{entered.countDown();if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException();return new MiningEngine.Output(Map.of(),1,1,"controlled-test");}).when(miningEngine).execute(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyInt());
        var ids=new ArrayList<Long>();
        try{
            for(int i=0;i<10;i++){
                var result=json.readTree(postJson("/api/system/mining/requests",system,payload(UUID.randomUUID().toString())).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
                if(i==0)assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));
                if(i==9)assertEquals("PENDING",result.path("status").asText());
                ids.add(result.path("requestId").asLong());
            }
        } finally { release.countDown(); }
        for(long id:ids)await(id);
    }
    @Test void concurrentDuplicateSubmissionsCreateOnlyOneRequestAndOneRunPerAlgorithm()throws Exception{
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);String body=payload(UUID.randomUUID().toString());
        try{
            var a=pool.submit(()->postJson("/api/system/mining/requests",system,body).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
            var b=pool.submit(()->postJson("/api/system/mining/requests",system,body).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
            long first=json.readTree(a.get(10,java.util.concurrent.TimeUnit.SECONDS)).path("requestId").asLong();long second=json.readTree(b.get(10,java.util.concurrent.TimeUnit.SECONDS)).path("requestId").asLong();
            assertEquals(first,second);await(first);assertEquals(2,miningRepository.runs(store,first).size());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=?",Integer.class,store));
        } finally {pool.shutdownNow();}
    }
    @Test void historicalProvenanceUsesPersistedVersionsAndMeasurementMethods()throws Exception{
        var request=pending();long id=request.view().requestId();var run=miningRepository.runs(store,id).get(0);
        jdbc.update("UPDATE mining_run SET conversion_version='SAVED_CONVERSION',runtime_method='SAVED_RUNTIME',memory_method='SAVED_MEMORY' WHERE run_id=?",run.runId());
        var read=miningRepository.run(store,run.runId());assertEquals("SAVED_CONVERSION",read.conversionVersion());assertEquals("SAVED_RUNTIME",read.runtimeMethod());assertEquals("SAVED_MEMORY",read.memoryMethod());
    }
    @Test void legacyUnownedRunningRequestIsNotAssumedAbandoned()throws Exception{
        var request=pending();long id=request.view().requestId();
        jdbc.update("UPDATE mining_request SET status='RUNNING' WHERE request_id=?",id);
        var run=miningRepository.runs(store,id).get(0);
        jdbc.update("UPDATE mining_run SET status='RUNNING' WHERE run_id=?",run.runId());
        worker.recover();
        worker.execute(store,id);
        assertEquals("RUNNING",miningRepository.find(store,id).view().status());
        assertEquals("RUNNING",miningRepository.run(store,run.runId()).status());
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
        var request=pending();long id=request.view().requestId();var claim=dispatchLease.claim(store,id,"rollback-test",60).orElseThrow();
        var run=miningRepository.runs(store,id).get(0);miningRepository.claimRun(claim,run.runId());
        var patterns=new LinkedHashMap<String,Integer>();patterns.put(products.get(0).toString(),30);patterns.put("2147483647",30);
        assertThrows(Exception.class,()->miningRepository.complete(claim,run.runId(),request.input(),new MiningEngine.Output(patterns,10,10,"test")));
        assertEquals(0,miningRepository.patterns(store,run.runId(),0,20).totalElements());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pattern_item WHERE store_id=?",Integer.class,store));
        assertEquals("RUNNING",miningRepository.run(store,run.runId()).status());
    }
    @Test void durableFailureAndTimeoutStatusesContainNoPartialPatterns()throws Exception{
        for(String code:List.of("SPMF_FAILED","TIMED_OUT")){
            org.mockito.Mockito.doThrow(new SpmfMiningEngine.ExecutionFailure(code,"Safe test failure")).when(miningEngine).execute(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyInt());
            long id=json.readTree(postJson("/api/system/mining/requests",system,payload(UUID.randomUUID().toString())).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).path("requestId").asLong();
            terminal(id);assertEquals("FAILED",miningRepository.find(store,id).view().status());
            for(var run:miningRepository.runs(store,id)){assertEquals(code.equals("TIMED_OUT")?"TIMED_OUT":"FAILED",run.status());assertEquals(code,run.failureCode());assertEquals(0,miningRepository.patterns(store,run.runId(),0,20).totalElements());}
        }
    }
    @Test void exactResultMismatchIsNotReportedAsSuccessfulBenchmark()throws Exception{
        org.mockito.Mockito.doReturn(new MiningEngine.Output(Map.of(),1,1,"same")).when(miningEngine).execute(org.mockito.ArgumentMatchers.eq(MiningEngine.Algorithm.HUI_MINER),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyInt());
        long id=json.readTree(postJson("/api/system/mining/requests",system,payload(UUID.randomUUID().toString())).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).path("requestId").asLong();
        terminal(id);assertEquals("FAILED",miningRepository.find(store,id).view().status());assertEquals("FAILED",miningRepository.find(store,id).view().comparisonStatus());
    }
    @Test void invalidSnapshotsFailBeforeDurableRequestIsCreated()throws Exception{
        for(String operation:List.of("UPDATE utility_snapshot SET status='FAILED' WHERE snapshot_id=?","UPDATE utility_snapshot SET total_weighted_utility=97 WHERE snapshot_id=?")){
            jdbc.update(operation,snapshot);postJson("/api/system/mining/requests",system,payload(UUID.randomUUID().toString())).andExpect(status().isBadRequest());
            jdbc.update("UPDATE utility_snapshot SET status='READY',total_weighted_utility=96 WHERE snapshot_id=?",snapshot);
        }
        postJson("/api/system/mining/requests",system,payload(UUID.randomUUID().toString()).replace("\"snapshotId\":"+snapshot,"\"snapshotId\":2147483647")).andExpect(status().isNotFound());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=?",Integer.class,store));
    }
    @Test void databasePreventsCrossStorePatternItems()throws Exception{
        var request=pending();long id=request.view().requestId();var claim=dispatchLease.claim(store,id,"isolation-test",60).orElseThrow();var run=miningRepository.runs(store,id).get(0);miningRepository.claimRun(claim,run.runId());
        miningRepository.complete(claim,run.runId(),request.input(),new MiningEngine.Output(Map.of(products.get(0).toString(),30),10,10,"test"));
        int foreignStore=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO pattern_item(pattern_id,store_id,product_id,algorithm_item_id) SELECT pattern_id,?, ?,2 FROM high_utility_pattern WHERE run_id=?",foreignStore,products.get(1),run.runId()));
    }
    @Test void unavailableRuntimeRejectsNewWorkAndPreservesReadableHistory()throws Exception{
        var existing=pending();jdbc.update("UPDATE mining_request SET status='FAILED' WHERE request_id=?",existing.view().requestId());
        org.mockito.Mockito.doThrow(new vn.edu.fpt.paspm.common.ApiException(503,"SPMF_JAVA_VERSION_UNSUPPORTED: Configure a Java 25 child"))
            .when(miningEngine).requireAvailable();
        postJson("/api/system/mining/requests",system,payload(UUID.randomUUID().toString())).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.startsWith("SPMF_JAVA_VERSION_UNSUPPORTED:")));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=?",Integer.class,store));
        getJson("/api/mining/requests/"+existing.view().requestId(),merchant).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
    }
    @Test void validEmptyResultIsCompletedWithZeroPatterns()throws Exception{
        configure(100);
        var body=payload(UUID.randomUUID().toString());
        long id=json.readTree(postJson("/api/system/mining/requests",system,body).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).path("requestId").asLong();await(id);
        for(var run:miningRepository.runs(store,id)){assertEquals(0,run.patternCount());assertEquals("COMPLETED",run.status());}
    }
    void terminal(long id)throws Exception{long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);while(System.nanoTime()<end){if(!List.of("PENDING","RUNNING").contains(miningRepository.find(store,id).view().status()))return;Thread.sleep(20);}fail("Request never terminated");}
    void await(long id)throws Exception{long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);while(System.nanoTime()<end){String s=jdbc.queryForObject("SELECT status FROM mining_request WHERE request_id=?",String.class,id);if(!List.of("PENDING","RUNNING").contains(s)){assertEquals("COMPLETED",s);return;}Thread.sleep(20);}fail("Mining timed out waiting for request "+id);}
    String legacyPayload() { return "{\"requestKey\":\""+UUID.randomUUID()+"\",\"snapshotId\":"+snapshot+",\"algorithms\":[\"HUI_MINER\"],\"minUtilMode\":\"ABSOLUTE\",\"minUtilValue\":30}"; }
    void configure(int value) throws Exception {
        long version=jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1",Long.class);
        String body="{\"algorithm\":\"HUI_MINER\",\"minUtilMode\":\"ABSOLUTE\",\"minUtilValue\":"+value+",\"expectedVersion\":"+version+"}";
        mvc.perform(put("/api/system/mining/config").header("Authorization","Bearer "+system).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }
    String payload(String key){return "{\"requestKey\":\""+key+"\",\"storeId\":"+store+",\"snapshotId\":"+snapshot+",\"compareAlgorithms\":true}";}
    String token(String role,Integer s){var u=users.createPendingUser("Mining QA",UUID.randomUUID()+"@example.test",role,s,null,null);jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",u.id());return auth.token(users.findById(u.id())).accessToken();}
    org.springframework.test.web.servlet.ResultActions postJson(String path,String token,String body)throws Exception{return mvc.perform(post(path).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body));}
    org.springframework.test.web.servlet.ResultActions getJson(String path,String token)throws Exception{return mvc.perform(get(path).header("Authorization","Bearer "+token));}
}

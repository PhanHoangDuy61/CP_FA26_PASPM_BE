package vn.edu.fpt.paspm.recommendation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vn.edu.fpt.paspm.recommendation.RecommendationContracts.*;

import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.mining.*;
import vn.edu.fpt.paspm.user.UserRepository;
import vn.edu.fpt.paspm.utility.UtilityMatrixContracts.SnapshotView;

/** Safety fixtures deliberately stub only frozen-reader/rule output; persistence, HTTP security and transactions are real. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f06hardening;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true","paspm.jwt-secret=test-only-jwt-key-with-at-least-thirty-two-bytes","paspm.otp-secret=test-only-otp-key-with-at-least-thirty-two-bytes","paspm.avatar.directory=/tmp/paspm-test-avatars","paspm.bootstrap-email=","paspm.bootstrap-password="})
@AutoConfigureMockMvc
@Import(RecommendationHardeningIntegrationTest.CounterConfiguration.class)
class RecommendationHardeningIntegrationTest {
    @TestConfiguration static class CounterConfiguration { @Bean static RecommendationSqlCounter statementCounter(){return new RecommendationSqlCounter();} }
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    @Autowired UserRepository users; @Autowired AuthService auth; @Autowired RecommendationSqlCounter counter;
    @MockitoBean FrozenEvidenceReader reader; @MockitoBean RecommendationRules rules;
    @MockitoSpyBean RecommendationRepository repository;
    int store,other,actor,product,snapshot; long request,run,pattern; String merchant,admin,foreign,customer,system;
    RecPolicy policy; RecEligibility eligible; FrozenEvidenceReader.Source source;

    @BeforeEach void setup() throws Exception {
        store=store();other=store(); merchant=token("MERCHANDISER",store);admin=token("STORE_ADMIN",store);
        foreign=token("MERCHANDISER",other);customer=token("END_CUSTOMER",null);system=token("SYSTEM_ADMIN",null);
        actor=jdbc.queryForObject("SELECT MIN(user_id) FROM app_user WHERE store_id=? AND role_id=(SELECT role_id FROM role WHERE code='MERCHANDISER')",Integer.class,store);
        jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,'hardening','Hardening','each',FALSE,10,0,'ACTIVE')",store);
        product=jdbc.queryForObject("SELECT product_id FROM product WHERE store_id=?",Integer.class,store);
        jdbc.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,initial_quantity,remaining_quantity,status) VALUES (?,'stock',CURRENT_TIMESTAMP,10,10,'ACTIVE')",product);
        jdbc.update("INSERT INTO utility_snapshot(store_id,created_by,range_start,range_end,as_of_date,config_snapshot,source_hash,matrix_hash,total_weighted_utility,rounding_scale,status) VALUES (?,?,CURRENT_DATE,CURRENT_DATE,CURRENT_DATE,'{}',?,?,1,6,'READY')",store,actor,"a".repeat(64),"b".repeat(64));
        snapshot=jdbc.queryForObject("SELECT snapshot_id FROM utility_snapshot WHERE store_id=?",Integer.class,store);
        jdbc.update("INSERT INTO mining_request(store_id,requested_by,utility_snapshot_id,request_key,payload_hash,algorithms,min_util_mode,min_util_value,min_util_spmf,input_snapshot_hash,frozen_input,utility_config_snapshot,status,comparison_status) VALUES (?,?,?, ?,?,'HUI_MINER','ABSOLUTE',1,1,?,'{}','{}','COMPLETED','NOT_REQUESTED')",store,actor,snapshot,UUID.randomUUID().toString(),"c".repeat(64),"d".repeat(64));
        request=jdbc.queryForObject("SELECT request_id FROM mining_request WHERE store_id=?",Long.class,store);
        jdbc.update("INSERT INTO mining_run(store_id,request_id,algorithm,library_version,conversion_version,memory_method,runtime_method,status,itemset_count) VALUES (?,?,'HUI_MINER','SPMF_2.67a',?,'synthetic bounded safety fixture','test fixture','COMPLETED',1)",store,request,MiningInputBuilder.VERSION);
        run=jdbc.queryForObject("SELECT run_id FROM mining_run WHERE store_id=?",Long.class,store);
        jdbc.update("INSERT INTO high_utility_pattern(store_id,run_id,canonical_key,canonical_hash,utility,utility_spmf,pattern_size) VALUES (?,?,?, ?,1,1,1)",store,run,Integer.toString(product),MiningInputBuilder.sha(Integer.toString(product)));
        pattern=jdbc.queryForObject("SELECT pattern_id FROM high_utility_pattern WHERE store_id=?",Long.class,store);
        jdbc.update("INSERT INTO pattern_item(pattern_id,store_id,product_id,algorithm_item_id) VALUES (?,?,?,1)",pattern,store,product);
        policy=new RecPolicy(1,4,2,new BigDecimal("0.05"),new BigDecimal("0.5"),1,RecommendationPolicyService.RULE_VERSION);
        eligible=new RecEligibility(EligibilityStatus.ELIGIBLE,List.of(),Instant.EPOCH,LocalDate.now(),"Asia/Ho_Chi_Minh",List.of(),"e".repeat(64));
        var runView=new MiningContracts.RunView(run,request,store,snapshot,MiningEngine.Algorithm.HUI_MINER,1,"SPMF_2.67a","COMPLETED",1,"d".repeat(64),MiningInputBuilder.VERSION,null,null,null,null,"synthetic","synthetic",null,1,null,null);
        var snapshotView=new SnapshotView(snapshot,store,actor,LocalDate.now(),LocalDate.now(),LocalDate.now(),null,"a".repeat(64),"b".repeat(64),BigDecimal.ONE,6,"READY",Instant.EPOCH,List.of());
        source=new FrozenEvidenceReader.Source(runView,snapshotView,"ABSOLUTE",BigDecimal.ONE,BigDecimal.ONE,"d".repeat(64),List.of(new FrozenEvidenceReader.SourcePattern(pattern,List.of(product),BigDecimal.ONE,1,1)),new TransactionEvidence(List.of(Set.of(product))));
        when(reader.read(store,run)).thenReturn(source);
        when(reader.read(other,run)).thenThrow(new ApiException(404,"Mining run not found"));
    }

    @ParameterizedTest @ValueSource(ints={25,100})
    void listBatchesItemsAndAuditRelationsInsteadOfNPlusOne(int count) throws Exception {
        List<Long> ids=seed(count,RecType.BANNER);
        long reviewed=ids.get(0);
        postJson("/api/recommendations/"+reviewed+"/reject",merchant,"{\"expectedVersion\":1,\"reason\":\"Measured history preservation\"}").andExpect(status().isOk());
        List<Long> elapsed=new ArrayList<>();Map<String,Integer> measured=Map.of();JsonNode page=null;
        for(int attempt=0;attempt<3;attempt++) {
            counter.start();long start=System.nanoTime();
            try { page=body(getJson("/api/recommendations?pageSize="+count,merchant).andExpect(status().isOk())); }
            finally {elapsed.add(System.nanoTime()-start); measured=counter.stop();}
        }
        assertEquals(count,page.path("items").size());assertEquals(ids.get(count-1).longValue(),page.path("items").get(0).path("recommendationId").asLong());
        JsonNode last=page.path("items").get(count-1);assertEquals(product,last.path("productIds").get(0).asInt());
        assertEquals(1,last.path("sources").size());assertEquals(1,last.path("reviewHistory").size());assertEquals("REJECTED",last.path("reviewStatus").asText());
        assertEquals(0,body(getJson("/api/recommendations?pageSize=100",foreign)).path("totalElements").asInt());
        elapsed.sort(Long::compare);int relationQueries=measured.entrySet().stream().filter(e->e.getKey().startsWith("recommendation")).mapToInt(Map.Entry::getValue).sum();
        System.out.println("F06_PERF "+json.writeValueAsString(Map.of("database",databaseName(),"candidates",count,"relationQueries",relationQueries,"allSelects",measured.values().stream().mapToInt(Integer::intValue).sum(),"medianMillis",elapsed.get(1)/1_000_000.0,"queryCounts",measured)));
        assertTrue(relationQueries<=5,"Expected <=5 relation SELECTs including count, got "+relationQueries+" "+measured);
    }

    @ParameterizedTest @ValueSource(ints={9999,10000})
    void generationAcceptsCountsBelowAndExactlyAtSafetyLimit(int count) throws Exception {
        stubCandidates(count);
        var historyBefore=frozen();
        JsonNode result=body(generate().andExpect(status().isOk()));
        assertEquals(count,result.path("createdCount").asInt());assertEquals(count,result.path("recommendationIds").size());assertEquals(count,recommendations());
        assertEquals(count,jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_source WHERE store_id=?",Integer.class,store));
        getJson("/api/recommendations?miningRunId="+run+"&pageSize=1",merchant).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(count)).andExpect(jsonPath("$.items[0].rank").value(1));
        assertEquals(historyBefore,frozen());
    }

    @Test void boundedRelationChunksPreserveSourceHistoryAndStoreIsolation() throws Exception {
        List<Long> ids=seed(501,RecType.BANNER);
        // The SELECT is descending: these are positions501/500/499 around the500-ID chunk split.
        var expectedProducts=new HashMap<Long,Integer>();var expectedRuns=new HashMap<Long,Long>();
        for(int i=0;i<3;i++) {
            long id=ids.get(i);String sku="boundary-"+i;
            jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,'Chunk boundary','each',FALSE,10,0,'ACTIVE')",store,sku);
            int selected=jdbc.queryForObject("SELECT product_id FROM product WHERE store_id=? AND sku=?",Integer.class,store,sku);
            jdbc.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,initial_quantity,remaining_quantity,status) VALUES (?,'stock',CURRENT_TIMESTAMP,10,10,'ACTIVE')",selected);
            jdbc.update("UPDATE recommendation_item SET product_id=? WHERE store_id=? AND recommendation_id=?",selected,store,id);
            jdbc.update("UPDATE recommendation SET canonical_key=? WHERE store_id=? AND recommendation_id=?",Integer.toString(selected),store,id);
            jdbc.update("INSERT INTO mining_run(store_id,request_id,algorithm,attempt_no,library_version,conversion_version,memory_method,runtime_method,status,itemset_count) SELECT store_id,request_id,algorithm,?,library_version,conversion_version,memory_method,runtime_method,status,itemset_count FROM mining_run WHERE run_id=?",i+2,run);
            long selectedRun=jdbc.queryForObject("SELECT run_id FROM mining_run WHERE request_id=? AND attempt_no=?",Long.class,request,i+2);
            jdbc.update("INSERT INTO high_utility_pattern(store_id,run_id,canonical_key,canonical_hash,utility,utility_spmf,pattern_size) VALUES (?,?,?,?,1,1,1)",store,selectedRun,Integer.toString(selected),MiningInputBuilder.sha(Integer.toString(selected)));
            long selectedPattern=jdbc.queryForObject("SELECT pattern_id FROM high_utility_pattern WHERE run_id=?",Long.class,selectedRun);
            jdbc.update("INSERT INTO pattern_item(pattern_id,store_id,product_id,algorithm_item_id) VALUES (?,?,?,1)",selectedPattern,store,selected);
            jdbc.update("DELETE FROM recommendation_source WHERE store_id=? AND recommendation_id=?",store,id);
            repository.source(store,id,new RecSource(selectedRun,selectedPattern,request,snapshot,"HUI_MINER","SPMF_2.67a",MiningInputBuilder.VERSION,"d".repeat(64),"a".repeat(64),"b".repeat(64),"ABSOLUTE",BigDecimal.ONE,1,BigDecimal.ONE,BigDecimal.ONE,LocalDate.now()));
            postJson("/api/recommendations/"+id+"/reject",merchant,json.writeValueAsString(Map.of("expectedVersion",1,"reason","boundary-"+(501-i)))).andExpect(status().isOk());
            expectedProducts.put(id,selected);expectedRuns.put(id,selectedRun);
        }
        counter.start();Map<String,Integer> measured;
        try {var records=repository.list(store,null,null,null,null,501,0);var related=repository.relations(store,records.stream().map(RecommendationRepository.Stored::id).toList());
            assertEquals(501,records.size());
            for(var record:records)if(expectedProducts.containsKey(record.id())) {
                assertEquals(List.of(expectedProducts.get(record.id())),record.products());
                assertEquals(expectedRuns.get(record.id()),related.sources().get(record.id()).get(0).runId());
                assertEquals("boundary-"+(501-ids.indexOf(record.id())),related.history().get(record.id()).get(0).reason());
            }}
        finally {measured=counter.stop();}
        assertEquals(2,measured.get("recommendation_item"));assertEquals(2,measured.get("recommendation_source"));assertEquals(2,measured.get("recommendation_review_history"));
        var foreignRelations=repository.relations(other,ids);assertTrue(foreignRelations.sources().isEmpty());assertTrue(foreignRelations.history().isEmpty());
        assertThrows(ApiException.class,()->repository.find(other,ids.get(0),false));
        getJson("/api/recommendations/"+ids.get(0),merchant).andExpect(status().isOk()).andExpect(jsonPath("$.sources.length()").value(1));
    }

    @Test void generationReadsActorOnceForAtomicBatch() throws Exception {
        stubCandidates(25);counter.start();long start=System.nanoTime();Map<String,Integer> measured;
        try {generate().andExpect(status().isOk()).andExpect(jsonPath("$.createdCount").value(25));}
        finally {measured=counter.stop();}
        System.out.println("F06_GENERATION_PERF "+json.writeValueAsString(Map.of("database",databaseName(),"candidates",25,"millis",(System.nanoTime()-start)/1_000_000.0,"queryCounts",measured)));
        assertTrue(measured.getOrDefault("app_user",0)<=4,"Actor identity must not be loaded once per created candidate: "+measured);
    }

    @Test void generationAboveSafetyLimitIs409AndRollsBackAllNewRowsPreservingExisting() throws Exception {
        long existing=seed(1,RecType.BANNER).get(0);var before=frozen();var recommendationBefore=json.writeValueAsString(jdbc.queryForList("SELECT * FROM recommendation WHERE store_id=?",store));
        stubCandidates(10001);generate().andExpect(status().isConflict()).andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("10000")));
        assertEquals(1,recommendations());assertEquals(recommendationBefore,json.writeValueAsString(jdbc.queryForList("SELECT * FROM recommendation WHERE store_id=?",store)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_source WHERE store_id=?",Integer.class,store));
        assertEquals(before,frozen());assertNotNull(repository.find(store,existing,false));
    }

    @Test void injectedSourcePersistenceFailureRollsBackGenerationAndSourceRows() throws Exception {
        long existing=seed(1,RecType.BANNER).get(0);var before=frozen();stubCandidates(3);
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation->{invocation.callRealMethod();if(calls.incrementAndGet()==2)throw new DataIntegrityViolationException("Injected test-only source write failure");return null;}).when(repository).source(eq(store),anyLong(),any());
        generate().andExpect(status().isConflict());
        assertEquals(1,recommendations());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_source WHERE store_id=?",Integer.class,store));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_review_history WHERE store_id=?",Integer.class,store));
        assertNotNull(repository.find(store,existing,false));assertEquals(before,frozen());
    }

    @Test void rankedScopeAboveLimitIs409WhileFilteredScopeAndPagesRemainStable() throws Exception {
        seed(10001,RecType.BANNER);
        getJson("/api/recommendations?miningRunId="+run,merchant).andExpect(status().isConflict());
        getJson("/api/recommendations?miningRunId="+run+"&type=BUNDLE",merchant).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        getJson("/api/recommendations?miningRunId="+run+"&reviewStatus=APPROVED",merchant).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        getJson("/api/recommendations?miningRunId="+run,foreign).andExpect(status().isNotFound());
        JsonNode first=body(getJson("/api/recommendations?pageSize=100",merchant));JsonNode second=body(getJson("/api/recommendations?pageSize=100&page=1",merchant));
        assertEquals(10001,first.path("totalElements").asInt());assertTrue(first.path("items").get(99).path("recommendationId").asLong()>second.path("items").get(0).path("recommendationId").asLong());
        assertEquals(first.path("items").findValues("recommendationId"),body(getJson("/api/recommendations?pageSize=100",merchant)).path("items").findValues("recommendationId"));
    }

    @Test void reviewValidationAndCrossStoreModificationsNeverWriteHistory() throws Exception {
        long id=seed(1,RecType.BANNER).get(0);var before=frozen();
        for(String reason:List.of("null","\"\"","\"   \"","\""+"x".repeat(1001)+"\""))
            postJson("/api/recommendations/"+id+"/approve",merchant,"{\"expectedVersion\":1,\"reason\":"+reason+"}").andExpect(status().isBadRequest());
        postJson("/api/recommendations/"+id+"/reject",merchant,"{\"expectedVersion\":1}").andExpect(status().isBadRequest());
        for(String version:List.of("null","0","-1","1.5","\"1\""))
            postJson("/api/recommendations/"+id+"/reject",merchant,"{\"expectedVersion\":"+version+",\"reason\":\"Boundary\"}").andExpect(status().isBadRequest());
        for(String action:List.of("approve","reject")) {
            postJson("/api/recommendations/"+id+"/"+action,foreign,"{\"expectedVersion\":1,\"reason\":\"Foreign\"}").andExpect(status().isNotFound());
            postJson("/api/recommendations/"+id+"/"+action,admin,"{\"expectedVersion\":1,\"reason\":\"Admin\"}").andExpect(status().isForbidden());
        }
        postJson("/api/recommendations/"+id+"/approve",merchant,"{\"expectedVersion\":2,\"reason\":\"Stale\"}").andExpect(status().isConflict());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_review_history WHERE store_id=?",Integer.class,store));assertEquals(before,frozen());
        assertEquals(1,repository.find(store,id,false).version());
    }

    @Test void reviewFailureAfterCompareAndSetRollsBackStateAndHistory() throws Exception {
        long id=seed(1,RecType.BANNER).get(0);
        doAnswer(invocation->{invocation.callRealMethod();throw new DataIntegrityViolationException("Injected test-only failure after review history insert");}).when(repository).review(any(),anyInt(),any(),any(),any());
        postJson("/api/recommendations/"+id+"/reject",merchant,"{\"expectedVersion\":1,\"reason\":\"Atomicity\"}").andExpect(status().isConflict());
        assertEquals(ReviewStatus.PENDING_REVIEW,repository.find(store,id,false).status());assertEquals(1,repository.find(store,id,false).version());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_review_history WHERE store_id=?",Integer.class,store));
    }

    @Test void generationAuthenticationAuthorizationAndForgedStoreAreRejected() throws Exception {
        mvc.perform(post("/api/recommendations/generate").contentType(MediaType.APPLICATION_JSON).content(payload())).andExpect(status().isUnauthorized());
        for(String who:List.of("invalid",admin,customer,system))
            postJson("/api/recommendations/generate",who,payload()).andExpect(status().is(who.equals("invalid")?401:403));
        postJson("/api/recommendations/generate",foreign,payload()).andExpect(status().isNotFound());
        postJson("/api/recommendations/generate",merchant,payload().replace("}",",\"storeId\":"+other+"}")).andExpect(status().isBadRequest());
        assertEquals(0,recommendations());
    }

    @Test void concurrentApprovalAndRejectionHaveOneWinnerAndOneImmutableHistoryRow() throws Exception {
        long id=seed(1,RecType.BANNER).get(0);var before=frozen();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var gate=new java.util.concurrent.CountDownLatch(1);List<java.util.concurrent.Future<Integer>> jobs=new ArrayList<>();
            for(String action:List.of("approve","reject"))jobs.add(pool.submit(()->{gate.await();return postJson("/api/recommendations/"+id+"/"+action,merchant,"{\"expectedVersion\":1,\"reason\":\"Concurrent decision\"}").andReturn().getResponse().getStatus();}));
            gate.countDown();List<Integer> statuses=new ArrayList<>();for(var job:jobs)statuses.add(job.get(15,java.util.concurrent.TimeUnit.SECONDS));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
        } finally {pool.shutdownNow();}
        assertEquals(2,repository.find(store,id,false).version());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_review_history WHERE store_id=?",Integer.class,store));
        assertEquals(before,frozen());
    }

    @Test void maximumReviewReasonAndVersionStateBoundaryRemainConsistent() throws Exception {
        long id=seed(1,RecType.BANNER).get(0);
        postJson("/api/recommendations/"+id+"/approve",merchant,"{\"expectedVersion\":"+Long.MAX_VALUE+",\"reason\":\"No silent version coercion\"}").andExpect(status().isConflict());
        postJson("/api/recommendations/"+id+"/reject",merchant,json.writeValueAsString(Map.of("expectedVersion",1,"reason","x".repeat(1000)))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        for(String action:List.of("approve","reject"))postJson("/api/recommendations/"+id+"/"+action,merchant,"{\"expectedVersion\":2,\"reason\":\"No reopening\"}").andExpect(status().isConflict());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_review_history WHERE store_id=?",Integer.class,store));
        assertEquals(1000,repository.find(store,id,false).reason().length());
    }

    String databaseName() throws Exception {try(var connection=jdbc.getDataSource().getConnection()){return connection.getMetaData().getDatabaseProductName();}}
    void stubCandidates(int count) {
        List<RecommendationRules.Candidate> candidates=new ArrayList<>();for(int i=0;i<count;i++)candidates.add(candidate(i+100));
        when(rules.apply(eq(source),any(),anyList(),any())).thenReturn(new RecommendationRules.Result(List.copyOf(candidates),List.of()));
    }
    RecommendationRules.Candidate candidate(int utility){return new RecommendationRules.Candidate(RecType.BANNER,List.of(product),null,BigDecimal.valueOf(utility),new TransactionEvidence(List.of(Set.of(product))).measure(List.of(product),null),List.of("Bounded synthetic safety fixture"));}
    List<Long> seed(int count,RecType type) {
        List<Long> ids=new ArrayList<>();for(int i=0;i<count;i++) {
            var candidate=new RecommendationRules.Candidate(type,List.of(product),null,BigDecimal.ONE,new TransactionEvidence(List.of(Set.of(product))).measure(List.of(product),null),List.of("Bounded test fixture"));
            long id=repository.create(store,actor,snapshot,MiningInputBuilder.sha("seed-"+i),Integer.toString(product),candidate,policy,eligible);
            repository.source(store,id,recSource());ids.add(id);
        }return ids;
    }
    RecSource recSource(){return new RecSource(run,pattern,request,snapshot,"HUI_MINER","SPMF_2.67a",MiningInputBuilder.VERSION,"d".repeat(64),"a".repeat(64),"b".repeat(64),"ABSOLUTE",BigDecimal.ONE,1,BigDecimal.ONE,BigDecimal.ONE,LocalDate.now());}
    int recommendations(){return jdbc.queryForObject("SELECT COUNT(*) FROM recommendation WHERE store_id=?",Integer.class,store);}
    String frozen() throws Exception {var tables=new LinkedHashMap<String,Object>();for(String table:List.of("utility_snapshot","mining_request","mining_run","high_utility_pattern","pattern_item","inventory_batch","sales_transaction","transaction_item"))tables.put(table,jdbc.queryForList("SELECT * FROM "+table));return json.writeValueAsString(tables);}
    int store(){jdbc.update("INSERT INTO store(name) VALUES (?)","Hardening "+UUID.randomUUID());return jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);}
    String token(String role,Integer storeId){var user=users.createPendingUser("Hardening",UUID.randomUUID()+"@example.test",role,storeId,"unused-test-hash",null);jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",user.id());return auth.token(users.findById(user.id())).accessToken();}
    String payload(){return "{\"miningRunId\":"+run+",\"types\":[\"BANNER\"]}";}
    ResultActions generate() throws Exception {return postJson("/api/recommendations/generate",merchant,payload());}
    ResultActions postJson(String path,String who,String payload) throws Exception{return mvc.perform(post(path).header("Authorization","Bearer "+who).contentType(MediaType.APPLICATION_JSON).content(payload));}
    ResultActions getJson(String path,String who) throws Exception{return mvc.perform(get(path).header("Authorization","Bearer "+who));}
    JsonNode body(ResultActions action) throws Exception{return json.readTree(action.andReturn().getResponse().getContentAsString());}
}

package vn.edu.fpt.paspm.utility;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.User;
import vn.edu.fpt.paspm.user.UserRepository;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:utility;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
    "paspm.jwt-secret=01234567890123456789012345678901", "paspm.otp-secret=01234567890123456789012345678901",
    "paspm.bootstrap-email=", "paspm.bootstrap-password="
})
@org.springframework.context.annotation.Import(UtilityIntegrationTest.SourceConfig.class)
@AutoConfigureMockMvc
class UtilityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired ObjectMapper json;
    String system, admin, merchant, foreign;
    int store, product;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM utility_matrix_item");
        jdbc.update("DELETE FROM utility_snapshot");
        jdbc.update("DELETE FROM import_staging_row");
        jdbc.update("DELETE FROM transaction_item");
        jdbc.update("DELETE FROM sales_transaction");
        jdbc.update("DELETE FROM import_batch");
        jdbc.update("DELETE FROM api_connection");
        jdbc.update("INSERT INTO store(name) VALUES (?)", "Utility " + UUID.randomUUID());
        store=jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        jdbc.update("INSERT INTO store(name) VALUES (?)", "Other " + UUID.randomUUID());
        int other=jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        system=token("SYSTEM_ADMIN", null); admin=token("STORE_ADMIN", store);
        merchant=token("MERCHANDISER", store); foreign=token("MERCHANDISER", other);
        jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,'Utility Milk','each',TRUE,100,20,'ACTIVE')",store,"U-"+UUID.randomUUID());
        product=jdbc.queryForObject("SELECT MAX(product_id) FROM product",Integer.class);
    }

    @Test void separatesPolicyWindowAndWeightPermissionsAndPreservesOwnedSettings() throws Exception {
        policy("0","0.5");
        put("/api/utility/config/expiry-window",admin,"{\"expiryWindowDays\":45}").andExpect(status().isOk());
        put("/api/utility/config",merchant,config("0.4")).andExpect(status().isOk())
            .andExpect(jsonPath("$.config.expiryWindowDays").value(45));
        put("/api/utility/config/expiry-window",admin,"{\"expiryWindowDays\":60}").andExpect(status().isOk())
            .andExpect(jsonPath("$.config.lambda").value(0.4));
        put("/api/system/utility/lambda-policy",merchant,"{\"lambdaMin\":0,\"lambdaMax\":1}").andExpect(status().isForbidden());
        put("/api/utility/config/expiry-window",merchant,"{\"expiryWindowDays\":1}").andExpect(status().isForbidden());
        put("/api/utility/config",admin,config("0.3")).andExpect(status().isForbidden());
        put("/api/utility/config",merchant,config("0.3").replace("}",",\"lambdaMax\":5}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/utility/config").header("Authorization","Bearer "+merchant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.config.expiryWindowDays").value(60))
            .andExpect(jsonPath("$.config.lambda").value(0.4));
        mvc.perform(get("/api/system/utility/lambda-policy").header("Authorization","Bearer "+system))
            .andExpect(status().isOk()).andExpect(jsonPath("$.lambdaMin").value(0.0))
            .andExpect(jsonPath("$.lambdaMax").value(0.5));
        mvc.perform(get("/api/system/utility/lambda-policy").header("Authorization","Bearer "+merchant))
            .andExpect(status().isForbidden());
    }

    @Test void rejectsInvalidBoundsWindowsAndCoefficientsWithoutChangingValues() throws Exception {
        policy("0","0.5");
        put("/api/system/utility/lambda-policy",system,"{\"lambdaMin\":-1,\"lambdaMax\":0.5}").andExpect(status().isBadRequest());
        put("/api/system/utility/lambda-policy",system,"{\"lambdaMin\":0.4,\"lambdaMax\":0.2}").andExpect(status().isBadRequest());
        for(String t: new String[]{"0","-1","1.5","null"})
            put("/api/utility/config/expiry-window",admin,"{\"expiryWindowDays\":"+t+"}").andExpect(status().isBadRequest());
        put("/api/utility/config",merchant,config("0.6")).andExpect(status().isBadRequest());
        put("/api/utility/config",merchant,config("0.2").replace("\"alpha\":1","\"alpha\":0.5")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/utility/config")).andExpect(status().isUnauthorized());
    }

    @Test void scorePatchIsPartialValidatedAndStoreScoped() throws Exception {
        patchScore(merchant,"{\"stockPriorityScore\":0.8}").andExpect(status().isOk());
        patchScore(merchant,"{\"seasonalPriorityScore\":0.9}").andExpect(status().isOk())
            .andExpect(jsonPath("$.stockPriorityScore").value(0.8));
        for(String bad:new String[]{"{\"stockPriorityScore\":-0.1}","{\"seasonalPriorityScore\":1.1}","{\"stockPriorityScore\":null}","{}"})
            patchScore(merchant,bad).andExpect(status().isBadRequest());
        patchScore(foreign,"{\"stockPriorityScore\":0.1}").andExpect(status().isNotFound());
        patchScore(admin,"{\"stockPriorityScore\":0.1}").andExpect(status().isForbidden());
        mvc.perform(get("/api/utility/products/{id}/scores",product).header("Authorization","Bearer "+merchant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.stockPriorityScore").value(0.8))
            .andExpect(jsonPath("$.seasonalPriorityScore").value(0.9));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_batch WHERE product_id=?",Integer.class,product));
    }

    @Autowired FixtureSource fixtures;
    static final java.time.LocalDate ANALYSIS=java.time.LocalDate.of(2026,10,17);
    static final String RANGE="{\"rangeStart\":\"2026-10-01\",\"rangeEnd\":\"2026-10-31\",\"asOfDate\":\"2026-10-17\"}";

    @Test void snapshotFreezesPolicyWindowScoresAndHistoricalUtilityAndRejectsStaleLambda() throws Exception {
        policy("0","0.5");
        patchScore(merchant,"{\"stockPriorityScore\":0.8,\"seasonalPriorityScore\":0.9}").andExpect(status().isOk());
        String stockAndSeason="{\"enableStock\":true,\"enableExpiry\":false,\"enableSeason\":true,\"alpha\":0.7,\"beta\":0,\"gamma\":0.3,\"lambda\":0.5}";
        put("/api/utility/config",merchant,stockAndSeason).andExpect(status().isOk());
        fixture();
        postUtility("preview",merchant,RANGE).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalWeightedUtility").value(16980))
            .andExpect(jsonPath("$.items[0].baseUtility").value(12000));
        String created=postUtility("snapshots",merchant,RANGE).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id=json.readTree(created).get("snapshotId").asInt();
        String hash=json.readTree(created).get("matrixHash").asText();
        postUtility("snapshots",merchant,RANGE).andExpect(status().isCreated()).andExpect(jsonPath("$.matrixHash").value(hash));
        put("/api/utility/config/expiry-window",admin,"{\"expiryWindowDays\":60}").andExpect(status().isOk());
        patchScore(merchant,"{\"stockPriorityScore\":0.2,\"seasonalPriorityScore\":0.1}").andExpect(status().isOk());
        policy("0","0.1");
        mvc.perform(get("/api/utility/config").header("Authorization","Bearer "+merchant)).andExpect(jsonPath("$.lambdaValid").value(false));
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot",Integer.class);
        postUtility("snapshots",merchant,RANGE).andExpect(status().isConflict());
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot",Integer.class));
        mvc.perform(get("/api/utility/snapshots/{id}",id).header("Authorization","Bearer "+merchant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.matrixHash").value(hash))
            .andExpect(jsonPath("$.configuration.config.expiryWindowDays").isEmpty())
            .andExpect(jsonPath("$.configuration.lambdaPolicy.lambdaMax").value(0.5));
        mvc.perform(get("/api/utility/snapshots/{id}/matrix",id).header("Authorization","Bearer "+merchant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].weightedUtility").value(16980))
            .andExpect(jsonPath("$.items[0].stockScore").value(0.8)).andExpect(jsonPath("$.items[0].seasonalScore").value(0.9))
            .andExpect(jsonPath("$.items[0].weightValue").value(1.415)).andExpect(jsonPath("$.items[0].quantity").value(3))
            .andExpect(jsonPath("$.items[0].algorithmItemId").value(product));
        mvc.perform(get("/api/utility/snapshots/{id}",id).header("Authorization","Bearer "+foreign)).andExpect(status().isNotFound());
        mvc.perform(get("/api/utility/snapshots/{id}/matrix",id).header("Authorization","Bearer "+foreign)).andExpect(status().isNotFound());
        put("/api/utility/config",merchant,stockAndSeason.replace("\"lambda\":0.5","\"lambda\":0.1")).andExpect(status().isOk());
        String next=postUtility("snapshots",merchant,RANGE).andExpect(status().isCreated())
            .andExpect(jsonPath("$.configuration.config.expiryWindowDays").value(60))
            .andExpect(jsonPath("$.configuration.config.lambda").value(0.1))
            .andReturn().getResponse().getContentAsString();
        int nextId=json.readTree(next).get("snapshotId").asInt();
        mvc.perform(get("/api/utility/snapshots/{id}/matrix",nextId).header("Authorization","Bearer "+merchant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].stockScore").value(0.2))
            .andExpect(jsonPath("$.items[0].seasonalScore").value(0.1))
            .andExpect(jsonPath("$.items[0].weightValue").value(1.017))
            .andExpect(jsonPath("$.items[0].weightedUtility").value(12204));
        mvc.perform(get("/api/utility/snapshots/{id}/matrix",id).header("Authorization","Bearer "+merchant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].weightValue").value(1.415))
            .andExpect(jsonPath("$.items[0].weightedUtility").value(16980));
    }

    @Test void weightReportExamplesFlowThroughPreviewForProductSevenAndThreeDayThresholds() throws Exception {
        policy("0","0.5");
        patchScore(merchant,"{\"stockPriorityScore\":0.8,\"seasonalPriorityScore\":0.9}").andExpect(status().isOk());
        put("/api/utility/config",merchant,"{\"enableStock\":true,\"enableExpiry\":true,\"enableSeason\":true,\"alpha\":0.5,\"beta\":0.2,\"gamma\":0.3,\"lambda\":0.5}").andExpect(status().isOk());
        put("/api/utility/config/expiry-window",admin,"{\"expiryWindowDays\":30}").andExpect(status().isOk());
        fixture();
        jdbc.update("UPDATE product SET expiry_push_days=7 WHERE product_id=?",product);
        jdbc.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,expiry_date,initial_quantity,remaining_quantity,status) VALUES (?,'B001',CURRENT_TIMESTAMP,?,20,20,'ACTIVE')",product,ANALYSIS.plusDays(5));
        jdbc.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,expiry_date,initial_quantity,remaining_quantity,status) VALUES (?,'B002',CURRENT_TIMESTAMP,?,80,80,'ACTIVE')",product,ANALYSIS.plusDays(40));
        jdbc.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,expiry_date,initial_quantity,remaining_quantity,status) VALUES (?,'B003',CURRENT_TIMESTAMP,?,10,10,'ACTIVE')",product,ANALYSIS.minusDays(1));

        var preciseJson=json.copy().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        var testA=preciseJson.readTree(postUtility("preview",merchant,RANGE).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var itemA=testA.path("items").get(0);
        assertTrue(testA.path("ready").asBoolean());
        assertDecimal("1",itemA.path("expiryScore").decimalValue());
        assertDecimal("1.435",itemA.path("weightValue").decimalValue());
        assertDecimal("12000.000000",itemA.path("baseUtility").decimalValue());
        assertDecimal("17220.000000",itemA.path("weightedUtility").decimalValue());
        assertDecimal("17220.000000",testA.path("totalWeightedUtility").decimalValue());

        put("/api/utility/config/expiry-window",admin,"{\"expiryWindowDays\":10}").andExpect(status().isOk());
        jdbc.update("UPDATE product SET expiry_push_days=3 WHERE product_id=?",product);
        var testB=preciseJson.readTree(postUtility("preview",merchant,RANGE).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var itemB=testB.path("items").get(0);
        assertTrue(testB.path("ready").asBoolean());
        assertDecimal("0",itemB.path("expiryScore").decimalValue());
        assertDecimal("1.335",itemB.path("weightValue").decimalValue());
        assertDecimal("12000.000000",itemB.path("baseUtility").decimalValue());
        assertDecimal("16020.000000",itemB.path("weightedUtility").decimalValue());
        assertDecimal("16020.000000",testB.path("totalWeightedUtility").decimalValue());
    }

    @Test void missingExpiryIsPreviewWarningAndPreventsReadySnapshotOnlyWhenEnabled() throws Exception {
        policy("0","0.5");fixture();
        jdbc.update("UPDATE product SET expiry_push_days=7 WHERE product_id=?",product);
        String expiry=config("0.5").replace("\"enableStock\":true","\"enableStock\":false").replace("\"enableExpiry\":false","\"enableExpiry\":true").replace("\"alpha\":1","\"alpha\":0").replace("\"beta\":0","\"beta\":1");
        put("/api/utility/config",merchant,expiry).andExpect(status().isOk());
        jdbc.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,initial_quantity,remaining_quantity,status) VALUES (?,'MISSING',CURRENT_TIMESTAMP,5,5,'ACTIVE')",product);
        postUtility("preview",merchant,RANGE).andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(false))
            .andExpect(jsonPath("$.items[0].expiryStatus").value("MISSING_METADATA"));
        postUtility("snapshots",merchant,RANGE).andExpect(status().isBadRequest());
        jdbc.update("UPDATE inventory_batch SET status='BLOCKED' WHERE product_id=?",product);
        postUtility("preview",merchant,RANGE).andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(true))
            .andExpect(jsonPath("$.items[0].expiryStatus").value("NO_SELLABLE_BATCH"));
        postUtility("snapshots",merchant,RANGE).andExpect(status().isCreated());
    }

    @Test void rejectsDuplicateNegativeAndForeignSourceRowsAndInvalidRanges() throws Exception {
        policy("0","0.5");fixture();
        var tx=fixtures.data.get(store).get(0);
        fixtures.data.put(store,java.util.List.of(tx,tx));
        postUtility("preview",merchant,RANGE).andExpect(status().isBadRequest());
        fixtures.data.put(store,java.util.List.of(new TransactionSource.PreparedTransaction(store,
            fixtures.data.get(store).get(0).transactionId(),ANALYSIS,true,
            java.util.List.of(new TransactionSource.Item(product,3,java.math.BigDecimal.ONE,java.math.BigDecimal.TEN,null)))));
        postUtility("snapshots",merchant,RANGE).andExpect(status().isBadRequest());
        postUtility("preview",merchant,RANGE.replace("2026-10-01","2026-11-01")).andExpect(status().isBadRequest());
        fixture();
        postUtility("preview",merchant,RANGE.replace("}",",\"storeId\":123}")).andExpect(status().isBadRequest());
        postUtility("preview",admin,RANGE).andExpect(status().isForbidden());
    }
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean UtilitySnapshotRepository snapshotRepository;

    @Test void expirySnapshotsKeepOriginalDateProductThresholdAndBatchScore() throws Exception {
        policy("0","0.5");fixture();
        jdbc.update("UPDATE product SET expiry_push_days=7 WHERE product_id=?",product);
        String expiry=config("0.5").replace("\"enableStock\":true","\"enableStock\":false").replace("\"enableExpiry\":false","\"enableExpiry\":true").replace("\"alpha\":1","\"alpha\":0").replace("\"beta\":0","\"beta\":1");
        put("/api/utility/config",merchant,expiry).andExpect(status().isOk());
        jdbc.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,expiry_date,initial_quantity,remaining_quantity,status) VALUES (?,'FIVE',CURRENT_TIMESTAMP,?,20,20,'ACTIVE')",product,ANALYSIS.plusDays(5));
        jdbc.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,expiry_date,initial_quantity,remaining_quantity,status) VALUES (?,'FORTY',CURRENT_TIMESTAMP,?,80,80,'ACTIVE')",product,ANALYSIS.plusDays(40));
        String created=postUtility("snapshots",merchant,RANGE).andExpect(status().isCreated())
            .andExpect(jsonPath("$.totalWeightedUtility").value(18000)).andReturn().getResponse().getContentAsString();
        int id=json.readTree(created).get("snapshotId").asInt();
        jdbc.update("UPDATE inventory_batch SET remaining_quantity=0 WHERE product_id=? AND batch_code='FIVE'",product);
        put("/api/utility/config/expiry-window",admin,"{\"expiryWindowDays\":60}").andExpect(status().isOk());
        postUtility("preview",merchant,RANGE).andExpect(status().isOk()).andExpect(jsonPath("$.totalWeightedUtility").value(12000));
        mvc.perform(get("/api/utility/snapshots/{id}/matrix",id).header("Authorization","Bearer "+merchant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalWeightedUtility").value(18000))
            .andExpect(jsonPath("$.configuration.analysisDate").value("2026-10-17"))
            .andExpect(jsonPath("$.configuration.config.expiryWindowDays").isEmpty());
    }

    @Test void persistenceFailureRollsBackSnapshotHeader() throws Exception {
        policy("0","0.5");fixture();
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("injected matrix write failure"))
            .when(snapshotRepository).saveItems(org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyList());
        postUtility("snapshots",merchant,RANGE).andExpect(status().isConflict());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot WHERE store_id=?",Integer.class,store));
    }

    @Test void openApiAndDatabaseConstraintsCoverF03() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/system/utility/lambda-policy'].put").exists())
            .andExpect(jsonPath("$.paths['/api/utility/config/expiry-window'].put").exists())
            .andExpect(jsonPath("$.paths['/api/utility/snapshots/{id}/matrix'].get").exists())
            .andExpect(jsonPath("$.components.schemas.ExpiryWindowRequest.properties.expiryWindowDays.type").value("integer"))
            .andExpect(jsonPath("$.paths['/api/utility/preview'].post.responses['503'].description").value("Service configuration or required dependency unavailable"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE product SET stock_priority_score=1.1 WHERE product_id=?",product));
        policy("0","0.5");fixture();
        String created=postUtility("snapshots",merchant,RANGE).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id=json.readTree(created).get("snapshotId").asInt();
        int transactionId=fixtures.data.get(store).get(0).transactionId();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO utility_matrix_item(snapshot_id,transaction_id,product_id,quantity,base_utility,expiry_status,weight_value,weighted_utility) VALUES (?,?,?,3,12000,'NO_SELLABLE_BATCH',1,12000)",id,transactionId,product));
        mvc.perform(get("/api/utility/snapshots/{id}/matrix?pageSize=0",id).header("Authorization","Bearer "+merchant)).andExpect(status().isBadRequest());
    }

    @Test void previewRejectsMissingEnabledScoresAndSkipsIneligibleSourceRows() throws Exception {
        policy("0","0.5");fixture();
        put("/api/utility/config",merchant,config("0.5")).andExpect(status().isOk());
        postUtility("preview",merchant,RANGE).andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(false))
            .andExpect(jsonPath("$.totalWeightedUtility").isEmpty());
        postUtility("snapshots",merchant,RANGE).andExpect(status().isBadRequest());
        patchScore(merchant,"{\"stockPriorityScore\":0}").andExpect(status().isOk());
        var tx=fixtures.data.get(store).get(0);
        fixtures.data.put(store,java.util.List.of(new TransactionSource.PreparedTransaction(store,tx.transactionId(),ANALYSIS,false,tx.items())));
        postUtility("preview",merchant,RANGE).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        postUtility("snapshots",merchant,RANGE).andExpect(status().isBadRequest());
    }

    @Test void matrixHashIgnoresPreparedSourceOrdering() throws Exception {
        policy("0","0.5");fixture();
        var tx=fixtures.data.get(store).get(0);
        var second=new TransactionSource.PreparedTransaction(store,2,ANALYSIS,true,tx.items());
        fixtures.data.put(store,java.util.List.of(second,tx));
        String first=postUtility("preview",merchant,RANGE).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        fixtures.data.put(store,java.util.List.of(tx,second));
        postUtility("preview",merchant,RANGE).andExpect(status().isOk()).andExpect(jsonPath("$.matrixHash").value(json.readTree(first).get("matrixHash").asText()));
    }

    void fixture() {
        String sourceOrderId="utility-fixture-"+UUID.randomUUID();
        jdbc.update("INSERT INTO sales_transaction(store_id,source_system,source_order_id,occurred_at,status,f03_eligible,total_amount,content_sha256) VALUES (?,'TEST_FIXTURE',?,?,'COMPLETED',TRUE,30000,?)",
            store,sourceOrderId,java.sql.Timestamp.from(ANALYSIS.atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant()),"a".repeat(64));
        int transactionId=jdbc.queryForObject("SELECT transaction_id FROM sales_transaction WHERE store_id=? AND source_order_id=?",Integer.class,store,sourceOrderId);
        jdbc.update("INSERT INTO transaction_item(store_id,transaction_id,product_id,quantity,unit_price,unit_cost) VALUES (?,?,?,3,10000,6000)",store,transactionId,product);
        fixtures.data.put(store,java.util.List.of(new TransactionSource.PreparedTransaction(store,transactionId,ANALYSIS,true,
            java.util.List.of(new TransactionSource.Item(product,3,new java.math.BigDecimal("10000"),new java.math.BigDecimal("6000"),null)))));
    }
    void assertDecimal(String expected,java.math.BigDecimal actual) {
        assertEquals(0,new java.math.BigDecimal(expected).compareTo(actual),"decimal value");
    }
    org.springframework.test.web.servlet.ResultActions postUtility(String path,String token,String body)throws Exception {
        return mvc.perform(post("/api/utility/"+path).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body));
    }
    @org.springframework.boot.test.context.TestConfiguration
    static class SourceConfig {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        FixtureSource fixtureSource() { return new FixtureSource(); }
    }
    static class FixtureSource implements TransactionSource {
        final java.util.Map<Integer,java.util.List<TransactionSource.PreparedTransaction>> data=new java.util.HashMap<>();
        public String sourceType() { return "TEST_FIXTURE"; }
        public java.util.List<TransactionSource.PreparedTransaction> load(int storeId,java.time.LocalDate start,java.time.LocalDate end) { return data.getOrDefault(storeId,java.util.List.of()); }
    }

    String token(String role,Integer storeId) {
        User u=users.createPendingUser("Utility tester",UUID.randomUUID()+"@example.test",role,storeId,null,null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=? WHERE user_id=?",java.sql.Timestamp.from(Instant.now()),u.id());
        return auth.token(users.findById(u.id())).accessToken();
    }
    org.springframework.test.web.servlet.ResultActions put(String path,String token,String body)throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body));
    }
    org.springframework.test.web.servlet.ResultActions patchScore(String token,String body)throws Exception {
        return mvc.perform(patch("/api/utility/products/{id}/scores",product).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body));
    }
    void policy(String min,String max)throws Exception { put("/api/system/utility/lambda-policy",system,"{\"lambdaMin\":"+min+",\"lambdaMax\":"+max+"}").andExpect(status().isOk()); }
    String config(String lambda) { return "{\"enableStock\":true,\"enableExpiry\":false,\"enableSeason\":false,\"alpha\":1,\"beta\":0,\"gamma\":0,\"lambda\":"+lambda+"}"; }
}

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
        jdbc.update("DELETE FROM utility_snapshot_season");
        jdbc.update("DELETE FROM utility_snapshot_storage");
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

    @Autowired FixtureSource fixtures;
    static final java.time.LocalDate ANALYSIS=java.time.LocalDate.of(2026,10,17);
    static final String RANGE="{\"rangeStart\":\"2026-10-01\",\"rangeEnd\":\"2026-10-31\",\"asOfDate\":\"2026-10-17\"}";

    @Test void policyAndConfigUseTheTwoWeightContractWithRolesAndValidation() throws Exception {
        policy("0.5");
        put("/api/system/utility/lambda-policy",system,"{\"lambdaMin\":0,\"lambdaMax\":0.5}").andExpect(status().isBadRequest());
        put("/api/system/utility/lambda-policy",system,"{\"lambdaMax\":-1}").andExpect(status().isBadRequest());
        put("/api/system/utility/lambda-policy",merchant,"{\"lambdaMax\":1}").andExpect(status().isForbidden());
        mvc.perform(get("/api/system/utility/lambda-policy").header("Authorization","Bearer "+system))
            .andExpect(status().isOk()).andExpect(jsonPath("$.lambdaMax").value(0.5)).andExpect(jsonPath("$.lambdaMin").doesNotExist())
            .andExpect(jsonPath("$.scoringVersion").value("W2_EXPIRY_SEASON_V1"));
        mvc.perform(get("/api/system/utility/lambda-policy").header("Authorization","Bearer "+merchant)).andExpect(status().isForbidden());
        mvc.perform(get("/api/utility/config").header("Authorization","Bearer "+admin)).andExpect(status().isOk())
            .andExpect(jsonPath("$.config.alpha").value(0.5)).andExpect(jsonPath("$.config.salesWindowDays").value(30))
            .andExpect(jsonPath("$.config.scoringVersion").value("W2_EXPIRY_SEASON_V1"));
        put("/api/utility/config",merchant,config("0.6","0.4","0.3").replace("}",",\"salesWindowDays\":14}")).andExpect(status().isOk())
            .andExpect(jsonPath("$.config.salesWindowDays").value(14)).andExpect(jsonPath("$.lambdaValid").value(true));
        put("/api/utility/config",merchant,config("0.7","0.3","0.2")).andExpect(status().isOk())
            .andExpect(jsonPath("$.config.salesWindowDays").value(14)).andExpect(jsonPath("$.config.alpha").value(0.7));
        for(String bad:new String[]{config("0.6","0.5","0.3"),config("0.6","0.4","0.6"),config("1.2","-0.2","0.1"),
                config("0.6","0.4","0.3").replace("}",",\"salesWindowDays\":0}"),config("0.6","0.4","0.3").replace("}",",\"salesWindowDays\":366}"),
                config("0.6","0.4","0.3").replace("}",",\"salesWindowDays\":1.5}"),config("0.6","0.4","0.3").replace("}",",\"gamma\":0}"),
                "{\"enableStock\":true,\"enableExpiry\":true,\"enableSeason\":true,\"alpha\":0.5,\"beta\":0.2,\"gamma\":0.3,\"lambda\":0.5}"})
            put("/api/utility/config",merchant,bad).andExpect(status().isBadRequest());
        mvc.perform(get("/api/utility/config").header("Authorization","Bearer "+merchant)).andExpect(jsonPath("$.config.alpha").value(0.7));
        put("/api/utility/config",admin,config("0.5","0.5","0.1")).andExpect(status().isForbidden());
        put("/api/utility/config/expiry-window",admin,"{\"expiryWindowDays\":30}").andExpect(status().isGone());
        put("/api/utility/config/expiry-window",merchant,"{\"expiryWindowDays\":30}").andExpect(status().isForbidden());
        mvc.perform(get("/api/utility/config")).andExpect(status().isUnauthorized());
        policy("0.1");
        mvc.perform(get("/api/utility/config").header("Authorization","Bearer "+merchant)).andExpect(jsonPath("$.lambdaValid").value(false));
    }

    @Test void seasonsAreStoreScopedMerchandiserWrittenAndProtectedWhileAssigned() throws Exception {
        int tet=createSeason(season("Tet","2026-10-01","2026-10-31","0.9"));
        send(post("/api/utility/seasons"),merchant,season("tet","2026-12-01","2026-12-02","0.1")).andExpect(status().isConflict());
        send(post("/api/utility/seasons"),merchant,season("Bad","2026-12-02","2026-12-01","0.1")).andExpect(status().isBadRequest());
        send(post("/api/utility/seasons"),merchant,season("Heavy","2026-12-01","2026-12-02","1.5")).andExpect(status().isBadRequest());
        send(post("/api/utility/seasons"),merchant,season("Extra","2026-12-01","2026-12-02","0.1").replace("}",",\"storeId\":1}")).andExpect(status().isBadRequest());
        send(post("/api/utility/seasons"),admin,season("Admin","2026-12-01","2026-12-02","0.1")).andExpect(status().isForbidden());
        send(get("/api/utility/seasons"),admin,null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].name").value("Tet"));
        send(get("/api/utility/seasons/{id}",tet),foreign,null).andExpect(status().isNotFound());
        send(get("/api/utility/seasons"),foreign,null).andExpect(jsonPath("$.totalElements").value(0));
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/utility/seasons/{id}/products/{p}",tet,product),merchant,null).andExpect(status().isCreated());
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/utility/seasons/{id}/products/{p}",tet,product),merchant,null).andExpect(status().isOk());
        jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) SELECT MAX(store_id),?,'Foreign','each',FALSE,1,1,'ACTIVE' FROM store",UUID.randomUUID().toString());
        int foreignProduct=jdbc.queryForObject("SELECT MAX(product_id) FROM product",Integer.class);
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/utility/seasons/{id}/products/{p}",tet,foreignProduct),merchant,null).andExpect(status().isNotFound());
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/utility/seasons/{id}/products/{p}",tet,product),foreign,null).andExpect(status().isNotFound());
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/utility/seasons/{id}/products/{p}",tet,product),admin,null).andExpect(status().isForbidden());
        send(get("/api/utility/seasons/{id}/products",tet),admin,null).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].productId").value(product));
        send(delete("/api/utility/seasons/{id}",tet),merchant,null).andExpect(status().isConflict());
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/utility/seasons/{id}",tet,null),merchant,season("Tet 2026","2026-10-01","2026-10-31","0.8").replace("}",",\"expectedVersion\":9}")).andExpect(status().isConflict());
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/utility/seasons/{id}",tet,null),merchant,season("Tet 2026","2026-10-01","2026-10-31","0.8")).andExpect(status().isBadRequest());
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/utility/seasons/{id}",tet,null),merchant,season("Tet 2026","2026-10-01","2026-10-31","0.8").replace("}",",\"expectedVersion\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.name").value("Tet 2026"));
        send(delete("/api/utility/seasons/{id}/products/{p}",tet,product),merchant,null).andExpect(status().isNoContent());
        send(delete("/api/utility/seasons/{id}/products/{p}",tet,product),merchant,null).andExpect(status().isNotFound());
        send(delete("/api/utility/seasons/{id}",tet),admin,null).andExpect(status().isForbidden());
        send(delete("/api/utility/seasons/{id}",tet),merchant,null).andExpect(status().isNoContent());
        send(get("/api/utility/seasons/{id}",tet),merchant,null).andExpect(status().isNotFound());
    }

    @Test void productScoresPreviewRiskSeasonAndMissingPushDaysAndThePatchIsGone() throws Exception {
        policy("0.5");
        put("/api/utility/config",merchant,config("0.5","0.5","0.5")).andExpect(status().isOk());
        var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        batch("WITH",120,today.plusDays(3),today.plusDays(10));batch("WITHOUT",50,null,today.plusDays(20));
        int s=createSeason(season("Now",today.minusDays(1).toString(),today.plusDays(1).toString(),"0.9"));
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/utility/seasons/{id}/products/{p}",s,product),merchant,null).andExpect(status().isCreated());
        int missing=jdbc.queryForObject("SELECT batch_id FROM inventory_batch WHERE product_id=? AND batch_code='WITHOUT'",Integer.class,product);
        send(get("/api/utility/products/{id}/scores",product),admin,null).andExpect(status().isOk())
            .andExpect(jsonPath("$.expiryStatus").value("SLOW_MOVING"))
            .andExpect(jsonPath("$.seasonWeight").value(0.9)).andExpect(jsonPath("$.seasonId").value(s))
            .andExpect(jsonPath("$.batchesMissingPushDay[0]").value(missing))
            .andExpect(jsonPath("$.weightValue").value(1.23)) // 1 + 0.5 * (0.5 * 0 + 0.5 * 0.9) = 1.225 -> 1.23
            .andExpect(jsonPath("$.ready").value(true));
        send(get("/api/utility/products/{id}/scores",product),foreign,null).andExpect(status().isNotFound());
        send(patch("/api/utility/products/{id}/scores",product),merchant,"{\"stockPriorityScore\":0.8}").andExpect(status().isGone());
        send(patch("/api/utility/products/{id}/scores",product),admin,"{\"stockPriorityScore\":0.8}").andExpect(status().isForbidden());
    }

    @Test void snapshotFreezesWeightV2AndSeasonsByValueAndOnlyWarnsForMissingPushDays() throws Exception {
        policy("0.5");
        put("/api/utility/config",merchant,config("0.6","0.4","0.5")).andExpect(status().isOk());
        fixture(750); // 25 per day over N=30
        batch("GOLDEN",120,ANALYSIS.plusDays(3),ANALYSIS.plusDays(10));
        batch("NOPUSH",999,null,ANALYSIS.plusDays(30));
        int tet=createSeason(season("Tet","2026-10-01","2026-10-31","0.9"));
        int ended=createSeason(season("Ended","2026-09-01","2026-10-16","1"));
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/utility/seasons/{id}/products/{p}",tet,product),merchant,null).andExpect(status().isCreated());
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/utility/seasons/{id}/products/{p}",ended,product),merchant,null).andExpect(status().isCreated());
        var precise=json.copy().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        var preview=precise.readTree(postUtility("preview",merchant,RANGE).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var item=preview.path("items").get(0);
        assertDecimal("0.375",item.path("expiryRisk").decimalValue());
        assertDecimal("0.9",item.path("seasonWeight").decimalValue());
        assertEquals(tet,item.path("seasonId").asInt());
        assertDecimal("1.29",item.path("weightValue").decimalValue()); // 1 + 0.5 * (0.6 * 0.375 + 0.4 * 0.9) = 1.2925
        assertDecimal("3870000",item.path("weightedUtility").decimalValue());
        assertEquals("MISSING_PUSH_DAY",item.path("expiryStatus").asText());
        assertTrue(preview.path("warnings").toString().contains("no push day"));
        String created=postUtility("snapshots",merchant,RANGE).andExpect(status().isCreated())
            .andExpect(jsonPath("$.configuration.config.scoringVersion").value("W2_EXPIRY_SEASON_V1"))
            .andExpect(jsonPath("$.configuration.weightScale").value(2))
            .andExpect(jsonPath("$.configuration.seasons.length()").value(1))
            .andExpect(jsonPath("$.configuration.seasons[0].weight").value(0.9))
            .andReturn().getResponse().getContentAsString();
        int id=json.readTree(created).get("snapshotId").asInt();
        String hash=json.readTree(created).get("matrixHash").asText();
        assertTrue(json.readTree(created).path("warnings").toString().contains("no push day"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot_season WHERE snapshot_id=? AND season_id=? AND weight=0.9",Integer.class,id,tet));
        // Later edits never reach the frozen snapshot (Q8).
        send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/utility/seasons/{id}",tet,null),merchant,season("Renamed","2026-10-01","2026-10-31","0.1").replace("}",",\"expectedVersion\":1}")).andExpect(status().isOk());
        put("/api/utility/config",merchant,config("1","0","0.1")).andExpect(status().isOk());
        mvc.perform(get("/api/utility/snapshots/{id}/matrix",id).header("Authorization","Bearer "+merchant)).andExpect(status().isOk())
            .andExpect(jsonPath("$.matrixHash").value(hash)).andExpect(jsonPath("$.items[0].weightValue").value(1.29))
            .andExpect(jsonPath("$.items[0].seasonId").value(tet)).andExpect(jsonPath("$.configuration.seasons[0].name").value("Tet"))
            .andExpect(jsonPath("$.items[0].expiryEvidence.scoringVersion").value("W2_EXPIRY_SEASON_V1"))
            .andExpect(jsonPath("$.items[0].expiryEvidence.unitsSold").value(750));
        // A lambda above a lowered maximum blocks new snapshots (409); existing ones stay readable.
        put("/api/utility/config",merchant,config("0.6","0.4","0.5")).andExpect(status().isOk());
        policy("0.2");
        postUtility("snapshots",merchant,RANGE).andExpect(status().isConflict());
        mvc.perform(get("/api/utility/snapshots/{id}",id).header("Authorization","Bearer "+foreign)).andExpect(status().isNotFound());
        mvc.perform(get("/api/utility/snapshots/{id}/matrix",id).header("Authorization","Bearer "+foreign)).andExpect(status().isNotFound());
    }

    @Test void rejectsDuplicateNegativeAndForeignSourceRowsAndInvalidRanges() throws Exception {
        policy("0.5");fixture(3);
        var tx=fixtures.data.get(store).get(0);
        fixtures.data.put(store,java.util.List.of(tx,tx));
        postUtility("preview",merchant,RANGE).andExpect(status().isBadRequest());
        fixtures.data.put(store,java.util.List.of(new TransactionSource.PreparedTransaction(store,
            fixtures.data.get(store).get(0).transactionId(),ANALYSIS,true,
            java.util.List.of(new TransactionSource.Item(product,3,java.math.BigDecimal.ONE,java.math.BigDecimal.TEN,null)))));
        postUtility("snapshots",merchant,RANGE).andExpect(status().isBadRequest());
        postUtility("preview",merchant,RANGE.replace("2026-10-01","2026-11-01")).andExpect(status().isBadRequest());
        fixture(3);
        postUtility("preview",merchant,RANGE.replace("}",",\"storeId\":123}")).andExpect(status().isBadRequest());
        postUtility("preview",admin,RANGE).andExpect(status().isForbidden());
    }
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean UtilitySnapshotRepository snapshotRepository;

    @Test void persistenceFailureRollsBackSnapshotHeader() throws Exception {
        policy("0.5");fixture(3);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("injected matrix write failure"))
            .when(snapshotRepository).saveItems(org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyList());
        postUtility("snapshots",merchant,RANGE).andExpect(status().isConflict());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot WHERE store_id=?",Integer.class,store));
    }

    @Test void openApiAndDatabaseConstraintsCoverF03() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/system/utility/lambda-policy'].put").exists())
            .andExpect(jsonPath("$.paths['/api/utility/config/expiry-window'].put.deprecated").value(true))
            .andExpect(jsonPath("$.paths['/api/utility/seasons'].post").exists())
            .andExpect(jsonPath("$.paths['/api/utility/seasons/{seasonId}/products/{productId}'].put").exists())
            .andExpect(jsonPath("$.paths['/api/utility/snapshots/{id}/matrix'].get").exists())
            .andExpect(jsonPath("$.components.schemas.WeightUpdate.properties.salesWindowDays.type").exists())
            .andExpect(jsonPath("$.components.schemas.PolicyUpdate.properties.lambdaMin").doesNotExist())
            .andExpect(jsonPath("$.components.schemas.Evidence.properties.expiryPushDays").exists())
            .andExpect(jsonPath("$.components.schemas.FrozenConfiguration.properties.config").exists())
            .andExpect(jsonPath("$.components.schemas.FrozenV2.properties.seasons").exists())
            .andExpect(jsonPath("$.paths['/api/utility/preview'].post.responses['503'].description").value("Service configuration or required dependency unavailable"));
        policy("0.5");fixture(3);
        String created=postUtility("snapshots",merchant,RANGE).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id=json.readTree(created).get("snapshotId").asInt();
        int transactionId=fixtures.data.get(store).get(0).transactionId();
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("INSERT INTO utility_matrix_item(snapshot_id,transaction_id,product_id,quantity,base_utility,expiry_status,weight_value,weighted_utility,expiry_risk) VALUES (?,?,?,3,12000,'AVAILABLE',1,12000,1.5)",id,transactionId+1000,product));
        batch("CHECKED",5,null,ANALYSIS.plusDays(1));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE inventory_batch SET push_day=DATE '2030-01-02' WHERE product_id=? AND expiry_date IS NOT NULL",product));
        mvc.perform(get("/api/utility/snapshots/{id}/matrix?pageSize=0",id).header("Authorization","Bearer "+merchant)).andExpect(status().isBadRequest());
    }

    @Test void ineligibleSourceRowsAreSkippedAndAnEmptyMatrixCannotBeSnapshotted() throws Exception {
        policy("0.5");fixture(3);
        var tx=fixtures.data.get(store).get(0);
        fixtures.data.put(store,java.util.List.of(new TransactionSource.PreparedTransaction(store,tx.transactionId(),ANALYSIS,false,tx.items())));
        postUtility("preview",merchant,RANGE).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        postUtility("snapshots",merchant,RANGE).andExpect(status().isBadRequest());
    }

    @Test void matrixHashIgnoresPreparedSourceOrdering() throws Exception {
        policy("0.5");fixture(3);
        var tx=fixtures.data.get(store).get(0);
        var second=new TransactionSource.PreparedTransaction(store,2,ANALYSIS,true,tx.items());
        fixtures.data.put(store,java.util.List.of(second,tx));
        String first=postUtility("preview",merchant,RANGE).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        fixtures.data.put(store,java.util.List.of(tx,second));
        postUtility("preview",merchant,RANGE).andExpect(status().isOk()).andExpect(jsonPath("$.matrixHash").value(json.readTree(first).get("matrixHash").asText()));
    }

    void fixture(int quantity) {
        String sourceOrderId="utility-fixture-"+UUID.randomUUID();
        jdbc.update("INSERT INTO sales_transaction(store_id,source_system,source_order_id,occurred_at,status,f03_eligible,total_amount,content_sha256) VALUES (?,'TEST_FIXTURE',?,?,'COMPLETED',TRUE,30000,?)",
            store,sourceOrderId,java.sql.Timestamp.from(ANALYSIS.atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant()),"a".repeat(64));
        int transactionId=jdbc.queryForObject("SELECT transaction_id FROM sales_transaction WHERE store_id=? AND source_order_id=?",Integer.class,store,sourceOrderId);
        jdbc.update("INSERT INTO transaction_item(store_id,transaction_id,product_id,quantity,unit_price,unit_cost) VALUES (?,?,?,?,10000,6000)",store,transactionId,product,quantity);
        fixtures.data.put(store,java.util.List.of(new TransactionSource.PreparedTransaction(store,transactionId,ANALYSIS,true,
            java.util.List.of(new TransactionSource.Item(product,quantity,new java.math.BigDecimal("10000"),new java.math.BigDecimal("6000"),null)))));
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
    org.springframework.test.web.servlet.ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,String token,String body)throws Exception {
        if(body!=null)request=request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request.header("Authorization","Bearer "+token));
    }
    void policy(String max)throws Exception { put("/api/system/utility/lambda-policy",system,"{\"lambdaMax\":"+max+"}").andExpect(status().isOk()); }
    String config(String alpha,String beta,String lambda) { return "{\"alpha\":"+alpha+",\"beta\":"+beta+",\"lambda\":"+lambda+"}"; }
    String season(String name,String start,String end,String weight) { return "{\"name\":\""+name+"\",\"startDate\":\""+start+"\",\"endDate\":\""+end+"\",\"weight\":"+weight+"}"; }
    int createSeason(String body)throws Exception {
        return json.readTree(send(post("/api/utility/seasons"),merchant,body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("seasonId").asInt();
    }
    void batch(String code,long quantity,java.time.LocalDate pushDay,java.time.LocalDate expiry) {
        jdbc.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,expiry_date,initial_quantity,remaining_quantity,status,push_day) VALUES (?,?,CURRENT_TIMESTAMP,?,?,?,'ACTIVE',?)",product,code,expiry,quantity,quantity,pushDay);
    }
}

package vn.edu.fpt.paspm.utility;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:weightv12;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true",
    "paspm.jwt-secret=test-only-jwt-key-with-at-least-thirty-two-bytes",
    "paspm.otp-secret=test-only-otp-key-with-at-least-thirty-two-bytes",
    "paspm.avatar.directory=/tmp/paspm-v12-test-avatars",
    "paspm.bootstrap-email=","paspm.bootstrap-password=","paspm.store-time-zone=Asia/Ho_Chi_Minh"
})
@AutoConfigureMockMvc
class WeightRulesV12IntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired ObjectMapper json;
    int store,other,product;
    String admin,merchant,foreign,system;
    final LocalDate day=LocalDate.of(2026,10,19);
    final String range="{\"rangeStart\":\"2026-10-01\",\"rangeEnd\":\"2026-10-31\",\"asOfDate\":\"2026-10-19\"}";

    @BeforeEach void setup() throws Exception {
        store=store();other=store();
        admin=token("STORE_ADMIN",store);merchant=token("MERCHANDISER",store);
        foreign=token("STORE_ADMIN",other);system=token("SYSTEM_ADMIN",null);
        jdbc.update("UPDATE system_configuration SET config_value=? WHERE config_key='UTILITY_LAMBDA_POLICY'","{\"lambdaMin\":0,\"lambdaMax\":0.5}");
        String created=mvc.perform(post("/api/products").header("Authorization","Bearer "+admin)
            .contentType(MediaType.APPLICATION_JSON).content(productBody("7")))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        product=json.readTree(created).path("id").asInt();
    }
    @Test void storeAdminCanConfigureThresholdAndInventoryIsUnchanged() throws Exception {
        int batch=batch("five",day.plusDays(5),20,"ACTIVE",0);
        updateThreshold("3").andExpect(status().isOk()).andExpect(jsonPath("$.expiryPushDays").value(3));
        mvc.perform(get("/api/products/{id}",product).header("Authorization","Bearer "+admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.expiryPushDays").value(3))
            .andExpect(jsonPath("$.stockQuantity").value(20));
        assertEquals(20,jdbc.queryForObject("SELECT remaining_quantity FROM inventory_batch WHERE batch_id=?",Integer.class,batch));
        for(String invalid:new String[]{"-1","1.5","\"7\"","true","2147483648"})
            updateThreshold(invalid).andExpect(status().isBadRequest());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE product SET expiry_push_days=-1 WHERE product_id=?",product));
    }
    @Test void nullAndOmittedThresholdHaveEffectiveZero() throws Exception {
        updateThreshold("null").andExpect(status().isOk()).andExpect(jsonPath("$.expiryPushDays").value(0));
        mvc.perform(put("/api/products/{id}",product).header("Authorization","Bearer "+admin)
            .contentType(MediaType.APPLICATION_JSON).content(productBody("7").replace(",\"expiryPushDays\":7","")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.expiryPushDays").value(0));
    }
    @Test void productThresholdEnforcesRolesAndStoreScope() throws Exception {
        mvc.perform(put("/api/products/{id}",product).header("Authorization","Bearer "+merchant)
            .contentType(MediaType.APPLICATION_JSON).content(productBody("3"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/products/{id}",product).header("Authorization","Bearer "+foreign)
            .contentType(MediaType.APPLICATION_JSON).content(productBody("3"))).andExpect(status().isNotFound());
        mvc.perform(put("/api/products/{id}",product).contentType(MediaType.APPLICATION_JSON).content(productBody("3")))
            .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/products/{id}",product).header("Authorization","Bearer "+admin)
            .contentType(MediaType.APPLICATION_JSON).content(productBody("3").replace("{","{\"storeId\":"+other+",")))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/system/utility/lambda-policy").header("Authorization","Bearer "+merchant)
            .contentType(MediaType.APPLICATION_JSON).content("{\"lambdaMin\":0,\"lambdaMax\":1}"))
            .andExpect(status().isForbidden());
    }
    @Test void officialExamplesUseProductThresholdAndHistoricalF04ValuesWithoutStoreFallback() throws Exception {
        configure();int tx=transaction();
        batch("late-received-earliest-expiry",day.plusDays(5),20,"ACTIVE",10);
        batch("early-received-later-expiry",day.plusDays(40),80,"ACTIVE",100);
        batch("expired",day.minusDays(1),10,"ACTIVE",200);
        for(int t:new int[]{7,3,0}) {
            updateThreshold(Integer.toString(t)).andExpect(status().isOk());
            mvc.perform(put("/api/utility/config/expiry-window").header("Authorization","Bearer "+admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"expiryWindowDays\":100}"))
                .andExpect(status().isOk());
            JsonNode result=preview();export(t==7?"utility_preview_response":"utility_preview_threshold_"+t+"_response",result);JsonNode item=result.path("items").get(0);
            assertEquals(t==7?1:0,item.path("expiryScore").asInt());
            decimal(t==7?"1.435":"1.335",item.path("weightValue"));
            decimal(t==7?"17220":"16020",item.path("weightedUtility"));
            decimal("12000",item.path("baseUtility"));
            assertEquals(t,item.path("expiryEvidence").path("expiryPushDays").asInt());
            assertEquals(day.plusDays(5).toString(),item.path("expiryEvidence").path("earliestEligibleExpiryDate").asText());
            assertEquals(5,item.path("expiryEvidence").path("daysRemaining").asInt());
            assertEquals("EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1",result.path("configuration").path("config").path("scoringVersion").asText());
            assertEquals("Asia/Ho_Chi_Minh",result.path("configuration").path("timeZone").asText());
        }
        assertEquals(0,new BigDecimal("10000").compareTo(jdbc.queryForObject("SELECT unit_price FROM transaction_item WHERE transaction_id=?",BigDecimal.class,tx)));
        assertEquals(0,new BigDecimal("6000").compareTo(jdbc.queryForObject("SELECT unit_cost FROM transaction_item WHERE transaction_id=?",BigDecimal.class,tx)));
        assertEquals(110,jdbc.queryForObject("SELECT SUM(remaining_quantity) FROM inventory_batch WHERE product_id=?",Integer.class,product));
    }
    @Test void independentPushQueryUsesFefoAndIgnoresMiningOrHistoricalTransactions() throws Exception {
        int earliest=batch("earliest",day.plusDays(2),1,"ACTIVE",2);
        batch("second",day.plusDays(5),20,"ACTIVE",100);
        batch("far",day.plusDays(40),80,"ACTIVE",200);
        batch("today",day,10,"ACTIVE",0);
        batch("blocked",day.plusDays(1),10,"BLOCKED",0);
        batch("empty",day.plusDays(1),0,"ACTIVE",0);
        mvc.perform(get("/api/utility/expiry-push-batches").param("asOfDate",day.toString()).header("Authorization","Bearer "+merchant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.items[0].batchId").value(earliest)).andExpect(jsonPath("$.items[0].pushRequiredQuantity").value(1))
            .andExpect(jsonPath("$.items[1].pushRequiredQuantity").value(20)).andExpect(jsonPath("$.items[0].asOfDate").value(day.toString()));
        mvc.perform(get("/api/utility/expiry-push-batches").param("productId",Integer.toString(product)).header("Authorization","Bearer "+foreign))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/utility/expiry-push-batches").header("Authorization","Bearer "+foreign))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/utility/expiry-push-batches")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/utility/expiry-push-batches").header("Authorization","Bearer "+system)).andExpect(status().isForbidden());
        mvc.perform(get("/api/utility/expiry-push-batches").param("pageSize","0").header("Authorization","Bearer "+merchant)).andExpect(status().isBadRequest());
        jdbc.update("UPDATE product SET status='INACTIVE' WHERE product_id=?",product);
        mvc.perform(get("/api/utility/expiry-push-batches").param("asOfDate",day.toString()).header("Authorization","Bearer "+merchant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void snapshotFreezesEvidenceAndWarningsAfterProductAndInventoryChanges() throws Exception {
        configure();transaction();batch("five",day.plusDays(5),20,"ACTIVE",0);batch("forty",day.plusDays(40),80,"ACTIVE",0);
        JsonNode created=json.readTree(mvc.perform(post("/api/utility/snapshots").header("Authorization","Bearer "+merchant)
            .contentType(MediaType.APPLICATION_JSON).content(range)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        int id=created.path("snapshotId").asInt();String hash=created.path("matrixHash").asText();
        export("utility_snapshot_response",created);
        export("utility_matrix_response",json.readTree(mvc.perform(get("/api/utility/snapshots/{id}/matrix",id).header("Authorization","Bearer "+merchant)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()));
        updateThreshold("3").andExpect(status().isOk());
        jdbc.update("UPDATE inventory_batch SET status='BLOCKED' WHERE product_id=?",product);
        mvc.perform(get("/api/utility/snapshots/{id}/matrix",id).header("Authorization","Bearer "+merchant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.matrixHash").value(hash))
            .andExpect(jsonPath("$.items[0].expiryEvidence.expiryPushDays").value(7))
            .andExpect(jsonPath("$.items[0].expiryEvidence.batches[0].remainingQuantity").value(20))
            .andExpect(jsonPath("$.items[0].weightedUtility").value(17220));
        decimal("16020",preview().path("items").get(0).path("weightedUtility"));
    }
    @Test void oldLinearSnapshotsStayReadableAndTheirStoredValuesNeverChange() throws Exception {
        configure();int tx=transaction();
        String oldConfig="{\"config\":{\"enableStock\":true,\"enableExpiry\":true,\"enableSeason\":true,\"alpha\":0.5,\"beta\":0.2,\"gamma\":0.3,\"lambda\":0.5,\"expiryWindowDays\":30,\"version\":1,\"scoringVersion\":\"F03_LINEAR_V1\"},\"lambdaPolicy\":{\"lambdaMin\":0,\"lambdaMax\":0.5,\"version\":1},\"effectiveCoefficients\":{\"alpha\":0.5,\"beta\":0.2,\"gamma\":0.3},\"analysisDate\":\"2026-10-19\",\"source\":\"F04_DATABASE_V1\",\"utilityScale\":6,\"roundingMode\":\"HALF_UP\",\"itemMapping\":\"PRODUCT_ID_V1\"}";
        int actor=jdbc.queryForObject("SELECT user_id FROM app_user WHERE store_id=? AND role_id=(SELECT role_id FROM role WHERE code='MERCHANDISER')",Integer.class,store);
        jdbc.update("INSERT INTO utility_snapshot(store_id,created_by,range_start,range_end,as_of_date,config_snapshot,source_hash,matrix_hash,total_weighted_utility,rounding_scale,status) VALUES (?,?,DATE '2026-10-01',DATE '2026-10-31',DATE '2026-10-19',?,?,?,16220,6,'READY')",store,actor,oldConfig,"a".repeat(64),"b".repeat(64));
        int id=jdbc.queryForObject("SELECT MAX(snapshot_id) FROM utility_snapshot WHERE store_id=?",Integer.class,store);
        jdbc.update("INSERT INTO utility_matrix_item(snapshot_id,transaction_id,product_id,quantity,base_utility,stock_score,expiry_score,seasonal_score,expiry_status,weight_value,weighted_utility) VALUES (?,?,?,3,12000,0.8,0.166666666666666667,0.9,'AVAILABLE',1.351666666666666667,16220)",id,tx,product);
        String original=jdbc.queryForObject("SELECT config_snapshot FROM utility_snapshot WHERE snapshot_id=?",String.class,id);
        updateThreshold("3").andExpect(status().isOk());
        mvc.perform(get("/api/utility/snapshots/{id}",id).header("Authorization","Bearer "+merchant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.configuration.config.scoringVersion").value("F03_LINEAR_V1"));
        mvc.perform(get("/api/utility/snapshots/{id}/matrix",id).header("Authorization","Bearer "+merchant))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].weightedUtility").value(16220))
            .andExpect(jsonPath("$.items[0].expiryEvidence").isEmpty());
        assertEquals(original,jdbc.queryForObject("SELECT config_snapshot FROM utility_snapshot WHERE snapshot_id=?",String.class,id));
        assertEquals(0,new BigDecimal("0.166666666666666667").compareTo(jdbc.queryForObject("SELECT expiry_score FROM utility_matrix_item WHERE snapshot_id=?",BigDecimal.class,id)));
    }
    @Test void missingMetadataIsReportedAndCannotBecomeAnEnabledReadySnapshot() throws Exception {
        configure();transaction();batch("missing",null,10,"ACTIVE",0);
        var result=preview();assertFalse(result.path("ready").asBoolean());
        assertEquals("MISSING_METADATA",result.path("items").get(0).path("expiryStatus").asText());
        mvc.perform(post("/api/utility/snapshots").header("Authorization","Bearer "+merchant).contentType(MediaType.APPLICATION_JSON).content(range))
            .andExpect(status().isBadRequest());
    }
    @Test void actualOpenApiShowsThresholdAndIndependentPushContract() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.components.schemas.ProductRequest.properties.expiryPushDays.type").value("integer"))
            .andExpect(jsonPath("$.components.schemas.ProductView.properties.expiryPushDays").exists())
            .andExpect(jsonPath("$.paths['/api/utility/expiry-push-batches'].get").exists())
            .andExpect(jsonPath("$.paths['/api/utility/config/expiry-window'].put.deprecated").value(true));
    }
    @Test void inactiveHistoricalProductWarningIsFrozenEvenWhenExpiryIsNotApplicable() throws Exception {
        configure();transaction();
        jdbc.update("UPDATE product SET status='INACTIVE',requires_expiry=FALSE WHERE product_id=?",product);
        assertTrue(preview().path("warnings").toString().contains("inactive"));
        JsonNode created=json.readTree(mvc.perform(post("/api/utility/snapshots").header("Authorization","Bearer "+merchant)
            .contentType(MediaType.APPLICATION_JSON).content(range)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertTrue(created.path("warnings").toString().contains("inactive"));
        jdbc.update("UPDATE product SET status='ACTIVE' WHERE product_id=?",product);
        String matrix=mvc.perform(get("/api/utility/snapshots/{id}/matrix",created.path("snapshotId").asInt()).header("Authorization","Bearer "+merchant))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(json.readTree(matrix).path("warnings").toString().contains("inactive"));
    }
    JsonNode preview() throws Exception {
        return json.readTree(mvc.perform(post("/api/utility/preview").header("Authorization","Bearer "+merchant)
            .contentType(MediaType.APPLICATION_JSON).content(range)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    void configure() throws Exception {
        jdbc.update("UPDATE product SET stock_priority_score=0.8,seasonal_priority_score=0.9 WHERE product_id=?",product);
        mvc.perform(put("/api/utility/config").header("Authorization","Bearer "+merchant).contentType(MediaType.APPLICATION_JSON)
            .content("{\"enableStock\":true,\"enableExpiry\":true,\"enableSeason\":true,\"alpha\":0.5,\"beta\":0.2,\"gamma\":0.3,\"lambda\":0.5}"))
            .andExpect(status().isOk());
    }
    org.springframework.test.web.servlet.ResultActions updateThreshold(String days) throws Exception {
        return mvc.perform(put("/api/products/{id}",product).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(productBody(days)));
    }
    String productBody(String days) {
        return "{\"sku\":\"MILK\",\"name\":\"Milk\",\"unit\":\"bottle\",\"requiresExpiry\":true,\"sellingPrice\":100,\"costPrice\":20,\"status\":\"ACTIVE\",\"expiryPushDays\":"+days+"}";
    }
    int store() {jdbc.update("INSERT INTO store(name) VALUES (?)","V12 "+UUID.randomUUID());return jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);}
    String token(String role,Integer storeId) {
        var user=users.createPendingUser("V12",UUID.randomUUID()+"@example.test",role,storeId,"unused-test-hash",null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",user.id());
        return auth.token(users.findById(user.id())).accessToken();
    }
    int batch(String code,LocalDate expiry,int quantity,String status,int receivedDaysAgo) {
        jdbc.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,expiry_date,initial_quantity,remaining_quantity,status) VALUES (?,?,?,?,?,?,?)",product,code,Timestamp.from(day.minusDays(receivedDaysAgo).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant()),expiry,Math.max(1,quantity),quantity,status);
        return jdbc.queryForObject("SELECT batch_id FROM inventory_batch WHERE product_id=? AND batch_code=?",Integer.class,product,code);
    }
    int transaction() {
        jdbc.update("INSERT INTO sales_transaction(store_id,source_system,source_order_id,occurred_at,status,f03_eligible,total_amount,content_sha256) VALUES (?,'V12_TEST',?,?,'COMPLETED',TRUE,30000,?)",store,UUID.randomUUID().toString(),Timestamp.from(day.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant()),"a".repeat(64));
        int tx=jdbc.queryForObject("SELECT MAX(transaction_id) FROM sales_transaction WHERE store_id=?",Integer.class,store);
        jdbc.update("INSERT INTO transaction_item(store_id,transaction_id,product_id,quantity,unit_price,unit_cost) VALUES (?,?,?,3,10000,6000)",store,tx,product);return tx;
    }
    void export(String name,JsonNode value) throws Exception {
        String directory=System.getProperty("paspm.v12.export-dir");
        if(directory!=null) {
            var path=java.nio.file.Path.of(directory);java.nio.file.Files.createDirectories(path);
            json.writerWithDefaultPrettyPrinter().writeValue(path.resolve(name+".json").toFile(),value);
        }
    }
    void decimal(String expected,JsonNode actual) {assertEquals(0,new BigDecimal(expected).compareTo(actual.decimalValue()));}
}

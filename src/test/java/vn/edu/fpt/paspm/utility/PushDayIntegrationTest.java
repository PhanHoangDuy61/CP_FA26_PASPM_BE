package vn.edu.fpt.paspm.utility;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
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

/** Replaces the v1.2 binary-threshold test: Q1 push day per batch (F02), product expiryPushDays removed (DEC-20261116-02), W2 expiry-push lists. */
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:pushday;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true",
    "paspm.jwt-secret=test-only-jwt-key-with-at-least-thirty-two-bytes",
    "paspm.otp-secret=test-only-otp-key-with-at-least-thirty-two-bytes",
    "paspm.avatar.directory=/tmp/paspm-pushday-test-avatars",
    "paspm.bootstrap-email=","paspm.bootstrap-password=","paspm.store-time-zone=Asia/Ho_Chi_Minh"
})
@AutoConfigureMockMvc
class PushDayIntegrationTest {
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
        jdbc.update("UPDATE system_configuration SET config_value=? WHERE config_key='UTILITY_LAMBDA_POLICY'","{\"scoringVersion\":\"W2_EXPIRY_SEASON_V1\",\"lambdaMax\":0.5}");
        product=json.readTree(mvc.perform(post("/api/products").header("Authorization","Bearer "+admin)
            .contentType(MediaType.APPLICATION_JSON).content(productBody(null)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asInt();
    }

    @Test void storeAdminSetsPushDayOnCreateAndPatchAndOnlyStoreAdminMayWrite() throws Exception {
        var created=body(batchPost(admin,"{\"receivedAt\":\"2026-10-01T00:00:00Z\",\"expiryDate\":\"2026-12-10\",\"initialQuantity\":5,\"pushDay\":\"2026-12-08\"}").andExpect(status().isCreated()));
        assertEquals("BATCH-20261001-001",created.path("batchCode").asText());
        assertEquals("2026-12-08",created.path("pushDay").asText());
        int id=created.path("id").asInt();
        batchPost(admin,"{\"receivedAt\":\"2026-10-01T00:00:00Z\",\"expiryDate\":\"2026-12-10\",\"initialQuantity\":5,\"pushDay\":\"2026-12-11\"}").andExpect(status().isBadRequest());
        var none=body(batchPost(admin,"{\"receivedAt\":\"2026-10-01T00:00:00Z\",\"expiryDate\":\"2026-12-10\",\"initialQuantity\":5}").andExpect(status().isCreated()));
        assertEquals("BATCH-20261001-002",none.path("batchCode").asText());
        assertTrue(none.path("pushDay").isNull());
        batchPatch(admin,none.path("id").asInt(),"{\"pushDay\":\"2026-12-01\"}").andExpect(status().isOk()).andExpect(jsonPath("$.pushDay").value("2026-12-01"))
            .andExpect(jsonPath("$.remainingQuantity").value(5));
        batchPatch(admin,id,"{\"pushDay\":\"2026-12-31\"}").andExpect(status().isBadRequest());
        batchPatch(admin,id,"{}").andExpect(status().isBadRequest());
        batchPatch(merchant,id,"{\"pushDay\":\"2026-12-01\"}").andExpect(status().isForbidden());
        batchPatch(foreign,id,"{\"pushDay\":\"2026-12-01\"}").andExpect(status().isNotFound());
        batchPost(merchant,"{\"receivedAt\":\"2026-10-01T00:00:00Z\",\"expiryDate\":\"2026-12-10\",\"initialQuantity\":5,\"pushDay\":\"2026-12-01\"}").andExpect(status().isForbidden());
        mvc.perform(get("/api/inventory-batches/{id}",id).header("Authorization","Bearer "+admin)).andExpect(status().isOk()).andExpect(jsonPath("$.pushDay").value("2026-12-08"));
        mvc.perform(get("/api/products/{id}/batches",product).header("Authorization","Bearer "+admin)).andExpect(status().isOk()).andExpect(jsonPath("$[0].pushDay").exists());
    }

    @Test void productExpiryPushDaysIsRejectedAndScoringUsesBatchPushDays() throws Exception {
        // Removed from the product contract on 2026-11-16 (DEC-20261116-02): any value, even null, is an unknown field.
        for(String value:new String[]{"3","null","0","-1"})updateThreshold(value).andExpect(status().isBadRequest());
        mvc.perform(put("/api/products/{id}",product).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(productBody(null)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.expiryPushDays").doesNotExist());
        configure();transaction(30);
        batch("SOON",day.plusDays(20),day.plusDays(1),10,"ACTIVE");
        var before=preview().path("items").get(0).path("weightValue").decimalValue();
        updateThreshold("99").andExpect(status().isBadRequest());
        assertEquals(0,before.compareTo(preview().path("items").get(0).path("weightValue").decimalValue()),"a rejected request changes nothing");
    }

    @Test void expiryPushListsUsePushDaysWarnForMissingOnesAndKeepRbac() throws Exception {
        int due=batch("DUE",day.plusDays(10),day,4,"ACTIVE");
        int passed=batch("PASSED",day.plusDays(5),day.minusDays(2),6,"ACTIVE");
        batch("LATER",day.plusDays(30),day.plusDays(9),7,"ACTIVE");
        int missing=batch("MISSING",day.plusDays(30),null,8,"ACTIVE");
        batch("EXPIRED",day,day.minusDays(1),9,"ACTIVE");
        batch("BLOCKED",day.plusDays(9),day,9,"BLOCKED");
        var page=body(mvc.perform(get("/api/utility/expiry-push-batches").param("asOfDate",day.toString()).header("Authorization","Bearer "+merchant)).andExpect(status().isOk()));
        assertEquals(2,page.path("totalElements").asInt());
        assertEquals(passed,page.path("items").get(0).path("batchId").asInt());assertEquals(-2,page.path("items").get(0).path("daysToPush").asInt());
        assertEquals(due,page.path("items").get(1).path("batchId").asInt());
        assertTrue(page.path("warnings").toString().contains("["+missing+"]"));
        mvc.perform(get("/api/utility/expiry-push-batches").param("productId",Integer.toString(product)).header("Authorization","Bearer "+foreign)).andExpect(status().isNotFound());
        mvc.perform(get("/api/utility/expiry-push-batches").header("Authorization","Bearer "+foreign)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/utility/expiry-push-batches")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/utility/expiry-push-batches").header("Authorization","Bearer "+system)).andExpect(status().isForbidden());
        mvc.perform(get("/api/utility/expiry-push-batches").param("pageSize","0").header("Authorization","Bearer "+merchant)).andExpect(status().isBadRequest());
        // F06 uses the Store-local today: a batch whose push day is today is due.
        var today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        jdbc.update("DELETE FROM inventory_batch WHERE product_id=?",product);
        int now=batch("NOW",today.plusDays(3),today,3,"ACTIVE");
        mvc.perform(get("/api/recommendations/expiry-push").header("Authorization","Bearer "+merchant)).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].batchId").value(now)).andExpect(jsonPath("$.items[0].pushDay").value(today.toString()))
            .andExpect(jsonPath("$.items[0].pushRequired").value(true));
    }

    @Test void openApiDropsTheRemovedFieldsAndRoutesAndPublishesPushDay() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.components.schemas.ProductRequest.properties.expiryPushDays").doesNotExist())
            .andExpect(jsonPath("$.components.schemas.ProductView.properties.expiryPushDays").doesNotExist())
            .andExpect(jsonPath("$.components.schemas.BatchCreateRequest.properties.pushDay").exists())
            .andExpect(jsonPath("$.components.schemas.BatchPatchRequest.properties.pushDay").exists())
            .andExpect(jsonPath("$.components.schemas.BatchView.properties.pushDay").exists())
            .andExpect(jsonPath("$.paths['/api/inventory-batches/{id}/push-day'].delete").exists())
            .andExpect(jsonPath("$.components.schemas.RecView.properties.seasonEnded").exists())
            .andExpect(jsonPath("$.components.schemas.RecView.properties.endedSeasons").exists())
            .andExpect(jsonPath("$.paths['/api/utility/products/{productId}/scores'].get").exists())
            .andExpect(jsonPath("$.paths['/api/utility/products/{productId}/scores'].patch").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/utility/config/expiry-window']").doesNotExist());
    }

    @Test void inactiveHistoricalProductWarningIsFrozenEvenWhenExpiryIsNotApplicable() throws Exception {
        configure();transaction(3);
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

    @Test void clearPushDayIsIdempotentScopedAndWarnsOnNextPreview() throws Exception {
        configure();transaction(3);
        int id=batch("CLEAR",day.plusDays(20),day.plusDays(1),5,"ACTIVE");
        String path="/api/inventory-batches/"+id+"/push-day";
        batchPatch(admin,id,"{\"pushDay\":null,\"status\":\"ACTIVE\"}").andExpect(status().isOk()).andExpect(jsonPath("$.pushDay").value(day.plusDays(1).toString()));
        for(String who:new String[]{merchant,system})mvc.perform(delete(path).header("Authorization","Bearer "+who)).andExpect(status().isForbidden());
        mvc.perform(delete(path).header("Authorization","Bearer "+foreign)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/inventory-batches/2147483647/push-day").header("Authorization","Bearer "+admin)).andExpect(status().isNotFound());
        mvc.perform(delete(path)).andExpect(status().isUnauthorized());
        for(int i=0;i<2;i++) {
            mvc.perform(delete(path).header("Authorization","Bearer "+admin)).andExpect(status().isOk()).andExpect(jsonPath("$.pushDay").isEmpty()).andExpect(jsonPath("$.remainingQuantity").value(5));
            mvc.perform(get("/api/inventory-batches/"+id).header("Authorization","Bearer "+admin)).andExpect(status().isOk()).andExpect(jsonPath("$.pushDay").isEmpty());
        }
        assertNull(jdbc.queryForObject("SELECT push_day FROM inventory_batch WHERE batch_id=?",LocalDate.class,id));
        assertTrue(preview().path("items").get(0).path("expiryEvidence").path("warnings").toString().contains("MISSING_PUSH_DAY"));
    }
    @Test void missingPushDayAndZeroSalesBothWarnInScoresPreviewAndFrozenSnapshot() throws Exception {
        configure();transaction(3);
        jdbc.update("UPDATE sales_transaction SET occurred_at='2026-01-01 00:00:00' WHERE store_id=?",store);
        batch("NO-PUSH",day.plusDays(365),null,5,"ACTIVE");
        var scores=body(mvc.perform(get("/api/utility/products/"+product+"/scores").header("Authorization","Bearer "+merchant)).andExpect(status().isOk()));
        assertTrue(scores.path("warnings").toString().contains("MISSING_PUSH_DAY"));
        assertTrue(scores.path("warnings").toString().contains("SLOW_MOVING_NO_RECENT_SALES"));
        String oldRange=range.replace("2026-10-01","2026-01-01");
        var preview=body(mvc.perform(post("/api/utility/preview").header("Authorization","Bearer "+merchant).contentType(MediaType.APPLICATION_JSON).content(oldRange)).andExpect(status().isOk()));
        assertTrue(preview.path("items").get(0).path("expiryEvidence").path("warnings").toString().contains("MISSING_PUSH_DAY"));
        assertTrue(preview.path("items").get(0).path("expiryEvidence").path("warnings").toString().contains("SLOW_MOVING_NO_RECENT_SALES"));
        var snap=body(mvc.perform(post("/api/utility/snapshots").header("Authorization","Bearer "+merchant).contentType(MediaType.APPLICATION_JSON).content(oldRange)).andExpect(status().isCreated()));
        String evidence=jdbc.queryForObject("SELECT expiry_evidence FROM utility_matrix_item WHERE snapshot_id=?",String.class,snap.path("snapshotId").asInt());
        assertTrue(evidence.contains("MISSING_PUSH_DAY"));assertTrue(evidence.contains("SLOW_MOVING_NO_RECENT_SALES"));
    }
    @Test void unsupportedStoredConfigVersionReturnsCodedError() throws Exception {
        jdbc.update("UPDATE store SET utility_config=? WHERE store_id=?","{\"scoringVersion\":\"FUTURE_UNKNOWN\"}",store);
        mvc.perform(get("/api/utility/config").header("Authorization","Bearer "+merchant)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UNSUPPORTED_SCORING_VERSION")).andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("FUTURE_UNKNOWN")));
    }
    @Test void corruptSnapshotVersionReturnsCodedErrorWithoutChangingStoredJson() throws Exception {
        configure();transaction(3);
        var snap=body(mvc.perform(post("/api/utility/snapshots").header("Authorization","Bearer "+merchant).contentType(MediaType.APPLICATION_JSON).content(range)).andExpect(status().isCreated()));
        int id=snap.path("snapshotId").asInt();
        String corrupted="{\"config\":{\"scoringVersion\":42}}";
        jdbc.update("UPDATE utility_snapshot SET config_snapshot=? WHERE snapshot_id=?",corrupted,id);
        String before=jdbc.queryForObject("SELECT config_snapshot FROM utility_snapshot WHERE snapshot_id=?",String.class,id);
        mvc.perform(get("/api/utility/snapshots/"+id).header("Authorization","Bearer "+merchant)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UNSUPPORTED_SCORING_VERSION")).andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("42")));
        assertEquals(before,jdbc.queryForObject("SELECT config_snapshot FROM utility_snapshot WHERE snapshot_id=?",String.class,id));
    }

    JsonNode body(org.springframework.test.web.servlet.ResultActions r) throws Exception { return json.readTree(r.andReturn().getResponse().getContentAsString()); }
    org.springframework.test.web.servlet.ResultActions batchPost(String token,String body) throws Exception {
        return mvc.perform(post("/api/products/{id}/batches",product).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body));
    }
    org.springframework.test.web.servlet.ResultActions batchPatch(String token,int id,String body) throws Exception {
        return mvc.perform(patch("/api/inventory-batches/{id}",id).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body));
    }
    JsonNode preview() throws Exception {
        return json.readTree(mvc.perform(post("/api/utility/preview").header("Authorization","Bearer "+merchant)
            .contentType(MediaType.APPLICATION_JSON).content(range)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    void configure() throws Exception {
        mvc.perform(put("/api/utility/config").header("Authorization","Bearer "+merchant).contentType(MediaType.APPLICATION_JSON)
            .content("{\"alpha\":0.6,\"beta\":0.4,\"lambda\":0.5}")).andExpect(status().isOk());
    }
    void transaction(int quantity) {
        jdbc.update("INSERT INTO sales_transaction(store_id,source_system,source_order_id,occurred_at,status,f03_eligible,total_amount,content_sha256) VALUES (?,'TEST',?,?,'COMPLETED',TRUE,1,?)",
            store,UUID.randomUUID().toString(),Timestamp.from(day.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant()),"a".repeat(64));
        int tx=jdbc.queryForObject("SELECT MAX(transaction_id) FROM sales_transaction",Integer.class);
        jdbc.update("INSERT INTO transaction_item(store_id,transaction_id,product_id,quantity,unit_price,unit_cost) VALUES (?,?,?,?,100,20)",store,tx,product,quantity);
    }
    org.springframework.test.web.servlet.ResultActions updateThreshold(String days) throws Exception {
        return mvc.perform(put("/api/products/{id}",product).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(productBody(days)));
    }
    /** @param days null for the current contract; any other value adds the removed legacy field. */
    String productBody(String days) {
        return "{\"name\":\"Milk\",\"unit\":\"bottle\",\"requiresExpiry\":true,\"sellingPrice\":100,\"costPrice\":20,\"status\":\"ACTIVE\""+(days==null?"":",\"expiryPushDays\":"+days)+"}";
    }
    int store() {jdbc.update("INSERT INTO store(name) VALUES (?)","PushDay "+UUID.randomUUID());return jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);}
    String token(String role,Integer storeId) {
        var user=users.createPendingUser("PushDay",UUID.randomUUID()+"@example.test",role,storeId,"unused-test-hash",null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",user.id());
        return auth.token(users.findById(user.id())).accessToken();
    }
    int batch(String code,LocalDate expiry,LocalDate pushDay,int quantity,String status) {
        jdbc.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,expiry_date,initial_quantity,remaining_quantity,status,push_day) VALUES (?,?,?,?,?,?,?,?)",product,code,
            Timestamp.from(day.minusDays(5).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant()),expiry,quantity,quantity,status,pushDay);
        return jdbc.queryForObject("SELECT batch_id FROM inventory_batch WHERE product_id=? AND batch_code=?",Integer.class,product,code);
    }
}

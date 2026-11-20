package vn.edu.fpt.paspm.utility;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
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
import org.springframework.test.web.servlet.ResultActions;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:retailutility;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
    "paspm.jwt-secret=test-only-jwt-key-with-at-least-thirty-two-bytes",
    "paspm.otp-secret=test-only-otp-key-with-at-least-thirty-two-bytes",
    "paspm.bootstrap-email=", "paspm.bootstrap-password=",
    "paspm.avatar.directory=/tmp/paspm-test-avatars", "paspm.store-time-zone=Asia/Ho_Chi_Minh"
})
@AutoConfigureMockMvc
class RetailCatalogUtilityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired ObjectMapper json;
    @Autowired UtilityTimeZone zone;
    int store, other, category, foreignCategory, product, foreignProduct;
    String merchant, admin, foreign, system, customer;
    LocalDate today;
    static final String WEIGHTS="""
        {"alpha":0.6,"beta":0.4,"lambda":0.5}
        """;

    @BeforeEach void fixture() throws Exception {
        store=store();other=store();today=zone.today();
        merchant=token("MERCHANDISER",store);admin=token("STORE_ADMIN",store);
        foreign=token("MERCHANDISER",other);system=token("SYSTEM_ADMIN",null);customer=token("END_CUSTOMER",null);
        category=category(store);foreignCategory=category(other);
        product=product(store,category);foreignProduct=product(other,foreignCategory);
        jdbc.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,expiry_date,initial_quantity,remaining_quantity,status,push_day) VALUES (?,?,CURRENT_TIMESTAMP,?,10,10,'ACTIVE',?)",product,UUID.randomUUID().toString(),today.plusDays(5),today.plusDays(2));
        putJson("/api/system/utility/lambda-policy",system,"{\"lambdaMax\":0.5}").andExpect(status().isOk());
        putJson("/api/utility/config",merchant,WEIGHTS).andExpect(status().isOk());
    }

    @Test void merchandiserReadsOnlyTheirStoreCatalogWithFiltersAndPagination() throws Exception {
        getJson("/api/products?search=Priority&categoryId="+category+"&status=ACTIVE&page=0&pageSize=1&sortBy=sku&sortDirection=desc",merchant)
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(product)).andExpect(jsonPath("$.content[0].stockQuantity").value(10));
        getJson("/api/products?status=INACTIVE",merchant).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        getJson("/api/products?categoryId="+foreignCategory,merchant).andExpect(status().isNotFound());
        getJson("/api/products?storeId="+other,merchant).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(product));
        getJson("/api/products/"+product,merchant).andExpect(status().isOk()).andExpect(jsonPath("$.category.id").value(category));
        getJson("/api/categories",merchant).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(category));
        getJson("/api/categories/"+category,merchant).andExpect(status().isOk());
        getJson("/api/products/"+foreignProduct,merchant).andExpect(status().isNotFound());
        getJson("/api/categories/"+foreignCategory,merchant).andExpect(status().isNotFound());
    }

    @Test void catalogReadDoesNotGrantProductCategoryOrInventoryWrites() throws Exception {
        String productBody="{\"name\":\"Denied\",\"unit\":\"bottle\",\"requiresExpiry\":true,\"sellingPrice\":100,\"costPrice\":20,\"status\":\"ACTIVE\"}";
        String categoryBody="{\"name\":\"Denied\",\"status\":\"ACTIVE\"}";
        for(String path:new String[]{"/api/products","/api/categories"}) {
            mvc.perform(post(path).header("Authorization","Bearer "+merchant).contentType(MediaType.APPLICATION_JSON).content(path.endsWith("products")?productBody:categoryBody))
                .andExpect(status().isForbidden());
        }
        for(String path:new String[]{"/api/products/"+product,"/api/categories/"+category}) {
            putJson(path,merchant,path.startsWith("/api/products/")?productBody:categoryBody).andExpect(status().isForbidden());
            mvc.perform(patch(path+"/status").header("Authorization","Bearer "+merchant).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/products/"+product+"/batches").header("Authorization","Bearer "+merchant).contentType(MediaType.APPLICATION_JSON).content("{\"receivedAt\":\"2026-10-01T00:00:00Z\",\"expiryDate\":\"2099-01-01\",\"initialQuantity\":1}"))
            .andExpect(status().isForbidden());
    }

    @Test void catalogRejectsUnauthenticatedAndNonStoreBusinessRoles() throws Exception {
        for(String path:new String[]{"/api/products","/api/products/"+product,"/api/categories","/api/categories/"+category}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            for(String who:new String[]{system,customer})getJson(path,who).andExpect(status().isForbidden());
            getJson(path,admin).andExpect(status().isOk());
        }
    }

    @Test void currentScoresUseTheW2FormulaWithSalesWindowAndActiveSeasons() throws Exception {
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM transaction_item WHERE product_id=?",Integer.class,product));
        JsonNode current=body(getJson(scorePath(),merchant).andExpect(status().isOk())
            .andExpect(jsonPath("$.expiryStatus").value("SLOW_MOVING")).andExpect(jsonPath("$.expiryRisk").value(0))
            .andExpect(jsonPath("$.seasonWeight").value(0)).andExpect(jsonPath("$.weightValue").value(1.0))
            .andExpect(jsonPath("$.ready").value(true)).andExpect(jsonPath("$.lambdaValid").value(true)));
        assertEquals(today.toString(),current.path("asOfDate").asText());
        assertEquals(zone.id(),current.path("timeZone").asText());
        assertEquals("W2_EXPIRY_SEASON_V1",current.path("scoringVersion").asText());
        assertTrue(current.path("warnings").toString().contains("SLOW_MOVING_NO_RECENT_SALES"));
        assertEquals(body(getJson("/api/utility/config",merchant)).path("config").path("version"),current.path("configVersion"));
        int season=season("Today",today.minusDays(1),today,"1");assign(season,product);
        season("Future",today.plusDays(1),today.plusDays(9),"0.2");
        getJson(scorePath(),merchant).andExpect(jsonPath("$.seasonWeight").value(1)).andExpect(jsonPath("$.seasonId").value(season))
            .andExpect(jsonPath("$.activeSeasons.length()").value(1)).andExpect(jsonPath("$.weightValue").value(1.2));
        sale(30,today);sale(99,today.minusDays(30)); // only the 30 days ending today count: v = 30 / 30 = 1 per day
        getJson(scorePath(),merchant).andExpect(jsonPath("$.expiryStatus").value("AVAILABLE"))
            .andExpect(jsonPath("$.expiryRisk").value(0.8)).andExpect(jsonPath("$.expiryEvidence.unitsSold").value(30))
            .andExpect(jsonPath("$.weightValue").value(1.44)); // 1 + 0.5 * (0.6 * 0.8 + 0.4 * 1)
    }

    @Test void scoreReadsAreStoreScopedAndTheDeletedPatchIsAnUnsupportedMethod() throws Exception {
        var inventory=jdbc.queryForList("SELECT * FROM inventory_batch WHERE product_id=?",product);
        getJson(scorePath(),admin).andExpect(status().isOk());
        mvc.perform(get(scorePath())).andExpect(status().isUnauthorized());
        getJson(scorePath(),"invalid").andExpect(status().isUnauthorized());
        getJson(scorePath(),foreign).andExpect(status().isNotFound());
        for(String who:new String[]{system,customer}) {
            getJson(scorePath(),who).andExpect(status().isForbidden());
            scores(who,"{\"stockPriorityScore\":0.1}").andExpect(status().isMethodNotAllowed());
        }
        // PATCH was deleted on 2026-11-16 (DEC-20261116-03): no handler remains, so every authenticated role gets 405.
        scores(admin,"{\"stockPriorityScore\":0.1}").andExpect(status().isMethodNotAllowed());
        scores(merchant,"{\"stockPriorityScore\":0.1}").andExpect(status().isMethodNotAllowed());
        putJson("/api/utility/config",admin,WEIGHTS).andExpect(status().isForbidden());
        putJson("/api/system/utility/lambda-policy",merchant,"{\"lambdaMax\":1}").andExpect(status().isForbidden());
        assertEquals(inventory,jdbc.queryForList("SELECT * FROM inventory_batch WHERE product_id=?",product));
    }

    @Test void sharedExpiryRulesForUnsellableMissingPushDayAndNonExpiryProducts() throws Exception {
        int season=season("All",today.minusDays(1),today.plusDays(1),"0.5");assign(season,product);
        for(String change:new String[]{"status='BLOCKED'","status='ACTIVE',remaining_quantity=0","remaining_quantity=10,push_day='"+today.minusDays(1)+"',expiry_date='"+today+"'"}) {
            jdbc.update("UPDATE inventory_batch SET "+change+" WHERE product_id=?",product);
            getJson(scorePath(),merchant).andExpect(status().isOk()).andExpect(jsonPath("$.expiryStatus").value("NO_SELLABLE_BATCH"))
                .andExpect(jsonPath("$.expiryRisk").value(0)).andExpect(jsonPath("$.weightValue").value(1.1));
        }
        jdbc.update("UPDATE inventory_batch SET expiry_date=?,push_day=NULL WHERE product_id=?",today.plusDays(5),product);
        int batch=jdbc.queryForObject("SELECT batch_id FROM inventory_batch WHERE product_id=?",Integer.class,product);
        getJson(scorePath(),merchant).andExpect(jsonPath("$.expiryStatus").value("MISSING_PUSH_DAY"))
            .andExpect(jsonPath("$.batchesMissingPushDay[0]").value(batch)).andExpect(jsonPath("$.ready").value(true));
        jdbc.update("UPDATE product SET requires_expiry=FALSE WHERE product_id=?",product);
        getJson(scorePath(),merchant).andExpect(jsonPath("$.expiryStatus").value("NOT_APPLICABLE")).andExpect(jsonPath("$.expiryRisk").value(0));
        jdbc.update("UPDATE product SET requires_expiry=TRUE,status='INACTIVE' WHERE product_id=?",product);
        getJson(scorePath(),merchant).andExpect(jsonPath("$.expiryStatus").value("NO_SELLABLE_BATCH"));
    }

    @Test void policyChangesInvalidateCurrentWeightWithoutClampingStoredLambda() throws Exception {
        putJson("/api/system/utility/lambda-policy",system,"{\"lambdaMax\":0.1}").andExpect(status().isOk());
        JsonNode result=body(getJson(scorePath(),merchant).andExpect(status().isOk())
            .andExpect(jsonPath("$.lambdaValid").value(false)).andExpect(jsonPath("$.ready").value(false)));
        assertTrue(result.has("weightValue")&&result.path("weightValue").isNull());
        assertFalse(result.path("warnings").isEmpty());
        getJson("/api/utility/config",merchant).andExpect(jsonPath("$.config.lambda").value(0.5));
        putJson("/api/utility/config",merchant,WEIGHTS).andExpect(status().isBadRequest());
        putJson("/api/utility/config",merchant,WEIGHTS.replace("\"lambda\":0.5","\"lambda\":0.1")).andExpect(status().isOk());
        getJson(scorePath(),merchant).andExpect(jsonPath("$.ready").value(true)).andExpect(jsonPath("$.weightValue").value(1.0));
    }

    @Test void openApiDescribesTheW2ScoreView() throws Exception {
        JsonNode schemas=body(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())).path("components").path("schemas");
        JsonNode fields=schemas.path("Scores").path("properties");
        for(String field:new String[]{"expiryRisk","seasonWeight","seasonId","weightValue","expiryStatus","expiryEvidence","batchesMissingPushDay","activeSeasons","asOfDate","timeZone","configVersion","scoringVersion","lambdaValid","ready","warnings"})
            assertTrue(fields.has(field),field);
        for(String removed:new String[]{"stockPriorityScore","seasonalPriorityScore","expiryPushDays","expiryScore"})
            assertFalse(fields.has(removed),removed);
        assertTrue(fields.path("weightValue").path("type").toString().contains("null"));
        assertTrue(schemas.path("ScorePatch").isMissingNode());
    }

    int season(String name,LocalDate start,LocalDate end,String weight) throws Exception {
        return body(mvc.perform(post("/api/utility/seasons").header("Authorization","Bearer "+merchant).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\""+name+"\",\"startDate\":\""+start+"\",\"endDate\":\""+end+"\",\"weight\":"+weight+"}")).andExpect(status().isCreated())).path("seasonId").asInt();
    }
    void assign(int season,int productId) throws Exception {
        mvc.perform(put("/api/utility/seasons/"+season+"/products/"+productId).header("Authorization","Bearer "+merchant)).andExpect(status().isCreated());
    }
    void sale(int quantity,LocalDate date) {
        jdbc.update("INSERT INTO sales_transaction(store_id,source_system,source_order_id,occurred_at,status,f03_eligible,total_amount,content_sha256) VALUES (?,'TEST',?,?,'COMPLETED',TRUE,1,?)",
            store,UUID.randomUUID().toString(),java.sql.Timestamp.from(date.atTime(12,0).atZone(java.time.ZoneId.of(zone.id())).toInstant()),"a".repeat(64));
        int tx=jdbc.queryForObject("SELECT MAX(transaction_id) FROM sales_transaction",Integer.class);
        jdbc.update("INSERT INTO transaction_item(store_id,transaction_id,product_id,quantity,unit_price,unit_cost) VALUES (?,?,?,?,10,5)",store,tx,product,quantity);
    }

    ResultActions getJson(String path,String token) throws Exception {return mvc.perform(get(path).header("Authorization","Bearer "+token));}
    ResultActions putJson(String path,String token,String body) throws Exception {return mvc.perform(put(path).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body));}
    ResultActions scores(String token,String body) throws Exception {return mvc.perform(patch("/api/utility/products/"+product+"/scores").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body));}
    String scorePath(){return "/api/utility/products/"+product+"/scores";}
    JsonNode body(ResultActions result) throws Exception{return json.readTree(result.andReturn().getResponse().getContentAsString());}
    int store(){jdbc.update("INSERT INTO store(name) VALUES (?)","Retail "+UUID.randomUUID());return jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);}
    int category(int owner){jdbc.update("INSERT INTO category(store_id,name,status) VALUES (?,?,'ACTIVE')",owner,"Dairy "+UUID.randomUUID());return jdbc.queryForObject("SELECT MAX(category_id) FROM category",Integer.class);}
    int product(int owner,int cat){jdbc.update("INSERT INTO product(store_id,category_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,?,'Priority Milk','bottle',TRUE,100,20,'ACTIVE')",owner,cat,UUID.randomUUID().toString());return jdbc.queryForObject("SELECT MAX(product_id) FROM product",Integer.class);}
    String token(String role,Integer owner){var user=users.createPendingUser("Retail QA",UUID.randomUUID()+"@example.test",role,owner,"unused-test-hash",null);jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",user.id());return auth.token(users.findById(user.id())).accessToken();}
}

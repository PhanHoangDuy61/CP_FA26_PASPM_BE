package vn.edu.fpt.paspm.recommendation;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.util.UUID;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;
import vn.edu.fpt.paspm.utility.UtilityTimeZone;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f06expiry;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true","paspm.jwt-secret=test-only-jwt-key-with-at-least-thirty-two-bytes","paspm.otp-secret=test-only-otp-key-with-at-least-thirty-two-bytes","paspm.avatar.directory=/tmp/paspm-test-avatars","paspm.bootstrap-email=","paspm.bootstrap-password=","paspm.store-time-zone=Asia/Ho_Chi_Minh"})
@AutoConfigureMockMvc
class RecommendationExpiryIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired UserRepository users;
    @Autowired AuthService auth; @Autowired ObjectMapper json; @Autowired UtilityTimeZone zone;
    int store,other; String admin,merchant,foreign,system,customer; LocalDate day;
    @BeforeEach void setup() {
        store=store(); other=store(); day=zone.today(); admin=token("STORE_ADMIN",store);
        merchant=token("MERCHANDISER",store); foreign=token("MERCHANDISER",other);
        system=token("SYSTEM_ADMIN",null);customer=token("END_CUSTOMER",null);
    }
    @Test void noMiningNeededAndReadDoesNotMutateData() throws Exception {
        int p=product(7,true,"ACTIVE"); int b=batch(p,2,8,"ACTIVE",0);
        var before=jdbc.queryForList("SELECT * FROM inventory_batch WHERE product_id=?",p);
        var frozen=freeze();
        var productBefore=jdbc.queryForList("SELECT * FROM product WHERE product_id=?",p);
        JsonNode result=read(merchant,null);
        assertEquals(1,result.path("totalElements").asInt()); assertEquals(b,result.path("items").get(0).path("batchId").asInt());
        assertEquals(day.toString(),result.path("asOfDate").asText());assertEquals(zone.id(),result.path("timeZone").asText());
        Instant.parse(result.path("checkedAt").asText());
        assertEquals(result.path("checkedAt"),result.path("items").get(0).path("checkedAt"));
        assertEquals(before,jdbc.queryForList("SELECT * FROM inventory_batch WHERE product_id=?",p));
        assertEquals(frozen,freeze());
        assertEquals(productBefore,jdbc.queryForList("SELECT * FROM product WHERE product_id=?",p));
        read(admin,null);
    }
    @Test void thresholdsAndCalendarBoundariesFilterCurrentSellableBatches() throws Exception {
        for(int t:new int[]{0,3,7}) {
            int p=product(t,true,"ACTIVE");
            batch(p,-1,5,"ACTIVE",0);batch(p,0,5,"ACTIVE",0);batch(p,1,0,"ACTIVE",0);batch(p,1,5,"BLOCKED",0);
            if(t>0)batch(p,t,5,"ACTIVE",0);batch(p,t+1,5,"ACTIVE",0);
            var result=read(merchant,p);assertEquals(t==0?0:1,result.path("totalElements").asInt());
            if(t>0){var item=result.path("items").get(0);assertEquals(t,item.path("daysRemaining").asInt());assertTrue(item.path("pushRequired").asBoolean());assertEquals(t,item.path("expiryPushDays").asInt());}
        }
        int non=product(7,false,"ACTIVE"),inactive=product(7,true,"INACTIVE");batch(non,1,3,"ACTIVE",0);batch(inactive,1,3,"ACTIVE",0);
        assertEquals(0,read(merchant,non).path("totalElements").asInt());assertEquals(0,read(merchant,inactive).path("totalElements").asInt());
    }
    @Test void fefoAcrossProductsAndPaginationIgnoreReceiptOrder() throws Exception {
        int a=product(7,true,"ACTIVE"),b=product(7,true,"ACTIVE");
        batch(a,7,5,"ACTIVE",100);int first=batch(b,1,3,"ACTIVE",0);batch(a,3,4,"ACTIVE",10);
        var all=read(merchant,null);assertEquals(3,all.path("totalElements").asInt());assertEquals(first,all.path("items").get(0).path("batchId").asInt());
        var page=json.readTree(mvc.perform(request(merchant).param("page","1").param("pageSize","1")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1,page.path("items").size());assertEquals(all.path("items").get(1).path("batchId"),page.path("items").get(0).path("batchId"));
    }
    @Test void missingMetadataWarningsSurviveWithKnownPushBatches() throws Exception {
        int p=product(7,true,"ACTIVE");batch(p,2,4,"ACTIVE",0);batch(p,null,4,"ACTIVE",0);
        var result=read(merchant,p);assertEquals(1,result.path("totalElements").asInt());assertFalse(result.path("warnings").isEmpty());
        assertEquals("MISSING_METADATA",result.path("items").get(0).path("status").asText());
        assertEquals(result.path("warnings"),result.path("items").get(0).path("warnings"));
        int missingOnly=product(7,true,"ACTIVE");batch(missingOnly,null,3,"ACTIVE",0);
        var missing=read(merchant,missingOnly);assertEquals(0,missing.path("totalElements").asInt());assertFalse(missing.path("warnings").isEmpty());
    }
    @Test void accessAndCurrentOnlyValidation() throws Exception {
        int p=product(7,true,"ACTIVE");
        mvc.perform(get("/api/recommendations/expiry-push")).andExpect(status().isUnauthorized());
        mvc.perform(request("invalid")).andExpect(status().isUnauthorized());
        mvc.perform(request(system)).andExpect(status().isForbidden());mvc.perform(request(customer)).andExpect(status().isForbidden());
        mvc.perform(request(foreign).param("productId",Integer.toString(p))).andExpect(status().isNotFound());
        for(String[] query:new String[][]{{"page","-1"},{"pageSize","0"},{"pageSize","101"},{"asOfDate",day.toString()}})
            mvc.perform(request(merchant).param(query[0],query[1])).andExpect(status().isBadRequest());
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String token){return get("/api/recommendations/expiry-push").header("Authorization","Bearer "+token);}
    JsonNode read(String token,Integer p)throws Exception {var r=request(token);if(p!=null)r.param("productId",p.toString());return json.readTree(mvc.perform(r).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    java.util.Map<String,java.util.List<java.util.Map<String,Object>>> freeze(){
        var result=new java.util.LinkedHashMap<String,java.util.List<java.util.Map<String,Object>>>();
        for(String table:new String[]{"utility_snapshot","utility_matrix_item","mining_request","mining_run","high_utility_pattern","pattern_item","sales_transaction","transaction_item"})result.put(table,jdbc.queryForList("SELECT * FROM "+table));
        return result;
    }
    int store(){jdbc.update("INSERT INTO store(name) VALUES (?)","Expiry "+UUID.randomUUID());return jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);}
    String token(String role,Integer storeId){var u=users.createPendingUser("Expiry",UUID.randomUUID()+"@example.test",role,storeId,"unused-test-hash",null);jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",u.id());return auth.token(users.findById(u.id())).accessToken();}
    int product(int t,boolean expiry,String status){String sku=UUID.randomUUID().toString();jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status,expiry_push_days) VALUES (?,?,?,'unit',?,100,20,?,?)",store,sku,"Expiry",expiry,status,t);return jdbc.queryForObject("SELECT product_id FROM product WHERE store_id=? AND sku=?",Integer.class,store,sku);}
    int batch(int p,Integer days,int quantity,String status,int receivedAgo){String code=UUID.randomUUID().toString();jdbc.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,expiry_date,initial_quantity,remaining_quantity,status) VALUES (?,?,?,?,?,?,?)",p,code,java.sql.Timestamp.from(day.minusDays(receivedAgo).atStartOfDay(ZoneId.of(zone.id())).toInstant()),days==null?null:day.plusDays(days),Math.max(1,quantity),quantity,status);return jdbc.queryForObject("SELECT batch_id FROM inventory_batch WHERE product_id=? AND batch_code=?",Integer.class,p,code);}
}

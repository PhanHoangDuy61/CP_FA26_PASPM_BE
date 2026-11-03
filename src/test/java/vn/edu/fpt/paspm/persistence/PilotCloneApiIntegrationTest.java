package vn.edu.fpt.paspm.persistence;

import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Synthetic API fixtures on an authorized copy. Never claims empty source rows as real-data coverage. */
@SpringBootTest(properties={"spring.flyway.enabled=true","paspm.avatar.directory=${java.io.tmpdir}/paspm-jpa-clone-avatars","paspm.bootstrap-email=","paspm.bootstrap-password=",
    "paspm.jwt-secret=pilot-test-signing-key-at-least-thirty-two-characters",
    "paspm.otp-secret=pilot-test-otp-key-at-least-thirty-two-characters"})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="paspm.test.jpa-clone", matches="true")
class PilotCloneApiIntegrationTest {
    @DynamicPropertySource static void target(DynamicPropertyRegistry registry){PilotCloneTarget.register(registry);}
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired ObjectMapper json;
    int store,other,category,otherCategory;
    String admin,foreign,merchant;
    @BeforeEach void fixtures(){
        store=store();other=store();
        jdbc.update("INSERT INTO category(store_id,name,status) VALUES (?,'Synthetic pilot category','ACTIVE')",store);
        category=jdbc.queryForObject("SELECT MAX(category_id) FROM category WHERE store_id=?",Integer.class,store);
        jdbc.update("INSERT INTO category(store_id,name,status) VALUES (?,'Synthetic foreign category','ACTIVE')",other);
        otherCategory=jdbc.queryForObject("SELECT MAX(category_id) FROM category WHERE store_id=?",Integer.class,other);
        admin=token(store,"STORE_ADMIN");foreign=token(other,"STORE_ADMIN");merchant=token(store,"MERCHANDISER");
    }
    private int store(){jdbc.update("INSERT INTO store(name) VALUES (?)","Synthetic JPA pilot "+UUID.randomUUID());return jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);}
    private String token(int owner,String role){var u=users.createPendingUser("Synthetic pilot",UUID.randomUUID()+"@example.test",role,owner,null,null);jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",u.id());return auth.token(users.findById(u.id())).accessToken();}
    private Map<String,Object> body(String sku){var b=new LinkedHashMap<String,Object>();b.put("sku",sku);b.put("name","Synthetic pilot product");b.put("categoryId",category);b.put("unit","each");b.put("requiresExpiry",false);b.put("sellingPrice",new BigDecimal("50.00"));b.put("costPrice",new BigDecimal("20.00"));b.put("status","ACTIVE");return b;}
    private JsonNode create(Map<String,Object> payload)throws Exception{return json.readTree(mvc.perform(post("/api/products").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());}
    @Test void productRoundtripDefaultsAndDatabaseMetadataMatch()throws Exception{
        var payload=body("PILOT-"+UUID.randomUUID());var created=create(payload);int id=created.path("id").asInt();assertEquals(0,created.path("expiryPushDays").asInt());
        mvc.perform(get("/api/products/{id}",id).header("Authorization","Bearer "+admin)).andExpect(status().isOk()).andExpect(content().json(created.toString()));
        assertEquals(payload.get("sku"),jdbc.queryForObject("SELECT sku FROM product WHERE store_id=? AND product_id=?",String.class,store,id));
        assertEquals(0,new BigDecimal("50.00").compareTo(jdbc.queryForObject("SELECT selling_price FROM product WHERE product_id=?",BigDecimal.class,id)));
        mvc.perform(get("/api/products").param("search",payload.get("sku").toString()).param("categoryId",String.valueOf(category)).param("status","ACTIVE").header("Authorization","Bearer "+admin)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }
    @Test void duplicateSkuAndValidationKeepExistingErrorContract()throws Exception{
        var payload=body("PILOT-"+UUID.randomUUID());create(payload);
        mvc.perform(post("/api/products").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Resource already exists or violates a data constraint"));
        payload.put("sku","DIFFERENT-"+UUID.randomUUID());payload.put("sellingPrice",-1);
        mvc.perform(post("/api/products").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isBadRequest());
    }
    @Test void foreignStoreAndRolesCannotModifyProduct()throws Exception{
        var payload=body("PILOT-"+UUID.randomUUID());int id=create(payload).path("id").asInt();
        mvc.perform(get("/api/products/{id}",id).header("Authorization","Bearer "+foreign)).andExpect(status().isNotFound());
        mvc.perform(put("/api/products/{id}",id).header("Authorization","Bearer "+foreign).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/products/{id}/status",id).header("Authorization","Bearer "+foreign).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/products").header("Authorization","Bearer "+merchant).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isForbidden());
        payload.put("categoryId",otherCategory);payload.put("sku","NEW-"+UUID.randomUUID());
        mvc.perform(post("/api/products").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/{id}",id)).andExpect(status().isUnauthorized());
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM product WHERE product_id=?",String.class,id));
    }
    @Test void metadataAndStatusPreserveScoresAndBatchInventory()throws Exception{
        var payload=body("PILOT-"+UUID.randomUUID());int id=create(payload).path("id").asInt();
        var batch=Map.of("batchCode","PILOT-BATCH-"+UUID.randomUUID(),"receivedAt",Instant.now().toString(),"expiryDate",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(5).toString(),"initialQuantity",8);
        var b=json.readTree(mvc.perform(post("/api/products/{id}/batches",id).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(batch))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(patch("/api/utility/products/{id}/scores",id).header("Authorization","Bearer "+merchant).contentType(MediaType.APPLICATION_JSON).content("{\"stockPriorityScore\":0.8,\"seasonalPriorityScore\":0.9}")).andExpect(status().isOk());
        String summary=mvc.perform(get("/api/products/{id}/inventory-summary",id).header("Authorization","Bearer "+admin)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        payload.put("name","Edited metadata");payload.put("sellingPrice",new BigDecimal("75.00"));payload.put("requiresExpiry",true);payload.put("expiryPushDays",7);
        mvc.perform(put("/api/products/{id}",id).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isOk()).andExpect(jsonPath("$.stockQuantity").value(8));
        for(String state:List.of("INACTIVE","ACTIVE"))mvc.perform(patch("/api/products/{id}/status",id).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\""+state+"\"}")).andExpect(status().isOk());
        assertEquals(0,new BigDecimal("0.8").compareTo(jdbc.queryForObject("SELECT stock_priority_score FROM product WHERE product_id=?",BigDecimal.class,id)));
        assertEquals(0,new BigDecimal("0.9").compareTo(jdbc.queryForObject("SELECT seasonal_priority_score FROM product WHERE product_id=?",BigDecimal.class,id)));
        mvc.perform(get("/api/inventory-batches/{id}",b.path("id").asInt()).header("Authorization","Bearer "+admin)).andExpect(status().isOk()).andExpect(content().json(b.toString()));
        mvc.perform(get("/api/products/{id}/inventory-summary",id).header("Authorization","Bearer "+admin)).andExpect(status().isOk()).andExpect(content().json(summary));
    }
    @Test void simulatedFoodStoreKeepsExactInventoryFixtureAfterJpaMetadataWrites() throws Exception {
        jdbc.update("UPDATE category SET name='Simulated dairy and fresh food' WHERE category_id=?",category);
        var milk=body("FOOD-MILK-"+UUID.randomUUID());milk.put("name","Simulated fresh milk");
        milk.put("requiresExpiry",true);milk.put("expiryPushDays",7);
        int id=create(milk).path("id").asInt();
        for (var food:List.of(Map.of("name","Simulated bread","sku","FOOD-BREAD-"),Map.of("name","Simulated coffee","sku","FOOD-COFFEE-"))) {
            var item=body(food.get("sku")+UUID.randomUUID());item.put("name",food.get("name"));create(item);
        }
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        int[] quantities={8,6,10,4,5};int[] days={1,3,30,-1,20};int mutableBatch=0;
        for(int index=0;index<quantities.length;index++) {
            var batch=Map.of("batchCode","SIM-FOOD-"+UUID.randomUUID(),"receivedAt",Instant.now().toString(),
                    "expiryDate",today.plusDays(days[index]).toString(),"initialQuantity",quantities[index]);
            var response=json.readTree(mvc.perform(post("/api/products/{id}/batches",id).header("Authorization","Bearer "+admin)
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(batch)))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
            int batchId=response.path("id").asInt();if(index==0)mutableBatch=batchId;
            if(index==4)mvc.perform(patch("/api/inventory-batches/{id}",batchId).header("Authorization","Bearer "+admin)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BLOCKED\"}")).andExpect(status().isOk());
        }
        assertFoodSummary(id,33,24,14);
        milk.put("name","Simulated fresh milk - metadata edited");milk.put("sellingPrice",new BigDecimal("60.00"));
        mvc.perform(put("/api/products/{id}",id).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(milk))).andExpect(status().isOk()).andExpect(jsonPath("$.stockQuantity").value(33));
        assertFoodSummary(id,33,24,14);
        mvc.perform(patch("/api/inventory-batches/{id}",mutableBatch).header("Authorization","Bearer "+admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"remainingQuantity\":3}")).andExpect(status().isOk());
        assertFoodSummary(id,28,19,9);
    }
    private void assertFoodSummary(int id,int total,int sellable,int soon)throws Exception {
        mvc.perform(get("/api/products/{id}/inventory-summary",id).param("expiringWithinDays","7").header("Authorization","Bearer "+admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalQuantity").value(total))
                .andExpect(jsonPath("$.sellableQuantity").value(sellable)).andExpect(jsonPath("$.expiredQuantity").value(4))
                .andExpect(jsonPath("$.blockedQuantity").value(5)).andExpect(jsonPath("$.expiringSoonQuantity").value(soon));
    }

}

package vn.edu.fpt.paspm.catalog;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Decision D2 (confirmed 2026-11-02): GET /api/system/stores, System Admin only, read-only, paginated, minimal fields. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:systemstorelist;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true","paspm.jwt-secret=01234567890123456789012345678901","paspm.otp-secret=01234567890123456789012345678901","paspm.bootstrap-email=","paspm.bootstrap-password=","paspm.avatar.directory=/tmp/paspm-storelist-avatars"})
@AutoConfigureMockMvc
class SystemStoreListHttpTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    String token(String role, Integer store) {
        var user=users.createPendingUser("Store list",UUID.randomUUID()+"@example.test",role,store,null,null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",user.id());
        return auth.token(users.findById(user.id())).accessToken();
    }
    int store(String name){ jdbc.update("INSERT INTO store(name) VALUES (?)",name); return jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class); }

    @Test void systemAdminListsStoresInDeterministicNameThenIdOrderWithMinimalFieldsOnly() throws Exception {
        jdbc.update("DELETE FROM store WHERE store_id NOT IN (SELECT store_id FROM app_user WHERE store_id IS NOT NULL) AND store_id NOT IN (SELECT store_id FROM product) AND store_id NOT IN (SELECT store_id FROM mining_schedule)");
        String tag="Zlist"+UUID.randomUUID().toString().substring(0,8);
        int b=store(tag+" B"), a2=store(tag+" A"), a1=store(tag+" A");
        String admin="Bearer "+token("SYSTEM_ADMIN",null);
        var body=mvc.perform(get("/api/system/stores?page=0&pageSize=100").header("Authorization",admin)).andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.pageSize").value(100))
            .andExpect(jsonPath("$.items[*].storeId",hasItems(a1,a2,b)))
            .andReturn().getResponse().getContentAsString();
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var items=mapper.readTree(body).path("items");
        var ids=new java.util.ArrayList<Integer>();
        for(var item:items){
            var fields=new java.util.TreeSet<String>();item.fieldNames().forEachRemaining(fields::add);
            org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of("name","storeId"),fields,"only the Store id and name may be returned");
            if(item.path("name").asText().startsWith(tag))ids.add(item.path("storeId").asInt());
        }
        org.junit.jupiter.api.Assertions.assertEquals(List.of(Math.min(a1,a2),Math.max(a1,a2),b),ids,"name ascending, then storeId ascending");
        org.junit.jupiter.api.Assertions.assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM store",Long.class),mapper.readTree(body).path("totalElements").asLong());
    }
    @Test void paginationIsValidatedAndPagesPastTheEndAreEmpty() throws Exception {
        String admin="Bearer "+token("SYSTEM_ADMIN",null);
        store("Pagination "+UUID.randomUUID());
        mvc.perform(get("/api/system/stores?page=0&pageSize=1").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/system/stores?page=100000&pageSize=20").header("Authorization",admin)).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.totalElements",greaterThan(0)));
        mvc.perform(get("/api/system/stores").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.pageSize").value(20));
        for(String bad:List.of("?page=-1","?pageSize=0","?pageSize=101","?page=abc","?pageSize=x"))
            mvc.perform(get("/api/system/stores"+bad).header("Authorization",admin)).andExpect(status().isBadRequest());
    }
    @Test void onlySystemAdminMayCallItAndItNeverWrites() throws Exception {
        int store=store("Rbac "+UUID.randomUUID());
        for(String role:List.of("STORE_ADMIN","MERCHANDISER","END_CUSTOMER"))
            mvc.perform(get("/api/system/stores").header("Authorization","Bearer "+token(role,role.equals("END_CUSTOMER")?null:store))).andExpect(status().isForbidden());
        mvc.perform(get("/api/system/stores")).andExpect(status().isUnauthorized());
        String admin="Bearer "+token("SYSTEM_ADMIN",null);
        for(var method:List.of(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/system/stores"),org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/system/stores"),org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/system/stores"),org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/system/stores")))
            mvc.perform(method.header("Authorization",admin).contentType("application/json").content("{}")).andExpect(status().is4xxClientError());
    }
}

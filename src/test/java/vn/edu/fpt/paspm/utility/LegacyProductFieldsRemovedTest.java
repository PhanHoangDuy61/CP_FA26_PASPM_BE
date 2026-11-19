package vn.edu.fpt.paspm.utility;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;

/** Owner decisions P5/P6 changed on 2026-11-16 (DEC-20261116-02/03): legacy product fields and two routes removed. */
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:legacyremoved;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true",
    "paspm.jwt-secret=test-only-jwt-key-with-at-least-thirty-two-bytes",
    "paspm.otp-secret=test-only-otp-key-with-at-least-thirty-two-bytes",
    "paspm.avatar.directory=${java.io.tmpdir}/paspm-legacy-removed-avatars",
    "paspm.bootstrap-email=","paspm.bootstrap-password=","paspm.store-time-zone=Asia/Ho_Chi_Minh"
})
@AutoConfigureMockMvc
class LegacyProductFieldsRemovedTest {
    static final String[] REMOVED={"expiryPushDays","stockPriorityScore","seasonalPriorityScore"};
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired ObjectMapper json;
    int store,product;
    String admin,merchant;

    @BeforeEach void setup() throws Exception {
        jdbc.update("INSERT INTO store(name) VALUES (?)","Legacy removed "+UUID.randomUUID());
        store=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        admin=token("STORE_ADMIN",store);merchant=token("MERCHANDISER",store);
        product=body(send(post("/api/products"),admin,productBody("LEGACY-"+UUID.randomUUID(),"")).andExpect(status().isCreated())).path("id").asInt();
    }

    @Test void productResponsesNoLongerCarryAnyRemovedField() throws Exception {
        for(JsonNode view:new JsonNode[]{
                body(send(get("/api/products/{id}",product),admin,null).andExpect(status().isOk())),
                body(send(put("/api/products/{id}",product),admin,productBody("LEGACY-U-"+UUID.randomUUID(),"")).andExpect(status().isOk())),
                body(send(get("/api/products"),merchant,null).andExpect(status().isOk())).path("content").get(0)})
            for(String field:REMOVED)assertFalse(view.has(field),field+" must not be in "+view);
        var scores=body(send(get("/api/utility/products/{id}/scores",product),merchant,null).andExpect(status().isOk()));
        for(String field:REMOVED)assertFalse(scores.has(field),field);
    }

    @Test void sendingARemovedFieldIsRejectedWith400() throws Exception {
        for(String field:REMOVED) {
            String extra=",\""+field+"\":"+(field.equals("expiryPushDays")?"7":"0.5");
            send(post("/api/products"),admin,productBody("LEGACY-C-"+UUID.randomUUID(),extra)).andExpect(status().isBadRequest());
            send(put("/api/products/{id}",product),admin,productBody("LEGACY-P-"+UUID.randomUUID(),extra)).andExpect(status().isBadRequest());
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE store_id=?",Integer.class,store),"rejected writes create nothing");
    }

    @Test void configOmitsExpiryWindowDaysAndRejectsItOnPut() throws Exception {
        var before=body(send(get("/api/utility/config"),merchant,null).andExpect(status().isOk()));
        assertFalse(before.has("expiryWindowDays"));
        assertTrue(before.has("config"));
        assertFalse(before.path("config").has("expiryWindowDays"));
        send(put("/api/utility/config"),merchant,
            "{\"alpha\":0.5,\"beta\":0.5,\"lambda\":0.3,\"salesWindowDays\":30,\"expiryWindowDays\":30}")
            .andExpect(status().isBadRequest());
        var after=body(send(get("/api/utility/config"),merchant,null).andExpect(status().isOk()));
        assertEquals(before,after,"rejected obsolete field must not change Store settings");
    }

    @Test void theTwoObsoleteRoutesAnswerLikeAnyUnknownRoute() throws Exception {
        int unknownPath=send(put("/api/utility/config/no-such-route"),admin,"{}").andReturn().getResponse().getStatus();
        int unknownMethod=send(patch("/api/utility/config"),merchant,"{}").andReturn().getResponse().getStatus();
        assertEquals(404,unknownPath,"reference: unknown path");
        assertEquals(405,unknownMethod,"reference: unsupported method on an existing path");
        // Whole path removed -> same as an unknown path.
        assertEquals(unknownPath,send(put("/api/utility/config/expiry-window"),admin,"{\"expiryWindowDays\":30}").andReturn().getResponse().getStatus());
        // GET on the same path stays (W2 preview), so the removed PATCH behaves like any unsupported method.
        assertEquals(unknownMethod,send(patch("/api/utility/products/{id}/scores",product),merchant,"{\"stockPriorityScore\":0.5}").andReturn().getResponse().getStatus());
        send(get("/api/utility/products/{id}/scores",product),merchant,null).andExpect(status().isOk());
        mvc.perform(put("/api/utility/config/expiry-window").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test void openApiNoLongerListsTheRoutesOrTheFields() throws Exception {
        var spec=body(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
        assertTrue(spec.path("paths").path("/api/utility/config/expiry-window").isMissingNode());
        var scores=spec.path("paths").path("/api/utility/products/{productId}/scores");
        assertTrue(scores.has("get"));assertFalse(scores.has("patch"));
        var schemas=spec.path("components").path("schemas");
        for(String schema:new String[]{"ProductRequest","ProductView","Scores"})
            for(String field:REMOVED)assertFalse(schemas.path(schema).path("properties").has(field),schema+"."+field);
    }

    JsonNode body(org.springframework.test.web.servlet.ResultActions r) throws Exception { return json.readTree(r.andReturn().getResponse().getContentAsString()); }
    org.springframework.test.web.servlet.ResultActions send(MockHttpServletRequestBuilder request,String token,String body) throws Exception {
        request=request.header("Authorization","Bearer "+token);
        if(body!=null)request=request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }
    static String productBody(String sku,String extra) {
        String normalized=sku.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");
        String validSku="SKU-"+(normalized+"00000000000").substring(0,11);
        return "{\"sku\":\""+validSku+"\",\"name\":\"Milk\",\"unit\":\"bottle\",\"requiresExpiry\":true,\"sellingPrice\":100,\"costPrice\":20,\"status\":\"ACTIVE\""+extra+"}";
    }
    String token(String role,Integer storeId) {
        var user=users.createPendingUser("Legacy removed",UUID.randomUUID()+"@example.test",role,storeId,"unused-test-hash",null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",user.id());
        return auth.token(users.findById(user.id())).accessToken();
    }
}

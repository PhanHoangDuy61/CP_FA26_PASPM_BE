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
        {"enableStock":true,"enableExpiry":true,"enableSeason":true,"alpha":0.5,"beta":0.2,"gamma":0.3,"lambda":0.5}
        """;

    @BeforeEach void fixture() throws Exception {
        store=store();other=store();today=zone.today();
        merchant=token("MERCHANDISER",store);admin=token("STORE_ADMIN",store);
        foreign=token("MERCHANDISER",other);system=token("SYSTEM_ADMIN",null);customer=token("END_CUSTOMER",null);
        category=category(store);foreignCategory=category(other);
        product=product(store,category);foreignProduct=product(other,foreignCategory);
        jdbc.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,expiry_date,initial_quantity,remaining_quantity,status) VALUES (?,?,CURRENT_TIMESTAMP,?,10,10,'ACTIVE')",product,UUID.randomUUID().toString(),today.plusDays(5));
        putJson("/api/system/utility/lambda-policy",system,"{\"lambdaMin\":0,\"lambdaMax\":0.5}").andExpect(status().isOk());
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
        String productBody="{\"sku\":\"DENIED\",\"name\":\"Denied\",\"unit\":\"bottle\",\"requiresExpiry\":true,\"sellingPrice\":100,\"costPrice\":20,\"status\":\"ACTIVE\"}";
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
        mvc.perform(post("/api/products/"+product+"/batches").header("Authorization","Bearer "+merchant).contentType(MediaType.APPLICATION_JSON).content("{\"batchCode\":\"DENIED\",\"receivedAt\":\"2026-10-01T00:00:00Z\",\"expiryDate\":\"2099-01-01\",\"initialQuantity\":1}"))
            .andExpect(status().isForbidden());
    }

    @Test void catalogRejectsUnauthenticatedAndNonStoreBusinessRoles() throws Exception {
        for(String path:new String[]{"/api/products","/api/products/"+product,"/api/categories","/api/categories/"+category}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            for(String who:new String[]{system,customer})getJson(path,who).andExpect(status().isForbidden());
            getJson(path,admin).andExpect(status().isOk());
        }
    }

    ResultActions getJson(String path,String token) throws Exception {return mvc.perform(get(path).header("Authorization","Bearer "+token));}
    ResultActions putJson(String path,String token,String body) throws Exception {return mvc.perform(put(path).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body));}
    ResultActions scores(String token,String body) throws Exception {return mvc.perform(patch("/api/utility/products/"+product+"/scores").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body));}
    String scorePath(){return "/api/utility/products/"+product+"/scores";}
    JsonNode body(ResultActions result) throws Exception{return json.readTree(result.andReturn().getResponse().getContentAsString());}
    int store(){jdbc.update("INSERT INTO store(name) VALUES (?)","Retail "+UUID.randomUUID());return jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);}
    int category(int owner){jdbc.update("INSERT INTO category(store_id,name,status) VALUES (?,?,'ACTIVE')",owner,"Dairy "+UUID.randomUUID());return jdbc.queryForObject("SELECT MAX(category_id) FROM category",Integer.class);}
    int product(int owner,int cat){jdbc.update("INSERT INTO product(store_id,category_id,sku,name,unit,requires_expiry,selling_price,cost_price,status,expiry_push_days,stock_priority_score,seasonal_priority_score) VALUES (?,?,?,'Priority Milk','bottle',TRUE,100,20,'ACTIVE',7,0.8,0.9)",owner,cat,UUID.randomUUID().toString());return jdbc.queryForObject("SELECT MAX(product_id) FROM product",Integer.class);}
    String token(String role,Integer owner){var user=users.createPendingUser("Retail QA",UUID.randomUUID()+"@example.test",role,owner,"unused-test-hash",null);jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",user.id());return auth.token(users.findById(user.id())).accessToken();}
}

package vn.edu.fpt.paspm.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.User;
import vn.edu.fpt.paspm.user.UserRepository;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=true", "paspm.jwt-secret=01234567890123456789012345678901",
        "paspm.bootstrap-email=", "paspm.bootstrap-password=",
        "paspm.store-time-zone=Asia/Ho_Chi_Minh"
})
@AutoConfigureMockMvc
class CatalogIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthService auth;
    @Autowired ObjectMapper json;

    private String storeAdminToken;
    private String otherStoreToken;
    private int firstStoreId;
    private int secondStoreId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM inventory_batch");
        jdbc.update("DELETE FROM product");
        jdbc.update("DELETE FROM category");
        firstStoreId = store("F02 Store A");
        secondStoreId = store("F02 Store B");
        storeAdminToken = token(active("catalog-a-" + UUID.randomUUID() + "@example.test", firstStoreId));
        otherStoreToken = token(active("catalog-b-" + UUID.randomUUID() + "@example.test", secondStoreId));
    }

    @Test
    void catalogRequiresAuthenticationAndStoreAdminRole() throws Exception {
        mvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());

        User merchandiser = active("catalog-merch-" + UUID.randomUUID() + "@example.test", "MERCHANDISER", 1);
        mvc.perform(get("/api/categories").header("Authorization", "Bearer " + token(merchandiser)))
                .andExpect(status().isForbidden());
    }

    @Test
    void categoryAndProductAreScopedToAuthenticatedStore() throws Exception {
        String category = """
                {"name":"Dairy","status":"ACTIVE"}
                """;
        String response = mvc.perform(post("/api/categories")
                        .header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(category))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Dairy"))
                .andReturn().getResponse().getContentAsString();
        int categoryId = json.readTree(response).get("id").asInt();

        mvc.perform(get("/api/categories/{id}", categoryId)
                        .header("Authorization", "Bearer " + otherStoreToken))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/categories/{id}", categoryId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Milk & Dairy\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Milk & Dairy"));
        mvc.perform(patch("/api/categories/{id}/status", categoryId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));

        String product = """
                {"sku":"MILK-01","name":"Fresh Milk","categoryId":%d,"unit":"bottle","requiresExpiry":true,"sellingPrice":2.50,"costPrice":1.25,"status":"ACTIVE"}
                """.formatted(categoryId);
        String forgedStoreProduct = product.replace("{\"sku\":", "{\"storeId\":" + secondStoreId + ",\"sku\":");
        mvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(forgedStoreProduct))
                .andExpect(status().isBadRequest());
        String productResponse = mvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("MILK-01"))
                .andExpect(jsonPath("$.stockQuantity").value(0))
                .andReturn().getResponse().getContentAsString();
        int productId = json.readTree(productResponse).get("id").asInt();

        mvc.perform(get("/api/products?search=MILK&categoryId={categoryId}&status=ACTIVE&page=0&pageSize=5", categoryId)
                        .header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(productId));
        mvc.perform(put("/api/products/{id}", productId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(product.replace("Fresh Milk", "Fresh Milk Updated")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Fresh Milk Updated"));
        mvc.perform(patch("/api/products/{id}/status", productId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
        mvc.perform(patch("/api/products/{id}/status", productId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));

        mvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + otherStoreToken)
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isNotFound());

        String batchResponse = mvc.perform(post("/api/products/{id}/batches", productId)
                        .header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchCode\":\"B-001\",\"receivedAt\":\"2026-10-01T10:00:00Z\",\"expiryDate\":\"2099-12-31\",\"initialQuantity\":12}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.remainingQuantity").value(12))
                .andReturn().getResponse().getContentAsString();
        int batchId = json.readTree(batchResponse).get("id").asInt();
        mvc.perform(get("/api/products/{id}/batches", productId).header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].batchCode").value("B-001"));
        mvc.perform(get("/api/inventory-batches/{id}", batchId).header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.initialQuantity").value(12));
        mvc.perform(patch("/api/inventory-batches/{id}", batchId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remainingQuantity\":5,\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.remainingQuantity").value(5))
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        mvc.perform(post("/api/products/{id}/batches", productId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchCode\":\"NO-EXPIRY\",\"receivedAt\":\"2026-10-01T10:00:00Z\",\"initialQuantity\":2}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/products/{id}", productId).header("Authorization", "Bearer " + otherStoreToken))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/inventory-batches/{id}", batchId).header("Authorization", "Bearer " + otherStoreToken))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/inventory-batches/{id}", batchId).header("Authorization", "Bearer " + otherStoreToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"remainingQuantity\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/inventory-batches/{id}", batchId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"remainingQuantity\":13}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/products/{id}/batches", productId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchCode\":\"B-001\",\"receivedAt\":\"2026-10-01T10:00:00Z\",\"expiryDate\":\"2099-12-31\",\"initialQuantity\":12}"))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/products/2147483000").header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateSkuNegativePriceAndInvalidBatchQuantities() throws Exception {
        int categoryId = createCategory("Produce");
        String product = """
                {"sku":"APPLE-01","name":"Apple","categoryId":%d,"unit":"each","requiresExpiry":false,"sellingPrice":1,"costPrice":0.5,"status":"ACTIVE"}
                """.formatted(categoryId);
        String response = mvc.perform(post("/api/products").header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int productId = json.readTree(response).get("id").asInt();

        mvc.perform(post("/api/products").header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(product.replace("\"sellingPrice\":1", "\"sellingPrice\":-1")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/products/{id}/batches", productId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchCode\":\"BAD\",\"receivedAt\":\"2026-10-01T10:00:00Z\",\"initialQuantity\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void productWriteHidesCategoryOwnedByAnotherStore() throws Exception {
        jdbc.update("INSERT INTO category(store_id, name, status) VALUES (?, 'Foreign category', 'ACTIVE')", secondStoreId);
        int foreignCategoryId = jdbc.queryForObject(
                "SELECT category_id FROM category WHERE store_id=? AND name='Foreign category'",
                Integer.class, secondStoreId);
        String product = """
                {"sku":"FOREIGN-CATEGORY-01","name":"Foreign category product","categoryId":%d,"unit":"each","requiresExpiry":false,"sellingPrice":1,"costPrice":0.5,"status":"ACTIVE"}
                """.formatted(foreignCategoryId);

        mvc.perform(post("/api/products").header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isNotFound());
    }

    @Test
    void openApiListsF02Endpoints() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/categories']").exists())
                .andExpect(jsonPath("$.paths['/api/categories/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/products']").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}/status']").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}/batches']").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}/inventory-summary']").exists())
                .andExpect(jsonPath("$.paths['/api/inventory-batches/{id}']").exists());
    }

    @Test
    void databaseConstraintsRejectCrossStoreCategoriesAndInvalidInventory() throws Exception {
        int categoryId = createCategory("Owned by Store A");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO product(store_id, category_id, sku, name, unit, requires_expiry,
                                    selling_price, cost_price, status)
                VALUES (?, ?, 'FOREIGN-CATEGORY', 'Foreign category', 'each', FALSE, 1, 1, 'ACTIVE')
                """, secondStoreId, categoryId));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO product(store_id, sku, name, unit, requires_expiry, selling_price, cost_price, status)
                VALUES (?, 'NEG-PRICE', 'Negative price', 'each', FALSE, -1, 1, 'ACTIVE')
                """, firstStoreId));
    }

    @Test
    void inventorySummarySeparatesExpiredBlockedAndSellableBatches() throws Exception {
        int categoryId = createCategory("Summary category");
        String productBody = """
                {"sku":"SUMMARY-01","name":"Summary Product","categoryId":%d,"unit":"each","requiresExpiry":true,"sellingPrice":1,"costPrice":0.5,"status":"ACTIVE"}
                """.formatted(categoryId);
        String response = mvc.perform(post("/api/products").header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(productBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int productId = json.readTree(response).get("id").asInt();
        mvc.perform(get("/api/products/{id}/inventory-summary", productId)
                        .header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalQuantity").value(0))
                .andExpect(jsonPath("$.sellableQuantity").value(0))
                .andExpect(jsonPath("$.expiredQuantity").value(0))
                .andExpect(jsonPath("$.blockedQuantity").value(0));

        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        createBatch(productId, "SUMMARY-EXPIRED", today.minusDays(1), 3);
        createBatch(productId, "SUMMARY-TODAY", today, 4);
        createBatch(productId, "SUMMARY-SOON", today.plusDays(5), 5);
        int blockedBatchId = createBatch(productId, "SUMMARY-BLOCKED", today.minusDays(3), 6);
        mvc.perform(patch("/api/inventory-batches/{id}", blockedBatchId)
                        .header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk());
        createBatch(productId, "SUMMARY-LATER", today.plusDays(30), 7);

        mvc.perform(get("/api/products/{id}/inventory-summary?expiringWithinDays=7", productId)
                        .header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalQuantity").value(25))
                .andExpect(jsonPath("$.sellableQuantity").value(16))
                .andExpect(jsonPath("$.expiredQuantity").value(3))
                .andExpect(jsonPath("$.blockedQuantity").value(6))
                .andExpect(jsonPath("$.expiringSoonQuantity").value(9));

        mvc.perform(get("/api/products/{id}/inventory-summary?expiringWithinDays=-1", productId)
                        .header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/products/{id}/inventory-summary", productId)
                        .header("Authorization", "Bearer " + otherStoreToken))
                .andExpect(status().isNotFound());
    }

    private int store(String name) {
        jdbc.update("INSERT INTO store(name) VALUES (?)", name);
        return jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
    }

    private User active(String email, int storeId) { return active(email, "STORE_ADMIN", storeId); }

    private User active(String email, String role, int storeId) {
        User user = users.createPendingUser("Catalog Test", email, role, storeId,
                passwords.encode("TestPassword1!"), null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=? WHERE user_id=?",
                Timestamp.from(Instant.now()), user.id());
        return users.findById(user.id());
    }

    private String token(User user) { return auth.token(user).accessToken(); }

    private int createCategory(String name) throws Exception {
        String response = mvc.perform(post("/api/categories").header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("name", name, "status", "ACTIVE"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asInt();
    }

    private int createBatch(int productId, String batchCode, java.time.LocalDate expiryDate, int quantity) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
                "batchCode", batchCode, "receivedAt", "2026-10-01T10:00:00Z",
                "expiryDate", expiryDate.toString(), "initialQuantity", quantity));
        String response = mvc.perform(post("/api/products/{id}/batches", productId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asInt();
    }
}

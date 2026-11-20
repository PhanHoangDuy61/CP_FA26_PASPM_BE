package vn.edu.fpt.paspm.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
        "paspm.otp-secret=test-only-otp-key-with-at-least-thirty-two-bytes",
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
    void catalogRequiresAuthenticationAndAuthorizedStoreRole() throws Exception {
        mvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());

        User merchandiser = active("catalog-merch-" + UUID.randomUUID() + "@example.test", "MERCHANDISER", firstStoreId);
        mvc.perform(get("/api/categories").header("Authorization", "Bearer " + token(merchandiser)))
                .andExpect(status().isOk());
        User system = active("catalog-system-" + UUID.randomUUID() + "@example.test", "SYSTEM_ADMIN", null);
        mvc.perform(get("/api/categories").header("Authorization", "Bearer " + token(system)))
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
                {"name":"Fresh Milk","categoryId":%d,"unit":"bottle","requiresExpiry":true,"sellingPrice":2.50,"costPrice":1.25,"status":"ACTIVE"}
                """.formatted(categoryId);
        String forgedStoreProduct = product.replace("{\"name\":", "{\"storeId\":" + secondStoreId + ",\"name\":");
        mvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(forgedStoreProduct))
                .andExpect(status().isBadRequest());
        String productResponse = mvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stockQuantity").value(0))
                .andReturn().getResponse().getContentAsString();
        int productId = json.readTree(productResponse).get("id").asInt();
        org.junit.jupiter.api.Assertions.assertEquals(String.format(java.util.Locale.ROOT, "SKU-%011d", productId),
                json.readTree(productResponse).get("sku").asText());

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
                        .content("{\"receivedAt\":\"2026-10-01T10:00:00Z\",\"expiryDate\":\"2099-12-31\",\"initialQuantity\":12}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.remainingQuantity").value(12))
                .andExpect(jsonPath("$.batchCode").value("BATCH-20261001-001"))
                .andReturn().getResponse().getContentAsString();
        int batchId = json.readTree(batchResponse).get("id").asInt();
        mvc.perform(get("/api/products/{id}/batches", productId).header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].batchCode").value("BATCH-20261001-001"));
        mvc.perform(get("/api/inventory-batches/{id}", batchId).header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.initialQuantity").value(12));
        mvc.perform(patch("/api/inventory-batches/{id}", batchId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remainingQuantity\":5,\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.remainingQuantity").value(5))
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        mvc.perform(post("/api/products/{id}/batches", productId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receivedAt\":\"2026-10-01T10:00:00Z\",\"initialQuantity\":2}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/products/{id}/batches", productId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":99,\"receivedAt\":\"2026-10-01T10:00:00Z\","
                                + "\"expiryDate\":\"2099-12-31\",\"initialQuantity\":2}"))
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
                        .content("{\"receivedAt\":\"2026-10-02T10:00:00Z\",\"expiryDate\":\"2099-12-31\",\"initialQuantity\":12}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.batchCode").value("BATCH-20261002-002"));

        mvc.perform(post("/api/products/{id}/batches", productId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchCode\":\"CLIENT-BATCH\",\"receivedAt\":\"2026-10-01T10:00:00Z\","
                                + "\"expiryDate\":\"2099-12-31\",\"initialQuantity\":1}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/products/2147483000").header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsClientSuppliedSkuNegativePriceAndInvalidBatchQuantities() throws Exception {
        int categoryId = createCategory("Produce");
        String product = """
                {"name":"Apple","categoryId":%d,"unit":"each","requiresExpiry":false,"sellingPrice":1,"costPrice":0.5,"status":"ACTIVE"}
                """.formatted(categoryId);
        String response = mvc.perform(post("/api/products").header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int productId = json.readTree(response).get("id").asInt();
        org.junit.jupiter.api.Assertions.assertEquals(String.format(java.util.Locale.ROOT, "SKU-%011d", productId),
                json.readTree(response).get("sku").asText());

        mvc.perform(post("/api/products").header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NAME_CONFIRMATION_REQUIRED"));
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(product.replace("{\"name\":", "{\"sku\":\"SKU-99999999999\",\"name\":")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(product.replace("\"sellingPrice\":1", "\"sellingPrice\":-1")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/products/{id}/batches", productId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receivedAt\":\"2026-10-01T10:00:00Z\",\"initialQuantity\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentBatchCreatesUseUniquePerProductSequence() throws Exception {
        int productId = createProduct("SKU-CONCUR-0001", "Concurrent batches", false);
        int count = 4;
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> responses = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                responses.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return mvc.perform(post("/api/products/{id}/batches", productId)
                                    .header("Authorization", "Bearer " + storeAdminToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"receivedAt\":\"2026-10-01T20:00:00Z\",\"initialQuantity\":1}"))
                            .andReturn().getResponse().getContentAsString();
                }));
            }
            ready.await();
            start.countDown();
            TreeSet<String> codes = new TreeSet<>();
            for (Future<String> response : responses) {
                var result = json.readTree(response.get());
                assertTrue(result.path("id").asInt() > 0);
                codes.add(result.path("batchCode").asText());
            }
            assertEquals(java.util.Set.of("BATCH-20261002-001", "BATCH-20261002-002",
                    "BATCH-20261002-003", "BATCH-20261002-004"), codes);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void productIdsAndSkusAreServerGeneratedAndSimilarNamesRequireConfirmation() throws Exception {
        int categoryId = createCategory("Catalog");
        String firstProduct = productBody(" sku-abcd-000001 ", "Fresh Milk", categoryId, "bottle", false, 2, 1, "ACTIVE", null);
        String created = mvc.perform(post("/api/products").header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(firstProduct))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn().getResponse().getContentAsString();
        int productId = json.readTree(created).get("id").asInt();
        org.junit.jupiter.api.Assertions.assertTrue(productId > 0);
        org.junit.jupiter.api.Assertions.assertEquals(String.format(java.util.Locale.ROOT, "SKU-%011d", productId),
                json.readTree(created).get("sku").asText());

        String attemptedSkuOverride = firstProduct.replace("{\"name\":", "{\"sku\":\"SKU-00000000001\",\"name\":");
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attemptedSkuOverride))
                .andExpect(status().isBadRequest());

        String similar = productBody("SKU-ABCD-000002", "Fresh Milks", categoryId, "bottle", false, 2, 1, "ACTIVE", null);
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(similar))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NAME_CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.confirmWith").value("confirmSimilarName=true"));
        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE store_id=?", Integer.class, firstStoreId));

        String confirmed = similar.replace("\"status\":\"ACTIVE\"", "\"status\":\"ACTIVE\",\"confirmSimilarName\":true");
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(confirmed))
                .andExpect(status().isCreated());
        String conflictingUpdate = firstProduct.replace("\"name\":\"Fresh Milk\"", "\"name\":\"Fresh Milks\"");
        mvc.perform(put("/api/products/{id}", productId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(conflictingUpdate))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NAME_CONFIRMATION_REQUIRED"));
        mvc.perform(get("/api/products/{id}", productId).header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Fresh Milk"));
        mvc.perform(put("/api/products/{id}", productId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictingUpdate.replace("\"status\":\"ACTIVE\"", "\"status\":\"ACTIVE\",\"confirmSimilarName\":true")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Fresh Milks"));

        String otherStoreProduct = productBody(" sku-abcd-000001 ", "Other Store Product", null,
                "each", false, 1, 1, "ACTIVE", null);
        String otherStoreCreated = mvc.perform(post("/api/products").header("Authorization", "Bearer " + otherStoreToken)
                        .contentType(MediaType.APPLICATION_JSON).content(otherStoreProduct))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertNotEquals(json.readTree(created).get("sku").asText(),
                json.readTree(otherStoreCreated).get("sku").asText());

        mvc.perform(post("/api/products").header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstProduct.replace("{\"name\":", "{\"id\":999,\"name\":")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void productSortingSupportsAllowlistedColumnsAndStablePagination() throws Exception {
        int alphaCategory = createCategory("A Category");
        int zuluCategory = createCategory("Z Category");
        String alphaResponse = mvc.perform(post("/api/products").header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("SKU-SORTA-00001", "Apple", alphaCategory, "box", false, 1, 1, "ACTIVE", null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int alphaId = json.readTree(alphaResponse).get("id").asInt();
        String zuluResponse = mvc.perform(post("/api/products").header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody("SKU-SORTZ-00001", "Zucchini", zuluCategory, "each", true, 2, 2, "INACTIVE", null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int zuluId = json.readTree(zuluResponse).get("id").asInt();

        createBatch(alphaId, null, 1);
        createBatch(zuluId, java.time.LocalDate.now().plusDays(30), 2);
        mvc.perform(get("/api/products/{id}/batches", alphaId).header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].batchCode").value("BATCH-20261001-001"));
        mvc.perform(get("/api/products/{id}/batches", zuluId).header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].batchCode").value("BATCH-20261001-001"));

        for (String sortBy : java.util.List.of("name", "sku", "category", "unit", "costPrice",
                "sellingPrice", "status", "requiresExpiry", "stockQuantity")) {
            mvc.perform(get("/api/products?sortBy={sortBy}&sortDirection=asc", sortBy)
                            .header("Authorization", "Bearer " + storeAdminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(alphaId))
                    .andExpect(jsonPath("$.content[1].id").value(zuluId));
            mvc.perform(get("/api/products?sortBy={sortBy}&sortDirection=desc", sortBy)
                            .header("Authorization", "Bearer " + storeAdminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(zuluId))
                    .andExpect(jsonPath("$.content[1].id").value(alphaId));
        }

        String tiedName = productBody("SKU-SORTA-00002", "Apple", alphaCategory, "box", false, 1, 1,
                "ACTIVE", true);
        String tiedResponse = mvc.perform(post("/api/products").header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(tiedName))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int tiedId = json.readTree(tiedResponse).get("id").asInt();
        mvc.perform(get("/api/products?sortBy=name&sortDirection=asc&page=0&pageSize=1")
                        .header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(alphaId));
        mvc.perform(get("/api/products?sortBy=name&sortDirection=asc&page=1&pageSize=1")
                        .header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(tiedId));
        mvc.perform(get("/api/products?sortBy=sku%20DESC").header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void batchExpiryCanBeCorrectedAndClearedOnlyWhenAllowed() throws Exception {
        int expiringProduct = createProduct("SKU-EXPIRY-0001", "Required expiry", true);
        mvc.perform(get("/api/products/{id}", expiringProduct).header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requiresExpiry").value(true));
        java.time.LocalDate expiry = java.time.LocalDate.now().plusDays(30);
        String batchResponse = mvc.perform(post("/api/products/{id}/batches", expiringProduct)
                        .header("Authorization", "Bearer " + storeAdminToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receivedAt\":\"2026-10-01T10:00:00Z\","
                                + "\"expiryDate\":\"" + expiry + "\",\"initialQuantity\":10,\"pushDay\":\""
                                + expiry.minusDays(2) + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber())
                .andReturn().getResponse().getContentAsString();
        int batchId = json.readTree(batchResponse).get("id").asInt();

        mvc.perform(patch("/api/inventory-batches/{id}", batchId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiryDate\":\"" + expiry.minusDays(3) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/inventory-batches/{id}", batchId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expiryDate\":null}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/inventory-batches/{id}", batchId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiryDate\":\"" + expiry.plusDays(5) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiryDate").value(expiry.plusDays(5).toString()));
        mvc.perform(delete("/api/inventory-batches/{id}/expiry-date", batchId)
                        .header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/api/inventory-batches/{id}", batchId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remainingQuantity\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/inventory-batches/{id}", batchId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remainingQuantity\":11}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/inventory-batches/{id}", batchId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchCode\":\"CHANGED\",\"receivedAt\":\"2026-10-02T10:00:00Z\","
                                + "\"initialQuantity\":50}"))
                .andExpect(status().isBadRequest());

        int optionalProduct = createProduct("SKU-OPTIONAL-01", "Optional expiry", false);
        mvc.perform(get("/api/products/{id}", optionalProduct).header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requiresExpiry").value(false));
        int optionalBatch = createBatch(optionalProduct, expiry, 5);
        mvc.perform(patch("/api/inventory-batches/{id}", optionalBatch)
                        .header("Authorization", "Bearer " + storeAdminToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiryDate\":\"" + expiry.plusDays(2) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiryDate").value(expiry.plusDays(2).toString()));
        mvc.perform(delete("/api/inventory-batches/{id}/expiry-date", optionalBatch)
                        .header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiryDate").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(delete("/api/inventory-batches/{id}/expiry-date", optionalBatch)
                        .header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiryDate").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(patch("/api/inventory-batches/{id}", batchId)
                        .header("Authorization", "Bearer " + otherStoreToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remainingQuantity\":1}"))
                .andExpect(status().isNotFound());

        User merchandiser = active("catalog-batch-merch-" + UUID.randomUUID() + "@example.test",
                "MERCHANDISER", firstStoreId);
        mvc.perform(patch("/api/inventory-batches/{id}", optionalBatch)
                        .header("Authorization", "Bearer " + token(merchandiser)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remainingQuantity\":1}"))
                .andExpect(status().isForbidden());

        int legacyProduct = createProduct("SKU-LEGACY-0001", "Legacy batch", false);
        createBatch(legacyProduct, null, 3);
        String enableExpiry = productBody("SKU-LEGACY-0001", "Legacy batch", null, "each", true,
                1, 1, "ACTIVE", null);
        mvc.perform(put("/api/products/{id}", legacyProduct).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(enableExpiry))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/products/{id}", legacyProduct).header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requiresExpiry").value(false));
    }

    @Test
    void productWriteHidesCategoryOwnedByAnotherStore() throws Exception {
        jdbc.update("INSERT INTO category(store_id, name, status) VALUES (?, 'Foreign category', 'ACTIVE')", secondStoreId);
        int foreignCategoryId = jdbc.queryForObject(
                "SELECT category_id FROM category WHERE store_id=? AND name='Foreign category'",
                Integer.class, secondStoreId);
        String product = """
                {"name":"Foreign category product","categoryId":%d,"unit":"each","requiresExpiry":false,"sellingPrice":1,"costPrice":0.5,"status":"ACTIVE"}
                """.formatted(foreignCategoryId);

        mvc.perform(post("/api/products").header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isNotFound());
    }

    @Test
    void openApiListsF02Endpoints() throws Exception {
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/categories']").exists())
                .andExpect(jsonPath("$.paths['/api/categories/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/products']").exists())
                .andExpect(jsonPath("$.paths['/api/products'].post.description").value(
                        org.hamcrest.Matchers.containsString("PRODUCT_NAME_CONFIRMATION_REQUIRED")))
                .andExpect(jsonPath("$.paths['/api/products/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}/status']").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}/batches']").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}/inventory-summary']").exists())
                .andExpect(jsonPath("$.paths['/api/inventory-batches/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/inventory-batches/{id}/push-day'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/inventory-batches/{id}/expiry-date'].delete").exists())
                .andExpect(jsonPath("$.components.schemas.ProductRequest.properties.confirmSimilarName").exists())
                .andExpect(jsonPath("$.components.schemas.ProductRequest.properties.sku").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.BatchCreateRequest.properties.batchCode").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ProductView.properties.id.readOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.ProductView.properties.sku.readOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.BatchView.properties.batchCode.readOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.BatchPatchRequest.properties.expiryDate").exists())
                .andReturn();
        String exportDir = System.getProperty("paspm.f02.export-dir");
        if (exportDir != null) {
            java.nio.file.Path directory = java.nio.file.Path.of(exportDir);
            java.nio.file.Files.createDirectories(directory);
            java.nio.file.Files.writeString(directory.resolve("openapi.json"),
                    json.readTree(result.getResponse().getContentAsString()).toPrettyString());
        }
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
                {"name":"Summary Product","categoryId":%d,"unit":"each","requiresExpiry":true,"sellingPrice":1,"costPrice":0.5,"status":"ACTIVE"}
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
        createBatch(productId, today.minusDays(1), 3);
        createBatch(productId, today, 4);
        createBatch(productId, today.plusDays(5), 5);
        int blockedBatchId = createBatch(productId, today.minusDays(3), 6);
        mvc.perform(patch("/api/inventory-batches/{id}", blockedBatchId)
                        .header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk());
        createBatch(productId, today.plusDays(30), 7);

        mvc.perform(get("/api/products/{id}/inventory-summary?expiringWithinDays=7", productId)
                        .header("Authorization", "Bearer " + storeAdminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalQuantity").value(25))
                .andExpect(jsonPath("$.sellableQuantity").value(12))
                .andExpect(jsonPath("$.expiredQuantity").value(7))
                .andExpect(jsonPath("$.blockedQuantity").value(6))
                .andExpect(jsonPath("$.expiringSoonQuantity").value(5));

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

    private User active(String email, String role, Integer storeId) {
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

    private int createBatch(int productId, java.time.LocalDate expiryDate, int quantity) throws Exception {
        var request = new java.util.LinkedHashMap<String, Object>();
        request.put("receivedAt", "2026-10-01T10:00:00Z");
        if (expiryDate != null) request.put("expiryDate", expiryDate.toString());
        request.put("initialQuantity", quantity);
        String body = json.writeValueAsString(request);
        String response = mvc.perform(post("/api/products/{id}/batches", productId).header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asInt();
    }

    private int createProduct(String ignoredLegacySku, String name, boolean requiresExpiry) throws Exception {
        String body = productBody(ignoredLegacySku, name, null, "each", requiresExpiry, 1, 1, "ACTIVE", null);
        String response = mvc.perform(post("/api/products").header("Authorization", "Bearer " + storeAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asInt();
    }

    private String productBody(String ignoredLegacySku, String name, Integer categoryId, String unit, boolean requiresExpiry,
            int sellingPrice, int costPrice, String status, Boolean confirmSimilarName) throws Exception {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("name", name);
        if (categoryId != null) body.put("categoryId", categoryId);
        body.put("unit", unit);
        body.put("requiresExpiry", requiresExpiry);
        body.put("sellingPrice", sellingPrice);
        body.put("costPrice", costPrice);
        body.put("status", status);
        if (confirmSimilarName != null) body.put("confirmSimilarName", confirmSimilarName);
        return json.writeValueAsString(body);
    }
}

package vn.edu.fpt.paspm.storefront;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.layout.LayoutPolicyProperties;
import vn.edu.fpt.paspm.layout.LayoutRules;
import vn.edu.fpt.paspm.user.UserRepository;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:f09storefront;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "paspm.jwt-secret=01234567890123456789012345678901",
        "paspm.otp-secret=test-only-otp-key-with-at-least-thirty-two-bytes",
        "paspm.bootstrap-email=", "paspm.bootstrap-password=",
        "paspm.store-time-zone=Asia/Ho_Chi_Minh",
        "paspm.layout.max-blocks-by-zone.BUNDLE=10",
        "paspm.layout.max-blocks-by-zone.CROSS_SELL=10",
        "paspm.layout.max-blocks-by-zone.FBT=10",
        "paspm.layout.max-blocks-by-zone.BANNER=10",
        "paspm.layout.max-products-by-zone.BUNDLE=30",
        "paspm.layout.max-products-by-zone.CROSS_SELL=30",
        "paspm.layout.max-products-by-zone.FBT=30",
        "paspm.layout.max-products-by-zone.BANNER=30"
})
@AutoConfigureMockMvc
class StorefrontIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired ObjectMapper json;
    @Autowired StorefrontRepository storefronts;
    @MockitoSpyBean JdbcTemplate jdbcSpy;

    @Test
    void publicStoreAndCatalogRoutesAreAnonymousAndExposeOnlyPublicFields() throws Exception {
        mvc.perform(get("/api/storefront/active-layout")).andExpect(status().isUnauthorized());
        int store = store("Public " + UUID.randomUUID(), true);
        int active = product(store, "Public Milk", true, "ACTIVE", "Description", "https://images.example/milk.jpg");
        batch(active, 8, "ACTIVE", LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(3));
        product(store, "Alpha Product", false, "ACTIVE", null, null);
        product(store, "Zulu Product", false, "ACTIVE", null, null);
        int inactive = product(store, "Internal Draft", false, "INACTIVE", null, null);
        batch(inactive, 20, "ACTIVE", null);
        int foreignStore = store("Foreign " + UUID.randomUUID(), true);
        int foreignProduct = product(foreignStore, "Foreign Product", false, "ACTIVE", null, null);
        batch(foreignProduct, 3, "ACTIVE", null);

        JsonNode stores = json.readTree(mvc.perform(get("/api/storefront/stores"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String slug = slug(store);
        JsonNode publicStore = findStore(stores, slug);
        assertNotNull(publicStore);
        assertEquals(2, publicStore.size());
        assertFalse(publicStore.has("storeId"));
        assertFalse(publicStore.has("id"));

        mvc.perform(get("/api/storefront/" + slug + "/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallbackType").value("DEFAULT_CATALOG"))
                .andExpect(jsonPath("$.zones").isEmpty())
                .andExpect(jsonPath("$.campaignName").doesNotExist());
        JsonNode fallbackProducts = json.readTree(mvc.perform(get("/api/storefront/" + slug + "/home"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("catalog").get(0).path("products");
        assertEquals(List.of("Alpha Product", "Public Milk", "Zulu Product"),
                java.util.stream.StreamSupport.stream(fallbackProducts.spliterator(), false)
                        .map(item -> item.path("name").asText()).toList());
        // FR-09.3 (PO review 2026-11-25): a product with no sellable stock is OUT_OF_STOCK, never another label.
        assertEquals(List.of("OUT_OF_STOCK", "AVAILABLE", "OUT_OF_STOCK"),
                java.util.stream.StreamSupport.stream(fallbackProducts.spliterator(), false)
                        .map(item -> item.path("availability").asText()).toList());

        JsonNode page = json.readTree(mvc.perform(get("/api/storefront/" + slug + "/products")
                .param("search", "Milk").param("page", "0").param("pageSize", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andReturn().getResponse().getContentAsString());
        JsonNode product = page.path("items").get(0);
        assertEquals(active, product.path("productId").asInt());
        assertEquals("AVAILABLE", product.path("availability").asText());
        assertEquals("https://images.example/milk.jpg", product.path("imageUrl").asText());
        assertFalse(product.has("sku"));
        assertFalse(product.has("costPrice"));
        assertFalse(product.has("stockQuantity"));
        assertFalse(product.has("requiresExpiry"));

        JsonNode detail = json.readTree(mvc.perform(get("/api/storefront/" + slug + "/products/" + active))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("Public Milk", detail.path("name").asText());
        assertFalse(detail.has("costPrice"));
        assertFalse(detail.has("stockQuantity"));
        assertFalse(detail.has("batch"));
        mvc.perform(get("/api/storefront/" + slug + "/products/" + inactive))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Product not found"));
        mvc.perform(get("/api/storefront/" + slug + "/products/" + foreignProduct))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Product not found"));
        mvc.perform(get("/api/storefront/unknown-store/home"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Storefront not found"));
        mvc.perform(get("/api/storefront/no-such-store/home"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Storefront not found"));
    }

    @Test
    void inactiveStoresAreNotPublicAndTheStoreSlugIsTheStoreManagementSlug() throws Exception {
        int store = store("Inactive " + UUID.randomUUID(), false);
        int product = product(store, "Hidden while inactive", false, "ACTIVE", null, null);
        batch(product, 5, "ACTIVE", null);
        String slug = slug(store);
        JsonNode publicStores = json.readTree(mvc.perform(get("/api/storefront/stores"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertNull(findStore(publicStores, slug));
        mvc.perform(get("/api/storefront/" + slug + "/home"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Storefront not found"));
        mvc.perform(get("/api/storefront/" + slug + "/products"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Storefront not found"));
        mvc.perform(get("/api/storefront/" + slug + "/products/" + product))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Storefront not found"));

        // Reactivating the Store (DEC-20261123-01) is the only switch; the public slug is store.slug.
        jdbc.update("UPDATE store SET status='ACTIVE' WHERE store_id=?", store);
        publicStores = json.readTree(mvc.perform(get("/api/storefront/stores"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertNotNull(findStore(publicStores, slug));
        mvc.perform(get("/api/storefront/" + slug + "/products/" + product)).andExpect(status().isOk());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM campaign WHERE store_id=?", Integer.class, store));
    }

    @Test
    void softDeletedProductsAreHiddenEverywhereInThePublicStorefront() throws Exception {
        int store = store("Soft delete " + UUID.randomUUID(), true);
        int admin = user("STORE_ADMIN", store);
        int kept = product(store, "Kept product", false, "ACTIVE", null, null);
        int deleted = product(store, "Deleted product", false, "ACTIVE", null, null);
        int deletedButActive = product(store, "Deleted but still ACTIVE row", false, "ACTIVE", null, null);
        for (int id : List.of(kept, deleted, deletedButActive)) batch(id, 5, "ACTIVE", null);
        // DEC-20261124-03 soft delete sets INACTIVE + deleted_at; the second row checks the deleted_at guard alone.
        jdbc.update("UPDATE product SET status='INACTIVE',deleted_at=CURRENT_TIMESTAMP,deleted_by=? WHERE product_id=?",
                admin, deleted);
        jdbc.update("UPDATE product SET deleted_at=CURRENT_TIMESTAMP,deleted_by=? WHERE product_id=?",
                admin, deletedButActive);
        String slug = slug(store);

        JsonNode page = json.readTree(mvc.perform(get("/api/storefront/" + slug + "/products"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1, page.path("totalElements").asInt());
        assertEquals(kept, page.path("items").get(0).path("productId").asInt());
        for (int id : List.of(deleted, deletedButActive)) {
            mvc.perform(get("/api/storefront/" + slug + "/products/" + id))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Product not found"));
        }
        String home = mvc.perform(get("/api/storefront/" + slug + "/home"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(home.contains("Kept product"));
        assertFalse(home.contains("Deleted"));
        var availability = storefronts.availability(store, List.of(deletedButActive),
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        assertFalse(availability.get(deletedButActive).productActive());
    }

    @Test
    void activeHomepageFailsClosedUntilEveryRecommendationZoneHasCaps() {
        ApiException error = assertThrows(ApiException.class,
                () -> new LayoutRules(new LayoutPolicyProperties()).requireConfiguredPublicCapacities());
        assertEquals(503, error.status());
        assertEquals("Storefront layout capacities are not configured", error.getMessage());
    }

    @Test
    void availabilityUsesSellableActiveBatchesAndOneBulkQuery() {
        int store = store("Availability " + UUID.randomUUID(), true);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        int validExpiry = product(store, "Valid expiry", true, "ACTIVE", null, null);
        int expiresToday = product(store, "Expires today", true, "ACTIVE", null, null);
        int expired = product(store, "Expired", true, "ACTIVE", null, null);
        int missingExpiry = product(store, "Missing expiry", true, "ACTIVE", null, null);
        int nonExpiring = product(store, "Non-expiring", false, "ACTIVE", null, null);
        int noStock = product(store, "No stock", false, "ACTIVE", null, null);
        int blocked = product(store, "Blocked", false, "ACTIVE", null, null);
        int inactive = product(store, "Inactive", false, "INACTIVE", null, null);
        batch(validExpiry, 5, "ACTIVE", today.plusDays(1));
        batch(expiresToday, 5, "ACTIVE", today);
        batch(expired, 5, "ACTIVE", today.minusDays(1));
        batch(missingExpiry, 5, "ACTIVE", null);
        batch(nonExpiring, 5, "ACTIVE", null);
        batch(blocked, 5, "BLOCKED", today.plusDays(1));
        batch(inactive, 5, "ACTIVE", null);

        clearInvocations(jdbcSpy);
        var result = storefronts.availability(store,
                List.of(validExpiry, expiresToday, expired, missingExpiry, nonExpiring, noStock, blocked, inactive), today);
        assertTrue(result.get(validExpiry).sellableQuantity() > 0);
        // DEC-20261124-01: a batch is still sellable on its expiry date and expired only the day after.
        assertTrue(result.get(expiresToday).sellableQuantity() > 0);
        assertEquals(0, result.get(expired).sellableQuantity());
        assertEquals(0, result.get(missingExpiry).sellableQuantity());
        assertTrue(result.get(missingExpiry).missingRequiredExpiry());
        assertTrue(result.get(nonExpiring).sellableQuantity() > 0);
        assertEquals(0, result.get(noStock).sellableQuantity());
        assertEquals(0, result.get(blocked).sellableQuantity());
        assertFalse(result.get(inactive).productActive());
        verify(jdbcSpy, times(1)).query(contains("missing_required_expiry"),
                any(RowCallbackHandler.class), any(Object[].class));
    }

    @Test
    void activeLayoutBExposesAllBundleActionsAndKeepsOtherZonesFiltered() throws Exception {
        int store = store("Campaign " + UUID.randomUUID(), true);
        int actor = user("MERCHANDISER", store);
        int snapshot = snapshot(store, actor);
        List<Integer> valid = List.of(
                product(store, "Valid bundle A", false, "ACTIVE", "A", null),
                product(store, "Valid bundle B", false, "ACTIVE", "B", null));
        valid.forEach(id -> batch(id, 4, "ACTIVE", null));
        List<Integer> pending = List.of(
                product(store, "Pending bundle A", false, "ACTIVE", null, null),
                product(store, "Pending bundle B", false, "ACTIVE", null, null));
        pending.forEach(id -> batch(id, 4, "ACTIVE", null));
        List<Integer> expired = List.of(
                product(store, "Expired bundle A", true, "ACTIVE", null, null),
                product(store, "Expired bundle B", true, "ACTIVE", null, null));
        expired.forEach(id -> batch(id, 4, "ACTIVE",
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1)));
        List<Integer> crossSell = List.of(
                product(store, "Cross-sell A", false, "ACTIVE", null, null),
                product(store, "Cross-sell B", false, "ACTIVE", null, null));
        crossSell.forEach(id -> batch(id, 4, "ACTIVE", null));
        List<Integer> validFbt = List.of(
                product(store, "Valid FBT A", false, "ACTIVE", null, null),
                product(store, "Valid FBT B", false, "ACTIVE", null, null));
        validFbt.forEach(id -> batch(id, 4, "ACTIVE", null));
        List<Integer> banner = List.of(product(store, "Banner", false, "ACTIVE", null, null));
        batch(banner.get(0), 4, "ACTIVE", null);
        long validRec = recommendation(store, snapshot, actor, "BUNDLE", valid, true);
        long pendingRec = recommendation(store, snapshot, actor, "BUNDLE", pending, false);
        long expiredRec = recommendation(store, snapshot, actor, "FBT", expired, true);
        long crossSellRec = recommendation(store, snapshot, actor, "CROSS_SELL", crossSell, true);
        long fbtRec = recommendation(store, snapshot, actor, "FBT", validFbt, true);
        long bannerRec = recommendation(store, snapshot, actor, "BANNER", banner, true);
        long layout = activeBundleLayout(store, snapshot, actor,
                List.of("BUNDLE", "BUNDLE", "FBT", "CROSS_SELL", "FBT", "BANNER"),
                List.of(validRec, pendingRec, expiredRec, crossSellRec, fbtRec, bannerRec),
                List.of(valid, pending, expired, crossSell, validFbt, banner));

        jdbc.update("INSERT INTO campaign(store_id,utility_snapshot_id,name,created_by,status) "
                + "VALUES (?,?,?,?,'DRAFT')", store, snapshot, "Never active", actor);
        jdbc.update("INSERT INTO campaign(store_id,utility_snapshot_id,name,created_by,status) "
                + "VALUES (?,?,?,?,'ENDED')", store, snapshot, "Already ended", actor);

        JsonNode home = json.readTree(mvc.perform(get("/api/storefront/" + slug(store) + "/home"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(home.path("fallbackType").isNull());
        assertEquals("Chosen campaign", home.path("campaignName").asText());
        var renderedTypes = new java.util.HashSet<String>();
        home.path("zones").forEach(zone -> zone.path("placements")
                .forEach(placement -> renderedTypes.add(placement.path("type").asText())));
        assertEquals(java.util.Set.of("BUNDLE", "CROSS_SELL", "FBT", "BANNER"), renderedTypes);
        assertEquals(4, home.path("zones").size());
        assertEquals(2, home.path("zones").get(1).path("placements").get(0).path("products").size());
        JsonNode bundles = home.path("zones").get(1).path("placements");
        assertEquals(2, bundles.size());
        assertTrue(bundles.get(0).path("bundleKey").isTextual());
        assertTrue(bundles.get(1).path("bundleKey").isTextual());
        assertNotEquals(bundles.get(0).path("bundleKey").asText(), bundles.get(1).path("bundleKey").asText());
        home.path("zones").forEach(zone -> {
            if (!"BUNDLE".equals(zone.path("name").asText())) {
                zone.path("placements").forEach(placement -> assertFalse(placement.has("bundleKey")));
            }
        });
        String response = home.toString();
        for (String forbidden : List.of("recommendationId", "reviewStatus", "reviewHistory", "evidence",
                "priorityCoverage", "utility", "weightedUtility", "costPrice", "stockQuantity",
                "remainingQuantity", "batchCode", "expiryDate")) {
            assertFalse(response.contains(forbidden), "public projection must not contain " + forbidden);
        }
        assertTrue(layout > 0);
    }

    @Test
    void runtimeOpenApiDocumentsThePublicRoutes() throws Exception {
        JsonNode api = json.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode paths = api.path("paths");
        for (String path : List.of("/api/storefront/stores", "/api/storefront/{storeSlug}/home",
                "/api/storefront/{storeSlug}/products", "/api/storefront/{storeSlug}/products/{productId}")) {
            assertTrue(paths.has(path), "Missing OpenAPI path " + path);
        }
        assertFalse(paths.has("/api/system/stores/{storeId}/storefront-visibility"),
                "Public visibility follows Store status; there is no separate toggle");
        for (String path : List.of("/api/storefront/stores", "/api/storefront/{storeSlug}/home",
                "/api/storefront/{storeSlug}/products", "/api/storefront/{storeSlug}/products/{productId}")) {
            JsonNode operation = paths.path(path).path("get");
            assertTrue(operation.has("security"), "Missing explicit public security override on " + path);
            assertTrue(operation.path("security").isArray());
            assertEquals(0, operation.path("security").size(), "Public route must not inherit bearer auth: " + path);
        }
        JsonNode post = paths.path("/api/storefront/{storeSlug}/cart/bundles").path("post");
        assertEquals("addStorefrontCartBundle", post.path("operationId").asText());
        assertEquals("#/components/schemas/BundleAddResult",
                post.at("/responses/200/content/application~1json/schema/$ref").asText());
        assertEquals(2, post.at("/responses/409/content/application~1json/schema/oneOf").size());
        assertTrue(api.at("/components/schemas/HomePlacement/properties/bundleKey").isObject());
        JsonNode productSchema = api.path("components").path("schemas").path("ProductCard");
        assertTrue(productSchema.path("properties").has("availability"));
        assertEquals("[\"AVAILABLE\",\"OUT_OF_STOCK\"]",
                productSchema.path("properties").path("availability").path("enum").toString());
        assertFalse(productSchema.path("properties").has("costPrice"));
        assertFalse(productSchema.path("properties").has("stockQuantity"));
        JsonNode storefrontPage = api.path("components").path("schemas").path("StorefrontProductPage");
        assertTrue(storefrontPage.path("properties").has("items"));
        JsonNode catalogPage = api.path("components").path("schemas").path("ProductPage");
        assertTrue(catalogPage.path("properties").has("content"));
        assertTrue(catalogPage.path("properties").has("totalPages"));
    }

    private int store(String name, boolean active) {
        jdbc.update("INSERT INTO store(name,slug,status) VALUES (?,?,?)", name,
                "f09-" + UUID.randomUUID().toString().substring(0, 13), active ? "ACTIVE" : "INACTIVE");
        return jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
    }

    private String slug(int store) {
        return jdbc.queryForObject("SELECT slug FROM store WHERE store_id=?", String.class, store);
    }

    private int product(int store, String name, boolean requiresExpiry, String status,
            String description, String imageUrl) {
        String sku = "P-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status,
                    description,image_url)
                VALUES (?,?,?,'each',?,10,2,?,?,?)
                """, store, sku, name, requiresExpiry, status, description, imageUrl);
        return jdbc.queryForObject("SELECT MAX(product_id) FROM product WHERE store_id=?", Integer.class, store);
    }

    private void batch(int productId, int quantity, String status, LocalDate expiry) {
        jdbc.update("""
                INSERT INTO inventory_batch(product_id,batch_code,received_at,expiry_date,
                    initial_quantity,remaining_quantity,status)
                VALUES (?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?)
                """, productId, "B-" + UUID.randomUUID(), expiry, quantity, quantity, status);
    }

    private int user(String role, int store) {
        var user = users.createPendingUser("F09 test", UUID.randomUUID() + "@example.test",
                role, store, null, null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?", user.id());
        return user.id();
    }

    private int snapshot(int store, int actor) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        jdbc.update("""
                INSERT INTO utility_snapshot(store_id,created_by,range_start,range_end,as_of_date,
                    config_snapshot,source_hash,matrix_hash,total_weighted_utility,rounding_scale,status)
                VALUES (?,?,?,?,?,'{}',?,?,0,6,'READY')
                """, store, actor, today.minusDays(2), today.minusDays(1), today.minusDays(1),
                "a".repeat(64), "b".repeat(64));
        return jdbc.queryForObject("SELECT MAX(snapshot_id) FROM utility_snapshot WHERE store_id=?",
                Integer.class, store);
    }

    private long recommendation(int store, int snapshot, int actor, String type,
            List<Integer> productIds, boolean approved) {
        String id = UUID.randomUUID().toString().replace("-", "") + "a".repeat(32);
        String key = productIds.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        String policy = """
                {"version":1,"maxBundleSize":4,"minSupportCount":2,"minSupportRatio":0.05,
                 "minConfidence":0.5,"minimumSellableQuantity":1,"ruleVersion":"F06_RULES_V1_1"}
                """;
        Integer anchor = "CROSS_SELL".equals(type) ? productIds.get(0) : null;
        jdbc.update("""
                INSERT INTO recommendation(store_id,utility_snapshot_id,identity_hash,canonical_key,type,
                    anchor_product_id,utility,rule_version,policy_version,policy_snapshot,evidence,generation_eligibility,
                    explanation,review_status,version,created_by,reviewed_by,reviewed_at,review_reason)
                VALUES (?,?,?,?,?,?,10,'F06_RULES_V1_1',1,?,'{}','{}','[]',?,1,?,?,?,?)
                """, store, snapshot, id, key, type, anchor, policy, approved ? "APPROVED" : "PENDING_REVIEW",
                actor, approved ? actor : null, approved ? java.sql.Timestamp.from(java.time.Instant.now()) : null,
                approved ? "approved for test" : null);
        long recommendationId = jdbc.queryForObject(
                "SELECT MAX(recommendation_id) FROM recommendation WHERE store_id=?", Long.class, store);
        for (Integer product : productIds) {
            jdbc.update("INSERT INTO recommendation_item(recommendation_id,store_id,product_id) VALUES (?,?,?)",
                    recommendationId, store, product);
        }
        return recommendationId;
    }

    private long activeBundleLayout(int store, int snapshot, int actor, List<String> types,
            List<Long> recommendationIds, List<List<Integer>> productBlocks) {
        jdbc.update("INSERT INTO campaign(store_id,utility_snapshot_id,name,created_by) VALUES (?,?,?,?)",
                store, snapshot, "Chosen campaign", actor);
        long campaign = jdbc.queryForObject("SELECT MAX(campaign_id) FROM campaign WHERE store_id=?",
                Long.class, store);
        jdbc.update("""
                INSERT INTO storefront_layout(store_id,campaign_id,utility_snapshot_id,role,status,version,name,created_by)
                VALUES (?,?,?,'A','READY',1,'Baseline',?)
                """, store, campaign, snapshot, actor);
        long layoutA = jdbc.queryForObject("SELECT MAX(layout_id) FROM storefront_layout WHERE store_id=?",
                Long.class, store);
        jdbc.update("""
                INSERT INTO storefront_layout(store_id,campaign_id,utility_snapshot_id,role,status,version,name,created_by)
                VALUES (?,?,?,'B','READY',1,'Selected',?)
                """, store, campaign, snapshot, actor);
        long layoutB = jdbc.queryForObject("SELECT MAX(layout_id) FROM storefront_layout WHERE store_id=?",
                Long.class, store);
        jdbc.update("UPDATE campaign SET layout_a_id=?,layout_b_id=?,selected_layout_id=?,decision='CHOOSE_B',"
                + "status='ACTIVE' WHERE store_id=? AND campaign_id=?",
                layoutA, layoutB, layoutB, store, campaign);

        var nextPositionByZone = new java.util.HashMap<String, Integer>();
        int[] blockNumbers = new int[2];
        for (int i = 0; i < recommendationIds.size(); i++) {
            String zone = types.get(i);
            int zoneIndex = "FBT".equals(zone) ? 1 : 0;
            int blockNo = ++blockNumbers[zoneIndex];
            for (Integer productId : productBlocks.get(i)) {
                int position = nextPositionByZone.merge(zone, 1, Integer::sum);
                jdbc.update("""
                        INSERT INTO layout_placement(store_id,layout_id,utility_snapshot_id,recommendation_id,
                            product_id,zone,block_no,position)
                        VALUES (?,?,?,?,?,?,?,?)
                        """, store, layoutB, snapshot, recommendationIds.get(i), productId, zone,
                        blockNo, position);
            }
        }
        return layoutB;
    }

    private JsonNode findStore(JsonNode stores, String slug) {
        for (JsonNode store : stores) if (slug.equals(store.path("storeSlug").asText())) return store;
        return null;
    }
}

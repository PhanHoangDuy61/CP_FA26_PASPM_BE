package vn.edu.fpt.paspm.storefront.cart;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;
import vn.edu.fpt.paspm.utility.UtilityTimeZone;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:f10cart;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "paspm.jwt-secret=01234567890123456789012345678901",
        "paspm.otp-secret=test-only-otp-key-with-at-least-thirty-two-bytes",
        "paspm.bootstrap-email=", "paspm.bootstrap-password=",
        "paspm.layout.max-blocks-by-zone.BUNDLE=10", "paspm.layout.max-products-by-zone.BUNDLE=30",
        "paspm.layout.max-blocks-by-zone.CROSS_SELL=10", "paspm.layout.max-products-by-zone.CROSS_SELL=30",
        "paspm.layout.max-blocks-by-zone.FBT=10", "paspm.layout.max-products-by-zone.FBT=30",
        "paspm.layout.max-blocks-by-zone.BANNER=10", "paspm.layout.max-products-by-zone.BANNER=30"
})
@AutoConfigureMockMvc
class CartIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired UtilityTimeZone timeZone;
    @Autowired ObjectMapper json;

    private int storeId;
    private String base;
    private String customer;

    @BeforeEach
    void setUp() {
        var slug = "cart-" + UUID.randomUUID();
        storeId = store(slug, "ACTIVE");
        base = url(slug);
        customer = token("END_CUSTOMER", null);
    }

    @Test
    void virtualCartAndNoOpClearCreateNoRows() throws Exception {
        call(get(base)).andExpect(status().isOk()).andExpect(jsonPath("$.cartId").doesNotExist())
                .andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.subtotal").value(0)).andExpect(jsonPath("$.updatedAt").doesNotExist());
        call(delete(base).header("If-Match", tag(0))).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0));
        assertEquals(0, count("shopping_cart"));
        assertEquals(0, count("storefront_event"));
    }

    @Test
    void ordinaryLinesUseCurrentPriceAndRecordExactlyOneEventPerChange() throws Exception {
        int product = product(storeId, false);
        batch(product, 10, "ACTIVE", null);
        add(product, 2, 0).andExpect(status().isOk()).andExpect(jsonPath("$.subtotal").value(20));
        long item = line();
        add(product, 1, 1).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3));
        jdbc.update("UPDATE product SET selling_price=12.25 WHERE product_id=?", product);
        call(get(base)).andExpect(status().isOk()).andExpect(jsonPath("$.subtotal").value(36.75))
                .andExpect(jsonPath("$.version").value(2));
        quantity(item, 3, 2).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        quantity(item, 4, 2).andExpect(status().isOk()).andExpect(jsonPath("$.subtotal").value(49));
        call(delete(base + "/items/" + item).header("If-Match", tag(3))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        add(product, 1, 4).andExpect(status().isOk());
        call(delete(base).header("If-Match", tag(5))).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(6)).andExpect(jsonPath("$.subtotal").value(0));
        call(delete(base).header("If-Match", tag(6))).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(6));
        assertEquals(1, count("shopping_cart"));
        assertEquals(0, count("shopping_cart_item"));
        assertEquals(List.of("ITEM_ADDED", "ITEM_ADDED", "ITEM_QUANTITY_CHANGED", "ITEM_REMOVED",
                "ITEM_ADDED", "CART_CLEARED"), jdbc.queryForList(
                "SELECT event_type FROM storefront_event WHERE store_id=? ORDER BY event_id", String.class, storeId));
        assertEquals(10, jdbc.queryForObject("SELECT remaining_quantity FROM inventory_batch WHERE product_id=?",
                Integer.class, product));
    }

    @Test
    void validatesVersionAndStrictBodyBeforeAnyChange() throws Exception {
        int product = product(storeId, false);
        batch(product, 10, "ACTIVE", null);
        String body = item(product, 1);
        call(post(base + "/items").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        for (String invalid : List.of("*", "0", "W/\"0\"", "\"0\", \"1\"", "\"-1\"", "\"9223372036854775808\"")) {
            call(post(base + "/items").header("If-Match", invalid).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        for (String invalid : List.of("{\"productId\":"+product+",\"quantity\":1.5}", "{\"productId\":"+product+",\"quantity\":\"1\"}",
                "{\"productId\":"+product+",\"quantity\":1,\"storeId\":1}", item(product, 0))) {
            call(post(base + "/items").header("If-Match", tag(0)).contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, count("shopping_cart"));
        add(product, 1, 0).andExpect(status().isOk());
        add(product, 1, 0).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CART_VERSION_CONFLICT"));
        call(delete(base).header("If-Match", tag(0))).andExpect(status().isConflict());
        assertEquals(1, count("storefront_event"));
    }

    @Test
    void enforcesRoleAndIsolatesStoreAndCustomer() throws Exception {
        int product = product(storeId, false);
        batch(product, 8, "ACTIVE", null);
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(post(base + "/items")).andExpect(status().isUnauthorized());
        mvc.perform(get(base).header("Authorization", "Bearer " + token("STORE_ADMIN", storeId)))
                .andExpect(status().isForbidden());
        mvc.perform(post(base + "/items").header("Authorization", "Bearer " + token("STORE_ADMIN", storeId)))
                .andExpect(status().isForbidden());
        String other = "cart-" + UUID.randomUUID();
        int otherStore = store(other, "ACTIVE");
        int foreign = product(otherStore, false);
        batch(foreign, 10, "ACTIVE", null);
        add(foreign, 1, 0).andExpect(status().isNotFound());
        add(foreign, 1, 9).andExpect(status().isNotFound());
        add(product, 1, 0).andExpect(status().isOk());
        long item = line();
        call(get(url(other))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        call(post(url(other) + "/items").header("If-Match", tag(0)).contentType("application/json")
                .content(item(foreign, 2))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        call(delete(url(other) + "/items/" + item).header("If-Match", tag(1))).andExpect(status().isNotFound());
        call(delete(url(other) + "/items/" + item).header("If-Match", tag(0))).andExpect(status().isNotFound());
        String anotherCustomer = token("END_CUSTOMER", null);
        mvc.perform(delete(base + "/items/" + item).header("If-Match", tag(0))
                .header("Authorization", "Bearer " + anotherCustomer)).andExpect(status().isNotFound());
        jdbc.update("UPDATE store SET status='INACTIVE' WHERE store_id=?", storeId);
        call(get(base)).andExpect(status().isNotFound());
        call(delete(base).header("If-Match", tag(1))).andExpect(status().isNotFound());
    }

    @Test
    void sellableStockUsesStoreDateAndStaleLinesCanBeReduced() throws Exception {
        int product = product(storeId, true);
        LocalDate today = timeZone.today();
        batch(product, 2, "ACTIVE", today);
        batch(product, 40, "ACTIVE", today.minusDays(1));
        batch(product, 40, "BLOCKED", today.plusDays(1));
        batch(product, 40, "ACTIVE", null);
        add(product, 3, 0).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_SELLABLE_STOCK"));
        add(product, 2, 0).andExpect(status().isOk());
        long item = line();
        quantity(item, 3, 1).andExpect(status().isConflict());
        jdbc.update("UPDATE inventory_batch SET remaining_quantity=0 WHERE product_id=? AND expiry_date=?", product, today);
        call(get(base)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].state").value("OUT_OF_STOCK"));
        int adminId = users.createPendingUser("Cart test admin", UUID.randomUUID() + "@example.test",
                "STORE_ADMIN", storeId, null, null).id();
        jdbc.update("UPDATE product SET status='INACTIVE', deleted_at=CURRENT_TIMESTAMP, deleted_by=? WHERE product_id=?",
                adminId, product);
        add(product, 1, 1).andExpect(status().isNotFound());
        quantity(item, 3, 1).andExpect(status().isNotFound());
        quantity(item, 1, 1).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.items[0].state").value("NO_LONGER_AVAILABLE"))
                .andExpect(jsonPath("$.items[0].name").doesNotExist()).andExpect(jsonPath("$.subtotal").doesNotExist());
        call(delete(base + "/items/" + item).header("If-Match", tag(2))).andExpect(status().isOk());
    }

    @Test
    void singleItemWritesAtSellableStockBoundaryRejectTheEntireExcess() throws Exception {
        int product = product(storeId, false);
        batch(product, 8, "ACTIVE", null);
        add(product, 5, 0).andExpect(status().isOk());
        add(product, 4, 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_SELLABLE_STOCK"));
        call(get(base)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(5));
        add(product, 3, 1).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(8));
        long item = line();
        quantity(item, 5, 2).andExpect(status().isOk());
        quantity(item, 9, 3).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_SELLABLE_STOCK"));
        call(get(base)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.items[0].quantity").value(5));
        quantity(item, 8, 3).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.items[0].quantity").value(8));
        assertEquals(4, count("storefront_event"));
    }

    @Test
    void cartCapAppliesAfterMergingQuantities() throws Exception {
        int product = product(storeId, false);
        batch(product, 150, "ACTIVE", null);
        add(product, 100, 0).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CART_QUANTITY_LIMIT"));
        add(product, 98, 0).andExpect(status().isOk());
        quantity(line(), 99, 1).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(99));
        add(product, 1, 2).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CART_QUANTITY_LIMIT"));
        quantity(line(), 100, 2).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CART_QUANTITY_LIMIT"));
        quantity(line(), 98, 2).andExpect(status().isOk());
        add(product, 1, 3).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(99));
        call(get(base)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.items[0].quantity").value(99));
        assertEquals(4, count("storefront_event"));
    }

    @Test
    void concurrentFirstWritesWithSameVersionProduceOneChange() throws Exception {
        int product = product(storeId, false);
        batch(product, 10, "ACTIVE", null);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Integer> write = () -> {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));
            return add(product, 1, 0).andReturn().getResponse().getStatus();
        };
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(write);
            var b = pool.submit(write);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(List.of(200, 409), java.util.stream.Stream.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))
                    .sorted().toList());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, count("shopping_cart"));
        assertEquals(1, count("shopping_cart_item"));
        assertEquals(1, count("storefront_event"));
    }

    @Test
    void manualBundleAddsOnlyAvailableRoomAndKeepsOrdinaryLines() throws Exception {
        int stocked = product(storeId, false);
        int out = product(storeId, false);
        int capped = product(storeId, false);
        int hidden = product(storeId, false);
        batch(stocked, 2, "ACTIVE", null);
        batch(capped, 110, "ACTIVE", null);
        Block block = activeBlock(List.of(stocked, out, capped, hidden));
        jdbc.update("UPDATE product SET status='INACTIVE' WHERE product_id=?", hidden);
        add(stocked, 1, 0).andExpect(status().isOk());
        add(capped, 99, 1).andExpect(status().isOk());
        String key = bundleKey();
        assertFalse(key.contains("="));
        bundle(key, 2).andExpect(status().isOk())
                .andExpect(jsonPath("$.cart.version").value(3))
                .andExpect(jsonPath("$.cart.items.length()").value(2))
                .andExpect(jsonPath("$.cart.items[0].quantity").value(2))
                .andExpect(jsonPath("$.cart.items[0].addedFromBundle").value(true))
                .andExpect(jsonPath("$.components.length()").value(4))
                .andExpect(jsonPath("$.components[0].productId").value(stocked))
                .andExpect(jsonPath("$.components[0].requested").value(1))
                .andExpect(jsonPath("$.components[0].added").value(1))
                .andExpect(jsonPath("$.components[1].added").value(0))
                .andExpect(jsonPath("$.components[2].added").value(0))
                .andExpect(jsonPath("$.components[3].added").value(0));
        assertBundleEvent(block, "BUNDLE_ADDED", "MANUAL", 1, null);
        call(get(base)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(2));
        assertEquals(2, jdbc.queryForObject("SELECT remaining_quantity FROM inventory_batch WHERE product_id=?",
                Integer.class, stocked));
    }

    @Test
    void allSkippedStillCommitsOneRejectionAndLeavesVirtualCartUnchanged() throws Exception {
        int one = product(storeId, false);
        int two = product(storeId, false);
        Block block = activeBlock(List.of(one, two));
        String key = bundleKey();
        bundle(key, 0).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUNDLE_ADD_REJECTED"))
                .andExpect(jsonPath("$.components.length()").value(2))
                .andExpect(jsonPath("$.components[0].productId").value(one))
                .andExpect(jsonPath("$.components[0].added").value(0))
                .andExpect(jsonPath("$.components[1].productId").value(two))
                .andExpect(jsonPath("$.components[1].added").value(0));
        assertBundleEvent(block, "BUNDLE_ADD_REJECTED", "MANUAL", 0, "BUNDLE_ADD_REJECTED");
        call(get(base)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        assertEquals(0, count("shopping_cart"));
        assertEquals(0, count("shopping_cart_item"));
    }

    @Test
    void exactReadyRecommendationIsMinedEvenWithMissingPlacementProvenanceButAmbiguityIsManual() throws Exception {
        int one = product(storeId, false);
        int two = product(storeId, false);
        batch(one, 10, "ACTIVE", null);
        batch(two, 10, "ACTIVE", null);
        Block block = activeBlock(List.of(one, two));
        recommendation(block, List.of(one, two), 1);
        recommendation(block, List.of(one, two), 20); // Approved but not currently ready.
        String key = bundleKey();
        bundle(key, 0).andExpect(status().isOk()).andExpect(jsonPath("$.cart.items[0].addedFromBundle").value(true));
        assertBundleEvent(block, "BUNDLE_ADDED", "MINED", 2, null);
        recommendation(block, List.of(one, two), 1);
        bundle(key, 1).andExpect(status().isOk());
        assertEquals(List.of("MINED", "MANUAL"), jdbc.queryForList("""
                SELECT bundle_origin FROM storefront_event WHERE store_id=? ORDER BY event_id
                """, String.class, storeId));
    }

    @Test
    void invalidForeignAndStaleKeysAndInvalidRequestsRecordNoEvent() throws Exception {
        int one = product(storeId, false);
        int two = product(storeId, false);
        Block block = activeBlock(List.of(one, two));
        String key = bundleKey();
        String other = "cart-" + UUID.randomUUID();
        store(other, "ACTIVE");
        call(bundleRequest(url(other), key, 0)).andExpect(status().isNotFound());
        bundle("forged", 0).andExpect(status().isNotFound());
        String tampered = (key.startsWith("A") ? "B" : "A") + key.substring(1);
        bundle(tampered, 0).andExpect(status().isNotFound());
        mvc.perform(bundleRequest(base, key, 0)).andExpect(status().isUnauthorized());
        mvc.perform(bundleRequest(base, key, 0).header("Authorization", "Bearer " + token("STORE_ADMIN", storeId)))
                .andExpect(status().isForbidden());
        call(post(base + "/bundles").contentType("application/json").content("{\"bundleKey\":\"" + key + "\"}"))
                .andExpect(status().isBadRequest());
        for (String body : List.of("{}", "{\"bundleKey\":42}",
                "{\"bundleKey\":\"" + key + "\",\"bundleQuantity\":2}")) {
            call(post(base + "/bundles").header("If-Match", tag(0)).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        jdbc.update("UPDATE storefront_layout SET version=2 WHERE layout_id=?", block.layout());
        bundle(key, 0).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BUNDLE_UNAVAILABLE"));
        assertEquals(0, count("storefront_event"));
        assertEquals(0, count("shopping_cart"));
    }

    @Test
    void simultaneousBundleWritesWithSameCartVersionCommitOnlyOneEvent() throws Exception {
        int one = product(storeId, false);
        int two = product(storeId, false);
        batch(one, 10, "ACTIVE", null);
        batch(two, 10, "ACTIVE", null);
        Block block = activeBlock(List.of(one, two));
        String key = bundleKey();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<org.springframework.mock.web.MockHttpServletResponse> write = () -> {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));
            return bundle(key, 0).andReturn().getResponse();
        };
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(write);
            var b = pool.submit(write);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            var responses = List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertEquals(List.of(200, 409), responses.stream().map(r -> r.getStatus()).sorted().toList());
            var conflict = responses.stream().filter(r -> r.getStatus() == 409).findFirst().orElseThrow();
            assertEquals("CART_VERSION_CONFLICT", json.readTree(conflict.getContentAsString()).path("code").asText());
        } finally {
            pool.shutdownNow();
        }
        assertBundleEvent(block, "BUNDLE_ADDED", "MANUAL", 2, null);
        assertEquals(1, count("shopping_cart"));
        assertEquals(2, count("shopping_cart_item"));
    }

    private String bundleKey() throws Exception {
        return json.readTree(mvc.perform(get(base.substring(0, base.length() - "/cart".length()) + "/home")).andExpect(status().isOk())
                .andExpect(jsonPath("$.zones[0].placements[0].bundleKey").isString())
                .andReturn().getResponse().getContentAsString())
                .path("zones").get(0).path("placements").get(0).path("bundleKey").asText();
    }

    private org.springframework.test.web.servlet.ResultActions bundle(String key, long version) throws Exception {
        return call(bundleRequest(base, key, version));
    }

    private MockHttpServletRequestBuilder bundleRequest(String cartUrl, String key, long version) throws Exception {
        return post(cartUrl + "/bundles").header("If-Match", tag(version)).contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("bundleKey", key)));
    }

    private record Block(long campaign, long layout, int snapshot, int actor) {}

    private Block activeBlock(List<Integer> products) {
        int actor = users.createPendingUser("Test merchandiser", UUID.randomUUID() + "@example.test",
                "MERCHANDISER", storeId, null, null).id();
        LocalDate asOf = timeZone.today().minusDays(1);
        jdbc.update("""
                INSERT INTO utility_snapshot(store_id,created_by,range_start,range_end,as_of_date,
                    config_snapshot,source_hash,matrix_hash,total_weighted_utility,rounding_scale,status)
                VALUES (?,?,?,?,?,'{}',?,?,0,6,'READY')
                """, storeId, actor, asOf.minusDays(1), asOf, asOf, "a".repeat(64), "b".repeat(64));
        int snapshot = jdbc.queryForObject("SELECT snapshot_id FROM utility_snapshot WHERE store_id=?", Integer.class, storeId);
        jdbc.update("INSERT INTO campaign(store_id,utility_snapshot_id,name,created_by) VALUES (?,?,?,?)",
                storeId, snapshot, "Bundle campaign", actor);
        long campaign = jdbc.queryForObject("SELECT campaign_id FROM campaign WHERE store_id=?", Long.class, storeId);
        jdbc.update("""
                INSERT INTO storefront_layout(store_id,campaign_id,utility_snapshot_id,role,status,version,name,created_by)
                VALUES (?,?,?,'B','READY',1,'Selected',?)
                """, storeId, campaign, snapshot, actor);
        long layout = jdbc.queryForObject("SELECT layout_id FROM storefront_layout WHERE store_id=?", Long.class, storeId);
        jdbc.update("""
                INSERT INTO storefront_layout_revision(store_id,layout_id,version,placements_json,content_sha256,created_by)
                VALUES (?,?,1,'[]',?,?)
                """, storeId, layout, "c".repeat(64), actor);
        jdbc.update("""
                UPDATE campaign SET layout_b_id=?,selected_layout_id=?,decision='CHOOSE_B',status='ACTIVE'
                WHERE campaign_id=?
                """, layout, layout, campaign);
        for (int i = 0; i < products.size(); i++) {
            jdbc.update("""
                    INSERT INTO layout_placement(store_id,layout_id,utility_snapshot_id,product_id,zone,block_no,position)
                    VALUES (?,?,?,?,'BUNDLE',1,?)
                    """, storeId, layout, snapshot, products.get(i), i + 1);
        }
        return new Block(campaign, layout, snapshot, actor);
    }

    private void recommendation(Block block, List<Integer> products, int minimum) {
        String identity = UUID.randomUUID().toString().replace("-", "") + "a".repeat(32);
        String key = products.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        String policy = """
                {"version":1,"maxBundleSize":4,"minSupportCount":2,"minSupportRatio":0.05,
                 "minConfidence":0.5,"minimumSellableQuantity":%d,"ruleVersion":"F06_RULES_V1_1"}
                """.formatted(minimum);
        jdbc.update("""
                INSERT INTO recommendation(store_id,utility_snapshot_id,identity_hash,canonical_key,type,utility,
                    rule_version,policy_version,policy_snapshot,evidence,generation_eligibility,explanation,
                    review_status,version,created_by,reviewed_by,reviewed_at,review_reason)
                VALUES (?,?,?,?,'BUNDLE',10,'F06_RULES_V1_1',1,?,'{}','{}','[]','APPROVED',1,?,?,CURRENT_TIMESTAMP,'Approved for test')
                """, storeId, block.snapshot(), identity, key, policy, block.actor(), block.actor());
        long recommendation = jdbc.queryForObject("SELECT recommendation_id FROM recommendation WHERE store_id=? AND identity_hash=?",
                Long.class, storeId, identity);
        for (int product : products) {
            jdbc.update("INSERT INTO recommendation_item(recommendation_id,store_id,product_id) VALUES (?,?,?)",
                    recommendation, storeId, product);
        }
    }

    private void assertBundleEvent(Block block, String type, String origin, int units, String failure) {
        var rows = jdbc.query("""
                SELECT event_type,campaign_id,layout_id,layout_version,block_no,bundle_origin,added_product_units,failure_code
                FROM storefront_event WHERE store_id=? AND event_type LIKE 'BUNDLE_%'
                """, (rs, row) -> {
            assertEquals(type, rs.getString("event_type"));
            assertEquals(block.campaign(), rs.getLong("campaign_id"));
            assertEquals(block.layout(), rs.getLong("layout_id"));
            assertEquals(1L, rs.getLong("layout_version"));
            assertEquals(1, rs.getInt("block_no"));
            assertEquals(origin, rs.getString("bundle_origin"));
            assertEquals(units, rs.getInt("added_product_units"));
            assertEquals(failure, rs.getString("failure_code"));
            return type;
        }, storeId);
        assertEquals(List.of(type), rows);
    }

    private org.springframework.test.web.servlet.ResultActions call(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + customer));
    }

    private org.springframework.test.web.servlet.ResultActions add(int product, int quantity, long version) throws Exception {
        return call(post(base + "/items").header("If-Match", tag(version)).contentType("application/json")
                .content(item(product, quantity)));
    }

    private org.springframework.test.web.servlet.ResultActions quantity(long item, int quantity, long version) throws Exception {
        return call(patch(base + "/items/" + item).header("If-Match", tag(version)).contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("quantity", quantity))));
    }

    private static String item(int product, int quantity) {
        return "{\"productId\":" + product + ",\"quantity\":" + quantity + "}";
    }

    private static String tag(long version) { return "\"" + version + "\""; }
    private static String url(String slug) { return "/api/storefront/" + slug + "/cart"; }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE store_id=?", Integer.class, storeId);
    }

    private long line() {
        return jdbc.queryForObject("SELECT item_id FROM shopping_cart_item WHERE store_id=?", Long.class, storeId);
    }

    private String token(String role, Integer store) {
        var user = users.createPendingUser("Cart test", UUID.randomUUID() + "@example.test", role, store, null, null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?", user.id());
        return auth.token(users.findById(user.id())).accessToken();
    }

    private int store(String slug, String status) {
        jdbc.update("INSERT INTO store(name,slug,status) VALUES (?,?,?)", slug, slug, status);
        return jdbc.queryForObject("SELECT store_id FROM store WHERE slug=?", Integer.class, slug);
    }

    private int product(int store, boolean requiresExpiry) {
        var sku = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status)
                VALUES (?,?,?,'each',?,10,2,'ACTIVE')
                """, store, sku, sku, requiresExpiry);
        return jdbc.queryForObject("SELECT product_id FROM product WHERE store_id=? AND sku=?", Integer.class, store, sku);
    }

    private void batch(int product, int quantity, String status, LocalDate date) {
        jdbc.update("""
                INSERT INTO inventory_batch(product_id,batch_code,received_at,expiry_date,initial_quantity,remaining_quantity,status)
                VALUES (?,?,CURRENT_TIMESTAMP,?,?,?,?)
                """, product, UUID.randomUUID().toString(), date, quantity, quantity, status);
    }
}

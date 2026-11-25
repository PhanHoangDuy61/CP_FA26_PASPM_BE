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
        "paspm.bootstrap-email=", "paspm.bootstrap-password="
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
    void cartCapAppliesAfterMergingQuantities() throws Exception {
        int product = product(storeId, false);
        batch(product, 150, "ACTIVE", null);
        add(product, 100, 0).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CART_QUANTITY_LIMIT"));
        add(product, 99, 0).andExpect(status().isOk());
        add(product, 1, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CART_QUANTITY_LIMIT"));
        quantity(line(), 100, 1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CART_QUANTITY_LIMIT"));
        assertEquals(1, count("storefront_event"));
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

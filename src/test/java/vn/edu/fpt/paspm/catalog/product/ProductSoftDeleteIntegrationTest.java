package vn.edu.fpt.paspm.catalog.product;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.User;
import vn.edu.fpt.paspm.user.UserRepository;

/**
 * DEC-20261124-03: STORE_ADMIN soft-deletes a product of their own Store. 409 (+ code, reason) while the product still has
 * sellable stock or sits in a PUBLISHED/ACTIVE campaign layout; otherwise the row stays (history, snapshots, mining, layouts,
 * SKU) and is excluded from catalog queries before pagination. No restore.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:product_soft_delete;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
        "paspm.otp-secret=test-only-otp-key-with-at-least-thirty-two-bytes",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=true", "paspm.jwt-secret=01234567890123456789012345678901",
        "paspm.bootstrap-email=", "paspm.bootstrap-password=",
        "paspm.store-time-zone=Asia/Ho_Chi_Minh"
})
@AutoConfigureMockMvc
class ProductSoftDeleteIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthService auth;
    @Autowired ObjectMapper json;

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    private int storeId;
    private int otherStoreId;
    private User admin;
    private String adminToken;
    private String merchandiserToken;
    private String otherAdminToken;
    private String systemToken;

    @BeforeEach
    void setUp() {
        storeId = store("Soft delete A " + UUID.randomUUID());
        otherStoreId = store("Soft delete B " + UUID.randomUUID());
        admin = active("sd-a-" + UUID.randomUUID() + "@example.test", "STORE_ADMIN", storeId);
        adminToken = token(admin);
        merchandiserToken = token(active("sd-m-" + UUID.randomUUID() + "@example.test", "MERCHANDISER", storeId));
        otherAdminToken = token(active("sd-b-" + UUID.randomUUID() + "@example.test", "STORE_ADMIN", otherStoreId));
        systemToken = token(active("sd-s-" + UUID.randomUUID() + "@example.test", "SYSTEM_ADMIN", null));
    }

    @Test
    void onlyTheStoreAdminOfTheOwningStoreCanDeleteAndEveryoneElseIsRefused() throws Exception {
        int product = product("Owned");
        mvc.perform(delete("/api/products/{id}", product)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/products/{id}", product).header("Authorization", "Bearer " + merchandiserToken))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/{id}", product).header("Authorization", "Bearer " + systemToken))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/{id}", product).header("Authorization", "Bearer " + otherAdminToken))
                .andExpect(status().isNotFound());
        assertNull(deletedAt(product), "refused requests never touch the row");
        mvc.perform(delete("/api/products/{id}", 987654321).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/products/{id}", product).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/products/{id}", product).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void softDeleteKeepsTheRowAndHistoryButHidesTheProductFromEveryCatalogRoute() throws Exception {
        int product = product("History product");
        int batch = batch(product, today.minusDays(2), 5, "ACTIVE", 5); // expired: no sellable stock, so the delete is allowed
        int sku = jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE product_id=? AND sku LIKE 'SKU-%'", Integer.class, product);
        assertEquals(1, sku);
        String skuBefore = jdbc.queryForObject("SELECT sku FROM product WHERE product_id=?", String.class, product);

        mvc.perform(delete("/api/products/{id}", product).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // the row, its SKU and its batches stay for history; the product is inactive and audited
        var row = jdbc.queryForMap("SELECT sku,status,deleted_at,deleted_by FROM product WHERE product_id=?", product);
        assertEquals(skuBefore, row.get("sku"));
        assertEquals("INACTIVE", row.get("status"));
        assertNotNull(row.get("deleted_at"));
        assertEquals(admin.id(), ((Number) row.get("deleted_by")).intValue());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_batch WHERE batch_id=?", Integer.class, batch));

        String auth = "Bearer " + adminToken;
        mvc.perform(get("/api/products/{id}", product).header("Authorization", auth)).andExpect(status().isNotFound());
        mvc.perform(put("/api/products/{id}", product).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(productJson("Renamed", false))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/products/{id}/status", product).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}")).andExpect(status().isNotFound());
        assertEquals("INACTIVE", jdbc.queryForObject("SELECT status FROM product WHERE product_id=?", String.class, product),
                "a deleted product can never be re-activated: there is no restore");
        mvc.perform(get("/api/products/{id}/batches", product).header("Authorization", auth)).andExpect(status().isNotFound());
        mvc.perform(post("/api/products/{id}/batches", product).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"receivedAt\":\"2026-10-01T10:00:00Z\",\"initialQuantity\":1}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/inventory-batches/{id}", batch).header("Authorization", auth)).andExpect(status().isNotFound());
        mvc.perform(patch("/api/inventory-batches/{id}", batch).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"remainingQuantity\":1}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/{id}/inventory-summary", product).header("Authorization", auth)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products").param("search", "History product").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void deletedProductsAreExcludedBeforePaginationAndFromTheSimilarNameCheck() throws Exception {
        List<Integer> ids = new java.util.ArrayList<>();
        for (String name : List.of("Alpha item", "Bravo thing", "Charlie widget", "Delta gadget", "Echo gizmo")) ids.add(product(name));
        for (int index : new int[] {1, 3}) {
            mvc.perform(delete("/api/products/{id}", ids.get(index)).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNoContent());
        }
        String auth = "Bearer " + adminToken;
        mvc.perform(get("/api/products").param("pageSize", "2").param("page", "0").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Alpha item"))
                .andExpect(jsonPath("$.content[1].name").value("Charlie widget"));
        mvc.perform(get("/api/products").param("pageSize", "2").param("page", "1").header("Authorization", auth))
                .andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.content[0].name").value("Echo gizmo"));
        mvc.perform(get("/api/products").param("pageSize", "2").param("status", "INACTIVE").header("Authorization", auth))
                .andExpect(jsonPath("$.totalElements").value(0));
        // MERCHANDISER reads the same filtered catalogue
        mvc.perform(get("/api/products").header("Authorization", "Bearer " + merchandiserToken))
                .andExpect(jsonPath("$.totalElements").value(3));

        // a deleted product's name does not trigger the similar-name confirmation; its SKU is never reissued
        String deletedSku = jdbc.queryForObject("SELECT sku FROM product WHERE product_id=?", String.class, ids.get(1));
        mvc.perform(post("/api/products").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Bravo thing", false)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sku", org.hamcrest.Matchers.not(deletedSku)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE sku=? AND store_id=?", Integer.class, deletedSku, storeId));
    }

    @Test
    void sellableStockBlocksTheDeleteWithCodeReasonAndQuantity() throws Exception {
        int product = product("With stock");
        batch(product, today, 4, "ACTIVE", 4);                  // expires today: still sellable
        batch(product, today.plusDays(9), 3, "ACTIVE", 3);
        batch(product, today.minusDays(1), 9, "ACTIVE", 9);      // expired: not sellable
        batch(product, today.plusDays(9), 8, "BLOCKED", 8);      // blocked: not sellable
        batch(product, today.plusDays(9), 2, "ACTIVE", 0);       // empty: not sellable
        mvc.perform(delete("/api/products/{id}", product).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_HAS_SELLABLE_STOCK"))
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.reason").value(org.hamcrest.Matchers.containsString("7")))
                .andExpect(jsonPath("$.sellableQuantity").value(7))
                .andExpect(jsonPath("$.campaigns.length()").value(0));
        assertNull(deletedAt(product));
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM product WHERE product_id=?", String.class, product));

        // once only expired / blocked / empty units remain, the delete is allowed
        jdbc.update("UPDATE inventory_batch SET remaining_quantity=0 WHERE product_id=? AND status='ACTIVE' AND expiry_date>=?", product, today);
        mvc.perform(delete("/api/products/{id}", product).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        assertNotNull(deletedAt(product));
    }

    @Test
    void aProductInAPublishedOrActiveCampaignLayoutCannotBeDeletedButDraftAndEndedDoNotBlock() throws Exception {
        for (String live : List.of("PUBLISHED", "ACTIVE")) {
            int product = product("Live " + live);
            long campaign = campaign(live, product);
            mvc.perform(delete("/api/products/{id}", product).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PRODUCT_IN_LIVE_CAMPAIGN"))
                    .andExpect(jsonPath("$.reason").isNotEmpty())
                    .andExpect(jsonPath("$.sellableQuantity").value(0))
                    .andExpect(jsonPath("$.campaigns.length()").value(1))
                    .andExpect(jsonPath("$.campaigns[0].campaignId").value(campaign))
                    .andExpect(jsonPath("$.campaigns[0].status").value(live));
            assertNull(deletedAt(product));
        }
        for (String quiet : List.of("DRAFT", "ENDED")) {
            int product = product("Quiet " + quiet);
            campaign(quiet, product);
            mvc.perform(delete("/api/products/{id}", product).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNoContent());
            // layout history is kept and still points at the soft-deleted row
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM layout_placement WHERE product_id=?", Integer.class, product));
        }
    }

    @Test
    void whenStockAndCampaignBothApplyTheStockCodeComesFirstAndCampaignsAreIncluded() throws Exception {
        int product = product("Both");
        batch(product, today.plusDays(20), 5, "ACTIVE", 5);
        long campaign = campaign("ACTIVE", product);
        mvc.perform(delete("/api/products/{id}", product).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_HAS_SELLABLE_STOCK"))
                .andExpect(jsonPath("$.sellableQuantity").value(5))
                .andExpect(jsonPath("$.campaigns[0].campaignId").value(campaign))
                .andExpect(jsonPath("$.campaigns[0].status").value("ACTIVE"));
    }

    @Test
    void aForeignStoreProductIsNotFoundAndStaysUntouched() throws Exception {
        int mine = product("Mine");
        jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,?,?,FALSE,1,1,'ACTIVE')",
                otherStoreId, "SKU-FOREIGN-" + UUID.randomUUID().toString().substring(0, 8), "Foreign", "each");
        int foreign = jdbc.queryForObject("SELECT MAX(product_id) FROM product WHERE store_id=?", Integer.class, otherStoreId);
        mvc.perform(delete("/api/products/{id}", foreign).header("Authorization", "Bearer " + adminToken)).andExpect(status().isNotFound());
        assertNull(deletedAt(foreign));
        mvc.perform(delete("/api/products/{id}", mine).header("Authorization", "Bearer " + adminToken)).andExpect(status().isNoContent());
    }

    @Test
    void seasonAssignmentsOfADeletedProductAreKeptLabelledAsDeletedAndNewAssignmentsAreRejected() throws Exception {
        int kept = product("Season kept");
        int doomed = product("Season doomed");
        String merchant = "Bearer " + merchandiserToken;
        String season = mvc.perform(post("/api/utility/seasons").header("Authorization", merchant).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tet " + UUID.randomUUID() + "\",\"startDate\":\"" + today + "\",\"endDate\":\"" + today.plusDays(20) + "\",\"weight\":0.5}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int seasonId = json.readTree(season).get("seasonId").asInt();
        for (int id : new int[] {kept, doomed}) {
            mvc.perform(put("/api/utility/seasons/{s}/products/{p}", seasonId, id).header("Authorization", merchant)).andExpect(status().isCreated());
        }
        mvc.perform(delete("/api/products/{id}", doomed).header("Authorization", "Bearer " + adminToken)).andExpect(status().isNoContent());

        // the assignment and its history are kept; the list labels the product as deleted
        mvc.perform(get("/api/utility/seasons/{s}/products", seasonId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].productId").value(kept)).andExpect(jsonPath("$.items[0].deleted").value(false))
                .andExpect(jsonPath("$.items[1].productId").value(doomed)).andExpect(jsonPath("$.items[1].deleted").value(true))
                .andExpect(jsonPath("$.items[1].status").value("INACTIVE"));
        // a new assignment of a deleted product is rejected (also the idempotent re-assign), another Store's stays 404
        mvc.perform(put("/api/utility/seasons/{s}/products/{p}", seasonId, doomed).header("Authorization", merchant))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_DELETED"));
        // the existing assignment can still be removed
        mvc.perform(delete("/api/utility/seasons/{s}/products/{p}", seasonId, doomed).header("Authorization", merchant))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/utility/seasons/{s}/products/{p}", seasonId, kept).header("Authorization", merchant))
                .andExpect(status().isNoContent());
    }

    // ---- helpers

    private Timestamp deletedAt(int product) {
        return jdbc.queryForObject("SELECT deleted_at FROM product WHERE product_id=?", Timestamp.class, product);
    }

    private int store(String name) {
        jdbc.update("INSERT INTO store(name) VALUES (?)", name);
        return jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
    }

    private User active(String email, String role, Integer store) {
        User user = users.createPendingUser("Soft Delete Test", email, role, store, passwords.encode("TestPassword1!"), null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=? WHERE user_id=?", Timestamp.from(Instant.now()), user.id());
        return users.findById(user.id());
    }

    private String token(User user) { return auth.token(user).accessToken(); }

    private String productJson(String name, boolean requiresExpiry) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("name", name); body.put("unit", "each"); body.put("requiresExpiry", requiresExpiry);
        body.put("sellingPrice", 1); body.put("costPrice", 1); body.put("status", "ACTIVE"); body.put("confirmSimilarName", true);
        return json.writeValueAsString(body);
    }

    private int product(String name) throws Exception {
        String response = mvc.perform(post("/api/products").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(productJson(name, false)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asInt();
    }

    private int batch(int product, LocalDate expiry, int initial, String status, int remaining) {
        jdbc.update("""
                INSERT INTO inventory_batch(product_id,batch_code,received_at,expiry_date,initial_quantity,remaining_quantity,status)
                VALUES (?,?,CURRENT_TIMESTAMP,?,?,?,?)
                """, product, "B-" + UUID.randomUUID().toString().substring(0, 12), expiry, initial, remaining, status);
        return jdbc.queryForObject("SELECT MAX(batch_id) FROM inventory_batch WHERE product_id=?", Integer.class, product);
    }

    /** One campaign with layout A holding the product as a BASELINE placement (the whole F07/F08 chain is not needed here). */
    private long campaign(String status, int product) {
        jdbc.update("""
                INSERT INTO utility_snapshot(store_id,created_by,range_start,range_end,as_of_date,config_snapshot,source_hash,matrix_hash,
                                             total_weighted_utility,rounding_scale,status)
                VALUES (?,?,CURRENT_DATE,CURRENT_DATE,CURRENT_DATE,'{}',?,?,1,6,'READY')
                """, storeId, admin.id(), "a".repeat(64), "b".repeat(64));
        int snapshot = jdbc.queryForObject("SELECT MAX(snapshot_id) FROM utility_snapshot WHERE store_id=?", Integer.class, storeId);
        jdbc.update("INSERT INTO campaign(store_id,utility_snapshot_id,name,created_by,status) VALUES (?,?,?,?,?)",
                storeId, snapshot, "Campaign " + UUID.randomUUID(), admin.id(), status);
        long campaign = jdbc.queryForObject("SELECT MAX(campaign_id) FROM campaign WHERE store_id=?", Long.class, storeId);
        jdbc.update("INSERT INTO storefront_layout(store_id,campaign_id,utility_snapshot_id,role,name,created_by) VALUES (?,?,?,'A','A baseline',?)",
                storeId, campaign, snapshot, admin.id());
        long layout = jdbc.queryForObject("SELECT MAX(layout_id) FROM storefront_layout WHERE store_id=?", Long.class, storeId);
        jdbc.update("""
                INSERT INTO layout_placement(store_id,layout_id,utility_snapshot_id,recommendation_id,product_id,zone,block_no,position)
                VALUES (?,?,?,NULL,?,'BASELINE',1,1)
                """, storeId, layout, snapshot, product);
        return campaign;
    }
}

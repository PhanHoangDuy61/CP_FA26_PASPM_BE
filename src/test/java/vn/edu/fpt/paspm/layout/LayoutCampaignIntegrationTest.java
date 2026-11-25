package vn.edu.fpt.paspm.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.recommendation.RecommendationContracts.RecType;
import vn.edu.fpt.paspm.recommendation.RecommendationContracts.RecView;
import vn.edu.fpt.paspm.recommendation.RecommendationContracts.ReviewStatus;
import vn.edu.fpt.paspm.recommendation.RecommendationService;
import vn.edu.fpt.paspm.user.UserRepository;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:f07f08;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "paspm.jwt-secret=01234567890123456789012345678901",
        "paspm.otp-secret=test-only-otp-key-with-at-least-thirty-two-bytes",
        "paspm.bootstrap-email=", "paspm.bootstrap-password=",
        "paspm.store-time-zone=Asia/Ho_Chi_Minh"
})
@AutoConfigureMockMvc
class LayoutCampaignIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired ObjectMapper json;
    @Autowired LayoutPolicyProperties layoutPolicy;
    @MockitoBean RecommendationService recommendations;

    int storeId;
    int foreignStoreId;
    int snapshotId;
    List<Integer> products;
    String merchantToken;
    String adminToken;
    String foreignToken;
    int merchantUserId;
    LocalDate trainingDay;
    LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        reset(recommendations);
        today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        trainingDay = today.minusDays(5);
        storeId = store("F07 Store");
        foreignStoreId = store("F07 Foreign");
        merchantToken = token("MERCHANDISER", storeId);
        adminToken = token("STORE_ADMIN", storeId);
        foreignToken = token("MERCHANDISER", foreignStoreId);

        products = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            jdbc.update("""
                    INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status)
                    VALUES (?,?,?,'each',FALSE,10,1,'ACTIVE')
                    """, storeId, "LAYOUT-" + i, "Layout product " + i);
            int product = jdbc.queryForObject("SELECT MAX(product_id) FROM product", Integer.class);
            products.add(product);
            jdbc.update("""
                    INSERT INTO inventory_batch(product_id,batch_code,received_at,initial_quantity,remaining_quantity,status)
                    VALUES (?, ?, CURRENT_TIMESTAMP, 10, 10, 'ACTIVE')
                    """, product, "AVAILABLE-" + i);
        }
        transaction(trainingDay, products, new String[] {"10.00", "10.00", "5.00"}, new String[] {"2.00", "4.00", "1.00"});
        transaction(trainingDay, List.of(products.get(1)), new String[] {"10.00"}, new String[] {"4.00"});
        snapshotId = createSnapshot();
    }

    @Test
    void baselineUsesTransactionFrequencyThenProductIdForTies() throws Exception {
        JsonNode created = createCampaign(List.of());
        JsonNode baseline = read(get("/api/layouts/" + created.path("layoutAId").asLong(), merchantToken)
                .andExpect(status().isOk()));
        assertEquals(products.get(1), baseline.path("placements").get(0).path("productId").asInt());
        assertEquals("A", baseline.path("role").asText());
        assertEquals("READY", baseline.path("status").asText());
        assertEquals(List.of(products.get(1), products.get(0), products.get(2)),
                java.util.stream.StreamSupport.stream(baseline.path("placements").spliterator(), false)
                        .map(item -> item.path("productId").asInt()).toList());
    }

    @Test
    void storeIsolationRolesAndReadOnlyBaselineAreEnforced() throws Exception {
        long campaign = createCampaign(List.of()).path("campaignId").asLong();
        long layoutA = campaign(campaign).path("layoutAId").asLong();

        get("/api/layouts/" + layoutA, merchantToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("A"))
                .andExpect(jsonPath("$.valid").value(true));
        put("/api/layouts/" + layoutA + "/placements", merchantToken,
                "{\"expectedVersion\":1,\"placements\":[]}").andExpect(status().isConflict());
        get("/api/layouts/" + layoutA, foreignToken).andExpect(status().isNotFound());
        get("/api/campaigns/" + campaign, foreignToken).andExpect(status().isNotFound());
        get("/api/campaigns", token("END_CUSTOMER", null)).andExpect(status().isForbidden());
        get("/api/layouts/policy", merchantToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.capacityPolicy").value("UNCONFIGURED_LIMITS_ARE_UNBOUNDED"))
                .andExpect(jsonPath("$.zones[0].zone").value("BASELINE"))
                .andExpect(jsonPath("$.zones[0].maxBlocks").value(35))
                .andExpect(jsonPath("$.zones[0].maxProducts").value(35));
        post("/api/campaigns", adminToken,
                "{\"name\":\"denied\",\"snapshotId\":" + snapshotId + ",\"recommendationIds\":[]}")
                .andExpect(status().isForbidden());
    }

    @Test
    void lifecycleRejectsInvalidTransitionsAndWithdrawalClearsDecision() throws Exception {
        JsonNode created = createCampaign(List.of());
        long campaignId = created.path("campaignId").asLong();
        long layoutB = created.path("layoutBId").asLong();
        post("/api/campaigns/" + campaignId + "/publish", merchantToken, "{\"expectedVersion\":1}")
                .andExpect(status().isConflict());
        post("/api/campaigns/" + campaignId + "/activate", adminToken, "{\"expectedVersion\":1}")
                .andExpect(status().isConflict());
        post("/api/campaigns/" + campaignId + "/end", adminToken, "{\"expectedVersion\":1}")
                .andExpect(status().isConflict());
        post("/api/campaigns/" + campaignId + "/withdraw", merchantToken, "{\"expectedVersion\":1}")
                .andExpect(status().isConflict());
        put("/api/layouts/" + layoutB + "/placements", adminToken, placements(1))
                .andExpect(status().isForbidden());

        JsonNode decision = read(put("/api/campaigns/" + campaignId + "/decision", merchantToken,
                "{\"expectedVersion\":1,\"decision\":\"KEEP_A\"}").andExpect(status().isOk()));
        JsonNode published = read(post("/api/campaigns/" + campaignId + "/publish", merchantToken,
                "{\"expectedVersion\":" + decision.path("version").asLong() + "}")
                .andExpect(status().isOk()));
        JsonNode withdrawn = read(post("/api/campaigns/" + campaignId + "/withdraw", merchantToken,
                "{\"expectedVersion\":" + published.path("version").asLong() + "}")
                .andExpect(status().isOk()));
        assertEquals("DRAFT", withdrawn.path("status").asText());
        assertTrue(withdrawn.path("decision").isNull());
        assertTrue(withdrawn.path("selectedLayoutId").isNull());
        post("/api/campaigns/" + campaignId + "/publish", merchantToken,
                "{\"expectedVersion\":" + withdrawn.path("version").asLong() + "}")
                .andExpect(status().isConflict());
    }

    @Test
    void placementBlocksAreValidatedVersionedAndRecheckedBeforePublish() throws Exception {
        long bundle = recommendation(RecType.BUNDLE, List.of(products.get(0), products.get(1)),
                ReviewStatus.APPROVED, true);
        long overlapping = recommendation(RecType.BANNER, List.of(products.get(0)),
                ReviewStatus.APPROVED, true);
        JsonNode created = createCampaign(List.of());
        long campaignId = created.path("campaignId").asLong();
        long layoutA = created.path("layoutAId").asLong();
        long layoutB = created.path("layoutBId").asLong();

        BigDecimal expectedA = snapshotProductScore(products);
        get("/api/layouts/" + layoutA + "/preview", merchantToken).andExpect(status().isOk())
                .andExpect(result -> assertDecimal(expectedA,
                        json.readTree(result.getResponse().getContentAsString()).path("weightedUtilityScore").decimalValue()));

        put("/api/layouts/" + layoutB + "/placements", merchantToken,
                placements(1, bundle)).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(result -> assertDecimal(snapshotProductScore(products.subList(0, 2)),
                        json.readTree(result.getResponse().getContentAsString()).path("weightedUtilityScore").decimalValue()));
        put("/api/layouts/" + layoutB + "/placements", merchantToken, placements(1, bundle))
                .andExpect(status().isConflict());
        put("/api/layouts/" + layoutB + "/placements", merchantToken,
                placements(2, bundle, overlapping)).andExpect(status().isBadRequest());

        long pending = recommendation(RecType.FBT, List.of(products.get(1), products.get(2)),
                ReviewStatus.PENDING_REVIEW, false);
        put("/api/layouts/" + layoutB + "/placements", merchantToken, placements(2, pending))
                .andExpect(status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/layouts/{id}/ready", layoutB)
                        .header("Authorization", bearer(merchantToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));

        JsonNode decision = read(put("/api/campaigns/" + campaignId + "/decision", merchantToken,
                "{\"expectedVersion\":1,\"decision\":\"CHOOSE_B\"}").andExpect(status().isOk()));
        setRecommendation(bundle, ReviewStatus.APPROVED, false);
        get("/api/layouts/" + layoutB, merchantToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.placements[0].valid").value(true));
        jdbc.update("UPDATE product SET status='INACTIVE' WHERE product_id=?", products.get(0));
        get("/api/layouts/" + layoutB, merchantToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.placements[0].valid").value(false))
                .andExpect(jsonPath("$.placements[1].valid").value(true));
        post("/api/campaigns/" + campaignId + "/publish", merchantToken,
                "{\"expectedVersion\":" + decision.path("version").asLong() + "}")
                .andExpect(status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/layouts/{id}/ready", layoutB)
                        .header("Authorization", bearer(merchantToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isConflict());
    }

    @Test
    void placementsCanSplitRecommendationsAndSimulationPinsTheSavedProductRevision() throws Exception {
        long bundle = recommendation(RecType.BUNDLE, List.of(products.get(0), products.get(1)),
                ReviewStatus.APPROVED, true);
        JsonNode created = createCampaign(List.of(bundle));
        long layoutB = created.path("layoutBId").asLong();

        put("/api/layouts/" + layoutB + "/placements", merchantToken,
                placementRequest(1, new int[] {products.get(0), products.get(1)},
                        new String[] {"BUNDLE", "CROSS_SELL"}, new int[] {1, 1},
                        new int[] {1, 1}, new Long[] {bundle, bundle}))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.placements.length()").value(2))
                .andExpect(jsonPath("$.placements[0].sourceRecommendationId").value(bundle))
                .andExpect(jsonPath("$.placements[1].sourceRecommendationId").value(bundle));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_item WHERE recommendation_id=?",
                Integer.class, bundle), "editing B must not alter F06 recommendation evidence");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM storefront_layout_revision WHERE layout_id=? AND version=2",
                Integer.class, layoutB));
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM layout_placement
                WHERE layout_id=? AND recommendation_id=? AND product_id IN (?,?)
                """, Integer.class, layoutB, bundle, products.get(0), products.get(1)));

        put("/api/layouts/" + layoutB + "/placements", merchantToken,
                placementRequest(2, new int[] {products.get(0)}, new String[] {"BUNDLE"},
                        new int[] {1}, new int[] {1}, new Long[] {bundle}))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placements.length()").value(1));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_item WHERE recommendation_id=?",
                Integer.class, bundle), "removing a placement must retain F06 evidence");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM layout_placement WHERE layout_id=?",
                Integer.class, layoutB));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM storefront_layout_revision WHERE layout_id=? AND version=3",
                Integer.class, layoutB));

        post("/api/layouts/" + layoutB + "/ready", merchantToken, "{\"expectedVersion\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
        JsonNode report = read(post("/api/campaigns/" + created.path("campaignId").asLong() + "/simulations",
                merchantToken, simulationRequest(1, 1, 3, today.plusDays(1), today.plusDays(2)))
                .andExpect(status().isCreated()));
        assertEquals(3, report.path("layouts").get(1).path("layoutVersion").asLong());
        assertEquals("OFFLINE_REPLAY_V4", report.path("algorithmVersion").asText());
        assertEquals(3, jdbc.queryForObject("""
                SELECT layout_version FROM comparison_report_layout
                WHERE report_id=? AND role='B'
                """, Integer.class, report.path("reportId").asLong()));
    }

    @Test
    void manualPlacementsUseSnapshotUtilityAndCurrentProductEligibility() throws Exception {
        JsonNode created = createCampaign(List.of());
        long layoutB = created.path("layoutBId").asLong();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/layouts/products")
                .param("snapshotId", String.valueOf(snapshotId))
                .param("q", "Layout product")
                .param("page", "0")
                .param("pageSize", "10")
                .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items[0].snapshotWeightedUtility").isNotEmpty())
                .andExpect(jsonPath("$.items[0].productStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].sellableQuantity").value(10))
                .andExpect(jsonPath("$.items[0].eligible").value(true));

        put("/api/layouts/" + layoutB + "/placements", merchantToken,
                "{\"expectedVersion\":1,\"recommendationIds\":[]}")
                .andExpect(status().isBadRequest());
        int recommendationItemsBeforeManualPlacement = jdbc.queryForObject(
                "SELECT COUNT(*) FROM recommendation_item", Integer.class);
        put("/api/layouts/" + layoutB + "/placements", merchantToken,
                placementRequest(1, new int[] {products.get(2)}, new String[] {"BANNER"},
                        new int[] {1}, new int[] {1}, new Long[] {null}))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placements[0].sourceRecommendationId").isEmpty())
                .andExpect(jsonPath("$.placements[0].recommendationId").isEmpty());
        assertEquals(recommendationItemsBeforeManualPlacement,
                jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_item", Integer.class),
                "a manual placement must not create F06 evidence");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM storefront_layout_revision WHERE layout_id=? AND version=2",
                Integer.class, layoutB));
    }

    @Test
    void ineligibleManualProductAndRepeatedProductAreRejectedWithoutChangingRevision() throws Exception {
        long layoutB = createCampaign(List.of()).path("layoutBId").asLong();
        String repeated = placementRequest(1, new int[] {products.get(0), products.get(0)},
                new String[] {"BANNER", "BANNER"}, new int[] {1, 1}, new int[] {1, 2},
                new Long[] {null, null});
        put("/api/layouts/" + layoutB + "/placements", merchantToken, repeated)
                .andExpect(status().isBadRequest());

        jdbc.update("UPDATE product SET status='INACTIVE' WHERE product_id=?", products.get(0));
        put("/api/layouts/" + layoutB + "/placements", merchantToken,
                placementRequest(1, new int[] {products.get(0)}, new String[] {"BANNER"},
                        new int[] {1}, new int[] {1}, new Long[] {null}))
                .andExpect(status().isConflict());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM layout_placement WHERE layout_id=?",
                Integer.class, layoutB));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM storefront_layout_revision WHERE layout_id=? AND version=1",
                Integer.class, layoutB));
    }

    @Test
    void openApiExposesF07F08OperationsAndSchemas() throws Exception {
        String document = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode openApi = json.readTree(document);
        JsonNode paths = openApi.path("paths");
        for (String path : List.of("/api/layouts", "/api/layouts/products", "/api/layouts/{id}/placements", "/api/campaigns",
                "/api/campaigns/{id}/simulations", "/api/campaigns/{id}/activate",
                "/api/campaigns/{id}/rollback", "/api/storefront/active-layout")) {
            assertTrue(paths.has(path), "Missing OpenAPI path " + path);
        }
        assertTrue(paths.path("/api/campaigns").path("post").path("responses").has("201"));
        assertTrue(openApi.path("components").path("schemas").has("CampaignReportView"));
        JsonNode schemas = openApi.path("components").path("schemas");
        for (String schema : List.of("LayoutView", "ReportLayoutView")) {
            JsonNode fields = schemas.path(schema).path("properties");
            assertTrue(fields.path("priorityCoverage").path("type").toString().contains("null"));
            assertTrue(fields.has("priorityCoverageDenominator"));
        }
        JsonNode saveRequest = openApi.path("components").path("schemas").path("SavePlacementsRequest");
        assertTrue(saveRequest.path("properties").has("placements"));
        assertFalse(saveRequest.path("properties").has("recommendationIds"));
        assertTrue(openApi.path("components").path("schemas").has("PlacementRequest"));
        assertTrue(openApi.path("tags").toString().contains("F07 Layouts and F08 Campaigns"));
    }

    @Test
    void offlineSimulationExcludesTrainingIdsKeepsIdenticalCartsAndPinsLayoutVersions() throws Exception {
        long bundle = recommendation(RecType.BUNDLE, List.of(products.get(0), products.get(1)),
                ReviewStatus.APPROVED, true);
        JsonNode created = createCampaign(List.of(bundle));
        long campaignId = created.path("campaignId").asLong();
        long layoutA = created.path("layoutAId").asLong();
        long layoutB = created.path("layoutBId").asLong();
        transaction(today.minusDays(1), products, new String[] {"10.00", "10.00", "5.00"},
                new String[] {"2.00", "4.00", "1.00"});
        transaction(today, List.of(products.get(1), products.get(2)),
                new String[] {"10.00", "5.00"}, new String[] {"4.00", "1.00"});

        put("/api/layouts/" + layoutB + "/placements", merchantToken,
                placementRequest(1, new int[] {products.get(0), products.get(1)},
                        new String[] {"BUNDLE", "BUNDLE"}, new int[] {1, 1},
                        new int[] {1, 2}, new Long[] {bundle, bundle}))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/layouts/{id}/ready", layoutB)
                        .header("Authorization", bearer(merchantToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isOk());

        long trainingId = transactionIdFor(trainingDay);
        List<Integer> evaluationIds = jdbc.query("""
                SELECT t.transaction_id FROM sales_transaction t
                WHERE t.store_id=? AND NOT EXISTS (
                    SELECT 1 FROM utility_matrix_item m WHERE m.snapshot_id=? AND m.transaction_id=t.transaction_id)
                ORDER BY t.transaction_id
                """, (rs, row) -> rs.getInt(1), storeId, snapshotId);
        assertEquals(2, evaluationIds.size());

        post("/api/campaigns/" + campaignId + "/simulations", merchantToken,
                simulationRequest(1, 1, 2, trainingDay, trainingDay.plusDays(1)))
                .andExpect(status().isBadRequest());

        JsonNode report = read(post("/api/campaigns/" + campaignId + "/simulations", merchantToken,
                simulationRequest(1, 1, 2, today.minusDays(1), today))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.createdBy").value(merchantUserId))
                .andExpect(result -> assertEquals(2,
                        json.readTree(result.getResponse().getContentAsString()).path("evaluationCount").asInt())));
        assertEquals(64, report.path("datasetHash").asText().length());
        JsonNode reportA = report.path("layouts").get(0);
        JsonNode reportB = report.path("layouts").get(1);
        assertEquals(1, reportA.path("layoutVersion").asLong());
        assertEquals(2, reportB.path("layoutVersion").asLong());
        assertEquals(2, reportB.path("hitRate").path("numerator").asInt());
        assertEquals(2, reportB.path("hitRate").path("denominator").asInt());
        assertDecimal(new BigDecimal("0.500000"), reportB.path("bundleConversion").path("rate").decimalValue());
        assertTrue(report.path("assumptions").path("bundleConversion").asText().contains("saved B layout revision"));
        assertDecimal(new BigDecimal("0.714286"), reportB.path("capturedUtility").path("rate").decimalValue());
        assertDecimal(BigDecimal.ZERO, reportB.path("priorityCoverage").decimalValue());
        assertEquals(2, reportB.path("priorityCoverageDenominator").asInt());
        assertEquals(evaluationIds, jdbc.query("""
                SELECT transaction_id FROM comparison_report_transaction
                WHERE report_id=? ORDER BY transaction_id
                """, (rs, row) -> rs.getInt(1), report.path("reportId").asLong()));
        assertFalse(evaluationIds.contains(trainingId));
        get("/api/campaign-reports/" + report.path("reportId").asLong(), foreignToken)
                .andExpect(status().isNotFound());
        get("/api/campaigns/" + campaignId + "/reports", foreignToken)
                .andExpect(status().isNotFound());

        put("/api/layouts/" + layoutB + "/placements", merchantToken, placements(2, bundle))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/layouts/{id}/ready", layoutB)
                        .header("Authorization", bearer(merchantToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":3}"))
                .andExpect(status().isOk());
        get("/api/campaign-reports/" + report.path("reportId").asLong(), merchantToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layouts[1].layoutVersion").value(2));
        put("/api/campaigns/" + campaignId + "/decision", merchantToken,
                "{\"expectedVersion\":1,\"decision\":\"CHOOSE_B\",\"reportId\":"
                        + report.path("reportId").asLong() + "}")
                .andExpect(status().isConflict());
    }

    @Test
    void zeroDenominatorsAreNullAndPublishDoesNotChangeActiveStorefrontUntilActivation() throws Exception {
        JsonNode created = createCampaign(List.of());
        long campaignId = created.path("campaignId").asLong();
        long layoutA = created.path("layoutAId").asLong();
        long layoutB = created.path("layoutBId").asLong();
        long bundle = recommendation(RecType.BUNDLE, List.of(products.get(0), products.get(1)),
                ReviewStatus.APPROVED, true);
        put("/api/layouts/" + layoutB + "/placements", merchantToken, placements(1, bundle))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/layouts/{id}/ready", layoutB)
                        .header("Authorization", bearer(merchantToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isOk());

        JsonNode report = read(post("/api/campaigns/" + campaignId + "/simulations", merchantToken,
                simulationRequest(1, 1, 2, today.plusDays(2), today.plusDays(3)))
                .andExpect(status().isCreated()));
        assertEquals(0, report.path("evaluationCount").asInt());
        for (JsonNode layout : report.path("layouts")) {
            assertTrue(layout.path("hitRate").path("rate").isNull());
            assertTrue(layout.path("bundleConversion").path("rate").isNull());
            assertTrue(layout.path("capturedUtility").path("rate").isNull());
        }

        JsonNode decided = read(put("/api/campaigns/" + campaignId + "/decision", merchantToken,
                "{\"expectedVersion\":1,\"decision\":\"KEEP_A\"}").andExpect(status().isOk()));
        JsonNode published = read(post("/api/campaigns/" + campaignId + "/publish", merchantToken,
                "{\"expectedVersion\":" + decided.path("version").asLong() + "}")
                .andExpect(status().isOk()));
        get("/api/storefront/active-layout", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultLayout").value(true))
                .andExpect(jsonPath("$.layout").isEmpty());
        JsonNode activated = read(post("/api/campaigns/" + campaignId + "/activate", adminToken,
                "{\"expectedVersion\":" + published.path("version").asLong() + "}")
                .andExpect(status().isOk()));
        get("/api/storefront/active-layout", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultLayout").value(false))
                .andExpect(jsonPath("$.layout.layoutId").value(layoutA));
        post("/api/campaigns/" + campaignId + "/end", adminToken,
                "{\"expectedVersion\":" + activated.path("version").asLong() + "}")
                .andExpect(status().isOk());
        get("/api/storefront/active-layout", adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultLayout").value(true));
    }

    @Test
    void rollbackWithoutPriorActiveCampaignReturnsStorefrontToDefault() throws Exception {
        JsonNode published = publishKeepA();
        long campaignId = published.path("campaignId").asLong();
        JsonNode active = read(post("/api/campaigns/" + campaignId + "/activate", adminToken,
                "{\"expectedVersion\":" + published.path("version").asLong() + "}")
                .andExpect(status().isOk()));
        JsonNode rollback = read(post("/api/campaigns/" + campaignId + "/rollback", adminToken,
                "{\"expectedVersion\":" + active.path("version").asLong() + "}")
                .andExpect(status().isOk()));
        assertTrue(rollback.path("defaultLayout").asBoolean());
        assertTrue(rollback.path("campaign").isNull());
        assertEquals("ENDED", jdbc.queryForObject("SELECT status FROM campaign WHERE campaign_id=?",
                String.class, campaignId));
    }

    @Test
    void activationSerializesPerStoreAndRollbackRestoresTheRecordedPreviousCampaign() throws Exception {
        JsonNode first = publishKeepA();
        JsonNode second = publishKeepA();
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> activateAfter(start, first.path("campaignId").asLong(), first.path("version").asLong()));
            var two = executor.submit(() -> activateAfter(start, second.path("campaignId").asLong(), second.path("version").asLong()));
            start.countDown();
            one.get(20, TimeUnit.SECONDS);
            two.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM campaign WHERE store_id=? AND status='ACTIVE'",
                Integer.class, storeId));

        JsonNode active = read(get("/api/storefront/active-layout", adminToken).andExpect(status().isOk()));
        long currentCampaign = active.path("campaign").path("campaignId").asLong();
        long currentVersion = active.path("campaign").path("version").asLong();
        long previousCampaign = currentCampaign == first.path("campaignId").asLong()
                ? second.path("campaignId").asLong() : first.path("campaignId").asLong();
        JsonNode rollback = read(post("/api/campaigns/" + currentCampaign + "/rollback", adminToken,
                "{\"expectedVersion\":" + currentVersion + "}").andExpect(status().isOk()));
        assertEquals(previousCampaign, rollback.path("campaign").path("campaignId").asLong());
        assertEquals(rollback.path("campaign").path("selectedLayoutId").asLong(),
                rollback.path("layout").path("layoutId").asLong());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM campaign WHERE store_id=? AND status='ACTIVE'",
                Integer.class, storeId));
        assertEquals("ENDED", jdbc.queryForObject("SELECT status FROM campaign WHERE campaign_id=?",
                String.class, currentCampaign));
    }

    // ---- DEC-20261119-06: owner zone caps, top-K baseline and equal-size simulation; F08 saved-block semantics started in OFFLINE_REPLAY_V3.

    @Test
    void policyExposesTheOwnerZoneCapacities() throws Exception {
        JsonNode policy = read(get("/api/layouts/policy", merchantToken).andExpect(status().isOk()));
        var caps = new java.util.HashMap<String, List<Integer>>();
        for (JsonNode zone : policy.path("zones")) caps.put(zone.path("zone").asText(), List.of(zone.path("maxBlocks").asInt(), zone.path("maxProducts").asInt()));
        assertEquals(java.util.Map.of("BASELINE", List.of(35, 35), "BANNER", List.of(3, 3), "BUNDLE", List.of(4, 16),
                "CROSS_SELL", List.of(2, 8), "FBT", List.of(2, 8)), caps);
    }

    /** Adds 37 products to one training transaction: ranking becomes p1 (2 transactions), then p0, p2, new1..new37 by id. */
    private List<Integer> fortyProductSnapshot() throws Exception {
        var added = new ArrayList<Integer>();
        for (int i = 1; i <= 37; i++) {
            jdbc.update("""
                    INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status)
                    VALUES (?,?,?,'each',FALSE,10,1,'ACTIVE')
                    """, storeId, "WIDE-" + i, "Wide product " + i);
            added.add(jdbc.queryForObject("SELECT MAX(product_id) FROM product", Integer.class));
            jdbc.update("""
                    INSERT INTO inventory_batch(product_id,batch_code,received_at,initial_quantity,remaining_quantity,status)
                    VALUES (?, ?, CURRENT_TIMESTAMP, 10, 10, 'ACTIVE')
                    """, added.get(added.size() - 1), "WIDE-AVAILABLE-" + i);
        }
        String[] prices = new String[37], costs = new String[37];
        java.util.Arrays.fill(prices, "10.00");
        java.util.Arrays.fill(costs, "3.00");
        transaction(trainingDay, added, prices, costs);
        snapshotId = createSnapshot();
        var ranked = new ArrayList<Integer>(List.of(products.get(1), products.get(0), products.get(2)));
        ranked.addAll(added);
        return ranked;
    }

    private List<Integer> placementProducts(JsonNode layout) {
        return java.util.stream.StreamSupport.stream(layout.path("placements").spliterator(), false)
                .map(item -> item.path("productId").asInt()).toList();
    }

    @Test
    void campaignOnMoreThanThirtyFiveProductsKeepsTheTopKBaselineInFrequencyOrder() throws Exception {
        List<Integer> ranked = fortyProductSnapshot();
        JsonNode created = createCampaign(List.of());
        long layoutA = created.path("layoutAId").asLong();
        JsonNode baseline = read(get("/api/layouts/" + layoutA, merchantToken).andExpect(status().isOk()));
        assertEquals(ranked.subList(0, 35), placementProducts(baseline));
        assertTrue(baseline.path("valid").asBoolean());
        JsonNode params = json.readTree(jdbc.queryForObject("SELECT baseline_params FROM storefront_layout WHERE layout_id=?", String.class, layoutA));
        if (params.isTextual()) params = json.readTree(params.asText());
        assertEquals("F07_BASELINE_V2", params.path("ruleVersion").asText());
        assertEquals(35, params.path("k").asInt());
        assertEquals(40, params.path("candidateProducts").asInt());
        assertEquals("COUNT(DISTINCT transaction_id) DESC, product_id ASC", params.path("ordering").asText());
    }

    @Test
    void theBaselineCapAppliesOnlyToV2BaselinesSoHistoricalV1LayoutsStayValid() throws Exception {
        List<Integer> ranked = fortyProductSnapshot();
        long layoutA = createCampaign(List.of()).path("layoutAId").asLong();
        jdbc.update("""
                INSERT INTO layout_placement(store_id,layout_id,utility_snapshot_id,recommendation_id,product_id,zone,block_no,position)
                VALUES (?,?,?,NULL,?,'BASELINE',36,36)
                """, storeId, layoutA, snapshotId, ranked.get(35));
        get("/api/layouts/" + layoutA, merchantToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
        jdbc.update("UPDATE storefront_layout SET baseline_params=? WHERE layout_id=?",
                "{\"ruleVersion\":\"F07_BASELINE_V1\",\"ordering\":\"COUNT(DISTINCT transaction_id) DESC, product_id ASC\",\"score\":\"number of snapshot transactions containing each product\"}", layoutA);
        get("/api/layouts/" + layoutA, merchantToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void priorityCoverageUsesFrozenWeightsAndHandlesEmptyLayoutsAndAnOversizedB() throws Exception {
        // The second product occurs in multiple matrix rows, but counts only once in a layout.
        setSnapshotWeight(products.get(1), "1.01");
        Integer oldCapacity = layoutPolicy.getMaxProductsByZone().put("BASELINE", 1);
        try {
            long bundle = recommendation(RecType.BUNDLE, List.of(products.get(0), products.get(1)),
                    ReviewStatus.APPROVED, true);
            JsonNode created = createCampaign(List.of());
            long layoutA = created.path("layoutAId").asLong();
            long layoutB = created.path("layoutBId").asLong();
            var empty = read(get("/api/layouts/" + layoutB + "/preview", merchantToken).andExpect(status().isOk()));
            assertTrue(empty.path("priorityCoverage").isNull());
            assertEquals(0, empty.path("priorityCoverageDenominator").asInt(-1));
            var a = read(get("/api/layouts/" + layoutA, merchantToken).andExpect(status().isOk()));
            assertCoverage(a, "1", 1);
            var b = read(put("/api/layouts/" + layoutB + "/placements", merchantToken, placements(1, bundle))
                    .andExpect(status().isOk()));
            assertCoverage(b, "0.5", 2);
            post("/api/layouts/" + layoutB + "/ready", merchantToken, "{\"expectedVersion\":2}")
                    .andExpect(status().isOk());
            var report = read(post("/api/campaigns/" + created.path("campaignId").asLong() + "/simulations",
                    merchantToken, simulationRequest(1, 1, 2, today.plusDays(2), today.plusDays(3)))
                    .andExpect(status().isCreated()));
            assertEquals(0, report.path("evaluationCount").asInt());
            assertTrue(report.path("assumptions").path("nExceedsBaseline").asBoolean());
            assertCoverage(report.path("layouts").get(0), "1", 1);
            assertCoverage(report.path("layouts").get(1), "0.5", 2);
            // Simulate inconsistent historical fixture data and verify the saved report stays frozen.
            jdbc.update("""
                    UPDATE utility_matrix_item SET weight_value=1
                    WHERE snapshot_id=? AND product_id=? AND transaction_id=(SELECT MIN(transaction_id) FROM utility_matrix_item WHERE snapshot_id=? AND product_id=?)
                    """, snapshotId, products.get(1), snapshotId, products.get(1));
            var unavailable = read(get("/api/layouts/" + layoutB, merchantToken).andExpect(status().isOk()));
            assertTrue(unavailable.path("priorityCoverage").isNull());
            assertEquals(2, unavailable.path("priorityCoverageDenominator").asInt(-1));
            var savedReport = read(get("/api/campaign-reports/" + report.path("reportId").asLong(), merchantToken)
                    .andExpect(status().isOk()));
            assertCoverage(savedReport.path("layouts").get(1), "0.5", 2);
        } finally {
            if (oldCapacity == null) layoutPolicy.getMaxProductsByZone().remove("BASELINE");
            else layoutPolicy.getMaxProductsByZone().put("BASELINE", oldCapacity);
        }
    }

    private void setSnapshotWeight(int productId, String weight) {
        jdbc.update("UPDATE utility_matrix_item SET weight_value=? WHERE snapshot_id=? AND product_id=?",
                new BigDecimal(weight), snapshotId, productId);
    }

    private void assertCoverage(JsonNode layout, String expected, int count) {
        assertTrue(layout.path("priorityCoverage").isNumber());
        assertDecimal(new BigDecimal(expected), layout.path("priorityCoverage").decimalValue());
        assertEquals(count, layout.path("priorityCoverageDenominator").asInt(-1));
    }

    @Test
    void equalSizeSimulationComparesBWithTheTopNOfAAndReportsNAndTheSizeOfA() throws Exception {
        List<Integer> ranked = fortyProductSnapshot();
        setSnapshotWeight(ranked.get(0), "1.01");
        setSnapshotWeight(ranked.get(8), "1.02");
        long first = recommendation(RecType.BUNDLE, ranked.subList(22, 26), ReviewStatus.APPROVED, true);
        long second = recommendation(RecType.BUNDLE, ranked.subList(26, 30), ReviewStatus.APPROVED, true);
        JsonNode created = createCampaign(List.of(first, second));
        long campaignId = created.path("campaignId").asLong();
        long layoutB = created.path("layoutBId").asLong();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/layouts/{id}/ready", layoutB)
                        .header("Authorization", bearer(merchantToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1}"))
                .andExpect(status().isOk());
        JsonNode viewB = read(get("/api/layouts/" + layoutB, merchantToken).andExpect(status().isOk()));
        assertEquals(8, viewB.path("equalSizeProductCount").asInt());
        assertDecimal(snapshotProductScore(ranked.subList(0, 8)), viewB.path("equalSizeBaselineScore").decimalValue());
        JsonNode viewA = read(get("/api/layouts/" + created.path("layoutAId").asLong(), merchantToken).andExpect(status().isOk()));
        assertTrue(viewA.path("equalSizeBaselineScore").isNull());
        assertTrue(viewA.path("equalSizeProductCount").isNull());
        assertCoverage(viewA, "0.057143", 35);
        assertCoverage(viewB, "0", 8);

        // Evaluation: one basket holds only A's 9th product (in A, outside A's top 8), the other only A's 1st product.
        transaction(today.minusDays(1), List.of(ranked.get(8)), new String[] {"10.00"}, new String[] {"3.00"});
        transaction(today.minusDays(1), List.of(ranked.get(0)), new String[] {"10.00"}, new String[] {"4.00"});
        JsonNode report = read(post("/api/campaigns/" + campaignId + "/simulations", merchantToken,
                simulationRequest(1, 1, 1, today.minusDays(1), today)).andExpect(status().isCreated()));
        assertEquals("OFFLINE_REPLAY_V4", report.path("algorithmVersion").asText());
        JsonNode assumptions = report.path("assumptions");
        assertEquals("EQUAL_SIZE_TOP_N_OF_A", assumptions.path("comparisonMode").asText());
        assertEquals(8, assumptions.path("comparedProductCountN").asInt());
        assertEquals(35, assumptions.path("baselineProductCount").asInt());
        assertEquals(8, assumptions.path("baselineProductsCompared").asInt());
        assertEquals(35, assumptions.path("baselineK").asInt());
        assertFalse(assumptions.path("nExceedsBaseline").asBoolean());
        JsonNode reportA = report.path("layouts").get(0);
        assertEquals("A", reportA.path("role").asText());
        assertCoverage(reportA, "0.125", 8);
        assertCoverage(report.path("layouts").get(1), "0", 8);
        JsonNode stored = read(get("/api/campaign-reports/" + report.path("reportId").asLong(), merchantToken)
                .andExpect(status().isOk()));
        assertCoverage(stored.path("layouts").get(0), "0.125", 8);
        assertEquals(2, reportA.path("hitRate").path("denominator").asInt());
        assertEquals(1, reportA.path("hitRate").path("numerator").asInt(), "the 9th product of A is outside the equal-size prefix");
        assertEquals(0, report.path("layouts").get(1).path("hitRate").path("numerator").asInt());

        // A report stored by the V1 algorithm is read back exactly as stored.
        long reportId = report.path("reportId").asLong();
        String v1Assumptions = "{\"label\":\"simulated\",\"priorityCoverage\":\"N/A until the F03 priority coverage formula is confirmed.\"}";
        jdbc.update("UPDATE comparison_report SET algorithm_version='OFFLINE_REPLAY_V1',assumptions=? WHERE report_id=?", v1Assumptions, reportId);
        jdbc.update("UPDATE comparison_report_layout SET priority_coverage=NULL,priority_coverage_denominator=NULL WHERE report_id=?", reportId);
        JsonNode legacy = read(get("/api/campaign-reports/" + reportId, merchantToken).andExpect(status().isOk()));
        assertEquals("OFFLINE_REPLAY_V1", legacy.path("algorithmVersion").asText());
        assertEquals(json.readTree(v1Assumptions), legacy.path("assumptions"));
        assertTrue(legacy.path("layouts").get(0).path("priorityCoverage").isNull());
        assertTrue(legacy.path("layouts").get(0).path("priorityCoverageDenominator").isNull());
        assertEquals(1, legacy.path("layouts").get(0).path("hitRate").path("numerator").asInt(), "stored metrics are not recomputed");
    }

    private JsonNode publishKeepA() throws Exception {
        JsonNode created = createCampaign(List.of());
        long id = created.path("campaignId").asLong();
        JsonNode decision = read(put("/api/campaigns/" + id + "/decision", merchantToken,
                "{\"expectedVersion\":1,\"decision\":\"KEEP_A\"}").andExpect(status().isOk()));
        return read(post("/api/campaigns/" + id + "/publish", merchantToken,
                "{\"expectedVersion\":" + decision.path("version").asLong() + "}")
                .andExpect(status().isOk()));
    }

    private void activateAfter(CountDownLatch start, long id, long version) {
        try {
            assertTrue(start.await(5, TimeUnit.SECONDS));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/campaigns/{id}/activate", id)
                            .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"expectedVersion\":" + version + "}"))
                    .andExpect(status().isOk());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode createCampaign(List<Long> recommendationIds) throws Exception {
        return read(post("/api/campaigns", merchantToken, json.writeValueAsString(java.util.Map.of(
                "name", "F07 " + UUID.randomUUID(), "snapshotId", snapshotId,
                "recommendationIds", recommendationIds)))
                .andExpect(status().isCreated()));
    }

    private JsonNode campaign(long id) throws Exception {
        return read(get("/api/campaigns/" + id, merchantToken).andExpect(status().isOk()));
    }

    private long recommendation(RecType type, List<Integer> productIds, ReviewStatus status, boolean ready) {
        String identity = UUID.randomUUID().toString().replace("-", "") + "a".repeat(32);
        Integer anchor = type == RecType.CROSS_SELL ? productIds.get(0) : null;
        Integer reviewer = status == ReviewStatus.APPROVED ? merchantUserId : null;
        Timestamp reviewedAt = reviewer == null ? null : Timestamp.from(java.time.Instant.now());
        String reviewReason = reviewer == null ? null : "test review";
        jdbc.update("""
                INSERT INTO recommendation(store_id,utility_snapshot_id,identity_hash,canonical_key,type,
                    utility,rule_version,policy_version,policy_snapshot,evidence,generation_eligibility,
                    explanation,review_status,version,created_by,reviewed_by,reviewed_at,review_reason,anchor_product_id)
                VALUES (?,?,?,?,?,999999.000000,'F06_TEST',1,'{}','{}','{}','[]',?,2,?,?,?,?,?)
                """, storeId, snapshotId, identity, String.join(",", productIds.stream().map(String::valueOf).toList()),
                type.name(), status.name(), merchantUserId, reviewer, reviewedAt, reviewReason, anchor);
        long id = jdbc.queryForObject("SELECT MAX(recommendation_id) FROM recommendation", Long.class);
        for (Integer product : productIds) {
            jdbc.update("INSERT INTO recommendation_item(recommendation_id,store_id,product_id) VALUES (?,?,?)",
                    id, storeId, product);
        }
        when(recommendations.get(any(Jwt.class), eq(id)))
                .thenReturn(recommendationView(id, type, productIds, status, ready));
        return id;
    }

    private void setRecommendation(long id, ReviewStatus status, boolean ready) {
        when(recommendations.get(any(Jwt.class), eq(id))).thenReturn(recommendationView(id, RecType.BUNDLE,
                List.of(products.get(0), products.get(1)), status, ready));
    }

    private RecView recommendationView(long id, RecType type, List<Integer> productIds,
            ReviewStatus reviewStatus, boolean ready) {
        boolean approved = reviewStatus == ReviewStatus.APPROVED;
        return new RecView(id, storeId, snapshotId, type, productIds,
                type == RecType.CROSS_SELL ? productIds.get(0) : null,
                new BigDecimal("999999.000000"), reviewStatus, 2L, "F06_TEST",
                null, null, null, null, false, ready, List.of(), List.of(), List.of(),
                approved ? merchantUserId : null, approved ? Instant.now() : null,
                approved ? "test review" : null, Instant.now(), null, false, List.of());
    }

    private BigDecimal snapshotProductScore(List<Integer> productIds) {
        String placeholders = String.join(",", java.util.Collections.nCopies(productIds.size(), "?"));
        return jdbc.queryForObject("SELECT SUM(weighted_utility) FROM utility_matrix_item "
                + "WHERE snapshot_id=? AND product_id IN (" + placeholders + ")", BigDecimal.class,
                prepend(snapshotId, productIds));
    }

    private Object[] prepend(int first, List<Integer> rest) {
        Object[] values = new Object[rest.size() + 1];
        values[0] = first;
        for (int i = 0; i < rest.size(); i++) values[i + 1] = rest.get(i);
        return values;
    }

    private int createSnapshot() throws Exception {
        JsonNode response = read(post("/api/utility/snapshots", merchantToken, json.writeValueAsString(java.util.Map.of(
                "rangeStart", trainingDay, "rangeEnd", trainingDay, "asOfDate", trainingDay)))
                .andExpect(status().isCreated()));
        return response.path("snapshotId").asInt();
    }

    private int store(String name) {
        jdbc.update("INSERT INTO store(name) VALUES (?)", name + " " + UUID.randomUUID());
        return jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
    }

    private String token(String role, Integer scope) throws Exception {
        var user = users.createPendingUser("F07 Test", UUID.randomUUID() + "@example.test",
                role, scope, null, null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?", user.id());
        if ("MERCHANDISER".equals(role) && scope != null && scope == storeId) merchantUserId = user.id();
        return auth.token(users.findById(user.id())).accessToken();
    }

    private int transaction(LocalDate day, List<Integer> productIds, String[] prices, String[] costs) {
        String orderId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO sales_transaction(store_id,source_system,source_order_id,occurred_at,status,
                    f03_eligible,total_amount,content_sha256)
                VALUES (?,'F07_TEST',?,?,'COMPLETED',TRUE,100,?)
                """, storeId, orderId, Timestamp.valueOf(day.atStartOfDay().plusHours(12)),
                "a".repeat(64));
        int transactionId = jdbc.queryForObject("""
                SELECT transaction_id FROM sales_transaction WHERE store_id=? AND source_order_id=?
                """, Integer.class, storeId, orderId);
        for (int i = 0; i < productIds.size(); i++) {
            jdbc.update("""
                    INSERT INTO transaction_item(store_id,transaction_id,product_id,quantity,unit_price,unit_cost)
                    VALUES (?,?,?,1,?,?)
                    """, storeId, transactionId, productIds.get(i), new BigDecimal(prices[i]), new BigDecimal(costs[i]));
        }
        return transactionId;
    }

    private int transactionIdFor(LocalDate date) {
        return jdbc.queryForObject("""
                SELECT t.transaction_id FROM sales_transaction t
                WHERE t.store_id=? AND t.source_system='F07_TEST' AND DATE(t.occurred_at)=?
                ORDER BY t.transaction_id LIMIT 1
                """, Integer.class, storeId, java.sql.Date.valueOf(date));
    }

    private String placements(long version, long... recommendationIds) {
        var counts = new java.util.HashMap<String, Integer>();
        var positions = new java.util.HashMap<String, Integer>();
        var items = new ArrayList<String>();
        for (long recommendationId : recommendationIds) {
            String zone = jdbc.queryForObject("SELECT type FROM recommendation WHERE recommendation_id=?",
                    String.class, recommendationId);
            int blockNo = counts.merge(zone, 1, Integer::sum);
            List<Integer> productIds = jdbc.query(
                    "SELECT product_id FROM recommendation_item WHERE recommendation_id=? ORDER BY product_id",
                    (rs, row) -> rs.getInt(1), recommendationId);
            int position = positions.getOrDefault(zone, 0) + 1;
            positions.put(zone, position + productIds.size() - 1);
            for (int i = 0; i < productIds.size(); i++) {
                items.add("{\"productId\":" + productIds.get(i) + ",\"zone\":\"" + zone
                        + "\",\"blockNo\":" + blockNo + ",\"position\":" + (position + i)
                        + ",\"sourceRecommendationId\":" + recommendationId + "}");
            }
        }
        return "{\"expectedVersion\":" + version + ",\"placements\":["
                + String.join(",", items) + "]}";
    }

    private static String placementRequest(long version, int[] productIds, String[] zones,
            int[] blockNos, int[] positions, Long[] sourceRecommendationIds) {
        var items = new ArrayList<String>();
        for (int i = 0; i < productIds.length; i++) {
            items.add("{\"productId\":" + productIds[i] + ",\"zone\":\"" + zones[i]
                    + "\",\"blockNo\":" + blockNos[i] + ",\"position\":" + positions[i]
                    + ",\"sourceRecommendationId\":"
                    + (sourceRecommendationIds[i] == null ? "null" : sourceRecommendationIds[i]) + "}");
        }
        return "{\"expectedVersion\":" + version + ",\"placements\":["
                + String.join(",", items) + "]}";
    }

    private static String simulationRequest(long campaignVersion, long versionA, long versionB,
            LocalDate start, LocalDate end) {
        return "{\"expectedCampaignVersion\":" + campaignVersion + ",\"expectedLayoutAVersion\":" + versionA
                + ",\"expectedLayoutBVersion\":" + versionB + ",\"rangeStart\":\"" + start
                + "\",\"rangeEnd\":\"" + end + "\"}";
    }

    private ResultActions get(String path, String token) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                .header("Authorization", bearer(token)));
    }

    private ResultActions post(String path, String token, String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions put(String path, String token, String body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode read(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static void assertDecimal(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual));
    }
}

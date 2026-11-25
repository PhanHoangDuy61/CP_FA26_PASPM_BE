package vn.edu.fpt.paspm.dashboard;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:f11dashboard;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "paspm.jwt-secret=dashboard-test-only-signing-key-at-least-thirty-two-bytes",
        "paspm.otp-secret=dashboard-test-only-otp-key-at-least-thirty-two-bytes",
        "paspm.bootstrap-email=", "paspm.bootstrap-password=",
        "paspm.store-time-zone=Asia/Ho_Chi_Minh"
})
@AutoConfigureMockMvc
class DashboardIntegrationTest {
    private static final String SNAPSHOTS = "/api/dashboard/snapshot-utilities";
    private static final String BUNDLES = "/api/dashboard/bundle-additions";
    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 24);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired UserRepository users;

    private int store;
    private int foreignStore;
    private int actor;
    private int foreignActor;
    private String admin;
    private String merchant;

    @BeforeEach
    void setUp() {
        store = store();
        foreignStore = store();
        actor = user("STORE_ADMIN", store);
        foreignActor = user("MERCHANDISER", foreignStore);
        admin = token(actor);
        merchant = token(user("MERCHANDISER", store));
    }

    @Test
    void readySnapshotHeadersAreScopedOrderedPagedAndProjectedWithoutReadingEvidence() throws Exception {
        var older = Instant.parse("2026-11-25T05:00:00Z");
        var newer = older.plusSeconds(60);
        int first = snapshot(store, actor, older, "READY", "219.430067");
        int lowerTie = snapshot(store, actor, newer, "READY", "7.010002");
        int upperTie = snapshot(store, actor, newer, "READY", "11.010009");
        int failed = snapshot(store, actor, newer, "FAILED", "13.000001");
        int foreign = snapshot(foreignStore, foreignActor, newer, "READY", "70.800000");

        call(get(SNAPSHOTS), merchant).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].snapshotId", contains(upperTie, lowerTie, first)))
                .andExpect(jsonPath("$.items[0].totalWeightedUtility").value(11.010009))
                .andExpect(jsonPath("$.items[0].createdAt").value(newer.toString()))
                .andExpect(jsonPath("$.items[0].rangeStart").value("2026-11-20"))
                .andExpect(jsonPath("$.items[0].rangeEnd").value("2026-11-23"))
                .andExpect(jsonPath("$.items[0].analysisDate").value(DAY.toString()))
                .andExpect(jsonPath("$.items[0].configSnapshot").doesNotExist())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.totalElements").value(3));
        call(get(SNAPSHOTS).param("page", "1").param("pageSize", "2"), admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[*].snapshotId", contains(first)))
                .andExpect(jsonPath("$.totalElements").value(3));
        call(get(SNAPSHOTS).param("snapshotId", String.valueOf(first)), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].totalWeightedUtility").value(219.430067));
        for (int id : new int[] {failed, foreign, Integer.MAX_VALUE}) {
            call(get(SNAPSHOTS).param("snapshotId", String.valueOf(id)).param("page", "2"), merchant)
                    .andExpect(status().isNotFound());
        }
        call(get(SNAPSHOTS).param("page", "2").param("pageSize", "100"), merchant)
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void bundleWindowSplitsAtLocalMidnightCountsOnlySuccessfulActionsAndFillsZeroDays() throws Exception {
        var start = DAY.atStartOfDay(STORE_ZONE).toInstant();
        var next = start.plusSeconds(24 * 3600);
        event(store, null, start.minusNanos(1000), "BUNDLE_ADDED", "MINED", 80L);
        event(store, null, start, "BUNDLE_ADDED", "MINED", 2L);
        event(store, null, next.minusNanos(1000), "BUNDLE_ADDED", "MANUAL", 1L);
        event(store, null, next, "BUNDLE_ADDED", "MANUAL", 3L);
        event(store, null, next, "BUNDLE_ADD_REJECTED", "MANUAL", 0L);
        event(store, null, next, "ITEM_QUANTITY_CHANGED", null, null);
        event(store, null, next, "ITEM_REMOVED", null, null);
        event(store, null, next, "CART_CLEARED", null, null);
        event(foreignStore, null, next, "BUNDLE_ADDED", "MINED", 60L);
        event(store, null, DAY.plusDays(3).atStartOfDay(STORE_ZONE).toInstant(), "BUNDLE_ADDED", "MINED", 40L);

        var end = DAY.plusDays(2);
        call(window(DAY, end), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(DAY.toString()))
                .andExpect(jsonPath("$.to").value(end.toString()))
                .andExpect(jsonPath("$.timeZone").value(STORE_ZONE.getId()))
                .andExpect(jsonPath("$.campaignId").doesNotExist())
                .andExpect(jsonPath("$.totalAddActions").value(3))
                .andExpect(jsonPath("$.addActionsByOrigin.MINED").value(1))
                .andExpect(jsonPath("$.addActionsByOrigin.MANUAL").value(2))
                .andExpect(jsonPath("$.totalAddedProductUnits").value(6))
                .andExpect(jsonPath("$.days.length()").value(3))
                .andExpect(jsonPath("$.days[0].date").value(DAY.toString()))
                .andExpect(jsonPath("$.days[0].addedProductUnits").value(3))
                .andExpect(jsonPath("$.days[1].addActionsByOrigin.MINED").value(0))
                .andExpect(jsonPath("$.days[1].addActionsByOrigin.MANUAL").value(1))
                .andExpect(jsonPath("$.days[2].date").value(end.toString()))
                .andExpect(jsonPath("$.days[2].addedProductUnits").value(0))
                .andExpect(jsonPath("$.days[2].addActionsByOrigin.MINED").value(0))
                .andExpect(jsonPath("$.days[2].addActionsByOrigin.MANUAL").value(0));
    }

    @Test
    void campaignFilterWorksForEndedCampaignsAndHidesForeignAndMissingIds() throws Exception {
        var at = DAY.atStartOfDay(STORE_ZONE).toInstant();
        long ended = campaign(store, actor, at, "ENDED");
        long draft = campaign(store, actor, at, "DRAFT");
        long foreign = campaign(foreignStore, foreignActor, at, "ENDED");
        event(store, ended, at, "BUNDLE_ADDED", "MINED", 5L);
        event(store, draft, at, "BUNDLE_ADDED", "MANUAL", 8L);
        event(foreignStore, foreign, at, "BUNDLE_ADDED", "MANUAL", 10L);
        call(window(DAY, DAY).param("campaignId", String.valueOf(ended)), merchant)
                .andExpect(status().isOk()).andExpect(jsonPath("$.campaignId").value(ended))
                .andExpect(jsonPath("$.totalAddActions").value(1))
                .andExpect(jsonPath("$.addActionsByOrigin.MINED").value(1))
                .andExpect(jsonPath("$.addActionsByOrigin.MANUAL").value(0))
                .andExpect(jsonPath("$.totalAddedProductUnits").value(5));
        for (long id : new long[] {foreign, Long.MAX_VALUE}) {
            call(window(DAY, DAY).param("campaignId", String.valueOf(id)), merchant)
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Campaign not found"));
        }
    }

    @Test
    void defaultWindowIncludesTodayAndExactlyNinetyDaysAreAccepted() throws Exception {
        LocalDate before = LocalDate.now(STORE_ZONE);
        call(get(BUNDLES), merchant).andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(30))
                .andExpect(jsonPath("$.totalAddActions").value(0))
                .andExpect(jsonPath("$.totalAddedProductUnits").value(0))
                .andExpect(jsonPath("$.days[0].addedProductUnits").value(0))
                .andExpect(result -> {
                    var tree = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(result.getResponse().getContentAsString());
                    LocalDate to = LocalDate.parse(tree.path("to").asText());
                    assertTrue(List.of(before, LocalDate.now(STORE_ZONE)).contains(to));
                    org.junit.jupiter.api.Assertions.assertEquals(to.minusDays(29).toString(), tree.path("from").asText());
                });
        call(window(DAY, DAY.plusDays(89)), admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(90))
                .andExpect(jsonPath("$.days[89].date").value(DAY.plusDays(89).toString()));
        call(window(DAY, DAY.plusDays(90)), admin).andExpect(status().isBadRequest());
    }

    @Test
    void invalidDatesAndQueryNumbersReturnBadRequest() throws Exception {
        for (var request : List.of(get(BUNDLES).param("from", DAY.toString()), get(BUNDLES).param("to", DAY.toString()),
                get(BUNDLES).param("from", "2026-02-30").param("to", DAY.toString()),
                get(BUNDLES).param("from", "").param("to", ""),
                get(BUNDLES).param("from", "2026-11-23+07:00").param("to", DAY.toString()),
                window(DAY, DAY.minusDays(1)), get(BUNDLES).param("campaignId", "0"),
                get(SNAPSHOTS).param("page", "-1"), get(SNAPSHOTS).param("pageSize", "0"),
                get(SNAPSHOTS).param("pageSize", "101"), get(SNAPSHOTS).param("snapshotId", "0"),
                get(SNAPSHOTS).param("snapshotId", "abc"))) {
            call(request, admin).andExpect(status().isBadRequest());
        }
    }

    @Test
    void onlyActiveStoreStaffCanReadDashboard() throws Exception {
        String customer = token(user("END_CUSTOMER", null));
        String system = token(user("SYSTEM_ADMIN", null));
        for (String route : List.of(SNAPSHOTS, BUNDLES)) {
            mvc.perform(get(route)).andExpect(status().isUnauthorized());
            call(get(route), customer).andExpect(status().isForbidden());
            call(get(route), system).andExpect(status().isForbidden());
            call(get(route), admin).andExpect(status().isOk());
            call(get(route), merchant).andExpect(status().isOk());
        }
        jdbc.update("UPDATE store SET status='INACTIVE' WHERE store_id=?", store);
        for (String route : List.of(SNAPSHOTS, BUNDLES)) {
            for (String token : List.of(admin, merchant)) {
                call(get(route), token).andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("STORE_INACTIVE"));
            }
        }
    }

    @Test
    void openApiUsesExplicitDashboardOperationIds() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['" + SNAPSHOTS + "'].get.operationId").value("listDashboardSnapshotUtilities"))
                .andExpect(jsonPath("$.paths['" + BUNDLES + "'].get.operationId").value("getDashboardBundleAdditions"));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private static MockHttpServletRequestBuilder window(LocalDate from, LocalDate to) {
        return get(BUNDLES).param("from", from.toString()).param("to", to.toString());
    }

    private int store() {
        String slug = "dashboard-" + UUID.randomUUID();
        jdbc.update("INSERT INTO store(name,slug,status) VALUES (?,?,'ACTIVE')", slug, slug);
        return jdbc.queryForObject("SELECT store_id FROM store WHERE slug=?", Integer.class, slug);
    }

    private int user(String role, Integer storeId) {
        int id = users.createPendingUser("Dashboard test user", UUID.randomUUID() + "@example.test",
                role, storeId, null, null).id();
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?", id);
        return id;
    }

    private String token(int userId) {
        return auth.token(users.findById(userId)).accessToken();
    }

    private int snapshot(int storeId, int createdBy, Instant at, String state, String utility) {
        String hash = UUID.randomUUID().toString().replace("-", "") + "a".repeat(32);
        jdbc.update("""
                INSERT INTO utility_snapshot(store_id,created_by,range_start,range_end,as_of_date,config_snapshot,
                    source_hash,matrix_hash,total_weighted_utility,rounding_scale,status,created_at)
                VALUES (?,?,?,?,?,'{"scoringVersion":"FUTURE_UNKNOWN_VERSION"}',?,?,?,6,?,?)
                """, storeId, createdBy, DAY.minusDays(4), DAY.minusDays(1), DAY,
                hash, hash, new BigDecimal(utility), state, Timestamp.from(at));
        return jdbc.queryForObject("SELECT snapshot_id FROM utility_snapshot WHERE store_id=? AND source_hash=?",
                Integer.class, storeId, hash);
    }

    private long campaign(int storeId, int createdBy, Instant at, String state) {
        int snapshot = snapshot(storeId, createdBy, at, "READY", "12.000000");
        jdbc.update("INSERT INTO campaign(store_id,utility_snapshot_id,name,created_by,status) VALUES (?,?,?,?,?)",
                storeId, snapshot, "Dashboard fixture", createdBy, state);
        return jdbc.queryForObject("SELECT campaign_id FROM campaign WHERE store_id=? AND utility_snapshot_id=?",
                Long.class, storeId, snapshot);
    }

    private void event(int storeId, Long campaign, Instant at, String type, String origin, Long units) {
        jdbc.update("""
                INSERT INTO storefront_event(store_id,campaign_id,occurred_at,event_type,bundle_origin,added_product_units)
                VALUES (?,?,?,?,?,?)
                """, storeId, campaign, Timestamp.from(at), type, origin, units);
    }
}

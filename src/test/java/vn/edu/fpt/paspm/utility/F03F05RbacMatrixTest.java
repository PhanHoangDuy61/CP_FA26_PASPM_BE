package vn.edu.fpt.paspm.utility;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.User;
import vn.edu.fpt.paspm.user.UserRepository;

/**
 * Role matrix for every F03 route and every F05 route that is not behind the D1 gate: each role outside the documented
 * set must be refused (403, anonymous 401) and each documented role must get past authorization (anything but 401/403).
 * Bodies are syntactically valid so the refusal can only come from authorization, not from request binding.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:f03f05rbac;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
    "paspm.jwt-secret=01234567890123456789012345678901", "paspm.otp-secret=01234567890123456789012345678901",
    "paspm.bootstrap-email=", "paspm.bootstrap-password="
})
@AutoConfigureMockMvc
class F03F05RbacMatrixTest {
    static final String SA = "SYSTEM_ADMIN", ST = "STORE_ADMIN", ME = "MERCHANDISER", EC = "END_CUSTOMER";
    static final List<String> ROLES = List.of(SA, ST, ME, EC);
    static final String RANGE = "{\"rangeStart\":\"2026-10-01\",\"rangeEnd\":\"2026-10-31\",\"asOfDate\":\"2026-10-31\"}";
    static final String WEIGHTS = "{\"enableStock\":true,\"enableExpiry\":false,\"enableSeason\":false,\"alpha\":1,\"beta\":0,\"gamma\":0,\"lambda\":0.1}";
    static final String MINING = "{\"requestKey\":\"" + UUID.randomUUID() + "\",\"snapshotId\":1,\"algorithms\":[\"HUI_MINER\"],\"minUtilMode\":\"ABSOLUTE\",\"minUtilValue\":1}";

    record Route(HttpMethod method, String path, String body, Set<String> allowed) {
        @Override public String toString() { return method + " " + path; }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    int product;

    @BeforeEach void setup() {
        jdbc.update("INSERT INTO store(name) VALUES (?)", "Rbac " + UUID.randomUUID());
        int store = jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,'Rbac Milk','each',TRUE,100,20,'ACTIVE')", store, "R-" + UUID.randomUUID());
        product = jdbc.queryForObject("SELECT MAX(product_id) FROM product", Integer.class);
        tokens.clear();
        for (String role : ROLES) tokens.put(role, token(role, role.equals(SA) || role.equals(EC) ? null : store));
    }
    final java.util.Map<String, String> tokens = new java.util.HashMap<>();

    List<Route> routes() {
        Set<String> admin = Set.of(SA), stores = Set.of(ST, ME), merch = Set.of(ME), storeAdmin = Set.of(ST), none = Set.of();
        List<Route> r = new ArrayList<>();
        // F03
        r.add(new Route(HttpMethod.GET, "/api/system/utility/lambda-policy", null, admin));
        r.add(new Route(HttpMethod.PUT, "/api/system/utility/lambda-policy", "{\"lambdaMin\":0,\"lambdaMax\":1}", admin));
        r.add(new Route(HttpMethod.GET, "/api/utility/config", null, stores));
        r.add(new Route(HttpMethod.PUT, "/api/utility/config", WEIGHTS, merch));
        r.add(new Route(HttpMethod.PUT, "/api/utility/config/expiry-window", "{\"expiryWindowDays\":7}", storeAdmin));
        r.add(new Route(HttpMethod.GET, "/api/utility/products/" + product + "/scores", null, stores));
        r.add(new Route(HttpMethod.PATCH, "/api/utility/products/" + product + "/scores", "{\"stockPriorityScore\":0.5}", merch));
        r.add(new Route(HttpMethod.POST, "/api/utility/preview", RANGE, merch));
        r.add(new Route(HttpMethod.POST, "/api/utility/snapshots", RANGE, merch));
        r.add(new Route(HttpMethod.GET, "/api/utility/snapshots/999999", null, merch));
        r.add(new Route(HttpMethod.GET, "/api/utility/snapshots/999999/matrix", null, merch));
        r.add(new Route(HttpMethod.GET, "/api/utility/expiry-push-batches", null, stores));
        // F05 Store-scoped reads and the two forbidden legacy routes
        for (String p : List.of("/api/mining/snapshots", "/api/mining/requests", "/api/mining/requests/999999", "/api/mining/requests/999999/runs",
                "/api/mining/runs/999999", "/api/mining/runs/999999/patterns"))
            r.add(new Route(HttpMethod.GET, p, null, stores));
        r.add(new Route(HttpMethod.POST, "/api/mining/requests", MINING, none));
        r.add(new Route(HttpMethod.GET, "/api/mining/runs/999999/benchmark", null, none));
        // F05 System Admin operator routes that are not behind the D1 read gate
        r.add(new Route(HttpMethod.GET, "/api/system/mining/config", null, admin));
        r.add(new Route(HttpMethod.POST, "/api/system/mining/requests", MINING, admin));
        r.add(new Route(HttpMethod.GET, "/api/system/mining/stores/999999/runs/999999/benchmark", null, admin));
        r.add(new Route(HttpMethod.GET, "/api/system/mining/schedules", null, admin));
        r.add(new Route(HttpMethod.GET, "/api/system/mining/schedules/999999", null, admin));
        r.add(new Route(HttpMethod.GET, "/api/system/mining/schedules/999999/occurrences", null, admin));
        return r;
    }

    MockHttpServletRequestBuilder build(Route r, String token) {
        MockHttpServletRequestBuilder b = request(r.method(), r.path());
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (r.body() != null) b.contentType(MediaType.APPLICATION_JSON).content(r.body());
        return b;
    }

    @Test void everyRoleOutsideTheDocumentedSetIsRefusedAndAnonymousGets401() throws Exception {
        int checked = 0;
        for (Route r : routes()) {
            mvc.perform(build(r, null)).andExpect(status().isUnauthorized());
            checked++;
            for (String role : ROLES) {
                if (r.allowed().contains(role)) continue;
                int actual = mvc.perform(build(r, tokens.get(role))).andReturn().getResponse().getStatus();
                assertNotEquals(200, actual, r + " as " + role);
                org.junit.jupiter.api.Assertions.assertEquals(403, actual, r + " as " + role + " must be 403");
                checked++;
            }
        }
        int expected = routes().stream().mapToInt(r -> 1 + (int) ROLES.stream().filter(x -> !r.allowed().contains(x)).count()).sum();
        org.junit.jupiter.api.Assertions.assertEquals(expected, checked);
        org.junit.jupiter.api.Assertions.assertEquals(26, routes().size(), "route table must list every F03 route and every ungated F05 route");
    }

    @Test void everyDocumentedRolePassesAuthorizationOnReadRoutes() throws Exception {
        for (Route r : routes()) {
            if (r.method() != HttpMethod.GET) continue;
            for (String role : r.allowed()) {
                int actual = mvc.perform(build(r, tokens.get(role))).andReturn().getResponse().getStatus();
                assertNotEquals(401, actual, r + " as " + role);
                assertNotEquals(403, actual, r + " as " + role);
            }
        }
    }

    @Test void documentedRolesPassAuthorizationOnWriteRoutesTooAndNothingFallsThroughTo5xx() throws Exception {
        for (Route r : routes()) {
            for (String role : r.allowed()) {
                int actual = mvc.perform(build(r, tokens.get(role))).andReturn().getResponse().getStatus();
                assertNotEquals(401, actual, r + " as " + role);
                assertNotEquals(403, actual, r + " as " + role);
                org.junit.jupiter.api.Assertions.assertTrue(actual < 500 || actual == 503, r + " as " + role + " -> " + actual);
            }
        }
    }

    String token(String role, Integer storeId) {
        User u = users.createPendingUser("Rbac tester", UUID.randomUUID() + "@example.test", role, storeId, null, null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=? WHERE user_id=?", java.sql.Timestamp.from(Instant.now()), u.id());
        return auth.token(users.findById(u.id())).accessToken();
    }
}

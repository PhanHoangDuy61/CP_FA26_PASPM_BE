package vn.edu.fpt.paspm.catalog.store;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;

/** DEC-20261123-01 §1–§2 and §6: System Admin Store management (UC34). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:storeadministration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "paspm.jwt-secret=01234567890123456789012345678901", "paspm.otp-secret=01234567890123456789012345678901",
        "paspm.bootstrap-email=", "paspm.bootstrap-password=",
        "paspm.media.root=/tmp/paspm-store-admin-media", "paspm.avatar.directory=/tmp/paspm-store-admin-media/avatars"})
@AutoConfigureMockMvc
class StoreAdministrationHttpTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    final ObjectMapper json = new ObjectMapper();

    String token(String role, Integer store) {
        var user = users.createPendingUser("Store admin test " + role, UUID.randomUUID() + "@example.test", role, store, null, null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?", user.id());
        return "Bearer " + auth.token(users.findById(user.id())).accessToken();
    }

    String admin() {
        return token("SYSTEM_ADMIN", null);
    }

    static String tag() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    ResultActions send(MockHttpServletRequestBuilder request, String bearer, String body) throws Exception {
        request.header("Authorization", bearer);
        if (body != null) request.contentType("application/json").content(body);
        return mvc.perform(request);
    }

    JsonNode create(String bearer, String name, String slug) throws Exception {
        String body = send(post("/api/system/stores"), bearer, "{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test void createReturnsTheNewActiveStoreAndEnforcesNameAndSlugRules() throws Exception {
        String admin = admin(), t = tag();
        var created = json.readTree(send(post("/api/system/stores"), admin,
                "{\"name\":\"  Cửa hàng " + t + "  \",\"slug\":\"cua-hang-" + t + "\",\"address\":\" 12 Lê Lợi \",\"phone\":\"+84 28 1234 5678\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cửa hàng " + t))
                .andExpect(jsonPath("$.slug").value("cua-hang-" + t))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.address").value("12 Lê Lợi"))
                .andExpect(jsonPath("$.phone").value("+84 28 1234 5678"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.iconUrl").value(nullValue()))
                .andExpect(jsonPath("$.bannerUrl").value(nullValue()))
                .andExpect(jsonPath("$.deactivatedAt").value(nullValue()))
                .andExpect(jsonPath("$.assignedUserCount").value(0))
                .andExpect(jsonPath("$.assignedUsers.length()").value(0))
                .andReturn().getResponse().getContentAsString());
        assertTrue(created.path("storeId").asInt() > 0);
        assertFalse(created.path("createdAt").isNull());

        // name: unique after trim, ignoring case
        send(post("/api/system/stores"), admin, "{\"name\":\"CỬA HÀNG " + t + "\",\"slug\":\"other-" + t + "\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Store name already exists"));
        // slug: unique
        send(post("/api/system/stores"), admin, "{\"name\":\"Other " + t + "\",\"slug\":\"cua-hang-" + t + "\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Store slug already exists"));
        for (String bad : List.of(
                "{\"name\":\"   \",\"slug\":\"blank-" + t + "\"}",
                "{\"name\":\"" + "x".repeat(151) + "\",\"slug\":\"long-" + t + "\"}",
                "{\"name\":\"Bad slug " + t + "\",\"slug\":\"Bad Slug\"}",
                "{\"name\":\"Bad slug " + t + "\",\"slug\":\"-edge-\"}",
                "{\"name\":\"Bad slug " + t + "\",\"slug\":\"" + "a".repeat(51) + "\"}",
                "{\"name\":\"Bad phone " + t + "\",\"slug\":\"phone-" + t + "\",\"phone\":\"call me\"}",
                "{\"name\":\"Status " + t + "\",\"slug\":\"status-" + t + "\",\"status\":\"INACTIVE\"}",
                "{\"slug\":\"no-name-" + t + "\"}"))
            send(post("/api/system/stores"), admin, bad).andExpect(status().isBadRequest());
    }

    @Test void detailListsAssignedUsersReadOnlyAndUnknownStoresAre404() throws Exception {
        String admin = admin(), t = tag();
        int store = create(admin, "Detail " + t, "detail-" + t).path("storeId").asInt();
        token("STORE_ADMIN", store);
        token("MERCHANDISER", store);
        send(get("/api/system/stores/" + store), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedUserCount").value(2))
                .andExpect(jsonPath("$.assignedUsers[*].role", containsInAnyOrder("STORE_ADMIN", "MERCHANDISER")))
                .andExpect(jsonPath("$.assignedUsers[0].userId").isNumber())
                .andExpect(jsonPath("$.assignedUsers[0].email").isString())
                .andExpect(jsonPath("$.assignedUsers[0].status").value("ACTIVE"));
        send(get("/api/system/stores/999999"), admin, null).andExpect(status().isNotFound());
        send(put("/api/system/stores/999999"), admin, "{\"expectedVersion\":1,\"name\":\"N " + t + "\",\"slug\":\"n-" + t + "\"}").andExpect(status().isNotFound());
        send(patch("/api/system/stores/999999/status"), admin, "{\"expectedVersion\":1,\"status\":\"INACTIVE\"}").andExpect(status().isNotFound());
    }

    @Test void updateChangesAllFieldsIncludingSlugWithOptimisticLocking() throws Exception {
        String admin = admin(), t = tag();
        int store = create(admin, "Update " + t, "update-" + t).path("storeId").asInt();
        create(admin, "Taken " + t, "taken-" + t);
        send(put("/api/system/stores/" + store), admin,
                "{\"expectedVersion\":1,\"name\":\"Renamed " + t + "\",\"slug\":\"renamed-" + t + "\",\"address\":\"1 Street\",\"phone\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed " + t)).andExpect(jsonPath("$.slug").value("renamed-" + t))
                .andExpect(jsonPath("$.address").value("1 Street")).andExpect(jsonPath("$.phone").value(nullValue()))
                .andExpect(jsonPath("$.version").value(2));
        send(put("/api/system/stores/" + store), admin, "{\"expectedVersion\":1,\"name\":\"Stale " + t + "\",\"slug\":\"stale-" + t + "\"}")
                .andExpect(status().isConflict());
        send(put("/api/system/stores/" + store), admin, "{\"expectedVersion\":2,\"name\":\"taken " + t + "\",\"slug\":\"renamed-" + t + "\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Store name already exists"));
        send(put("/api/system/stores/" + store), admin, "{\"expectedVersion\":2,\"name\":\"Renamed " + t + "\",\"slug\":\"taken-" + t + "\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Store slug already exists"));
        // Keeping its own name (another case) is not a duplicate
        send(put("/api/system/stores/" + store), admin, "{\"expectedVersion\":2,\"name\":\"RENAMED " + t + "\",\"slug\":\"renamed-" + t + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3));
        send(put("/api/system/stores/" + store), admin, "{\"expectedVersion\":3,\"name\":\"S " + t + "\",\"slug\":\"s-" + t + "\",\"status\":\"INACTIVE\"}")
                .andExpect(status().isBadRequest());
    }

    @Test void statusChangeDeactivatesAndReactivatesAndNeverDeletes() throws Exception {
        String admin = admin(), t = tag();
        int store = create(admin, "Status " + t, "status-" + t).path("storeId").asInt();
        int adminId = jdbc.queryForObject("SELECT MAX(user_id) FROM app_user u JOIN role r ON r.role_id=u.role_id WHERE r.code='SYSTEM_ADMIN'", Integer.class);
        send(patch("/api/system/stores/" + store + "/status"), admin, "{\"expectedVersion\":1,\"status\":\"INACTIVE\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.deactivatedAt").isNotEmpty()).andExpect(jsonPath("$.deactivatedBy").value(adminId))
                .andExpect(jsonPath("$.version").value(2));
        send(patch("/api/system/stores/" + store + "/status"), admin, "{\"expectedVersion\":2,\"status\":\"INACTIVE\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Store is already INACTIVE"));
        send(patch("/api/system/stores/" + store + "/status"), admin, "{\"expectedVersion\":1,\"status\":\"ACTIVE\"}")
                .andExpect(status().isConflict());
        send(patch("/api/system/stores/" + store + "/status"), admin, "{\"expectedVersion\":2,\"status\":\"ACTIVE\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.deactivatedAt").value(nullValue())).andExpect(jsonPath("$.deactivatedBy").value(nullValue()));
        send(patch("/api/system/stores/" + store + "/status"), admin, "{\"expectedVersion\":3,\"status\":\"CLOSED\"}")
                .andExpect(status().isBadRequest());
        send(delete("/api/system/stores/" + store), admin, null).andExpect(status().is4xxClientError());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM store WHERE store_id=?", Integer.class, store));
    }

    @Test void pickerListsActiveStoresByDefaultAndFiltersByStatusAndQuery() throws Exception {
        String admin = admin(), t = tag();
        int active = create(admin, "Picker Alpha " + t, "picker-alpha-" + t).path("storeId").asInt();
        int inactive = create(admin, "Picker Beta " + t, "picker-beta-" + t).path("storeId").asInt();
        send(patch("/api/system/stores/" + inactive + "/status"), admin, "{\"expectedVersion\":1,\"status\":\"INACTIVE\"}").andExpect(status().isOk());

        send(get("/api/system/stores?pageSize=100&q=" + t), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].storeId", contains(active)))
                .andExpect(jsonPath("$.items[0].name").value("Picker Alpha " + t))
                .andExpect(jsonPath("$.items[0].slug").value("picker-alpha-" + t))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.totalElements").value(1));
        send(get("/api/system/stores?pageSize=100&status=INACTIVE&q=" + t), admin, null)
                .andExpect(jsonPath("$.items[*].storeId", contains(inactive)));
        send(get("/api/system/stores?pageSize=100&status=ALL&q=PICKER-" + t.toUpperCase()), admin, null)
                .andExpect(jsonPath("$.items.length()").value(0));
        send(get("/api/system/stores?pageSize=100&status=ALL&q=" + t.toUpperCase()), admin, null)
                .andExpect(jsonPath("$.items[*].storeId", contains(active, inactive)));
        send(get("/api/system/stores?status=CLOSED"), admin, null).andExpect(status().isBadRequest());
    }

    @Test void onlySystemAdminMayUseTheAdministrationRoutes() throws Exception {
        String t = tag();
        int store = create(admin(), "Rbac " + t, "rbac-" + t).path("storeId").asInt();
        for (String role : List.of("STORE_ADMIN", "MERCHANDISER", "END_CUSTOMER")) {
            String bearer = token(role, role.equals("END_CUSTOMER") ? null : store);
            send(get("/api/system/stores"), bearer, null).andExpect(status().isForbidden());
            send(post("/api/system/stores"), bearer, "{\"name\":\"X " + t + "\",\"slug\":\"x-" + t + "\"}").andExpect(status().isForbidden());
            send(get("/api/system/stores/" + store), bearer, null).andExpect(status().isForbidden());
            send(put("/api/system/stores/" + store), bearer, "{\"expectedVersion\":1,\"name\":\"X\",\"slug\":\"x\"}").andExpect(status().isForbidden());
            send(patch("/api/system/stores/" + store + "/status"), bearer, "{\"expectedVersion\":1,\"status\":\"INACTIVE\"}").andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/system/stores/" + store)).andExpect(status().isUnauthorized());
    }
}

package vn.edu.fpt.paspm.catalog.store;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;

/** DEC-20261123-01 §3: own Store profile (UC35). Store Admin edits address/phone only; Merchandiser reads. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:storeprofile;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "paspm.jwt-secret=01234567890123456789012345678901", "paspm.otp-secret=01234567890123456789012345678901",
        "paspm.bootstrap-email=", "paspm.bootstrap-password=",
        "paspm.media.root=/tmp/paspm-store-profile-media", "paspm.avatar.directory=/tmp/paspm-store-profile-media/avatars"})
@AutoConfigureMockMvc
class StoreProfileHttpTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;

    String token(String role, Integer store) {
        var user = users.createPendingUser("Profile " + role, UUID.randomUUID() + "@example.test", role, store, null, null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?", user.id());
        return "Bearer " + auth.token(users.findById(user.id())).accessToken();
    }

    int store(String name) {
        jdbc.update("INSERT INTO store(name,slug,address) VALUES (?,?,?)", name, "profile-" + UUID.randomUUID().toString().substring(0, 8), "Old address");
        return jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
    }

    @Test void storeUsersReadTheirOwnStoreTakenFromTheAccount() throws Exception {
        String name = "Profile " + UUID.randomUUID();
        int store = store(name);
        store("Other " + UUID.randomUUID());
        for (String role : new String[]{"STORE_ADMIN", "MERCHANDISER"})
            mvc.perform(get("/api/store/profile").header("Authorization", token(role, store))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.storeId").value(store)).andExpect(jsonPath("$.name").value(name))
                    .andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.address").value("Old address"))
                    .andExpect(jsonPath("$.iconUrl").value(nullValue())).andExpect(jsonPath("$.version").value(1));
        for (String role : new String[]{"SYSTEM_ADMIN", "END_CUSTOMER"})
            mvc.perform(get("/api/store/profile").header("Authorization", token(role, null))).andExpect(status().isForbidden());
        mvc.perform(get("/api/store/profile")).andExpect(status().isUnauthorized());
    }

    @Test void storeAdminEditsOnlyContactFieldsWithOptimisticLocking() throws Exception {
        int store = store("Edit " + UUID.randomUUID());
        String admin = token("STORE_ADMIN", store);
        mvc.perform(put("/api/store/profile").header("Authorization", admin).contentType("application/json")
                        .content("{\"expectedVersion\":1,\"address\":\" 5 Nguyễn Huệ \",\"phone\":\"0901 234 567\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.address").value("5 Nguyễn Huệ"))
                .andExpect(jsonPath("$.phone").value("0901 234 567")).andExpect(jsonPath("$.version").value(2));
        mvc.perform(put("/api/store/profile").header("Authorization", admin).contentType("application/json")
                .content("{\"expectedVersion\":1,\"address\":\"Stale\",\"phone\":null}")).andExpect(status().isConflict());
        for (String forbidden : new String[]{"\"name\":\"New name\"", "\"slug\":\"new-slug\"", "\"status\":\"INACTIVE\""})
            mvc.perform(put("/api/store/profile").header("Authorization", admin).contentType("application/json")
                    .content("{\"expectedVersion\":2,\"address\":null,\"phone\":null," + forbidden + "}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/store/profile").header("Authorization", admin).contentType("application/json")
                .content("{\"expectedVersion\":2,\"address\":null,\"phone\":\"not a phone\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/store/profile").header("Authorization", admin).contentType("application/json")
                        .content("{\"expectedVersion\":2,\"address\":\"  \",\"phone\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.address").value(nullValue())).andExpect(jsonPath("$.version").value(3));
        mvc.perform(put("/api/store/profile").header("Authorization", token("MERCHANDISER", store)).contentType("application/json")
                .content("{\"expectedVersion\":3,\"address\":\"x\",\"phone\":null}")).andExpect(status().isForbidden());
    }
}

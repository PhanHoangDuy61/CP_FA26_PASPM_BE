package vn.edu.fpt.paspm.mining;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Decision D1 (confirmed 2026-11-02): with the shipped configuration System Admin reads Store snapshots, requests and runs,
 * raw patterns stay closed, and no other role gains anything.
 */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:miningreadgate;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true","paspm.jwt-secret=01234567890123456789012345678901","paspm.otp-secret=01234567890123456789012345678901","paspm.bootstrap-email=","paspm.bootstrap-password=","paspm.avatar.directory=/tmp/paspm-readgate-avatars"})
@AutoConfigureMockMvc
class MiningSystemStoreReadGateTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    String token(String role, Integer store) {
        var user=users.createPendingUser("Gate test",UUID.randomUUID()+"@example.test",role,store,null,null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",user.id());
        return auth.token(users.findById(user.id())).accessToken();
    }
    static List<String> fiveReadPaths(String base) {
        return List.of(base+"/snapshots",base+"/requests",base+"/requests/1",base+"/requests/1/runs",base+"/runs/1/patterns");
    }
    @Test void snapshotsRequestsAndRunsAreOpenByDefaultForSystemAdminAndPatternsAreNot() throws Exception {
        jdbc.update("INSERT INTO store(name) VALUES (?)","Read gate "+UUID.randomUUID());
        int store=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        String bearer="Bearer "+token("SYSTEM_ADMIN",null), base="/api/system/mining/stores/"+store, unknown="/api/system/mining/stores/99999999";
        mvc.perform(get(base+"/snapshots").header("Authorization",bearer)).andExpect(status().isOk());
        mvc.perform(get(base+"/requests").header("Authorization",bearer)).andExpect(status().isOk());
        mvc.perform(get(base+"/requests/987654").header("Authorization",bearer)).andExpect(status().isNotFound());
        mvc.perform(get(base+"/requests/987654/runs").header("Authorization",bearer)).andExpect(status().isNotFound());
        for(String path:List.of("/snapshots","/requests","/requests/1","/requests/1/runs"))
            mvc.perform(get(unknown+path).header("Authorization",bearer)).andExpect(status().isNotFound());
        // patterns: 403 with the same answer for a known and an unknown Store, so the refusal cannot probe Store ids
        for(String prefix:List.of(base,unknown))
            mvc.perform(get(prefix+"/runs/1/patterns").header("Authorization",bearer)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("raw mining patterns")));
    }
    @Test void gateDoesNotWeakenRolesAndDoesNotCoverTheRequiredBenchmarkRoute() throws Exception {
        jdbc.update("INSERT INTO store(name) VALUES (?)","Read gate roles "+UUID.randomUUID());
        int store=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        String system=token("SYSTEM_ADMIN",null);
        for(String role:List.of("MERCHANDISER","STORE_ADMIN","END_CUSTOMER")) {
            String bearer="Bearer "+token(role,role.equals("END_CUSTOMER")?null:store);
            for(String path:fiveReadPaths("/api/system/mining/stores/"+store))
                mvc.perform(get(path).header("Authorization",bearer)).andExpect(status().isForbidden())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("decision D1"))));
        }
        for(String path:fiveReadPaths("/api/system/mining/stores/"+store))mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/system/mining/stores/"+store+"/runs/987654/benchmark").header("Authorization","Bearer "+system)).andExpect(status().isNotFound());
        mvc.perform(get("/api/system/mining/config").header("Authorization","Bearer "+system)).andExpect(status().isOk());
    }
}

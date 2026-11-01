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
 * Decision D1 option "b" (status and runs only): exactly REQUESTS and RUNS are opened, SNAPSHOTS and PATTERNS stay refused.
 * Opening one group must never open another.
 */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:miningreadgranular;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true","paspm.jwt-secret=01234567890123456789012345678901","paspm.otp-secret=01234567890123456789012345678901","paspm.bootstrap-email=","paspm.bootstrap-password=","paspm.avatar.directory=/tmp/paspm-readgranular-avatars","paspm.mining.system-store-read.routes=REQUESTS,RUNS"})
@AutoConfigureMockMvc
class MiningSystemStoreReadGranularGateTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    String token(String role, Integer store) {
        var user=users.createPendingUser("Granular gate",UUID.randomUUID()+"@example.test",role,store,null,null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",user.id());
        return auth.token(users.findById(user.id())).accessToken();
    }
    @Test void onlyTheNamedGroupsAreOpenAndTheOthersStayRefusedBeforeAnyStoreLookup() throws Exception {
        jdbc.update("INSERT INTO store(name) VALUES (?)","Granular "+UUID.randomUUID());
        int store=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        String system=token("SYSTEM_ADMIN",null), bearer="Bearer "+system, base="/api/system/mining/stores/"+store, unknown="/api/system/mining/stores/99999999";
        // opened groups: real answers (200 list, 404 for an id that does not exist) — never the gate's 403
        mvc.perform(get(base+"/requests").header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(base+"/requests/987654").header("Authorization",bearer)).andExpect(status().isNotFound());
        mvc.perform(get(base+"/requests/987654/runs").header("Authorization",bearer)).andExpect(status().isNotFound());
        mvc.perform(get(unknown+"/requests").header("Authorization",bearer)).andExpect(status().isNotFound());
        // closed groups: 403 with the decision reference, identical for a known and an unknown Store
        for(String prefix:List.of(base,unknown)) {
            mvc.perform(get(prefix+"/snapshots").header("Authorization",bearer)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("pending Product Owner approval"),org.hamcrest.Matchers.containsString("SNAPSHOTS"))));
            mvc.perform(get(prefix+"/runs/1/patterns").header("Authorization",bearer)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("pending Product Owner approval"),org.hamcrest.Matchers.containsString("PATTERNS"))));
        }
        // roles are unchanged: opening a group for System Admin never opens it for anyone else
        for(String role:List.of("MERCHANDISER","STORE_ADMIN","END_CUSTOMER")) {
            String other="Bearer "+token(role,role.equals("END_CUSTOMER")?null:store);
            for(String path:List.of(base+"/requests",base+"/requests/1",base+"/requests/1/runs",base+"/snapshots",base+"/runs/1/patterns"))
                mvc.perform(get(path).header("Authorization",other)).andExpect(status().isForbidden());
        }
        mvc.perform(get(base+"/requests")).andExpect(status().isUnauthorized());
        // the approved benchmark route is not part of the gate
        mvc.perform(get(base+"/runs/987654/benchmark").header("Authorization",bearer)).andExpect(status().isNotFound());
    }
}

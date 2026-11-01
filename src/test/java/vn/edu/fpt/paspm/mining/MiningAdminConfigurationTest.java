package vn.edu.fpt.paspm.mining;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:miningadmin;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true","paspm.jwt-secret=01234567890123456789012345678901","paspm.otp-secret=01234567890123456789012345678901","paspm.bootstrap-email=","paspm.bootstrap-password=","paspm.avatar.directory=/tmp/paspm-admin-test-avatars"})
@AutoConfigureMockMvc
class MiningAdminConfigurationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    String token(String role, Integer store) {
        var user=users.createPendingUser("Admin test",UUID.randomUUID()+"@example.test",role,store,null,null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",user.id());
        return auth.token(users.findById(user.id())).accessToken();
    }
    @Test void schedulesRequireSystemAdminAndValidateStoreAndIanaZone() throws Exception {
        jdbc.update("INSERT INTO store(name) VALUES (?)", "Schedule " + UUID.randomUUID());
        int store=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        String system=token("SYSTEM_ADMIN",null), merchant=token("MERCHANDISER",store);
        String body="{\"storeId\":"+store+",\"frequency\":\"WEEKLY\",\"dayOfWeek\":1,\"localTime\":\"09:00\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"lookbackDays\":7}";
        mvc.perform(post("/api/system/mining/schedules").header("Authorization","Bearer "+merchant).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/system/mining/schedules").header("Authorization","Bearer "+system).contentType(MediaType.APPLICATION_JSON).content(body.replace("Asia/Ho_Chi_Minh","not/a-zone"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/system/mining/schedules").header("Authorization","Bearer "+system).contentType(MediaType.APPLICATION_JSON).content(body.replace("Asia/Ho_Chi_Minh","+07:00"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/system/mining/schedules").header("Authorization","Bearer "+system).contentType(MediaType.APPLICATION_JSON).content(body.replace("\"storeId\":"+store,"\"storeId\":1.5"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/system/mining/schedules").header("Authorization","Bearer "+system).contentType(MediaType.APPLICATION_JSON).content(body.replace("\"storeId\":"+store,"\"storeId\":99999999"))).andExpect(status().isNotFound());
        String created=mvc.perform(post("/api/system/mining/schedules").header("Authorization","Bearer "+system).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.storeId").value(store)).andReturn().getResponse().getContentAsString();
        long id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).path("scheduleId").asLong();
        mvc.perform(get("/api/system/mining/schedules")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/system/mining/schedules").header("Authorization","Bearer "+merchant)).andExpect(status().isForbidden());
        mvc.perform(get("/api/system/mining/schedules").header("Authorization","Bearer "+system).param("page","0").param("pageSize","10"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].scheduleId").value(id)).andExpect(jsonPath("$.totalElements").value(1));
        int actor=jdbc.queryForObject("SELECT MAX(user_id) FROM app_user u JOIN role r ON r.role_id=u.role_id WHERE r.code='SYSTEM_ADMIN'",Integer.class);
        jdbc.update("INSERT INTO mining_config_version(config_version,algorithm,min_util_mode,min_util_value,changed_by) VALUES(987654321,'EFIM','ABSOLUTE',10,?)",actor);
        jdbc.update("INSERT INTO mining_schedule_occurrence(schedule_id,store_id,scheduled_at,schedule_version,config_version,request_key,status) VALUES(?,?,CURRENT_TIMESTAMP,1,987654321,?,'PENDING')",id,store,UUID.randomUUID().toString());
        long occurrence=jdbc.queryForObject("SELECT MAX(occurrence_id) FROM mining_schedule_occurrence",Long.class);
        mvc.perform(get("/api/system/mining/schedules/"+id+"/occurrences/"+occurrence).header("Authorization","Bearer "+merchant)).andExpect(status().isForbidden());
        mvc.perform(get("/api/system/mining/schedules/"+id+"/occurrences/"+occurrence).header("Authorization","Bearer "+system))
            .andExpect(status().isOk()).andExpect(jsonPath("$.occurrenceId").value(occurrence)).andExpect(jsonPath("$.storeId").value(store));
        mvc.perform(get("/api/system/mining/schedules/"+id+"/occurrences/"+(occurrence+999)).header("Authorization","Bearer "+system)).andExpect(status().isNotFound());
        String update="{\"schedule\":"+body+",\"enabled\":true,\"expectedVersion\":1}";
        mvc.perform(put("/api/system/mining/schedules/"+id).header("Authorization","Bearer "+system).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/system/mining/schedules/"+id).header("Authorization","Bearer "+system)).andExpect(jsonPath("$.enabled").value(false));
        String admin=token("STORE_ADMIN",store), customer=token("END_CUSTOMER",null);
        for(String denied:new String[]{merchant,admin,customer}) {
            String bearer="Bearer "+denied;
            mvc.perform(post("/api/system/mining/schedules").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
            mvc.perform(get("/api/system/mining/schedules").header("Authorization",bearer)).andExpect(status().isForbidden());
            mvc.perform(get("/api/system/mining/schedules/"+id).header("Authorization",bearer)).andExpect(status().isForbidden());
            mvc.perform(put("/api/system/mining/schedules/"+id).header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isForbidden());
            mvc.perform(get("/api/system/mining/schedules/"+id+"/occurrences").header("Authorization",bearer)).andExpect(status().isForbidden());
            mvc.perform(get("/api/system/mining/schedules/"+id+"/occurrences/"+occurrence).header("Authorization",bearer)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/system/mining/schedules/"+id+"/occurrences").header("Authorization","Bearer "+system))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].occurrenceId").value(occurrence));
        String disable="{\"schedule\":"+body+",\"enabled\":false,\"expectedVersion\":1}";
        mvc.perform(put("/api/system/mining/schedules/"+id).header("Authorization","Bearer "+system).contentType(MediaType.APPLICATION_JSON).content(disable))
            .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.version").value(2));
        mvc.perform(get("/api/system/mining/schedules/"+id).header("Authorization","Bearer "+system)).andExpect(jsonPath("$.version").value(2));
    }
    @Test void systemAdminVersionsConfigAndOtherRolesCannotReadOrWrite() throws Exception {
        jdbc.update("INSERT INTO store(name) VALUES (?)", "Config " + UUID.randomUUID());
        int store=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        String system=token("SYSTEM_ADMIN",null), merchant=token("MERCHANDISER",store), admin=token("STORE_ADMIN",store), customer=token("END_CUSTOMER",null);
        String body="{\"algorithm\":\"EFIM\",\"minUtilMode\":\"PERCENTAGE\",\"minUtilValue\":5,\"expectedVersion\":0}";
        mvc.perform(get("/api/system/mining/config")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/system/mining/config").header("Authorization","Bearer "+merchant).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
        for(String denied:new String[]{merchant,admin,customer}) {
            mvc.perform(get("/api/system/mining/config").header("Authorization","Bearer "+denied)).andExpect(status().isForbidden());
            mvc.perform(put("/api/system/mining/config").header("Authorization","Bearer "+denied).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/system/mining/config").header("Authorization","Bearer "+system)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        mvc.perform(put("/api/system/mining/config").header("Authorization","Bearer "+system).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.algorithm").value("EFIM"));
        mvc.perform(put("/api/system/mining/config").header("Authorization","Bearer "+system).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        mvc.perform(put("/api/system/mining/config").header("Authorization","Bearer "+system).contentType(MediaType.APPLICATION_JSON).content(body.replace("\"expectedVersion\":0","\"expectedVersion\":1").replace("\"minUtilValue\":5","\"minUtilValue\":101"))).andExpect(status().isBadRequest());
    }
}

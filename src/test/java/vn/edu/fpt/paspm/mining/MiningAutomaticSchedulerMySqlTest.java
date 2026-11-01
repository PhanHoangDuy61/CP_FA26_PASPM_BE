package vn.edu.fpt.paspm.mining;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;
import vn.edu.fpt.paspm.user.UserRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Isolated MySQL: explicitly enables test polling, but the public enable API remains guarded. */
@EnabledIfSystemProperty(named="paspm.test.mysql",matches="true")
@SpringBootTest(properties={"paspm.jwt-secret=01234567890123456789012345678901","paspm.otp-secret=01234567890123456789012345678901","paspm.bootstrap-email=","paspm.bootstrap-password=","paspm.avatar.directory=/tmp/paspm-auto-test-avatars","paspm.mining.schedule-dispatch-enabled=true","paspm.mining.spmf-jar=${paspm.test.spmf-jar}","paspm.mining.java-executable=${paspm.test.spmf-java}"})
@AutoConfigureMockMvc
class MiningAutomaticSchedulerMySqlTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){MySqlTestDatabase.create().register(registry);}
    @Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired AuthService auth;@Autowired ObjectMapper json;
    String token(String role,Integer store){var u=users.createPendingUser("Auto test",UUID.randomUUID()+"@example.test",role,store,null,null);jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",u.id());return auth.token(users.findById(u.id())).accessToken();}
    @Test void enabledFixtureIsPolledCreatesFreshSnapshotAndRunsBothAlgorithms()throws Exception{
        jdbc.update("INSERT INTO store(name) VALUES ('Synthetic scheduled store')");int store=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        String system=token("SYSTEM_ADMIN",null),merchant=token("MERCHANDISER",store);
        jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,'SCHEDULE-1','Synthetic','each',FALSE,100,20,'ACTIVE')",store);
        int product=jdbc.queryForObject("SELECT MAX(product_id) FROM product",Integer.class);
        String key=UUID.randomUUID().toString();jdbc.update("INSERT INTO sales_transaction(store_id,source_system,source_order_id,occurred_at,status,f03_eligible,total_amount,content_sha256) VALUES (?,'TEST',?,?, 'COMPLETED',TRUE,100,?)",store,key,Timestamp.from(Instant.now().minusSeconds(86400)),"a".repeat(64));
        int tx=jdbc.queryForObject("SELECT MAX(transaction_id) FROM sales_transaction WHERE store_id=?",Integer.class,store);
        jdbc.update("INSERT INTO transaction_item(store_id,transaction_id,product_id,quantity,unit_price,unit_cost) VALUES (?,?,?,1,100,20)",store,tx,product);
        mvc.perform(put("/api/system/mining/config").header("Authorization","Bearer "+system).contentType(MediaType.APPLICATION_JSON).content("{\"algorithm\":\"HUI_MINER\",\"minUtilMode\":\"ABSOLUTE\",\"minUtilValue\":1,\"expectedVersion\":0}")).andExpect(status().isOk());
        String schedule="{\"storeId\":"+store+",\"frequency\":\"WEEKLY\",\"dayOfWeek\":1,\"localTime\":\"09:00\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"lookbackDays\":7,\"compareAlgorithms\":true}";
        mvc.perform(post("/api/system/mining/schedules").contentType(MediaType.APPLICATION_JSON).content(schedule)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/system/mining/schedules").header("Authorization","Bearer "+merchant).contentType(MediaType.APPLICATION_JSON).content(schedule)).andExpect(status().isForbidden());
        var created=mvc.perform(post("/api/system/mining/schedules").header("Authorization","Bearer "+system).contentType(MediaType.APPLICATION_JSON).content(schedule)).andExpect(status().isCreated()).andReturn();
        long id=json.readTree(created.getResponse().getContentAsString()).path("scheduleId").asLong();
        mvc.perform(put("/api/system/mining/schedules/"+id).header("Authorization","Bearer "+system).contentType(MediaType.APPLICATION_JSON).content("{\"schedule\":"+schedule+",\"enabled\":true,\"expectedVersion\":1}")).andExpect(status().isServiceUnavailable());
        jdbc.update("UPDATE mining_schedule SET enabled=TRUE,next_fire_at=? WHERE schedule_id=?",Timestamp.from(Instant.now().minusSeconds(10)),id);
        long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(75);String state=null;
        while(System.nanoTime()<end){var values=jdbc.queryForList("SELECT status FROM mining_schedule_occurrence WHERE schedule_id=?",String.class,id);if(!values.isEmpty()){state=values.get(0);if(state.equals("COMPLETED")||state.equals("FAILED"))break;}Thread.sleep(100);}
        assertEquals("COMPLETED",state);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mining_schedule_occurrence WHERE schedule_id=? AND snapshot_id IS NOT NULL AND request_id IS NOT NULL",Integer.class,id));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=? AND status='COMPLETED' AND comparison_status='MATCH'",Integer.class,store));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM mining_run WHERE store_id=? AND status='COMPLETED'",Integer.class,store));
    }
}

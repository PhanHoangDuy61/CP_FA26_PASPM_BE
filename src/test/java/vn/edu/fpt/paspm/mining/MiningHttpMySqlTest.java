package vn.edu.fpt.paspm.mining;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;
import vn.edu.fpt.paspm.user.UserRepository;
import static org.junit.jupiter.api.Assertions.*;

/** Actual embedded HTTP server on Java 17, MySQL, and independently configured SPMF JVM. */
@EnabledIfSystemProperty(named="paspm.test.java17-backend",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "paspm.jwt-secret=01234567890123456789012345678901","paspm.otp-secret=01234567890123456789012345678901",
    "paspm.bootstrap-email=","paspm.bootstrap-password=","paspm.avatar.directory=/tmp/paspm-test-avatars"})
class MiningHttpMySqlTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        MySqlTestDatabase.create().register(registry);
        // Runtime paths intentionally come from actual PASPM_MINING_* process environment variables.
    }
    @Autowired JdbcTemplate jdbc; @Autowired UserRepository users; @Autowired AuthService auth;
    @Autowired TestRestTemplate http; @Autowired ObjectMapper json; @LocalServerPort int port;

    @Test void java17BackendMinesRealSnapshotOverHttpWithJava25Child() throws Exception {
        assertEquals("17",System.getProperty("java.specification.version"));
        jdbc.update("INSERT INTO store(name) VALUES ('P0 Java17 HTTP fixture')");
        int store=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        var user=users.createPendingUser("P0 HTTP",UUID.randomUUID()+"@example.test","MERCHANDISER",store,null,null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",user.id());
        var headers=new HttpHeaders();headers.setBearerAuth(auth.token(users.findById(user.id())).accessToken());headers.setContentType(MediaType.APPLICATION_JSON);
        var products=new ArrayList<Integer>();
        for(int i=1;i<=7;i++) {
            jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,'Frozen','each',FALSE,100,0,'ACTIVE')",store,"M"+i);
            products.add(jdbc.queryForObject("SELECT MAX(product_id) FROM product",Integer.class));
        }
        for(String line:SpmfMiningEngineTest.INPUT.split("\n")) {
            var parts=line.split(":");String key=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO sales_transaction(store_id,source_system,source_order_id,occurred_at,status,f03_eligible,total_amount,content_sha256) VALUES (?,'TEST',?,'2026-10-19 00:00:00','COMPLETED',TRUE,100,?)",store,key,"a".repeat(64));
            int tx=jdbc.queryForObject("SELECT transaction_id FROM sales_transaction WHERE store_id=? AND source_order_id=?",Integer.class,store,key);
            var ids=parts[0].split(" ");var utilities=parts[2].split(" ");
            for(int i=0;i<ids.length;i++)jdbc.update("INSERT INTO transaction_item(store_id,transaction_id,product_id,quantity,unit_price,unit_cost) VALUES (?,?,?,1,?,0)",store,tx,products.get(Integer.parseInt(ids[i])-1),Integer.parseInt(utilities[i]));
        }
        var snapshot=http.exchange("/api/utility/snapshots",HttpMethod.POST,new HttpEntity<>("{\"rangeStart\":\"2026-10-19\",\"rangeEnd\":\"2026-10-19\",\"asOfDate\":\"2026-10-19\"}",headers),JsonNode.class);
        assertEquals(HttpStatus.CREATED,snapshot.getStatusCode());
        String body=json.writeValueAsString(Map.of("requestKey",UUID.randomUUID(),"snapshotId",snapshot.getBody().path("snapshotId").asInt(),"algorithms",List.of("HUI_MINER","EFIM"),"minUtilMode","ABSOLUTE","minUtilValue",30));
        var created=http.exchange("/api/mining/requests",HttpMethod.POST,new HttpEntity<>(body,headers),JsonNode.class);
        assertEquals(HttpStatus.ACCEPTED,created.getStatusCode());long id=created.getBody().path("requestId").asLong();
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);JsonNode request;
        do { request=get("/api/mining/requests/"+id,headers);if(!List.of("PENDING","RUNNING").contains(request.path("status").asText()))break;Thread.sleep(25); } while(System.nanoTime()<deadline);
        assertEquals("COMPLETED",request.path("status").asText());assertEquals("MATCH",request.path("comparisonStatus").asText());
        var runs=get("/api/mining/requests/"+id+"/runs",headers);assertEquals(2,runs.size());
        for(var run:runs) {
            assertEquals(8,run.path("patternCount").asInt());
            assertEquals(request.path("inputHash").asText(),run.path("inputHash").asText());
            assertEquals(8,get("/api/mining/runs/"+run.path("runId").asLong()+"/patterns",headers).path("totalElements").asInt());
        }
        assertEquals(16,jdbc.queryForObject("SELECT COUNT(*) FROM high_utility_pattern WHERE store_id=?",Integer.class,store));
        var spec=http.getForEntity("/v3/api-docs",JsonNode.class);assertEquals(HttpStatus.OK,spec.getStatusCode());assertTrue(spec.getBody().path("paths").has("/api/mining/requests"));
        assertEquals(HttpStatus.UNAUTHORIZED,http.getForEntity("/api/mining/requests",String.class).getStatusCode());
        System.out.println("P0_HTTP backendJava="+System.getProperty("java.version")+" port="+port+" realAlgorithms=HUI_MINER,EFIM patternsPerRun=8 comparison=MATCH MySQLPersistence=16 OpenAPI=PASS");
    }
    private JsonNode get(String path,HttpHeaders headers) {
        var response=http.exchange(path,HttpMethod.GET,new HttpEntity<>(headers),JsonNode.class);
        assertEquals(HttpStatus.OK,response.getStatusCode());return response.getBody();
    }
}

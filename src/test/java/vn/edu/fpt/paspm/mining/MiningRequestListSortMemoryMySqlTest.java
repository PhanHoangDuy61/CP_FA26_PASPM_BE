package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.ResultActions;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;
import vn.edu.fpt.paspm.mining.MiningContracts.CreateRequest;
import vn.edu.fpt.paspm.mining.MiningContracts.MinUtilMode;
import vn.edu.fpt.paspm.user.UserRepository;
import vn.edu.fpt.paspm.utility.UtilitySnapshotRepository;

/**
 * Walk-through defect (2026-11-27): {@code GET /api/mining/requests} answered 500 with MySQL error 1038 "Out of sort memory"
 * once a request's {@code frozen_input} was larger than the server's default {@code sort_buffer_size} (262144 bytes), because
 * the list query sorted whole rows ({@code SELECT * ... ORDER BY request_id DESC}). H2 has no sort buffer, so this only shows
 * on MySQL. The test keeps the server default, stores requests whose frozen input is larger than the sort buffer, and lists them.
 */
@EnabledIfSystemProperty(named="paspm.test.mysql",matches="true")
@SpringBootTest(properties={"spring.flyway.enabled=true","paspm.jwt-secret=01234567890123456789012345678901","paspm.otp-secret=01234567890123456789012345678901",
    "paspm.bootstrap-email=","paspm.bootstrap-password=","paspm.avatar.directory=/tmp/paspm-test-avatars"})
@AutoConfigureMockMvc
class MiningRequestListSortMemoryMySqlTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){MySqlTestDatabase.create().register(registry);}
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    @Autowired UserRepository users; @Autowired AuthService auth;
    @Autowired MiningRepository repository; @Autowired MiningInputBuilder builder; @Autowired UtilitySnapshotRepository snapshots;
    int store,snapshot,actor;String merchant,foreign;
    final List<Long> created=new ArrayList<>();

    @BeforeEach void setup()throws Exception{
        jdbc.update("INSERT INTO store(name) VALUES (?)","Sort memory "+UUID.randomUUID());store=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        merchant=token("MERCHANDISER",store);actor=jdbc.queryForObject("SELECT MAX(user_id) FROM app_user",Integer.class);
        jdbc.update("INSERT INTO store(name) VALUES (?)","Other "+UUID.randomUUID());foreign=token("MERCHANDISER",jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class));
        var products=new ArrayList<Integer>();
        for(int i=1;i<=7;i++){
            jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,'Frozen','each',FALSE,100,0,'ACTIVE')",store,"S"+i);
            products.add(jdbc.queryForObject("SELECT MAX(product_id) FROM product",Integer.class));
        }
        for(String line:SpmfMiningEngineTest.INPUT.split("\n")){
            var parts=line.split(":");String key=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO sales_transaction(store_id,source_system,source_order_id,occurred_at,status,f03_eligible,total_amount,content_sha256) VALUES (?,'TEST',?,'2026-10-19 00:00:00','COMPLETED',TRUE,100,?)",store,key,"a".repeat(64));
            int tx=jdbc.queryForObject("SELECT transaction_id FROM sales_transaction WHERE store_id=? AND source_order_id=?",Integer.class,store,key);
            String[] ids=parts[0].split(" "),utils=parts[2].split(" ");
            for(int i=0;i<ids.length;i++)jdbc.update("INSERT INTO transaction_item(store_id,transaction_id,product_id,quantity,unit_price,unit_cost) VALUES (?,?,?,1,?,0)",store,tx,products.get(Integer.parseInt(ids[i])-1),Integer.parseInt(utils[i]));
        }
        var made=mvc.perform(post("/api/utility/snapshots").header("Authorization","Bearer "+merchant).contentType(MediaType.APPLICATION_JSON)
            .content("{\"rangeStart\":\"2026-10-19\",\"rangeEnd\":\"2026-10-19\",\"asOfDate\":\"2026-10-19\"}")).andExpect(status().isCreated()).andReturn();
        snapshot=json.readTree(made.getResponse().getContentAsString()).path("snapshotId").asInt();
        // Requests are stored the way MiningService does it, but without starting SPMF (no worker, no dispatch).
        var view=snapshots.find(store,snapshot);var rows=snapshots.page(store,snapshot,0,MiningInputBuilder.MAX_ROWS);
        var prepared=builder.prepare(view,rows,MinUtilMode.ABSOLUTE,BigDecimal.valueOf(30));
        for(int i=0;i<4;i++){
            var request=new CreateRequest(UUID.randomUUID(),snapshot,List.of(MiningEngine.Algorithm.EFIM),MinUtilMode.ABSOLUTE,BigDecimal.valueOf(30));
            created.add(repository.create(store,actor,request,"hash-"+i,view,prepared.input(),prepared.threshold()).view().requestId());
        }
    }

    /** Grows the stored {@code frozen_input} past the sort buffer while keeping it valid JSON for the reader. */
    long inflateFrozenInputBeyondSortBuffer()throws Exception{
        long sortBuffer=jdbc.queryForObject("SELECT @@session.sort_buffer_size",Long.class);
        long target=sortBuffer+100_000;
        for(long id:created){
            ObjectNode input=(ObjectNode)json.readTree(jdbc.queryForObject("SELECT frozen_input FROM mining_request WHERE request_id=?",String.class,id));
            input.put("text",input.path("text").asText()+" ".repeat((int)target));
            jdbc.update("UPDATE mining_request SET frozen_input=? WHERE request_id=?",json.writeValueAsString(input),id);
            assertTrue(jdbc.queryForObject("SELECT LENGTH(frozen_input) FROM mining_request WHERE request_id=?",Long.class,id)>sortBuffer,
                "the frozen input must be larger than the server's sort_buffer_size so a row sort cannot fit it");
        }
        return sortBuffer;
    }
    ResultActions list(String token,String query)throws Exception{return mvc.perform(get("/api/mining/requests"+query).header("Authorization","Bearer "+token));}
    List<Long> ids(JsonNode page){var out=new ArrayList<Long>();page.path("items").forEach(n->out.add(n.path("requestId").asLong()));return out;}

    @Test void listingRequestsWhoseFrozenInputExceedsTheSortBufferStillAnswers200InNewestFirstOrder()throws Exception {
        long sortBuffer=inflateFrozenInputBeyondSortBuffer();
        var expected=new ArrayList<>(created);Collections.reverse(expected);
        var body=json.readTree(list(merchant,"?page=0&pageSize=20").andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(expected,ids(body),"newest request first");
        assertEquals(4,body.path("totalElements").asInt());
        assertEquals(0,body.path("page").asInt());assertEquals(20,body.path("pageSize").asInt());
        assertTrue(sortBuffer>0);
    }
    @Test void pagingAndTotalAreUnchanged()throws Exception {
        inflateFrozenInputBeyondSortBuffer();
        var expected=new ArrayList<>(created);Collections.reverse(expected);
        var first=json.readTree(list(merchant,"?page=0&pageSize=3").andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var second=json.readTree(list(merchant,"?page=1&pageSize=3").andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(expected.subList(0,3),ids(first));assertEquals(expected.subList(3,4),ids(second));
        assertEquals(4,first.path("totalElements").asInt());assertEquals(4,second.path("totalElements").asInt());
        assertEquals(List.of(),ids(json.readTree(list(merchant,"?page=2&pageSize=3").andExpect(status().isOk()).andReturn().getResponse().getContentAsString())));
    }
    @Test void everyListedItemEqualsItsDetailAndAnotherStoreSeesNone()throws Exception {
        inflateFrozenInputBeyondSortBuffer();
        var body=json.readTree(list(merchant,"?page=0&pageSize=20").andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for(JsonNode item:body.path("items")){
            var detail=json.readTree(mvc.perform(get("/api/mining/requests/"+item.path("requestId").asLong()).header("Authorization","Bearer "+merchant))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals(detail,item,"the list item is the same view as the detail");
        }
        list(foreign,"?page=0&pageSize=20").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.items.length()").value(0));
    }
    String token(String role,Integer s){var u=users.createPendingUser("Sort QA",UUID.randomUUID()+"@example.test",role,s,null,null);jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",u.id());return auth.token(users.findById(u.id())).accessToken();}
}

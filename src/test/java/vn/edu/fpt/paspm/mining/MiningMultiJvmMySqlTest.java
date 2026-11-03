package vn.edu.fpt.paspm.mining;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;
import vn.edu.fpt.paspm.user.UserRepository;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: two independent Java 17 Backend processes against a new disposable MySQL schema. */
@EnabledIfSystemProperty(named="paspm.test.multijvm",matches="true")
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "paspm.jwt-secret=01234567890123456789012345678901","paspm.otp-secret=01234567890123456789012345678901",
 "paspm.bootstrap-email=","paspm.bootstrap-password=","paspm.avatar.directory=/tmp/paspm-test-avatars"})
class MiningMultiJvmMySqlTest {
    static MySqlTestDatabase db;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){db=MySqlTestDatabase.create();db.register(registry);}
    @Autowired JdbcTemplate jdbc; @Autowired UserRepository users; @Autowired AuthService auth;
    @Autowired TestRestTemplate parentHttp; @Autowired ObjectMapper json; @Autowired MiningWorker parentWorker;
    final TestRestTemplate http=new TestRestTemplate();
    record Node(Process process,int port,Path dir,Path log) {}
    record Lease(String owner,long fence,Timestamp until,String status) {}
    int store,snapshot; HttpHeaders admin,merchant;
    final List<Node> nodes=new ArrayList<>();
    final List<String> evidence=new ArrayList<>();

    @Test void gracefulShutdownStopsActiveSpmfAndAllowsRecovery() throws Exception {
        parentWorker.close();fixture();Path base=Files.createTempDirectory("paspm-graceful-stop-");ProcessHandle child=null;
        try {
            Node a=start(base.resolve("a"));Files.writeString(a.dir.resolve("hold-before-spmf"),"hold");
            long id=submit(a);waitFor(()->Files.exists(a.dir.resolve("before-spmf")),30000,"graceful-stop SPMF barrier");
            Lease initial=lease(id);Files.delete(a.dir.resolve("hold-before-spmf"));child=awaitSpmfChild(a);
            assertEquals(0,new ProcessBuilder("kill","-STOP",Long.toString(child.pid())).start().waitFor());
            a.process.destroy();assertTrue(a.process.waitFor(20,TimeUnit.SECONDS),"Backend must exit on graceful signal");
            ProcessHandle observed=child;waitFor(()->!executing(observed),10000,"SPMF stops after graceful shutdown");
            long childPid=child.pid();if("true".equals(System.getenv("PASPM_TEST_REQUIRE_REAP")))waitFor(()->!Files.exists(Path.of("/proc",Long.toString(childPid))),10000,"init reaps stopped SPMF");
            Node b=start(base.resolve("b"));waitTerminal(id,65000);assertSuccessful(id,8);
            evidence.add("graceful_shutdown A_pid="+a.process.pid()+" B_pid="+b.process.pid()+" spmf_pid="+childPid+" request="+id+" owner="+initial.owner+" fence="+initial.fence+" active_spmf_terminated=true recovered_patterns=8 PASS");
        } finally {
            if(child!=null&&child.isAlive())new ProcessBuilder("kill","-KILL",Long.toString(child.pid())).start().waitFor();
            for(Node n:nodes)if(n.process.isAlive()){n.process.destroyForcibly();n.process.waitFor(10,TimeUnit.SECONDS);}
            saveEvidence();
        }
    }

    @Test void revokedLeaseStopsActiveSpmfAndAllowsRecovery() throws Exception {
        parentWorker.close();fixture();Path base=Files.createTempDirectory("paspm-lease-loss-");ProcessHandle child=null;
        try {
            Node a=start(base.resolve("a"));Files.writeString(a.dir.resolve("hold-before-spmf"),"hold");
            long id=submit(a);waitFor(()->Files.exists(a.dir.resolve("before-spmf")),30000,"lease-loss SPMF barrier");
            Lease initial=lease(id);Files.delete(a.dir.resolve("hold-before-spmf"));child=awaitSpmfChild(a);
            assertEquals(0,new ProcessBuilder("kill","-STOP",Long.toString(child.pid())).start().waitFor());
            jdbc.update("UPDATE mining_request SET lease_owner='test-revoked-owner',lease_fence=lease_fence+1 WHERE request_id=?",id);
            ProcessHandle observed=child;waitFor(()->!executing(observed),20000,"lease loss stops active SPMF");
            assertTrue(a.process.isAlive());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM high_utility_pattern p JOIN mining_run r ON p.run_id=r.run_id WHERE r.request_id=?",Integer.class,id));
            signal(a,"STOP");jdbc.update("UPDATE mining_request SET lease_until=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP) WHERE request_id=?",id);
            Node b=start(base.resolve("b"));waitTerminal(id,65000);assertSuccessful(id,8);signal(a,"CONT");
            evidence.add("lease_loss A_pid="+a.process.pid()+" B_pid="+b.process.pid()+" spmf_pid="+child.pid()+" request="+id+" old_owner="+initial.owner+" old_fence="+initial.fence+" active_spmf_terminated=true stale_patterns=0 recovered_patterns=8 PASS");
        } finally {
            if(child!=null&&child.isAlive())new ProcessBuilder("kill","-KILL",Long.toString(child.pid())).start().waitFor();
            for(Node n:nodes)if(n.process.isAlive()){try{signal(n,"CONT");}catch(Exception ignored){}n.process.destroyForcibly();n.process.waitFor(10,TimeUnit.SECONDS);}
            saveEvidence();
        }
    }

    @org.junit.jupiter.api.RepeatedTest(2) void killedDuringRealSpmfRecoversAndInspectsOrphan() throws Exception {
        parentWorker.close();fixture();Path base=Files.createTempDirectory("paspm-spmf-crash-");ProcessHandle child=null,otherChild=null;
        try {
            Node a=start(base.resolve("a"));Files.writeString(a.dir.resolve("hold-before-spmf"),"hold");
            long id=submit(a);waitFor(()->Files.exists(a.dir.resolve("before-spmf")),30000,"before actual SPMF start");
            Lease first=lease(id);Node b=start(base.resolve("b"));assertEquals(first.owner,lease(id).owner);
            Files.writeString(b.dir.resolve("hold-before-spmf"),"hold");long other=submit(b);
            waitFor(()->Files.exists(b.dir.resolve("before-spmf")),30000,"second actual SPMF barrier");
            Files.delete(b.dir.resolve("hold-before-spmf"));otherChild=awaitSpmfChild(b);
            assertEquals(0,new ProcessBuilder("kill","-STOP",Long.toString(otherChild.pid())).start().waitFor());assertTrue(otherChild.isAlive());
            Files.delete(a.dir.resolve("hold-before-spmf"));
            child=awaitSpmfChild(a);
            assertEquals(0,new ProcessBuilder("kill","-STOP",Long.toString(child.pid())).start().waitFor());
            assertTrue(child.isAlive(),"actual SPMF child still running when backend is killed");
            a.process.destroyForcibly();assertTrue(a.process.waitFor(10,TimeUnit.SECONDS));
            if("Linux".equalsIgnoreCase(System.getProperty("os.name"))) {ProcessHandle observed=child;waitFor(()->!executing(observed),10000,"kernel parent-death cleanup");}
            boolean orphanAlive=executing(child);long childPid=child.pid();
            if("true".equals(System.getenv("PASPM_TEST_REQUIRE_REAP")))waitFor(()->!Files.exists(Path.of("/proc",Long.toString(childPid))),10000,"container init reaps terminated SPMF");
            boolean reaped="Linux".equalsIgnoreCase(System.getProperty("os.name"))&&!Files.exists(Path.of("/proc",Long.toString(childPid)));
            assertTrue(executing(otherChild),"B's independently owned SPMF must not be killed");
            assertEquals(0,new ProcessBuilder("kill","-CONT",Long.toString(otherChild.pid())).start().waitFor());waitTerminal(other,35000);assertSuccessful(other,8);
            if(orphanAlive){new ProcessBuilder("kill","-KILL",Long.toString(childPid)).start().waitFor();}
            waitTerminal(id,65000);Lease recovered=lease(id);assertTrue(recovered.fence>first.fence);assertSuccessful(id,8);
            evidence.add("real_spmf_crash killed_A_pid="+a.process.pid()+" B_pid="+b.process.pid()+" spmf_java25_pid="+childPid+" other_spmf_pid="+otherChild.pid()+" other_spmf_survived=true request="+id+" orphan_alive_after_backend_sigkill="+orphanAlive+" reaped_by_container_init="+reaped+" orphan_test_cleanup="+(orphanAlive?"SIGKILL":"not_needed")+" old_fence="+first.fence+" new_fence="+recovered.fence+" patterns=8 PASS");
        } finally {
            if(child!=null&&child.isAlive())new ProcessBuilder("kill","-KILL",Long.toString(child.pid())).start().waitFor();
            if(otherChild!=null&&otherChild.isAlive())new ProcessBuilder("kill","-KILL",Long.toString(otherChild.pid())).start().waitFor();
            for(Node n:nodes)if(n.process.isAlive()){n.process.destroyForcibly();n.process.waitFor(10,TimeUnit.SECONDS);}
            saveEvidence();
        }
    }

    @Test void processKilledBeforeCommitLeavesNoRequest() throws Exception {
        parentWorker.close();fixture();Path base=Files.createTempDirectory("paspm-precommit-");
        ExecutorService caller=Executors.newSingleThreadExecutor();UUID key=UUID.randomUUID();
        try(var lock=java.sql.DriverManager.getConnection(db.url(),db.user(),db.password())) {
            lock.setAutoCommit(false);
            try(var statement=lock.createStatement();var ignored=statement.executeQuery("SELECT config_version FROM mining_config_head WHERE config_id=1 FOR UPDATE")) {
                Node a=start(base.resolve("a"));
                Future<Long> waiting=caller.submit(()->submit(a,key));
                waitFor(()->jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits",Integer.class)>0,20000,"administrative request blocked inside transaction");
                assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE request_key=?",Integer.class,key.toString()));
                a.process.destroyForcibly();assertTrue(a.process.waitFor(10,TimeUnit.SECONDS));lock.rollback();
                assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE request_key=?",Integer.class,key.toString()));waiting.cancel(true);
                evidence.add("pre_commit killed_A_pid="+a.process.pid()+" administrative_api_transaction_blocked=true request_rows_after_kill=0 PASS");
            }
        } finally {
            caller.shutdownNow();for(Node n:nodes)if(n.process.isAlive()){n.process.destroyForcibly();n.process.waitFor(10,TimeUnit.SECONDS);}
            saveEvidence();
        }
    }

    @Test void independentProcessesRenewRecoverAndDiscoverCommittedWork() throws Exception {
        assertEquals("17",System.getProperty("java.specification.version"));
        parentWorker.close();
        fixture();
        Path base=Files.createTempDirectory("paspm-multijvm-");
        ExecutorService caller=Executors.newSingleThreadExecutor();
        try {
            Node a=start(base.resolve("a"));
            Files.writeString(a.dir.resolve("hold-output"),"hold");
            long first=submit(a);
            waitFor(()->Files.exists(a.dir.resolve("output-held")),30000,"A real SPMF output barrier");
            Lease firstLease=lease(first);
            assertEquals("RUNNING",firstLease.status);assertNotNull(firstLease.owner);
            Thread.sleep(11500);
            Lease renewed=lease(first);assertEquals(firstLease.owner,renewed.owner);assertEquals(firstLease.fence,renewed.fence);assertTrue(renewed.until.after(firstLease.until),"heartbeat must advance expiry");
            Node b=start(base.resolve("b"));
            Lease afterStart=lease(first);assertEquals(firstLease.owner,afterStart.owner);assertEquals(firstLease.fence,afterStart.fence);assertEquals("RUNNING",afterStart.status);
            assertTrue(jdbc.queryForList("SELECT status FROM mining_run WHERE request_id=?",String.class,first).stream().noneMatch("FAILED"::equals));
            Files.delete(a.dir.resolve("hold-output"));waitTerminal(first,35000);assertSuccessful(first,8);
            evidence.add("healthy A_pid="+a.process.pid()+" B_pid="+b.process.pid()+" A_port="+a.port+" B_port="+b.port+" request="+first+" owner="+firstLease.owner+" fence="+firstLease.fence+" expiry_before="+firstLease.until+" expiry_after="+renewed.until+" patterns=8 PASS");

            Files.deleteIfExists(a.dir.resolve("output-held"));Files.writeString(a.dir.resolve("hold-output"),"hold");signal(b,"STOP");
            long revoked=submit(a);waitFor(()->Files.exists(a.dir.resolve("output-held")),30000,"renewal failure real output barrier");
            Lease revokedLease=lease(revoked);
            jdbc.update("UPDATE mining_request SET lease_owner='test-revoked-owner',lease_fence=lease_fence+1 WHERE request_id=?",revoked);
            waitFor(()->Files.readString(a.log).contains("java.lang.InterruptedException"),15000,"heartbeat interrupts old worker on renewal failure");
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM high_utility_pattern p JOIN mining_run r ON p.run_id=r.run_id WHERE r.request_id=?",Integer.class,revoked));
            signal(a,"STOP");jdbc.update("UPDATE mining_request SET lease_until=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP) WHERE request_id=?",revoked);signal(b,"CONT");
            waitTerminal(revoked,35000);assertSuccessful(revoked,8);signal(a,"CONT");Files.delete(a.dir.resolve("hold-output"));
            evidence.add("renewal_failure A_pid="+a.process.pid()+" B_pid="+b.process.pid()+" request="+revoked+" old_owner="+revokedLease.owner+" old_fence="+revokedLease.fence+" simulated_revoke=true worker_interrupted=true stale_patterns=0 recovered_patterns=8 PASS");

            signal(b,"STOP");Files.deleteIfExists(a.dir.resolve("output-held"));Files.writeString(a.dir.resolve("hold-output"),"hold");Files.writeString(a.dir.resolve("ignore-interrupt"),"test only");
            long stale=submit(a);waitFor(()->Files.exists(a.dir.resolve("output-held")),30000,"stale request real output barrier");
            Lease staleLease=lease(stale);signal(a,"STOP");signal(b,"CONT");waitTerminal(stale,65000);
            Lease replacement=lease(stale);assertTrue(replacement.fence>staleLease.fence);assertSuccessful(stale,8);
            Files.delete(a.dir.resolve("hold-output"));signal(a,"CONT");
            waitFor(()->Files.readString(a.log).contains("Mining lease is no longer active"),20000,"stale actual persistence rejection");
            assertEquals("COMPLETED",lease(stale).status);assertEquals(replacement.fence,lease(stale).fence);assertSuccessful(stale,8);
            Files.delete(a.dir.resolve("ignore-interrupt"));
            evidence.add("stale A_pid="+a.process.pid()+" B_pid="+b.process.pid()+" request="+stale+" owner="+staleLease.owner+" old_fence="+staleLease.fence+" new_fence="+replacement.fence+" actual_repository_rejection=Mining_lease_is_no_longer_active patterns=8 PASS");

            signal(b,"STOP");Files.deleteIfExists(a.dir.resolve("output-held"));Files.writeString(a.dir.resolve("hold-output"),"hold");
            long crash=submit(a);waitFor(()->Files.exists(a.dir.resolve("output-held")),30000,"crash request real output barrier");
            Lease beforeCrash=lease(crash);a.process.destroyForcibly();assertTrue(a.process.waitFor(10,TimeUnit.SECONDS));signal(b,"CONT");
            Thread.sleep(2000);Lease beforeExpiry=lease(crash);assertEquals(beforeCrash.owner,beforeExpiry.owner);assertEquals(beforeCrash.fence,beforeExpiry.fence);assertEquals("RUNNING",beforeExpiry.status);
            waitTerminal(crash,65000);Lease recovered=lease(crash);assertEquals("COMPLETED",recovered.status);assertTrue(recovered.fence>beforeCrash.fence);assertSuccessful(crash,8);
            evidence.add("crash killed_A_pid="+a.process.pid()+" B_pid="+b.process.pid()+" request="+crash+" old_owner="+beforeCrash.owner+" old_fence="+beforeCrash.fence+" old_expiry="+beforeCrash.until+" new_fence="+recovered.fence+" patterns=8 PASS (barrier after real SPMF exited; orphan-child test NOT RUN)");

            signal(b,"STOP");Node c=start(base.resolve("c"));Files.writeString(c.dir.resolve("hold-enqueue"),"hold");
            Future<Long> interrupted=caller.submit(()->submit(c));
            waitFor(()->Files.exists(c.dir.resolve("enqueue-held")),30000,"post-commit enqueue barrier");
            long committed=Long.parseLong(Files.readString(c.dir.resolve("enqueue-held")));Lease pending=lease(committed);assertEquals("PENDING",pending.status);
            c.process.destroyForcibly();assertTrue(c.process.waitFor(10,TimeUnit.SECONDS));signal(b,"CONT");
            waitTerminal(committed,35000);assertSuccessful(committed,8);
            evidence.add("post_commit_pre_enqueue killed_C_pid="+c.process.pid()+" B_pid="+b.process.pid()+" request="+committed+" initial_status=PENDING final_status=COMPLETED patterns=8 PASS");
            interrupted.cancel(true);

            signal(b,"STOP");Node d=start(base.resolve("d"));Files.writeString(d.dir.resolve("hold-output"),"hold");
            List<Long> saturated=new ArrayList<>();saturated.add(submit(d));waitFor(()->Files.exists(d.dir.resolve("output-held")),30000,"queue worker barrier");
            for(int i=0;i<10;i++)saturated.add(submit(d));
            for(int i=1;i<saturated.size();i++)assertEquals("PENDING",lease(saturated.get(i)).status);
            d.process.destroyForcibly();assertTrue(d.process.waitFor(10,TimeUnit.SECONDS));signal(b,"CONT");
            for(long id:saturated){waitTerminal(id,80000);assertSuccessful(id,8);}
            evidence.add("queue_saturation killed_D_pid="+d.process.pid()+" B_pid="+b.process.pid()+" request_ids="+saturated+" pending_before_kill=10 completed=11 patterns_per_request=8 PASS");
        } finally {
            caller.shutdownNow();
            for(Node n:nodes){if(n.process.isAlive()){try{signal(n,"CONT");}catch(Exception ignored){}n.process.destroyForcibly();n.process.waitFor(10,TimeUnit.SECONDS);}}
            saveEvidence();
        }
    }
    void fixture() throws Exception {
        jdbc.update("INSERT INTO store(name) VALUES ('Multi JVM synthetic')");store=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        var m=users.createPendingUser("Test Merchant",UUID.randomUUID()+"@example.test","MERCHANDISER",store,null,null);
        var s=users.createPendingUser("Test System",UUID.randomUUID()+"@example.test","SYSTEM_ADMIN",null,null,null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id IN (?,?)",m.id(),s.id());
        merchant=headers(auth.token(users.findById(m.id())).accessToken());admin=headers(auth.token(users.findById(s.id())).accessToken());
        var products=new ArrayList<Integer>();
        for(int i=1;i<=7;i++){jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,'Frozen','each',FALSE,100,0,'ACTIVE')",store,"MJ"+i);products.add(jdbc.queryForObject("SELECT MAX(product_id) FROM product",Integer.class));}
        for(String line:SpmfMiningEngineTest.INPUT.split("\n")){
            var parts=line.split(":");String key=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO sales_transaction(store_id,source_system,source_order_id,occurred_at,status,f03_eligible,total_amount,content_sha256) VALUES (?,'TEST',?,'2026-10-19 00:00:00','COMPLETED',TRUE,100,?)",store,key,"a".repeat(64));
            int tx=jdbc.queryForObject("SELECT transaction_id FROM sales_transaction WHERE store_id=? AND source_order_id=?",Integer.class,store,key);var ids=parts[0].split(" ");var util=parts[2].split(" ");
            for(int i=0;i<ids.length;i++)jdbc.update("INSERT INTO transaction_item(store_id,transaction_id,product_id,quantity,unit_price,unit_cost) VALUES (?,?,?,1,?,0)",store,tx,products.get(Integer.parseInt(ids[i])-1),Integer.parseInt(util[i]));
        }
        var snap=parentHttp.exchange("/api/utility/snapshots",HttpMethod.POST,new HttpEntity<>("{\"rangeStart\":\"2026-10-19\",\"rangeEnd\":\"2026-10-19\",\"asOfDate\":\"2026-10-19\"}",merchant),JsonNode.class);
        assertEquals(HttpStatus.CREATED,snap.getStatusCode());snapshot=snap.getBody().path("snapshotId").asInt();
        var policy=parentHttp.exchange("/api/system/mining/config",HttpMethod.PUT,new HttpEntity<>("{\"algorithm\":\"HUI_MINER\",\"minUtilMode\":\"ABSOLUTE\",\"minUtilValue\":30,\"expectedVersion\":0}",admin),JsonNode.class);assertEquals(HttpStatus.OK,policy.getStatusCode());
    }
    HttpHeaders headers(String token){var h=new HttpHeaders();h.setBearerAuth(token);h.setContentType(MediaType.APPLICATION_JSON);return h;}
    Node start(Path dir)throws Exception{
        Files.createDirectories(dir);int port;try(var socket=new ServerSocket(0)){port=socket.getLocalPort();}
        String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        var pb=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",cp,MiningMultiJvmBackend.class.getName(),"--server.port="+port,"--spring.main.allow-bean-definition-overriding=true","--paspm.bootstrap-email=","--paspm.bootstrap-password=","--paspm.avatar.directory=/tmp/paspm-test-avatars");
        var env=pb.environment();env.put("DB_URL",db.url());env.put("DB_USER",db.user());env.put("DB_PASSWORD",db.password());env.put("JWT_SECRET","01234567890123456789012345678901");env.put("OTP_SECRET","01234567890123456789012345678901");env.put("PASPM_TEST_BARRIER_DIR",dir.toString());
        Path log=dir.resolve("backend.log");pb.redirectErrorStream(true).redirectOutput(log.toFile());Process p=pb.start();Node n=new Node(p,port,dir,log);nodes.add(n);
        waitFor(()->{if(!p.isAlive())throw new IllegalStateException("Backend exited; see "+log);try{return http.getForEntity(url(n)+"/api/mining/requests",String.class).getStatusCode().value()==401;}catch(Exception e){return false;}},60000,"Backend "+p.pid()+" startup");return n;
    }
    String url(Node n){return "http://127.0.0.1:"+n.port;}
    long submit(Node n)throws Exception{return submit(n,UUID.randomUUID());}
    long submit(Node n,UUID key)throws Exception{
        String body=json.writeValueAsString(Map.of("requestKey",key,"storeId",store,"snapshotId",snapshot,"compareAlgorithms",false));
        var result=http.exchange(url(n)+"/api/system/mining/requests",HttpMethod.POST,new HttpEntity<>(body,admin),JsonNode.class);
        assertEquals(HttpStatus.ACCEPTED,result.getStatusCode());return result.getBody().path("requestId").asLong();
    }
    Lease lease(long id){return jdbc.queryForObject("SELECT lease_owner,lease_fence,lease_until,status FROM mining_request WHERE request_id=?",(r,n)->new Lease(r.getString(1),r.getLong(2),r.getTimestamp(3),r.getString(4)),id);}
    void waitTerminal(long id,int millis)throws Exception{waitFor(()->!List.of("PENDING","RUNNING").contains(lease(id).status),millis,"terminal request "+id);assertEquals("COMPLETED",lease(id).status);}
    void assertSuccessful(long id,int expected){assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mining_run WHERE request_id=? AND status='COMPLETED'",Integer.class,id));assertEquals(expected,jdbc.queryForObject("SELECT COUNT(*) FROM high_utility_pattern p JOIN mining_run r ON p.run_id=r.run_id WHERE r.request_id=?",Integer.class,id));}
    interface Condition {boolean check()throws Exception;}
    void waitFor(Condition condition,int millis,String what)throws Exception{long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(millis);while(System.nanoTime()<end){if(condition.check())return;Thread.sleep(20);}fail("Timed out: "+what);}
    void signal(Node n,String signal)throws Exception{assertEquals(0,new ProcessBuilder("kill","-"+signal,Long.toString(n.process.pid())).start().waitFor());}
    ProcessHandle awaitSpmfChild(Node n)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        var pattern=java.util.regex.Pattern.compile("SPMF_PROCESS_STARTED [^\\n]*pid=([0-9]+)");
        while(System.nanoTime()<end){
            var match=pattern.matcher(Files.readString(n.log));long pid=-1;while(match.find())pid=Long.parseLong(match.group(1));
            if(pid>0){var child=ProcessHandle.of(pid);if(child.isPresent()&&child.get().parent().map(p->p.pid()==n.process.pid()).orElse(false)&&child.get().info().arguments().map(args->Arrays.asList(args).contains("HUI-Miner")&&!Arrays.asList(args).contains("-c")).orElse(false))return child.get();}
            Thread.sleep(1);
        }
        throw new AssertionError("actual SPMF child not observed in process log and ancestry");
    }
    boolean executing(ProcessHandle p){if(!p.isAlive())return false;Path stat=Path.of("/proc",Long.toString(p.pid()),"stat");try{if(Files.exists(stat)&&Files.readString(stat).matches("(?s)^\\d+ \\(.*\\) Z .*"))return false;}catch(Exception ignored){}return true;}
    void saveEvidence()throws Exception{String destination=System.getenv("PASPM_MULTIJVM_EVIDENCE_FILE");if(destination!=null){Path p=Path.of(destination);Files.createDirectories(p.getParent());Files.write(p,evidence,java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);}}
}

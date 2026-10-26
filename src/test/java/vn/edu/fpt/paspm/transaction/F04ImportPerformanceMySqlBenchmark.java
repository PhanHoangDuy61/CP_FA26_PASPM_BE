package vn.edu.fpt.paspm.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;
import vn.edu.fpt.paspm.transaction.importing.TransactionImportService;
import vn.edu.fpt.paspm.user.UserRepository;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in, synthetic-only benchmark; never uses the application datasource settings. */
@EnabledIfSystemProperty(named="paspm.test.performance", matches="true")
@SpringBootTest(properties={"paspm.jwt-secret=01234567890123456789012345678901",
    "paspm.otp-secret=test-only-otp-key-with-at-least-thirty-two-bytes",
    "paspm.bootstrap-email=", "paspm.bootstrap-password=", "spring.flyway.enabled=true"})
@Import(F04ImportPerformanceMySqlBenchmark.ProbeConfig.class)
class F04ImportPerformanceMySqlBenchmark {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) { MySqlTestDatabase.create().register(r); }
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired TransactionImportService imports;
    @Autowired ObjectMapper json;
    @Autowired SqlProbe probe;
    @Test void measureImportAndQueryPlans() throws Exception {
        jdbc.update("INSERT INTO store(name) VALUES ('Synthetic import benchmark')");
        int store=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,'BENCH','Synthetic product','each',FALSE,10,6,'ACTIVE')",store);
        var user=users.createPendingUser("Benchmark",UUID.randomUUID()+"@example.test","STORE_ADMIN",store,null,null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",user.id());
        Jwt jwt=Jwt.withTokenValue("synthetic-test-only").header("alg","none").subject(String.valueOf(user.id())).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        imports.importCsv(jwt,file("warmup",100));
        List<Map<String,Object>> samples=new ArrayList<>();
        for(int index=0;index<4;index++) {
            int rows=new int[]{100,1000,10000,10000}[index];
            int padding=index==3?450:0;
            var file=file((padding==0?"rows":"padded")+rows,rows,padding);
            long heapStart=usedHeap(); AtomicLong peak=new AtomicLong(heapStart);
            var monitor=Executors.newSingleThreadScheduledExecutor();
            monitor.scheduleAtFixedRate(()->peak.accumulateAndGet(usedHeap(),Math::max),0,10,TimeUnit.MILLISECONDS);
            long cpuStart=ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
            probe.start();long start=System.nanoTime();
            var result=imports.importCsv(jwt,file);
            long elapsed=System.nanoTime()-start;
            var sql=probe.stop();
            long cpu=ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime()-cpuStart;
            monitor.shutdownNow();
            assertEquals(rows,result.acceptedOrderCount());assertEquals(0,result.rejectedCount());
            Map<String,Object> sample=new LinkedHashMap<>();
            sample.put("rows",rows);sample.put("paddingPerIdentifier",padding);sample.put("uploadBytes",file.getSize());sample.put("elapsedMs",elapsed/1_000_000.0);
            sample.put("rowsPerSecond",rows*1_000_000_000.0/elapsed);sample.put("requestThreadCpuMs",cpu/1_000_000.0);
            sample.put("sampledHeapGrowthBytes",Math.max(0,peak.get()-heapStart));sample.putAll(sql);samples.add(sample);
        }
        Map<String,Object> report=new LinkedHashMap<>();
        report.put("scope","Synthetic bounded service invocation, single client, warmup100; heap sampled10ms, fixture bytes allocated before measurement; no network/RBAC timing; SELECT execute counts only, not server-wide statement counters");
        report.put("java",System.getProperty("java.version"));report.put("mysql",jdbc.queryForObject("SELECT VERSION()",String.class));
        report.put("samples",samples);
        report.put("idempotencyPlan",jdbc.queryForList("EXPLAIN ANALYZE SELECT transaction_id,content_sha256 FROM sales_transaction WHERE store_id=? AND source_system='CSV_UPLOAD' AND source_order_id=?",store,"rows10000-9999"));
        report.put("productLookupPlan",jdbc.queryForList("EXPLAIN ANALYZE SELECT sku,product_id FROM product WHERE store_id=? AND sku IN ('BENCH')",store));
        report.put("inventoryRows",jdbc.queryForObject("SELECT COUNT(*) FROM inventory_batch WHERE product_id IN (SELECT product_id FROM product WHERE store_id=?)",Integer.class,store));
        Path output=Path.of(System.getProperty("paspm.test.performance.output","target/f04-import-performance.json"));
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output,json.writerWithDefaultPrettyPrinter().writeValueAsString(report)+"\n");
        System.out.println("F04_PERFORMANCE samples="+samples.size()+" outputWritten=true");
    }
    static long usedHeap(){var rt=Runtime.getRuntime();return rt.totalMemory()-rt.freeMemory();}
    static MockMultipartFile file(String prefix,int rows) { return file(prefix,rows,0); }
    static MockMultipartFile file(String prefix,int rows,int padding) {
        StringBuilder csv=new StringBuilder("source_order_id,occurred_at,sku,quantity,unit_price,unit_cost,status\n");
        for(int i=0;i<rows;i++)csv.append(prefix).append('-').append(i).append(" ".repeat(padding)).append(",2026-10-19T12:00:00+07:00,BENCH,1,10,6,COMPLETED\n");
        return new MockMultipartFile("file","synthetic.csv","text/csv",csv.toString().getBytes(StandardCharsets.UTF_8));
    }
    @TestConfiguration static class ProbeConfig {@Bean static SqlProbe sqlProbe(){return new SqlProbe();}}
    static class SqlProbe implements BeanPostProcessor {
        private boolean enabled;private final Map<String,Integer> queries=new TreeMap<>();private final List<Long> transactions=new ArrayList<>();
        synchronized void start(){queries.clear();transactions.clear();enabled=true;}
        synchronized Map<String,Object> stop(){enabled=false;long sum=transactions.stream().mapToLong(Long::longValue).sum();return Map.of("selectExecutions",Map.copyOf(queries),"committedOrderTransactions",transactions.size(),"averageOrderTransactionMs",transactions.isEmpty()?0:sum/1_000_000.0/transactions.size());}
        synchronized void query(String sql){if(enabled&&sql.stripLeading().toLowerCase(Locale.ROOT).startsWith("select")){var m=java.util.regex.Pattern.compile("(?i)\\bFROM\\s+([a-z_]+)").matcher(sql);queries.merge(m.find()?m.group(1):"other",1,Integer::sum);}}
        synchronized void commit(long start){if(enabled&&start>0)transactions.add(System.nanoTime()-start);}
        @Override public Object postProcessAfterInitialization(Object bean,String name){if(!(bean instanceof DataSource ds))return bean;return new DelegatingDataSource(ds){@Override public Connection getConnection()throws SQLException{return wrap(ds.getConnection());}@Override public Connection getConnection(String u,String p)throws SQLException{return wrap(ds.getConnection(u,p));}};}
        Connection wrap(Connection c){long[] started={0};return(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
            if(m.getName().equals("setAutoCommit")&&Boolean.FALSE.equals(a[0]))started[0]=System.nanoTime();
            Object r=invoke(m,c,a);if(m.getName().equals("commit")){commit(started[0]);started[0]=0;}
            if(r instanceof PreparedStatement ps&&a!=null&&a.length>0&&a[0]instanceof String sql)return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(p2,m2,a2)->{if(m2.getName().startsWith("execute"))query(sql);return invoke(m2,ps,a2);});return r;});}
        static Object invoke(Method m,Object t,Object[] a)throws Throwable{try{return m.invoke(t,a);}catch(InvocationTargetException e){throw e.getCause();}}
    }
}

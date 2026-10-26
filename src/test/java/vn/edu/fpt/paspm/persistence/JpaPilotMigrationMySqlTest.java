package vn.edu.fpt.paspm.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.nio.file.*;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.fpt.paspm.deployment.legacy.*;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.fpt.paspm.PaspmApplication;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.catalog.category.CategoryRepository;
import vn.edu.fpt.paspm.catalog.product.ProductRepository;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;
import vn.edu.fpt.paspm.user.UserRepository;

/** Migration acceptance checks on new disposable schemas, never the configured source. */
@EnabledIfSystemProperty(named="paspm.test.mysql", matches="true")
class JpaPilotMigrationMySqlTest {
    @TempDir Path backupDirectory;
    private JdbcTemplate jdbc(MySqlTestDatabase db) {
        return new JdbcTemplate(new DriverManagerDataSource(db.url(), db.user(), db.password()));
    }

    private ConfigurableApplicationContext application(MySqlTestDatabase db) {
        return new SpringApplicationBuilder(PaspmApplication.class).run(
            "--server.port=0", "--server.address=127.0.0.1", "--spring.profiles.active=jpa-migration-verification",
            "--spring.datasource.url="+db.url(), "--spring.datasource.username="+db.user(),
            "--spring.datasource.password="+db.password(), "--spring.flyway.enabled=true",
            "--paspm.bootstrap-email=", "--paspm.bootstrap-password=",
            "--paspm.jwt-secret=migration-test-only-signing-key-at-least-thirty-two-bytes",
            "--paspm.otp-secret=migration-test-only-otp-key-at-least-thirty-two-bytes",
            "--paspm.avatar.directory="+System.getProperty("java.io.tmpdir")+"/paspm-jpa-migration-avatars",
            "--logging.level.root=ERROR");
    }

    @Test void freshV1ToV12ValidatesJpaConstraintsAndActualCategoryProductHttpApis() {
        var db=MySqlTestDatabase.create();
        var flyway=Flyway.configure().dataSource(db.url(),db.user(),db.password()).load();
        assertEquals(12,flyway.migrate().migrationsExecuted);flyway.validate();
        var jdbc=jdbc(db);
        assertEquals("12",flyway.info().current().getVersion().toString());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='product' AND index_name='uq_product_store_sku' AND column_name='sku' AND non_unique=0",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema=DATABASE() AND constraint_name='fk_product_category_store'",Integer.class));
        try(var context=application(db)) {
            jdbc.update("INSERT INTO store(name) VALUES ('Synthetic migration HTTP store')");
            int store=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
            var users=context.getBean(UserRepository.class);
            var user=users.createPendingUser("Synthetic migration admin","migration-http@example.test","STORE_ADMIN",store,null,null);
            jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",user.id());
            var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(context.getBean(AuthService.class).token(users.findById(user.id())).accessToken());
            String base="http://127.0.0.1:"+context.getEnvironment().getProperty("local.server.port");
            var http=new TestRestTemplate();
            var category=http.exchange(base+"/api/categories",HttpMethod.POST,new HttpEntity<>(Map.of("name","Synthetic dairy","status","ACTIVE"),headers),JsonNode.class);
            assertEquals(HttpStatus.CREATED,category.getStatusCode());int categoryId=category.getBody().path("id").asInt();assertTrue(categoryId>0);
            var payload=Map.of("sku","MIGRATION-MILK","name","Synthetic milk","categoryId",categoryId,"unit","bottle","requiresExpiry",true,"sellingPrice",30,"costPrice",20,"status","ACTIVE");
            var created=http.exchange(base+"/api/products",HttpMethod.POST,new HttpEntity<>(payload,headers),JsonNode.class);
            assertEquals(HttpStatus.CREATED,created.getStatusCode());int product=created.getBody().path("id").asInt();
            assertEquals(HttpStatus.OK,http.exchange(base+"/api/categories/"+categoryId,HttpMethod.GET,new HttpEntity<>(headers),JsonNode.class).getStatusCode());
            var read=http.exchange(base+"/api/products/"+product,HttpMethod.GET,new HttpEntity<>(headers),JsonNode.class);
            assertEquals(HttpStatus.OK,read.getStatusCode());assertEquals("MIGRATION-MILK",read.getBody().path("sku").asText());
            assertEquals(categoryId,jdbc.queryForObject("SELECT category_id FROM product WHERE product_id=?",Integer.class,product));
            assertEquals(HttpStatus.UNAUTHORIZED,http.getForEntity(base+"/api/products/"+product,String.class).getStatusCode());
            assertCheckViolation(()->jdbc.update("UPDATE product SET selling_price=-1 WHERE product_id=?",product));
            assertCheckViolation(()->jdbc.update("UPDATE category SET status='INVALID' WHERE category_id=?",categoryId));
        }
        System.out.println("JPA_MIGRATION_FRESH migrations=1,2,3,4,5,6,7,8,9,10,11,12 schemaValidation=PASS HTTP=PASS constraints=PASS");
    }

    @Test void v5CatalogAndEmptySnapshotUpgradePreservesRowsAndJpaJdbcTransactions() throws Exception {
        var db=MySqlTestDatabase.create();var jdbc=jdbc(db);
        assertEquals(5,Flyway.configure().dataSource(db.url(),db.user(),db.password()).target("5").load().migrate().migrationsExecuted);
        seedV5(jdbc,false);var before=fingerprints(jdbc);
        var current=Flyway.configure().dataSource(db.url(),db.user(),db.password()).load();
        assertEquals(7,current.migrate().migrationsExecuted);current.validate();
        assertEquals("12",current.info().current().getVersion().toString());
        assertEquals(before,fingerprints(jdbc));
        assertEquals(0,jdbc.queryForObject("SELECT expiry_push_days FROM product WHERE product_id=201",Integer.class));
        assertNull(jdbc.queryForObject("SELECT validation_warnings FROM utility_snapshot WHERE snapshot_id=401",String.class));
        for(String table:new String[]{"sales_transaction","transaction_item","mining_request","recommendation"})
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("UPDATE product SET category_id=102 WHERE product_id=201"));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO product(store_id,sku,name,unit,selling_price,cost_price,status) VALUES (1,'V5-MILK','Duplicate','bottle',30,20,'ACTIVE')"));
        assertCheckViolation(()->jdbc.update("UPDATE inventory_batch SET remaining_quantity=99 WHERE batch_id=301"));
        try(var context=application(db)) {
            var products=context.getBean(ProductRepository.class);var categories=context.getBean(CategoryRepository.class);
            var sharedJdbc=context.getBean(JdbcTemplate.class);
            assertEquals("V5-MILK",products.find(1,201).sku());assertEquals("Synthetic dairy",categories.find(1,101).name());
            new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status->{
                assertTrue(products.update(1,201,"V5-MILK","Changed only inside rollback",101,"bottle",true,new BigDecimal("35"),new BigDecimal("20"),"ACTIVE",3));
                assertEquals("Changed only inside rollback",products.find(1,201).name());
                assertEquals(0,new BigDecimal("0.8").compareTo(sharedJdbc.queryForObject("SELECT stock_priority_score FROM product WHERE product_id=201",BigDecimal.class)));
                assertEquals(7,sharedJdbc.queryForObject("SELECT remaining_quantity FROM inventory_batch WHERE batch_id=301",Integer.class));
                assertTrue(products.create(1,"ROLLBACK-ONLY","Synthetic transient",101,"bottle",false,BigDecimal.TEN,BigDecimal.ONE,"ACTIVE",0).id()>0);
                sharedJdbc.update("UPDATE category SET name='Rolled back JDBC category' WHERE category_id=101");
                status.setRollbackOnly();
            });
        }
        assertEquals(before,fingerprints(jdbc));
        System.out.println("JPA_MIGRATION_V5_CATALOG before="+before+" after="+fingerprints(jdbc)+" preserved=PASS migrations=6,7,8,9,10,11,12 mixedRollback=PASS");
    }

    @Test void v5HistoricalMatrixRequiresReconciliationBeforeV7AndOriginalRowsStayIntact() throws Exception {
        var db=MySqlTestDatabase.create();var jdbc=jdbc(db);
        Flyway.configure().dataSource(db.url(),db.user(),db.password()).target("5").load().migrate();
        seedV5(jdbc,true);var before=fingerprints(jdbc);
        System.out.println("JPA_MIGRATION_V5_MATRIX before="+before+" fixture=VALID_V5_SYNTHETIC transactionTableAbsent=true");
        // Characterize the discovered migration blocker, not successful upgrade support.
        // V5 accepts prepared transaction IDs before SALES_TRANSACTION exists; V7 cannot invent their source rows.
        var error=assertThrows(FlywayException.class,()->Flyway.configure().dataSource(db.url(),db.user(),db.password()).load().migrate());
        Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();
        assertInstanceOf(SQLException.class,cause);assertEquals(1452,((SQLException)cause).getErrorCode());
        assertTrue(error.getMessage().contains("fk_utility_matrix_transaction"));
        assertEquals(before,fingerprints(jdbc),"Original V5 records must remain intact even when migration fails");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='7' AND success=FALSE",Integer.class));
        System.out.println("JPA_MIGRATION_V5_MATRIX compatibility=FAIL sqlState=23000 errorCode=1452 originalRowsPreserved=true after="+fingerprints(jdbc));
    }

    @Test void supportedPopulatedRecoveryThenBootJpaWritesPreserveFrozenHistory() throws Exception {
        var target=MySqlTestDatabase.create();var jdbc=jdbc(target);
        Flyway.configure().dataSource(target.url(),target.user(),target.password()).target("5").load().migrate();
        seedV5(jdbc,true);
        LegacyDataset frozen;
        try(var connection=java.sql.DriverManager.getConnection(target.url(),target.user(),target.password())) {
            frozen=LegacyDataset.inspect(connection,"utility_matrix_item");
        }
        var empty=MySqlTestDatabase.create();
        String schema=URI.create(empty.url().substring(5)).getPath().substring(1);
        String user="bc_restore_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        String password=UUID.randomUUID().toString().replace("-","");
        String helper=System.getenv("PASPM_TEST_RESTORE_PROVISIONER");
        assertNotNull(helper,"Explicit disposable restore helper required");
        Process provision=new ProcessBuilder(helper).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            try(var input=provision.getOutputStream()) {
                input.write(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(
                        Map.of("schema",schema,"username",user,"password",password)));
            }
            assertTrue(provision.waitFor(30,TimeUnit.SECONDS));assertEquals(0,provision.exitValue());
        } finally { if(provision.isAlive())provision.destroyForcibly(); }
        var restored=new MySqlTestDatabase(empty.url(),user,password);
        Path dump=backupDirectory.resolve("synthetic-v5.sql"),manifest=backupDirectory.resolve("verified.properties");
        URI uri=URI.create(target.url().substring(5));
        ProcessBuilder backup=new ProcessBuilder("mysqldump","--host=127.0.0.1","--port="+uri.getPort(),
                "--user="+target.user(),"--single-transaction","--set-gtid-purged=OFF","--no-tablespaces",uri.getPath().substring(1))
                .redirectOutput(dump.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD);
        backup.environment().put("MYSQL_PWD",target.password());
        Process dumping=backup.start();
        try {assertTrue(dumping.waitFor(30,TimeUnit.SECONDS));assertEquals(0,dumping.exitValue());}
        finally {if(dumping.isAlive())dumping.destroyForcibly();}
        try(var original=java.sql.DriverManager.getConnection(target.url(),target.user(),target.password());
                var verification=java.sql.DriverManager.getConnection(restored.url(),user,password)) {
            LegacyBackupVerifier.generate(original,verification,dump,"task-approved-synthetic-fixture",manifest,
                    new LegacyBackupVerifier.RestoreCredentials(user,password));
        }
        var guarded=Flyway.configure().dataSource(target.url(),target.user(),target.password())
                .callbacks(new LegacyRecoveryCallback(true,manifest.toString(),restored.url(),user,password)).load();
        assertTrue(guarded.migrate().success);guarded.validate();assertEquals("12",guarded.info().current().getVersion().toString());
        try(var context=application(target)) {
            var products=context.getBean(ProductRepository.class);
            new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
                assertTrue(products.update(1,201,"V5-MILK","Metadata after recovery",101,"bottle",true,
                        new BigDecimal("35"),new BigDecimal("20"),"ACTIVE",3));
                assertTrue(products.updateStatus(1,201,"INACTIVE"));
                assertEquals("Metadata after recovery",products.find(1,201).name());
                assertNull(products.find(2,201));
            });
        }
        try(var connection=java.sql.DriverManager.getConnection(target.url(),target.user(),target.password())) {
            assertEquals(frozen,LegacyDataset.inspect(connection,"utility_matrix_item_legacy_v5"));
        }
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sales_transaction",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM utility_matrix_item",Integer.class));
        assertEquals(7,jdbc.queryForObject("SELECT remaining_quantity FROM inventory_batch WHERE batch_id=301",Integer.class));
        assertEquals(0,new BigDecimal("0.8").compareTo(jdbc.queryForObject("SELECT stock_priority_score FROM product WHERE product_id=201",BigDecimal.class)));
        assertEquals(0,new BigDecimal("0.9").compareTo(jdbc.queryForObject("SELECT seasonal_priority_score FROM product WHERE product_id=201",BigDecimal.class)));
        System.out.println("JPA_SUPPORTED_RECOVERY_BOOT frozen="+frozen+" archivePreserved=true fakeSales=0 metadataWrite=true");
    }

    private void assertCheckViolation(Runnable action) {
        var error=assertThrows(DataAccessException.class,action::run);
        assertInstanceOf(SQLException.class,error.getMostSpecificCause());
        assertEquals(3819,((SQLException)error.getMostSpecificCause()).getErrorCode());
    }

    private void seedV5(JdbcTemplate jdbc,boolean matrix) {
        jdbc.update("INSERT INTO store(store_id,name,utility_config) VALUES (1,'Synthetic store A','{\"expiryWindowDays\":30}'),(2,'Synthetic store B',NULL)");
        jdbc.update("INSERT INTO app_user(user_id,store_id,role_id,email,name,status) VALUES (11,1,(SELECT role_id FROM role WHERE code='MERCHANDISER'),'v5-synthetic@example.test','Synthetic planner','ACTIVE')");
        jdbc.update("INSERT INTO category(category_id,store_id,name,status) VALUES (101,1,'Synthetic dairy','ACTIVE'),(102,2,'Synthetic drinks','ACTIVE')");
        jdbc.update("INSERT INTO product(product_id,store_id,category_id,sku,name,unit,requires_expiry,selling_price,cost_price,status,stock_priority_score,seasonal_priority_score) VALUES (201,1,101,'V5-MILK','Synthetic milk','bottle',TRUE,30,20,'ACTIVE',.8,.9),(202,2,102,'V5-TEA','Synthetic tea','box',FALSE,25,15,'ACTIVE',.2,.4)");
        jdbc.update("INSERT INTO inventory_batch(batch_id,product_id,batch_code,received_at,expiry_date,initial_quantity,remaining_quantity,status) VALUES (301,201,'V5-DAIRY','2026-10-01 00:00:00','2026-10-30',10,7,'ACTIVE'),(302,202,'V5-TEA','2026-10-01 00:00:00',NULL,8,5,'BLOCKED')");
        jdbc.update("INSERT INTO utility_snapshot(snapshot_id,store_id,created_by,range_start,range_end,as_of_date,config_snapshot,source_hash,matrix_hash,total_weighted_utility,rounding_scale,status) VALUES (401,1,11,'2026-10-01','2026-10-02','2026-10-02','{\"scoringVersion\":\"F03_LINEAR_V1\"}',?,?,?,6,'READY')","a".repeat(64),"b".repeat(64),matrix?10:0);
        if(matrix)jdbc.update("INSERT INTO utility_matrix_item(matrix_item_id,snapshot_id,transaction_id,product_id,quantity,base_utility,stock_score,expiry_score,seasonal_score,expiry_status,weight_value,weighted_utility) VALUES (501,401,9101,201,1,10,.8,0,.9,'AVAILABLE',1,10)");
    }

    private Map<String,String> fingerprints(JdbcTemplate jdbc) throws Exception {
        var queries=new LinkedHashMap<String,String>();
        queries.put("store","SELECT store_id,name,utility_config,mining_schedule FROM store ORDER BY store_id");
        queries.put("category","SELECT category_id,store_id,name,status FROM category ORDER BY category_id");
        queries.put("product","SELECT product_id,store_id,category_id,sku,name,unit,requires_expiry,selling_price,cost_price,status,stock_priority_score,seasonal_priority_score FROM product ORDER BY product_id");
        queries.put("inventory_batch","SELECT * FROM inventory_batch ORDER BY batch_id");
        queries.put("utility_snapshot","SELECT snapshot_id,store_id,created_by,range_start,range_end,as_of_date,config_snapshot,source_hash,matrix_hash,total_weighted_utility,rounding_scale,status,created_at FROM utility_snapshot ORDER BY snapshot_id");
        queries.put("utility_matrix_item","SELECT matrix_item_id,snapshot_id,transaction_id,product_id,quantity,base_utility,stock_score,expiry_score,seasonal_score,expiry_status,weight_value,weighted_utility FROM utility_matrix_item ORDER BY matrix_item_id");
        var result=new LinkedHashMap<String,String>();
        for(var entry:queries.entrySet()) {
            String rows=jdbc.queryForList(entry.getValue()).toString();
            result.put(entry.getKey(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rows.getBytes(StandardCharsets.UTF_8))));
        }
        return result;
    }
}

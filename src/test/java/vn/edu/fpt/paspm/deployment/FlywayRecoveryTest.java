package vn.edu.fpt.paspm.deployment;

import java.nio.file.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class FlywayRecoveryTest {
    @TempDir Path oldMigrations;
    private String corrected() { return System.getProperty("paspm.test.migrations","classpath:db/migration"); }
    private Flyway flyway(String url,String user,String password,String location,String target) {
        return Flyway.configure().dataSource(url,user,password).locations(location).target(target).load();
    }
    @Test void oldAppliedChecksumRequiresExplicitRepairAndPreservesData() throws Exception {
        try(var files=Files.list(Path.of("src/main/resources/db/migration"))) {
            for(Path source:files.filter(p->p.toString().endsWith(".sql")).toList())
                Files.copy(source,oldMigrations.resolve(source.getFileName()));
        }
        Files.copy(Path.of("src/test/resources/db/historical/V7__f04_transaction_import_pipeline.sql"),
            oldMigrations.resolve("V7__f04_transaction_import_pipeline.sql"),StandardCopyOption.REPLACE_EXISTING);
        String url="jdbc:h2:mem:repair_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        flyway(url,"sa","","filesystem:"+oldMigrations,"7").migrate();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url,"sa",""));
        int batch=seed(jdbc);
        int before=jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='7'",Integer.class);
        var corrected=flyway(url,"sa","",corrected(),"7");
        assertFalse(corrected.validateWithResult().validationSuccessful,"Historical checksum change must not validate silently");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM import_staging_row WHERE import_batch_id=?",Integer.class,batch));
        // Explicit controlled repair ONLY of this known in-memory test database.
        corrected.repair();
        corrected.validate();
        int after=jdbc.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='7'",Integer.class);
        assertNotEquals(before,after);
        assertEquals(4,flyway(url,"sa","",corrected(),"latest").migrate().migrationsExecuted);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM import_staging_row WHERE import_batch_id=?",Integer.class,batch));
        assertEquals(2,jdbc.queryForObject("SELECT `row_number` FROM import_staging_row WHERE import_batch_id=?",Integer.class,batch));
        System.out.println("P0_CHECKSUM original="+before+" corrected="+after+" dataPreserved=true");
    }
    @Test @EnabledIfSystemProperty(named="paspm.test.mysql",matches="true")
    void existingMySqlV7UpgradesWithoutReplayingOrDeletingData() {
        var db=MySqlTestDatabase.create();
        var at7=flyway(db.url(),db.user(),db.password(),corrected(),"7");
        assertEquals(7,at7.migrate().migrationsExecuted);
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(db.url(),db.user(),db.password()));
        int batch=seed(jdbc);
        var latest=flyway(db.url(),db.user(),db.password(),corrected(),"latest");
        assertEquals(4,latest.migrate().migrationsExecuted);latest.validate();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM import_staging_row WHERE import_batch_id=?",Integer.class,batch));
        assertEquals(11,jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=TRUE",Integer.class));
        System.out.println("P0_MYSQL_UPGRADE preservedBatch="+batch+" migrationsExecuted=4 validation=PASS");
    }
    private int seed(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO store(name) VALUES ('P0 recovery fixture')");
        int store=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        jdbc.update("INSERT INTO import_batch(store_id,method,filename,status) VALUES (?,'FILE','fixture.csv','COMPLETED')",store);
        int batch=jdbc.queryForObject("SELECT MAX(import_batch_id) FROM import_batch",Integer.class);
        jdbc.update("INSERT INTO import_staging_row(store_id,import_batch_id,`row_number`,raw_data,status) VALUES (?,?,2,'{}','REJECTED')",store,batch);
        return batch;
    }
}

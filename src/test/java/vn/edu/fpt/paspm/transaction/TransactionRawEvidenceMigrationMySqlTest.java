package vn.edu.fpt.paspm.transaction;

import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="paspm.test.mysql",matches="true")
class TransactionRawEvidenceMigrationMySqlTest {
    @Test void wideningRawEvidenceAfterV11PreservesExistingBytesAndMigrationChecksums() {
        var db=MySqlTestDatabase.create();
        var before=Flyway.configure().dataSource(db.url(),db.user(),db.password()).target("11").load();
        assertEquals(11,before.migrate().migrationsExecuted);before.validate();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(db.url(),db.user(),db.password()));
        jdbc.update("INSERT INTO store(name) VALUES ('Synthetic raw preservation')");
        int store=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        jdbc.update("INSERT INTO import_batch(store_id,method,filename,status) VALUES (?,'FILE','synthetic.csv','COMPLETED_WITH_ERRORS')",store);
        int batch=jdbc.queryForObject("SELECT MAX(import_batch_id) FROM import_batch",Integer.class);
        String raw="{\"source_order_id\":\"Giữ nguyên \\n \\\" unicode\",\"quantity\":\"1\"}";
        jdbc.update("INSERT INTO import_staging_row(store_id,import_batch_id,`row_number`,source_order_id,raw_data,status) VALUES (?,?,2,NULL,?,'REJECTED')",store,batch,raw);
        var checksumBefore=jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE success=TRUE ORDER BY installed_rank");
        var latest=Flyway.configure().dataSource(db.url(),db.user(),db.password()).load();
        assertEquals(1,latest.migrate().migrationsExecuted);latest.validate();
        assertEquals("12",latest.info().current().getVersion().toString());
        assertEquals(raw,jdbc.queryForObject("SELECT raw_data FROM import_staging_row WHERE import_batch_id=?",String.class,batch));
        assertEquals(checksumBefore,jdbc.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE success=TRUE AND version <> '12' ORDER BY installed_rank"));
        assertEquals("longtext",jdbc.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='import_staging_row' AND column_name='raw_data'",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sales_transaction",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_batch",Integer.class));
    }
}

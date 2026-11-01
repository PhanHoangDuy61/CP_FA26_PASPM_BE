package vn.edu.fpt.paspm.utility;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ExpiryFefoOrderingTest {
    @Test void sameExpiryUsesOldestReceiptThenBatchIdWithinTheStore() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE product(product_id INT PRIMARY KEY,store_id INT NOT NULL)");
        jdbc.execute("CREATE TABLE inventory_batch(batch_id INT PRIMARY KEY,product_id INT NOT NULL,received_at TIMESTAMP NOT NULL,expiry_date DATE,remaining_quantity INT,status VARCHAR(20))");
        jdbc.update("INSERT INTO product VALUES(1,1),(2,2)");
        jdbc.update("INSERT INTO inventory_batch VALUES (1,1,'2026-01-03 10:00:00','2026-02-01',2,'ACTIVE'),(3,1,'2026-01-01 10:00:00','2026-02-01',4,'ACTIVE'),(2,1,'2026-01-01 10:00:00','2026-02-01',3,'ACTIVE'),(4,2,'2025-12-01 10:00:00','2026-02-01',5,'ACTIVE')");
        var repo = new UtilityProductRepository(jdbc);
        var result = new ExpiryScoreCalculator().calculate(1,true,repo.batches(1,1),LocalDate.of(2026,1,25),7);
        assertEquals(List.of(2,3,1),result.evidence().batches().stream().map(ExpiryScoreCalculator.PushBatch::batchId).toList());
        assertTrue(repo.batches(2,1).isEmpty());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
            "INSERT INTO inventory_batch VALUES (5,1,NULL,'2026-02-01',1,'ACTIVE')"));
    }
}

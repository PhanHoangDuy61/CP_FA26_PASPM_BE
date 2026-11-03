package vn.edu.fpt.paspm.catalog.product.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;

@EnabledIfSystemProperty(named="paspm.test.mysql", matches="true")
class ProductPersistenceMySqlIntegrationTest extends ProductPersistenceIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        MySqlTestDatabase.create().register(registry);
    }

    @Test void repeatableReadJdbcSnapshotSurvivesIndependentNativeCommit() {
        int id = products.create(store,"RR-SNAPSHOT","Original name",null,"unit",false,
                BigDecimal.TEN,BigDecimal.ONE,"ACTIVE",0).id();
        var repeatableRead = new TransactionTemplate(manager);
        repeatableRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        repeatableRead.executeWithoutResult(ignored -> {
            assertEquals("Original name",jdbc.queryForObject(
                    "SELECT name FROM product WHERE store_id=? AND product_id=?",String.class,store,id));
            var outerConnection = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
            CompletableFuture.runAsync(() -> {
                try (var connection = dataSource.getConnection();var statement = connection.prepareStatement(
                        "UPDATE product SET name='Independent committed name' WHERE store_id=? AND product_id=?")) {
                    assertNotSame(outerConnection,connection);
                    connection.setAutoCommit(false);
                    statement.setInt(1,store); statement.setInt(2,id);
                    assertEquals(1,statement.executeUpdate());
                    connection.commit();
                } catch (java.sql.SQLException exception) {
                    throw new CompletionException(exception);
                }
            }).orTimeout(5,TimeUnit.SECONDS).join();
            assertEquals("Original name",jdbc.queryForObject(
                    "SELECT name FROM product WHERE store_id=? AND product_id=?",String.class,store,id));
        });
        assertEquals("Independent committed name",jdbc.queryForObject(
                "SELECT name FROM product WHERE store_id=? AND product_id=?",String.class,store,id));
    }
}

package vn.edu.fpt.paspm.transaction.importing;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class TransactionOrderIdentifierTest {
    TransactionOrderValidator validator;
    @BeforeEach void fixture() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:id"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE product(product_id INT PRIMARY KEY,store_id INT,sku VARCHAR(80))");
        jdbc.update("INSERT INTO product VALUES (1,10,'MILK')");
        validator = new TransactionOrderValidator(jdbc);
    }
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings={" ", "  ", "\t", "\r\n"})
    void nullEmptyAndTrimmedBlankIdentifiersAreRejectedWithRequiredFieldError(String identifier) {
        Map<String,String> fields = new LinkedHashMap<>(Map.of("occurred_at","2026-10-19T12:00:00+07:00","sku","MILK","quantity","1","unit_price","10","unit_cost","6","status","COMPLETED"));
        fields.put("source_order_id", identifier);
        var orders = validator.validateAndGroup(10, List.of(new TransactionCsvParser.ParsedCsvRow(2, fields)));
        assertEquals(1, orders.size());
        assertFalse(orders.get(0).valid());
        assertTrue(orders.get(0).errors().stream().anyMatch(error -> error.field().equals("source_order_id") && error.message().equals("Order ID is required")));
    }
}

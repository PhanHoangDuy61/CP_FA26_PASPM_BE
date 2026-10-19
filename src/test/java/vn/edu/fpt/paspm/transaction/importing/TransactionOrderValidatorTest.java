package vn.edu.fpt.paspm.transaction.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TransactionOrderValidatorTest {
    private TransactionOrderValidator validator;

    @BeforeEach
    void setUp() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:validator" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE product(product_id INT PRIMARY KEY, store_id INT NOT NULL, sku VARCHAR(80) NOT NULL)");
        jdbc.update("INSERT INTO product(product_id,store_id,sku) VALUES (1,10,'MILK'),(2,20,'FOREIGN')");
        validator = new TransactionOrderValidator(jdbc);
    }

    @Test
    void aggregatesMatchingRepeatedProductLinesAndKeepsSourceRows() {
        var order = validate(10,
                row(2, "order-1", "MILK", "2", "10.00", "6.00", "completed"),
                row(3, "order-1", "MILK", "3", "10", "6", "COMPLETED")).get(0);

        assertTrue(order.valid());
        assertTrue(order.eligible());
        assertEquals(Instant.parse("2026-10-19T05:00:00Z"), order.occurredAt());
        assertEquals(1, order.lines().size());
        assertEquals(5, order.lines().get(0).quantity());
        assertEquals(List.of(2, 3), order.lines().get(0).sourceRowNumbers());
        assertEquals(new BigDecimal("10.00"), order.lines().get(0).unitPrice());
    }

    @Test
    void rejectsWholeOrderWhenRepeatedSkuHasDifferentPrice() {
        var order = validate(10,
                row(2, "order-1", "MILK", "1", "10", "6", "COMPLETED"),
                row(3, "order-1", "MILK", "1", "11", "6", "COMPLETED")).get(0);

        assertFalse(order.valid());
        assertTrue(order.errors().stream().anyMatch(error -> error.field().equals("sku")));
    }

    @Test
    void storeScopedSkuLookupRejectsUnknownAndForeignStoreProducts() {
        var orders = validate(10,
                row(2, "order-unknown", "NO-SUCH-SKU", "1", "10", "6", "COMPLETED"),
                row(3, "order-foreign", "FOREIGN", "1", "10", "6", "COMPLETED"));

        assertEquals(2, orders.size());
        assertTrue(orders.stream().noneMatch(order -> order.valid()));
        assertTrue(orders.stream().allMatch(order -> order.errors().stream().anyMatch(error -> error.field().equals("sku"))));
    }

    @Test
    void invalidQuantityRejectsTheCompleteOrderAndIdentifiesTheRow() {
        var order = validate(10,
                row(2, "order-1", "MILK", "2", "10", "6", "COMPLETED"),
                row(3, "order-1", "MILK", "0", "10", "6", "COMPLETED")).get(0);

        assertFalse(order.valid());
        assertTrue(order.errors().stream().anyMatch(error -> error.rowNumber() == 3 && error.field().equals("quantity")));
    }

    @Test
    void nonCompletedAndBelowCostOrdersRemainValidButAreNotF03Eligible() {
        var orders = validate(10,
                row(2, "cancelled", "MILK", "1", "10", "6", "CANCELLED"),
                row(3, "partial", "MILK", "1", "10", "6", "PARTIAL_REFUND"),
                row(4, "below-cost", "MILK", "1", "5", "6", "COMPLETED"));

        assertEquals(3, orders.size());
        assertTrue(orders.stream().allMatch(TransactionOrderValidator.ValidatedOrder::valid));
        assertTrue(orders.stream().noneMatch(TransactionOrderValidator.ValidatedOrder::eligible));
    }

    private List<TransactionOrderValidator.ValidatedOrder> validate(int storeId, TransactionCsvParser.ParsedCsvRow... rows) {
        return validator.validateAndGroup(storeId, List.of(rows));
    }

    private TransactionCsvParser.ParsedCsvRow row(int row, String orderId, String sku, String quantity,
            String price, String cost, String status) {
        return new TransactionCsvParser.ParsedCsvRow(row, Map.of(
                "source_order_id", orderId,
                "occurred_at", "2026-10-19T12:00:00+07:00",
                "sku", sku,
                "quantity", quantity,
                "unit_price", price,
                "unit_cost", cost,
                "status", status));
    }
}

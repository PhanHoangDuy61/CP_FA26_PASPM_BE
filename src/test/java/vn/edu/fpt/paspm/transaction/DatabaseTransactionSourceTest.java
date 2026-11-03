package vn.edu.fpt.paspm.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseTransactionSourceTest {
    private JdbcTemplate jdbc;
    private DatabaseTransactionSource source;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:f04source" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE sales_transaction(transaction_id INT PRIMARY KEY,store_id INT,status VARCHAR(30),f03_eligible BOOLEAN,occurred_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE transaction_item(transaction_item_id INT PRIMARY KEY,store_id INT,transaction_id INT,product_id INT,quantity INT,unit_price DECIMAL(12,2),unit_cost DECIMAL(12,2))");
        source = new DatabaseTransactionSource(jdbc);
    }

    @Test
    void loadsOnlyEligibleCompletedTransactionsWithinStoreLocalDateBoundaries() {
        insertTransaction(1, 10, "COMPLETED", true, "2026-10-18T17:00:00Z");
        insertTransaction(2, 10, "CANCELLED", false, "2026-10-19T12:00:00Z");
        insertTransaction(3, 20, "COMPLETED", true, "2026-10-19T12:00:00Z");
        jdbc.update("INSERT INTO transaction_item VALUES (1,10,1,31,4,12.50,7.25)");
        jdbc.update("INSERT INTO transaction_item VALUES (2,10,2,31,2,12.50,7.25)");
        jdbc.update("INSERT INTO transaction_item VALUES (3,20,3,99,1,9.00,4.00)");

        var transactions = source.load(10, LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-19"));

        assertEquals(1, transactions.size());
        assertEquals(1, transactions.get(0).transactionId());
        assertEquals(LocalDate.parse("2026-10-19"), transactions.get(0).occurredOn());
        assertEquals(31, transactions.get(0).items().get(0).productId());
        assertEquals(4, transactions.get(0).items().get(0).quantity());
        assertEquals(0, new java.math.BigDecimal("12.50").compareTo(transactions.get(0).items().get(0).unitPrice()));
        assertEquals(0, new java.math.BigDecimal("7.25").compareTo(transactions.get(0).items().get(0).unitCost()));
        assertNull(transactions.get(0).items().get(0).sourceUtility());
    }

    @Test
    void dateRangeIsInclusiveInAsiaHoChiMinhAndEmptyWhenNoEligibleRowsExist() {
        insertTransaction(1, 10, "COMPLETED", true, "2026-10-18T16:59:59Z");
        assertEquals(0, source.load(10, LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-19")).size());
        assertEquals(0, source.load(10, LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-18")).size());
    }

    private void insertTransaction(int id, int store, String status, boolean eligible, String occurredAt) {
        jdbc.update("INSERT INTO sales_transaction VALUES (?,?,?,?,?)", id, store, status, eligible,
                Timestamp.from(Instant.parse(occurredAt)));
    }
}

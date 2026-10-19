package vn.edu.fpt.paspm.transaction;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.edu.fpt.paspm.utility.TransactionSource;

/** Supplies persisted, validated F04 sales rows to F03 without an HTTP hop. */
@Component
@Profile("!utility-fixture")
public class DatabaseTransactionSource implements TransactionSource {
    private final JdbcTemplate jdbc;
    private final ZoneId storeZone;

    public DatabaseTransactionSource(JdbcTemplate jdbc) { this(jdbc, "Asia/Ho_Chi_Minh"); }

    @Autowired
    public DatabaseTransactionSource(JdbcTemplate jdbc,
            @Value("${paspm.store-time-zone:Asia/Ho_Chi_Minh}") String storeTimeZone) {
        this.jdbc = jdbc;
        this.storeZone = ZoneId.of(storeTimeZone);
    }

    @Override
    public String sourceType() { return "F04_DATABASE_V1"; }

    @Override
    public List<PreparedTransaction> load(int storeId, LocalDate rangeStart, LocalDate rangeEnd) {
        if (rangeStart.isAfter(rangeEnd)) return List.of();
        var from = rangeStart.atStartOfDay(storeZone).toInstant();
        var toExclusive = rangeEnd.plusDays(1).atStartOfDay(storeZone).toInstant();
        Map<Integer, TransactionRows> transactions = new LinkedHashMap<>();
        jdbc.query("""
                SELECT t.transaction_id,t.store_id,t.occurred_at,
                       i.product_id,i.quantity,i.unit_price,i.unit_cost
                FROM sales_transaction t
                JOIN transaction_item i ON i.store_id=t.store_id AND i.transaction_id=t.transaction_id
                WHERE t.store_id=? AND t.status='COMPLETED' AND t.f03_eligible=TRUE
                  AND t.occurred_at>=? AND t.occurred_at<?
                ORDER BY t.transaction_id,i.transaction_item_id
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            int transactionId = rs.getInt("transaction_id");
            TransactionRows transaction = transactions.get(transactionId);
            if (transaction == null) {
                transaction = new TransactionRows(rs.getInt("store_id"), rs.getTimestamp("occurred_at").toInstant());
                transactions.put(transactionId, transaction);
            }
            transaction.items().add(new Item(rs.getInt("product_id"), rs.getInt("quantity"),
                    rs.getBigDecimal("unit_price"), rs.getBigDecimal("unit_cost"), null));
        }, storeId, java.sql.Timestamp.from(from), java.sql.Timestamp.from(toExclusive));

        List<PreparedTransaction> prepared = new ArrayList<>(transactions.size());
        transactions.forEach((id, rows) -> prepared.add(new PreparedTransaction(rows.storeId(), id,
                rows.occurredAt().atZone(storeZone).toLocalDate(), true, List.copyOf(rows.items()))));
        return List.copyOf(prepared);
    }

    private record TransactionRows(int storeId, java.time.Instant occurredAt, List<Item> items) {
        TransactionRows(int storeId, java.time.Instant occurredAt) { this(storeId, occurredAt, new ArrayList<>()); }
    }
}

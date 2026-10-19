package vn.edu.fpt.paspm.transaction.query;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.paspm.transaction.TransactionContracts.TransactionLine;
import vn.edu.fpt.paspm.transaction.TransactionContracts.TransactionView;

@Repository
public class TransactionQueryRepository {
    private final JdbcTemplate jdbc;

    public TransactionQueryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<TransactionView> list(int storeId, String status, Instant from, Instant to, int page, int pageSize) {
        Query query = query(storeId, status, from, to);
        List<Object> arguments = new ArrayList<>(query.arguments());
        arguments.add(pageSize);
        arguments.add((long) page * pageSize);
        return jdbc.query("""
                SELECT t.transaction_id,t.source_system,t.source_order_id,t.occurred_at,t.status,t.f03_eligible,t.total_amount
                FROM sales_transaction t
                """ + query.where() + " ORDER BY t.occurred_at DESC,t.transaction_id DESC LIMIT ? OFFSET ?",
                (rs, row) -> new TransactionView(rs.getInt("transaction_id"), rs.getString("source_system"),
                        rs.getString("source_order_id"), rs.getTimestamp("occurred_at").toInstant(),
                        rs.getString("status"), rs.getBoolean("f03_eligible"), rs.getBigDecimal("total_amount"), List.of()),
                arguments.toArray());
    }

    public long count(int storeId, String status, Instant from, Instant to) {
        Query query = query(storeId, status, from, to);
        return jdbc.queryForObject("SELECT COUNT(*) FROM sales_transaction t " + query.where(), Long.class,
                query.arguments().toArray());
    }

    public TransactionView get(int storeId, int transactionId) {
        List<TransactionView> transactions = jdbc.query("""
                SELECT transaction_id,source_system,source_order_id,occurred_at,status,f03_eligible,total_amount
                FROM sales_transaction WHERE store_id=? AND transaction_id=?
                """, (rs, row) -> new TransactionView(rs.getInt("transaction_id"), rs.getString("source_system"),
                        rs.getString("source_order_id"), rs.getTimestamp("occurred_at").toInstant(), rs.getString("status"),
                        rs.getBoolean("f03_eligible"), rs.getBigDecimal("total_amount"), List.of()), storeId, transactionId);
        if (transactions.isEmpty()) return null;
        TransactionView transaction = transactions.get(0);
        List<TransactionLine> items = jdbc.query("""
                SELECT i.transaction_item_id,i.product_id,p.sku,p.name,i.quantity,i.unit_price,i.unit_cost
                FROM transaction_item i JOIN product p ON p.store_id=i.store_id AND p.product_id=i.product_id
                WHERE i.store_id=? AND i.transaction_id=? ORDER BY i.transaction_item_id
                """, (rs, row) -> new TransactionLine(rs.getInt("transaction_item_id"), rs.getInt("product_id"),
                        rs.getString("sku"), rs.getString("name"), rs.getInt("quantity"),
                        rs.getBigDecimal("unit_price"), rs.getBigDecimal("unit_cost")), storeId, transactionId);
        return new TransactionView(transaction.transactionId(), transaction.sourceSystem(), transaction.sourceOrderId(),
                transaction.occurredAt(), transaction.status(), transaction.f03Eligible(), transaction.totalAmount(), items);
    }

    private Query query(int storeId, String status, Instant from, Instant to) {
        StringBuilder where = new StringBuilder(" WHERE t.store_id=?");
        List<Object> arguments = new ArrayList<>();
        arguments.add(storeId);
        if (status != null && !status.isBlank()) {
            where.append(" AND t.status=?");
            arguments.add(status);
        }
        if (from != null) {
            where.append(" AND t.occurred_at>=?");
            arguments.add(Timestamp.from(from));
        }
        if (to != null) {
            where.append(" AND t.occurred_at<=?");
            arguments.add(Timestamp.from(to));
        }
        return new Query(where.toString(), arguments);
    }

    private record Query(String where, List<Object> arguments) {}
}

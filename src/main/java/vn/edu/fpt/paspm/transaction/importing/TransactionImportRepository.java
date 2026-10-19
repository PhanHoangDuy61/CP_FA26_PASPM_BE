package vn.edu.fpt.paspm.transaction.importing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.paspm.transaction.TransactionContracts.ImportError;
import vn.edu.fpt.paspm.transaction.TransactionContracts.ImportSummary;
import vn.edu.fpt.paspm.transaction.importing.TransactionCsvParser.ParsedCsvRow;
import vn.edu.fpt.paspm.transaction.importing.TransactionOrderValidator.ValidatedLine;
import vn.edu.fpt.paspm.transaction.importing.TransactionOrderValidator.ValidatedOrder;
import vn.edu.fpt.paspm.transaction.importing.TransactionOrderValidator.ValidationError;

@Repository
public class TransactionImportRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public TransactionImportRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public int createBatch(int storeId, String filename) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO import_batch(store_id,method,filename,status) VALUES (?,'FILE',?,'PROCESSING')",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, storeId);
            statement.setString(2, filename);
            return statement;
        }, key);
        Number id = generatedKey(key, "import_batch_id");
        if (id == null) throw new IllegalStateException("Database did not return import batch ID");
        return id.intValue();
    }

    public void insertStagingRows(int storeId, int batchId, List<ParsedCsvRow> rows) {
        jdbc.batchUpdate("INSERT INTO import_staging_row(store_id,import_batch_id,row_number,source_order_id,raw_data,status) VALUES (?,?,?,?,?, 'PENDING')",
                rows, 500, (statement, row) -> {
                    statement.setInt(1, storeId);
                    statement.setInt(2, batchId);
                    statement.setInt(3, row.rowNumber());
                    String orderId = row.values().get("source_order_id");
                    statement.setString(4, orderId == null || orderId.isBlank() ? null : orderId.trim());
                    statement.setString(5, writeJson(row.values()));
                });
        jdbc.update("UPDATE import_batch SET total_count=? WHERE import_batch_id=?", rows.size(), batchId);
    }

    public ExistingOrder findExisting(int storeId, String sourceOrderId) {
        List<ExistingOrder> rows = jdbc.query("""
                SELECT transaction_id, content_sha256 FROM sales_transaction
                WHERE store_id=? AND source_system='CSV_UPLOAD' AND source_order_id=?
                """, (rs, row) -> new ExistingOrder(rs.getInt("transaction_id"), rs.getString("content_sha256")),
                storeId, sourceOrderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<Integer, Integer> findItemIds(int storeId, int transactionId) {
        Map<Integer, Integer> ids = new HashMap<>();
        jdbc.query("SELECT product_id,transaction_item_id FROM transaction_item WHERE store_id=? AND transaction_id=?",
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        ids.put(rs.getInt("product_id"), rs.getInt("transaction_item_id")), storeId, transactionId);
        return ids;
    }

    public int insertTransaction(int storeId, int batchId, ValidatedOrder order, String contentHash,
            java.math.BigDecimal totalAmount) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO sales_transaction(store_id,import_batch_id,source_system,source_order_id,occurred_at,status,f03_eligible,total_amount,content_sha256)
                    VALUES (?,?,'CSV_UPLOAD',?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, storeId);
            statement.setInt(2, batchId);
            statement.setString(3, order.sourceOrderId());
            statement.setTimestamp(4, Timestamp.from(order.occurredAt()));
            statement.setString(5, order.status());
            statement.setBoolean(6, order.eligible());
            statement.setBigDecimal(7, totalAmount);
            statement.setString(8, contentHash);
            return statement;
        }, key);
        Number id = generatedKey(key, "transaction_id");
        if (id == null) throw new IllegalStateException("Database did not return transaction ID");
        return id.intValue();
    }

    public Map<Integer, Integer> insertItems(int storeId, int transactionId, List<ValidatedLine> lines) {
        Map<Integer, Integer> itemIds = new HashMap<>();
        for (ValidatedLine line : lines) {
            var key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO transaction_item(store_id,transaction_id,product_id,quantity,unit_price,unit_cost)
                        VALUES (?,?,?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setInt(1, storeId);
                statement.setInt(2, transactionId);
                statement.setInt(3, line.productId());
                statement.setInt(4, line.quantity());
                statement.setBigDecimal(5, line.unitPrice());
                statement.setBigDecimal(6, line.unitCost());
                return statement;
            }, key);
            Number id = generatedKey(key, "transaction_item_id");
            if (id == null) throw new IllegalStateException("Database did not return transaction item ID");
            itemIds.put(line.productId(), id.intValue());
        }
        return itemIds;
    }

    public void markOrderValid(int batchId, ValidatedOrder order, Map<Integer, Integer> productIdsByRow) {
        for (int rowNumber : order.rowNumbers()) {
            jdbc.update("UPDATE import_staging_row SET status='VALID',product_id=? WHERE import_batch_id=? AND row_number=?",
                    productIdsByRow.get(rowNumber), batchId, rowNumber);
        }
    }

    public void markOrderRejected(int batchId, ValidatedOrder order) {
        Map<Integer, List<ValidationError>> errorsByRow = new HashMap<>();
        for (ValidationError error : order.errors()) errorsByRow.computeIfAbsent(error.rowNumber(), ignored -> new ArrayList<>()).add(error);
        for (int rowNumber : order.rowNumbers()) {
            List<ValidationError> errors = errorsByRow.getOrDefault(rowNumber, List.of());
            String details = errors.isEmpty() ? "Order rejected because another row failed validation" : writeJson(errors);
            jdbc.update("UPDATE import_staging_row SET status='REJECTED',error_details=?,processed_at=? WHERE import_batch_id=? AND row_number=?",
                    details, Timestamp.from(Instant.now()), batchId, rowNumber);
        }
    }

    public void markConflict(int batchId, ValidatedOrder order, String message) {
        for (int rowNumber : order.rowNumbers()) {
            jdbc.update("UPDATE import_staging_row SET status='REJECTED',error_details=?,processed_at=? WHERE import_batch_id=? AND row_number=?",
                    writeJson(List.of(new ValidationError(rowNumber, "source_order_id", message))),
                    Timestamp.from(Instant.now()), batchId, rowNumber);
        }
    }

    public void markProcessed(int batchId, ValidatedOrder order, int transactionId, Map<Integer, Integer> itemIds,
            Map<Integer, Integer> productIdsByRow) {
        for (int rowNumber : order.rowNumbers()) {
            Integer productId = productIdsByRow.get(rowNumber);
            jdbc.update("""
                    UPDATE import_staging_row SET status='PROCESSED',product_id=?,processed_transaction_id=?,
                        transaction_item_id=?,processed_at=? WHERE import_batch_id=? AND row_number=?
                    """, productId, transactionId, itemIds.get(productId), Timestamp.from(Instant.now()), batchId, rowNumber);
        }
    }

    public void finishBatch(int batchId, int validRows, int rejectedRows, int totalOrders, int acceptedOrders,
            int rejectedOrders, int duplicateOrders, int conflictOrders) {
        String status = rejectedRows == 0 && conflictOrders == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS";
        String errorSummary = rejectedRows == 0 && conflictOrders == 0 ? null : "One or more orders were rejected or conflicted";
        jdbc.update("""
                UPDATE import_batch SET valid_count=?,rejected_count=?,total_order_count=?,accepted_order_count=?,
                    rejected_order_count=?,duplicate_order_count=?,conflict_order_count=?,status=?,finished_at=?,error_summary=?
                WHERE import_batch_id=?
                """, validRows, rejectedRows, totalOrders, acceptedOrders, rejectedOrders, duplicateOrders,
                conflictOrders, status, Timestamp.from(Instant.now()), errorSummary, batchId);
    }

    public void failBatch(int batchId, String message) {
        jdbc.update("UPDATE import_batch SET status='FAILED',finished_at=?,error_summary=? WHERE import_batch_id=?",
                Timestamp.from(Instant.now()), shorten(message), batchId);
    }

    public ImportSummary getBatch(int storeId, int batchId) {
        List<ImportSummary> result = jdbc.query("SELECT * FROM import_batch WHERE store_id=? AND import_batch_id=?",
                (rs, row) -> new ImportSummary(rs.getInt("import_batch_id"), rs.getString("status"),
                        rs.getString("filename"), instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("finished_at")),
                        rs.getInt("total_count"), rs.getInt("valid_count"), rs.getInt("rejected_count"),
                        rs.getInt("total_order_count"), rs.getInt("accepted_order_count"), rs.getInt("rejected_order_count"),
                        rs.getInt("duplicate_order_count"), rs.getInt("conflict_order_count"), rs.getString("error_summary")),
                storeId, batchId);
        return result.isEmpty() ? null : result.get(0);
    }

    public List<ImportSummary> listBatches(int storeId, int page, int pageSize) {
        return jdbc.query("SELECT * FROM import_batch WHERE store_id=? ORDER BY started_at DESC,import_batch_id DESC LIMIT ? OFFSET ?",
                (rs, row) -> new ImportSummary(rs.getInt("import_batch_id"), rs.getString("status"),
                        rs.getString("filename"), instant(rs.getTimestamp("started_at")), instant(rs.getTimestamp("finished_at")),
                        rs.getInt("total_count"), rs.getInt("valid_count"), rs.getInt("rejected_count"),
                        rs.getInt("total_order_count"), rs.getInt("accepted_order_count"), rs.getInt("rejected_order_count"),
                        rs.getInt("duplicate_order_count"), rs.getInt("conflict_order_count"), rs.getString("error_summary")),
                storeId, pageSize, (long) page * pageSize);
    }

    public long countBatches(int storeId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM import_batch WHERE store_id=?", Long.class, storeId);
    }

    public List<ImportError> listErrors(int storeId, int batchId, int page, int pageSize) {
        return jdbc.query("""
                SELECT r.row_number,r.source_order_id,r.status,r.error_details FROM import_staging_row r
                JOIN import_batch b ON b.import_batch_id=r.import_batch_id
                WHERE b.store_id=? AND b.import_batch_id=? AND (r.status='REJECTED' OR r.error_details IS NOT NULL)
                ORDER BY r.row_number LIMIT ? OFFSET ?
                """, (rs, row) -> new ImportError(rs.getInt("row_number"), rs.getString("source_order_id"),
                        rs.getString("status"), rs.getString("error_details")), storeId, batchId, pageSize, (long) page * pageSize);
    }

    public long countErrors(int storeId, int batchId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM import_staging_row r JOIN import_batch b ON b.import_batch_id=r.import_batch_id
                WHERE b.store_id=? AND b.import_batch_id=? AND (r.status='REJECTED' OR r.error_details IS NOT NULL)
                """, Long.class, storeId, batchId);
    }

    private Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }
    private Number generatedKey(GeneratedKeyHolder holder, String column) {
        Map<String, Object> keys = holder.getKeys();
        Object value = keys == null ? null : keys.get(column);
        if (value == null && keys != null) {
            value = keys.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(column))
                    .map(Map.Entry::getValue).findFirst().orElse(null);
        }
        if (value == null && keys != null && keys.size() == 1) value = keys.values().iterator().next();
        return value instanceof Number number ? number : null;
    }
    private String writeJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to serialize import audit data", exception); }
    }
    private String shorten(String message) { return message == null ? "Import failed" : message.substring(0, Math.min(2000, message.length())); }

    public record ExistingOrder(int transactionId, String contentHash) {}
}

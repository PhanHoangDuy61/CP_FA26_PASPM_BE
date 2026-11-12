package vn.edu.fpt.paspm.transaction.importing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import vn.edu.fpt.paspm.transaction.TransactionContracts.ImportProgress;
import vn.edu.fpt.paspm.transaction.TransactionContracts.ImportStatistics;
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
                    "INSERT INTO import_batch(store_id,method,filename,status,processing_started_at) VALUES (?,'FILE',?,'PROCESSING',CURRENT_TIMESTAMP)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, storeId);
            statement.setString(2, filename);
            return statement;
        }, key);
        Number id = generatedKey(key, "import_batch_id");
        if (id == null) throw new IllegalStateException("Database did not return import batch ID");
        return id.intValue();
    }

    public int createAsyncBatch(int storeId, String filename, String idempotencyKey, String contentHash) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO import_batch(store_id,method,filename,status,stage,idempotency_key,content_sha256)
                    VALUES (?,'FILE',?,'RECEIVING','RECEIVING',?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, storeId);
            statement.setString(2, filename);
            statement.setString(3, idempotencyKey);
            statement.setString(4, contentHash);
            return statement;
        }, key);
        Number id = generatedKey(key, "import_batch_id");
        if (id == null) throw new IllegalStateException("Database did not return import batch ID");
        return id.intValue();
    }

    public ExistingBatch findIdempotentBatch(int storeId, String idempotencyKey) {
        return jdbc.query("""
                SELECT import_batch_id,content_sha256 FROM import_batch
                WHERE store_id=? AND idempotency_key=?
                """, (rs, row) -> new ExistingBatch(rs.getInt(1), rs.getString(2)),
                storeId, idempotencyKey).stream().findFirst().orElse(null);
    }

    public void queueBatch(int storeId, int batchId, int totalCount) {
        if (jdbc.update("""
                UPDATE import_batch SET status='QUEUED',stage='QUEUED',input_total_count=?,total_count=?,
                    updated_at=CURRENT_TIMESTAMP
                WHERE store_id=? AND import_batch_id=? AND status='RECEIVING'
                """, totalCount, totalCount, storeId, batchId) != 1) {
            throw new IllegalStateException("Import batch could not be queued");
        }
    }

    public void setInputTotalCount(int storeId, int batchId, int totalCount) {
        jdbc.update("UPDATE import_batch SET input_total_count=?,total_count=?,updated_at=CURRENT_TIMESTAMP WHERE store_id=? AND import_batch_id=?",
                totalCount, totalCount, storeId, batchId);
    }

    public void insertStagingRows(int storeId, int batchId, List<ParsedCsvRow> rows) {
        jdbc.batchUpdate("INSERT INTO import_staging_row(store_id,import_batch_id,`row_number`,source_order_id,raw_data,status) VALUES (?,?,?,?,?, 'PENDING')",
                rows, 500, (statement, row) -> {
                    statement.setInt(1, storeId);
                    statement.setInt(2, batchId);
                    statement.setInt(3, row.rowNumber());
                    String orderId = row.values().get("source_order_id");
                    String normalizedOrderId = orderId == null ? null : orderId.trim();
                    // This nullable lookup projection must not reject a row before business validation.
                    // Keep the complete original value in raw_data; never truncate an order identity.
                    statement.setString(4, normalizedOrderId == null || normalizedOrderId.isBlank()
                            || normalizedOrderId.length() > 150 ? null : normalizedOrderId);
                    statement.setString(5, writeJson(row.values()));
                });
        jdbc.update("UPDATE import_batch SET total_count=?,input_total_count=?,updated_at=CURRENT_TIMESTAMP WHERE store_id=? AND import_batch_id=?",
                rows.size(), rows.size(), storeId, batchId);
    }

    public ExistingOrder findExisting(int storeId, String sourceOrderId) {
        List<ExistingOrder> rows = jdbc.query("""
                SELECT transaction_id, content_sha256, import_batch_id FROM sales_transaction
                WHERE store_id=? AND source_system='CSV_UPLOAD' AND source_order_id=?
                """, (rs, row) -> new ExistingOrder(rs.getInt("transaction_id"), rs.getString("content_sha256"),
                        rs.getInt("import_batch_id")),
                storeId, sourceOrderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<ParsedCsvRow> stagingRows(int storeId, int batchId) {
        return jdbc.query("""
                SELECT `row_number`,raw_data FROM import_staging_row
                WHERE store_id=? AND import_batch_id=? ORDER BY `row_number`
                """, (rs, row) -> new ParsedCsvRow(rs.getInt(1), readRowData(rs.getString(2))), storeId, batchId);
    }

    public List<DispatchCandidate> dispatchCandidates() {
        return jdbc.query("""
                SELECT store_id,import_batch_id FROM import_batch
                WHERE status='QUEUED' OR
                    (status='PROCESSING' AND lease_owner IS NOT NULL AND lease_until<=CURRENT_TIMESTAMP)
                ORDER BY import_batch_id LIMIT 100
                """, (rs, row) -> new DispatchCandidate(rs.getInt(1), rs.getInt(2)));
    }

    public void assignOrderGroups(int storeId, int batchId, List<ValidatedOrder> orders) {
        Map<Integer, Integer> groupsByRow = new HashMap<>();
        for (ValidatedOrder order : orders) {
            int group = order.rowNumbers().stream().mapToInt(Integer::intValue).min().orElseThrow();
            for (int rowNumber : order.rowNumbers()) groupsByRow.put(rowNumber, group);
        }
        jdbc.batchUpdate("""
                UPDATE import_staging_row SET result_group=?
                WHERE store_id=? AND import_batch_id=? AND `row_number`=?
                """, groupsByRow.entrySet(), 500, (statement, entry) -> {
            statement.setInt(1, entry.getValue());
            statement.setInt(2, storeId);
            statement.setInt(3, batchId);
            statement.setInt(4, entry.getKey());
        });
    }

    public void touchBatch(TransactionImportLease.Claim claim) {
        if (jdbc.update("""
                UPDATE import_batch SET updated_at=CURRENT_TIMESTAMP
                WHERE store_id=? AND import_batch_id=? AND status='PROCESSING' AND lease_owner=? AND lease_fence=?
                """, claim.storeId(), claim.batchId(), claim.owner(), claim.fence()) != 1) {
            throw new IllegalStateException("Import batch lease was lost while updating progress");
        }
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
        int group = group(order);
        for (int rowNumber : order.rowNumbers()) {
            jdbc.update("UPDATE import_staging_row SET status='VALID',product_id=?,result_group=? WHERE import_batch_id=? AND `row_number`=?",
                    productIdsByRow.get(rowNumber), group, batchId, rowNumber);
        }
    }

    public void markOrderRejected(int batchId, ValidatedOrder order) {
        int group = group(order);
        Map<Integer, List<ValidationError>> errorsByRow = new HashMap<>();
        for (ValidationError error : order.errors()) errorsByRow.computeIfAbsent(error.rowNumber(), ignored -> new ArrayList<>()).add(error);
        for (int rowNumber : order.rowNumbers()) {
            List<ValidationError> errors = errorsByRow.getOrDefault(rowNumber, List.of());
            String details = errors.isEmpty() ? "Order rejected because another row failed validation" : writeJson(errors);
            jdbc.update("UPDATE import_staging_row SET status='REJECTED',result_group=?,result_kind='REJECTED',error_details=?,processed_at=? WHERE import_batch_id=? AND `row_number`=?",
                    group, details, Timestamp.from(Instant.now()), batchId, rowNumber);
        }
    }

    public void markConflict(int batchId, ValidatedOrder order, String message) {
        int group = group(order);
        for (int rowNumber : order.rowNumbers()) {
            jdbc.update("UPDATE import_staging_row SET status='REJECTED',result_group=?,result_kind='CONFLICT',error_details=?,processed_at=? WHERE import_batch_id=? AND `row_number`=?",
                    group, writeJson(List.of(new ValidationError(rowNumber, "source_order_id", message))),
                    Timestamp.from(Instant.now()), batchId, rowNumber);
        }
    }

    public void markProcessed(int batchId, ValidatedOrder order, int transactionId, Map<Integer, Integer> itemIds,
            Map<Integer, Integer> productIdsByRow, String resultKind) {
        int group = group(order);
        for (int rowNumber : order.rowNumbers()) {
            Integer productId = productIdsByRow.get(rowNumber);
            jdbc.update("""
                    UPDATE import_staging_row SET status='PROCESSED',product_id=?,processed_transaction_id=?,
                        transaction_item_id=?,result_group=?,result_kind=?,processed_at=? WHERE import_batch_id=? AND `row_number`=?
                    """, productId, transactionId, itemIds.get(productId), group, resultKind,
                    Timestamp.from(Instant.now()), batchId, rowNumber);
        }
    }

    public void finishBatch(int batchId, int validRows, int rejectedRows, int totalOrders, int acceptedOrders,
            int rejectedOrders, int duplicateOrders, int conflictOrders) {
        String status = rejectedRows == 0 && conflictOrders == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS";
        String errorSummary = rejectedRows == 0 && conflictOrders == 0 ? null : "One or more orders were rejected or conflicted";
        jdbc.update("""
                UPDATE import_batch SET valid_count=?,rejected_count=?,total_order_count=?,accepted_order_count=?,
                    rejected_order_count=?,duplicate_order_count=?,conflict_order_count=?,status=?,stage=?,finished_at=?,
                    updated_at=CURRENT_TIMESTAMP,input_total_count=total_count,
                    error_summary=?,lease_owner=NULL,lease_until=NULL
                WHERE import_batch_id=?
                """, validRows, rejectedRows, totalOrders, acceptedOrders, rejectedOrders, duplicateOrders,
                conflictOrders, status, status, Timestamp.from(Instant.now()), errorSummary, batchId);
    }

    public void failBatch(int batchId, String message) {
        jdbc.update("""
                UPDATE import_batch SET status='FAILED',stage='FAILED',finished_at=?,updated_at=CURRENT_TIMESTAMP,
                    error_summary=?,lease_owner=NULL,lease_until=NULL WHERE import_batch_id=?
                """, Timestamp.from(Instant.now()), shorten(message), batchId);
    }

    public void failBatch(TransactionImportLease.Claim claim, String message) {
        if (jdbc.update("""
                UPDATE import_batch SET status='FAILED',stage='FAILED',finished_at=?,updated_at=CURRENT_TIMESTAMP,
                    error_summary=?,lease_owner=NULL,lease_until=NULL
                WHERE store_id=? AND import_batch_id=? AND status='PROCESSING' AND lease_owner=? AND lease_fence=?
                """, Timestamp.from(Instant.now()), shorten(message), claim.storeId(), claim.batchId(),
                claim.owner(), claim.fence()) != 1) {
            throw new IllegalStateException("Import batch lease was lost before failure could be recorded");
        }
    }

    public void finishBatch(TransactionImportLease.Claim claim, int validRows, int rejectedRows, int totalOrders,
            int acceptedOrders, int rejectedOrders, int duplicateOrders, int conflictOrders) {
        String status = rejectedRows == 0 && conflictOrders == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS";
        String errorSummary = rejectedRows == 0 && conflictOrders == 0 ? null : "One or more orders were rejected or conflicted";
        if (jdbc.update("""
                UPDATE import_batch SET valid_count=?,rejected_count=?,total_order_count=?,accepted_order_count=?,
                    rejected_order_count=?,duplicate_order_count=?,conflict_order_count=?,status=?,stage=?,
                    finished_at=?,updated_at=CURRENT_TIMESTAMP,input_total_count=total_count,
                    error_summary=?,lease_owner=NULL,lease_until=NULL
                WHERE store_id=? AND import_batch_id=? AND status='PROCESSING' AND lease_owner=? AND lease_fence=?
                """, validRows, rejectedRows, totalOrders, acceptedOrders, rejectedOrders, duplicateOrders,
                conflictOrders, status, status, Timestamp.from(Instant.now()), errorSummary, claim.storeId(),
                claim.batchId(), claim.owner(), claim.fence()) != 1) {
            throw new IllegalStateException("Import batch lease was lost before completion");
        }
    }

    public ImportProgress getProgress(int storeId, int batchId) {
        return jdbc.query("""
                SELECT b.*,
                    (SELECT COUNT(*) FROM import_staging_row r WHERE r.store_id=b.store_id AND r.import_batch_id=b.import_batch_id AND r.status IN ('PROCESSED','REJECTED')) AS actual_processed,
                    (SELECT COUNT(*) FROM import_staging_row r WHERE r.store_id=b.store_id AND r.import_batch_id=b.import_batch_id AND r.status IN ('VALID','PROCESSED')) AS actual_valid,
                    (SELECT COUNT(*) FROM import_staging_row r WHERE r.store_id=b.store_id AND r.import_batch_id=b.import_batch_id AND r.status='REJECTED') AS actual_rejected,
                    (SELECT COUNT(DISTINCT result_group) FROM import_staging_row r WHERE r.store_id=b.store_id AND r.import_batch_id=b.import_batch_id AND result_group IS NOT NULL) AS actual_orders,
                    (SELECT COUNT(DISTINCT result_group) FROM import_staging_row r WHERE r.store_id=b.store_id AND r.import_batch_id=b.import_batch_id AND result_kind='ACCEPTED') AS actual_accepted_orders,
                    (SELECT COUNT(DISTINCT result_group) FROM import_staging_row r WHERE r.store_id=b.store_id AND r.import_batch_id=b.import_batch_id AND result_kind='DUPLICATE') AS actual_duplicate_orders,
                    (SELECT COUNT(DISTINCT result_group) FROM import_staging_row r WHERE r.store_id=b.store_id AND r.import_batch_id=b.import_batch_id AND result_kind='REJECTED') AS actual_rejected_orders,
                    (SELECT COUNT(DISTINCT result_group) FROM import_staging_row r WHERE r.store_id=b.store_id AND r.import_batch_id=b.import_batch_id AND result_kind='CONFLICT') AS actual_conflict_orders
                FROM import_batch b WHERE b.store_id=? AND b.import_batch_id=?
                """, (rs, row) -> {
                    int processed = rs.getInt("actual_processed");
                    int total = rs.getInt("input_total_count");
                    Integer totalCount = rs.wasNull() ? null : total;
                    Integer percent = totalCount == null || totalCount == 0 ? null
                            : (int) Math.min(100, Math.round(processed * 100.0f / totalCount));
                    String status = rs.getString("status");
                    String stage = rs.getString("stage");
                    ImportStatistics statistics = "LEGACY".equals(stage)
                            ? new ImportStatistics(rs.getInt("valid_count"), rs.getInt("rejected_count"),
                                    rs.getInt("total_order_count"), rs.getInt("accepted_order_count"),
                                    rs.getInt("rejected_order_count"), rs.getInt("duplicate_order_count"),
                                    rs.getInt("conflict_order_count"))
                            : new ImportStatistics(rs.getInt("actual_valid"), rs.getInt("actual_rejected"),
                                    rs.getInt("actual_orders"), rs.getInt("actual_accepted_orders"),
                                    rs.getInt("actual_rejected_orders"), rs.getInt("actual_duplicate_orders"),
                                    rs.getInt("actual_conflict_orders"));
                    return new ImportProgress(rs.getInt("import_batch_id"), status, stage, processed,
                            totalCount, percent, instant(rs.getTimestamp("created_at")),
                            instant(rs.getTimestamp("processing_started_at")), instant(rs.getTimestamp("updated_at")),
                            instant(rs.getTimestamp("finished_at")), statistics, rs.getString("error_summary"));
                }, storeId, batchId).stream().findFirst().orElse(null);
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
                SELECT r.`row_number`,r.source_order_id,r.status,r.error_details FROM import_staging_row r
                JOIN import_batch b ON b.import_batch_id=r.import_batch_id
                WHERE b.store_id=? AND b.import_batch_id=? AND (r.status='REJECTED' OR r.error_details IS NOT NULL)
                ORDER BY r.`row_number` LIMIT ? OFFSET ?
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
    private Map<String, String> readRowData(String value) {
        try { return json.readValue(value, new TypeReference<>() {}); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to read staged import row", exception); }
    }
    private String shorten(String message) { return message == null ? "Import failed" : message.substring(0, Math.min(2000, message.length())); }

    private int group(ValidatedOrder order) {
        return order.rowNumbers().stream().mapToInt(Integer::intValue).min().orElseThrow();
    }

    public record DispatchCandidate(int storeId, int batchId) {}
    public record ExistingBatch(int batchId, String contentHash) {}
    public record ExistingOrder(int transactionId, String contentHash, int importBatchId) {}
}

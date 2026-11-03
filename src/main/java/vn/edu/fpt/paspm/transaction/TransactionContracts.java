package vn.edu.fpt.paspm.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class TransactionContracts {
    private TransactionContracts() {}

    public record Page<T>(List<T> content, int page, int pageSize, long totalElements) {}
    public record ImportSummary(int importBatchId, String status, String filename, Instant startedAt,
            Instant finishedAt, int totalCount, int validCount, int rejectedCount, int totalOrderCount,
            int acceptedOrderCount, int rejectedOrderCount, int duplicateOrderCount, int conflictOrderCount,
            String errorSummary) {}
    public record ImportError(int rowNumber, String sourceOrderId, String status, String errorDetails) {}
    public record TransactionLine(int transactionItemId, int productId, String sku, String productName,
            int quantity, BigDecimal unitPrice, BigDecimal unitCost) {}
    public record TransactionView(int transactionId, String sourceSystem, String sourceOrderId, Instant occurredAt,
            String status, boolean f03Eligible, BigDecimal totalAmount, List<TransactionLine> items) {}
}

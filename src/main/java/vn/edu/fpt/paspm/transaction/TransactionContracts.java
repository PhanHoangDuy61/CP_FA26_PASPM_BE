package vn.edu.fpt.paspm.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public final class TransactionContracts {
    private TransactionContracts() {}

    public record Page<T>(List<T> content, int page, int pageSize, long totalElements) {}
    public record ImportSummary(int importBatchId, String status, String filename, Instant startedAt,
            Instant finishedAt, int totalCount, int validCount, int rejectedCount, int totalOrderCount,
            int acceptedOrderCount, int rejectedOrderCount, int duplicateOrderCount, int conflictOrderCount,
            String errorSummary) {}
    public record ImportProgress(int importBatchId,
            @Schema(allowableValues = {"RECEIVING", "QUEUED", "PROCESSING", "COMPLETED",
                    "COMPLETED_WITH_ERRORS", "FAILED"}) String status,
            @Schema(allowableValues = {"RECEIVING", "QUEUED", "IMPORTING", "COMPLETED",
                    "COMPLETED_WITH_ERRORS", "FAILED", "LEGACY"}) String stage,
            int processedCount,
            @Schema(types = {"integer", "null"}, format = "int32") Integer totalCount,
            @Schema(types = {"integer", "null"}, format = "int32",
                    description = "Measured processedCount / totalCount; null when no exact nonzero total is known")
            Integer progressPercent,
            Instant createdAt,
            @Schema(types = {"string", "null"}, format = "date-time") Instant startedAt,
            Instant updatedAt,
            @Schema(types = {"string", "null"}, format = "date-time") Instant completedAt,
            ImportStatistics statistics,
            @Schema(types = {"string", "null"}) String errorSummary) {}
    public record ImportStatistics(int validCount, int rejectedCount, int totalOrderCount,
            int acceptedOrderCount, int rejectedOrderCount, int duplicateOrderCount, int conflictOrderCount) {}
    public record ImportError(int rowNumber, String sourceOrderId, String status, String errorDetails) {}
    public record TransactionLine(int transactionItemId, int productId, String sku, String productName,
            int quantity, BigDecimal unitPrice, BigDecimal unitCost) {}
    public record TransactionView(int transactionId, String sourceSystem, String sourceOrderId, Instant occurredAt,
            String status, boolean f03Eligible, BigDecimal totalAmount, List<TransactionLine> items) {}
}

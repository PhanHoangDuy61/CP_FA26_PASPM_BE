package vn.edu.fpt.paspm.utility;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.constraints.*;
import vn.edu.fpt.paspm.utility.UtilityContracts.*;

public final class UtilityMatrixContracts {
    private UtilityMatrixContracts() {}
    public record RangeRequest(@NotNull LocalDate rangeStart,@NotNull LocalDate rangeEnd,@NotNull LocalDate asOfDate) {}
    public record PreviewRequest(@NotNull LocalDate rangeStart,@NotNull LocalDate rangeEnd,@NotNull LocalDate asOfDate,
        @Min(0) Integer page,@Min(1) @Max(100) Integer pageSize) {
        public RangeRequest range() { return new RangeRequest(rangeStart,rangeEnd,asOfDate); }
    }
    public record EffectiveCoefficients(BigDecimal alpha,BigDecimal beta,BigDecimal gamma) {}
    public record FrozenConfiguration(WeightConfig config,LambdaPolicy lambdaPolicy,EffectiveCoefficients effectiveCoefficients,
        LocalDate analysisDate,String source,int utilityScale,String roundingMode,String itemMapping,String timeZone) {}
    public record MatrixItem(int transactionId,int productId,int algorithmItemId,int quantity,BigDecimal baseUtility,
        BigDecimal stockScore,BigDecimal expiryScore,BigDecimal seasonalScore,String expiryStatus,
        BigDecimal weightValue,BigDecimal weightedUtility,BigDecimal transactionUtility,ExpiryScoreCalculator.Evidence expiryEvidence) {}
    public record MatrixResult(List<MatrixItem> items,BigDecimal totalWeightedUtility,String sourceHash,String matrixHash,
        List<String> warnings,boolean ready) {}
    public record MatrixPage(List<MatrixItem> items,int page,int pageSize,long totalElements,BigDecimal totalWeightedUtility,
        String sourceHash,String matrixHash,List<String> warnings,boolean ready,FrozenConfiguration configuration) {}
    public record SnapshotView(int snapshotId,int storeId,int createdBy,LocalDate rangeStart,LocalDate rangeEnd,
        LocalDate asOfDate,FrozenConfiguration configuration,String sourceHash,String matrixHash,
        BigDecimal totalWeightedUtility,int roundingScale,String status,Instant createdAt,List<String> warnings) {}
}

package vn.edu.fpt.paspm.mining;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import vn.edu.fpt.paspm.mining.MiningEngine.Algorithm;
import vn.edu.fpt.paspm.common.StrictIntegerDeserializer;
import vn.edu.fpt.paspm.utility.UtilityMatrixContracts.SnapshotView;

public final class MiningContracts {
    private MiningContracts() {}
    public enum MinUtilMode { PERCENTAGE, ABSOLUTE }
    public enum State { PENDING, RUNNING, COMPLETED, FAILED, TIMED_OUT }
    public record CreateRequest(@NotNull UUID requestKey,
        @Schema(minimum="1",example="2") @NotNull @Positive @JsonDeserialize(using=StrictIntegerDeserializer.class) Integer snapshotId,
        @ArraySchema(minItems=1,maxItems=2,uniqueItems=true,schema=@Schema(implementation=Algorithm.class)) @NotEmpty @Size(min=1,max=2) List<@NotNull Algorithm> algorithms,
        @NotNull MinUtilMode minUtilMode,
        @Schema(description="Positive value, at most 6 decimal places; PERCENTAGE in (0,100], ABSOLUTE in frozen weighted utility units",example="30") @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=24,fraction=6) BigDecimal minUtilValue) {}
    public record Page<T>(List<T> items,int page,int pageSize,long totalElements) {}
    public record RequestView(long requestId,int storeId,int requestedBy,UUID requestKey,int snapshotId,
        List<MiningEngine.Algorithm> algorithms,MinUtilMode minUtilMode,BigDecimal minUtilValue,
        int minUtilSpmf,BigDecimal effectiveMinUtility,@Schema(allowableValues={"PENDING","RUNNING","COMPLETED","FAILED"}) String status,@Schema(allowableValues={"NOT_REQUESTED","PENDING","MATCH","FAILED","BLOCKED"}) String comparisonStatus,
        String snapshotHash,String inputHash,String conversionVersion,int utilityScale,String roundingMode,
        long divisor,int totalScaledUtility,int transactionCount,int distinctItems,int inputBytes,
        SnapshotView snapshot,Instant createdAt) {}
    public record RunView(long runId,long requestId,int storeId,int snapshotId,MiningEngine.Algorithm algorithm,
        int attemptNo,String libraryVersion,@Schema(allowableValues={"PENDING","RUNNING","COMPLETED","FAILED","TIMED_OUT"}) String status,int minUtilSpmf,String inputHash,
        String conversionVersion,@Schema(types={"string","null"},format="date-time") Instant startedAt,@Schema(types={"string","null"},format="date-time") Instant completedAt,@Schema(types={"integer","null"},format="int64") Long runtimeMillis,
        @Schema(types={"number","null"},format="double") Double memoryMiB,String memoryMethod,String runtimeMethod,@Schema(types={"string","null"}) String environment,
        @Schema(types={"integer","null"},format="int32") Integer patternCount,@Schema(types={"string","null"}) String failureCode,@Schema(types={"string","null"}) String failureMessage) {}
    public record PatternView(long patternId,long runId,List<Integer> productIds,BigDecimal utility,
        int utilitySpmf,int patternSize,@Schema(types={"integer","null"},format="int32",description="Not measured by SPMF; always null") Integer supportCount) {}
    public record BenchmarkView(RunView run,String snapshotHash,int transactionCount,int distinctItems,
        int inputBytes,long divisor,int utilityScale,BigDecimal effectiveMinUtility,String comparisonStatus,
        String repetitionPolicy) {}
}

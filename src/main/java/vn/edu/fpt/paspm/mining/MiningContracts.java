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
        @ArraySchema(minItems=1,maxItems=1,uniqueItems=true,schema=@Schema(allowableValues={"EFIM"})) @NotEmpty @Size(min=1,max=1) List<@NotNull Algorithm> algorithms,
        @NotNull MinUtilMode minUtilMode,
        @Schema(description="Positive value, at most 6 decimal places; PERCENTAGE in (0,100], ABSOLUTE in frozen weighted utility units",example="30") @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=24,fraction=6) BigDecimal minUtilValue) {}
    public record StoreCreateRequest(@NotNull UUID requestKey,
        @NotNull @Positive @JsonDeserialize(using=StrictIntegerDeserializer.class) Integer snapshotId) {}
    public record ProgressView(long requestId,int storeId,String status,String stage,
        @Schema(types={"string","null"},format="date-time") Instant stageStartedAt,
        @Schema(types={"integer","null"},format="int64") Long currentRunId,
        @Schema(types={"string","null"}) String algorithm,long attemptNo,int completedSteps,int totalSteps,int percent,
        @Schema(types={"integer","null"},format="int64") Long elapsedMs,
        @Schema(types={"integer","null"},format="int64") Long timeoutMs,String comparisonStatus) {}
    public record Page<T>(List<T> items,int page,int pageSize,long totalElements) {}
    public record RequestView(long requestId,int storeId,int requestedBy,UUID requestKey,int snapshotId,
        List<MiningEngine.Algorithm> algorithms,MinUtilMode minUtilMode,BigDecimal minUtilValue,
        int minUtilSpmf,BigDecimal effectiveMinUtility,@Schema(allowableValues={"PENDING","RUNNING","COMPLETED","FAILED"}) String status,@Schema(allowableValues={"NOT_REQUESTED","PENDING","MATCH","FAILED","BLOCKED"}) String comparisonStatus,
        String snapshotHash,String inputHash,String conversionVersion,int utilityScale,String roundingMode,
        long divisor,@Schema(format="int64") long totalScaledUtility,int transactionCount,int distinctItems,int inputBytes,
        SnapshotView snapshot,Instant createdAt,
        @Schema(types={"string","null"},allowableValues={"LOSSLESS","WHOLE_UNIT_HALF_UP"},description="SPMF input mode (DEC-20261119-05); null only for an unknown stored version") String conversionMode,
        @Schema(types={"number","null"},description="Rounding unit in đồng of the SPMF input: 1 for WHOLE_UNIT_HALF_UP, null for LOSSLESS (exact scale 6)") BigDecimal roundingUnit) {
        public RequestView(long requestId,int storeId,int requestedBy,UUID requestKey,int snapshotId,List<MiningEngine.Algorithm> algorithms,MinUtilMode minUtilMode,BigDecimal minUtilValue,
            int minUtilSpmf,BigDecimal effectiveMinUtility,String status,String comparisonStatus,String snapshotHash,String inputHash,String conversionVersion,int utilityScale,String roundingMode,
            long divisor,long totalScaledUtility,int transactionCount,int distinctItems,int inputBytes,SnapshotView snapshot,Instant createdAt){
            this(requestId,storeId,requestedBy,requestKey,snapshotId,algorithms,minUtilMode,minUtilValue,minUtilSpmf,effectiveMinUtility,status,comparisonStatus,snapshotHash,inputHash,
                conversionVersion,utilityScale,roundingMode,divisor,totalScaledUtility,transactionCount,distinctItems,inputBytes,snapshot,createdAt,
                MiningInputBuilder.modeFor(conversionVersion),MiningInputBuilder.roundingUnitFor(conversionVersion));
        }
    }
    public record RunView(long runId,long requestId,int storeId,int snapshotId,MiningEngine.Algorithm algorithm,
        int attemptNo,String libraryVersion,@Schema(allowableValues={"PENDING","RUNNING","COMPLETED","FAILED","TIMED_OUT"}) String status,int minUtilSpmf,String inputHash,
        String conversionVersion,@Schema(types={"string","null"},format="date-time") Instant startedAt,@Schema(types={"string","null"},format="date-time") Instant completedAt,@Schema(types={"integer","null"},format="int64") Long runtimeMillis,
        @Schema(types={"number","null"},format="double") Double memoryMiB,String memoryMethod,String runtimeMethod,@Schema(types={"string","null"}) String environment,
        @Schema(types={"integer","null"},format="int32") Integer patternCount,@Schema(types={"string","null"}) String failureCode,@Schema(types={"string","null"}) String failureMessage,
        @Schema(types={"string","null"},allowableValues={"LOSSLESS","WHOLE_UNIT_HALF_UP"},description="SPMF input mode derived from conversionVersion (DEC-20261119-05)") String conversionMode,
        @Schema(types={"number","null"},description="Rounding unit in đồng: 1 for WHOLE_UNIT_HALF_UP, null for LOSSLESS") BigDecimal roundingUnit) {
        public RunView(long runId,long requestId,int storeId,int snapshotId,MiningEngine.Algorithm algorithm,
        int attemptNo,String libraryVersion,String status,int minUtilSpmf,String inputHash,
        String conversionVersion,Instant startedAt,Instant completedAt,Long runtimeMillis,
        Double memoryMiB,String memoryMethod,String runtimeMethod,String environment,
        Integer patternCount,String failureCode,String failureMessage){
            this(runId,requestId,storeId,snapshotId,algorithm,attemptNo,libraryVersion,status,minUtilSpmf,inputHash,conversionVersion,startedAt,completedAt,runtimeMillis,memoryMiB,memoryMethod,runtimeMethod,environment,patternCount,failureCode,failureMessage,
                MiningInputBuilder.modeFor(conversionVersion),MiningInputBuilder.roundingUnitFor(conversionVersion));
        }
    }
    public record PatternView(long patternId,long runId,List<Integer> productIds,BigDecimal utility,
        int utilitySpmf,int patternSize,@Schema(types={"integer","null"},format="int32",description="Not measured by SPMF; always null") Integer supportCount) {}
}

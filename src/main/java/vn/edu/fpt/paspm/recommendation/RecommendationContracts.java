package vn.edu.fpt.paspm.recommendation;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import vn.edu.fpt.paspm.common.StrictIntegerDeserializer;

public final class RecommendationContracts {
    private RecommendationContracts() {}
    public enum RecType { BUNDLE, CROSS_SELL, FBT, BANNER }
    public enum ReviewStatus { PENDING_REVIEW, APPROVED, REJECTED }
    public enum EligibilityStatus { ELIGIBLE, NEEDS_RECHECK, INELIGIBLE }
    @Schema(additionalProperties=Schema.AdditionalPropertiesValue.FALSE)
    public record RecGenerateRequest(@NotNull @Positive @JsonDeserialize(using=StrictLong.class) Long miningRunId,
        @NotEmpty @Size(max=4) List<@NotNull RecType> types) {
        @JsonAnySetter public void unknown(String name,Object value){throw new IllegalArgumentException("Unknown field");}
    }
    @Schema(additionalProperties=Schema.AdditionalPropertiesValue.FALSE)
    public record RecReviewRequest(@NotNull @Positive @JsonDeserialize(using=StrictLong.class) Long expectedVersion,
        @NotBlank @Size(max=1000) String reason) {
        @JsonAnySetter public void unknown(String name,Object value){throw new IllegalArgumentException("Unknown field");}
    }
    @Schema(additionalProperties=Schema.AdditionalPropertiesValue.FALSE)
    public record RecPolicyRequest(@NotNull @Positive @JsonDeserialize(using=StrictLong.class) Long expectedVersion,
        @NotNull @Min(2) @Max(256) @JsonDeserialize(using=StrictIntegerDeserializer.class) Integer maxBundleSize,
        @NotNull @Min(1) @JsonDeserialize(using=StrictIntegerDeserializer.class) Integer minSupportCount,
        @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer=1,fraction=6) BigDecimal minSupportRatio,
        @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer=1,fraction=6) BigDecimal minConfidence,
        @NotNull @Min(1) @JsonDeserialize(using=StrictIntegerDeserializer.class) Integer minimumSellableQuantity) {
        @JsonAnySetter public void unknown(String name,Object value){throw new IllegalArgumentException("Unknown field");}
    }
    public record RecPolicy(long version,int maxBundleSize,int minSupportCount,BigDecimal minSupportRatio,
        BigDecimal minConfidence,int minimumSellableQuantity,String ruleVersion) {}
    public record RecProductEligibility(int productId,String productStatus,long sellableQuantity,EligibilityStatus status,
        @Schema(types={"number","null"}) BigDecimal expiryPriorityScore,String expiryStatus,int expiryPushDays,List<String> warnings) {}
    public record RecEligibility(EligibilityStatus status,List<RecProductEligibility> products,Instant checkedAt,
        LocalDate asOfDate,String timeZone,List<String> warnings,String fingerprint) {}
    public record RecSource(long runId,long patternId,long requestId,int snapshotId,String algorithm,String libraryVersion,
        String conversionVersion,String inputHash,String sourceHash,String matrixHash,String minUtilMode,BigDecimal minUtilValue,
        int minUtilSpmf,BigDecimal effectiveMinUtility,BigDecimal patternUtility,LocalDate analysisDate) {}
    public record RecSkipped(long patternId,RecType type,@Schema(types={"integer","null"}) Integer anchorProductId,String reason) {}
    public record RecGenerationResult(long miningRunId,int snapshotId,long policyVersion,String ruleVersion,int createdCount,
        int reusedCount,List<Long> recommendationIds,List<RecSkipped> skipped) {}
    public record RecReview(ReviewStatus oldStatus,ReviewStatus newStatus,int reviewedBy,Instant reviewedAt,String reason,
        long resultingVersion,RecEligibility eligibilityCheck) {}
    public record RecView(long recommendationId,int storeId,int snapshotId,RecType type,List<Integer> productIds,
        @Schema(types={"integer","null"}) Integer anchorProductId,BigDecimal utility,ReviewStatus reviewStatus,long version,
        String ruleVersion,RecPolicy policy,TransactionEvidence.Evidence evidence,RecEligibility generationEligibility,
        RecEligibility currentEligibility,boolean inventoryChanged,boolean readyForConsumption,List<RecSource> sources,
        List<String> explanation,List<RecReview> reviewHistory,@Schema(types={"integer","null"}) Integer reviewedBy,
        @Schema(types={"string","null"},format="date-time") Instant reviewedAt,@Schema(types={"string","null"}) String reviewReason,
        Instant createdAt,@Schema(types={"integer","null"}) Integer rank) {}
    public record RecPage(List<RecView> items,int page,int pageSize,long totalElements,String ordering) {}
    public static class StrictLong extends JsonDeserializer<Long> {
        @Override public Long deserialize(JsonParser parser,DeserializationContext context)throws IOException {
            if(!parser.hasToken(JsonToken.VALUE_NUMBER_INT))return (Long)context.handleUnexpectedToken(Long.class,parser);
            return parser.getLongValue();
        }
    }
}

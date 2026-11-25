package vn.edu.fpt.paspm.utility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Stored JSON models. Legacy records are retained; no read rewrites stored documents. */
public final class UtilityVersionedContracts {
    private UtilityVersionedContracts() {}
    public static final String V2="W2_EXPIRY_SEASON_V1";
    private static void requireV2(String version) { if(!V2.equals(version))throw new IllegalArgumentException("Unsupported scoringVersion"); }
    public interface WeightSettings { BigDecimal lambda(); long version(); String scoringVersion(); }
    public record WeightV2(BigDecimal alpha,BigDecimal beta,BigDecimal lambda,int salesWindowDays,
        long version,String scoringVersion) implements WeightSettings {
        public WeightV2 { requireV2(scoringVersion); }
        /** A Store that never saved a configuration (same values as the V25 conversion of a missing config, version 1). */
        public static WeightV2 defaults() {
            return new WeightV2(new BigDecimal("0.5"),new BigDecimal("0.5"),BigDecimal.ZERO,30,1,V2);
        }
    }
    public interface PolicyPayload {
        BigDecimal lambdaMax();
        default BigDecimal lambdaMin() { return BigDecimal.ZERO; }
    }
    public record PolicyPayloadV2(String scoringVersion,BigDecimal lambdaMax) implements PolicyPayload { public PolicyPayloadV2 { requireV2(scoringVersion); } }
    public interface PolicyValues extends PolicyPayload { long version(); }
    public record LambdaV2(String scoringVersion,BigDecimal lambdaMax,long version) implements PolicyValues { public LambdaV2 { requireV2(scoringVersion); } }
    public interface CoefficientValues { BigDecimal alpha(); BigDecimal beta(); }
    public record CoefficientsV2(BigDecimal alpha,BigDecimal beta) implements CoefficientValues {}
    public interface FrozenSettings {
        WeightSettings config(); PolicyValues lambdaPolicy(); CoefficientValues effectiveCoefficients();
        LocalDate analysisDate(); String source(); int utilityScale(); String roundingMode();
        String itemMapping(); String timeZone();
    }
    public record SeasonCopy(int seasonId,String name,LocalDate startDate,LocalDate endDate,
        BigDecimal weight,long version,List<Integer> productIds) {}
    /** weightScale/weightRoundingMode freeze the Q9 rule: W(i) is rounded half-up to 2 decimals before it is applied. */
    public record FrozenV2(WeightV2 config,LambdaV2 lambdaPolicy,CoefficientsV2 effectiveCoefficients,
        LocalDate analysisDate,String source,int utilityScale,String roundingMode,int weightScale,String weightRoundingMode,
        String itemMapping,String timeZone,List<SeasonCopy> seasons) implements FrozenSettings {}
    public interface ExpiryEvidence { LocalDate asOfDate(); String reason(); }
    public record RiskBatch(int batchId,LocalDate pushDay,LocalDate expiryDate,long remainingQuantity,
        Long daysToPush,BigDecimal expectedSoldQuantity,BigDecimal riskQuantity,boolean eligible,List<String> warnings) {}
    public record FefoEvidence(String scoringVersion,LocalDate asOfDate,String reason,int salesWindowDays,
        long unitsSold,BigDecimal dailySalesRate,BigDecimal riskQuantity,long eligibleQuantity,
        BigDecimal score,List<RiskBatch> batches,List<String> warnings) implements ExpiryEvidence { public FefoEvidence { requireV2(scoringVersion); } }
}

package vn.edu.fpt.paspm.utility;

import java.math.BigDecimal;
import vn.edu.fpt.paspm.common.StrictIntegerDeserializer;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.*;

public final class UtilityContracts {
    private UtilityContracts() {}
    public record LambdaPolicy(BigDecimal lambdaMin, BigDecimal lambdaMax, long version) {}
    public record PolicyRequest(
        @io.swagger.v3.oas.annotations.media.Schema(example="0") @NotNull @DecimalMin("0") @Digits(integer=24,fraction=6) BigDecimal lambdaMin,
        @io.swagger.v3.oas.annotations.media.Schema(example="0.5") @NotNull @DecimalMin("0") @Digits(integer=24,fraction=6) BigDecimal lambdaMax) {}
    public record WeightRequest(
        @io.swagger.v3.oas.annotations.media.Schema(example="true") @NotNull Boolean enableStock, @io.swagger.v3.oas.annotations.media.Schema(example="true") @NotNull Boolean enableExpiry, @io.swagger.v3.oas.annotations.media.Schema(example="true") @NotNull Boolean enableSeason,
        @io.swagger.v3.oas.annotations.media.Schema(example="0.5") @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer=1,fraction=6) BigDecimal alpha,
        @io.swagger.v3.oas.annotations.media.Schema(example="0.2") @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer=1,fraction=6) BigDecimal beta,
        @io.swagger.v3.oas.annotations.media.Schema(example="0.3") @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer=1,fraction=6) BigDecimal gamma,
        @io.swagger.v3.oas.annotations.media.Schema(example="0.5") @NotNull @DecimalMin("0") @Digits(integer=24,fraction=6) BigDecimal lambda) {}
    public record ExpiryWindowRequest(
        @io.swagger.v3.oas.annotations.media.Schema(example="30",minimum="1")
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=StrictIntegerDeserializer.class)
        @NotNull @Min(1) Integer expiryWindowDays) {}
    public record WeightConfig(boolean enableStock, boolean enableExpiry, boolean enableSeason,
        BigDecimal alpha, BigDecimal beta, BigDecimal gamma, BigDecimal lambda,
        @io.swagger.v3.oas.annotations.media.Schema(deprecated=true,description="Legacy Store setting; ignored by v1.2 scoring. Product.expiryPushDays is authoritative") Integer expiryWindowDays, long version, String scoringVersion) {
        public static WeightConfig defaults() {
            return new WeightConfig(false,false,false,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,
                BigDecimal.ZERO,null,1,ExpiryScoreCalculator.SCORING_VERSION);
        }
        public BigDecimal effectiveAlpha() { return enableStock ? alpha : BigDecimal.ZERO; }
        public BigDecimal effectiveBeta() { return enableExpiry ? beta : BigDecimal.ZERO; }
        public BigDecimal effectiveGamma() { return enableSeason ? gamma : BigDecimal.ZERO; }
    }
    public record ConfigView(WeightConfig config, LambdaPolicy lambdaPolicy, boolean lambdaValid) {}
    public static final class ScorePatch {
        @DecimalMin("0") @DecimalMax("1") @Digits(integer=1,fraction=6)
        private BigDecimal stockPriorityScore;
        @DecimalMin("0") @DecimalMax("1") @Digits(integer=1,fraction=6)
        private BigDecimal seasonalPriorityScore;
        public BigDecimal getStockPriorityScore() { return stockPriorityScore; }
        public BigDecimal getSeasonalPriorityScore() { return seasonalPriorityScore; }
        @JsonSetter(nulls=Nulls.FAIL)
        public void setStockPriorityScore(BigDecimal value) { stockPriorityScore=value; }
        @JsonSetter(nulls=Nulls.FAIL)
        public void setSeasonalPriorityScore(BigDecimal value) { seasonalPriorityScore=value; }
    }
    public record Scores(int productId, BigDecimal stockPriorityScore, BigDecimal seasonalPriorityScore) {}
}

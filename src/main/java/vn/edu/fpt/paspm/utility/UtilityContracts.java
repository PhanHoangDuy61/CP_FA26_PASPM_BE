package vn.edu.fpt.paspm.utility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import vn.edu.fpt.paspm.common.StrictIntegerDeserializer;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;

import vn.edu.fpt.paspm.utility.UtilityVersionedContracts.*;

public final class UtilityContracts {
    private UtilityContracts() {}

    // ---- Legacy stored shapes (F03_LINEAR_V1 / EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1). Read-only: frozen JSON only.
    public record LambdaPolicy(BigDecimal lambdaMin, BigDecimal lambdaMax, long version) implements PolicyValues {}
    /** Legacy stored policy payload {lambdaMin,lambdaMax}; no longer accepted by the API. */
    public record PolicyRequest(BigDecimal lambdaMin, BigDecimal lambdaMax) implements PolicyPayload {}
    public record WeightConfig(boolean enableStock, boolean enableExpiry, boolean enableSeason,
        BigDecimal alpha, BigDecimal beta, BigDecimal gamma, BigDecimal lambda,
        @Schema(deprecated=true,description="Legacy Store setting; ignored since v1.2") Integer expiryWindowDays, long version, String scoringVersion) implements WeightSettings {
        public static WeightConfig defaults() {
            return new WeightConfig(false,false,false,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,
                BigDecimal.ZERO,null,1,ExpiryScoreCalculator.SCORING_VERSION);
        }
        public BigDecimal effectiveAlpha() { return enableStock ? alpha : BigDecimal.ZERO; }
        public BigDecimal effectiveBeta() { return enableExpiry ? beta : BigDecimal.ZERO; }
        public BigDecimal effectiveGamma() { return enableSeason ? gamma : BigDecimal.ZERO; }
    }

    // ---- W2_EXPIRY_SEASON_V1 API.
    /** Q3: the System Admin sets only the upper bound; the lower bound is fixed at 0. */
    public record PolicyUpdate(
        @Schema(example="0.5") @NotNull @DecimalMin("0") @Digits(integer=24,fraction=6) BigDecimal lambdaMax) {}
    /** Q3/Q6: the Merchandiser sets alpha (expiry) and beta (season), alpha + beta = 1, lambda in [0, lambdaMax] and N. */
    public record WeightUpdate(
        @Schema(example="0.6") @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer=1,fraction=6) BigDecimal alpha,
        @Schema(example="0.4") @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer=1,fraction=6) BigDecimal beta,
        @Schema(example="0.3") @NotNull @DecimalMin("0") @Digits(integer=24,fraction=6) BigDecimal lambda,
        @Schema(example="30",minimum="1",maximum="365",types={"integer","null"},description="Sales window N in days for v(i); omitted keeps the current value")
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=StrictIntegerDeserializer.class)
        @Min(1) @Max(365) Integer salesWindowDays) {}
    public record ConfigView(WeightV2 config, LambdaV2 lambdaPolicy, boolean lambdaValid) {}
    public record Scores(
        int productId,
        @Schema(description="Current U_expiry preview in [0,1] (scale 18)") BigDecimal expiryRisk,
        @Schema(description="Current M(i): weight of the highest active assigned season, else 0") BigDecimal seasonWeight,
        @Schema(types={"integer","null"},description="Season that supplies M(i); null when M(i)=0 from no season") Integer seasonId,
        @Schema(types={"number","null"},description="Current Backend Weight preview (2 decimals), not a frozen historical Weight. Null when ready=false") BigDecimal weightValue,
        String expiryStatus,
        FefoEvidence expiryEvidence,
        @Schema(description="ACTIVE batches with stock and no push day; excluded from Q_risk and Q_eligible") List<Integer> batchesMissingPushDay,
        @Schema(description="Seasons active on asOfDate that include this product") List<SeasonCopy> activeSeasons,
        LocalDate asOfDate,
        String timeZone,
        long configVersion,
        String scoringVersion,
        boolean lambdaValid,
        boolean ready,
        List<String> warnings) {}
}

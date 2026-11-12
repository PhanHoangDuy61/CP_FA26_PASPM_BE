package vn.edu.fpt.paspm.layout;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import vn.edu.fpt.paspm.common.StrictIntegerDeserializer;
import vn.edu.fpt.paspm.recommendation.RecommendationContracts.RecType;

public final class LayoutContracts {
    private LayoutContracts() {}

    public enum LayoutRole { A, B }
    public enum LayoutStatus { DRAFT, READY, ARCHIVED }
    public enum CampaignStatus { DRAFT, PUBLISHED, ACTIVE, ENDED }
    public enum CampaignDecision { KEEP_A, CHOOSE_B }

    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public record CreateCampaignRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull @Positive @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer snapshotId,
            @NotNull @Size(max = 100) List<@NotNull @Positive Long> recommendationIds) {}

    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public record SavePlacementsRequest(
            @NotNull @Positive Long expectedVersion,
            @NotNull @Size(max = 100) List<@NotNull @Positive Long> recommendationIds) {}

    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public record TransitionRequest(@NotNull @Positive Long expectedVersion) {}

    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public record DecisionRequest(
            @NotNull @Positive Long expectedVersion,
            @NotNull CampaignDecision decision,
            @Positive Long reportId) {}

    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public record SimulationRequest(
            @NotNull @Positive Long expectedCampaignVersion,
            @NotNull @Positive Long expectedLayoutAVersion,
            @NotNull @Positive Long expectedLayoutBVersion,
            @NotNull LocalDate rangeStart,
            @NotNull LocalDate rangeEnd) {}

    public record Page<T>(List<T> items, int page, int pageSize, long totalElements) {}

    public record LayoutSummary(long layoutId, long campaignId, int snapshotId, LayoutRole role,
            LayoutStatus status, long version, String name, Instant createdAt, Instant updatedAt) {}

    public record PlacementView(Integer productId, Long recommendationId, RecType recommendationType,
            String sku, String productName, String zone, int blockNo, int position,
            BigDecimal snapshotWeightedUtility, boolean valid, List<String> validationErrors) {}

    public record LayoutView(long layoutId, long campaignId, int snapshotId, LayoutRole role,
            LayoutStatus status, long version, String name, BigDecimal weightedUtilityScore,
            boolean valid, List<String> validationErrors, List<PlacementView> placements,
            Instant createdAt, Instant updatedAt) {}

    public record RecommendationOption(long recommendationId, int snapshotId, RecType type,
            List<Integer> productIds, BigDecimal utility, long version) {}

    public record ZonePolicy(String zone, RecType recommendationType, Integer maxBlocks, Integer maxProducts) {}

    public record LayoutPolicyView(String utilityScoreLabel, String utilityScoreDefinition,
            String capacityPolicy, List<ZonePolicy> zones) {}

    public record CampaignSummary(long campaignId, int snapshotId, String name, CampaignStatus status,
            long version, Long layoutAId, Long layoutBId, Long selectedLayoutId,
            CampaignDecision decision, Long decisionReportId, Instant createdAt, Instant publishedAt, Instant activatedAt,
            Instant endedAt) {}

    public record CampaignView(long campaignId, int snapshotId, String name, CampaignStatus status,
            long version, long layoutAId, long layoutBId, Long selectedLayoutId,
            CampaignDecision decision, Long decisionReportId, Instant createdAt, Instant publishedAt, Instant activatedAt,
            Instant endedAt, List<Long> reportIds) {}

    public record MetricView(int numerator, int denominator, BigDecimal rate) {}

    public record UtilityMetricView(BigDecimal numerator, BigDecimal denominator, BigDecimal rate) {}

    public record ReportLayoutView(long layoutId, long layoutVersion, LayoutRole role,
            MetricView hitRate, MetricView bundleConversion, UtilityMetricView capturedUtility,
            BigDecimal priorityCoverage, Integer priorityCoverageDenominator) {}

    public record CampaignReportView(long reportId, long campaignId, int snapshotId, int createdBy,
            LocalDate rangeStart, LocalDate rangeEnd, String sourceNamespace, String datasetHash,
            int evaluationCount, boolean simulated, String algorithmVersion, Object assumptions,
            List<ReportLayoutView> layouts, Instant createdAt) {}

    public record ActiveLayoutView(boolean defaultLayout, CampaignSummary campaign, LayoutView layout) {}

    public record PlacementBlock(@NotNull @Positive Long recommendationId,
            @NotNull RecType type, @NotEmpty List<@NotNull @Positive Integer> productIds,
            @NotNull String zone, @Positive int blockNo, @Positive int startPosition) {}
}

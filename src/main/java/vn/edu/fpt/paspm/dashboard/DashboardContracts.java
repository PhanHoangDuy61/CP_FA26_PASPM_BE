package vn.edu.fpt.paspm.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DashboardContracts {
    private DashboardContracts() {}

    public record SnapshotUtility(int snapshotId, LocalDate rangeStart, LocalDate rangeEnd, LocalDate analysisDate,
            Instant createdAt, BigDecimal totalWeightedUtility) {}

    public record SnapshotUtilityPage(List<SnapshotUtility> items, int page, int pageSize, long totalElements) {}

    public record BundleOriginCounts(@JsonProperty("MINED") long mined, @JsonProperty("MANUAL") long manual) {}

    public record BundleAdditionDay(LocalDate date, BundleOriginCounts addActionsByOrigin, long addedProductUnits) {}

    public record BundleAdditions(LocalDate from, LocalDate to, String timeZone,
            @Schema(types = {"integer", "null"}, format = "int64") Long campaignId,
            long totalAddActions, BundleOriginCounts addActionsByOrigin, long totalAddedProductUnits,
            List<BundleAdditionDay> days) {}
}

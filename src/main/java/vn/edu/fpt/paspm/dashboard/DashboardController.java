package vn.edu.fpt.paspm.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.fpt.paspm.dashboard.DashboardContracts.BundleAdditions;
import vn.edu.fpt.paspm.dashboard.DashboardContracts.SnapshotUtilityPage;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('STORE_ADMIN','MERCHANDISER')")
@Tag(name = "F11 Retail Performance Dashboard")
public class DashboardController {
    private static final String LOCAL_DATE = "[0-9]{4}-[0-9]{2}-[0-9]{2}";

    private final DashboardService dashboard;

    DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/snapshot-utilities")
    @Operation(operationId = "listDashboardSnapshotUtilities", summary = "List frozen own-Store READY snapshot totals")
    public SnapshotUtilityPage snapshotUtilities(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) @Positive Integer snapshotId) {
        return dashboard.snapshotUtilities(jwt, page, pageSize, snapshotId);
    }

    @GetMapping("/bundle-additions")
    @Operation(operationId = "getDashboardBundleAdditions", summary = "Read successful bundle additions by Store-local day",
            description = "Supply both dates for an inclusive window of at most 90 days. Omit both for the last 30 days including today.")
    public BundleAdditions bundleAdditions(@AuthenticationPrincipal Jwt jwt,
            @Parameter(schema = @Schema(type = "string", format = "date"))
            @RequestParam(required = false) @Pattern(regexp = LOCAL_DATE) String from,
            @Parameter(schema = @Schema(type = "string", format = "date"))
            @RequestParam(required = false) @Pattern(regexp = LOCAL_DATE) String to,
            @RequestParam(required = false) @Positive Long campaignId) {
        return dashboard.bundleAdditions(jwt, from, to, campaignId);
    }
}

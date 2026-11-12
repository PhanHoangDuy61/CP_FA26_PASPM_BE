package vn.edu.fpt.paspm.layout;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static vn.edu.fpt.paspm.layout.LayoutContracts.*;

@RestController
@PreAuthorize("hasAnyRole('MERCHANDISER','STORE_ADMIN')")
@Tag(name = "F07 Layouts and F08 Campaigns",
        description = "Store-scoped layout editing, offline simulated reports, and campaign lifecycle")
public class LayoutCampaignController {
    private final LayoutCampaignService service;

    public LayoutCampaignController(LayoutCampaignService service) {
        this.service = service;
    }

    @GetMapping("/api/layouts")
    @Operation(summary = "List layouts in the authenticated Store")
    public Page<LayoutSummary> layouts(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Positive Long campaignId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return service.layouts(jwt, campaignId, page, pageSize);
    }

    @GetMapping("/api/layouts/policy")
    @Operation(summary = "Read the shared layout zones and configured capacities used by F07 and F09")
    public LayoutPolicyView policy() {
        return service.policy();
    }

    @GetMapping("/api/layouts/recommendations")
    @Operation(summary = "List same-snapshot APPROVED recommendations currently ready for B")
    public Page<RecommendationOption> recommendations(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @Positive int snapshotId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return service.recommendationOptions(jwt, snapshotId, page, pageSize);
    }

    @GetMapping("/api/layouts/{id}")
    @Operation(summary = "Read an own-Store layout and its current snapshot utility score")
    public LayoutView layout(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id) {
        return service.layout(jwt, id);
    }

    @GetMapping("/api/layouts/{id}/preview")
    @Operation(summary = "Preview placements, current recommendation validity, and snapshot utility")
    public LayoutView preview(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id) {
        return service.preview(jwt, id);
    }

    @PutMapping("/api/layouts/{id}/placements")
    @PreAuthorize("hasRole('MERCHANDISER')")
    @Operation(summary = "Replace B placements as whole recommendation blocks using expectedVersion")
    public LayoutView placements(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @Valid @RequestBody SavePlacementsRequest request) {
        return service.savePlacements(jwt, id, request);
    }

    @PostMapping("/api/layouts/{id}/ready")
    @PreAuthorize("hasRole('MERCHANDISER')")
    @Operation(summary = "Recheck recommendation eligibility and move layout B to READY")
    public LayoutView ready(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @Valid @RequestBody TransitionRequest request) {
        return service.readyLayout(jwt, id, request);
    }

    @GetMapping("/api/campaigns")
    @Operation(summary = "List campaigns in the authenticated Store")
    public Page<CampaignView> campaigns(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return service.campaigns(jwt, page, pageSize);
    }

    @PostMapping("/api/campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MERCHANDISER')")
    @Operation(summary = "Create a DRAFT campaign with immutable baseline A and editable recommendation layout B")
    public CampaignView createCampaign(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCampaignRequest request) {
        return service.createCampaign(jwt, request);
    }

    @GetMapping("/api/campaigns/{id}")
    @Operation(summary = "Read an own-Store campaign and its lifecycle/version")
    public CampaignView campaign(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id) {
        return service.campaign(jwt, id);
    }

    @PutMapping("/api/campaigns/{id}/decision")
    @PreAuthorize("hasRole('MERCHANDISER')")
    @Operation(summary = "Record KEEP_A or CHOOSE_B; an optional report must match both current layout versions")
    public CampaignView decision(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @Valid @RequestBody DecisionRequest request) {
        return service.decide(jwt, id, request);
    }

    @PostMapping("/api/campaigns/{id}/simulations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MERCHANDISER')")
    @Operation(summary = "Run a synchronous offline replay and persist an immutable simulated report",
            description = "Inclusive Store-local evaluation dates must begin strictly after the selected utility snapshot rangeEnd; overlapping or earlier dates return 400.")
    public CampaignReportView simulate(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @Valid @RequestBody SimulationRequest request) {
        return service.simulate(jwt, id, request);
    }

    @GetMapping("/api/campaigns/{id}/reports")
    @Operation(summary = "List immutable simulated reports for an own-Store campaign")
    public Page<CampaignReportView> reports(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return service.reports(jwt, id, page, pageSize);
    }

    @GetMapping("/api/campaign-reports/{reportId}")
    @Operation(summary = "Read one immutable own-Store simulation report")
    public CampaignReportView report(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long reportId) {
        return service.report(jwt, reportId);
    }

    @PostMapping("/api/campaigns/{id}/publish")
    @PreAuthorize("hasRole('MERCHANDISER')")
    @Operation(summary = "Publish the selected valid layout without changing the active storefront")
    public CampaignView publish(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @Valid @RequestBody TransitionRequest request) {
        return service.publish(jwt, id, request);
    }

    @PostMapping("/api/campaigns/{id}/withdraw")
    @PreAuthorize("hasRole('MERCHANDISER')")
    @Operation(summary = "Withdraw a published campaign back to DRAFT for further editing")
    public CampaignView withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @Valid @RequestBody TransitionRequest request) {
        return service.withdraw(jwt, id, request);
    }

    @PostMapping("/api/campaigns/{id}/activate")
    @PreAuthorize("hasRole('STORE_ADMIN')")
    @Operation(summary = "Recheck current eligibility and activate a published campaign")
    public CampaignView activate(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @Valid @RequestBody TransitionRequest request) {
        return service.activate(jwt, id, request);
    }

    @PostMapping("/api/campaigns/{id}/end")
    @PreAuthorize("hasRole('STORE_ADMIN')")
    @Operation(summary = "End the active campaign so F09 returns the default layout")
    public CampaignView end(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @Valid @RequestBody TransitionRequest request) {
        return service.end(jwt, id, request);
    }

    @PostMapping("/api/campaigns/{id}/rollback")
    @PreAuthorize("hasRole('STORE_ADMIN')")
    @Operation(summary = "Rollback the active campaign to the previously active campaign in history")
    public ActiveLayoutView rollback(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive long id,
            @Valid @RequestBody TransitionRequest request) {
        return service.rollback(jwt, id, request);
    }

    @GetMapping("/api/storefront/active-layout")
    @Operation(summary = "F09 read contract: active selected layout or default-layout indicator")
    public ActiveLayoutView activeLayout(@AuthenticationPrincipal Jwt jwt) {
        return service.activeLayout(jwt);
    }
}

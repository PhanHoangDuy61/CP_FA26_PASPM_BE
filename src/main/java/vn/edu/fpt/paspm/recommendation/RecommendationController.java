package vn.edu.fpt.paspm.recommendation;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static vn.edu.fpt.paspm.recommendation.RecommendationContracts.*;

@RestController
@RequestMapping("/api/recommendations")
@Tag(name="F06 Recommendations",description="Store-scoped reviewable candidates; independent live expiry alerts. Approval never publishes or reserves inventory.")
@PreAuthorize("hasAnyRole('MERCHANDISER','STORE_ADMIN')")
public class RecommendationController {
    private final RecommendationService service;private final RecommendationReviewService reviews;private final RecommendationPolicyService policies;
    public RecommendationController(RecommendationService service,RecommendationReviewService reviews,RecommendationPolicyService policies){this.service=service;this.reviews=reviews;this.policies=policies;}
    @PostMapping("/generate") @PreAuthorize("hasRole('MERCHANDISER')")
    @Operation(summary="Generate idempotent review candidates from a completed own-Store mining run",description="Frozen evidence, versioned Store policy and current inventory; equivalent algorithm sources are preserved. No SPMF execution or automatic approval.")
    public RecGenerationResult generate(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody RecGenerateRequest request){return service.generate(jwt,request);}
    @GetMapping @Operation(summary="List candidates; miningRunId scopes deterministic live ranking",description="Without miningRunId, chronological history; raw utility is never ranked across unrelated snapshots. Filters: snapshotId, type, reviewStatus. Rank evaluated before pagination.")
    public RecPage list(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) Long miningRunId,@RequestParam(required=false) Integer snapshotId,@RequestParam(required=false) RecType type,@RequestParam(required=false) ReviewStatus reviewStatus,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int pageSize){return service.list(jwt,miningRunId,snapshotId,type,reviewStatus,page,pageSize);}
    @GetMapping("/{id}") @Operation(summary="Get immutable evidence, review history and rechecked live eligibility",description="Downstream must require reviewStatus=APPROVED and readyForConsumption=true at time of use. Inventory is not reserved; recheck again when actually activating/fulfilling.")
    public RecView get(@AuthenticationPrincipal Jwt jwt,@PathVariable long id){return service.get(jwt,id);}
    @PostMapping("/{id}/approve") @PreAuthorize("hasRole('MERCHANDISER')") @Operation(summary="Approve a pending eligible candidate with optimistic version check")
    public RecView approve(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody RecReviewRequest request){return reviews.review(jwt,id,request,ReviewStatus.APPROVED);}
    @PostMapping("/{id}/reject") @PreAuthorize("hasRole('MERCHANDISER')") @Operation(summary="Reject a pending candidate and preserve auditable decision history")
    public RecView reject(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody RecReviewRequest request){return reviews.review(jwt,id,request,ReviewStatus.REJECTED);}
    @GetMapping("/config") @Operation(summary="Read effective own-Store versioned rule policy and approved defaults")
    public RecPolicy config(@AuthenticationPrincipal Jwt jwt){return policies.get(jwt);}
    @PutMapping("/config") @PreAuthorize("hasRole('STORE_ADMIN')") @Operation(summary="Store Admin updates own-Store policy with expectedVersion",description="Does not rewrite existing recommendation policy/evidence/review history. Defaults version1; first update produces version2.")
    public RecPolicy config(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody RecPolicyRequest request){return policies.update(jwt,request);}
}

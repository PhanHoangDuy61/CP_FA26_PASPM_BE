package vn.edu.fpt.paspm.recommendation;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.edu.fpt.paspm.common.ApiException;

@RestController
@PreAuthorize("hasAnyRole('STORE_ADMIN','MERCHANDISER')")
public class RecommendationExpiryController {
    private final RecommendationExpiryService service;
    public RecommendationExpiryController(RecommendationExpiryService service) { this.service=service; }

    @GetMapping("/api/recommendations/expiry-push")
    @Operation(summary="Read current sellable expiry-push batches without Mining",
        description="Uses the current Store-local date only; historical asOfDate is rejected. Ordered by expiry date, productId and batchId. checkedAt records this eligibility check, not an inventory reservation.")
    public RecommendationExpiryService.RecExpiryPage list(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(required=false) Integer productId,@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int pageSize,@RequestParam java.util.Map<String,String> parameters) {
        if(parameters.containsKey("asOfDate"))throw new ApiException(400,"asOfDate is not supported for current expiry recommendations");
        return service.list(jwt,productId,page,pageSize);
    }
}

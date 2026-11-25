package vn.edu.fpt.paspm.utility;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import vn.edu.fpt.paspm.utility.UtilityContracts.*;

@RestController
@PreAuthorize("hasRole('MERCHANDISER')")
@RequestMapping("/api/utility/products/{productId}/scores")
public class ProductScoreController {
    private final ProductScoreService service;
    public ProductScoreController(ProductScoreService service) { this.service=service; }
    @GetMapping @PreAuthorize("hasAnyRole('STORE_ADMIN','MERCHANDISER')")
    @Operation(summary="Read manual S/M and current Backend-calculated E/W in the authenticated Store",
        description="Store Admin and Merchandiser read only their Store. Current preview uses Store-local today, not frozen historical values; unavailable Weight is null with ready=false and warnings.")
    public Scores get(@AuthenticationPrincipal Jwt jwt,@PathVariable int productId) { return service.get(jwt,productId); }
    @PatchMapping @Operation(summary="Merchandiser updates supplied S/M and reads recalculated current E/W",
        description="Only stockPriorityScore/seasonalPriorityScore are accepted. Explicit null, calculated values and other fields are rejected. Inventory and immutable snapshots/mining results are unchanged.")
    public Scores patch(@AuthenticationPrincipal Jwt jwt,@PathVariable int productId,@Valid @RequestBody ScorePatch request) { return service.patch(jwt,productId,request); }
}

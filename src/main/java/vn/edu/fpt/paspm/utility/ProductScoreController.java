package vn.edu.fpt.paspm.utility;

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
    @Operation(summary="Read the current W2 preview of a product in the authenticated Store",
        description="U_expiry (FEFO), M(i) from active seasons, current W (2 decimals) and the batches missing a push day. Store-local today; not a frozen historical value.")
    public Scores get(@AuthenticationPrincipal Jwt jwt,@PathVariable int productId) { return service.get(jwt,productId); }
    @PatchMapping @Deprecated
    @Operation(deprecated=true,summary="Removed: always 410 Gone",description="Manual stock/seasonal scores no longer exist in W2_EXPIRY_SEASON_V1. Use season assignment.")
    public Scores patch(@PathVariable int productId) { return service.removedPatch(); }
}

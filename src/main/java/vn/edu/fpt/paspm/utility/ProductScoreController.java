package vn.edu.fpt.paspm.utility;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import vn.edu.fpt.paspm.utility.UtilityContracts.*;

/** W2 product preview only. The PATCH of manual stock/seasonal scores was deleted on 2026-11-16 (DEC-20261116-03); use season assignment. */
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
}

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
    @GetMapping @Operation(summary="Read manual stock and seasonal scores in the authenticated Store")
    public Scores get(@AuthenticationPrincipal Jwt jwt,@PathVariable int productId) { return service.get(jwt,productId); }
    @PatchMapping @Operation(summary="Update supplied manual scores without editing inventory; explicit null is rejected")
    public Scores patch(@AuthenticationPrincipal Jwt jwt,@PathVariable int productId,@Valid @RequestBody ScorePatch request) { return service.patch(jwt,productId,request); }
}

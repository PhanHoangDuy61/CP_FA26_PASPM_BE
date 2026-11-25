package vn.edu.fpt.paspm.utility;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import vn.edu.fpt.paspm.utility.UtilityContracts.*;
import vn.edu.fpt.paspm.utility.UtilityVersionedContracts.LambdaV2;

@RestController
public class UtilityConfigurationController {
    private final UtilityConfigurationService service;
    public UtilityConfigurationController(UtilityConfigurationService service) { this.service=service; }
    @GetMapping("/api/system/utility/lambda-policy") @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary="Read the versioned system lambda policy",description="W2_EXPIRY_SEASON_V1: only lambdaMax; the lower bound is fixed at 0.")
    public LambdaV2 policy() { return service.policy(); }
    @PutMapping("/api/system/utility/lambda-policy") @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(operationId="updateLambdaPolicy", summary="Change lambdaMax without rewriting Store settings or snapshots",description="Body {lambdaMax}. lambdaMin is no longer accepted (400 as an unknown field).")
    public LambdaV2 policy(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody PolicyUpdate request) { return service.updatePolicy(jwt,request); }
    @GetMapping("/api/utility/config") @PreAuthorize("hasAnyRole('STORE_ADMIN','MERCHANDISER')")
    @Operation(summary="Read Store Weight v2 settings (alpha, beta, lambda, salesWindowDays) and the permitted lambda range")
    public ConfigView config(@AuthenticationPrincipal Jwt jwt) { return service.config(jwt); }
    @PutMapping("/api/utility/config") @PreAuthorize("hasRole('MERCHANDISER')")
    @Operation(operationId="updateUtilityConfig", summary="Merchandiser sets alpha (expiry) + beta (season) = 1, lambda in [0, lambdaMax] and the sales window N",
        description="400 when alpha + beta != 1 or lambda is outside [0, lambdaMax]. Removed fields (enableStock/enableExpiry/enableSeason/gamma) are rejected as unknown.")
    public ConfigView config(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody WeightUpdate request) { return service.weights(jwt,request); }
}

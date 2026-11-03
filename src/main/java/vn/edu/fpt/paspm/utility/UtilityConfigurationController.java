package vn.edu.fpt.paspm.utility;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import vn.edu.fpt.paspm.utility.UtilityContracts.*;

@RestController
public class UtilityConfigurationController {
    private final UtilityConfigurationService service;
    public UtilityConfigurationController(UtilityConfigurationService service) { this.service=service; }
    @GetMapping("/api/system/utility/lambda-policy") @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary="Read the versioned system lambda policy")
    public LambdaPolicy policy() { return service.policy(); }
    @PutMapping("/api/system/utility/lambda-policy") @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary="Change permitted lambda bounds without rewriting Store settings or snapshots")
    public LambdaPolicy policy(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody PolicyRequest request) { return service.updatePolicy(jwt,request); }
    @GetMapping("/api/utility/config") @PreAuthorize("hasAnyRole('STORE_ADMIN','MERCHANDISER')")
    @Operation(summary="Read Store weights and permitted lambda range; legacy expiry window is ignored by new scoring")
    public ConfigView config(@AuthenticationPrincipal Jwt jwt) { return service.config(jwt); }
    @PutMapping("/api/utility/config") @PreAuthorize("hasRole('MERCHANDISER')")
    @Operation(summary="Set Store weights and selected lambda; expiry scoring uses only Product.expiryPushDays")
    public ConfigView config(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody WeightRequest request) { return service.weights(jwt,request); }
    @PutMapping("/api/utility/config/expiry-window") @PreAuthorize("hasRole('STORE_ADMIN')")
    @Deprecated
    @Operation(deprecated=true,summary="Legacy compatibility only: Store expiry window is ignored by v1.2; configure Product.expiryPushDays instead")
    public ConfigView expiry(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ExpiryWindowRequest request) { return service.expiryWindow(jwt,request); }
}

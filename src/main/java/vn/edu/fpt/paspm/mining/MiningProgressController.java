package vn.edu.fpt.paspm.mining;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import vn.edu.fpt.paspm.catalog.CatalogAccess;
import static vn.edu.fpt.paspm.mining.MiningContracts.ProgressView;

@RestController
@Tag(name="F05 Mining")
public class MiningProgressController {
    private final MiningProgressReader progress;
    private final CatalogAccess access;
    private final SystemStoreReadPolicy policy;
    private static final org.slf4j.Logger audit=org.slf4j.LoggerFactory.getLogger("paspm.audit.system-store-read");
    public MiningProgressController(MiningProgressReader progress,CatalogAccess access,SystemStoreReadPolicy policy) {
        this.progress=progress;this.access=access;this.policy=policy;
    }
    @GetMapping("/api/mining/requests/{id}/progress")
    @PreAuthorize("hasAnyRole('MERCHANDISER','STORE_ADMIN')")
    @Operation(summary="Read lightweight mining progress for this Store",description="Percent measures completed work, not success or an ETA. FAILED may be below 100%; a completed comparison mismatch may be 100%. Legacy timing is unavailable.")
    public ProgressView store(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) {
        return progress.get(access.currentStoreId(jwt),id);
    }
    @GetMapping("/api/system/mining/stores/{storeId}/requests/{requestId}/progress")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary="System Admin: read scalar mining progress (REQUESTS read-policy group)")
    public ProgressView system(@AuthenticationPrincipal Jwt jwt,@PathVariable int storeId,@PathVariable long requestId) {
        policy.require(SystemStoreReadPolicy.Route.REQUESTS);
        audit.info("system-admin read route={} storeId={} actor={}",SystemStoreReadPolicy.Route.REQUESTS,storeId,jwt.getSubject());
        return progress.get(storeId,requestId);
    }
}

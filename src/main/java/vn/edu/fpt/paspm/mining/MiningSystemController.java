package vn.edu.fpt.paspm.mining;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.common.StrictIntegerDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@RestController
@RequestMapping("/api/system/mining")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class MiningSystemController {
    private final MiningService mining;
    private final boolean storeReadApproved;
    /**
     * Decision D1 (docs/decisions/PO_DECISION_REGISTER_F05_2026-11-01.md) is PENDING: it is not approved that System Admin may read
     * Store mining results. The five read routes below are therefore refused unless an operator explicitly enables them for integration testing.
     */
    public MiningSystemController(MiningService mining,@Value("${paspm.mining.system-store-read-enabled:false}") boolean storeReadApproved) {
        this.mining=mining; this.storeReadApproved=storeReadApproved;
    }
    private void requireStoreReadApproved() {
        // Checked before any Store lookup so a refused call cannot be used to probe which Store ids exist.
        if (!storeReadApproved) throw new ApiException(403,"System Admin reads of Store mining results are pending Product Owner approval (decision D1)");
    }
    public record ManualRequest(@NotNull @Positive @JsonDeserialize(using=StrictIntegerDeserializer.class) Integer storeId,
        @NotNull @Positive @JsonDeserialize(using=StrictIntegerDeserializer.class) Integer snapshotId,@NotNull UUID requestKey, Boolean compareAlgorithms) {}
    @PostMapping("/requests") @ResponseStatus(HttpStatus.ACCEPTED)
    public MiningContracts.RequestView create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ManualRequest request) {
        return mining.createAdministrative(jwt,request.storeId(),request.snapshotId(),request.requestKey(),Boolean.TRUE.equals(request.compareAlgorithms()));
    }
    @GetMapping("/stores/{storeId}/snapshots") @Operation(summary="PENDING_BUSINESS_APPROVAL (disabled: HTTP 403 unless paspm.mining.system-store-read-enabled=true) - System Admin: list READY Utility Snapshots of an explicitly selected Store",description="Read-only. Same representation as GET /api/mining/snapshots. Unknown Store returns 404.")
    public MiningContracts.Page<vn.edu.fpt.paspm.utility.UtilityMatrixContracts.SnapshotView> snapshots(@PathVariable @Positive int storeId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int pageSize) {
        requireStoreReadApproved();
        return mining.snapshotsForStore(storeId,page,pageSize);
    }
    @GetMapping("/stores/{storeId}/requests") @Operation(summary="PENDING_BUSINESS_APPROVAL (disabled: HTTP 403 unless paspm.mining.system-store-read-enabled=true) - System Admin: list a Store's mining requests, newest first")
    public MiningContracts.Page<MiningContracts.RequestView> requests(@PathVariable @Positive int storeId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int pageSize) {
        requireStoreReadApproved();
        return mining.requestsForStore(storeId,page,pageSize);
    }
    @GetMapping("/stores/{storeId}/requests/{requestId}") @Operation(summary="PENDING_BUSINESS_APPROVAL (disabled: HTTP 403 unless paspm.mining.system-store-read-enabled=true) - System Admin: read one request's status and immutable input provenance",description="404 when the request does not belong to the Store in the path.")
    public MiningContracts.RequestView request(@PathVariable @Positive int storeId,@PathVariable @Positive long requestId) {
        requireStoreReadApproved();
        return mining.requestForStore(storeId,requestId);
    }
    @GetMapping("/stores/{storeId}/requests/{requestId}/runs") @Operation(summary="PENDING_BUSINESS_APPROVAL (disabled: HTTP 403 unless paspm.mining.system-store-read-enabled=true) - System Admin: read the algorithm runs of a request in the selected Store")
    public java.util.List<MiningContracts.RunView> runs(@PathVariable @Positive int storeId,@PathVariable @Positive long requestId) {
        requireStoreReadApproved();
        return mining.runsForStore(storeId,requestId);
    }
    @GetMapping("/stores/{storeId}/runs/{runId}/patterns") @Operation(summary="PENDING_BUSINESS_APPROVAL (disabled: HTTP 403 unless paspm.mining.system-store-read-enabled=true) - System Admin: page raw high-utility patterns of a run in the selected Store",description="Utility descending, canonical Product IDs ascending; empty while pending/failed. 404 when the run does not belong to the Store in the path.")
    public MiningContracts.Page<MiningContracts.PatternView> patterns(@PathVariable @Positive int storeId,@PathVariable @Positive long runId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int pageSize) {
        requireStoreReadApproved();
        return mining.patternsForStore(storeId,runId,page,pageSize);
    }
    @GetMapping("/stores/{storeId}/runs/{runId}/benchmark")
    public MiningContracts.BenchmarkView benchmark(@PathVariable @Positive int storeId,@PathVariable @Positive long runId) {
        return mining.benchmarkForStore(storeId,runId);
    }
}

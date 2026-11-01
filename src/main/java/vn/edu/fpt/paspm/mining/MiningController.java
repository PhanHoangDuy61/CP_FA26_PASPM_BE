package vn.edu.fpt.paspm.mining;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import static vn.edu.fpt.paspm.mining.MiningContracts.*;
import vn.edu.fpt.paspm.utility.UtilityMatrixContracts.SnapshotView;

@RestController
@RequestMapping("/api/mining")
@Tag(name="F05 Mining",description="Frozen F03 snapshot mining results. Store-scoped and read-only for Merchandiser and Store Admin. Submitting mining and reading benchmarks are System Admin only under /api/system/mining.")
@PreAuthorize("hasAnyRole('MERCHANDISER','STORE_ADMIN')")
public class MiningController {
    private final MiningService service;
    public MiningController(MiningService service){this.service=service;}
    @GetMapping("/snapshots") @Operation(summary="List this Store's READY frozen snapshots; numerical eligibility is validated on submission")
    public Page<SnapshotView> snapshots(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int pageSize){return service.snapshots(jwt,page,pageSize);}
    @PostMapping("/requests") @ResponseStatus(HttpStatus.ACCEPTED) @PreAuthorize("denyAll()")
    @Operation(deprecated=true,summary="FORBIDDEN legacy route: always HTTP 403 for every role; do not call. Use POST /api/system/mining/requests",description="Kept mapped only so the 403 is explicit. Mining submission is System Admin only.")
    public RequestView create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CreateRequest request){return service.create(jwt,request);}
    @GetMapping("/requests") @Operation(summary="List this Store's mining requests, newest first")
    public Page<RequestView> requests(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int pageSize){return service.requests(jwt,page,pageSize);}
    @GetMapping("/requests/{id}") @Operation(summary="Get immutable input provenance and current mining request status")
    public RequestView request(@AuthenticationPrincipal Jwt jwt,@PathVariable long id){return service.request(jwt,id);}
    @GetMapping("/requests/{id}/runs") @Operation(summary="Get each persisted algorithm run for a request")
    public List<RunView> runs(@AuthenticationPrincipal Jwt jwt,@PathVariable long id){return service.runs(jwt,id);}
    @GetMapping("/runs/{id}") @Operation(summary="Get run status, measured metrics and sanitized failure information")
    public RunView run(@AuthenticationPrincipal Jwt jwt,@PathVariable long id){return service.run(jwt,id);}
    @GetMapping("/runs/{id}/patterns") @Operation(summary="Page raw high-utility patterns by utility descending, canonical Product IDs ascending",description="Empty while pending/failed; check run status. Utility is frozen weighted utility, not revenue. pageSize controls display only and never changes minUtil. Support is not measured.")
    public Page<PatternView> patterns(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int pageSize){return service.patterns(jwt,id,page,pageSize);}
    @GetMapping("/runs/{id}/benchmark") @PreAuthorize("denyAll()") @Operation(deprecated=true,summary="FORBIDDEN legacy route: always HTTP 403 for every role; do not call. Use GET /api/system/mining/stores/{storeId}/runs/{runId}/benchmark",description="Kept mapped only so the 403 is explicit. Benchmarks are System Admin only.")
    public BenchmarkView benchmark(@AuthenticationPrincipal Jwt jwt,@PathVariable long id){return service.benchmark(jwt,id);}
}

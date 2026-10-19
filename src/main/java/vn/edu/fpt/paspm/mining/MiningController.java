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
@Tag(name="F05 Mining",description="Frozen F03 snapshot mining. Store-scoped; Merchandiser creates, Store Admin and Merchandiser read.")
@PreAuthorize("hasAnyRole('MERCHANDISER','STORE_ADMIN')")
public class MiningController {
    private final MiningService service;
    public MiningController(MiningService service){this.service=service;}
    @GetMapping("/snapshots") @Operation(summary="List this Store's READY frozen snapshots; numerical eligibility is validated on submission")
    public Page<SnapshotView> snapshots(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int pageSize){return service.snapshots(jwt,page,pageSize);}
    @PostMapping("/requests") @ResponseStatus(HttpStatus.ACCEPTED) @PreAuthorize("hasRole('MERCHANDISER')")
    @Operation(summary="Submit durable asynchronous mining; UUID requestKey is idempotent per Store",description="Same key and payload returns existing request. Different payload returns 409. Missing/unverified SPMF returns 503. Input is a READY F03 snapshot; no uploads or current Weight recalculation.")
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
    @GetMapping("/runs/{id}/benchmark") @Operation(summary="Read comparable cold child-JVM wall time and sampled Java heap MiB, plus exact result comparison")
    public BenchmarkView benchmark(@AuthenticationPrincipal Jwt jwt,@PathVariable long id){return service.benchmark(jwt,id);}
}

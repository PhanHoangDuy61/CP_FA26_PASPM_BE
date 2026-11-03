package vn.edu.fpt.paspm.utility;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import vn.edu.fpt.paspm.utility.UtilityMatrixContracts.*;

@RestController
@RequestMapping("/api/utility")
@PreAuthorize("hasRole('MERCHANDISER')")
public class UtilitySnapshotController {
    private final UtilitySnapshotService service;
    public UtilitySnapshotController(UtilitySnapshotService service) { this.service=service; }
    @PostMapping("/preview") @Operation(summary="Preview utility using a fixed analysis date without saving a snapshot")
    public MatrixPage preview(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody PreviewRequest request) { return service.preview(jwt,request); }
    @PostMapping("/snapshots") @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="Atomically create an immutable snapshot; revalidate lambda against current policy")
    public SnapshotView create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody RangeRequest request) { return service.create(jwt,request); }
    @GetMapping("/snapshots/{id}") @Operation(summary="Read frozen snapshot settings without consulting current policy or inventory")
    public SnapshotView get(@AuthenticationPrincipal Jwt jwt,@PathVariable int id) { return service.get(jwt,id); }
    @GetMapping("/snapshots/{id}/matrix") @Operation(summary="Read immutable matrix rows with stable Product-to-mining-item mapping")
    public MatrixPage matrix(@AuthenticationPrincipal Jwt jwt,@PathVariable int id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int pageSize) { return service.matrix(jwt,id,page,pageSize); }
}

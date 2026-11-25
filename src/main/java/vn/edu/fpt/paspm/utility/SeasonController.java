package vn.edu.fpt.paspm.utility;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.edu.fpt.paspm.utility.SeasonContracts.*;

@RestController
@RequestMapping("/api/utility/seasons")
@PreAuthorize("hasRole('MERCHANDISER')")
public class SeasonController {
    private final SeasonService service;
    public SeasonController(SeasonService service) { this.service=service; }
    @GetMapping @PreAuthorize("hasAnyRole('STORE_ADMIN','MERCHANDISER')")
    @Operation(summary="List the authenticated Store's seasons",description="Ordered by startDate, then seasonId. activeToday uses the Store-local date and rule P3 (inclusive dates).")
    public SeasonPage list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int pageSize) { return service.list(jwt,page,pageSize); }
    @GetMapping("/{seasonId}") @PreAuthorize("hasAnyRole('STORE_ADMIN','MERCHANDISER')")
    @Operation(summary="Read one season of the authenticated Store; another Store's season is 404")
    public SeasonView get(@AuthenticationPrincipal Jwt jwt,@PathVariable int seasonId) { return service.get(jwt,seasonId); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId="createSeason", summary="Merchandiser creates a season",description="name unique per Store (case-insensitive, 409); startDate <= endDate (400); weight in [0,1]. expectedVersion must be absent.")
    public SeasonView create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody SeasonRequest request) { return service.create(jwt,request); }
    @PutMapping("/{seasonId}")
    @Operation(summary="Merchandiser updates a season",description="Requires expectedVersion (409 on mismatch). Existing snapshots keep their own copy of the season.")
    public SeasonView update(@AuthenticationPrincipal Jwt jwt,@PathVariable int seasonId,@Valid @RequestBody SeasonRequest request) { return service.update(jwt,seasonId,request); }
    @DeleteMapping("/{seasonId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="Merchandiser deletes a season",description="409 while any product is still assigned (Q8).")
    public void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable int seasonId) { service.delete(jwt,seasonId); }
    @GetMapping("/{seasonId}/products") @PreAuthorize("hasAnyRole('STORE_ADMIN','MERCHANDISER')")
    @Operation(summary="List the products assigned to a season")
    public SeasonProductPage products(@AuthenticationPrincipal Jwt jwt,@PathVariable int seasonId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int pageSize) { return service.products(jwt,seasonId,page,pageSize); }
    @PutMapping("/{seasonId}/products/{productId}")
    @Operation(summary="Merchandiser assigns a product to a season",description="Idempotent: 201 when created, 200 when it already existed. A product of another Store is 404.")
    public ResponseEntity<Void> assign(@AuthenticationPrincipal Jwt jwt,@PathVariable int seasonId,@PathVariable int productId) {
        return ResponseEntity.status(service.assign(jwt,seasonId,productId)?HttpStatus.CREATED:HttpStatus.OK).build();
    }
    @DeleteMapping("/{seasonId}/products/{productId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="Merchandiser unassigns a product from a season",description="404 when the product is not assigned.")
    public void unassign(@AuthenticationPrincipal Jwt jwt,@PathVariable int seasonId,@PathVariable int productId) { service.unassign(jwt,seasonId,productId); }
}

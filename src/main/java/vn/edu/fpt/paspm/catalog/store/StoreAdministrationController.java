package vn.edu.fpt.paspm.catalog.store;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import vn.edu.fpt.paspm.catalog.store.StoreContracts.ChangeStatus;
import vn.edu.fpt.paspm.catalog.store.StoreContracts.CreateStore;
import vn.edu.fpt.paspm.catalog.store.StoreContracts.StoreDetailView;
import vn.edu.fpt.paspm.catalog.store.StoreContracts.StoreView;
import vn.edu.fpt.paspm.catalog.store.StoreContracts.UpdateStore;
import vn.edu.fpt.paspm.mining.MiningContracts;

/**
 * UC34 Manage Stores (DEC-20261123-01 §2). The list route is the D2 Store picker (2026-11-02), extended additively: more
 * fields, {@code q}/{@code status} filters, and ACTIVE Stores only by default. There is no delete.
 */
@RestController
@Validated
@RequestMapping("/api/system/stores")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class StoreAdministrationController {
    private final StoreService stores;

    public StoreAdministrationController(StoreService stores) {
        this.stores = stores;
    }

    @GetMapping
    @Operation(summary = "System Admin: page through Stores (picker), ordered by name then storeId",
            description = "page is zero-based (default 0), pageSize 1..100 (default 20). status = ACTIVE (default), INACTIVE or ALL. "
                    + "q matches name or slug, ignoring case. A page past the end returns an empty items list.")
    public MiningContracts.Page<StoreView> listStores(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int pageSize,
                                                     @RequestParam(required = false) String q,
                                                     @RequestParam(required = false) String status) {
        return stores.list(q, status, page, pageSize);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "System Admin: create a Store (ACTIVE)",
            description = "name is trimmed and unique ignoring case; slug is unique ([a-z0-9]+(-[a-z0-9]+)*, at most 50). 409 on a duplicate.")
    public StoreDetailView createStore(@Valid @RequestBody CreateStore request) {
        return stores.create(request);
    }

    @GetMapping("/{storeId}")
    @Operation(summary = "System Admin: Store detail with the number of assigned users and a read-only list of them")
    public StoreDetailView getStore(@PathVariable @Positive int storeId) {
        return stores.detail(storeId);
    }

    @PutMapping("/{storeId}")
    @Operation(summary = "System Admin: update name, slug, address and phone",
            description = "Send expectedVersion; a stale version returns 409. Status changes use PATCH …/status.")
    public StoreDetailView updateStore(@PathVariable @Positive int storeId, @Valid @RequestBody UpdateStore request) {
        return stores.update(storeId, request);
    }

    @PatchMapping("/{storeId}/status")
    @Operation(summary = "System Admin: deactivate (INACTIVE) or reactivate (ACTIVE) a Store",
            description = "INACTIVE: users of the Store get 403 STORE_INACTIVE; no new System Admin work or user assignment (409 "
                    + "STORE_INACTIVE); schedulers skip it; in-flight work finishes; history stays readable. 409 on a stale "
                    + "expectedVersion or when the Store already has that status.")
    public StoreDetailView changeStoreStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive int storeId,
                                        @Valid @RequestBody ChangeStatus request) {
        return stores.changeStatus(storeId, request, Integer.parseInt(jwt.getSubject()));
    }
}

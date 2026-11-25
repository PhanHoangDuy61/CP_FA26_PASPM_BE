package vn.edu.fpt.paspm.catalog.store;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.catalog.store.StoreContracts.StoreView;
import vn.edu.fpt.paspm.catalog.store.StoreContracts.UpdateProfile;

/** UC35 own Store profile (DEC-20261123-01 §3). The Store comes from the authenticated account, never from the request. */
@RestController
@RequestMapping("/api/store/profile")
public class StoreProfileController {
    private final StoreService stores;
    private final CatalogAccess access;

    public StoreProfileController(StoreService stores, CatalogAccess access) {
        this.stores = stores;
        this.access = access;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STORE_ADMIN','MERCHANDISER')")
    @Operation(summary = "Store Admin / Merchandiser: view the own Store")
    public StoreView getStoreProfile(@AuthenticationPrincipal Jwt jwt) {
        return stores.profile(access.currentStoreId(jwt));
    }

    @PutMapping
    @PreAuthorize("hasRole('STORE_ADMIN')")
    @Operation(summary = "Store Admin: edit the own Store's address and phone",
            description = "Only expectedVersion, address and phone are accepted (name, slug and status are unknown fields: 400). "
                    + "Blank values are stored as null. A stale expectedVersion returns 409.")
    public StoreView updateStoreProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfile request) {
        return stores.updateProfile(access.currentStoreId(jwt), request);
    }
}

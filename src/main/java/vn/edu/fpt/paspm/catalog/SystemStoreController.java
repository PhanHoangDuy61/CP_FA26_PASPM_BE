package vn.edu.fpt.paspm.catalog;

import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.mining.MiningContracts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import vn.edu.fpt.paspm.storefront.StorefrontContracts.VisibilityRequest;
import vn.edu.fpt.paspm.storefront.StorefrontContracts.VisibilityView;
import vn.edu.fpt.paspm.storefront.StorefrontRepository;

/**
 * Decision D2 (confirmed 2026-11-02): a read-only Store picker for System Admin screens that must name a Store explicitly
 * (administrative Mining requests, schedule creation, cross-Store reads). It returns the Store id and name only.
 */
@RestController
@RequestMapping("/api/system/stores")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class SystemStoreController {
    private static final int MAX_PAGE_SIZE = 100;
    private final JdbcTemplate jdbc;
    private final StorefrontRepository storefronts;
    public SystemStoreController(JdbcTemplate jdbc, StorefrontRepository storefronts) {
        this.jdbc = jdbc;
        this.storefronts = storefronts;
    }
    public record StoreView(int storeId, String name) {}
    @GetMapping
    @Operation(summary = "System Admin: page through all Stores (id and name only), ordered by name then storeId",
        description = "Read-only picker. page is zero-based (default 0), pageSize 1..100 (default 20). A page past the end returns an empty items list.")
    public MiningContracts.Page<StoreView> listStores(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int pageSize) {
        if (page < 0 || page > 100_000 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) throw new ApiException(400, "Invalid pagination");
        List<StoreView> items = jdbc.query("SELECT store_id,name FROM store ORDER BY name,store_id LIMIT ? OFFSET ?",
            (rs, n) -> new StoreView(rs.getInt(1), rs.getString(2)), pageSize, (long) page * pageSize);
        return new MiningContracts.Page<>(items, page, pageSize, jdbc.queryForObject("SELECT COUNT(*) FROM store", Long.class));
    }

    @PutMapping("/{storeId}/storefront-visibility")
    @Operation(summary = "SYSTEM_ADMIN: enable or disable public visibility without changing campaign state")
    public VisibilityView storefrontVisibility(@PathVariable @Positive int storeId,
            @Valid @RequestBody VisibilityRequest request) {
        var result = storefronts.setVisibility(storeId, request.storefrontEnabled());
        return new VisibilityView(result.storeId(), result.storeSlug(), result.storefrontEnabled());
    }
}

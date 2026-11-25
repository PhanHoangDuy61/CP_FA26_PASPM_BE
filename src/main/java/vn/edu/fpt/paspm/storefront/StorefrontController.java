package vn.edu.fpt.paspm.storefront;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.fpt.paspm.storefront.StorefrontContracts.HomeView;
import vn.edu.fpt.paspm.storefront.StorefrontContracts.ProductCard;
import vn.edu.fpt.paspm.storefront.StorefrontContracts.StorefrontProductPage;
import vn.edu.fpt.paspm.storefront.StorefrontContracts.PublicStore;

@RestController
@Tag(name = "F09 Public Storefront")
public class StorefrontController {
    private final StorefrontService storefront;

    public StorefrontController(StorefrontService storefront) {
        this.storefront = storefront;
    }

    @GetMapping("/api/storefront/stores")
    @Operation(summary = "List ACTIVE Stores (public storefront); storeSlug is the Store slug", security = {})
    @SecurityRequirements
    public List<PublicStore> stores() {
        return storefront.stores();
    }

    @GetMapping("/api/storefront/{storeSlug}/home")
    @Operation(summary = "Read the public homepage of an ACTIVE Store", security = {})
    @SecurityRequirements
    public HomeView home(@PathVariable String storeSlug) {
        return storefront.home(storeSlug);
    }

    @GetMapping("/api/storefront/{storeSlug}/products")
    @Operation(operationId = "listStorefrontProducts", summary = "Search and page through ACTIVE public products", security = {})
    @SecurityRequirements
    public StorefrontProductPage products(@PathVariable String storeSlug,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) @Positive Integer categoryId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int pageSize,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        return storefront.products(storeSlug, search, categoryId, page, pageSize, sortBy, sortDirection);
    }

    @GetMapping("/api/storefront/{storeSlug}/products/{productId}")
    @Operation(summary = "Read public product detail without internal inventory or cost fields", security = {})
    @SecurityRequirements
    public ProductCard product(@PathVariable String storeSlug, @PathVariable @Positive int productId) {
        return storefront.product(storeSlug, productId);
    }
}

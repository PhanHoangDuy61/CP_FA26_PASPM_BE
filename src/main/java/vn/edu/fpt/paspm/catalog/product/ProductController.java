package vn.edu.fpt.paspm.catalog.product;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import vn.edu.fpt.paspm.catalog.CatalogContracts.ProductPage;
import vn.edu.fpt.paspm.catalog.CatalogContracts.ProductRequest;
import vn.edu.fpt.paspm.catalog.CatalogContracts.ProductView;
import vn.edu.fpt.paspm.catalog.CatalogContracts.StatusRequest;

@RestController
@PreAuthorize("hasRole('STORE_ADMIN')")
public class ProductController {
    private final ProductService products;

    public ProductController(ProductService products) { this.products = products; }

    @Operation(summary = "Search and filter products in the authenticated Store")
    @GetMapping("/api/products")
    @PreAuthorize("hasAnyRole('STORE_ADMIN','MERCHANDISER')")
    public ProductPage list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        return products.list(jwt, search, categoryId, status, page, pageSize, sortBy, sortDirection);
    }

    @Operation(summary = "Get a product and batch-derived stock for the authenticated Store")
    @GetMapping("/api/products/{id}")
    @PreAuthorize("hasAnyRole('STORE_ADMIN','MERCHANDISER')")
    public ProductView get(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) { return products.get(jwt, id); }

    @Operation(summary = "Create a product in the authenticated Store; stock is managed through batches")
    @PostMapping("/api/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProductRequest request) {
        return products.create(jwt, request);
    }

    @Operation(summary = "Update product information without changing stock")
    @PutMapping("/api/products/{id}")
    public ProductView update(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                              @Valid @RequestBody ProductRequest request) {
        return products.update(jwt, id, request);
    }

    @Operation(summary = "Activate or deactivate a product")
    @PatchMapping("/api/products/{id}/status")
    public ProductView updateStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                                    @Valid @RequestBody StatusRequest request) {
        return products.updateStatus(jwt, id, request.status());
    }
}

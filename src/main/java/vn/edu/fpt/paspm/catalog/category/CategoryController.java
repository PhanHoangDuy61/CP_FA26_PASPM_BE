package vn.edu.fpt.paspm.catalog.category;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.swagger.v3.oas.annotations.Operation;
import vn.edu.fpt.paspm.catalog.CatalogContracts.CategoryRequest;
import vn.edu.fpt.paspm.catalog.CatalogContracts.CategoryView;
import vn.edu.fpt.paspm.catalog.CatalogContracts.StatusRequest;

@RestController
@PreAuthorize("hasRole('STORE_ADMIN')")
public class CategoryController {
    private final CategoryService categories;

    public CategoryController(CategoryService categories) { this.categories = categories; }

    @Operation(summary = "List categories for the authenticated Store")
    @GetMapping("/api/categories")
    public List<CategoryView> list(@AuthenticationPrincipal Jwt jwt) { return categories.list(jwt); }

    @Operation(summary = "Get a Store category")
    @GetMapping("/api/categories/{id}")
    public CategoryView get(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) { return categories.get(jwt, id); }

    @Operation(summary = "Create a category in the authenticated Store")
    @PostMapping("/api/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CategoryRequest request) {
        return categories.create(jwt, request);
    }

    @Operation(summary = "Update a Store category")
    @PutMapping("/api/categories/{id}")
    public CategoryView update(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                               @Valid @RequestBody CategoryRequest request) {
        return categories.update(jwt, id, request);
    }

    @Operation(summary = "Activate or deactivate a Store category")
    @PatchMapping("/api/categories/{id}/status")
    public CategoryView updateStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                                     @Valid @RequestBody StatusRequest request) {
        return categories.updateStatus(jwt, id, request.status());
    }
}

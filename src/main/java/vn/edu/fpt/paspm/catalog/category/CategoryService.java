package vn.edu.fpt.paspm.catalog.category;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.oauth2.jwt.Jwt;
import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.catalog.CatalogContracts.CategoryRequest;
import vn.edu.fpt.paspm.catalog.CatalogContracts.CategoryView;
import vn.edu.fpt.paspm.common.ApiException;

@Service
public class CategoryService {
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
    private final CategoryRepository categories;
    private final CatalogAccess access;

    public CategoryService(CategoryRepository categories, CatalogAccess access) {
        this.categories = categories; this.access = access;
    }

    public List<CategoryView> list(Jwt jwt) { return categories.findAll(access.currentStoreId(jwt)); }

    public CategoryView get(Jwt jwt, int id) { return requireCategory(access.currentStoreId(jwt), id); }

    @Transactional
    public CategoryView create(Jwt jwt, CategoryRequest request) {
        int storeId = access.currentStoreId(jwt);
        validateStatus(request.status());
        return categories.create(storeId, request.name(), request.status());
    }

    @Transactional
    public CategoryView update(Jwt jwt, int id, CategoryRequest request) {
        int storeId = access.currentStoreId(jwt);
        validateStatus(request.status());
        if (!categories.update(storeId, id, request.name(), request.status())) throw missing();
        return requireCategory(storeId, id);
    }

    @Transactional
    public CategoryView updateStatus(Jwt jwt, int id, String status) {
        int storeId = access.currentStoreId(jwt);
        validateStatus(status);
        if (!categories.updateStatus(storeId, id, status)) throw missing();
        return requireCategory(storeId, id);
    }

    private CategoryView requireCategory(int storeId, int id) {
        CategoryView category = categories.find(storeId, id);
        if (category == null) throw missing();
        return category;
    }

    private void validateStatus(String status) {
        if (status == null || !STATUSES.contains(status)) throw new ApiException(400, "Invalid category status");
    }

    private ApiException missing() { return new ApiException(404, "Category not found"); }
}

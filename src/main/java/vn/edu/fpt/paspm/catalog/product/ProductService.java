package vn.edu.fpt.paspm.catalog.product;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;

import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.catalog.CatalogContracts.ProductPage;
import vn.edu.fpt.paspm.catalog.CatalogContracts.ProductRequest;
import vn.edu.fpt.paspm.catalog.CatalogContracts.ProductView;
import vn.edu.fpt.paspm.catalog.category.CategoryRepository;
import vn.edu.fpt.paspm.common.ApiException;

@Service
public class ProductService {
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final Map<String, String> SORTS = Map.of(
            "name", "p.name", "sku", "p.sku", "sellingPrice", "p.selling_price", "status", "p.status");
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final CatalogAccess access;

    public ProductService(ProductRepository products, CategoryRepository categories, CatalogAccess access) {
        this.products = products; this.categories = categories; this.access = access;
    }

    public ProductPage list(Jwt jwt, String search, Integer categoryId, String status,
                            int page, int pageSize, String sortBy, String sortDirection) {
        int storeId = access.currentStoreId(jwt);
        if (page < 0 || pageSize < 1 || pageSize > 100) throw new ApiException(400, "Invalid pagination");
        if (!status.isBlank()) validateStatus(status);
        String column = SORTS.get(sortBy);
        if (column == null) throw new ApiException(400, "Unsupported sort field");
        String direction = sortDirection.equalsIgnoreCase("asc") ? "ASC"
                : sortDirection.equalsIgnoreCase("desc") ? "DESC" : null;
        if (direction == null) throw new ApiException(400, "Sort direction must be asc or desc");
        if (categoryId != null && categories.find(storeId, categoryId) == null) {
            throw new ApiException(404, "Category not found");
        }
        return products.search(storeId, search, categoryId, status, page, pageSize, column, direction);
    }

    public ProductView get(Jwt jwt, int id) { return requireProduct(access.currentStoreId(jwt), id); }

    @Transactional
    public ProductView create(Jwt jwt, ProductRequest request) {
        int storeId = access.currentStoreId(jwt);
        validateRequest(storeId, request);
        return products.create(storeId, request.sku(), request.name(), request.categoryId(), request.unit(),
                request.requiresExpiry(), request.sellingPrice(), request.costPrice(), request.status(), request.expiryPushDays()==null?0:request.expiryPushDays());
    }

    @Transactional
    public ProductView update(Jwt jwt, int id, ProductRequest request) {
        int storeId = access.currentStoreId(jwt);
        requireProduct(storeId, id);
        validateRequest(storeId, request);
        if (request.requiresExpiry() && products.hasExpirylessBatches(id)) {
            throw new ApiException(400, "Cannot require expiry while a batch has no expiry date");
        }
        if (!products.update(storeId, id, request.sku(), request.name(), request.categoryId(), request.unit(),
                request.requiresExpiry(), request.sellingPrice(), request.costPrice(), request.status(), request.expiryPushDays()==null?0:request.expiryPushDays())) {
            throw missing();
        }
        return requireProduct(storeId, id);
    }

    @Transactional
    public ProductView updateStatus(Jwt jwt, int id, String status) {
        int storeId = access.currentStoreId(jwt);
        validateStatus(status);
        if (!products.updateStatus(storeId, id, status)) throw missing();
        return requireProduct(storeId, id);
    }

    private void validateRequest(int storeId, ProductRequest request) {
        validateStatus(request.status());
        if (request.categoryId() != null && categories.find(storeId, request.categoryId()) == null) {
            throw new ApiException(404, "Category not found");
        }
    }

    private ProductView requireProduct(int storeId, int id) {
        ProductView product = products.find(storeId, id);
        if (product == null) throw missing();
        return product;
    }

    private void validateStatus(String status) {
        if (status == null || !STATUSES.contains(status)) throw new ApiException(400, "Invalid product status");
    }

    private ApiException missing() { return new ApiException(404, "Product not found"); }
}

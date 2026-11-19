package vn.edu.fpt.paspm.catalog.product;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
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
            "name", "p.name",
            "sku", "p.sku",
            "category", "c.name",
            "unit", "p.unit",
            "costPrice", "p.cost_price",
            "sellingPrice", "p.selling_price",
            "status", "p.status",
            "requiresExpiry", "p.requires_expiry",
            "stockQuantity", "stock_quantity");
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
        String sku = normalizeSku(request.sku());
        requireUniqueSku(storeId, sku, null);
        requireNameConfirmation(storeId, 0, request.name(), request.confirmSimilarName());
        try {
            return products.create(storeId, sku, request.name(), request.categoryId(), request.unit(),
                    request.requiresExpiry(), request.sellingPrice(), request.costPrice(), request.status());
        } catch (DataIntegrityViolationException exception) {
            if (isSkuConstraintViolation(exception)) throw duplicateSku();
            throw exception;
        }
    }

    @Transactional
    public ProductView update(Jwt jwt, int id, ProductRequest request) {
        int storeId = access.currentStoreId(jwt);
        requireProduct(storeId, id);
        validateRequest(storeId, request);
        String sku = normalizeSku(request.sku());
        requireUniqueSku(storeId, sku, id);
        requireNameConfirmation(storeId, id, request.name(), request.confirmSimilarName());
        if (request.requiresExpiry() && products.hasExpirylessBatches(id)) {
            throw new ApiException(400, "Cannot require expiry while a batch has no expiry date");
        }
        try {
            if (!products.update(storeId, id, sku, request.name(), request.categoryId(), request.unit(),
                    request.requiresExpiry(), request.sellingPrice(), request.costPrice(), request.status())) {
                throw missing();
            }
        } catch (DataIntegrityViolationException exception) {
            if (isSkuConstraintViolation(exception)) throw duplicateSku();
            throw exception;
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

    private void requireNameConfirmation(int storeId, int excludedProductId, String name, Boolean confirmed) {
        if (!Boolean.TRUE.equals(confirmed)
                && products.findOtherNames(storeId, excludedProductId).stream()
                        .anyMatch(existing -> similarName(name, existing))) {
            throw new ProductNameConfirmationRequiredException();
        }
    }

    private void requireUniqueSku(int storeId, String sku, Integer excludedProductId) {
        if (products.skuExists(storeId, sku, excludedProductId)) throw duplicateSku();
    }

    private static String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean similarName(String first, String second) {
        String left = normalizeName(first);
        String right = normalizeName(second);
        if (left.equals(right)) return true;
        if (Math.min(left.length(), right.length()) < 4 || Math.abs(left.length() - right.length()) > 1) return false;

        int i = 0;
        int j = 0;
        int edits = 0;
        while (i < left.length() && j < right.length()) {
            if (left.charAt(i) == right.charAt(j)) {
                i++;
                j++;
                continue;
            }
            if (++edits > 1) return false;
            if (left.length() == right.length()) {
                i++;
                j++;
            } else if (left.length() < right.length()) {
                j++;
            } else {
                i++;
            }
        }
        return edits + (i < left.length() || j < right.length() ? 1 : 0) <= 1;
    }

    private static String normalizeName(String name) {
        return name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static boolean isSkuConstraintViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("uq_product_store_sku")) return true;
        }
        return false;
    }

    private static ApiException duplicateSku() {
        return new ApiException(409, "SKU already exists in this Store");
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

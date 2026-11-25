package vn.edu.fpt.paspm.catalog.product;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;

import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.catalog.CatalogContracts.ProductPage;
import vn.edu.fpt.paspm.catalog.CatalogContracts.ProductRequest;
import vn.edu.fpt.paspm.catalog.CatalogContracts.ProductView;
import vn.edu.fpt.paspm.catalog.category.CategoryRepository;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.UtilityTimeZone;

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

    private final UtilityTimeZone timeZone;

    public ProductService(ProductRepository products, CategoryRepository categories, CatalogAccess access,
                          UtilityTimeZone timeZone) {
        this.products = products; this.categories = categories; this.access = access; this.timeZone = timeZone;
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
        requireNameConfirmation(storeId, 0, request.name(), request.confirmSimilarName());
        return products.create(storeId, request.name(), request.categoryId(), request.unit(),
                request.requiresExpiry(), request.sellingPrice(), request.costPrice(), request.status(),
                request.imageUrl(), request.description());
    }

    @Transactional
    public ProductView update(Jwt jwt, int id, ProductRequest request) {
        int storeId = access.currentStoreId(jwt);
        requireProduct(storeId, id);
        validateRequest(storeId, request);
        requireNameConfirmation(storeId, id, request.name(), request.confirmSimilarName());
        if (request.requiresExpiry() && products.hasExpirylessBatches(id)) {
            throw new ApiException(400, "Cannot require expiry while a batch has no expiry date");
        }
        if (!products.update(storeId, id, request.name(), request.categoryId(), request.unit(),
                request.requiresExpiry(), request.sellingPrice(), request.costPrice(), request.status(),
                request.imageUrl(), request.description())) {
            throw missing();
        }
        return requireProduct(storeId, id);
    }

    /**
     * DEC-20261124-03. Order: 404 (unknown, other Store, already deleted) then 409 (sellable stock first, campaigns always
     * reported) then soft delete. Role is checked before this by the controller (401 -> 403 -> 404).
     */
    @Transactional
    public void delete(Jwt jwt, int id) {
        int storeId = access.currentStoreId(jwt);
        if (!products.lockForDelete(storeId, id)) throw missing();
        long sellable = products.sellableQuantity(id, timeZone.today());
        var campaigns = products.liveCampaigns(storeId, id);
        if (sellable > 0) {
            throw new ProductDeleteBlockedException(ProductDeleteBlockedException.HAS_SELLABLE_STOCK,
                    "The product still has " + sellable + " sellable unit(s) (ACTIVE, not expired); sell, write off or block them first"
                            + (campaigns.isEmpty() ? "" : ", and remove it from the live campaign(s)"),
                    sellable, campaigns);
        }
        if (!campaigns.isEmpty()) {
            throw new ProductDeleteBlockedException(ProductDeleteBlockedException.IN_LIVE_CAMPAIGN,
                    "The product is placed in " + campaigns.size() + " PUBLISHED or ACTIVE campaign layout(s); end the campaign first",
                    0, campaigns);
        }
        if (!products.softDelete(storeId, id, Integer.parseInt(jwt.getSubject()))) throw missing();
    }

    @Transactional
    public ProductView updateStatus(Jwt jwt, int id, String status) {
        int storeId = access.currentStoreId(jwt);
        validateStatus(status);
        requireProduct(storeId, id); // a deleted product is 404 and can never be re-activated
        if (!products.updateStatus(storeId, id, status)) throw missing();
        return requireProduct(storeId, id);
    }

    private void validateRequest(int storeId, ProductRequest request) {
        validateStatus(request.status());
        validateImageUrl(request.imageUrl());
        if (request.categoryId() != null && categories.find(storeId, request.categoryId()) == null) {
            throw new ApiException(404, "Category not found");
        }
    }

    private void validateImageUrl(String value) {
        if (value == null || value.isBlank()) return;
        try {
            URI uri = new URI(value.trim());
            if (!uri.isAbsolute() || uri.getHost() == null
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new ApiException(400, "imageUrl must be an absolute HTTP or HTTPS URL");
            }
        } catch (URISyntaxException exception) {
            throw new ApiException(400, "imageUrl must be a valid HTTP or HTTPS URL");
        }
    }

    private void requireNameConfirmation(int storeId, int excludedProductId, String name, Boolean confirmed) {
        if (!Boolean.TRUE.equals(confirmed)
                && products.findOtherNames(storeId, excludedProductId).stream()
                        .anyMatch(existing -> similarName(name, existing))) {
            throw new ProductNameConfirmationRequiredException();
        }
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

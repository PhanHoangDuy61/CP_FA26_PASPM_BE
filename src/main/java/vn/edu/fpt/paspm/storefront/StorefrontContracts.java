package vn.edu.fpt.paspm.storefront;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public final class StorefrontContracts {
    private StorefrontContracts() {}

    public enum Availability { AVAILABLE, UNAVAILABLE }

    public record PublicStore(String storeSlug, String name) {}
    public record StoreSummary(String storeSlug, String name) {}
    public record VisibilityRequest(@NotNull Boolean storefrontEnabled) {}
    public record VisibilityView(int storeId, String storeSlug, boolean storefrontEnabled) {}
    public record ProductCategory(Integer categoryId, String name) {}
    public record ProductCard(int productId, String name, String description, String imageUrl,
            String unit, BigDecimal sellingPrice, ProductCategory category, Availability availability) {}
    public record StorefrontProductPage(List<ProductCard> items, int page, int pageSize, long totalElements) {}
    public record CatalogCategory(Integer categoryId, String name, List<ProductCard> products) {}
    public record HomePlacement(String type, int blockNo, int startPosition, List<ProductCard> products) {}
    public record HomeZone(String name, List<HomePlacement> placements) {}
    public record HomeView(StoreSummary store, String campaignName,
            @Schema(types = {"string", "null"}) String fallbackType,
            List<HomeZone> zones, List<CatalogCategory> catalog) {}
}

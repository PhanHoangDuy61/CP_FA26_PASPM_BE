package vn.edu.fpt.paspm.storefront;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public final class StorefrontContracts {
    private StorefrontContracts() {}

    /** FR-09.3: a public product is AVAILABLE or OUT_OF_STOCK (no quantity is exposed). */
    public enum Availability { AVAILABLE, OUT_OF_STOCK }

    public record PublicStore(String storeSlug, String name) {}
    public record StoreSummary(String storeSlug, String name) {}
    public record ProductCategory(Integer categoryId, String name) {}
    public record ProductCard(int productId, String name, String description, String imageUrl,
            String unit, BigDecimal sellingPrice, ProductCategory category, Availability availability) {}
    public record StorefrontProductPage(List<ProductCard> items, int page, int pageSize, long totalElements) {}
    public record CatalogCategory(Integer categoryId, String name, List<ProductCard> products) {}
    public record HomePlacement(String type, int blockNo, int startPosition, List<ProductCard> products,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Opaque F10 key, present only for a BUNDLE block on the active selected Layout B")
            String bundleKey) {}
    public record HomeZone(String name, List<HomePlacement> placements) {}
    public record HomeView(StoreSummary store, String campaignName,
            @Schema(types = {"string", "null"}) String fallbackType,
            List<HomeZone> zones, List<CatalogCategory> catalog) {}
}

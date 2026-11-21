package vn.edu.fpt.paspm.storefront;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.layout.LayoutRules;
import vn.edu.fpt.paspm.recommendation.RecommendationContracts.RecPolicy;
import vn.edu.fpt.paspm.utility.UtilitySettingsRepository;
import vn.edu.fpt.paspm.utility.UtilityTimeZone;
import vn.edu.fpt.paspm.storefront.StorefrontRepository.ActiveLayout;
import vn.edu.fpt.paspm.storefront.StorefrontRepository.AvailabilityRow;
import vn.edu.fpt.paspm.storefront.StorefrontRepository.PlacementRow;
import vn.edu.fpt.paspm.storefront.StorefrontRepository.ProductRow;
import vn.edu.fpt.paspm.storefront.StorefrontRepository.RecommendationRow;
import vn.edu.fpt.paspm.storefront.StorefrontRepository.StoreRow;
import static vn.edu.fpt.paspm.storefront.StorefrontContracts.*;

@Service
public class StorefrontService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Map<String, String> SORTS = Map.of(
            "name", "p.name", "sellingPrice", "p.selling_price");

    private final StorefrontRepository repository;
    private final UtilityTimeZone timeZone;
    private final UtilitySettingsRepository json;
    private final LayoutRules layoutRules;

    public StorefrontService(StorefrontRepository repository, UtilityTimeZone timeZone,
            UtilitySettingsRepository json, LayoutRules layoutRules) {
        this.repository = repository;
        this.timeZone = timeZone;
        this.json = json;
        this.layoutRules = layoutRules;
    }

    @Transactional(readOnly = true)
    public List<PublicStore> stores() {
        return repository.publicStores().stream()
                .map(store -> new PublicStore(store.slug(), store.name())).toList();
    }

    @Transactional(readOnly = true)
    public HomeView home(String storeSlug) {
        StoreRow store = requirePublicStore(storeSlug);
        ActiveLayout active = repository.activeLayout(store.id());
        if (active == null) return fallbackHome(store);
        if (!"READY".equals(active.status())) {
            throw new ApiException(503, "The active storefront layout is unavailable");
        }
        layoutRules.requireConfiguredPublicCapacities();
        return activeHome(store, active);
    }

    @Transactional(readOnly = true)
    public StorefrontProductPage products(String storeSlug, String search, Integer categoryId, int page,
            int pageSize, String sortBy, String sortDirection) {
        StoreRow store = requirePublicStore(storeSlug);
        if (page < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new ApiException(400, "Invalid pagination");
        }
        String sortColumn = SORTS.get(sortBy);
        if (sortColumn == null) throw new ApiException(400, "Unsupported sort field");
        String direction = "asc".equalsIgnoreCase(sortDirection) ? "ASC"
                : "desc".equalsIgnoreCase(sortDirection) ? "DESC" : null;
        if (direction == null) throw new ApiException(400, "Sort direction must be asc or desc");
        if (categoryId != null && !repository.categoryExists(store.id(), categoryId)) {
            throw new ApiException(404, "Category not found");
        }
        var result = repository.products(store.id(), search, categoryId, sortColumn, direction, page, pageSize);
        var availability = repository.availability(store.id(), ids(result.items()), timeZone.today());
        return new StorefrontProductPage(cards(result.items(), availability), page, pageSize, result.totalElements());
    }

    @Transactional(readOnly = true)
    public ProductCard product(String storeSlug, int productId) {
        StoreRow store = requirePublicStore(storeSlug);
        ProductRow product = repository.activeProduct(store.id(), productId);
        if (product == null) throw new ApiException(404, "Product not found");
        Map<Integer, AvailabilityRow> availability = repository.availability(store.id(),
                List.of(product.id()), timeZone.today());
        return card(product, availability.get(product.id()));
    }

    private StoreRow requirePublicStore(String slug) {
        StoreRow store = repository.publicStore(slug);
        if (store == null) throw new ApiException(404, "Storefront not found");
        return store;
    }

    private HomeView fallbackHome(StoreRow store) {
        List<ProductRow> products = repository.activeProductsByCategory(store.id());
        Map<Integer, AvailabilityRow> availability = repository.availability(store.id(), ids(products), timeZone.today());
        Map<CategoryKey, List<ProductCard>> grouped = new LinkedHashMap<>();
        for (ProductRow product : products) {
            grouped.computeIfAbsent(new CategoryKey(product.categoryId(),
                    product.categoryName() == null ? "Uncategorized" : product.categoryName()),
                    ignored -> new ArrayList<>()).add(card(product, availability.get(product.id())));
        }
        List<CatalogCategory> catalog = grouped.entrySet().stream()
                .map(entry -> new CatalogCategory(entry.getKey().id(), entry.getKey().name(),
                        List.copyOf(entry.getValue())))
                .toList();
        return new HomeView(new StoreSummary(store.slug(), store.name()), null, "DEFAULT_CATALOG",
                List.of(), catalog);
    }

    private HomeView activeHome(StoreRow store, ActiveLayout active) {
        List<PlacementRow> rows = repository.placements(store.id(), active.layoutId());
        Map<Integer, AvailabilityRow> availability = repository.availability(store.id(),
                rows.stream().map(PlacementRow::productId).distinct().toList(), timeZone.today());
        List<Long> recommendationIds = rows.stream().map(PlacementRow::recommendationId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, RecommendationRow> recommendations = repository.recommendations(store.id(), recommendationIds);
        Map<Long, List<Integer>> recommendationProducts =
                repository.recommendationProducts(store.id(), recommendationIds);

        var zones = new LinkedHashMap<String, List<HomePlacement>>();
        var recommendationBlocks = new LinkedHashMap<Long, List<PlacementRow>>();
        for (PlacementRow row : rows) {
            if (row.recommendationId() == null) {
                if (!publicProduct(row, availability.get(row.productId()))) continue;
                zones.computeIfAbsent(row.zone(), ignored -> new ArrayList<>()).add(new HomePlacement(
                        "BASELINE", row.blockNo(), row.position(),
                        List.of(card(row, availability.get(row.productId())))));
            } else {
                recommendationBlocks.computeIfAbsent(row.recommendationId(), ignored -> new ArrayList<>()).add(row);
            }
        }

        for (Map.Entry<Long, List<PlacementRow>> entry : recommendationBlocks.entrySet()) {
            List<PlacementRow> block = entry.getValue();
            RecommendationRow recommendation = recommendations.get(entry.getKey());
            if (!validBlock(active, recommendation, block, recommendationProducts.get(entry.getKey()), availability)) {
                continue;
            }
            PlacementRow first = block.get(0);
            List<ProductCard> products = block.stream()
                    .sorted(Comparator.comparingInt(PlacementRow::position)
                            .thenComparingInt(PlacementRow::productId))
                    .map(row -> card(row, availability.get(row.productId()))).toList();
            zones.computeIfAbsent(first.zone(), ignored -> new ArrayList<>()).add(new HomePlacement(
                    first.recommendationType(), first.blockNo(), first.position(), products));
        }
        List<HomeZone> result = zones.entrySet().stream()
                .map(entry -> new HomeZone(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
        return new HomeView(new StoreSummary(store.slug(), store.name()), active.campaignName(),
                null, result, List.of());
    }

    private boolean validBlock(ActiveLayout active, RecommendationRow recommendation,
            List<PlacementRow> block, List<Integer> storedProducts,
            Map<Integer, AvailabilityRow> availability) {
        if (recommendation == null || !"APPROVED".equals(recommendation.status())
                || recommendation.snapshotId() != active.snapshotId()
                || !recommendation.type().equals(block.get(0).recommendationType())
                || block.stream().anyMatch(row -> !recommendation.type().equals(row.recommendationType())
                        || row.recommendationSnapshotId() == null
                        || row.recommendationSnapshotId() != active.snapshotId())) {
            return false;
        }
        List<Integer> placedProducts = block.stream().map(PlacementRow::productId).sorted().toList();
        if (storedProducts == null || !storedProducts.equals(placedProducts)) return false;
        RecPolicy policy = policy(recommendation);
        if (policy.minimumSellableQuantity() < 1) return false;
        return block.stream().allMatch(row -> {
            AvailabilityRow value = availability.get(row.productId());
            return publicProduct(row, value) && !value.missingRequiredExpiry()
                    && value.sellableQuantity() >= policy.minimumSellableQuantity();
        });
    }

    private RecPolicy policy(RecommendationRow recommendation) {
        return json.read(recommendation.policy(), RecPolicy.class);
    }

    private List<ProductCard> cards(List<ProductRow> products, Map<Integer, AvailabilityRow> availability) {
        return products.stream().map(product -> card(product, availability.get(product.id()))).toList();
    }

    private ProductCard card(ProductRow product, AvailabilityRow availability) {
        return new ProductCard(product.id(), product.name(), product.description(), product.imageUrl(),
                product.unit(), product.sellingPrice(), category(product.categoryId(), product.categoryName()),
                availability != null && availability.productActive() && availability.sellableQuantity() > 0
                        ? Availability.AVAILABLE : Availability.UNAVAILABLE);
    }

    private ProductCard card(PlacementRow product, AvailabilityRow availability) {
        return new ProductCard(product.productId(), product.name(), product.description(), product.imageUrl(),
                product.unit(), product.sellingPrice(), category(product.categoryId(), product.categoryName()),
                availability != null && availability.productActive() && availability.sellableQuantity() > 0
                        ? Availability.AVAILABLE : Availability.UNAVAILABLE);
    }

    private ProductCategory category(Integer id, String name) {
        return id == null ? null : new ProductCategory(id, name);
    }

    private boolean publicProduct(PlacementRow row, AvailabilityRow availability) {
        return "ACTIVE".equals(row.productStatus()) && availability != null && availability.productActive();
    }

    private List<Integer> ids(List<ProductRow> products) {
        return products.stream().map(ProductRow::id).distinct().toList();
    }

    private record CategoryKey(Integer id, String name) {}
}

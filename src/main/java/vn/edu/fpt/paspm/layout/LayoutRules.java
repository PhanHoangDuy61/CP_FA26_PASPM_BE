package vn.edu.fpt.paspm.layout;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.recommendation.RecommendationContracts.RecType;
import static vn.edu.fpt.paspm.layout.LayoutContracts.*;

@Component
public class LayoutRules {
    private static final String BASELINE = "BASELINE";
    private final LayoutPolicyProperties properties;
    private final Map<RecType, String> zones = new EnumMap<>(RecType.class);

    public LayoutRules(LayoutPolicyProperties properties) {
        this.properties = properties;
        for (RecType type : RecType.values()) zones.put(type, type.name());
        validateConfiguredCaps(properties.getMaxBlocksByZone());
        validateConfiguredCaps(properties.getMaxProductsByZone());
    }

    public String zone(RecType type) { return zones.get(type); }

    public boolean isEditableZone(String zone) {
        return java.util.Arrays.stream(RecType.values()).anyMatch(type -> type.name().equals(zone));
    }

    public List<String> capacityErrorsForPlacements(List<PlacementRequest> placements) {
        var grouped = new LinkedHashMap<String, Map<Integer, List<Integer>>>();
        for (PlacementRequest placement : placements) {
            grouped.computeIfAbsent(placement.zone(), ignored -> new java.util.LinkedHashMap<>())
                    .computeIfAbsent(placement.blockNo(), ignored -> new ArrayList<>())
                    .add(placement.productId());
        }
        var blocks = new ArrayList<PlacementBlock>();
        grouped.forEach((zone, zoneBlocks) -> zoneBlocks.forEach((blockNo, products) ->
                blocks.add(new PlacementBlock(null, null, List.copyOf(products), zone, blockNo, 1))));
        return capacityErrors(blocks);
    }

    /** F09 renders an active layout only when every zone has a configured cap (DEC-20261119-03). */
    public void requireConfiguredPublicCapacities() {
        for (RecType type : RecType.values()) {
            String zone = zone(type);
            if (properties.getMaxBlocksByZone().get(zone) == null
                    || properties.getMaxProductsByZone().get(zone) == null) {
                throw new ApiException(503, "Storefront layout capacities are not configured");
            }
        }
    }

    /** DEC-20261119-06: K for baseline A is the BASELINE product capacity; {@code null} keeps the V1 "all products" baseline. */
    public Integer baselineK() { return properties.getMaxProductsByZone().get(BASELINE); }

    /** Top-K of the frequency-ranked snapshot products, order preserved; every product when BASELINE is not configured. */
    public List<Integer> baselineSelection(List<Integer> ranked) {
        Integer k = baselineK();
        return k == null || ranked.size() <= k ? List.copyOf(ranked) : List.copyOf(ranked.subList(0, k));
    }

    /** Equal-size comparison: the first N products of A in placement order, or all of A when N exceeds |A|. */
    public static List<Integer> equalSizePrefix(List<Integer> baselineInOrder, int n) {
        return List.copyOf(baselineInOrder.subList(0, Math.min(Math.max(n, 0), baselineInOrder.size())));
    }

    /** Assumption entries stored with an OFFLINE_REPLAY_V2 report. */
    public static Map<String, Object> equalSizeAssumptions(int n, int baselineSize, Integer k) {
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("comparisonMode", "EQUAL_SIZE_TOP_N_OF_A");
        result.put("comparisonRule", "B is compared with the first N products of A in placement order (frequency rank), N = distinct products in B's revision; all of A when N > |A|.");
        result.put("comparedProductCountN", n);
        result.put("baselineProductCount", baselineSize);
        result.put("baselineProductsCompared", Math.min(n, baselineSize));
        result.put("baselineK", k);
        result.put("nExceedsBaseline", n > baselineSize);
        return result;
    }

    public LayoutPolicyView policy() {
        var result = new ArrayList<ZonePolicy>();
        result.add(new ZonePolicy(BASELINE, null, properties.getMaxBlocksByZone().get(BASELINE),
                properties.getMaxProductsByZone().get(BASELINE)));
        for (RecType type : RecType.values()) {
            String zone = zone(type);
            result.add(new ZonePolicy(zone, type, properties.getMaxBlocksByZone().get(zone),
                    properties.getMaxProductsByZone().get(zone)));
        }
        return new LayoutPolicyView("Weighted utility (snapshot)",
                "Sum of distinct products' utility_matrix_item.weighted_utility in the shared snapshot",
                "UNCONFIGURED_LIMITS_ARE_UNBOUNDED", List.copyOf(result));
    }

    public List<String> capacityErrors(List<PlacementBlock> blocks) {
        var blockCounts = new java.util.HashMap<String, Integer>();
        var productCounts = new java.util.HashMap<String, Integer>();
        for (PlacementBlock block : blocks) {
            blockCounts.merge(block.zone(), 1, Integer::sum);
            productCounts.merge(block.zone(), block.productIds().size(), Integer::sum);
        }
        var errors = new ArrayList<String>();
        checkCaps("blocks", blockCounts, properties.getMaxBlocksByZone(), errors);
        checkCaps("products", productCounts, properties.getMaxProductsByZone(), errors);
        return List.copyOf(errors);
    }

    public void requireCapacity(List<PlacementBlock> blocks) {
        List<String> errors = capacityErrors(blocks);
        if (!errors.isEmpty()) throw new ApiException(400, String.join("; ", errors));
    }

    private static void checkCaps(String label, Map<String, Integer> counts,
            Map<String, Integer> caps, List<String> errors) {
        counts.forEach((zone, count) -> {
            Integer cap = caps.get(zone);
            if (cap != null && count > cap) errors.add(zone + " exceeds configured " + label + " capacity " + cap);
        });
    }

    private static void validateConfiguredCaps(Map<String, Integer> caps) {
        caps.forEach((zone, cap) -> {
            if (!(BASELINE.equals(zone) || java.util.Arrays.stream(RecType.values())
                    .anyMatch(type -> type.name().equals(zone)))) {
                throw new IllegalStateException("Unsupported layout zone in paspm.layout capacity settings: " + zone);
            }
            if (cap == null || cap < 1) {
                throw new IllegalStateException("Layout capacity values must be positive for zone " + zone);
            }
        });
    }
}

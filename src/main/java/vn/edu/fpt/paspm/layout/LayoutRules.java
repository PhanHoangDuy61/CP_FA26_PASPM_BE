package vn.edu.fpt.paspm.layout;

import java.util.ArrayList;
import java.util.EnumMap;
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

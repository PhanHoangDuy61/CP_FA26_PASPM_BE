package vn.edu.fpt.paspm.storefront;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.layout.LayoutRules;
import vn.edu.fpt.paspm.recommendation.ProductEligibilityService;
import vn.edu.fpt.paspm.recommendation.RecommendationContracts.EligibilityStatus;
import vn.edu.fpt.paspm.recommendation.RecommendationContracts.RecPolicy;
import vn.edu.fpt.paspm.utility.UtilitySettingsRepository;

@Service
public class StorefrontBundleService {
    public enum Origin { MINED, MANUAL }
    public record Bundle(long campaignId, long layoutId, long layoutVersion, int blockNo,
            List<Integer> productIds, Origin origin) {}

    private final StorefrontRepository storefront;
    private final BundleKeys keys;
    private final LayoutRules layoutRules;
    private final ProductEligibilityService eligibility;
    private final UtilitySettingsRepository json;

    StorefrontBundleService(StorefrontRepository storefront, BundleKeys keys, LayoutRules layoutRules,
            ProductEligibilityService eligibility, UtilitySettingsRepository json) {
        this.storefront = storefront;
        this.keys = keys;
        this.layoutRules = layoutRules;
        this.eligibility = eligibility;
        this.json = json;
    }

    // Called while the cart transaction holds the Store row lock used by campaign activation and ending.
    public Bundle resolve(int storeId, String key) {
        var ref = keys.decode(key);
        if (ref.storeId() != storeId) throw BundleKeys.notFound();
        var active = storefront.activeLayout(storeId);
        if (active == null || !"B".equals(active.role()) || !"READY".equals(active.status())
                || ref.campaignId() != active.campaignId() || ref.layoutId() != active.layoutId()
                || ref.layoutVersion() != active.layoutVersion()) {
            throw unavailable();
        }
        layoutRules.requireConfiguredPublicCapacities();
        var products = storefront.bundleProducts(storeId, ref.layoutId(), ref.layoutVersion(), ref.blockNo());
        if (products.isEmpty()) throw unavailable();
        Origin origin = classify(storeId, active.snapshotId(), products);
        return new Bundle(ref.campaignId(), ref.layoutId(), ref.layoutVersion(), ref.blockNo(), products, origin);
    }

    private Origin classify(int storeId, int snapshotId, List<Integer> products) {
        var sorted = products.stream().sorted().toList();
        String canonicalKey = sorted.stream().map(String::valueOf).collect(Collectors.joining(","));
        var candidates = storefront.approvedBundleCandidates(storeId, snapshotId, canonicalKey);
        var candidateItems = storefront.recommendationProducts(storeId, candidates.stream()
                .map(StorefrontRepository.RecommendationRow::id).toList());
        long ready = candidates.stream()
                .filter(candidate -> sorted.equals(candidateItems.get(candidate.id())))
                .filter(candidate -> eligibility.check(storeId, sorted, json.read(candidate.policy(), RecPolicy.class))
                        .status() == EligibilityStatus.ELIGIBLE)
                .limit(2).count();
        return ready == 1 ? Origin.MINED : Origin.MANUAL;
    }

    private static ApiException unavailable() {
        return new ApiException(409, "The bundle is no longer current; refresh the storefront", "BUNDLE_UNAVAILABLE");
    }
}

package vn.edu.fpt.paspm.recommendation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import vn.edu.fpt.paspm.recommendation.RecommendationContracts.EligibilityStatus;

public final class RecommendationVersionedContracts {
    private RecommendationVersionedContracts() {}
    public interface EligibilityEvidence {
        EligibilityStatus status(); List<String> warnings(); String fingerprint();
    }
    public record ProductEligibilityV2(int productId,String productStatus,long sellableQuantity,
        EligibilityStatus status,BigDecimal expiryPriorityScore,String expiryStatus,List<String> warnings) {}
    public record EligibilityV2(String scoringVersion,EligibilityStatus status,List<ProductEligibilityV2> products,
        Instant checkedAt,LocalDate asOfDate,String timeZone,List<String> warnings,String fingerprint)
        implements EligibilityEvidence {
        public EligibilityV2 { if(!vn.edu.fpt.paspm.utility.UtilityVersionedContracts.V2.equals(scoringVersion))throw new IllegalArgumentException("Unsupported scoringVersion"); }
    }
}

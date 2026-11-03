package vn.edu.fpt.paspm.recommendation;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.mining.MiningInputBuilder;
import vn.edu.fpt.paspm.utility.*;
import static vn.edu.fpt.paspm.recommendation.RecommendationContracts.*;

/** Current F02 sellability, deliberately independent of historical stock/Weight. */
@Service
public class ProductEligibilityService {
    private final UtilityProductRepository products;private final ExpiryScoreCalculator expiry;private final UtilityTimeZone timeZone;
    public ProductEligibilityService(UtilityProductRepository products,ExpiryScoreCalculator expiry,UtilityTimeZone timeZone){this.products=products;this.expiry=expiry;this.timeZone=timeZone;}
    public RecEligibility check(int store,List<Integer> ids,RecPolicy policy) {
        var date=timeZone.today();var checkedAt=Instant.now();var results=new ArrayList<RecProductEligibility>();var warnings=new ArrayList<String>();
        var fingerprint=new StringBuilder(date+"|"+policy.minimumSellableQuantity());EligibilityStatus overall=EligibilityStatus.ELIGIBLE;
        for(int id:ids) {
            UtilityProductRepository.Product product;
            try {product=products.product(store,id);}catch(ApiException e){
                if(e.status()!=404)throw e;
                results.add(new RecProductEligibility(id,"UNAVAILABLE",0,EligibilityStatus.INELIGIBLE,null,"UNAVAILABLE",0,List.of("Product unavailable")));
                overall=EligibilityStatus.INELIGIBLE;fingerprint.append('|').append(id).append(":UNAVAILABLE");continue;
            }
            var batches=products.batches(store,id);long sellable=0;boolean missing=false;
            fingerprint.append('|').append(id).append(':').append(product.status()).append(':').append(product.requiresExpiry()).append(':').append(product.expiryPushDays());
            for(var batch:batches) {
                fingerprint.append('|').append(batch.batchId()).append(':').append(batch.status()).append(':').append(batch.remainingQuantity()).append(':').append(batch.expiryDate());
                if(!"ACTIVE".equals(batch.status())||batch.remainingQuantity()<=0)continue;
                if(product.requiresExpiry()&&batch.expiryDate()==null){missing=true;continue;}
                if(batch.expiryDate()==null||batch.expiryDate().isAfter(date))sellable+=batch.remainingQuantity();
            }
            var score=expiry.calculate(id,product.requiresExpiry(),batches,date,product.expiryPushDays());
            var messages=new ArrayList<String>();EligibilityStatus status;
            if(!"ACTIVE".equals(product.status())){status=EligibilityStatus.INELIGIBLE;messages.add("Product inactive");}
            else if(missing){status=EligibilityStatus.NEEDS_RECHECK;messages.add("Missing required batch expiry metadata");}
            else if(sellable<policy.minimumSellableQuantity()){status=EligibilityStatus.INELIGIBLE;messages.add("Insufficient current sellable inventory");}
            else status=EligibilityStatus.ELIGIBLE;
            if(status==EligibilityStatus.INELIGIBLE||overall==EligibilityStatus.INELIGIBLE)overall=EligibilityStatus.INELIGIBLE;
            else if(status==EligibilityStatus.NEEDS_RECHECK)overall=EligibilityStatus.NEEDS_RECHECK;
            for(String message:messages)warnings.add("Product "+id+": "+message);
            results.add(new RecProductEligibility(id,product.status(),sellable,status,score.score(),score.status(),product.expiryPushDays(),List.copyOf(messages)));
        }
        return new RecEligibility(overall,List.copyOf(results),checkedAt,date,timeZone.id(),List.copyOf(warnings),MiningInputBuilder.sha(fingerprint.toString()));
    }
}

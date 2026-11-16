package vn.edu.fpt.paspm.recommendation;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.mining.MiningInputBuilder;
import vn.edu.fpt.paspm.utility.*;
import vn.edu.fpt.paspm.recommendation.RecommendationVersionedContracts.*;
import static vn.edu.fpt.paspm.recommendation.RecommendationContracts.*;

/**
 * Current F02 sellability, deliberately independent of historical stock/Weight. W2: expiryPriorityScore is the
 * current U_expiry; the fingerprint covers batch push days and no longer the removed product push-day threshold, so an older
 * recommendation reports inventoryChanged=true once after the upgrade (accepted).
 */
@Service
public class ProductEligibilityService {
    private final UtilityProductRepository products;private final ExpiryRiskCalculator risk;private final UtilityTimeZone timeZone;
    private final UtilitySettingsRepository settings;
    public ProductEligibilityService(UtilityProductRepository products,ExpiryRiskCalculator risk,UtilityTimeZone timeZone,UtilitySettingsRepository settings){
        this.products=products;this.risk=risk;this.timeZone=timeZone;this.settings=settings;
    }
    public EligibilityV2 check(int store,List<Integer> ids,RecPolicy policy) {
        var date=timeZone.today();var checkedAt=Instant.now();var results=new ArrayList<ProductEligibilityV2>();var warnings=new ArrayList<String>();
        int window=settings.config(store,false).salesWindowDays();
        var from=date.minusDays(window-1L).atStartOfDay(timeZone.zone()).toInstant();var to=date.plusDays(1).atStartOfDay(timeZone.zone()).toInstant();
        var fingerprint=new StringBuilder(UtilityVersionedContracts.V2+"|"+date+"|"+policy.minimumSellableQuantity());EligibilityStatus overall=EligibilityStatus.ELIGIBLE;
        for(int id:ids) {
            UtilityProductRepository.Product product;
            try {product=products.product(store,id);}catch(ApiException e){
                if(e.status()!=404)throw e;
                results.add(new ProductEligibilityV2(id,"UNAVAILABLE",0,EligibilityStatus.INELIGIBLE,null,"UNAVAILABLE",List.of("Product unavailable")));
                overall=EligibilityStatus.INELIGIBLE;fingerprint.append('|').append(id).append(":UNAVAILABLE");continue;
            }
            var batches=products.riskBatches(store,id);long sellable=0;boolean missing=false;
            fingerprint.append('|').append(id).append(':').append(product.status()).append(':').append(product.requiresExpiry());
            for(var batch:batches) {
                fingerprint.append('|').append(batch.batchId()).append(':').append(batch.status()).append(':').append(batch.remainingQuantity()).append(':').append(batch.expiryDate()).append(':').append(batch.pushDay());
                if(!"ACTIVE".equals(batch.status())||batch.remainingQuantity()<=0)continue;
                if(product.requiresExpiry()&&batch.expiryDate()==null){missing=true;continue;}
                if(batch.expiryDate()==null||batch.expiryDate().isAfter(date))sellable+=batch.remainingQuantity();
            }
            var score=risk.calculate(product.requiresExpiry(),"ACTIVE".equals(product.status())?batches:List.of(),date,window,products.unitsSold(store,id,from,to));
            var messages=new ArrayList<String>();EligibilityStatus status;
            if(!"ACTIVE".equals(product.status())){status=EligibilityStatus.INELIGIBLE;messages.add("Product inactive");}
            else if(missing){status=EligibilityStatus.NEEDS_RECHECK;messages.add("Missing required batch expiry metadata");}
            else if(sellable<policy.minimumSellableQuantity()){status=EligibilityStatus.INELIGIBLE;messages.add("Insufficient current sellable inventory");}
            else status=EligibilityStatus.ELIGIBLE;
            if(!score.batchesMissingPushDay().isEmpty())messages.add("Batches "+score.batchesMissingPushDay()+" have no push day");
            if(status==EligibilityStatus.INELIGIBLE||overall==EligibilityStatus.INELIGIBLE)overall=EligibilityStatus.INELIGIBLE;
            else if(status==EligibilityStatus.NEEDS_RECHECK)overall=EligibilityStatus.NEEDS_RECHECK;
            for(String message:messages)warnings.add("Product "+id+": "+message);
            results.add(new ProductEligibilityV2(id,product.status(),sellable,status,score.score(),score.status(),List.copyOf(messages)));
        }
        return new EligibilityV2(UtilityVersionedContracts.V2,overall,List.copyOf(results),checkedAt,date,timeZone.id(),List.copyOf(warnings),MiningInputBuilder.sha(fingerprint.toString()));
    }
}

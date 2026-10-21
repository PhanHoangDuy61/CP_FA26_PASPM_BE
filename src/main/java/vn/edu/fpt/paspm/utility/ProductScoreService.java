package vn.edu.fpt.paspm.utility;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.UtilityContracts.*;

@Service
public class ProductScoreService {
    private final ProductScoreRepository repository;
    private final CatalogAccess access;
    private final UtilityProductRepository products;
    private final UtilitySettingsRepository settings;
    private final ExpiryScoreCalculator expiry;
    private final UtilityTimeZone timeZone;
    public ProductScoreService(ProductScoreRepository repository,CatalogAccess access,UtilityProductRepository products,
            UtilitySettingsRepository settings,ExpiryScoreCalculator expiry,UtilityTimeZone timeZone) {
        this.repository=repository;this.access=access;this.products=products;
        this.settings=settings;this.expiry=expiry;this.timeZone=timeZone;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Scores get(Jwt jwt,int id) { return find(access.currentStoreId(jwt),id); }
    private Scores find(int store,int id) {
        var product=products.product(store,id);
        var config=settings.config(store,false);
        var policy=settings.policy(false);
        var date=timeZone.today();
        var result=expiry.calculate(product.id(),product.requiresExpiry(),
            "ACTIVE".equals(product.status())?products.batches(store,id):List.of(),date,product.expiryPushDays());
        var warnings=new ArrayList<String>();
        if("MISSING_METADATA".equals(result.status()))warnings.add("Missing required expiry metadata");
        if("NO_SELLABLE_BATCH".equals(result.status()))warnings.add("No sellable batch; current Weight is not proof of inventory availability");
        boolean lambdaValid=UtilityConfigurationService.validLambda(config.lambda(),policy);
        BigDecimal weight=null;
        if(!lambdaValid)warnings.add("Selected lambda is outside the current system policy; update the Store weight configuration");
        else {
            try {
                if(config.enableExpiry()&&"MISSING_METADATA".equals(result.status()))
                    throw new ApiException(400,"Missing enabled expiry priority metadata");
                weight=UtilityMath.weight(config,product.stockScore(),result.score(),product.seasonalScore());
            } catch(ApiException e) { warnings.add(e.getMessage()); }
        }
        return new Scores(id,product.stockScore(),product.seasonalScore(),result.score(),weight,result.status(),
            result.evidence(),product.expiryPushDays(),date,timeZone.id(),config.version(),lambdaValid,weight!=null,List.copyOf(warnings));
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public Scores patch(Jwt jwt,int id,ScorePatch request) {
        if(request.getStockPriorityScore()==null&&request.getSeasonalPriorityScore()==null)throw new ApiException(400,"At least one score must be provided");
        int store=access.currentStoreId(jwt);
        if(!repository.update(store,id,request))throw new ApiException(404,"Product not found");
        return find(store,id);
    }
}

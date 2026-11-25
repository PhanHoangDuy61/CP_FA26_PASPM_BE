package vn.edu.fpt.paspm.utility;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.utility.UtilityContracts.*;

/** Current (not frozen) W2 preview for one product on the Store-local today. */
@Service
public class ProductScoreService {
    private final CatalogAccess access;
    private final UtilityProductRepository products;
    private final UtilitySettingsRepository settings;
    private final WeightV2Calculator weights;
    private final UtilityTimeZone timeZone;
    public ProductScoreService(CatalogAccess access,UtilityProductRepository products,UtilitySettingsRepository settings,
            WeightV2Calculator weights,UtilityTimeZone timeZone) {
        this.access=access;this.products=products;this.settings=settings;this.weights=weights;this.timeZone=timeZone;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Scores get(Jwt jwt,int id) {
        int store=access.currentStoreId(jwt);
        var product=products.product(store,id);
        var config=settings.config(store,false);
        var policy=settings.policy(false);
        var date=timeZone.today();
        var active=weights.activeSeasons(store,date);
        var units=weights.unitsSold(store,date,config.salesWindowDays());
        var result=weights.product(store,product,date,config,units,WeightV2Calculator.seasonScores(active));
        var warnings=new ArrayList<String>(result.warnings());
        boolean lambdaValid=UtilityConfigurationService.validLambda(config.lambda(),policy);
        BigDecimal weight=result.weight();
        if(!lambdaValid) { weight=null;warnings.add("Selected lambda is outside the current system policy [0, lambdaMax]; update the Store weight configuration"); }
        var seasons=active.stream().filter(s->s.productIds().contains(id)).toList();
        return new Scores(id,result.expiry().score(),result.season().weight(),result.season().seasonId(),weight,result.expiry().status(),
            result.expiry().evidence(),result.expiry().batchesMissingPushDay(),seasons,date,timeZone.id(),config.version(),config.scoringVersion(),
            lambdaValid,weight!=null,List.copyOf(warnings));
    }
}

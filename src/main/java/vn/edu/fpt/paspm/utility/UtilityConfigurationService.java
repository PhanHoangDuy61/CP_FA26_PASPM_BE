package vn.edu.fpt.paspm.utility;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.UtilityContracts.*;

@Service
public class UtilityConfigurationService {
    private final UtilitySettingsRepository settings;
    private final CatalogAccess access;
    public UtilityConfigurationService(UtilitySettingsRepository settings,CatalogAccess access) { this.settings=settings;this.access=access; }
    @Transactional(readOnly=true) public LambdaPolicy policy() { return settings.policy(false); }
    @Transactional public LambdaPolicy updatePolicy(Jwt jwt,PolicyRequest request) {
        if(request.lambdaMax().compareTo(request.lambdaMin())<0) throw new ApiException(400,"lambdaMax must be at least lambdaMin");
        settings.policy(true);
        settings.policy(request,Integer.parseInt(jwt.getSubject()));
        return settings.policy(false);
    }
    @Transactional(readOnly=true) public ConfigView config(Jwt jwt) {
        return view(settings.config(access.currentStoreId(jwt),false),settings.policy(false));
    }
    @Transactional public ConfigView weights(Jwt jwt,WeightRequest request) {
        int store=access.currentStoreId(jwt);
        var policy=settings.policy(true);
        var old=settings.config(store,true);
        BigDecimal sum=BigDecimal.ZERO;
        if(request.enableStock())sum=sum.add(request.alpha());
        if(request.enableExpiry())sum=sum.add(request.beta());
        if(request.enableSeason())sum=sum.add(request.gamma());
        if((request.enableStock()||request.enableExpiry()||request.enableSeason())&&sum.compareTo(BigDecimal.ONE)!=0)
            throw new ApiException(400,"Enabled coefficients must sum exactly to 1");
        requireLambda(request.lambda(),policy,400);
        var config=new WeightConfig(request.enableStock(),request.enableExpiry(),request.enableSeason(),request.alpha(),request.beta(),request.gamma(),request.lambda(),old.expiryWindowDays(),old.version()+1,old.scoringVersion());
        settings.config(store,config); return view(config,policy);
    }
    @Transactional public ConfigView expiryWindow(Jwt jwt,ExpiryWindowRequest request) {
        int days=request.expiryWindowDays();
        int store=access.currentStoreId(jwt);
        var policy=settings.policy(true);
        var old=settings.config(store,true);
        var config=new WeightConfig(old.enableStock(),old.enableExpiry(),old.enableSeason(),old.alpha(),old.beta(),old.gamma(),old.lambda(),days,old.version()+1,old.scoringVersion());
        settings.config(store,config);return view(config,policy);
    }
    public static boolean validLambda(BigDecimal lambda,LambdaPolicy policy) {
        return lambda.compareTo(policy.lambdaMin())>=0&&lambda.compareTo(policy.lambdaMax())<=0;
    }
    public static void requireLambda(BigDecimal lambda,LambdaPolicy policy,int status) {
        if(!validLambda(lambda,policy))throw new ApiException(status,"Selected lambda is outside the current system policy; update the Store weight configuration");
    }
    private ConfigView view(WeightConfig config,LambdaPolicy policy) { return new ConfigView(config,policy,validLambda(config.lambda(),policy)); }
}

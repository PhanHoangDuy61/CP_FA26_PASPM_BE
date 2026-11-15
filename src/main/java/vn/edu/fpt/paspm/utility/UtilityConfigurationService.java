package vn.edu.fpt.paspm.utility;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.UtilityContracts.*;
import vn.edu.fpt.paspm.utility.UtilityVersionedContracts.*;

@Service
public class UtilityConfigurationService {
    private final UtilitySettingsRepository settings;
    private final CatalogAccess access;
    public UtilityConfigurationService(UtilitySettingsRepository settings,CatalogAccess access) { this.settings=settings;this.access=access; }
    @Transactional(readOnly=true) public LambdaV2 policy() { return settings.policy(false); }
    /** Only the System Admin changes lambdaMax. Store settings and frozen snapshots are not rewritten. */
    @Transactional public LambdaV2 updatePolicy(Jwt jwt,PolicyUpdate request) {
        settings.policy(true);
        settings.policy(new PolicyPayloadV2(UtilityVersionedContracts.V2,request.lambdaMax()),Integer.parseInt(jwt.getSubject()));
        return settings.policy(false);
    }
    @Transactional(readOnly=true) public ConfigView config(Jwt jwt) {
        return view(settings.config(access.currentStoreId(jwt),false),settings.policy(false));
    }
    @Transactional public ConfigView weights(Jwt jwt,WeightUpdate request) {
        int store=access.currentStoreId(jwt);
        var policy=settings.policy(true);
        var old=settings.config(store,true);
        if(request.alpha().add(request.beta()).compareTo(BigDecimal.ONE)!=0)throw new ApiException(400,"alpha + beta must equal exactly 1");
        requireLambda(request.lambda(),policy,400);
        int window=request.salesWindowDays()==null?old.salesWindowDays():request.salesWindowDays();
        var config=new WeightV2(request.alpha(),request.beta(),request.lambda(),window,old.version()+1,UtilityVersionedContracts.V2);
        settings.config(store,config);return view(config,policy);
    }
    /** Q3: the permitted range is [0, lambdaMax]. */
    public static boolean validLambda(BigDecimal lambda,PolicyValues policy) {
        return lambda!=null&&lambda.signum()>=0&&lambda.compareTo(policy.lambdaMax())<=0;
    }
    public static void requireLambda(BigDecimal lambda,PolicyValues policy,int status) {
        if(!validLambda(lambda,policy))throw new ApiException(status,"Selected lambda is outside the current system policy [0, lambdaMax]; update the Store weight configuration");
    }
    private ConfigView view(WeightV2 config,LambdaV2 policy) { return new ConfigView(config,policy,validLambda(config.lambda(),policy)); }
}

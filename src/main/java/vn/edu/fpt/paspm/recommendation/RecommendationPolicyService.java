package vn.edu.fpt.paspm.recommendation;

import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.UtilitySettingsRepository;
import static vn.edu.fpt.paspm.recommendation.RecommendationContracts.*;

@Service
public class RecommendationPolicyService {
    public static final String RULE_VERSION="F06_RULES_V1_1";
    private final JdbcTemplate jdbc;private final UtilitySettingsRepository json;
    private final CatalogAccess access;private final AuthService auth;
    public RecommendationPolicyService(JdbcTemplate jdbc,UtilitySettingsRepository json,CatalogAccess access,AuthService auth){this.jdbc=jdbc;this.json=json;this.access=access;this.auth=auth;}
    public RecPolicy current(int store) {return current(store,false);}
    private RecPolicy current(int store,boolean lock) {
        return jdbc.query("SELECT config_json FROM recommendation_rule_config WHERE store_id=?"+(lock?" FOR UPDATE":""),(r,n)->json.read(r.getString(1),RecPolicy.class),store)
            .stream().findFirst().orElse(new RecPolicy(1,4,2,new BigDecimal("0.05"),new BigDecimal("0.50"),1,RULE_VERSION));
    }
    @PreAuthorize("hasAnyRole('STORE_ADMIN','MERCHANDISER')")
    public RecPolicy get(Jwt jwt){return current(access.currentStoreId(jwt));}
    void lock(int store){jdbc.queryForObject("SELECT store_id FROM store WHERE store_id=? FOR UPDATE",Integer.class,store);}
    @Transactional @PreAuthorize("hasRole('STORE_ADMIN')")
    public RecPolicy update(Jwt jwt,RecPolicyRequest request) {
        int store=access.currentStoreId(jwt);lock(store);var previous=current(store,true);
        if(previous.version()!=request.expectedVersion())throw new ApiException(409,"Rule configuration version changed");
        var policy=new RecPolicy(previous.version()+1,request.maxBundleSize(),request.minSupportCount(),request.minSupportRatio(),request.minConfidence(),request.minimumSellableQuantity(),RULE_VERSION);
        int updated=jdbc.update("UPDATE recommendation_rule_config SET config_version=?,config_json=?,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE store_id=?",policy.version(),json.write(policy),auth.current(jwt).id(),store);
        if(updated==0)jdbc.update("INSERT INTO recommendation_rule_config(store_id,config_version,config_json,updated_by) VALUES (?,?,?,?)",store,policy.version(),json.write(policy),auth.current(jwt).id());
        return policy;
    }
}

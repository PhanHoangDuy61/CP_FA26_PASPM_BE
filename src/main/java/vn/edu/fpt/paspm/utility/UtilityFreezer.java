package vn.edu.fpt.paspm.utility;

import java.time.LocalDate;
import org.springframework.stereotype.Component;
import vn.edu.fpt.paspm.utility.UtilityVersionedContracts.*;

/**
 * Freezes the W2 configuration for preview, manual snapshots and scheduled snapshots (one rule for all, P3).
 * Lock order: lambda policy, then Store config. Callers must hold a transaction.
 */
@Component
public class UtilityFreezer {
    public static final String ITEM_MAPPING="PRODUCT_ID_V1";
    private final UtilitySettingsRepository settings;
    private final WeightV2Calculator weights;
    private final UtilityTimeZone timeZone;
    public UtilityFreezer(UtilitySettingsRepository settings,WeightV2Calculator weights,UtilityTimeZone timeZone) {
        this.settings=settings;this.weights=weights;this.timeZone=timeZone;
    }
    public FrozenV2 freeze(int store,LocalDate analysisDate,String source) {
        var policy=settings.policy(true);
        var config=settings.config(store,true);
        UtilityConfigurationService.requireLambda(config.lambda(),policy,409);
        return new FrozenV2(config,policy,new CoefficientsV2(config.alpha(),config.beta()),analysisDate,source,
            UtilityMath.SCALE,"HALF_UP",2,"HALF_UP",ITEM_MAPPING,timeZone.id(),weights.activeSeasons(store,analysisDate));
    }
}

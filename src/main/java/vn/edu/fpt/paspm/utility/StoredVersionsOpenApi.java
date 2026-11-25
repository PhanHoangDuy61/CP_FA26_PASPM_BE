package vn.edu.fpt.paspm.utility;

import io.swagger.v3.core.converter.ModelConverters;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Legacy stored shapes are reachable only through oneOf, so springdoc does not emit them by itself and the $ref
 * would dangle. Register every versioned record explicitly (old snapshots, matrix rows and recommendations).
 */
@Configuration(proxyBeanMethods=false)
public class StoredVersionsOpenApi {
    @Bean OpenApiCustomizer storedVersionSchemas() {
        return api -> {
            for(Class<?> type:new Class<?>[]{ExpiryScoreCalculator.Evidence.class,UtilityVersionedContracts.FefoEvidence.class,
                    UtilityMatrixContracts.FrozenConfiguration.class,UtilityVersionedContracts.FrozenV2.class,
                    vn.edu.fpt.paspm.recommendation.RecommendationContracts.RecEligibility.class,
                    vn.edu.fpt.paspm.recommendation.RecommendationVersionedContracts.EligibilityV2.class})
                ModelConverters.getInstance().readAll(type).forEach((name,schema)->api.getComponents().getSchemas().putIfAbsent(name,schema));
        };
    }
}

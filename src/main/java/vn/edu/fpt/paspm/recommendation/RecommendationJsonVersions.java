package vn.edu.fpt.paspm.recommendation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static vn.edu.fpt.paspm.recommendation.RecommendationVersionedContracts.*;
import static vn.edu.fpt.paspm.utility.UtilityVersionedContracts.V2;

@Configuration(proxyBeanMethods=false)
public class RecommendationJsonVersions {
    @Bean SimpleModule recommendationVersionReaders() { return module(); }
    public static SimpleModule module() {
        var module=new SimpleModule("paspm-recommendation-stored-versions");
        module.addDeserializer(EligibilityEvidence.class,new JsonDeserializer<>() {
            @Override public EligibilityEvidence deserialize(JsonParser parser,DeserializationContext context) throws IOException {
                JsonNode node=parser.getCodec().readTree(parser);
                var version=node.path("scoringVersion");
                if(version.isMissingNode()||version.isNull())return parser.getCodec().treeToValue(node,RecommendationContracts.RecEligibility.class);
                if(version.isTextual()&&V2.equals(version.asText()))return parser.getCodec().treeToValue(node,EligibilityV2.class);
                throw JsonMappingException.from(parser,"Unsupported recommendation eligibility scoringVersion");
            }
        });
        return module;
    }
}

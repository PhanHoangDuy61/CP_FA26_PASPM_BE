package vn.edu.fpt.paspm.utility;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.util.function.Function;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static vn.edu.fpt.paspm.utility.UtilityVersionedContracts.*;

@Configuration(proxyBeanMethods=false)
public class UtilityJsonVersions {
    @Bean SimpleModule utilityVersionReaders() { return module(); }
    public static SimpleModule module() {
        var module=new SimpleModule("paspm-utility-stored-versions");
        module.addDeserializer(WeightSettings.class,reader(n->v2(n)?WeightV2.class:UtilityContracts.WeightConfig.class));
        module.addDeserializer(PolicyPayload.class,reader(n->v2(n)?PolicyPayloadV2.class:UtilityContracts.PolicyRequest.class));
        module.addDeserializer(PolicyValues.class,reader(n->v2(n)?LambdaV2.class:UtilityContracts.LambdaPolicy.class));
        module.addDeserializer(FrozenSettings.class,reader(n->v2(n.path("config"))?FrozenV2.class:UtilityMatrixContracts.FrozenConfiguration.class));
        module.addDeserializer(ExpiryEvidence.class,reader(n->v2(n)?FefoEvidence.class:ExpiryScoreCalculator.Evidence.class));
        return module;
    }
    private static boolean v2(JsonNode node) {
        var version=node.path("scoringVersion");
        if(version.isMissingNode()||version.isNull())return false;
        if(!version.isTextual())throw new IllegalArgumentException("Invalid scoringVersion");
        return switch(version.asText()) {
            case V2 -> true;
            case "F03_LINEAR_V1",ExpiryScoreCalculator.SCORING_VERSION -> false;
            default -> throw new IllegalArgumentException("Unsupported persisted scoringVersion: "+version.asText());
        };
    }
    private static <T> JsonDeserializer<T> reader(Function<JsonNode,Class<? extends T>> select) {
        return new JsonDeserializer<>() {
            @Override public T deserialize(JsonParser parser,DeserializationContext context) throws IOException {
                JsonNode node=parser.getCodec().readTree(parser);
                try { return parser.getCodec().treeToValue(node,select.apply(node)); }
                catch(IllegalArgumentException e) { throw JsonMappingException.from(parser,e.getMessage(),e); }
            }
        };
    }
}

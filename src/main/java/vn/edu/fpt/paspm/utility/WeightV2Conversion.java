package vn.edu.fpt.paspm.utility;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Owner decision 2026-11-15: one-time conversion of a live Store utility_config from the three-weight
 * model to W2_EXPIRY_SEASON_V1. Frozen JSON (snapshots, mining requests, recommendations) is never passed here.
 */
public final class WeightV2Conversion {
    public static final String RULE="OWNER_2026_11_15_V1";
    public static final int DEFAULT_SALES_WINDOW_DAYS=30;
    private static final BigDecimal HALF=new BigDecimal("0.5");
    private static final ObjectMapper JSON=new ObjectMapper().enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private WeightV2Conversion() {}

    public record Result(boolean hadConfig,BigDecimal oldStockCoefficient,BigDecimal oldExpiryCoefficient,
        BigDecimal oldSeasonCoefficient,BigDecimal oldLambda,BigDecimal alpha,BigDecimal beta,BigDecimal lambda,
        boolean lambdaClamped,boolean defaultCoefficients,long version,String json) {}

    /** @param raw live store.utility_config, or null when the Store never saved one */
    public static Result convert(String raw,BigDecimal lambdaMax) {
        if(lambdaMax==null||lambdaMax.signum()<0)throw new IllegalArgumentException("lambdaMax must be nonnegative");
        JsonNode node;
        try { node=raw==null?null:JSON.readTree(raw); }
        catch(java.io.IOException e) { throw new IllegalArgumentException("Store utility_config is not valid JSON",e); }
        node=unwrap(node);
        if(node!=null&&(node.isNull()||node.isMissingNode()))node=null;
        if(node!=null&&!node.isObject())throw new IllegalArgumentException("Store utility_config must be a JSON object");
        if(node!=null&&UtilityVersionedContracts.V2.equals(node.path("scoringVersion").asText(null)))
            throw new IllegalArgumentException("Store utility_config is already "+UtilityVersionedContracts.V2);
        boolean had=node!=null;
        // Legacy mapping (UtilityContracts.WeightConfig): alpha=stock, beta=expiry, gamma=season.
        BigDecimal stock=had&&flag(node,"enableStock")?number(node,"alpha"):BigDecimal.ZERO;
        BigDecimal expiry=had&&flag(node,"enableExpiry")?number(node,"beta"):BigDecimal.ZERO;
        BigDecimal season=had&&flag(node,"enableSeason")?number(node,"gamma"):BigDecimal.ZERO;
        BigDecimal oldLambda=had?number(node,"lambda"):BigDecimal.ZERO;
        BigDecimal sum=expiry.add(season);
        boolean defaults=sum.signum()==0;
        BigDecimal alpha=defaults?HALF:expiry.divide(sum,6,RoundingMode.HALF_UP);
        BigDecimal beta=BigDecimal.ONE.subtract(alpha);
        BigDecimal lambda=oldLambda.signum()<0?BigDecimal.ZERO:oldLambda.min(lambdaMax);
        boolean clamped=lambda.compareTo(oldLambda)!=0;
        // A missing Store config was presented as WeightConfig.defaults() (version 1).
        long version=(had?node.path("version").asLong(0):1)+1;
        var out=JSON.createObjectNode();
        out.put("alpha",plain(alpha));out.put("beta",plain(beta));out.put("lambda",plain(lambda));
        out.put("salesWindowDays",DEFAULT_SALES_WINDOW_DAYS);out.put("version",version);
        out.put("scoringVersion",UtilityVersionedContracts.V2);
        try {
            return new Result(had,had?stock:null,had?expiry:null,had?season:null,had?oldLambda:null,plain(alpha),plain(beta),plain(lambda),
                clamped,defaults,version,JSON.writeValueAsString(out));
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    /** H2 stores a JSON value bound as a string as a JSON string literal; the app reader unwraps it the same way. */
    public static JsonNode unwrap(JsonNode node) {
        if(node==null||!node.isTextual())return node;
        try { return JSON.readTree(node.asText()); }
        catch(java.io.IOException e) { throw new IllegalArgumentException("Stored JSON text is not valid JSON",e); }
    }
    private static boolean flag(JsonNode node,String field) {
        var value=node.path(field);
        if(value.isMissingNode()||value.isNull())return false;
        if(!value.isBoolean())throw new IllegalArgumentException("Store utility_config."+field+" must be boolean");
        return value.booleanValue();
    }
    private static BigDecimal number(JsonNode node,String field) {
        var value=node.path(field);
        if(value.isMissingNode()||value.isNull())return BigDecimal.ZERO;
        if(!value.isNumber())throw new IllegalArgumentException("Store utility_config."+field+" must be numeric");
        var number=value.decimalValue();
        if(number.signum()<0)throw new IllegalArgumentException("Store utility_config."+field+" must be nonnegative");
        return number;
    }
    private static BigDecimal plain(BigDecimal value) {
        var stripped=value.stripTrailingZeros();
        return stripped.scale()<0?stripped.setScale(0):stripped;
    }
}

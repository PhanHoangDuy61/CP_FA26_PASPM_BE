package vn.edu.fpt.paspm.utility;

import java.math.BigDecimal;
import java.math.RoundingMode;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.UtilityContracts.WeightConfig;

public final class UtilityMath {
    public static final int SCALE=6;
    private UtilityMath() {}
    public static BigDecimal base(int quantity,BigDecimal unitPrice,BigDecimal unitCost,BigDecimal sourceUtility) {
        if(quantity<=0)throw new ApiException(400,"Prepared item quantity must be positive");
        if(sourceUtility!=null)return utility(sourceUtility);
        if(unitPrice==null||unitCost==null||unitPrice.signum()<0||unitCost.signum()<0)
            throw new ApiException(400,"Historical unit price and cost are required and must be nonnegative");
        return utility(unitPrice.subtract(unitCost).multiply(BigDecimal.valueOf(quantity)));
    }
    public static BigDecimal weight(WeightConfig c,BigDecimal stock,BigDecimal expiry,BigDecimal season) {
        BigDecimal priority=BigDecimal.ZERO;
        if(c.enableStock())priority=priority.add(c.alpha().multiply(score(stock,"stock")));
        if(c.enableExpiry())priority=priority.add(c.beta().multiply(score(expiry,"expiry")));
        if(c.enableSeason())priority=priority.add(c.gamma().multiply(score(season,"seasonal")));
        BigDecimal weight=BigDecimal.ONE.add(c.lambda().multiply(priority)).setScale(18,RoundingMode.HALF_UP);
        if(weight.precision()-weight.scale()>20)throw new ApiException(400,"Weight exceeds supported decimal precision");
        return weight;
    }
    /**
     * W2_EXPIRY_SEASON_V1: W = 1 + lambda * (alpha * riskNum/riskDen + beta * m), computed exactly and rounded
     * half-up to 2 decimals once (paspm-experiments src/weighting.py, assumption A1). 1 <= W <= round(1 + lambda, 2).
     */
    public static BigDecimal weightV2(BigDecimal lambda,BigDecimal alpha,BigDecimal beta,
            java.math.BigInteger riskNum,java.math.BigInteger riskDen,BigDecimal m) {
        if(lambda==null||alpha==null||beta==null||m==null||riskNum==null||riskDen==null)throw new ApiException(400,"Weight input is incomplete");
        if(lambda.signum()<0||alpha.signum()<0||beta.signum()<0||alpha.add(beta).compareTo(BigDecimal.ONE)!=0)throw new ApiException(400,"Invalid Weight coefficients");
        if(m.signum()<0||m.compareTo(BigDecimal.ONE)>0)throw new ApiException(400,"Invalid season weight");
        if(riskDen.signum()<=0||riskNum.signum()<0||riskNum.compareTo(riskDen)>0)throw new ApiException(400,"Invalid expiry risk");
        BigDecimal d=new BigDecimal(riskDen);
        BigDecimal numerator=d.add(lambda.multiply(alpha.multiply(new BigDecimal(riskNum)).add(beta.multiply(m).multiply(d))));
        BigDecimal weight=numerator.divide(d,2,RoundingMode.HALF_UP);
        if(weight.precision()-weight.scale()>20)throw new ApiException(400,"Weight exceeds supported decimal precision");
        return weight;
    }
    private static BigDecimal score(BigDecimal value,String label) {
        if(value==null)throw new ApiException(400,"Missing enabled "+label+" priority score");
        if(value.signum()<0||value.compareTo(BigDecimal.ONE)>0)throw new ApiException(400,"Invalid "+label+" priority score");
        return value;
    }
    public static BigDecimal weighted(BigDecimal base,BigDecimal weight) { return utility(base.multiply(weight)); }
    public static BigDecimal utility(BigDecimal value) {
        if(value.signum()<0)throw new ApiException(400,"Negative utility is incompatible with the F05 contract");
        BigDecimal rounded=value.setScale(SCALE,RoundingMode.HALF_UP);
        try { rounded.unscaledValue().longValueExact(); } catch(ArithmeticException e) {
            throw new ApiException(400,"Utility exceeds the signed 64-bit scaled F05 representation");
        }
        return rounded;
    }
}

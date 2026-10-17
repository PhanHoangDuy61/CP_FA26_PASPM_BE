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

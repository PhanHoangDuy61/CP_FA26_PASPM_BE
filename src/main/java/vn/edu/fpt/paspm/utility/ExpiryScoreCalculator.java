package vn.edu.fpt.paspm.utility;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExpiryScoreCalculator {
    public record Batch(LocalDate expiryDate,long remainingQuantity,String status) {}
    public record Result(BigDecimal score,String status) {}
    public Result calculate(boolean requiresExpiry,List<Batch> batches,LocalDate analysisDate,int windowDays) {
        if(windowDays<=0)throw new IllegalArgumentException("Expiry window must be positive");
        if(!requiresExpiry)return new Result(BigDecimal.ZERO,"NOT_APPLICABLE");
        BigDecimal quantity=BigDecimal.ZERO, weighted=BigDecimal.ZERO;
        boolean missing=false;
        for(var batch:batches) {
            if(!"ACTIVE".equals(batch.status())||batch.remainingQuantity()<=0)continue;
            if(batch.expiryDate()==null) { missing=true;continue; }
            long days=ChronoUnit.DAYS.between(analysisDate,batch.expiryDate());
            if(days<=0)continue;
            BigDecimal score=BigDecimal.ONE.subtract(BigDecimal.valueOf(days).divide(BigDecimal.valueOf(windowDays),MathContext.DECIMAL128)).max(BigDecimal.ZERO);
            BigDecimal q=BigDecimal.valueOf(batch.remainingQuantity());
            quantity=quantity.add(q);weighted=weighted.add(q.multiply(score));
        }
        if(missing)return new Result(null,"MISSING_METADATA");
        if(quantity.signum()==0)return new Result(BigDecimal.ZERO,"NO_SELLABLE_BATCH");
        return new Result(weighted.divide(quantity,18,RoundingMode.HALF_UP),"AVAILABLE");
    }
}

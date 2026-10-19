package vn.edu.fpt.paspm.utility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Shared v1.2 policy for Utility and independent expiry-push reads. Never mutates inventory. */
@Component
public class ExpiryScoreCalculator {
    public static final String SCORING_VERSION="EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1";
    public record Batch(int batchId,LocalDate expiryDate,long remainingQuantity,String status) {
        public Batch(LocalDate expiryDate,long remainingQuantity,String status) { this(0,expiryDate,remainingQuantity,status); }
    }
    public record PushBatch(int productId,int batchId,LocalDate expiryDate,long remainingQuantity,
        int expiryPushDays,long daysRemaining,boolean pushRequired,long pushRequiredQuantity,LocalDate asOfDate) {}
    public record Evidence(int expiryPushDays,LocalDate earliestEligibleExpiryDate,Long daysRemaining,
        LocalDate asOfDate,String reason,List<PushBatch> batches) {}
    public record Result(BigDecimal score,String status,Evidence evidence) {}

    public Result calculate(boolean requiresExpiry,List<Batch> batches,LocalDate asOfDate,Integer configuredDays) {
        return calculate(0,requiresExpiry,batches,asOfDate,configuredDays);
    }
    public Result calculate(int productId,boolean requiresExpiry,List<Batch> batches,LocalDate asOfDate,Integer configuredDays) {
        int threshold=configuredDays==null?0:configuredDays;
        if(threshold<0)throw new IllegalArgumentException("Product expiryPushDays must be nonnegative");
        if(!requiresExpiry)return result(BigDecimal.ZERO,"NOT_APPLICABLE",threshold,null,asOfDate,"PRODUCT_NOT_REQUIRING_EXPIRY",List.of());
        List<PushBatch> eligible=new ArrayList<>();
        boolean missing=false;
        for(var batch:batches) {
            if(!"ACTIVE".equals(batch.status())||batch.remainingQuantity()<=0)continue;
            if(batch.expiryDate()==null) { missing=true;continue; }
            long days=ChronoUnit.DAYS.between(asOfDate,batch.expiryDate());
            if(days<=0)continue;
            boolean push=threshold>0&&days<=threshold;
            eligible.add(new PushBatch(productId,batch.batchId(),batch.expiryDate(),batch.remainingQuantity(),
                threshold,days,push,push?batch.remainingQuantity():0,asOfDate));
        }
        eligible.sort(Comparator.comparing(PushBatch::expiryDate).thenComparingInt(PushBatch::batchId));
        LocalDate earliest=eligible.isEmpty()?null:eligible.get(0).expiryDate();
        if(missing)return result(threshold==0?BigDecimal.ZERO:null,"MISSING_METADATA",threshold,earliest,asOfDate,"MISSING_EXPIRY_METADATA",eligible);
        if(eligible.isEmpty())return result(BigDecimal.ZERO,"NO_SELLABLE_BATCH",threshold,null,asOfDate,"NO_ELIGIBLE_BATCH",eligible);
        boolean push=eligible.get(0).pushRequired();
        return result(push?BigDecimal.ONE:BigDecimal.ZERO,"AVAILABLE",threshold,earliest,asOfDate,
            threshold==0?"THRESHOLD_DISABLED":push?"WITHIN_THRESHOLD":"OUTSIDE_THRESHOLD",eligible);
    }
    private Result result(BigDecimal score,String status,int threshold,LocalDate earliest,LocalDate date,String reason,List<PushBatch> batches) {
        return new Result(score,status,new Evidence(threshold,earliest,earliest==null?null:ChronoUnit.DAYS.between(date,earliest),date,reason,List.copyOf(batches)));
    }
}

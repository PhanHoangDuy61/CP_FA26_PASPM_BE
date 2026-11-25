package vn.edu.fpt.paspm.utility;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import vn.edu.fpt.paspm.catalog.inventory.BatchExpiryRules;
import vn.edu.fpt.paspm.utility.UtilityVersionedContracts.FefoEvidence;
import vn.edu.fpt.paspm.utility.UtilityVersionedContracts.RiskBatch;

/**
 * W2_EXPIRY_SEASON_V1 expiry risk (paspm-experiments src/expiry.py, assumption A4):
 * U_expiry = Q_risk / Q_eligible by a FEFO simulation at a constant daily sales rate v = unitsSold / N.
 * Quantities are kept as exact integers in units of 1/N, so U_expiry and W are exact before rounding.
 * Never mutates inventory.
 */
@Component
public class ExpiryRiskCalculator {
    public static final String MISSING_PUSH_DAY="MISSING_PUSH_DAY";
    public static final String SLOW_MOVING="SLOW_MOVING_NO_RECENT_SALES";
    public static final String EXPIRED="EXPIRED";
    public record Batch(int batchId,LocalDate pushDay,LocalDate expiryDate,long remainingQuantity,String status) {}
    /** riskScaled/eligibleScaled is the exact U_expiry (both in units of 1/N; eligibleScaled is 1 when U is 0 by rule). */
    public record Result(BigDecimal score,BigInteger riskScaled,BigInteger eligibleScaled,String status,
        FefoEvidence evidence,List<Integer> batchesMissingPushDay) {}

    public Result calculate(boolean requiresExpiry,List<Batch> batches,LocalDate asOfDate,int salesWindowDays,long unitsSold) {
        if(salesWindowDays<1)throw new IllegalArgumentException("salesWindowDays must be positive");
        if(unitsSold<0)throw new IllegalArgumentException("unitsSold must be nonnegative");
        BigDecimal rate=BigDecimal.valueOf(unitsSold).divide(BigDecimal.valueOf(salesWindowDays),6,RoundingMode.HALF_UP);
        if(!requiresExpiry)return zero("NOT_APPLICABLE","PRODUCT_NOT_REQUIRING_EXPIRY",asOfDate,salesWindowDays,unitsSold,rate,0,List.of(),List.of(),List.of());
        var eligible=new ArrayList<Batch>();var listed=new ArrayList<RiskBatch>();var missing=new ArrayList<Integer>();
        var warnings=new ArrayList<String>();
        var ordered=new ArrayList<>(batches);ordered.sort(Comparator.comparingInt(Batch::batchId));
        for(var batch:ordered) {
            if(!"ACTIVE".equals(batch.status())||batch.remainingQuantity()<=0)continue;
            if(BatchExpiryRules.isExpired(batch.expiryDate(),asOfDate)) {
                listed.add(new RiskBatch(batch.batchId(),batch.pushDay(),batch.expiryDate(),batch.remainingQuantity(),days(asOfDate,batch.pushDay()),null,null,false,List.of(EXPIRED)));
                continue;
            }
            if(batch.pushDay()==null) {
                missing.add(batch.batchId());
                listed.add(new RiskBatch(batch.batchId(),null,batch.expiryDate(),batch.remainingQuantity(),null,null,null,false,List.of(MISSING_PUSH_DAY)));
                continue;
            }
            eligible.add(batch);
        }
        if(!missing.isEmpty())warnings.add(MISSING_PUSH_DAY);
        if(unitsSold==0)warnings.add(SLOW_MOVING);
        long eligibleQuantity=eligible.stream().mapToLong(Batch::remainingQuantity).sum();
        if(eligibleQuantity==0) {
            listed.sort(Comparator.comparingInt(RiskBatch::batchId));
            return zero(missing.isEmpty()?"NO_SELLABLE_BATCH":"MISSING_PUSH_DAY","NO_ELIGIBLE_BATCH",asOfDate,salesWindowDays,unitsSold,rate,0,listed,missing,warnings);
        }
        // FEFO: push day ascending, then batch_id as an integer.
        eligible.sort(Comparator.comparing(Batch::pushDay).thenComparingInt(Batch::batchId));
        if(unitsSold==0) { // Q7: no recent sales -> U_expiry = 0 with a warning (hard to sell).
            var rows=new ArrayList<RiskBatch>();
            for(var b:eligible)rows.add(new RiskBatch(b.batchId(),b.pushDay(),b.expiryDate(),b.remainingQuantity(),days(asOfDate,b.pushDay()),null,null,true,List.of()));
            rows.addAll(listed);
            return zero("SLOW_MOVING",SLOW_MOVING,asOfDate,salesWindowDays,unitsSold,rate,eligibleQuantity,rows,missing,warnings);
        }
        BigInteger n=BigInteger.valueOf(salesWindowDays),units=BigInteger.valueOf(unitsSold);
        BigInteger sold=BigInteger.ZERO,risk=BigInteger.ZERO;
        var rows=new ArrayList<RiskBatch>();
        for(var b:eligible) {
            long days=days(asOfDate,b.pushDay());
            BigInteger quantity=BigInteger.valueOf(b.remainingQuantity()).multiply(n);
            // P4: a push day on or before the analysis date gives zero selling days, so the whole batch is at risk.
            BigInteger canSell=units.multiply(BigInteger.valueOf(Math.max(days,0))).subtract(sold).max(BigInteger.ZERO);
            BigInteger batchSold=quantity.min(canSell);
            BigInteger batchRisk=quantity.subtract(batchSold);
            risk=risk.add(batchRisk);sold=sold.add(batchSold);
            rows.add(new RiskBatch(b.batchId(),b.pushDay(),b.expiryDate(),b.remainingQuantity(),days,
                scaled(batchSold,salesWindowDays),scaled(batchRisk,salesWindowDays),true,days<=0?List.of("PUSH_DAY_REACHED"):List.of()));
        }
        rows.addAll(listed);
        BigInteger eligibleScaled=BigInteger.valueOf(eligibleQuantity).multiply(n);
        BigDecimal score=new BigDecimal(risk).divide(new BigDecimal(eligibleScaled),18,RoundingMode.HALF_UP);
        String status=missing.isEmpty()?"AVAILABLE":"MISSING_PUSH_DAY";
        var evidence=new FefoEvidence(UtilityVersionedContracts.V2,asOfDate,"FEFO_SIMULATION",salesWindowDays,unitsSold,rate,
            scaled(risk,salesWindowDays),eligibleQuantity,score,List.copyOf(rows),List.copyOf(warnings));
        return new Result(score,risk,eligibleScaled,status,evidence,List.copyOf(missing));
    }
    private static Long days(LocalDate asOf,LocalDate pushDay) { return pushDay==null?null:ChronoUnit.DAYS.between(asOf,pushDay); }
    private static BigDecimal scaled(BigInteger value,int n) { return new BigDecimal(value).divide(BigDecimal.valueOf(n),6,RoundingMode.HALF_UP); }
    private static Result zero(String status,String reason,LocalDate date,int window,long units,BigDecimal rate,long eligible,
            List<RiskBatch> rows,List<Integer> missing,List<String> warnings) {
        BigDecimal score=BigDecimal.ZERO.setScale(18);
        return new Result(score,BigInteger.ZERO,BigInteger.ONE,status,new FefoEvidence(UtilityVersionedContracts.V2,date,reason,window,units,rate,
            BigDecimal.ZERO.setScale(6),eligible,score,List.copyOf(rows),List.copyOf(warnings)),List.copyOf(missing));
    }
}

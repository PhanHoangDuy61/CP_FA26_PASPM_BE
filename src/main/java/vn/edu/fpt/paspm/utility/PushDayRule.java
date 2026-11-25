package vn.edu.fpt.paspm.utility;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import vn.edu.fpt.paspm.catalog.inventory.BatchExpiryRules;

/**
 * W2 expiry push: a sellable batch (ACTIVE, remaining > 0, not expired: the date is not after its expiry date) must be pushed once its Store Admin push day
 * is on or before the date (P4). A batch without a push day only produces a warning. Shared by F03 and F06 lists.
 */
public final class PushDayRule {
    private PushDayRule() {}
    public record DueBatch(int productId,int batchId,LocalDate pushDay,LocalDate expiryDate,long daysToPush,long remainingQuantity) {}
    public record Result(List<DueBatch> due,List<Integer> missingPushDay) {}
    public static Result evaluate(int productId,List<ExpiryRiskCalculator.Batch> batches,LocalDate date) {
        var due=new ArrayList<DueBatch>();var missing=new ArrayList<Integer>();
        for(var b:batches) {
            if(!"ACTIVE".equals(b.status())||b.remainingQuantity()<=0)continue;
            if(BatchExpiryRules.isExpired(b.expiryDate(),date))continue;
            if(b.pushDay()==null) { missing.add(b.batchId());continue; }
            if(!b.pushDay().isAfter(date))due.add(new DueBatch(productId,b.batchId(),b.pushDay(),b.expiryDate(),ChronoUnit.DAYS.between(date,b.pushDay()),b.remainingQuantity()));
        }
        due.sort(Comparator.comparing(DueBatch::pushDay).thenComparingInt(DueBatch::batchId));
        missing.sort(Integer::compare);
        return new Result(List.copyOf(due),List.copyOf(missing));
    }
}

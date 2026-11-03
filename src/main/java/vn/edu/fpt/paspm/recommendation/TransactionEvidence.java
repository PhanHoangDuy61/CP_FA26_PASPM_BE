package vn.edu.fpt.paspm.recommendation;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.RoundingMode;
import java.util.*;

/** Presence in immutable historical baskets; quantities and SPMF support are not evidence. */
public final class TransactionEvidence {
    private final List<Set<Integer>> transactions;
    public TransactionEvidence(List<Set<Integer>> transactions) {
        this.transactions=transactions==null?List.of():transactions.stream().map(Set::copyOf).toList();
    }
    public int transactionCount(){return transactions.size();}
    public Evidence measure(List<Integer> productIds,Integer anchorProductId) {
        if(transactions.isEmpty())return new Evidence(0,null,null,null,null,"EVIDENCE_UNAVAILABLE");
        var group=new HashSet<>(productIds);
        if(anchorProductId!=null)group.add(anchorProductId);
        int support=(int)transactions.stream().filter(t->t.containsAll(group)).count();
        Integer anchor=anchorProductId==null?null:(int)transactions.stream().filter(t->t.contains(anchorProductId)).count();
        return new Evidence(transactions.size(),support,ratio(support,transactions.size()),anchor,
            anchor==null||anchor==0?null:ratio(support,anchor),anchor!=null&&anchor==0?"ZERO_ANCHOR_SUPPORT":"AVAILABLE");
    }
    private static BigDecimal ratio(int numerator,int denominator){return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator),6,RoundingMode.HALF_UP);}
    @Schema(name="RecTransactionEvidence",description="Presence counts from one immutable source dataset; ratios displayed at six decimal places, decisions use exact counts")
    public record Evidence(int totalTransactions,@Schema(types={"integer","null"}) Integer supportCount,
        @Schema(types={"number","null"}) BigDecimal supportRatio,@Schema(types={"integer","null"}) Integer anchorSupportCount,
        @Schema(types={"number","null"}) BigDecimal confidence,
        @Schema(allowableValues={"AVAILABLE","EVIDENCE_UNAVAILABLE","ZERO_ANCHOR_SUPPORT"}) String status) {}
}

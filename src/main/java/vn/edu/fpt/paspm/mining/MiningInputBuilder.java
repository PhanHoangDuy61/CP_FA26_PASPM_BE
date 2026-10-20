package vn.edu.fpt.paspm.mining;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Component;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.UtilityMatrixContracts.*;

/** Consumes only frozen values: never calls Product, inventory or utility calculation services. */
@Component
public class MiningInputBuilder {
    public static final String VERSION="F03_SCALE6_GCD_INT32_DENSE_V1";
    public static final int MAX_ROWS=20000, MAX_ITEMS=256;
    public record Dataset(String text,String hash,Map<Integer,Integer> products,long divisor,
                          int totalUtility,int transactionCount,String conversionVersion,int utilityScale,String roundingMode) {
        public Dataset(String text,String hash,Map<Integer,Integer> products,long divisor,int totalUtility,int transactionCount){
            this(text,hash,products,divisor,totalUtility,transactionCount,VERSION,6,"HALF_UP");
        }
        public int inputBytes(){return text.getBytes(StandardCharsets.UTF_8).length;}
        public BigDecimal originalUtility(long normalized){
            return BigDecimal.valueOf(normalized).multiply(BigDecimal.valueOf(divisor)).movePointLeft(utilityScale);
        }
    }
    public Dataset build(SnapshotView snapshot,List<MatrixItem> rows) {
        if(snapshot==null)throw new ApiException(404,"Utility snapshot not found");
        if(!"READY".equals(snapshot.status()) || snapshot.roundingScale()!=6 || snapshot.configuration()==null
            || snapshot.configuration().utilityScale()!=6 || !"HALF_UP".equals(snapshot.configuration().roundingMode())
            || !"PRODUCT_ID_V1".equals(snapshot.configuration().itemMapping())
            || snapshot.configuration().config()==null || snapshot.asOfDate()==null
            || !snapshot.asOfDate().equals(snapshot.configuration().analysisDate())
            || snapshot.rangeStart()==null || snapshot.rangeEnd()==null || snapshot.rangeEnd().isBefore(snapshot.rangeStart())
            || !hashValid(snapshot.sourceHash()) || !hashValid(snapshot.matrixHash()))
            throw bad("Snapshot is incomplete or has unsupported frozen metadata");
        if(rows.isEmpty() || rows.size()>MAX_ROWS)throw bad("Snapshot matrix must contain 1.."+MAX_ROWS+" rows");
        var transactions=new TreeMap<Integer,TreeMap<Integer,Long>>();
        var totals=new HashMap<Integer,BigDecimal>();var products=new TreeSet<Integer>();
        long divisor=0,total=0;
        try {
            for(var row:rows){
                if(row.transactionId()<=0 || row.productId()<=0 || row.algorithmItemId()!=row.productId() || row.quantity()<=0)
                    throw bad("Invalid frozen item mapping/quantity at transaction "+row.transactionId());
                long scaled=scaled(row.weightedUtility());
                var tx=transactions.computeIfAbsent(row.transactionId(),k->new TreeMap<>());
                if(tx.putIfAbsent(row.productId(),scaled)!=null)throw bad("Duplicate item in transaction "+row.transactionId());
                if(row.transactionUtility()==null || row.transactionUtility().signum()<0)throw bad("Invalid transaction utility at "+row.transactionId());
                var previous=totals.putIfAbsent(row.transactionId(),row.transactionUtility());
                if(previous!=null && previous.compareTo(row.transactionUtility())!=0)throw bad("Inconsistent transaction utility at "+row.transactionId());
                divisor=gcd(divisor,scaled);total=Math.addExact(total,scaled);products.add(row.productId());
            }
            if(products.size()>MAX_ITEMS)throw bad("Snapshot exceeds "+MAX_ITEMS+" distinct products");
            if(scaled(snapshot.totalWeightedUtility())!=total)throw bad("Snapshot total does not match frozen matrix");
            if(divisor==0)divisor=1;
            if(total/divisor>Integer.MAX_VALUE)throw bad("Lossless converted dataset exceeds SPMF int32 capacity");
            var mapping=new LinkedHashMap<Integer,Integer>();var reverse=new HashMap<Integer,Integer>();
            for(int product:products){int id=mapping.size()+1;mapping.put(id,product);reverse.put(product,id);}
            var text=new StringBuilder();
            for(var entry:transactions.entrySet()){
                long sum=0;var ids=new StringJoiner(" ");var values=new StringJoiner(" ");
                for(var item:entry.getValue().entrySet()){
                    sum=Math.addExact(sum,item.getValue());ids.add(reverse.get(item.getKey()).toString());values.add(Long.toString(item.getValue()/divisor));
                }
                if(sum!=scaled(totals.get(entry.getKey())))throw bad("Transaction utility does not match items at "+entry.getKey());
                text.append(ids).append(':').append(sum/divisor).append(':').append(values).append('\n');
            }
            return new Dataset(text.toString(),sha(text.toString()),Collections.unmodifiableMap(mapping),divisor,(int)(total/divisor),transactions.size());
        }catch(ArithmeticException ex){throw bad("Frozen utility precision or signed 64-bit capacity exceeded");}
    }
    public int threshold(Dataset dataset,MiningContracts.MinUtilMode mode,BigDecimal value){
        if(mode==null || value==null || value.signum()<=0 || value.stripTrailingZeros().scale()>6)throw bad("minUtil must be positive with at most 6 decimal places");
        if(mode==MiningContracts.MinUtilMode.PERCENTAGE && value.compareTo(BigDecimal.valueOf(100))>0)throw bad("Percentage minUtil must be in (0,100]");
        try{
            BigDecimal threshold=mode==MiningContracts.MinUtilMode.PERCENTAGE
                ?value.multiply(BigDecimal.valueOf(dataset.totalUtility())).divide(BigDecimal.valueOf(100),0,RoundingMode.CEILING)
                :value.movePointRight(6).divide(BigDecimal.valueOf(dataset.divisor()),0,RoundingMode.CEILING);
            int result=threshold.intValueExact();if(result<1)throw bad("Dataset has no positive utility for percentage minUtil; use a positive absolute threshold");
            return result;
        }catch(ArithmeticException e){throw bad("minUtil exceeds SPMF int32 capacity");}
    }
    private static long scaled(BigDecimal x){if(x==null || x.signum()<0)throw bad("Null or negative frozen weighted utility");return x.setScale(6,RoundingMode.UNNECESSARY).movePointRight(6).longValueExact();}
    private static long gcd(long a,long b){while(b!=0){long t=a%b;a=b;b=t;}return a;}
    private static boolean hashValid(String x){return x!=null&&x.matches("[0-9a-f]{64}");}
    public static String sha(String x){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(x.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static ApiException bad(String text){return new ApiException(400,text);}
}
